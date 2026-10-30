import { createClient, createConfig } from '@jorgetroya80/mad-transit-client';
import { env } from '@/shared/config';

// Absolute so Request accepts it outside the browser too (Node's Request has no document base URL).
const baseUrl = new URL(env.apiBaseUrl, globalThis.location.origin).href;

export const apiClient = createClient(
  createConfig({
    baseUrl,
    // Looked up per request: MSW patches fetch after this module has loaded.
    fetch: (input, init) => globalThis.fetch(input, init),
  }),
);
