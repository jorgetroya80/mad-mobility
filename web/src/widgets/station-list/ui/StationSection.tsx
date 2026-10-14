import { type ReactNode, useId } from 'react';

type StationListSection = 'favorites' | 'nearby';

type StationSectionProps = {
  section: StationListSection;
  heading: string;
  // What the numbers in the rows count, so the filter's effect is spelled out under the heading.
  caption: string;
  children: ReactNode;
};

// `data-station-list` and the focusable heading (`tabIndex={-1}`, no tab stop) let the page
// return the focus to the section a detail was opened from, or to its heading.
export function StationSection({ section, heading, caption, children }: StationSectionProps) {
  const headingId = useId();
  const captionId = useId();
  return (
    <section
      aria-labelledby={headingId}
      aria-describedby={captionId}
      data-station-list={section}
      className="flex flex-col gap-2"
    >
      <div>
        <h2
          id={headingId}
          tabIndex={-1}
          className="text-base font-semibold text-slate-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
        >
          {heading}
        </h2>
        <p id={captionId} className="text-sm text-slate-600">
          {caption}
        </p>
      </div>
      {children}
    </section>
  );
}
