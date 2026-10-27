import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: 'openapi.json',
  output: {
    path: 'src/generated',
    // tsconfig uses NodeNext, which needs extensions on relative imports
    module: { extension: '.js' },
  },
  plugins: ['@hey-api/typescript', '@hey-api/sdk', '@hey-api/client-fetch'],
});
