# Spec: web-data-state

- Created: 2026-10-31
- Status: **approved** (2026-10-31)
- Plan: [PLAN-web-data-state.md](../plans/PLAN-web-data-state.md)

Módulo `web-data-state` del [Capability Map](CAPABILITY-MAP.md), el segundo del rediseño de la UI. Es la fase 2 («Estado de datos») del documento «Mad Transit — Especificación de UI» (revisión 32), que es privado. Da a cada petición de estaciones un único estado del que se derivan los avisos y los colores, guarda la última respuesta en `localStorage` y reintenta con espera creciente.

## Objective

Que la web siga siendo útil cuando la EMT, el API o la conexión fallan, y que el usuario sepa siempre si lo que ve está confirmado.

- Como usuario, si falla la actualización sigo viendo las estaciones, en gris «Sin confirmar», con la hora del dato.
- Como usuario, si recargo y el servicio no responde, veo la última lista guardada en lugar de una pantalla vacía.
- Como usuario, cuando vuelve la conexión o el servicio, los datos se refrescan solos y vuelven los colores.
- Como desarrollador, cada pantalla lee un `status` (`loading`, `ok`, `stale`, `error`, `offline`) en lugar de combinar `data`, `isError` y `stale` por su cuenta.

**Fuera de alcance:** los componentes nuevos del rediseño (indicador de la cabecera, `StatusBanner`, pantalla de error con cuenta atrás, mapa sustituido por un mensaje sin conexión) y sus textos, que llegan con `web-redesign-desktop`, `web-redesign-mobile` y `web-error-states`; el estado de las llegadas de bus, que llega con `web-bus`; abrir la app sin conexión, que necesita un service worker y llega con `web-offline-shell`.

## Decisiones (2026-10-31, del usuario)

1. **`stale` del API.** Cuando el API responde con `stale: true` (la EMT no responde y el API sirve su propia caché), el estado es `stale`, igual que cuando falla la petición del front: datos en gris y aviso con la hora.
2. **Caché.** Se guardan en `localStorage` la lista completa (mapa, buscador y favoritas) y la última lista de cercanas de cada modo, con las distancias que dio el API.
3. **Tiempos.** Los del documento de diseño: refresco cada 30 s (hoy 60 s), reintentos tras un fallo a los 30, 60 y 120 s (máximo), límite de 10 s por petición, y un dato con más de 5 min pasa a `stale` aunque no haya fallado ninguna petición.
4. **Alcance en la UI.** Modelo de estado, caché, reintentos y nivel `unconfirmed` en la insignia y en el mapa. Las pantallas actuales leen el nuevo estado con sus avisos de hoy; no hay componentes nuevos.

## Modelo de estados

| Estado    | Cuándo                                                                 | Qué ve el usuario (en este módulo)                         |
| --------- | ---------------------------------------------------------------------- | ---------------------------------------------------------- |
| `loading` | Sin datos ni caché, petición en curso                                  | El mensaje de carga de hoy                                 |
| `ok`      | Hay datos de menos de 5 min y la última petición fue bien              | Pantalla normal                                            |
| `stale`   | Hay datos y falló la última petición, el API dijo `stale` o tienen más de 5 min | Datos en gris «Sin confirmar» y el aviso con la hora de hoy |
| `error`   | Falló la petición y no hay datos ni caché                              | El mensaje de error con «Reintentar» de hoy                |
| `offline` | El navegador está sin conexión (con o sin datos)                       | Datos de la caché en gris y un aviso «Sin conexión»        |

Prioridad: `offline` > `stale` / `ok` (si hay datos) > `error` > `loading`. `offline` sin datos se pinta como `error`; el texto propio llega con `web-error-states`.

## Assumptions

1. **Una función pura decide el estado.** `entities/station/model/dataStatus.ts` recibe `{ hasData, failed, apiStale, updatedAt, now, online }` y devuelve el estado. La edad del dato sale de `updatedAt` del API (`parseUpdatedAt`) y, si falta, del momento en que llegó la respuesta.
2. **Las tres consultas.** `useAllStations`, `useNearbyStations` y `useStation` mantienen sus claves. Los modelos de los widgets (`useNearbyList`, `useStationDetail`, `useBicimadSource`, `useFavoriteStations` y `StationsMap`) cambian `refreshFailed` y `stale` por `status`. «Estación no encontrada» sigue siendo un resultado aparte del detalle, no un estado de datos.
3. **Conexión.** TanStack Query ya escucha `online` y `offline` (`onlineManager`) y refresca al volver (`refetchOnReconnect`). Un hook `useOnline` en `shared/lib` lee `onlineManager` con `useSyncExternalStore`; no se añaden listeners propios.
4. **Refresco y reintentos.** `POLL_INTERVAL_MS` pasa a 30 s y solo corre con la pestaña visible (`refetchIntervalInBackground: false`, como hoy). Tras un fallo, el siguiente intento espera 30, 60 y 120 s, y se queda en 120 s hasta un acierto, que vuelve al ritmo normal. Ese ritmo sustituye al reintento inmediato de hoy (`QUERY_RETRY_COUNT`). «Reintentar» pide en el acto y reinicia la espera. Una estación no encontrada no se reintenta, como hoy.
5. **Límite de 10 s.** `fetchStations.ts` combina la señal de TanStack Query con `AbortSignal.timeout(10_000)` mediante `AbortSignal.any` (Chrome 116, Safari 17.4, Firefox 124). Pasado el límite, la petición cuenta como fallida.
6. **Caché en `localStorage`.** Claves con el prefijo de favoritas: `bicimad-now:stations` (lista completa) y `bicimad-now:nearby-bikes` y `bicimad-now:nearby-docks` (última lista de cercanas con su `near`). Se escriben tras cada respuesta correcta. Al arrancar, la lista completa entra como `initialData` con su fecha (`initialDataUpdatedAt`) y las cercanas como `placeholderData` del modo, aunque el centro haya cambiado. Lo leído se valida y, si no tiene la forma esperada, se descarta; un fallo de escritura (modo privado, cuota) no rompe nada. El detalle no se guarda: sin datos propios, toma la estación de la lista completa en caché.
7. **Nivel `unconfirmed`.** `AvailabilityLevel` gana `unconfirmed`. `availabilityFor(station, need, status)` lo devuelve cuando `status` es `stale` u `offline`, antes que `off`, `empty`, `low` y `good`, y mantiene la cifra. La insignia usa los tokens `availability-unconfirmed*`, que ya están en el tema, y su texto para lectores de pantalla añade «sin confirmar». En el mapa, `MapColors` añade los colores de `unconfirmed` y el punto pinta la cifra en gris.
8. **Avisos.** `stale` muestra el `StaleNotice` de hoy. Su texto pasa a decir «Sin confirmar» con la hora, para que valga también cuando la causa es el front. `offline` añade un `PanelNotice` «Sin conexión». El aviso «No se pudo actualizar» desaparece: lo cubre `stale`.
9. **Regla de `web/AGENTS.md`.** «`localStorage` only stores the favorites» pasa a cubrir las favoritas, la caché de estaciones y las preferencias de la UI, como se decidió al aprobar el capability map.

## Tech Stack

El de [web-design-tokens](SPEC-web-design-tokens.md#tech-stack). Sin dependencias nuevas: la persistencia se hace a mano y no con `@tanstack/query-sync-storage-persister`.

## Commands

```bash
pnpm --filter web dev:mock
pnpm --filter web test
pnpm --filter web test:coverage
pnpm --filter web depcruise
pnpm check:task
```

Manual en `dev:mock`, con DevTools:

- «Offline» en Network: aviso «Sin conexión», datos en gris; al volver, se refrescan solos.
- Bloquear la URL del API: tras el fallo, datos en gris con la hora y reintentos a los 30, 60 y 120 s.
- Bloquear la URL de estaciones y recargar: la lista guardada aparece en gris.

## Project Structure

```
web/
  AGENTS.md                                          regla de localStorage
  src/shared/config/                                 POLL_INTERVAL_MS 30 s, REQUEST_TIMEOUT_MS, RETRY_DELAYS_MS, STALE_AFTER_MS, claves de caché
  src/shared/lib/network/useOnline.ts                estado de onlineManager
  src/shared/lib/storage/                            lectura y escritura sin hook, reutilizadas por useLocalStorage
  src/app/providers/QueryProvider.tsx                sin reintento inmediato
  src/entities/station/model/
    dataStatus.ts                                    función pura del estado
    availability.ts                                  + unconfirmed
    types.ts                                         + DataStatus, + unconfirmed
  src/entities/station/api/
    fetchStations.ts                                 límite de 10 s
    stationQueries.ts                                caché, espera creciente
    stationsCache.ts                                 leer, validar y guardar
  src/entities/station/ui/                           insignia y texto de unconfirmed
  src/shared/ui/base-map/                            color de unconfirmed
  src/widgets/*/model/                               status en lugar de refreshFailed y stale
```

## Code Style

El de [web-bicimad](SPEC-web-bicimad.md#code-style). La función de estado es pura y se prueba sin React:

```ts
export type DataStatus = 'loading' | 'ok' | 'stale' | 'error' | 'offline';

export function dataStatus({ hasData, failed, apiStale, updatedAt, now, online }: StatusInput): DataStatus {
  if (!online) return 'offline';
  if (hasData) {
    const tooOld = updatedAt !== undefined && now - updatedAt.getTime() > STALE_AFTER_MS;
    return failed || apiStale || tooOld ? 'stale' : 'ok';
  }
  return failed ? 'error' : 'loading';
}
```

## Testing Strategy

- Vitest, Testing Library y MSW, como hoy, con temporizadores falsos para la espera.
- `dataStatus`: cada fila de la tabla y la prioridad entre estados, incluido el límite de 5 min.
- `availabilityFor`: 0, 1, 4 y 5 con `ok`, y `unconfirmed` con `stale` y `offline` también para `NO_SERVICE`.
- Consultas con MSW (los casos del documento de diseño): `ok` y luego fallo con datos, `stale`; fallo sin caché, `error`; evento `offline`, `offline`; `online`, refresco; respuesta con `stale: true`, `stale`; más de 10 s sin respuesta, fallo; reintentos a los 30, 60 y 120 s y vuelta a 30 s tras un acierto.
- Caché: se escribe tras un acierto, se lee al arrancar, se descarta si está corrupta y un `setItem` que lanza no rompe la consulta.
- Insignia y punto del mapa en gris con la cifra y «sin confirmar» para lectores de pantalla.
- Cobertura ≥ 80 % por capa; `pnpm --filter web depcruise` sin violaciones.

## Boundaries

- **Siempre:** derivar avisos y colores de `status`; validar lo leído de `localStorage`; color nunca solo (cifra y texto en `unconfirmed`).
- **Preguntar antes:** cualquier dependencia; cambiar textos más allá de los de los supuestos 7 y 8; tocar `api/`.
- **Nunca:** guardar en `localStorage` algo distinto de favoritas, caché de estaciones y preferencias; reintentar más rápido que 30 s tras un fallo; vaciar la pantalla si hay datos previos.

## Success Criteria

1. SC1: `dataStatus` y `availabilityFor` cubren la tabla de estados y el nivel `unconfirmed` con tests.
2. SC2: con el API caído y datos previos, la lista, el detalle y el mapa siguen visibles en gris con la hora del dato.
3. SC3: recargar con el API caído muestra la última lista completa y la última de cercanas del modo, en gris.
4. SC4: al volver la conexión, los datos se refrescan sin intervención y vuelven los colores.
5. SC5: tras un fallo, los intentos siguen a los 30, 60 y 120 s; tras un acierto, cada 30 s; con la pestaña oculta, ninguno.
6. SC6: una petición sin respuesta en 10 s cuenta como fallida.
7. SC7: `pnpm check:task` pasa.

## Open Questions

Ninguna.

## Changelog

- 2026-10-31: borrador a partir del documento de diseño (revisión 32), con las decisiones 1 a 4 del usuario.
- 2026-10-31: aprobada por el usuario.
- 2026-10-31: recargar sin conexión no abre la app (no hay service worker), así que la caché no llega a leerse. SC3 pasa a medirse recargando con el API caído; abrir la app sin conexión queda para `web-offline-shell`.
