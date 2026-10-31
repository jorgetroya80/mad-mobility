import {
  type Coordinates,
  type DataStatus,
  type Need,
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
// saved list fills in.
export function useNearbyList(center: Coordinates | undefined, need: Need): NearbyList {
  const query = useNearbyStations(center, need);
  const { status, updatedAt, retry } = useDataStatus(query);
  return { status, stations: query.data?.stations, updatedAt, retry };
}
