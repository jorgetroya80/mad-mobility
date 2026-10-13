const MS_PER_SECOND = 1000;
const SECONDS_PER_MINUTE = 60;

const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });

export function formatRelative(date: Date, now: Date): string {
  const seconds = Math.round((date.getTime() - now.getTime()) / MS_PER_SECOND);
  if (Math.abs(seconds) < SECONDS_PER_MINUTE) return rtf.format(seconds, 'second');
  return rtf.format(Math.round(seconds / SECONDS_PER_MINUTE), 'minute');
}
