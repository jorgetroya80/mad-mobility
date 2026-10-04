# Plan: api-core

- Created: 2026-10-04
- Status: **approved** (2026-10-04)
- Spec: [SPEC-api-core.md](../specs/SPEC-api-core.md)

## Overview

Crear el proyecto Gradle de `api/` y la infraestructura `shared/emt` descrita en la spec: autenticación EMT, cliente HTTP resiliente, cupo, caché con datos antiguos, health, logs JSON con request id, métricas, CORS, errores RFC 9457 e imagen Docker. No hay endpoints de negocio; lo que necesita un endpoint para probarse usa un controlador que solo existe en los tests.

## Decisiones de implementación

- **Lista de tareas en este documento.** Como en `foundation`, las casillas de abajo son la fuente de verdad del progreso.
- **Una rama y un PR por tarea**, con squash merge. Tipos: `build(api)` para el esqueleto y Docker, `feat(api)` para comportamiento, `test(api)` para el smoke test. El primer `feat(api)` hará que release-please proponga `api` 0.1.0.
- **Orden por riesgo.** La captura de respuestas reales de la EMT (T4) va antes que el cliente: los códigos de error y el formato real deciden el diseño de `EmtHttpClient`.
- **Tests sin Docker salvo el de la imagen.** `MockRestServiceServer` para la lógica de `EmtHttpClient` (rápido, sin red); WireMock dentro del proceso (`wiremock-standalone`, con Jetty y Jackson sombreados para no chocar con Boot 4) para lo que necesita HTTP real: timeouts, reintento, circuit breaker y concurrencia. Testcontainers solo arranca la imagen de T11.
- **Controlador solo de test** (`src/test/.../TestEmtController`) para probar `ProblemDetailsHandler`, CORS y `X-Request-Id` sin adelantar endpoints de `api-bicimad`.
- **JaCoCo con umbral desde T9**, cuando `shared/emt` ya está completo; antes solo genera el informe.

### Versiones

Comprobadas en Maven Central el 2026-10-04. Las que gestiona Spring Boot no se declaran en el catálogo.

| Dependencia                                | Versión            | Notas                                                                                                               |
| ------------------------------------------ | ------------------ | ------------------------------------------------------------------------------------------------------------------- |
| Gradle wrapper                             | 9.3.1              | El plugin Kotlin 2.3.21 está probado hasta 9.3.0; Java 25 requiere 9.1.0+                                           |
| Spring Boot (plugin y BOM)                 | 4.1.1              | Gestiona Kotlin 2.3.21, JUnit Jupiter 6.0.3, Testcontainers 2.0.5, Micrometer 1.17.1, Caffeine 3.2.4, Jackson 3.1.5 |
| Spring Modulith BOM                        | 2.1.1              | Línea 2.1, publicada junto a Boot 4.1; se confirma en T2                                                            |
| Resilience4j (`resilience4j-spring-boot4`) | 2.4.0              | Compilado contra Boot 4.0.0; se valida con Boot 4.1.1 en T7                                                         |
| `wiremock-standalone`                      | 3.13.2             | Última estable; la 4.x sigue en beta                                                                                |
| MockK                                      | 1.14.11            |                                                                                                                     |
| SpringMockK                                | 5.0.1              | `@MockkBean` en tests de Spring                                                                                     |
| Spotless (plugin de Gradle)                | 8.10.3             | Con ktlint                                                                                                          |
| `eclipse-temurin`                          | `25-jdk`, `25-jre` | Java 25 LTS, fijadas por digest en el `Dockerfile`                                                                  |

## Grafo de dependencias

```
T1 esqueleto Gradle
 ├── T2 health + Actuator + ModularityTest
 └── T3 request id + logs JSON + CORS
        └── T4 EmtProperties + fixtures reales
               └── T5 EmtAuth (+ base WireMock)
                      └── T6 EmtHttpClient (code, relogin, timeouts)
                             └── T7 retry + circuit breaker
                                    └── T8 QuotaTracker
                                           └── T9 CacheService + umbral JaCoCo
                                                  └── T10 ProblemDetailsHandler + EmtHealthIndicator
                                                         ├── T11 Dockerfile
                                                         └── T12 smoke test contra la EMT real
```

## Task List

### Fase 1: Esqueleto

- [x] **T1: Proyecto Gradle mínimo** (M, ~45 min)
  - Descripción: wrapper 9.3.1, `settings.gradle.kts`, `build.gradle.kts` (plugins de Kotlin, Spring Boot, Spotless con ktlint y JaCoCo solo con informe; toolchain Java 25; `version` leída de `gradle.properties`), `gradle/libs.versions.toml`, `MadMobilityApplication.kt`, `application.yaml` (hilos virtuales, `spring.config.import=optional:file:.env.local[.properties]`), test de arranque de contexto.
  - Aceptación:
    - `./gradlew build` pasa en local.
    - En el PR se ejecuta el job `API (Gradle)` con Java 25 (`java-version: '25'` en `ci.yml`; antes se omitía) y `ci-ok` queda en verde.
    - Un `.kt` mal formateado sale formateado al hacer commit (`lint-staged` + `spotlessApply`).
  - Verificación: `cd api && ./gradlew build`; revisar los checks del PR; commit de prueba con formato roto.
  - Archivos: `api/settings.gradle.kts`, `api/build.gradle.kts`, `api/gradle/libs.versions.toml`, `api/src/main/kotlin/.../MadMobilityApplication.kt`, `api/src/main/resources/application.yaml`, `.github/workflows/ci.yml` (+ wrapper generado y test de contexto).
  - Dependencias: ninguna.

- [x] **T2: Health, Actuator y verificación de módulos** (S, ~30 min)
  - Descripción: grupos de health `live` y `ready` servidos en `/health/live` y `/health/ready` (base path de Actuator en `/`), exposición HTTP solo de `health` e `info`, `ModularityTest` con Spring Modulith 2.0.8 y la detección configurada para que `shared` y cada `modules/<x>` sean módulos.
  - Aceptación:
    - `/health/live` y `/health/ready` devuelven 200 `UP`; `/actuator/metrics` y `/metrics` devuelven 404.
    - `ModularityTest` pasa y falla si se añade una clase en `shared` que importa de `modules` (comprobado a mano y revertido).
  - Verificación: `./gradlew test`; `./gradlew bootRun` y `curl` a los tres endpoints.
  - Archivos: `application.yaml`, `build.gradle.kts`, `libs.versions.toml`, `ModularityTest.kt`, `HealthEndpointsTest.kt`.
  - Dependencias: T1.

- [x] **T3: Request id, logs JSON y CORS** (M, ~45 min)
  - Descripción: `RequestIdFilter` (valida o genera, MDC `requestId`, cabecera en la respuesta), logging estructurado ECS por defecto y legible con el perfil `local`, `CorsConfig` desde `CORS_ALLOWED_ORIGINS`, `TestEmtController` mínimo para los tests web.
  - Aceptación:
    - SC11 parcial: toda respuesta lleva `X-Request-Id`; uno válido se devuelve igual; uno inválido se sustituye; la salida de log capturada es JSON con `requestId`.
    - SC13: preflight desde un origen permitido recibe cabeceras CORS; desde otro, no; sin orígenes configurados, nunca.
  - Verificación: `./gradlew test`.
  - Archivos: `RequestIdFilter.kt`, `CorsConfig.kt`, `application.yaml`, `RequestIdFilterTest.kt`, `CorsConfigTest.kt` (+ `TestEmtController.kt`).
  - Dependencias: T1.

#### Checkpoint 1: esqueleto

- [ ] `./gradlew build` en verde en local y en CI.
- [ ] Revisión humana antes de tocar la EMT.

### Fase 2: Cliente EMT

- [x] **T4: Propiedades EMT y respuestas reales** (S, ~40 min, **requiere tus credenciales en `api/.env.local`**)
  - Descripción: `EmtProperties` validadas (`@Validated`: email+contraseña o clientId+passKey, base URL, timeouts), `api/.env.example`. Captura con `curl` de respuestas reales, anonimizadas (sin token ni email): login correcto, login con credenciales malas, estaciones de BiciMAD (recortadas a 3) y petición con token inválido. Se documenta en `src/test/resources/emt/README.md` qué código devuelve cada caso.
  - Aceptación:
    - SC2: sin credenciales, la app no arranca y el error nombra la variable que falta.
    - Los fixtures no contienen el token, el email ni la contraseña (`grep` en el PR).
    - Los códigos de token inválido y credenciales malas quedan anotados; el de cupo agotado se toma de la documentación y se marca como no verificado.
  - Verificación: `./gradlew test`; `./gradlew bootRun` sin `.env.local`; `grep -ri` del email en `src/test/resources`.
  - Archivos: `EmtProperties.kt`, `.env.example`, `EmtPropertiesTest.kt`, `src/test/resources/emt/*.json`, `src/test/resources/emt/README.md`.
  - Dependencias: T3.

- [x] **T5: `EmtAuth`** (M, ~1 h)
  - Descripción: login con las dos variantes de credenciales, token guardado hasta caducidad menos 5 min (`Clock` inyectado), single-flight, `invalidate()`, jerarquía `EmtException` inicial (`EmtAuthFailed`, `EmtUnavailable`). Clase base `EmtWireMockTest` con un servidor WireMock dentro del proceso, en puerto aleatorio y reiniciado entre tests.
  - Aceptación:
    - SC3: 50 peticiones concurrentes sin token producen exactamente 1 login en WireMock.
    - SC4: con reloj fijo, el token se reutiliza hasta caducidad menos 5 min y después hay login nuevo.
    - Credenciales rechazadas lanzan `EmtAuthFailed`; ningún log contiene la contraseña ni el token.
  - Verificación: `./gradlew test --tests '*EmtAuth*'`.
  - Archivos: `EmtAuth.kt`, `EmtException.kt`, `EmtWireMockTest.kt`, `EmtAuthTest.kt`, `libs.versions.toml`.
  - Dependencias: T4.

- [x] **T6: `EmtHttpClient` sin resiliencia** (M, ~1 h)
  - Descripción: `get(module, path, type)` sobre `RestClient` con timeouts (2 s conexión, 5 s lectura), cabecera `accessToken`, chequeo de `code` (`00`/`01` éxito) también en respuestas HTTP 200, relogin y un reintento ante token inválido (HTTP 401 con `code` `80`, cuyo cuerpo hay que leer), `EmtProtocolError` y `EmtUnavailable` con motivo (`TIMEOUT`, `SERVER_ERROR`). Lógica probada con `MockRestServiceServer`; el timeout real, con WireMock.
  - Aceptación:
    - SC7: token inválido provoca un login nuevo y un solo reintento; un código desconocido lanza `EmtProtocolError` con el código.
    - Una respuesta lenta (> 5 s) lanza `EmtUnavailable(TIMEOUT)`; un 500 lanza `EmtUnavailable(SERVER_ERROR)`.
    - Los fixtures de T4 se deserializan sin errores.
  - Verificación: `./gradlew test --tests '*EmtHttpClient*'`.
  - Archivos: `EmtHttpClient.kt`, `EmtException.kt`, `EmtHttpClientTest.kt`, `EmtHttpClientTimeoutTest.kt`.
  - Dependencias: T5.

- [ ] **T7: Reintento y circuit breaker** (S, ~45 min)
  - Descripción: Resilience4j 2.4.0 (`resilience4j-spring-boot4`): 1 reintento con 500 ms solo ante timeout, E/S o 5xx; circuit breaker único (ventana 10, 50 %, 30 s abierto, 2 en semiabierto) con `EmtUnavailable(CIRCUIT_OPEN)`; métricas de Resilience4j en Micrometer. Configuración en `application.yaml`.
  - Aceptación:
    - SC5: 500 + 200 termina en éxito con 2 llamadas; dos timeouts lanzan `EmtUnavailable` en menos de 12 s; un 4xx no se reintenta.
    - SC6: tras 5 fallos en 10 llamadas, las siguientes fallan sin llegar a WireMock; a los 30 s (reloj o espera configurada en test) pasa a semiabierto.
  - Verificación: `./gradlew test --tests '*EmtHttpClient*'`.
  - Archivos: `EmtHttpClient.kt`, `application.yaml`, `libs.versions.toml`, `build.gradle.kts`, `EmtResilienceTest.kt`.
  - Dependencias: T6.

#### Checkpoint 2: cliente EMT

- [ ] SC2-SC7 comprobados.
- [ ] Revisión humana: diseño del cliente y códigos de la EMT.

### Fase 3: Cupo, caché y errores

- [ ] **T8: `QuotaTracker`** (S, ~45 min)
  - Descripción: contadores diarios por módulo y global (18.000 por defecto), reinicio a las 00:00 Europe/Madrid con `Clock`, integración en `EmtHttpClient` (sin cupo no hay llamada; el login cuenta como módulo `auth`), métricas `emt.quota.used` y `emt.quota.limit`, y métrica aparte del consumo que informe la EMT si viene en el login.
  - Aceptación:
    - SC9 parcial: con el cupo de un módulo agotado, `EmtHttpClient` lanza `EmtQuotaExceeded` y WireMock no recibe la petición.
    - Con reloj fijo a las 23:59:59 y luego 00:00:00 Europe/Madrid, el contador vuelve a 0 (también en el cambio de horario).
  - Verificación: `./gradlew test --tests '*Quota*'`.
  - Archivos: `QuotaTracker.kt`, `EmtHttpClient.kt`, `application.yaml`, `QuotaTrackerTest.kt`, `EmtQuotaIntegrationTest.kt`.
  - Dependencias: T7.

- [ ] **T9: `CacheService` y umbral de cobertura** (M, ~1 h)
  - Descripción: Caffeine, TTL y `max-stale` por módulo (60 s y 24 h por defecto), single-flight, `stale = true` con el `updatedAt` original ante `EmtException`, métrica `emt.cache.gets`. Activa la verificación de JaCoCo (≥ 80 % de líneas en `shared`).
  - Aceptación:
    - SC8 completo (TTL, stale, `max-stale`, 50 lecturas concurrentes con 1 llamada a `loader`).
    - SC9 completo: con cupo agotado, `CacheService` sirve el dato anterior con `stale = true`.
    - `./gradlew build` falla si la cobertura de `shared` baja de 80 %.
  - Verificación: `./gradlew build`; informe en `api/build/reports/jacoco`.
  - Archivos: `CacheService.kt`, `application.yaml`, `build.gradle.kts`, `CacheServiceTest.kt`.
  - Dependencias: T8.

- [ ] **T10: `ProblemDetailsHandler` y `EmtHealthIndicator`** (M, ~1 h)
  - Descripción: tabla de errores de la spec (504, 503 con `Retry-After`, 502, 500) en `application/problem+json` con `requestId` y sin el `code` de la EMT; componente `emt` en `/health` (circuito, token válido y caducidad, cupo usado), fuera de los grupos `live` y `ready`.
  - Aceptación:
    - SC12: cada error devuelve su código, `problem+json` y `Retry-After` donde toca; el cuerpo no contiene el `code` de la EMT.
    - SC10: con WireMock parado, `/health/live` y `/health/ready` siguen en 200 y `/health` muestra `emt` con el circuito abierto.
    - SC11 completo: ninguna línea de log de la suite contiene la contraseña, el `passKey` ni el token.
  - Verificación: `./gradlew test`.
  - Archivos: `ProblemDetailsHandler.kt`, `EmtHealthIndicator.kt`, `TestEmtController.kt`, `ProblemDetailsHandlerTest.kt`, `EmtHealthIndicatorTest.kt`.
  - Dependencias: T9.

#### Checkpoint 3: infraestructura completa

- [ ] SC1-SC13 comprobados; `./gradlew build` en verde en CI.
- [ ] Revisión humana antes de la entrega.

### Fase 4: Entrega

- [ ] **T11: Imagen Docker** (S, ~30 min)
  - Descripción: `Dockerfile` multi-stage (`eclipse-temurin:25-jdk` para `bootJar`, `25-jre` en runtime, ambas por digest), capas con `jarmode=tools extract`, usuario no root, `EXPOSE 8080`; `.dockerignore`. Test con Testcontainers (`ImageFromDockerfile`) que construye la imagen, la arranca y comprueba `/health/live` y el usuario.
  - Aceptación:
    - SC14: `docker build` funciona; `docker run` arranca como usuario no root (`docker exec ... id -u` ≠ 0) y `/health/live` responde 200.
    - `publish-api` deja de omitirse en la siguiente release (se confirma cuando llegue).
  - Verificación: `docker build -t mad-mobility-api api && docker run --rm -p 8080:8080 --env-file api/.env.local mad-mobility-api`.
  - Archivos: `api/Dockerfile`, `api/.dockerignore`, `DockerImageTest.kt`, `build.gradle.kts`.
  - Dependencias: T10.

- [ ] **T12: Smoke test contra la EMT real** (S, ~30 min, **requiere tus credenciales**)
  - Descripción: source set `smokeTest` y tarea `./gradlew smokeTest` (no la ejecuta `build`): login real, una llamada a estaciones de BiciMAD y comprobación de que la respuesta encaja con los fixtures de T4.
  - Aceptación:
    - SC15: `./gradlew smokeTest` pasa con credenciales locales; resultado anotado en el PR.
    - `./gradlew build` no ejecuta el smoke test ni necesita credenciales.
  - Verificación: `./gradlew smokeTest`; `./gradlew build` sin `.env.local`.
  - Archivos: `build.gradle.kts`, `src/smokeTest/kotlin/.../EmtSmokeTest.kt`.
  - Dependencias: T10 (puede ir en paralelo con T11).

#### Checkpoint final

- [ ] SC1-SC15 de la spec comprobados.
- [ ] Spec y plan pasan a **implemented**; `CAPABILITY-MAP.md` actualizado.

## Riesgos y mitigaciones

| Riesgo                                                           | Impacto | Mitigación                                                                                                                  |
| ---------------------------------------------------------------- | ------- | --------------------------------------------------------------------------------------------------------------------------- |
| WireMock comparte classpath con Boot 4 (Jetty, Jackson)          | Medio   | Usar `wiremock-standalone`, que los sombrea; si aun así choca, contenedor de WireMock con Testcontainers (pedir aprobación) |
| No se puede provocar un "cupo agotado" real para capturarlo      | Medio   | Código tomado de la documentación y marcado como no verificado; `QuotaTracker` corta antes de llegar a él                   |
| El formato real de la EMT difiere de la documentación            | Medio   | Fixtures reales en T4, antes de escribir el cliente; smoke test en T12                                                      |
| Spring Modulith 2.1.x no fuera la línea de Boot 4.1              | Bajo    | Confirmarlo en T2 con el árbol de dependencias (`./gradlew dependencies`)                                                   |
| `DockerImageTest` lento en cada build                            | Bajo    | Etiquetarlo y medirlo en T11; si pasa de ~1 min, moverlo a una tarea aparte (pedir aprobación)                              |
| `resilience4j-spring-boot4` 2.4.0 no declara soporte de Boot 4.1 | Medio   | Validarlo en T7; si falla, módulos core sin starter (pedir aprobación)                                                      |
| Docker no disponible en local                                    | Bajo    | Solo afecta a `DockerImageTest`; el resto de la suite no necesita Docker                                                    |

## Paralelización

Un solo desarrollador: orden secuencial T1 → T12. T2 y T3 son independientes entre sí, igual que T11 y T12.

## Estimación

Unas 9 h en total, en 3 o 4 sesiones de fin de semana: Fase 1 (~2 h), Fase 2 (~3,5 h), Fase 3 (~2,5 h), Fase 4 (~1 h). Cada checkpoint es un buen punto para cortar la sesión.

## Changelog

- 2026-10-04: versión inicial.
- 2026-10-04: WireMock dentro del proceso y `MockRestServiceServer` en lugar de Testcontainers con el módulo alpha de WireMock; Testcontainers solo para la imagen Docker (T11).
- 2026-10-04: Java 25 LTS, Spring Boot 4.1.1, Kotlin 2.3.21, Gradle 9.3.1 y Spring Modulith 2.1.1 (ver changelog de la spec). T1 cambia `java-version` a `'25'` en `ci.yml`.
- 2026-10-04: plan aprobado.
- 2026-10-04: T4 captura los códigos reales de la EMT (ver `api/src/test/resources/emt/README.md`): credenciales malas = HTTP 200 + `89`; token inválido o ausente = HTTP 401 + `80`. T6 lo tiene en cuenta. Las credenciales se validan en el constructor de `EmtProperties` en lugar de con `@Validated`, para nombrar la variable que falta sin añadir Bean Validation.
- 2026-10-04: T5 implementa `invalidate(rejectedToken)` en lugar de `invalidate()`: solo descarta el token si sigue siendo el rechazado, para no tirar uno renovado por otra petición concurrente. Añade `spring-boot-starter-restclient` (parte de Spring Boot; en Boot 4 `RestClient.Builder` vive en su propio starter) y el bean `Clock` en Europe/Madrid.
- 2026-10-04: en T5, el read timeout del cliente JDK de Spring cubre también la lectura del cuerpo; un error de E/S al leerlo se trata como `EmtUnavailable(SERVER_ERROR)`, no como `EmtProtocolError`. Los tests usan los timeouts de producción salvo el test de timeout (un timeout de 500 ms hacía fallar CI de forma intermitente).
- 2026-10-04: en T6, `EmtHttpClient.get(module, path, elementType: Class<T>)` devuelve `List<T>` (el array `data` de la EMT) en lugar de `T`. Si la EMT rechaza también el token recién emitido, lanza `EmtProtocolError` con el código `80`. Se añade MockK para simular `EmtAuth` en los tests con `MockRestServiceServer`.
