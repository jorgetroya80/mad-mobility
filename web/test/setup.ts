import '@testing-library/jest-dom/vitest';
import { server } from '@/shared/api/mocks/node';
import { stubMatchMedia } from './matchMedia';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
// Below md by default; a test calls stubMatchMedia(true) for the wide layout.
beforeEach(() => stubMatchMedia(false));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
