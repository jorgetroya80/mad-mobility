import { searchStatusText } from '../lib/searchStatusText';
import type { SearchSources } from '../model/useNumberSearch';
import { useNumberSearch } from '../model/useNumberSearch';
import { useResultsKeyboard } from '../model/useResultsKeyboard';
import { ResultList } from './ResultList';
import { SearchInput } from './SearchInput';
import { SourcePicker } from './SourcePicker';

type SearchByNumberProps = {
  sources: SearchSources;
};

export function SearchByNumber({ sources }: SearchByNumberProps) {
  const search = useNumberSearch(sources);
  const { source, query, results } = search;
  const { inputRef, listRef, choose, onInputKeyDown, onResultKeyDown } = useResultsKeyboard({
    results,
    query,
    onChoose: search.choose,
  });

  return (
    <div className="flex flex-col gap-2">
      {sources.length > 1 ? (
        <SourcePicker sources={sources} selectedId={source.id} onChange={search.selectSource} />
      ) : null}
      <SearchInput
        ref={inputRef}
        itemLabel={source.itemLabel}
        query={query}
        onQueryChange={search.setQuery}
        onKeyDown={onInputKeyDown}
      />
      {results.length > 0 ? (
        <ResultList
          ref={listRef}
          results={results}
          onChoose={choose}
          onResultKeyDown={onResultKeyDown}
        />
      ) : null}
      <output className="text-sm text-slate-700 empty:hidden">
        {searchStatusText(source, query, results.length)}
      </output>
    </div>
  );
}
