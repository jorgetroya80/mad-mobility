import {
  type AvailabilityLevel,
  availabilityFor,
  availabilityLabel,
  type Need,
  type Station,
} from '@/entities/station';
import type { MapPoint, MapPointTone } from '@/shared/ui';

type MapStation = Pick<Station, 'number' | 'lat' | 'lon' | 'status' | 'bikes' | 'freeDocks'>;

// Same names today, but the map's tones belong to `shared` and must not follow the entity
// silently: a new level fails to compile here instead of reaching the map unstyled.
const TONE_BY_LEVEL = {
  good: 'good',
  low: 'low',
  empty: 'empty',
  off: 'off',
} as const satisfies Record<AvailabilityLevel, MapPointTone>;

function toMapPoint(station: MapStation, need: Need): MapPoint {
  const availability = availabilityFor(station, need);
  return {
    id: station.number,
    lng: station.lon,
    lat: station.lat,
    label: availabilityLabel(availability),
    tone: TONE_BY_LEVEL[availability.level],
  };
}

export function toMapPoints(stations: readonly MapStation[], need: Need): MapPoint[] {
  return stations.map((station) => toMapPoint(station, need));
}
