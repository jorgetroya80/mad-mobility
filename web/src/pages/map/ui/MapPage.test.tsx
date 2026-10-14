import { screen, within } from '@testing-library/react';
import type * as SharedUi from '@/shared/ui';
import { stubMatchMedia } from '../../../../test/matchMedia';
import { renderWithRouter } from '../../../../test/renderWithRouter';

// jsdom has no WebGL: replace the map, keep the rest of the shared UI real.
vi.mock('@/shared/ui', async (importOriginal) => ({
  ...(await importOriginal<typeof SharedUi>()),
  BaseMap: () => <div data-testid="base-map" />,
}));

describe('MapPage', () => {
  it('names the page', async () => {
    await renderWithRouter();

    expect(screen.getByRole('heading', { level: 1, name: 'BiciMAD Now' })).toBeInTheDocument();
  });

  it('shows the map', async () => {
    await renderWithRouter();

    expect(within(screen.getByRole('main')).getByTestId('base-map')).toBeInTheDocument();
  });

  // Bottom sheet (below md) and side panel (md and up).
  it.each([false, true])(
    'shows the empty state and the EMT attribution in the panel (from md: %s)',
    async (isWide) => {
      stubMatchMedia(isWide);
      await renderWithRouter();

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
