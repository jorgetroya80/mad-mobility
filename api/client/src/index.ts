import createClient, { type Client, type ClientOptions } from 'openapi-fetch';
import type { components, paths } from './schema.js';

export type { components, paths } from './schema.js';

export type Station = components['schemas']['StationResponse'];
export type StationsResponse = components['schemas']['StationsResponse'];
export type StationDetailResponse = components['schemas']['StationDetailResponse'];
export type Problem = components['schemas']['Problem'];

export type BicimadClientOptions = Pick<ClientOptions, 'fetch'>;

export function createBicimadClient(
  baseUrl: string,
  options: BicimadClientOptions = {},
): Client<paths> {
  return createClient<paths>({ baseUrl, ...options });
}
