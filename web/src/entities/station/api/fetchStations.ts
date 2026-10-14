import type { StationDetailResponse, StationsResponse } from '@jorgetroya80/bicimad-client';
import { apiClient } from '@/shared/api';
import { NEARBY_RADIUS_M } from '@/shared/config';
import type { Need } from '../model/types';
import { StationNotFoundError } from './StationNotFoundError';

const NOT_FOUND = 404;

type RequestOptions = { signal: AbortSignal };

function requestFailed(response: Response, problem: unknown): Error {
  return new Error(`GET ${response.url} failed with ${response.status}`, { cause: problem });
}

export async function fetchAllStations({ signal }: RequestOptions): Promise<StationsResponse> {
  const { data, error, response } = await apiClient.GET('/v1/bicimad/stations', { signal });
  if (error) throw requestFailed(response, error);
  return data;
}

type NearbyRequest = RequestOptions & { near: string; need: Need };

export async function fetchNearbyStations({
  near,
  need,
  signal,
}: NearbyRequest): Promise<StationsResponse> {
  const { data, error, response } = await apiClient.GET('/v1/bicimad/stations', {
    params: { query: { near, radius: NEARBY_RADIUS_M, need } },
    signal,
  });
  if (error) throw requestFailed(response, error);
  return data;
}

type StationRequest = RequestOptions & { number: string };

export async function fetchStation({
  number,
  signal,
}: StationRequest): Promise<StationDetailResponse> {
  const { data, error, response } = await apiClient.GET('/v1/bicimad/stations/{number}', {
    params: { path: { number } },
    signal,
  });
  if (response.status === NOT_FOUND) throw new StationNotFoundError(number);
  if (error) throw requestFailed(response, error);
  return data;
}
