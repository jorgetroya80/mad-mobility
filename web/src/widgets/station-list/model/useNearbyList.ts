import {
  type Coordinates,
  type Need,
  parseUpdatedAt,
  type Station,
  useNearbyStations,
} from '@/entities/station';

export type NearbyList =
  | { status: 'loading' }
  | { status: 'error'; retry: () => void }
  | {
      status: 'ready';
      stations: readonly Station[];
      updatedAt: Date | undefined;
      stale: boolean;
      refreshFailed: boolean;
    };

// Without a center (location pending) the query stays idle, which reads as loading. A failed
// refresh keeps the previous data, so `refreshFailed` only adds a notice on top of it.
export function useNearbyList(center: Coordinates | undefined, need: Need): NearbyList {
  const { data, isError, refetch } = useNearbyStations(center, need);

  if (data) {
    return {
      status: 'ready',
      stations: data.stations,
      updatedAt: parseUpdatedAt(data.updatedAt),
      stale: data.stale,
      refreshFailed: isError,
    };
  }
  if (isError) return { status: 'error', retry: () => void refetch() };
  return { status: 'loading' };
}
