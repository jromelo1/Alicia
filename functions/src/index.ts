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
import { getFirestore, DocumentReference } from "firebase-admin/firestore";
import { Retell } from "retell-sdk";
import {
  buildNotice, collectRecipients, displayName, readAlert, resolveCallStatus, sendTwilioSms, Lang,
} from "./familyNotify";

initializeApp();
const db = getFirestore();

const RETELL_API_KEY = defineSecret("RETELL_API_KEY");
const RETELL_AGENT_ID = defineString("RETELL_AGENT_ID");
const RETELL_FROM_NUMBER = defineString("RETELL_FROM_NUMBER");

// Avisos a la familia por SMS (Twilio). Si TWILIO_ACCOUNT_SID queda vacío, no se
// envía nada y todo lo demás funciona igual (solo queda un log).
const TWILIO_ACCOUNT_SID = defineString("TWILIO_ACCOUNT_SID", { default: "" });
const TWILIO_FROM = defineString("TWILIO_FROM", { default: "" });
const TWILIO_AUTH_TOKEN = defineSecret("TWILIO_AUTH_TOKEN");
// "false" para enviar solo alertas y llamadas no contestadas (sin el resumen diario).
const FAMILY_SMS_SUMMARIES = defineString("FAMILY_SMS_SUMMARIES", { default: "true" });

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

    await Promise.all(
      snapshot.docs.map(async (doc) => {
        const circleId = doc.id;
        const profile = doc.data().callProfile as CallProfile | undefined;
        if (!profile || !profile.phoneE164 || !profile.timeZoneId) {
          logger.warn(`dailyCallScheduler: perfil incompleto en ${circleId}, se salta`);
          return;
        }

        const { hour, minute, dateStr } = localTimeParts(profile.timeZoneId, nowMs);
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
          await createRetellCall(circleId, profile);
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

async function createRetellCall(circleId: string, profile: CallProfile): Promise<void> {
  const response = await fetch(RETELL_CREATE_CALL_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${RETELL_API_KEY.value()}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from_number: RETELL_FROM_NUMBER.value(),
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
      },
    }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new Error(`Retell respondió ${response.status}: ${body}`);
  }
}

/** Hora y fecha local de un timeZone de Android (IANA), a partir de un instante en ms. */
function localTimeParts(timeZoneId: string, atMs: number): { hour: number; minute: number; dateStr: string } {
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: timeZoneId,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = Object.fromEntries(
    formatter.formatToParts(new Date(atMs)).map((p) => [p.type, p.value])
  );
  return {
    hour: parseInt(parts.hour === "24" ? "0" : parts.hour, 10),
    minute: parseInt(parts.minute, 10),
    dateStr: `${parts.year}-${parts.month}-${parts.day}`,
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
