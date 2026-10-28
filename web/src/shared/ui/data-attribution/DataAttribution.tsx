const EMT_URL = 'https://www.emtmadrid.es';

export function DataAttribution() {
  return (
    <p className="text-xs text-slate-600">
      Powered by{' '}
      <a
        href={EMT_URL}
        target="_blank"
        rel="noopener noreferrer"
        className="underline focus-visible:outline-2 focus-visible:outline-offset-2"
      >
        EMT de Madrid
      </a>
    </p>
  );
}
