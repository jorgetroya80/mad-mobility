import { act } from '@testing-library/react';
import type * as SharedUi from '@/shared/ui';
import { renderWithRouter } from '../../test/renderWithRouter';

// jsdom has no WebGL: replace the map, keep the rest of the shared UI real.
vi.mock('@/shared/ui', async (importOriginal) => ({
  ...(await importOriginal<typeof SharedUi>()),
  BaseMap: () => <div data-testid="base-map" />,
}));

type AppRouter = Awaited<ReturnType<typeof renderWithRouter>>['router'];

function currentSearch(router: AppRouter) {
  return router.state.matches.at(-1)?.search;
}

function currentUrl(router: AppRouter): URL {
  return new URL(router.state.location.href, 'http://localhost');
}

describe('app router', () => {
  it('reads ?station=538 as a string', async () => {
    const { router } = await renderWithRouter({ initialEntries: ['/?station=538&need=docks'] });

    expect(currentSearch(router)).toEqual({ station: '538', need: 'docks' });
  });

  it('treats an unknown need as bikes', async () => {
    const { router } = await renderWithRouter({ initialEntries: ['/?need=xyz'] });

    expect(currentSearch(router)).toEqual({ need: 'bikes' });
  });

  it('writes the station as plain text and leaves out need=bikes', async () => {
    const { router } = await renderWithRouter();

    await act(() => router.navigate({ to: '/', search: { station: '538', need: 'bikes' } }));

    expect(router.state.location.href).toBe('/?station=538');
  });

  it('writes need=docks next to the station', async () => {
    const { router } = await renderWithRouter();

    await act(() => router.navigate({ to: '/', search: { station: '538', need: 'docks' } }));

    const url = currentUrl(router);
    expect(url.pathname).toBe('/');
    expect(Object.fromEntries(url.searchParams)).toEqual({ station: '538', need: 'docks' });
  });
});
