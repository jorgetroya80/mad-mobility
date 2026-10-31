import { getStation, listStations } from '@jorgetroya80/mad-transit-client';
import { apiClient } from '../client';
import { stationsFixture } from './fixtures';
import { apiTimestamp, createHandlers } from './handlers';
import { server } from './node';

const NOW = '2026-10-31T16:45:12Z';

describe('createHandlers', () => {
  it('keeps the fixture dates by default, so tests stay deterministic', async () => {
    const { data } = await listStations({ client: apiClient });

    expect(data?.updatedAt).toBe(stationsFixture.updatedAt);
  });

  it('dates every response with the given clock', async () => {
    server.use(...createHandlers({ updatedAt: () => NOW }));

    const list = await listStations({ client: apiClient });
    const nearby = await listStations({
      client: apiClient,
      query: { near: '40.4170,-3.7024', radius: 500, need: 'bikes' },
    });
    const detail = await getStation({ client: apiClient, path: { number: '1a' } });

    expect(list.data?.updatedAt).toBe(NOW);
    expect(nearby.data?.updatedAt).toBe(NOW);
    expect(detail.data?.updatedAt).toBe(NOW);
  });
});

describe('apiTimestamp', () => {
  it('writes the date in whole seconds, like the API', () => {
    expect(apiTimestamp(new Date('2026-10-31T16:45:12.789Z'))).toBe(NOW);
  });
});
