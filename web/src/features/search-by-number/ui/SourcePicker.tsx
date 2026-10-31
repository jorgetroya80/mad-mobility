import { useId } from 'react';
import type { SearchSource } from '../model/types';

type SourcePickerProps = {
  sources: readonly SearchSource[];
  selectedId: string;
  onChange: (id: string) => void;
};

/** Native radio group: one choice out of several, with arrow-key navigation built in. */
export function SourcePicker({ sources, selectedId, onChange }: SourcePickerProps) {
  const name = useId();

  return (
    <fieldset className="inline-flex self-start rounded-control border border-line-strong bg-surface p-0.5">
      <legend className="sr-only">Servicio</legend>
      {sources.map((source) => (
        <label key={source.id}>
          <input
            type="radio"
            name={name}
            value={source.id}
            checked={source.id === selectedId}
            onChange={() => onChange(source.id)}
            className="peer sr-only"
          />
          <span className="flex min-h-11 min-w-11 cursor-pointer items-center rounded-control px-4 font-medium text-fg-muted peer-checked:bg-primary peer-checked:text-primary-foreground peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary hover:bg-chip peer-checked:hover:bg-primary">
            {source.label}
          </span>
        </label>
      ))}
    </fieldset>
  );
}
