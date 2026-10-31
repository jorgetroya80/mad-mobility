import type { Need } from '@/entities/station';
import { useNeed } from '../model/useNeed';

const OPTIONS = [
  { need: 'bikes', label: 'Bicis' },
  { need: 'docks', label: 'Anclajes' },
] as const satisfies readonly { need: Need; label: string }[];

export function NeedToggle() {
  const [need, setNeed] = useNeed();

  return (
    <fieldset className="inline-flex rounded-control border border-line-strong bg-surface p-0.5">
      <legend className="sr-only">Buscar</legend>
      {OPTIONS.map((option) => (
        <button
          key={option.need}
          type="button"
          aria-pressed={option.need === need}
          onClick={() => setNeed(option.need)}
          className="min-h-11 min-w-11 rounded-control px-4 font-medium text-fg-muted hover:bg-chip focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary aria-pressed:bg-primary aria-pressed:text-primary-foreground"
        >
          {option.label}
        </button>
      ))}
    </fieldset>
  );
}
