import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithRouter } from '../../../../test/renderWithRouter';
import { useSelectedStation } from './useSelectedStation';

function SelectionProbe() {
  const { number, select, clear } = useSelectedStation();
  return (
    <>
      <output>{number ?? 'ninguna'}</output>
      <button type="button" onClick={() => select('538')}>
        Elegir 538
      </button>
      <button type="button" onClick={clear}>
        Cerrar
      </button>
    </>
  );
}

type AppRouter = Awaited<ReturnType<typeof renderWithRouter>>['router'];

function searchParams(router: AppRouter) {
  return Object.fromEntries(new URL(router.state.location.href, 'http://localhost').searchParams);
}

describe('useSelectedStation', () => {
  it('reads the station from the URL', async () => {
    await renderWithRouter(<SelectionProbe />, { initialEntries: ['/?station=1a'] });

    expect(screen.getByRole('status')).toHaveTextContent('1a');
  });

  it('has no station without ?station', async () => {
    await renderWithRouter(<SelectionProbe />);

    expect(screen.getByRole('status')).toHaveTextContent('ninguna');
  });

  it('selects with a new history entry, so back removes the station', async () => {
    const { router } = await renderWithRouter(<SelectionProbe />);

    await userEvent.click(screen.getByRole('button', { name: 'Elegir 538' }));

    expect(router.state.location.href).toBe('/?station=538');
    expect(screen.getByRole('status')).toHaveTextContent('538');

    await act(async () => router.history.back());

    expect(searchParams(router)).toEqual({});
    expect(screen.getByRole('status')).toHaveTextContent('ninguna');
  });

  it('keeps need=docks when selecting and going back', async () => {
    const { router } = await renderWithRouter(<SelectionProbe />, {
      initialEntries: ['/?need=docks'],
    });

    await userEvent.click(screen.getByRole('button', { name: 'Elegir 538' }));
    expect(searchParams(router)).toEqual({ station: '538', need: 'docks' });

    await act(async () => router.history.back());
    expect(searchParams(router)).toEqual({ need: 'docks' });
  });

  it('clears the station, keeps need and replaces the history entry', async () => {
    const { router } = await renderWithRouter(<SelectionProbe />, {
      initialEntries: ['/?station=538&need=docks'],
    });
    const entries = router.history.length;

    await userEvent.click(screen.getByRole('button', { name: 'Cerrar' }));

    expect(searchParams(router)).toEqual({ need: 'docks' });
    expect(screen.getByRole('status')).toHaveTextContent('ninguna');
    expect(router.history.length).toBe(entries);
  });
});
