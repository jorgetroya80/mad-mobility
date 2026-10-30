# Plan: api-bus

- Created: 2026-10-30
- Status: **in review** (2026-10-30)
- Spec: [SPEC-api-bus.md](../specs/SPEC-api-bus.md)

## Overview

Módulo `modules/bus` con tres endpoints (`/v1/bus/stops`, `/v1/bus/stops/{number}`, `/v1/bus/stops/{number}/arrivals`) sobre `shared/emt`. Antes hay que añadir `POST` a `EmtHttpClient`. El manejo del `code` `80` no cambia: el HTTP no permite distinguir un token inválido de una parada no disponible (hallazgos de la spec). Las respuestas reales de la EMT se capturaron el 2026-10-30 y se convierten en fixtures en T1.

## Decisiones de implementación

- **Rama `feat/api-bus`, un PR y un commit por tarea.** Sin `Co-Authored-By`. El PR solo toca `api/` y `docs/`. Título `feat(api): add EMT bus stops, details and arrivals`: solo añade endpoints, así que no es un breaking change. release-please publica una versión menor del API y del cliente.
- **Primero lo de más riesgo.** `POST` toca `shared/emt`, que usa BiciMAD, así que va en T2 con los tests de BiciMAD en verde antes de seguir.
- **Fixtures recortados de las capturas reales**, como `bicimad-stations.json`: unas pocas paradas, líneas y llegadas que cubran los casos de la spec (`999999`, nocturnas, `[0, 0]`, parada inexistente). Sin el token.
- **`GeoPoint` y el haversine se copian en `modules/bus`.** Spring Modulith prohíbe importar `bicimad` (spec, Project Structure).
- **El tipo de día de hoy** sale de un `Clock` inyectado (Europe/Madrid), para poder testear lunes, viernes, sábado y domingo.

## Grafo de dependencias

```
T1 fixtures ──► T2 EmtHttpClient (POST + code 80) ──┬─► T3 lista de paradas + catálogo (adaptador)
                                                   │        │
                                                   │        └─► T4 GET /v1/bus/stops (near, radius)
                                                   │                 │
                                                   │                 ├─► T5 GET /v1/bus/stops/{number}
                                                   │                 │
                                                   └─────────────────┴─► T6 GET …/arrivals
                                                                             │
                                                         T7 compresión + OpenAPI ◄┘
                                                                             │
                                                                     T8 smoke + cierre
```

## Task List

### Fase 1: Base compartida

- [ ] **T1: Fixtures del bus** (S, ~30 min)
  - Descripción: pasar las capturas de la carpeta temporal a `src/test/resources/emt/` recortadas: `bus-stops-list.json` (paradas 72, 2115, 2768, 4025 y una con un código de línea desconocido), `bus-lines-info.json` (las líneas de esas paradas), `bus-stop-detail.json` (72 y 2768: `LA` y `VI`), `bus-arrivals.json` (72, con un `999999` y una llegada de más de 20 min), `bus-arrivals-missing-stop.json` (`code` `80`, HTTP 200) y `bus-token-invalid.json` (lista con token inválido: `code` `80`, HTTP 200). Añadir una fila por fixture a la tabla del `README.md` y los hallazgos de la spec.
  - Aceptación:
    - Los JSON son las respuestas reales recortadas, sin token ni datos personales.
    - El README dice para cada uno la petición, el HTTP y el `code`.
  - Verificación: `python3 -m json.tool` en cada archivo; `pnpm check:fast` (gitleaks).
  - Archivos: 5 fixtures, `src/test/resources/emt/README.md`.
  - Dependencias: ninguna.

- [ ] **T2: `EmtHttpClient.post` y `code` `80` en el bus** (M, ~1 h 15 min)
  - Descripción: test primero. Añadir `post(module, path, body, elementType)` con el mismo token, cupo, reintento y manejo de errores que `get`. Tests nuevos que fijan el `code` `80` tal como lo devuelve el bus: con HTTP 200 también hace login otra vez una sola vez.
  - Aceptación:
    - `code` `80` con HTTP 401 (BiciMAD, llegadas) y con HTTP 200 (lista, catálogo, detalle; `bus-token-invalid.json`) → login otra vez y repetir una vez.
    - Si se repite el `80` con el token nuevo (`bus-arrivals-missing-stop.json` en las dos respuestas) → `EmtProtocolError`, que el API devuelve como `502` estándar; un solo login.
    - `post` envía el cuerpo JSON y la cabecera `accessToken`, cuenta en el cupo y reintenta una vez un fallo de transporte.
    - Todos los tests de BiciMAD siguen en verde.
  - Verificación: `./gradlew -p api test --tests '*EmtHttpClient*' --tests '*bicimad*'`.
  - Archivos: `shared/emt/EmtHttpClient.kt`, `EmtHttpClientTest.kt`.
  - Dependencias: T1.

### Checkpoint: Fase 1

- [ ] `pnpm check:task` en verde; JaCoCo de `shared` ≥ 80 %.
- [ ] Revisión humana de `post` antes de seguir: `shared/emt` lo usa también BiciMAD.

### Fase 2: Paradas

- [ ] **T3: Lista de paradas y catálogo de líneas (adaptador)** (M, ~1 h 30 min)
  - Descripción: `modules/bus/ModuleMetadata.kt`, dominio `BusStop` y `GeoPoint`, DTOs `EmtBusStop` y `EmtLineInfo`, y un mapper que traduce los códigos de línea a etiquetas con el catálogo: sin sentido, sin duplicados, código desconocido tal cual + `WARN`. Paradas sin coordenadas o sin número descartadas + `WARN`. Lista o catálogo vacíos → `EmtProtocolError`.
  - Aceptación:
    - La parada 2768 del fixture da `lines = ["73", "N3"]`; la 72 no repite `501`.
    - Un código que no está en el catálogo sale tal cual, con un log `WARN`.
    - `ModularityTest` reconoce `bus` y comprueba que no importa `bicimad`.
  - Verificación: `./gradlew -p api test --tests '*bus*' --tests '*Modularity*'`.
  - Archivos: `ModuleMetadata.kt`, `domain/BusStop.kt`, `domain/GeoPoint.kt`, `adapters/emt/EmtBusStop.kt`, `adapters/emt/EmtBusStopMapper.kt` (+ test).
  - Dependencias: T2.

- [ ] **T4: `GET /v1/bus/stops` con `near` y `radius`** (M, ~1 h 30 min)
  - Descripción: puerto `BusStopProvider`, `EmtBusStopProvider` con `POST stops/list/` sin cuerpo (clave `bus-stops`) y `GET lines/info/{AAAAMMDD}/` (clave `bus-lines`), los dos con TTL de 24 h y `max-stale` de 7 días. Caso de uso `FindNearbyStops`, `BusStopsQuery` (radio de 1 a 2000, por defecto 500) y `BusStopsController`. Cupos y cachés en `application.yaml`.
  - Aceptación:
    - SC1: con `near` de Sol, las paradas del fixture a ≤ 500 m, ordenadas por distancia y después por `number`, con `distanceMeters`.
    - Sin `near`: todas, sin `distanceMeters`. `radius` sin `near` o fuera de [1, 2000] → `400`.
    - Dos peticiones seguidas = 1 llamada a cada endpoint de la EMT; con la EMT caída, `stale: true`.
  - Verificación: `./gradlew -p api build`; `bootRun` + `curl -s 'localhost:8080/v1/bus/stops?near=40.4168,-3.7038' | jq '.stops | length'`.
  - Archivos: `ports/BusStopProvider.kt`, `adapters/emt/EmtBusStopProvider.kt`, `application/FindNearbyStops.kt`, `http/BusStopsController.kt`, `http/BusStopsQuery.kt`, `http/BusStopResponse.kt`, `application.yaml` (+ tests).
  - Dependencias: T3.

- [ ] **T5: `GET /v1/bus/stops/{number}`** (M, ~1 h 30 min)
  - Descripción: DTO del detalle de la EMT y su mapper: `postalAddress`, líneas con cabeceras y sentido (`A` va hacia `headerA`), frecuencias de texto a número y el horario de hoy (supuesto 11, con `Clock`). Provider con clave `bus-stop-detail` por parada. `GetStop` comprueba antes la lista: un número que no está en ella da `404` sin llamar a la EMT. Las nocturnas cruzan la medianoche tal cual.
  - Aceptación:
    - SC2: la 72 da sus 14 líneas con horario y frecuencia; `N1` con `firstBus` `23:30` y `lastBus` `05:30`.
    - Con varios tipos de día por línea, gana el de hoy: el test cubre lunes, viernes (con `VI` y sin él), sábado y domingo. Una línea sin el tipo de hoy sale con horario `null`.
    - `GET /v1/bus/stops/99999999` → `404` y 0 llamadas en WireMock; `GET /v1/bus/stops/abc` → `400`.
  - Verificación: `./gradlew -p api test --tests '*bus*'`.
  - Archivos: `adapters/emt/EmtBusStopDetail.kt`, `adapters/emt/EmtBusStopDetailMapper.kt`, `adapters/emt/EmtBusStopDetailProvider.kt`, `application/GetStop.kt`, `http/BusStopsController.kt` (+ tests).
  - Dependencias: T4.

### Checkpoint: Fase 2

- [ ] `pnpm check:task` en verde; JaCoCo de `modules/bus` ≥ 80 %.
- [ ] `bootRun` con la EMT real: lista cerca de Sol y detalle de 2768 coinciden con las capturas.

### Fase 3: Llegadas y cierre

- [ ] **T6: `GET /v1/bus/stops/{number}/arrivals`** (M, ~2 h)
  - Descripción: DTO `EmtArrive` y `EmtArrivalMapper` (spec, Code Style): `999999` → `noEstimate`, minutos redondeados hacia abajo, `busLat`/`busLon` desde `geometry` (`null` con `[0, 0]` o sin coordenadas). Provider con `POST stops/{id}/arrives/` y clave `bus-arrivals` (TTL 30 s, `max-stale` 2 min). `GetArrivals` comprueba antes la lista (`404`). Orden: minutos, `noEstimate` al final y después la línea.
  - Aceptación:
    - SC3: las 16 llegadas del fixture de la 72 en orden, con el `999999` como `noEstimate: true` y sin posición.
    - SC4: una parada de la lista con `code` `80` también tras el login → `502` problem+json estándar.
    - SC5 (llegadas): con la EMT caída, se sirve la caché como `stale` hasta 2 min y después `503`.
  - Verificación: `./gradlew -p api test --tests '*bus*'`; `bootRun` + `curl -s localhost:8080/v1/bus/stops/2768/arrivals`.
  - Archivos: `domain/Arrival.kt`, `adapters/emt/EmtArrive.kt`, `adapters/emt/EmtArrivalMapper.kt`, `adapters/emt/EmtArrivalProvider.kt`, `application/GetArrivals.kt`, `http/BusStopsController.kt` (+ tests).
  - Dependencias: T5.

- [ ] **T7: Compresión y contrato OpenAPI** (S, ~45 min)
  - Descripción: `server.compression` para `application/json` a partir de 2 KB. Anotaciones springdoc de los tres endpoints: `operationId` `listBusStops`, `getBusStop` y `getBusStopArrivals`, y las respuestas `400`, `404`, `429`, `502`, `503` y `504`, como en `StationsController`.
  - Aceptación:
    - Con `Accept-Encoding: gzip`, `/v1/bus/stops` llega comprimido (test de integración: `Content-Encoding: gzip`).
    - El OpenAPI generado tiene las tres operaciones con sus esquemas y errores; Redocly lint en verde.
  - Verificación: `./gradlew -p api generateOpenApi`; `./gradlew -p api build`.
  - Archivos: `application.yaml`, `http/BusStopsController.kt`, `OpenApiExportTest.kt` o un test HTTP nuevo.
  - Dependencias: T6.

- [ ] **T8: Smoke test y documentación** (S, ~45 min)
  - Descripción: `smokeTest` llama a los tres endpoints del bus contra la EMT real. Spec y capability map a **implemented**, changelog del plan y `api/AGENTS.md` si cambia algo. Comprobar `SA` y `FE` con `smokeTest` un sábado o un domingo; si el formato difiere, PR `fix(api)` aparte.
  - Aceptación:
    - SC6: el cupo del bus no pasa de 12.100; `emt.quota.used` muestra las cuatro claves.
    - SC7: `pnpm check:task` en verde y el cliente generado tiene las tres operaciones.
  - Verificación: `./gradlew -p api smokeTest` (manual, con `.env.local`); `pnpm check:task`.
  - Archivos: `src/smokeTest/kotlin/…`, `docs/specs/SPEC-api-bus.md`, `docs/specs/CAPABILITY-MAP.md`, este plan.
  - Dependencias: T7.

### Checkpoint final

- [ ] SC1-SC7 comprobados; `pnpm check:task` y CI en verde.
- [ ] Pedir permiso antes de push, abrir el PR y hacer merge. Después del merge, release del API y del cliente: requisito de [PLAN-web-bus.md](PLAN-web-bus.md).

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Una parada de la lista que la EMT da por no disponible hace un login en cada sondeo de 30 s mientras está abierta | Medio | El login es compartido (single-flight) y se cuenta en `emt.auth.logins`; si sube, se guarda el `502` unos segundos en un cambio aparte |
| `SA` y `FE` no se han visto en una respuesta real | Medio | La EMT ya devuelve un tipo por línea; regla de respaldo con `Clock`; `smokeTest` en fin de semana (T8) |
| La EMT cambia el formato de la lista o pide otra vez un cuerpo | Medio | La lista vacía se trata como error, se sirve la caché `stale` hasta 7 días y el log `WARN` lo avisa |
| El cupo de llegadas (10.000 al día) se agota con muchos usuarios | Medio | Caché compartida de 30 s por parada; `503` con `Retry-After`; la app tiene 250.000 al día (hallazgos) si hace falta subirlo en un cambio aparte |
| Una respuesta de 4.935 paradas pesa demasiado para el plan gratuito de Render | Bajo | gzip en T7 (179 KB en la EMT, menos en la nuestra) y ETag |

## Paralelización

Secuencial: todas las tareas tocan `BusStopsController` o dependen de T2. T1 puede hacerse mientras se revisa este plan.

## Estimación

Unas 10 h en total. Fase 1 ~2 h, fase 2 ~4 h 30 min y fase 3 ~3 h 30 min.

## Changelog

- 2026-10-30: borrador a partir de la spec aprobada.
- 2026-10-30: T2 ya no cambia el manejo del `code` `80` (el bus responde HTTP 200 con un token inválido); se quita `inService` de T6.
