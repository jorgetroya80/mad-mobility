import { AttributionControl, Map, type EaseToOptions } from 'maplibre-gl';
import { useEffect, useRef, useState, type RefObject } from 'react';
import {
  MADRID_CENTER,
  MADRID_ZOOM,
  MAP_STYLE_URL,
  MD_MEDIA_QUERY,
  REDUCED_MOTION_QUERY,
} from '@/shared/config';
import { addPointLayers, listenToPointClicks, setSourceData } from './mapLayers';
import {
  POINTS_SOURCE_ID,
  toFeatureCollection,
  toUserLocationCollection,
  USER_LOCATION_SOURCE_ID,
  type LngLat,
  type MapPoint,
} from './points';

type SelectPoint = (id: string) => void;

// Below md the bottom sheet covers the bottom of the map, so the attribution goes to the top.
function addAttribution(map: Map, fromMd: boolean) {
  const control = new AttributionControl({ compact: true });
  map.addControl(control, fromMd ? 'bottom-right' : 'top-right');
  return control;
}

function placeAttribution(map: Map) {
  const md = window.matchMedia(MD_MEDIA_QUERY);
  let attribution = addAttribution(map, md.matches);
  const onBreakpointChange = ({ matches }: { matches: boolean }) => {
    map.removeControl(attribution);
    attribution = addAttribution(map, matches);
  };
  md.addEventListener('change', onBreakpointChange);
  return () => md.removeEventListener('change', onBreakpointChange);
}

function toLngLat(lng: number | undefined, lat: number | undefined): LngLat | undefined {
  return lng === undefined || lat === undefined ? undefined : { lng, lat };
}

// Returns the map once its style has loaded and the point layers exist; undefined before.
export function useMapLibre(
  containerRef: RefObject<HTMLElement | null>,
  onSelectPoint?: SelectPoint,
) {
  const [loadedMap, setLoadedMap] = useState<Map>();
  // The click handler is registered once, so it reads the latest callback from a ref.
  const onSelectPointRef = useRef(onSelectPoint);
  useEffect(() => {
    onSelectPointRef.current = onSelectPoint;
  });

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
    const stopPlacingAttribution = placeAttribution(map);
    map.on('load', () => {
      addPointLayers(map);
      listenToPointClicks(map, (id) => onSelectPointRef.current?.(id));
      setLoadedMap(map);
    });

    return () => {
      stopPlacingAttribution();
      map.remove();
    };
  }, [containerRef]);

  return loadedMap;
}

export function usePointsData(
  map: Map | undefined,
  points: readonly MapPoint[],
  selectedId?: string,
) {
  useEffect(() => {
    if (!map) return;
    setSourceData(map, POINTS_SOURCE_ID, toFeatureCollection(points, selectedId));
  }, [map, points, selectedId]);
}

export function useUserLocationData(map: Map | undefined, location?: LngLat) {
  const lng = location?.lng;
  const lat = location?.lat;
  useEffect(() => {
    if (!map) return;
    setSourceData(map, USER_LOCATION_SOURCE_ID, toUserLocationCollection(toLngLat(lng, lat)));
  }, [map, lng, lat]);
}

// Depends on the numbers, not the objects, so an equal focus or padding does not move the map
// again. A new padding re-eases to the same focus, so it stays in the visible area. The padding
// is always passed: MapLibre keeps the last one for later camera moves.
export function useEaseTo(map: Map | undefined, focus?: LngLat, paddingBottom = 0) {
  const lng = focus?.lng;
  const lat = focus?.lat;
  useEffect(() => {
    const target = toLngLat(lng, lat);
    if (!map || !target) return;
    const camera: EaseToOptions = {
      center: [target.lng, target.lat],
      padding: { bottom: paddingBottom },
    };
    const reduceMotion = window.matchMedia(REDUCED_MOTION_QUERY).matches;
    map.easeTo(reduceMotion ? { ...camera, duration: 0 } : camera);
  }, [map, lng, lat, paddingBottom]);
}
