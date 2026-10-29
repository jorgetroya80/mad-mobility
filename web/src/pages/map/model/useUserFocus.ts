import { useState } from 'react';

export type UserFocus = {
  // False while the user asked for their location with this station open: the map follows them.
  focusSelected: boolean;
  focusUser: () => void;
};

// "Mi ubicación" with a station open moves the map to the user and keeps the detail. Opening,
// closing or changing the station gives the focus back to the selection.
export function useUserFocus(number: string | undefined): UserFocus {
  const [focus, setFocus] = useState({ number, onUser: false });
  if (focus.number !== number) setFocus({ number, onUser: false });
  const onUser = focus.number === number && focus.onUser;

  return {
    focusSelected: !onUser,
    focusUser: () => setFocus({ number, onUser: true }),
  };
}
