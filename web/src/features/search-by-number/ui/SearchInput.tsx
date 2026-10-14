import type { KeyboardEvent, Ref } from 'react';
import { useId } from 'react';

type SearchInputProps = {
  ref: Ref<HTMLInputElement>;
  itemLabel: string;
  query: string;
  onQueryChange: (query: string) => void;
  onKeyDown: (event: KeyboardEvent) => void;
};

export function SearchInput({ ref, itemLabel, query, onQueryChange, onKeyDown }: SearchInputProps) {
  const id = useId();

  return (
    <>
      <label htmlFor={id} className="text-sm font-medium text-slate-700">
        {`Buscar ${itemLabel} por número`}
      </label>
      <input
        ref={ref}
        id={id}
        type="search"
        // Numbers can carry letters ('25A'), so no numeric keyboard.
        inputMode="text"
        autoComplete="off"
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
        onKeyDown={onKeyDown}
        className="min-h-11 rounded-md border border-slate-300 bg-white px-3 text-slate-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
      />
    </>
  );
}
