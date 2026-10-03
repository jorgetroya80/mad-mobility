# Plan: foundation

- Created: 2026-10-03
- Status: **approved** (2026-10-03)
- Spec: [SPEC-foundation.md](../specs/SPEC-foundation.md)

## Overview

Montar el repositorio `jorgetroya80/mad-mobility` con validaciones locales (Husky, commitlint, lint-staged, pre-push de tipos), CI filtrado por ruta, validación del título del PR, release-please en modo manifest con publicación de artefactos y Dependabot para npm. No hay código de aplicación: todo debe funcionar con `api/` y `web/` como placeholders y omitir con éxito lo que todavía no existe.

## Decisiones de implementación

- **Lista de tareas en este documento.** No se usa `tasks/todo.md`; las casillas de abajo son la fuente de verdad del progreso.
- **Nunca se hace push de código a `main`.** El único push directo es un commit raíz vacío (`chore: initial commit`), necesario porque un PR requiere una rama destino. Las tareas 1-4 se suben como la rama `chore/foundation-hooks` y entran por PR; desde ahí, cada tarea es una rama y un PR con squash merge.
- **Orden por riesgo.** Lo que depende de GitHub (permisos de Actions, release-please) va antes que lo cosmético, para fallar pronto.
- **Guardas por existencia de archivos.** Hooks y jobs comprueban `api/build.gradle.kts`, `web/tsconfig.json` o `api/Dockerfile` antes de ejecutar; si no existen, terminan con éxito.
- **`publish-web` también lleva guarda** (`web/vite.config.*`), además de `web--release_created`: con el placeholder no hay build que publicar. Pequeña precisión sobre la spec.

## Grafo de dependencias

```
T1 raíz del repo
 ├── T2 commitlint + commit-msg
 ├── T3 lint-staged + pre-commit
 └── T4 pre-push typecheck
        └── T5 primer push + permisos de Actions
               ├── T6 ci.yml + actionlint
               │      └── T7 pr-title.yml
               ├── T8 release-please (config + workflow)
               │      └── T9 publicación + deploy placeholders
               ├── T10 Dependabot
               └── T11 protección de main (requiere checks de T6 y T7)
```

## Task List

### Fase 1: Validaciones locales

- [x] **T1: Raíz del monorepo** (S, ~20 min)
  - Descripción: `git init` con rama `main`, `package.json` raíz privado (`packageManager: pnpm@12.8.1`, `engines.node: ">=24"`, script `prepare: husky`), `pnpm-workspace.yaml` (`packages: [web]`), `.nvmrc` (`24`), `.gitignore` (incluye `.env.local`, `node_modules`, `build`, `.gradle`, `dist`).
  - Aceptación:
    - `corepack enable && pnpm install` termina sin errores y `pnpm -v` muestra `12.8.1`.
    - `git status` no muestra `.env.local`.
  - Verificación: ejecutar los dos comandos anteriores.
  - Archivos: `package.json`, `pnpm-workspace.yaml`, `.nvmrc`, `.gitignore`, `pnpm-lock.yaml`.
  - Dependencias: ninguna.

- [x] **T2: commitlint y hook commit-msg** (S, ~15 min)
  - Descripción: instalar Husky 9, `@commitlint/cli` y `@commitlint/config-conventional`; `commitlint.config.mjs` con los tipos y scopes de la spec; `.husky/commit-msg`.
  - Aceptación:
    - `git commit -m "update stuff"` se rechaza (SC1).
    - `git commit -m "docs: add capability map"` se acepta (SC1).
    - `git commit -m "feat(foo): x"` se rechaza por scope no permitido.
  - Verificación: los tres commits anteriores (con `--allow-empty`).
  - Archivos: `package.json`, `commitlint.config.mjs`, `.husky/commit-msg`.
  - Dependencias: T1.

- [x] **T3: lint-staged, Prettier y hook pre-commit** (S, ~20 min)
  - Descripción: `lint-staged.config.mjs` con las reglas de la spec (las de `web/` y `api/` omitidas si el proyecto no existe), `.prettierrc`, `.prettierignore`, `.husky/pre-commit`.
  - Aceptación:
    - Un `.md` mal formateado sale formateado del commit (SC2).
    - Un commit sin archivos formateables no falla.
    - El hook tarda menos de 10 s.
  - Verificación: commit de prueba con un `.md` desordenado y `time git commit ...`.
  - Archivos: `lint-staged.config.mjs`, `.prettierrc`, `.prettierignore`, `.husky/pre-commit`, `package.json`.
  - Dependencias: T2.

- [x] **T4: Hook pre-push de comprobación de tipos** (S, ~25 min)
  - Descripción: `scripts/typecheck-changed.sh` según la spec, script `typecheck:changed` en `package.json`, `.husky/pre-push`. Debe funcionar sin upstream (primer push) usando la raíz del repositorio como base.
  - Aceptación:
    - `shellcheck scripts/typecheck-changed.sh` sin avisos.
    - Con solo cambios en `docs/`, termina con éxito sin ejecutar nada (SC3).
    - Con cambios en `web/` y sin `web/tsconfig.json`, termina con éxito e informa de que omite `web`.
  - Verificación: `pnpm run typecheck:changed` en ramas de prueba locales.
  - Archivos: `scripts/typecheck-changed.sh`, `.husky/pre-push`, `package.json`.
  - Dependencias: T2.

#### Checkpoint 1: hooks locales

- [ ] SC1, SC2 y SC3 comprobados en local.
- [ ] Revisión humana antes del primer push.

### Fase 2: GitHub y CI

- [x] **T5: Commit raíz, protección básica de `main`, PR de T1-T4 y permisos de Actions** (S, ~20 min, **requiere confirmación**)
  - Descripción: recolocar T1-T4 sobre un commit raíz vacío; push solo de ese commit a `main`; ruleset en `main` que exige PR y bloquea force push y borrado; en el repositorio solo squash merge y borrar ramas tras merge; push de `chore/foundation-hooks` y PR hacia `main`; permisos de Actions de solo lectura y permitir que Actions cree y apruebe PRs.
  - Aceptación:
    - `main` en GitHub contiene solo el commit raíz vacío hasta que se mergea el PR.
    - `git push origin main` directo se rechaza.
    - El PR `build(repo): add monorepo root and git hooks` existe y solo permite squash merge.
    - `gh api repos/jorgetroya80/mad-mobility/actions/permissions/workflow` muestra `default_workflow_permissions: read` y `can_approve_pull_request_reviews: true`.
  - Verificación: los comandos `gh api` de rulesets y permisos; intento de push directo rechazado.
  - Archivos: ninguno (configuración remota).
  - Dependencias: T4.

- [ ] **T6: Workflow de CI y actionlint** (M, ~45 min)
  - Descripción: `.github/workflows/ci.yml` con `concurrency`, `permissions: contents: read`, job `changes` (`dorny/paths-filter`), jobs `api`, `web`, `contract`, `workflows` con sus guardas y job agregador `ci-ok`.
  - Aceptación:
    - Un PR que solo cambia `docs/` ejecuta `changes` y `ci-ok` en verde, con `api` y `web` omitidos (SC4).
    - Un PR que cambia un workflow ejecuta `actionlint` sin errores (SC10).
    - Forzar un fallo en un job hace fallar `ci-ok`.
  - Verificación: abrir el PR de esta tarea y uno de prueba que toque `docs/`; `actionlint` en local.
  - Archivos: `.github/workflows/ci.yml`.
  - Dependencias: T5.

- [ ] **T7: Validación del título del PR** (XS, ~15 min)
  - Descripción: `.github/workflows/pr-title.yml` con `amannn/action-semantic-pull-request`, mismos tipos y scopes que commitlint, disparado en `opened`, `edited`, `synchronize`.
  - Aceptación:
    - Título `Update stuff` falla; `feat(web): add map` pasa (SC5).
  - Verificación: editar el título del PR de esta tarea entre ambos valores.
  - Archivos: `.github/workflows/pr-title.yml`.
  - Dependencias: T6.

#### Checkpoint 2: CI

- [ ] SC4, SC5 y SC10 comprobados en GitHub.
- [ ] Revisión humana.

### Fase 3: Releases y dependencias

- [ ] **T8: release-please** (M, ~40 min)
  - Descripción: `release-please-config.json` y `.release-please-manifest.json` según la spec; placeholders `api/gradle.properties` (`version=0.0.0 # x-release-please-version`) y `web/package.json` (`name`, `version: 0.0.0`, `private: true`); `.github/workflows/release.yml` solo con el job `release-please`.
  - Aceptación:
    - Tras mergear un PR `feat(web): ...` que toca `web/`, release-please abre un PR de release que sube solo `web` a `0.1.0` y crea `web/CHANGELOG.md` (SC6).
    - Un merge `docs: ...` fuera de `api/` y `web/` no altera el PR de release.
  - Verificación: PR de prueba `feat(web): add readme` con `web/README.md`; revisar el PR de release generado.
  - Archivos: `release-please-config.json`, `.release-please-manifest.json`, `api/gradle.properties`, `web/package.json`, `.github/workflows/release.yml`.
  - Dependencias: T6.

- [ ] **T9: Publicación de artefactos y deploy placeholders** (S, ~30 min)
  - Descripción: añadir a `release.yml` los jobs `publish-api` (GHCR, guarda `api/Dockerfile`), `publish-web` (guarda `web/vite.config.*`, adjunta `web-dist.tar.gz`), `deploy-api` y `deploy-web` (solo si `vars.DEPLOY_ENABLED == 'true'`). Permisos por job.
  - Aceptación:
    - Al mergear el PR de release se crea la etiqueta `web-v0.1.0` y su GitHub Release (SC7, parte sin artefacto).
    - `publish-web`, `publish-api`, `deploy-api` y `deploy-web` aparecen como omitidos (SC8).
  - Verificación: mergear el PR de release de T8 y revisar la ejecución de `release.yml`.
  - Archivos: `.github/workflows/release.yml`.
  - Dependencias: T8.

- [ ] **T10: Dependabot para npm** (XS, ~10 min)
  - Descripción: `.github/dependabot.yml` con ecosistema `npm`, directorio `/`, semanal (lunes), grupo minor/patch, prefijo `chore(deps)`.
  - Aceptación:
    - Insights > Dependency graph > Dependabot muestra la configuración sin errores.
    - El primer PR de Dependabot pasa commitlint y `ci-ok` (SC9, se comprueba el lunes siguiente).
  - Verificación: pestaña de Dependabot en GitHub; "Check for updates" manual.
  - Archivos: `.github/dependabot.yml`.
  - Dependencias: T5.

- [ ] **T11: Checks requeridos en `main`** (XS, ~10 min, **requiere confirmación**)
  - Descripción: añadir al ruleset de `main` (creado en T5) los checks requeridos `ci-ok` y `pr-title`.
  - Aceptación:
    - Un PR con `ci-ok` en rojo no se puede mergear.
  - Verificación: intento de push directo con un commit vacío; `gh api repos/jorgetroya80/mad-mobility/rulesets`.
  - Archivos: ninguno (configuración remota).
  - Dependencias: T6, T7.

#### Checkpoint final

- [ ] SC1-SC10 de la spec comprobados (SC7 completo y SC9 quedan pendientes de `web-shell` y del primer lunes, respectivamente).
- [ ] Spec y plan pasan a **implemented**; `CAPABILITY-MAP.md` actualizado.

## Riesgos y mitigaciones

| Riesgo                                                              | Impacto    | Mitigación                                                                                                     |
| ------------------------------------------------------------------- | ---------- | -------------------------------------------------------------------------------------------------------------- |
| Dependabot no soporta todavía el lockfile de pnpm 12                | Medio      | Comprobarlo en T10; si falla, fijar el formato de lockfile compatible o pasar a Renovate (requiere aprobación) |
| release-please no crea el PR por falta de permisos                  | Alto       | Permisos configurados en T5, antes de T8                                                                       |
| Las releases creadas con `GITHUB_TOKEN` no disparan otros workflows | Medio      | Publicación y deploy dentro de `release.yml` (T9)                                                              |
| `ci-ok` cuenta un job omitido como fallo y bloquea PRs              | Alto       | Probarlo explícitamente en T6 con un PR solo de `docs/`                                                        |
| Hooks lentos que invitan a usar `--no-verify`                       | Medio      | Objetivos de tiempo medidos en T3 y T4                                                                         |
| Proteger `main` antes de que existan los checks impide mergear      | Medio      | T5 solo exige PR; los checks requeridos se añaden en T11, después de T6 y T7                                   |
| Java 24 sin parches                                                 | Bajo ahora | Registrado en la spec; no afecta a este módulo                                                                 |

## Paralelización

Un solo desarrollador: orden secuencial T1 -> T11. Si se quisiera paralelizar, T7, T8 y T10 son independientes entre sí una vez hecho T6.

## Estimación

Unas 4 h en total, en 2 sesiones de fin de semana: Fase 1 (~1,5 h), Fases 2 y 3 (~2,5 h).

## Changelog

- 2026-10-03: no se hace push de código a `main`. T5 crea un commit raíz vacío, protege `main` exigiendo PR y sube T1-T4 como PR; T11 solo añade los checks requeridos.
