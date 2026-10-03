import { existsSync } from 'node:fs';

const hasWeb = existsSync('web/tsconfig.json');
const hasApi = existsSync('api/build.gradle.kts');

/** @type {import('lint-staged').Configuration} */
export default {
  '*.{json,md,yml,yaml,css}': 'prettier --write',
  'web/**/*.{ts,tsx}': (files) =>
    hasWeb
      ? [
          `pnpm --filter web exec eslint --fix ${files.join(' ')}`,
          `prettier --write ${files.join(' ')}`,
        ]
      : [],
  'api/**/*.{kt,kts}': () => (hasApi ? './api/gradlew -p api spotlessApply' : []),
};
