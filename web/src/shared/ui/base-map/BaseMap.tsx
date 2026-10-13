import { AttributionControl, Map, setWorkerUrl } from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import { useEffect, useRef } from 'react';
import { MADRID_CENTER, MADRID_ZOOM, MAP_STYLE_URL } from '@/shared/config';

// MapLibre derives the worker URL from its own import.meta.url, which neither Vite's dev
// pre-bundling nor the production build preserve; Vite bundles the worker and gives its URL.
setWorkerUrl(workerUrl);

type BaseMapProps = { className?: string };

export function BaseMap({ className }: BaseMapProps) {
  const containerRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    // Replaces the default control, which only collapses on narrow screens, with an always-compact one.
    const map = new Map({
      container,
      style: MAP_STYLE_URL,
      center: MADRID_CENTER,
      zoom: MADRID_ZOOM,
      attributionControl: false,
    });
    map.addControl(new AttributionControl({ compact: true }));

    return () => map.remove();
  }, []);

  return <section ref={containerRef} aria-label="Mapa de Madrid" className={className} />;
}
