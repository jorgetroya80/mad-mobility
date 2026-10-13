# Spec: web-shell

- Created: 2026-10-13
- Status: **approved** (2026-10-13)
- Plan: [PLAN-web-shell.md](../plans/PLAN-web-shell.md)

Módulo `web-shell` del [Capability Map](CAPABILITY-MAP.md). Crea el proyecto `web/` (Vite + React + TypeScript), su tooling (oxlint, oxfmt), la arquitectura Feature-Sliced Design (FSD), el cliente tipado del API, el mapa base y el layout responsive. No muestra estaciones: eso es `web-bicimad`. Fuente: [docs/ideas/bicimad-now.md](../ideas/bicimad-now.md).

## Objective

Dejar lista una SPA vacía pero completa (mapa de Madrid, layout responsive, cliente del API, tests, lint y CI) para que `web-bicimad` solo añada entidades y features, sin decidir infraestructura.

- Como desarrollador del frontend, quiero un proyecto con versiones exactas y lockfile congelado, para que el build sea reproducible en local, en CI y en la release.
- Como desarrollador del frontend, quiero una arquitectura FSD con capas y API pública por slice, para que `web-bicimad` y `bus` crezcan sin acoplarse entre sí.
- Como desarrollador del frontend, quiero importar el cliente `@jorgetroya80/bicimad-client` publicado, para que un cambio incompatible del API rompa mi build y no la app.
- Como desarrollador del frontend, quiero simular el API con MSW, para desarrollar y testear sin backend y sin gastar cupo de la EMT.
- Como usuario, quiero ver el mapa de Madrid a pantalla completa en el móvil y con un panel lateral en escritorio, con la atribución de los datos visible.

**Fuera de alcance:** estaciones (mapa, lista, detalle), filtro bicis/anclajes, favoritos, geolocalización, enlaces `?station=` y router (todo en `web-bicimad`); despliegue y revisión de términos de las teselas (`deploy`); módulo `bus`.

## Assumptions

1. El cliente se instala desde GitHub Packages con versión exacta (hoy `0.3.0`), nunca con `workspace:`. `api/client` sigue en el workspace solo para compilarse y publicarse; `web` no lo enlaza.
2. GitHub Packages exige autenticación también para paquetes públicos. En local hace falta `NODE_AUTH_TOKEN` (PAT con `read:packages`); en CI, `GITHUB_TOKEN` con `packages: read`.
3. Como el workspace es uno, todo job que ejecute `pnpm install` (`api`, `web`, `tooling`, `publish-api-client`, `publish-web`) necesita ese token, no solo el de `web`.
4. `minimumReleaseAge` (10 días) bloquearía la versión del cliente recién publicada: `@jorgetroya80/bicimad-client` va en `minimumReleaseAgeExclude`.
5. La estructura `features/bicimad`, `shared/map`, etc. de la idea queda sustituida por FSD (ver [Project Structure](#project-structure)).
6. Prettier sigue formateando lo que no es de `web/` (Markdown, YAML y JSON de la raíz, como en `foundation`). oxfmt es el único formateador dentro de `web/`, y `web/` pasa a `.prettierignore`.
7. La URL del API se fija en build con `VITE_API_BASE_URL` (por defecto `/api`). En local, el proxy de Vite manda `/api/*` a `http://localhost:8080/*` quitando el prefijo; en producción lo decide `deploy`.
8. Las teselas salen de OpenFreeMap (estilo `liberty`), sin clave de API.
9. Cada parte usa su TypeScript: `web` 6.0.3; `api/client` sigue en 5.9.3, porque `openapi-typescript` 7.13.0 (la última) pide `typescript: ^5.x` como peer. Por eso `web` no reutiliza tooling de TypeScript de `api/client`.
10. TypeScript 6 cambia defaults (`types` vacío por defecto, entre otros): los `tsconfig.json` de `web` declaran `types` de forma explícita (`geojson`, `vite/client`, `vitest/globals`, `@testing-library/jest-dom/vitest`). `geojson` hace falta porque los tipos de `maplibre-gl` usan el namespace global `GeoJSON`.
11. `skipLibCheck: true` en `web`: `@testing-library/jest-dom` 7.0.1 y Vitest 5 declaran `Assertion` con parámetros de tipo distintos (`TS2428`) dentro de `node_modules`. Nuestro código se sigue comprobando entero y los matchers funcionan (verificado el 2026-10-13 con TS 6.0.3 y 7.0.2).
12. Dependabot se gestiona desde la configuración de GitHub; este módulo no toca `.github/dependabot.yml` ni añade `registries`.

## Tech Stack

| Herramienta                                               | Línea   | Uso                                                                  |
| --------------------------------------------------------- | ------- | -------------------------------------------------------------------- |
| Node.js                                                   | 24 LTS  | Como `foundation` (`.nvmrc`)                                         |
| pnpm                                                      | 12.8.1  | Como `foundation` (`packageManager`)                                 |
| TypeScript                                                | 6.0.3   | `tsc --noEmit` (`strict`)                                            |
| React + React DOM                                         | 19.x    | UI                                                                   |
| Vite + `@vitejs/plugin-react`                             | 8.x/6.x | Dev server, proxy, build estático                                    |
| Tailwind CSS + `@tailwindcss/vite`                        | 4.x     | Estilos (config CSS-first, sin `tailwind.config.js`)                 |
| `@tanstack/react-query`                                   | 5.x     | Estado del servidor, polling                                         |
| `@jorgetroya80/bicimad-client`                            | 0.3.0   | Cliente tipado del API (`openapi-fetch` + tipos)                     |
| `maplibre-gl`                                             | 6.x     | Mapa base, envuelto en `shared/ui/base-map`                          |
| oxlint                                                    | 1.x     | Lint (plugins `typescript`, `react`, `jsx-a11y`, `import`, `vitest`) |
| oxfmt                                                     | 0.x     | Formato de TS, TSX, CSS y JSON de `web/`                             |
| Vitest + `@vitest/coverage-v8` + jsdom                    | 5.x     | Tests y cobertura                                                    |
| `@testing-library/react`, `dom`, `user-event`, `jest-dom` | última  | Tests de componentes (`dom` es peer obligatorio de `react`)          |
| `@types/react`, `@types/react-dom`                        | 19.x    | Tipos de React                                                       |
| `@types/geojson`                                          | última  | Namespace `GeoJSON` que usan los tipos de `maplibre-gl`              |
| MSW                                                       | 3.x     | API simulada en tests y en `pnpm dev:mock`                           |

**Versiones fijadas:**

- `web/package.json` solo lleva versiones exactas (`"react": "19.3.0"`, nunca `^` ni `~`), en `dependencies` y `devDependencies`. `saveExact` activo en el workspace para que `pnpm add` lo respete.
- `api/client/package.json` fija sus `devDependencies`: `typescript` 5.9.3 y `openapi-typescript` 7.13.0. Su `dependencies` (`openapi-fetch`) sigue con rango: es una librería publicada y el rango lo resuelve quien la instala.
- La raíz no cambia.
- El lockfile se instala siempre con `--frozen-lockfile` (ya en CI).
- Las versiones exactas se eligen en el plan: la última estable con más de 10 días (`minimumReleaseAge`) y compatible con el resto.
- Ninguna dependencia fuera de esta tabla sin preguntar (ver [Boundaries](#boundaries)).

## Commands

```bash
export NODE_AUTH_TOKEN=<PAT con read:packages>   # solo para pnpm install
pnpm install

pnpm --filter web dev            # Vite en :5173, proxy /api -> localhost:8080
pnpm --filter web dev:mock       # vite --mode mock: MSW en el navegador, sin backend
pnpm --filter web lint           # oxlint
pnpm --filter web format         # oxfmt (escribe)
pnpm --filter web format:check   # oxfmt --check (CI)
pnpm --filter web typecheck      # tsc --noEmit
pnpm --filter web test           # vitest run --coverage
pnpm --filter web build          # tsc --noEmit && vite build -> web/dist
pnpm --filter web preview        # sirve web/dist
```

## Project Structure

Capas FSD, de arriba abajo: `app` → `pages` → `widgets` → `features` → `entities` → `shared`. Una capa solo importa de las de abajo; dos slices de la misma capa no se importan entre sí. `features` y `entities` no se crean en este módulo.

```
web/
  index.html
  package.json                 versiones exactas; scripts de Commands
  tsconfig.json                strict, skipLibCheck, types explícitos, alias @/ -> src/; include: src, test, vite.config.ts
  vite.config.ts               plugins, proxy /api, config de Vitest; en modo mock sirve /mockServiceWorker.js
  .oxlintrc.json               reglas de lint
  .oxfmtrc.json                singleQuote, printWidth 100 (como Prettier en la raíz)
  src/
    app/
      main.tsx                 punto de entrada; en modo mock carga MSW con import() dinámico
      App.tsx                  providers + página
      providers/
        QueryProvider.tsx      QueryClient con los defaults de abajo
      styles/
        index.css              @import "tailwindcss"; tokens de tema
    pages/
      map/
        ui/MapPage.tsx         AppLayout + BaseMap; panel con estado vacío
        index.ts               API pública del slice
    widgets/
      app-layout/
        ui/AppLayout.tsx       mapa a pantalla completa + slot de panel (bottom sheet / lateral)
        index.ts
    shared/
      api/
        client.ts              createClient<paths>({ baseUrl: env.apiBaseUrl, fetch: (request) => globalThis.fetch(request) })
        mocks/
          handlers.ts          handlers MSW tipados con los tipos del cliente
          fixtures.ts          respuestas de ejemplo tipadas
          browser.ts           setupWorker (dev:mock)
          node.ts              setupServer (tests)
        index.ts
      config/
        env.ts                 lee y valida import.meta.env una sola vez (ver abajo)
        constants.ts           POLL_INTERVAL_MS = 60_000, MADRID_CENTER (Puerta del Sol), zooms
        index.ts
      lib/
        time/formatRelative.ts "hace X" con Intl.RelativeTimeFormat (es)
        index.ts
      ui/
        base-map/              envoltorio de MapLibre (único sitio que importa maplibre-gl)
        bottom-sheet/          panel inferior: plegado / desplegado
        side-panel/            panel lateral fijo (md:)
        state-message/         estados vacío / error / cargando
        relative-time/         <RelativeTime date=…> con formatRelative
        data-attribution/      "Datos: EMT Madrid MobilityLabs"
        index.ts
  test/
    setup.ts                   jest-dom + servidor MSW (onUnhandledFrame: 'error'; en MSW 3 sustituye a onUnhandledRequest)
```

- Cada slice (y cada segmento de `shared`) expone su API pública en `index.ts`. Fuera del slice solo se importa desde ese `index.ts`. Las reglas FSD no se comprueban con lint: se revisan en cada PR.
- Dentro de un slice se usan imports relativos; entre slices, siempre el alias `@/`.
- `env.ts` valida `VITE_API_BASE_URL` al cargar el módulo. Sin valor usa `/api`. Acepta una ruta que empieza por `/` o una URL `http(s)://`, y le quita la `/` final. Con cualquier otro valor lanza `Error('VITE_API_BASE_URL must be a path starting with / or an http(s) URL: <valor>')`, así que la app no arranca con una URL mal configurada.
- MSW nunca llega a producción:
  - `main.tsx` arranca el worker solo si `import.meta.env.MODE === 'mock'`, con `import('@/shared/api/mocks/browser')`. En el build de producción esa rama es código muerto y Vite la elimina.
  - `mockServiceWorker.js` no está en `public/` ni en git. Un plugin pequeño dentro de `vite.config.ts` lo sirve desde `msw/lib/mockServiceWorker.js` solo en `vite --mode mock`; así nunca se copia a `dist/` y siempre coincide con la versión instalada de MSW.
- Los tests van junto al código (`Component.test.tsx`).
- `QueryClient` por defecto: `staleTime` 60 s, `refetchIntervalInBackground: false` (el polling se pausa con la pestaña oculta), `refetchOnWindowFocus: true`, `retry: 1`. El `refetchInterval` lo pone cada query en `web-bicimad` con `POLL_INTERVAL_MS`.

### Layout responsive

- Móvil (< `md`): mapa a pantalla completa con `h-dvh` (no `h-screen`: `100vh` no descuenta la barra del navegador en Safari iOS y el panel queda tapado); `BottomSheet` con dos estados (plegado con el asa visible / desplegado al 60 % de la altura), cambiados con un botón asa. Arrastrar queda fuera de alcance.
- `md` en adelante: `SidePanel` fijo a la izquierda (360 px), mapa en el resto.
- Controles táctiles ≥ 44 px; foco visible; el asa es un `button` con `aria-expanded`.
- Se prueba a 360, 768 y 1280 px de ancho.
- Atribución siempre visible: `AttributionControl` de MapLibre (OpenStreetMap / OpenFreeMap) y `DataAttribution` (EMT).

### Cambios fuera de `web/`

| Archivo                         | Cambio                                                                                                                                                                                                   |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `.npmrc` (nuevo)                | `@jorgetroya80:registry=https://npm.pkg.github.com` y `//npm.pkg.github.com/:_authToken=${NODE_AUTH_TOKEN}`                                                                                              |
| `api/client/package.json`       | `devDependencies` con versión exacta: `typescript` 5.9.3, `openapi-typescript` 7.13.0                                                                                                                    |
| `pnpm-workspace.yaml`           | `minimumReleaseAgeExclude: ['@jorgetroya80/bicimad-client']`; `saveExact: true`                                                                                                                          |
| `.prettierignore`               | añade `web/`                                                                                                                                                                                             |
| `lint-staged.config.mjs`        | `web/**/*.{ts,tsx,css,json}`: `pnpm --filter web exec oxlint --fix` (solo ts/tsx) + `pnpm --filter web exec oxfmt`, para usar `.oxlintrc.json` y `.oxfmtrc.json` de `web/` (sustituye eslint + prettier) |
| `.github/workflows/ci.yml`      | `packages: read` y `NODE_AUTH_TOKEN` en los jobs con `pnpm install`; job `web` añade `format:check`                                                                                                      |
| `.github/workflows/release.yml` | `packages: read` y `NODE_AUTH_TOKEN` en `publish-web` (y en `publish-api-client` si instala el workspace)                                                                                                |
| `web/README.md`                 | quita "Not built yet"; comandos y requisito del token                                                                                                                                                    |

## Code Style

Clean Code y SOLID aplicados a React y FSD. Ejemplo del estilo esperado:

```tsx
// shared/lib/time/formatRelative.ts — función pura, sin React
const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });

export function formatRelative(date: Date, now: Date): string {
  const seconds = Math.round((date.getTime() - now.getTime()) / 1000);
  if (Math.abs(seconds) < 60) return rtf.format(seconds, 'second');
  return rtf.format(Math.round(seconds / 60), 'minute');
}

// shared/ui/relative-time/RelativeTime.tsx — solo presenta
type RelativeTimeProps = { date: Date; now?: Date };

export function RelativeTime({ date, now = new Date() }: RelativeTimeProps) {
  return <time dateTime={date.toISOString()}>{formatRelative(date, now)}</time>;
}
```

**SOLID:**

- **S (responsabilidad única):** un componente presenta o compone; la lógica va en hooks (`useX`) o funciones puras de `lib/`. Un componente, un archivo.
- **O (abierto/cerrado):** se extiende por composición (`children`, slots como `panel` en `AppLayout`), no con props booleanas que cambian el comportamiento (`isMobile`, `variant="special"`).
- **L (sustitución):** los componentes que envuelven elementos nativos aceptan y reenvían sus props (`ComponentProps<'button'>`), así sustituyen al nativo sin sorpresas.
- **I (segregación de interfaces):** props mínimas y específicas; nunca se pasa un objeto entero del API a un componente que usa dos campos.
- **D (inversión de dependencias):** la UI no conoce `fetch`, `openapi-fetch` ni `maplibre-gl`. Dependen de `shared/api` y `shared/ui/base-map`, que son los únicos que importan esas librerías y se pueden sustituir en tests.

**Clean Code:**

- Nombres que dicen qué es (`freeDocks`, `isPanelOpen`), sin abreviaturas ni prefijos húngaros.
- Funciones cortas, un nivel de abstracción, retornos tempranos; sin números mágicos (van a `shared/config`).
- Sin `any`, sin `as` salvo en límites justificados, sin `!` no-null; `strict` y `noUncheckedIndexedAccess` activos.
- Comentarios solo para el porqué; el qué lo cuenta el código. Nada de código comentado.
- Exports con nombre (sin `default`), salvo donde una herramienta lo exige (`vite.config.ts`).
- Sin efectos para estado derivado: se calcula en render.

**Reglas de lint que lo hacen cumplir** (`.oxlintrc.json`, todas `error`; nombres comprobados con oxlint 1.88.0 el 2026-10-13):

| Regla                                                              | Qué impide                                                     |
| ------------------------------------------------------------------ | -------------------------------------------------------------- |
| `typescript/no-explicit-any`                                       | `any`                                                          |
| `typescript/no-non-null-assertion`                                 | `!` no-null                                                    |
| `typescript/consistent-type-imports`                               | imports de tipos sin `type` (va con `verbatimModuleSyntax`)    |
| `import/no-default-export`                                         | exports por defecto (excepto `vite.config.ts`, con `override`) |
| `react-hooks/rules-of-hooks`, `react-hooks/exhaustive-deps`        | hooks condicionales y dependencias incompletas                 |
| `eqeqeq`, `no-console`                                             | `==` y `console.*` olvidados                                   |
| `max-params` (3), `max-depth` (3), `complexity` (10)               | funciones con demasiados parámetros, anidación o ramas         |
| `max-lines-per-function` (50, sin líneas en blanco ni comentarios) | componentes y funciones largos; desactivada en `*.test.ts(x)`  |

Además, las categorías `correctness` y `suspicious` de oxlint activas, y los plugins `jsx-a11y` y `vitest` con sus reglas recomendadas. Lo que no cubre una regla (nombres, comentarios, SOLID) se revisa en el PR.

## Testing Strategy

| Nivel       | Herramientas                      | Qué cubre                                                                                                                            |
| ----------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Unidad      | Vitest                            | `formatRelative` (segundos, minutos, límites), `env` (sin valor → `/api`, `/` final quitada, URL válida, valor inválido lanza error) |
| Componentes | Vitest + Testing Library + jsdom  | `BottomSheet` (abrir/cerrar, `aria-expanded`, teclado), `StateMessage`, `RelativeTime`                                               |
| Layout      | Testing Library                   | `AppLayout` renderiza el panel y el mapa; `MapPage` muestra el estado vacío y la atribución                                          |
| API         | Vitest + MSW (`setupServer`)      | `shared/api` llama a `/v1/bicimad/stations` con la `baseUrl` configurada y devuelve datos tipados                                    |
| Mapa        | Vitest con `maplibre-gl` simulado | `BaseMap` crea el mapa con centro, zoom, estilo y `AttributionControl`; lo destruye al desmontar                                     |
| Manual      | navegador                         | 360 / 768 / 1280 px; `dev` contra `bootRun` y `dev:mock` sin backend (anotado en el PR)                                              |

- `openapi-fetch` guarda el `fetch` global al crear el cliente, antes de que MSW lo intercepte. Por eso `client.ts` pasa `fetch: (request) => globalThis.fetch(request)`, que lo busca en cada llamada; sin eso las peticiones de los tests no llegan a MSW.
- jsdom no tiene WebGL: `maplibre-gl` se simula en los tests de `BaseMap` y el resto de tests simulan `BaseMap` entero.
- Cobertura v8 ≥ 80 % de líneas en `src/shared` y `src/widgets`; `src/app/main.tsx` y `shared/api/mocks/browser.ts` excluidos. El umbral va en `test.coverage.thresholds` de `vite.config.ts` (por carpeta), así que `pnpm --filter web test` falla en local y en CI si baja.
- Sin tests end-to-end (como en la idea).

## Boundaries

- **Always:** versiones exactas; importar solo por la API pública de cada slice; `lint`, `format:check`, `typecheck`, `test` y `build` en verde antes del PR; Conventional Commits con scope `web` (o `ci` para workflows, `repo` para tooling de la raíz).
- **Ask first:** cualquier dependencia fuera del Tech Stack (router, librería de bottom sheet, `react-map-gl`, iconos…); desactivar una regla de oxlint; bajar el umbral de cobertura; cambiar las reglas de Prettier en la raíz; activar el lint con tipos de oxlint (`--type-aware`).
- **Never:** `^` o `~` en `web/package.json` o en las `devDependencies` de `api/client`; `workspace:` para el cliente; `fetch` directo o `maplibre-gl` fuera de `shared`; commitear `.env.local`, tokens o `dist/`; ESLint o Prettier dentro de `web/`; `// oxlint-disable` sin motivo escrito al lado.

## Success Criteria

1. `pnpm install --frozen-lockfile` funciona con `NODE_AUTH_TOKEN` en local y en CI, e instala `@jorgetroya80/bicimad-client@0.3.0` desde GitHub Packages.
2. `web/package.json` no tiene ningún `^` ni `~`; las `devDependencies` de `api/client` tampoco. En el lockfile, `web` resuelve `typescript@6.0.3` y `api/client` `typescript@5.9.3`; el job `api` sigue generando y compilando el cliente.
3. `pnpm --filter web lint`, `format:check`, `typecheck`, `test` y `build` pasan en local y en el job `web` de CI.
4. Cobertura ≥ 80 % de líneas en `src/shared` y `src/widgets`.
5. Con `bootRun` y `pnpm --filter web dev` levantados, `curl -s -o /dev/null -w '%{http_code}' localhost:5173/api/v1/bicimad/stations` devuelve `200` (proxy de Vite).
6. Con `pnpm --filter web dev:mock` y sin backend, `await (await fetch('/api/v1/bicimad/stations')).json()` en la consola del navegador devuelve el fixture de `shared/api/mocks`.
7. Tras `pnpm --filter web build`, `dist/` no contiene `mockServiceWorker.js` y `grep -r 'MSW' web/dist` no encuentra nada.
8. A 360 px el mapa ocupa la pantalla y el bottom sheet se abre y cierra con el botón y con el teclado; a 768 y 1280 px el panel lateral está fijo y el mapa ocupa el resto.
9. El mapa muestra Madrid centrado en Puerta del Sol, con la atribución de OpenStreetMap/OpenFreeMap y la de EMT visibles.
10. Un commit que toca `web/**/*.ts` pasa por `oxlint --fix` y `oxfmt` en pre-commit; un push que toca `web/` ejecuta `typecheck` de `web`.
11. Los jobs `api` y `tooling` siguen en verde con el token añadido.
12. En la siguiente release de `web`, `publish-web` adjunta `web-dist.tar.gz` a la GitHub Release. Se confirma después del merge, en la primera release; no bloquea el PR.
13. El JavaScript de `dist/` suma ≤ 300 KB gzip (tamaños de la salida de `vite build`, anotados en el PR). Referencia: un prototipo con React, TanStack Query, `openapi-fetch` y MapLibre dio 81 KB gzip el 2026-10-13.

## Open Questions

- Ninguna.

## Changelog

- 2026-10-13: versión inicial. Decisiones del usuario: lint con oxlint, formato con oxfmt, arquitectura FSD, versiones fijadas, Clean Code y SOLID en todas las features.
- 2026-10-13: sin Steiger (las reglas FSD van con `no-restricted-imports` de oxlint); TypeScript 6.0.3; Dependabot se gestiona desde GitHub; Prettier sigue fuera de `web/`; se fijan también las `devDependencies` de `api/client`.
- 2026-10-13: `api/client` también usa TypeScript 6.0.3, la misma versión que `web`; se acepta el peer `^5.x` de `openapi-typescript`.
- 2026-10-13: se revierte el cambio anterior. `api/client` sigue en TypeScript 5.9.3 (peer `^5.x` de `openapi-typescript`) y `web` usa 6.0.3.
- 2026-10-13: hallazgos de una prueba del stack en un proyecto mínimo: `skipLibCheck` por el conflicto de tipos entre jest-dom 7 y Vitest 5; `onUnhandledFrame` en MSW 3; `geojson` en `types`; `fetch` diferido en el cliente para MSW.
- 2026-10-13: spec aprobada.
- 2026-10-13: revisión con la skill de spec; vuelve a **in review**. Se añaden a Tech Stack `@testing-library/dom`, `@types/react`, `@types/react-dom` y `@types/geojson`; SC6 y SC7 pasan a comprobaciones concretas (`curl` al proxy, `fetch` desde la consola); MSW queda fuera del build de producción (import dinámico en modo `mock`, worker servido por Vite sin estar en `public/`; nuevo SC8); la verificación de las reglas FSD (SC4) es manual y se anota en el PR.
- 2026-10-13: sin lint de FSD. Se quitan las reglas `no-restricted-imports` por capa y su criterio de éxito; las reglas FSD se revisan en cada PR.
- 2026-10-13: hallazgos medios de la revisión. Reglas de oxlint que hacen cumplir Clean Code (lista en Code Style); umbral de cobertura en `test.coverage.thresholds`; comportamiento de `env.ts` con valores inválidos; `lint-staged` ejecuta oxlint y oxfmt dentro de `web/`.
- 2026-10-13: hallazgos menores de la revisión. `h-dvh` en el layout móvil; `test/` y `vite.config.ts` en el `include` del `tsconfig`; presupuesto de 300 KB gzip de JS (SC13); SC12 se confirma tras la primera release.
- 2026-10-13: spec aprobada tras la revisión.
