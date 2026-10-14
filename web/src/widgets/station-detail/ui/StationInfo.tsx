import type { Coordinates, Station } from '@/entities/station';
import { useNeed } from '@/features/filter-by-need';
import { FavoriteButton } from '@/features/toggle-favorite';
import { PanelNotice, StaleNotice, UpdatedAt } from '@/shared/ui';
import { useStationDistance } from '../model/useStationDistance';
import { StationFields } from './StationFields';

type StationInfoProps = {
  station: Station;
  updatedAt: Date | undefined;
  stale: boolean;
  refreshFailed: boolean;
  center: Coordinates | undefined;
};

export function StationInfo({
  station,
  updatedAt,
  stale,
  refreshFailed,
  center,
}: StationInfoProps) {
  const [need] = useNeed();
  const distanceMeters = useStationDistance(center, need, station.number);

  return (
    <>
      {stale ? <StaleNotice updatedAt={updatedAt} /> : null}
      {refreshFailed ? <PanelNotice>No se pudo actualizar</PanelNotice> : null}
      <StationFields station={station} distanceMeters={distanceMeters} />
      <FavoriteButton number={station.number} className="self-start" />
      {updatedAt ? <UpdatedAt date={updatedAt} /> : null}
    </>
  );
}
