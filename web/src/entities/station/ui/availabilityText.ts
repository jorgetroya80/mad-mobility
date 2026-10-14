import type { AvailabilityLevel, Need } from '../model/types';

const NOUNS = {
  bikes: { one: 'bici', other: 'bicis' },
  docks: { one: 'anclaje libre', other: 'anclajes libres' },
} as const satisfies Record<Need, { one: string; other: string }>;

const OFF_TEXT = 'Sin servicio';

export function availabilityText(level: AvailabilityLevel, count: number, need: Need): string {
  if (level === 'off') return OFF_TEXT;
  const noun = count === 1 ? NOUNS[need].one : NOUNS[need].other;
  return `${count} ${noun}`;
}
