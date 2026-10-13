import { existsSync } from 'node:fs';

const hasWeb = existsSync('web/tsconfig.json');
const hasApi = existsSync('api/build.gradle.kts');

/** @type {import('lint-staged').Configuration} */
export default {
  'web/**/*.{ts,tsx}': (files) =>
    hasWeb
      ? [
          `pnpm --filter web exec oxlint --fix ${files.join(' ')}`,
          `pnpm --filter web exec oxfmt ${files.join(' ')}`,
        ]
      : [],
  'web/**/*.{css,json}': (files) =>
    hasWeb ? `pnpm --filter web exec oxfmt ${files.join(' ')}` : [],
  'api/**/*.{kt,kts}': () => (hasApi ? './api/gradlew -p api spotlessApply' : []),
};
