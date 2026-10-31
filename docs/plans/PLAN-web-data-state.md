# Plan: web-data-state

- Created: 2026-10-31
- Status: **approved** (2026-10-31)
- Spec: [SPEC-web-data-state.md](../specs/SPEC-web-data-state.md)

## Overview

Dar a cada consulta de estaciones un `status` (`loading`, `ok`, `stale`, `error`, `offline`) calculado por una función pura. Las peticiones tienen un límite de 10 s, refresco cada 30 s y reintentos tras un fallo a los 30, 60 y 120 s. La última respuesta se guarda en `localStorage`, y el nivel `unconfirmed` pinta en gris la insignia y el mapa cuando el dato no está confirmado. Las pantallas actuales cambian sus avisos para leer `status`; no hay componentes nuevos.

## Decisiones de implementación

- **Rama `feat/web-data-state`, un PR y un commit por tarea.** Sin `Co-Authored-By`. El PR toca `web/` y `docs/`. Título `feat(web): keep stations visible when the data cannot be confirmed`: cambia lo que ve el usuario, así que publica una release de la web.
- **Primero lo que puede fallar (T2).** Resuelto en T2: TanStack Query no tiene un contador de fallos seguidos que sobreviva entre refrescos (`fetchFailureCount` vuelve a 0 al empezar cada petición y `errorUpdateCount` nunca se reinicia). `stationQueries.ts` guarda en un `WeakMap` por consulta el `errorUpdateCount` del último reinicio (un acierto o «Reintentar»), y `refetchInterval` calcula la espera con la diferencia. Las consultas usan `retry: 0`.
- **Un hook de estado en `entities/station`.** `useDataStatus(query)` junta `dataStatus` (pura), `useOnline` y `useNow`, y devuelve `status`, `updatedAt` y `retry`. Los widgets solo leen su resultado.
- **`availabilityFor` recibe `status` como argumento obligatorio.** El compilador encuentra todos los llamadores. `toMapPoints` mantiene su `satisfies Record<AvailabilityLevel, MapPointTone>`, así que olvidar el tono `unconfirmed` no compila.
- **Lectura y escritura de `localStorage` sin hook.** `readRaw` y `writeRaw` de `useLocalStorage.ts` se exportan desde `shared/lib/storage` y la caché de estaciones las reutiliza, con el mismo trato de errores (modo privado, cuota).
- **Tests con temporizadores falsos.** Las esperas de 30, 60 y 120 s y el límite de 10 s se prueban con `vi.useFakeTimers({ shouldAdvanceTime: true })` y MSW, sin esperas reales.

## Grafo de dependencias

```
T1 dataStatus + useOnline + useDataStatus
   │
   ├─► T2 límite de 10 s, refresco 30 s, espera creciente
   │      │
   │      └─► T3 caché en localStorage
   │             │
   │             └─► T4 lista, mapa, buscador y favoritas leen status
   │                    │
   │                    └─► T5 detalle lee status (+ estación de la lista en caché)
   │
   └─► T6 nivel unconfirmed en la insignia y el mapa ─► T7 cierre
```

T6 solo depende de T1 y de que los widgets tengan `status` (T4 y T5): va después para pasar el `status` real a `availabilityFor` en lugar de un valor provisional.

## Task List

### Fase 1: Estado y peticiones

- [x] **T1: Función de estado y conexión** (S, ~1 h)
  - Descripción: `DataStatus` en `model/types.ts`. `model/dataStatus.ts` con la función pura de la spec. `STALE_AFTER_MS` (5 min) en `shared/config`. `shared/lib/network/useOnline.ts` lee `onlineManager` de TanStack Query con `useSyncExternalStore`. `model/useDataStatus.ts` recibe `{ data, isError, refetch, dataUpdatedAt }` y la marca `stale` del API, y devuelve `{ status, updatedAt, retry }`.
  - Aceptación:
    - `dataStatus` cubre las cinco filas de la tabla, la prioridad entre estados y el límite de 5 min (justo por debajo y por encima).
    - `useOnline` cambia al emitir `offline` y `online` en `window`.
    - `useDataStatus` pasa de `ok` a `stale` cuando el reloj pasa los 5 min sin datos nuevos.
  - Verificación: `pnpm --filter web exec vitest run src/entities/station/model src/shared/lib/network`.
  - Archivos: `entities/station/model/{types.ts,dataStatus.ts,dataStatus.test.ts,useDataStatus.ts,useDataStatus.test.ts}`, `shared/lib/network/{useOnline.ts,useOnline.test.ts}`, `shared/lib/index.ts`, `shared/config/constants.ts`.
  - Dependencias: ninguna.

- [x] **T2: Límite de 10 s y espera creciente** (M, ~1 h 30 min)
  - Descripción: `REQUEST_TIMEOUT_MS` (10 s), `POLL_INTERVAL_MS` a 30 s y `RETRY_DELAYS_MS` (30, 60 y 120 s) en `shared/config`; se quita `QUERY_RETRY_COUNT`. `fetchStations.ts` combina la señal con `AbortSignal.timeout` mediante `AbortSignal.any`. Las tres consultas usan `retry: 0` (el detalle conserva el no reintento de «no encontrada») y un `refetchInterval` que, tras un fallo, toma el siguiente valor de `RETRY_DELAYS_MS` y, tras un acierto, vuelve a 30 s. `QueryProvider` quita el reintento por defecto.
  - Aceptación:
    - Una respuesta de MSW que tarda más de 10 s cuenta como fallo.
    - Tras fallos seguidos, las peticiones llegan a los 30, 60, 120 y 120 s; tras un acierto, cada 30 s (SC5, SC6).
    - `refetch()` pide en el acto y la siguiente espera vuelve a empezar en 30 s.
    - Con `document.visibilityState` en `hidden` no hay refrescos.
  - Verificación: `pnpm --filter web exec vitest run src/entities/station/api src/app`.
  - Archivos: `shared/config/{constants.ts,index.ts,index.test.ts}`, `entities/station/api/{fetchStations.ts,stationQueries.ts,stationQueries.test.tsx}`, `app/providers/QueryProvider.tsx`.
  - Dependencias: T1.

- [x] **T3: Caché en `localStorage`** (M, ~1 h 15 min)
  - Descripción: exportar `readRaw` y `writeRaw` desde `shared/lib/storage`. `entities/station/api/stationsCache.ts` lee, valida y guarda la lista completa (`bicimad-now:stations`) y la de cercanas de cada modo (`bicimad-now:nearby-bikes` y `bicimad-now:nearby-docks`, con su `near`). Se guarda en el `queryFn` tras cada acierto. `useAllStations` arranca con `initialData` e `initialDataUpdatedAt` y `useNearbyStations` con `placeholderData` del modo cuando no hay datos de su clave. Actualizar la regla de `localStorage` en `web/AGENTS.md`.
  - Aceptación:
    - Tras un acierto, las tres claves tienen la respuesta.
    - Al montar con caché, los datos aparecen sin esperar a la red.
    - Un JSON corrupto o con otra forma se ignora; un `setItem` que lanza no rompe la consulta.
  - Verificación: `pnpm --filter web exec vitest run src/entities/station/api src/shared/lib/storage`.
  - Archivos: `shared/lib/storage/{useLocalStorage.ts,index}`, `shared/lib/index.ts`, `shared/config/constants.ts`, `entities/station/api/{stationsCache.ts,stationsCache.test.ts,stationQueries.ts}`, `web/AGENTS.md`.
  - Dependencias: T2.

### Checkpoint: Fase 1

- [ ] `pnpm check:task` en verde.
- [ ] Revisión con el usuario antes de tocar las pantallas.

### Fase 2: Pantallas

- [x] **T4: Lista, mapa, buscador y favoritas leen `status`** (M, ~1 h 30 min)
  - Descripción: `useNearbyList` devuelve `{ status, stations, updatedAt, retry }` sin `refreshFailed` ni `stale`. `NearbyStations` y `StationList` muestran el mensaje de carga en `loading`, el de error con «Reintentar» en `error` y en `offline` sin datos, `StaleNotice` en `stale` y un `PanelNotice` «Sin conexión» en `offline` con datos. `StaleNotice` pasa a «Sin confirmar» con la hora. `useBicimadSource`, `useFavoriteStations` y `StationsMap` usan `useDataStatus` y exponen o pasan `status`.
  - Aceptación:
    - Con MSW: `ok` y luego fallo, la lista sigue visible con el aviso «Sin confirmar» (SC2).
    - Respuesta con `stale: true`, mismo aviso.
    - Evento `offline`, aviso «Sin conexión» y la lista sigue; `online`, nueva petición y el aviso desaparece (SC4).
    - Ya no aparece «No se pudo actualizar».
  - Verificación: `pnpm --filter web exec vitest run src/widgets/station-list src/widgets/stations-map src/widgets/transit-search src/shared/ui`.
  - Archivos: `widgets/station-list/{model/useNearbyList.ts,model/useFavoriteStations.ts,ui/NearbyStations.tsx,ui/StationList.tsx,ui/StationList.test.tsx}`, `widgets/stations-map/ui/StationsMap.tsx`, `widgets/transit-search/model/useBicimadSource.ts`, `shared/ui/data-freshness/StaleNotice.tsx`.
  - Dependencias: T3.

- [x] **T5: El detalle lee `status`** (S, ~1 h)
  - Descripción: `useStationDetail` devuelve `not-found` o `{ status, station, updatedAt, retry }`. Sin datos propios toma la estación de la lista completa (`useAllStations`) con el `status` de esta. `StationInfo` y `DetailBody` siguen el mismo patrón de avisos que T4.
  - Aceptación:
    - Detalle con fallo tras un acierto: sigue visible con «Sin confirmar».
    - Detalle de una estación que solo está en la caché de la lista completa: se muestra en `stale` u `offline`.
    - «Estación no encontrada» se comporta como hoy.
  - Verificación: `pnpm --filter web exec vitest run src/widgets/station-detail`.
  - Archivos: `widgets/station-detail/{model/useStationDetail.ts,ui/StationInfo.tsx,ui/DetailBody.tsx,ui/StationDetail.test.tsx}`.
  - Dependencias: T4.

### Checkpoint: Fase 2

- [ ] `pnpm check:task` en verde.
- [ ] `dev:mock` con DevTools: «Offline», bloquear la URL del API y recargar sin conexión (los tres recorridos de Commands en la spec).

### Fase 3: Gris y cierre

- [x] **T6: Nivel `unconfirmed`** (M, ~1 h 15 min)
  - Descripción: `AvailabilityLevel` gana `unconfirmed`. `availabilityFor(station, need, status)` lo devuelve en `stale` y `offline`, antes que `off`. `availabilityText` añade «sin confirmar» a la cifra. `AvailabilityBadge` usa `bg-availability-unconfirmed text-availability-unconfirmed-foreground`. `MapPointTone` y `MapColors` añaden `unconfirmed`, leído con `token()`. `StationRow`, `StationFields` y `toMapPoints` pasan el `status` de su widget.
  - Aceptación:
    - `availabilityFor` con 0, 1, 4 y 5 en `ok`, y `unconfirmed` en `stale` y `offline`, también para `NO_SERVICE` (SC1).
    - La insignia en `unconfirmed` muestra la cifra y su texto accesible dice «sin confirmar».
    - `readMapColors()` devuelve el tono `unconfirmed` y `pointLayers()` lo usa.
    - Con datos en `stale`, la lista, el detalle y el mapa salen en gris (SC2, SC3).
  - Verificación: `pnpm --filter web exec vitest run src/entities/station src/shared/ui/base-map src/widgets`.
  - Archivos: `entities/station/{model/types.ts,model/availability.ts,model/availability.test.ts,ui/availabilityText.ts,ui/AvailabilityBadge.tsx,ui/StationRow.tsx}`, `shared/ui/base-map/{points.ts,mapColors.ts,mapColors.test.ts}`, `widgets/stations-map/lib/toMapPoints.ts`, `widgets/station-detail/ui/StationFields.tsx`, `widgets/station-list/ui/StationRows.tsx`.
  - Dependencias: T5.

- [x] **T7: Cierre** (XS, ~30 min)
  - Descripción: buscar restos de `refreshFailed`, `QUERY_RETRY_COUNT` y `isError` fuera de `useDataStatus`. Cobertura y capas. Changelog de este plan.
  - Aceptación: `pnpm check:task` en verde (SC7); ningún resto de los nombres anteriores.
  - Verificación: `pnpm check:task`; `grep -rn "refreshFailed\|QUERY_RETRY_COUNT" web/src` vacío.
  - Archivos: este plan y lo que salga del `grep`.
  - Dependencias: T6.

### Checkpoint final

- [ ] SC1-SC7 comprobados; CI en verde.
- [ ] Pedir permiso antes de push, abrir el PR y hacer merge.
- [ ] Después del despliegue: en Render, modo avión en el móvil y recargar; marcar spec, plan y capability map como **implemented**.

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| TanStack Query no da un contador de fallos que sobreviva entre refrescos | Medio | Va en T2, al principio; alternativa: contador propio por clave de consulta |
| Temporizadores falsos y MSW se bloquean entre sí en los tests | Medio | `shouldAdvanceTime: true` y `advanceTimersByTimeAsync`; ya se usa así en `useNow.test.ts` si aplica |
| `placeholderData` de cercanas con un centro distinto muestra distancias viejas | Bajo | Solo hasta la primera respuesta, y en gris si el dato tiene más de 5 min o hay fallo |
| La lista completa (~600 estaciones) se escribe cada 30 s en `localStorage` | Bajo | Unos 150 KB por escritura; si se nota en el perfil, guardar solo cuando cambia `updatedAt` |
| `AbortSignal.any` no existe en navegadores antiguos (Safari < 17.4) | Bajo | Soporte de navegadores del resto de la app; se apunta en la spec (supuesto 5) |

## Paralelización

T6 podría ir en paralelo con T2-T5 pasando `'ok'` a `availabilityFor`, pero se hace en serie para no dejar valores provisionales.

## Estimación

Unas 8 h en total. Fase 1 ~3 h 45 min, fase 2 ~2 h 30 min y fase 3 ~1 h 45 min.

## Changelog

- 2026-10-31: borrador a partir de la spec aprobada.
- 2026-10-31: aprobado por el usuario.
- 2026-10-31: T1 hecha. `useDataStatus` acepta cualquier consulta cuyo dato traiga `updatedAt` y `stale` (lista, cercanas y detalle) y se exporta desde `entities/station` con su tipo `DataStatusResult`. Si `updatedAt` no se puede leer, la edad sale de `dataUpdatedAt`. Un dato de exactamente 5 min sigue en `ok`; pasa a `stale` al superarlos, en el siguiente tic de `useNow` (30 s). `useOnline` se suscribe a `onlineManager` sin listeners propios. 27 tests nuevos (399 en total) y `check:task` en verde.
- 2026-10-31: T2 hecha. Contador propio de fallos seguidos (ver «Primero lo que puede fallar»): `fetchFailureCount` se reinicia en cada petición y `errorUpdateCount` nunca, así que ninguno sirve solo. Cada espera cuenta desde la última respuesta, porque TanStack reinicia el intervalo en cada cambio de la consulta. «Reintentar» (`refetch`) reinicia la espera en 30 s aunque falle: los hooks devuelven el resultado tras un `Proxy` que solo cambia `refetch`, para no romper el seguimiento de propiedades de TanStack. Una estación no encontrada sigue la misma espera creciente en lugar de preguntarse cada 30 s. `AbortSignal.timeout` no responde a los temporizadores falsos, así que el test del límite de 10 s lo sustituye por una señal propia. El test de detalle que esperaba un reintento inmediato pasa a comprobar uno a los 30 s, y `floor-guard` avisa de 4 aserciones reescritas (valores de configuración y ese test), que se explican en el commit. 406 tests y `check:task` en verde salvo ese aviso.
- 2026-10-31: T3 hecha. Cada clave guarda `{ response, savedAt }` (las cercanas, también `near`), y lo leído se valida campo a campo sin dependencias: cualquier forma inesperada o JSON roto se descarta. `useAllStations` arranca con `initialData` e `initialDataUpdatedAt: savedAt`, así que una caché más vieja que el `staleTime` (30 s) se pide otra vez al montar. Las cercanas usan una función de `placeholderData` por modo, de módulo para que TanStack no relea `localStorage` en cada render: gana la lista anterior del mismo hook y la guardada solo rellena un arranque vacío, también sin centro todavía. El placeholder tiene `dataUpdatedAt` 0, pero `useDataStatus` usa el `updatedAt` del API, que viene en la respuesta guardada. `test/setup.ts` limpia `localStorage` tras cada test, porque ahora cada consulta correcta escribe en él. 434 tests; `check:task` en verde salvo los 4 avisos conocidos de `floor-guard` de T2.
- 2026-10-31: T4 hecha. `useNearbyList` devuelve `{ status, stations, updatedAt, retry }` con `stations` en `undefined` sin datos, para no confundir «sin datos» con una lista vacía dentro del radio; `offline` sin datos se pinta como el error con «Reintentar». `StaleNotice` dice «Sin confirmar: datos de hace …» o solo «Sin confirmar». Con la ubicación pendiente se muestra la lista de cercanas guardada. `useBicimadSource` usa `useDataStatus`: sin datos, `offline` cuenta como error. `useFavoriteStations` y `StationsMap` no cambian: aún no usan `status`, que llega con T6. Con un `updatedAt` ilegible, la lista se fecha con la llegada de la respuesta (decidido en T1), y el placeholder (`dataUpdatedAt` 0) queda sin fecha en lugar de 1970. El `Proxy` de `stationQueries.ts` se queda: sustituirlo obligaría a pasar la clave o el cliente de la consulta a `useDataStatus`. `floor-guard` suma 8 avisos de aserciones reescritas por los textos nuevos, explicados en el commit. 441 tests; el resto de `check:task` en verde.
- 2026-10-31: T5 hecha. `useStationDetail` devuelve `not-found` o `{ status, station, updatedAt, retry }`, con `station` en `undefined` sin datos. «No encontrada» sigue ganando a cualquier dato. Sin datos propios, toma la estación de la lista completa (también la guardada), comparando el número sin distinguir mayúsculas, con el `status` y la hora de esa lista; «Reintentar» siempre pide el detalle. Decisión del usuario: si el dato acaba de llegar, `StaleNotice` dice solo «Sin confirmar», sin «datos de ahora». Para eso `formatRelative` ya no da tiempos futuros (un dato llegado después del último tic de `useNow` es «ahora») y `isJustNow` sale de `shared/lib`. Quedan dos `queryByText('No se pudo actualizar')` en tests que comprueban que el texto no aparece. `floor-guard` suma 2 avisos de aserciones reescritas en el detalle. 452 tests; el resto de `check:task` en verde.
- 2026-10-31: T6 hecha. `availabilityFor(station, need, status)` devuelve `unconfirmed` con `stale` y `offline` antes que `off`, con la cifra; `availabilityLabel` muestra la cifra (el guion queda solo para `off`). El texto accesible añade «, sin confirmar» a la cifra («3 bicis, sin confirmar»), como el resto de partes de la etiqueta de la fila. `StationRow`, `StationRows`, `StationFields` y `toMapPoints` reciben `status` obligatorio: las cercanas usan el de su lista y las favoritas y el mapa el de la lista completa (`useFavoriteStations` pasa a devolver `{ stations, status }`). El punto del mapa en gris usa el borde normal; si se ve poco sobre el mapa, el token `availability-unconfirmed-line` está disponible. Los tests de `MapPage` y `StationsMap` fijan la fecha (en `MapPage` solo `Date`) para que los datos de los fixtures sigan confirmados. `floor-guard` suma 4 avisos por el nuevo argumento obligatorio de `availabilityFor` y `toMapPoints`. 471 tests; el resto de `check:task` en verde.
- 2026-10-31: T7 hecha. Sin restos en el código de producción: ni `refreshFailed`, ni `QUERY_RETRY_COUNT`, ni «No se pudo actualizar», e `isError` solo se lee en `useDataStatus` (el detalle mira `error` solo para «no encontrada»). Arreglo aprobado por el usuario: los handlers de MSW salen de `createHandlers({ updatedAt })`; el worker de `dev:mock` fecha cada respuesta en el momento (segundos enteros, como el API) para que las estaciones no salgan en gris, y los tests siguen con la fecha fija de los fixtures. 474 tests; `pnpm --filter web build` en verde. Todos los pasos de `check:task` pasan menos `floor-guard`, que sale con 1 por 18 aserciones reescritas (4 en T2, 8 en T4, 2 en T5 y 4 en T6), todas por cambios de comportamiento o de texto buscados y explicadas en sus commits; por eso las casillas «`check:task` en verde» de los checkpoints quedan sin marcar. Quedan la revisión manual en `dev:mock`, los SC en Render y el permiso para el push y el PR.
