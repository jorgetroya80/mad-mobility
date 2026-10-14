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
    <fieldset className="inline-flex self-start rounded-md border border-slate-300 bg-white p-0.5">
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
          <span className="flex min-h-11 min-w-11 cursor-pointer items-center rounded px-4 font-medium text-slate-700 peer-checked:bg-sky-700 peer-checked:text-white peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-sky-700 hover:bg-slate-50 peer-checked:hover:bg-sky-700">
            {source.label}
          </span>
        </label>
      ))}
    </fieldset>
  );
}
