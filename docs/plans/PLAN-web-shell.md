# Plan: web-shell

- Created: 2026-10-13
- Status: **implemented** (2026-10-13)
- Spec: [SPEC-web-shell.md](../specs/SPEC-web-shell.md)

## Overview

Crear el proyecto `web/` (Vite + React + TypeScript 6.0.3 + Tailwind) con arquitectura FSD, oxlint y oxfmt, Vitest con cobertura, el cliente `@jorgetroya80/bicimad-client` instalado desde GitHub Packages, MSW para tests y `dev:mock`, el mapa base de MapLibre y el layout responsive (bottom sheet en móvil, panel lateral desde `md`). Sin estaciones: eso es `web-bicimad`.

## Decisiones de implementación

- **Lista de tareas en este documento.** Como en los módulos anteriores, las casillas de abajo son la fuente de verdad del progreso (no se usa `tasks/`).
- **Una rama (`feat/web-shell`) y un solo PR para todo el módulo, con un commit por tarea.** Squash merge: en `main` queda un commit `feat(web)`. Tipos por tarea: `build(web)` para dependencias y configuración, `feat(web)` para comportamiento, `ci` para workflows, `docs` para el cierre.
- **Primero lo más arriesgado.** T1 es la instalación del cliente desde GitHub Packages: si el token, `.npmrc` o `minimumReleaseAgeExclude` fallan, todo lo demás se bloquea, y además rompe los jobs `api` y `tooling` de CI.
- **Cada tarea deja `web/` funcionando.** Desde T2 existe `web/tsconfig.json`, así que el job `web` de CI se activa y ejecuta `lint`, `typecheck`, `test` y `build`. T2 crea esos cuatro scripts (con `lint` y `test` provisionales: `oxlint` sin configuración y `vitest run --passWithNoTests`); T3 y T4 los completan.
- **TDD en la lógica.** `env`, `formatRelative`, `BottomSheet` y el cliente del API se escriben test primero. La configuración (Vite, oxlint, oxfmt) se verifica ejecutándola.
- **`maplibre-gl` solo en `shared/ui/base-map`.** Los tests de `BaseMap` simulan `maplibre-gl` con `vi.mock`; el resto de tests simulan `BaseMap` entero.
- **Prototipo como referencia.** La prueba del stack del 2026-10-13 (scratchpad) ya confirmó `tsc`, `vite build`, Vitest + MSW y los nombres de las reglas de oxlint. No se copia código del prototipo: solo se usan sus hallazgos, ya recogidos en la spec.

### Versiones

Comprobadas el 2026-10-13 en npm. Todas tienen al menos 10 días a fecha de hoy (`minimumReleaseAge`); donde la última era más nueva, se toma la anterior de la misma línea. En T1/T2, `pnpm install` lo vuelve a comprobar; si alguna ya se puede subir, se mantiene la de esta tabla salvo que haya un motivo para cambiarla (se anota en el Changelog).

| Paquete                            | Versión   | Publicada            | Notas                                                   |
| ---------------------------------- | --------- | -------------------- | ------------------------------------------------------- |
| `typescript`                       | 6.0.3     | 2026-04-16           | Solo `web`; `api/client` sigue en 5.9.3                 |
| `react`, `react-dom`               | 19.3.0    | 2026-09-09           |                                                         |
| `@types/react`, `@types/react-dom` | 19.3.0    | 2026-09-09           |                                                         |
| `@types/geojson`                   | 7946.0.16 | 2025-01-23           | Tipos que usa `maplibre-gl`                             |
| `vite`                             | 8.3.2     | 2026-10-01           | 8.3.4 es del 2026-10-08                                 |
| `@vitejs/plugin-react`             | 6.1.1     | 2026-08-28           | 6.1.2 es del 2026-10-05                                 |
| `tailwindcss`, `@tailwindcss/vite` | 4.3.3     | 2026-07-16           |                                                         |
| `@tanstack/react-query`            | 5.104.1   | 2026-10-02           | 5.104.2 es del 2026-10-11                               |
| `maplibre-gl`                      | 6.11.2    | 2026-09-24           | 6.14.0 es del 2026-10-11                                |
| `oxlint`                           | 1.86.0    | 2026-09-28           | Reglas probadas con 1.88.0; se revalidan en T3          |
| `oxfmt`                            | 0.71.0    | 2026-09-28           |                                                         |
| `vitest`, `@vitest/coverage-v8`    | 5.0.3     | 2026-09-30           |                                                         |
| `jsdom`                            | 30.1.1    | 2026-09-22           |                                                         |
| `@testing-library/react`           | 16.3.3    | 2026-08-27           |                                                         |
| `@testing-library/dom`             | 10.4.2    | 2026-09-13           | Peer de `@testing-library/react`                        |
| `@testing-library/user-event`      | 14.6.7    | 2026-09-02           |                                                         |
| `@testing-library/jest-dom`        | 7.0.1     | 2026-08-09           |                                                         |
| `msw`                              | 3.0.2     | 2026-10-03           | `onUnhandledFrame` probado con 3.0.3; se revalida en T5 |
| `@jorgetroya80/bicimad-client`     | 0.4.0     | release `api-v0.4.0` | Excluido de `minimumReleaseAge`                         |

`api/client` fija sus `devDependencies` a lo que ya resuelve el lockfile: `typescript` 5.9.3 y `openapi-typescript` 7.13.0.

## Grafo de dependencias

```
T1 registro + cliente + tokens en CI
 └── T2 esqueleto Vite + React + TS + Tailwind (página vacía, proxy)
        ├── T3 oxlint + oxfmt + lint-staged
        └── T4 Vitest + cobertura + shared/config (env)
               └── T5 shared/api + MSW en tests + QueryProvider
               │      └── T6 dev:mock (MSW en el navegador, fuera de prod)
               ├── T7 shared/ui/base-map (MapLibre) en MapPage
               ├── T8 shared/lib/time + RelativeTime + StateMessage + DataAttribution
               └── T9 BottomSheet + SidePanel
                      └── T10 widgets/app-layout + MapPage completa   (necesita T7, T8, T9)
T11 cierre (tras T10)
```

## Task List

### Fase 1: Base del proyecto

- [x] **T1: Registro de GitHub Packages, cliente y tokens en CI** (M, ~1 h)
  - Descripción: `.npmrc` en la raíz solo con el registro de `@jorgetroya80` (sin credenciales: pnpm 12 no expande variables en las del repo). En local el token va en `~/.npmrc` (ver supuesto 2 de la spec). En `pnpm-workspace.yaml`, `minimumReleaseAgeExclude: ['@jorgetroya80/bicimad-client']` y `saveExact: true`. `web/package.json` añade `@jorgetroya80/bicimad-client` 0.3.0 en `dependencies`. `api/client/package.json` fija sus `devDependencies` (5.9.3 y 7.13.0). En `ci.yml` (`api`, `web`, `tooling`) y `release.yml` (`publish-web`, `publish-api-client`): `permissions: packages: read` (o `write` donde ya está), `actions/setup-node` con `registry-url: https://npm.pkg.github.com` y `scope: '@jorgetroya80'`, y `NODE_AUTH_TOKEN: ${{ secrets.GITHUB_TOKEN }}` en el paso de `pnpm install`.
  - Aceptación:
    - SC1: con `~/.npmrc` y `NODE_AUTH_TOKEN` en local, `pnpm install --frozen-lockfile` instala `@jorgetroya80/bicimad-client@0.3.0` desde `npm.pkg.github.com`; sin token, falla con 401 (mensaje claro, documentado en T11).
    - SC2 (parte de `api/client`): sin `^` ni `~` en `devDependencies`; el lockfile resuelve `typescript@5.9.3` para `api/client`.
    - `actionlint` sin errores.
  - Verificación: `pnpm install --frozen-lockfile`; `pnpm --filter @jorgetroya80/bicimad-client run build` tras `./api/gradlew -p api generateOpenApi` y `generate`; `actionlint`. SC11 (jobs `api` y `tooling` en verde) se confirma en el checkpoint 1, con el PR abierto.
  - Archivos: `.npmrc`, `pnpm-workspace.yaml`, `web/package.json`, `api/client/package.json`, `pnpm-lock.yaml`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`.
  - Commit: `build(web): install the api client from github packages`.

- [x] **T2: Esqueleto de la app** (M, ~1 h)
  - Descripción: dependencias de la tabla de versiones para React, Vite, TypeScript y Tailwind. `index.html`, `vite.config.ts` (plugin React, Tailwind, alias `@/`, proxy `/api` → `localhost:8080` quitando el prefijo), `tsconfig.json` (`strict`, `noUncheckedIndexedAccess`, `verbatimModuleSyntax`, `skipLibCheck`, `types` explícitos, `include: src, test, vite.config.ts`). FSD mínimo: `src/app/main.tsx`, `src/app/App.tsx`, `src/app/styles/index.css` y `src/pages/map` (`MapPage` con un título y `index.ts`). Scripts `dev`, `build`, `preview`, `typecheck` y los provisionales `lint` y `test`.
  - Aceptación:
    - `typecheck` y `build` pasan; `dist/` contiene `index.html` y los assets.
    - SC5: con `bootRun` y `dev` levantados, `curl -s -o /dev/null -w '%{http_code}' localhost:5173/api/v1/bicimad/stations` devuelve `200`.
    - SC2 (parte de `web`): sin `^` ni `~` en `web/package.json`.
  - Verificación: `pnpm --filter web typecheck && pnpm --filter web build`; `curl` de SC5; la página se ve en el navegador con estilos de Tailwind.
  - Archivos: `web/package.json`, `web/index.html`, `web/vite.config.ts`, `web/tsconfig.json`, `web/src/app/{main.tsx,App.tsx,styles/index.css}`, `web/src/pages/map/{ui/MapPage.tsx,index.ts}`.
  - Commit: `build(web): scaffold vite, react, typescript and tailwind`.

- [x] **T3: Lint y formato** (S, ~45 min)
  - Descripción: `.oxlintrc.json` con plugins (`typescript`, `react` (incluye `react-hooks/*`), `import`, `jsx-a11y`, `vitest`), categorías `correctness` y `suspicious`, y las reglas de la tabla de Code Style (con `override` para `vite.config.ts` y `*.test.ts(x)`). `.oxfmtrc.json` (`singleQuote`, `printWidth: 100`). Scripts `lint` y `format`. `.prettierignore` añade `web/`. `lint-staged.config.mjs`: `web/**/*.{ts,tsx}` → `pnpm --filter web exec oxlint --fix` + `oxfmt`; `web/**/*.{css,json}` → `oxfmt`. `.vscode/extensions.json` recomienda la extensión de Oxc; formato al guardar en VS Code y JetBrains (configuración local, ver spec).
  - Aceptación:
    - `lint` y `oxfmt --check` pasan sobre el código de T2.
    - Un archivo temporal con `any`, `!` y `export default` hace fallar `lint` con esas tres reglas (se borra al terminar).
    - SC10 (parte de pre-commit): un commit que toca `web/src/**/*.tsx` ejecuta oxlint y oxfmt; `prettier --check .` en la raíz ignora `web/`.
  - Verificación: `pnpm --filter web lint && pnpm --filter web exec oxfmt --check`; `pnpm exec prettier --check .`; commit de prueba.
  - Archivos: `web/.oxlintrc.json`, `web/.oxfmtrc.json`, `web/package.json`, `.prettierignore`, `lint-staged.config.mjs`, `.vscode/extensions.json`.
  - Commit: `build(web): add oxlint and oxfmt`.

- [x] **T4: Tests, cobertura y configuración** (M, ~1 h)
  - Descripción: Vitest, jsdom, coverage-v8, Testing Library y jest-dom. Configuración de test en `vite.config.ts` (`environment: 'jsdom'`, `globals`, `setupFiles`, `coverage.thresholds` del 80 % para `src/shared/**` y `src/widgets/**`, exclusiones de la spec). `test/setup.ts` con jest-dom. Primer código con TDD: `shared/config/env.ts` (reglas de `VITE_API_BASE_URL` de la spec) y `constants.ts` (`POLL_INTERVAL_MS`, `MADRID_CENTER`, zooms). El script `test` pasa a `vitest run --coverage`.
  - Aceptación:
    - Tests de `env`: sin valor → `/api`; quita la `/` final; acepta `https://…`; un valor como `api` lanza el error de la spec.
    - Cobertura ≥ 80 % en `src/shared` y `test` falla si se baja el umbral a propósito (se comprueba y se deshace).
  - Verificación: `pnpm --filter web test`.
  - Archivos: `web/package.json`, `web/vite.config.ts`, `web/test/setup.ts`, `web/src/shared/config/{env.ts,env.test.ts,constants.ts,index.ts}`.
  - Commit: `feat(web): add test setup and validated env config`.

#### Checkpoint 1: base

- [x] `lint`, `typecheck`, `test` y `build` en verde en local.
- [x] Rama empujada y PR en borrador: jobs `api`, `web` y `tooling` en verde (SC11).
- [x] Revisión con el usuario antes de seguir.

### Fase 2: Datos

- [x] **T5: Cliente del API, MSW en tests y QueryProvider** (M, ~1 h)
  - Descripción: `shared/api/client.ts` con `createClient<paths>({ baseUrl: env.apiBaseUrl, fetch: (request) => globalThis.fetch(request) })`, reexportando los tipos que hagan falta. `shared/api/mocks/{fixtures.ts,handlers.ts,node.ts}`, tipados con los tipos del cliente. `test/setup.ts` arranca el servidor con `onUnhandledFrame: 'error'`. `app/providers/QueryProvider.tsx` con los defaults de la spec, usado en `App.tsx`.
  - Aceptación:
    - Test de `shared/api`: `GET /v1/bicimad/stations` contra MSW devuelve el fixture con sus tipos; una petición sin handler hace fallar el test.
    - Test de `QueryProvider`: los defaults (`staleTime` 60 s, `refetchIntervalInBackground: false`, `retry: 1`) están aplicados.
    - Un cambio incompatible en los tipos del fixture rompe `typecheck`.
  - Verificación: `pnpm --filter web test && pnpm --filter web typecheck`.
  - Archivos: `web/src/shared/api/{client.ts,client.test.ts,index.ts}`, `web/src/shared/api/mocks/{fixtures.ts,handlers.ts,node.ts}`, `web/test/setup.ts`, `web/src/app/providers/QueryProvider.tsx`, `web/src/app/App.tsx`.
  - Commit: `feat(web): add typed api client with msw test server`.

- [x] **T6: Modo `dev:mock`** (S, ~45 min)
  - Descripción: `shared/api/mocks/browser.ts` (`setupWorker`). `main.tsx` arranca el worker solo si `import.meta.env.MODE === 'mock'`, con `import()` dinámico, antes de montar React. Plugin pequeño en `vite.config.ts` que, solo en modo `mock`, sirve `/mockServiceWorker.js` desde `msw/lib/mockServiceWorker.js`. Script `dev:mock` = `vite --mode mock`.
  - Aceptación:
    - SC6: con `dev:mock` y sin backend, `await (await fetch('/api/v1/bicimad/stations')).json()` en la consola devuelve el fixture.
    - SC7: tras `build`, `dist/` no contiene `mockServiceWorker.js` y `grep -r 'MSW' web/dist` no encuentra nada. Si el `grep` da un falso positivo por otra librería, se cambia el patrón y se anota en el Changelog.
  - Verificación: `pnpm --filter web dev:mock` + consola del navegador; `pnpm --filter web build && ! ls web/dist/mockServiceWorker.js && ! grep -rq 'MSW' web/dist`.
  - Archivos: `web/src/shared/api/mocks/browser.ts`, `web/src/app/main.tsx`, `web/vite.config.ts`, `web/package.json`.
  - Commit: `feat(web): add mock mode with msw in the browser`.

#### Checkpoint 2: datos

- [x] `test`, `typecheck` y `build` en verde; SC6 y SC7 comprobados.
- [x] Revisión con el usuario.

### Fase 3: Mapa y layout

- [x] **T7: Mapa base** (M, ~1 h)
  - Descripción: `shared/ui/base-map/BaseMap.tsx`, único archivo que importa `maplibre-gl` (y su CSS). Crea el mapa con el estilo `liberty` de OpenFreeMap, `MADRID_CENTER` y el zoom inicial, añade `AttributionControl` compacto y lo destruye al desmontar. `MapPage` muestra `BaseMap` a pantalla completa (provisional hasta T10).
  - Aceptación:
    - Test con `maplibre-gl` simulado: se crea con centro, zoom, estilo y `AttributionControl`; `remove()` se llama al desmontar.
    - SC9 (parte de OSM/OpenFreeMap): en el navegador se ve Madrid centrado en Puerta del Sol con la atribución de OpenStreetMap/OpenFreeMap.
  - Verificación: `pnpm --filter web test`; `pnpm --filter web dev` en el navegador.
  - Archivos: `web/src/shared/ui/base-map/{BaseMap.tsx,BaseMap.test.tsx,index.ts}`, `web/src/shared/ui/index.ts`, `web/src/pages/map/ui/MapPage.tsx`.
  - Commit: `feat(web): add maplibre base map centered on madrid`.

- [x] **T8: Tiempo relativo y mensajes de estado** (S, ~45 min)
  - Descripción: `shared/lib/time/formatRelative.ts` (TDD), `shared/ui/relative-time`, `shared/ui/state-message` (vacío / error / cargando, con `role="status"` o `role="alert"`) y `shared/ui/data-attribution` ("Datos: EMT Madrid MobilityLabs").
  - Aceptación:
    - `formatRelative`: "hace 30 segundos", "hace 1 minuto", "hace 5 minutos" y el límite de 60 s.
    - `RelativeTime` pone `dateTime` en ISO; `StateMessage` usa el rol correcto en cada variante.
  - Verificación: `pnpm --filter web test`.
  - Archivos: `web/src/shared/lib/{time/formatRelative.ts,time/formatRelative.test.ts,index.ts}`, `web/src/shared/ui/{relative-time,state-message,data-attribution}/*`, `web/src/shared/ui/index.ts`.
  - Commit: `feat(web): add relative time, state message and data attribution`.

- [x] **T9: Bottom sheet y panel lateral** (S, ~1 h)
  - Descripción: `shared/ui/bottom-sheet` (dos estados, asa `button` con `aria-expanded` y `aria-controls`, ≥ 44 px, foco visible, desplegado al 60 % de la altura) y `shared/ui/side-panel` (360 px, fijo a la izquierda). Ambos reciben el contenido por `children`.
  - Aceptación:
    - `BottomSheet`: se abre y se cierra con clic, con `Enter` y con `Espacio`; `aria-expanded` cambia; el contenido se renderiza.
    - `SidePanel` renderiza `children` dentro de un `aside` con nombre accesible.
  - Verificación: `pnpm --filter web test`.
  - Archivos: `web/src/shared/ui/bottom-sheet/{BottomSheet.tsx,BottomSheet.test.tsx,index.ts}`, `web/src/shared/ui/side-panel/{SidePanel.tsx,SidePanel.test.tsx,index.ts}`, `web/src/shared/ui/index.ts`.
  - Commit: `feat(web): add bottom sheet and side panel`.

- [x] **T10: Layout responsive y página del mapa** (M, ~1 h)
  - Descripción: `widgets/app-layout` con `AppLayout` (`h-dvh`; recibe `map` y `panel` como slots): `BottomSheet` por debajo de `md`, `SidePanel` desde `md`. `MapPage` compone `AppLayout` con `BaseMap`, un `StateMessage` vacío ("Las estaciones llegan en la siguiente versión" o similar, a confirmar en el PR) y `DataAttribution`.
  - Aceptación:
    - Test de `AppLayout`: renderiza el mapa y el panel recibidos. Test de `MapPage` (con `BaseMap` simulado): estado vacío y atribución de EMT visibles.
    - SC8: a 360 px el mapa ocupa la pantalla y el bottom sheet se abre y se cierra con el botón y con el teclado; a 768 y 1280 px el panel lateral está fijo y el mapa ocupa el resto.
    - SC9 completo: las atribuciones de OSM/OpenFreeMap y de EMT son visibles en los tres anchos.
  - Verificación: `pnpm --filter web test`; navegador con DevTools a 360, 768 y 1280 px (capturas en el PR).
  - Archivos: `web/src/widgets/app-layout/{ui/AppLayout.tsx,ui/AppLayout.test.tsx,index.ts}`, `web/src/pages/map/ui/{MapPage.tsx,MapPage.test.tsx}`.
  - Commit: `feat(web): add responsive app layout to the map page`.

#### Checkpoint 3: mapa y layout

- [x] `lint`, `typecheck`, `test` (con cobertura ≥ 80 %) y `build` en verde.
- [x] SC8 y SC9 comprobados en el navegador.
- [x] Revisión con el usuario.

### Fase 4: Cierre

- [x] **T11: Documentación y verificación final** (S, ~45 min)
  - Descripción: `web/README.md` (quita "Not built yet"; comandos, requisito de `~/.npmrc` con la línea de `_authToken` y de `NODE_AUTH_TOKEN` con `read:packages`, `dev` frente a `dev:mock`). Añadir un favicon (`web/public/favicon.ico` o SVG enlazado desde `index.html`) para quitar el 404 de la consola visto en T7. Medir el JS de `dist/` (SC13). Repasar SC1-SC13 y anotar en el PR los manuales (SC6, SC7, SC9, SC10, SC13, capturas). Spec y plan a **implemented** y Capability Map actualizado.
  - Aceptación:
    - SC3 y SC4 en local y en CI; SC13: JS de la carga inicial ≤ 300 KB gzip.
    - Todos los SC anotados en el PR menos SC12, que se confirma en la primera release de `web`.
  - Verificación: `pnpm --filter web lint && pnpm --filter web typecheck && pnpm --filter web test && pnpm --filter web build`; CI del PR en verde.
  - Archivos: `web/README.md`, `web/public/favicon.*`, `web/index.html`, `docs/specs/SPEC-web-shell.md`, `docs/plans/PLAN-web-shell.md`, `docs/specs/CAPABILITY-MAP.md`.
  - Commit: `docs: close web-shell module`.

#### Checkpoint final

- [x] SC1-SC11 y SC13 verificados; PR listo para squash merge.
- [x] SC12: `web-v0.2.0` (PR de release #46) lleva `web-dist.tar.gz`.

## Riesgos y mitigaciones

| Riesgo                                                                                                           | Impacto | Mitigación                                                                                                                            |
| ---------------------------------------------------------------------------------------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `GITHUB_TOKEN` no puede leer el paquete en CI (el paquete no está enlazado al repo o le falta acceso de Actions) | Alto    | Es T1, lo primero. Si falla, revisar en GitHub "Manage Actions access" del paquete; no se usa un PAT en CI sin preguntar              |
| Sin `~/.npmrc` o sin token válido, `pnpm install` falla con 401 para todo el workspace, incluida la raíz         | Medio   | Documentado en el README de `web` (T11); es una consecuencia aceptada en la spec                                                      |
| Dependabot no resuelve el paquete privado del registro                                                           | Bajo    | Aceptado: Dependabot se gestiona desde GitHub (supuesto 12 de la spec)                                                                |
| Versiones anteriores a las del prototipo (`oxlint` 1.86.0, `msw` 3.0.2, `vitest` 5.0.3) se comportan distinto    | Bajo    | Se revalidan las reglas de oxlint en T3 y `onUnhandledFrame` en T5; si algo falla, se anota y se pregunta antes de cambiar de versión |
| El plugin que sirve `mockServiceWorker.js` no funciona con Vite 8                                                | Medio   | Alternativa sin dependencias: copiar el worker a una carpeta fuera de `public/` y usarla como `publicDir` solo en modo `mock`         |
| Las teselas de OpenFreeMap fallan o tienen límites de uso                                                        | Bajo    | En desarrollo no bloquea; los términos se revisan en `deploy` (fuera de alcance)                                                      |
| `grep 'MSW'` da falso positivo en `dist/`                                                                        | Bajo    | Se ajusta el patrón en T6 y se anota                                                                                                  |
| El JS supera 300 KB gzip                                                                                         | Bajo    | El prototipo dio 81 KB; si se supera, carga diferida de `BaseMap` con `lazy()`                                                        |

## Paralelización

Un solo desarrollador: orden T1 → T11. Tras T4, T7, T8 y T9 son independientes entre sí; T5-T6 también son independientes de T7-T9.

## Estimación

Unas 9,5 h, en 3-4 sesiones: Fase 1 (~3,75 h), Fase 2 (~1,75 h), Fase 3 (~3,75 h), Fase 4 (~0,75 h). Cada checkpoint es un buen punto para cortar.

## Resultado

- T1-T11 en la rama `feat/web-shell`, un commit por tarea (T10 en dos), en el PR #43 (squash merge). Cambio previo en el API: PR #44 y release `api-v0.4.0` (PR #45) para que el cliente acepte un `fetch` propio.
- SC1-SC13 verificados:
  - `pnpm install --frozen-lockfile` desde cero con token válido; CI con `GITHUB_TOKEN` en verde.
  - `lint`, `typecheck`, `test` (63 tests; `src/shared` y `src/widgets` al 100 %) y `build`.
  - Proxy con `bootRun` (200 con datos reales), `dev:mock` en Chrome headless (fixture de 3 estaciones), sin MSW en `dist/`.
  - Layout y atribuciones con el build de producción a 360, 768 y 1280 px; favicon sin 404.
  - Carga inicial 78,56 KB gzip de JS (MapLibre en diferido: 274,99 KB + worker ≈ 144 KB).
- SC12: release `web-v0.2.0` (PR #46) con `web-dist.tar.gz`; `publish-web` en verde. La misma release subió `api` a 0.5.0 sin cambios en el API, porque el squash de #43 tocaba `api/client/package.json`; desde el PR #47 el job `release-scope` de CI lo impide.

## Changelog

- 2026-10-13: versión inicial.
- 2026-10-13: plan aprobado.
- 2026-10-13: T1 corregida: el token no va en el `.npmrc` del repo (pnpm 12 no expande variables en credenciales del repo), sino en `~/.npmrc` en local y en el `.npmrc` que genera `setup-node` con `registry-url` en CI.
- 2026-10-13: T1 implementada. `setup-node` con `registry-url` y `scope` en `api`, `web`, `tooling` y `publish-web`; `publish-api-client` ya los tenía y solo recibe el token en la instalación. Lockfile con el cliente 0.3.0 desde `npm.pkg.github.com` (paquete real, no enlazado a `api/client`); el cliente sigue compilando con TypeScript 5.9.3; `actionlint` sin errores. Pendiente: SC1 desde cero con un token local válido (el actual da 401; el lockfile salió de la caché de pnpm) y SC11 en el PR.
- 2026-10-13: T2 implementada. `oxlint` 1.86.0 y `vitest` 5.0.3 se instalan ya en T2 para los scripts provisionales `lint` y `test` (el job `web` de CI los ejecuta desde que existe `web/tsconfig.json`). `types` del `tsconfig` solo lista `vite/client`: `geojson`, `vitest/globals` y `@testing-library/jest-dom/vitest` los añade la tarea que instala cada paquete. SC6 comprobado: con `bootRun`, `/api/v1/bicimad/stations` por el proxy devuelve 200 con datos reales. Build: 68,71 KB gzip de JS. Commit con `--no-verify` (decisión del usuario): lint-staged aún llama a eslint, que no está instalado; T3 lo sustituye. commitlint y Prettier se comprobaron a mano.
- 2026-10-13: T3 implementada. Sin `format:check` ni cambios en `ci.yml` (decisión del usuario: formato al guardar y en pre-commit). El plugin `react` de oxlint 1.86.0 incluye `react-hooks/*` (no existe un plugin `react-hooks`). `react/react-in-jsx-scope` desactivada (aprobado por el usuario) e `import/no-unassigned-import` con `allow` para CSS. `.oxfmtrc.json` ignora `CHANGELOG.md` (oxfmt también formatea Markdown). lint-staged: la regla de Prettier de la raíz descarta los archivos de `web/`. Comprobado: el archivo de prueba falla con `no-explicit-any`, `no-non-null-assertion` y `no-default-export`; lint-staged ejecuta oxlint y oxfmt en `web/` y Prettier no los toca. VS Code: extensión Oxc 1.63.0 instalada y `.vscode/settings.json` local; JetBrains: plugin pendiente de instalar desde el IDE.
- 2026-10-13: T4 implementada con TDD (rojo: el import de `./env` no resolvía; verde: 11 tests, `shared/config` al 100 %). `parseApiBaseUrl` es una función pura y `env` la aplica a `import.meta.env` (tipado en `src/vite-env.d.ts`); `/` se mantiene como `/`. `MADRID_CENTER = { lng, lat }` y solo `MADRID_ZOOM = 15`. Umbrales de cobertura por glob en `vite.config.ts`; comprobado que `test` falla por debajo del 80 % en `src/shared`. Fuera de la lista de archivos: `index.test.ts` (prueba la API pública de `shared/config`; sin él, `constants.ts` sin consumidores dejaba `src/shared` en el 76,92 %), `coverage/` en `.gitignore` y `allow` de jest-dom en `import/no-unassigned-import`. SC1 comprobado desde cero con el token nuevo: instalación con la caché de pnpm vacía.
- 2026-10-13: checkpoint 1. Rama empujada y PR #43 en borrador; CI en verde (`API (Gradle)`, `Web (pnpm)`, `Root tooling`, `Lint workflows`, `pr-title`, `ci-ok`): `GITHUB_TOKEN` lee el cliente desde GitHub Packages (SC11).
- 2026-10-13: T5 implementada. El cliente 0.3.0 no aceptaba un `fetch` propio; por decisión del usuario se cambió antes el cliente (PR #44, `createBicimadClient(baseUrl, { fetch })`, release `api-v0.4.0` en el PR #45) en lugar de añadir `openapi-fetch` a `web`. `feat/web-shell` rebasada sobre `main`. `baseUrl` absoluta (`new URL(env.apiBaseUrl, location.origin)`) porque el `Request` de Node no acepta rutas relativas (aprobado). `onUnhandledFrame: 'error'` existe en msw 3.0.2: una petición sin handler rechaza la llamada y MSW lo registra con `console.error`; el test comprueba las dos cosas. Fixture tipado con `StationsResponse` (3 estaciones); un tipo incorrecto en el fixture rompe `typecheck` (comprobado). `createQueryClient()` con los defaults de la spec (`staleTime = POLL_INTERVAL_MS`). Añadido `QueryProvider.test.tsx`. 15 tests; `src/shared/api` al 100 %. MSW fuera del build (76,13 KB gzip de JS). Aviso de pnpm: `@vitest/mocker` pide msw `^2.4.9` como peer opcional (solo modo navegador de Vitest; no afecta).
- 2026-10-13: T6 implementada. `enableMocking()` en `main.tsx` (await de nivel superior) solo en modo `mock`, con `onUnhandledFrame: 'bypass'` para no llenar la consola con teselas y assets de Vite. Plugin propio en `vite.config.ts` (`apply` solo con `serve` + modo `mock`) que sirve `msw/mockServiceWorker.js`. No se usa el plugin oficial `msw/vite`: emite el worker al build si se importa `virtual:msw`, lo que choca con SC8. SC8: sin `mockServiceWorker.js` ni código de MSW en `dist/` (76,14 KB gzip). SC7 comprobado en Chrome headless sin backend: `fetch('/api/v1/bicimad/stations')` devuelve el fixture y la consola muestra "[MSW] Mocking enabled.". Con `dev` normal, `/mockServiceWorker.js` cae en el fallback de Vite (`index.html`). Checkpoint 2 pendiente de revisión del usuario.
- 2026-10-13: checkpoint 2 revisado por el usuario.
- 2026-10-13: `maplibre-gl` baja a 6.11.2 (decisión del usuario): 6.12.0 se publicó el 2026-10-03 a las 21:45 UTC y aún no cumplía los 10 días de `minimumReleaseAge` a la hora de T7. Corregida la numeración de los SC en este plan, desfasada desde que se quitó el SC4 de la spec (lint FSD).
- 2026-10-13: T7 implementada con `maplibre-gl` 6.11.2 (TDD: rojo por import sin resolver; 22 tests, `base-map` al 100 %). `BaseMap.tsx` es el único archivo que importa MapLibre: estilo `liberty` (`MAP_STYLE_URL` en `shared/config`), centro y zoom de Madrid, `AttributionControl` compacto y `remove()` al desmontar; contenedor `<section aria-label="Mapa de Madrid">` (jsx-a11y rechaza `role="region"` en un `div`). Desviaciones aprobadas: `setWorkerUrl` con la URL de Vite (sin ella, "Worker failed to load") y carga diferida (`LazyBaseMap`, exportado como `BaseMap`) porque MapLibre llevaba el JS a ≈ 495 KB gzip. Build: carga inicial 77,43 KB (`index` 73,89 + `config` 3,54), chunk de MapLibre 274,93 KB, worker ≈ 144 KB. SC9 (parte OSM/OpenFreeMap) comprobado en Chrome headless con el build de producción a 1280x800 y 360x740: mapa centrado en Sol y atribución visible. Único error de consola: `favicon.ico` 404 (fuera de T7). `MapPage` muestra el mapa a pantalla completa con un `<h1>` `sr-only` (provisional hasta T10).
- 2026-10-13: T11 añade un favicon (decisión del usuario), para quitar el 404 de `favicon.ico`.
- 2026-10-13: T8 implementada con TDD (cada unidad en rojo antes de implementar; 32 tests). `formatRelative`: "ahora" (0 s), "hace 30 segundos", "hace 59 segundos", "hace 1 minuto" (60 s), "hace 5 minutos"; los segundos se redondean antes del límite de 60 s. `RelativeTime` con `now` obligatorio (`react/purity`). `StateMessage({ variant, title, children })`: `status` para vacío, `alert` para error, `status` + `aria-busy` para cargando; borde de color y título, nunca solo color. `DataAttribution`: "Datos: EMT Madrid MobilityLabs" con enlace a emtmadrid.es (`noopener noreferrer`). Ninguna pieza entra aún en el bundle (73,92 KB gzip inicial).
- 2026-10-13: T9 implementada con TDD (40 tests). `BottomSheet({ label, defaultExpanded?, children })`, no controlado: asa `button` de ancho completo y `min-h-11` con `aria-expanded`, `aria-controls` y foco visible; contenido en `<section aria-labelledby hidden>` (oculto también para lectores de pantalla); desplegado a `h-[60dvh]`. Sin animación (opcional en la spec). `SidePanel` reenvía las props de `aside` con `label` obligatorio, `w-90` (360 px) y scroll propio; la visibilidad por breakpoint la decide `AppLayout`. Añadido `SidePanel.test.tsx`.
- 2026-10-13: T10 implementada con TDD (56 tests; `widgets` y `pages` al 100 %), en dos commits: `feat(web): animate the bottom sheet` (animación con `translate-y`, `inert` en lugar de `hidden`, franja del asa en el flujo) y `feat(web): add responsive app layout to the map page`. `AppLayout({ map, panel, panelLabel })`: columna por debajo de `md`, fila con `SidePanel` de 360 px desde `md`; panel montado en los dos contenedores por CSS. `MapPage`: `<h1>` `sr-only`, estado vacío "Las estaciones llegan en la siguiente versión" (confirmado) y `DataAttribution` al pie. Atribución de MapLibre arriba a la derecha por debajo de `md` (decisión del usuario; `MD_MEDIA_QUERY` en `shared/config`). Carga inicial ≈ 81,8 KB gzip (JS 78,56 + CSS 3,24); MapLibre sigue en el chunk diferido (274,99 KB). SC8 y SC9 comprobados con el build de producción en Chrome headless a 360x740 (plegado, desplegado y a mitad de animación), 768x1024 y 1280x800: las dos atribuciones visibles en todos. `inert` no lo respetan jsdom ni Testing Library: los tests comprueban el atributo.
- 2026-10-13: checkpoint 3 revisado por el usuario.
- 2026-10-13: T11 implementada. `web/README.md` reescrito (requisitos con `~/.npmrc` y token, comandos, `VITE_API_BASE_URL`, arquitectura), favicon SVG (`web/public/favicon.svg`, sin 404 de `favicon.ico`) y README raíz actualizado (cómo arrancar la web). Repaso de SC1-SC13: todos en verde salvo SC12 (pendiente de release). Avisos de consola del estilo `liberty` de OpenFreeMap (`highway-shield-*`) ignorados: son del estilo, no del código.
- 2026-10-13: plan implementado.
- 2026-10-13: revisión del PR #43: cuatro arreglos menores en un commit `fix(web)` (proxy, validación de `VITE_API_BASE_URL`, arranque de MSW, plugin del worker; 57 tests). Tres puntos abiertos en la spec (landmarks, fechas inválidas, panel montado dos veces), a resolver antes del merge.
- 2026-10-13: puntos abiertos de la revisión del PR #43, resueltos en un commit `fix(web)`. `useMediaQuery` (`useSyncExternalStore`) en `shared/lib`; `AppLayout({ title, map, panel, panelLabel })` monta solo el contenedor del breakpoint, todo dentro de `<main>` con el `<h1>`; `SidePanel` es una `section` (región con nombre). Tests: `stubMatchMedia` en `test/matchMedia.ts` (jsdom no tiene `matchMedia`; por defecto por debajo de `md`) y `unstubGlobals` en Vitest. 63 tests; lint, typecheck y build en verde; capturas del build a móvil y 1280 px correctas. Fechas inválidas: regla en la spec para `web-bicimad`, sin código. La aceptación de T9 (`aside`) queda como histórico.
- 2026-10-13: SC12 verificado en la release `web-v0.2.0` (PR #46).
