import { nearbyStationsFixture, stationsFixture } from '@/shared/api/mocks/fixtures';
import { NEARBY_STORAGE_KEYS, STATIONS_STORAGE_KEY } from '@/shared/config';
import { writeRaw } from '@/shared/lib';
import {
  readAllStations,
  readNearbyStations,
  saveAllStations,
  saveNearbyStations,
} from './stationsCache';

const NOW = new Date('2026-10-31T10:00:00Z');
const NEAR = '40.4170,-3.7024';
const KEYS = [STATIONS_STORAGE_KEY, NEARBY_STORAGE_KEYS.bikes, NEARBY_STORAGE_KEYS.docks];

const [firstStation] = stationsFixture.stations;

function store(key: string, value: unknown) {
  localStorage.setItem(key, JSON.stringify(value));
}

describe('stationsCache', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    // A failed write keeps an in-memory copy per key: a successful one drops it before clearing.
    for (const key of KEYS) writeRaw(key, 'null');
    localStorage.clear();
  });

  it('reads nothing when nothing is saved', () => {
    expect(readAllStations()).toBeUndefined();
    expect(readNearbyStations('bikes')).toBeUndefined();
  });

  it('parses the same saved list only once', () => {
    saveAllStations(stationsFixture);
    const parse = vi.spyOn(JSON, 'parse');

    const first = readAllStations();
    const second = readAllStations();

    expect(second).toBe(first);
    expect(parse).toHaveBeenCalledTimes(1);
  });

  it('parses the list again once a new one is saved', () => {
    saveAllStations(stationsFixture);
    const first = readAllStations();

    vi.setSystemTime(new Date(NOW.getTime() + 30_000));
    saveAllStations(stationsFixture);

    expect(readAllStations()).not.toBe(first);
    expect(readAllStations()?.savedAt).toBe(NOW.getTime() + 30_000);
  });

  it('saves the full list with the time it was saved', () => {
    saveAllStations(stationsFixture);

    expect(readAllStations()).toEqual({ response: stationsFixture, savedAt: NOW.getTime() });
  });

  it('saves the nearby list of each need with its center', () => {
    saveNearbyStations('docks', NEAR, nearbyStationsFixture);

    expect(readNearbyStations('docks')).toEqual({
      near: NEAR,
      response: nearbyStationsFixture,
      savedAt: NOW.getTime(),
    });
    expect(readNearbyStations('bikes')).toBeUndefined();
  });

  it('ignores corrupt JSON', () => {
    localStorage.setItem(STATIONS_STORAGE_KEY, '{not json');
    localStorage.setItem(NEARBY_STORAGE_KEYS.bikes, '{not json');

    expect(readAllStations()).toBeUndefined();
    expect(readNearbyStations('bikes')).toBeUndefined();
  });

  const valid = { response: stationsFixture, savedAt: NOW.getTime() };
  const withStation = (station: unknown) => ({
    ...valid,
    response: { ...stationsFixture, stations: [station] },
  });

  it.each([
    ['null', null],
    ['an array', []],
    ['a missing savedAt', { response: stationsFixture }],
    ['a missing response', { savedAt: NOW.getTime() }],
    ['stations that are not a list', { ...valid, response: { ...stationsFixture, stations: {} } }],
    [
      'an updatedAt that is not a string',
      { ...valid, response: { ...stationsFixture, updatedAt: 1 } },
    ],
    [
      'a stale flag that is not a boolean',
      { ...valid, response: { ...stationsFixture, stale: 'no' } },
    ],
    ['a station that is not an object', withStation('1a')],
    ['a count that is not a number', withStation({ ...firstStation, bikes: '12' })],
    ['an unknown station status', withStation({ ...firstStation, status: 'CLOSED' })],
    ['an unknown occupancy', withStation({ ...firstStation, occupancy: 'FULL' })],
    ['a distance that is not a number', withStation({ ...firstStation, distanceMeters: '50' })],
    ['a name that is not a string', withStation({ ...firstStation, name: null })],
  ])('ignores a full list with %s', (_case, stored) => {
    store(STATIONS_STORAGE_KEY, stored);

    expect(readAllStations()).toBeUndefined();
  });

  it('ignores a nearby list without its center', () => {
    store(NEARBY_STORAGE_KEYS.bikes, { response: nearbyStationsFixture, savedAt: NOW.getTime() });

    expect(readNearbyStations('bikes')).toBeUndefined();
  });

  it('does not throw when the storage refuses the write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Quota exceeded', 'QuotaExceededError');
    });

    expect(() => saveAllStations(stationsFixture)).not.toThrow();
    expect(() => saveNearbyStations('bikes', NEAR, nearbyStationsFixture)).not.toThrow();
  });
});
