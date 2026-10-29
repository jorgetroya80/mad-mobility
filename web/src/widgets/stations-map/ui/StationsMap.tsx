import { useMemo } from 'react';
import { type Coordinates, type Station, useAllStations } from '@/entities/station';
import { useNeed } from '@/features/filter-by-need';
import { useSelectedStation } from '@/features/select-station';
import { BaseMap } from '@/shared/ui';
import { toMapPoints } from '../lib/toMapPoints';

type StationsMapProps = {
  center: Coordinates | undefined;
  userLocation: Coordinates | undefined;
  focusPadding?: { bottom: number };
  // False keeps the selected station marked but centers the map on `center` instead.
  focusSelected?: boolean;
  // Called on every tapped point, also the station already selected, which does not change the URL.
  onSelect?: (number: string) => void;
  className?: string;
};

// Module-level so the map keeps the same array while the stations load or fail.
const NO_STATIONS: readonly Station[] = [];

// `?station` may differ in case from the API's number (`1A` vs `1a`).
function findStation(stations: readonly Station[], number: string | undefined) {
  if (number === undefined) return undefined;
  const wanted = number.toLowerCase();
  return stations.find((station) => station.number.toLowerCase() === wanted);
}

// The map never blocks on the stations: the list shows loading and errors.
export function StationsMap({
  center,
  userLocation,
  focusPadding,
  focusSelected = true,
  onSelect,
  className,
}: StationsMapProps) {
  const { data } = useAllStations();
  const [need] = useNeed();
  const { number, select } = useSelectedStation();
  const stations = data?.stations ?? NO_STATIONS;
  // Stable while stations and need stay the same, so the map only resets its data on real changes.
  const points = useMemo(() => toMapPoints(stations, need), [stations, need]);
  const selected = findStation(stations, number);

  return (
    <BaseMap
      className={className}
      points={points}
      selectedId={selected?.number}
      onSelectPoint={(id) => {
        onSelect?.(id);
        select(id);
      }}
      focus={selected && focusSelected ? { lng: selected.lon, lat: selected.lat } : center}
      focusPadding={focusPadding}
      userLocation={userLocation}
    />
  );
}
