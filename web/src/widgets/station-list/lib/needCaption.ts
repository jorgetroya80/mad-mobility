import type { Need } from '@/entities/station';

const CAPTIONS = {
  bikes: 'Bicis disponibles',
  docks: 'Anclajes libres',
} as const satisfies Record<Need, string>;

export function needCaption(need: Need): string {
  return CAPTIONS[need];
}
