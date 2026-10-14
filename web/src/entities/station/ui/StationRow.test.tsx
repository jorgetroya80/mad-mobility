import { createRef } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StationRow } from './StationRow';

const station = {
  number: '1a',
  name: 'Puerta del Sol A',
  status: 'OPERATIONAL',
  bikes: 12,
  freeDocks: 3,
  distanceMeters: 120,
} as const;

describe('StationRow', () => {
  it('names the button with number, name, distance and availability', () => {
    render(<StationRow station={station} need="bikes" />);

    expect(
      screen.getByRole('button', { name: 'Estación 1a, Puerta del Sol A, a 120 m, 12 bicis' }),
    ).toBeInTheDocument();
  });

  it('shows the availability for the chosen need', () => {
    render(<StationRow station={station} need="docks" />);

    expect(screen.getByRole('button', { name: /3 anclajes libres$/ })).toBeInTheDocument();
  });

  it('shows number, name and distance as visible text', () => {
    render(<StationRow station={station} need="bikes" />);

    const row = screen.getByRole('button');
    expect(row).toHaveTextContent('1a');
    expect(row).toHaveTextContent('Puerta del Sol A');
    expect(row).toHaveTextContent('120 m');
  });

  it('omits the distance when the station has none', () => {
    const { distanceMeters: _omitted, ...withoutDistance } = station;

    render(<StationRow station={withoutDistance} need="bikes" />);

    const row = screen.getByRole('button', { name: 'Estación 1a, Puerta del Sol A, 12 bicis' });
    expect(row).not.toHaveTextContent(' m');
  });

  it('says "Sin servicio" for a station out of service', () => {
    render(<StationRow station={{ ...station, status: 'NO_SERVICE' }} need="bikes" />);

    expect(screen.getByRole('button', { name: /, Sin servicio$/ })).toBeInTheDocument();
  });

  it('forwards onClick, ref and data attributes to the native button', async () => {
    const onClick = vi.fn<() => void>();
    const ref = createRef<HTMLButtonElement>();

    render(
      <StationRow station={station} need="bikes" onClick={onClick} ref={ref} data-station="1a" />,
    );
    await userEvent.click(screen.getByRole('button'));

    expect(onClick).toHaveBeenCalledOnce();
    expect(ref.current).toBe(screen.getByRole('button'));
    expect(ref.current).toHaveAttribute('data-station', '1a');
    expect(ref.current).toHaveAttribute('type', 'button');
  });
});
