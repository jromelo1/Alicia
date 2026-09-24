/**
 * Cloud Functions de Círculo de Confianza — Llamada diaria de Alicia.
 *
 * Dos piezas:
 *  1. `dailyCallScheduler` — corre cada 15 minutos, revisa qué círculos tienen la
 *     llamada activada y a quién le toca ahora según su hora local preferida, y le
 *     pide a Retell que llame.
 *  2. `retellWebhook` — recibe el resultado de la llamada (resumen, duración,
 *     estado) cuando Retell termina de analizarla, y lo guarda en Firestore para
 *     que la app lo muestre y para que la siguiente llamada tenga contexto.
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
import * as admin from "firebase-admin";
import { Retell } from "retell-sdk";

admin.initializeApp();
const db = admin.firestore();

const RETELL_API_KEY = defineSecret("RETELL_API_KEY");
const RETELL_AGENT_ID = defineString("RETELL_AGENT_ID");
const RETELL_FROM_NUMBER = defineString("RETELL_FROM_NUMBER");

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
}

interface RetellCallAnalysis {
  call_summary?: string;
  user_sentiment?: string;
  in_voicemail?: boolean;
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
  { secrets: [RETELL_API_KEY] },
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
      const status = resolveCallStatus(call);

      const circleRef = db.collection("circles").doc(circleId);
      await circleRef.collection("callHistory").doc(call.call_id).set({
        startedAt,
        endedAt,
        durationSeconds,
        summary,
        status,
      });
      await circleRef.set(
        {
          callProfile: {
            lastCallNotes: summary,
            lastCallAt: endedAt,
          },
        },
        { merge: true }
      );

      logger.info(`retellWebhook: llamada ${call.call_id} guardada para ${circleId} (${status})`);
      res.status(200).send("ok");
    } catch (err) {
      logger.error("retellWebhook: error guardando la llamada", err);
      // 500 para que Retell reintente (hasta 3 veces) en vez de darla por perdida.
      res.status(500).send("error");
    }
  }
);

/**
 * Clasificación best-effort del resultado de la llamada — Retell no documenta
 * un enum cerrado de `disconnection_reason` con el mismo nivel de detalle que el
 * resto de la API. Si en los logs aparecen valores no cubiertos aquí, ampliar
 * esta función (ver logger.info de abajo, que siempre deja el valor crudo).
 */
function resolveCallStatus(call: RetellCallObject): "COMPLETED" | "NO_ANSWER" | "FAILED" | "IN_VOICEMAIL" | "UNKNOWN" {
  const reason = (call.disconnection_reason ?? "").toLowerCase();
  logger.info(`resolveCallStatus: disconnection_reason="${reason}"`);

  if (call.call_analysis?.in_voicemail) return "IN_VOICEMAIL";
  if (reason.includes("no_answer") || reason.includes("busy")) return "NO_ANSWER";
  if (reason.includes("error") || reason.includes("failed") || reason.includes("dial_failed")) return "FAILED";
  if (reason.includes("hangup") || reason === "" || reason.includes("agent_end")) return "COMPLETED";
  return "UNKNOWN";
}
