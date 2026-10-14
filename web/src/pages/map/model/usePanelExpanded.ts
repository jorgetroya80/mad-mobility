import { useState } from 'react';

// Below md the bottom sheet opens whenever a station gets selected, whatever the source (map,
// list, search or link); the handle can still collapse it. State is adjusted during render,
// so the detail mounts already outside the inert collapsed sheet and can take the focus.
export function usePanelExpanded(selectedStation: string | undefined) {
  const [expanded, setExpanded] = useState(selectedStation !== undefined);
  const [shownStation, setShownStation] = useState(selectedStation);

  if (selectedStation !== shownStation) {
    setShownStation(selectedStation);
    if (selectedStation !== undefined) setExpanded(true);
  }

  return [expanded, setExpanded] as const;
}
