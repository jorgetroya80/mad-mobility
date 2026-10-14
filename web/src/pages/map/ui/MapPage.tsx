import { useSelectedStation } from '@/features/select-station';
import { AppLayout } from '@/widgets/app-layout';
import { StationsMap } from '@/widgets/stations-map';
import { useListFocusReturn } from '../model/useListFocusReturn';
import { useMapFocusPadding } from '../model/useMapFocusPadding';
import { useMapLocation } from '../model/useMapLocation';
import { usePanelExpanded } from '../model/usePanelExpanded';
import { MapPanel } from './MapPanel';

export function MapPage() {
  const location = useMapLocation();
  const { number } = useSelectedStation();
  const [panelExpanded, setPanelExpanded] = usePanelExpanded(number);
  const panelRef = useListFocusReturn(number);
  const focusPadding = useMapFocusPadding(panelExpanded);

  return (
    <AppLayout
      title="BiciMAD Now"
      map={
        <StationsMap
          center={location.center}
          userLocation={location.userLocation}
          focusPadding={focusPadding}
          className="h-full w-full"
        />
      }
      panelLabel="Estaciones"
      panelExpanded={panelExpanded}
      onPanelExpandedChange={setPanelExpanded}
      panel={<MapPanel ref={panelRef} location={location} isStationOpen={number !== undefined} />}
    />
  );
}
