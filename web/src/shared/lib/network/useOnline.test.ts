import { onlineManager } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import { useOnline } from './useOnline';

describe('useOnline', () => {
  // onlineManager is a module-level singleton: restore it so other tests start online.
  afterEach(() => onlineManager.setOnline(true));

  it('starts online', () => {
    const { result } = renderHook(() => useOnline());

    expect(result.current).toBe(true);
  });

  it('turns false when the browser goes offline', () => {
    const { result } = renderHook(() => useOnline());

    act(() => {
      window.dispatchEvent(new Event('offline'));
    });

    expect(result.current).toBe(false);
  });

  it('turns true again when the browser comes back online', () => {
    const { result } = renderHook(() => useOnline());
    act(() => {
      window.dispatchEvent(new Event('offline'));
    });

    act(() => {
      window.dispatchEvent(new Event('online'));
    });

    expect(result.current).toBe(true);
  });
});
