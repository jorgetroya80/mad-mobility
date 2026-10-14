import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FAVORITES_STORAGE_KEY } from '@/shared/config';
import { FavoriteButton } from './FavoriteButton';

function favoriteButton() {
  return screen.getByRole('button', { name: 'Favorita' });
}

describe('FavoriteButton', () => {
  afterEach(() => localStorage.clear());

  it('is not pressed when the station is not a favorite', () => {
    render(<FavoriteButton number="538" />);

    expect(favoriteButton()).toHaveAttribute('aria-pressed', 'false');
  });

  it('is pressed when the station is already a favorite', () => {
    localStorage.setItem(FAVORITES_STORAGE_KEY, JSON.stringify(['538']));

    render(<FavoriteButton number="538" />);

    expect(favoriteButton()).toHaveAttribute('aria-pressed', 'true');
  });

  it('toggles on click and keeps the same accessible name', async () => {
    render(<FavoriteButton number="538" />);

    await userEvent.click(favoriteButton());
    expect(favoriteButton()).toHaveAttribute('aria-pressed', 'true');
    expect(localStorage.getItem(FAVORITES_STORAGE_KEY)).toBe('["538"]');

    await userEvent.click(favoriteButton());
    expect(favoriteButton()).toHaveAttribute('aria-pressed', 'false');
  });

  it('toggles with Enter and Space', async () => {
    render(<FavoriteButton number="538" />);
    favoriteButton().focus();

    await userEvent.keyboard('{Enter}');
    expect(favoriteButton()).toHaveAttribute('aria-pressed', 'true');

    await userEvent.keyboard(' ');
    expect(favoriteButton()).toHaveAttribute('aria-pressed', 'false');
  });

  it('hides the star from assistive technology', () => {
    render(<FavoriteButton number="538" />);

    expect(screen.getByText('☆')).toHaveAttribute('aria-hidden', 'true');
  });

  it('forwards native button props', () => {
    render(<FavoriteButton number="538" className="extra" data-testid="favorite" />);

    expect(screen.getByTestId('favorite')).toHaveClass('extra');
  });
});
