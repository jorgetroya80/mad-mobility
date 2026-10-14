# Spec: web-bicimad

- Created: 2026-10-14
- Status: **approved** (2026-10-14)
- Plan: [PLAN-web-bicimad.md](../plans/PLAN-web-bicimad.md)

Módulo `web-bicimad` del [Capability Map](CAPABILITY-MAP.md). Sobre la SPA de [web-shell](SPEC-web-shell.md) añade las estaciones de BiciMAD: mapa, lista de cercanas, detalle, filtro bicis/anclajes, buscador y favoritos. Consume el API de [api-bicimad](SPEC-api-bicimad.md) con el cliente `@jorgetroya80/bicimad-client`. Fuente: [docs/ideas/bicimad-now.md](../ideas/bicimad-now.md).

## Objective

Que un usuario sepa en menos de 5 segundos si hay bici, o un anclaje libre, cerca de donde está.

- Como usuario con el móvil, quiero ver las estaciones a ≤ 500 m de mí, ordenadas por cercanía y con las que tienen bicis (o anclajes) primero, para decidir a cuál ir sin mirar el mapa entero.
- Como usuario, quiero elegir si busco bicis o anclajes con un solo toque, para que el orden, los colores y los números respondan a lo que necesito.
- Como usuario, quiero ver en el mapa todas las estaciones con su disponibilidad por color y por número, para orientarme también fuera de los 500 m (y sin depender solo del color).
- Como usuario, quiero tocar una estación (en el mapa, la lista o el buscador) y ver su detalle: bicis, anclajes libres, estado y hace cuánto se actualizó.
- Como usuario que no da permiso de ubicación, quiero que la app funcione igual desde la Puerta del Sol y poder buscar una estación por su número.
- Como usuario habitual, quiero marcar estaciones como favoritas y verlas siempre arriba de la lista, estén donde estén.
- Como usuario, quiero compartir un enlace a una estación (`?station=538&need=docks`) y que el botón atrás cierre el detalle.
- Como usuario, quiero saber cuándo los datos son antiguos porque la EMT no responde.

**Fuera de alcance:** geocoding de direcciones (buscador de calles); radio configurable; rutas o indicaciones para llegar; notificaciones; PWA o modo offline; tests end-to-end; módulo `bus`; despliegue (`deploy`).

## Decisiones (2026-10-14, del usuario)

1. **Dos queries.** El mapa pide todas las estaciones (`GET /v1/bicimad/stations`, sin `near`). La lista pide las cercanas (`?near=lat,lon&radius=500&need=…`), ordenadas por el backend. La web no calcula distancias ni reordena: reutiliza la lógica del API, y el ETag del API hace que el polling devuelva `304`.
2. **TanStack Router** para el estado de la URL (`?station`, `?need`): search params validados y tipados por ruta. Hay una sola ruta (`/`) por ahora; el módulo `bus` añadirá las suyas.
3. **Buscador genérico por número**, independiente del servicio. Hoy busca estaciones de BiciMAD; más adelante, paradas de autobús. El usuario elige el servicio y busca por número de estación o parada. Cada servicio aporta una *fuente de búsqueda*; el buscador no conoce estaciones ni paradas. Filtra en local sobre los datos ya cargados: sin nombre ni dirección, sin servicio externo ni cupo. Ver [Buscador por número](#buscador-por-número).
4. **Radio fijo de 500 m** (`NEARBY_RADIUS_M`), el valor por defecto del API.

## Assumptions

1. La ubicación se pide una sola vez al cargar (`getCurrentPosition`, timeout 10 s, `maximumAge` 60 s). Si se deniega, falla o tarda, el centro es `MADRID_CENTER` (Puerta del Sol) y se avisa en el panel: "Sin ubicación: mostrando estaciones cerca de la Puerta del Sol". No se sigue la posición (`watchPosition`) y hay un botón "Mi ubicación" para volver a pedirla.
2. La lista se calcula alrededor de la ubicación del usuario (o de Sol), no del centro del mapa: mover el mapa no cambia la lista.
3. El filtro tiene dos valores, `bikes` (por defecto) y `docks`. `bikes` no se escribe en la URL; `docks` sí (`?need=docks`).
4. Disponibilidad para la necesidad elegida (`bikes` o `freeDocks`), con color **y** número:
   - `NO_SERVICE`: gris, "Sin servicio".
   - 0: rojo, "0".
   - 1 a 4 (`LOW_AVAILABILITY_THRESHOLD = 5`): ámbar.
   - 5 o más: verde.

   El campo `occupancy` del API no se usa para el color: mide lo lleno que está el anclaje, no lo que necesita el usuario.
5. El buscador está siempre visible arriba del panel, no solo sin ubicación. Quita espacios, ignora mayúsculas (`25a` encuentra `25A`) y busca el número por prefijo (`1` encuentra `1a`, `10`, `125`…), ordenado como el API (numérico, después alfabético). Muestra como mucho 8 resultados; `Enter` con una coincidencia exacta abre su detalle. Una entrada vacía no muestra resultados.
6. Favoritos: `localStorage`, clave `bicimad-now:favorites`, array de `number` (el identificador público). Si el valor guardado no es válido se ignora (sin errores en consola). Un favorito que ya no existe en el API no se muestra.
7. El detalle usa `GET /v1/bicimad/stations/{number}`: un enlace con un número que no existe muestra "Estación no encontrada" (404 del API) en el panel, con un botón para volver a la lista.
8. `updatedAt` se convierte a `Date` en un solo sitio (`entities/station/model`). Si no es válida se trata como ausente y no se muestra la hora (regla de [web-shell](SPEC-web-shell.md#code-style)).
9. "Actualizado hace X" se refresca cada 30 s con un hook `useNow` (`shared/lib`), sin pedir datos.
10. El cliente sigue en `@jorgetroya80/bicimad-client@0.4.0`; el contrato no cambia.
11. Rutas definidas en código (`createRoute`), sin el plugin de rutas por archivos: con una ruta no compensa una dependencia de build más ni un `routeTree.gen.ts` generado.
12. Search params como texto plano. Por defecto TanStack Router pasa cada valor por `JSON.parse`, así que `?station=538` llegaría como número y al escribir `'538'` la URL quedaría `?station=%22538%22`. `parseSearchWith((v) => v)` no lo evita: el valor sigue llegando como número. El router se crea con `parseSearch: (search) => Object.fromEntries(new URLSearchParams(search))` para leer y `stringifySearch: stringifySearchWith(String, (v) => v)` para escribir, así que los valores son siempre `string` y la URL queda `?station=538&need=docks`. La ruta `/` añade el middleware `stripSearchParams({ need: 'bikes' })` para no escribir el valor por defecto (supuesto 3). Comprobado con 1.170.41 en el plan y en los tests del router.

## Tech Stack

El de [web-shell](SPEC-web-shell.md#tech-stack), más:

| Herramienta              | Línea | Uso                                                       |
| ------------------------ | ----- | --------------------------------------------------------- |
| `@tanstack/react-router` | 1.x   | Ruta `/` y search params `station` y `need` validados     |

Versión exacta en el plan (última estable con más de 10 días, `minimumReleaseAge`). Sin `@tanstack/router-plugin` ni devtools.

## Commands

Los de [web-shell](SPEC-web-shell.md#commands), sin cambios:

```bash
pnpm --filter web dev:mock       # sin backend: fixtures de MSW
pnpm --filter web dev            # contra ./gradlew bootRun
pnpm --filter web lint
pnpm --filter web typecheck
pnpm --filter web test           # vitest run --coverage
pnpm --filter web build
```

## Project Structure

Se añaden las capas `entities` y `features`. Cada slice expone su API pública en `index.ts`; las reglas FSD se revisan en el PR, como en `web-shell`.

```
web/src/
  app/
    router.tsx                      createRouter, ruta raíz y ruta / (validateSearch), parse/stringify
                                      de search en texto plano, declaración de Register para los tipos
    App.tsx                         QueryProvider + RouterProvider
  pages/
    map/
      model/mapSearch.ts            validateMapSearch(raw) -> { station?: string; need: Need }
      ui/MapPage.tsx                compone los widgets; geolocalización
  widgets/
    app-layout/                     sin cambios de API
    stations-map/
      ui/StationsMap.tsx            BaseMap + estaciones (todas) + ubicación del usuario; selección
    station-list/
      ui/StationList.tsx            favoritas + cercanas; estados cargando / vacío / error / stale
    station-detail/
      ui/StationDetail.tsx          detalle de ?station; 404; volver a la lista
    transit-search/
      ui/TransitSearch.tsx          SearchByNumber con las fuentes disponibles (hoy solo BiciMAD)
      model/useBicimadSource.ts     estaciones -> SearchSource; al elegir, selecciona la estación
  features/
    select-station/                 useSelectedStation(): ?station (navigate con push)
    filter-by-need/                 useNeed(): ?need (navigate con replace) + NeedToggle (bicis / anclajes)
    search-by-number/               genérico: SearchByNumber (selector de servicio + combobox), SearchSource,
                                      lib/matchByNumber.ts (puro); no importa de entities
    toggle-favorite/                useFavorites() (localStorage) + FavoriteButton
    locate-user/                    LocateButton ("Mi ubicación")
  entities/
    station/
      api/stationQueries.ts         query keys + useAllStations, useNearbyStations, useStation
      model/availability.ts         availabilityFor(station, need) -> { level, count }
      model/parseUpdatedAt.ts       string -> Date | undefined
      model/types.ts                Station, Need, AvailabilityLevel (reexporta tipos del cliente)
      ui/AvailabilityBadge.tsx      color + número + texto accesible
      ui/StationRow.tsx             fila de lista: número, nombre, distancia, AvailabilityBadge
  shared/
    config/constants.ts             + NEARBY_RADIUS_M = 500, LOW_AVAILABILITY_THRESHOLD = 5,
                                      NOW_TICK_MS = 30_000, SEARCH_MAX_RESULTS = 8, FAVORITES_STORAGE_KEY
    lib/
      geo/useGeolocation.ts         { status: 'pending' | 'granted' | 'denied' | 'unavailable', position, retry }
      time/useNow.ts                Date que se renueva cada N ms
      storage/useLocalStorage.ts    lectura validada + escritura; tolera excepciones
    ui/
      base-map/                     + capa de puntos genérica (ver abajo)
      bottom-sheet/                 + modo controlado (expanded / onExpandedChange)
    api/mocks/                      + handler de /stations/{number} (200 y 404) y de ?near
```

### Cambios en `shared` (API genérica, sin conocer estaciones)

- **`BaseMap`** recibe props opcionales y sigue siendo el único que importa `maplibre-gl`:
  - `points: MapPoint[]` con `{ id, lng, lat, label, tone: 'good' | 'low' | 'empty' | 'off' }`. Se pintan con una fuente GeoJSON y dos capas: `circle` (color por `tone`) y `symbol` (texto `label`, el número). Con 677 puntos una capa rinde mejor que 677 marcadores DOM. Cambiar `points` llama a `setData`, no recrea el mapa.
  - `selectedId` (punto resaltado con borde) y `onSelectPoint(id)` (clic o toque en un punto; el cursor cambia a `pointer`).
  - `focus?: { lng, lat }`: cuando cambia, el mapa hace `easeTo` (sin animación con `prefers-reduced-motion`).
  - `userLocation?: { lng, lat }`: punto azul propio, distinto de las estaciones.
  - `focusPadding?: { bottom }` (px): parte inferior del mapa tapada por algo encima; el `focus` se centra en la zona visible por encima. Si cambia con el mismo `focus`, el mapa vuelve a centrarlo.
  - Los puntos del mapa no son accesibles por teclado: la lista y el buscador son el camino accesible a cada estación (el mapa es un apoyo visual).
- **`BottomSheet`** acepta `expanded` y `onExpandedChange` (controlado) además de `defaultExpanded`. Seleccionar una estación en el mapa por debajo de `md` despliega el panel. `useBottomSheetOverlap()` da los px del contenido de encima que tapa el sheet desplegado (60 % del viewport menos el asa, que ya está en el flujo); su tamaño sale de una sola fuente. `MapPage` lo pasa como `focusPadding` solo por debajo de `md` y con el sheet desplegado, para que la estación seleccionada quede visible encima del sheet (SC5).
- **`AppLayout`** pasa el estado del bottom sheet sin cambiar su API pública: `MapPage` le da `panelExpanded` y `onPanelExpandedChange` (opcionales).

### Datos y polling

| Hook                                  | Petición                                               | Activa si                       |
| ------------------------------------- | ------------------------------------------------------ | ------------------------------- |
| `useAllStations()`                    | `GET /v1/bicimad/stations`                             | siempre                         |
| `useNearbyStations(center, need)`     | `GET /v1/bicimad/stations?near=…&radius=500&need=…`    | la geolocalización ha terminado |
| `useStation(number)`                  | `GET /v1/bicimad/stations/{number}`                    | hay `?station`                  |

- Las tres con `refetchInterval: POLL_INTERVAL_MS`; el resto, defaults del `QueryClient` de `web-shell` (pausa con la pestaña oculta, refetch al volver el foco, `retry: 1`). Un 404 no se reintenta.
- `near` se redondea a 4 decimales (≈ 11 m) en la query key, para que un cambio mínimo de posición no cree una caché nueva.
- Si una petición falla y había datos, se siguen mostrando los anteriores con "No se pudo actualizar" y la hora del último dato; sin datos, `StateMessage` de error con botón "Reintentar".
- Con `stale: true`: aviso "Datos de hace X min: la EMT no responde" arriba del panel.

### Buscador por número

El buscador sirve para cualquier servicio de transporte. Añadir autobuses es añadir una fuente nueva, sin cambiar el buscador (abierto/cerrado).

```ts
// features/search-by-number/model/types.ts
export type SearchItem = {
  number: string;      // lo que teclea el usuario: '25A' (estación), '1234' (parada)
  title: string;       // 'Fuencarral'
  subtitle?: string;   // 'Calle Fuencarral nº 106'
};

export type SearchSource = {
  id: string;          // 'bicimad'; 'bus' en el futuro
  label: string;       // 'BiciMAD'
  itemLabel: string;   // 'estación'; 'parada' en el futuro (placeholder, textos accesibles)
  itemLabelPlural: string; // 'estaciones', 'paradas': el plural en español no se deduce bien
  items: readonly SearchItem[];
  status: 'loading' | 'ready' | 'error';
  onSelect: (number: string) => void;
};
```

- `SearchByNumber({ sources })`:
  - Con una fuente no muestra selector: hoy el usuario ve directamente "Buscar estación por número".
  - Con dos o más muestra un selector de servicio (botones de opción, ≥ 44 px) y busca solo en la fuente elegida. Cambiar de servicio conserva el texto y recalcula los resultados.
  - El servicio elegido es estado local del componente (no va a la URL ni a `localStorage`). La URL lo podrá llevar cuando exista una ruta por servicio.
  - Campo `type="search"` y debajo una lista de botones (uno por resultado, ≥ 44 px, nombre "1a, Puerta del Sol A, dirección"). Las flechas mueven el foco real: `↓` en el campo va al primer resultado, `↓`/`↑` recorren la lista (sin dar la vuelta) y `↑` en el primero, o `Escape` en cualquiera, vuelve al campo sin borrar el texto. `Enter` en el campo elige la coincidencia exacta, si la hay; `Enter` o espacio en un botón lo elige. Al elegir se vacía el campo y el foco vuelve a él. Un `<output>` anuncia el número de resultados ("3 estaciones") y los estados.
  - Estados: fuente cargando ("Cargando estaciones…"), con error ("No se pudieron cargar las estaciones") y sin resultados ("Ninguna estación con el número 99Z"). Los textos usan `itemLabel`.
- `matchByNumber(items, query, max)` es una función pura y genérica sobre `SearchItem`. Quita espacios, ignora mayúsculas, busca por prefijo, ordena como el API (numérico, después alfabético: `5`, `5a`, `5b`, `10`) y devuelve como mucho `max` (`SEARCH_MAX_RESULTS = 8`). Una coincidencia exacta va la primera.
- `features/search-by-number` no importa de `entities` ni de otras `features`. La fuente de BiciMAD se monta en `widgets/transit-search`: `useBicimadSource()` toma `useAllStations()`, convierte cada `Station` en `SearchItem` (`number`, `name`, `address`) y en `onSelect` llama a `useSelectedStation().select(number)`.
- El futuro módulo `bus` solo añadirá `useBusSource()` (de `entities/stop`) a la lista de fuentes de `TransitSearch`.

### Panel (lista y detalle)

Sin `?station`: buscador, `NeedToggle`, aviso de ubicación o de datos antiguos (si aplica), "Favoritas" (si hay), "Cerca de ti" (o "Cerca de la Puerta del Sol") con hasta 500 m y "Actualizado hace X", y `DataAttribution` al pie.

Con `?station`: `StationDetail` sustituye a la lista en el mismo panel (como dice la idea): botón "Volver" (`router.history.back()` si se llegó desde la app; si se llegó por un enlace, navega a `/` conservando `need`), número y nombre, dirección, bicis, anclajes libres y totales, estado, distancia (si está en la lista de cercanas), `FavoriteButton` y "Actualizado hace X". El foco pasa al título del detalle al abrirlo y vuelve a la fila de origen al cerrarlo.

## Code Style

El de [web-shell](SPEC-web-shell.md#code-style) (Clean Code, SOLID, reglas de oxlint), sin reglas nuevas. Ejemplo de la lógica de dominio, pura y testeable sin React:

```ts
// entities/station/model/availability.ts
import { LOW_AVAILABILITY_THRESHOLD } from '@/shared/config';
import type { AvailabilityLevel, Need, Station } from './types';

type Availability = { level: AvailabilityLevel; count: number };

export function availabilityFor(station: Station, need: Need): Availability {
  const count = need === 'bikes' ? station.bikes : station.freeDocks;
  if (station.status === 'NO_SERVICE') return { level: 'off', count };
  if (count === 0) return { level: 'empty', count };
  if (count < LOW_AVAILABILITY_THRESHOLD) return { level: 'low', count };
  return { level: 'good', count };
}
```

- `entities` no sabe de la URL ni de `localStorage`; `features` no importa de otras `features`; `widgets` componen `features` y `entities`.
- `StationsMap` traduce `Station` a `MapPoint` con `availabilityFor`; `shared/ui/base-map` nunca importa tipos del cliente del API.
- Los textos de la UI van en español y en el propio componente (sin i18n).

## Testing Strategy

| Nivel       | Herramientas                     | Qué cubre                                                                                                                                                       |
| ----------- | -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Unidad      | Vitest                           | `availabilityFor` (los 4 niveles, límite 4/5, ambas `need`), `parseUpdatedAt`, `matchByNumber` (mayúsculas, espacios, prefijo, orden, coincidencia exacta primero, máximo 8), `validateMapSearch` (valores ausentes o inválidos), redondeo de `near`        |
| Hooks       | Vitest + `renderHook`            | `useGeolocation` (concedida, denegada, sin API, timeout), `useLocalStorage` / `useFavorites` (valor corrupto, `setItem` que lanza), `useNow` |
| Datos       | Vitest + MSW                     | las tres queries con sus parámetros; 404 del detalle sin reintento; `stale: true`; error con datos previos                                                     |
| Componentes | Testing Library                  | `NeedToggle`, `SearchByNumber` (con 1 fuente sin selector; con 2 fuentes simuladas busca solo en la elegida; teclado con foco real; estados cargando / error / sin resultados), `TransitSearch` (elegir abre el detalle), `FavoriteButton`, `AvailabilityBadge` (texto accesible además del color), `StationList` (estados), `StationDetail` (404, volver, foco) |
| Mapa        | Vitest con `maplibre-gl` simulado | `BaseMap` crea fuente y capas, llama a `setData` al cambiar `points`, emite `onSelectPoint`, hace `easeTo` con `focus`                                         |
| Página      | Testing Library + MSW + router   | `MapPage` con el router real y `createMemoryHistory`: abrir `?station=1a` muestra el detalle; elegir "Anclajes" escribe `?need=docks` y reordena; atrás cierra el detalle; `?need=xyz` cuenta como `bikes` |
| Manual      | navegador                        | 360 / 768 / 1280 px; ubicación concedida y denegada; `dev:mock` y `dev` contra `bootRun` (anotado en el PR)                                                      |

- Los componentes que leen la URL se testean con un helper `renderWithRouter(ui, { initialEntries })` en `test/` (router real con `createMemoryHistory`), no con el router simulado.
- Fixtures nuevas en `shared/api/mocks`: estaciones con los 4 niveles de disponibilidad, una `NO_SERVICE`, una respuesta `stale: true` y la respuesta de `?near`.
- Cobertura v8 ≥ 80 % de líneas también en `src/entities`, `src/features` y `src/pages` (se añaden a `test.coverage.thresholds`).

## Boundaries

- **Always:** lo de [web-shell](SPEC-web-shell.md#boundaries); disponibilidad con color **y** número o texto; estados cargando / vacío / error / stale en cada vista con datos; `?station` y `?need` como única fuente de verdad de selección y filtro.
- **Ask first:** cualquier dependencia nueva además de TanStack Router (plugin de rutas, devtools, geocoder, librería de bottom sheet o de iconos); cambiar el radio o los umbrales de color; pedir la ubicación de forma continua; cambiar la API pública de `AppLayout`.
- **Never:** que `features/search-by-number` conozca estaciones o paradas (solo `SearchItem` y `SearchSource`); leer o escribir la URL fuera del router (`window.location`, `history.pushState`); calcular distancias u orden en el cliente para la lista (lo hace el API); llamar a servicios externos además del API propio y las teselas; guardar en `localStorage` algo que no sean los favoritos; `maplibre-gl` fuera de `shared/ui/base-map`; usar solo el color para indicar disponibilidad.

## Success Criteria

1. Con `dev:mock`, al cargar con ubicación concedida, la lista muestra las estaciones de la respuesta de `?near` en el orden del API, cada una con número, nombre, distancia y disponibilidad (color + número).
2. Con la ubicación denegada, el mapa se centra en la Puerta del Sol, la lista se calcula desde ahí y aparece el aviso "Sin ubicación…"; "Mi ubicación" vuelve a pedir permiso.
3. Elegir "Anclajes" cambia la URL a `?need=docks`, pide `need=docks` al API y cambia el número y el color de la lista y del mapa a anclajes libres; "Bicis" quita `need` de la URL.
4. El mapa pinta todas las estaciones de `GET /v1/bicimad/stations` (677 contra `bootRun`) con una capa, sin marcadores DOM; tocar una abre su detalle y, por debajo de `md`, despliega el bottom sheet.
5. Abrir `/?station=1a` muestra el detalle de esa estación y centra el mapa en ella; abrir una estación con número `538` deja la URL como `?station=538` (sin comillas ni `%22`); `/?station=nope` muestra "Estación no encontrada" sin reintentar la petición.
6. Con el detalle abierto desde la lista, el botón atrás del navegador vuelve a la lista y devuelve el foco a la fila de origen.
7. Buscar `1` lista solo estaciones cuyo número empieza por `1`, en orden numérico y como mucho 8; buscar `1A` o ` 1a ` encuentra `1a`; buscar `sol` no encuentra nada (no busca por nombre); elegir un resultado, o `Enter` con coincidencia exacta, abre su detalle.
8. Marcar una favorita la muestra en "Favoritas" arriba de la lista; persiste al recargar; con un valor corrupto en `bicimad-now:favorites` la app arranca sin favoritas y sin errores.
9. Con una respuesta `stale: true` se ve "Datos de hace X min: la EMT no responde"; si una petición falla con datos previos, se siguen viendo los datos con "No se pudo actualizar".
10. Con la pestaña visible, la red muestra una petición por query cada 60 s (`304` contra `bootRun` si nada cambió); con la pestaña oculta, ninguna.
11. `pnpm --filter web lint`, `typecheck`, `test` y `build` en verde en local y en CI; cobertura ≥ 80 % de líneas en `src/shared`, `src/entities`, `src/features`, `src/widgets` y `src/pages`.
12. JavaScript de la carga inicial ≤ 300 KB gzip (como SC13 de `web-shell`; MapLibre sigue en diferido), anotado en el PR junto con lo que añade TanStack Router.
13. Se prueba a 360, 768 y 1280 px; todos los controles se usan con teclado y miden ≥ 44 px.
14. El buscador es independiente del servicio: `features/search-by-number` no importa nada de `entities` ni tipos del cliente del API (revisado en el PR), y un test con dos fuentes simuladas (`bicimad` y `bus`) muestra el selector y busca solo en la fuente elegida.

## Open Questions

Ninguna. Las supuestas en [Assumptions](#assumptions) (ubicación al cargar, umbral de 5, buscador siempre visible, detalle por `/stations/{number}`, rutas en código, search params como texto) se dan por buenas salvo que se corrijan en la revisión.

## Changelog

- 2026-10-14: versión inicial. Decisiones del usuario: dos queries (todas para el mapa, `near` para la lista), sin router (hook propio sobre la URL), buscador de estaciones en local, radio fijo de 500 m.
- 2026-10-14: TanStack Router en lugar del hook propio sobre la URL (decisión del usuario): rutas en código, search params validados en `pages/map` y serializados como texto plano. El buscador solo busca por número de estación (decisión del usuario).
- 2026-10-14: buscador genérico por número (decisión del usuario): `features/search-by-number` trabaja con fuentes (`SearchSource`) y muestra un selector de servicio cuando hay más de una. Hoy solo existe la fuente de BiciMAD (`widgets/transit-search`); el módulo `bus` añadirá la suya sin cambiar el buscador. Nuevo SC14.
- 2026-10-14: spec aprobada.
- 2026-10-14: supuesto 12 corregido con lo comprobado en T1: `parseSearchWith` no evita la conversión a número; se lee con `URLSearchParams`, se escribe con `stringifySearchWith(String, …)` y `stripSearchParams` quita `need=bikes`.
- 2026-10-14: el buscador deja el combobox ARIA por una lista de botones con navegación por flechas y foco real (decisión del usuario): funciona igual con lectores de pantalla en móvil y escritorio y no necesita desactivar `jsx-a11y/prefer-tag-over-role`.
- 2026-10-14: arreglo tras el checkpoint 4: por debajo de `md` la estación seleccionada quedaba detrás del bottom sheet desplegado. Nueva prop opcional `focusPadding` de `BaseMap` y `useBottomSheetOverlap()` en `shared/ui`; desde `md` no cambia nada.
