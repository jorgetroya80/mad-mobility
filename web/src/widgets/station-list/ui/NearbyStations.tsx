import type { Need } from '@/entities/station';
import { NEARBY_RADIUS_M } from '@/shared/config';
import { PanelNotice, StateMessage, UpdatedAt } from '@/shared/ui';
import type { NearbyList } from '../model/useNearbyList';
import { StationRows } from './StationRows';

type NearbyStationsProps = {
  list: NearbyList;
  need: Need;
  onSelect: (number: string) => void;
};

export function NearbyStations({ list, need, onSelect }: NearbyStationsProps) {
  if (list.status === 'loading') {
    return <StateMessage variant="loading" title="Cargando estaciones cercanas…" />;
  }
  if (list.status === 'error') {
    return (
      <StateMessage variant="error" title="No se pudieron cargar las estaciones cercanas">
        <button
          type="button"
          onClick={list.retry}
          className="min-h-11 rounded-md px-3 font-medium underline focus-visible:outline-2 focus-visible:outline-sky-700"
        >
          Reintentar
        </button>
      </StateMessage>
    );
  }
  return (
    <>
      {list.refreshFailed ? <PanelNotice>No se pudo actualizar</PanelNotice> : null}
      {list.updatedAt ? <UpdatedAt date={list.updatedAt} /> : null}
      {list.stations.length === 0 ? (
        <StateMessage variant="empty" title={`Ninguna estación en ${NEARBY_RADIUS_M} m`} />
      ) : (
        <StationRows stations={list.stations} need={need} onSelect={onSelect} />
      )}
    </>
  );
}
