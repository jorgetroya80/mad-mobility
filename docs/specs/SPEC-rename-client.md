# Spec: api-bicimad — cliente publicado como mad-transit-client

- Created: 2026-10-30
- Status: **implemented** (2026-10-30)
- Plan: [PLAN-rename-client.md](../plans/PLAN-rename-client.md)

Cambio del módulo `api-bicimad` del [Capability Map](CAPABILITY-MAP.md), con efecto en `web-shell`. Renombra el paquete npm del cliente generado de `@jorgetroya80/bicimad-client` a `@jorgetroya80/mad-transit-client`. El contrato HTTP del API y el código del cliente no cambian.

## Problema

[SPEC-rename-mad-transit.md](SPEC-rename-mad-transit.md) dejó el paquete fuera de alcance porque su nombre describía el servicio de bicis. Pero el paquete es el cliente de todo el API de Mad Transit, no solo de BiciMAD: cuando el API sirva los autobuses de la EMT, irán en el mismo paquete. El nombre actual ya no describe lo que contiene.

## Objective

- Como mantenedor, quiero que el paquete del cliente se llame como el producto, para que no convivan dos nombres.
- Como frontend, quiero importar el cliente desde `@jorgetroya80/mad-transit-client` sin cambiar nada más en el código.
- Como mantenedor, quiero hacer el cambio sin romper el build de web en ningún momento.

**Fuera de alcance:**

- El módulo `bicimad` del API, sus rutas `/v1/bicimad/*` y el archivo `bicimad.json` del OpenAPI.
- Cambios en las exportaciones del paquete o en el generador.
- Borrar el paquete viejo `@jorgetroya80/bicimad-client` de GitHub Packages.
- Los `CHANGELOG.md` y las specs y planes ya cerrados, que son histórico.

## Assumptions

1. El scope `@jorgetroya80` se mantiene: GitHub Packages exige que el scope sea el dueño del repo.
2. El primer `pnpm publish` del nombre nuevo desde `release.yml` con `GITHUB_TOKEN` crea el paquete en GitHub Packages, enlazado al repo por el campo `repository` de `package.json`, sin pasos manuales. Así pasó con el paquete actual en `api-v0.2.0`.
3. El token de Render y el de CI (`read:packages`) pueden instalar el paquete nuevo sin cambiar permisos. Se comprueba tras la primera publicación.
4. El paquete viejo sigue instalable con sus versiones publicadas (hasta la 0.7.1).

## Nombres

| Qué | Antes | Después |
| --- | --- | --- |
| Paquete npm | `@jorgetroya80/bicimad-client` | `@jorgetroya80/mad-transit-client` |
| Carpeta | `api/client/` | sin cambios |
| Filtro de pnpm en CI y scripts | `--filter @jorgetroya80/bicimad-client` | `--filter @jorgetroya80/mad-transit-client` |
| `minimumReleaseAgeExclude` | nombre viejo | nombre nuevo |

## Entrega en dos PRs

La regla `release-scope` no permite que un PR que publica toque `api/` y `web/` a la vez, y web no puede instalar el nombre nuevo hasta que exista. Por eso:

1. **PR 1, `feat(api)!`:** cambia el nombre en `api/client/`, en CI, en `scripts/` y en los README del API. `minimumReleaseAgeExclude` lleva los dos nombres. Al fusionarse, release-please publica `api 0.8.0` y `publish-api-client` publica `@jorgetroya80/mad-transit-client@0.8.0`. Es incompatible para quien consume el paquete, que tiene que cambiar el nombre: el cuerpo lleva `BREAKING CHANGE:`. La imagen Docker también sale como 0.8.0 sin cambios en el servidor, porque el cliente comparte versión con el API.
2. **PR 2, `refactor(web)`:** web cambia la dependencia y los imports al nombre nuevo, y se quita el nombre viejo de `minimumReleaseAgeExclude`. Se actualiza la documentación viva. El comportamiento de web no cambia, así que no publica versión de web.

Entre PR 1 y PR 2, web sigue con `@jorgetroya80/bicimad-client@0.6.0`, que funciona porque el contrato HTTP no cambia.

## Project Structure

```
api/client/package.json                name
api/client/README.md                   nombre en título, instalación e import
api/README.md                          nombre del paquete publicado
.github/workflows/ci.yml               filtro de pnpm
.github/workflows/release.yml          filtro de pnpm en publish-api-client
scripts/typecheck-changed.sh           filtro de pnpm
pnpm-workspace.yaml                    minimumReleaseAgeExclude
web/package.json                       dependencia mad-transit-client 0.8.0
web/src/**                             imports (8 archivos)
AGENTS.md, README.md                   nombre del paquete
web/AGENTS.md, web/README.md           nombre del paquete
docs/specs/CAPABILITY-MAP.md           filas de api-bicimad y web-shell
```

## Testing Strategy

- Cliente: `generate` y `build` con el filtro nuevo pasan en CI.
- Web: los tests actuales pasan sin cambios salvo los imports.
- `grep -rn "bicimad-client"` fuera de changelogs, specs y planes cerrados y esta spec no da resultados tras el PR 2.

## Boundaries

- **Always:** versión exacta del cliente en `web/package.json`.
- **Ask first:** borrar o cambiar la visibilidad de cualquier paquete en GitHub Packages.
- **Never:** cambiar el contrato HTTP o las exportaciones del paquete en esta spec.

## Success Criteria

1. La release tras el PR 1 es `api-v0.8.0` y publica `@jorgetroya80/mad-transit-client@0.8.0` en GitHub Packages, enlazado al repo `mad-transit`.
2. `pnpm install --frozen-lockfile` instala el paquete nuevo en local, en CI y en Render.
3. Tras el PR 2, ningún archivo vivo nombra `bicimad-client` (ver Testing Strategy).
4. Web: `lint`, `typecheck`, `test` y `build` en verde; `pnpm check:task` en verde en los dos PRs.
5. **Uso real:** tras el deploy de la web en Render, la lista de estaciones se ve como antes.

## Open Questions

- Ninguna.

## Decisiones

- 2026-10-30: nombre `@jorgetroya80/mad-transit-client`; el scope no cambia.
- 2026-10-30: el paquete viejo se deja publicado. GitHub Packages no permite marcarlo como deprecated y borrarlo no tiene vuelta atrás.
- 2026-10-30: `feat(api)!` y no `build(api)`, porque solo un commit publicable lanza la release que publica el nombre nuevo.

## Changelog

- 2026-10-30: versión inicial; pasa a in review.
- 2026-10-30: spec aprobada.
- 2026-10-30: implementación del PR 1 (T1-T2): paquete renombrado en `api/client`, CI y scripts.
- 2026-10-30: spec implementada. SC1-SC4 comprobados; SC5 queda para el próximo deploy de la web en Render.
