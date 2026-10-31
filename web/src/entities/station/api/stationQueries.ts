import {
  keepPreviousData,
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
    queryFn: fetchAllStations,
    retry: 0,
    refetchInterval: pollInterval,
  });
  return useRestartingRefetch(result, queryKey);
}

// Switching need or center keeps the previous rows on screen until the new ones arrive,
// instead of flashing back to loading.
export function useNearbyStations(center: Coordinates | undefined, need: Need) {
  const queryKey = stationKeys.nearby(center, need);
  const result = useQuery({
    queryKey,
    queryFn: center
      ? ({ signal }) => fetchNearbyStations({ near: toNearParam(center), need, signal })
      : skipToken,
    placeholderData: keepPreviousData,
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
