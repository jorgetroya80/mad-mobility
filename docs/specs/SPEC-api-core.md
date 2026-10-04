# Spec: api-core

- Created: 2026-10-04
- Status: **approved** (2026-10-04)
- Plan: [PLAN-api-core.md](../plans/PLAN-api-core.md)

Módulo `api-core` del [Capability Map](CAPABILITY-MAP.md). Crea el proyecto Gradle de `api/` y la infraestructura compartida `shared/emt` que usarán los módulos de dominio (`bicimad` primero, `bus` después). Fuente: [docs/ideas/bicimad-now.md](../ideas/bicimad-now.md).

## Objective

Que un módulo de dominio solo tenga que traducir datos de la EMT a su modelo: token, resiliencia, caché, cupo, health y observabilidad ya están resueltos y probados.

- Como módulo de dominio, quiero un cliente EMT que gestione el token, los timeouts, el reintento, el circuit breaker y el chequeo del campo `code`, para que mi adaptador solo traduzca datos.
- Como módulo de dominio, quiero una caché con TTL propio, single-flight y datos antiguos (`stale`) si la EMT falla, para responder rápido y sin gastar cupo.
- Como mantenedor, quiero un límite de cupo diario por módulo que nunca se supere, y ver el consumo en métricas.
- Como operador, quiero `/health/live` y `/health/ready`, logs JSON con request id y métricas de la EMT, para diagnosticar sin acceso al código.
- Como frontend, quiero CORS configurado para mi dominio y errores en formato RFC 9457.

**Fuera de alcance:** endpoints de negocio y dominio (`api-bicimad`), springdoc y la exportación de `openapi/bicimad.json` (`api-bicimad`), rate limit por IP (`api-bicimad`), ETag y `Cache-Control` hacia el cliente (`api-bicimad`), despliegue (`deploy`), Prometheus u otro backend de métricas (`deploy`).

## Assumptions

1. Un solo proyecto Gradle en `api/` (Kotlin DSL, version catalog). Los módulos son paquetes, no subproyectos; Spring Modulith verifica sus límites.
2. Spring MVC bloqueante con hilos virtuales (`spring.threads.virtual.enabled=true`), sin corrutinas ni WebFlux.
3. La autenticación EMT acepta email/contraseña o `X-ClientId` + `passKey`; se elige según las variables de entorno presentes.
4. Contadores de cupo en memoria: un reinicio los pone a cero. Aceptable con una sola instancia y el TTL de 60 s (~1.440 llamadas/día de 20.000).
5. Solo el test de la imagen Docker necesita Docker en local (Testcontainers). Los runners `ubuntu-latest` de GitHub ya lo traen.

## Tech Stack

| Herramienta                      | Versión                                           | Uso                                                               |
| -------------------------------- | ------------------------------------------------- | ----------------------------------------------------------------- |
| Java                             | 25 LTS (Eclipse Temurin)                          | Toolchain de Gradle, imagen Docker                                |
| Kotlin                           | 2.3.21 (la que gestiona Spring Boot 4.1.1)        | Lenguaje                                                          |
| Spring Boot                      | 4.1.1                                             | Web MVC, Actuator, validación, `RestClient`, logging estructurado |
| Spring Modulith                  | 2.1.x (línea de Boot 4.1)                         | Verificación de módulos en tests                                  |
| Resilience4j                     | 2.x (módulos core; starter si existe para Boot 4) | Retry y circuit breaker del cliente EMT                           |
| Caffeine                         | 3.x                                               | Almacenamiento de `CacheService`                                  |
| Micrometer                       | gestionado por Boot                               | Métricas vía Actuator                                             |
| Gradle                           | wrapper 9.3.1                                     | Build (máximo probado con el plugin Kotlin 2.3.21)                |
| Spotless + ktlint                | última estable                                    | Formato (ya lo invoca `lint-staged`)                              |
| JUnit Jupiter                    | gestionado por Boot                               | Tests                                                             |
| MockK + AssertJ                  | última estable                                    | Dobles de prueba y aserciones                                     |
| WireMock (`wiremock-standalone`) | 3.x                                               | EMT simulada por HTTP real, dentro del proceso del test           |
| Testcontainers                   | gestionado por Boot                               | Test de la imagen Docker construida                               |
| JaCoCo                           | plugin de Gradle                                  | Cobertura                                                         |

Las versiones exactas se fijan en `gradle/libs.versions.toml` durante el plan, comprobando compatibilidad con Boot 4.1.1 y la política de antigüedad mínima de 10 días.

## Commands

```bash
cd api

./gradlew build                   # compila, spotlessCheck, tests (unitarios + integración), jacoco
./gradlew test                    # solo tests (el de la imagen Docker requiere Docker)
./gradlew spotlessApply           # formatea
./gradlew smokeTest               # llama a la EMT real; requiere credenciales en .env.local (manual)
./gradlew bootRun                 # arranca en :8080, lee .env.local si existe

docker build -t mad-mobility-api .
docker run --rm -p 8080:8080 --env-file .env.local mad-mobility-api

curl -s localhost:8080/health/live
curl -s localhost:8080/health/ready
curl -s localhost:8080/health      # incluye el detalle del componente emt
```

Variables de entorno (plantilla en `api/.env.example`, valores reales en `api/.env.local`, ignorado por git):

| Variable               | Obligatoria               | Por defecto                    |
| ---------------------- | ------------------------- | ------------------------------ |
| `EMT_BASE_URL`         | no                        | `https://openapi.emtmadrid.es` |
| `EMT_EMAIL`            | sí, o la pareja de abajo  | —                              |
| `EMT_PASSWORD`         | sí, con `EMT_EMAIL`       | —                              |
| `EMT_CLIENT_ID`        | sí, o la pareja de arriba | —                              |
| `EMT_PASS_KEY`         | sí, con `EMT_CLIENT_ID`   | —                              |
| `CORS_ALLOWED_ORIGINS` | no                        | vacío (sin CORS)               |

## Project Structure

```
api/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties              ya existe: version=0.0.0 (release-please)
  gradle/libs.versions.toml
  gradlew, gradlew.bat, gradle/wrapper/
  Dockerfile                     multi-stage; activa el job publish-api existente
  .dockerignore
  .env.example
  src/main/kotlin/io/github/jorgetroya80/madmobility/
    MadMobilityApplication.kt
    shared/
      emt/
        EmtProperties.kt         credenciales, base URL, timeouts, umbrales (validado al arrancar)
        EmtAuth.kt               login, caché del token, single-flight relogin
        EmtHttpClient.kt         RestClient + retry + circuit breaker + chequeo de code + cupo
        EmtException.kt          jerarquía sellada de errores
        CacheService.kt          TTL por módulo, stale-on-error, single-flight
        QuotaTracker.kt          cupo diario por módulo y global
        EmtHealthIndicator.kt    componente "emt" en /health
      web/
        RequestIdFilter.kt       X-Request-Id + MDC
        CorsConfig.kt
        ProblemDetailsHandler.kt RFC 9457 para errores de la EMT
    modules/                     vacío; api-bicimad añade modules/bicimad
  src/main/resources/application.yaml
  src/test/kotlin/...            espejo de main
    ModularityTest.kt
  src/test/resources/emt/        respuestas reales de la EMT, anonimizadas (fixtures)
  src/smokeTest/kotlin/...       source set aparte, excluido de build
```

`shared` no contiene dominio. Los módulos de `modules/*` solo pueden usar `shared` y no se importan entre sí.

## Component Contracts

Este módulo es proveedor; estos contratos son lo que consumen `api-bicimad` y `bus`.

### `EmtAuth`

- Login: `GET /v2/mobilitylabs/user/login/` con cabeceras `email` + `password` o `X-ClientId` + `passKey`.
- Guarda el `accessToken` y lo reutiliza hasta `tokenSecExpiration` menos un margen de 5 min.
- Single-flight: si varias peticiones necesitan token a la vez, se hace un solo login y todas esperan su resultado.
- `invalidate()`: lo llama `EmtHttpClient` cuando la EMT responde con un código de token inválido.
- Cada login consume cupo con el módulo `auth`.

### `EmtHttpClient`

```kotlin
fun <T : Any> get(module: String, path: String, type: KClass<T>): T
```

- Añade la cabecera `accessToken`. Timeouts: conexión 2 s, lectura 5 s.
- Antes de cada llamada pide cupo a `QuotaTracker`; sin cupo lanza `EmtQuotaExceeded` sin llamar a la EMT.
- Reintento: 1, con backoff de 500 ms, solo ante timeout, error de E/S o 5xx. Nunca ante 4xx.
- Circuit breaker (uno para toda la EMT): ventana de 10 llamadas, 50 % de fallos lo abre, 30 s abierto, 2 llamadas de prueba en semiabierto.
- Chequeo de `code` del cuerpo: `00`/`01` es éxito y devuelve `data`; token inválido invalida el token y reintenta una vez con login nuevo; cualquier otro código lanza `EmtProtocolError` con el código y `description`.
- Errores (`EmtException`, sellada): `EmtUnavailable` con motivo `TIMEOUT`, `SERVER_ERROR` (E/S o 5xx) o `CIRCUIT_OPEN`, `EmtQuotaExceeded`, `EmtAuthFailed` (credenciales rechazadas), `EmtProtocolError` (código inesperado o cuerpo ilegible).
- Nunca registra el token, la contraseña ni el `passKey`.

### `CacheService`

```kotlin
data class Cached<T>(val value: T, val updatedAt: Instant, val stale: Boolean)

fun <T : Any> get(module: String, key: String, loader: () -> T): Cached<T>
```

- TTL y antigüedad máxima por módulo en configuración (`mad-mobility.cache.<module>.ttl`, `max-stale`). Por defecto: TTL 60 s, `max-stale` 24 h.
- Dentro del TTL devuelve el valor sin llamar a `loader`.
- Fuera del TTL llama a `loader` una sola vez aunque haya peticiones concurrentes (single-flight).
- Si `loader` lanza `EmtException` y hay un valor anterior más reciente que `max-stale`, devuelve ese valor con `stale = true` y su `updatedAt` original. Si no lo hay, propaga la excepción.
- Las demás excepciones (errores de programación) siempre se propagan.

### `QuotaTracker`

- `tryAcquire(module): Boolean`. Límites en configuración: `mad-mobility.quota.global-daily-limit` (por defecto 18.000, margen sobre 20.000) y `mad-mobility.quota.<module>.daily-limit`.
- Devuelve `false` si se alcanza el límite del módulo o el global.
- Los contadores se reinician a las 00:00 Europe/Madrid (ver Open Questions).
- Si la respuesta de login de la EMT informa del consumo (`apiCounter` o similar), se publica como métrica aparte para compararla con el contador local.

### Health

- `/health/live`: estado de liveness de Spring. `/health/ready`: readiness de Spring. Ninguno depende de la EMT: con la EMT caída se sigue sirviendo caché.
- `/health`: incluye el componente `emt` con estado del circuito, validez del token (sí/no y caducidad) y cupo usado. Nunca el token.
- La configuración inválida (faltan credenciales) hace que la app no arranque, con un mensaje que nombra la variable que falta.
- Actuator solo expone `health` e `info` por HTTP; `metrics` queda habilitado internamente para el registro de Micrometer.

### Observabilidad

- Logs: `logging.structured.format.console=ecs` (JSON) por defecto; perfil `local` con formato legible.
- `RequestIdFilter`: acepta `X-Request-Id` si cumple `[A-Za-z0-9-]{1,64}`, si no genera un UUID. Lo pone en el MDC (`requestId`) y en la respuesta.
- Métricas: `http.client.requests` de `RestClient` (latencia y estado de la EMT), `emt.cache.gets{module,result=hit|miss|stale}`, `emt.quota.used{module}`, `emt.quota.limit{module}`, `emt.auth.logins{result}` y las del circuit breaker de Resilience4j.

### Web

- CORS: orígenes de `CORS_ALLOWED_ORIGINS` (lista separada por comas), métodos `GET`, `HEAD`, `OPTIONS`, ruta `/v1/**`, cabeceras expuestas `ETag` y `X-Request-Id`. Sin orígenes, no hay CORS.
- `ProblemDetailsHandler`: la API solo devuelve códigos HTTP estándar con `application/problem+json` (`type`, `title`, `status`, `detail`, `instance`, `requestId`). Los códigos internos de la EMT (`code`) nunca salen en la respuesta; solo se registran en logs. Se aplica cuando no hay caché que servir:

  | Error interno                       | HTTP                                                                               |
  | ----------------------------------- | ---------------------------------------------------------------------------------- |
  | `EmtUnavailable` (`TIMEOUT`)        | `504 Gateway Timeout`                                                              |
  | `EmtUnavailable` (`SERVER_ERROR`)   | `503 Service Unavailable`                                                          |
  | `EmtUnavailable` (`CIRCUIT_OPEN`)   | `503 Service Unavailable` + `Retry-After` (segundos hasta semiabierto)             |
  | `EmtQuotaExceeded`                  | `503 Service Unavailable` + `Retry-After` (segundos hasta las 00:00 Europe/Madrid) |
  | `EmtAuthFailed`, `EmtProtocolError` | `502 Bad Gateway`                                                                  |
  | Cualquier otra excepción            | `500 Internal Server Error`, sin detalle interno                                   |

  Los errores de petición (`400`, `404`) los define cada módulo con el mismo formato. El `detail` nunca incluye datos sensibles.

### Docker

- Build con `eclipse-temurin:25-jdk` (`./gradlew bootJar`), runtime con `eclipse-temurin:25-jre`, ambas fijadas por digest.
- Capas de Spring Boot (`jarmode=tools extract`), usuario no root, `EXPOSE 8080`.

## Code Style

```kotlin
@Component
class CacheService(
    private val properties: CacheProperties,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
) {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<Any>>()

    fun <T : Any> get(module: String, key: String, loader: () -> T): Cached<T> {
        val entry = store.getIfPresent(key(module, key))
        if (entry != null && entry.isFresh(clock, properties.ttl(module))) {
            return entry.also { record(module, Result.HIT) }
        }
        return try {
            refresh(module, key, loader)
        } catch (e: EmtException) {
            entry?.takeIf { it.isWithin(clock, properties.maxStale(module)) }
                ?.copy(stale = true)
                ?.also { record(module, Result.STALE) }
                ?: throw e
        }
    }
}
```

- Inyección por constructor; nada de `@Autowired` en campos ni `lateinit` para dependencias.
- Configuración con `@ConfigurationProperties` + `@Validated` en data classes inmutables.
- Todo lo que depende de la hora recibe un `Clock` inyectado (tests con reloj fijo).
- Sin `!!`. Errores esperados como excepciones de la jerarquía sellada `EmtException`, no `null`.
- Nombres: clases en `PascalCase`, funciones y propiedades en `camelCase`, claves de configuración en `kebab-case` bajo el prefijo `mad-mobility`.
- Formato: lo que diga Spotless con ktlint; no se discute a mano.

## Testing Strategy

| Nivel       | Herramientas                                    | Qué cubre                                                                                                                      |
| ----------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Unitario    | JUnit, MockK, AssertJ, `Clock` fijo             | `CacheService` (TTL, stale, `max-stale`, single-flight), `QuotaTracker` (límites, reinicio), `EmtAuth` (margen, single-flight) |
| Cliente     | `MockRestServiceServer` (`spring-test`)         | Lógica de `EmtHttpClient` sin red: chequeo de `code`, relogin, mapeo de errores, cupo agotado sin llamada a la EMT             |
| Integración | `@SpringBootTest` + WireMock dentro del proceso | HTTP real: login y cabeceras, timeouts, reintento, circuit breaker, concurrencia (single-flight)                               |
| Imagen      | Testcontainers                                  | La imagen de `api/Dockerfile` arranca como no root y `/health/live` responde 200                                               |
| Web         | `@SpringBootTest` + `MockMvc`                   | `/health/*`, CORS, `X-Request-Id`, problem+json, ausencia de secretos en logs                                                  |
| Modularidad | Spring Modulith `ApplicationModules.verify()`   | `shared` sin dependencias de `modules`; módulos sin dependencias entre sí                                                      |
| Smoke       | source set `smokeTest`, credenciales reales     | Login real y una llamada a BiciMAD; confirma que los fixtures siguen el formato real. Manual, nunca en CI                      |

- Fixtures en `src/test/resources/emt/`, capturados de la EMT real y sin token.
- Los tests de concurrencia (single-flight) usan `CountDownLatch` y comprueban el número de llamadas en WireMock, sin `sleep`.
- JaCoCo: informe en cada build y verificación de al menos 80 % de líneas en el paquete `shared`.
- CI no cambia: el job `API (Gradle)` ya ejecuta `./gradlew build` en cuanto existe `api/build.gradle.kts`.

## Boundaries

- **Always:** credenciales solo en variables de entorno; toda llamada a la EMT pasa por `EmtHttpClient` (cupo y circuit breaker); `Clock` inyectado para lógica temporal; imágenes Docker fijadas por digest; `./gradlew build` en verde antes de abrir PR; Conventional Commits con scope `api`.
- **Ask first:** dependencias fuera de esta tabla; cambiar los valores por defecto de timeouts, reintento, circuit breaker, TTL o cupo; exponer más endpoints de Actuator; cambiar la estructura de paquetes o la detección de módulos; añadir Redis, base de datos o corrutinas.
- **Never:** credenciales o token en el repositorio, en logs, en respuestas o hacia el navegador; llamar a la EMT real desde tests de CI; código de dominio (estaciones, paradas) en `shared`; desactivar o saltar `ModularityTest`; bajar el umbral de cobertura para pasar el build.

## Success Criteria

1. `./gradlew build` pasa en local y en el job `API (Gradle)` de CI, con Spotless, tests, JaCoCo (≥ 80 % en `shared`) y `ModularityTest`.
2. Sin credenciales, la app no arranca y el error nombra la variable que falta.
3. Con 50 peticiones concurrentes y sin token, WireMock recibe exactamente 1 login.
4. El token se reutiliza hasta su caducidad menos 5 min; después se hace un login nuevo.
5. Una respuesta 500 seguida de una 200 termina en éxito con 2 llamadas; dos timeouts seguidos lanzan `EmtUnavailable` en menos de 12 s.
6. Tras 5 fallos en una ventana de 10, el circuito se abre y las llamadas siguientes fallan sin llegar a WireMock; a los 30 s pasa a semiabierto.
7. Un código de token inválido provoca un login nuevo y un solo reintento; un código desconocido lanza `EmtProtocolError` con el código.
8. `CacheService`: dentro del TTL no llama a `loader`; fuera del TTL con `loader` fallando devuelve el valor anterior con `stale = true` y su `updatedAt`; sin valor anterior o pasado `max-stale`, propaga el error; 50 lecturas concurrentes fuera del TTL llaman a `loader` una vez.
9. Con el cupo de un módulo agotado, WireMock no recibe más peticiones de ese módulo y `CacheService` sirve `stale`; con reloj fijo, el contador vuelve a 0 a las 00:00 Europe/Madrid.
10. Con WireMock parado, `/health/live` y `/health/ready` devuelven 200 `UP` y `/health` muestra `emt` con el circuito abierto.
11. Toda respuesta lleva `X-Request-Id`; un valor válido recibido se devuelve igual; los logs JSON incluyen `requestId`; ninguna línea de log de los tests contiene la contraseña, el `passKey` ni el token.
12. Cada error de la tabla de `ProblemDetailsHandler` devuelve su código HTTP y `application/problem+json` sin el `code` de la EMT; `503` por cupo o circuito abierto incluye `Retry-After`.
13. Un preflight CORS desde un origen permitido recibe las cabeceras CORS; desde otro origen, no.
14. `docker build` funciona; un test con Testcontainers comprueba que el contenedor arranca como usuario no root y `/health/live` responde 200.
15. `./gradlew smokeTest` pasa contra la EMT real con credenciales locales (una vez, manual, resultado anotado en el PR).

## Open Questions

- Ninguna.

## Changelog

- 2026-10-04: versión inicial.
- 2026-10-04: resueltas dos preguntas. El cupo se reinicia a las 00:00 Europe/Madrid. Los códigos de la EMT (token inválido, cupo agotado) se fijan con respuestas reales capturadas en el smoke test y solo se usan internamente; la API responde con códigos HTTP estándar (tabla en `ProblemDetailsHandler`).
- 2026-10-04: paquete base `io.github.jorgetroya80.madmobility` confirmado. Spec aprobada.
- 2026-10-04: tests sin Docker salvo el de la imagen: `MockRestServiceServer` para la lógica del cliente y WireMock dentro del proceso para HTTP real (timeouts, reintento, circuit breaker, concurrencia); Testcontainers solo prueba la imagen Docker. Se descarta el módulo alpha de WireMock para Testcontainers.
- 2026-10-04: versiones revisadas antes de implementar: Java 25 LTS (Java 24 sin soporte desde 2025-09-22), Spring Boot 4.1.1 (4.0 pierde soporte el 2026-12-31), Kotlin 2.3.21 (la que gestiona Boot 4.1.1; genera bytecode de Java 25), Gradle 9.3.1 (máximo probado por el plugin Kotlin 2.3.21 es 9.3.0, y Java 25 requiere 9.1.0+).
