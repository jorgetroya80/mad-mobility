import {
  type Coordinates,
  type DataStatus,
  type Need,
  readNearbyStations,
  type Station,
  useDataStatus,
  useNearbyStations,
} from '@/entities/station';

export type NearbyList = {
  status: DataStatus;
  // Undefined without data, which is not the same as an empty list within the radius.
  stations: readonly Station[] | undefined;
  updatedAt: Date | undefined;
  retry: () => void;
};

// Without a center (location pending) the query stays idle, which reads as loading unless a
// saved list fills in. TanStack drops that saved list (placeholder data) once the request
// fails, so it is read again here: the list stays on screen, unconfirmed, instead of an error.
export function useNearbyList(center: Coordinates | undefined, need: Need): NearbyList {
  const query = useNearbyStations(center, need);
  const { isError, dataUpdatedAt, refetch } = query;
  const data = query.data ?? (isError ? readNearbyStations(need)?.response : undefined);
  const { status, updatedAt, retry } = useDataStatus({ data, isError, dataUpdatedAt, refetch });
  return { status, stations: data?.stations, updatedAt, retry };
}
