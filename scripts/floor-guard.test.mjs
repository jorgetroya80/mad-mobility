// Runs scripts/floor-guard.mjs against throwaway git repos: each case commits a CONSTRAINTS.md,
// edits it, and checks what the guard reports. Run with: node --test scripts/
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const GUARD = new URL('./floor-guard.mjs', import.meta.url).pathname;

const BASE = `# Constraints

## Floor (siempre, en cada diff)

- Sin supresiones nuevas: ver la lista de CONSTRAINTS.md.
- Sin secretos en el código ni en el historial.

## Enforced with numbers

| Dimensión | Regla | Comprobado con | Cuándo | Por qué este número |
| --- | --- | --- | --- | --- |
| Cobertura de líneas cambiadas | ≥ 80 % de las líneas nuevas o cambiadas | changed-coverage | CI | El mismo 80 % |
| Secretos | 0 hallazgos | gitleaks | CI | Un secreto no se deshace |

## Measured, not yet enforced

| Métrica | Hoy | Dirección | Comando |
| --- | --- | --- | --- |
| Cobertura de líneas de web | 99.1 % | must not fall (tolerancia 0.5 %) | vitest |
| Hallazgos de Semgrep por debajo de alta | 6: 5 de configuración y 1 en un test | must not grow | semgrep |
| Vulnerabilidades altas en desarrollo | 8 (\`js-yaml\` y \`vite\`) | must not grow | pnpm audit |

## Sin cubrir

- **Dependencias de la api (Gradle).** pnpm audit solo ve npm. Para cubrirlas: activar Dependabot.
- **Rendimiento y accesibilidad.** Necesitan una URL.

## Exceptions

| ID | Regla | Ruta | Motivo | Responsable | Caduca |
| --- | --- | --- | --- | --- | --- |
`;

function guard(edit) {
  const dir = mkdtempSync(join(tmpdir(), 'floor-guard-'));
  try {
    const git = (...args) => execFileSync('git', args, { cwd: dir, stdio: 'ignore' });
    git('init', '-q', '-b', 'main');
    git('config', 'user.email', 'test@example.com');
    git('config', 'user.name', 'Test');
    writeFileSync(join(dir, 'CONSTRAINTS.md'), BASE);
    git('add', '.');
    git('commit', '-q', '-m', 'base');
    writeFileSync(join(dir, 'CONSTRAINTS.md'), edit(BASE));
    const run = spawnSync('node', [GUARD, '--base', 'main'], { cwd: dir, encoding: 'utf8' });
    return { status: run.status, output: run.stdout + run.stderr };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const replace = (from, to) => (text) => {
  assert.equal(text.split(from).length, 2, `expected one "${from}"`);
  return text.replace(from, to);
};
const removeLine = (start) => (text) =>
  text
    .split('\n')
    .filter((line) => !line.startsWith(start))
    .join('\n');

const lowersTheBar = [
  ['a rule threshold goes down', replace('≥ 80 %', '≥ 70 %'), 'threshold-loosened'],
  ['a rule row is removed', removeLine('| Secretos |'), 'rule-removed'],
  ['a floor bullet is removed', removeLine('- Sin secretos'), 'rule-removed'],
  ['a "must not fall" value goes down', replace('| 99.1 % |', '| 90 % |'), 'threshold-loosened'],
  ['a "must not grow" value goes up', replace('| 6: 5 de', '| 9: 5 de'), 'threshold-loosened'],
  [
    'a measured value is removed',
    replace('| 6: 5 de configuración y 1 en un test |', '| pendiente |'),
    'threshold-removed',
  ],
  [
    'a measured direction changes',
    replace('| 99.1 % | must not fall', '| 99.1 % | must not grow'),
    'threshold-changed',
  ],
  [
    'a new exception is added',
    (t) => t + '| W1 | no-console | scripts/** | CLI output | @me | 2027-01-29 |\n',
    'new-exception',
  ],
];

for (const [name, edit, rule] of lowersTheBar) {
  test(`flags when ${name}`, () => {
    const { status, output } = guard(edit);
    assert.equal(status, 1, output);
    assert.match(output, new RegExp(`\\[${rule}\\]`));
  });
}

const raisesTheBar = [
  ['a rule threshold goes up', replace('≥ 80 %', '≥ 85 %')],
  ['a "must not fall" value goes up', replace('| 99.1 % |', '| 99.5 % |')],
  ['a "must not grow" value goes down', replace('| 6: 5 de', '| 3: 5 de')],
  // PR #80: the value went down and the cell gained a date and a PR number.
  [
    'a "must not grow" value goes down with new text around it',
    replace(
      '| 8 (`js-yaml` y `vite`) |',
      '| 0, tras el PR #78 y la subida de vite. Medido el 2026-10-29 |',
    ),
  ],
];

for (const [name, edit] of raisesTheBar) {
  test(`stays clean when ${name}`, () => {
    const { status, output } = guard(edit);
    assert.equal(status, 0, output);
    assert.match(output, /floor-guard: clean/);
  });
}

// PR #77: a gap that got covered. Removing it is worth a look, but it does not lower the bar.
test('only notes a gap removed from "Sin cubrir"', () => {
  const { status, output } = guard(removeLine('- **Dependencias de la api (Gradle).**'));
  assert.equal(status, 0, output);
  assert.match(output, /\[gap-removed\]/);
  assert.doesNotMatch(output, /\[rule-removed\]/);
});
