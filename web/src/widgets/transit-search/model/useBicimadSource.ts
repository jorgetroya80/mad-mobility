import { type DataStatus, type Station, useAllStations, useDataStatus } from '@/entities/station';
import type { SearchItem, SearchSource } from '@/features/search-by-number';
import { useSelectedStation } from '@/features/select-station';
import { useMemo } from 'react';

const BICIMAD_SOURCE_ID = 'bicimad';
const BICIMAD_LABEL = 'BiciMAD';
const STATION_LABEL = 'estación';
const STATION_LABEL_PLURAL = 'estaciones';
const NO_ITEMS: readonly SearchItem[] = [];

// Stations from an earlier success or the saved list stay searchable whatever the status.
// Without them, offline reads as an error like a failed request.
function sourceStatus(hasData: boolean, status: DataStatus): SearchSource['status'] {
  if (hasData) return 'ready';
  return status === 'loading' ? 'loading' : 'error';
}

function toSearchItem({ number, name, address }: Station): SearchItem {
  return { number, title: name, subtitle: address };
}

export function useBicimadSource(): SearchSource {
  const query = useAllStations();
  const { status: dataStatus } = useDataStatus(query);
  const { select } = useSelectedStation();
  const stations = query.data?.stations;
  const status = sourceStatus(stations !== undefined, dataStatus);

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
