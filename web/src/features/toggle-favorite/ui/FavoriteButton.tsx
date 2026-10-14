import type { ComponentProps } from 'react';
import { useFavorites } from '../model/useFavorites';

type FavoriteButtonProps = Omit<ComponentProps<'button'>, 'children' | 'onClick'> & {
  number: string;
};

// A toggle button keeps the same name in both states (WAI-ARIA APG): aria-pressed tells
// whether it is on, so the label does not change to "Quitar…".
export function FavoriteButton({ number, className = '', ...props }: FavoriteButtonProps) {
  const { isFavorite, toggle } = useFavorites();
  const pressed = isFavorite(number);

  return (
    <button
      type="button"
      {...props}
      aria-pressed={pressed}
      onClick={() => toggle(number)}
      className={`inline-flex min-h-11 min-w-11 items-center gap-2 rounded-md border border-slate-300 bg-white px-4 font-medium text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 aria-pressed:border-amber-500 aria-pressed:bg-amber-50 ${className}`}
    >
      <span aria-hidden="true" className="text-lg text-amber-700">
        {pressed ? '★' : '☆'}
      </span>
      Favorita
    </button>
  );
}
