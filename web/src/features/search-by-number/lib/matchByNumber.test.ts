import type { SearchItem } from '../model/types';
import { isExactMatch, matchByNumber } from './matchByNumber';

function items(...numbers: string[]): SearchItem[] {
  return numbers.map((number) => ({ number, title: `Estación ${number}` }));
}

function numbersOf(found: readonly SearchItem[]) {
  return found.map((item) => item.number);
}

const MAX = 8;

describe('matchByNumber', () => {
  it('finds items whose number starts with the query', () => {
    const found = matchByNumber(items('1a', '10', '125', '21', '2'), '1', MAX);

    expect(numbersOf(found)).toEqual(['1a', '10', '125']);
  });

  it('ignores case and surrounding or inner whitespace', () => {
    const all = items('25A', '25b', '2');

    expect(numbersOf(matchByNumber(all, '25a', MAX))).toEqual(['25A']);
    expect(numbersOf(matchByNumber(all, ' 25 a ', MAX))).toEqual(['25A']);
  });

  it('orders numerically, then alphabetically', () => {
    const found = matchByNumber(items('10', '5b', '5', '50', '5a'), '5', MAX);

    expect(numbersOf(found)).toEqual(['5', '5a', '5b', '50']);
  });

  it('orders the suffix without case: 1, 1a, 1B, 10', () => {
    const found = matchByNumber(items('10', '1B', '1a', '1'), '1', MAX);

    expect(numbersOf(found)).toEqual(['1', '1a', '1B', '10']);
  });

  it('puts the exact match first', () => {
    const found = matchByNumber(items('10a', '100', '10', '10b'), '10', MAX);

    expect(numbersOf(found)).toEqual(['10', '10a', '10b', '100']);
  });

  it('treats a match that differs only in case as exact', () => {
    const found = matchByNumber(items('7', '7A', '7a1'), '7a', MAX);

    expect(numbersOf(found)).toEqual(['7A', '7a1']);
  });

  it('returns at most max results, keeping the exact match', () => {
    const many = items(...Array.from({ length: 20 }, (_, index) => String(index + 1)));

    expect(numbersOf(matchByNumber(many, '1', MAX))).toEqual([
      '1',
      '10',
      '11',
      '12',
      '13',
      '14',
      '15',
      '16',
    ]);
    expect(matchByNumber(many, '1', 3)).toHaveLength(3);
  });

  it('returns nothing for an empty or blank query', () => {
    expect(matchByNumber(items('1', '2'), '', MAX)).toEqual([]);
    expect(matchByNumber(items('1', '2'), '   ', MAX)).toEqual([]);
  });

  it('does not search by title', () => {
    const sol: SearchItem[] = [{ number: '1a', title: 'Puerta del Sol A', subtitle: 'Sol' }];

    expect(matchByNumber(sol, 'sol', MAX)).toEqual([]);
  });

  it('does not change the input', () => {
    const all = items('10', '5');

    matchByNumber(all, '', MAX);
    matchByNumber(all, '1', MAX);

    expect(numbersOf(all)).toEqual(['10', '5']);
  });
});

describe('isExactMatch', () => {
  it('compares the normalized number and query', () => {
    expect(isExactMatch({ number: '25A', title: 'Fuencarral' }, ' 25a ')).toBe(true);
    expect(isExactMatch({ number: '25A', title: 'Fuencarral' }, '25')).toBe(false);
  });
});
