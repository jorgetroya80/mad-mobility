import type { StationsResponse } from '@jorgetroya80/bicimad-client';
import { stationsFixture } from './mocks/fixtures';
import { apiClient } from './client';

describe('apiClient', () => {
  it('lists the stations from the configured base URL', async () => {
    const { data, error, response } = await apiClient.GET('/v1/bicimad/stations');

    expect(response.url).toBe(new URL('/api/v1/bicimad/stations', location.href).href);
    expect(error).toBeUndefined();
    expect(data).toEqual(stationsFixture);
    expectTypeOf(data).toEqualTypeOf<StationsResponse | undefined>();
  });

  it('fails when a request has no MSW handler', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

    await expect(
      apiClient.GET('/v1/bicimad/stations/{number}', { params: { path: { number: '1a' } } }),
    ).rejects.toThrow('fetch failed');
    expect(consoleError).toHaveBeenCalledWith(
      expect.stringMatching(
        /without a matching request handler:\s+• GET \/api\/v1\/bicimad\/stations\/1a/,
      ),
    );

    consoleError.mockRestore();
  });
});
