import { useGoBack } from '../model/useGoBack';

export function BackButton() {
  const goBack = useGoBack();
  return (
    <button
      type="button"
      onClick={goBack}
      className="inline-flex min-h-11 items-center gap-2 self-start rounded-control px-3 font-medium text-primary hover:bg-chip focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <span aria-hidden="true">←</span>
      Volver
    </button>
  );
}
