import { listStations, type StationsResponse } from '@jorgetroya80/mad-transit-client';
import { env } from '@/shared/config';
import { stationsFixture } from './mocks/fixtures';
import { apiClient } from './client';

describe('apiClient', () => {
  it('lists the stations from the configured base URL', async () => {
    const { data, error, response } = await listStations({ client: apiClient });

    expect(response?.url).toBe(new URL('/api/v1/bicimad/stations', location.href).href);
    expect(error).toBeUndefined();
    expect(data).toEqual(stationsFixture);
    expectTypeOf(data).toEqualTypeOf<StationsResponse | undefined>();
  });

  // Every client path has a handler, so the unhandled request goes through fetch directly.
  it('fails when a request has no MSW handler', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    await expect(fetch(new URL(`${env.apiBaseUrl}/v1/unknown`, location.href))).rejects.toThrow(
      'fetch failed',
    );
    expect(consoleError).toHaveBeenCalledWith(
      expect.stringMatching(/without a matching request handler:\s+• GET \/api\/v1\/unknown/),
    );

    consoleError.mockRestore();
  });
});
