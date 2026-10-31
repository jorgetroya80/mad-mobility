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
      <label htmlFor={id} className="text-sm font-medium text-fg-muted">
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
        className="min-h-11 rounded-control border border-input-line bg-surface px-3 text-fg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      />
    </>
  );
}
