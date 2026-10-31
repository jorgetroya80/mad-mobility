import type { Coordinates, DataStatus, Station } from '@/entities/station';
import { useNeed } from '@/features/filter-by-need';
import { FavoriteButton } from '@/features/toggle-favorite';
import { PanelNotice, StaleNotice, UpdatedAt } from '@/shared/ui';
import { useStationDistance } from '../model/useStationDistance';
import { StationFields } from './StationFields';

type StationInfoProps = {
  station: Station;
  status: DataStatus;
  updatedAt: Date | undefined;
  center: Coordinates | undefined;
};

export function StationInfo({ station, status, updatedAt, center }: StationInfoProps) {
  const [need] = useNeed();
  const distanceMeters = useStationDistance(center, need, station.number);

  return (
    <>
      {status === 'stale' ? <StaleNotice updatedAt={updatedAt} /> : null}
      {status === 'offline' ? <PanelNotice>Sin conexión</PanelNotice> : null}
      <StationFields station={station} distanceMeters={distanceMeters} />
      <FavoriteButton number={station.number} className="self-start" />
      {updatedAt ? <UpdatedAt date={updatedAt} /> : null}
    </>
  );
}
