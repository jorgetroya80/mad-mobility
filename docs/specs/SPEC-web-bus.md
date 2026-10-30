# Spec: web-bus

- Created: 2026-10-30
- Status: **approved** (2026-10-30)
- Plan: [PLAN-web-bus.md](../plans/PLAN-web-bus.md)

Módulo `web-bus` del [Capability Map](CAPABILITY-MAP.md). Sobre la SPA de [web-shell](SPEC-web-shell.md) y junto a [web-bicimad](SPEC-web-bicimad.md), añade las paradas de autobús EMT: icono de bus en el mapa, resumen al tocarlo, lista de cercanas y detalle con llegadas en el panel. Consume [api-bus](SPEC-api-bus.md) con `@jorgetroya80/mad-transit-client`.

## Objective

Que un usuario sepa en menos de 5 segundos qué parada de autobús tiene cerca y cuándo pasa el próximo autobús.

- Como usuario, quiero elegir entre BiciMAD y Bus con un toque, para ver solo lo que me interesa.
- Como usuario, quiero ver en el mapa las paradas a ≤ 500 m de mí con un icono de autobús.
- Como usuario, quiero tocar una parada y ver al momento, junto al icono, su nombre, sus líneas y los próximos autobuses.
- Como usuario, quiero ver en el panel el detalle completo de esa parada: líneas, destinos, horario, frecuencia y llegadas que se actualizan solas.
- Como usuario sin permiso de ubicación, quiero ver las paradas cerca de la Puerta del Sol y poder buscar una parada por su número.
- Como usuario, quiero compartir un enlace a una parada (`?service=bus&stop=72`) y que el botón atrás cierre el detalle.

**Fuera de alcance:** buscar en otra zona moviendo el mapa o tocándolo (decisión 2); favoritos de paradas; recorridos de líneas; seguir un autobús fuera de las llegadas de la parada abierta; notificaciones; cambios en el API.

## Decisiones (2026-10-30, del usuario)

1. **Selector de servicio.** Un selector "BiciMAD / Bus" arriba del panel. El mapa, la lista y el buscador muestran un servicio cada vez. Con ≈ 4.900 paradas, mezclar ambos en el mapa satura.
2. **Ubicación como hoy.** Las paradas se piden alrededor de la ubicación del usuario o de la Puerta del Sol (`useMapLocation`). Mover el mapa no cambia nada.
3. **Resumen en el mapa y detalle en el panel.** Tocar una parada abre un popup sobre el icono con un resumen y, a la vez, el detalle completo en el panel (el mismo patrón que `?station`).
4. **Favoritos de paradas en un spec posterior.**
5. **Un solo selector de servicio.** El buscador sigue al selector de arriba y su `SourcePicker` se oculta.
6. **La lista no muestra llegadas.** Se decidió tras ver los datos reales: a ≤ 500 m hay entre 25 y 41 paradas. Con una llegada por fila serían hasta 41 llamadas cada 30 s por zona, unas 4.900 por hora, frente al cupo de 10.000 al día de las llegadas. Las llegadas salen solo en el popup y en el detalle.
7. **Dónde está el autobús.** Cada llegada del detalle tiene un botón "Ver en el mapa", como el "Ver ubicación" del visor PMV de la EMT. El dato sale de la llamada de llegadas, sin peticiones extra.

## Assumptions

1. **URL.** `?service=bus` (por defecto `bicimad`, que no se escribe, con `stripSearchParams`) y `?stop=72`. Cambiar de servicio usa `navigate` con `replace`, como `?need`. Quita `?station` o `?stop` y conserva `?need`. `?stop` sin `service=bus` se ignora.
2. **Mapa.** Solo las paradas cercanas (`GET /v1/bus/stops?near=…&radius=500`), no las 4.900: es lo que pidió el usuario y evita pintar miles de iconos. Cada parada es un icono de autobús (capa `symbol`), sin color de disponibilidad. La seleccionada se dibuja más grande y con borde. Una parada abierta desde un enlace o desde el buscador, fuera de los 500 m, se añade al mapa con los datos de `useStop`, y el mapa se centra en ella (`useUserFocus`, como con `?station`).
3. **Popup.** Muestra número y nombre, las líneas y las 3 primeras llegadas ("3 → San Amaro · 2 min"). Usa la misma query de llegadas que el detalle (misma clave), así que no hace peticiones extra. Se cierra con su botón, con `Escape`, al tocar otro punto o al cerrar el detalle. No es accesible por teclado desde el mapa, igual que los puntos: el camino accesible es la lista y el buscador, y el panel muestra todo lo del popup. Por debajo de `md`, `focusPadding` deja el icono y el popup encima del bottom sheet.
4. **Lista.** "Paradas cercanas", por distancia (orden del API). Cada fila: número, nombre, líneas y distancia. Estados cargando, vacío ("Ninguna parada a 500 m"), error y `stale`, como en BiciMAD.
5. **Detalle.** Número, nombre, dirección y distancia; llegadas por orden ("Llegando" con 0 min, "Sin estimación" con `noEstimate`); y una tabla de líneas con cabeceras, horario y frecuencia. Las nocturnas (`N…`) muestran su horario tal cual ("23:30–05:30") y una etiqueta "Nocturno" en texto, no solo con color. Un número inexistente muestra "Parada no encontrada". La distancia sale de la lista de cercanas (como `useStationDistance`); fuera de los 500 m no se muestra. Si fallan solo las llegadas (por ejemplo, un `503` porque tienen más de 2 min), el detalle se sigue mostrando con el aviso "Llegadas no disponibles" y un botón para reintentar. El panel se titula "Paradas" con Bus elegido.
6. **Datos y polling.** Las paradas cercanas casi no cambian: `staleTime` de 1 h, sin `refetchInterval`. Las llegadas se piden solo con una parada abierta, cada 30 s (el TTL del API) y no con la pestaña oculta.
7. **Buscador.** `transit-search` añade una fuente `bus` (`useBusSource`) con todas las paradas (`GET /v1/bus/stops` sin `near`). La pide solo cuando el usuario elige Bus, no al cargar la app. `SearchByNumber` recibe la fuente activa según `?service`. Con una sola fuente no pinta `SourcePicker`, así BiciMAD queda igual que hoy, pero sin su selector (decisión 5).
9. **Autobús en el mapa.** "Ver en el mapa" pinta el autobús con un icono distinto del de parada (vehículo en un círculo, con su línea como texto) y encuadra la parada y el autobús juntos. La posición se mueve con cada sondeo de 30 s. Solo se muestra un autobús a la vez, y se quita al cerrar el detalle o al elegir otra llegada. No va en la URL: es un estado de la vista. Una llegada sin posición (`busLat` a `null`) no muestra el botón. El botón dice qué hace con texto accesible: "Ver en el mapa el autobús de la línea 27".
8. **Cliente.** Los tipos y las funciones salen de la versión de `@jorgetroya80/mad-transit-client` que publique api-bus, así que web-bus empieza cuando esa release esté publicada. MSW sirve fixtures con esos tipos para desarrollar y testear sin el API.

### Errores: qué ve el usuario y qué hace la app

La regla general es **mostrar lo último bueno, decir que es antiguo y no inventar**. Unas llegadas viejas engañan más que no tenerlas. Los textos y componentes son los de BiciMAD (`StateMessage`, `StaleNotice`, `PanelNotice`). TanStack Query reintenta una vez (`QUERY_RETRY_COUNT`) los fallos de red y los 5xx, pero nunca un 4xx.

| Lo que responde el API                       | Paradas cercanas y mapa                                                         | Detalle                                                    | Llegadas (detalle y popup)                                                         |
| -------------------------------------------- | ------------------------------------------------------------------------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `200` con `stale: true`                      | Lista y mapa normales + `StaleNotice` ("Datos de hace X min: la EMT no responde") | Igual, con `StaleNotice`                                   | Igual, con "Actualizado hace X"                                                      |
| `200` con `arrivals: []`                     | —                                                                               | —                                                          | "No hay autobuses previstos"                                                       |
| `404`                                        | —                                                                               | "Parada no encontrada" + "Volver a la lista"; sin reintento | No se pide (la parada no existe)                                                   |
| `429`, `502`, `503`, `504` o sin red         | `StateMessage` "No se pudieron cargar las paradas cercanas" + "Reintentar"; el mapa queda sin iconos | "No se pudo cargar la parada" + "Reintentar" | "Llegadas no disponibles" + "Reintentar"; el resto del detalle sigue visible |
| Falla un sondeo con datos ya cargados        | Se mantienen los datos; `StaleNotice` si superan 1 h                            | Se mantienen                                               | Se mantienen como mucho 2 min desde su `updatedAt` (con `useNow`); después, "Llegadas no disponibles" y el autobús desaparece del mapa |
| `400` o `500`                                | Como un `5xx`: es un fallo nuestro, no del usuario                              | Igual                                                      | Igual                                                                              |

- "Reintentar" llama a `refetch()`. El sondeo de 30 s sigue aunque falle, así que las llegadas vuelven solas cuando se recupera la EMT.
- Con `Retry-After` (`429` y cupo agotado) no se reintenta antes de ese tiempo. El sondeo espera hasta entonces.
- Los errores no rompen la página: cada widget pinta su propio estado y BiciMAD no se ve afectado.
- Sin ubicación se usa la Puerta del Sol, como hoy (`useGeolocation`).

## Tech Stack

El de [web-bicimad](SPEC-web-bicimad.md#tech-stack), sin dependencias nuevas. El cliente sube a la versión con las operaciones de bus. El icono es un SVG escrito a mano.

## Commands

```bash
pnpm --filter web add @jorgetroya80/mad-transit-client@<versión de api-bus>   # exacta, sin ^
pnpm --filter web dev:mock       # sin backend: fixtures de bus en MSW
pnpm --filter web dev            # contra ./gradlew bootRun
pnpm --filter web test
pnpm --filter web test:coverage
pnpm --filter web depcruise
pnpm --filter web build
pnpm check:task
```

Manual: `dev:mock`, abrir `/?service=bus&stop=72`, tocar un icono y comprobar el popup y el panel.

## Project Structure

```
web/src/
  app/router.tsx                     + stripSearchParams({ service: 'bicimad' })
  pages/map/
    model/mapSearch.ts               + service, stop
    ui/MapPage.tsx                   compone los widgets de BiciMAD o de Bus según el servicio
  widgets/
    bus-stops-map/                   BaseMap + paradas cercanas + popup de la seleccionada
    bus-stop-list/                   paradas cercanas
    bus-stop-detail/                 detalle de ?stop con llegadas y líneas
    transit-search/model/useBusSource.ts   paradas -> SearchSource
  features/
    select-service/                  useService(): ?service + ServiceToggle (BiciMAD / Bus)
    select-stop/                     useSelectedStop(): ?stop (navigate con push)
  entities/
    bus-stop/
      api/                           query keys + useNearbyStops, useAllStops, useStop, useArrivals
      model/                         tipos (reexporta los del cliente), formatArrival
      ui/                            StopRow, ArrivalRow, LineBadge
  shared/
    ui/base-map/                     + puntos con icono y popup genérico (ver abajo)
    api/mocks/                       + handlers y fixtures de /v1/bus/stops/**
```

### Cambios en `shared/ui/base-map` (genéricos, sin conocer paradas)

- `MapPoint` admite `icon?: 'bus-stop' | 'bus'` (parada o vehículo). Los puntos con icono se pintan en una capa `symbol` aparte; los demás siguen como círculos. El SVG del autobús vive en `base-map` y se registra con `map.addImage` al cargar el estilo.
- `fitPoints?: readonly LngLat[]`: encuadra varios puntos (`fitBounds`, sin animación con `prefers-reduced-motion`). Se usa para la parada y su autobús.
- `popup?: { at: LngLat; content: ReactNode; onClose: () => void }`. Usa `maplibregl.Popup` y pinta `content` con un portal de React, así que el contenido es React normal y testeable. `maplibre-gl` sigue solo en `base-map`.

## Code Style

El de [web-bicimad](SPEC-web-bicimad.md#code-style), sin reglas nuevas. La lógica de presentación va en `entities`, pura y testeable sin React:

```ts
// entities/bus-stop/model/formatArrival.ts
import type { Arrival } from './types';

export function formatArrival({ minutes, noEstimate }: Arrival): string {
  if (noEstimate || minutes === null) return 'Sin estimación';
  if (minutes === 0) return 'Llegando';
  return `${minutes} min`;
}
```

- `BusStopsMap` traduce `BusStop` a `MapPoint` con `icon: 'bus'`; `base-map` nunca importa tipos del cliente.
- Los widgets de bus no importan los de BiciMAD ni al revés; `MapPage` elige cuáles componer.

## Testing Strategy

- Vitest, Testing Library y MSW, como web-bicimad. Consultas por rol y nombre accesible.
- `mapSearch`: valores válidos, desconocidos y por defecto de `service` y `stop`.
- Widgets: lista, detalle (200, 404, `stale`, sin llegadas), popup (contenido, cierre con botón y con `Escape`).
- Llegadas: polling de 30 s con temporizadores falsos; sin polling sin parada abierta; cada fila de la tabla de errores (vacía, 404, 502, 503, sondeo fallido que caduca a los 2 min).
- "Ver en el mapa": pinta el autobús, encuadra la parada y el autobús, no aparece sin posición y se quita al cerrar.
- `base-map`: capa de iconos y popup con el mock de MapLibre que ya existe.
- Cobertura ≥ 80 % por capa; `pnpm --filter web depcruise` sin violaciones (los widgets de bus no importan los de BiciMAD).

## Boundaries

- **Siempre:** el API calcula distancias y orden; textos en español; color nunca solo.
- **Preguntar antes:** añadir dependencias (iconos incluidos: el SVG se escribe a mano); cambiar el comportamiento de BiciMAD.
- **Nunca:** `maplibre-gl` fuera de `base-map`; tipos del API escritos a mano; `window.location`.

## Success Criteria

1. SC1: con Bus elegido, el mapa muestra iconos de autobús en las paradas a ≤ 500 m de la ubicación (o de Sol).
2. SC2: tocar un icono abre el popup con nombre, líneas y próximas llegadas, y el detalle en el panel.
3. SC3: el detalle muestra líneas con horario y frecuencia, y llegadas que se renuevan cada 30 s.
4. SC4: `?service=bus&stop=72` abre el detalle directamente; atrás lo cierra; un número inexistente muestra "Parada no encontrada".
5. SC5: "Ver en el mapa" en una llegada muestra el autobús y la parada juntos; con la EMT caída, las llegadas desaparecen a los 2 min con "Llegadas no disponibles".
6. SC6: el buscador encuentra una parada por su número.
7. SC7: BiciMAD funciona igual que antes con el servicio por defecto.
8. SC8: `pnpm check:task` pasa.

## Open Questions

Ninguna.

## Changelog

- 2026-10-30: borrador con las decisiones 1 a 3 del usuario.
- 2026-10-30: revisión del SPEC (1): añade Tech Stack, Commands y Code Style. Añade la parada seleccionada fuera de los 500 m, el error que afecta solo a las llegadas, el popup sin peticiones extra, la navegación con `replace`, el cliente antes de empezar y la pregunta 3.
- 2026-10-30: revisión del SPEC (2): decisiones 4 a 6 del usuario (favoritos fuera, un solo selector, lista sin llegadas tras ver los datos reales); `noEstimate` con el texto "Sin estimación". Se cierran las preguntas abiertas.
- 2026-10-30: decisión 7 (dónde está el autobús, con "Ver en el mapa"), supuesto 9 y sección "Errores: qué ve el usuario y qué hace la app". `base-map` añade el icono de vehículo y `fitPoints`.
- 2026-10-30: aprobada por el usuario.
- 2026-10-30: se quita `inService` (el API responde `502` estándar); ese caso entra en la fila de errores 5xx.
