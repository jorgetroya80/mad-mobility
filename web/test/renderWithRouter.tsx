import { createMemoryHistory, RouterProvider } from '@tanstack/react-router';
import { act, render } from '@testing-library/react';
import { QueryProvider } from '@/app/providers/QueryProvider';
import { createAppRouter } from '@/app/router';

type RenderWithRouterOptions = { initialEntries?: string[] };

// Mounts the real app router: navigation only commits while RouterProvider is mounted.
export async function renderWithRouter({ initialEntries = ['/'] }: RenderWithRouterOptions = {}) {
  const router = createAppRouter({ history: createMemoryHistory({ initialEntries }) });
  const view = render(
    <QueryProvider>
      <RouterProvider router={router} />
    </QueryProvider>,
  );
  await act(() => router.load());
  return { ...view, router };
}
