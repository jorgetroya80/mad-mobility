import type { Need } from '@/entities/station';
import { useFavoriteStations } from '../model/useFavoriteStations';
import { StationRows } from './StationRows';
import { StationSection } from './StationSection';

type FavoriteStationsProps = { need: Need; onSelect: (number: string) => void };

export function FavoriteStations({ need, onSelect }: FavoriteStationsProps) {
  const stations = useFavoriteStations();

  if (stations.length === 0) return null;
  return (
    <StationSection heading="Favoritas">
      <StationRows stations={stations} need={need} onSelect={onSelect} />
    </StationSection>
  );
}
