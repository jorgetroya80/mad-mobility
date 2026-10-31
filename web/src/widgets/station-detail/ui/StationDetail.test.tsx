import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { Coordinates } from '@/entities/station';
import { staleStationsFixture, stationsFixture } from '@/shared/api/mocks/fixtures';
import { server } from '@/shared/api/mocks/node';
import { env, POLL_INTERVAL_MS } from '@/shared/config';
import { renderWithRouter } from '../../../../test/renderWithRouter';
import { StationDetail } from './StationDetail';

const STATION_URL = `${env.apiBaseUrl}/v1/bicimad/stations/:number`;
const SOL: Coordinates = { lat: 40.41702, lng: -3.70243 };
// Five minutes after the fixtures' `updatedAt` (10:15), 35 after the stale one (09:45).
const NOW = new Date('2026-10-13T10:20:00Z');
// The app's QueryClient retries once after react-query's 1 s backoff, which runs on the
// fake clock that `shouldAdvanceTime` moves along with real time.
const AFTER_RETRY = { timeout: 3_000 };
const RETRY_WINDOW_MS = 2_000;
const SERVER_ERROR = 500;
const [puertaDelSolA] = stationsFixture.stations;

type AppRouter = Awaited<ReturnType<typeof renderWithRouter>>['router'];

function respondStation(resolver: () => Response) {
  server.use(http.get(STATION_URL, resolver));
}

function stationJson(overrides: { updatedAt?: string; stale?: boolean }) {
  const { updatedAt, stale, source } = stationsFixture;
  respondStation(() =>
    HttpResponse.json({ station: puertaDelSolA, updatedAt, stale, source, ...overrides }),
  );
}

function failStation() {
  respondStation(() =>
    HttpResponse.json(
      { title: 'Internal Server Error', status: SERVER_ERROR },
      { status: SERVER_ERROR },
    ),
  );
}

function recordDetailRequests(): URL[] {
  const urls: URL[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (/\/stations\/[^/]+$/.test(url.pathname)) urls.push(url);
  });
  return urls;
}

function renderDetail(center?: Coordinates, initialEntries = ['/?station=1a']) {
  return renderWithRouter(<StationDetail center={center} />, { initialEntries });
}

function field(term: string): HTMLElement {
  const definition = screen.getByText(term, { selector: 'dt' }).nextElementSibling;
  if (!(definition instanceof HTMLElement)) throw new Error(`No value for ${term}`);
  return definition;
}

function searchParams(router: AppRouter) {
  return Object.fromEntries(new URL(router.state.location.href, 'http://localhost').searchParams);
}

async function openedTitle(name = '1a · Puerta del Sol A') {
  return screen.findByRole('heading', { level: 2, name });
}

describe('StationDetail', () => {
  let user: ReturnType<typeof userEvent.setup>;

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(NOW);
    user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  });

  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
    server.events.removeAllListeners();
  });

  it('shows the station data, a favorite button and the update time', async () => {
    await renderDetail();

    await openedTitle();

    expect(field('Dirección')).toHaveTextContent('Puerta del Sol nº 1');
    expect(field('Bicis')).toHaveTextContent('12 bicis');
    expect(field('Anclajes libres')).toHaveTextContent('12 anclajes libres');
    expect(field('Anclajes totales')).toHaveTextContent('24');
    expect(field('Estado')).toHaveTextContent('En servicio');
    expect(screen.getByRole('button', { name: 'Favorita' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
    expect(screen.getByText(/Actualizado/)).toHaveTextContent('Actualizado hace 5 minutos');
  });

  it('says when a station is out of service', async () => {
    await renderDetail(undefined, ['/?station=2']);

    await openedTitle('2 · Miguel Moya');

    expect(field('Estado')).toHaveTextContent('Sin servicio');
  });

  it('moves the focus to the title when it opens', async () => {
    await renderDetail();

    expect(await openedTitle()).toHaveFocus();
  });

  it('moves the focus to the title when another station opens', async () => {
    const { router } = await renderDetail();
    await openedTitle();
    screen.getByRole('button', { name: 'Favorita' }).focus();

    await act(() => router.navigate({ to: '/', search: { station: '3', need: 'bikes' } }));

    expect(await openedTitle('3 · Plaza Conde Suchil')).toHaveFocus();
  });

  it('shows the distance when the station is in the nearby list', async () => {
    await renderDetail(SOL);

    await openedTitle();

    expect(await screen.findByText('Distancia', { selector: 'dt' })).toBeInTheDocument();
    expect(field('Distancia')).toHaveTextContent('12 m');
  });

  it('hides the distance without a center', async () => {
    await renderDetail();

    await openedTitle();

    expect(screen.queryByText('Distancia', { selector: 'dt' })).not.toBeInTheDocument();
  });

  it('says the station does not exist after a single request (SC5)', async () => {
    const requests = recordDetailRequests();

    await renderDetail(undefined, ['/?station=nope']);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Estación no encontrada');
    await vi.advanceTimersByTimeAsync(RETRY_WINDOW_MS);
    expect(requests).toHaveLength(1);
  });

  it('goes back to the list from a missing station, keeping need', async () => {
    const { router } = await renderDetail(undefined, ['/?station=nope&need=docks']);

    const alert = await screen.findByRole('alert');
    await user.click(within(alert).getByRole('button', { name: 'Volver a la lista' }));

    expect(searchParams(router)).toEqual({ need: 'docks' });
  });

  it('goes back in history when the station was opened inside the app (SC6)', async () => {
    const { router } = await renderDetail(undefined, ['/']);
    await act(() => router.navigate({ to: '/', search: { station: '1a', need: 'bikes' } }));
    await openedTitle();

    await user.click(screen.getByRole('button', { name: 'Volver' }));

    await waitFor(() => expect(router.state.location.href).toBe('/'));
    expect(router.history.canGoBack()).toBe(false);
  });

  it('closes the detail keeping need when it was opened from a link', async () => {
    const { router } = await renderDetail(undefined, ['/?station=1a&need=docks']);
    await openedTitle();

    await user.click(screen.getByRole('button', { name: 'Volver' }));

    expect(router.state.location.href).toBe('/?need=docks');
  });

  it('warns when the EMT data is stale', async () => {
    stationJson({ updatedAt: staleStationsFixture.updatedAt, stale: true });

    await renderDetail();

    expect(await screen.findByText(/Sin confirmar/)).toHaveTextContent(
      'Sin confirmar: datos de hace 35 minutos',
    );
  });

  it('hides the update time when updatedAt is invalid', async () => {
    stationJson({ updatedAt: 'nope' });

    await renderDetail();
    await openedTitle();

    expect(screen.queryByText(/Actualizado/)).not.toBeInTheDocument();
  });

  it('shows an error and retries on demand', async () => {
    failStation();

    await renderDetail();

    const alert = await screen.findByRole('alert', {}, AFTER_RETRY);
    expect(alert).toHaveTextContent('No se pudo cargar la estación');

    server.resetHandlers();
    await user.click(within(alert).getByRole('button', { name: 'Reintentar' }));

    expect(await openedTitle()).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('keeps the data when a later refresh fails', async () => {
    await renderDetail();
    await openedTitle();

    failStation();
    await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);

    expect(await screen.findByText('No se pudo actualizar', {}, AFTER_RETRY)).toBeInTheDocument();
    expect(field('Bicis')).toHaveTextContent('12 bicis');
  });
});
