import { env, MADRID_CENTER, MADRID_ZOOM, POLL_INTERVAL_MS } from '.';

describe('shared/config public API', () => {
  it('exposes the API base URL read from the environment', () => {
    expect(env.apiBaseUrl).toBe('/api');
  });

  it('polls every minute', () => {
    expect(POLL_INTERVAL_MS).toBe(60_000);
  });

  it('centers the map on Puerta del Sol', () => {
    expect(MADRID_CENTER).toEqual({ lng: -3.7038, lat: 40.4168 });
    expect(MADRID_ZOOM).toBe(15);
  });
});
