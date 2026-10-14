import { useGoBack } from '../model/useGoBack';

export function BackButton() {
  const goBack = useGoBack();
  return (
    <button
      type="button"
      onClick={goBack}
      className="inline-flex min-h-11 items-center gap-2 self-start rounded-md px-3 font-medium text-sky-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700"
    >
      <span aria-hidden="true">←</span>
      Volver
    </button>
  );
}
