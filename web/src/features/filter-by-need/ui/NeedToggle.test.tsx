import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithRouter } from '../../../../test/renderWithRouter';
import { NeedToggle } from './NeedToggle';

type AppRouter = Awaited<ReturnType<typeof renderWithRouter>>['router'];

function searchParams(router: AppRouter) {
  return Object.fromEntries(new URL(router.state.location.href, 'http://localhost').searchParams);
}

function toggleButton(name: 'Bicis' | 'Anclajes') {
  return screen.getByRole('button', { name });
}

describe('NeedToggle', () => {
  it('groups both options under an accessible name', async () => {
    await renderWithRouter(<NeedToggle />);

    expect(screen.getByRole('group', { name: 'Buscar' })).toContainElement(
      toggleButton('Anclajes'),
    );
  });

  it('presses "Bicis" by default', async () => {
    await renderWithRouter(<NeedToggle />);

    expect(toggleButton('Bicis')).toHaveAttribute('aria-pressed', 'true');
    expect(toggleButton('Anclajes')).toHaveAttribute('aria-pressed', 'false');
  });

  it('presses "Anclajes" with ?need=docks', async () => {
    await renderWithRouter(<NeedToggle />, { initialEntries: ['/?need=docks'] });

    expect(toggleButton('Anclajes')).toHaveAttribute('aria-pressed', 'true');
    expect(toggleButton('Bicis')).toHaveAttribute('aria-pressed', 'false');
  });

  it('writes need=docks without a new history entry and keeps the station', async () => {
    const { router } = await renderWithRouter(<NeedToggle />, {
      initialEntries: ['/?station=538'],
    });
    const entries = router.history.length;

    await userEvent.click(toggleButton('Anclajes'));

    expect(searchParams(router)).toEqual({ station: '538', need: 'docks' });
    expect(router.history.length).toBe(entries);
    expect(toggleButton('Anclajes')).toHaveAttribute('aria-pressed', 'true');
  });

  it('removes need from the URL when choosing "Bicis"', async () => {
    const { router } = await renderWithRouter(<NeedToggle />, {
      initialEntries: ['/?station=538&need=docks'],
    });

    await userEvent.click(toggleButton('Bicis'));

    expect(searchParams(router)).toEqual({ station: '538' });
    expect(toggleButton('Bicis')).toHaveAttribute('aria-pressed', 'true');
  });
});
