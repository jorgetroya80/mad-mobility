import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { Station, StationsResponse } from '@jorgetroya80/bicimad-client';
import { stationsFixture } from '@/shared/api/mocks/fixtures';
import { server } from '@/shared/api/mocks/node';
import { env, SEARCH_MAX_RESULTS } from '@/shared/config';
import { renderWithRouter } from '../../../../test/renderWithRouter';
import { TransitSearch } from './TransitSearch';

const STATIONS_URL = `${env.apiBaseUrl}/v1/bicimad/stations`;
const UNAVAILABLE = 503;
// The app's QueryClient retries once after react-query's 1 s backoff.
const AFTER_RETRY = { timeout: 3_000 };

function fixtureStation(): Station {
  const [station] = stationsFixture.stations;
  if (!station) throw new Error('The fixtures need at least one station');
  return station;
}

// More stations starting with '1' than fit in the results, listed out of order on purpose.
const MANY_NUMBERS = ['19', '2', '1b', '100', '12', '1a', '15', '11', '3', '13', '10', '14', '1'];

function stationNumbered(number: string, index: number): Station {
  return { ...fixtureStation(), id: index, number, name: `Estación ${number}` };
}

function respondAll(resolver: () => Response | Promise<Response>) {
  server.use(http.get(STATIONS_URL, resolver));
}

function serveStations(stations: Station[]) {
  const body: StationsResponse = { ...stationsFixture, stations };
  respondAll(() => HttpResponse.json(body));
}

function searchBox() {
  return screen.getByRole('searchbox', { name: 'Buscar estación por número' });
}

function resultNumbers() {
  return screen
    .queryAllByRole('listitem')
    .map((item) => item.querySelector('button')?.dataset.number);
}

function status() {
  return screen.getByRole('status');
}

async function renderSearch() {
  const user = userEvent.setup();
  const view = await renderWithRouter(<TransitSearch />);
  return { user, ...view };
}

// Typing before the stations arrive would only show "Cargando…".
async function renderLoadedSearch() {
  const view = await renderSearch();
  await view.user.type(searchBox(), '1');
  await screen.findByRole('list');
  await view.user.clear(searchBox());
  return view;
}

describe('TransitSearch', () => {
  it('searches BiciMAD stations without a service picker', async () => {
    await renderSearch();

    expect(searchBox()).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Servicio' })).not.toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  });

  it('lists stations by number prefix, in numeric order and at most the maximum', async () => {
    serveStations(MANY_NUMBERS.map(stationNumbered));
    const { user } = await renderLoadedSearch();

    await user.type(searchBox(), '1');

    expect(resultNumbers()).toEqual(['1', '1a', '1b', '10', '11', '12', '13', '14']);
    expect(resultNumbers()).toHaveLength(SEARCH_MAX_RESULTS);
  });

  it('shows the name and address of each station', async () => {
    const { user } = await renderLoadedSearch();

    await user.type(searchBox(), '1a');

    const [result] = within(screen.getByRole('list')).getAllByRole('button');
    expect(result).toHaveAccessibleName('1a, Puerta del Sol A, Puerta del Sol nº 1');
  });

  it.each(['1A', ' 1a '])('finds 1a when typing "%s"', async (typed) => {
    const { user } = await renderLoadedSearch();

    await user.type(searchBox(), typed);

    expect(resultNumbers()).toEqual(['1a']);
  });

  it('finds nothing by name', async () => {
    const { user } = await renderLoadedSearch();

    await user.type(searchBox(), 'sol');

    expect(resultNumbers()).toEqual([]);
    expect(status()).toHaveTextContent('Ninguna estación con el número sol');
  });

  it('selects the clicked station in the URL', async () => {
    const { user, router } = await renderLoadedSearch();

    await user.type(searchBox(), '1');
    await user.click(screen.getByRole('button', { name: /^1b,/ }));

    await waitFor(() => expect(router.state.location.search).toEqual({ station: '1b' }));
    expect(searchBox()).toHaveValue('');
  });

  it('selects the exact match on Enter', async () => {
    const { user, router } = await renderLoadedSearch();

    await user.type(searchBox(), '1A{Enter}');

    await waitFor(() => expect(router.state.location.search).toEqual({ station: '1a' }));
  });

  it('says it is loading while the stations arrive', async () => {
    let release = () => {};
    const released = new Promise<void>((resolve) => {
      release = resolve;
    });
    respondAll(async () => {
      await released;
      return HttpResponse.json(stationsFixture);
    });
    const { user } = await renderSearch();

    await user.type(searchBox(), '1');

    expect(status()).toHaveTextContent('Cargando estaciones…');
    await act(async () => release());
    expect(await screen.findByRole('list')).toBeInTheDocument();
  });

  it('says the stations could not be loaded when the request fails', async () => {
    respondAll(() =>
      HttpResponse.json(
        { title: 'Service Unavailable', status: UNAVAILABLE },
        { status: UNAVAILABLE },
      ),
    );
    const { user } = await renderSearch();

    await user.type(searchBox(), '1');

    expect(
      await screen.findByText('No se pudieron cargar las estaciones', {}, AFTER_RETRY),
    ).toBeInTheDocument();
    expect(resultNumbers()).toEqual([]);
  });
});
