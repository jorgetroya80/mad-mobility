# Plan: web-bicimad

- Created: 2026-10-14
- Status: **implemented** (2026-10-14)
- Spec: [SPEC-web-bicimad.md](../specs/SPEC-web-bicimad.md)

## Overview

Añadir a la SPA de `web-shell` las estaciones de BiciMAD: TanStack Router para `?station` y `?need`, la entidad `station` (modelo, queries y UI), las features (seleccionar, filtrar, favoritos, ubicación y buscador genérico por número), los widgets (mapa, lista, detalle, buscador) y la página que los compone. Todo dentro de `web/`; el API y el cliente no cambian.

## Decisiones de implementación

- **Lista de tareas en este documento.** Las casillas de abajo son la fuente de verdad del progreso.
- **Una rama (`feat/web-bicimad`) y un solo PR, con un commit por tarea.** Squash merge: en `main` queda un commit `feat(web)`. Tipos por tarea: `build(web)` para dependencias y configuración, `feat(web)` para comportamiento, `test(web)` si solo cambian tests, `docs` para el cierre.
- **Solo `web/` y `docs/`.** No hace falta un PR previo para otro componente: el job `release-scope` pasa.
- **Primero lo más arriesgado.** T1 integra TanStack Router con el parser de search params propio: si la URL no queda limpia o los tipos no encajan, cambian las features que leen la URL.
- **De abajo arriba en FSD.** `shared` y `entities` antes que `features`; `features` antes que `widgets`; la página al final. Hasta T15, `MapPage` sigue mostrando el estado vacío de `web-shell`, así que cada tarea deja la app funcionando.
- **TDD en la lógica.** `validateMapSearch`, `availabilityFor`, `parseUpdatedAt`, `matchByNumber`, los hooks de `shared/lib` y las queries se escriben test primero. Los componentes se testean con Testing Library tras escribirlos.
- **Tests con la URL:** helper `renderWithRouter(ui, { initialEntries })` en `web/test/`, con el router real y `createMemoryHistory` (T1).

### Prototipo de TanStack Router (2026-10-14, scratchpad)

Con `@tanstack/react-router` 1.170.41 en Node:

- El parser por defecto convierte `?station=538` en el número `538` y escribe `'538'` como `?station=%22538%22`. **`parseSearchWith((v) => v)` no lo arregla**: sigue devolviendo `538` número.
- Lo que funciona: `parseSearch: (search) => Object.fromEntries(new URLSearchParams(search))` y `stringifySearch: stringifySearchWith(String, (v) => v)`. `?station=538&need=docks` se lee como `{ station: '538', need: 'docks' }` y `buildLocation` escribe `/?station=538`, `/?station=25A&need=docks`.
- `stripSearchParams({ need: 'bikes' })` como middleware de búsqueda de la ruta quita `need=bikes` de la URL (la spec pide que `bikes` no se escriba).
- `validateSearch` convierte `?need=xyz` en `bikes`; `router.state.location.search` guarda el valor crudo, así que los componentes leen siempre de `Route.useSearch()`, nunca de `location`.

La spec (supuesto 12) proponía `parseSearchWith` y `stringifySearchWith`; T1 usa la variante comprobada y la spec se corrige en ese commit.

### Versiones

Comprobada el 2026-10-14 en npm. Las demás no cambian (tabla de [PLAN-web-shell](PLAN-web-shell.md#versiones)).

| Paquete                  | Versión  | Publicada  | Notas                                                                                     |
| ------------------------ | -------- | ---------- | ----------------------------------------------------------------------------------------- |
| `@tanstack/react-router` | 1.170.41 | 2026-09-30 | 1.170.44 es del 2026-10-12. Peers `react`/`react-dom` ≥ 18. Trae `@tanstack/history` y `router-core` |

## Grafo de dependencias

```
T1 TanStack Router + mapSearch + renderWithRouter
 └── T2 entities/station/model (availability, parseUpdatedAt, constantes)
        ├── T3 entities/station/api (3 queries) + mocks
        ├── T7 entities/station/ui (AvailabilityBadge, StationRow)
        └── ...
T4 shared/lib (useGeolocation, useNow, useLocalStorage)        (independiente de T2-T3)
T5 shared/ui/base-map: capa de puntos, selección, focus, ubicación
T6 shared/ui/bottom-sheet controlado + AppLayout
T8 features: select-station, filter-by-need, locate-user        (T1, T2, T4)
T9 features/toggle-favorite                                      (T4)
T10 features/search-by-number (genérico)                         (solo shared)
T11 widgets/station-list                                         (T3, T7, T8, T9)
T12 widgets/station-detail                                       (T3, T7, T8, T9)
T13 widgets/stations-map                                         (T3, T5, T8)
T14 widgets/transit-search                                       (T3, T8, T10)
T15 pages/map: composición + tests de página + cobertura         (T6, T11-T14)
T16 verificación manual + cierre
```

## Task List

### Fase 1: Router y dominio

- [x] **T1: TanStack Router y search params de la página** (M, ~1,5 h)
  - Descripción: añadir `@tanstack/react-router` 1.170.41. `app/router.tsx`: ruta raíz y ruta `/` en código, `validateSearch: validateMapSearch`, middleware `stripSearchParams({ need: 'bikes' })`, `parseSearch`/`stringifySearch` del prototipo, `declare module '@tanstack/react-router' { interface Register { router: typeof router } }`. `App.tsx`: `QueryProvider` + `RouterProvider`. `pages/map/model/mapSearch.ts` con `validateMapSearch(raw) -> { station?: string; need: Need }` (el tipo `Need` vive aquí hasta T2, que lo mueve a `entities/station`). `test/renderWithRouter.tsx`. Corregir el supuesto 12 de la spec.
  - Aceptación:
    - `validateMapSearch`: sin valores → `{ need: 'bikes' }`; `station` numérica o con letras → string; `need` desconocido → `bikes`.
    - Con el router en memoria, `?station=538` llega como `'538'`; navegar a la estación `538` deja `/?station=538`; con `need: 'bikes'` no aparece `need`; con `docks`, `&need=docks`.
    - `MapPage` sigue pintándose igual (test existente en verde con `renderWithRouter`).
  - Verificación: `pnpm --filter web test`, `typecheck`, `build`; anotar el aumento de la carga inicial (gzip) frente a 78,56 KB.
  - Archivos: `web/package.json`, `pnpm-lock.yaml`, `web/src/app/router.tsx`, `web/src/app/App.tsx`, `web/src/pages/map/model/mapSearch.ts` (+ test), `web/test/renderWithRouter.tsx`, spec.
  - Commit: `build(web): add tanstack router with plain-text search params`.

- [x] **T2: Modelo de estación** (S, ~45 min)
  - Descripción: `entities/station/model`: `types.ts` (`Station` del cliente, `Need`, `AvailabilityLevel`), `availabilityFor`, `parseUpdatedAt`. Constantes nuevas en `shared/config`: `NEARBY_RADIUS_M`, `LOW_AVAILABILITY_THRESHOLD`, `NOW_TICK_MS`, `SEARCH_MAX_RESULTS`, `FAVORITES_STORAGE_KEY`. `pages/map` importa `Need` de la entidad.
  - Aceptación: `availabilityFor` cubre `off`, `empty`, `low` (4) y `good` (5) para `bikes` y `docks`; `NO_SERVICE` gana aunque haya bicis. `parseUpdatedAt('nope')` → `undefined`.
  - Verificación: `pnpm --filter web test -- entities/station`.
  - Archivos: `web/src/entities/station/model/*`, `web/src/entities/station/index.ts`, `web/src/shared/config/constants.ts`, `web/src/pages/map/model/mapSearch.ts`.
  - Commit: `feat(web): add station availability model`.

- [x] **T3: Queries de estaciones y mocks** (M, ~1,5 h)
  - Descripción: `entities/station/api/stationQueries.ts`: query keys, `useAllStations`, `useNearbyStations(center, need)` (con `enabled`, `near` redondeado a 4 decimales), `useStation(number)` (sin reintento en 404; error tipado "no encontrada"). Todas con `refetchInterval: POLL_INTERVAL_MS`. Fixtures y handlers de MSW: los 4 niveles, una `NO_SERVICE`, `?near`, `/stations/{number}` (200 y 404) y una variante `stale: true` para tests.
  - Aceptación: las peticiones llevan `near`, `radius=500` y `need`; dos posiciones a < 11 m comparten la misma query key; un 404 hace una sola petición; `stale` y `updatedAt` llegan al consumidor.
  - Verificación: tests con MSW; `dev:mock` sigue respondiendo.
  - Archivos: `web/src/entities/station/api/*`, `web/src/shared/api/mocks/{fixtures,handlers}.ts`, `web/src/entities/station/index.ts`.
  - Commit: `feat(web): add station queries`.

#### Checkpoint 1: router y dominio

`lint`, `typecheck`, `test`, `build` en verde; URL limpia comprobada en tests; revisión del usuario antes de tocar `shared/ui`.

### Fase 2: Piezas compartidas

- [x] **T4: Hooks de `shared/lib`** (M, ~1,5 h)
  - Descripción: `geo/useGeolocation` (`pending | granted | denied | unavailable`, `retry`; `getCurrentPosition` con timeout 10 s y `maximumAge` 60 s), `time/useNow(intervalMs)`, `storage/useLocalStorage(key, parse)` (valor inválido → valor por defecto; `getItem`/`setItem` que lanzan no rompen la app). Sin `console.*`.
  - Aceptación: tests de concedida, denegada, sin `navigator.geolocation` y timeout; `retry` vuelve a pedir; `useNow` avanza con temporizadores falsos; `useLocalStorage` con JSON corrupto y con `setItem` que lanza.
  - Verificación: `pnpm --filter web test -- shared/lib`.
  - Archivos: `web/src/shared/lib/{geo,time,storage}/*`, `web/src/shared/lib/index.ts`.
  - Commit: `feat(web): add geolocation, clock and storage hooks`.

- [x] **T5: Capa de puntos en `BaseMap`** (L, ~2,5 h)
  - Descripción: props `points`, `selectedId`, `onSelectPoint`, `focus`, `userLocation` según la spec. Fuente GeoJSON + capas `circle` (color por `tone`, borde si seleccionado) y `symbol` (`label`); `setData` al cambiar `points`; las capas se añaden tras `load`; `easeTo` con `focus` (`duration: 0` con `prefers-reduced-motion`); cursor `pointer` sobre puntos; punto de usuario en su propia fuente. `LazyBaseMap` reenvía las props. Colores como tokens en `index.css` (leídos con `getComputedStyle` o constantes en `shared/config`, se decide en la tarea y se anota).
  - Aceptación: con `maplibre-gl` simulado, se crean fuente y capas una vez; cambiar `points` llama a `setData` sin recrear el mapa; un clic en la capa llama a `onSelectPoint(id)`; cambiar `focus` llama a `easeTo`.
  - Verificación: tests de `BaseMap`; en `dev:mock`, los 3+ puntos del fixture con color y número (con `points` de prueba temporales en `MapPage`, sin commitear).
  - Archivos: `web/src/shared/ui/base-map/{BaseMap,LazyBaseMap}.tsx` (+ tests), `web/src/shared/ui/base-map/{index,points}.ts`, `web/src/app/styles/index.css`.
  - Commit: `feat(web): draw selectable points on the base map`.

- [x] **T6: `BottomSheet` controlado** (S, ~45 min)
  - Descripción: `expanded` y `onExpandedChange` opcionales en `BottomSheet`; `AppLayout` acepta `panelExpanded` y `onPanelExpandedChange` (opcionales) y los pasa al bottom sheet. Sin cambiar el comportamiento no controlado.
  - Aceptación: tests existentes en verde; en modo controlado el asa llama a `onExpandedChange` y el estado lo decide el padre.
  - Verificación: `pnpm --filter web test -- bottom-sheet app-layout`.
  - Archivos: `web/src/shared/ui/bottom-sheet/BottomSheet.tsx` (+ test), `web/src/widgets/app-layout/ui/AppLayout.tsx` (+ test).
  - Commit: `feat(web): let the page control the bottom sheet`.

- [x] **T7: UI de la estación** (S, ~1 h)
  - Descripción: `AvailabilityBadge({ level, count, need })` con color, número y texto accesible ("3 bicis", "Sin servicio"); `StationRow({ station, need, distanceMeters?, onSelect })` como `button` ≥ 44 px.
  - Aceptación: el nombre accesible de la fila incluye número, nombre, distancia y disponibilidad; el badge nunca depende solo del color.
  - Verificación: tests de componentes.
  - Archivos: `web/src/entities/station/ui/*`, `web/src/entities/station/index.ts`.
  - Commit: `feat(web): add station row and availability badge`.

#### Checkpoint 2: piezas compartidas

Todo en verde; mapa con puntos de prueba revisado en `dev:mock` a 360 y 1280 px; revisión del usuario.

### Fase 3: Features

- [x] **T8: Selección, filtro y ubicación** (M, ~1,5 h)
  - Descripción: `features/select-station`: `useSelectedStation()` → `{ number, select(number), clear() }` con `navigate` (push) y `getRouteApi('/')`. `features/filter-by-need`: `useNeed()` (replace) y `NeedToggle` (grupo de dos botones con `aria-pressed`). `features/locate-user`: `LocateButton({ status, onLocate })`.
  - Aceptación: con `renderWithRouter`, `select('538')` deja `?station=538` y atrás lo quita; `NeedToggle` en "Anclajes" deja `?need=docks` sin nueva entrada en el historial; "Bicis" quita `need`.
  - Verificación: tests con el router en memoria.
  - Archivos: `web/src/features/{select-station,filter-by-need,locate-user}/**`.
  - Commit: `feat(web): add station selection, need filter and locate button`.

- [x] **T9: Favoritos** (S, ~45 min)
  - Descripción: `features/toggle-favorite`: `useFavorites()` sobre `useLocalStorage(FAVORITES_STORAGE_KEY)` (array de strings, sin duplicados) y `FavoriteButton({ number })` con `aria-pressed`.
  - Aceptación: marcar y desmarcar persiste; valor corrupto → sin favoritas y sin errores; dos componentes que usan el hook se mantienen sincronizados.
  - Verificación: tests.
  - Archivos: `web/src/features/toggle-favorite/**`.
  - Commit: `feat(web): add favorite stations`.

- [x] **T10: Buscador genérico por número** (M, ~2 h)
  - Descripción: `features/search-by-number`: tipos `SearchItem` y `SearchSource`, `matchByNumber(items, query, max)` puro y `SearchByNumber({ sources })` (selector solo con ≥ 2 fuentes, combobox ARIA, estados con `itemLabel`). Sin imports de `entities`.
  - Aceptación: `matchByNumber` (prefijo, mayúsculas, espacios, orden `5, 5a, 5b, 10`, exacta primero, máximo 8); con una fuente no hay selector; con dos fuentes simuladas (`bicimad`, `bus`) busca solo en la elegida y conserva el texto al cambiar (SC14); flechas, `Enter` y `Escape`; estados cargando, error y sin resultados.
  - Verificación: tests; `grep -r "entities\|bicimad-client" web/src/features/search-by-number` sin resultados.
  - Archivos: `web/src/features/search-by-number/**`.
  - Commit: `feat(web): add generic search by number`.

#### Checkpoint 3: features

Todo en verde; revisión del usuario de la API de `SearchSource` antes de los widgets.

### Fase 4: Widgets y página

- [x] **T11: Lista de estaciones** (M, ~1,5 h)
  - Descripción: `widgets/station-list`: favoritas (de `useAllStations`, omitiendo las que no existen) y cercanas (`useNearbyStations`), aviso de ubicación, aviso `stale`, "No se pudo actualizar", estados cargando / vacío ("Ninguna estación en 500 m") / error con "Reintentar", "Actualizado hace X" con `useNow`.
  - Aceptación: SC1, SC8 (parte de lista) y SC9 en tests con MSW.
  - Verificación: tests.
  - Archivos: `web/src/widgets/station-list/**`.
  - Commit: `feat(web): add nearby and favorite station list`.

- [x] **T12: Detalle de estación** (M, ~1,5 h)
  - Descripción: `widgets/station-detail`: `useStation(number)`, datos de la spec, `FavoriteButton`, "Volver" (`history.back()` si hay entrada previa de la app; si no, `clear()`), "Estación no encontrada", foco al título al abrir.
  - Aceptación: SC5 (404 sin reintento) y SC6 (volver) en tests.
  - Verificación: tests con `renderWithRouter` y MSW.
  - Archivos: `web/src/widgets/station-detail/**`.
  - Commit: `feat(web): add station detail`.

- [x] **T13: Mapa de estaciones** (S, ~1 h)
  - Descripción: `widgets/stations-map`: `useAllStations` → `MapPoint[]` con `availabilityFor` y `need`; `selectedId` y `focus` desde la estación seleccionada; `userLocation`; `onSelectPoint` → `select`.
  - Aceptación: la conversión a puntos (tono y número según `need`) en un test puro; con `BaseMap` simulado, tocar un punto selecciona la estación.
  - Verificación: tests.
  - Archivos: `web/src/widgets/stations-map/**`.
  - Commit: `feat(web): show stations on the map`.

- [x] **T14: Buscador de transporte** (S, ~45 min)
  - Descripción: `widgets/transit-search`: `useBicimadSource()` (`useAllStations` → `SearchSource` con `itemLabel: 'estación'`, `onSelect` → `select`) y `TransitSearch` con `SearchByNumber sources={[bicimad]}`.
  - Aceptación: SC7 con MSW y router: elegir un resultado deja `?station=<número>`.
  - Verificación: tests.
  - Archivos: `web/src/widgets/transit-search/**`.
  - Commit: `feat(web): search bicimad stations by number`.

- [x] **T15: Página del mapa** (M, ~2 h)
  - Descripción: `MapPage` compone `StationsMap`, `TransitSearch`, `NeedToggle`, `LocateButton`, `StationList` o `StationDetail` según `?station`, y `DataAttribution`. Geolocalización con `useGeolocation`; centro = posición o `MADRID_CENTER`. Bottom sheet controlado: se despliega al seleccionar una estación. Foco a la fila de origen al volver. `test.coverage.thresholds` ≥ 80 % en `src/entities`, `src/features` y `src/pages`. Pendientes de T11: `placeholderData: keepPreviousData` en `useNearbyStations` para que cambiar el filtro o el centro no vuelva a "Cargando…" (decisión del usuario), y devolver el foco a la fila de la sección de origen (una estación favorita y cercana aparece dos veces).
  - Aceptación: tests de página de la spec (`?station=1a`, "Anclajes", atrás, `?need=xyz`, ubicación denegada → aviso y Sol); SC11 en local.
  - Verificación: `pnpm --filter web lint && pnpm --filter web typecheck && pnpm --filter web test && pnpm --filter web build`.
  - Archivos: `web/src/pages/map/ui/MapPage.tsx` (+ test), `web/vite.config.ts`, `web/src/pages/map/index.ts`.
  - Commit: `feat(web): compose the bicimad map page`.

#### Checkpoint 4: app completa

`dev:mock` recorrido entero a 360, 768 y 1280 px; revisión del usuario.

### Fase 5: Cierre

- [x] **T16: Verificación y documentación** (S, ~1 h)
  - Descripción: `dev` contra `bootRun` (677 puntos, polling de 60 s con `304`, pestaña oculta sin peticiones: SC4, SC10); ubicación concedida y denegada en el navegador (SC2); teclado y 44 px (SC13); carga inicial gzip (SC12). `web/README.md` (qué hace la app). Spec y plan a **implemented**, Capability Map actualizado, sección Resultado.
  - Aceptación: SC1-SC14 anotados en el PR.
  - Archivos: `web/README.md`, `docs/specs/SPEC-web-bicimad.md`, `docs/plans/PLAN-web-bicimad.md`, `docs/specs/CAPABILITY-MAP.md`.
  - Commit: `docs: record web-bicimad verification`.

#### Checkpoint final

CI en verde en el PR; revisión del usuario; squash merge con título `feat(web): add bicimad stations`.

## Riesgos y mitigaciones

| Riesgo                                                                                       | Impacto | Mitigación                                                                                                       |
| -------------------------------------------------------------------------------------------- | ------- | ---------------------------------------------------------------------------------------------------------------- |
| La serialización de search params se comporta distinto en el navegador que en Node            | Medio   | T1 lo prueba con el router en memoria y en `dev:mock`; el prototipo ya cubre lectura y escritura                  |
| `navigate` de TanStack Router no hace commit fuera de `RouterProvider` (visto en el prototipo) | Bajo    | Los tests de navegación siempre montan `RouterProvider` con `renderWithRouter`                                    |
| Capas de MapLibre difíciles de testear con el mapa simulado                                   | Medio   | Lógica de puntos en funciones puras (`points.ts`, T13); el mock solo comprueba llamadas a `addSource`/`setData`   |
| El estilo `liberty` no tiene la fuente de glifos que pide la capa `symbol`                    | Medio   | En T5 se usa la `text-font` del propio estilo; si falla, el número va en un círculo más grande sin `symbol` (se pregunta) |
| El router y los widgets nuevos superan 300 KB gzip de carga inicial                           | Bajo    | Hoy 78,56 KB; se mide en T1 y T15                                                                                |
| Geolocalización imposible de probar en jsdom                                                 | Bajo    | `navigator.geolocation` simulado en tests; prueba real manual en T16                                             |
| Foco al volver del detalle se pierde al re-montar la lista                                   | Medio   | El número de la estación de origen se guarda en `MapPage` y la fila recibe el foco tras montar (test en T15)     |

## Paralelización

Un solo desarrollador: orden T1 → T16. Independientes entre sí: T4, T5 y T6 (solo `shared`), y T9 y T10 (no usan el router).

## Estimación

Unas 22 h, en 6-8 sesiones: Fase 1 (~3,75 h), Fase 2 (~5,75 h), Fase 3 (~4,25 h), Fase 4 (~6,75 h), Fase 5 (~1 h). Cada checkpoint es un buen punto para cortar.

## Resultado

- T1-T16 en la rama `feat/web-bicimad`, un commit por tarea más un arreglo tras el checkpoint 4 (`fix(web)`: la estación seleccionada queda visible encima del bottom sheet). 351 tests; cobertura de líneas 99,66 % (umbral 80 % por capa cumplido).
- Verificación manual del 2026-10-14, `pnpm --filter web dev` contra `./gradlew bootRun` con la EMT real, en Chrome headless (CDP):
  - SC1: con ubicación concedida (Sol), "Cerca de ti" lista 13 estaciones en el orden del API (primero las que tienen bicis), con distancia y disponibilidad.
  - SC2: con la ubicación denegada, aviso "Sin ubicación…" y "Cerca de la Puerta del Sol".
  - SC4: el mapa pinta las 678 estaciones del API en una capa (captura); seleccionar abre el detalle (tests).
  - SC10: polling a los 61 s y 121 s (una petición por query); el navegador envía `If-None-Match` y recibe `304` si nada cambió (`200` cuando la EMT actualizó); con la pestaña oculta, 0 peticiones en 70 s; al volver, una por query.
  - SC12: carga inicial 115,89 KB gzip (`index` 112,22 + `config` 3,67); MapLibre sigue en diferido.
  - SC13: a 360 px el orden de tabulación es mapa, atribución, asa, buscador, Bicis, Anclajes, Mi ubicación y las filas, con foco visible; todos los controles miden ≥ 44 px salvo el enlace en línea de la atribución de la EMT (texto en línea, exento en WCAG 2.5.8).
  - SC3, SC5-SC9 y SC14: cubiertos por los tests de página, widgets y features; SC5 también con captura a 360 y 1280 px.
- Pendiente fuera de este módulo: mientras "Mi ubicación" reintenta, el título dice "Cerca de ti" sobre la lista anterior (menor).

## Changelog

- 2026-10-14: versión inicial. Prototipo de TanStack Router 1.170.41: parser por defecto convierte `538` en número; se usa `URLSearchParams` para leer y `stringifySearchWith(String)` para escribir; `stripSearchParams` para omitir `need=bikes`.
- 2026-10-14: plan aprobado.
- 2026-10-14: T1 hecha. `@tanstack/react-router` 1.170.41; `app/router.tsx` con `createAppRouter({ history })`, ruta `/` en código, search en texto plano y `stripSearchParams`; `validateMapSearch` en `pages/map`; `test/renderWithRouter.tsx` monta el router real con `createMemoryHistory`; el test de `MapPage` pasa a usarlo y `test/setup.ts` simula `window.scrollTo` (jsdom no lo implementa y el router lo llama al navegar). Carga inicial: 104,54 KB gzip (`index` 100,96 + `config` 3,58), +25,98 KB frente a 78,56 KB.
- 2026-10-14: T2 hecha. `entities/station` con `types.ts` (`Station` del cliente, `Need`, `AvailabilityLevel`), `availabilityFor` y `parseUpdatedAt` (fecha inválida → `undefined`). `availabilityFor` recibe `Pick<Station, 'status' | 'bikes' | 'freeDocks'>` en vez de `Station` entera: solo usa esos campos y los tests no necesitan fixtures completas. Constantes nuevas en `shared/config` (con test). `Need` se mueve a la entidad; `pages/map` ya no lo reexporta (no tenía importadores).
- 2026-10-14: T3 hecha. `entities/station/api`: `stationKeys` (`all`, `nearby(center, need)`, `detail(number)`), `fetchStations.ts` y `stationQueries.ts`. Los hooks devuelven el `UseQueryResult` con la respuesta del API tal cual (`stations`/`station`, `updatedAt` como string, `stale`): `parseUpdatedAt` lo aplica el consumidor. `near` se formatea con `toFixed(NEAR_COORDINATE_DECIMALS)` y el mismo texto va en la key y en la petición. Sin centro o sin número se usa `skipToken` (equivale a `enabled: false` y tipa bien el `queryFn`). Un 404 lanza `StationNotFoundError` (con `number`); el resto, `Error` con el `Problem` en `cause`. `useStation` no reintenta `StationNotFoundError` y para lo demás usa `QUERY_RETRY_COUNT`, constante nueva en `shared/config` que también usa `QueryProvider` (antes `retry: 1` literal). Fixtures: estaciones con nombre (`1a` bien, `1b` pocas, `2` `NO_SERVICE`, `3` nueva vacía), `staleStationsFixture`, `nearbyStationsFixture` (con `distanceMeters`) y `stationNotFoundProblem`; handlers de `?near` y `/stations/:number` (sin distinguir mayúsculas, 404 `application/problem+json`). El test de petición sin handler de `client.test.ts` pasa a usar `fetch` sobre `/api/v1/unknown`: todas las rutas del cliente tienen ya handler. Peticiones contadas en los tests con `server.events` (`request:start`); `retryDelay: 0` solo en el `QueryClient` de los tests.
- 2026-10-14: T4 hecha. `shared/lib`: `useGeolocation()` → `{ status, position?, retry }` con tipo propio `GeoPosition` (`{ lat, lng }`, `shared` no importa de `entities`); pide con `getCurrentPosition` cada vez que el estado pasa a `pending` (al montar y en `retry`, sin contador de intentos: oxlint rechaza dependencias del efecto que no se usan dentro); sin `navigator.geolocation` empieza y se queda en `unavailable`; `PERMISSION_DENIED` → `denied`, el resto → `unavailable`; resultados tras desmontar se ignoran. `useNow(intervalMs)` con `setInterval` limpiado al desmontar. `useLocalStorage<T>(key, parse, fallback)` → `[value, setValue]`: JSON validado por `parse` (`undefined` = inválido → `fallback`); `useSyncExternalStore` con suscriptores por clave a nivel de módulo y el evento `storage` (otras pestañas); el snapshot es el texto crudo (primitivo, estable) y se decodifica con `useMemo`, así que `parse` y `fallback` deben ser estables (de módulo); si `setItem` lanza, el valor queda en memoria (compartido por clave hasta recargar); si `getItem` lanza, `fallback`. Constantes nuevas en `shared/config` (con test): `GEOLOCATION_TIMEOUT_MS = 10_000`, `GEOLOCATION_MAX_AGE_MS = 60_000`.
- 2026-10-14: T5 hecha. `BaseMap` acepta `points`, `selectedId`, `onSelectPoint`, `focus` y `userLocation` (todas opcionales; `MapPage` no cambia); `LazyBaseMap` reenvía todas las props. Colores: única fuente en tokens de `index.css` dentro de `@theme static` (`--color-availability-{good,low,empty,off}` y su `-foreground`, `--color-map-user-location`); `static` los mantiene en el CSS aunque ninguna clase los use, `BaseMap` los lee con `getComputedStyle` al añadir las capas (las `paint` de MapLibre no leen variables CSS) y T7 usará las clases de Tailwind (`bg-availability-low text-availability-low-foreground`) sin repetir hex. Valores: verde `#15803d`, ámbar `#fbbf24` con texto `#030712`, rojo `#b91c1c`, gris `#4b5563`, texto blanco en los demás (todos ≥ 4,5:1); usuario `#2563eb` con borde blanco. Fuente de la capa `symbol`: `Noto Sans Bold`, una de las tres que usa `liberty` (glifos en `tiles.openfreemap.org/fonts`). Carga: fuentes y capas se añaden en `map.on('load')` y solo entonces el hook devuelve el mapa; los efectos de datos (`setData`) y `easeTo` dependen de ese mapa, así que lo que llega antes de `load` se aplica al cargar. El clic se registra una vez y lee el callback de un ref. Seleccionado: radio 16 en vez de 12, borde oscuro de 3 px y encima de los demás (`circle-sort-key`/`symbol-sort-key`); las etiquetas no se ocultan por colisión. `focus` depende de `lng`/`lat`, no del objeto; `duration: 0` con `REDUCED_MOTION_QUERY` (constante nueva en `shared/config`). Archivos además de los previstos: `mapColors.ts`, `mapLayers.ts` y `useMapLibre.ts` (hooks), para respetar los límites de oxlint. Comprobado en `dev:mock` con puntos temporales (Chrome headless con SwiftShader). Carga inicial: 104,55 KB gzip (`index` 100,94 + `config` 3,61); chunk diferido de MapLibre 276,16 KB.
- 2026-10-14: T6 hecha. `BottomSheet` acepta `expanded` y `onExpandedChange` (patrón controlado/no controlado de React, sin hook aparte): con `expanded` el estado lo decide el padre y el asa solo llama a `onExpandedChange(!expanded)`; sin él sigue el estado interno sembrado con `defaultExpanded`, que también avisa a `onExpandedChange` si se pasa. `AppLayout` acepta `panelExpanded` y `onPanelExpandedChange` (opcionales) y los pasa solo al bottom sheet; el panel lateral desde `md` los ignora. API pública existente sin cambios.
- 2026-10-14: T7 hecha. `entities/station/ui`: `AvailabilityBadge({ level, count, need })` es una píldora con las clases de los tokens de T5 (`bg-availability-* text-availability-*-foreground`, escritas enteras en un mapa para que Tailwind las encuentre); muestra el número (un guion `–` si `off`, oculto a lectores de pantalla) y el texto accesible en `sr-only`, así que nunca depende solo del color. Textos con funciones puras: `availabilityText(level, count, need)` ("1 bici", "3 bicis", "0 bicis", "1 anclaje libre", "4 anclajes libres", "Sin servicio"; plural con `count === 1`, sin `Intl.PluralRules`) y `formatDistance(meters)` con `Intl.NumberFormat('es')` (redondea a metros antes de comparar: "120 m", "999,6 m" → "1 km", "1,2 km"). `StationRow({ station, need, ...props })` en vez de `onSelect`: envuelve un `button` nativo (`min-h-11`, foco visible `sky-700`) y reenvía sus props (`onClick`, `ref` como prop de React 19, `data-*`, `className`), así T11/T15 eligen la estación y devuelven el foco sin API propia. La distancia se lee de `station.distanceMeters` (sin prop aparte): solo la traen las cercanas y las favoritas de `useAllStations` no la tienen, que es justo cuando se omite. `station` es un `Pick` con los campos que usa. Nombre accesible con `aria-label` ("Estación 1a, Puerta del Sol A, a 120 m, 12 bicis"): con el contenido visible saldría "1a Puerta del Sol A 120 m 12 bicis" sin separadores; el texto visible está dentro del nombre (WCAG 2.5.3). `availabilityText` y `formatDistance` no se exportan aún (T12 podrá exportar `formatDistance` si lo necesita). Tests: 163 → 189.
- 2026-10-14: T8 hecha. `createAppRouter({ history, mapComponent })`: el árbol de rutas se crea en una función y el componente de `/` se inyecta (por defecto `MapPage`), sin ramas de test en producción. `renderWithRouter(ui?, { initialEntries })` como dice la spec: con `ui` lo pinta dentro de la ruta `/` real (misma validación, middleware y parse/stringify); sin él pinta la app entera (`MapPage.test` no cambia); `router.test` pasa a usar un contenido mínimo y ya no simula `BaseMap`. `features/select-station`: `useSelectedStation()` → `{ number, select, clear }` con `getRouteApi('/')`; `select` hace push (atrás cierra el detalle, SC6) y `clear` hace **replace**: lo usa "Volver" cuando se llegó por enlace (T12), y con push el botón atrás volvería a abrir la estación recién cerrada. `features/filter-by-need`: `useNeed()` → `[need, setNeed]` con replace y `NeedToggle` como `fieldset` con `legend` "Buscar" oculta (oxlint pide la etiqueta nativa en vez de `role="group"`) y dos botones `aria-pressed` de 44 px. `features/locate-user`: `LocateButton({ status, onLocate, ...props })` reenvía las props del `button`; con `pending` muestra "Localizando…" con `aria-busy` y `aria-disabled` (no `disabled`, que haría perder el foco) e ignora los clics. `station` se quita escribiendo `undefined` (el router no lo serializa). Tests: 189 → 206.
- 2026-10-14: T9 hecha. `features/toggle-favorite`: `useFavorites()` → `{ favorites, isFavorite(number), toggle(number) }` sobre `useLocalStorage(FAVORITES_STORAGE_KEY, parseFavorites, NO_FAVORITES)`, ambos de módulo. `parseFavorites` en su propio archivo: solo acepta un array de strings y quita duplicados conservando la primera posición; cualquier otra cosa → `undefined` (sin favoritas). `toggle` añade al final o quita, sin duplicados. Los números se comparan tal cual (sin pasar a minúsculas): vienen del API (`25A`) y normalizarlos podría dejar de casar con él. `FavoriteButton({ number, ...props })` reenvía las props del `button`; nombre accesible fijo "Favorita" (texto visible) con `aria-pressed`, porque el patrón de botón conmutable de WAI-ARIA pide que la etiqueta no cambie con el estado ("Guardar en…"/"Quitar de…" anunciaría el estado dos veces); estrella `★`/`☆` con `aria-hidden` (`amber-700`, contraste ≥ 3:1), 44 px y foco visible `sky-700`. El filtrado de favoritas que ya no existen queda para T11. Tests: 206 → 227.
- 2026-10-14: T10 hecha. `features/search-by-number`: `SearchSource` gana `itemLabelPlural` ("estaciones", "paradas": el plural en español no se deduce de `itemLabel`), también en la spec. `matchByNumber` puro: quita todos los espacios, ignora mayúsculas, busca por prefijo y ordena como el API (parte numérica y después el sufijo, este sin distinguir mayúsculas; el API sí distingue), con la exacta primero y como mucho `SEARCH_MAX_RESULTS`; `isExactMatch` para `Enter`; no se exporta del slice (sin consumidores fuera). `SearchByNumber({ sources })` con `sources` como tupla no vacía. Sin combobox ARIA (`jsx-a11y/prefer-tag-over-role` rechaza `role="listbox"`/`"option"`; decisión del usuario, ver spec): campo `type="search"` y lista de botones nativos con foco real (`↓` del campo al primero; `↓`/`↑` sin dar la vuelta; `↑` en el primero o `Escape` vuelven al campo conservando el texto; `Enter` en el campo elige la coincidencia exacta). Nombre del botón con separadores `sr-only` ("1a, Puerta del Sol A, dirección"), sin `aria-label`. Al elegir: `onSelect(number)`, se vacía el campo y el foco vuelve a él. La lista se ve siempre que hay texto y coincidencias (sin estado abierto/cerrado). Selector de servicio con radios nativos en un `fieldset` ("Servicio"), solo con ≥ 2 fuentes; el servicio es estado local y cambiarlo conserva el texto. `<output>` (región viva educada) con "3 estaciones" / "1 estación", cargando, error y sin resultados. Tests: 227 → 258.
- 2026-10-14: T11 hecha. `widgets/station-list`: `StationList({ center, isApproximateLocation })` (props mínimas: `center` es `undefined` mientras la ubicación está pendiente y la query de cercanas espera; `isApproximateLocation` decide el aviso "Sin ubicación…" y el título "Cerca de la Puerta del Sol"); `need`, selección y favoritas los lee el propio widget. `useNearbyList(center, need)` traduce la query a una unión `loading` / `error` (con `retry`) / `ready` (`stations` en el orden del API, `updatedAt` ya pasado por `parseUpdatedAt`, `stale`, `refreshFailed`): con datos previos y error se siguen mostrando con "No se pudo actualizar". `useFavoriteStations()` cruza `useFavorites()` con `useAllStations()` mediante `pickFavorites` (puro, orden de inserción, omite números que el API ya no tiene; sin datos de todas, sin sección). Secciones `<section>` con `h2` (`aria-labelledby`) y filas en `<ul>`, cada `StationRow` con `data-station-number` para que T15 devuelva el foco; si una favorita también está cerca aparece dos veces y la primera fila con ese número es la de "Favoritas". Avisos en `<output>` (región viva educada; oxlint pide la etiqueta nativa en vez de `role="status"`). El aviso `stale` reutiliza `RelativeTime`: "Datos de hace 35 minutos: la EMT no responde" (en vez de "X min"); con `updatedAt` inválido, "Datos antiguos: la EMT no responde". "Actualizado hace X" y el aviso usan cada uno su `useNow(NOW_TICK_MS)`, así solo se repinta ese texto. Tests con reloj simulado (`shouldAdvanceTime`): el error tras datos se provoca con el polling (`POLL_INTERVAL_MS`) y los casos de error esperan el reintento de 1 s del `QueryClient` de la app. Tests: 258 → 279.
- 2026-10-14: T12 hecha. `widgets/station-detail`: `StationDetail({ center })` (única prop: el centro de la lista de cercanas; la distancia sale de `useNearbyStations(center, need)` con la misma key, sin petición extra si la lista ya está en caché, y sin `center` no se muestra); número, `need` y `clear` los lee el propio widget, y sin `?station` no pinta nada. `useStationDetail(number)` traduce `useStation` a `loading` / `not-found` / `error` (con `retry`) / `ready` (`station`, `updatedAt` ya pasado por `parseUpdatedAt`, `stale`, `refreshFailed`); `StationNotFoundError` gana a los datos previos (una estación que el API ya no tiene no se queda en pantalla). "Volver" con `useCanGoBack()` del router (`__TSR_index !== 0`: la entrada donde aterrizó el usuario es la 0, así que si hay una anterior es de la app): `router.history.back()` o, si se llegó por enlace, `clear()`; nunca `window.history`. Título `h2` "1a · Puerta del Sol A" ("Estación 1a" mientras carga) con `tabIndex={-1}` que toma el foco al montar; el detalle se re-monta con `key={number}`, así abrir otra estación también le lleva el foco (sin efecto con dependencias que no usa). Datos en `<dl>`: Dirección, Distancia (si está en cercanas), Estado ("En servicio" / "Sin servicio"), Bicis y Anclajes libres con `AvailabilityBadge` (nunca solo color) y Anclajes totales; `FavoriteButton`, "Actualizado hace X", aviso `stale` y "No se pudo actualizar" como en T11. "Estación no encontrada" con "Volver a la lista" (`clear()`, conserva `need`); otros errores, "Reintentar". `PanelNotice`, `UpdatedAt` y `StaleNotice` bajan de `widgets/station-list` a `shared/ui` (`panel-notice`, `data-freshness`, con test propio) porque FSD prohíbe importar entre widgets y no dependen de la entidad; `entities/station` exporta `formatDistance`. Tests: 279 → 296.
- 2026-10-14: T13 hecha. `widgets/stations-map`: `StationsMap({ center, userLocation, className? })` (props mínimas: `center` es `undefined` mientras la ubicación está pendiente y el mapa no se mueve; `need` y selección los lee el propio widget). `toMapPoints(stations, need)` puro: `id` = número, `lng` = `lon`, `tone` por un mapa explícito `AvailabilityLevel` → `MapPointTone` con `satisfies` (mismos nombres hoy, pero un nivel nuevo no compila en vez de llegar al mapa sin estilo). La etiqueta sale de `availabilityLabel({ level, count })`, nueva en `entities/station` y exportada (el número o `–` si `off`): `AvailabilityBadge` la usa en lugar de su `OFF_SYMBOL` privado, así la lista y el mapa comparten la regla. `points` con `useMemo` sobre `data.stations` y `need` (array vacío de módulo mientras carga o falla: el mapa nunca bloquea, la lista muestra los errores), así seleccionar no cambia la referencia y `setData` no se repite. `selectedId` es el número canónico de la estación que casa con `?station` sin distinguir mayúsculas (`?station=1A` → `1a`); si no casa con ninguna (o aún no hay datos), sin selección y `focus` = `center`; con selección, `focus` = sus coordenadas. `onSelectPoint` → `select` (push). Tests con `BaseMap` simulado que guarda sus props y `renderWithRouter` + MSW. Tests: 296 → 315.
- 2026-10-14: T14 hecha. `widgets/transit-search`: `TransitSearch()` (sin props) pinta `SearchByNumber sources={[bicimad]}`; con una sola fuente no hay selector de servicio. `useBicimadSource()` construye la fuente BiciMAD (`id`, etiquetas y textos en constantes con nombre) a partir de `useAllStations()` (`number`, `title` = `name`, `subtitle` = `address`) y `onSelect` = `select` de `useSelectedStation()` (push). `status`: `ready` si hay datos (los de un éxito anterior siguen buscables aunque falle un refresco), `error` si falló sin datos, `loading` en otro caso. `items` y la fuente entera con `useMemo`: el polling con datos iguales no cambia la referencia. Tests con `renderWithRouter` + MSW: etiqueta y sin selector, SC7 con 13 estaciones de prueba (prefijo `1`, orden numérico, como mucho `SEARCH_MAX_RESULTS`), `1A` y ` 1a ` → `1a`, `sol` sin resultados, clic y `Enter` dejan `?station=<número>` y vacían el campo, "Cargando estaciones…" con el handler retenido y error tras el reintento de la app. Tests: 315 → 325.
- 2026-10-14: T15 hecha. `pages/map`: `MapPage` compone `StationsMap`, y en el panel `TransitSearch`, una fila con `NeedToggle` y `LocateButton`, `StationDetail` si hay `?station` (si no, `StationList`) y `DataAttribution` al pie (`MapPanel`). `useMapLocation()` sobre `useGeolocation`: `center` = posición si se concede, `MADRID_CENTER` si se deniega o no hay API, `undefined` mientras está pendiente; `isApproximateLocation` = denegada o no disponible; "Mi ubicación" llama a `retry`. `usePanelExpanded(number)`: el bottom sheet empieza desplegado con `?station` y se despliega cada vez que cambia a una estación (mapa, lista, buscador o enlace); el estado se ajusta durante el render (no en un efecto), así el detalle monta fuera del `inert` y su título toma el foco; el asa sigue plegándolo. Foco al volver (`useListFocusReturn`): un *callback ref* con limpieza (React 19) escucha en el contenedor del panel los clics que suben de las filas (`Enter`/espacio en un botón también son clic; un `onClick` en el `div` lo rechaza `jsx-a11y`) y guarda sección + número (`lib/stationListFocus.ts`); al pasar de detalle a lista enfoca esa fila en su sección o, si no hay origen (mapa, buscador, enlace) o la fila ya no está (p. ej. se quitó de favoritas), el primer título de la lista. Se eligió el título y no el campo de búsqueda porque enfocar un campo abre el teclado en móvil y el título anuncia dónde se está. El origen se olvida tras usarse, así abrir luego la misma estación desde el mapa no devuelve el foco a una fila antigua. `widgets/station-list`: cada `<section>` lleva `data-station-list="favorites" | "nearby"` y su `h2` `tabIndex={-1}` con foco visible (contrato DOM documentado, sin API nueva). Pendiente de T11: `useNearbyStations` con `placeholderData: keepPreviousData`, así cambiar `need` o el centro mantiene las filas anteriores hasta que llegan las nuevas en vez de volver a "Cargando…"; `useNearbyList` no mira `isPlaceholderData`: "Actualizado hace X" y el aviso `stale` describen los datos que se ven, que son reales, y la distancia del detalle puede ser la del centro anterior solo durante esa petición. Cobertura: umbral de 80 % de líneas también en `src/entities`, `src/features` y `src/pages`. Comprobado en `dev:mock` con Chrome headless a 360×740 y 1280×800 (lista y `?station=1a`, ubicación concedida por CDP). Carga inicial: 115,76 KB gzip (`index` 112,09 + `config` 3,67). Tests: 325 → 339.
- 2026-10-14: arreglo tras el checkpoint 4: por debajo de `md`, con el bottom sheet desplegado, la estación seleccionada quedaba centrada en todo el mapa y tapada por el sheet. `BaseMap` acepta `focusPadding?: { bottom }` (px) y lo pasa siempre como `padding` a `easeTo` (MapLibre conserva el último padding); un cambio de padding con el mismo foco vuelve a centrarlo (así sigue visible al desplegar, plegar o girar el móvil, a cambio de recentrar si el usuario había movido el mapa); sigue sin animación con `prefers-reduced-motion`. `StationsMap` reenvía `focusPadding`. `useMapFocusPadding(panelExpanded)` en `pages/map`: solo por debajo de `md` y con el sheet desplegado vale `useBottomSheetOverlap()`, si no 0. `shared/ui/bottom-sheet/sheetSize.ts` es la única fuente del tamaño (60 % del viewport, asa de 44 px): `BottomSheet` pasa a estilos en línea (`height: 60dvh`, `--handle-height: 44px`) porque Tailwind no genera clases a partir de constantes, y `useBottomSheetOverlap()` = `round(innerHeight × 0,6 − 44)` (el asa ya está en el flujo, fuera del mapa). `useViewportHeight()` nuevo en `shared/lib/media` (`resize`, que también salta al girar o al cambiar la barra del navegador). Se descarta medir el sheet: haría falta un `ResizeObserver` y subir la medida desde `BottomSheet` por `AppLayout`, y la transformación del deslizamiento complica la lectura. Carga inicial: 115,89 KB gzip (`index` 112,22 + `config` 3,67). Tests: 339 → 351.
- 2026-10-14: T16 hecha. Verificación contra `bootRun` (ver Resultado); README de `web` actualizado; spec y plan a **implemented**.
- 2026-10-14: ajuste tras la revisión del usuario: `StationSection` recibe `caption` ("Bicis disponibles" / "Anclajes libres", `lib/needCaption.ts`) que se muestra bajo el título y describe la sección (`aria-describedby`). No se añaden tooltips: no se ven en móvil ni con teclado. 353 tests.
