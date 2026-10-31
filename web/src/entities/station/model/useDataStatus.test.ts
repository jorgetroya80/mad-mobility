import { onlineManager } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { NOW_TICK_MS, STALE_AFTER_MS } from '@/shared/config';
import { useDataStatus } from './useDataStatus';

const START = new Date('2026-10-31T10:00:00Z');

type Query = Parameters<typeof useDataStatus>[0];

function query(overrides: Partial<Query> = {}): Query {
  return {
    data: { updatedAt: START.toISOString(), stale: false },
    isError: false,
    dataUpdatedAt: START.getTime(),
    refetch: vi.fn<() => void>(),
    ...overrides,
  };
}

describe('useDataStatus', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(START);
  });
  afterEach(() => {
    vi.useRealTimers();
    onlineManager.setOnline(true);
  });

  it('reports fresh data as ok with the API date', () => {
    const { result } = renderHook(() => useDataStatus(query()));

    expect(result.current.status).toBe('ok');
    expect(result.current.updatedAt).toEqual(START);
  });

  it('turns stale once the data is over 5 min old without new data', () => {
    const { result } = renderHook(() => useDataStatus(query()));

    // The clock ticks every NOW_TICK_MS: the first tick past the limit flips the status.
    act(() => vi.advanceTimersByTime(STALE_AFTER_MS));
    expect(result.current.status).toBe('ok');

    act(() => vi.advanceTimersByTime(NOW_TICK_MS));
    expect(result.current.status).toBe('stale');
  });

  it('reports stale when the API marks the data stale', () => {
    const { result } = renderHook(() =>
      useDataStatus(query({ data: { updatedAt: START.toISOString(), stale: true } })),
    );

    expect(result.current.status).toBe('stale');
  });

  it('reports stale when a refresh fails with data', () => {
    const { result } = renderHook(() => useDataStatus(query({ isError: true })));

    expect(result.current.status).toBe('stale');
  });

  it.each([
    { isError: false, status: 'loading' },
    { isError: true, status: 'error' },
  ])('reports $status without data when isError is $isError', ({ isError, status }) => {
    const { result } = renderHook(() =>
      useDataStatus(query({ data: undefined, isError, dataUpdatedAt: 0 })),
    );

    expect(result.current.status).toBe(status);
    expect(result.current.updatedAt).toBeUndefined();
  });

  it('reports offline when the browser loses the connection', () => {
    const { result } = renderHook(() => useDataStatus(query()));

    act(() => onlineManager.setOnline(false));

    expect(result.current.status).toBe('offline');
  });

  it('falls back to the response time when the API date is unreadable', () => {
    const received = new Date(START.getTime() - 1000);
    const { result } = renderHook(() =>
      useDataStatus(
        query({ data: { updatedAt: 'nope', stale: false }, dataUpdatedAt: received.getTime() }),
      ),
    );

    expect(result.current.updatedAt).toEqual(received);
  });

  it('leaves the date unknown for placeholder data without a readable API date', () => {
    const { result } = renderHook(() =>
      useDataStatus(query({ data: { updatedAt: 'nope', stale: false }, dataUpdatedAt: 0 })),
    );

    expect(result.current.updatedAt).toBeUndefined();
  });

  it('retries by refetching', () => {
    const refetch = vi.fn<() => void>();
    const { result } = renderHook(() => useDataStatus(query({ refetch })));

    result.current.retry();

    expect(refetch).toHaveBeenCalledOnce();
  });
});
