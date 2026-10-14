import { parseFavorites } from './parseFavorites';

describe('parseFavorites', () => {
  it('accepts an array of station numbers', () => {
    expect(parseFavorites(['538', '25A'])).toEqual(['538', '25A']);
  });

  it('drops duplicates and keeps the first position', () => {
    expect(parseFavorites(['538', '1a', '538'])).toEqual(['538', '1a']);
  });

  it('keeps numbers exactly as stored', () => {
    expect(parseFavorites(['25A', '25a'])).toEqual(['25A', '25a']);
  });

  it.each([
    ['an object', { favorites: ['538'] }],
    ['a string', '538'],
    ['null', null],
    ['numeric items', [538]],
    ['mixed items', ['538', 1]],
  ])('rejects %s', (_case, raw) => {
    expect(parseFavorites(raw)).toBeUndefined();
  });
});
