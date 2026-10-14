import { act, render } from '@testing-library/react';
import {
  MADRID_CENTER,
  MADRID_ZOOM,
  MAP_STYLE_URL,
  MD_MEDIA_QUERY,
  REDUCED_MOTION_QUERY,
} from '@/shared/config';
import { BaseMap } from './BaseMap';
import {
  POINT_CIRCLE_LAYER_ID,
  POINT_LABEL_LAYER_ID,
  POINTS_SOURCE_ID,
  toFeatureCollection,
  toUserLocationCollection,
  USER_LOCATION_LAYER_ID,
  USER_LOCATION_SOURCE_ID,
  type MapPoint,
} from './points';

const maplibre = vi.hoisted(() => {
  const instances: MockMap[] = [];

  class MockAttributionControl {
    constructor(readonly options: unknown) {}
  }

  class MockSource {
    readonly setData = vi.fn<(data: unknown) => void>();
  }

  type Handler = (event: Record<string, unknown>) => void;
  type Subscription = { type: string; layerId: string | undefined; handler: Handler };

  class MockMap {
    readonly addControl = vi.fn<(control: unknown, position?: string) => void>();
    readonly removeControl = vi.fn<(control: unknown) => void>();
    readonly remove = vi.fn<() => void>();
    readonly addLayer = vi.fn<(layer: { id: string; source?: unknown }) => void>();
    readonly easeTo = vi.fn<(options: unknown) => void>();
    readonly sources = new Map<string, MockSource>();
    readonly subscriptions: Subscription[] = [];
    readonly canvas = { style: { cursor: '' } };

    readonly addSource = vi.fn<(id: string, source: unknown) => void>((id) => {
      this.sources.set(id, new MockSource());
    });

    getSource(id: string) {
      return this.sources.get(id);
    }

    getCanvas() {
      return this.canvas;
    }

    on(type: string, layerOrHandler: string | Handler, handler?: Handler) {
      if (typeof layerOrHandler === 'string' && handler) {
        this.subscriptions.push({ type, layerId: layerOrHandler, handler });
      } else if (typeof layerOrHandler === 'function') {
        this.subscriptions.push({ type, layerId: undefined, handler: layerOrHandler });
      }
      return this;
    }

    fire(type: string, layerId?: string, event: Record<string, unknown> = {}) {
      this.subscriptions
        .filter((subscription) => subscription.type === type && subscription.layerId === layerId)
        .forEach((subscription) => subscription.handler(event));
    }

    constructor(readonly options: Record<string, unknown>) {
      instances.push(this);
    }
  }

  // Plain array: setWorkerUrl runs at import time, before Vitest's clearMocks wipes vi.fn calls.
  const workerUrls: string[] = [];
  const setWorkerUrl = (url: string) => workerUrls.push(url);

  return { instances, MockMap, MockAttributionControl, workerUrls, setWorkerUrl };
});

vi.mock('maplibre-gl', () => ({
  Map: maplibre.MockMap,
  AttributionControl: maplibre.MockAttributionControl,
  setWorkerUrl: maplibre.setWorkerUrl,
}));

vi.mock('maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url', () => ({
  default: '/assets/maplibre-gl-worker.js',
}));

// jsdom has no matchMedia: a stub whose `change` listeners the tests can fire.
type ChangeListener = (event: { matches: boolean }) => void;

function stubMatchMedia(matches: boolean, reducedMotion = false) {
  const listeners = new Set<ChangeListener>();
  const query = {
    matches,
    addEventListener: vi.fn<(type: 'change', listener: ChangeListener) => void>(
      (_type, listener) => {
        listeners.add(listener);
      },
    ),
    removeEventListener: vi.fn<(type: 'change', listener: ChangeListener) => void>(
      (_type, listener) => {
        listeners.delete(listener);
      },
    ),
  };
  const matchMedia = vi.fn<(media: string) => typeof query>((media) =>
    media === REDUCED_MOTION_QUERY ? { ...query, matches: reducedMotion } : query,
  );
  vi.stubGlobal('matchMedia', matchMedia);
  const change = (next: boolean) => listeners.forEach((listener) => listener({ matches: next }));
  return { matchMedia, query, listeners, change };
}

function renderedMap() {
  expect(maplibre.instances).toHaveLength(1);
  const [map] = maplibre.instances;
  if (!map) throw new Error('map was not created');
  return map;
}

describe('BaseMap', () => {
  beforeEach(() => {
    maplibre.instances.length = 0;
    stubMatchMedia(true);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('points MapLibre at the worker bundled by Vite', () => {
    expect(maplibre.workerUrls).toEqual(['/assets/maplibre-gl-worker.js']);
  });

  it('creates the map once in its container, centered on Madrid with the OpenFreeMap style', () => {
    const { getByRole } = render(<BaseMap />);

    expect(renderedMap().options).toEqual({
      container: getByRole('region', { name: 'Mapa de Madrid' }),
      style: MAP_STYLE_URL,
      center: MADRID_CENTER,
      zoom: MADRID_ZOOM,
      attributionControl: false,
    });
  });

  it('adds a compact attribution control', () => {
    render(<BaseMap />);

    const [control] = renderedMap().addControl.mock.calls[0] ?? [];
    expect(control).toBeInstanceOf(maplibre.MockAttributionControl);
    expect(control).toHaveProperty('options', { compact: true });
  });

  it('places the attribution at the top right below md, clear of the bottom sheet', () => {
    const { matchMedia } = stubMatchMedia(false);
    render(<BaseMap />);

    expect(matchMedia).toHaveBeenCalledWith(MD_MEDIA_QUERY);
    expect(renderedMap().addControl.mock.calls[0]?.[1]).toBe('top-right');
  });

  it('places the attribution at the bottom right from md up', () => {
    stubMatchMedia(true);
    render(<BaseMap />);

    expect(renderedMap().addControl.mock.calls[0]?.[1]).toBe('bottom-right');
  });

  it('moves the attribution when the viewport crosses md', () => {
    const { change } = stubMatchMedia(false);
    render(<BaseMap />);
    const map = renderedMap();
    const [first] = map.addControl.mock.calls[0] ?? [];

    change(true);

    expect(map.removeControl).toHaveBeenCalledWith(first);
    const [second, position] = map.addControl.mock.calls[1] ?? [];
    expect(second).toBeInstanceOf(maplibre.MockAttributionControl);
    expect(position).toBe('bottom-right');
  });

  it('stops listening to the breakpoint on unmount', () => {
    const { listeners, query } = stubMatchMedia(false);
    const { unmount } = render(<BaseMap />);
    expect(listeners.size).toBe(1);

    unmount();

    expect(query.removeEventListener).toHaveBeenCalledOnce();
    expect(listeners.size).toBe(0);
  });

  it('removes the map on unmount', () => {
    const { unmount } = render(<BaseMap />);
    const map = renderedMap();
    expect(map.remove).not.toHaveBeenCalled();

    unmount();

    expect(map.remove).toHaveBeenCalledOnce();
  });

  it('applies the given class name to the container', () => {
    const { getByRole } = render(<BaseMap className="h-full" />);

    expect(getByRole('region', { name: 'Mapa de Madrid' })).toHaveClass('h-full');
  });
});

const points: MapPoint[] = [
  { id: '1a', lng: -3.7038, lat: 40.4168, label: '7', tone: 'good' },
  { id: '2', lng: -3.7059, lat: 40.42, label: '0', tone: 'empty' },
];

function loadedMap() {
  const map = renderedMap();
  act(() => map.fire('load'));
  return map;
}

function lastData(map: InstanceType<typeof maplibre.MockMap>, sourceId: string) {
  return map.getSource(sourceId)?.setData.mock.lastCall?.[0];
}

describe('BaseMap points', () => {
  beforeEach(() => {
    maplibre.instances.length = 0;
    stubMatchMedia(true);
    document.documentElement.style.setProperty('--color-availability-good', '#15803d');
    document.documentElement.style.setProperty('--color-map-user-location', '#2563eb');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.documentElement.removeAttribute('style');
  });

  it('adds nothing until the style loads', () => {
    render(<BaseMap points={points} />);

    expect(renderedMap().addSource).not.toHaveBeenCalled();
    expect(renderedMap().addLayer).not.toHaveBeenCalled();
  });

  it('adds the points and user location sources and layers once, after the style loads', () => {
    const { rerender } = render(<BaseMap points={points} />);
    const map = loadedMap();
    rerender(<BaseMap points={[...points]} />);

    expect(map.addSource.mock.calls.map(([id]) => id)).toEqual([
      POINTS_SOURCE_ID,
      USER_LOCATION_SOURCE_ID,
    ]);
    expect(map.addLayer.mock.calls.map(([layer]) => layer.id)).toEqual([
      POINT_CIRCLE_LAYER_ID,
      POINT_LABEL_LAYER_ID,
      USER_LOCATION_LAYER_ID,
    ]);
  });

  it('paints the layers with the theme colors', () => {
    render(<BaseMap points={points} />);
    const map = loadedMap();

    const layers = map.addLayer.mock.calls.map(([layer]) => JSON.stringify(layer));
    expect(layers[0]).toContain('#15803d');
    expect(layers[2]).toContain('#2563eb');
  });

  it('applies the points passed before the style loaded', () => {
    render(<BaseMap points={points} selectedId="2" />);
    const map = loadedMap();

    expect(lastData(map, POINTS_SOURCE_ID)).toEqual(toFeatureCollection(points, '2'));
  });

  it('updates the source data when points or selection change, without recreating the map', () => {
    const { rerender } = render(<BaseMap points={points} />);
    const map = loadedMap();
    const [first] = points;

    rerender(<BaseMap points={[first as MapPoint]} />);
    expect(lastData(map, POINTS_SOURCE_ID)).toEqual(toFeatureCollection([first as MapPoint]));

    rerender(<BaseMap points={[first as MapPoint]} selectedId="1a" />);
    expect(lastData(map, POINTS_SOURCE_ID)).toEqual(toFeatureCollection([first as MapPoint], '1a'));
    expect(maplibre.instances).toHaveLength(1);
  });

  it('draws the user location in its own source', () => {
    const { rerender } = render(<BaseMap />);
    const map = loadedMap();
    expect(lastData(map, USER_LOCATION_SOURCE_ID)).toEqual(toUserLocationCollection(undefined));

    rerender(<BaseMap userLocation={{ lng: -3.7, lat: 40.4 }} />);

    expect(lastData(map, USER_LOCATION_SOURCE_ID)).toEqual(
      toUserLocationCollection({ lng: -3.7, lat: 40.4 }),
    );
  });

  it('selects the clicked point with the latest callback', () => {
    const first = vi.fn<(id: string) => void>();
    const latest = vi.fn<(id: string) => void>();
    const { rerender } = render(<BaseMap points={points} onSelectPoint={first} />);
    const map = loadedMap();
    rerender(<BaseMap points={points} onSelectPoint={latest} />);

    map.fire('click', POINT_CIRCLE_LAYER_ID, { features: [{ properties: { id: '2' } }] });

    expect(latest).toHaveBeenCalledExactlyOnceWith('2');
    expect(first).not.toHaveBeenCalled();
  });

  it('ignores a click without a point', () => {
    const onSelectPoint = vi.fn<(id: string) => void>();
    render(<BaseMap points={points} onSelectPoint={onSelectPoint} />);
    const map = loadedMap();

    map.fire('click', POINT_CIRCLE_LAYER_ID, { features: [] });

    expect(onSelectPoint).not.toHaveBeenCalled();
  });

  it('shows a pointer cursor over the points', () => {
    render(<BaseMap points={points} />);
    const map = loadedMap();

    map.fire('mouseenter', POINT_CIRCLE_LAYER_ID);
    expect(map.canvas.style.cursor).toBe('pointer');

    map.fire('mouseleave', POINT_CIRCLE_LAYER_ID);
    expect(map.canvas.style.cursor).toBe('');
  });

  it('eases to the focus once loaded and when it changes', () => {
    const { rerender } = render(<BaseMap focus={{ lng: -3.7, lat: 40.4 }} />);
    const map = renderedMap();
    expect(map.easeTo).not.toHaveBeenCalled();

    act(() => map.fire('load'));
    expect(map.easeTo).toHaveBeenLastCalledWith({ center: [-3.7, 40.4] });

    rerender(<BaseMap focus={{ lng: -3.7, lat: 40.4 }} />);
    expect(map.easeTo).toHaveBeenCalledOnce();

    rerender(<BaseMap focus={{ lng: -3.71, lat: 40.41 }} />);
    expect(map.easeTo).toHaveBeenLastCalledWith({ center: [-3.71, 40.41] });
  });

  it('jumps to the focus without animation when the user prefers reduced motion', () => {
    stubMatchMedia(true, true);
    render(<BaseMap focus={{ lng: -3.7, lat: 40.4 }} />);
    const map = loadedMap();

    expect(map.easeTo).toHaveBeenCalledExactlyOnceWith({ center: [-3.7, 40.4], duration: 0 });
  });
});
