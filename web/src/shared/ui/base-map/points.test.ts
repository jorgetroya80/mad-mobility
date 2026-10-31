import type { MapColors } from './mapColors';
import {
  POINT_CIRCLE_LAYER_ID,
  POINT_LABEL_LAYER_ID,
  POINTS_SOURCE_ID,
  pointLayers,
  toFeatureCollection,
  toUserLocationCollection,
  USER_LOCATION_LAYER_ID,
  USER_LOCATION_SOURCE_ID,
  userLocationLayer,
  type MapPoint,
} from './points';

const sol: MapPoint = { id: '1a', lng: -3.7038, lat: 40.4168, label: '7', tone: 'good' };
const callao: MapPoint = { id: '2', lng: -3.7059, lat: 40.42, label: '0', tone: 'empty' };

const colors: MapColors = {
  tones: {
    good: { fill: '#good', text: '#good-text' },
    low: { fill: '#low', text: '#low-text' },
    empty: { fill: '#empty', text: '#empty-text' },
    off: { fill: '#off', text: '#off-text' },
    unconfirmed: { fill: '#unconfirmed', text: '#unconfirmed-text' },
  },
  userLocation: '#user',
  pointStroke: '#stroke',
  pointSelectedStroke: '#selected-stroke',
  emptyStroke: '#empty-stroke',
};

const isEmptyTone = ['==', ['get', 'tone'], 'empty'];

describe('toFeatureCollection', () => {
  it('turns each point into a GeoJSON point with [lng, lat] coordinates', () => {
    const collection = toFeatureCollection([sol, callao]);

    expect(collection.type).toBe('FeatureCollection');
    expect(collection.features.map((feature) => feature.geometry)).toEqual([
      { type: 'Point', coordinates: [-3.7038, 40.4168] },
      { type: 'Point', coordinates: [-3.7059, 40.42] },
    ]);
  });

  it('keeps id, label and tone as properties, none selected by default', () => {
    const [feature] = toFeatureCollection([sol]).features;

    expect(feature?.properties).toEqual({ id: '1a', label: '7', tone: 'good', selected: false });
  });

  it('flags only the selected point', () => {
    const collection = toFeatureCollection([sol, callao], '2');

    expect(collection.features.map((feature) => feature.properties.selected)).toEqual([
      false,
      true,
    ]);
  });

  it('is empty without points', () => {
    expect(toFeatureCollection([]).features).toEqual([]);
  });
});

describe('toUserLocationCollection', () => {
  it('has one point at the user location', () => {
    expect(toUserLocationCollection({ lng: -3.7, lat: 40.4 }).features).toEqual([
      { type: 'Feature', geometry: { type: 'Point', coordinates: [-3.7, 40.4] }, properties: {} },
    ]);
  });

  it('is empty without a location', () => {
    expect(toUserLocationCollection(undefined).features).toEqual([]);
  });
});

describe('pointLayers', () => {
  it('draws circles and labels from the points source, labels on top', () => {
    const [circle, label] = pointLayers(colors);

    expect(circle).toMatchObject({ id: POINT_CIRCLE_LAYER_ID, type: 'circle' });
    expect(circle.source).toBe(POINTS_SOURCE_ID);
    expect(label).toMatchObject({ id: POINT_LABEL_LAYER_ID, type: 'symbol' });
    expect(label.source).toBe(POINTS_SOURCE_ID);
  });

  it('fills each circle with the color of its tone', () => {
    const [circle] = pointLayers(colors);

    expect(circle.paint?.['circle-color']).toEqual([
      'match',
      ['get', 'tone'],
      'good',
      '#good',
      'low',
      '#low',
      'empty',
      '#empty',
      'unconfirmed',
      '#unconfirmed',
      '#off',
    ]);
  });

  it('writes the label with a font of the liberty style, in the text color of its tone', () => {
    const [, label] = pointLayers(colors);

    expect(label.layout?.['text-field']).toEqual(['get', 'label']);
    expect(label.layout?.['text-font']).toEqual(['Noto Sans Bold']);
    expect(label.paint?.['text-color']).toEqual([
      'match',
      ['get', 'tone'],
      'good',
      '#good-text',
      'low',
      '#low-text',
      'empty',
      '#empty-text',
      'unconfirmed',
      '#unconfirmed-text',
      '#off-text',
    ]);
  });

  it('makes the selected point bigger, with a thicker border', () => {
    const [circle] = pointLayers(colors);
    const radius = circle.paint?.['circle-radius'];
    const strokeWidth = circle.paint?.['circle-stroke-width'];

    expect(radius).toEqual(['case', ['get', 'selected'], expect.any(Number), expect.any(Number)]);
    expect(strokeWidth).toEqual([
      'case',
      ['get', 'selected'],
      expect.any(Number),
      isEmptyTone,
      expect.any(Number),
      expect.any(Number),
    ]);
    const [, , selectedRadius, radiusOtherwise] = radius as [string, unknown, number, number];
    expect(selectedRadius).toBeGreaterThan(radiusOtherwise);
    const [, , selectedWidth, , , widthOtherwise] = strokeWidth as unknown[];
    expect(selectedWidth).toBeGreaterThan(widthOtherwise as number);
  });

  it('borders the empty tone with a 2 px line, between the selected and the other widths', () => {
    const [circle] = pointLayers(colors);
    const strokeWidth = circle.paint?.['circle-stroke-width'] as unknown[];
    const [, , selectedWidth, , emptyWidth, widthOtherwise] = strokeWidth;

    expect(emptyWidth).toBe(2);
    expect(selectedWidth).toBeGreaterThan(emptyWidth as number);
    expect(emptyWidth).toBeGreaterThan(widthOtherwise as number);
  });

  it('strokes the selected point, then the empty tone, then the rest with their theme colors', () => {
    const [circle] = pointLayers(colors);

    expect(circle.paint?.['circle-stroke-color']).toEqual([
      'case',
      ['get', 'selected'],
      '#selected-stroke',
      isEmptyTone,
      '#empty-stroke',
      '#stroke',
    ]);
  });
});

describe('userLocationLayer', () => {
  it('draws the user as a dot in its own source and color', () => {
    const layer = userLocationLayer(colors);

    expect(layer).toMatchObject({
      id: USER_LOCATION_LAYER_ID,
      type: 'circle',
      source: USER_LOCATION_SOURCE_ID,
    });
    expect(layer.paint?.['circle-color']).toBe('#user');
  });

  it('borders the dot with the point stroke color', () => {
    expect(userLocationLayer(colors).paint?.['circle-stroke-color']).toBe('#stroke');
  });
});
