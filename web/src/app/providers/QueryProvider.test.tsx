import { useQueryClient } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { createQueryClient, QueryProvider } from './QueryProvider';

function StaleTimeProbe() {
  const queryClient = useQueryClient();
  return <p>{queryClient.getDefaultOptions().queries?.staleTime?.toString()}</p>;
}

describe('createQueryClient', () => {
  it('applies the app query defaults', () => {
    const queries = createQueryClient().getDefaultOptions().queries;

    expect(queries).toMatchObject({
      staleTime: 60_000,
      refetchIntervalInBackground: false,
      refetchOnWindowFocus: true,
      retry: 1,
    });
  });
});

describe('QueryProvider', () => {
  it('provides a query client with the app defaults to its children', () => {
    render(
      <QueryProvider>
        <StaleTimeProbe />
      </QueryProvider>,
    );

    expect(screen.getByText('60000')).toBeInTheDocument();
  });
});
