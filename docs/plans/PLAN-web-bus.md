# Plan: web-bus

- Created: 2026-10-30
- Status: **in review** (2026-10-30)
- Spec: [SPEC-web-bus.md](../specs/SPEC-web-bus.md)

## Overview

Selector "BiciMAD / Bus", paradas cercanas con icono de bus en el mapa, popup de resumen, lista, detalle con líneas y llegadas, posición del autobús ("Ver en el mapa") y búsqueda de paradas por número. Empieza cuando esté publicada la versión de `@jorgetroya80/mad-transit-client` que saque [PLAN-api-bus.md](PLAN-api-bus.md).

## Decisiones de implementación

- **Rama `feat/web-bus`, un PR y un commit por tarea.** Sin `Co-Authored-By`. El PR solo toca `web/` y `docs/`. Título `feat(web): show EMT bus stops with arrivals`.
- **Subir el cliente es parte de T1**: `web/package.json` y el lockfile, versión exacta. Va en el mismo PR porque solo toca `web/`.
- **Lo de BiciMAD no cambia de comportamiento.** T2 introduce el selector con BiciMAD como valor por defecto, y los tests de BiciMAD que ya existen tienen que pasar sin tocarlos. La única diferencia visible es que el buscador pierde su propio selector (decisión 5).
- **Primero `base-map` (T3) y después los widgets**, porque es la única pieza nueva que toca `maplibre-gl` y su mock.
- **Respetar `Retry-After`**: una función `retry` y una `retryDelay` en las queries de bus que no reintentan un 4xx y esperan `Retry-After` en un `429` o un `503`. Si sirve también para BiciMAD, va en otro PR (`fix(web)`) para no cambiar su comportamiento aquí.

## Grafo de dependencias

```
T1 cliente + MSW + entities/bus-stop
   │
   ├─► T2 selector de servicio + URL ──┬─► T4 mapa con iconos + lista + ?stop ─► T5 detalle ─► T6 llegadas ─┬─► T7 popup
   │                                   │                                                                    └─► T8 autobús en el mapa
T3 base-map: puntos con icono ─────────┘
   │
   └─► (T7 y T8 usan también popup y fitPoints de base-map)
T2 ─► T9 buscador de paradas
T1-T9 ─► T10 cierre
```

## Task List

### Fase 1: Base

- [ ] **T1: Cliente, fixtures de MSW y `entities/bus-stop`** (M, ~1 h 30 min)
  - Descripción: subir `@jorgetroya80/mad-transit-client` a la versión de api-bus. Handlers de MSW y fixtures de `/v1/bus/stops`, `/v1/bus/stops/{number}` (200 y 404) y `/arrivals` (normal, `noEstimate`, vacía y `502`). `entities/bus-stop`: query keys; `useNearbyStops`, `useAllStops`, `useStop` y `useArrivals` (30 s, sin pestaña oculta, con `retry` y `retryDelay` que respetan `Retry-After`); tipos reexportados del cliente y `formatArrival`.
  - Aceptación:
    - `formatArrival`: "Llegando" con 0, "N min" y "Sin estimación" con `noEstimate`.
    - `useArrivals` no reintenta un `404` y espera `Retry-After` en un `503` (test con MSW).
    - `pnpm --filter web typecheck` en verde con el cliente nuevo; no hay tipos del API escritos a mano.
  - Verificación: `pnpm --filter web exec vitest run src/entities/bus-stop`; `pnpm --filter web typecheck`.
  - Archivos: `web/package.json`, `shared/api/mocks/{handlers,fixtures}.ts`, `entities/bus-stop/{api,model,index.ts}`.
  - Dependencias: release del cliente de api-bus.

- [ ] **T2: Selector de servicio y URL** (M, ~1 h 30 min)
  - Descripción: `features/select-service` (`useService` con `navigate` + `replace` y `ServiceToggle`, un grupo de radios nativo). `mapSearch` añade `service` y `stop`; el router añade `stripSearchParams({ service: 'bicimad' })`. Cambiar de servicio quita `?station` o `?stop` y conserva `?need`. `MapPage` elige los widgets según el servicio (con Bus, de momento, un panel vacío "Paradas"). El buscador recibe la fuente activa y `SearchByNumber` no pinta `SourcePicker` con una sola fuente.
  - Aceptación:
    - `/?service=bus&stop=72` se valida; un `service` desconocido vuelve a `bicimad`; `?stop` sin `service=bus` se ignora.
    - Cambiar a Bus y volver deja la URL en `/?need=docks` si `need` era `docks`.
    - Todos los tests de BiciMAD pasan sin cambios, salvo los del `SourcePicker` (decisión 5).
  - Verificación: `pnpm --filter web test`; `pnpm --filter web depcruise`.
  - Archivos: `features/select-service/*`, `pages/map/model/mapSearch.ts` (+ test), `app/router.tsx`, `pages/map/ui/MapPage.tsx`, `widgets/transit-search/ui/TransitSearch.tsx`.
  - Dependencias: T1.

- [ ] **T3: `base-map`: puntos con icono** (M, ~1 h 30 min)
  - Descripción: `MapPoint.icon?: 'bus-stop' | 'bus'`. Los puntos con icono van en una capa `symbol` aparte (`icon-image`, la seleccionada más grande) y los demás siguen como círculos. Los dos SVG se escriben a mano en `base-map` y se registran con `map.addImage` al cargar el estilo (y otra vez si el estilo se recarga).
  - Aceptación:
    - Con puntos `bus-stop`, `setData` llena la fuente de iconos y la de círculos queda vacía (test con el mock de MapLibre).
    - Clic en un icono llama a `onSelectPoint(id)`; el cursor cambia a `pointer`.
    - BiciMAD no cambia: sus tests de `base-map` y `StationsMap` pasan sin tocarlos.
  - Verificación: `pnpm --filter web exec vitest run src/shared/ui/base-map`; `pnpm --filter web depcruise`.
  - Archivos: `shared/ui/base-map/{points.ts,useMapLibre.ts,busIcons.ts,BaseMap.tsx}` (+ tests).
  - Dependencias: ninguna (en paralelo con T1 y T2).

### Checkpoint: Fase 1

- [ ] `pnpm check:task` en verde; cobertura por capa ≥ 80 %.
- [ ] `pnpm --filter web dev:mock`: BiciMAD igual que antes; el selector cambia la URL.

### Fase 2: Paradas

- [ ] **T4: Mapa de paradas, lista y `?stop`** (M, ~2 h)
  - Descripción: `features/select-stop` (`useSelectedStop`, `navigate` + `push`). `widgets/bus-stops-map`: paradas cercanas como `bus-stop`, más la seleccionada si está fuera de los 500 m (con `useStop`); foco en la seleccionada (`useUserFocus`). `widgets/bus-stop-list`: "Paradas cercanas" con `StopRow` (número, nombre, líneas, distancia) y los estados cargando, vacío, error y `stale`.
  - Aceptación:
    - SC1: con Bus, el mapa recibe un punto `bus-stop` por cada parada cercana del fixture.
    - Tocar un icono o una fila escribe `?stop=<número>` y despliega el panel por debajo de `md`.
    - La lista de errores de la spec (5xx, sin red) muestra "No se pudieron cargar las paradas cercanas" + "Reintentar".
  - Verificación: `pnpm --filter web test`; `pnpm --filter web depcruise`.
  - Archivos: `features/select-stop/*`, `widgets/bus-stops-map/*`, `widgets/bus-stop-list/*`, `entities/bus-stop/ui/StopRow.tsx`, `pages/map/ui/MapPage.tsx`.
  - Dependencias: T2, T3.

- [ ] **T5: Detalle de la parada** (M, ~1 h 30 min)
  - Descripción: `widgets/bus-stop-detail`: número, nombre, dirección, distancia (de la lista de cercanas) y una tabla de líneas con cabeceras, horario, frecuencia y la etiqueta "Nocturno" en las `N…`. `404` → "Parada no encontrada" + "Volver a la lista". `StaleNotice` con `stale`. El botón atrás cierra el detalle.
  - Aceptación:
    - SC4: `/?service=bus&stop=72` abre el detalle; atrás vuelve a la lista; `?stop=99999999` muestra "Parada no encontrada".
    - `N1` muestra "23:30–05:30" y "Nocturno" en texto.
    - Una parada fuera de los 500 m no muestra distancia.
  - Verificación: `pnpm --filter web exec vitest run src/widgets/bus-stop-detail`.
  - Archivos: `widgets/bus-stop-detail/*`, `entities/bus-stop/ui/LineBadge.tsx`, `pages/map/ui/MapPanel.tsx`.
  - Dependencias: T4.

- [ ] **T6: Llegadas en el detalle** (M, ~1 h 30 min)
  - Descripción: lista de llegadas con `ArrivalRow` (línea, destino, `formatArrival`), sondeo cada 30 s y todas las filas de la tabla de errores de la spec: vacía, `502`, `503`, y unas llegadas que caducan 2 min después de su `updatedAt` (`useNow`) cuando falla un sondeo. Un error en las llegadas no oculta el resto del detalle.
  - Aceptación:
    - SC3: con temporizadores falsos, una segunda petición a los 30 s; ninguna sin parada abierta.
    - Con el sondeo fallando, las llegadas siguen visibles 2 min y después aparece "Llegadas no disponibles" + "Reintentar".
    - `502` muestra "Llegadas no disponibles" + "Reintentar"; `[]` muestra "No hay autobuses previstos".
  - Verificación: `pnpm --filter web exec vitest run src/widgets/bus-stop-detail src/entities/bus-stop`.
  - Archivos: `widgets/bus-stop-detail/ui/Arrivals.tsx`, `widgets/bus-stop-detail/model/useFreshArrivals.ts`, `entities/bus-stop/ui/ArrivalRow.tsx` (+ tests).
  - Dependencias: T5.

### Checkpoint: Fase 2

- [ ] `pnpm check:task` en verde.
- [ ] `dev:mock`: Bus → iconos → tocar uno → detalle con líneas y llegadas que se renuevan. Revisión humana antes de la fase 3.

### Fase 3: Mapa enriquecido, buscador y cierre

- [ ] **T7: Popup en el mapa** (M, ~1 h 30 min)
  - Descripción: `base-map` añade `popup?: { at, content, onClose }` (`maplibregl.Popup` + portal de React). `bus-stops-map` muestra en el popup de la parada seleccionada su número y nombre, las líneas y las 3 primeras llegadas, con la misma query que el detalle. Se cierra con su botón, con `Escape`, al tocar otro punto y al cerrar el detalle.
  - Aceptación:
    - SC2: tocar un icono abre el popup y el detalle; el popup muestra 3 llegadas como mucho.
    - Abrir el popup no añade peticiones (MSW cuenta una sola petición de llegadas).
    - `Escape` y el botón de cerrar quitan `?stop`.
  - Verificación: `pnpm --filter web exec vitest run src/shared/ui/base-map src/widgets/bus-stops-map`.
  - Archivos: `shared/ui/base-map/{BaseMap.tsx,useMapPopup.ts}`, `widgets/bus-stops-map/ui/{BusStopsMap.tsx,StopPopup.tsx}` (+ tests).
  - Dependencias: T6.

- [ ] **T8: "Ver en el mapa" (posición del autobús)** (M, ~1 h 30 min)
  - Descripción: `base-map` añade `fitPoints`. Botón "Ver en el mapa el autobús de la línea X" en cada llegada con posición. El autobús se pinta como punto `bus`, con la línea como texto, y se encuadra junto a la parada. Se mueve con cada sondeo y se quita al cerrar el detalle, al elegir otra llegada o al caducar las llegadas.
  - Aceptación:
    - SC5: pulsar el botón añade un punto `bus` y llama a `fitPoints` con la parada y el autobús.
    - Sin `busLat` no hay botón; con las llegadas caducadas, el autobús desaparece.
    - Con `prefers-reduced-motion`, `fitBounds` sin animación.
  - Verificación: `pnpm --filter web exec vitest run src/shared/ui/base-map src/widgets/bus-stops-map src/widgets/bus-stop-detail`.
  - Archivos: `shared/ui/base-map/{BaseMap.tsx,useMapLibre.ts}`, `widgets/bus-stops-map/ui/BusStopsMap.tsx`, `widgets/bus-stop-detail/ui/Arrivals.tsx`, `pages/map/ui/MapPage.tsx` (estado del autobús elegido).
  - Dependencias: T6, T7.

- [ ] **T9: Buscar paradas por número** (S, ~45 min)
  - Descripción: `widgets/transit-search/model/useBusSource.ts`: `useAllStops` (solo con Bus elegido) → `SearchSource`. Elegir un resultado escribe `?stop`.
  - Aceptación:
    - SC6: con Bus, escribir `276` lista la 2768 y `Enter` con `2768` abre su detalle.
    - Con BiciMAD no se pide `/v1/bus/stops` (MSW sin peticiones).
  - Verificación: `pnpm --filter web exec vitest run src/widgets/transit-search`.
  - Archivos: `widgets/transit-search/model/useBusSource.ts`, `widgets/transit-search/ui/TransitSearch.tsx` (+ test).
  - Dependencias: T2, T4.

- [ ] **T10: Cierre** (S, ~45 min)
  - Descripción: spec y capability map a **implemented**, changelog del plan y `web/AGENTS.md` si cambia la lista de search params (`?service`, `?stop`). Después del merge y del despliegue, probar en el móvil contra Render (la geolocalización necesita HTTPS).
  - Aceptación:
    - SC7: BiciMAD igual que antes en Render.
    - SC8: `pnpm check:task` en verde.
  - Verificación: `pnpm check:task`; manual en https://mad-transit-web.onrender.com con Bus y BiciMAD.
  - Archivos: `docs/specs/SPEC-web-bus.md`, `docs/specs/CAPABILITY-MAP.md`, `web/AGENTS.md`, este plan.
  - Dependencias: T1-T9.

### Checkpoint final

- [ ] SC1-SC8 comprobados; CI en verde.
- [ ] Pedir permiso antes de push, abrir el PR y hacer merge.

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| El mock de MapLibre no tiene `addImage`, `Popup` ni `fitBounds` | Medio | Ampliar el mock en T3, T7 y T8, en la misma tarea que lo necesita |
| El popup tapa el icono o queda debajo del bottom sheet en el móvil | Medio | `focusPadding` como con `?station`; comprobarlo en el checkpoint de la fase 2 y en Render |
| Los cambios de `MapPage` rompen BiciMAD | Alto | BiciMAD es el servicio por defecto y sus tests no se tocan (T2); SC7 |
| La lista de 4.900 paradas para el buscador tarda en el móvil | Bajo | Solo se pide con Bus en el buscador; gzip y ETag del API; `staleTime` de 24 h |
| La release del cliente de api-bus se retrasa | Medio | T3 no depende del cliente y puede hacerse antes |

## Paralelización

T3 puede hacerse en paralelo con T1 y T2. T9 solo necesita T2 y T4. El resto es secuencial porque comparte `MapPage` y `bus-stops-map`.

## Estimación

Unas 14 h en total. Fase 1 ~4 h 30 min, fase 2 ~5 h y fase 3 ~4 h 30 min.

## Changelog

- 2026-10-30: borrador a partir de la spec aprobada.
- 2026-10-30: se quita `inService`; un `502` de las llegadas se trata como el resto de errores.
