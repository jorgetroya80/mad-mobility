import { render } from '@testing-library/react';
import { MADRID_CENTER, MADRID_ZOOM, MAP_STYLE_URL, MD_MEDIA_QUERY } from '@/shared/config';
import { BaseMap } from './BaseMap';

const maplibre = vi.hoisted(() => {
  const instances: MockMap[] = [];

  class MockAttributionControl {
    constructor(readonly options: unknown) {}
  }

  class MockMap {
    readonly addControl = vi.fn<(control: unknown, position?: string) => void>();
    readonly removeControl = vi.fn<(control: unknown) => void>();
    readonly remove = vi.fn<() => void>();

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

function stubMatchMedia(matches: boolean) {
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
  const matchMedia = vi.fn<(media: string) => typeof query>(() => query);
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
