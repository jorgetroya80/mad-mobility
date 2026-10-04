# Capability Map: BiciMAD Now

- Created: 2026-10-03
- Status: **draft** (2026-10-03)

Fuente: [docs/ideas/bicimad-now.md](../ideas/bicimad-now.md). Cada módulo tiene su propia especificación en esta carpeta (`SPEC-<id>.md`). Los ids son estables y no se renombran.

| Módulo        | Responsabilidad                                                                                                                    | Depende de              | Spec                                     | Estado                |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ----------------------- | ---------------------------------------- | --------------------- |
| `foundation`  | Monorepo, Husky, commitlint, lint-staged, CI con GitHub Actions, release-please, publicación de artefactos                         | —                       | [SPEC-foundation.md](SPEC-foundation.md) | implemented           |
| `contract`    | Sustituido: el contrato OpenAPI se genera desde el código (springdoc) dentro de `api-bicimad`                                      | —                       | —                                        | obsolete (2026-10-03) |
| `api-core`    | `shared/emt`: autenticación, cliente HTTP resiliente, caché, cupo, health checks; proyecto Gradle base                             | foundation              | [SPEC-api-core.md](SPEC-api-core.md)     | approved              |
| `api-bicimad` | Módulo `bicimad`: dominio, casos de uso, endpoints `/v1/bicimad/*`; exporta `openapi/bicimad.json` con springdoc                   | api-core                | —                                        | pending               |
| `web-shell`   | Proyecto Vite + React + Tailwind + TanStack Query, cliente API tipado (desde `openapi/bicimad.json`), mapa base, layout responsive | api-bicimad, foundation | —                                        | pending               |
| `web-bicimad` | Mapa, lista, detalle, filtros y favoritos de estaciones                                                                            | web-shell, api-bicimad  | —                                        | pending               |
| `deploy`      | Despliegue en la plataforma elegida (por definir)                                                                                  | foundation              | —                                        | on hold               |

## Orden de construcción

```
foundation -> api-core -> api-bicimad -> web-shell -> web-bicimad -> deploy
```

**Enfoque de contrato: código primero (2026-10-03).** Los controladores Spring son la fuente de verdad; springdoc genera el OpenAPI, que se exporta a `openapi/bicimad.json`, se commitea y alimenta los tipos de React (`openapi-typescript`). CI regenera el archivo y falla si difiere del commiteado. Por eso el frontend se construye después de `api-bicimad`.

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
