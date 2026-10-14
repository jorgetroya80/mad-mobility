import { formatDistance } from './formatDistance';

describe('formatDistance', () => {
  it.each([
    [0, '0 m'],
    [120, '120 m'],
    [120.6, '121 m'],
    [999, '999 m'],
    [999.6, '1 km'],
    [1000, '1 km'],
    [1234, '1,2 km'],
    [12_345, '12,3 km'],
  ])('formats %d m as "%s"', (meters, expected) => {
    expect(formatDistance(meters)).toBe(expected);
  });
});
