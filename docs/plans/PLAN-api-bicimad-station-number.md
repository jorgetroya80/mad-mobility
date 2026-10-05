# Plan: api-bicimad — detalle por número de estación

- Created: 2026-10-05
- Status: **approved** (2026-10-05)
- Spec: [SPEC-api-bicimad-station-number.md](../specs/SPEC-api-bicimad-station-number.md)

## Overview

Cambiar `GET /v1/bicimad/stations/{id}` por `GET /v1/bicimad/stations/{number}` (texto, sin distinguir mayúsculas), garantizar que el número es único en la caché y dejar documentado el cambio incompatible para que la siguiente release sea la 0.3.0.

## Decisiones de implementación

- **Una rama (`feat/api-bicimad-station-number`) y un solo PR, con un commit por tarea.** Sin `Co-Authored-By`. El squash merge debe conservar `feat(api)!` en el título del PR y el `BREAKING CHANGE:` en el cuerpo, para que release-please suba a 0.3.0.
- **Cada tarea la implementa el agente `spring-boot-engineer` con la skill clean-code y TDD**, como en `api-bicimad`.
- **Orden por dependencia:** primero la unicidad del número (T1), porque el detalle por número la necesita.
- **Comparación sin distinguir mayúsculas en el caso de uso**, no en el mapper: el dominio conserva el número tal como lo envía la EMT (`25A`).

## Grafo de dependencias

```
T1 número único en el mapper
 └── T2 detalle por número (caso de uso + HTTP + OpenAPI)
        └── T3 documentación, smoke test y cierre
```

## Task List

- [ ] **T1: Número de estación único** (S, ~20 min)
  - Descripción: `EmtStationMapper` descarta las estaciones cuyo `number` ya apareció (sin distinguir mayúsculas), con un `WARN` que incluye el `id` y el número, y conserva la primera.
  - Aceptación:
    - Dos estaciones con el número `25A` y `25a`: se conserva la primera y hay exactamente un `WARN`.
    - El fixture real sigue traduciéndose igual (sin duplicados, sin avisos).
  - Verificación: `./gradlew test --tests '*EmtStationMapper*'`.
  - Archivos: `adapters/emt/EmtStationMapper.kt`, `EmtStationMapperTest.kt`.
  - Dependencias: ninguna.

- [ ] **T2: Detalle por número** (M, ~45 min)
  - Descripción: `GetStation` busca por número sin distinguir mayúsculas; `StationNotFound` recibe el número. El controlador expone `/{number}` como `String`, valida el patrón `^[0-9]+[A-Za-z]?$` (400 problem+json si no cumple) y documenta el parámetro en el OpenAPI (`string`, patrón, ejemplo `538`). Commit `feat(api)!` con `BREAKING CHANGE:` en el cuerpo.
  - Aceptación:
    - SC2 y SC3: `25a` → `25A`; `abc`, `5-A` y `538AB` → 400; `999` → 404; todos con `requestId`.
    - SC4: `OpenApiExportTest` comprueba el parámetro `number` (`string`, con patrón); `redocly lint` sin avisos; el cliente compila.
    - Ningún test sigue usando el `id` en la URL.
  - Verificación: `./gradlew build generateOpenApi`; `npx --yes @redocly/cli@2 lint api/build/openapi/bicimad.json`; `pnpm --filter @jorgetroya80/bicimad-client run generate build`.
  - Archivos: `application/GetStation.kt`, `http/StationsController.kt`, `GetStationTest.kt`, `StationsControllerTest.kt`, `OpenApiExportTest.kt`.
  - Dependencias: T1.

- [ ] **T3: Documentación, uso real y cierre** (S, ~30 min, **requiere `api/.env.local`**)
  - Descripción: `SPEC-api-bicimad.md` (contrato del detalle, SC5 y enlace a esta spec) y la línea del identificador en `docs/ideas/bicimad-now.md`. Comprobación con `bootRun` contra la EMT real de `/stations/538`, y `./gradlew smokeTest`. Investigar por qué una consulta del 2026-10-05 devolvió 557 estaciones en vez de 678 (cambio real de la EMT o estaciones descartadas) y anotar el resultado. Spec y plan a **implemented**.
  - Aceptación:
    - SC1: `/stations/538` → 200 "Haendel - Silvano" con la EMT real.
    - SC5: `./gradlew build` y `./gradlew smokeTest` en verde.
    - La causa de 557 contra 678 queda explicada en el changelog del plan (o abierta como pregunta si no se puede reproducir).
  - Verificación: `bootRun` + `curl`; `./gradlew smokeTest`.
  - Archivos: `SPEC-api-bicimad.md`, `bicimad-now.md`, esta spec y este plan.
  - Dependencias: T2.

### Checkpoint final

- [ ] SC1-SC5 comprobados; CI en verde en el PR.
- [ ] Revisión humana del título del PR (`feat(api)!: …`) antes del squash merge.
- [ ] SC6 se confirma en la release 0.3.0.

## Riesgos y mitigaciones

| Riesgo                                                                      | Impacto | Mitigación                                                                                  |
| --------------------------------------------------------------------------- | ------- | ------------------------------------------------------------------------------------------- |
| El squash merge pierde el `!` o el `BREAKING CHANGE:` y la release es 0.2.1 | Medio   | El título del PR lleva `feat(api)!` (lo valida `pr-title`); revisión humana antes del merge |
| La EMT reutiliza o cambia números de estación                               | Bajo    | El mapper garantiza unicidad en cada carga; un cambio de número solo rompe enlaces antiguos |
| Spring trata `25A` como extensión o hace mal el parseo de la ruta           | Bajo    | Tests de controlador con `25A` y `25a`                                                      |

## Estimación

Unos 95 min en una sesión: T1 (~20 min), T2 (~45 min), T3 (~30 min).

## Changelog

- 2026-10-05: versión inicial.
- 2026-10-05: plan aprobado.
