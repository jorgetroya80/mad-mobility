import { availabilityFor, availabilityLabel } from './availability';
import type { AvailabilityLevel, DataStatus, Need, Station } from './types';

type StationCounts = Pick<Station, 'status' | 'bikes' | 'freeDocks'>;

function operational(bikes: number, freeDocks: number): StationCounts {
  return { status: 'OPERATIONAL', bikes, freeDocks };
}

describe('availabilityFor', () => {
  type Case = { need: Need; station: StationCounts; level: AvailabilityLevel; count: number };

  const cases: Case[] = [
    { need: 'bikes', station: operational(0, 20), level: 'empty', count: 0 },
    { need: 'bikes', station: operational(1, 20), level: 'low', count: 1 },
    { need: 'bikes', station: operational(4, 20), level: 'low', count: 4 },
    { need: 'bikes', station: operational(5, 0), level: 'good', count: 5 },
    { need: 'docks', station: operational(20, 0), level: 'empty', count: 0 },
    { need: 'docks', station: operational(20, 4), level: 'low', count: 4 },
    { need: 'docks', station: operational(0, 5), level: 'good', count: 5 },
  ];

  it.each(cases)('$need with $count → $level', ({ need, station, level, count }) => {
    expect(availabilityFor(station, need, 'ok')).toEqual({ level, count });
  });

  it.each<Need>(['bikes', 'docks'])(
    'reports a NO_SERVICE station as off for %s, keeping the count',
    (need) => {
      const station: StationCounts = { status: 'NO_SERVICE', bikes: 12, freeDocks: 7 };
      const count = need === 'bikes' ? 12 : 7;
      expect(availabilityFor(station, need, 'ok')).toEqual({ level: 'off', count });
    },
  );

  // Unconfirmed comes first: the count may be wrong whatever it says, even "out of service".
  it.each<{ status: DataStatus; station: StationCounts }>([
    { status: 'stale', station: operational(0, 20) },
    { status: 'stale', station: operational(4, 20) },
    { status: 'stale', station: operational(5, 20) },
    { status: 'offline', station: operational(1, 20) },
    { status: 'stale', station: { status: 'NO_SERVICE', bikes: 3, freeDocks: 7 } },
    { status: 'offline', station: { status: 'NO_SERVICE', bikes: 3, freeDocks: 7 } },
  ])(
    'reports $station.bikes bikes as unconfirmed when $status, keeping the count',
    ({ status, station }) => {
      expect(availabilityFor(station, 'bikes', status)).toEqual({
        level: 'unconfirmed',
        count: station.bikes,
      });
    },
  );
});

describe('availabilityLabel', () => {
  it.each<AvailabilityLevel>(['good', 'low', 'empty'])('shows the count when %s', (level) => {
    expect(availabilityLabel({ level, count: 3 })).toBe('3');
  });

  it('shows the count when unconfirmed, even of a station out of service', () => {
    expect(availabilityLabel({ level: 'unconfirmed', count: 3 })).toBe('3');
  });

  it('shows a dash instead of the count when off', () => {
    expect(availabilityLabel({ level: 'off', count: 7 })).toBe('–');
  });
});
