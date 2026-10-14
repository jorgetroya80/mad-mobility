import { NEAR_COORDINATE_DECIMALS } from '@/shared/config';
import type { Need } from '../model/types';

export type Coordinates = { lat: number; lng: number };

// Key and request share this value, so a cached list always matches what was asked for.
export function toNearParam({ lat, lng }: Coordinates): string {
  return `${lat.toFixed(NEAR_COORDINATE_DECIMALS)},${lng.toFixed(NEAR_COORDINATE_DECIMALS)}`;
}

export const stationKeys = {
  all: () => ['stations', 'all'] as const,
  nearby: (center: Coordinates | undefined, need: Need) =>
    ['stations', 'nearby', { near: center && toNearParam(center), need }] as const,
  detail: (number: string | undefined) => ['stations', 'detail', number] as const,
};
