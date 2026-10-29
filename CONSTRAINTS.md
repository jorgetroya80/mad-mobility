# Constraints

- Created: 2026-10-29
- Last reviewed: 2026-10-29 by @jorgetroya80

Lo mínimo que tiene que cumplir un cambio para darse por bueno, con el comando que lo comprueba. Este archivo manda: los scripts de `package.json` y los pasos de CI lo reflejan, y si no coinciden, gana el archivo.

**Bloquea o avisa.** Lo que ya bloqueaba antes de este archivo sigue bloqueando: tipos, lint, formato, tests con su umbral de cobertura, commitlint, pr-title y release-scope. Todo lo nuevo (el mínimo común, secretos, Semgrep, dependencias, capas y cobertura de las líneas cambiadas) **solo avisa**. En CI va con `continue-on-error`: el hallazgo aparece en el log y no impide el merge. Decidido el 2026-10-29; revisar el 2027-01-29 si alguno pasa a bloquear.

## Floor (siempre, en cada diff)

Lo comprueba `node scripts/floor-guard.mjs` contra el merge base con `origin/main`. Mira solo el diff, incluidos los archivos sin seguimiento. Sale con 0 si está limpio, 1 si hay algo y 2 si no puede ejecutarse.

- Sin supresiones nuevas: `@ts-ignore`, `@ts-expect-error`, `oxlint-disable`, `eslint-disable`, `@Suppress(`, `ktlint-disable`, `istanbul ignore`, `v8 ignore`, `nosemgrep`, `gitleaks:allow`.
- Sin trabajo a medias: `TODO`, `FIXME`, `TODO()`, `NotImplementedError`, `catch` vacíos.
- Sin tests saltados, enfocados, borrados ni con aserciones quitadas: `.skip`, `.only`, `.todo`, `@Disabled`. Una aserción que se reescribe cuenta como quitada y avisa; si el cambio la refuerza, se explica en el commit.
- Sin secretos en el código ni en el historial.
- Este archivo no se relaja para que un cambio pase. Si se baja un umbral, se quita una regla o se añade una excepción, el guard avisa.

## Enforced with numbers

| Dimensión | Regla | Comprobado con | Cuándo | Por qué este número |
| --- | --- | --- | --- | --- |
| Tipos | 0 errores | `pnpm --filter web typecheck`; `./api/gradlew -p api compileKotlin compileTestKotlin` | cada edición (pre-push), CI (bloquea) | Ya estaba |
| Lint y formato | 0 errores | `pnpm --filter web lint`; `oxfmt` (lint-staged); `./api/gradlew -p api spotlessCheck` | cada commit, CI (bloquea) | Ya estaba |
| Cobertura web por capa | ≥ 80 % de líneas en cada capa | `pnpm --filter web test:coverage` (umbrales en `web/vite.config.ts`) | final de tarea, CI (bloquea) | Ya estaba |
| Cobertura api | ≥ 80 % de líneas en `shared` y en cada módulo | `./api/gradlew -p api build` (JaCoCo) | final de tarea, CI (bloquea) | Ya estaba |
| Cobertura de líneas cambiadas | ≥ 80 % de las líneas nuevas o cambiadas | `node scripts/changed-coverage.mjs` (lee `web/coverage/coverage-final.json` y el XML de JaCoCo; no repite los tests) | final de tarea, CI (avisa) | El mismo 80 % que ya usan Vitest y JaCoCo; obliga a escribir un test sin pedirlo para cada línea de configuración |
| Secretos | 0 hallazgos | `pnpm check:secrets` (gitleaks 8.28.0 en Docker, `--redact`, commits de la rama) | cada edición, CI (avisa) | Un secreto filtrado no se puede deshacer; `--redact` evita que el valor acabe en un log |
| Seguridad del código | 0 hallazgos de severidad alta (`ERROR`) | `pnpm check:semgrep` (Semgrep 1.140.0, `p/default` + `p/owasp-top-ten`) | `check:full`, CI (avisa) | Hoy hay 0; los de severidad media son configuración y se miden abajo |
| Dependencias de producción | 0 vulnerabilidades altas o críticas | `pnpm audit --prod --audit-level high` | `check:full`, CI (avisa) | Hoy hay 0 en producción; por debajo de alta casi todo es ruido |
| Capas (web) | 0 violaciones | `pnpm --filter web depcruise` (`web/.dependency-cruiser.cjs`) | final de tarea, CI (avisa) | Reglas de capas de Feature-Sliced Design y las prohibiciones de `SPEC-web-bicimad.md`: `maplibre-gl` solo en `shared/ui/base-map`, `search-by-number` sin estaciones y sin ciclos en ejecución. Hoy pasan todas |

Cada regla dice qué comando da el veredicto. Si una regla tiene número pero no tiene comando, es un deseo, no una constraint.

### Comandos por momento

| Script | Qué ejecuta | Tiempo hoy | Presupuesto |
| --- | --- | --- | --- |
| `pnpm check:fast` | typecheck de lo cambiado, lint de web, gitleaks de la rama y el floor guard | ~1 s | unos segundos |
| `pnpm check:task` | `check:fast` + tests de web, tests de api con JaCoCo, cobertura de líneas cambiadas y capas | ~9 s sin cambios en api, ~30 s con ellos | 90 s |
| `pnpm check:full` | `check:task` + Semgrep + `pnpm audit --prod` | ~15 s, más la descarga de las reglas de Semgrep | sin límite (CI) |

`check:secrets` y `check:semgrep` necesitan Docker en local. CI usa las mismas imágenes, fijadas por digest.

## Measured, not yet enforced

Medidos el 2026-10-29 en `main` (`ca07422`). Cada comprobación se compara con este valor; si empeora, es un hallazgo.

| Métrica | Hoy | Dirección | Comando |
| --- | --- | --- | --- |
| Cobertura de líneas de web | 99.1 % | must not fall (tolerancia 0.5 %) | `pnpm --filter web test:coverage` (resumen "All files") |
| Vulnerabilidades altas en dependencias de desarrollo | 8 (`js-yaml` vía `@hey-api/openapi-ts`; `vite` y `undici` vía `vitest`) | must not grow | `pnpm audit --audit-level high` |
| Hallazgos de Semgrep por debajo de alta | 6: 5 de configuración (Dependabot, `.npmrc` y `pnpm-workspace.yaml`) y 1 `detect-non-literal-regexp` en `MapPage.test.tsx` | must not grow | `check:semgrep` sin `--severity ERROR` (cuenta todo; los altos son 0) |

## Sin cubrir

- **Dependencias de la api (Gradle).** `pnpm audit` solo ve npm. osv-scanner necesita un `gradle.lockfile` que no existe, y las alertas de Dependabot están desactivadas en el repo. Para cubrirlas: activar las alertas de Dependabot con el dependency graph, o el bloqueo de dependencias de Gradle.
- **osv-scanner con el lockfile de pnpm.** La 2.2.4 solo reconoce 15 de los ~880 paquetes de `pnpm-lock.yaml` (v9). Por eso se usa `pnpm audit`, que consulta la base de avisos de GitHub.
- **Rendimiento y accesibilidad.** Necesitan una URL, y Render solo despliega `main`. No se comprueban; se decidió así el 2026-10-29.

## Exceptions

| ID | Regla | Ruta | Motivo | Responsable | Caduca |
| --- | --- | --- | --- | --- | --- |
