// DOM contract of widgets/station-list: each section carries `data-station-list` and its
// focusable heading; each row, `data-station-number`.
const SECTION_ATTRIBUTE = 'data-station-list';
const ROW_SELECTOR = '[data-station-number]';
const SECTION_HEADING_SELECTOR = `[${SECTION_ATTRIBUTE}] h2`;

// A station can be listed in two sections (favorite and nearby), so the number alone is not enough.
export type RowOrigin = { section: string; number: string };

export function rowOriginOf(target: EventTarget | null): RowOrigin | undefined {
  if (!(target instanceof Element)) return undefined;
  const row = target.closest<HTMLElement>(ROW_SELECTOR);
  const number = row?.dataset.stationNumber;
  const section = row?.closest(`[${SECTION_ATTRIBUTE}]`)?.getAttribute(SECTION_ATTRIBUTE);
  if (number === undefined || !section) return undefined;
  return { section, number };
}

export function findRow(container: Element, { section, number }: RowOrigin) {
  const rows = container.querySelectorAll<HTMLElement>(
    `[${SECTION_ATTRIBUTE}="${section}"] ${ROW_SELECTOR}`,
  );
  return Array.from(rows).find((row) => row.dataset.stationNumber === number);
}

export function findFirstSectionHeading(container: Element) {
  return container.querySelector<HTMLElement>(SECTION_HEADING_SELECTOR) ?? undefined;
}
