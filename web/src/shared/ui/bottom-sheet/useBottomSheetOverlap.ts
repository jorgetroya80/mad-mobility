import { useViewportHeight } from '@/shared/lib';
import { EXPANDED_SHEET_VIEWPORT_PERCENT, HANDLE_HEIGHT_PX } from './sheetSize';

// Height of the content above the sheet that the expanded sheet covers: the handle strip is
// in the page flow, so that content already ends above it. innerHeight follows the viewport
// the same way dvh does.
export function useBottomSheetOverlap(): number {
  const viewportHeight = useViewportHeight();
  return Math.round((viewportHeight * EXPANDED_SHEET_VIEWPORT_PERCENT) / 100 - HANDLE_HEIGHT_PX);
}
