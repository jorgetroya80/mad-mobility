import { useState } from 'react';
import type { Coordinates } from '@/entities/station';
import { MADRID_CENTER } from '@/shared/config';
import { type GeolocationStatus, useGeolocation } from '@/shared/lib';

export type MapLocation = {
  // Undefined until the first answer: the list waits and the map does not move.
  center: Coordinates | undefined;
  // Undefined while asking, so the answer moves the map back even when the position is the same.
  mapCenter: Coordinates | undefined;
  userLocation: Coordinates | undefined;
  // The center is Puerta del Sol because the position was denied or is unavailable.
  isApproximateLocation: boolean;
  status: GeolocationStatus;
  locate: () => void;
};

type SettledStatus = Exclude<GeolocationStatus, 'pending'>;
type SettledLocation = { status: SettledStatus; position: Coordinates | undefined };

// While "Mi ubicación" asks again, the last answer keeps describing the list still on screen,
// so its heading and notice do not change before the new answer arrives.
function useLastSettledLocation(
  status: GeolocationStatus,
  position: Coordinates | undefined,
): SettledLocation | undefined {
  const [settled, setSettled] = useState<SettledLocation>();
  if (status === 'pending') return settled;
  if (status !== settled?.status || position !== settled.position) {
    setSettled({ status, position });
  }
  return { status, position };
}

// The list is computed around the user (or Sol), never around the map's viewport.
export function useMapLocation(): MapLocation {
  const { status, position, retry } = useGeolocation();
  const settled = useLastSettledLocation(status, position);
  const isApproximateLocation = settled?.status === 'denied' || settled?.status === 'unavailable';
  const center = isApproximateLocation ? MADRID_CENTER : settled?.position;

  return {
    center,
    mapCenter: status === 'pending' ? undefined : center,
    userLocation: settled?.position,
    isApproximateLocation,
    status,
    locate: retry,
  };
}
