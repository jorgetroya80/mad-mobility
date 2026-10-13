import { BaseMap } from '@/shared/ui';

export function MapPage() {
  return (
    <main className="h-dvh">
      <h1 className="sr-only">BiciMAD Now</h1>
      <BaseMap className="h-full w-full" />
    </main>
  );
}
