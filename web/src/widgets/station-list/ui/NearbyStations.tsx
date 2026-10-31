import type { Need } from '@/entities/station';
import { NEARBY_RADIUS_M } from '@/shared/config';
import { StateMessage, UpdatedAt } from '@/shared/ui';
import type { NearbyList } from '../model/useNearbyList';
import { StationRows } from './StationRows';

type NearbyStationsProps = {
  list: NearbyList;
  need: Need;
  onSelect: (number: string) => void;
};

export function NearbyStations({ list, need, onSelect }: NearbyStationsProps) {
  const { stations } = list;
  if (list.status === 'loading') {
    return <StateMessage variant="loading" title="Cargando estaciones cercanas…" />;
  }
  // Offline without data looks like an error until web-error-states gives it its own text.
  if (stations === undefined) {
    return (
      <StateMessage variant="error" title="No se pudieron cargar las estaciones cercanas">
        <button
          type="button"
          onClick={list.retry}
          className="min-h-11 rounded-control px-3 font-medium underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          Reintentar
        </button>
      </StateMessage>
    );
  }
  return (
    <>
      {list.updatedAt ? <UpdatedAt date={list.updatedAt} /> : null}
      {stations.length === 0 ? (
        <StateMessage variant="empty" title={`Ninguna estación en ${NEARBY_RADIUS_M} m`} />
      ) : (
        <StationRows stations={stations} need={need} onSelect={onSelect} />
      )}
    </>
  );
}
