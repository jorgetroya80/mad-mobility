import type { ComponentProps } from 'react';

// Wraps a native aside (forwards its props); `label` is required so the landmark has a name.
type SidePanelProps = Omit<ComponentProps<'aside'>, 'aria-label'> & { label: string };

export function SidePanel({ label, className = '', children, ...props }: SidePanelProps) {
  return (
    <aside
      {...props}
      aria-label={label}
      className={`h-full w-90 shrink-0 overflow-y-auto border-r border-slate-200 bg-white ${className}`}
    >
      {children}
    </aside>
  );
}
