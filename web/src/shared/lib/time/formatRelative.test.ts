import { formatRelative, isJustNow } from './formatRelative';

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

  // useNow ticks every 30 s, so data that arrived after the last tick is dated after `now`.
  it('says "ahora" for a date after now instead of a future time', () => {
    expect(formatRelative(secondsAgo(-10), NOW)).toBe('ahora');
  });
});

describe('isJustNow', () => {
  it.each([0, -10])('is true %i s ago', (seconds) => {
    expect(isJustNow(secondsAgo(seconds), NOW)).toBe(true);
  });

  it('is false one second ago', () => {
    expect(isJustNow(secondsAgo(1), NOW)).toBe(false);
  });
});
