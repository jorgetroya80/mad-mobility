import { useCallback, useEffect, useState } from 'react';
import { GEOLOCATION_MAX_AGE_MS, GEOLOCATION_TIMEOUT_MS } from '@/shared/config';

export type GeoPosition = { lat: number; lng: number };

export type GeolocationStatus = 'pending' | 'granted' | 'denied' | 'unavailable';

type GeolocationReading = { status: GeolocationStatus; position?: GeoPosition };

export type GeolocationState = GeolocationReading & { retry: () => void };

const POSITION_OPTIONS: PositionOptions = {
  timeout: GEOLOCATION_TIMEOUT_MS,
  maximumAge: GEOLOCATION_MAX_AGE_MS,
};

function isSupported(): boolean {
  return 'geolocation' in navigator;
}

function initialReading(): GeolocationReading {
  return { status: isSupported() ? 'pending' : 'unavailable' };
}

function toReading({ coords }: GeolocationPosition): GeolocationReading {
  return { status: 'granted', position: { lat: coords.latitude, lng: coords.longitude } };
}

function statusFor(error: GeolocationPositionError): GeolocationStatus {
  return error.code === error.PERMISSION_DENIED ? 'denied' : 'unavailable';
}

// Asks whenever the status turns pending (on mount and on retry), never with watchPosition:
// the page decides what to show while it is not granted.
export function useGeolocation(): GeolocationState {
  const [reading, setReading] = useState(initialReading);
  const pending = reading.status === 'pending';

  useEffect(() => {
    if (!pending) return;
    let active = true;
    navigator.geolocation.getCurrentPosition(
      (position) => active && setReading(toReading(position)),
      (error) => active && setReading({ status: statusFor(error) }),
      POSITION_OPTIONS,
    );
    return () => {
      active = false;
    };
  }, [pending]);

  const retry = useCallback(() => setReading(initialReading()), []);

  return { ...reading, retry };
}
