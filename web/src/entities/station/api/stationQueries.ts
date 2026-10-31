import type { StationsResponse } from '@jorgetroya80/mad-transit-client';
import {
  type PlaceholderDataFunction,
  type QueryKey,
  type QueryState,
  skipToken,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from '@tanstack/react-query';
import { POLL_INTERVAL_MS, RETRY_DELAYS_MS } from '@/shared/config';
import type { Need } from '../model/types';
import { fetchAllStations, fetchNearbyStations, fetchStation } from './fetchStations';
import { type Coordinates, stationKeys, toNearParam } from './stationKeys';
import {
  readAllStations,
  readNearbyStations,
  saveAllStations,
  saveNearbyStations,
} from './stationsCache';

type PacedQuery = { state: Pick<QueryState, 'status' | 'errorUpdateCount'> };

// TanStack Query has no counter of consecutive failures that survives a refetch: fetchFailureCount
// starts again at every fetch and errorUpdateCount never resets. So this keeps the
// errorUpdateCount at which the wait last restarted (a success or a manual retry) per query.
const waitStart = new WeakMap<PacedQuery, number>();

function consecutiveFailures(query: PacedQuery): number {
  const { status, errorUpdateCount } = query.state;
  if (status === 'success') waitStart.set(query, errorUpdateCount);
  return errorUpdateCount - (waitStart.get(query) ?? 0);
}

// Every 30 s while the requests succeed; after a failure, 30, 60 and then 120 s until one succeeds.
// The interval restarts on every query update, so each wait counts from the last answer.
function pollInterval(query: PacedQuery): number {
  const failures = consecutiveFailures(query);
  if (failures === 0) return POLL_INTERVAL_MS;
  return RETRY_DELAYS_MS[Math.min(failures, RETRY_DELAYS_MS.length) - 1] ?? POLL_INTERVAL_MS;
}

// «Reintentar» asks at once and restarts the wait at 30 s even if that request fails too. A Proxy
// swaps refetch without reading every field, which would defeat TanStack's tracked re-renders.
function useRestartingRefetch<TData>(
  result: UseQueryResult<TData>,
  queryKey: QueryKey,
): UseQueryResult<TData> {
  const queryClient = useQueryClient();
  const refetch: typeof result.refetch = (options) => {
    const query = queryClient.getQueryCache().find({ queryKey, exact: true });
    if (query) waitStart.set(query, query.state.errorUpdateCount);
    return result.refetch(options);
  };
  return new Proxy(result, {
    get: (target, property, receiver) =>
      property === 'refetch' ? refetch : Reflect.get(target, property, receiver),
  });
}

// Failures are not retried at once: pollInterval paces the next attempt. A missing station
// follows the same pace, so it is asked for again slower and slower.
export function useAllStations() {
  const queryKey = stationKeys.all();
  const result = useQuery({
    queryKey,
    queryFn: async ({ signal }) => {
      const response = await fetchAllStations({ signal });
      saveAllStations(response);
      return response;
    },
    // Read once, when the query is created. The save time makes an old cache stale, so it is
    // asked for again on mount.
    initialData: () => readAllStations()?.response,
    initialDataUpdatedAt: () => readAllStations()?.savedAt,
    retry: 0,
    refetchInterval: pollInterval,
  });
  return useRestartingRefetch(result, queryKey);
}

async function fetchAndSaveNearby(
  center: Coordinates,
  need: Need,
  signal: AbortSignal,
): Promise<StationsResponse> {
  const near = toNearParam(center);
  const response = await fetchNearbyStations({ near, need, signal });
  saveNearbyStations(need, near, response);
  return response;
}

// Module-level so TanStack keeps the placeholder between renders instead of reading storage
// again. The previous rows of the same hook win; the saved list only fills an empty start.
type NearbyKey = ReturnType<typeof stationKeys.nearby>;

const nearbyPlaceholders: Record<
  Need,
  PlaceholderDataFunction<StationsResponse, Error, StationsResponse, NearbyKey>
> = {
  bikes: (previous) => previous ?? readNearbyStations('bikes')?.response,
  docks: (previous) => previous ?? readNearbyStations('docks')?.response,
};

// Switching need or center keeps the previous rows on screen until the new ones arrive,
// instead of flashing back to loading. On a fresh start, the last saved list of the need fills
// in, even for another center.
export function useNearbyStations(center: Coordinates | undefined, need: Need) {
  const queryKey = stationKeys.nearby(center, need);
  const result = useQuery({
    queryKey,
    queryFn: center ? ({ signal }) => fetchAndSaveNearby(center, need, signal) : skipToken,
    placeholderData: nearbyPlaceholders[need],
    retry: 0,
    refetchInterval: pollInterval,
  });
  return useRestartingRefetch(result, queryKey);
}

export function useStation(number: string | undefined) {
  const queryKey = stationKeys.detail(number);
  const result = useQuery({
    queryKey,
    queryFn: number ? ({ signal }) => fetchStation({ number, signal }) : skipToken,
    retry: 0,
    refetchInterval: pollInterval,
  });
  return useRestartingRefetch(result, queryKey);
}
