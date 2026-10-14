import { availabilityText } from './availabilityText';

describe('availabilityText', () => {
  it.each([
    [1, 'bikes', '1 bici'],
    [3, 'bikes', '3 bicis'],
    [0, 'bikes', '0 bicis'],
    [1, 'docks', '1 anclaje libre'],
    [4, 'docks', '4 anclajes libres'],
    [0, 'docks', '0 anclajes libres'],
  ] as const)('says %i %s as "%s"', (count, need, expected) => {
    expect(availabilityText('good', count, need)).toBe(expected);
  });

  it('says "Sin servicio" when the station is off, whatever the count', () => {
    expect(availabilityText('off', 7, 'bikes')).toBe('Sin servicio');
    expect(availabilityText('off', 0, 'docks')).toBe('Sin servicio');
  });
});
