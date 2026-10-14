import { parseUpdatedAt } from './parseUpdatedAt';

describe('parseUpdatedAt', () => {
  it('parses an ISO-8601 timestamp', () => {
    expect(parseUpdatedAt('2026-10-05T10:15:00Z')).toEqual(new Date('2026-10-05T10:15:00Z'));
  });

  it.each(['nope', ''])('treats %j as absent', (value) => {
    expect(parseUpdatedAt(value)).toBeUndefined();
  });
});
