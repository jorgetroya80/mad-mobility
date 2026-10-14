import { act } from '@testing-library/react';
import { renderWithRouter } from '../../test/renderWithRouter';

// These tests are about the URL, not the page: a stub keeps the map out of jsdom.
const routeContent = <p>map route</p>;

type AppRouter = Awaited<ReturnType<typeof renderWithRouter>>['router'];

function currentSearch(router: AppRouter) {
  return router.state.matches.at(-1)?.search;
}

function currentUrl(router: AppRouter): URL {
  return new URL(router.state.location.href, 'http://localhost');
}

describe('app router', () => {
  it('reads ?station=538 as a string', async () => {
    const { router } = await renderWithRouter(routeContent, {
      initialEntries: ['/?station=538&need=docks'],
    });

    expect(currentSearch(router)).toEqual({ station: '538', need: 'docks' });
  });

  it('treats an unknown need as bikes', async () => {
    const { router } = await renderWithRouter(routeContent, { initialEntries: ['/?need=xyz'] });

    expect(currentSearch(router)).toEqual({ need: 'bikes' });
  });

  it('writes the station as plain text and leaves out need=bikes', async () => {
    const { router } = await renderWithRouter(routeContent);

    await act(() => router.navigate({ to: '/', search: { station: '538', need: 'bikes' } }));

    expect(router.state.location.href).toBe('/?station=538');
  });

  it('writes need=docks next to the station', async () => {
    const { router } = await renderWithRouter(routeContent);

    await act(() => router.navigate({ to: '/', search: { station: '538', need: 'docks' } }));

    const url = currentUrl(router);
    expect(url.pathname).toBe('/');
    expect(Object.fromEntries(url.searchParams)).toEqual({ station: '538', need: 'docks' });
  });
});
