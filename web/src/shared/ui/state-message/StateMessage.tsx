import type { ReactNode } from 'react';

type StateMessageVariant = 'empty' | 'error' | 'loading';

type StateMessageProps = {
  variant: StateMessageVariant;
  title: string;
  children?: ReactNode;
};

// One entry per variant: a new state is a new entry, not a new branch in the component.
const VARIANTS = {
  empty: { role: 'status', busy: undefined, className: 'border-line-strong text-fg-muted' },
  error: {
    role: 'alert',
    busy: undefined,
    className: 'border-notice-error-foreground text-notice-error-foreground',
  },
  loading: { role: 'status', busy: true, className: 'border-primary text-primary' },
} as const satisfies Record<
  StateMessageVariant,
  { role: 'status' | 'alert'; busy: true | undefined; className: string }
>;

export function StateMessage({ variant, title, children }: StateMessageProps) {
  const { role, busy, className } = VARIANTS[variant];
  return (
    <div
      role={role}
      aria-busy={busy}
      className={`rounded-card border-l-4 bg-surface p-4 ${className}`}
    >
      <p className="font-medium">{title}</p>
      {children ? <div className="mt-1 text-sm">{children}</div> : null}
    </div>
  );
}
