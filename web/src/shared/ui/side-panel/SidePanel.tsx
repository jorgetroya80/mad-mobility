import type { ComponentProps } from 'react';

// Wraps a native section (forwards its props); `label` is required so the region has a name.
type SidePanelProps = Omit<ComponentProps<'section'>, 'aria-label'> & { label: string };

export function SidePanel({ label, className = '', children, ...props }: SidePanelProps) {
  return (
    <section
      {...props}
      aria-label={label}
      className={`h-full w-90 shrink-0 overflow-y-auto border-r border-line bg-surface ${className}`}
    >
      {children}
    </section>
  );
}
