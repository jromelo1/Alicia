/**
 * Cloud Functions de Círculo de Confianza — Llamada diaria de Alicia.
 *
 * Dos piezas:
 *  1. `dailyCallScheduler` — corre cada 15 minutos, revisa qué círculos tienen la
 *     llamada activada y a quién le toca ahora según su hora local preferida, y le
 *     pide a Retell que llame.
 *  2. `retellWebhook` — recibe el resultado de la llamada (resumen, duración,
 *     estado) cuando Retell termina de analizarla, y lo guarda en Firestore para
 *     que la app lo muestre y para que la siguiente llamada tenga contexto. Además
 *     avisa a la familia por SMS (Twilio): alerta si Retell detectó una señal de
 *     riesgo, aviso si no contestó, y un resumen corto si la llamada salió bien
 *     (lógica en familyNotify.ts).
 *  3. `createSubscription` + `mercadoPagoWebhook` — cobro mensual con Mercado Pago
 *     Suscripciones (lógica en mercadoPago.ts). Si BILLING_ENFORCE=true, el
 *     scheduler deja de llamar a los círculos de la web cuya prueba de 7 días
 *     terminó sin una suscripción autorizada.
 *
 * Configuración necesaria antes de desplegar (ver README de esta carpeta):
 *   firebase functions:secrets:set RETELL_API_KEY
 *   firebase functions:config:set retell.agent_id="agent_xxx" retell.from_number="+1XXXXXXXXXX"
 *   (o usar `firebase functions:params` / archivo .env, ver defineString abajo)
 */

import { onSchedule } from "firebase-functions/v2/scheduler";
import { onRequest } from "firebase-functions/v2/https";
import { defineSecret, defineString } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, DocumentReference } from "firebase-admin/firestore";
import { createHmac, timingSafeEqual } from "crypto";
import { Retell } from "retell-sdk";
import {
  buildNotice, collectRecipients, displayName, readAlert, resolveCallStatus, sendTwilioSms, Lang,
} from "./familyNotify";
import {
  MP_API, PlanId, billingFromPreapproval, buildPreapprovalBody, canCall, isEmail, isPlanId,
  parseSignatureHeader, signatureManifest,
} from "./mercadoPago";
import { isCallDay, maxCallDurationMs, planRule } from "./plans";

initializeApp();
const db = getFirestore();

const RETELL_API_KEY = defineSecret("RETELL_API_KEY");
const RETELL_AGENT_ID = defineString("RETELL_AGENT_ID");
const RETELL_FROM_NUMBER = defineString("RETELL_FROM_NUMBER");
// Número propio (p. ej. de Bogotá, +57…) conectado a Retell por SIP trunk (Twilio).
// Los números que vende Retell no pueden llamar a Colombia ni a casi toda Latinoamérica
// ("Call country not supported"); con este, toda llamada que NO sea a +1 sale desde aquí.
// Vacío = todas las llamadas salen desde RETELL_FROM_NUMBER, como antes.
const RETELL_FROM_NUMBER_LATAM = defineString("RETELL_FROM_NUMBER_LATAM", { default: "" });

// Avisos a la familia por SMS (Twilio). Si TWILIO_ACCOUNT_SID queda vacío, no se
// envía nada y todo lo demás funciona igual (solo queda un log).
const TWILIO_ACCOUNT_SID = defineString("TWILIO_ACCOUNT_SID", { default: "" });
const TWILIO_FROM = defineString("TWILIO_FROM", { default: "" });
const TWILIO_AUTH_TOKEN = defineSecret("TWILIO_AUTH_TOKEN");
// "false" para enviar solo alertas y llamadas no contestadas (sin el resumen diario).
const FAMILY_SMS_SUMMARIES = defineString("FAMILY_SMS_SUMMARIES", { default: "true" });

// Mercado Pago Suscripciones. Precios en la moneda de la cuenta de Mercado Pago
// (Colombia → COP, sin decimales). Se cambian en functions/.env y se vuelve a desplegar.
const MP_ACCESS_TOKEN = defineSecret("MP_ACCESS_TOKEN");
const MP_WEBHOOK_SECRET = defineSecret("MP_WEBHOOK_SECRET");
const MP_CURRENCY = defineString("MP_CURRENCY", { default: "COP" });
const MP_PRICE_ESENCIAL = defineString("MP_PRICE_ESENCIAL", { default: "79900" });
const MP_PRICE_DIARIO = defineString("MP_PRICE_DIARIO", { default: "139900" });
const MP_PRICE_PLUS = defineString("MP_PRICE_PLUS", { default: "239900" });
const MP_BACK_URL = defineString("MP_BACK_URL", { default: "https://callmealicia.com/?pago=listo" });
// "true" = sin pago no hay llamadas después de la prueba. Por defecto "false" para
// no cortarle a nadie las llamadas mientras se prueba el cobro.
const BILLING_ENFORCE = defineString("BILLING_ENFORCE", { default: "false" });
const WEB_ORIGINS = ["https://callmealicia.com", "https://www.callmealicia.com", "http://localhost:8080"];

const RETELL_CREATE_CALL_URL = "https://api.retellai.com/v2/create-phone-call";
// La ventana debe ser igual al intervalo del scheduler — si se cambia uno, cambiar el otro.
const RUN_WINDOW_MINUTES = 15;

// ─────────────────────────────────────────────────────────────────────────────
//  Tipos mínimos — solo lo que esta función realmente usa de cada API.
// ─────────────────────────────────────────────────────────────────────────────

interface CallProfile {
  fullName?: string;
  treatment?: string;
  originCountry?: string;
  interests?: string;
  familyInfo?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  phoneE164?: string;
  preferredHour?: number;
  preferredMinute?: number;
  timeZoneId?: string;
  callEnabled?: boolean;
  lastCallNotes?: string;
  lastCallAt?: number;
  /** "es" | "en" — idioma que eligió quien se registró en la web. */
  language?: string;
}

interface RetellCallAnalysis {
  call_summary?: string;
  user_sentiment?: string;
  in_voicemail?: boolean;
  /** Campos de "Post-Call Analysis" configurados en el agente — aquí `alerta` y `motivo_alerta`. */
  custom_analysis_data?: Record<string, unknown>;
}

interface RetellCallObject {
  call_id: string;
  start_timestamp?: number;
  end_timestamp?: number;
  duration_ms?: number;
  disconnection_reason?: string;
  call_analysis?: RetellCallAnalysis;
  metadata?: { circleId?: string };
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. Scheduler — decide a quién le toca llamar ahora
// ─────────────────────────────────────────────────────────────────────────────

export const dailyCallScheduler = onSchedule(
  {
    schedule: `every ${RUN_WINDOW_MINUTES} minutes`,
    secrets: [RETELL_API_KEY],
  },
  async () => {
    const snapshot = await db
      .collection("circles")
      .where("callProfile.callEnabled", "==", true)
      .get();

    if (snapshot.empty) {
      logger.info("dailyCallScheduler: ningún círculo con llamada activada");
      return;
    }

    const nowMs = Date.now();
    const enforceBilling = BILLING_ENFORCE.value() === "true";

    await Promise.all(
      snapshot.docs.map(async (doc) => {
        const circleId = doc.id;
        const profile = doc.data().callProfile as CallProfile | undefined;
        if (enforceBilling && !canCall(doc.data(), nowMs)) {
          logger.info(`dailyCallScheduler: ${circleId} sin suscripción activa tras la prueba, se salta`);
          return;
        }
        if (!profile || !profile.phoneE164 || !profile.timeZoneId) {
          logger.warn(`dailyCallScheduler: perfil incompleto en ${circleId}, se salta`);
          return;
        }

        const { hour, minute, dateStr, weekday } = localTimeParts(profile.timeZoneId, nowMs);
        const plan = doc.data().plan;
        if (!isCallDay(plan, weekday)) return; // p. ej. Esencial: solo lunes, miércoles y viernes
        const lastCallDateStr = profile.lastCallAt
          ? localTimeParts(profile.timeZoneId, profile.lastCallAt).dateStr
          : null;

        if (lastCallDateStr === dateStr) {
          // Ya se llamó (o se intentó) hoy en la hora local de este círculo.
          return;
        }

        const targetMinutes = (profile.preferredHour ?? 10) * 60 + (profile.preferredMinute ?? 0);
        const nowMinutes = hour * 60 + minute;
        const isDue = nowMinutes >= targetMinutes && nowMinutes < targetMinutes + RUN_WINDOW_MINUTES;
        if (!isDue) return;

        logger.info(`dailyCallScheduler: llamando a ${circleId} (${profile.fullName ?? "sin nombre"})`);

        try {
          await createRetellCall(circleId, profile, plan);
          // Se marca de inmediato — si el webhook tarda o falla, igual no se
          // vuelve a marcar como "pendiente hoy" y se evita un doble marcado.
          // El webhook (retellWebhook) sobrescribe este valor con la hora real
          // de fin de la llamada en cuanto Retell la termina de analizar.
          await doc.ref.set(
            { callProfile: { lastCallAt: nowMs } },
            { merge: true }
          );
        } catch (err) {
          logger.error(`dailyCallScheduler: falló la llamada a ${circleId}`, err);
          // No se marca lastCallAt — se reintentará en la siguiente ventana de
          // 15 min de hoy. Si Retell sigue fallando todo el día, revisar logs:
          // no hay límite de reintentos en esta primera versión.
        }
      })
    );
  }
);

/** EE. UU./Canadá (+1) → número de Retell; resto de países → número latinoamericano si está configurado. */
function pickFromNumber(to: string): string {
  const latam = RETELL_FROM_NUMBER_LATAM.value();
  return latam && !to.startsWith("+1") ? latam : RETELL_FROM_NUMBER.value();
}

async function createRetellCall(circleId: string, profile: CallProfile, plan: unknown): Promise<void> {
  const maxMs = maxCallDurationMs(plan);
  const response = await fetch(RETELL_CREATE_CALL_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RETELL_API_KEY.value()}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from_number: pickFromNumber(profile.phoneE164 ?? ""),
      to_number: profile.phoneE164,
      override_agent_id: RETELL_AGENT_ID.value(),
      metadata: { circleId },
      retell_llm_dynamic_variables: {
        full_name: profile.fullName ?? "",
        treatment: profile.treatment ?? "",
        origin_country: profile.originCountry ?? "",
        interests: profile.interests ?? "",
        family_info: profile.familyInfo ?? "",
        emergency_contact_name: profile.emergencyContactName ?? "",
        emergency_contact_phone: profile.emergencyContactPhone ?? "",
        last_call_notes: profile.lastCallNotes ?? "",
        // Minutos del plan: el prompt le pide a Alicia despedirse un minuto antes.
        max_minutes: String(planRule(plan)?.minutes ?? 10),
      },
      // Corte de seguridad por plan (minutos del plan + 1 de margen).
      ...(maxMs ? { agent_override: { agent: { max_call_duration_ms: maxMs } } } : {}),
    }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`Retell respondió ${response.status}: ${body}`);
  }
}

/** Hora y fecha local de un timeZone de Android (IANA), a partir de un instante en ms. */
function localTimeParts(timeZoneId: string, atMs: number): { hour: number; minute: number; dateStr: string; weekday: number } {
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: timeZoneId,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  });
  const parts = Object.fromEntries(
    formatter.formatToParts(new Date(atMs)).map((p) => [p.type, p.value])
  );
  return {
    hour: parseInt(parts.hour === "24" ? "0" : parts.hour, 10),
    minute: parseInt(parts.minute, 10),
    dateStr: `${parts.year}-${parts.month}-${parts.day}`,
    weekday: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].indexOf(parts.weekday),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
//  2. Webhook — recibe el resultado de la llamada
// ─────────────────────────────────────────────────────────────────────────────

export const retellWebhook = onRequest(
  { secrets: [RETELL_API_KEY, TWILIO_AUTH_TOKEN] },
  async (req, res) => {
    const signature = req.headers["x-retell-signature"];
    if (typeof signature !== "string" || !req.rawBody) {
      res.status(400).send("Falta firma o cuerpo crudo");
      return;
    }

    // IMPORTANTE: se verifica contra el cuerpo crudo (rawBody), nunca contra
    // JSON.stringify(req.body) — Retell firma los bytes exactos que envió, y
    // re-serializar el JSON parseado puede no coincidir byte a byte.
    const isValid = await Retell.verify(
      req.rawBody.toString("utf-8"),
      RETELL_API_KEY.value(),
      signature
    );
    if (!isValid) {
      logger.warn("retellWebhook: firma inválida, se descarta");
      res.status(401).send("Firma inválida");
      return;
    }

    const event = req.body?.event as string | undefined;
    const call = req.body?.call as RetellCallObject | undefined;

    // call_started y call_ended también llegan, pero call_analyzed es el único
    // que trae el resumen (call_analysis) — los otros se reconocen sin hacer nada.
    if (event !== "call_analyzed" || !call) {
      res.status(200).send("ok");
      return;
    }

    const circleId = call.metadata?.circleId;
    if (!circleId) {
      logger.warn(`retellWebhook: call_analyzed sin circleId en metadata (call_id=${call.call_id})`);
      res.status(200).send("ok");
      return;
    }

    try {
      const endedAt = call.end_timestamp ?? Date.now();
      const startedAt = call.start_timestamp ?? endedAt;
      const durationSeconds = call.duration_ms ? Math.round(call.duration_ms / 1000) : Math.round((endedAt - startedAt) / 1000);
      const summary = call.call_analysis?.call_summary ?? "";
      logger.info(`retellWebhook: disconnection_reason="${call.disconnection_reason ?? ""}"`);
      const status = resolveCallStatus(call.disconnection_reason, call.call_analysis);
      const { alert, reason: alertReason } = readAlert(call.call_analysis);

      const circleRef = db.collection("circles").doc(circleId);
      const historyRef = circleRef.collection("callHistory").doc(call.call_id);
      await historyRef.set(
        { startedAt, endedAt, durationSeconds, summary, status, alert, alertReason },
        { merge: true }
      );

      // Solo una llamada completada con resumen reemplaza las notas: si no contestó,
      // se conservan las de la última conversación real para que la próxima tenga contexto.
      const profileUpdate: Record<string, unknown> = { lastCallAt: endedAt };
      if (status === "COMPLETED" && summary.trim()) profileUpdate.lastCallNotes = summary;
      await circleRef.set({ callProfile: profileUpdate }, { merge: true });

      logger.info(`retellWebhook: llamada ${call.call_id} guardada para ${circleId} (${status}${alert ? ", ALERTA" : ""})`);

      await notifyFamily(circleRef, historyRef, { status, alert, alertReason, summary, durationSeconds });

      res.status(200).send("ok");
    } catch (err) {
      logger.error("retellWebhook: error guardando la llamada", err);
      // 500 para que Retell reintente (hasta 3 veces) en vez de darla por perdida.
      res.status(500).send("error");
    }
  }
);

/**
 * Envía el SMS a la familia, una sola vez por llamada aunque Retell reintente el
 * webhook: se "reclama" el envío en una transacción sobre el registro de la llamada.
 * Un error de SMS no hace fallar el webhook (la llamada ya quedó guardada).
 */
async function notifyFamily(
  circleRef: DocumentReference,
  historyRef: DocumentReference,
  info: { status: ReturnType<typeof resolveCallStatus>; alert: boolean; alertReason: string; summary: string; durationSeconds: number }
): Promise<void> {
  try {
    const circleSnap = await circleRef.get();
    const profile = (circleSnap.data()?.callProfile ?? {}) as CallProfile;
    const lang: Lang = profile.language === "en" ? "en" : "es";
    const notice = buildNotice({
      ...info,
      name: displayName(profile.fullName, profile.treatment),
      lang,
      summariesEnabled: FAMILY_SMS_SUMMARIES.value() !== "false",
    });
    if (!notice) return;

    const membersSnap = await circleRef.collection("members").get();
    const recipients = collectRecipients(
      profile.phoneE164 ?? "",
      profile.emergencyContactPhone,
      membersSnap.docs.map((d) => d.data() as { phone?: string; notifyOnPanic?: boolean })
    );
    if (recipients.length === 0) {
      logger.info(`notifyFamily: ${circleRef.id} sin contactos de familia — no se envía ${notice.kind}`);
      return;
    }

    const sid = TWILIO_ACCOUNT_SID.value();
    const from = TWILIO_FROM.value();
    if (!sid || !from) {
      logger.warn(`notifyFamily: Twilio sin configurar — se omite ${notice.kind} para ${circleRef.id}`);
      return;
    }

    const claimed = await db.runTransaction(async (tx) => {
      const h = await tx.get(historyRef);
      if (h.get("familyNotice")) return false;
      tx.set(historyRef, { familyNotice: { kind: notice.kind, to: recipients.length, at: Date.now() } }, { merge: true });
      return true;
    });
    if (!claimed) return; // reintento de Retell: ya se avisó

    const results = await Promise.allSettled(
      recipients.map((to) => sendTwilioSms({ accountSid: sid, authToken: TWILIO_AUTH_TOKEN.value(), from }, to, notice.body))
    );
    const failed = results.filter((r) => r.status === "rejected") as PromiseRejectedResult[];
    failed.forEach((f) => logger.error("notifyFamily: SMS falló", f.reason));
    logger.info(`notifyFamily: ${notice.kind} enviado a ${recipients.length - failed.length}/${recipients.length} contactos de ${circleRef.id}`);
  } catch (err) {
    logger.error("notifyFamily: error avisando a la familia", err);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  3. Mercado Pago — crear la suscripción y recibir sus cambios
// ─────────────────────────────────────────────────────────────────────────────

function priceFor(plan: PlanId): number {
  const raw = { esencial: MP_PRICE_ESENCIAL, diario: MP_PRICE_DIARIO, plus: MP_PRICE_PLUS }[plan].value();
  return Number(raw);
}

async function mpFetch(path: string, init: { method?: string; body?: unknown } = {}): Promise<any> {
  const res = await fetch(`${MP_API}${path}`, {
    method: init.method ?? "GET",
    headers: { Authorization: `Bearer ${MP_ACCESS_TOKEN.value()}`, "Content-Type": "application/json" },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Mercado Pago respondió ${res.status} en ${path}: ${text}`);
  return text ? JSON.parse(text) : {};
}

/**
 * La web la llama justo después del registro, con la sesión de Firebase de quien se
 * registró (header Authorization: Bearer <ID token>) y { circleId, email, lang }.
 * Devuelve { url } = página de pago de Mercado Pago. El plan y el precio se leen del
 * servidor (circles/{id}.plan y los parámetros MP_PRICE_*), nunca del navegador.
 */
export const createSubscription = onRequest(
  { secrets: [MP_ACCESS_TOKEN], cors: WEB_ORIGINS },
  async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ error: "method" }); return; }
    try {
      const token = (req.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
      if (!token) { logger.warn("createSubscription: sin token"); res.status(401).json({ error: "auth" }); return; }
      const { uid } = await getAuth().verifyIdToken(token);

      const { circleId, email, lang } = (req.body ?? {}) as { circleId?: string; email?: string; lang?: string };
      if (typeof circleId !== "string" || !isEmail(email)) { logger.warn("createSubscription: datos inválidos"); res.status(400).json({ error: "input" }); return; }

      const user = await db.collection("users").doc(uid).get();
      if (user.get("circleId") !== circleId) { logger.warn(`createSubscription: ${uid} no pertenece a ${circleId}`); res.status(403).json({ error: "circle" }); return; }

      const circleRef = db.collection("circles").doc(circleId);
      const circle = (await circleRef.get()).data() ?? {};
      const plan = circle.plan;
      if (!isPlanId(plan)) { logger.warn(`createSubscription: ${circleId} sin plan válido`); res.status(400).json({ error: "plan" }); return; }
      if (circle.billing?.status === "authorized") { res.status(409).json({ error: "already_active" }); return; }

      const amount = priceFor(plan);
      if (!(amount > 0)) throw new Error(`Precio inválido para ${plan}`);
      const l = lang === "en" ? "en" : "es";
      const backUrl = MP_BACK_URL.value() + (MP_BACK_URL.value().includes("?") ? "&" : "?") + "lang=" + l;

      const pre = await mpFetch("/preapproval", {
        method: "POST",
        body: buildPreapprovalBody({
          circleId, plan, amount, currency: MP_CURRENCY.value(), payerEmail: email.trim(),
          backUrl, trialStartedAt: circle.trialStartedAt, lang: l, nowMs: Date.now(),
        }),
      });
      if (!pre.init_point) throw new Error("Mercado Pago no devolvió init_point");

      await circleRef.set(
        { billing: { ...billingFromPreapproval(pre, plan, Date.now()), payerEmail: email.trim(), initPoint: pre.init_point } },
        { merge: true }
      );
      logger.info(`createSubscription: ${circleId} → preapproval ${pre.id} (${plan}, ${amount} ${MP_CURRENCY.value()})`);
      res.status(200).json({ url: pre.init_point });
    } catch (err) {
      logger.error("createSubscription: error", err);
      res.status(500).json({ error: "server" });
    }
  }
);

function validMpSignature(req: { headers: Record<string, unknown> }, dataId: string): boolean {
  const sig = parseSignatureHeader(req.headers["x-signature"] as string | undefined);
  if (!sig) return false;
  const requestId = (req.headers["x-request-id"] as string | undefined) ?? "";
  const expected = createHmac("sha256", MP_WEBHOOK_SECRET.value())
    .update(signatureManifest(dataId, requestId, sig.ts))
    .digest("hex");
  const a = Buffer.from(expected), b = Buffer.from(sig.v1);
  return a.length === b.length && timingSafeEqual(a, b);
}

/** Consulta la suscripción en Mercado Pago y guarda su estado en el círculo. */
async function syncPreapproval(preapprovalId: string, extra: Record<string, unknown> = {}): Promise<void> {
  const p = await mpFetch(`/preapproval/${encodeURIComponent(preapprovalId)}`);
  const circleId = p.external_reference as string | undefined;
  if (!circleId) { logger.warn(`mercadoPagoWebhook: preapproval ${preapprovalId} sin external_reference`); return; }
  const circleRef = db.collection("circles").doc(circleId);
  const snap = await circleRef.get();
  if (!snap.exists) { logger.warn(`mercadoPagoWebhook: círculo ${circleId} no existe`); return; }
  const current = snap.get("billing") as { preapprovalId?: string; status?: string; plan?: string } | undefined;
  // Un aviso viejo de otra suscripción (p. ej. un link anterior sin pagar) no pisa la vigente.
  if (current?.preapprovalId && current.preapprovalId !== p.id && p.status !== "authorized") {
    logger.info(`mercadoPagoWebhook: ${p.id} (${p.status}) no es la suscripción vigente de ${circleId}, se ignora`);
    return;
  }
  await circleRef.set({ billing: { ...billingFromPreapproval(p, current?.plan ?? snap.get("plan"), Date.now()), ...extra } }, { merge: true });
  logger.info(`mercadoPagoWebhook: ${circleId} → ${p.status}`);
}

/**
 * URL a configurar en Mercado Pago → Tus integraciones → (tu aplicación) → Webhooks,
 * con los eventos "Planes y suscripciones". Responde 200 rápido; si algo falla
 * responde 500 y Mercado Pago reintenta.
 */
export const mercadoPagoWebhook = onRequest(
  { secrets: [MP_ACCESS_TOKEN, MP_WEBHOOK_SECRET] },
  async (req, res) => {
    const type = String(req.query.type ?? req.query.topic ?? req.body?.type ?? "");
    const dataId = String(req.query["data.id"] ?? req.body?.data?.id ?? "");
    if (!dataId) { res.status(200).send("ok"); return; }
    if (!validMpSignature(req, dataId)) {
      logger.warn(`mercadoPagoWebhook: firma inválida (type=${type}, id=${dataId})`);
      res.status(401).send("firma inválida");
      return;
    }
    try {
      if (type === "subscription_preapproval") {
        await syncPreapproval(dataId);
      } else if (type === "subscription_authorized_payment") {
        const pay = await mpFetch(`/authorized_payments/${encodeURIComponent(dataId)}`);
        if (pay.preapproval_id) {
          await syncPreapproval(pay.preapproval_id, {
            lastPayment: { id: String(pay.id ?? dataId), status: pay.status ?? "", amount: pay.transaction_amount ?? 0, at: Date.now() },
          });
        }
      }
      res.status(200).send("ok");
    } catch (err) {
      logger.error("mercadoPagoWebhook: error", err);
      res.status(500).send("error");
    }
  }
);
