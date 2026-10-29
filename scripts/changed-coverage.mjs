#!/usr/bin/env node
// Line coverage of the source lines added or changed since the merge base, read from the reports
// the test runs already write: web/coverage/coverage-final.json (Vitest) and
// api/build/reports/jacoco/test/jacocoTestReport.xml (JaCoCo). It never runs the tests itself.
// Usage: node scripts/changed-coverage.mjs [--base <ref>] [--min <percent>] [--part web|api]
// Exit codes: 0 at or above the minimum (or no instrumented line changed), 1 below it,
// 2 a report is missing for a part that changed.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const arg = (name, fallback) => {
  const i = process.argv.indexOf(name);
  return i > -1 ? process.argv[i + 1] : fallback;
};
const base = arg('--base', 'origin/main');
const min = Number(arg('--min', '80'));
// CI checks each part in its own job, where only that part's report exists.
const part = arg('--part', 'all');

const WEB_REPORT = 'web/coverage/coverage-final.json';
const API_REPORT = 'api/build/reports/jacoco/test/jacocoTestReport.xml';
const WEB_SOURCE = /^web\/src\/.*\.tsx?$/;
const API_SOURCE = /^api\/src\/main\/kotlin\/(.*\.kt)$/;
const isWebTest = (f) => /\.test\.tsx?$|\.d\.ts$/.test(f);

const git = (args) => execFileSync('git', args, { encoding: 'utf8' });
const bail = (msg) => {
  console.error('changed-coverage: ' + msg);
  process.exit(2);
};

process.chdir(git(['rev-parse', '--show-toplevel']).trim());
let mergeBase;
try {
  mergeBase = git(['merge-base', base, 'HEAD']).trim();
} catch {
  bail('no merge base against ' + base);
}

// Added line numbers per file, from the working tree against the merge base.
const changed = new Map();
let file = null;
for (const line of git(['diff', '--unified=0', mergeBase, '--', 'web/src', 'api/src/main']).split(
  '\n',
)) {
  if (line.startsWith('+++ ')) {
    const path = line.slice(4).replace(/^b\//, '');
    file = path === '/dev/null' ? null : path;
    if (file) changed.set(file, new Set());
  } else if (file && line.startsWith('@@')) {
    const [, start, count = '1'] = line.match(/\+(\d+)(?:,(\d+))?/);
    for (let n = Number(start); n < Number(start) + Number(count); n++) changed.get(file).add(n);
  }
}

// line -> covered, for the lines a report instruments.
function webLines(path) {
  const entry = webReport[resolve(path)];
  if (!entry) return new Map();
  const lines = new Map();
  for (const [id, { start }] of Object.entries(entry.statementMap)) {
    lines.set(start.line, (lines.get(start.line) ?? false) || entry.s[id] > 0);
  }
  return lines;
}

function apiLines(relative) {
  const slash = relative.lastIndexOf('/');
  const pkg = relative.slice(0, slash);
  const name = relative.slice(slash + 1);
  const pkgBlock = apiReport.match(
    new RegExp(`<package name="${pkg}">([\\s\\S]*?)</package>`),
  )?.[1];
  const fileBlock = pkgBlock?.match(
    new RegExp(`<sourcefile name="${name}">([\\s\\S]*?)</sourcefile>`),
  )?.[1];
  const lines = new Map();
  for (const [, nr, mi, ci] of fileBlock?.matchAll(/<line nr="(\d+)" mi="(\d+)" ci="(\d+)"/g) ??
    []) {
    if (Number(mi) + Number(ci) > 0) lines.set(Number(nr), Number(ci) > 0);
  }
  return lines;
}

const webFiles =
  part === 'api' ? [] : [...changed.keys()].filter((f) => WEB_SOURCE.test(f) && !isWebTest(f));
const apiFiles = part === 'web' ? [] : [...changed.keys()].filter((f) => API_SOURCE.test(f));
if (webFiles.length && !existsSync(WEB_REPORT)) bail(`${WEB_REPORT} missing: run pnpm --filter web test:coverage`);
if (apiFiles.length && !existsSync(API_REPORT)) bail(`${API_REPORT} missing: run the api tests`);
const webReport = webFiles.length ? JSON.parse(readFileSync(WEB_REPORT, 'utf8')) : {};
const apiReport = apiFiles.length ? readFileSync(API_REPORT, 'utf8') : '';

let covered = 0;
let total = 0;
const uncovered = [];
for (const f of [...webFiles, ...apiFiles]) {
  const lines = WEB_SOURCE.test(f) ? webLines(f) : apiLines(f.match(API_SOURCE)[1]);
  const missed = [];
  for (const n of changed.get(f)) {
    if (!lines.has(n)) continue;
    total++;
    if (lines.get(n)) covered++;
    else missed.push(n);
  }
  if (missed.length) uncovered.push(`  ${f}: ${missed.join(', ')}`);
}

if (total === 0) {
  console.log('changed-coverage: no instrumented line changed');
  process.exit(0);
}
const percent = (covered / total) * 100;
const summary = `changed-coverage: ${percent.toFixed(1)}% of ${total} changed lines (minimum ${min}%)`;
if (uncovered.length) console.log('Uncovered changed lines:\n' + uncovered.join('\n'));
if (percent < min) {
  console.error(summary);
  process.exit(1);
}
console.log(summary);
