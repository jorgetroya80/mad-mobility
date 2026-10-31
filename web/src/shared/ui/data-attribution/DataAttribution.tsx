const EMT_URL = 'https://www.emtmadrid.es';

export function DataAttribution() {
  return (
    <p className="text-xs text-fg-muted">
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
