# Plan: api-bicimad

- Created: 2026-10-05
- Status: **implemented** (2026-10-05)
- Spec: [SPEC-api-bicimad.md](../specs/SPEC-api-bicimad.md)

## Overview

Añadir el módulo `modules/bicimad` (dominio, capa anticorrupción, casos de uso y endpoints `/v1/bicimad/stations`), la caché HTTP (`ETag`, `Cache-Control`) y el rate limit por IP en `shared/web`, springdoc solo en desarrollo y el paquete `@jorgetroya80/bicimad-client`, que se publica en GitHub Packages en cada release del API.

## Decisiones de implementación

- **Lista de tareas en este documento.** Como en `api-core`, las casillas de abajo son la fuente de verdad del progreso.
- **Una rama (`feat/api-bicimad`) y un solo PR para todo el módulo, con un commit por tarea.** El repo solo permite squash merge: en `main` queda un commit `feat(api)`; los commits por tarea se conservan en el PR. Tipos: `feat(api)` para comportamiento, `build(api)` para Gradle y el paquete del cliente, `ci` para workflows, `test(api)` para el smoke test.
- **Cortes verticales.** Cada tarea de las fases 1 y 2 deja un endpoint que funciona de punta a punta (EMT simulada → dominio → HTTP). La primera tarea es la traducción de la EMT porque es lo más arriesgado: el formato real ya difiere de la documentación.
- **Tests HTTP con `StationProvider` simulado** (`@WebMvcTest` + `@MockkBean`) para parámetros, orden y errores; WireMock solo para el adaptador (una llamada por TTL, `stale`), como en `api-core`.
- **springdoc en `developmentOnly` + `testImplementation`**; las anotaciones (`swagger-annotations-jakarta`) en `compileOnly`. `generateOpenApi` es una tarea `Test` (etiqueta `openapi`) que arranca el contexto con `StationProvider` simulado, pide `/v3/api-docs` y escribe `build/openapi/bicimad.json`.
- **El paquete del cliente no lleva versión real en git.** `api/client/package.json` tiene `0.0.0`; la release fija la del API con `pnpm version` sin commit.
- **El job `OpenAPI lint` y la carpeta `openapi/` desaparecen**: el lint pasa al job `API (Gradle)`, sobre el JSON generado.

### Versiones

Comprobadas el 2026-10-05 en Maven Central y npm. Todas cumplen la antigüedad mínima de 10 días.

| Dependencia                           | Versión               | Notas                                                                               |
| ------------------------------------- | --------------------- | ----------------------------------------------------------------------------------- |
| `springdoc-openapi-starter-webmvc-ui` | 3.1.1 (2026-09-06)    | Línea 3.x = Spring Boot 4; compatibilidad con Boot 4.1.1 se confirma en T7          |
| `swagger-annotations-jakarta`         | la que trae springdoc | `compileOnly`, sin versión propia en el catálogo si el BOM de springdoc la gestiona |
| `openapi-typescript`                  | 7.13.0                | devDependency del cliente; `peerDependencies: typescript ^5.x`                      |
| `openapi-fetch`                       | 0.17.0                | dependency del cliente                                                              |
| `typescript`                          | 5.9.x                 | No 6.x ni 7.x: `openapi-typescript` 7.13 exige `^5.x`                               |
| `@redocly/cli`                        | 2.x (`npx`)           | Ya se usa en CI                                                                     |

## Grafo de dependencias

```
T1 traducción EMT → dominio
 └── T2 GET /stations (todas) + caché + cupo
        ├── T3 near / radius / need
        │      └── T4 GET /stations/{id}
        │             ├── T5 ETag + Cache-Control
        │             └── T6 rate limit por IP
        └── T7 springdoc dev-only + generateOpenApi       (necesita los endpoints de T3-T4 para un contrato completo)
               └── T8 paquete del cliente + CI
                      └── T9 publicación en la release
T10 smoke test + cierre (tras T9)
```

## Task List

### Fase 1: Endpoints

- [x] **T1: Capa anticorrupción** (M, ~1 h)
  - Descripción: `modules/bicimad/ModuleMetadata.kt` (`@ApplicationModule`), dominio (`Station`, `StationStatus`, `Occupancy`), `EmtStation` con el formato real y `EmtStationMapper` con la tabla de la spec: nombre sin prefijo, `[lon, lat]` → `lat`/`lon`, `activate`/`no_available` → `status`, `light` → `occupancy`, `virtualDelete` descartadas, valores desconocidos → `UNKNOWN` + `WARN`, estaciones incompletas descartadas + `WARN`.
  - Aceptación:
    - SC2 (parte de traducción): las 3 estaciones del fixture producen los valores esperados, incluidos `light = 3` → `UNKNOWN` y `no_available = 1` → `NO_SERVICE`.
    - Casos sintéticos: `virtualDelete`, `light = 7`, sin `geometry`, nombre sin prefijo.
    - `ModularityTest` reconoce `bicimad` como módulo y pasa.
  - Verificación: `./gradlew test --tests '*bicimad*' --tests '*Modularity*'`.
  - Archivos: `ModuleMetadata.kt`, `domain/Station.kt`, `adapters/emt/EmtStation.kt`, `adapters/emt/EmtStationMapper.kt`, `EmtStationMapperTest.kt`.
  - Dependencias: ninguna.

- [x] **T2: `GET /v1/bicimad/stations` sin filtros** (M, ~1 h)
  - Descripción: puerto `StationProvider` y `StationSnapshot`; `EmtStationProvider` (`EmtHttpClient` + `CacheService`, módulo `bicimad`, clave `stations`; 0 estaciones válidas → `EmtProtocolError`); cupo `bicimad` a 3000 en `application.yaml`; `StationsController` y DTOs con `updatedAt`, `stale`, `source`; orden por `number` (numérico y después alfabético). JaCoCo ≥ 80 % también en `modules/bicimad`.
  - Aceptación:
    - SC2 completo: el JSON tiene los campos de la spec y ningún campo de la EMT.
    - SC6: dos peticiones en 60 s = 1 llamada en WireMock; con la EMT caída y caché previa, `stale: true` y el `updatedAt` original.
    - `./gradlew build` falla si la cobertura de `modules/bicimad` baja de 80 %.
  - Verificación: `./gradlew build`; `./gradlew bootRun` + `curl -s localhost:8080/v1/bicimad/stations | jq '.stations | length'`.
  - Archivos: `ports/StationProvider.kt`, `domain/StationSnapshot.kt`, `adapters/emt/EmtStationProvider.kt`, `http/StationsController.kt`, `http/StationResponse.kt`, `application.yaml`, `build.gradle.kts` (+ tests).
  - Dependencias: T1.

- [x] **T3: Filtros `near`, `radius` y `need`** (M, ~1 h)
  - Descripción: `GeoPoint` con haversine, `FindNearbyStations` (radio y orden: disponibles para `need` primero, después distancia o `number`), `StationsQuery` (parseo y validación), `distanceMeters` solo con `near`, `400` problem+json.
  - Aceptación:
    - SC3: solo estaciones a ≤ 500 m, por `distanceMeters` ascendente.
    - SC4: con `need=docks`, las disponibles primero; mismo número de estaciones que sin `need`.
    - SC5 (parte de lista): `radius=6000`, `radius` sin `near`, `near=abc` y `need=car` → `400`.
    - Haversine: Sol–Cibeles (≈ 950 m) con error < 1 %.
  - Verificación: `./gradlew test --tests '*bicimad*'`; `curl` con `near=40.4168,-3.7038&need=docks`.
  - Archivos: `domain/GeoPoint.kt`, `application/FindNearbyStations.kt`, `http/StationsQuery.kt`, `http/StationsController.kt`, `FindNearbyStationsTest.kt`, `StationsControllerTest.kt`.
  - Dependencias: T2.

- [x] **T4: `GET /v1/bicimad/stations/{id}`** (S, ~30 min)
  - Descripción: `GetStation` sobre el mismo snapshot; `StationNotFound` con `@ResponseStatus(NOT_FOUND)` (el `ProblemDetailsHandler` actual ya respeta el código); `id` no numérico → `400`.
  - Aceptación:
    - SC5 completo: `id=abc` → `400`; `id` inexistente o con `virtualDelete` → `404` problem+json.
    - La respuesta es `{ station, updatedAt, stale, source }` sin `distanceMeters`.
  - Verificación: `./gradlew test --tests '*bicimad*'`; `curl localhost:8080/v1/bicimad/stations/1409`.
  - Archivos: `application/GetStation.kt`, `http/StationsController.kt`, `http/StationResponse.kt`, `GetStationTest.kt`, `StationsControllerTest.kt`.
  - Dependencias: T3.

#### Checkpoint 1: endpoints

- [x] SC2-SC6 comprobados; `./gradlew build` en verde en CI.
- [x] Revisión humana del JSON real (`bootRun` contra la EMT) antes de fijar el contrato con springdoc.

### Fase 2: Protección HTTP

- [x] **T5: `ETag` y `Cache-Control`** (S, ~30 min)
  - Descripción: `shared/web/HttpCacheConfig.kt` registra `ShallowEtagHeaderFilter` (ETag débil) y añade `Cache-Control: no-cache` a las respuestas `200` de `/v1/**`.
  - Aceptación:
    - SC7: con el `ETag` recibido, la segunda petición devuelve `304` sin cuerpo; todas las `200` de `/v1/**` llevan `Cache-Control: no-cache`; `/health/*` no cambia.
  - Verificación: `./gradlew test --tests '*HttpCache*'`; `curl -i` dos veces con `If-None-Match`.
  - Archivos: `shared/web/HttpCacheConfig.kt`, `HttpCacheConfigTest.kt`.
  - Dependencias: T4.

- [x] **T6: Rate limit por IP** (M, ~1 h)
  - Descripción: `shared/web/RateLimitFilter.kt`: token bucket por IP en Caffeine (`expireAfterAccess` 10 min, máximo 100.000), estado por IP en una sola referencia atómica (sin locks globales), `Clock` inyectado, propiedades `mad-mobility.rate-limit.capacity` (60) y `refill-per-second` (1), solo `/v1/**`, `429` problem+json con `Retry-After` y `requestId`, métrica `http.rate_limit.rejected` sin la IP.
  - Aceptación:
    - SC8: la petición 61 de una IP en el mismo instante (reloj fijo) → `429` + `Retry-After`; otra IP → `200`; tras avanzar el reloj 1 s vuelve a haber 1 token; `/health/live` nunca → `429`.
    - 50 hilos con la misma IP consumen exactamente 50 tokens (sin carreras).
  - Verificación: `./gradlew test --tests '*RateLimit*'`.
  - Archivos: `shared/web/RateLimitFilter.kt`, `application.yaml`, `RateLimitFilterTest.kt`.
  - Dependencias: T4 (independiente de T5).

#### Checkpoint 2: protección HTTP

- [x] SC7-SC8 comprobados; cobertura de `shared` sigue ≥ 80 %.

### Fase 3: Contrato y cliente

- [x] **T7: springdoc solo en desarrollo y `generateOpenApi`** (M, ~1 h)
  - Descripción: springdoc 3.1.1 en `developmentOnly` y `testImplementation`, anotaciones en `compileOnly`; `springdoc.paths-to-match: /v1/**`, título, versión del API y licencia de los datos en el documento; anotaciones en `StationsController` (descripciones, respuestas `400`/`404`/`429`/`503` con problem+json); `OpenApiExportTest` (`@Tag("openapi")`, excluida de `test`) y tarea `generateOpenApi`; verificación de que `bootJar` no contiene `org/springdoc/**`; `DockerImageTest` comprueba `404` en `/v3/api-docs` y `/swagger-ui.html`.
  - Aceptación:
    - SC9 (local): `./gradlew generateOpenApi` sin `.env.local` ni red escribe `build/openapi/bicimad.json` solo con rutas `/v1/**`; `npx @redocly/cli@2 lint` pasa.
    - SC10: `./gradlew build` falla si `bootJar` contiene springdoc; `./gradlew dockerImageTest` ve `404` en ambas rutas.
    - `./gradlew bootRun` sirve Swagger UI en `/swagger-ui.html`.
  - Verificación: `./gradlew generateOpenApi build dockerImageTest`; abrir Swagger UI con `bootRun`.
  - Archivos: `libs.versions.toml`, `build.gradle.kts`, `application.yaml`, `StationsController.kt`, `OpenApiExportTest.kt`, `DockerImageTest.kt`.
  - Dependencias: T4 (mejor tras T6, para documentar el `429`).

- [x] **T8: Paquete `@jorgetroya80/bicimad-client` y CI** (M, ~1 h)
  - Descripción: `api/client/` (`package.json` con `name`, versión `0.0.0`, `publishConfig.registry` de GitHub Packages, `repository`, `files: [dist, openapi.json]`, scripts `generate` y `build`; `tsconfig.json`; `src/index.ts` con tipos, alias `Station`/`StationsResponse` y `createBicimadClient`), `.gitignore` de `schema.d.ts`, `dist/` y `openapi.json`; `api/client` en `pnpm-workspace.yaml`. En `ci.yml`: el job `API (Gradle)` ejecuta `generateOpenApi`, `redocly lint` y la compilación del cliente; se quitan el job `contract`, el filtro `openapi/**` y su comprobación de existencia (también en `ci-ok` si lo referencia).
  - Aceptación:
    - SC9 y SC11 (CI): en el PR, el job `API (Gradle)` genera, lintea y compila el cliente; `ci-ok` en verde.
    - Romper a propósito un tipo usado en `src/index.ts` (renombrar `Station` en el controlador) hace fallar el job (comprobado y revertido).
    - `pnpm --filter @jorgetroya80/bicimad-client pack` produce un tarball con `dist/` y `openapi.json`.
  - Verificación: `./gradlew generateOpenApi && pnpm --filter @jorgetroya80/bicimad-client run generate build`; checks del PR.
  - Archivos: `api/client/package.json`, `api/client/tsconfig.json`, `api/client/src/index.ts`, `api/client/.gitignore`, `pnpm-workspace.yaml`, `pnpm-lock.yaml`, `.github/workflows/ci.yml`.
  - Dependencias: T7.

- [x] **T9: Publicación del cliente en la release** (S, ~45 min)
  - Descripción: job `publish-api-client` en `release.yml`, tras `release-please` cuando hay release de `api`, independiente de `publish-api`: checkout del tag, Java 25 + `./gradlew generateOpenApi`, pnpm + `generate` + `build`, `pnpm version <api-version> --no-git-tag-version`, `pnpm publish --no-git-checks` a `npm.pkg.github.com` con `GITHUB_TOKEN` (`packages: write`, `contents: read`). Acciones fijadas por SHA, como el resto.
  - Aceptación:
    - El job `Lint workflows` pasa.
    - `pnpm publish --dry-run` en local con la versión fijada muestra `@jorgetroya80/bicimad-client@<versión>` y los archivos esperados.
    - SC12 se confirma en la primera release de `api` tras el merge (se anota en el plan).
  - Verificación: checks del PR; `pnpm publish --dry-run` en local.
  - Archivos: `.github/workflows/release.yml`.
  - Dependencias: T8.

#### Checkpoint 3: contrato y cliente

- [x] SC9-SC11 comprobados en CI; revisión humana de `release.yml` antes del merge.

### Fase 4: Cierre

- [x] **T10: Smoke test y cierre** (S, ~30 min, **requiere tus credenciales en `api/.env.local`**)
  - Descripción: `EmtSmokeTest` añade la traducción real (`EmtStationMapper`) y comprueba > 600 estaciones válidas; spec y plan a **implemented**; `CAPABILITY-MAP.md` actualizado; memoria/notas de `web-shell` sobre cómo instalar el paquete (token de GitHub Packages).
  - Aceptación:
    - SC13: `./gradlew smokeTest` pasa contra la EMT real; resultado anotado en el PR.
    - SC1: `./gradlew build` en verde en CI con JaCoCo ≥ 80 % en `shared` y `modules/bicimad`.
  - Verificación: `./gradlew smokeTest`; checks del PR.
  - Archivos: `src/smokeTest/kotlin/.../EmtSmokeTest.kt`, `SPEC-api-bicimad.md`, `PLAN-api-bicimad.md`, `CAPABILITY-MAP.md`.
  - Dependencias: T9.

#### Checkpoint final

- [x] SC1-SC11 y SC13 de la spec comprobados. SC12 (publicación) se confirma en la primera release de `api` tras el merge.
- [x] Spec y plan pasan a **implemented**; `CAPABILITY-MAP.md` actualizado.

## Riesgos y mitigaciones

| Riesgo                                                                                 | Impacto | Mitigación                                                                                                |
| -------------------------------------------------------------------------------------- | ------- | --------------------------------------------------------------------------------------------------------- |
| springdoc 3.1.1 no funciona con Boot 4.1.1 / Jackson 3 / Kotlin                        | Alto    | Se confirma al empezar T7 con un `bootRun`; si falla, versión anterior de la línea 3.x o pedir aprobación |
| springdoc fuera del jar pero las anotaciones `compileOnly` faltan en runtime           | Bajo    | La JVM ignora anotaciones cuya clase no está; el test de la imagen (T7) arranca la app sin springdoc      |
| `developmentOnly` no entra en el classpath de tests                                    | Medio   | Declararlo también en `testImplementation` (T7)                                                           |
| `redocly lint` falla por reglas del preset (`servers`, `license`, `operationId`)       | Bajo    | Completar el documento con la configuración de springdoc en T7; no relajar reglas sin aprobación          |
| `ShallowEtagHeaderFilter` carga en memoria todo el cuerpo (~150 KB con 677 estaciones) | Bajo    | Aceptable con una instancia; se mide en T5                                                                |
| Instalar desde GitHub Packages requiere token incluso con paquete público              | Medio   | Documentarlo en T10 para `web-shell` (`.npmrc` con `GITHUB_TOKEN` en CI, token personal en local)         |
| La publicación solo se prueba de verdad en una release                                 | Medio   | `pnpm publish --dry-run` en T9; el job es independiente de `publish-api` y se puede relanzar              |
| `id` de la EMT inestable entre días (supuesto 1 de la spec)                            | Bajo    | Afecta a enlaces compartidos (404), no a los datos; se observa con el smoke test en días distintos        |

## Paralelización

Un solo desarrollador: orden T1 → T10. T5 y T6 son independientes entre sí.

## Estimación

Unas 8 h, en 3 sesiones: Fase 1 (~3,5 h), Fase 2 (~1,5 h), Fase 3 (~2,75 h), Fase 4 (~0,5 h). Cada checkpoint es un buen punto para cortar.

## Resultado

- T1-T10 en la rama `feat/api-bicimad`, un commit por tarea, en el PR #36 (squash merge).
- SC1-SC11 y SC13 verificados: `./gradlew build` (182 tests, JaCoCo ≥ 80 % en `shared` y en `modules/bicimad`, este al 99 %), `generateOpenApi` + `redocly lint` sin avisos, `dockerImageTest`, `bootRun` contra la EMT real y `./gradlew smokeTest` (678 estaciones válidas, 0 descartadas).
- Pendiente: SC12 (primera publicación real del cliente) y el README y la licencia del paquete del cliente (decisión del usuario).

## Changelog

- 2026-10-05: versión inicial.
- 2026-10-05: plan aprobado.
- 2026-10-05: T1 implementada. El módulo se declara con `id = "bicimad"` y `allowedDependencies = ["shared"]` (`"shared :: *"` rechaza los subpaquetes `emt` y `web` aunque `shared` sea OPEN). `EmtStationMapper` es un `object` sin estado. Son obligatorios `id`, `number`, `name`, `address`, `dock_bikes`, `free_bases`, `total_bases` y dos coordenadas; si falta cualquiera, la estación se descarta con un solo `WARN`. Si faltan `activate` o `no_available`, la estación es `NO_SERVICE`; si falta `light`, la ocupación es `UNKNOWN`. El rango de las coordenadas lo valida `GeoPoint` en T3.
- 2026-10-05: un solo PR para todo el módulo en lugar de uno por tarea, con un commit por tarea en la rama `feat/api-bicimad`.
- 2026-10-05: T2 implementada. El orden por número es `Station.BY_NUMBER` (dominio, reutilizable en T3) y lo aplica el caso de uso `ListStations`. Los tests HTTP simulan `StationProvider` con un bean de MockK en una `@TestConfiguration`, sin `@MockkBean`, porque SpringMockK no está en el catálogo. JaCoCo para `modules/bicimad` va en su propia tarea `bicimadCoverageVerification`, con el 99 % de líneas. Con `bootRun` contra la EMT real salen 678 estaciones (676 `OPERATIONAL`, 2 `NO_SERVICE`) sin ningún `WARN`. Pendiente: `updatedAt` conserva los microsegundos y Jackson 3 ordena las propiedades alfabéticamente; el contenido coincide con la spec.
- 2026-10-05: T3 implementada. `FindNearbyStations` sustituye a `ListStations`. `Station` guarda un `GeoPoint` ya validado: el mapper descarta con un `WARN` las estaciones con coordenadas fuera de rango, NaN o infinitas, para que una estación mala no provoque un 500. Los 400 usan `ResponseStatusException`, que ya gestiona `ProblemDetailsHandler`. `need` solo se acepta en minúsculas y `near` es estricto (`lat,lon`, sin espacios). El radio se compara con la distancia sin redondear; la distancia se redondea solo en la respuesta. Si dos estaciones están a la misma distancia, conservan el orden de entrada. Corregida la referencia Sol–Cibeles: son ≈ 950 m, no 1,1 km.
- 2026-10-05: T4 implementada. `GetStation` devuelve `FoundStation`; `StationNotFound` va con `@ResponseStatus(NOT_FOUND)` en `application` y el 404 usa el cuerpo genérico del handler, sin el id. Un `id` no numérico o mayor que `Int` da 400 por el type mismatch de Spring. Además, aprobado por el usuario: el mapper recorta los espacios de `name` y `address`, porque la EMT envía `"Plaza del Carmen "`.
- 2026-10-05: T5 implementada. `ShallowEtagHeaderFilter` (ETag débil) va en `/v1/*` justo después de `RequestIdFilter`, así el 304 conserva `X-Request-Id`. Spring solo calcula ETag para GET/HEAD con 2xx, por lo que un error nunca da 304. `Cache-Control: no-cache` lo pone `WebContentInterceptor` en `/v1/**` y llega también a los 4xx/5xx de los handlers; es inocuo y evita que el navegador cachee un 404. Limitarlo solo a los 200 exigiría envolver la respuesta. Además, aprobado por el usuario: `updatedAt` sale en segundos enteros; se trunca en los DTO HTTP, ni la caché ni el dominio cambian. Comprobado con `bootRun`: 200 con `ETag` y `no-cache`, revalidación con 304 y `/health/live` sin cabeceras de caché.
- 2026-10-05: T6 implementada. El orden de los filtros es `RequestIdFilter` → `RateLimitFilter` → ETag. El 429 reutiliza la construcción del problem de `ProblemDetailsHandler`, que pasa a estar en piezas `internal` a nivel de archivo, con tipo `urn:mad-mobility:problem:rate-limited`. Si el reloj retrocede, no se añaden tokens. En los tests la capacidad sube a 1.000.000 en la configuración común, porque todas las peticiones de MockMvc salen de 127.0.0.1; los tests del rate limit fijan sus propios límites. Comprobado con `bootRun`: de 70 peticiones seguidas, 61 dan 200 y 9 dan 429 con `Retry-After: 1`; `/health/live` sigue en 200.
- 2026-10-05: T7 implementada. springdoc 3.1.1 funciona con Boot 4.1.1, Jackson 3 y Kotlin (riesgo cerrado). Las anotaciones (`swagger-annotations-jakarta` 2.2.55) toman la versión del BOM de springdoc. La versión del documento sale de `bootBuildInfo`, importado en `application.yaml`; como efecto secundario, `/info` muestra ahora el artefacto y la versión. La configuración de springdoc es solo de propiedades (sin beans) con `servers: [/]`, y la licencia de los datos va en `info.license`. Los campos obligatorios se declaran con `@Schema(requiredProperties)`: inferirlos de la nulabilidad de Kotlin exigiría `jackson-module-kotlin` de Jackson 2. El test de exportación excluye `/v1/test/**` y falla si los obligatorios cambian. `verifyNoSpringdocInJar` (en `check`) rechaza cualquier entrada `springdoc` o `swagger` en el `bootJar`. `redocly lint` pasa sin errores ni avisos y sin relajar reglas; `dockerImageTest` pasa con 404 en `/v3/api-docs` y `/swagger-ui.html`. Pendiente (resuelto en T8): springdoc tipaba `distanceMeters` como `integer | null` y el esquema `ProblemDetail` mostraba un objeto `properties` que en la respuesta real no existe.
- 2026-10-05: T8 implementada, con la opción A aprobada por el usuario para el esquema. `distanceMeters` deja de ser `integer | null` al desactivar `springdoc.model-converters.kotlin-nullable-property-customizer` (solo configuración). Los errores apuntan a un esquema `Problem` documental (`shared/web/ProblemBody.kt`) con `requestId` al primer nivel y sin `properties`; `OpenApiExportTest` comprueba los dos arreglos. Las versiones del cliente son openapi-fetch 0.17.0, openapi-typescript 7.13.0 y typescript 5.9.3 (`~5.9.3`). `build` copia `schema.d.ts` a `dist/`, porque `tsc` no emite los `.d.ts` de entrada. Los archivos generados del cliente van en `.prettierignore`. El hook pre-push compila el cliente si cambia `api/client/` y ya no mira `openapi/`. CI: el job `API (Gradle)` genera, pasa el lint y compila el cliente; también se ejecuta si cambian `pnpm-lock.yaml` o `pnpm-workspace.yaml`. Se eliminan el job `contract` y los filtros `openapi/**`. Prueba del guardián de tipos: al renombrar el esquema `Problem` en el API fallan el test de exportación y `tsc` del cliente.
- 2026-10-05: T9 implementada. `publish-api-client` en `release.yml`, independiente de `publish-api`, con `packages: write` a nivel de job. La versión del API llega por `env` (sin `${{ }}` dentro de `run`) y se fija con `pnpm version --no-git-tag-version --no-git-checks`, porque pnpm 12 comprueba git incluso sin tag. No usa `cache: pnpm`, para que un job que publica no restaure cachés ajenas. `pnpm publish --dry-run` en local da `@jorgetroya80/bicimad-client@0.2.0` con 5 archivos. Pendiente para la primera release real: confirmar SC12 y, si GitHub Packages lo pide, enlazar el paquete al repo. El README y la licencia del paquete se dejan para más adelante (decisión del usuario).
- 2026-10-05: T10 implementada. `EmtSmokeTest` traduce las estaciones reales con `EmtStationMapper`: 678 válidas y 0 descartadas, con una sola llamada a estaciones por ejecución. Plan implementado.
