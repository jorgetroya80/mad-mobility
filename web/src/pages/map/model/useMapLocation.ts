import type { Coordinates } from '@/entities/station';
import { MADRID_CENTER } from '@/shared/config';
import { type GeolocationStatus, useGeolocation } from '@/shared/lib';

export type MapLocation = {
  // Undefined while the position is pending: the list waits and the map does not move.
  center: Coordinates | undefined;
  userLocation: Coordinates | undefined;
  // The center is Puerta del Sol because the position was denied or is unavailable.
  isApproximateLocation: boolean;
  status: GeolocationStatus;
  locate: () => void;
};

// The list is computed around the user (or Sol), never around the map's viewport.
export function useMapLocation(): MapLocation {
  const { status, position, retry } = useGeolocation();
  const isApproximateLocation = status === 'denied' || status === 'unavailable';

  return {
    center: isApproximateLocation ? MADRID_CENTER : position,
    userLocation: position,
    isApproximateLocation,
    status,
    locate: retry,
  };
}
