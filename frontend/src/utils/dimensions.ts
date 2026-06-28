// Sprint 10.122: honest dimension-constraint detection. The catalog has no product dimensions, so we cannot
// filter by exact size ("a sofa max 180 cm wide", "a table no longer than 120 cm"). Instead of silently
// ignoring the constraint and returning a plan that may not fit, we show a fixed, localized note telling the
// user to check the measurements on the product page. DETERMINISTIC keyword check (not an AI paragraph).
//
// High precision on purpose: room AREA ("30 m²", "30 kvadrata") is NOT a furniture dimension and must NOT
// trigger; the cm form ("180 cm", "120cm", "160x230 cm") covers the overwhelming majority of real requests.

export function detectDimensionConstraint(prompt: string | undefined | null): boolean {
  if (!prompt) return false;
  const text = prompt.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
  // a centimetre measurement in a furniture prompt is essentially always a size request/constraint
  if (/\d{2,3}\s*cm\b/.test(text)) return true;
  // "max/maks/do/ispod/bis/under/up to/fino a 1,8 m" — metres, but NOT m² / kvadrat (room area)
  if (/\b(max|maks|maksimal|do|ispod|bis|under|up\s*to|fino\s*a|jusqu)\w*\s*\d([.,]\d)?\s*m\b/.test(text)
      && !/m2|m²|kvadrat|sqm|square/.test(text)) {
    return true;
  }
  return false;
}
