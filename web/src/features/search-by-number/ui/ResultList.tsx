import type { KeyboardEvent, Ref } from 'react';
import type { SearchItem } from '../model/types';

type ResultListProps = {
  ref: Ref<HTMLUListElement>;
  results: readonly SearchItem[];
  onChoose: (item: SearchItem) => void;
  onResultKeyDown: (event: KeyboardEvent, index: number) => void;
};

// Read as "1a, Puerta del Sol A, Puerta del Sol nº 1" instead of the parts run together.
function Separator() {
  return <span className="sr-only">,</span>;
}

export function ResultList({ ref, results, onChoose, onResultKeyDown }: ResultListProps) {
  return (
    <ul ref={ref} className="divide-y divide-slate-200 rounded-md border border-slate-300 bg-white">
      {results.map((item, index) => (
        <li key={item.number}>
          <button
            type="button"
            data-number={item.number}
            onClick={() => onChoose(item)}
            onKeyDown={(event) => onResultKeyDown(event, index)}
            className="flex min-h-11 w-full flex-col justify-center px-3 py-2 text-left hover:bg-slate-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-sky-700"
          >
            <span>
              <span className="font-semibold text-slate-900">{item.number}</span>
              <Separator /> <span className="text-slate-700">{item.title}</span>
            </span>
            {item.subtitle ? (
              <span className="text-sm text-slate-600">
                <Separator /> {item.subtitle}
              </span>
            ) : null}
          </button>
        </li>
      ))}
    </ul>
  );
}
