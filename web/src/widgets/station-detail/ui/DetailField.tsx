import type { ReactNode } from 'react';

type DetailFieldProps = { term: string; children: ReactNode };

export function DetailField({ term, children }: DetailFieldProps) {
  return (
    <div className="flex items-center justify-between gap-3 py-2">
      <dt className="text-slate-600">{term}</dt>
      <dd className="text-right font-medium text-slate-900">{children}</dd>
    </div>
  );
}
