type ChangeListener = (event: { matches: boolean }) => void;

// jsdom has no matchMedia. Every query gets the same answer; `change` switches it and
// notifies the listeners, like a viewport crossing the breakpoint.
export function stubMatchMedia(initialMatches: boolean) {
  let matches = initialMatches;
  const listeners = new Set<ChangeListener>();
  vi.stubGlobal('matchMedia', (media: string) => ({
    get matches() {
      return matches;
    },
    media,
    addEventListener: (_type: 'change', listener: ChangeListener) => listeners.add(listener),
    removeEventListener: (_type: 'change', listener: ChangeListener) => listeners.delete(listener),
  }));

  return {
    change(nextMatches: boolean) {
      matches = nextMatches;
      for (const listener of listeners) listener({ matches });
    },
    get listenerCount() {
      return listeners.size;
    },
  };
}
