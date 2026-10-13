import { formatRelative } from './formatRelative';

const NOW = new Date('2026-10-13T10:00:00Z');

function secondsAgo(seconds: number): Date {
  return new Date(NOW.getTime() - seconds * 1000);
}

describe('formatRelative', () => {
  it('says "ahora" for the current instant', () => {
    expect(formatRelative(NOW, NOW)).toBe('ahora');
  });

  it('uses seconds below one minute', () => {
    expect(formatRelative(secondsAgo(30), NOW)).toBe('hace 30 segundos');
  });

  it('keeps seconds at 59 s', () => {
    expect(formatRelative(secondsAgo(59), NOW)).toBe('hace 59 segundos');
  });

  it('switches to minutes at 60 s', () => {
    expect(formatRelative(secondsAgo(60), NOW)).toBe('hace 1 minuto');
  });

  it('rounds to whole minutes', () => {
    expect(formatRelative(secondsAgo(5 * 60), NOW)).toBe('hace 5 minutos');
    expect(formatRelative(secondsAgo(5 * 60 + 20), NOW)).toBe('hace 5 minutos');
  });
});
