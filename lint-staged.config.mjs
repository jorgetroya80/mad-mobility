import { existsSync } from 'node:fs';
import { resolve, sep } from 'node:path';

const hasWeb = existsSync('web/tsconfig.json');
const hasApi = existsSync('api/build.gradle.kts');
const webDir = resolve('web') + sep;

const outsideWeb = (files) => files.filter((file) => !file.startsWith(webDir));

/** @type {import('lint-staged').Configuration} */
export default {
  '*.{json,md,yml,yaml,css}': (files) => {
    const rootFiles = outsideWeb(files);
    return rootFiles.length > 0 ? `prettier --write ${rootFiles.join(' ')}` : [];
  },
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
