import { RouterProvider } from '@tanstack/react-router';
import { QueryProvider } from './providers/QueryProvider';
import { createAppRouter } from './router';

const router = createAppRouter();

export function App() {
  return (
    <QueryProvider>
      <RouterProvider router={router} />
    </QueryProvider>
  );
}
