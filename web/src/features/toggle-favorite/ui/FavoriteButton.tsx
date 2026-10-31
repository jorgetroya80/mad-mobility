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
      className={`inline-flex min-h-11 min-w-11 items-center gap-2 rounded-control border border-line-strong bg-surface px-4 font-medium text-fg hover:bg-chip focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary aria-pressed:border-primary aria-pressed:bg-primary-soft ${className}`}
    >
      <span aria-hidden="true" className="text-lg text-primary">
        {pressed ? '★' : '☆'}
      </span>
      Favorita
    </button>
  );
}
