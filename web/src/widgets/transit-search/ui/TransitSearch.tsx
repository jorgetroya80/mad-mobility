import { SearchByNumber } from '@/features/search-by-number';
import { useBicimadSource } from '../model/useBicimadSource';

// Each transport mode adds its own source here; with two or more, the search shows a picker.
export function TransitSearch() {
  const bicimad = useBicimadSource();
  return <SearchByNumber sources={[bicimad]} />;
}
