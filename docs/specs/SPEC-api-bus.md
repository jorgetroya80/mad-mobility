# Spec: api-bus

- Created: 2026-10-30
- Status: **approved** (2026-10-30)
- Plan: [PLAN-api-bus.md](../plans/PLAN-api-bus.md)

Módulo `api-bus` del [Capability Map](CAPABILITY-MAP.md). Añade el módulo de dominio `modules/bus` sobre la infraestructura de [api-core](SPEC-api-core.md), con la misma forma que [api-bicimad](SPEC-api-bicimad.md): paradas de autobús EMT cercanas, su detalle y las próximas llegadas. Lo consume [web-bus](SPEC-web-bus.md). Fuente: [docs/ideas/bicimad-now.md](../ideas/bicimad-now.md) ("Diferencias previstas para el módulo Bus").

## Objective

Que cualquier cliente sepa en una sola petición qué paradas de autobús EMT hay cerca de un punto y, para una parada, qué líneas pasan y cuándo llega el próximo autobús de cada una.

- Como usuario del frontend, quiero las paradas a ≤ 500 m de mí, ordenadas por distancia y con sus líneas, para saber a cuál ir.
- Como usuario, quiero el detalle de una parada por su número (el del poste), con sus líneas, destinos, horario y frecuencia.
- Como usuario, quiero las próximas llegadas de una parada en minutos, por línea y destino, para decidir si espero o camino.
- Como usuario, quiero saber si los datos son antiguos (`stale`) y de cuándo son (`updatedAt`).
- Como mantenedor, quiero que el bus no agote el cupo diario de la EMT que también usa BiciMAD.

**Fuera de alcance:** frontend ([web-bus](SPEC-web-bus.md)); rutas o recorridos de líneas; seguimiento continuo de un autobús fuera de las llegadas de una parada; incidencias; planificador de viajes; cambios en `modules/bicimad`.

## Decisiones (2026-10-30, del usuario)

1. **Detalle con llegadas.** El detalle de una parada incluye información estática y las próximas llegadas en tiempo real.
2. **Lista completa diaria.** El API pide a la EMT la lista de todas las paradas (≈ 4.900) una vez al día y la guarda en caché. La cercanía y el detalle básico se calculan sobre ella, como en BiciMAD. No se llama a `arroundxy` por cada punto.
3. **El punto lo da el cliente.** El API recibe `near` y no sabe si es la ubicación del usuario o la Puerta del Sol.
4. **Horarios de hoy.** El detalle solo devuelve el horario y la frecuencia del tipo de día de hoy en Madrid.
5. **Sin favoritos.** Los favoritos de paradas van en un spec posterior.
6. **Posición del autobús.** Cada llegada incluye dónde está el autobús, como el visor PMV de la EMT (`emtmadrid.es/PMVVisor/pmv.aspx?stopnum=…`). Sale de la misma llamada de llegadas (`Arrive[].geometry`), sin coste de cupo.

## Assumptions

1. Endpoints de la EMT MobilityLabs, verificados con respuestas reales el 2026-10-30 (ver [Hallazgos de la EMT](#hallazgos-de-la-emt)):
   - Lista de paradas: `POST /v1/transport/busemtmad/stops/list/` **sin cuerpo**. Por parada: `node` (número), `name`, `geometry` y `lines` como códigos internos `"<línea>/<sentido>"` (`"603/2"`). No trae dirección ni etiquetas de línea.
   - Catálogo de líneas: `GET /v1/transport/busemtmad/lines/info/{AAAAMMDD}/`. Traduce el código interno a la etiqueta pública (`603` → `M3`, `361` → `001`).
   - Detalle de una parada: `GET /v1/transport/busemtmad/stops/{stopId}/detail/`. Trae `postalAddress` y `dataLine`. Por línea y tipo de día (`dayType`): etiqueta, cabeceras A y B, sentido, hora de inicio y fin, y frecuencia mínima y máxima en minutos.
   - Llegadas: `POST /v2/transport/busemtmad/stops/{stopId}/arrives/` con cuerpo JSON (`cultureInfo`, `Text_EstimationsRequired_YN: "Y"`). `Arrive[]` trae por autobús la etiqueta de la línea, el destino, `estimateArrive` (segundos) y `DistanceBus` (metros).
2. El número de parada de la EMT (`stopId`, el del poste) es estable y es el identificador público: `GET /v1/bus/stops/72`.
3. `EmtHttpClient` solo hace `GET`. Se añade `post(module, path, body, elementType)` con el mismo token, reintento, cupo y manejo de errores. Es un cambio en `shared/emt`, dentro del mismo PR (solo toca `api/`).
4. Las llegadas tienen su propia caché y su propio cupo. `CacheService` y `QuotaTracker` ya se configuran por "módulo" con una cadena, así que se usan cuatro claves: `bus-stops`, `bus-lines`, `bus-stop-detail` y `bus-arrivals`.
5. `estimateArrive = 999999` significa que no hay estimación y se traduce en `minutes = null`, `noEstimate = true`. Nunca llega al cliente un 999999. Valores de más de 20 min son reales (se vio uno de 2.623 s) y se devuelven tal cual.
6. Las paradas sin coordenadas o sin número se descartan con un log `WARN`. Una lista sin paradas válidas es `EmtProtocolError` y sirve la caché `stale`.
7. La lista de paradas en caché decide si una parada existe. Detalle y llegadas de un número que no está en ella dan `404` sin llamar a la EMT, así un número inventado no gasta cupo. Además evita un `code` `80` engañoso: la EMT contesta así a las llegadas de una parada inexistente, igual que a un token inválido.
10. Las etiquetas de `lines` en la lista salen del catálogo de líneas. Se quita el sentido y los duplicados (`501/1` y `501/2` dan una sola `501`), en el orden de las líneas. Un código que no está en el catálogo se devuelve tal cual, con un log `WARN`.
11. Horario de hoy. Códigos de `dayType`: `LA` laborable y `VI` viernes o víspera de festivo, los dos vistos en las capturas; `SA` sábado y `FE` domingo o festivo, según la documentación de la EMT y sin ver aún. El viernes 2026-10-30, la EMT devolvió **un solo tipo por línea**, ya el de hoy: `LA` en las diurnas y `VI` en las nocturnas (`N1`…`N26`). Por eso el API usa el tipo que trae la EMT. Solo si una línea trae varios, elige el de hoy en Madrid: lunes a jueves `LA`; viernes `VI` (o `LA`); sábado `SA`; domingo `FE`. Una línea sin horario se devuelve con `firstBus`, `lastBus` y frecuencias a `null`.
12. Las líneas nocturnas (`N…`, los "búhos") cruzan la medianoche: `firstBus` es mayor que `lastBus` (`23:30` → `05:30`). Es un horario válido y se devuelve tal cual.
8. Se activa `server.compression` para `application/json` (a partir de 2 KB). La respuesta de la EMT con 4.935 paradas pesa 926 KB, y gzip la deja en 179 KB; la nuestra, sin `wifi` ni códigos internos, pesará menos. También comprime las respuestas de BiciMAD, sin cambiar su contenido.
9. El radio máximo es 2.000 m, no 5.000 como en BiciMAD: hay unas 7 veces más paradas que estaciones, y a 5 km una respuesta tendría miles.

## Tech Stack

Lo de [api-bicimad](SPEC-api-bicimad.md#tech-stack), sin dependencias nuevas. El `POST` usa el `RestClient` que ya tiene `EmtHttpClient`.

## API Contract

Las respuestas de éxito llevan `updatedAt` (ISO 8601, UTC), `stale` y `source: "EMT Madrid MobilityLabs"`, como en BiciMAD.

### `GET /v1/bus/stops`

| Parámetro | Tipo                | Por defecto | Reglas                                             |
| --------- | ------------------- | ----------- | -------------------------------------------------- |
| `near`    | `lat,lon` (decimal) | —           | Opcional. `lat` en [-90, 90], `lon` en [-180, 180] |
| `radius`  | entero, metros      | `500`       | [1, 2000]. Solo válido con `near`                  |

- Sin `near`: todas las paradas, sin `distanceMeters` (lo usa el buscador por número).
- Con `near`: las que están a ≤ `radius` m, con `distanceMeters`, ordenadas por distancia y luego por `number`.
- Lista vacía: `200` con `stops: []`.

```json
{
  "stops": [
    {
      "number": "72",
      "name": "Cibeles-Casa de América",
      "lat": 40.4202683,
      "lon": -3.6921391,
      "lines": ["14", "150", "27", "37", "45", "5", "53"],
      "distanceMeters": 40
    }
  ],
  "updatedAt": "2026-10-30T10:15:00Z",
  "stale": false,
  "source": "EMT Madrid MobilityLabs"
}
```

### `GET /v1/bus/stops/{number}`

Información estática de una parada: la entrada de la lista, más la dirección (`postalAddress`) y las líneas del detalle de la EMT (clave `bus-stop-detail`), con el horario de hoy (supuesto 11). `direction` es el sentido de la línea en esta parada: `A` va hacia `headerA`, y `B` hacia `headerB`. Comprobado con las paradas 2115 y 2768: el destino de sus llegadas es la cabecera de su sentido. El sufijo de la lista (`073/2`) no coincide con ese sentido (`A`), así que no se usa.

```json
{
  "stop": {
    "number": "72",
    "name": "Cibeles-Casa de América",
    "address": "Paseo de Recoletos, 2",
    "lat": 40.4202683,
    "lon": -3.6921391,
    "lines": [
      {
        "label": "5",
        "headerA": "SOL/SEVILLA",
        "headerB": "CHAMARTIN",
        "direction": "B",
        "firstBus": "06:30",
        "lastBus": "22:58",
        "minFrequencyMinutes": 10,
        "maxFrequencyMinutes": 22
      }
    ]
  },
  "updatedAt": "…",
  "stale": false,
  "source": "EMT Madrid MobilityLabs"
}
```

### `GET /v1/bus/stops/{number}/arrivals`

Próximas llegadas, por orden de llegada. Endpoint aparte porque cambia cada pocos segundos y el detalle casi nunca.

```json
{
  "arrivals": [
    { "line": "14", "destination": "PIO XII", "minutes": 0, "noEstimate": false, "distanceMeters": 120, "busLat": 40.4199, "busLon": -3.6935 },
    { "line": "27", "destination": "PLAZA CASTILLA", "minutes": 1, "noEstimate": false, "distanceMeters": 650, "busLat": 40.4161, "busLon": -3.6932 },
    { "line": "N16", "destination": "AVDA. FELIPE II", "minutes": null, "noEstimate": true, "distanceMeters": null, "busLat": null, "busLon": null }
  ],
  "updatedAt": "…",
  "stale": false,
  "source": "EMT Madrid MobilityLabs"
}
```

- `minutes`: redondeado hacia abajo (`estimateArrive / 60`); `0` se muestra como "Llegando".
- Orden: por `minutes`, con `noEstimate` al final; a igualdad, por línea (numérico, después alfabético).
- `busLat` y `busLon`: posición del autobús según la EMT (`geometry.coordinates`, `[lon, lat]`). Valen `null` con `noEstimate` o si faltan coordenadas o son `[0, 0]`.
- Sin autobuses previstos: `200` con `arrivals: []`.

### Errores

Formato RFC 9457 de api-core (`application/problem+json` con `type`, `title`, `detail`, `status` y el `requestId` del log). La regla general: **primero, datos de la caché; si no hay, un error claro con `Retry-After` cuando se sabe cuánto esperar**. Nunca un dato inventado.

**Cuando falla la EMT** (`EmtHttpClient`, `CacheService` y `ProblemDetailsHandler`; ya existen salvo lo marcado como nuevo):

| Lo que pasa con la EMT                                                | Qué hace el API                                                                                                                      | Respuesta al cliente, si no hay caché válida |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------- |
| No contesta a tiempo                                                  | Reintenta una vez; luego sirve la caché como `stale` si no supera `max-stale`                                                        | `504`                                        |
| HTTP 5xx o conexión rechazada                                         | Igual                                                                                                                                | `503`                                        |
| `code` `80`, con cualquier HTTP (token rechazado o parada no disponible) | Hace login otra vez y repite la petición una vez, como hoy. Si con el token nuevo sigue el `80`, es un `EmtProtocolError` y sirve la caché como `stale` | `502` (error estándar `urn:mad-transit:problem:emt-bad-response`)    |
| Otro `code` de error (`90`…) o cuerpo ilegible                        | Log `WARN` con el código; sirve la caché como `stale`                                                                                | `502`                                        |
| Respuesta sin datos válidos (0 paradas, catálogo vacío)               | Se trata como el caso anterior                                                                                                       | `502`                                        |
| Un registro sin coordenadas, sin número o con valores desconocidos    | Descarta el registro con un log `WARN`; sirve el resto                                                                               | `200`                                        |
| Cupo propio agotado (`QuotaTracker`)                                  | No llama a la EMT; sirve la caché como `stale`                                                                                       | `503` + `Retry-After` hasta medianoche       |
| Llegadas en caché de más de 2 min                                     | No se sirven como `stale` (supuesto de [Caché y cupo](#caché-y-cupo))                                                                | `503`                                        |

**El `code` `80` no se puede distinguir por el HTTP** (comprobado el 2026-10-30, ver [Hallazgos](#hallazgos-de-la-emt)). Con un token inválido, BiciMAD y las llegadas contestan HTTP 401, pero la lista, el catálogo y el detalle del bus contestan HTTP 200. Por eso `EmtHttpClient` no cambia: cualquier `80` hace login otra vez una vez, y si sigue, la respuesta es el error estándar `502`. Una parada de la lista que la EMT da por no disponible acaba en ese `502`, no en un estado propio.

**Errores propios del API:**

| Caso                                                                                            | HTTP                  |
| ----------------------------------------------------------------------------------------------- | --------------------- |
| `near` mal formado o fuera de rango, `radius` fuera de rango o sin `near`, `number` no numérico | `400`                 |
| `number` que no está en la lista de paradas (sin llamar a la EMT)                               | `404`                 |
| Rate limit superado                                                                             | `429` + `Retry-After` |
| Excepción no prevista                                                                           | `500`, log `ERROR` con traza y `requestId`; el cuerpo no da detalles internos |

**Cómo se ve en operación:** cada fallo de la EMT deja un log con el módulo y la clave (`bus-arrivals 72`) y suma en `emt.cache.gets{result="stale"}`. `emt.quota.used` dice cuánto cupo queda.

### Caché y cupo

| Clave          | Qué guarda                   | TTL    | `max-stale` | Cupo diario | Llamadas esperadas                      |
| -------------- | ---------------------------- | ------ | ----------- | ----------- | --------------------------------------- |
| `bus-stops`    | lista de paradas (una entrada) | 24 h   | 7 días      | 50          | 1/día                                   |
| `bus-lines`    | catálogo de líneas (una entrada) | 24 h | 7 días      | 50          | 1/día                                   |
| `bus-stop-detail` | detalle por parada        | 24 h   | 7 días      | 20.000      | una por parada abierta al día (4.935 paradas como mucho) |
| `bus-arrivals` | llegadas por parada          | 30 s   | 2 min       | 150.000     | 120/h por parada abierta (polling 30 s) |

- Total del bus: 170.100. Con BiciMAD (3.000) queda bajo el global de 225.000, que es el 90 % de las 250.000 llamadas al día de la app (`apiCounter.dailyUse`, comprobado el 2026-10-30).
- Un solo cliente no puede agotar estos cupos. El rate limit por IP (1 petición por segundo) le deja 86.400 llamadas al día, menos que las 150.000 de llegadas. En el detalle, como mucho una llamada por parada y día (4.935), menos que las 20.000.
- Las llegadas con más de 2 min no se sirven como `stale`: un autobús que "llega en 1 min" de hace 5 min engaña. Sin caché válida, `503`.
- `ETag` y `Cache-Control: no-cache` ya aplican a `/v1/**` (`HttpCacheConfig`).

### Cliente publicado

Las operaciones nuevas (`listBusStops`, `getBusStop`, `getBusStopArrivals`) salen en `@jorgetroya80/mad-transit-client` en la siguiente release del API. Solo añade endpoints: `feat(api)`, sin `BREAKING CHANGE`.

## Commands

```bash
cd api
./gradlew build             # compila, ktlint, tests de bus y de shared, JaCoCo ≥ 80 %, Modulith
./gradlew generateOpenApi   # el OpenAPI incluye listBusStops, getBusStop y getBusStopArrivals
./gradlew bootRun
./gradlew smokeTest         # manual, con .env.local: también llama a los tres endpoints de bus

curl -s 'localhost:8080/v1/bus/stops?near=40.4168,-3.7038&radius=500'
curl -s localhost:8080/v1/bus/stops/72
curl -s localhost:8080/v1/bus/stops/72/arrivals
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/v1/bus/stops/99999999   # 404
curl -s -H 'Accept-Encoding: gzip' -o /dev/null -w '%{size_download}\n' localhost:8080/v1/bus/stops

cd ..
pnpm check:task
```

## Project Structure

```
api/src/main/kotlin/io/github/jorgetroya80/madtransit/
  shared/emt/EmtHttpClient.kt      + post(module, path, body, elementType)
  modules/bus/
    ModuleMetadata.kt              @ApplicationModule
    domain/                        BusStop, BusLine, Arrival, snapshots; GeoPoint propio (sin importar bicimad)
    ports/                         BusStopProvider, BusStopDetailProvider, ArrivalProvider
    application/                   FindNearbyStops, GetStop, GetArrivals
    adapters/emt/                  DTOs EMT, mappers (capa anticorrupción), providers con CacheService
    http/                          BusStopsController (/v1/bus/stops), query, DTOs de respuesta
api/src/test/resources/emt/        + bus-stops-list.json, bus-lines-info.json, bus-stop-detail.json,
                                     bus-arrivals.json, bus-arrivals-missing-stop.json (capturas del 2026-10-30, recortadas)
api/src/main/resources/application.yaml   + cache y cupo de bus-stops, bus-stop-detail, bus-arrivals; server.compression
```

`GeoPoint` y el haversine se duplican en `modules/bus`: Spring Modulith prohíbe importar `bicimad`, y moverlos a `shared` cambia BiciMAD (fuera de alcance). Si se quiere compartir, va en un `refactor(api)` aparte.

## Code Style

El de [api-bicimad](SPEC-api-bicimad.md#code-style), sin reglas nuevas. La capa anticorrupción traduce en el borde y el dominio no conoce los códigos de la EMT:

```kotlin
// adapters/emt/EmtArrivalMapper.kt
internal object EmtArrivalMapper {
    // EMT sends 999999 when it has no estimate for the bus
    private const val NO_ESTIMATE_SECONDS = 999_999

    fun toDomain(arrive: EmtArrive): Arrival =
        if (arrive.estimateArrive >= NO_ESTIMATE_SECONDS) {
            Arrival(arrive.line, arrive.destination, minutes = null, noEstimate = true, distanceMeters = null, bus = null)
        } else {
            Arrival(
                arrive.line,
                arrive.destination,
                minutes = arrive.estimateArrive / 60,
                noEstimate = false,
                distanceMeters = arrive.distanceBus,
                bus = arrive.geometry?.toGeoPointOrNull(),
            )
        }
}
```

- Un DTO por respuesta de la EMT, con los nombres de la EMT; el dominio usa nombres propios.
- Los casos de uso no conocen `CacheService` ni la EMT: solo los puertos.

## Testing Strategy

- Mappers con los fixtures reales: 999999, paradas sin coordenadas, líneas vacías, valores desconocidos.
- Casos de uso con providers falsos: radio, orden, 404.
- Controlador con MockMvc: 200, 400, 404, `stale`.
- Providers con WireMock: caché, cupo propio, `stale` dentro y fuera de `max-stale`.
- `EmtHttpClient.post` con WireMock: cuerpo, token, reintento, token inválido.
- Spring Modulith: `bus` no importa `bicimad`.
- Cobertura ≥ 80 % en `modules/bus` y en `shared`. Nunca se llama a la EMT real; `smokeTest` añade una llamada a cada endpoint del bus.

## Boundaries

- **Siempre:** traducir las rarezas de la EMT en el adaptador; cupo propio por clave; tests antes de cada caso.
- **Preguntar antes:** añadir dependencias; cambiar `modules/bicimad` o el contrato de BiciMAD; subir el cupo global.
- **Nunca:** llamar a la EMT real en tests; registrar credenciales; servir `999999` al cliente.

## Success Criteria

1. SC1: `GET /v1/bus/stops?near=40.4168,-3.7038` devuelve las paradas a ≤ 500 m ordenadas por distancia, con sus líneas.
2. SC2: `GET /v1/bus/stops/{number}` devuelve las líneas con cabeceras, horario y frecuencia; un número inexistente da `404`.
3. SC3: `GET /v1/bus/stops/{number}/arrivals` devuelve minutos por línea y destino; `999999` sale como `noEstimate: true`; las demás llevan `busLat` y `busLon`.
4. SC4: un `code` `80` en cualquier endpoint del bus hace login otra vez una sola vez; si se repite con el token nuevo, el API responde `502` problem+json estándar.
5. SC5: con la EMT caída, paradas y detalle se sirven `stale` hasta 7 días; las llegadas solo hasta 2 min.
6. SC6: el cupo del bus nunca supera 170.100 llamadas/día; las métricas `emt.quota.used` muestran las cuatro claves.
7. SC7: `pnpm check:task` pasa y el cliente generado incluye las tres operaciones.

## Hallazgos de la EMT

Capturas reales del 2026-10-30 con las credenciales de la app (`X-ClientId` + `passKey`); 10 llamadas.

| Llamada                                         | HTTP | `code` | Resultado                                                                                  |
| ----------------------------------------------- | ---- | ------ | ------------------------------------------------------------------------------------------ |
| `POST stops/list/` sin cuerpo                   | 200  | `00`   | 4.935 paradas, 926 KB (179 KB con gzip); todas con coordenadas y número numérico, sin duplicados |
| `POST stops/list/` con `{}` o `{"stopId": []}`  | 200  | `90`   | Error de la EMT (`$in needs an array`): el cuerpo no se envía                              |
| `GET stops/list/`                               | 405  | —      | Solo admite `POST`                                                                         |
| `GET lines/info/20261030/`                      | 200  | `00`   | 236 líneas con `line` (código) y `label` (etiqueta pública)                                |
| `GET stops/72/detail/`                          | 200  | `00`   | 14 líneas con `dayType` `LA` y `VI` el mismo viernes; frecuencias como texto (`"22"`)       |
| `POST stops/72/arrives/`                        | 200  | `00`   | 16 llegadas; `line` ya es la etiqueta; hay `999999` y estimaciones de más de 20 min         |
| `POST stops/999999/arrives/`                    | 200  | `80`   | "Parada no disponible actualmente o inexistente": el mismo código que un token inválido    |
| `GET stops/arroundxy/-3.7038/40.4168/250/`      | 200  | `00`   | 3 paradas, las mismas que da la lista completa a 250 m. Ya trae etiquetas, pero no se usa (decisión 2) |

- En la respuesta del login, `apiCounter.dailyUse` de la app es 250.000 llamadas, no las 20.000 que supone `QuotaTracker`. Desde la revisión del PR #87, el global usa ese dato (225.000).
- Nocturnas de la parada 72 (`N1`, `N4`, `N22`, `N24`, `N25`, `N26`): empiezan entre las 23:20 y las 00:00 y terminan entre las 05:30 y las 05:45, todas con `VI` el viernes.
- **Token inválido o ausente, por endpoint** (`code` `80`, "Error, token not found in cache"):

  | Endpoint                       | HTTP |
  | ------------------------------ | ---- |
  | `GET bicimad/stations/`        | 401  |
  | `POST busemtmad/stops/list/`   | 200  |
  | `GET busemtmad/lines/info/…`   | 200  |
  | `GET busemtmad/stops/…/detail/` | 200 |
  | `POST busemtmad/stops/…/arrives/` | 401 |

- **Parada inexistente con token válido** (99999999): el detalle da HTTP 200 con `code` `90` ("Error managing internal services") y las llegadas, HTTP 200 con `code` `80`. Los dos dan un `404` nuestro sin llegar a la EMT (supuesto 7).
- Parada 2768 ("Emigrantes-Carretera de Canillas"): la lista da `073/2` y `503/2`, que el catálogo traduce a `73` y `N3`. En el detalle, `73` solo trae `LA` y `N3` solo `VI`, el mismo viernes. Confirma el supuesto 11: la EMT ya da un solo tipo de día por línea.
- A ≤ 500 m hay entre 25 y 41 paradas (Sol 41, Cibeles 31, Moncloa 28, Vallecas 25).

## Changelog

- 2026-10-30: borrador con las decisiones 1 a 3 del usuario.
- 2026-10-30: revisión del SPEC (1): añade Tech Stack, Commands y Code Style. Añade los supuestos 7 (404 sin llamar a la EMT), 8 (compresión) y 9 (radio máximo), el orden de las llegadas y la pregunta 2. Renombra la clave `bus-lines` a `bus-stop-detail`.
- 2026-10-30: revisión del SPEC (2), con capturas reales de la EMT: la lista se pide sin cuerpo, las etiquetas de línea salen del catálogo (clave `bus-lines`), horarios solo de hoy (decisión 4), favoritos fuera (decisión 5), `beyondEstimate` pasa a ser `noEstimate`, y el código `80` de una parada inexistente se suma a las razones del supuesto 7. Se cierran las preguntas abiertas.
- 2026-10-30: decisión 6 (posición del autobús en cada llegada, `busLat` y `busLon`). La sección Errores pasa a tabla: qué hace el API con cada fallo de la EMT y qué responde. Añade `inService` y el caso del `code` `80` con HTTP 200.
- 2026-10-30: aprobada por el usuario.
- 2026-10-30: comprobado el `code` `80` en el bus: con un token inválido, la lista, el catálogo y el detalle responden HTTP 200, así que el HTTP no sirve para distinguirlo. Se quita `inService` y `EmtHttpClient` no cambia: un `80` que se repite tras el login da el error estándar `502` (decisión del usuario).
- 2026-10-30: revisión del PR #87 (R1). Un solo cliente rotando paradas agotaba el cupo de llegadas (10.000) en unas 3 h y el de detalle (2.000) en unos 33 min, y después todos recibían `503`. Los cupos pasan a 150.000 y 20.000, y el global a 225.000 (el 90 % de las 250.000 llamadas reales de la app).
