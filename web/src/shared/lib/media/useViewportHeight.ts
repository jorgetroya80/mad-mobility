import { useSyncExternalStore } from 'react';

// Rotating the device or showing the mobile browser toolbar also fires `resize`.
function subscribe(onChange: () => void) {
  window.addEventListener('resize', onChange);
  return () => window.removeEventListener('resize', onChange);
}

function getViewportHeight() {
  return window.innerHeight;
}

export function useViewportHeight(): number {
  return useSyncExternalStore(subscribe, getViewportHeight);
}
