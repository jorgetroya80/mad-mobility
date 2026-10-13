export const POLL_INTERVAL_MS = 60_000;

export const MADRID_CENTER = { lng: -3.7038, lat: 40.4168 } as const;

export const MADRID_ZOOM = 15;

export const MAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

// Tailwind's default `md` breakpoint (--breakpoint-md: 48rem, not overridden in index.css):
// keep both in sync if the theme ever changes it.
export const MD_MEDIA_QUERY = '(min-width: 48rem)';
