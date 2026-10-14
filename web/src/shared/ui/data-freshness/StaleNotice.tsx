import { NOW_TICK_MS } from '@/shared/config';
import { useNow } from '@/shared/lib';
import { RelativeTime } from '../relative-time';
import { PanelNotice } from '../panel-notice';

type StaleNoticeProps = { updatedAt: Date | undefined };

export function StaleNotice({ updatedAt }: StaleNoticeProps) {
  const now = useNow(NOW_TICK_MS);

  if (!updatedAt) return <PanelNotice>Datos antiguos: la EMT no responde</PanelNotice>;
  return (
    <PanelNotice>
      Datos de <RelativeTime date={updatedAt} now={now} />: la EMT no responde
    </PanelNotice>
  );
}
