import type { Need } from '@/entities/station';
import { useNeed } from '../model/useNeed';

const OPTIONS = [
  { need: 'bikes', label: 'Bicis' },
  { need: 'docks', label: 'Anclajes' },
] as const satisfies readonly { need: Need; label: string }[];

export function NeedToggle() {
  const [need, setNeed] = useNeed();

  return (
    <fieldset className="inline-flex rounded-md border border-slate-300 bg-white p-0.5">
      <legend className="sr-only">Buscar</legend>
      {OPTIONS.map((option) => (
        <button
          key={option.need}
          type="button"
          aria-pressed={option.need === need}
          onClick={() => setNeed(option.need)}
          className="min-h-11 min-w-11 rounded px-4 font-medium text-slate-700 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 aria-pressed:bg-sky-700 aria-pressed:text-white"
        >
          {option.label}
        </button>
      ))}
    </fieldset>
  );
}
