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
import { getFirestore, DocumentReference, QueryDocumentSnapshot } from "firebase-admin/firestore";
import { createHmac, timingSafeEqual } from "crypto";
import { Retell } from "retell-sdk";
import {
  buildNotice, collectRecipients, displayName, readAlert, readOptOut, resolveCallStatus, sendTwilioSms, Lang,
} from "./familyNotify";
import {
  MP_API, PlanId, billingFromPreapproval, buildPreapprovalBody, canCall, cancelledAndExpired, isEmail, isPlanId,
  paidUntilOnCancel,
  parseSignatureHeader, signatureManifest,
} from "./mercadoPago";
import { isCallDay, maxCallDurationMs, planRule } from "./plans";
import { voiceFor } from "./voices";

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

// Voz (acento) por país: "CO=voice_id,MX=voice_id". Vacío = la voz del agente. Ver voices.ts.
const RETELL_VOICES = defineString("RETELL_VOICES", { default: "" });

const RETELL_CREATE_CALL_URL = "https://api.retellai.com/v2/create-phone-call";
// La ventana debe ser igual al intervalo del scheduler — si se cambia uno, cambiar el otro.
const RUN_WINDOW_MINUTES = 15;
// Una llamada más corta que esto (p. ej. contestan y cuelgan a los 7 s) no es una
// conversación: su resumen no debe reemplazar las notas de la última conversación real.
const MIN_CONVERSATION_SECONDS = 30;

// ─────────────────────────────────────────────────────────────────────────────
//  Tipos mínimos — solo lo que esta función realmente usa de cada API.
// ─────────────────────────────────────────────────────────────────────────────

interface CallProfile {
  fullName?: string;
  treatment?: string;
  /** true = Alicia le habla de "tú", false = de "usted". */
  useTu?: boolean;
  originCountry?: string;
  interests?: string;
  familyInfo?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  phoneE164?: string;
  preferredHour?: number;
  preferredMinute?: number;
  timeZoneId?: string;
  /** Ver CallProfileStatus del lado Android — mismos valores de cadena. */
  profileStatus?: string;
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

interface RetellTranscriptTurn {
  /** "agent" = Alicia, "user" = la persona a la que se llamó. */
  role?: string;
  content?: string;
}

interface RetellCallObject {
  call_id: string;
  start_timestamp?: number;
  end_timestamp?: number;
  duration_ms?: number;
  disconnection_reason?: string;
  call_analysis?: RetellCallAnalysis;
  /** Texto plano con líneas "Agent: ..." y "User: ..." — respaldo si no viene transcript_object. */
  transcript?: string;
  transcript_object?: RetellTranscriptTurn[];
  metadata?: { circleId?: string };
}

interface TranscriptLine {
  role: "alicia" | "adulto";
  text: string;
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
      snapshot.docs.map(async (doc: QueryDocumentSnapshot) => {
        const circleId = doc.id;
        const profile = doc.data().callProfile as CallProfile | undefined;
        if (cancelledAndExpired(doc.data(), nowMs)) return; // plan cancelado: no se llama más
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
  const voiceId = voiceFor(RETELL_VOICES.value(), profile.originCountry, profile.phoneE164);
  const agentOverride: Record<string, unknown> = {};
  if (maxMs) agentOverride.max_call_duration_ms = maxMs;
  if (voiceId) agentOverride.voice_id = voiceId;
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
        formality: profile.useTu ? "tú" : "usted",
        language: profile.language ?? "es",
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
      ...(Object.keys(agentOverride).length ? { agent_override: { agent: agentOverride } } : {}),
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
  // "public": Retell necesita poder llamar a esta URL sin autenticarse — la
  // seguridad real la da la verificación de x-retell-signature de más abajo,
  // no el control de acceso de Cloud Run.
  { secrets: [RETELL_API_KEY, TWILIO_AUTH_TOKEN], invoker: "public" },
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
      // "No me llames más": la opción DNC de Retell (cuelga con disconnection_reason
      // "user_requested_dnc") o el campo de análisis post-llamada `no_llamar`.
      const optOut = call.disconnection_reason === "user_requested_dnc" || readOptOut(call.call_analysis);
      // Conversación completa, ya separada por turnos, para mostrarla en la app.
      const transcript = buildTranscript(call);

      const circleRef = db.collection("circles").doc(circleId);
      const historyRef = circleRef.collection("callHistory").doc(call.call_id);
      await historyRef.set(
        { startedAt, endedAt, durationSeconds, summary, status, alert, alertReason, optOut, transcript },
        { merge: true }
      );

      // Solo una llamada completada, con resumen y de al menos MIN_CONVERSATION_SECONDS
      // reemplaza las notas: si no contestó o colgó enseguida, se conservan las de la
      // última conversación real para que la próxima tenga contexto.
      const profileUpdate: Record<string, unknown> = { lastCallAt: endedAt };
      if (status === "COMPLETED" && durationSeconds >= MIN_CONVERSATION_SECONDS && summary.trim()) {
        profileUpdate.lastCallNotes = summary;
      }
      if (optOut) {
        // La persona pidió no volver a recibir llamadas: se pausan hasta que alguien las reactive en Mi cuenta.
        profileUpdate.callEnabled = false;
        profileUpdate.optedOutAt = endedAt;
      }
      await circleRef.set({ callProfile: profileUpdate }, { merge: true });

      logger.info(`retellWebhook: llamada ${call.call_id} guardada para ${circleId} (${status}${alert ? ", ALERTA" : ""})`);

      await notifyFamily(circleRef, historyRef, { status, alert, alertReason, summary, durationSeconds, optOut });

      res.status(200).send("ok");
    } catch (err) {
      logger.error("retellWebhook: error guardando la llamada", err);
      // 500 para que Retell reintente (hasta 3 veces) en vez de darla por perdida.
      res.status(500).send("error");
    }
  }
);

// Límites defensivos: un documento de Firestore admite 1 MB, y una llamada diaria de
// unos minutos queda muy por debajo — esto solo protege de una llamada anormalmente larga.
const MAX_TRANSCRIPT_TURNS = 300;
const MAX_CHARS_PER_TURN = 1500;

/**
 * Convierte la conversación de Retell al formato que lee la app: una lista ordenada de
 * { role: "alicia" | "adulto", text }. Prefiere `transcript_object` (ya viene separado por
 * turnos) y solo si no está parsea el texto plano `transcript`.
 */
function buildTranscript(call: RetellCallObject): TranscriptLine[] {
  const fromObject: TranscriptLine[] = (call.transcript_object ?? [])
    .filter((t) => typeof t.content === "string" && t.content.trim() !== "")
    .map((t) => ({
      role: t.role === "agent" ? ("alicia" as const) : ("adulto" as const),
      text: (t.content as string).trim().slice(0, MAX_CHARS_PER_TURN),
    }));
  if (fromObject.length > 0) return fromObject.slice(0, MAX_TRANSCRIPT_TURNS);

  const lines: TranscriptLine[] = [];
  for (const raw of (call.transcript ?? "").split(/\r?\n/)) {
    const match = /^(Agent|User):\s*(.*)$/i.exec(raw);
    if (match) {
      lines.push({
        role: match[1].toLowerCase() === "agent" ? "alicia" : "adulto",
        text: match[2].trim().slice(0, MAX_CHARS_PER_TURN),
      });
    } else if (lines.length > 0 && raw.trim() !== "") {
      // Continuación de la intervención anterior (texto con saltos de línea).
      const last = lines[lines.length - 1];
      last.text = `${last.text} ${raw.trim()}`.slice(0, MAX_CHARS_PER_TURN);
    }
  }
  return lines.filter((l) => l.text !== "").slice(0, MAX_TRANSCRIPT_TURNS);
}

/**
 * Envía el SMS a la familia, una sola vez por llamada aunque Retell reintente el
 * webhook: se "reclama" el envío en una transacción sobre el registro de la llamada.
 * Un error de SMS no hace fallar el webhook (la llamada ya quedó guardada).
 */
async function notifyFamily(
  circleRef: DocumentReference,
  historyRef: DocumentReference,
  info: { status: ReturnType<typeof resolveCallStatus>; alert: boolean; alertReason: string; summary: string; durationSeconds: number; optOut: boolean }
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
      membersSnap.docs.map((d) => d.data() as { phone?: string; notifyOnPanic?: boolean }),
      (circleSnap.get("smsOptOut") as string[] | undefined) ?? []
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
  const next: Record<string, unknown> = { ...billingFromPreapproval(p, current?.plan ?? snap.get("plan"), Date.now()), ...extra };
  if (p.status === "cancelled" && current?.status !== "cancelled") {
    // Cancelada desde Mercado Pago: si ya había pagado el mes, Alicia sigue hasta el fin de ese periodo.
    next.paidUntil = paidUntilOnCancel(current as { status?: string; nextPaymentDate?: string }, Date.now());
    next.cancelledAt = Date.now();
  }
  await circleRef.set({ billing: next }, { merge: true });
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

/**
 * Botón "Cancelar plan" de callmealicia.com/cuenta.html. Body: { circleId }.
 * Cancela la suscripción en Mercado Pago (si existe) y marca el círculo como
 * cancelado. Si el mes ya estaba pagado, las llamadas siguen hasta que termine.
 */
export const cancelSubscription = onRequest(
  { secrets: [MP_ACCESS_TOKEN], cors: WEB_ORIGINS },
  async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ error: "method" }); return; }
    try {
      const token = (req.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
      if (!token) { res.status(401).json({ error: "auth" }); return; }
      const { uid } = await getAuth().verifyIdToken(token);
      const { circleId } = (req.body ?? {}) as { circleId?: string };
      if (typeof circleId !== "string") { res.status(400).json({ error: "input" }); return; }
      const user = await db.collection("users").doc(uid).get();
      if (user.get("circleId") !== circleId) { logger.warn(`cancelSubscription: ${uid} no pertenece a ${circleId}`); res.status(403).json({ error: "circle" }); return; }

      const circleRef = db.collection("circles").doc(circleId);
      const circle = (await circleRef.get()).data() ?? {};
      const prev = circle.billing as { status?: string; preapprovalId?: string; nextPaymentDate?: string } | undefined;
      if (prev?.status === "cancelled") { res.status(200).json({ status: "cancelled", paidUntil: circle.billing.paidUntil ?? Date.now() }); return; }

      if (prev?.preapprovalId) {
        try {
          await mpFetch(`/preapproval/${encodeURIComponent(prev.preapprovalId)}`, { method: "PUT", body: { status: "cancelled" } });
        } catch (err) {
          // Una suscripción "pending" que nunca se pagó puede no aceptar el cambio: igual se cancela aquí.
          logger.warn(`cancelSubscription: Mercado Pago no canceló ${prev.preapprovalId}`, err);
          if (prev.status === "authorized" || prev.status === "paused") throw err;
        }
      }
      const now = Date.now();
      const paidUntil = paidUntilOnCancel(prev, now);
      await circleRef.set(
        { billing: { status: "cancelled", plan: circle.plan ?? "", provider: "mercadopago", cancelledAt: now, cancelledBy: uid, paidUntil, updatedAt: now } },
        { merge: true }
      );
      logger.info(`cancelSubscription: ${circleId} cancelado (llamadas hasta ${new Date(paidUntil).toISOString()})`);
      res.status(200).json({ status: "cancelled", paidUntil });
    } catch (err) {
      logger.error("cancelSubscription: error", err);
      res.status(500).json({ error: "server" });
    }
  }
);

/**
 * Botón "Eliminar mis datos" de callmealicia.com/cuenta.html. Body: { circleId }.
 * 1) cancela la suscripción en Mercado Pago, 2) pide a Retell borrar las llamadas y el
 * contacto (memoria) de ese número,
 * 3) borra el círculo con todo lo que tiene adentro (historial, miembros…), 4) borra
 * los documentos users/{uid} conectados y sus cuentas de inicio de sesión.
 * Queda solo un registro mínimo en deletions/{circleId} (fecha, sin datos personales).
 */
export const deleteMyData = onRequest(
  { secrets: [MP_ACCESS_TOKEN, RETELL_API_KEY], cors: WEB_ORIGINS, timeoutSeconds: 120 },
  async (req, res) => {
    if (req.method !== "POST") { res.status(405).json({ error: "method" }); return; }
    try {
      const token = (req.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
      if (!token) { res.status(401).json({ error: "auth" }); return; }
      const { uid } = await getAuth().verifyIdToken(token);
      const { circleId } = (req.body ?? {}) as { circleId?: string };
      if (typeof circleId !== "string" || !circleId) { res.status(400).json({ error: "input" }); return; }
      const user = await db.collection("users").doc(uid).get();
      if (user.get("circleId") !== circleId) { logger.warn(`deleteMyData: ${uid} no pertenece a ${circleId}`); res.status(403).json({ error: "circle" }); return; }

      const circleRef = db.collection("circles").doc(circleId);
      const circle = (await circleRef.get()).data() ?? {};

      // 1. Mercado Pago: sin esto podría seguir cobrando después de borrar todo.
      const b = circle.billing as { status?: string; preapprovalId?: string } | undefined;
      if (b?.preapprovalId && b.status !== "cancelled") {
        try {
          await mpFetch(`/preapproval/${encodeURIComponent(b.preapprovalId)}`, { method: "PUT", body: { status: "cancelled" } });
        } catch (err) {
          if (b.status === "authorized" || b.status === "paused") {
            logger.error(`deleteMyData: no se pudo cancelar ${b.preapprovalId}; no se borra nada`, err);
            res.status(502).json({ error: "billing" });
            return;
          }
          logger.warn(`deleteMyData: suscripción pendiente ${b.preapprovalId} no se canceló`, err);
        }
      }

      // 2. Retell: grabaciones y transcripciones (los ids del historial son los call_id).
      const calls = await circleRef.collection("callHistory").listDocuments();
      let retellOk = 0;
      for (const c of calls) {
        try {
          const r = await fetch(`https://api.retellai.com/v2/delete-call/${encodeURIComponent(c.id)}`, {
            method: "DELETE", headers: { Authorization: `Bearer ${RETELL_API_KEY.value()}` },
          });
          if (r.ok || r.status === 404) retellOk++;
          else logger.warn(`deleteMyData: Retell respondió ${r.status} al borrar ${c.id}`);
        } catch (err) { logger.warn(`deleteMyData: error borrando ${c.id} en Retell`, err); }
      }

      // 2b. Retell: la memoria de contacto vive en el "contacto" de ese número de teléfono,
      // aparte de las llamadas. Se busca por número exacto y se borra el contacto.
      let contactsDeleted = 0;
      const phone = (circle.callProfile as { phoneE164?: string } | undefined)?.phoneE164;
      if (phone) {
        try {
          const headers = { Authorization: `Bearer ${RETELL_API_KEY.value()}`, "Content-Type": "application/json" };
          const found = await fetch("https://api.retellai.com/list-contacts", {
            method: "POST", headers,
            body: JSON.stringify({ filter_criteria: { phone_number: { eq: phone } }, limit: 50 }),
          });
          if (!found.ok) {
            logger.warn(`deleteMyData: Retell respondió ${found.status} al buscar el contacto`);
          } else {
            const { items = [] } = (await found.json()) as { items?: { contact_id: string }[] };
            for (const item of items) {
              const r = await fetch(`https://api.retellai.com/delete-contact/${encodeURIComponent(item.contact_id)}`, {
                method: "DELETE", headers: { Authorization: `Bearer ${RETELL_API_KEY.value()}` },
              });
              if (r.ok || r.status === 404) contactsDeleted++;
              else logger.warn(`deleteMyData: Retell respondió ${r.status} al borrar el contacto ${item.contact_id}`);
            }
          }
        } catch (err) { logger.warn("deleteMyData: error borrando el contacto en Retell", err); }
      }

      // 3 y 4. Firestore y cuentas de inicio de sesión.
      const linked = await db.collection("users").where("circleId", "==", circleId).get();
      await db.recursiveDelete(circleRef);
      for (const u of linked.docs) {
        await u.ref.delete();
        try { await getAuth().deleteUser(u.id); } catch (err) { logger.warn(`deleteMyData: no se borró la cuenta ${u.id}`, err); }
      }
      await db.collection("deletions").doc(circleId).set({ deletedAt: Date.now(), calls: calls.length, retellDeleted: retellOk, retellContactsDeleted: contactsDeleted });
      logger.info(`deleteMyData: ${circleId} eliminado (${calls.length} llamadas, ${retellOk} borradas en Retell, ${contactsDeleted} contactos/memoria, ${linked.size} cuentas)`);
      res.status(200).json({ ok: true });
    } catch (err) {
      logger.error("deleteMyData: error", err);
      res.status(500).json({ error: "server" });
    }
  }
);
