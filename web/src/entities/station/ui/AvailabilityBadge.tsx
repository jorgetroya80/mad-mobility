import { availabilityLabel } from '../model/availability';
import type { AvailabilityLevel, Need } from '../model/types';
import { availabilityText } from './availabilityText';

type AvailabilityBadgeProps = { level: AvailabilityLevel; count: number; need: Need };

// Full class names (not built from `level`) so Tailwind finds them in the source.
const TONES = {
  good: 'bg-availability-good text-availability-good-foreground',
  low: 'bg-availability-low text-availability-low-foreground',
  empty: 'bg-availability-empty text-availability-empty-foreground',
  off: 'bg-availability-off text-availability-off-foreground',
} as const satisfies Record<AvailabilityLevel, string>;

// Color is never the only cue: the pill shows the number (or a dash when off) and screen
// readers get the full text.
export function AvailabilityBadge({ level, count, need }: AvailabilityBadgeProps) {
  return (
    <span
      className={`inline-flex h-6 min-w-8 shrink-0 items-center justify-center rounded-full px-2 text-sm font-semibold tabular-nums ${TONES[level]}`}
    >
      <span aria-hidden="true">{availabilityLabel({ level, count })}</span>
      <span className="sr-only">{availabilityText(level, count, need)}</span>
    </span>
  );
}
