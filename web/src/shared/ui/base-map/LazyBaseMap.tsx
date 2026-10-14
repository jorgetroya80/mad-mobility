import { lazy, Suspense } from 'react';
import type { BaseMapProps } from './BaseMap';

// MapLibre and its worker are most of the bundle: load them after the initial chunk.
const MapLibreMap = lazy(() => import('./BaseMap').then((module) => ({ default: module.BaseMap })));

export function LazyBaseMap(props: BaseMapProps) {
  return (
    <Suspense fallback={<div className={props.className} aria-hidden="true" />}>
      <MapLibreMap {...props} />
    </Suspense>
  );
}
