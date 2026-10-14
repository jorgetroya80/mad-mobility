import { MD_MEDIA_QUERY } from '@/shared/config';
import { useMediaQuery } from '@/shared/lib';
import { useBottomSheetOverlap } from '@/shared/ui';

// Below md the expanded bottom sheet covers the bottom of the map, so the focused station is
// centered above it; the side panel from md up sits beside the map and covers none of it.
export function useMapFocusPadding(panelExpanded: boolean) {
  const isWideViewport = useMediaQuery(MD_MEDIA_QUERY);
  const sheetOverlap = useBottomSheetOverlap();
  const coversMap = panelExpanded && !isWideViewport;
  return { bottom: coversMap ? sheetOverlap : 0 };
}
