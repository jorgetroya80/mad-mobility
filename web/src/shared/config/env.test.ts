import { parseApiBaseUrl } from './env';

const INVALID_URL_MESSAGE = 'VITE_API_BASE_URL must be a path starting with / or an http(s) URL:';

describe('parseApiBaseUrl', () => {
  it.each([undefined, ''])('defaults to /api when the value is %j', (raw) => {
    expect(parseApiBaseUrl(raw)).toBe('/api');
  });

  it('strips the trailing slash from a path', () => {
    expect(parseApiBaseUrl('/api/')).toBe('/api');
  });

  it('keeps the root path', () => {
    expect(parseApiBaseUrl('/')).toBe('/');
  });

  it('accepts an https URL and strips its trailing slash', () => {
    expect(parseApiBaseUrl('https://api.example.com/')).toBe('https://api.example.com');
  });

  it('accepts an http URL unchanged', () => {
    expect(parseApiBaseUrl('http://localhost:8080')).toBe('http://localhost:8080');
  });

  it.each(['api', 'ftp://x'])('throws for %j', (raw) => {
    expect(() => parseApiBaseUrl(raw)).toThrow(`${INVALID_URL_MESSAGE} ${raw}`);
  });
});
