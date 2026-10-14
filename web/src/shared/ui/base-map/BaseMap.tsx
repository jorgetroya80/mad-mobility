import { setWorkerUrl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import { useRef } from 'react';
import type { LngLat, MapPoint } from './points';
import { useEaseTo, useMapLibre, usePointsData, useUserLocationData } from './useMapLibre';

// MapLibre derives the worker URL from its own import.meta.url, which neither Vite's dev
// pre-bundling nor the production build preserve; Vite bundles the worker and gives its URL.
setWorkerUrl(workerUrl);

export type BaseMapProps = {
  className?: string;
  points?: readonly MapPoint[];
  selectedId?: string;
  onSelectPoint?: (id: string) => void;
  focus?: LngLat;
  userLocation?: LngLat;
};

// Module-level so a map without points keeps the same array and does not reset its data.
const NO_POINTS: readonly MapPoint[] = [];

// Points are not keyboard-accessible by design: lists and search reach every point.
export function BaseMap({
  className,
  points = NO_POINTS,
  selectedId,
  onSelectPoint,
  focus,
  userLocation,
}: BaseMapProps) {
  const containerRef = useRef<HTMLElement>(null);
  const map = useMapLibre(containerRef, onSelectPoint);
  usePointsData(map, points, selectedId);
  useUserLocationData(map, userLocation);
  useEaseTo(map, focus);

  return <section ref={containerRef} aria-label="Mapa de Madrid" className={className} />;
}
