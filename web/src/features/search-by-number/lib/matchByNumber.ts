import type { SearchItem } from '../model/types';

const WHITESPACE = /\s+/g;
const LEADING_DIGITS = /^\d*/;

function normalize(text: string): string {
  return text.replace(WHITESPACE, '').toLowerCase();
}

type SortKey = { numeric: number; suffix: string };

// Mirrors the API order for `number`: numeric part first, then the suffix (5, 5a, 5b, 10).
function sortKey(normalizedNumber: string): SortKey {
  const digits = LEADING_DIGITS.exec(normalizedNumber)?.[0] ?? '';
  const numeric = digits === '' ? Number.POSITIVE_INFINITY : Number(digits);
  return { numeric, suffix: normalizedNumber.slice(digits.length) };
}

function compareNumbers(a: string, b: string): number {
  const keyA = sortKey(a);
  const keyB = sortKey(b);
  if (keyA.numeric !== keyB.numeric) return keyA.numeric < keyB.numeric ? -1 : 1;
  if (keyA.suffix === keyB.suffix) return 0;
  return keyA.suffix < keyB.suffix ? -1 : 1;
}

export function isExactMatch(item: SearchItem, query: string): boolean {
  return normalize(item.number) === normalize(query);
}

export function matchByNumber(
  items: readonly SearchItem[],
  query: string,
  max: number,
): SearchItem[] {
  const wanted = normalize(query);
  if (wanted === '') return [];

  const candidates = items
    .map((item) => ({ item, number: normalize(item.number) }))
    .filter(({ number }) => number.startsWith(wanted))
    .sort((a, b) => compareNumbers(a.number, b.number));
  const exact = candidates.filter(({ number }) => number === wanted);
  const rest = candidates.filter(({ number }) => number !== wanted);

  return [...exact, ...rest].slice(0, max).map(({ item }) => item);
}
