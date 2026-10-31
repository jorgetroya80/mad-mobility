import { setupWorker } from 'msw/browser';
import { apiTimestamp, createHandlers } from './handlers';

// dev:mock dates the data now, so the stations look confirmed instead of 5 min old and grey.
export const worker = setupWorker(...createHandlers({ updatedAt: () => apiTimestamp(new Date()) }));
