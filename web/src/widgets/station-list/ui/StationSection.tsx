import { type ReactNode, useId } from 'react';

type StationListSection = 'favorites' | 'nearby';

type StationSectionProps = { section: StationListSection; heading: string; children: ReactNode };

// `data-station-list` and the focusable heading (`tabIndex={-1}`, no tab stop) let the page
// return the focus to the section a detail was opened from, or to its heading.
export function StationSection({ section, heading, children }: StationSectionProps) {
  const headingId = useId();
  return (
    <section
      aria-labelledby={headingId}
      data-station-list={section}
      className="flex flex-col gap-2"
    >
      <h2
        id={headingId}
        tabIndex={-1}
        className="text-base font-semibold text-slate-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
      >
        {heading}
      </h2>
      {children}
    </section>
  );
}
