import {
  AvailabilityBadge,
  availabilityFor,
  formatDistance,
  type Station,
} from '@/entities/station';
import { DetailField } from './DetailField';

type FieldsStation = Pick<Station, 'address' | 'status' | 'bikes' | 'freeDocks' | 'totalDocks'>;

type StationFieldsProps = { station: FieldsStation; distanceMeters: number | undefined };

const STATUS_TEXT = {
  OPERATIONAL: 'En servicio',
  NO_SERVICE: 'Sin servicio',
} as const satisfies Record<Station['status'], string>;

// Bikes and free docks reuse the availability badge, so they never rely on color alone.
export function StationFields({ station, distanceMeters }: StationFieldsProps) {
  const bikes = availabilityFor(station, 'bikes');
  const docks = availabilityFor(station, 'docks');

  return (
    <dl className="divide-y divide-line">
      <DetailField term="Dirección">{station.address}</DetailField>
      {distanceMeters === undefined ? null : (
        <DetailField term="Distancia">{formatDistance(distanceMeters)}</DetailField>
      )}
      <DetailField term="Estado">{STATUS_TEXT[station.status]}</DetailField>
      <DetailField term="Bicis">
        <AvailabilityBadge level={bikes.level} count={bikes.count} need="bikes" />
      </DetailField>
      <DetailField term="Anclajes libres">
        <AvailabilityBadge level={docks.level} count={docks.count} need="docks" />
      </DetailField>
      <DetailField term="Anclajes totales">{station.totalDocks}</DetailField>
    </dl>
  );
}
