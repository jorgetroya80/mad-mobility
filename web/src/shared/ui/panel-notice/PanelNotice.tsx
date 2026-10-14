import type { ReactNode } from 'react';

type PanelNoticeProps = { children: ReactNode };

// `output` is a polite live region: a notice that appears later (a failed refresh) is announced.
export function PanelNotice({ children }: PanelNoticeProps) {
  return (
    <output className="block rounded-md border-l-4 border-amber-500 bg-amber-50 p-3 text-sm text-slate-900">
      {children}
    </output>
  );
}
