import type { ReactNode } from 'react';

type StateMessageVariant = 'empty' | 'error' | 'loading';

type StateMessageProps = {
  variant: StateMessageVariant;
  title: string;
  children?: ReactNode;
};

// One entry per variant: a new state is a new entry, not a new branch in the component.
const VARIANTS = {
  empty: { role: 'status', busy: undefined, className: 'border-slate-300 text-slate-700' },
  error: { role: 'alert', busy: undefined, className: 'border-red-600 text-red-800' },
  loading: { role: 'status', busy: true, className: 'border-sky-600 text-sky-800' },
} as const satisfies Record<
  StateMessageVariant,
  { role: 'status' | 'alert'; busy: true | undefined; className: string }
>;

export function StateMessage({ variant, title, children }: StateMessageProps) {
  const { role, busy, className } = VARIANTS[variant];
  return (
    <div role={role} aria-busy={busy} className={`rounded-md border-l-4 bg-white p-4 ${className}`}>
      <p className="font-medium">{title}</p>
      {children ? <div className="mt-1 text-sm">{children}</div> : null}
    </div>
  );
}
