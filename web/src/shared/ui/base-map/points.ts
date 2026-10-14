import type {
  CircleLayerSpecification,
  ExpressionSpecification,
  SymbolLayerSpecification,
} from 'maplibre-gl';
import type { MapColors, ToneColors } from './mapColors';

export const MAP_POINT_TONES = ['good', 'low', 'empty', 'off'] as const;

export type MapPointTone = (typeof MAP_POINT_TONES)[number];

export type LngLat = { lng: number; lat: number };

export type MapPoint = LngLat & { id: string; label: string; tone: MapPointTone };

type MapPointProperties = Pick<MapPoint, 'id' | 'label' | 'tone'> & { selected: boolean };

export const POINTS_SOURCE_ID = 'points';
export const POINT_CIRCLE_LAYER_ID = 'point-circles';
export const POINT_LABEL_LAYER_ID = 'point-labels';
export const USER_LOCATION_SOURCE_ID = 'user-location';
export const USER_LOCATION_LAYER_ID = 'user-location-dot';

// A font the liberty style already uses: its glyph server only has the style's fonts.
const LABEL_FONT = ['Noto Sans Bold'];
const LABEL_SIZE_PX = 12;
// Fits a two-digit label inside the circle.
const POINT_RADIUS_PX = 12;
const SELECTED_POINT_RADIUS_PX = 16;
const POINT_STROKE_WIDTH_PX = 1.5;
const SELECTED_POINT_STROKE_WIDTH_PX = 3;
const POINT_STROKE_COLOR = '#ffffff';
const SELECTED_POINT_STROKE_COLOR = '#030712';
const USER_LOCATION_RADIUS_PX = 8;
const USER_LOCATION_STROKE_WIDTH_PX = 3;
const SELECTED_SORT_KEY = 1;
const UNSELECTED_SORT_KEY = 0;

const isSelected: ExpressionSpecification = ['get', 'selected'];

function toPoint({ lng, lat }: LngLat): GeoJSON.Point {
  return { type: 'Point', coordinates: [lng, lat] };
}

export function toFeatureCollection(
  points: readonly MapPoint[],
  selectedId?: string,
): GeoJSON.FeatureCollection<GeoJSON.Point, MapPointProperties> {
  return {
    type: 'FeatureCollection',
    features: points.map(({ id, label, tone, ...position }) => ({
      type: 'Feature',
      geometry: toPoint(position),
      properties: { id, label, tone, selected: id === selectedId },
    })),
  };
}

export function toUserLocationCollection(
  location: LngLat | undefined,
): GeoJSON.FeatureCollection<GeoJSON.Point> {
  const features: GeoJSON.Feature<GeoJSON.Point>[] = location
    ? [{ type: 'Feature', geometry: toPoint(location), properties: {} }]
    : [];
  return { type: 'FeatureCollection', features };
}

function byTone(colors: MapColors, pick: (tone: ToneColors) => string): ExpressionSpecification {
  const { good, low, empty, off } = colors.tones;
  return [
    'match',
    ['get', 'tone'],
    'good',
    pick(good),
    'low',
    pick(low),
    'empty',
    pick(empty),
    pick(off),
  ];
}

function whenSelected<T>(selected: T, otherwise: T): ExpressionSpecification {
  return ['case', isSelected, selected, otherwise] as ExpressionSpecification;
}

export function pointLayers(
  colors: MapColors,
): [CircleLayerSpecification, SymbolLayerSpecification] {
  const sortKey = whenSelected(SELECTED_SORT_KEY, UNSELECTED_SORT_KEY);
  return [
    {
      id: POINT_CIRCLE_LAYER_ID,
      type: 'circle',
      source: POINTS_SOURCE_ID,
      layout: { 'circle-sort-key': sortKey },
      paint: {
        'circle-color': byTone(colors, (tone) => tone.fill),
        'circle-radius': whenSelected(SELECTED_POINT_RADIUS_PX, POINT_RADIUS_PX),
        'circle-stroke-width': whenSelected(SELECTED_POINT_STROKE_WIDTH_PX, POINT_STROKE_WIDTH_PX),
        'circle-stroke-color': whenSelected(SELECTED_POINT_STROKE_COLOR, POINT_STROKE_COLOR),
      },
    },
    {
      id: POINT_LABEL_LAYER_ID,
      type: 'symbol',
      source: POINTS_SOURCE_ID,
      layout: {
        'text-field': ['get', 'label'],
        'text-font': LABEL_FONT,
        'text-size': LABEL_SIZE_PX,
        // Every point shows its number: the color alone never tells the availability.
        'text-allow-overlap': true,
        'text-ignore-placement': true,
        'symbol-sort-key': sortKey,
      },
      paint: { 'text-color': byTone(colors, (tone) => tone.text) },
    },
  ];
}

export function userLocationLayer(color: string): CircleLayerSpecification {
  return {
    id: USER_LOCATION_LAYER_ID,
    type: 'circle',
    source: USER_LOCATION_SOURCE_ID,
    paint: {
      'circle-color': color,
      'circle-radius': USER_LOCATION_RADIUS_PX,
      'circle-stroke-width': USER_LOCATION_STROKE_WIDTH_PX,
      'circle-stroke-color': POINT_STROKE_COLOR,
    },
  };
}
