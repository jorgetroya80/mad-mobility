import { getRouteApi } from '@tanstack/react-router';
import { useCallback } from 'react';

const mapRoute = getRouteApi('/');

export type SelectedStation = {
  number: string | undefined;
  select: (number: string) => void;
  clear: () => void;
};

// `?station` is the only source of truth. Selecting pushes an entry so Back closes the detail;
// clearing replaces it, so Back after closing does not reopen the station just closed.
export function useSelectedStation(): SelectedStation {
  const { station } = mapRoute.useSearch();
  const navigate = mapRoute.useNavigate();

  const select = useCallback(
    (number: string) => navigate({ search: (prev) => ({ ...prev, station: number }) }),
    [navigate],
  );
  const clear = useCallback(
    () => navigate({ search: (prev) => ({ ...prev, station: undefined }), replace: true }),
    [navigate],
  );

  return { number: station, select, clear };
}
