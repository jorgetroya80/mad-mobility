type Numbered = { number: string };

// Favorites keep the order the user added them; numbers the API no longer has are skipped.
export function pickFavorites<T extends Numbered>(
  stations: readonly T[],
  favorites: readonly string[],
): T[] {
  const byNumber = new Map(stations.map((station) => [station.number, station]));
  return favorites.flatMap((number) => byNumber.get(number) ?? []);
}
