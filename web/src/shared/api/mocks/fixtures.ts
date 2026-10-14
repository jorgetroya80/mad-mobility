import type { Problem, Station, StationsResponse } from '@jorgetroya80/bicimad-client';

// Bikes cover every availability level: good (12), low (2), empty (0) and off (NO_SERVICE).
const puertaDelSolA: Station = {
  id: 1,
  number: '1a',
  name: 'Puerta del Sol A',
  address: 'Puerta del Sol nº 1',
  lat: 40.4170224,
  lon: -3.7024255,
  status: 'OPERATIONAL',
  bikes: 12,
  freeDocks: 12,
  totalDocks: 24,
  occupancy: 'MEDIUM',
};

const puertaDelSolB: Station = {
  id: 2,
  number: '1b',
  name: 'Puerta del Sol B',
  address: 'Puerta del Sol nº 1',
  lat: 40.4171037,
  lon: -3.7025031,
  status: 'OPERATIONAL',
  bikes: 2,
  freeDocks: 22,
  totalDocks: 24,
  occupancy: 'LOW',
};

const miguelMoya: Station = {
  id: 3,
  number: '2',
  name: 'Miguel Moya',
  address: 'Calle Miguel Moya nº 1',
  lat: 40.4205886,
  lon: -3.7058415,
  status: 'NO_SERVICE',
  bikes: 0,
  freeDocks: 0,
  totalDocks: 24,
  occupancy: 'UNKNOWN',
};

const condeSuchil: Station = {
  id: 4,
  number: '3',
  name: 'Plaza Conde Suchil',
  address: 'Plaza del Conde del Valle de Suchil nº 3',
  lat: 40.4302937,
  lon: -3.7069171,
  status: 'OPERATIONAL',
  bikes: 0,
  freeDocks: 18,
  totalDocks: 18,
  occupancy: 'LOW',
};

export const stationsFixture: StationsResponse = {
  stations: [puertaDelSolA, puertaDelSolB, miguelMoya, condeSuchil],
  updatedAt: '2026-10-13T10:15:00Z',
  stale: false,
  source: 'EMT Madrid MobilityLabs',
};

export const staleStationsFixture: StationsResponse = {
  ...stationsFixture,
  updatedAt: '2026-10-13T09:45:00Z',
  stale: true,
};

// The `?near=` answer around Puerta del Sol: only stations within the radius, closest first.
export const nearbyStationsFixture: StationsResponse = {
  ...stationsFixture,
  stations: [
    { ...puertaDelSolA, distanceMeters: 12 },
    { ...puertaDelSolB, distanceMeters: 18 },
    { ...miguelMoya, distanceMeters: 420 },
  ],
};

export function stationNotFoundProblem(number: string): Problem {
  return {
    type: 'about:blank',
    title: 'Not Found',
    status: 404,
    detail: `No station with number ${number}`,
    instance: `/v1/bicimad/stations/${number}`,
    requestId: 'mock-request',
  };
}
