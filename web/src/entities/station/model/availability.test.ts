import { availabilityFor } from './availability';
import type { AvailabilityLevel, Need, Station } from './types';

type StationCounts = Pick<Station, 'status' | 'bikes' | 'freeDocks'>;

function operational(bikes: number, freeDocks: number): StationCounts {
  return { status: 'OPERATIONAL', bikes, freeDocks };
}

describe('availabilityFor', () => {
  type Case = { need: Need; station: StationCounts; level: AvailabilityLevel; count: number };

  const cases: Case[] = [
    { need: 'bikes', station: operational(0, 20), level: 'empty', count: 0 },
    { need: 'bikes', station: operational(4, 20), level: 'low', count: 4 },
    { need: 'bikes', station: operational(5, 0), level: 'good', count: 5 },
    { need: 'docks', station: operational(20, 0), level: 'empty', count: 0 },
    { need: 'docks', station: operational(20, 4), level: 'low', count: 4 },
    { need: 'docks', station: operational(0, 5), level: 'good', count: 5 },
  ];

  it.each(cases)('$need with $count → $level', ({ need, station, level, count }) => {
    expect(availabilityFor(station, need)).toEqual({ level, count });
  });

  it.each<Need>(['bikes', 'docks'])(
    'reports a NO_SERVICE station as off for %s, keeping the count',
    (need) => {
      const station: StationCounts = { status: 'NO_SERVICE', bikes: 12, freeDocks: 7 };
      const count = need === 'bikes' ? 12 : 7;
      expect(availabilityFor(station, need)).toEqual({ level: 'off', count });
    },
  );
});
