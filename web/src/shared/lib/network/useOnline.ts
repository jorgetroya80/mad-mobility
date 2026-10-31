import { onlineManager } from '@tanstack/react-query';
import { useSyncExternalStore } from 'react';

// TanStack Query already listens to the browser's online and offline events (and refetches on
// reconnect), so this reads its state instead of adding listeners of its own.
function subscribe(onChange: () => void): () => void {
  return onlineManager.subscribe(onChange);
}

export function useOnline(): boolean {
  return useSyncExternalStore(subscribe, () => onlineManager.isOnline());
}
