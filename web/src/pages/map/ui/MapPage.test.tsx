import { render, screen, within } from '@testing-library/react';
import type * as SharedUi from '@/shared/ui';
import { stubMatchMedia } from '../../../../test/matchMedia';
import { MapPage } from './MapPage';

// jsdom has no WebGL: replace the map, keep the rest of the shared UI real.
vi.mock('@/shared/ui', async (importOriginal) => ({
  ...(await importOriginal<typeof SharedUi>()),
  BaseMap: () => <div data-testid="base-map" />,
}));

describe('MapPage', () => {
  it('names the page', () => {
    render(<MapPage />);

    expect(screen.getByRole('heading', { level: 1, name: 'BiciMAD Now' })).toBeInTheDocument();
  });

  it('shows the map', () => {
    render(<MapPage />);

    expect(within(screen.getByRole('main')).getByTestId('base-map')).toBeInTheDocument();
  });

  // Bottom sheet (below md) and side panel (md and up).
  it.each([false, true])(
    'shows the empty state and the EMT attribution in the panel (from md: %s)',
    (isWide) => {
      stubMatchMedia(isWide);
      render(<MapPage />);

      const panel = screen.getByRole('region', { name: 'Estaciones' });
      expect(within(panel).getByRole('status')).toHaveTextContent(
        'Las estaciones llegan en la siguiente versión',
      );
      expect(
        within(panel).getByRole('link', { name: 'EMT Madrid MobilityLabs' }),
      ).toBeInTheDocument();
    },
  );
});
