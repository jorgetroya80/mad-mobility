import { useState } from 'react';

export type UserFocus = {
  // False while the user asked for their location with this station open: the map follows them.
  focusSelected: boolean;
  focusUser: () => void;
  focusSelection: () => void;
};

// "Mi ubicación" with a station open moves the map to the user and keeps the detail. Opening,
// closing or changing the station gives the focus back to the selection, and so does tapping it
// again on the map.
export function useUserFocus(number: string | undefined): UserFocus {
  const [focus, setFocus] = useState({ number, onUser: false });
  // Adjusted during render: this render still sees the old state, hence the check below.
  if (focus.number !== number) setFocus({ number, onUser: false });
  const onUser = focus.number === number && focus.onUser;

  return {
    focusSelected: !onUser,
    focusUser: () => setFocus({ number, onUser: true }),
    focusSelection: () => setFocus({ number, onUser: false }),
  };
}
