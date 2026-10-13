import type { ReactNode } from 'react';
import { MD_MEDIA_QUERY } from '@/shared/config';
import { useMediaQuery } from '@/shared/lib';
import { BottomSheet, SidePanel } from '@/shared/ui';

type AppLayoutProps = {
  title: string;
  map: ReactNode;
  panel: ReactNode;
  panelLabel: string;
};

// Full-screen map plus one panel: a bottom sheet below md (its handle strip takes the last row
// of the column, so the map ends above it), a fixed side panel from md up.
// Only one container is mounted, so the panel content and its state exist once.
export function AppLayout({ title, map, panel, panelLabel }: AppLayoutProps) {
  const isWideViewport = useMediaQuery(MD_MEDIA_QUERY);

  return (
    <main className="flex h-dvh flex-col overflow-hidden md:flex-row">
      <h1 className="sr-only">{title}</h1>
      {isWideViewport && (
        <SidePanel label={panelLabel} className="p-4">
          {panel}
        </SidePanel>
      )}
      <div className="relative min-h-0 min-w-0 flex-1">{map}</div>
      {!isWideViewport && <BottomSheet label={panelLabel}>{panel}</BottomSheet>}
    </main>
  );
}
