import { act, renderHook } from '@testing-library/react';
import { stubMatchMedia } from '../../../../test/matchMedia';
import { useMediaQuery } from './useMediaQuery';

const QUERY = '(min-width: 48rem)';

describe('useMediaQuery', () => {
  it.each([true, false])('returns %s on the first render when the query matches %s', (matches) => {
    stubMatchMedia(matches);

    const { result } = renderHook(() => useMediaQuery(QUERY));

    expect(result.current).toBe(matches);
  });

  it('updates when the viewport crosses the breakpoint', () => {
    const media = stubMatchMedia(false);
    const { result } = renderHook(() => useMediaQuery(QUERY));

    act(() => media.change(true));

    expect(result.current).toBe(true);
  });

  it('stops listening on unmount', () => {
    const media = stubMatchMedia(false);
    const { unmount } = renderHook(() => useMediaQuery(QUERY));

    unmount();

    expect(media.listenerCount).toBe(0);
  });
});
