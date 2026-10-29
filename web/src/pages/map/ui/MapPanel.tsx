import type { Ref } from 'react';
import { LocateButton } from '@/features/locate-user';
import { NeedToggle } from '@/features/filter-by-need';
import { DataAttribution } from '@/shared/ui';
import { StationDetail } from '@/widgets/station-detail';
import { StationList } from '@/widgets/station-list';
import { TransitSearch } from '@/widgets/transit-search';
import type { MapLocation } from '../model/useMapLocation';

type MapPanelProps = {
  ref: Ref<HTMLDivElement>;
  location: MapLocation;
  isStationOpen: boolean;
};

export function MapPanel({ ref, location, isStationOpen }: MapPanelProps) {
  return (
    <div ref={ref} className="flex min-h-full flex-col gap-4">
      <TransitSearch />
      <div className="flex flex-wrap items-center justify-between gap-2">
        <NeedToggle />
        <LocateButton status={location.status} onLocate={location.locate} />
      </div>
      {isStationOpen ? (
        <StationDetail center={location.center} />
      ) : (
        <StationList
          center={location.center}
          isApproximateLocation={location.isApproximateLocation}
          isLocationDenied={location.isLocationDenied}
        />
      )}
      <footer className="mt-auto">
        <DataAttribution />
      </footer>
    </div>
  );
}
