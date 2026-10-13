/// <reference types="vitest/config" />
import { fileURLToPath } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const MIN_LINE_COVERAGE = 80;

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./test/setup.ts'],
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
        'src/widgets/**': { lines: MIN_LINE_COVERAGE },
      },
    },
  },
});
