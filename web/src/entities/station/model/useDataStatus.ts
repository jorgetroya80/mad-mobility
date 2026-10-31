import { NOW_TICK_MS } from '@/shared/config';
import { useNow, useOnline } from '@/shared/lib';
import { dataStatus } from './dataStatus';
import { parseUpdatedAt } from './parseUpdatedAt';
import type { DataStatus } from './types';

// The slice of a station query this needs. Every station response carries the API's
// `updatedAt` and its `stale` flag.
type StationsQuery = {
  data: { updatedAt: string; stale: boolean } | undefined;
  isError: boolean;
  dataUpdatedAt: number;
  refetch: () => unknown;
};

export type DataStatusResult = {
  status: DataStatus;
  updatedAt: Date | undefined;
  retry: () => void;
};

// Placeholder data has no arrival time (0): its age is unknown rather than 1970.
function receivedAt(dataUpdatedAt: number): Date | undefined {
  return dataUpdatedAt > 0 ? new Date(dataUpdatedAt) : undefined;
}

// Without a readable API date, the data is as old as the response that brought it. The clock
// ticks so data turns stale after 5 min even when nothing else re-renders.
export function useDataStatus({
  data,
  isError,
  dataUpdatedAt,
  refetch,
}: StationsQuery): DataStatusResult {
  const online = useOnline();
  const now = useNow(NOW_TICK_MS);
  const updatedAt = data
    ? (parseUpdatedAt(data.updatedAt) ?? receivedAt(dataUpdatedAt))
    : undefined;

  return {
    status: dataStatus({
      hasData: data !== undefined,
      failed: isError,
      apiStale: data?.stale ?? false,
      updatedAt,
      now: now.getTime(),
      online,
    }),
    updatedAt,
    retry: () => void refetch(),
  };
}
