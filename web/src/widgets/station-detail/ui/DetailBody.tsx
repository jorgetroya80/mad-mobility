import type { Coordinates } from '@/entities/station';
import { useSelectedStation } from '@/features/select-station';
import { StateMessage } from '@/shared/ui';
import type { StationDetailState } from '../model/useStationDetail';
import { MessageAction } from './MessageAction';
import { StationInfo } from './StationInfo';

type DetailBodyProps = { detail: StationDetailState; center: Coordinates | undefined };

export function DetailBody({ detail, center }: DetailBodyProps) {
  const { clear } = useSelectedStation();

  if (detail.status === 'loading') {
    return <StateMessage variant="loading" title="Cargando estación…" />;
  }
  if (detail.status === 'not-found') {
    return (
      <StateMessage variant="error" title="Estación no encontrada">
        <MessageAction onClick={clear}>Volver a la lista</MessageAction>
      </StateMessage>
    );
  }
  // Offline without data looks like an error until web-error-states gives it its own text.
  if (detail.station === undefined) {
    return (
      <StateMessage variant="error" title="No se pudo cargar la estación">
        <MessageAction onClick={detail.retry}>Reintentar</MessageAction>
      </StateMessage>
    );
  }
  return (
    <StationInfo
      station={detail.station}
      status={detail.status}
      updatedAt={detail.updatedAt}
      center={center}
    />
  );
}
