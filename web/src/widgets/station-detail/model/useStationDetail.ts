import { parseUpdatedAt, type Station, StationNotFoundError, useStation } from '@/entities/station';

export type StationDetailState =
  | { status: 'loading' }
  | { status: 'not-found' }
  | { status: 'error'; retry: () => void }
  | {
      status: 'ready';
      station: Station;
      updatedAt: Date | undefined;
      stale: boolean;
      refreshFailed: boolean;
    };

// Not found wins over previous data: a station the API no longer has should not stay on screen.
// Any other failed refresh keeps the previous data, so `refreshFailed` only adds a notice.
export function useStationDetail(number: string): StationDetailState {
  const { data, error, refetch } = useStation(number);

  if (error instanceof StationNotFoundError) return { status: 'not-found' };
  if (data) {
    return {
      status: 'ready',
      station: data.station,
      updatedAt: parseUpdatedAt(data.updatedAt),
      stale: data.stale,
      refreshFailed: error !== null,
    };
  }
  if (error) return { status: 'error', retry: () => void refetch() };
  return { status: 'loading' };
}
