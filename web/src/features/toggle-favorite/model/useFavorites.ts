import { useCallback } from 'react';
import { FAVORITES_STORAGE_KEY } from '@/shared/config';
import { useLocalStorage } from '@/shared/lib';
import { parseFavorites } from './parseFavorites';

const NO_FAVORITES: readonly string[] = [];

export type Favorites = {
  favorites: readonly string[];
  isFavorite: (number: string) => boolean;
  toggle: (number: string) => void;
};

export function useFavorites(): Favorites {
  const [favorites, setFavorites] = useLocalStorage(
    FAVORITES_STORAGE_KEY,
    parseFavorites,
    NO_FAVORITES,
  );

  const isFavorite = useCallback((number: string) => favorites.includes(number), [favorites]);

  const toggle = useCallback(
    (number: string) =>
      setFavorites(
        favorites.includes(number)
          ? favorites.filter((favorite) => favorite !== number)
          : [...favorites, number],
      ),
    [favorites, setFavorites],
  );

  return { favorites, isFavorite, toggle };
}
