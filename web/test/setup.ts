import '@testing-library/jest-dom/vitest';
import { server } from '@/shared/api/mocks/node';
import { stubMatchMedia } from './matchMedia';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
// Below md by default; a test calls stubMatchMedia(true) for the wide layout.
beforeEach(() => stubMatchMedia(false));
// jsdom does not implement scrollTo, which the router calls on every navigation.
beforeEach(() => vi.spyOn(window, 'scrollTo').mockImplementation(() => {}));
afterEach(() => server.resetHandlers());
// Every successful stations query saves its list: no test starts from another test's cache.
afterEach(() => localStorage.clear());
afterAll(() => server.close());
