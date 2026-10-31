import { useEffect, useRef } from 'react';

type DetailTitleProps = { id: string; number: string; name: string | undefined };

// Focused on mount (the detail remounts per station). `tabIndex={-1}` makes it focusable
// without adding a tab stop.
export function DetailTitle({ id, number, name }: DetailTitleProps) {
  const ref = useRef<HTMLHeadingElement>(null);

  useEffect(() => ref.current?.focus(), []);

  return (
    <h2
      ref={ref}
      id={id}
      tabIndex={-1}
      className="text-lg font-semibold text-fg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      {name === undefined ? `Estación ${number}` : `${number} · ${name}`}
    </h2>
  );
}
