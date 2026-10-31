import type { StationsResponse } from '@jorgetroya80/mad-transit-client';
import { NEARBY_STORAGE_KEYS, STATIONS_STORAGE_KEY } from '@/shared/config';
import { readRaw, writeRaw } from '@/shared/lib';
import type { Need } from '../model/types';

type CachedStations = { response: StationsResponse; savedAt: number };
type CachedNearby = CachedStations & { near: string };

const STATUSES: readonly unknown[] = ['OPERATIONAL', 'NO_SERVICE'];
const OCCUPANCIES: readonly unknown[] = ['LOW', 'MEDIUM', 'HIGH', 'UNKNOWN'];
const STRING_FIELDS = ['number', 'name', 'address'] as const;
const NUMBER_FIELDS = ['id', 'lat', 'lon', 'bikes', 'freeDocks', 'totalDocks'] as const;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

// Storage is untrusted (another version of the app, a user, an extension): check every field the
// UI reads before rendering it.
function isStation(value: unknown): boolean {
  if (!isRecord(value)) return false;
  return (
    STRING_FIELDS.every((field) => typeof value[field] === 'string') &&
    NUMBER_FIELDS.every((field) => typeof value[field] === 'number') &&
    STATUSES.includes(value.status) &&
    OCCUPANCIES.includes(value.occupancy) &&
    (value.distanceMeters === undefined || typeof value.distanceMeters === 'number')
  );
}

function isStationsResponse(value: unknown): value is StationsResponse {
  return (
    isRecord(value) &&
    Array.isArray(value.stations) &&
    value.stations.every(isStation) &&
    typeof value.updatedAt === 'string' &&
    typeof value.stale === 'boolean' &&
    typeof value.source === 'string'
  );
}

function parseCached(raw: unknown): CachedStations | undefined {
  if (!isRecord(raw) || typeof raw.savedAt !== 'number') return undefined;
  if (!isStationsResponse(raw.response)) return undefined;
  return { response: raw.response, savedAt: raw.savedAt };
}

function parseNearby(raw: unknown): CachedNearby | undefined {
  const cached = parseCached(raw);
  if (!cached || !isRecord(raw) || typeof raw.near !== 'string') return undefined;
  return { ...cached, near: raw.near };
}

// The last text read per key and what it parsed to. The full list is ~600 stations and is read
// more than once at start (initialData and its date), so the same text is parsed only once.
const lastReadByKey = new Map<string, { raw: string; value: unknown }>();

function parseRaw<T>(raw: string, parse: (raw: unknown) => T | undefined): T | undefined {
  try {
    return parse(JSON.parse(raw));
  } catch {
    return undefined;
  }
}

function read<T>(key: string, parse: (raw: unknown) => T | undefined): T | undefined {
  const raw = readRaw(key);
  if (raw === null) return undefined;
  const last = lastReadByKey.get(key);
  if (last?.raw === raw) return last.value as T | undefined;
  const value = parseRaw(raw, parse);
  lastReadByKey.set(key, { raw, value });
  return value;
}

export function readAllStations(): CachedStations | undefined {
  return read(STATIONS_STORAGE_KEY, parseCached);
}

export function readNearbyStations(need: Need): CachedNearby | undefined {
  return read(NEARBY_STORAGE_KEYS[need], parseNearby);
}

// writeRaw swallows a refused write (private mode, quota), so saving never fails a query.
export function saveAllStations(response: StationsResponse): void {
  const cached: CachedStations = { response, savedAt: Date.now() };
  writeRaw(STATIONS_STORAGE_KEY, JSON.stringify(cached));
}

export function saveNearbyStations(need: Need, near: string, response: StationsResponse): void {
  const cached: CachedNearby = { near, response, savedAt: Date.now() };
  writeRaw(NEARBY_STORAGE_KEYS[need], JSON.stringify(cached));
}
