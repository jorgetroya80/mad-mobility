import type { Coordinates } from '@/entities/station';
import { useSelectedStation } from '@/features/select-station';
import { SelectedStationDetail } from './SelectedStationDetail';

type StationDetailProps = {
  // Center of the nearby list: the distance is read from it. Undefined hides the distance.
  center: Coordinates | undefined;
};

export function StationDetail({ center }: StationDetailProps) {
  const { number } = useSelectedStation();

  if (number === undefined) return null;
  // Remounts per station, so the title takes the focus again when another one opens.
  return <SelectedStationDetail key={number} number={number} center={center} />;
}
