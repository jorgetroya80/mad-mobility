import { type Station, useAllStations } from '@/entities/station';
import type { SearchItem, SearchSource } from '@/features/search-by-number';
import { useSelectedStation } from '@/features/select-station';
import { useMemo } from 'react';

const BICIMAD_SOURCE_ID = 'bicimad';
const BICIMAD_LABEL = 'BiciMAD';
const STATION_LABEL = 'estación';
const STATION_LABEL_PLURAL = 'estaciones';
const NO_ITEMS: readonly SearchItem[] = [];

type StationsQuery = { data?: unknown; isError: boolean };

// Stations from an earlier success stay searchable even if a later refresh failed.
function sourceStatus({ data, isError }: StationsQuery): SearchSource['status'] {
  if (data) return 'ready';
  return isError ? 'error' : 'loading';
}

function toSearchItem({ number, name, address }: Station): SearchItem {
  return { number, title: name, subtitle: address };
}

export function useBicimadSource(): SearchSource {
  const { data, isError } = useAllStations();
  const { select } = useSelectedStation();
  const stations = data?.stations;
  const status = sourceStatus({ data, isError });

  const items = useMemo(() => stations?.map(toSearchItem) ?? NO_ITEMS, [stations]);

  // Stable while nothing changes, so polling with identical data does not re-render the search.
  return useMemo(
    () => ({
      id: BICIMAD_SOURCE_ID,
      label: BICIMAD_LABEL,
      itemLabel: STATION_LABEL,
      itemLabelPlural: STATION_LABEL_PLURAL,
      items,
      status,
      onSelect: select,
    }),
    [items, status, select],
  );
}
