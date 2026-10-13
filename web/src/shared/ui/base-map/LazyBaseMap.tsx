import { lazy, Suspense } from 'react';

// MapLibre and its worker are most of the bundle: load them after the initial chunk.
const MapLibreMap = lazy(() => import('./BaseMap').then((module) => ({ default: module.BaseMap })));

type LazyBaseMapProps = { className?: string };

export function LazyBaseMap({ className }: LazyBaseMapProps) {
  return (
    <Suspense fallback={<div className={className} aria-hidden="true" />}>
      <MapLibreMap className={className} />
    </Suspense>
  );
}
