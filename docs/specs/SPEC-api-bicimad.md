# Spec: api-bicimad

- Created: 2026-10-05
- Status: **approved** (2026-10-05)
- Plan: [PLAN-api-bicimad.md](../plans/PLAN-api-bicimad.md)

Módulo `api-bicimad` del [Capability Map](CAPABILITY-MAP.md). Añade el módulo de dominio `modules/bicimad` sobre la infraestructura de [api-core](SPEC-api-core.md), documenta la API con springdoc y publica un cliente TypeScript en cada release del API. Fuente: [docs/ideas/bicimad-now.md](../ideas/bicimad-now.md).

## Objective

Que cualquier cliente sepa en una sola petición qué estaciones de BiciMAD cercanas tienen bicis o anclajes libres, con datos normalizados (sin rarezas de la EMT) y servidos desde caché.

- Como usuario del frontend, quiero las estaciones cerca de un punto, ordenadas por lo que necesito (bici o anclaje) y por distancia, para decidir en menos de 5 s a cuál ir.
- Como usuario del frontend, quiero el detalle de una estación por su `id`, para abrir un enlace compartido (`?station=1409`).
- Como usuario, quiero saber si los datos son antiguos (`stale`) y de cuándo son (`updatedAt`), porque la EMT puede no responder.
- Como desarrollador del frontend, quiero instalar un paquete npm versionado con el cliente tipado, para que un cambio del contrato rompa mi build y no la app en producción.
- Como mantenedor, quiero limitar las peticiones por IP sin dependencias nuevas, para que un cliente abusivo no agote el cupo ni la CPU.

**Fuera de alcance:** frontend (`web-shell`, `web-bicimad`), despliegue y proxy inverso (`deploy`), módulo `bus`, historial o predicción.

## Assumptions

1. El `id` de estación de la EMT es estable entre días (pendiente de validar en la idea; si no lo es, los enlaces compartidos fallan con 404, no con datos erróneos).
2. Las estaciones con `virtualDelete: true` no existen para la API: no aparecen en listas y su `id` da 404.
3. El rate limit usa `request.remoteAddr`. Leer `X-Forwarded-For` detrás de un proxy (`server.forward-headers-strategy`) se configura en `deploy`.
4. springdoc solo existe en desarrollo: va en las configuraciones `developmentOnly` (la usa `bootRun`) y `testImplementation` de Gradle, nunca en `bootJar` ni en la imagen Docker. En `bootRun` sirve `/v3/api-docs` y Swagger UI (`/swagger-ui.html`); en producción esas rutas dan 404. Documenta solo `/v1/**` (no Actuator). Las anotaciones de los controladores (`io.swagger.v3.oas.annotations`) van como `compileOnly`.
5. `openapi/bicimad.json` deja de commitearse: se genera en el build y se publica dentro del paquete del cliente. La carpeta `openapi/` y el job `OpenAPI lint` actual desaparecen (ver Changelog del Capability Map).
6. El paquete del cliente vive en `api/client/` y comparte versión con el API (la de `api/gradle.properties`, gestionada por release-please).

## Tech Stack

Lo de [api-core](SPEC-api-core.md#tech-stack), más:

| Herramienta                             | Versión                      | Uso                                                  |
| --------------------------------------- | ---------------------------- | ---------------------------------------------------- |
| springdoc-openapi (`starter-webmvc-ui`) | 3.x (línea de Spring Boot 4) | OpenAPI y Swagger UI, solo `developmentOnly` y tests |
| swagger-annotations                     | la que gestiona springdoc    | Anotaciones de los controladores (`compileOnly`)     |
| openapi-typescript                      | 7.x                          | Genera `schema.d.ts` del cliente (devDependency)     |
| openapi-fetch                           | 0.x última estable           | Cliente HTTP tipado del paquete (dependency)         |
| TypeScript                              | la del workspace             | Compila el paquete                                   |
| `@redocly/cli`                          | 2.x                          | Lint del OpenAPI generado en CI (ya se usa)          |

Versiones exactas en el plan, respetando `minimumReleaseAge` (10 días) y la compatibilidad con Boot 4.1.1.

## API Contract

Todas las respuestas de éxito incluyen `updatedAt` (ISO 8601, UTC), `stale` y `source: "EMT Madrid MobilityLabs"`.

### `GET /v1/bicimad/stations`

| Parámetro | Tipo                | Por defecto | Reglas                                             |
| --------- | ------------------- | ----------- | -------------------------------------------------- |
| `near`    | `lat,lon` (decimal) | —           | Opcional. `lat` en [-90, 90], `lon` en [-180, 180] |
| `radius`  | entero, metros      | `500`       | [1, 5000]. Solo válido con `near`                  |
| `need`    | `bikes` \| `docks`  | —           | Opcional. Solo ordena; nunca quita estaciones      |

- Sin `near`: todas las estaciones (≈ 677), sin `distanceMeters`.
- Con `near`: solo las que están a ≤ `radius` metros (haversine sobre la caché), con `distanceMeters` (entero).
- Orden:
  - Con `need`: primero las disponibles para esa necesidad (`status = OPERATIONAL` y `bikes > 0`, o `freeDocks > 0`), después el resto.
  - Dentro de cada grupo: por distancia si hay `near`; si no, por `number` (numérico, después alfabético: `5`, `5a`, `5b`, `10`).
- Lista vacía: `200` con `stations: []`.

```json
{
  "stations": [
    {
      "id": 1409,
      "number": "5",
      "name": "Fuencarral",
      "address": "Calle Fuencarral nº 106",
      "lat": 40.4285212,
      "lon": -3.7021354,
      "status": "OPERATIONAL",
      "bikes": 2,
      "freeDocks": 23,
      "totalDocks": 27,
      "occupancy": "LOW",
      "distanceMeters": 120
    }
  ],
  "updatedAt": "2026-10-05T10:15:00Z",
  "stale": false,
  "source": "EMT Madrid MobilityLabs"
}
```

### `GET /v1/bicimad/stations/{id}`

`{ "station": { ... }, "updatedAt", "stale", "source" }`, mismo objeto `station` sin `distanceMeters`.

### Capa anticorrupción (EMT → dominio)

| EMT                                                                      | Dominio                                                         |
| ------------------------------------------------------------------------ | --------------------------------------------------------------- |
| `id`                                                                     | `id`                                                            |
| `number`                                                                 | `number`                                                        |
| `name` (`"5 - Fuencarral"`)                                              | `name` sin el prefijo `"<number> - "` (`"Fuencarral"`)          |
| `geometry.coordinates` (`[lon, lat]`)                                    | `lat`, `lon`                                                    |
| `dock_bikes`, `free_bases`, `total_bases`                                | `bikes`, `freeDocks`, `totalDocks`                              |
| `activate = 1` y `no_available = 0`                                      | `status = OPERATIONAL`; cualquier otra combinación `NO_SERVICE` |
| `light` 0 / 2 / 1 / 3                                                    | `occupancy` `LOW` / `MEDIUM` / `HIGH` / `UNKNOWN`               |
| `virtualDelete = true`                                                   | estación descartada                                             |
| `reservations_count`, `geofenced_capacity`, `tipo_estacionPBSC`, `image` | ignorados                                                       |

- Un valor desconocido (`light = 7`) da `UNKNOWN` y un log `WARN`, nunca un error.
- Una estación con un campo obligatorio ausente o sin coordenadas se descarta con un log `WARN`; el resto se sirve.
- Si la EMT devuelve 0 estaciones válidas, se trata como `EmtProtocolError` (sirve `stale` si hay caché).

### Errores

Formato RFC 9457 de api-core. Propios del módulo:

| Caso                                                                                                                    | HTTP                  |
| ----------------------------------------------------------------------------------------------------------------------- | --------------------- |
| `near` mal formado o fuera de rango, `radius` fuera de rango, `radius` sin `near`, `need` desconocido, `id` no numérico | `400`                 |
| `id` inexistente (o con `virtualDelete`)                                                                                | `404`                 |
| Rate limit superado                                                                                                     | `429` + `Retry-After` |

Los errores de la EMT sin caché siguen la tabla de `ProblemDetailsHandler`.

### Caché HTTP

- `ETag` débil calculado del cuerpo (`ShallowEtagHeaderFilter` en `/v1/**`); `If-None-Match` coincidente devuelve `304`.
- `Cache-Control: no-cache`: el navegador siempre revalida y recibe `304` si nada cambió. Se descarta `max-age` porque, sumado al TTL de 60 s del servidor, podría mostrar datos de hasta 2 min.

### Caché y cupo

- Una sola entrada `CacheService` (`module = "bicimad"`, `key = "stations"`) con el TTL por defecto de 60 s. Detalle y cercanía se calculan sobre ella; nunca se llama a `arroundxy` ni al detalle de la EMT.
- Cupo propio: `mad-mobility.quota.modules.bicimad.daily-limit: 3000` (≈ 1.440 llamadas/día esperadas, margen ×2).

### Rate limit (`shared/web/RateLimitFilter`)

- Token bucket por IP en memoria (Caffeine, `expireAfterAccess` 10 min, máximo 100.000 IPs), sin dependencias nuevas.
- Solo `/v1/**`. Por defecto: capacidad 60, recarga 1 token/s (`mad-mobility.rate-limit.capacity`, `refill-per-second`).
- Sin tokens: `429` problem+json con `Retry-After` (segundos hasta el siguiente token).
- Coste O(1) por petición: una lectura de Caffeine y una operación atómica, sin bloqueos globales.
- Métrica `http.rate_limit.rejected`. Nunca registra la IP completa en métricas (cardinalidad).

### Cliente publicado (`@jorgetroya80/bicimad-client`)

- `api/client/`: `package.json`, `tsconfig.json`, `src/index.ts`. `src/schema.d.ts` se genera y lo ignora git.
- `src/index.ts` exporta los tipos generados (`paths`, `components`), alias cómodos (`Station`, `StationsResponse`) y `createBicimadClient(baseUrl: string)`, que devuelve `createClient<paths>({ baseUrl })` de openapi-fetch.
- Release (`publish-api-client` en `release.yml`, tras `release-please` cuando se crea release de `api`):
  1. `./gradlew generateOpenApi` escribe `api/build/openapi/bicimad.json`.
  2. `openapi-typescript` genera `schema.d.ts`; `tsc` compila a `dist/` (JS + `.d.ts`).
  3. Versión del paquete = versión del API (`pnpm version <api-version> --no-git-tag-version`, sin commit).
  4. `pnpm publish` a `npm.pkg.github.com` con `GITHUB_TOKEN` (`packages: write`). El paquete incluye `dist/` y `openapi.json`.
- Un fallo al publicar el cliente no impide publicar la imagen Docker (jobs independientes).

## Commands

```bash
cd api
./gradlew build                  # como en api-core + tests de bicimad + verificación del OpenAPI
./gradlew generateOpenApi        # escribe build/openapi/bicimad.json (sin EMT ni credenciales reales)
./gradlew bootRun

curl -s 'localhost:8080/v1/bicimad/stations?near=40.4168,-3.7038&radius=500&need=docks'
curl -s localhost:8080/v1/bicimad/stations/1409
curl -s -o /dev/null -w '%{http_code}\n' -H 'If-None-Match: <etag>' localhost:8080/v1/bicimad/stations   # 304
curl -s localhost:8080/v3/api-docs        # solo con bootRun; Swagger UI en /swagger-ui.html

cd ..
pnpm --filter @jorgetroya80/bicimad-client run generate   # openapi-typescript desde api/build/openapi/bicimad.json
pnpm --filter @jorgetroya80/bicimad-client run build      # tsc
```

## Project Structure

```
api/
  src/main/kotlin/io/github/jorgetroya80/madmobility/
    shared/web/
      RateLimitFilter.kt           nuevo
      HttpCacheConfig.kt           nuevo: ShallowEtagHeaderFilter + Cache-Control en /v1/**
    modules/bicimad/
      ModuleMetadata.kt            @ApplicationModule
      domain/
        Station.kt                 Station, StationStatus, Occupancy
        GeoPoint.kt                coordenadas + distancia haversine
        StationSnapshot.kt         estaciones + updatedAt + stale
      ports/
        StationProvider.kt         fun snapshot(): StationSnapshot
      application/
        FindNearbyStations.kt      filtro por radio + orden por need/distancia/number
        GetStation.kt              por id o StationNotFound
      adapters/emt/
        EmtStation.kt              DTO con el formato real de la EMT
        EmtStationMapper.kt        traducción (tabla de arriba)
        EmtStationProvider.kt      EmtHttpClient + CacheService
      http/
        StationsController.kt      /v1/bicimad/stations, anotaciones springdoc
        StationsQuery.kt           parseo y validación de near/radius/need
        StationResponse.kt         DTOs de respuesta
  src/test/kotlin/.../modules/bicimad/   espejo de main
  src/test/kotlin/.../OpenApiExportTest.kt  @Tag("openapi"): escribe build/openapi/bicimad.json
  client/
    package.json                   @jorgetroya80/bicimad-client, publishConfig a GitHub Packages
    tsconfig.json
    src/index.ts
pnpm-workspace.yaml                añade api/client
.github/workflows/ci.yml           job API: genera y lintea el OpenAPI, compila el cliente; quita openapi/** y el job contract
.github/workflows/release.yml      nuevo job publish-api-client
```

- `domain` no importa nada de Spring ni de `shared.emt`. `adapters/emt` es el único paquete que conoce la EMT.
- `generateOpenApi`: tarea `Test` que solo ejecuta el tag `openapi` (excluido de `test`), con `StationProvider` simulado; no necesita red.

## Code Style

Como [api-core](SPEC-api-core.md#code-style). Ejemplo del estilo esperado en el dominio:

```kotlin
data class GeoPoint(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0) { "lat out of range: $lat" }
        require(lon in -180.0..180.0) { "lon out of range: $lon" }
    }

    fun distanceTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }
}
```

- El dominio usa nombres propios (`bikes`, `freeDocks`), nunca los de la EMT.
- Los DTOs HTTP están separados del dominio; el controlador solo traduce.
- Enums de la API en `UPPER_SNAKE_CASE`; propiedades JSON en `camelCase`.

## Testing Strategy

| Nivel       | Herramientas                                 | Qué cubre                                                                                              |
| ----------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Dominio     | JUnit, AssertJ                               | Haversine (distancias conocidas, ±1 %), orden por `need`/distancia/`number`, filtro por radio          |
| Adaptador   | JUnit + fixture `bicimad-stations.json`      | Tabla de traducción completa, `virtualDelete`, valores desconocidos, estaciones incompletas, 0 válidas |
| Adaptador   | `@SpringBootTest` + WireMock (como api-core) | Una sola llamada a la EMT dentro del TTL; `stale` cuando la EMT falla                                  |
| HTTP        | `@WebMvcTest` + `StationProvider` simulado   | Parámetros, validación (`400`), `404`, forma del JSON, `ETag`/`304`, `Cache-Control`                   |
| Rate limit  | JUnit + `Clock` fijo; `MockMvc`              | Capacidad, recarga, `429` + `Retry-After`, IPs independientes, rutas fuera de `/v1/**` sin límite      |
| OpenAPI     | `OpenApiExportTest` + `redocly lint` en CI   | El documento se genera, solo contiene `/v1/**` y pasa el lint                                          |
| Cliente     | `tsc` en CI                                  | El paquete compila contra el OpenAPI generado                                                          |
| Modularidad | `ModularityTest` existente                   | `bicimad` solo depende de `shared`                                                                     |
| Smoke       | `smokeTest` (manual)                         | Añade: la traducción real de la EMT da > 600 estaciones válidas                                        |

- JaCoCo: ≥ 80 % de líneas también en `modules/bicimad`.
- Nuevos fixtures solo capturados de la EMT real (anotados en `api/src/test/resources/emt/README.md`).

## Boundaries

- **Always:** toda llamada a la EMT pasa por `EmtHttpClient` y `CacheService`; el dominio no conoce la EMT; `source` en todas las respuestas de éxito; `Clock` inyectado en `RateLimitFilter`; `./gradlew build` y `tsc` del cliente en verde antes del PR; Conventional Commits con scope `api` (o `ci` para workflows).
- **Ask first:** dependencias fuera de la tabla; cambiar el contrato JSON tras aprobar esta spec; cambiar los límites del rate limit, el radio máximo o el cupo de `bicimad`; publicar en otro registro; mover springdoc a `implementation`.
- **Never:** llamar a `arroundxy` u otros endpoints de la EMT por petición del usuario; exponer campos o códigos de la EMT en la respuesta; publicar el cliente con una versión distinta a la del API; commitear `schema.d.ts`, `dist/` o `bicimad.json`; registrar IPs completas en métricas; springdoc en el jar o la imagen de producción.

## Success Criteria

1. `./gradlew build` pasa en local y en CI, con JaCoCo ≥ 80 % en `shared` y en `modules/bicimad`, y `ModularityTest` verde.
2. Con el fixture, `GET /v1/bicimad/stations` devuelve las estaciones válidas con los campos y valores de la tabla de traducción (incluido `light = 3` → `UNKNOWN` y `no_available = 1` → `NO_SERVICE`).
3. `near=40.4168,-3.7038&radius=500` devuelve solo estaciones a ≤ 500 m, ordenadas por `distanceMeters` ascendente.
4. Con `need=docks`, las estaciones `OPERATIONAL` con `freeDocks > 0` van antes que las demás; ninguna estación desaparece respecto a la misma consulta sin `need`.
5. `radius=6000`, `radius` sin `near`, `near=abc`, `need=car` e `id=abc` devuelven `400` problem+json; `id` inexistente devuelve `404`.
6. Dos peticiones en 60 s producen una sola llamada a la EMT en WireMock; con la EMT caída y caché previa, la respuesta lleva `stale: true` y el `updatedAt` original.
7. Una segunda petición con el `ETag` recibido devuelve `304` sin cuerpo; todas las respuestas `200` de `/v1/**` llevan `Cache-Control: no-cache`.
8. La petición 61 de una IP en el mismo segundo devuelve `429` con `Retry-After`; otra IP sigue recibiendo `200`; `/health/live` nunca devuelve `429`.
9. `./gradlew generateOpenApi` funciona sin credenciales ni red, el JSON solo tiene rutas `/v1/**` y `redocly lint` pasa en CI.
10. El jar de `bootJar` no contiene clases de springdoc y, en el test de la imagen Docker, `/v3/api-docs` y `/swagger-ui.html` devuelven 404.
11. CI compila `api/client` contra el OpenAPI generado en cada PR que toca `api/**`.
12. En una release de `api`, el job `publish-api-client` publica `@jorgetroya80/bicimad-client@<versión del API>` en GitHub Packages, con `dist/` y `openapi.json`.
13. `./gradlew smokeTest` contra la EMT real devuelve > 600 estaciones válidas (manual, anotado en el PR).

## Open Questions

- Ninguna.

## Changelog

- 2026-10-05: versión inicial. Decisiones del usuario: `need` solo ordena; `near` opcional; rate limit propio y sin dependencias; el cliente TypeScript (tipos + openapi-fetch) se publica en GitHub Packages en cada release del API; `openapi/bicimad.json` deja de commitearse.
- 2026-10-05: springdoc solo en desarrollo (`developmentOnly` + tests, con Swagger UI); fuera del jar y la imagen de producción.
- 2026-10-05: spec aprobada.
