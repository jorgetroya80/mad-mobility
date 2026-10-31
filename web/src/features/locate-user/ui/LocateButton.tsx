import type { ComponentProps } from 'react';
import type { GeolocationStatus } from '@/shared/lib';

type LocateButtonProps = Omit<ComponentProps<'button'>, 'children' | 'onClick' | 'disabled'> & {
  status: GeolocationStatus;
  onLocate: () => void;
};

// aria-disabled instead of disabled while pending: a disabled button would lose the focus
// the user just put on it.
export function LocateButton({ status, onLocate, className = '', ...props }: LocateButtonProps) {
  const pending = status === 'pending';

  return (
    <button
      type="button"
      {...props}
      aria-busy={pending || undefined}
      aria-disabled={pending || undefined}
      onClick={pending ? undefined : onLocate}
      className={`min-h-11 rounded-control border border-line-strong bg-surface px-4 font-medium text-fg hover:bg-chip focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary aria-disabled:cursor-wait aria-disabled:text-fg-muted ${className}`}
    >
      {pending ? 'Localizando…' : 'Mi ubicación'}
    </button>
  );
}
