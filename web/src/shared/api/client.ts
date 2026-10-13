import { createBicimadClient } from '@jorgetroya80/bicimad-client';
import { env } from '@/shared/config';

// Absolute so Request accepts it outside the browser too (Node's Request has no document base URL).
const baseUrl = new URL(env.apiBaseUrl, globalThis.location.origin).href;

export const apiClient = createBicimadClient(baseUrl, {
  // Looked up per request: MSW patches fetch after this module has loaded.
  fetch: (request) => globalThis.fetch(request),
});
