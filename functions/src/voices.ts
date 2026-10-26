/**
 * Voz (acento) de Alicia según el país de la persona — lógica pura.
 *
 * RETELL_VOICES en functions/.env: lista "PAÍS=voice_id" separada por comas, p. ej.
 *   RETELL_VOICES=CO=11labs-Xxxx,MX=11labs-Yyyy,PE=11labs-Zzzz
 * Los voice_id se copian de Retell → Voices. Si un país no está en la lista, se usa
 * la voz configurada en el agente (como hasta ahora).
 *
 * El país sale primero de "País de origen" (lo que la familia escribió: así una mamá
 * colombiana que vive en Miami escucha acento colombiano) y, si está vacío o no se
 * reconoce, del indicativo de su celular.
 */

const DIAL_TO_COUNTRY: Array<[string, string]> = [
  ["+593", "EC"], ["+502", "GT"], ["+503", "SV"], ["+504", "HN"], ["+505", "NI"], ["+506", "CR"], ["+507", "PA"],
  ["+57", "CO"], ["+52", "MX"], ["+51", "PE"], ["+58", "VE"], ["+56", "CL"], ["+54", "AR"], ["+34", "ES"], ["+1", "US"],
];

const NAME_TO_COUNTRY: Record<string, string> = {
  colombia: "CO", mexico: "MX", peru: "PE", ecuador: "EC", venezuela: "VE", chile: "CL", argentina: "AR",
  guatemala: "GT", "el salvador": "SV", salvador: "SV", honduras: "HN", nicaragua: "NI", "costa rica": "CR",
  panama: "PA", espana: "ES", spain: "ES", "estados unidos": "US", "united states": "US", usa: "US", "ee uu": "US", eeuu: "US",
  "puerto rico": "PR", cuba: "CU", "republica dominicana": "DO", dominicana: "DO", bolivia: "BO", paraguay: "PY", uruguay: "UY",
};

function norm(s: string): string {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z ]/g, " ").replace(/\s+/g, " ").trim();
}

export function countryOf(originCountry: string | undefined, phoneE164: string | undefined): string {
  const n = norm(originCountry ?? "");
  if (n) {
    if (NAME_TO_COUNTRY[n]) return NAME_TO_COUNTRY[n];
    const hit = Object.keys(NAME_TO_COUNTRY).find((k) => n.includes(k));
    if (hit) return NAME_TO_COUNTRY[hit];
  }
  const p = phoneE164 ?? "";
  return DIAL_TO_COUNTRY.find(([code]) => p.startsWith(code))?.[1] ?? "";
}

export function parseVoices(raw: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of (raw ?? "").split(",")) {
    const [k, ...v] = part.split("=");
    const key = (k ?? "").trim().toUpperCase(), val = v.join("=").trim();
    if (key && val) out[key] = val;
  }
  return out;
}

export function voiceFor(raw: string, originCountry: string | undefined, phoneE164: string | undefined): string | null {
  const c = countryOf(originCountry, phoneE164);
  return (c && parseVoices(raw)[c]) || null;
}
