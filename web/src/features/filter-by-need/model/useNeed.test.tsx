import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithRouter } from '../../../../test/renderWithRouter';
import { useNeed } from './useNeed';

function NeedProbe() {
  const [need, setNeed] = useNeed();
  return (
    <button type="button" onClick={() => setNeed('docks')}>
      {need}
    </button>
  );
}

describe('useNeed', () => {
  it('treats an unknown need as bikes', async () => {
    await renderWithRouter(<NeedProbe />, { initialEntries: ['/?need=xyz'] });

    expect(screen.getByRole('button')).toHaveTextContent('bikes');
  });

  it('reads and writes the need in the URL', async () => {
    const { router } = await renderWithRouter(<NeedProbe />);

    await userEvent.click(screen.getByRole('button'));

    expect(router.state.location.href).toBe('/?need=docks');
    expect(screen.getByRole('button')).toHaveTextContent('docks');
  });
});
