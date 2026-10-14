import { validateMapSearch } from './mapSearch';

describe('validateMapSearch', () => {
  it('defaults to bikes without a station when the search is empty', () => {
    expect(validateMapSearch({})).toEqual({ need: 'bikes' });
  });

  it.each(['538', '25A'])('keeps station %s as a string', (station) => {
    expect(validateMapSearch({ station })).toEqual({ station, need: 'bikes' });
  });

  it.each([[''], [538], [null], [['538']]])('drops station %j', (station) => {
    expect(validateMapSearch({ station })).toEqual({ need: 'bikes' });
  });

  it('keeps need docks', () => {
    expect(validateMapSearch({ need: 'docks' })).toEqual({ need: 'docks' });
  });

  it.each(['xyz', '', 1])('treats need %j as bikes', (need) => {
    expect(validateMapSearch({ need })).toEqual({ need: 'bikes' });
  });
});
