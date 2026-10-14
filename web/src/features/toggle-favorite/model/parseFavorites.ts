// Station numbers are kept exactly as the API gives them ("25A" and "25a" are different):
// the stored values come from the API, so normalizing could stop matching it.
export function parseFavorites(raw: unknown): readonly string[] | undefined {
  if (!Array.isArray(raw)) return undefined;
  if (!raw.every((item) => typeof item === 'string')) return undefined;
  return [...new Set<string>(raw)];
}
