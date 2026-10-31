import {
  type DataStatus,
  type Station,
  StationNotFoundError,
  useAllStations,
  useDataStatus,
  useStation,
} from '@/entities/station';

export type StationDetailState =
  | { status: 'not-found' }
  | {
      status: DataStatus;
      // Undefined without data from the detail or the full list.
      station: Station | undefined;
      updatedAt: Date | undefined;
      retry: () => void;
    };

// `?station` may differ in case from the API's number (`1A` vs `1a`).
function findStation(stations: readonly Station[] | undefined, number: string) {
  const wanted = number.toLowerCase();
  return stations?.find((station) => station.number.toLowerCase() === wanted);
}

// Not found wins over any data: a station the API no longer has should not stay on screen.
// Without its own data (loading, failed or offline), the detail falls back to the station in the
// full list, possibly the saved one, with that list's status. Retry always asks for the detail.
export function useStationDetail(number: string): StationDetailState {
  const detail = useStation(number);
  const own = useDataStatus(detail);
  const all = useAllStations();
  const list = useDataStatus(all);

  if (detail.error instanceof StationNotFoundError) return { status: 'not-found' };
  if (detail.data) return { ...own, station: detail.data.station };
  const listed = findStation(all.data?.stations, number);
  if (listed)
    return { status: list.status, updatedAt: list.updatedAt, station: listed, retry: own.retry };
  return { ...own, station: undefined };
}
