import { getRouteApi } from '@tanstack/react-router';
import { useCallback } from 'react';
import type { Need } from '@/entities/station';

const mapRoute = getRouteApi('/');

// Replace, not push: switching the filter is not a place to go back to.
export function useNeed(): [Need, (need: Need) => void] {
  const { need } = mapRoute.useSearch();
  const navigate = mapRoute.useNavigate();

  const setNeed = useCallback(
    (next: Need) => navigate({ search: (prev) => ({ ...prev, need: next }), replace: true }),
    [navigate],
  );

  return [need, setNeed];
}
