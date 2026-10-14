import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { server } from '@/shared/api/mocks/node';
import {
  nearbyStationsFixture,
  staleStationsFixture,
  stationsFixture,
} from '@/shared/api/mocks/fixtures';
import { env, QUERY_RETRY_COUNT } from '@/shared/config';
import { StationNotFoundError } from './StationNotFoundError';
import { stationKeys } from './stationKeys';
import { useAllStations, useNearbyStations, useStation } from './stationQueries';

const STATIONS_URL = `${env.apiBaseUrl}/v1/bicimad/stations`;
const SOL = { lat: 40.41702, lng: -3.70243 };
// ~5 m north-east of SOL: same 4-decimal rounding (40.4170, -3.7024).
const NEAR_SOL = { lat: 40.41704, lng: -3.70238 };

// Same retry count as the app's QueryClient, without the backoff delay.
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: QUERY_RETRY_COUNT, retryDelay: 0 } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

function recordRequests(): URL[] {
  const urls: URL[] = [];
  server.events.on('request:start', ({ request }) => {
    urls.push(new URL(request.url));
  });
  return urls;
}

afterEach(() => server.events.removeAllListeners());

describe('stationKeys', () => {
  it('shares the nearby key between centers that round to the same 4 decimals', () => {
    expect(stationKeys.nearby(SOL, 'bikes')).toEqual(stationKeys.nearby(NEAR_SOL, 'bikes'));
  });

  it('separates the nearby key by need', () => {
    expect(stationKeys.nearby(SOL, 'bikes')).not.toEqual(stationKeys.nearby(SOL, 'docks'));
  });
});

describe('useAllStations', () => {
  it('returns every station with its freshness', async () => {
    const urls = recordRequests();

    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(stationsFixture);
    expect(result.current.data?.stale).toBe(false);
    expect(result.current.data?.updatedAt).toBe(stationsFixture.updatedAt);
    expect(urls.map((url) => url.search)).toEqual(['']);
  });

  it('passes stale data on to the consumer', async () => {
    server.use(http.get(STATIONS_URL, () => HttpResponse.json(staleStationsFixture)));

    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.stale).toBe(true);
    expect(result.current.data?.updatedAt).toBe(staleStationsFixture.updatedAt);
  });
});

describe('useNearbyStations', () => {
  it('asks for the stations around the rounded center for the need', async () => {
    const urls = recordRequests();

    const { result } = renderHook(() => useNearbyStations(SOL, 'docks'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(nearbyStationsFixture);
    expect(urls).toHaveLength(1);
    expect(Object.fromEntries(urls[0]?.searchParams ?? [])).toEqual({
      near: '40.4170,-3.7024',
      radius: '500',
      need: 'docks',
    });
  });

  it('waits for a center before asking', async () => {
    const urls = recordRequests();

    const { result } = renderHook(() => useNearbyStations(undefined, 'bikes'), {
      wrapper: createWrapper(),
    });

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.data).toBeUndefined();
    expect(urls).toHaveLength(0);
  });

  it('reuses the cached result for a center less than 11 m away', async () => {
    const urls = recordRequests();
    const wrapper = createWrapper();
    const first = renderHook(() => useNearbyStations(SOL, 'bikes'), { wrapper });
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));

    const second = renderHook(() => useNearbyStations(NEAR_SOL, 'bikes'), { wrapper });

    expect(second.result.current.data).toEqual(nearbyStationsFixture);
    expect(urls).toHaveLength(1);
  });
});

describe('useStation', () => {
  it('asks for the station by its number', async () => {
    const urls = recordRequests();

    const { result } = renderHook(() => useStation('1A'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(urls.map((url) => url.pathname)).toEqual([`${STATIONS_URL}/1A`]);
    expect(result.current.data?.station.number).toBe('1a');
    expect(result.current.data?.stale).toBe(false);
  });

  it('waits for a number before asking', async () => {
    const urls = recordRequests();

    const { result } = renderHook(() => useStation(undefined), { wrapper: createWrapper() });

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(result.current.fetchStatus).toBe('idle');
    expect(urls).toHaveLength(0);
  });

  it('reports an unknown number as not found without retrying', async () => {
    const urls = recordRequests();

    const { result } = renderHook(() => useStation('nope'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(StationNotFoundError);
    expect(result.current.error).toMatchObject({ number: 'nope' });
    expect(urls).toHaveLength(1);
  });

  it('retries other failures once', async () => {
    const urls = recordRequests();
    server.use(
      http.get(`${STATIONS_URL}/:number`, () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Internal Server Error',
            status: 500,
            instance: '/v1/bicimad/stations/1a',
            requestId: 'test',
          },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    const { result } = renderHook(() => useStation('1a'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).not.toBeInstanceOf(StationNotFoundError);
    expect(urls).toHaveLength(2);
  });
});
