import { useCallback, useMemo, useSyncExternalStore } from 'react';

type Listener = () => void;

const listenersByKey = new Map<string, Set<Listener>>();
// Values whose write failed (private mode, quota): they last until the page reloads.
const unsavedByKey = new Map<string, string>();

// Raw access without a hook, for values no component subscribes to (the stations cache).
export function readRaw(key: string): string | null {
  const unsaved = unsavedByKey.get(key);
  if (unsaved !== undefined) return unsaved;
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeRaw(key: string, raw: string): void {
  try {
    localStorage.setItem(key, raw);
    unsavedByKey.delete(key);
  } catch {
    unsavedByKey.set(key, raw);
  }
}

function notify(key: string): void {
  for (const listener of listenersByKey.get(key) ?? []) listener();
}

function subscribe(key: string, listener: Listener): () => void {
  const listeners = listenersByKey.get(key) ?? new Set<Listener>();
  listenersByKey.set(key, listeners);
  listeners.add(listener);
  // `key` is null when another tab clears the whole storage.
  const onStorage = (event: StorageEvent) => {
    if (event.key === key || event.key === null) listener();
  };
  window.addEventListener('storage', onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener('storage', onStorage);
  };
}

function decode<T>(raw: string | null, parse: (raw: unknown) => T | undefined, fallback: T): T {
  if (raw === null) return fallback;
  try {
    const value = parse(JSON.parse(raw));
    return value === undefined ? fallback : value;
  } catch {
    return fallback;
  }
}

/**
 * JSON value in localStorage, shared by every hook using the same key. `parse` validates the
 * stored value (undefined means invalid → `fallback`). Keep `parse` and `fallback` stable
 * (module-level), or the value is re-parsed and changes identity on every render.
 */
export function useLocalStorage<T>(
  key: string,
  parse: (raw: unknown) => T | undefined,
  fallback: T,
): [T, (value: T) => void] {
  const subscribeToKey = useCallback((listener: Listener) => subscribe(key, listener), [key]);
  // The snapshot is the raw string: a primitive, so React sees no change until storage changes.
  const raw = useSyncExternalStore(subscribeToKey, () => readRaw(key));
  const value = useMemo(() => decode(raw, parse, fallback), [raw, parse, fallback]);

  const setValue = useCallback(
    (next: T) => {
      writeRaw(key, JSON.stringify(next));
      notify(key);
    },
    [key],
  );

  return [value, setValue];
}
