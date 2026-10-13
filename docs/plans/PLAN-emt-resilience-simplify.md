# Plan: shared/emt — quitar el circuit breaker

- Created: 2026-10-12
- Status: **implemented** (2026-10-12)
- Spec: no aplica (limpieza/refactor, no feature nueva; discutido con el agente `spring-boot-engineer`)

## Overview

`EmtHttpClient` envuelve cada llamada con Resilience4j `CircuitBreaker` + `Retry`. Para este proyecto (una sola
dependencia externa, tráfico bajo/personal, sin necesidad de aislar fallos entre múltiples callers) el circuit
breaker es overengineering: añade estado (ventana deslizante, half-open, umbrales) y superficie de testing sin
valor de aprendizaje proporcional. Se quita y se deja solo timeout + retry (opción 1 de la discusión).

## Decisiones de implementación

- **Una rama (`refactor/emt-drop-circuit-breaker`) y un solo PR, con un commit por tarea.** Sin `Co-Authored-By`.
- **Lo implementa el agente `spring-boot-engineer`** con la skill clean-code.
- **El health indicator deja de bajar por el circuit breaker.** Sin breaker no hay señal de "abierto"; el health
  de `emt` se simplifica a token válido + cuota (ver T1). El endpoint sigue sin estar en los grupos live/ready, así
  que esto no afecta a Kubernetes.
- **`EmtUnavailable.Reason.CIRCUIT_OPEN` se elimina** del enum; su respuesta HTTP (503) la sigue cubriendo
  `SERVER_ERROR`/`CONNECTION_FAILED`, que ya mapean a `SERVICE_UNAVAILABLE`.
- **El `Retry` no cambia** (2 intentos, 500 ms, solo en `EmtUnavailable`): sigue siendo la única resiliencia.
- **No se toca la dependencia `resilience4j-spring-boot4`** en `build.gradle.kts`: `Retry` sigue viniendo de ahí.

## Grafo de dependencias

```
T1 quitar circuit breaker de producción (bean, cliente, health, problem handler, enum, yaml)
 └── T2 limpiar tests y verificar
      └── T3 actualizar documentación (SPEC-api-core.md, bicimad-now.md)
```

## Task List

- [x] **T1: Quitar el circuit breaker de producción** (M, ~40 min)
  - Descripción:
    - `EmtConfig.kt`: borrar el bean `emtCircuitBreaker` y el import de `CircuitBreakerRegistry`.
    - `EmtHttpClient.kt`: quitar el parámetro `emtCircuitBreaker`, el `catch (CallNotPermittedException)` y el
      wrapper `emtCircuitBreaker.executeSupplier { ... }` — queda `emtRetry.executeSupplier { getOnce(...) }`.
      Actualizar el KDoc de `get()`.
    - `EmtException.kt`: quitar `CIRCUIT_OPEN` de `EmtUnavailable.Reason`.
    - `EmtHealthIndicator.kt`: quitar la dependencia `CircuitBreaker` y el detalle/estado derivado de ella; el
      indicador pasa a reportar `up` siempre que pueda construirse (token + cuota siguen como detalles).
    - `ProblemDetailsHandler.kt`: quitar la dependencia `emtCircuitBreaker`, la rama `CIRCUIT_OPEN` en
      `statusAndProblem` y la rama de `retryAfter` que leía `waitIntervalFunctionInOpenState`.
    - `application.yaml`: borrar el bloque `resilience4j.circuitbreaker`.
  - Aceptación:
    - El proyecto compila sin referencias a `io.github.resilience4j.circuitbreaker.*` en `src/main`.
    - Los tests existentes de `EmtHttpClientHttpTest`/`EmtResilienceTest` que fuerzan 5xx, timeout o conexión
      cortada siguen pasando con el mismo status HTTP mapeado (503/504), sin ningún estado "open" de por medio.
  - Verificación: `./gradlew compileKotlin`.
  - Archivos: `EmtConfig.kt`, `EmtHttpClient.kt`, `EmtException.kt`, `EmtHealthIndicator.kt`,
    `ProblemDetailsHandler.kt`, `application.yaml`.
  - Dependencias: ninguna.

- [x] **T2: Limpiar tests y verificar** (M, ~40 min)
  - Descripción: quitar o adaptar todo lo que referenciaba el circuit breaker o `CIRCUIT_OPEN`:
    - `EmtResilienceConfigTest.kt`: borrar los tests de `circuitBreaker` (config, apertura al 50%, métricas);
      renombrar el fichero a `EmtRetryConfigTest.kt` si solo queda el test de `retry`.
    - `EmtResilienceTest.kt`: borrar los tests de apertura/half-open del circuito y el de "calls stopped by the
      quota are neither failures nor successes for the circuit"; quitar el `circuitBreaker` del constructor de
      `client(...)` y de los `EmtHttpClient(...)` de los tests que quedan; renombrar a `EmtRetryTest.kt` si procede.
    - Revisar y actualizar `SingleFlightTest.kt`, `CacheServiceTest.kt`, `EmtAuthTest.kt`,
      `EmtHttpClientHttpTest.kt`, `EmtHttpClientTest.kt`, `TestEmtController.kt`: quitar el parámetro
      `emtCircuitBreaker` al construir `EmtHttpClient` y cualquier referencia a `Reason.CIRCUIT_OPEN`.
    - Buscar y quitar cualquier aserción sobre el detalle `circuitBreaker` del health en tests de
      `EmtHealthIndicator` o de endpoint `/health`.
  - Aceptación: `./gradlew build` en verde, sin tests de circuit breaker restantes
    (`grep -r CircuitBreaker api/src` no debe dar resultados fuera de `build/`).
  - Verificación: `./gradlew build`; `./gradlew smokeTest` si hay `.env.local`.
  - Archivos: todos los tests listados arriba.
  - Dependencias: T1.

- [x] **T3: Actualizar documentación** (S, ~20 min)
  - Descripción: `SPEC-api-core.md` está **implementada**, así que no se reescribe su historia: se corrigen las
    menciones al circuit breaker que ya no aplican (user stories, tabla de stack, estructura de paquetes,
    métricas, tabla de tests de integración, boundaries "Always"/"Ask first") y se añade una entrada de
    changelog fechada explicando el cambio (mismo patrón que la de 2026-10-05 sobre `CONNECTION_FAILED`).
    `docs/ideas/bicimad-now.md` (líneas 13 y 23) deja de listar "circuit breaker" como parte de la resiliencia
    del cliente EMT.
  - Aceptación:
    - `grep -rn "circuit breaker\|CircuitBreaker" docs/specs/SPEC-api-core.md docs/ideas/bicimad-now.md` no
      devuelve nada fuera de la nueva entrada de changelog de la spec.
    - La spec sigue siendo coherente como registro histórico (no se borran entradas antiguas del changelog).
  - Verificación: `grep -rn "circuit breaker\|CircuitBreaker" docs/specs/SPEC-api-core.md docs/ideas/bicimad-now.md`.
  - Archivos: `SPEC-api-core.md`, `bicimad-now.md`.
  - Dependencias: T2.

### Checkpoint final

- [x] T1, T2 y T3 completadas; `./gradlew build` en verde.
- [x] Revisión humana del PR antes del merge.

## Riesgos y mitigaciones

| Riesgo                                                                      | Impacto | Mitigación                                                                          |
| ---------------------------------------------------------------------------- | ------- | ------------------------------------------------------------------------------------ |
| Sin circuit breaker, EMT caída genera reintentos en cada request (cada 60s) | Bajo    | Tráfico bajo/personal; el coste del reintento extra es insignificante               |
| Algún test queda huérfano referenciando `CircuitBreaker`                    | Bajo    | T2 explícitamente busca y limpia todas las referencias antes de cerrar              |
| El health indicator pierde la única señal de "EMT caída"                   | Bajo    | Decisión consciente (ver arriba); se puede revisar si en el futuro hace falta señal  |

## Estimación

Unos 100 min en una sesión: T1 (~40 min), T2 (~40 min), T3 (~20 min).

## Changelog

- 2026-10-12: versión inicial, a partir de la discusión con el agente `spring-boot-engineer` (opción 1: timeout +
  retry, sin fallback a caché).
- 2026-10-12: revisión con `/agent-skills:plan`. Añadida T3 (actualizar `SPEC-api-core.md` y `bicimad-now.md`,
  que seguían documentando el circuit breaker). Ajustado el criterio de aceptación de T1 para apoyarse en los
  tests existentes de fallo (5xx/timeout/conexión cortada) en vez de un escenario sin especificar.
- 2026-10-12: T1-T3 implementadas por el agente `spring-boot-engineer` en la rama
  `refactor/emt-drop-circuit-breaker` (commits 264b178, baf5a53, 36efc0e). Review con `/agent-skills:review`
  encontró y arregló un bug de formato propio (SC15 pegada a una nota, commit f3ae93c/a6827e2). PR
  [#42](https://github.com/jorgetroya80/mad-mobility/pull/42) mergeada. Plan implementado.
