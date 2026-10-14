import { useState } from 'react';
import { SEARCH_MAX_RESULTS } from '@/shared/config';
import { matchByNumber } from '../lib/matchByNumber';
import type { SearchItem, SearchSource } from './types';

export type SearchSources = readonly [SearchSource, ...SearchSource[]];

/** Chosen source (local state, first by default), the query and its results. */
export function useNumberSearch(sources: SearchSources) {
  const [first] = sources;
  const [selectedId, setSelectedId] = useState(first.id);
  const [query, setQuery] = useState('');
  const source = sources.find((candidate) => candidate.id === selectedId) ?? first;
  const results = matchByNumber(source.items, query, SEARCH_MAX_RESULTS);
  // Clearing the box after choosing leaves it ready for the next search.
  const choose = (item: SearchItem) => {
    setQuery('');
    source.onSelect(item.number);
  };

  // Only the source changes: the same query is searched again in the new one.
  return { source, query, results, choose, setQuery, selectSource: setSelectedId };
}
