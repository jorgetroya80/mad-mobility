import type { Coordinates } from '@/entities/station';
import { useNeed } from '@/features/filter-by-need';
import { useSelectedStation } from '@/features/select-station';
import { PanelNotice, StaleNotice } from '@/shared/ui';
import { needCaption } from '../lib/needCaption';
import { useNearbyList } from '../model/useNearbyList';
import { FavoriteStations } from './FavoriteStations';
import { NearbyStations } from './NearbyStations';
import { StationSection } from './StationSection';

type StationListProps = {
  // Undefined while the location is still being resolved: the nearby list waits for it.
  center: Coordinates | undefined;
  // True when `center` is the Puerta del Sol fallback instead of the user's position.
  isApproximateLocation: boolean;
};

export function StationList({ center, isApproximateLocation }: StationListProps) {
  const [need] = useNeed();
  const { select } = useSelectedStation();
  const nearby = useNearbyList(center, need);

  return (
    <div className="flex flex-col gap-4">
      {isApproximateLocation ? (
        <PanelNotice>Sin ubicación: mostrando estaciones cerca de la Puerta del Sol</PanelNotice>
      ) : null}
      {nearby.status === 'ready' && nearby.stale ? (
        <StaleNotice updatedAt={nearby.updatedAt} />
      ) : null}
      <FavoriteStations need={need} onSelect={select} />
      <StationSection
        section="nearby"
        heading={isApproximateLocation ? 'Cerca de la Puerta del Sol' : 'Cerca de ti'}
        caption={needCaption(need)}
      >
        <NearbyStations list={nearby} need={need} onSelect={select} />
      </StationSection>
    </div>
  );
}
