import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { nearbyStationsFixture, stationsFixture } from '@/shared/api/mocks/fixtures';
import { server } from '@/shared/api/mocks/node';
import { env, FAVORITES_STORAGE_KEY, MADRID_CENTER } from '@/shared/config';
import type * as SharedUi from '@/shared/ui';
import { stubMatchMedia } from '../../../../test/matchMedia';
import { renderWithRouter } from '../../../../test/renderWithRouter';

type LngLat = { lng: number; lat: number };
type CapturedMapProps = {
  selectedId?: string;
  onSelectPoint?: (id: string) => void;
  focus?: LngLat;
  userLocation?: LngLat;
  focusPadding?: { bottom: number };
};

const mapRenders = vi.hoisted(() => [] as CapturedMapProps[]);

// jsdom has no WebGL: the map only records the props it receives; the rest of the UI is real.
vi.mock('@/shared/ui', async (importOriginal) => ({
  ...(await importOriginal<typeof SharedUi>()),
  BaseMap: (props: CapturedMapProps) => {
    mapRenders.push(props);
    return <div data-testid="base-map" />;
  },
}));

const STATIONS_URL = `${env.apiBaseUrl}/v1/bicimad/stations`;
const USER = { lat: 40.4169, lng: -3.7033 };
// Station 1a in the fixtures.
const PUERTA_DEL_SOL_A = { lat: 40.4170224, lng: -3.7024255 };
const PERMISSION_DENIED = 1;

// 'pending' never answers, like a browser still asking for the position.
type GeoAnswer = 'granted' | 'denied' | 'pending';

// jsdom has no geolocation: every request is answered at once with the current answer.
function stubGeolocation(initialAnswer: GeoAnswer) {
  let answer = initialAnswer;
  const getCurrentPosition = vi.fn<Geolocation['getCurrentPosition']>((onSuccess, onError) => {
    if (answer === 'pending') return;
    if (answer === 'granted') {
      onSuccess({ coords: { latitude: USER.lat, longitude: USER.lng } } as GeolocationPosition);
      return;
    }
    onError?.({ code: PERMISSION_DENIED, PERMISSION_DENIED } as GeolocationPositionError);
  });
  Object.defineProperty(navigator, 'geolocation', {
    value: { getCurrentPosition },
    configurable: true,
  });
  return {
    getCurrentPosition,
    answerWith(next: GeoAnswer) {
      answer = next;
    },
  };
}

function recordNearbyRequests(): URL[] {
  const urls: URL[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.searchParams.has('near')) urls.push(url);
  });
  return urls;
}

// Holds the docks answer until `release`, so the test can look at the screen in between.
function holdDocksResponse() {
  let release = () => {};
  const released = new Promise<void>((resolve) => {
    release = resolve;
  });
  server.use(
    http.get(STATIONS_URL, async ({ request }) => {
      const params = new URL(request.url).searchParams;
      if (!params.has('near')) return HttpResponse.json(stationsFixture);
      if (params.get('need') === 'docks') await released;
      return HttpResponse.json(nearbyStationsFixture);
    }),
  );
  return { release };
}

function lastMapProps(): CapturedMapProps {
  const props = mapRenders.at(-1);
  if (!props) throw new Error('BaseMap was not rendered');
  return props;
}

function renderPage(initialEntry = '/') {
  return renderWithRouter(undefined, { initialEntries: [initialEntry] });
}

function panel() {
  return screen.getByRole('region', { name: 'Estaciones' });
}

function sheetHandle() {
  return screen.getByRole('button', { name: 'Estaciones' });
}

async function rowIn(section: string, number: string) {
  const region = await screen.findByRole('region', { name: section });
  return within(region).findByRole('button', { name: new RegExp(`^Estación ${number},`) });
}

async function detailTitle(name: string) {
  return screen.findByRole('heading', { level: 2, name });
}

function saveFavorites(numbers: string[]) {
  localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(numbers));
}

beforeEach(() => {
  mapRenders.length = 0;
});

afterEach(() => {
  Reflect.deleteProperty(navigator, 'geolocation');
  localStorage.clear();
  server.events.removeAllListeners();
});

describe('MapPage', () => {
  describe('landmarks', () => {
    beforeEach(() => stubGeolocation('granted'));

    it('names the page and shows the map', async () => {
      await renderPage();

      expect(screen.getByRole('heading', { level: 1, name: 'BiciMAD Now' })).toBeInTheDocument();
      expect(within(screen.getByRole('main')).getByTestId('base-map')).toBeInTheDocument();
    });

    // Bottom sheet (below md) and side panel (md and up).
    it.each([false, true])(
      'puts search, filter, location, list and attribution in the panel (from md: %s)',
      async (isWide) => {
        stubMatchMedia(isWide);
        await renderPage();

        const region = panel();
        expect(within(region).getByRole('searchbox')).toBeInTheDocument();
        expect(within(region).getByRole('button', { name: 'Anclajes' })).toBeInTheDocument();
        expect(within(region).getByRole('button', { name: 'Mi ubicación' })).toBeInTheDocument();
        expect(
          await within(region).findByRole('region', { name: 'Cerca de ti' }),
        ).toBeInTheDocument();
        expect(within(region).getByRole('link', { name: 'EMT de Madrid' })).toBeInTheDocument();
      },
    );
  });

  describe('with the location granted', () => {
    beforeEach(() => stubGeolocation('granted'));

    it('lists the stations near the user (SC1) and centers the map on them', async () => {
      const requests = recordNearbyRequests();

      await renderPage();

      expect(await rowIn('Cerca de ti', '1a')).toBeInTheDocument();
      expect(screen.queryByText(/Sin ubicación/)).not.toBeInTheDocument();
      expect(requests[0]?.searchParams.get('near')).toBe('40.4169,-3.7033');
      expect(lastMapProps()).toMatchObject({ focus: USER, userLocation: USER });
    });

    it('opens ?station=1a in place of the list and selects it on the map (SC5)', async () => {
      await renderPage('/?station=1a');

      expect(await detailTitle('1a · Puerta del Sol A')).toBeInTheDocument();
      expect(screen.queryByRole('region', { name: 'Cerca de ti' })).not.toBeInTheDocument();
      await waitFor(() =>
        expect(lastMapProps()).toMatchObject({ selectedId: '1a', focus: PUERTA_DEL_SOL_A }),
      );
    });

    it('asks for free docks with "Anclajes" without going back to loading (SC3)', async () => {
      const requests = recordNearbyRequests();
      const docks = holdDocksResponse();
      const user = userEvent.setup();
      const { router } = await renderPage();
      await rowIn('Cerca de ti', '1a');

      await user.click(screen.getByRole('button', { name: 'Anclajes' }));

      expect(router.state.location.href).toBe('/?need=docks');
      await waitFor(() => expect(requests.at(-1)?.searchParams.get('need')).toBe('docks'));
      expect(screen.queryByText(/Cargando estaciones cercanas/)).not.toBeInTheDocument();
      expect(await rowIn('Cerca de ti', '1a')).toHaveAccessibleName(/12 anclajes libres$/);

      docks.release();
    });

    it('treats an unknown need as bikes', async () => {
      const requests = recordNearbyRequests();

      await renderPage('/?need=xyz');

      expect(await rowIn('Cerca de ti', '1a')).toHaveAccessibleName(/12 bicis$/);
      expect(screen.getByRole('button', { name: 'Bicis' })).toHaveAttribute('aria-pressed', 'true');
      expect(requests.map((url) => url.searchParams.get('need'))).toEqual(['bikes']);
    });

    it('returns the focus to the nearby row on Back, though it is also a favorite (SC6)', async () => {
      saveFavorites(['1a']);
      const user = userEvent.setup();
      const { router } = await renderPage();
      await rowIn('Favoritas', '1a');

      await user.click(await rowIn('Cerca de ti', '1a'));
      expect(await detailTitle('1a · Puerta del Sol A')).toHaveFocus();

      await act(() => router.history.back());

      expect(await rowIn('Cerca de ti', '1a')).toHaveFocus();
      expect(router.state.location.href).toBe('/');
    });

    it('returns the focus to the favorite row with "Volver"', async () => {
      saveFavorites(['1a']);
      const user = userEvent.setup();
      await renderPage();

      await user.click(await rowIn('Favoritas', '1a'));
      await detailTitle('1a · Puerta del Sol A');
      await user.click(screen.getByRole('button', { name: 'Volver' }));

      expect(await rowIn('Favoritas', '1a')).toHaveFocus();
    });

    it('focuses the list heading when the detail was not opened from the list', async () => {
      const user = userEvent.setup();
      await renderPage('/?station=1a');

      await detailTitle('1a · Puerta del Sol A');
      await user.click(screen.getByRole('button', { name: 'Volver' }));

      expect(await screen.findByRole('heading', { level: 2, name: 'Cerca de ti' })).toHaveFocus();
    });
  });

  describe('with the location denied', () => {
    it('lists the stations near Puerta del Sol with a notice and centers the map there (SC2)', async () => {
      stubGeolocation('denied');
      const requests = recordNearbyRequests();

      await renderPage();

      expect(await rowIn('Cerca de la Puerta del Sol', '1a')).toBeInTheDocument();
      expect(
        screen.getByText('Sin ubicación: mostrando estaciones cerca de la Puerta del Sol'),
      ).toBeInTheDocument();
      expect(requests[0]?.searchParams.get('near')).toBe('40.4168,-3.7038');
      expect(lastMapProps()).toMatchObject({ focus: MADRID_CENTER, userLocation: undefined });
    });

    it('asks again with "Mi ubicación"', async () => {
      const geolocation = stubGeolocation('denied');
      const user = userEvent.setup();
      await renderPage();
      await rowIn('Cerca de la Puerta del Sol', '1a');

      geolocation.answerWith('granted');
      await user.click(screen.getByRole('button', { name: 'Mi ubicación' }));

      expect(geolocation.getCurrentPosition).toHaveBeenCalledTimes(2);
      expect(await rowIn('Cerca de ti', '1a')).toBeInTheDocument();
      expect(lastMapProps()).toMatchObject({ focus: USER, userLocation: USER });
    });
  });

  describe('while "Mi ubicación" asks again', () => {
    it('keeps describing the list it shows: still near Puerta del Sol', async () => {
      const geolocation = stubGeolocation('denied');
      const user = userEvent.setup();
      await renderPage();
      await rowIn('Cerca de la Puerta del Sol', '1a');

      geolocation.answerWith('pending');
      await user.click(screen.getByRole('button', { name: 'Mi ubicación' }));

      expect(screen.getByRole('button', { name: 'Localizando…' })).toBeInTheDocument();
      expect(
        screen.getByRole('heading', { level: 2, name: 'Cerca de la Puerta del Sol' }),
      ).toBeInTheDocument();
      expect(
        screen.getByText('Sin ubicación: mostrando estaciones cerca de la Puerta del Sol'),
      ).toBeInTheDocument();
      expect(lastMapProps()).toMatchObject({ focus: MADRID_CENTER });
    });
  });

  describe('bottom sheet below md', () => {
    beforeEach(() => stubGeolocation('granted'));

    it('starts collapsed on the list', async () => {
      await renderPage();
      expect(sheetHandle()).toHaveAttribute('aria-expanded', 'false');
    });

    it('starts expanded on a station link', async () => {
      await renderPage('/?station=1a');

      expect(sheetHandle()).toHaveAttribute('aria-expanded', 'true');
    });

    it('expands when a station is tapped on the map, and the handle still collapses it', async () => {
      const user = userEvent.setup();
      await renderPage();

      await act(async () => lastMapProps().onSelectPoint?.('1b'));

      expect(await detailTitle('1b · Puerta del Sol B')).toBeInTheDocument();
      expect(sheetHandle()).toHaveAttribute('aria-expanded', 'true');
      await user.click(sheetHandle());
      expect(sheetHandle()).toHaveAttribute('aria-expanded', 'false');
    });

    it('leaves the whole map to the focus while the sheet is collapsed', async () => {
      await renderPage();

      expect(lastMapProps().focusPadding).toEqual({ bottom: 0 });
    });

    it('centers the selected station above the expanded sheet, and in the whole map once collapsed', async () => {
      const user = userEvent.setup();
      await renderPage();

      await act(async () => lastMapProps().onSelectPoint?.('1b'));
      expect(await detailTitle('1b · Puerta del Sol B')).toBeInTheDocument();
      expect(lastMapProps().focusPadding?.bottom).toBeGreaterThan(0);

      await user.click(sheetHandle());
      expect(lastMapProps().focusPadding).toEqual({ bottom: 0 });
    });

    // jsdom ignores `inert`, so the row of the collapsed sheet can be clicked.
    it('expands when a row is chosen', async () => {
      const user = userEvent.setup();
      await renderPage();

      await user.click(await rowIn('Cerca de ti', '1b'));

      expect(await detailTitle('1b · Puerta del Sol B')).toBeInTheDocument();
      expect(sheetHandle()).toHaveAttribute('aria-expanded', 'true');
    });
  });

  describe('side panel from md', () => {
    beforeEach(() => {
      stubGeolocation('granted');
      stubMatchMedia(true);
    });

    it('centers the selected station in the whole map, the panel covers none of it', async () => {
      await renderPage('/?station=1a');

      expect(await detailTitle('1a · Puerta del Sol A')).toBeInTheDocument();
      expect(lastMapProps().focusPadding).toEqual({ bottom: 0 });
    });
  });
});
