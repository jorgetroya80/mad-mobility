import { act, renderHook } from '@testing-library/react';
import { useViewportHeight } from './useViewportHeight';

function resizeTo(height: number) {
  vi.stubGlobal('innerHeight', height);
  window.dispatchEvent(new Event('resize'));
}

describe('useViewportHeight', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('returns the viewport height on the first render', () => {
    vi.stubGlobal('innerHeight', 740);

    const { result } = renderHook(() => useViewportHeight());

    expect(result.current).toBe(740);
  });

  it('updates when the viewport is resized or rotated', () => {
    vi.stubGlobal('innerHeight', 740);
    const { result } = renderHook(() => useViewportHeight());

    act(() => resizeTo(360));

    expect(result.current).toBe(360);
  });

  it('stops listening on unmount', () => {
    const removeEventListener = vi.spyOn(window, 'removeEventListener');
    const { unmount } = renderHook(() => useViewportHeight());

    unmount();

    expect(removeEventListener).toHaveBeenCalledWith('resize', expect.any(Function));
  });
});
