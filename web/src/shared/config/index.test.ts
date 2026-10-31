import {
  env,
  FAVORITES_STORAGE_KEY,
  GEOLOCATION_MAX_AGE_MS,
  GEOLOCATION_TIMEOUT_MS,
  LOW_AVAILABILITY_THRESHOLD,
  MADRID_CENTER,
  MADRID_ZOOM,
  MAP_STYLE_URL,
  MD_MEDIA_QUERY,
  NEAR_COORDINATE_DECIMALS,
  NEARBY_RADIUS_M,
  NOW_TICK_MS,
  POLL_INTERVAL_MS,
  QUERY_RETRY_COUNT,
  REDUCED_MOTION_QUERY,
  SEARCH_MAX_RESULTS,
  STALE_AFTER_MS,
} from '.';

describe('shared/config public API', () => {
  it('exposes the API base URL read from the environment', () => {
    expect(env.apiBaseUrl).toBe('/api');
  });

  it('polls every minute', () => {
    expect(POLL_INTERVAL_MS).toBe(60_000);
  });

  it('treats data older than 5 min as stale', () => {
    expect(STALE_AFTER_MS).toBe(300_000);
  });

  it('retries a failed query once', () => {
    expect(QUERY_RETRY_COUNT).toBe(1);
  });

  it('centers the map on Puerta del Sol', () => {
    expect(MADRID_CENTER).toEqual({ lng: -3.7038, lat: 40.4168 });
    expect(MADRID_ZOOM).toBe(15);
  });

  it('uses the OpenFreeMap liberty style', () => {
    expect(MAP_STYLE_URL).toBe('https://tiles.openfreemap.org/styles/liberty');
  });

  it("matches Tailwind's md breakpoint", () => {
    expect(MD_MEDIA_QUERY).toBe('(min-width: 48rem)');
  });

  it('detects the reduced motion preference', () => {
    expect(REDUCED_MOTION_QUERY).toBe('(prefers-reduced-motion: reduce)');
  });

  it('lists stations within 500 m', () => {
    expect(NEARBY_RADIUS_M).toBe(500);
  });

  it('rounds the nearby center to 4 decimals', () => {
    expect(NEAR_COORDINATE_DECIMALS).toBe(4);
  });

  it('marks availability as low below 5', () => {
    expect(LOW_AVAILABILITY_THRESHOLD).toBe(5);
  });

  it('refreshes relative times every 30 s', () => {
    expect(NOW_TICK_MS).toBe(30_000);
  });

  it('waits up to 10 s for a position and accepts one up to 1 min old', () => {
    expect(GEOLOCATION_TIMEOUT_MS).toBe(10_000);
    expect(GEOLOCATION_MAX_AGE_MS).toBe(60_000);
  });

  it('shows at most 8 search results', () => {
    expect(SEARCH_MAX_RESULTS).toBe(8);
  });

  it('stores favorites under a namespaced key', () => {
    expect(FAVORITES_STORAGE_KEY).toBe('bicimad-now:favorites');
  });
});
