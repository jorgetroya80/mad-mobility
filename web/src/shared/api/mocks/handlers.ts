import type {
  Problem,
  StationDetailResponse,
  StationsResponse,
} from '@jorgetroya80/bicimad-client';
import { http, HttpResponse } from 'msw';
import { env } from '@/shared/config';
import { nearbyStationsFixture, stationNotFoundProblem, stationsFixture } from './fixtures';

const STATIONS_URL = `${env.apiBaseUrl}/v1/bicimad/stations`;
const NOT_FOUND = 404;

function findStation(number: string) {
  const wanted = number.toLowerCase();
  return stationsFixture.stations.find((station) => station.number.toLowerCase() === wanted);
}

export const handlers = [
  http.get<never, never, StationsResponse>(STATIONS_URL, ({ request }) => {
    const near = new URL(request.url).searchParams.has('near');
    return HttpResponse.json(near ? nearbyStationsFixture : stationsFixture);
  }),
  http.get<{ number: string }, never, StationDetailResponse | Problem>(
    `${STATIONS_URL}/:number`,
    ({ params }) => {
      const station = findStation(params.number);
      if (!station) {
        return HttpResponse.json(stationNotFoundProblem(params.number), {
          status: NOT_FOUND,
          headers: { 'Content-Type': 'application/problem+json' },
        });
      }
      const { updatedAt, stale, source } = stationsFixture;
      return HttpResponse.json({ station, updatedAt, stale, source });
    },
  ),
];
