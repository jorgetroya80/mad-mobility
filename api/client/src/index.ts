import createClient, { type Client } from 'openapi-fetch';
import type { components, paths } from './schema.js';

export type { components, paths } from './schema.js';

export type Station = components['schemas']['StationResponse'];
export type StationsResponse = components['schemas']['StationsResponse'];
export type StationDetailResponse = components['schemas']['StationDetailResponse'];
export type Problem = components['schemas']['Problem'];

export function createBicimadClient(baseUrl: string): Client<paths> {
  return createClient<paths>({ baseUrl });
}
