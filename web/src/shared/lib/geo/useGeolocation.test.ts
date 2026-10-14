import { act, renderHook } from '@testing-library/react';
import { GEOLOCATION_MAX_AGE_MS, GEOLOCATION_TIMEOUT_MS } from '@/shared/config';
import { useGeolocation } from './useGeolocation';

const ERROR_CODES = { PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3 } as const;

type Request = {
  onSuccess: PositionCallback;
  onError: PositionErrorCallback;
  options?: PositionOptions;
};

// jsdom has no geolocation: each request is kept so the test decides how it ends.
function stubGeolocation() {
  const requests: Request[] = [];
  const getCurrentPosition = vi.fn<Geolocation['getCurrentPosition']>(
    (onSuccess, onError, options) => {
      requests.push({ onSuccess, onError: onError ?? (() => {}), options });
    },
  );
  Object.defineProperty(navigator, 'geolocation', {
    value: { getCurrentPosition },
    configurable: true,
  });

  const last = () => {
    const request = requests.at(-1);
    if (!request) throw new Error('No geolocation request was made');
    return request;
  };
  return {
    getCurrentPosition,
    succeed(latitude: number, longitude: number) {
      const position = { coords: { latitude, longitude } } as GeolocationPosition;
      act(() => last().onSuccess(position));
    },
    fail(code: keyof typeof ERROR_CODES) {
      const error = {
        code: ERROR_CODES[code],
        message: code,
        ...ERROR_CODES,
      } as GeolocationPositionError;
      act(() => last().onError(error));
    },
    get options() {
      return last().options;
    },
  };
}

function removeGeolocation() {
  Reflect.deleteProperty(navigator, 'geolocation');
}

describe('useGeolocation', () => {
  afterEach(removeGeolocation);

  it('starts pending and asks once with the configured timeout and maximum age', () => {
    const geolocation = stubGeolocation();

    const { result } = renderHook(() => useGeolocation());

    expect(result.current.status).toBe('pending');
    expect(geolocation.getCurrentPosition).toHaveBeenCalledTimes(1);
    expect(geolocation.options).toEqual({
      timeout: GEOLOCATION_TIMEOUT_MS,
      maximumAge: GEOLOCATION_MAX_AGE_MS,
    });
  });

  it('maps a granted position to lat and lng', () => {
    const geolocation = stubGeolocation();
    const { result } = renderHook(() => useGeolocation());

    geolocation.succeed(40.42, -3.7);

    expect(result.current.status).toBe('granted');
    expect(result.current.position).toEqual({ lat: 40.42, lng: -3.7 });
  });

  it('is denied when the user refuses permission', () => {
    const geolocation = stubGeolocation();
    const { result } = renderHook(() => useGeolocation());

    geolocation.fail('PERMISSION_DENIED');

    expect(result.current.status).toBe('denied');
    expect(result.current.position).toBeUndefined();
  });

  it.each(['POSITION_UNAVAILABLE', 'TIMEOUT'] as const)('is unavailable on %s', (code) => {
    const geolocation = stubGeolocation();
    const { result } = renderHook(() => useGeolocation());

    geolocation.fail(code);

    expect(result.current.status).toBe('unavailable');
  });

  it('is unavailable without navigator.geolocation', () => {
    const { result } = renderHook(() => useGeolocation());

    expect(result.current.status).toBe('unavailable');
  });

  it('stays unavailable on retry without navigator.geolocation', () => {
    const { result } = renderHook(() => useGeolocation());

    act(() => result.current.retry());

    expect(result.current.status).toBe('unavailable');
  });

  it('asks again on retry, going through pending', () => {
    const geolocation = stubGeolocation();
    const { result } = renderHook(() => useGeolocation());
    geolocation.fail('PERMISSION_DENIED');

    act(() => result.current.retry());

    expect(result.current.status).toBe('pending');
    expect(geolocation.getCurrentPosition).toHaveBeenCalledTimes(2);
    geolocation.succeed(40.42, -3.7);
    expect(result.current.status).toBe('granted');
  });

  it('ignores a result that arrives after unmount', () => {
    const geolocation = stubGeolocation();
    const consoleError = vi.spyOn(console, 'error');
    const { result, unmount } = renderHook(() => useGeolocation());

    unmount();
    geolocation.succeed(40.42, -3.7);

    expect(result.current.status).toBe('pending');
    expect(consoleError).not.toHaveBeenCalled();
  });
});
