# Plan: api-bicimad — cliente publicado como mad-transit-client

- Created: 2026-10-30
- Status: **implemented** (2026-10-30)
- Spec: [SPEC-rename-client.md](../specs/SPEC-rename-client.md)

## Overview

Renombrar el paquete del cliente a `@jorgetroya80/mad-transit-client`, publicarlo con la release `api 0.8.0` y pasar web al nombre nuevo. El contrato HTTP y el código del cliente no cambian.

## Decisiones de implementación

- **Dos ramas y dos PRs**, porque `release-scope` no deja que un PR que publica toque `api/` y `web/` a la vez:
  - `feat/api-mad-transit-client` → PR `feat(api)!: publish the client as mad-transit-client` (T1-T2).
  - `refactor/web-mad-transit-client` → PR `refactor(web): use the mad-transit-client package` (T3-T4). Se abre cuando `@jorgetroya80/mad-transit-client@0.8.0` esté publicado.
- **Un commit por tarea, sin `Co-Authored-By`.** Cuerpos de squash en prosa, sin código; el del PR 1 lleva `BREAKING CHANGE:`.
- **Los dos nombres en `minimumReleaseAgeExclude` entre PR 1 y PR 2**, para que el lockfile de web (todavía con el nombre viejo) y el paquete nuevo se instalen sin esperar 10 días.
- **La documentación viva de la raíz va en el PR 2**, porque describe lo que usa web y web sigue con el nombre viejo hasta entonces. Los README del API van en el PR 1.

## Grafo de dependencias

```
T1 nombre nuevo en api/client, CI y scripts
 └── T2 docs del API y PR 1
       └── release api-v0.8.0 (publica mad-transit-client 0.8.0)
             └── T3 web usa el paquete nuevo
                   └── T4 documentación viva, PR 2 y cierre
```

Todo es secuencial.

## Task List

### Fase 1: publicar el nombre nuevo (PR 1, `api/`)

- [x] **T1: Renombrar el paquete** (S, ~15 min)
  - Descripción: `name` de `api/client/package.json` a `@jorgetroya80/mad-transit-client`. Filtro de pnpm en `ci.yml`, `release.yml` y `scripts/typecheck-changed.sh`. `minimumReleaseAgeExclude` en `pnpm-workspace.yaml` con los dos nombres. `pnpm install` para el lockfile.
  - Aceptación:
    - `pnpm --filter @jorgetroya80/mad-transit-client run generate` y `run build` pasan.
    - `pnpm install --frozen-lockfile` pasa.
  - Verificación: `./api/gradlew -p api generateOpenApi`; los dos comandos de arriba; `pnpm check:task`.
  - Archivos: `api/client/package.json`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `scripts/typecheck-changed.sh`, `pnpm-workspace.yaml`, `pnpm-lock.yaml`.
  - Dependencias: ninguna.

- [x] **T2: Docs del API y PR 1** (S, ~15 min)
  - Descripción: nombre nuevo en `api/README.md` y `api/client/README.md`; changelog de la spec y de este plan. Push y PR `feat(api)!: publish the client as mad-transit-client` con `BREAKING CHANGE:` en el cuerpo, en prosa.
  - Aceptación:
    - CI en verde (`API (Gradle)`, `pr-title`, `release-scope`, `ci-ok`).
    - El PR no toca `web/`.
  - Verificación: `gh pr checks`; `gh pr diff --name-only`.
  - Archivos: `api/README.md`, `api/client/README.md`, la spec, este plan.
  - Dependencias: T1.

### Checkpoint: PR 1

- [x] Revisión humana del título y del cuerpo antes del squash merge.
- [x] El PR de release-please propone `api 0.8.0`; tras fusionarlo, `publish-api-client` publica `@jorgetroya80/mad-transit-client@0.8.0` (SC1).
- [x] El paquete aparece enlazado al repo `mad-transit` en GitHub Packages (supuestos 2 y 3).

### Fase 2: web con el nombre nuevo (PR 2, `web/`)

- [x] **T3: Web usa `mad-transit-client`** (S, ~15 min)
  - Descripción: en `web/package.json`, cambiar `@jorgetroya80/bicimad-client` por `@jorgetroya80/mad-transit-client` `0.8.0`. Cambiar el import en los 8 archivos de `web/src`. Quitar el nombre viejo de `minimumReleaseAgeExclude`. `pnpm install`.
  - Aceptación:
    - `grep -rn "bicimad-client" web/src web/package.json` no da resultados.
    - Web: `lint`, `typecheck`, `test` y `build` en verde (SC4).
  - Verificación: `pnpm --filter web run lint`; `pnpm --filter web run typecheck`; `pnpm --filter web run test`; `pnpm --filter web run build`; `pnpm check:task`.
  - Archivos: `web/package.json`, `pnpm-lock.yaml`, `pnpm-workspace.yaml`, `web/src/shared/api/client.ts`, `web/src/shared/api/client.test.ts`, `web/src/shared/api/mocks/fixtures.ts`, `web/src/shared/api/mocks/handlers.ts`, `web/src/widgets/transit-search/ui/TransitSearch.test.tsx`, `web/src/widgets/station-list/ui/StationList.test.tsx`, `web/src/entities/station/model/types.ts`, `web/src/entities/station/api/fetchStations.ts`.
  - Dependencias: checkpoint del PR 1.

- [x] **T4: Documentación viva, PR 2 y cierre** (S, ~15 min)
  - Descripción: nombre nuevo en `AGENTS.md`, `README.md`, `web/AGENTS.md`, `web/README.md` y en las filas de `api-bicimad` y `web-shell` de `CAPABILITY-MAP.md`. Spec y plan a **implemented**. Push y PR `refactor(web): use the mad-transit-client package`.
  - Aceptación:
    - SC3: `grep -rn "bicimad-client" --exclude-dir=node_modules .` solo da resultados en `CHANGELOG.md`, en specs y planes cerrados y en esta spec y este plan.
    - CI en verde (`Web (pnpm)`, `release-scope`, `ci-ok`).
  - Verificación: `grep`; `gh pr checks`.
  - Archivos: `AGENTS.md`, `README.md`, `web/AGENTS.md`, `web/README.md`, `docs/specs/CAPABILITY-MAP.md`, la spec, este plan.
  - Dependencias: T3.

### Checkpoint final

- [ ] PR 2 fusionado sin crear release de web (es `refactor`).
- [ ] El build de la web en Render instala el paquete nuevo en el siguiente deploy (SC2), y la lista de estaciones se ve como antes (SC5).

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| El paquete nuevo nace privado o sin enlace al repo y CI o Render no pueden instalarlo | Alto | Se comprueba en el checkpoint del PR 1, antes de tocar web; si falla, se enlaza a mano en GitHub (Package settings) |
| El squash merge pierde `!` o `BREAKING CHANGE:` | Bajo | Con `bump-minor-pre-major` la release es 0.8.0 de todas formas por ser `feat`; `pr-title` valida el título y el cuerpo se revisa antes del merge |
| El cuerpo del squash lleva código y release-please se salta el commit | Medio | Cuerpo en prosa; revisión antes del merge |
| El deploy del API por la release 0.8.0 falla | Bajo | La imagen no cambia de código; si falla, Render mantiene la versión anterior |

## Estimación

Unos 60 min de trabajo en dos sesiones, más la espera de la release entre ellas: T1 (~15 min), T2 (~15 min), T3 (~15 min), T4 (~15 min).

## Changelog

- 2026-10-30: versión inicial; pasa a in review.
- 2026-10-30: plan aprobado.
- 2026-10-30: T1 implementada. `pnpm install` no cambia `pnpm-lock.yaml`: el lockfile indexa los paquetes del workspace por ruta, no por nombre. `generate`, `build`, `pnpm install --frozen-lockfile` y `pnpm check:task` en verde.
- 2026-10-30: T2 implementada. Nombre nuevo en `api/README.md` y `api/client/README.md`.
- 2026-10-30: checkpoint del PR 1 superado. #84 y la release #85 fusionados; `publish-api-client` publicó `@jorgetroya80/mad-transit-client@0.8.0`, público y enlazado a `jorgetroya80/mad-transit` sin pasos manuales (SC1, supuestos 2 y 3). El CI de #85 falló una vez sin ejecutar nada: dos runs sobre el mismo commit y `cancel-in-progress` canceló los dos; bastó con relanzar el último.
- 2026-10-30: T3 implementada. El nombre viejo no puede salir de `minimumReleaseAgeExclude` en el mismo `pnpm install` que lo quita del lockfile: pnpm valida antes el lockfile existente y rechaza `bicimad-client@0.6.0` por edad. Se instala con los dos nombres y después se quita el viejo; `pnpm install --frozen-lockfile` pasa. `pnpm check:task` (368 tests de web) y el build de web en verde.
- 2026-10-30: T4 implementada. Nombre nuevo en `AGENTS.md`, `README.md`, `web/AGENTS.md`, `web/README.md` y `CAPABILITY-MAP.md` (filas y enfoque de contrato, que recuerda el nombre viejo hasta la 0.7.1). SC3 comprobado: el nombre viejo solo queda en specs y planes ya cerrados, en `SPEC-rename-mad-transit.md` (su fuera de alcance) y en esta spec y este plan. Plan implementado; queda el checkpoint final.
