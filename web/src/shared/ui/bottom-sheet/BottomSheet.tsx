import { useId, useState, type ReactNode } from 'react';

type BottomSheetProps = {
  label: string;
  defaultExpanded?: boolean;
  children: ReactNode;
};

// Two states only: collapsed shows the handle, expanded takes 60 % of the viewport height.
// dvh, not vh: 100vh ignores the Safari iOS toolbar and would hide the bottom of the sheet.
export function BottomSheet({ label, defaultExpanded = false, children }: BottomSheetProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);
  const handleId = useId();
  const contentId = useId();

  return (
    <div
      className={`fixed inset-x-0 bottom-0 z-10 flex flex-col rounded-t-2xl bg-white shadow-[0_-4px_16px_rgb(0_0_0/0.15)] ${expanded ? 'h-[60dvh]' : ''}`}
    >
      <button
        id={handleId}
        type="button"
        aria-expanded={expanded}
        aria-controls={contentId}
        onClick={() => setExpanded((value) => !value)}
        className="flex min-h-11 w-full flex-col items-center justify-center gap-1 px-4 py-2 font-medium text-slate-800 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-sky-700"
      >
        <span aria-hidden="true" className="h-1 w-10 rounded-full bg-slate-300" />
        {label}
      </button>
      <section
        id={contentId}
        aria-labelledby={handleId}
        hidden={!expanded}
        className="min-h-0 flex-1 overflow-y-auto px-4 pb-4"
      >
        {children}
      </section>
    </div>
  );
}
