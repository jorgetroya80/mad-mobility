import { render, screen } from '@testing-library/react';
import { RelativeTime } from './RelativeTime';

describe('RelativeTime', () => {
  it('shows the relative time with the ISO date in dateTime', () => {
    const date = new Date('2026-10-13T09:55:00Z');
    const now = new Date('2026-10-13T10:00:00Z');

    render(<RelativeTime date={date} now={now} />);

    const time = screen.getByText('hace 5 minutos');
    expect(time.tagName).toBe('TIME');
    expect(time).toHaveAttribute('dateTime', '2026-10-13T09:55:00.000Z');
  });
});
