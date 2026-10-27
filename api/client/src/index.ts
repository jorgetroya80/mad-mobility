import type { StationResponse } from './generated/index.js';

// SDK functions (listStations, getStation) and every schema type (StationsResponse, Problem, ...)
export * from './generated/index.js';
export {
  createClient,
  createConfig,
  type Client,
  type Config,
  type CreateClientConfig,
} from './generated/client/index.js';

export type Station = StationResponse;
