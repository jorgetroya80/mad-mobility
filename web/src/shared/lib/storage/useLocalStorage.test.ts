import { act, renderHook } from '@testing-library/react';
import { useLocalStorage } from './useLocalStorage';

const FALLBACK: string[] = [];

function parseStrings(raw: unknown): string[] | undefined {
  if (!Array.isArray(raw) || !raw.every((item) => typeof item === 'string')) return undefined;
  return raw;
}

let keyCount = 0;
// The in-memory copy kept when setItem fails is module-level: a fresh key isolates each test.
function freshKey(): string {
  keyCount += 1;
  return `test:key-${keyCount}`;
}

function renderStorage(key: string) {
  return renderHook(() => useLocalStorage(key, parseStrings, FALLBACK));
}

function spyOnConsole() {
  return [vi.spyOn(console, 'error'), vi.spyOn(console, 'warn')];
}

function failStorage(method: 'getItem' | 'setItem') {
  vi.spyOn(Storage.prototype, method).mockImplementation(() => {
    throw new DOMException('Storage is not available', 'SecurityError');
  });
}

describe('useLocalStorage', () => {
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('returns the fallback when nothing is stored', () => {
    const { result } = renderStorage(freshKey());

    expect(result.current[0]).toBe(FALLBACK);
  });

  it('reads a valid stored value', () => {
    const key = freshKey();
    localStorage.setItem(key, JSON.stringify(['538', '1a']));

    const { result } = renderStorage(key);

    expect(result.current[0]).toEqual(['538', '1a']);
  });

  it.each([
    ['corrupt JSON', '{not json'],
    ['an invalid shape', JSON.stringify({ favorites: ['538'] })],
    ['invalid items', JSON.stringify([538])],
  ])('returns the fallback for %s without console output', (_case, stored) => {
    const key = freshKey();
    localStorage.setItem(key, stored);
    const consoleSpies = spyOnConsole();

    const { result } = renderStorage(key);

    expect(result.current[0]).toBe(FALLBACK);
    for (const spy of consoleSpies) expect(spy).not.toHaveBeenCalled();
  });

  it('keeps the same value across renders while storage does not change', () => {
    const key = freshKey();
    localStorage.setItem(key, JSON.stringify(['538']));
    const { result, rerender } = renderStorage(key);
    const first = result.current[0];

    rerender();

    expect(result.current[0]).toBe(first);
  });

  it('persists a written value and re-renders with it', () => {
    const key = freshKey();
    const { result } = renderStorage(key);

    act(() => result.current[1](['538']));

    expect(result.current[0]).toEqual(['538']);
    expect(localStorage.getItem(key)).toBe(JSON.stringify(['538']));
  });

  it('keeps two hooks with the same key in sync', () => {
    const key = freshKey();
    const writer = renderStorage(key);
    const reader = renderStorage(key);

    act(() => writer.result.current[1](['538']));

    expect(reader.result.current[0]).toEqual(['538']);
  });

  it('follows changes made in another tab', () => {
    const key = freshKey();
    const { result } = renderStorage(key);

    act(() => {
      localStorage.setItem(key, JSON.stringify(['1a']));
      window.dispatchEvent(new StorageEvent('storage', { key }));
    });

    expect(result.current[0]).toEqual(['1a']);
  });

  it('keeps the written value in memory when setItem throws', () => {
    const key = freshKey();
    failStorage('setItem');
    const consoleSpies = spyOnConsole();
    const first = renderStorage(key);
    const second = renderStorage(key);

    act(() => first.result.current[1](['538']));

    expect(first.result.current[0]).toEqual(['538']);
    expect(second.result.current[0]).toEqual(['538']);
    for (const spy of consoleSpies) expect(spy).not.toHaveBeenCalled();
  });

  it('returns the fallback when getItem throws', () => {
    const key = freshKey();
    localStorage.setItem(key, JSON.stringify(['538']));
    failStorage('getItem');
    const consoleSpies = spyOnConsole();

    const { result } = renderStorage(key);

    expect(result.current[0]).toBe(FALLBACK);
    for (const spy of consoleSpies) expect(spy).not.toHaveBeenCalled();
  });
});
