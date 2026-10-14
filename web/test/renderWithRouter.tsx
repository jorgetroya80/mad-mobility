import { createMemoryHistory, RouterProvider } from '@tanstack/react-router';
import { act, render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { QueryProvider } from '@/app/providers/QueryProvider';
import { createAppRouter } from '@/app/router';

type RenderWithRouterOptions = { initialEntries?: string[] };

// Mounts the real app router (navigation only commits while RouterProvider is mounted).
// With `ui`, it replaces MapPage inside the real `/` route, so its search validation and
// middlewares still apply; without it, the whole app is rendered.
export async function renderWithRouter(
  ui?: ReactNode,
  { initialEntries = ['/'] }: RenderWithRouterOptions = {},
) {
  const router = createAppRouter({
    history: createMemoryHistory({ initialEntries }),
    mapComponent: ui === undefined ? undefined : () => ui,
  });
  const view = render(
    <QueryProvider>
      <RouterProvider router={router} />
    </QueryProvider>,
  );
  await act(() => router.load());
  return { ...view, router };
}
