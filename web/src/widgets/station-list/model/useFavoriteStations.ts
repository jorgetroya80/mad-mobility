import { type DataStatus, type Station, useAllStations, useDataStatus } from '@/entities/station';
import { useFavorites } from '@/features/toggle-favorite';
import { pickFavorites } from '../lib/pickFavorites';

const NO_STATIONS: readonly Station[] = [];

type FavoriteStations = { stations: readonly Station[]; status: DataStatus };

// Favorites come from the full list, not the nearby one: they show wherever they are, with the
// full list's status.
export function useFavoriteStations(): FavoriteStations {
  const { favorites } = useFavorites();
  const query = useAllStations();
  const { status } = useDataStatus(query);
  const stations = query.data ? pickFavorites(query.data.stations, favorites) : NO_STATIONS;
  return { stations, status };
}
