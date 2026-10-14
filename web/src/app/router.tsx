import {
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
  stringifySearchWith,
  stripSearchParams,
} from '@tanstack/react-router';
import { MapPage, validateMapSearch } from '@/pages/map';

const rootRoute = createRootRoute();

const mapRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: MapPage,
  validateSearch: validateMapSearch,
  search: { middlewares: [stripSearchParams({ need: 'bikes' })] },
});

const routeTree = rootRoute.addChildren([mapRoute]);

type AppRouterOptions = { history?: RouterHistory };

// Plain-text search: the default JSON parser reads ?station=538 as a number and writes '538' as %22538%22.
export function createAppRouter({ history }: AppRouterOptions = {}) {
  return createRouter({
    routeTree,
    history,
    parseSearch: (search) => Object.fromEntries(new URLSearchParams(search)),
    stringifySearch: stringifySearchWith(String, (value) => value),
  });
}

declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>;
  }
}
