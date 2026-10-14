import { useCallback, useEffect, useRef } from 'react';
import {
  findFirstSectionHeading,
  findRow,
  type RowOrigin,
  rowOriginOf,
} from '../lib/stationListFocus';

// The list unmounts while a detail is open, so the row a detail came from is remembered here
// (section + number, from the click) and focused again once the list is back. A detail opened
// from the map, the search or a link has no row: the first list heading gets the focus, which
// announces where the user is without opening the on-screen keyboard as the search box would.
export function useListFocusReturn(openStation: string | undefined) {
  const panelRef = useRef<HTMLElement | null>(null);
  const originRef = useRef<RowOrigin | undefined>(undefined);
  const lastOpenRef = useRef(openStation);

  // Listens to the rows' clicks bubbling up (Enter and Space on a button click it too), so the
  // list needs no callback for it. The panel remounts when the layout crosses md.
  const attachPanel = useCallback((panel: HTMLElement | null) => {
    if (!panel) return undefined;
    const recordOrigin = (event: Event) => {
      const origin = rowOriginOf(event.target);
      if (origin) originRef.current = origin;
    };
    panelRef.current = panel;
    panel.addEventListener('click', recordOrigin);
    return () => {
      panel.removeEventListener('click', recordOrigin);
      panelRef.current = null;
    };
  }, []);

  useEffect(() => {
    const closedStation = lastOpenRef.current;
    lastOpenRef.current = openStation;
    const panel = panelRef.current;
    if (openStation !== undefined || closedStation === undefined || !panel) return;

    const origin = originRef.current;
    originRef.current = undefined;
    const row = origin?.number === closedStation ? findRow(panel, origin) : undefined;
    (row ?? findFirstSectionHeading(panel))?.focus();
  }, [openStation]);

  return attachPanel;
}
