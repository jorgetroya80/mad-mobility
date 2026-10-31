export const POLL_INTERVAL_MS = 30_000;

export const REQUEST_TIMEOUT_MS = 10_000;

// Wait before the next request after 1, 2 and 3+ consecutive failures. Never faster than 30 s.
export const RETRY_DELAYS_MS = [30_000, 60_000, 120_000] as const;

// Data older than this reads as unconfirmed even if no request failed.
export const STALE_AFTER_MS = 300_000;

export const MADRID_CENTER = { lng: -3.7038, lat: 40.4168 } as const;

export const MADRID_ZOOM = 15;

export const MAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

// Tailwind's default `md` breakpoint (--breakpoint-md: 48rem, not overridden in index.css):
// keep both in sync if the theme ever changes it.
export const MD_MEDIA_QUERY = '(min-width: 48rem)';

export const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';

export const NEARBY_RADIUS_M = 500;

// 4 decimals ≈ 11 m: smaller moves reuse the cached nearby list.
export const NEAR_COORDINATE_DECIMALS = 4;

export const LOW_AVAILABILITY_THRESHOLD = 5;

export const NOW_TICK_MS = 30_000;

export const GEOLOCATION_TIMEOUT_MS = 10_000;

// Reuses a position up to 1 min old, so a reload does not wait for a new fix.
export const GEOLOCATION_MAX_AGE_MS = 60_000;

export const SEARCH_MAX_RESULTS = 8;

export const FAVORITES_STORAGE_KEY = 'bicimad-now:favorites';

// The last stations lists, shown while the network answers or when it cannot.
export const STATIONS_STORAGE_KEY = 'bicimad-now:stations';

export const NEARBY_STORAGE_KEYS = {
  bikes: 'bicimad-now:nearby-bikes',
  docks: 'bicimad-now:nearby-docks',
} as const;
