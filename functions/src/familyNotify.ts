/**
 * Avisos a la familia después de cada llamada de Alicia — lógica pura (sin
 * Firebase), para poder probarla aparte. La usa `retellWebhook` en index.ts.
 *
 * Tres tipos de aviso, por SMS (Twilio):
 *  - ALERT   — Retell marcó la llamada con una señal de riesgo (campo de análisis
 *              post-llamada `alerta` = true). Siempre se envía.
 *  - MISSED  — la persona no contestó (o entró el buzón). Siempre se envía.
 *  - SUMMARY — llamada completada sin alerta: resumen corto. Se puede apagar con
 *              el parámetro FAMILY_SMS_SUMMARIES=false.
 * Las fallas técnicas de nuestro lado (sin saldo en Retell, errores de red…) NO se
 * le avisan a la familia — solo quedan en los logs, para no alarmar por un
 * problema que no es de su ser querido.
 */

export type CallStatus = "COMPLETED" | "NO_ANSWER" | "FAILED" | "IN_VOICEMAIL" | "UNKNOWN";
export type NoticeKind = "ALERT" | "MISSED" | "SUMMARY" | "OPTOUT";
export type Lang = "es" | "en";

export interface CallAnalysisLike {
  call_summary?: string;
  in_voicemail?: boolean;
  custom_analysis_data?: Record<string, unknown>;
}

/** Valores de `disconnection_reason` según la referencia de la API de Retell (get-call). */
const COMPLETED_REASONS = new Set([
  "user_hangup", "agent_hangup", "inactivity", "max_duration_reached", "call_transfer",
  "transfer_bridged", "transfer_cancelled", "user_requested_dnc", "user_requested_callback",
  "manual_stopped", "call_take_over",
]);
const NO_ANSWER_REASONS = new Set(["dial_no_answer", "dial_busy", "user_declined", "ivr_reached", "marked_as_spam"]);
const VOICEMAIL_REASONS = new Set(["voicemail_reached"]);

export function resolveCallStatus(reasonRaw: string | undefined, analysis?: CallAnalysisLike): CallStatus {
  const reason = (reasonRaw ?? "").toLowerCase();
  if (analysis?.in_voicemail || VOICEMAIL_REASONS.has(reason)) return "IN_VOICEMAIL";
  if (NO_ANSWER_REASONS.has(reason)) return "NO_ANSWER";
  if (COMPLETED_REASONS.has(reason)) return "COMPLETED";
  if (reason === "") return "COMPLETED";
  // dial_failed, invalid_destination, telephony_*, error_*, credit_exhausted, etc.
  if (/fail|error|invalid|telephony|sip_|network|concurrency|payment|credit|budget|timeout|scam/.test(reason)) return "FAILED";
  return "UNKNOWN";
}

/** Lee la alerta del análisis post-llamada. Acepta `alerta`/`motivo_alerta` (o `alert`/`alert_reason`). */
export function readAlert(analysis?: CallAnalysisLike): { alert: boolean; reason: string } {
  const d = analysis?.custom_analysis_data ?? {};
  const raw = d["alerta"] ?? d["alert"];
  const alert = raw === true || (typeof raw === "string" && ["true", "sí", "si", "yes"].includes(raw.trim().toLowerCase()));
  const reasonRaw = d["motivo_alerta"] ?? d["alert_reason"];
  const reason = typeof reasonRaw === "string" ? reasonRaw.trim() : "";
  return { alert, reason };
}

/**
 * ¿La persona pidió en la llamada que Alicia no la vuelva a llamar? Campo de análisis
 * post-llamada `no_llamar` (o `opt_out`) = true. Si es así, las llamadas se pausan.
 */
export function readOptOut(analysis?: CallAnalysisLike): boolean {
  const d = analysis?.custom_analysis_data ?? {};
  const raw = d["no_llamar"] ?? d["opt_out"];
  return raw === true || (typeof raw === "string" && ["true", "sí", "si", "yes"].includes(raw.trim().toLowerCase()));
}

/** Pie de cada SMS con el enlace para darse de baja. */
export function withUnsubscribe(body: string, lang: Lang): string {
  return body + (lang === "en" ? " Stop: callmealicia.com/baja" : " Baja: callmealicia.com/baja");
}

function clip(text: string, max: number): string {
  const t = text.replace(/\s+/g, " ").trim();
  return t.length <= max ? t : t.slice(0, max - 1).trimEnd() + "…";
}

/** Primer nombre, o el trato si lo hay ("Doña Rosa"), para que el SMS suene natural. */
export function displayName(fullName?: string, treatment?: string): string {
  if (treatment && treatment.trim()) return treatment.trim();
  const first = (fullName ?? "").trim().split(/\s+/)[0];
  return first || "tu ser querido";
}

export function buildNotice(args: {
  status: CallStatus;
  alert: boolean;
  alertReason: string;
  summary: string;
  durationSeconds: number;
  name: string;
  lang: Lang;
  summariesEnabled: boolean;
  optOut?: boolean;
}): { kind: NoticeKind; body: string } | null {
  const n = buildNoticeRaw(args);
  return n ? { kind: n.kind, body: withUnsubscribe(n.body, args.lang) } : null;
}

function buildNoticeRaw(args: {
  status: CallStatus; alert: boolean; alertReason: string; summary: string; durationSeconds: number;
  name: string; lang: Lang; summariesEnabled: boolean; optOut?: boolean;
}): { kind: NoticeKind; body: string } | null {
  const { status, alert, alertReason, summary, durationSeconds, name, lang } = args;
  const es = lang !== "en";

  if (alert && status === "COMPLETED") {
    const why = alertReason ? clip(alertReason, 220) : (es ? "una situación que conviene revisar" : "something worth checking on");
    return {
      kind: "ALERT",
      body: es
        ? `Call Me Alicia — IMPORTANTE: en la llamada de hoy, ${name} mencionó: ${why}. Te sugerimos comunicarte pronto. Si crees que es una emergencia, llama a los servicios de emergencia.`
        : `Call Me Alicia — IMPORTANT: on today's call, ${name} mentioned: ${why}. We suggest reaching out soon. If you think it's an emergency, call emergency services.`,
    };
  }

  if (args.optOut) {
    return {
      kind: "OPTOUT",
      body: es
        ? `Call Me Alicia: ${name} pidió que Alicia no la vuelva a llamar, así que pausamos las llamadas. Si cambia de opinión, puedes reactivarlas en callmealicia.com/cuenta.`
        : `Call Me Alicia: ${name} asked Alicia not to call again, so we paused the calls. If they change their mind, you can turn them back on at callmealicia.com/cuenta.`,
    };
  }

  if (status === "NO_ANSWER" || status === "IN_VOICEMAIL") {
    return {
      kind: "MISSED",
      body: es
        ? `Call Me Alicia: hoy Alicia no pudo hablar con ${name} (no contestó). Quizás quieras llamarle para saber cómo está.`
        : `Call Me Alicia: Alicia couldn't reach ${name} today (no answer). You may want to give them a call.`,
    };
  }

  if (status === "COMPLETED" && args.summariesEnabled && summary.trim()) {
    const minutes = Math.max(1, Math.round(durationSeconds / 60));
    return {
      kind: "SUMMARY",
      body: es
        ? `Call Me Alicia: hoy Alicia habló ${minutes} min con ${name}. Resumen: ${clip(summary, 320)}`
        : `Call Me Alicia: Alicia talked with ${name} for ${minutes} min today. Summary: ${clip(summary, 320)}`,
    };
  }

  return null;
}

const DIAL_CODES = ["+593", "+502", "+503", "+504", "+505", "+506", "+507", "+57", "+52", "+51", "+1"];

/** Indicativo del país de un número E.164 (de la lista de países de la web), o "" si no se reconoce. */
export function dialCodeOf(e164: string): string {
  return DIAL_CODES.find((c) => e164.startsWith(c)) ?? "";
}

/**
 * Lleva un número guardado por la app o la web a E.164. Los contactos agregados
 * desde la agenda del teléfono pueden venir sin "+" ni indicativo ("310 123 4567"):
 * en ese caso se asume el mismo país del adulto mayor. Devuelve "" si no es usable.
 */
export function toE164(raw: string, defaultDialCode: string): string {
  const s = (raw ?? "").trim();
  if (!s) return "";
  if (s.startsWith("+")) {
    const digits = s.slice(1).replace(/\D/g, "");
    return digits.length >= 8 ? "+" + digits : "";
  }
  let digits = s.replace(/\D/g, "");
  if (digits.startsWith("00")) return digits.length >= 10 ? "+" + digits.slice(2) : "";
  if (!defaultDialCode) return "";
  const cc = defaultDialCode.slice(1);
  if (digits.startsWith(cc) && digits.length > cc.length + 7) digits = digits.slice(cc.length);
  return digits.length >= 7 ? defaultDialCode + digits : "";
}

/** Contacto de emergencia + miembros con avisos activados, sin repetidos y sin el número del propio adulto mayor. */
export function collectRecipients(
  elderPhone: string,
  emergencyPhone: string | undefined,
  members: Array<{ phone?: string; notifyOnPanic?: boolean }>,
  optedOut: string[] = []
): string[] {
  const cc = dialCodeOf(elderPhone);
  const out = new Set<string>();
  const blocked = new Set(optedOut);
  const add = (p?: string) => {
    const n = toE164(p ?? "", cc);
    if (n && n !== elderPhone && !blocked.has(n)) out.add(n);
  };
  add(emergencyPhone);
  members.filter((m) => m.notifyOnPanic !== false).forEach((m) => add(m.phone));
  return [...out];
}

/** Envía un SMS por la API REST de Twilio (sin SDK). Lanza error si Twilio responde con error. */
export async function sendTwilioSms(
  cfg: { accountSid: string; authToken: string; from: string },
  to: string,
  body: string
): Promise<void> {
  const params = new URLSearchParams({ To: to, Body: body });
  // TWILIO_FROM puede ser un número (+1…) o un Messaging Service SID (MG…).
  if (cfg.from.startsWith("MG")) params.set("MessagingServiceSid", cfg.from);
  else params.set("From", cfg.from);
  const res = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${cfg.accountSid}/Messages.json`, {
    method: "POST",
    headers: {
      Authorization: "Basic " + Buffer.from(`${cfg.accountSid}:${cfg.authToken}`).toString("base64"),
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: params.toString(),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`Twilio respondió ${res.status}: ${text}`);
  }
}
