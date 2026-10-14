import { type ReactNode, useId } from 'react';

type StationSectionProps = { heading: string; children: ReactNode };

export function StationSection({ heading, children }: StationSectionProps) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2">
      <h2 id={headingId} className="text-base font-semibold text-slate-900">
        {heading}
      </h2>
      {children}
    </section>
  );
}
