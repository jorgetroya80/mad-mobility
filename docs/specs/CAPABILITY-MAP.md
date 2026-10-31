# Capability Map: Mad Transit

- Created: 2026-10-03
- Status: **approved** (2026-10-28)

Fuente: [docs/ideas/bicimad-now.md](../ideas/bicimad-now.md). Cada módulo tiene su propia especificación en esta carpeta (`SPEC-<id>.md`). Los ids son estables y no se renombran.

| Módulo        | Responsabilidad                                                                                                                                                                                      | Depende de              | Spec                                       | Estado                |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- | ------------------------------------------ | --------------------- |
| `foundation`  | Monorepo, Husky, commitlint, lint-staged, CI con GitHub Actions, release-please, publicación de artefactos                                                                                           | —                       | [SPEC-foundation.md](SPEC-foundation.md)   | implemented           |
| `contract`    | Sustituido: el contrato OpenAPI se genera desde el código (springdoc) dentro de `api-bicimad`                                                                                                        | —                       | —                                          | obsolete (2026-10-03) |
| `api-core`    | `shared/emt`: autenticación, cliente HTTP resiliente, caché, cupo, health checks; proyecto Gradle base                                                                                               | foundation              | [SPEC-api-core.md](SPEC-api-core.md)       | implemented           |
| `api-bicimad` | Módulo `bicimad`: dominio, casos de uso, endpoints `/v1/bicimad/*`; OpenAPI con springdoc y cliente `@jorgetroya80/mad-transit-client` (hey-api) publicado en cada release                                 | api-core                | [SPEC-api-bicimad.md](SPEC-api-bicimad.md) | implemented           |
| `web-shell`   | Proyecto Vite + React + Tailwind + TanStack Query con arquitectura FSD, oxlint, oxfmt y versiones fijadas; cliente API tipado (paquete `@jorgetroya80/mad-transit-client`), mapa base, layout responsive | api-bicimad, foundation | [SPEC-web-shell.md](SPEC-web-shell.md)     | implemented           |
| `web-bicimad` | Mapa, lista, detalle, filtros y favoritos de estaciones                                                                                                                                              | web-shell, api-bicimad  | [SPEC-web-bicimad.md](SPEC-web-bicimad.md) | implemented           |
| `deploy`      | Despliegue en Render (plan gratuito, Blueprint `render.yaml`): API desde la imagen de ghcr.io y web desde `web-dist.tar.gz`, con deploy hooks en `release.yml`; la web llama al API con CORS | foundation              | [SPEC-deploy.md](SPEC-deploy.md)           | implemented           |
| `api-bus`     | Módulo `bus`: paradas cercanas, detalle con líneas y llegadas en `/v1/bus/stops/*`; `POST` en `EmtHttpClient`; caché y cupo propios | api-core | [SPEC-api-bus.md](SPEC-api-bus.md) | approved |
| `web-bus`     | Selector BiciMAD / Bus, iconos de parada en el mapa, popup de resumen, lista, detalle con llegadas y buscador | web-shell, api-bus, web-error-states | [SPEC-web-bus.md](SPEC-web-bus.md) | approved |
| `web-design-tokens` | Tema del rediseño en el `@theme` de Tailwind (color, forma, sombra, movimiento), fuente Inter y migración de las clases de la paleta por defecto a tokens | web-bicimad | [SPEC-web-design-tokens.md](SPEC-web-design-tokens.md) | implemented |
| `web-data-state` | Estado único de la petición de estaciones (`loading`, `ok`, `stale`, `error`, `offline`), reintentos con espera creciente, caché en `localStorage` y nivel `unconfirmed` | web-bicimad | — | — |
| `web-redesign-desktop` | Componentes del rediseño y pantalla de escritorio (≥ 768 px): cabecera, panel lateral, tarjeta de estación, leyenda y búsqueda por número y nombre | web-design-tokens, web-data-state | — | — |
| `web-redesign-mobile` | Pantalla móvil (< 768 px): buscador flotante, modo en píldora, botón de centrar y bottom sheet con tres alturas por toques | web-redesign-desktop | — | — |
| `web-error-states` | Estados `error`, `stale`, `offline` y búsqueda sin resultados en las dos pantallas | web-redesign-desktop, web-redesign-mobile | — | — |
| `web-sheet-drag` | Arrastre del bottom sheet con imantado a la altura más cercana (opcional) | web-redesign-mobile | — | — |

## Orden de construcción

```
foundation -> api-core -> api-bicimad -> web-shell -> web-bicimad -> deploy
web-bicimad -> web-design-tokens -> web-data-state -> web-redesign-desktop -> web-redesign-mobile -> web-error-states -> web-bus
web-redesign-mobile -> web-sheet-drag
api-core -> api-bus -> (release del cliente) -> web-bus
```

**Rediseño de la UI (2026-10-31).** Los módulos `web-design-tokens` a `web-sheet-drag` aplican el diseño del lienzo «Mad Transit · rediseño» y del documento «Mad Transit — Especificación de UI», que son privados. El rediseño va antes que `web-bus`, que después se adapta a sus tokens y componentes y añade el selector BiciMAD / Bus.

**Enfoque de contrato: código primero (2026-10-03, revisado 2026-10-05, 2026-10-27 y 2026-10-30).** Los controladores Spring son la fuente de verdad; springdoc genera el OpenAPI en el build (no se commitea). En cada release del API se publica en GitHub Packages el paquete `@jorgetroya80/mad-transit-client` (hasta la 0.7.1, `@jorgetroya80/bicimad-client`), con la misma versión que el API. Desde la 0.6.0 lo genera hey-api (tipos, una función por `operationId` y su cliente fetch, con el `openapi.json` dentro; ver [SPEC-api-client-hey-api.md](SPEC-api-client-hey-api.md)); hasta la 0.5.0 eran tipos de `openapi-typescript` + `openapi-fetch`. El frontend instala ese paquete. Por eso se construye después de `api-bicimad`.

Las dependencias van en un solo sentido. Cada módulo pasa por Specify -> Plan -> Tasks -> Implement, con revisión humana entre fases.

## Convención de documentos

Todo SPEC y PLAN empieza, justo bajo el título, con esta lista:

```markdown
# Spec: <módulo>

- Created: AAAA-MM-DD
- Status: **<estado>** (AAAA-MM-DD del último cambio de estado)
- Plan: [PLAN-<módulo>.md](../plans/PLAN-<módulo>.md)
```

En un PLAN, la última línea enlaza al SPEC: `- Spec: [SPEC-<módulo>.md](../specs/SPEC-<módulo>.md)`. Si el documento enlazado aún no existe se escribe `pending`.

Estados posibles:

- **draft:** en redacción, puede cambiar.
- **in review:** completo, pendiente de aprobación humana.
- **approved:** base para la siguiente fase.
- **implemented:** el trabajo descrito está en `main`.
- **obsolete:** sustituido o descartado; se indica por qué y qué lo reemplaza.

La columna "Estado" de la tabla de módulos refleja el estado del SPEC de cada módulo.
