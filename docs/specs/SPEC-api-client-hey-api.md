# Spec: api-bicimad — cliente generado con hey-api

- Created: 2026-10-27
- Status: **implemented** (2026-10-28)
- Plan: [PLAN-api-client-hey-api.md](../plans/PLAN-api-client-hey-api.md)

Cambio del módulo `api-bicimad` del [Capability Map](CAPABILITY-MAP.md). Sustituye el generador del paquete `@jorgetroya80/bicimad-client` (openapi-typescript + openapi-fetch) por [hey-api](https://heyapi.dev) (`@hey-api/openapi-ts`), el mismo que usa el proyecto donations-api. El contrato HTTP del API no cambia.

## Problema

El cliente actual solo genera tipos (`src/schema.d.ts`). Web llama al API escribiendo la ruta como texto (`apiClient.GET('/v1/bicimad/stations/{number}', …)`) y cada llamada está envuelta a mano en `web/src/entities/station/api/fetchStations.ts`.

Con hey-api el paquete exporta una función por operación (`listStations`, `getStation`), con el nombre tomado del `operationId`. El objetivo es:

- Usar el mismo generador en mis dos proyectos y aprender sus ventajas e inconvenientes en un caso real.
- Que web llame a funciones con nombre en vez de escribir rutas.
- Dejar la puerta abierta a los plugins de hey-api (TanStack Query, Zod) en specs futuras.

## Objective

- Como frontend, quiero importar `listStations` y `getStation` del paquete, para no repetir rutas ni parámetros de ruta en web.
- Como mantenedor, quiero que el código generado viva en `src/generated/` (ignorado por git) y que `src/index.ts` sea el único punto público escrito a mano, como en el plan `update-openapi-client-exports` de donations-api.
- Como mantenedor, quiero que un cambio de `operationId` haga fallar el build del API, porque con hey-api el `operationId` pasa a ser API pública del cliente.

**Fuera de alcance:**

- Cambios en endpoints, parámetros o respuestas del API.
- Plugins de hey-api distintos de `@hey-api/typescript`, `@hey-api/sdk` y `@hey-api/client-fetch` (TanStack Query, Zod, Valibot). Cada uno, si se quiere, en su propia spec.
- Cambios en el flujo de publicación (`publish-api-client` en `release.yml`) más allá de lo que exija el nuevo build.

## Assumptions

Por comprobar en la fase de plan, contra la documentación de hey-api y la versión elegida:

1. `@hey-api/openapi-ts` 0.99.0 (publicada el 2026-06-22, cumple `minimumReleaseAge`) acepta OpenAPI 3.1 y funciona con TypeScript 5.9.3 (peer: `>=5.5.3`) y Node 24 (engines: `>=22.18.0`).
2. El cliente fetch va incluido en `@hey-api/openapi-ts`: `@hey-api/client-fetch` está deprecado desde la 0.73.0 ("bundled directly inside @hey-api/openapi-ts"). Hay que confirmar si el paquete publicado necesita alguna dependencia de runtime o si todo el código del cliente queda generado en `src/generated/`.
3. El cliente de hey-api acepta un `fetch` propio en su configuración. Web lo necesita: `web/src/shared/api/client.ts` busca `globalThis.fetch` en cada petición para que MSW funcione.
4. Por defecto, las funciones del SDK devuelven `{ data, error, response }` sin lanzar excepción, igual que openapi-fetch hoy. Así `fetchStations.ts` conserva su manejo de errores (404 → `StationNotFoundError`).
5. Los tipos generados conservan los nombres de los esquemas (`StationResponse`, `StationsResponse`, `StationDetailResponse`, `Problem`).
6. hey-api sigue en versiones 0.x, con cambios incompatibles frecuentes entre versiones menores. Por eso se fija la versión exacta (`saveExact: true` ya está activo).

## Cambio en el paquete publicado

| Antes (≤ 0.5.x) | Después |
| --- | --- |
| Genera `src/schema.d.ts` (solo tipos) | Genera `src/generated/*.ts` (tipos, SDK y cliente) |
| `createBicimadClient(baseUrl, { fetch })` devuelve un `Client<paths>` de openapi-fetch | Se elimina `createBicimadClient`; el paquete exporta `createClient`, `createConfig`, `Client`, `Config` y `CreateClientConfig` de hey-api, como donations-api |
| Web crea el cliente con `createBicimadClient(baseUrl, { fetch })` | Web crea el cliente con `createClient(createConfig({ baseUrl, fetch }))` |
| Llamada: `client.GET('/v1/bicimad/stations/{number}', { params: { path: { number } } })` | Llamada: `getStation({ client, path: { number } })` |
| Exporta `paths`, `components` | Exporta `listStations`, `getStation` y todos los tipos generados |
| Alias `Station`, `StationsResponse`, `StationDetailResponse`, `Problem` | Se mantienen los mismos alias |
| Dependencia de runtime `openapi-fetch` | Sin `openapi-fetch` |
| Incluye `openapi.json` | Igual |

- Rompe el API público del paquete: commit `feat(api)!` con `BREAKING CHANGE:` en el cuerpo. Con `bump-minor-pre-major`, el API y el cliente se publican como **0.6.0**. La imagen Docker también sale como 0.6.0 aunque no cambie, porque el cliente comparte versión con el API.
- `src/index.ts` sigue escrito a mano y versionado; es la única superficie pública y sobrevive a cada regeneración.
- Los scripts `generate` y `build` conservan su nombre, así CI (`ci.yml`, `release.yml`) y `scripts/typecheck-changed.sh` no cambian.

## Entrega en dos PRs

La regla `release-scope` de CI no permite que un PR que publica toque `api/` y `web/` a la vez. Por eso:

1. **PR 1 — `feat(api)!`:** cambio del generador en `api/client/` y test de `operationId` en `api/`. Al fusionarse, release-please publica la 0.6.0.
2. **PR 2 — `refactor(web)`:** web sube a `@jorgetroya80/bicimad-client@0.6.0` y migra `client.ts` y `fetchStations.ts`. El comportamiento de web no cambia, así que el PR no publica una versión nueva de web.

Entre PR 1 y PR 2 web sigue en 0.4.0, que sigue funcionando porque el contrato HTTP no cambia.

## Project Structure

```
api/client/
  openapi-ts.config.ts      nuevo: input openapi.json, output src/generated, plugins @hey-api/typescript, @hey-api/sdk y @hey-api/client-fetch
  package.json              scripts generate/build, dependencias (sale openapi-fetch y openapi-typescript, entra @hey-api/openapi-ts)
  .gitignore                src/schema.d.ts → src/generated/
  src/index.ts              reexporta SDK y tipos de ./generated, createClient, createConfig, Client, Config, CreateClientConfig y los alias
api/src/test/kotlin/.../OpenApiExportTest.kt   comprueba que los operationId son listStations y getStation
web/src/shared/api/client.ts                     cliente de hey-api con fetch propio
web/src/entities/station/api/fetchStations.ts    usa listStations y getStation
web/package.json                                 @jorgetroya80/bicimad-client 0.6.0
docs/specs/CAPABILITY-MAP.md                     generador del cliente en la fila de api-bicimad
```

Los mocks de MSW (`web/src/shared/api/mocks/`) no cambian: interceptan por URL y solo importan tipos que se mantienen.

## Testing Strategy

- `OpenApiExportTest`: los `operationId` del documento son exactamente `listStations` y `getStation`. Renombrar uno pasa a ser un cambio incompatible y debe fallar el build.
- Cliente: `pnpm --filter @jorgetroya80/bicimad-client run generate` y `run build` pasan en CI, igual que hoy.
- Web: los tests actuales (`client.test.ts`, tests de widgets con MSW) pasan sin cambiar sus aserciones; solo cambian los imports si hace falta.

## Boundaries

- **Always:** versión exacta de `@hey-api/openapi-ts`; el código generado nunca se commitea.
- **Ask first:** añadir cualquier plugin de hey-api fuera de `@hey-api/typescript`, `@hey-api/sdk` y `@hey-api/client-fetch`.
- **Never:** editar a mano ficheros de `src/generated/`; cambiar el contrato HTTP en esta spec.

## Success Criteria

1. `pnpm run generate` escribe solo en `api/client/src/generated/` y no toca `src/index.ts`; ejecutar generate y build dos veces seguidas da el mismo `dist/`.
2. `git status` tras generar no muestra cambios: `src/generated/` está ignorado y `src/index.ts` está versionado.
3. `dist/index.d.ts` exporta `createClient`, `createConfig`, `Client`, `Config`, `CreateClientConfig`, `listStations`, `getStation`, `Station`, `StationsResponse`, `StationDetailResponse` y `Problem`, y ya no exporta `createBicimadClient`.
4. El paquete publicado no depende de `openapi-fetch`.
5. `OpenApiExportTest` falla si se renombra un `operationId`.
6. La release tras PR 1 es `api-v0.6.0` y publica `@jorgetroya80/bicimad-client@0.6.0`.
7. **Uso real:** tras PR 2, `pnpm --filter web run dev` contra el API local muestra la lista de estaciones y el detalle de la 538 como antes; `lint`, `typecheck`, `test` y `build` de web en verde.

## Open Questions

- Ninguna.

## Decisiones

- 2026-10-27: se elimina `createBicimadClient` y se exportan directamente `createClient` y `createConfig` de hey-api, como en donations-api.
- 2026-10-27: plugins solo `@hey-api/typescript`, `@hey-api/sdk` y `@hey-api/client-fetch`; nada de `@tanstack/react-query`.
- 2026-10-27: se acepta que la imagen Docker salga como 0.6.0 sin cambios en el servidor; el cliente sigue compartiendo versión con el API.

## Changelog

- 2026-10-27: versión inicial.
- 2026-10-27: preguntas abiertas resueltas; pasa a in review.
- 2026-10-27: spec aprobada.
- 2026-10-27: implementación del PR 1 (T1-T3): cliente generado con hey-api y `operationId` protegidos por test.
- 2026-10-28: spec implementada. SC1-SC7 comprobados; web usa el cliente 0.6.0 (PR 2).
