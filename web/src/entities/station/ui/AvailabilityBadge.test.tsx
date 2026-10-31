import { render, screen } from '@testing-library/react';
import { AvailabilityBadge } from './AvailabilityBadge';

describe('AvailabilityBadge', () => {
  it.each([
    { level: 'good', count: 12, need: 'bikes', text: '12 bicis' },
    { level: 'low', count: 1, need: 'bikes', text: '1 bici' },
    { level: 'empty', count: 0, need: 'docks', text: '0 anclajes libres' },
    { level: 'low', count: 4, need: 'docks', text: '4 anclajes libres' },
  ] as const)(
    'shows $level $count $need as "$text" and the number',
    ({ level, count, need, text }) => {
      render(<AvailabilityBadge level={level} count={count} need={need} />);

      const badge = screen.getByText(text).parentElement;
      expect(badge).toHaveClass(`bg-availability-${level}`);
      expect(badge).toHaveTextContent(String(count));
    },
  );

  it('draws the empty badge with a dashed border and keeps the screen-reader text', () => {
    render(<AvailabilityBadge level="empty" count={0} need="bikes" />);

    const badge = screen.getByText('0 bicis').parentElement;
    expect(badge).toHaveClass('border-2', 'border-dashed', 'border-availability-empty-line');
    expect(screen.getByText('0 bicis')).toHaveClass('sr-only');
    expect(badge).toHaveTextContent('0');
  });

  it('says "Sin servicio" instead of the count when the station is off', () => {
    render(<AvailabilityBadge level="off" count={7} need="bikes" />);

    const badge = screen.getByText('Sin servicio').parentElement;
    expect(badge).toHaveClass('bg-availability-off');
    expect(badge).not.toHaveTextContent('7');
  });

  it('shows the count of unconfirmed data in grey and says it is not confirmed', () => {
    render(<AvailabilityBadge level="unconfirmed" count={3} need="bikes" />);

    const badge = screen.getByText('3 bicis, sin confirmar').parentElement;
    expect(badge).toHaveClass(
      'bg-availability-unconfirmed',
      'text-availability-unconfirmed-foreground',
    );
    expect(badge).toHaveTextContent('3');
  });
});
