import { pickFavorites } from './pickFavorites';

const stations = [{ number: '1a' }, { number: '1b' }, { number: '25A' }];

describe('pickFavorites', () => {
  it('keeps the favorites insertion order', () => {
    expect(pickFavorites(stations, ['25A', '1a'])).toEqual([{ number: '25A' }, { number: '1a' }]);
  });

  it('skips favorites that no longer exist', () => {
    expect(pickFavorites(stations, ['99', '1b'])).toEqual([{ number: '1b' }]);
  });

  it('compares numbers as they are', () => {
    expect(pickFavorites(stations, ['25a'])).toEqual([]);
  });

  it('returns nothing without favorites', () => {
    expect(pickFavorites(stations, [])).toEqual([]);
  });
});
