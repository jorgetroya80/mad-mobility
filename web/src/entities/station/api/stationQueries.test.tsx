import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { server } from '@/shared/api/mocks/node';
import {
  nearbyStationsFixture,
  staleStationsFixture,
  stationsFixture,
} from '@/shared/api/mocks/fixtures';
import {
  env,
  NEARBY_STORAGE_KEYS,
  POLL_INTERVAL_MS,
  REQUEST_TIMEOUT_MS,
  RETRY_DELAYS_MS,
  STATIONS_STORAGE_KEY,
} from '@/shared/config';
import { writeRaw } from '@/shared/lib';
import type { Need } from '../model/types';
import { StationNotFoundError } from './StationNotFoundError';
import { stationKeys } from './stationKeys';
import { useAllStations, useNearbyStations, useStation } from './stationQueries';

const STATIONS_URL = `${env.apiBaseUrl}/v1/bicimad/stations`;
const SOL = { lat: 40.41702, lng: -3.70243 };
// ~5 m north-east of SOL: same 4-decimal rounding (40.4170, -3.7024).
const NEAR_SOL = { lat: 40.41704, lng: -3.70238 };

// Same retry and background defaults as the app's QueryClient: the queries pace their own retries.
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: 0, refetchIntervalInBackground: false } },
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

const CACHE_KEYS = [STATIONS_STORAGE_KEY, NEARBY_STORAGE_KEYS.bikes, NEARBY_STORAGE_KEYS.docks];

// Every successful query saves its list: clear it so no test starts from another's cache.
afterEach(() => {
  server.events.removeAllListeners();
  vi.useRealTimers();
  vi.restoreAllMocks();
  // A failed write keeps an in-memory copy per key: a successful one drops it before clearing.
  for (const key of CACHE_KEYS) writeRaw(key, 'null');
  localStorage.clear();
});

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

  it('keeps the previous stations while another need or center loads', async () => {
    const urls = recordRequests();
    const { result, rerender } = renderHook(({ need }) => useNearbyStations(SOL, need), {
      wrapper: createWrapper(),
      initialProps: { need: 'bikes' as Need },
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    rerender({ need: 'docks' });

    expect(result.current.data).toEqual(nearbyStationsFixture);
    expect(result.current.isPlaceholderData).toBe(true);
    await waitFor(() => expect(result.current.isPlaceholderData).toBe(false));
    expect(urls.map((url) => url.searchParams.get('need'))).toEqual(['bikes', 'docks']);
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

  it('retries other failures on the next poll, not at once', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
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
    expect(urls).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(RETRY_DELAYS_MS[0]);
    await waitFor(() => expect(urls).toHaveLength(2));
  });

  it('names the request when the network fails', async () => {
    server.use(http.get(`${STATIONS_URL}/:number`, () => HttpResponse.error()));

    const { result } = renderHook(() => useStation('1a'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe(
      `GET ${new URL(`${STATIONS_URL}/1a`, location.href).href} failed`,
    );
  });
});

describe('request timeout', () => {
  afterEach(() => vi.restoreAllMocks());

  it('fails a request the API does not answer within 10 s', async () => {
    // AbortSignal.timeout runs on Node's own timers, out of reach of fake timers: the test fires it.
    const timeout = new AbortController();
    const timeoutSpy = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(timeout.signal);
    server.use(
      http.get(STATIONS_URL, async () => {
        await delay('infinite');
        return HttpResponse.json(stationsFixture);
      }),
    );
    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });
    await waitFor(() => expect(timeoutSpy).toHaveBeenCalledWith(REQUEST_TIMEOUT_MS));

    timeout.abort(new DOMException('The operation timed out.', 'TimeoutError'));

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toMatch(/^GET .+ failed$/);
  });
});

describe('polling and retries', () => {
  const [FIRST_RETRY_MS, SECOND_RETRY_MS, LAST_RETRY_MS] = RETRY_DELAYS_MS;
  // Fake time also advances with the real clock, so a wait is checked with a margin around it.
  const MARGIN_MS = 1000;

  function failStations() {
    server.use(http.get(STATIONS_URL, () => HttpResponse.error()));
  }

  function serveStations() {
    server.use(http.get(STATIONS_URL, () => HttpResponse.json(stationsFixture)));
  }

  // Checks that no request goes out until the wait is over, and that one does right after it.
  async function expectNextRequestAfter(urls: URL[], waitMs: number) {
    const before = urls.length;
    await vi.advanceTimersByTimeAsync(waitMs - MARGIN_MS);
    expect(urls).toHaveLength(before);
    await vi.advanceTimersByTimeAsync(MARGIN_MS);
    await vi.waitFor(() => expect(urls).toHaveLength(before + 1));
  }

  async function settled(result: { current: { fetchStatus: string } }) {
    await vi.waitFor(() => expect(result.current.fetchStatus).toBe('idle'));
  }

  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => focusManager.setFocused(undefined));

  it('polls every 30 s while the requests succeed', async () => {
    const urls = recordRequests();
    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });
    await vi.waitFor(() => expect(result.current.isSuccess).toBe(true));

    await expectNextRequestAfter(urls, POLL_INTERVAL_MS);
    await settled(result);
    await expectNextRequestAfter(urls, POLL_INTERVAL_MS);
  });

  it('waits 30, 60 and 120 s after consecutive failures, then stays at 120 s', async () => {
    failStations();
    const urls = recordRequests();
    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });
    await vi.waitFor(() => expect(result.current.isError).toBe(true));

    for (const waitMs of [FIRST_RETRY_MS, SECOND_RETRY_MS, LAST_RETRY_MS, LAST_RETRY_MS]) {
      await expectNextRequestAfter(urls, waitMs ?? 0);
      await settled(result);
    }
    expect(urls).toHaveLength(5);
  });

  it('goes back to every 30 s after a success', async () => {
    const urls = recordRequests();
    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });
    await vi.waitFor(() => expect(result.current.isSuccess).toBe(true));
    failStations();
    await expectNextRequestAfter(urls, POLL_INTERVAL_MS);
    await settled(result);
    await expectNextRequestAfter(urls, FIRST_RETRY_MS ?? 0);
    await settled(result);

    serveStations();
    await expectNextRequestAfter(urls, SECOND_RETRY_MS ?? 0);
    await vi.waitFor(() => expect(result.current.isError).toBe(false));

    await expectNextRequestAfter(urls, POLL_INTERVAL_MS);
  });

  it('asks at once on refetch and restarts the wait at 30 s', async () => {
    failStations();
    const urls = recordRequests();
    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });
    await vi.waitFor(() => expect(result.current.isError).toBe(true));
    await expectNextRequestAfter(urls, FIRST_RETRY_MS ?? 0);
    await settled(result);
    await expectNextRequestAfter(urls, SECOND_RETRY_MS ?? 0);
    await settled(result);

    // The next wait would be 120 s; a failed manual retry starts the sequence again.
    await act(async () => {
      await result.current.refetch();
    });
    expect(urls).toHaveLength(4);

    await expectNextRequestAfter(urls, FIRST_RETRY_MS ?? 0);
  });

  it('does not poll while the tab is hidden', async () => {
    const urls = recordRequests();
    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });
    await vi.waitFor(() => expect(result.current.isSuccess).toBe(true));

    act(() => focusManager.setFocused(false));
    await vi.advanceTimersByTimeAsync(3 * POLL_INTERVAL_MS);

    expect(urls).toHaveLength(1);
  });
});

describe('stations cache', () => {
  const HOUR_MS = 3_600_000;
  const cachedList = { ...stationsFixture, stations: stationsFixture.stations.slice(0, 1) };

  function store(key: string, value: unknown) {
    localStorage.setItem(key, JSON.stringify(value));
  }

  function hangStations() {
    server.use(
      http.get(STATIONS_URL, async () => {
        await delay('infinite');
        return HttpResponse.json(stationsFixture);
      }),
    );
  }

  // The app's staleTime: a cache older than it is asked for again on mount.
  function createAppWrapper() {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: 0, staleTime: POLL_INTERVAL_MS } },
    });
    return function Wrapper({ children }: { children: ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
    };
  }

  it('saves every successful list under its key', async () => {
    const wrapper = createWrapper();
    const all = renderHook(() => useAllStations(), { wrapper });
    const bikes = renderHook(() => useNearbyStations(SOL, 'bikes'), { wrapper });
    const docks = renderHook(() => useNearbyStations(SOL, 'docks'), { wrapper });

    await waitFor(() => expect(all.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(bikes.result.current.isSuccess).toBe(true));
    await waitFor(() => expect(docks.result.current.isSuccess).toBe(true));

    expect(JSON.parse(localStorage.getItem(STATIONS_STORAGE_KEY) ?? 'null')).toMatchObject({
      response: stationsFixture,
    });
    for (const need of ['bikes', 'docks'] as const) {
      expect(JSON.parse(localStorage.getItem(NEARBY_STORAGE_KEYS[need]) ?? 'null')).toMatchObject({
        near: '40.4170,-3.7024',
        response: nearbyStationsFixture,
      });
    }
  });

  it('starts from the saved list without waiting for the network, and asks again', async () => {
    const savedAt = Date.now() - HOUR_MS;
    store(STATIONS_STORAGE_KEY, { response: cachedList, savedAt });
    hangStations();
    const urls = recordRequests();

    const { result } = renderHook(() => useAllStations(), { wrapper: createAppWrapper() });

    expect(result.current.data).toEqual(cachedList);
    expect(result.current.dataUpdatedAt).toBe(savedAt);
    await waitFor(() => expect(urls).toHaveLength(1));
    expect(result.current.data).toEqual(cachedList);
  });

  it('ignores a saved list with the wrong shape', async () => {
    store(STATIONS_STORAGE_KEY, { response: { stations: 'nope' }, savedAt: Date.now() });
    hangStations();

    const { result } = renderHook(() => useAllStations(), { wrapper: createAppWrapper() });

    expect(result.current.data).toBeUndefined();
    expect(result.current.isPending).toBe(true);
  });

  it('keeps the response when the storage refuses the write', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Quota exceeded', 'QuotaExceededError');
    });

    const { result } = renderHook(() => useAllStations(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(stationsFixture);
  });

  it('shows the saved nearby list of the need while its own list loads, even for another center', async () => {
    store(NEARBY_STORAGE_KEYS.bikes, {
      near: '40.4000,-3.7000',
      response: cachedList,
      savedAt: Date.now(),
    });
    hangStations();

    const { result } = renderHook(() => useNearbyStations(SOL, 'bikes'), {
      wrapper: createWrapper(),
    });

    expect(result.current.data).toEqual(cachedList);
    expect(result.current.isPlaceholderData).toBe(true);
  });

  it('prefers the previous list of the same hook over the saved one', async () => {
    store(NEARBY_STORAGE_KEYS.docks, { near: '40.4170,-3.7024', response: cachedList, savedAt: 0 });
    const { result, rerender } = renderHook(({ need }) => useNearbyStations(SOL, need), {
      wrapper: createWrapper(),
      initialProps: { need: 'bikes' as Need },
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    hangStations();

    rerender({ need: 'docks' });

    expect(result.current.isPlaceholderData).toBe(true);
    expect(result.current.data).toEqual(nearbyStationsFixture);
  });
});
