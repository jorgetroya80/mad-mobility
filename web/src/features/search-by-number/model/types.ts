export type SearchItem = {
  /** What the user types: '25A' (station), '1234' (stop). */
  number: string;
  title: string;
  subtitle?: string;
};

export type SearchSource = {
  id: string;
  label: string;
  itemLabel: string;
  /** Spanish plurals are irregular ('estación' → 'estaciones'), so each source gives its own. */
  itemLabelPlural: string;
  items: readonly SearchItem[];
  status: 'loading' | 'ready' | 'error';
  onSelect: (number: string) => void;
};
