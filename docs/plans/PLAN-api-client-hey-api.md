# Plan: api-bicimad — cliente generado con hey-api

- Created: 2026-10-27
- Status: **approved** (2026-10-27)
- Spec: [SPEC-api-client-hey-api.md](../specs/SPEC-api-client-hey-api.md)

## Overview

Sustituir openapi-typescript + openapi-fetch por `@hey-api/openapi-ts` 0.99.0 (plugins `@hey-api/typescript`, `@hey-api/sdk` y `@hey-api/client-fetch`) en `api/client`, publicar el cliente como 0.6.0 y migrar web a las funciones `listStations` y `getStation`. El contrato HTTP del API no cambia.

## Decisiones de implementación

- **Dos ramas y dos PRs**, porque `release-scope` no deja que un PR que publica toque `api/` y `web/` a la vez:
  - `feat/api-client-hey-api` → PR `feat(api)!: generate the bicimad client with hey-api` (T1-T3).
  - `refactor/web-hey-api-client` → PR `refactor(web): use the hey-api bicimad client` (T4-T5). Se abre cuando `@jorgetroya80/bicimad-client@0.6.0` esté publicado.
- **Un commit por tarea, sin `Co-Authored-By`.** El squash merge del PR 1 conserva `feat(api)!` en el título y `BREAKING CHANGE:` en el cuerpo, en prosa y sin bloques de código (el código en el cuerpo del squash rompe release-please).
- **Primero lo arriesgado (T1):** los supuestos 1-6 de la spec se comprueban generando el cliente de verdad. Si alguno falla (por ejemplo, el cliente no acepta un `fetch` propio), se para y se revisa la spec antes de seguir.
- **El código generado no se commitea:** `src/generated/` se ignora y se regenera en CI y en la release, como hoy `src/schema.d.ts`.
- **`tsc` compila el código generado**, porque hey-api genera `.ts`, no un `.d.ts`. Desaparece el `cp src/schema.d.ts dist/` del script `build`.
- **Nombres de scripts sin cambios** (`generate`, `build`), así `ci.yml`, `release.yml` y `scripts/typecheck-changed.sh` no se tocan.

## Grafo de dependencias

```
T1 generador hey-api en api/client          T2 operationId estables (OpenApiExportTest)
 └──────────────┬───────────────────────────────┘
                T3 docs del PR 1, CI y merge
                 └── release api-v0.6.0 (publica el cliente 0.6.0)
                       └── T4 web usa el cliente 0.6.0
                             └── T5 uso real y cierre
```

T1 y T2 son independientes entre sí.

## Task List

### Fase 1: cliente con hey-api (PR 1, `api/`)

- [x] **T1: Generar el cliente con hey-api** (M, ~45 min)
  - Descripción: en `api/client`, quitar `openapi-typescript` y `openapi-fetch` y añadir `@hey-api/openapi-ts` 0.99.0 (versión exacta). Nuevo `openapi-ts.config.ts` con `input: 'openapi.json'`, salida en `src/generated` y los tres plugins. El script `generate` copia `../build/openapi/bicimad.json` y ejecuta `openapi-ts`; `build` pasa a ser solo `tsc`. `.gitignore` cambia `src/schema.d.ts` por `src/generated/`. `src/index.ts` reexporta el SDK y los tipos generados, `createClient`, `createConfig`, `Client`, `Config` y `CreateClientConfig`, y mantiene los alias `Station`, `StationsResponse`, `StationDetailResponse` y `Problem`. Elimina `createBicimadClient`.
  - Comprobar al generar (supuestos de la spec) y anotar en el changelog del plan:
    - El cliente acepta `fetch` en `createConfig` (supuesto 3).
    - Las funciones del SDK devuelven `{ data, error, response }` sin lanzar (supuesto 4).
    - Los tipos se llaman `StationResponse`, `StationsResponse`, `StationDetailResponse` y `Problem` (supuesto 5).
    - El paquete no necesita dependencias de runtime fuera de `src/generated/` (supuesto 2).
    - Los imports relativos generados compilan con `module: NodeNext` (necesitan extensión `.js`).
  - Aceptación:
    - SC1: `generate` escribe solo en `src/generated/`; generate + build dos veces dan el mismo `dist/`.
    - SC2: `git status` limpio tras generar.
    - SC3 y SC4: `dist/index.d.ts` exporta lo que pide la spec y no `createBicimadClient`; `package.json` no depende de `openapi-fetch`.
  - Verificación: `./api/gradlew -p api generateOpenApi`; `pnpm --filter @jorgetroya80/bicimad-client run generate`; `pnpm --filter @jorgetroya80/bicimad-client run build`; `grep -E 'createClient|createConfig|listStations|getStation' api/client/dist/index.d.ts`; `pnpm --filter @jorgetroya80/bicimad-client pack` y revisar el contenido del `.tgz`.
  - Archivos: `api/client/package.json`, `api/client/openapi-ts.config.ts`, `api/client/.gitignore`, `api/client/src/index.ts`, `pnpm-lock.yaml` (quizá `api/client/tsconfig.json`).
  - Dependencias: ninguna.

- [x] **T2: `operationId` estables** (XS, ~15 min)
  - Descripción: `OpenApiExportTest` comprueba que los `operationId` del documento son exactamente `listStations` y `getStation`. Con hey-api son nombres de funciones públicas del cliente.
  - Aceptación:
    - SC5: renombrar `getStation` en `StationsController` hace fallar el test (comprobado a mano y revertido).
  - Verificación: `./api/gradlew -p api generateOpenApi`; `./api/gradlew -p api build`.
  - Archivos: `api/src/test/kotlin/io/github/jorgetroya80/madmobility/OpenApiExportTest.kt`.
  - Dependencias: ninguna.

- [x] **T3: Documentación y PR 1** (S, ~20 min)
  - Descripción: fila de `api-bicimad` en `CAPABILITY-MAP.md` (cliente generado con hey-api) y su párrafo sobre el enfoque de contrato; changelog de la spec y de este plan. Push y PR `feat(api)!: generate the bicimad client with hey-api` con `BREAKING CHANGE:` en el cuerpo, en prosa.
  - Aceptación:
    - CI en verde en el PR (`API (Gradle)`, `pr-title`, `release-scope`, `ci-ok`).
    - El PR solo toca `api/`, `docs/` y `pnpm-lock.yaml`.
  - Verificación: `gh pr checks`; `gh pr diff --name-only`.
  - Archivos: `docs/specs/CAPABILITY-MAP.md`, `docs/specs/SPEC-api-client-hey-api.md`, este plan.
  - Dependencias: T1, T2.

### Checkpoint: PR 1

- [ ] SC1-SC5 comprobados; CI en verde.
- [ ] Revisión humana del título (`feat(api)!: …`) y del cuerpo antes del squash merge.
- [ ] El PR de release-please propone `api 0.6.0`; tras fusionarlo, `publish-api-client` publica `@jorgetroya80/bicimad-client@0.6.0` (SC6).

### Fase 2: web con el cliente nuevo (PR 2, `web/`)

- [ ] **T4: Web usa `listStations` y `getStation`** (M, ~40 min)
  - Descripción: web sube a `@jorgetroya80/bicimad-client@0.6.0`. `shared/api/client.ts` crea el cliente con `createClient(createConfig({ baseUrl, fetch }))`, conservando el `fetch` que busca `globalThis.fetch` en cada petición (MSW). `fetchStations.ts` llama a `listStations` y `getStation` pasando ese cliente, y mantiene `requestFailed` y `StationNotFoundError`. `client.test.ts` usa `listStations` en vez de `apiClient.GET`.
  - Aceptación:
    - Ningún fichero de web usa `.GET(` ni rutas `/v1/bicimad/…` fuera de los mocks de MSW.
    - Los tests existentes pasan sin cambiar sus aserciones de comportamiento.
    - `lint`, `typecheck`, `test` y `build` de web en verde.
  - Verificación: `pnpm --filter web run lint`; `pnpm --filter web run typecheck`; `pnpm --filter web run test`; `pnpm --filter web run build`.
  - Archivos: `web/package.json`, `pnpm-lock.yaml`, `web/src/shared/api/client.ts`, `web/src/shared/api/client.test.ts`, `web/src/entities/station/api/fetchStations.ts`.
  - Dependencias: checkpoint del PR 1 (cliente 0.6.0 publicado).

- [ ] **T5: Uso real y cierre** (S, ~20 min, **requiere `api/.env.local`**)
  - Descripción: con el API en local (`bootRun`) y `pnpm --filter web run dev`, comprobar la lista y el detalle de la 538. Spec y plan a **implemented**. Push y PR `refactor(web): use the hey-api bicimad client`.
  - Aceptación:
    - SC7: la lista de estaciones y el detalle de la 538 se ven como antes.
    - CI en verde (`Web (pnpm)`, `release-scope`, `ci-ok`).
  - Verificación: navegador contra `localhost`; `gh pr checks`.
  - Archivos: `docs/specs/SPEC-api-client-hey-api.md`, este plan.
  - Dependencias: T4.

### Checkpoint final

- [ ] SC1-SC7 comprobados.
- [ ] PR 2 fusionado sin crear release de web (es `refactor`).

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Los imports generados no llevan `.js` y no compilan con `NodeNext` | Medio | Revisar en T1 la opción de extensión de imports de hey-api 0.99.0; si no existe, cambiar `moduleResolution` del cliente a `Bundler` y comprobar que web lo resuelve |
| El cliente de hey-api no acepta un `fetch` propio y MSW deja de interceptar | Alto | Se comprueba en T1, antes de publicar nada; si falla, se para y se revisa la spec |
| hey-api 0.x cambia su API en una versión menor | Medio | Versión exacta; Dependabot no abre PRs de versión; cada subida es un cambio consciente |
| El squash merge pierde `!` o `BREAKING CHANGE:` y la release es 0.5.1 | Medio | `pr-title` valida el título; revisión humana del cuerpo antes del merge |
| La release incluye código en el cuerpo del squash y release-please falla | Medio | Cuerpo del PR 1 solo en prosa |

## Estimación

Unos 140 min de trabajo en dos sesiones, más la espera de la release entre ellas: T1 (~45 min), T2 (~15 min), T3 (~20 min), T4 (~40 min), T5 (~20 min).

## Changelog

- 2026-10-27: versión inicial.
- 2026-10-27: plan aprobado.
- 2026-10-27: T1 implementada. hey-api 0.99.0 no detecta `NodeNext` por sí solo: genera imports sin extensión, así que `openapi-ts.config.ts` fija `output.module.extension: '.js'`. Supuestos de la spec comprobados con el paquete empaquetado (`pnpm pack`) desde un consumidor `NodeNext`: `createConfig` acepta `fetch` (tipo `typeof fetch`), `listStations` y `getStation` devuelven `{ data, error, response }` sin lanzar (un 404 llega como `error` con el `Problem`), los tipos conservan sus nombres y el código generado no importa ningún paquete externo. `Station` es el único alias que queda en `src/index.ts`; `StationsResponse`, `StationDetailResponse` y `Problem` salen tal cual del código generado. Generate + build dos veces dan un `dist/` idéntico (34 ficheros). Para T4: `response` es opcional en el tipo del resultado, así que `requestFailed` en `fetchStations.ts` no puede leer `response.url` sin comprobarlo; y el `fetch` que pasa web debe aceptar `(input, init)`, no solo un `Request`.
- 2026-10-27: T2 implementada. `OpenApiExportTest` comprueba que los `operationId` son exactamente `listStations` y `getStation`. Comprobado a mano: con `getStation` renombrado a `findStation`, `generateOpenApi` falla en ese test; revertido. `./gradlew build generateOpenApi` en verde.
- 2026-10-27: T3 implementada. `CAPABILITY-MAP.md` indica que el cliente lo genera hey-api desde la 0.6.0. El `README.md` raíz se actualiza en T5, porque describe lo que usa web y web sigue con el cliente 0.4.0 hasta el PR 2. `docs/ideas/bicimad-now.md` no se toca: es la idea original y ya estaba superada en este punto desde 2026-10-05.
