import { NOW_TICK_MS } from '@/shared/config';
import { isJustNow, useNow } from '@/shared/lib';
import { RelativeTime } from '../relative-time';
import { PanelNotice } from '../panel-notice';

// Whatever the cause (a failed request, the API's own cache or old data), the data is unconfirmed.
type StaleNoticeProps = { updatedAt: Date | undefined };

export function StaleNotice({ updatedAt }: StaleNoticeProps) {
  const now = useNow(NOW_TICK_MS);

  // «datos de ahora» says nothing: data that just arrived only needs the warning.
  if (!updatedAt || isJustNow(updatedAt, now)) return <PanelNotice>Sin confirmar</PanelNotice>;
  return (
    <PanelNotice>
      Sin confirmar: datos de <RelativeTime date={updatedAt} now={now} />
    </PanelNotice>
  );
}
