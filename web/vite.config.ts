/// <reference types="vitest/config" />
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

const MIN_LINE_COVERAGE = 80;
const MOCK_MODE = 'mock';
const MOCK_SERVICE_WORKER_PATH = '/mockServiceWorker.js';

// Serves MSW's worker script only in `vite --mode mock`, so it never lands in public/ or dist/.
function mockServiceWorker(): Plugin {
  const workerFile = createRequire(import.meta.url).resolve('msw/mockServiceWorker.js');
  return {
    name: 'mock-service-worker',
    apply: (_config, { command, mode }) => command === 'serve' && mode === MOCK_MODE,
    configureServer(server) {
      const workerScript = readFileSync(workerFile);
      server.middlewares.use(MOCK_SERVICE_WORKER_PATH, (_request, response) => {
        response.setHeader('Content-Type', 'application/javascript');
        response.end(workerScript);
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), mockServiceWorker()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '^/api/': {
        target: 'http://localhost:8080',
        rewrite: (path) => path.replace(/^\/api(?=\/)/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./test/setup.ts'],
    unstubGlobals: true,
    coverage: {
      provider: 'v8',
      include: ['src/**'],
      exclude: [
        'src/app/main.tsx',
        'src/shared/api/mocks/browser.ts',
        'src/**/*.test.{ts,tsx}',
        'src/**/*.d.ts',
      ],
      thresholds: {
        'src/shared/**': { lines: MIN_LINE_COVERAGE },
        'src/entities/**': { lines: MIN_LINE_COVERAGE },
        'src/features/**': { lines: MIN_LINE_COVERAGE },
        'src/widgets/**': { lines: MIN_LINE_COVERAGE },
        'src/pages/**': { lines: MIN_LINE_COVERAGE },
      },
    },
  },
});
