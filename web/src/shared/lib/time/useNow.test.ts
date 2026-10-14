import { act, renderHook } from '@testing-library/react';
import { useNow } from './useNow';

const INTERVAL_MS = 30_000;
const START = new Date('2026-10-14T10:00:00Z');

describe('useNow', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(START);
  });
  afterEach(() => vi.useRealTimers());

  it('returns the current time', () => {
    const { result } = renderHook(() => useNow(INTERVAL_MS));

    expect(result.current).toEqual(START);
  });

  it('renews the time every interval', () => {
    const { result } = renderHook(() => useNow(INTERVAL_MS));

    act(() => vi.advanceTimersByTime(INTERVAL_MS));

    expect(result.current).toEqual(new Date(START.getTime() + INTERVAL_MS));
  });

  it('keeps the time between ticks', () => {
    const { result } = renderHook(() => useNow(INTERVAL_MS));

    act(() => vi.advanceTimersByTime(INTERVAL_MS - 1));

    expect(result.current).toEqual(START);
  });

  it('clears the interval on unmount', () => {
    const { unmount } = renderHook(() => useNow(INTERVAL_MS));

    unmount();

    expect(vi.getTimerCount()).toBe(0);
  });
});
