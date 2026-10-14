import type { Need } from '@/entities/station';

export type MapSearch = { station?: string; need: Need };

function parseStation(value: unknown): string | undefined {
  return typeof value === 'string' && value !== '' ? value : undefined;
}

function parseNeed(value: unknown): Need {
  return value === 'docks' ? 'docks' : 'bikes';
}

export function validateMapSearch(raw: Record<string, unknown>): MapSearch {
  const station = parseStation(raw.station);
  const need = parseNeed(raw.need);
  return station === undefined ? { need } : { station, need };
}
