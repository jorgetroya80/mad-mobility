import type { ReactNode } from 'react';

type PanelNoticeProps = { children: ReactNode };

// `output` is a polite live region: a notice that appears later (a failed refresh) is announced.
export function PanelNotice({ children }: PanelNoticeProps) {
  return (
    <output className="block rounded-card border-l-4 border-notice-stale-line bg-notice-stale p-3 text-sm text-notice-stale-foreground">
      {children}
    </output>
  );
}
