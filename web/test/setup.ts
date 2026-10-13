import '@testing-library/jest-dom/vitest';
import { server } from '@/shared/api/mocks/node';

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
