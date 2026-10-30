import {
  getStation,
  listStations,
  type StationDetailResponse,
  type StationsResponse,
} from '@jorgetroya80/mad-transit-client';
import { apiClient } from '@/shared/api';
import { NEARBY_RADIUS_M } from '@/shared/config';
import type { Need } from '../model/types';
import { StationNotFoundError } from './StationNotFoundError';

const NOT_FOUND = 404;

type RequestOptions = { signal: AbortSignal };

type RequestResult = { error: unknown; request?: Request; response?: Response };

// Without a response the request never reached the API (network error or abort);
// without a request it could not even be built.
function requestFailed({ error, request, response }: RequestResult): Error {
  const status = response ? ` with ${response.status}` : '';
  return new Error(`GET ${request?.url ?? 'request'} failed${status}`, { cause: error });
}

export async function fetchAllStations({ signal }: RequestOptions): Promise<StationsResponse> {
  const result = await listStations({ client: apiClient, signal });
  if (result.error) throw requestFailed(result);
  return result.data;
}

type NearbyRequest = RequestOptions & { near: string; need: Need };

export async function fetchNearbyStations({
  near,
  need,
  signal,
}: NearbyRequest): Promise<StationsResponse> {
  const result = await listStations({
    client: apiClient,
    query: { near, radius: NEARBY_RADIUS_M, need },
    signal,
  });
  if (result.error) throw requestFailed(result);
  return result.data;
}

type StationRequest = RequestOptions & { number: string };

export async function fetchStation({
  number,
  signal,
}: StationRequest): Promise<StationDetailResponse> {
  const result = await getStation({
    client: apiClient,
    path: { number },
    signal,
  });
  if (result.response?.status === NOT_FOUND) throw new StationNotFoundError(number);
  if (result.error) throw requestFailed(result);
  return result.data;
}
