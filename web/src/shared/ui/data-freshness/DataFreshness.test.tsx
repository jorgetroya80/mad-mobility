import { render, screen } from '@testing-library/react';
import { StaleNotice } from './StaleNotice';
import { UpdatedAt } from './UpdatedAt';

const NOW = new Date('2026-10-13T10:20:00Z');
const FIVE_MINUTES_AGO = new Date('2026-10-13T10:15:00Z');

describe('data freshness', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });

  afterEach(() => vi.useRealTimers());

  it('says how long ago the data was updated', () => {
    render(<UpdatedAt date={FIVE_MINUTES_AGO} />);

    expect(screen.getByText(/Actualizado/)).toHaveTextContent('Actualizado hace 5 minutos');
  });

  it('warns about stale data with its age in a live region', () => {
    render(<StaleNotice updatedAt={FIVE_MINUTES_AGO} />);

    expect(screen.getByRole('status')).toHaveTextContent(
      'Datos de hace 5 minutos: la EMT no responde',
    );
  });

  it('warns about stale data without a time when it is unknown', () => {
    render(<StaleNotice updatedAt={undefined} />);

    expect(screen.getByRole('status')).toHaveTextContent('Datos antiguos: la EMT no responde');
  });
});
