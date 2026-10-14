const METERS_PER_KILOMETER = 1000;

const metersFormat = new Intl.NumberFormat('es', { maximumFractionDigits: 0 });
const kilometersFormat = new Intl.NumberFormat('es', { maximumFractionDigits: 1 });

export function formatDistance(meters: number): string {
  // Round first so 999.6 m reads "1 km", not "1000 m".
  const roundedMeters = Math.round(meters);
  if (roundedMeters < METERS_PER_KILOMETER) return `${metersFormat.format(roundedMeters)} m`;
  return `${kilometersFormat.format(roundedMeters / METERS_PER_KILOMETER)} km`;
}
