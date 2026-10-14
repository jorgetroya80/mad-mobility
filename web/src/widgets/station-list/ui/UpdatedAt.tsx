import { NOW_TICK_MS } from '@/shared/config';
import { useNow } from '@/shared/lib';
import { RelativeTime } from '@/shared/ui';

type UpdatedAtProps = { date: Date };

export function UpdatedAt({ date }: UpdatedAtProps) {
  const now = useNow(NOW_TICK_MS);
  return (
    <p className="text-sm text-slate-600">
      Actualizado <RelativeTime date={date} now={now} />
    </p>
  );
}
