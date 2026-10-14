import { LOW_AVAILABILITY_THRESHOLD } from '@/shared/config';
import type { AvailabilityLevel, Need, Station } from './types';

type Availability = { level: AvailabilityLevel; count: number };

export function availabilityFor(
  station: Pick<Station, 'status' | 'bikes' | 'freeDocks'>,
  need: Need,
): Availability {
  const count = need === 'bikes' ? station.bikes : station.freeDocks;
  if (station.status === 'NO_SERVICE') return { level: 'off', count };
  if (count === 0) return { level: 'empty', count };
  if (count < LOW_AVAILABILITY_THRESHOLD) return { level: 'low', count };
  return { level: 'good', count };
}

const OFF_LABEL = '–';

// The visible number of a station, shared by the list badge and the map point. An off
// station's count is meaningless, so it shows a dash.
export function availabilityLabel({ level, count }: Availability): string {
  return level === 'off' ? OFF_LABEL : String(count);
}
