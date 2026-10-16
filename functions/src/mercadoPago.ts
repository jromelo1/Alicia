/**
 * Cobro mensual con Mercado Pago Suscripciones — lógica pura (sin Firebase), para
 * poder probarla aparte. La usan `createSubscription` y `mercadoPagoWebhook` en index.ts.
 *
 * Flujo:
 *  1. La web, justo después del registro, llama a `createSubscription`. Esa función
 *     crea una suscripción "pendiente" (POST /preapproval, sin plan asociado) con
 *     external_reference = código del círculo, y devuelve su `init_point`: la página
 *     de Mercado Pago donde la familia pone su tarjeta.
 *  2. El primer cobro se programa para el día en que termina la prueba gratis
 *     (auto_recurring.start_date), así no se cobra nada durante los 7 días.
 *  3. Mercado Pago avisa cada cambio a `mercadoPagoWebhook`; esa función verifica la
 *     firma, consulta la suscripción y guarda el estado en circles/{id}.billing.
 */

export type PlanId = "esencial" | "diario" | "plus";
export const PLAN_IDS: PlanId[] = ["esencial", "diario", "plus"];
export const TRIAL_DAYS = 7;
const DAY_MS = 24 * 60 * 60 * 1000;

export const MP_API = "https://api.mercadopago.com";

export function isPlanId(p: unknown): p is PlanId {
  return typeof p === "string" && (PLAN_IDS as string[]).includes(p);
}

/** Fin de la prueba gratis a partir de trialStartedAt (ms). */
export function trialEndsAt(trialStartedAt: number | undefined): number {
  return (trialStartedAt ?? 0) + TRIAL_DAYS * DAY_MS;
}

/**
 * ¿Puede Alicia seguir llamando a este círculo?
 *  - Círculos creados desde la app Android o sin plan (piloto) → siempre sí.
 *  - Con plan elegido en la web → sí durante la prueba, o si la suscripción está autorizada.
 */
type BillingLike = { status?: string; paidUntil?: number };

/**
 * Plan cancelado y ya pasó lo pagado → no se llama más, aunque BILLING_ENFORCE
 * esté apagado (cancelar siempre detiene las llamadas). Si se cancela un mes ya
 * pagado, Alicia sigue llamando hasta `paidUntil`.
 */
export function cancelledAndExpired(circle: { billing?: BillingLike }, nowMs: number): boolean {
  const b = circle.billing;
  return b?.status === "cancelled" && nowMs >= (b.paidUntil ?? 0);
}

/** Hasta cuándo puede seguir llamando un plan que se cancela ahora. */
export function paidUntilOnCancel(prev: { status?: string; nextPaymentDate?: string } | undefined, nowMs: number): number {
  if (prev?.status === "authorized" && prev.nextPaymentDate) {
    const t = Date.parse(prev.nextPaymentDate);
    if (Number.isFinite(t) && t > nowMs) return t;
  }
  return nowMs; // prueba gratis o sin pagar → se detiene ya
}

export function canCall(circle: { source?: string; plan?: string; trialStartedAt?: number; billing?: BillingLike }, nowMs: number): boolean {
  if (cancelledAndExpired(circle, nowMs)) return false;
  if (circle.billing?.status === "cancelled") return true; // mes ya pagado, sigue hasta paidUntil
  if (circle.source !== "web" || !circle.plan) return true;
  if (nowMs < trialEndsAt(circle.trialStartedAt)) return true;
  return circle.billing?.status === "authorized";
}

const PLAN_NAMES: Record<PlanId, { es: string; en: string }> = {
  esencial: { es: "Esencial", en: "Essential" },
  diario: { es: "Diario", en: "Daily" },
  plus: { es: "Plus", en: "Plus" },
};

/** Cuerpo del POST /preapproval para una suscripción mensual sin plan asociado. */
export function buildPreapprovalBody(args: {
  circleId: string;
  plan: PlanId;
  amount: number;
  currency: string;
  payerEmail: string;
  backUrl: string;
  trialStartedAt?: number;
  lang: "es" | "en";
  nowMs: number;
}): Record<string, unknown> {
  const name = PLAN_NAMES[args.plan][args.lang];
  const auto: Record<string, unknown> = {
    frequency: 1,
    frequency_type: "months",
    transaction_amount: args.amount,
    currency_id: args.currency,
  };
  // Primer cobro al terminar la prueba. Si la prueba ya terminó (o termina en
  // minutos), no se manda start_date y Mercado Pago cobra el primer mes de una vez.
  const end = trialEndsAt(args.trialStartedAt);
  if (end > args.nowMs + 30 * 60 * 1000) auto.start_date = new Date(end).toISOString();
  return {
    reason: args.lang === "en" ? `Call Me Alicia — ${name} plan` : `Call Me Alicia — Plan ${name}`,
    external_reference: args.circleId,
    payer_email: args.payerEmail,
    back_url: args.backUrl,
    status: "pending",
    auto_recurring: auto,
  };
}

export function isEmail(s: unknown): s is string {
  return typeof s === "string" && s.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s);
}

/** Lee "ts=…,v1=…" del header x-signature. */
export function parseSignatureHeader(h: string | undefined): { ts: string; v1: string } | null {
  if (!h) return null;
  let ts = "", v1 = "";
  for (const part of h.split(",")) {
    const [k, ...rest] = part.split("=");
    const key = (k ?? "").trim(), val = rest.join("=").trim();
    if (key === "ts") ts = val;
    else if (key === "v1") v1 = val;
  }
  return ts && v1 ? { ts, v1 } : null;
}

/**
 * Texto que Mercado Pago firma: "id:[data.id];request-id:[x-request-id];ts:[ts];"
 * (data.id en minúsculas si es alfanumérico; se omite cualquier parte que no venga).
 */
export function signatureManifest(dataId: string, requestId: string, ts: string): string {
  let m = "";
  if (dataId) m += `id:${/^[a-z0-9]+$/i.test(dataId) ? dataId.toLowerCase() : dataId};`;
  if (requestId) m += `request-id:${requestId};`;
  if (ts) m += `ts:${ts};`;
  return m;
}

/** Estado que guardamos en circles/{id}.billing a partir de la suscripción de Mercado Pago. */
export function billingFromPreapproval(p: {
  id?: string; status?: string; next_payment_date?: string; payer_email?: string;
  auto_recurring?: { transaction_amount?: number; currency_id?: string };
}, plan: string | undefined, nowMs: number): Record<string, unknown> {
  return {
    provider: "mercadopago",
    preapprovalId: p.id ?? "",
    status: p.status ?? "unknown", // pending | authorized | paused | cancelled
    plan: plan ?? "",
    amount: p.auto_recurring?.transaction_amount ?? 0,
    currency: p.auto_recurring?.currency_id ?? "",
    nextPaymentDate: p.next_payment_date ?? "",
    updatedAt: nowMs,
  };
}
