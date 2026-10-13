const DEFAULT_API_BASE_URL = '/api';
const ROOT_PATH = '/';
const VALID_API_BASE_URL = /^(\/(?!\/)|https?:\/\/.+)/;

function stripTrailingSlash(value: string): string {
  if (value === ROOT_PATH || !value.endsWith('/')) return value;
  return value.slice(0, -1);
}

export function parseApiBaseUrl(raw: string | undefined): string {
  if (!raw) return DEFAULT_API_BASE_URL;
  if (!VALID_API_BASE_URL.test(raw)) {
    throw new Error(`VITE_API_BASE_URL must be a path starting with / or an http(s) URL: ${raw}`);
  }
  return stripTrailingSlash(raw);
}

export const env = {
  apiBaseUrl: parseApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
} as const;
