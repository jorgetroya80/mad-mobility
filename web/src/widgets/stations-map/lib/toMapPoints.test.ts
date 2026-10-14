import type { Station } from '@/entities/station';
import { stationsFixture } from '@/shared/api/mocks/fixtures';
import { toMapPoints } from './toMapPoints';

const { stations } = stationsFixture;

describe('toMapPoints', () => {
  it('keeps one point per station, identified by its number at its coordinates', () => {
    const points = toMapPoints(stations, 'bikes');

    expect(points.map(({ id, lng, lat }) => ({ id, lng, lat }))).toEqual(
      stations.map(({ number, lon, lat }) => ({ id: number, lng: lon, lat })),
    );
  });

  it('shows the bikes and their availability when looking for bikes', () => {
    const points = toMapPoints(stations, 'bikes');

    expect(points.map(({ id, label, tone }) => ({ id, label, tone }))).toEqual([
      { id: '1a', label: '12', tone: 'good' },
      { id: '1b', label: '2', tone: 'low' },
      { id: '2', label: '–', tone: 'off' },
      { id: '3', label: '0', tone: 'empty' },
    ]);
  });

  it('shows the free docks and their availability when looking for docks', () => {
    const points = toMapPoints(stations, 'docks');

    expect(points.map(({ id, label, tone }) => ({ id, label, tone }))).toEqual([
      { id: '1a', label: '12', tone: 'good' },
      { id: '1b', label: '22', tone: 'good' },
      { id: '2', label: '–', tone: 'off' },
      { id: '3', label: '18', tone: 'good' },
    ]);
  });

  it('shows a dash, not the count, for a station out of service', () => {
    const offStations: Station[] = stations
      .slice(0, 1)
      .map((station) => ({ ...station, status: 'NO_SERVICE', bikes: 7 }));

    expect(toMapPoints(offStations, 'bikes')).toEqual([
      expect.objectContaining({ label: '–', tone: 'off' }),
    ]);
  });

  it('returns no points without stations', () => {
    expect(toMapPoints([], 'bikes')).toEqual([]);
  });
});
