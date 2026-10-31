import { STALE_AFTER_MS } from '@/shared/config';
import { dataStatus } from './dataStatus';
import type { DataStatus } from './types';

const NOW = new Date('2026-10-31T10:00:00Z').getTime();

type Input = Parameters<typeof dataStatus>[0];

function input(overrides: Partial<Input>): Input {
  return {
    hasData: true,
    failed: false,
    apiStale: false,
    updatedAt: new Date(NOW),
    now: NOW,
    online: true,
    ...overrides,
  };
}

function ageOf(ms: number): Date {
  return new Date(NOW - ms);
}

describe('dataStatus', () => {
  type Case = { name: string; overrides: Partial<Input>; status: DataStatus };

  const cases: Case[] = [
    { name: 'no data, request in flight', overrides: { hasData: false }, status: 'loading' },
    { name: 'fresh data, last request fine', overrides: {}, status: 'ok' },
    { name: 'data and a failed request', overrides: { failed: true }, status: 'stale' },
    { name: 'data the API marked stale', overrides: { apiStale: true }, status: 'stale' },
    {
      name: 'data older than 5 min',
      overrides: { updatedAt: ageOf(STALE_AFTER_MS + 1) },
      status: 'stale',
    },
    {
      name: 'no data and a failed request',
      overrides: { hasData: false, failed: true },
      status: 'error',
    },
    { name: 'browser offline', overrides: { online: false }, status: 'offline' },
  ];

  it.each(cases)('$name → $status', ({ overrides, status }) => {
    expect(dataStatus(input(overrides))).toBe(status);
  });

  // Offline wins over everything else, with or without data.
  it.each<Partial<Input>>([
    { hasData: true, failed: true, apiStale: true },
    { hasData: false, failed: true },
    { hasData: false },
  ])('reports offline over the other states for %j', (overrides) => {
    expect(dataStatus(input({ ...overrides, online: false }))).toBe('offline');
  });

  it('keeps data exactly 5 min old as ok', () => {
    expect(dataStatus(input({ updatedAt: ageOf(STALE_AFTER_MS) }))).toBe('ok');
  });

  it('marks data just over 5 min old as stale', () => {
    expect(dataStatus(input({ updatedAt: ageOf(STALE_AFTER_MS + 1) }))).toBe('stale');
  });

  it('keeps data without a date as ok', () => {
    expect(dataStatus(input({ updatedAt: undefined }))).toBe('ok');
  });

  it('ignores an old date when there is no data', () => {
    expect(dataStatus(input({ hasData: false, updatedAt: ageOf(STALE_AFTER_MS + 1) }))).toBe(
      'loading',
    );
  });
});
