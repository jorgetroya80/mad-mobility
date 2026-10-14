import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';
import { POLL_INTERVAL_MS, QUERY_RETRY_COUNT } from '@/shared/config';

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: POLL_INTERVAL_MS,
        refetchIntervalInBackground: false,
        refetchOnWindowFocus: true,
        retry: QUERY_RETRY_COUNT,
      },
    },
  });
}

type QueryProviderProps = { children: ReactNode };

export function QueryProvider({ children }: QueryProviderProps) {
  const [queryClient] = useState(createQueryClient);
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
