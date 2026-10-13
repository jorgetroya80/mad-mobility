import { AppLayout } from '@/widgets/app-layout';
import { BaseMap, DataAttribution, StateMessage } from '@/shared/ui';

export function MapPage() {
  return (
    <>
      <h1 className="sr-only">BiciMAD Now</h1>
      <AppLayout
        map={<BaseMap className="h-full w-full" />}
        panelLabel="Estaciones"
        panel={
          <div className="flex min-h-full flex-col gap-4">
            <StateMessage variant="empty" title="Las estaciones llegan en la siguiente versión" />
            <footer className="mt-auto">
              <DataAttribution />
            </footer>
          </div>
        }
      />
    </>
  );
}
