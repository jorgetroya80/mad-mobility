import type { KeyboardEvent } from 'react';
import { useRef } from 'react';
import { isExactMatch } from '../lib/matchByNumber';
import type { SearchItem } from './types';

type ResultsKeyboardOptions = {
  results: readonly SearchItem[];
  query: string;
  onChoose: (item: SearchItem) => void;
};

// Target index for the search box: ArrowUp on the first result also lands there.
const INPUT = -1;

/**
 * Arrow keys move the real focus between the search box and the result buttons. Choosing does
 * not move it: the source decides (the page focuses the detail it opens).
 */
export function useResultsKeyboard({ results, query, onChoose }: ResultsKeyboardOptions) {
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const focusInput = () => inputRef.current?.focus();
  // Out of range (before the first or after the last) leaves the focus where it is.
  const focusResult = (index: number) =>
    listRef.current?.querySelectorAll('button')[index]?.focus();

  const onInputKeyDown = (event: KeyboardEvent) => {
    const [first] = results;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      focusResult(0);
    } else if (event.key === 'Enter' && first && isExactMatch(first, query)) {
      event.preventDefault();
      onChoose(first);
    }
  };

  const onResultKeyDown = (event: KeyboardEvent, index: number) => {
    const targets: Record<string, number> = {
      ArrowDown: index + 1,
      ArrowUp: index - 1,
      Escape: INPUT,
    };
    const target = targets[event.key];
    if (target === undefined) return;
    event.preventDefault();
    if (target === INPUT) focusInput();
    else focusResult(target);
  };

  return { inputRef, listRef, onInputKeyDown, onResultKeyDown };
}
