/**
 * Reglas de cada plan — lógica pura, para poder probarla aparte. La usa
 * `dailyCallScheduler` en index.ts. Deben coincidir con lo que prometen la
 * página de precios y los Términos (callmealicia.com/terminos.html).
 *
 *   Esencial → lunes, miércoles y viernes, hasta 8 min
 *   Diario   → todos los días, hasta 5 min
 *   Plus     → todos los días, hasta 10 min
 *
 * Círculos sin plan (app Android, piloto) → todos los días, sin límite propio
 * (queda el máximo configurado en el agente de Retell).
 */

export interface PlanRule {
  /** Días de la semana en que se llama, 0 = domingo … 6 = sábado. */
  days: number[];
  /** Duración prometida en minutos (Alicia se despide antes de llegar). */
  minutes: number;
}

const EVERY_DAY = [0, 1, 2, 3, 4, 5, 6];

export const PLAN_RULES: Record<string, PlanRule> = {
  esencial: { days: [1, 3, 5], minutes: 8 },
  diario: { days: EVERY_DAY, minutes: 5 },
  plus: { days: EVERY_DAY, minutes: 10 },
};

export function planRule(plan: unknown): PlanRule | null {
  return typeof plan === "string" && PLAN_RULES[plan] ? PLAN_RULES[plan] : null;
}

/** ¿Le toca llamada hoy (día de la semana local) según su plan? */
export function isCallDay(plan: unknown, weekday: number): boolean {
  const rule = planRule(plan);
  return rule ? rule.days.includes(weekday) : true;
}

/**
 * Corte automático de la llamada: los minutos del plan + 1 de margen, para que
 * Alicia alcance a despedirse sin que la llamada se corte en mitad de una frase.
 * null = sin plan, se usa el máximo del agente.
 */
export function maxCallDurationMs(plan: unknown): number | null {
  const rule = planRule(plan);
  return rule ? (rule.minutes + 1) * 60_000 : null;
}
