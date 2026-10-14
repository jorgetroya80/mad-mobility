import { act, renderHook } from '@testing-library/react';
import { useBottomSheetOverlap } from './useBottomSheetOverlap';

describe('useBottomSheetOverlap', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  // 60 % of 740 px minus the 44 px handle strip, which is already outside the content above.
  it('is the expanded sheet height minus the handle strip kept in the flow', () => {
    vi.stubGlobal('innerHeight', 740);

    const { result } = renderHook(() => useBottomSheetOverlap());

    expect(result.current).toBe(400);
  });

  it('follows the viewport height, rounded to whole pixels', () => {
    vi.stubGlobal('innerHeight', 740);
    const { result } = renderHook(() => useBottomSheetOverlap());

    act(() => {
      vi.stubGlobal('innerHeight', 768);
      window.dispatchEvent(new Event('resize'));
    });

    expect(result.current).toBe(417);
  });
});
