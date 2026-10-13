import { formatRelative } from '@/shared/lib';

// `now` comes from the caller: reading the clock during render is impure (react/purity),
// and the caller owns the tick that keeps the text fresh.
type RelativeTimeProps = { date: Date; now: Date };

export function RelativeTime({ date, now }: RelativeTimeProps) {
  return <time dateTime={date.toISOString()}>{formatRelative(date, now)}</time>;
}
