# Plan: api-core-cleanup

- Created: 2026-10-05
- Status: **approved** (2026-10-05)
- Spec: [SPEC-api-core.md](../specs/SPEC-api-core.md) (refactor sin cambio de comportamiento; no hay spec nueva)

## Overview

Tres refactors de severidad media salidos de una revisión con el skill `clean-code` sobre `shared/emt` y `shared/web`: single-flight duplicado en `CacheService` y `EmtAuth` (DRY), `EmtAuth.login()` de ~40 líneas y `ProblemDetailsHandler.handleEmt()` de ~42 líneas (funciones pequeñas, un nivel de abstracción). Sin cambios de API pública, mensajes de log, métricas (nombres y tags) ni mensajes de excepción. Los tests actuales son la red de seguridad; solo se añade un test directo para `SingleFlight`.

## Decisiones de implementación

- **Una rama y un PR por tarea**, squash merge, nunca push a `main`. Tipo `refactor(api): ...` (release-please no propone versión por `refactor`).
- **Orden de menor a mayor riesgo.** T1 no toca la EMT; T2 parte una función sin cambiar su lógica; T3 toca concurrencia. T2 va antes que T3 porque las dos editan `EmtAuth.kt`: con `login()` ya partido, el diff de T3 queda limitado a `accessToken()`.
- **`SingleFlight` con clave, una sola forma.** `internal class SingleFlight<V : Any>` en `shared/emt/SingleFlight.kt`, con `ConcurrentHashMap<String, CompletableFuture<V>>` y `fun run(key: String, block: () -> V): V`. `EmtAuth` usa una clave constante (`"token"`): una variante sin clave sería una segunda clase para ahorrarse un string (KISS). Sin interfaz, sin bean de Spring: cada servicio crea la suya (`private val loads = SingleFlight<Cached<Any>>()`).
- **Lo específico se queda en los llamantes**, dentro del `block` (que solo ejecuta el dueño de la clave):
  - La re-comprobación tras tomar la propiedad: `CacheService` pasa `{ store.getIfPresent(key)?.takeIf { isFresh(...) } ?: load(...) }`; `EmtAuth` pasa `{ validToken() ?: renewToken() }`.
  - `authFailure`: `EmtAuth.renewToken()` hace `login()`, guarda el token, limpia `authFailure` y, en `catch (e: EmtAuthFailed)`, lo registra y relanza. Como va dentro del `block`, solo lo registra el dueño, igual que ahora.
- **Semántica que `SingleFlight` conserva tal cual**: `catch (e: Throwable)` (no `Exception`), el dueño lanza la excepción original, los que esperan reciben `e.cause` de la `CompletionException`, y la clave se libera en `finally` con `remove(key, mine)` (equivale al `compareAndSet(mine, null)` actual de `EmtAuth`).
- **Partición de `login()`** en `fetchLogin()` (petición HTTP y fallo de transporte → `record("unavailable")`), `rejectedLoginError(body, status): EmtException` (log de credenciales rechazadas y elección entre `EmtAuthFailed` y `EmtProtocolError`) y `toToken(login)` (cálculo de `renewAt` y log `"EMT login OK..."`). `login()` queda como orquestador de ~12 líneas: cupo, `fetchLogin()`, guarda de 5xx, `rejected`/`success`, `reportEmtUsage`, `toToken()`. La guarda de 5xx se queda en `login()` (una línea; meterla en `fetchLogin()` lo llevaría al límite de 20). `Pair` desestructurado en lugar de un tipo nuevo (YAGNI).
- **`handleEmt()`** queda en ~8 líneas: log, `statusAndProblem(e)`, `retryAfter(e)`, respuesta. Los `when` siguen exhaustivos sin `else` (`EmtException` es `sealed`): una subclase nueva debe romper la compilación. Se permite unir `SERVER_ERROR` y `CIRCUIT_OPEN` en una rama (mismo resultado).
- **Sin herramientas nuevas** (detekt, etc.): fuera del objetivo.

### Quién usa cada archivo

| Archivo                    | Lo usan                                                      | API pública que no cambia                                          |
| -------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------------ |
| `EmtAuth.kt`               | `EmtHttpClient`, `EmtHealthIndicator`, `EmtSmokeTest`        | `accessToken()`, `tokenRenewsAt()`, `invalidate()`, `QUOTA_MODULE` |
| `CacheService.kt`          | ningún código de `main` todavía (módulos de negocio futuros) | `get(module, key, loader)`, `Cached`, `CacheProperties`            |
| `ProblemDetailsHandler.kt` | Spring MVC (`@RestControllerAdvice`)                         | respuestas `problem+json` y `Retry-After`                          |

## Grafo de dependencias

```
T1 ProblemDetailsHandler.handleEmt   (independiente)
T2 EmtAuth.login partido             (independiente de T1)
 └── T3 SingleFlight (CacheService + EmtAuth)   (mismo archivo que T2: después)
```

## Task List

- [x] **T1: Partir `ProblemDetailsHandler.handleEmt()`** (S, ~20 min)
  - Descripción: extraer `statusAndProblem(e: EmtException): Pair<HttpStatus, Problem>` y `retryAfter(e: EmtException): Duration?`; `handleEmt()` solo registra el log, compone y devuelve la respuesta.
  - Aceptación:
    - `handleEmt()` ≤ ~10 líneas; cada función nueva ≤ 20 líneas y sin `else` en el `when` sobre `EmtException`.
    - Mismos códigos, `type`, cuerpo y `Retry-After` para los 8 casos de SC12.
  - Verificación: `cd api && ./gradlew test --tests '*ProblemDetailsHandler*'`; `./gradlew build`.
  - Archivos: `shared/web/ProblemDetailsHandler.kt`. Tests que lo cubren: `ProblemDetailsHandlerTest` (timeout, server-error, circuit-open con `Retry-After` 30, quota con 90, auth, protocol, unexpected, not-found), `EmtSecretsLeakTest`.
  - Dependencias: ninguna.

- [ ] **T2: Partir `EmtAuth.login()`** (S, ~30 min)
  - Descripción: extraer `fetchLogin()`, `rejectedLoginError(body, status)` y `toToken(login)` según las decisiones de arriba. El orden de efectos se mantiene: cupo → HTTP → métrica `emt.auth.logins` → `reportEmtUsage` → log de éxito.
  - Aceptación:
    - `login()` y cada función nueva ≤ 20 líneas, como mucho 2 argumentos.
    - Mismos mensajes de excepción y de log, misma métrica y tags (`success`, `rejected`, `unavailable`).
  - Verificación: `./gradlew test --tests '*EmtAuth*' --tests '*EmtSecretsLeak*' --tests '*EmtQuotaIntegration*'`; `./gradlew build`; si hay credenciales en `.env.local`, `./gradlew smokeTest` (login real).
  - Archivos: `shared/emt/EmtAuth.kt`. Tests que lo cubren: `EmtAuthTest` (las dos credenciales, 5xx, timeout, conexión cortada, fallo al leer el cuerpo, JSON roto, código inesperado, credenciales rechazadas sin secretos en log, cupo, renovación a la mitad), `EmtSecretsLeakTest`, `EmtQuotaIntegrationTest`, `EmtHttpClientHttpTest`, `EmtResilienceTest`.
  - Dependencias: ninguna.

#### Checkpoint 1

- [ ] T1 y T2 en `main`; `./gradlew build` en verde en CI.

- [ ] **T3: `SingleFlight` común para `CacheService` y `EmtAuth`** (M, ~45 min)
  - Descripción: crear `SingleFlight<V>` (con clave, `run(key, block)` y un `join` privado que desenvuelve `CompletionException`) y sustituir con él `CacheService.refresh()`/`join()` y el bloque `inFlight` + `join()` de `EmtAuth.accessToken()`. `EmtAuth` gana `renewToken()` (login + `token.set` + limpiar o registrar `authFailure`). Se borran los dos `join()` duplicados, `inFlight` de ambas clases y los imports de `CompletableFuture`/`CompletionException` que sobren. Test directo pequeño, `SingleFlightTest`: llamadas concurrentes a la misma clave ejecutan el bloque una vez y comparten el resultado; los que esperan reciben la excepción original (no `CompletionException`); tras un fallo, la clave queda libre y la siguiente llamada vuelve a ejecutar el bloque.
  - Aceptación:
    - No queda ningún `CompletableFuture` fuera de `SingleFlight.kt` (`grep` en `src/main`).
    - SC3 (50 llamadas concurrentes → 1 login), login fallido compartido, credenciales rechazadas sin reintento durante 1 min y SC8 (50 lecturas concurrentes → 1 llamada al `loader`, fallo compartido con `stale`) siguen en verde.
    - `accessToken()` ≤ ~6 líneas; `SingleFlight.run()` ≤ 15 líneas.
  - Verificación: `./gradlew test --tests '*SingleFlight*' --tests '*CacheService*' --tests '*EmtAuth*'`; `./gradlew build` (JaCoCo ≥ 80 % en `shared`).
  - Archivos: `shared/emt/SingleFlight.kt` (nuevo), `shared/emt/CacheService.kt`, `shared/emt/EmtAuth.kt`, `SingleFlightTest.kt` (nuevo). Tests que lo cubren: `CacheServiceTest` (`concurrent reads after the TTL call the loader once`, `concurrent callers share a failing load and all get stale data`, `programming errors are never hidden behind stale data`), `EmtAuthTest` (`concurrent callers share a single login`, `concurrent callers share a failing login`, `rejected credentials are not retried for a minute`, `invalidate forces a new login only for the rejected token`), `EmtHttpClientHttpTest`, `EmtSecretsLeakTest`.
  - Dependencias: T2 (mismo archivo).

#### Checkpoint final

- [ ] `./gradlew build` en verde en CI; ningún test existente modificado salvo el nuevo `SingleFlightTest`.
- [ ] Plan pasa a **implemented**.

## Fuera de alcance

Hallazgos menores de la misma revisión, sin tarea en este plan:

- Adquisición de cupo duplicada: `EmtAuth.kt:84` y `EmtHttpClient.kt:68`.
- `EmtUnavailable.Reason.SERVER_ERROR` usado también para fallos de transporte (nombre engañoso; cambiarlo afecta al mapeo HTTP).
- Números mágicos: `apply(1)` en `Retry-After`, `+999 / 1000` en `toSecondsCeil()`, `"Europe/Madrid"`.
- `module` y `path` pasados de función en función dentro de `EmtHttpClient`.
- Atómicos redundantes bajo lock en `QuotaTracker`.
- Pequeñas incoherencias de estilo y algo de YAGNI leve.

## Riesgos y mitigaciones

| Riesgo                                                                         | Impacto | Mitigación                                                                                                 |
| ------------------------------------------------------------------------------ | ------- | ---------------------------------------------------------------------------------------------------------- |
| `authFailure` registrado fuera del `block` (lo harían también los que esperan) | Bajo    | Va en `renewToken()`, dentro del `block`; lo cubre `rejected credentials are not retried for a minute`     |
| Cambio sutil al desenvolver excepciones (dueño vs. los que esperan)            | Medio   | `SingleFlightTest` comprueba el tipo original; los tests de fallo compartido de `CacheService` y `EmtAuth` |
| Tests concurrentes intermitentes                                               | Bajo    | Los actuales no usan `sleep`; `SingleFlightTest` sincroniza con `CountDownLatch`, sin esperas por tiempo   |

## Estimación

~1 h 35 min en una sesión: T1 (~20 min), T2 (~30 min), T3 (~45 min).

## Changelog

- 2026-10-05: versión inicial.
