import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { StationsResponse } from '@jorgetroya80/bicimad-client';
import type { Coordinates } from '@/entities/station';
import {
  nearbyStationsFixture,
  staleStationsFixture,
  stationsFixture,
} from '@/shared/api/mocks/fixtures';
import { server } from '@/shared/api/mocks/node';
import { env, FAVORITES_STORAGE_KEY, POLL_INTERVAL_MS } from '@/shared/config';
import { renderWithRouter } from '../../../../test/renderWithRouter';
import { StationList } from './StationList';

const STATIONS_URL = `${env.apiBaseUrl}/v1/bicimad/stations`;
const SOL: Coordinates = { lat: 40.41702, lng: -3.70243 };
// Five minutes after the fixtures' `updatedAt` (10:15), 35 after the stale one (09:45).
const NOW = new Date('2026-10-13T10:20:00Z');
// The app's QueryClient retries once after react-query's 1 s backoff, which runs on the
// fake clock that `shouldAdvanceTime` moves along with real time.
const AFTER_RETRY = { timeout: 3_000 };
const UNAVAILABLE = 503;

function respondNearby(resolver: () => Response) {
  server.use(
    http.get(STATIONS_URL, ({ request }) =>
      new URL(request.url).searchParams.has('near')
        ? resolver()
        : HttpResponse.json(stationsFixture),
    ),
  );
}

function nearbyJson(body: StationsResponse) {
  respondNearby(() => HttpResponse.json(body));
}

function failNearby() {
  respondNearby(() =>
    HttpResponse.json(
      { title: 'Service Unavailable', status: UNAVAILABLE },
      { status: UNAVAILABLE },
    ),
  );
}

function recordNearbyRequests(): URL[] {
  const urls: URL[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.searchParams.has('near')) urls.push(url);
  });
  return urls;
}

function recordAllRequests(): URL[] {
  const urls: URL[] = [];
  server.events.on('request:start', ({ request }) => urls.push(new URL(request.url)));
  return urls;
}

function saveFavorites(numbers: string[]) {
  localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(numbers));
}

type ListProps = { center: Coordinates | undefined; isApproximateLocation: boolean };

const LOCATED: ListProps = { center: SOL, isApproximateLocation: false };

function renderList(props: Partial<ListProps> = {}, initialEntries?: string[]) {
  return renderWithRouter(<StationList {...LOCATED} {...props} />, { initialEntries });
}

async function nearbyRegion(name = 'Cerca de ti') {
  return screen.findByRole('region', { name });
}

function rowNames(region: HTMLElement): string[] {
  return within(region)
    .getAllByRole('button', { name: /^Estación/ })
    .map((row) => row.getAttribute('aria-label') ?? '');
}

describe('StationList', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    server.events.removeAllListeners();
  });

  it('lists the nearby stations in API order with number, name, distance and availability (SC1)', async () => {
    await renderList();

    const region = await nearbyRegion();
    await within(region).findAllByRole('button', { name: /^Estación/ });

    expect(rowNames(region)).toEqual([
      'Estación 1a, Puerta del Sol A, a 12 m, 12 bicis',
      'Estación 1b, Puerta del Sol B, a 18 m, 2 bicis',
      'Estación 2, Miguel Moya, a 420 m, Sin servicio',
    ]);
    expect(within(region).getByRole('heading', { level: 2 })).toHaveTextContent('Cerca de ti');
    expect(screen.queryByText(/Sin ubicación/)).not.toBeInTheDocument();
  });

  it('tags each row with its station number', async () => {
    await renderList();

    const region = await nearbyRegion();
    const [first] = await within(region).findAllByRole('button', { name: /^Estación/ });

    expect(first).toHaveAttribute('data-station-number', '1a');
  });

  it('warns about the approximate location and names the fallback place', async () => {
    await renderList({ isApproximateLocation: true });

    expect(
      screen.getByText('Sin ubicación: mostrando estaciones cerca de la Puerta del Sol'),
    ).toBeInTheDocument();
    expect(await nearbyRegion('Cerca de la Puerta del Sol')).toBeInTheDocument();
  });

  it('shows loading without asking for nearby stations while the location is pending', async () => {
    const all = recordAllRequests();

    await renderList({ center: undefined });

    expect(screen.getByRole('status')).toHaveTextContent('Cargando estaciones cercanas');
    await waitFor(() => expect(all).toHaveLength(1));
    expect(all.some((url) => url.searchParams.has('near'))).toBe(false);
  });

  it('says so when no station is within the radius', async () => {
    nearbyJson({ ...nearbyStationsFixture, stations: [] });

    await renderList();

    expect(await screen.findByText('Ninguna estación en 500 m')).toBeInTheDocument();
  });

  it('shows an error without data and retries on demand', async () => {
    const requests = recordNearbyRequests();
    failNearby();
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    await renderList();

    const alert = await screen.findByRole('alert', {}, AFTER_RETRY);
    expect(alert).toHaveTextContent('No se pudieron cargar las estaciones cercanas');
    const attemptsBeforeRetry = requests.length;

    nearbyJson(nearbyStationsFixture);
    await user.click(within(alert).getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('button', { name: /^Estación 1a/ })).toBeInTheDocument();
    expect(requests.length).toBeGreaterThan(attemptsBeforeRetry);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('keeps the rows when a later refresh fails', async () => {
    await renderList();
    await screen.findByRole('button', { name: /^Estación 1a/ });

    failNearby();
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);

    expect(await screen.findByText('No se pudo actualizar', {}, AFTER_RETRY)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Estación 1a/ })).toBeInTheDocument();
    expect(screen.getByText(/Actualizado/)).toBeInTheDocument();
  });

  it('warns when the EMT data is stale', async () => {
    nearbyJson({
      ...nearbyStationsFixture,
      updatedAt: staleStationsFixture.updatedAt,
      stale: true,
    });

    await renderList();

    expect(await screen.findByText(/la EMT no responde/)).toHaveTextContent(
      'Datos de hace 35 minutos: la EMT no responde',
    );
  });

  it('warns about stale data without a time when updatedAt is invalid', async () => {
    nearbyJson({ ...nearbyStationsFixture, updatedAt: 'nope', stale: true });

    await renderList();

    expect(await screen.findByText(/la EMT no responde/)).toHaveTextContent(
      'Datos antiguos: la EMT no responde',
    );
  });

  it('does not warn about fresh data', async () => {
    await renderList();
    await screen.findByRole('button', { name: /^Estación 1a/ });

    expect(screen.queryByText(/la EMT no responde/)).not.toBeInTheDocument();
  });

  it('shows how long ago the nearby list was updated', async () => {
    await renderList();

    const region = await nearbyRegion();

    expect(await within(region).findByText(/Actualizado/)).toHaveTextContent(
      'Actualizado hace 5 minutos',
    );
  });

  it('hides the update time when updatedAt is invalid', async () => {
    nearbyJson({ ...nearbyStationsFixture, updatedAt: 'nope' });

    await renderList();
    await screen.findByRole('button', { name: /^Estación 1a/ });

    expect(screen.queryByText(/Actualizado/)).not.toBeInTheDocument();
  });

  it('shows favorites on top in insertion order, skipping unknown numbers (SC8)', async () => {
    saveFavorites(['3', '99', '1b']);

    await renderList();

    const favorites = await screen.findByRole('region', { name: 'Favoritas' });
    await within(favorites).findAllByRole('button', { name: /^Estación/ });

    expect(rowNames(favorites)).toEqual([
      'Estación 3, Plaza Conde Suchil, 0 bicis',
      'Estación 1b, Puerta del Sol B, 2 bicis',
    ]);
    const regions = screen.getAllByRole('region');
    expect(regions.indexOf(favorites)).toBeLessThan(regions.indexOf(await nearbyRegion()));
  });

  it('has no favorites section without favorites', async () => {
    await renderList();
    await screen.findByRole('button', { name: /^Estación 1a/ });

    expect(screen.queryByRole('region', { name: 'Favoritas' })).not.toBeInTheDocument();
  });

  it('has no favorites section when none of them exists', async () => {
    saveFavorites(['99']);

    await renderList();
    await screen.findByRole('button', { name: /^Estación 1a/ });

    expect(screen.queryByRole('region', { name: 'Favoritas' })).not.toBeInTheDocument();
  });

  it('selects the station of a clicked row', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const { router } = await renderList();

    await user.click(await screen.findByRole('button', { name: /^Estación 1b/ }));

    expect(router.state.location.href).toBe('/?station=1b');
  });

  it('asks for and shows free docks with need=docks', async () => {
    const requests = recordNearbyRequests();

    await renderList({}, ['/?need=docks']);

    const region = await nearbyRegion();
    await within(region).findAllByRole('button', { name: /^Estación/ });

    expect(requests.map((url) => url.searchParams.get('need'))).toEqual(['docks']);
    expect(rowNames(region)).toEqual([
      'Estación 1a, Puerta del Sol A, a 12 m, 12 anclajes libres',
      'Estación 1b, Puerta del Sol B, a 18 m, 22 anclajes libres',
      'Estación 2, Miguel Moya, a 420 m, Sin servicio',
    ]);
  });
});
