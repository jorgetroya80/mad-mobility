import { keepPreviousData, skipToken, useQuery } from '@tanstack/react-query';
import { POLL_INTERVAL_MS, QUERY_RETRY_COUNT } from '@/shared/config';
import type { Need } from '../model/types';
import { fetchAllStations, fetchNearbyStations, fetchStation } from './fetchStations';
import { StationNotFoundError } from './StationNotFoundError';
import { type Coordinates, stationKeys, toNearParam } from './stationKeys';

export function useAllStations() {
  return useQuery({
    queryKey: stationKeys.all(),
    queryFn: fetchAllStations,
    refetchInterval: POLL_INTERVAL_MS,
  });
}

// Switching need or center keeps the previous rows on screen until the new ones arrive,
// instead of flashing back to loading.
export function useNearbyStations(center: Coordinates | undefined, need: Need) {
  return useQuery({
    queryKey: stationKeys.nearby(center, need),
    queryFn: center
      ? ({ signal }) => fetchNearbyStations({ near: toNearParam(center), need, signal })
      : skipToken,
    placeholderData: keepPreviousData,
    refetchInterval: POLL_INTERVAL_MS,
  });
}

// A missing station will not appear on retry; anything else keeps the app's retry count.
function retryUnlessNotFound(failureCount: number, error: Error): boolean {
  return !(error instanceof StationNotFoundError) && failureCount < QUERY_RETRY_COUNT;
}

export function useStation(number: string | undefined) {
  return useQuery({
    queryKey: stationKeys.detail(number),
    queryFn: number ? ({ signal }) => fetchStation({ number, signal }) : skipToken,
    retry: retryUnlessNotFound,
    refetchInterval: POLL_INTERVAL_MS,
  });
}
