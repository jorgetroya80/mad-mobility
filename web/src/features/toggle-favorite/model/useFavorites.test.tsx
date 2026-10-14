import { act, render, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FAVORITES_STORAGE_KEY } from '@/shared/config';
import { useFavorites } from './useFavorites';

function storedFavorites(): unknown {
  return JSON.parse(localStorage.getItem(FAVORITES_STORAGE_KEY) ?? 'null');
}

function FavoriteCount() {
  const { favorites } = useFavorites();
  return <p>{favorites.length} favoritas</p>;
}

function ToggleStation({ number }: { number: string }) {
  const { toggle } = useFavorites();
  return (
    <button type="button" onClick={() => toggle(number)}>
      Cambiar {number}
    </button>
  );
}

describe('useFavorites', () => {
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('starts with no favorites', () => {
    const { result } = renderHook(() => useFavorites());

    expect(result.current.favorites).toEqual([]);
    expect(result.current.isFavorite('538')).toBe(false);
  });

  it('adds and removes a station, keeping insertion order', () => {
    const { result } = renderHook(() => useFavorites());

    act(() => result.current.toggle('538'));
    act(() => result.current.toggle('25A'));
    act(() => result.current.toggle('1a'));
    act(() => result.current.toggle('25A'));

    expect(result.current.favorites).toEqual(['538', '1a']);
    expect(result.current.isFavorite('538')).toBe(true);
    expect(result.current.isFavorite('25A')).toBe(false);
  });

  it('persists as a JSON array that survives a remount', () => {
    const first = renderHook(() => useFavorites());
    act(() => first.result.current.toggle('538'));
    first.unmount();

    const { result } = renderHook(() => useFavorites());

    expect(storedFavorites()).toEqual(['538']);
    expect(result.current.favorites).toEqual(['538']);
  });

  it.each([
    ['corrupt JSON', '{not json'],
    ['an invalid shape', JSON.stringify({ favorites: ['538'] })],
  ])('ignores %s without console output', (_case, stored) => {
    localStorage.setItem(FAVORITES_STORAGE_KEY, stored);
    const consoleSpies = [vi.spyOn(console, 'error'), vi.spyOn(console, 'warn')];

    const { result } = renderHook(() => useFavorites());

    expect(result.current.favorites).toEqual([]);
    for (const spy of consoleSpies) expect(spy).not.toHaveBeenCalled();
  });

  it('replaces a corrupt value on the next toggle', () => {
    localStorage.setItem(FAVORITES_STORAGE_KEY, '{not json');
    const { result } = renderHook(() => useFavorites());

    act(() => result.current.toggle('538'));

    expect(storedFavorites()).toEqual(['538']);
  });

  it('keeps two components in sync', async () => {
    render(
      <>
        <FavoriteCount />
        <ToggleStation number="538" />
      </>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Cambiar 538' }));

    expect(screen.getByText('1 favoritas')).toBeInTheDocument();
  });
});
