import type { GeoJSONSource, Map } from 'maplibre-gl';
import { readMapColors } from './mapColors';
import {
  POINT_CIRCLE_LAYER_ID,
  POINTS_SOURCE_ID,
  pointLayers,
  toFeatureCollection,
  toUserLocationCollection,
  USER_LOCATION_SOURCE_ID,
  userLocationLayer,
} from './points';

const POINTER_CURSOR = 'pointer';
const DEFAULT_CURSOR = '';

// Needs a loaded style: MapLibre rejects sources and layers added before it.
export function addPointLayers(map: Map) {
  const colors = readMapColors();
  map.addSource(POINTS_SOURCE_ID, { type: 'geojson', data: toFeatureCollection([]) });
  map.addSource(USER_LOCATION_SOURCE_ID, {
    type: 'geojson',
    data: toUserLocationCollection(undefined),
  });
  for (const layer of pointLayers(colors)) map.addLayer(layer);
  map.addLayer(userLocationLayer(colors.userLocation));
}

export function listenToPointClicks(map: Map, onSelect: (id: string) => void) {
  map.on('click', POINT_CIRCLE_LAYER_ID, (event) => {
    const id: unknown = event.features?.[0]?.properties['id'];
    if (typeof id === 'string') onSelect(id);
  });
  map.on('mouseenter', POINT_CIRCLE_LAYER_ID, () => {
    map.getCanvas().style.cursor = POINTER_CURSOR;
  });
  map.on('mouseleave', POINT_CIRCLE_LAYER_ID, () => {
    map.getCanvas().style.cursor = DEFAULT_CURSOR;
  });
}

export function setSourceData(map: Map, sourceId: string, data: GeoJSON.GeoJSON) {
  map.getSource<GeoJSONSource>(sourceId)?.setData(data);
}
