import type { ComponentProps } from 'react';
import { availabilityFor } from '../model/availability';
import type { Need, Station } from '../model/types';
import { AvailabilityBadge } from './AvailabilityBadge';
import { availabilityText } from './availabilityText';
import { formatDistance } from './formatDistance';

type RowStation = Pick<
  Station,
  'number' | 'name' | 'status' | 'bikes' | 'freeDocks' | 'distanceMeters'
>;

// Wraps a native button (forwards its props, `ref` included); the content and the
// accessible name come from the station.
type StationRowProps = Omit<ComponentProps<'button'>, 'children' | 'aria-label'> & {
  station: RowStation;
  need: Need;
};

export function StationRow({ station, need, className = '', ...props }: StationRowProps) {
  const { level, count } = availabilityFor(station, need);
  const distance =
    station.distanceMeters === undefined ? undefined : formatDistance(station.distanceMeters);
  const label = [
    `Estación ${station.number}`,
    station.name,
    distance && `a ${distance}`,
    availabilityText(level, count, need),
  ]
    .filter(Boolean)
    .join(', ');

  return (
    <button
      type="button"
      {...props}
      aria-label={label}
      className={`flex min-h-11 w-full items-center gap-3 px-3 py-2 text-left hover:bg-slate-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-sky-700 ${className}`}
    >
      <span className="w-10 shrink-0 font-semibold text-slate-900 tabular-nums">
        {station.number}
      </span>
      <span className="min-w-0 flex-1 truncate text-slate-800">{station.name}</span>
      {distance ? <span className="shrink-0 text-sm text-slate-600">{distance}</span> : null}
      <AvailabilityBadge level={level} count={count} need={need} />
    </button>
  );
}
