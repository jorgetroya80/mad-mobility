import { type Station, useAllStations } from '@/entities/station';
import { useFavorites } from '@/features/toggle-favorite';
import { pickFavorites } from '../lib/pickFavorites';

const NO_STATIONS: readonly Station[] = [];

// Favorites come from the full list, not the nearby one: they show wherever they are.
export function useFavoriteStations(): readonly Station[] {
  const { favorites } = useFavorites();
  const { data } = useAllStations();
  return data ? pickFavorites(data.stations, favorites) : NO_STATIONS;
}
