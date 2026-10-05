# Spec: api-bicimad — detalle por número de estación

- Created: 2026-10-05
- Status: **approved** (2026-10-05)
- Plan: [PLAN-api-bicimad-station-number.md](../plans/PLAN-api-bicimad-station-number.md)

Cambio del módulo `api-bicimad` del [Capability Map](CAPABILITY-MAP.md). Corrige el contrato definido en [SPEC-api-bicimad.md](SPEC-api-bicimad.md): el detalle de una estación se pide por su **número** (la etiqueta visible en la estación), no por el `id` interno de la EMT.

## Problema

Con la API 0.2.0, `GET /v1/bicimad/stations/538` devuelve `404`, aunque existe la estación 538 ("Haendel - Silvano"). El endpoint busca por `id` de la EMT (`2131` en este caso), un valor que el usuario no ve en ningún sitio. La intención del producto siempre fue usar el número de la estación.

## Por qué ocurrió

El fallo es de requisitos, no de código: la implementación cumple la spec aprobada y todos sus tests pasan. La decisión equivocada entró en la idea y nadie la cuestionó en ninguna fase posterior.

1. **Origen en la idea (2026-10-03).** [bicimad-now.md](../ideas/bicimad-now.md) fija "Identificador: `id` de la EMT; `number` se muestra como etiqueta". El motivo implícito era la estabilidad: un identificador técnico parece más seguro que una etiqueta. Esa estabilidad nunca se comprobó (sigue sin marcar en "Key Assumptions to Validate") y tampoco se comprobó lo contrario, que el número sea único.
2. **La spec copió la decisión sin preguntarla.** Al redactar [SPEC-api-bicimad.md](SPEC-api-bicimad.md), Claude hizo preguntas sobre `need`, `near`, la publicación del OpenAPI y el rate limit, pero no sobre el identificador. Lo trató como una decisión ya tomada porque estaba en la idea. Sí lo pasó a "Assumptions" (supuesto 1: "el `id` de la EMT es estable"), pero formulado como un riesgo técnico, no como una decisión de producto que tú debías confirmar.
3. **Los ejemplos no permitían ver la diferencia.** La spec y la idea usan `1409` como ejemplo (`/stations/1409`, `?station=1409`). Es un `id`, pero se lee igual que un número de estación. Ningún ejemplo mostraba un `id` y un `number` distintos uno al lado del otro, como `id 2131` y `number "538"`.
4. **Las revisiones validaron la forma, no el uso.** En el checkpoint 1 se revisó el JSON real de `/stations/1409`, que mostraba `"number": "5"`. La diferencia estaba a la vista, pero la revisión se centró en los campos y el formato, no en cómo llegaría el usuario a ese endpoint.
5. **Ningún criterio de aceptación partía del usuario.** Los criterios de éxito (SC5) prueban "`id` inexistente → 404", que es coherente con el diseño pero no con el uso real. Faltaba un escenario como "veo la estación 538 en la calle y consulto su estado".

### Qué cambia en el proceso

- En cada spec, los **identificadores públicos** (qué valor va en la URL y en el estado compartible) son una pregunta explícita al usuario, aunque la idea ya los fije.
- Los ejemplos de una spec usan valores que **no se pueden confundir** (un `id` y un `number` distintos y con formatos diferentes).
- Al menos un criterio de éxito por endpoint describe el **uso real** desde el punto de vista del usuario, no solo el contrato técnico.

## Objective

- Como usuario, quiero consultar una estación por el número que veo en ella (`538`, `25A`), para no tener que conocer identificadores internos.
- Como frontend, quiero que el estado compartible de la URL use el número (`?station=538`), legible y estable para el usuario.

**Fuera de alcance:** cambios en la lista (`GET /v1/bicimad/stations`), en el formato de las estaciones o en el cliente más allá de regenerarlo.

## Assumptions

1. El número de estación es único. Comprobado el 2026-10-05 con los datos reales: ningún número repetido; algunos llevan letra (`25A`, `25B`, `106A`, `106B`, `111A`, `111B`, `116A`, `116B`, `80A`, `80B`).
2. Si la EMT llegara a enviar dos estaciones con el mismo número, el mapper conserva la primera y descarta la otra con un `WARN` (mismo criterio que con las estaciones incompletas), para que el detalle nunca sea ambiguo.
3. No hay consumidores del cliente 0.2.0 todavía (`web-shell` no existe), así que romper el contrato tiene coste cero fuera del repo.

## Contract change

### `GET /v1/bicimad/stations/{number}`

| Antes (0.2.0)            | Después                                                       |
| ------------------------ | ------------------------------------------------------------- |
| `/stations/{id}`, entero | `/stations/{number}`, texto con patrón `^[0-9]+[A-Za-z]?$`    |
| `/stations/538` → `404`  | `/stations/538` → `200` (Haendel - Silvano)                   |
| `/stations/2131` → `200` | `/stations/2131` → `404` (no hay estación con número 2131)    |
| `id` no numérico → `400` | valor que no cumple el patrón (`abc`, `5-A`, `538AB`) → `400` |

- La búsqueda no distingue mayúsculas: `/stations/25a` y `/stations/25A` devuelven la misma estación.
- La respuesta no cambia: `{ station, updatedAt, stale, source }`, y `station` sigue incluyendo `id` y `number`.
- OpenAPI: parámetro de ruta `number` (`string`, con el patrón y el ejemplo `538`); `operationId` sigue siendo `getStation`.
- Es un cambio incompatible del contrato: commit `feat(api)!` con `BREAKING CHANGE:` en el cuerpo. Con `bump-minor-pre-major`, release-please lo publicará como 0.3.0, y el cliente se publicará con esa misma versión.

## Project Structure

Cambios en archivos existentes, sin archivos nuevos de producción:

```
api/src/main/kotlin/.../modules/bicimad/
  application/GetStation.kt          busca por number (sin distinguir mayúsculas)
  adapters/emt/EmtStationMapper.kt   descarta números duplicados con WARN
  http/StationsController.kt         ruta /{number}, validación del patrón, anotaciones OpenAPI
api/src/test/kotlin/.../modules/bicimad/   tests actualizados (GetStation, controlador, mapper)
api/src/test/kotlin/.../OpenApiExportTest.kt   parámetro number de tipo string
docs/specs/SPEC-api-bicimad.md       contrato y SC5 actualizados, enlace a esta spec
docs/ideas/bicimad-now.md            línea del identificador corregida
```

## Testing Strategy

Como en [SPEC-api-bicimad.md](SPEC-api-bicimad.md#testing-strategy). Además:

- `GetStationTest`: por número, sin distinguir mayúsculas y con número inexistente.
- `StationsControllerTest`: `538` → 200, `25a` → la estación `25A`, `abc` → 400, `999` → 404.
- `EmtStationMapperTest`: número duplicado → se conserva la primera y un `WARN`.
- `OpenApiExportTest`: el parámetro de ruta es `number`, de tipo `string`, con patrón.

## Boundaries

Las de [SPEC-api-bicimad.md](SPEC-api-bicimad.md#boundaries). Además:

- **Never:** volver a exponer el `id` de la EMT como identificador en URLs o estado compartible.

## Success Criteria

1. **Uso real:** con la EMT real, `GET /v1/bicimad/stations/538` devuelve 200 con `"name": "Haendel - Silvano"` y `"number": "538"`.
2. `GET /v1/bicimad/stations/25a` devuelve la estación con `"number": "25A"`.
3. `/stations/abc` → `400` problem+json; `/stations/999` → `404` problem+json; ambos con `requestId`.
4. El OpenAPI generado tiene el parámetro `number` (`string`, con patrón) y `redocly lint` pasa sin avisos; el cliente compila.
5. `./gradlew build` en verde con las dos puertas de JaCoCo; `./gradlew smokeTest` en verde.
6. El commit lleva `feat(api)!` y `BREAKING CHANGE:`, y la siguiente release del API es la 0.3.0.

## Open Questions

- Ninguna.

## Changelog

- 2026-10-05: versión inicial, tras detectar que `/stations/538` devolvía 404.
- 2026-10-05: spec aprobada.
