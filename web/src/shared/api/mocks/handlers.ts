import type { StationsResponse } from '@jorgetroya80/bicimad-client';
import { http, HttpResponse } from 'msw';
import { env } from '@/shared/config';
import { stationsFixture } from './fixtures';

export const handlers = [
  http.get<never, never, StationsResponse>(`${env.apiBaseUrl}/v1/bicimad/stations`, () =>
    HttpResponse.json(stationsFixture),
  ),
];
