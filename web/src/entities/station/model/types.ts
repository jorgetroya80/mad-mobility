export type { Station } from '@jorgetroya80/mad-transit-client';

export type Need = 'bikes' | 'docks';

export type AvailabilityLevel = 'good' | 'low' | 'empty' | 'off' | 'unconfirmed';

export type DataStatus = 'loading' | 'ok' | 'stale' | 'error' | 'offline';
