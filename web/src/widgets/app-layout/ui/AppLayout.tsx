import type { ReactNode } from 'react';
import { BottomSheet, SidePanel } from '@/shared/ui';

type AppLayoutProps = {
  map: ReactNode;
  panel: ReactNode;
  panelLabel: string;
};

// Full-screen map plus one panel: a bottom sheet below md (its handle strip takes the last row
// of the column, so the map ends above it), a fixed side panel from md up.
// Both containers are rendered and CSS shows one, so the layout needs no viewport detection;
// the hidden one is display: none, which also removes it from the accessibility tree.
export function AppLayout({ map, panel, panelLabel }: AppLayoutProps) {
  return (
    <div className="flex h-dvh flex-col overflow-hidden md:flex-row">
      <SidePanel label={panelLabel} className="hidden p-4 md:block">
        {panel}
      </SidePanel>
      <main className="relative min-h-0 min-w-0 flex-1">{map}</main>
      <div className="md:hidden">
        <BottomSheet label={panelLabel}>{panel}</BottomSheet>
      </div>
    </div>
  );
}
