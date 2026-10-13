import { MapPage } from '@/pages/map';
import { QueryProvider } from './providers/QueryProvider';

export function App() {
  return (
    <QueryProvider>
      <MapPage />
    </QueryProvider>
  );
}
