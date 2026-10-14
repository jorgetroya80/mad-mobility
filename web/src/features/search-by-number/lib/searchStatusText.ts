import type { SearchSource } from '../model/types';

type StatusSource = Pick<SearchSource, 'status' | 'itemLabel' | 'itemLabelPlural'>;

function countText(source: StatusSource, count: number) {
  return `${count} ${count === 1 ? source.itemLabel : source.itemLabelPlural}`;
}

/** Text for the live region below the search box; empty when there is nothing to announce. */
export function searchStatusText(source: StatusSource, query: string, resultCount: number) {
  const typed = query.trim();
  if (typed === '') return '';
  if (source.status === 'loading') return `Cargando ${source.itemLabelPlural}…`;
  if (source.status === 'error') return `No se pudieron cargar las ${source.itemLabelPlural}`;
  if (resultCount > 0) return countText(source, resultCount);
  return `Ninguna ${source.itemLabel} con el número ${typed}`;
}
