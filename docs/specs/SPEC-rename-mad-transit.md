# Spec: renombrar el producto a Mad Transit

- Created: 2026-10-29
- Status: **approved** (2026-10-29)
- Plan: [PLAN-rename-mad-transit.md](../plans/PLAN-rename-mad-transit.md)

Cambio transversal: afecta a `api-core`, `api-bicimad`, `web-shell`, `web-bicimad` y `deploy` del [Capability Map](CAPABILITY-MAP.md). Los ids de módulo no cambian.

## Problema

El producto pasa a llamarse **Mad Transit** (decisión del usuario, 2026-10-29). Hoy el nombre del producto no es el mismo en todas partes: la web muestra "BiciMAD Now", y el repo, los servicios, la imagen Docker, el código y la app de MobilityLabs usan otro. Mad Transit sustituye a todos y es el único nombre del producto desde ahora. Sirve también cuando la app incluya los autobuses de la EMT.

## Objective

- Como usuario, quiero ver "Mad Transit" en la web, en su URL y en la app de MobilityLabs, sin rastro de "Mad Mobility" ni de "BiciMAD Now".
- Como mantenedor, quiero que el código, la configuración, la imagen Docker, el repo y la documentación usen el nombre nuevo, para que no convivan dos nombres.
- Como mantenedor, quiero hacer el cambio sin dejar la web caída más de unos minutos y sin perder el despliegue automático.

**Fuera de alcance:**

- Los `CHANGELOG.md`, que son histórico.
- El paquete `@jorgetroya80/bicimad-client` y el módulo `bicimad`: describen el servicio de bicis, no el producto.
- Los nombres de archivo de los documentos (`docs/ideas/bicimad-now.md`, `SPEC-*.md`, `PLAN-*.md`) y los ids de módulo.
- Las entradas de changelog de specs y planes ya cerrados: se conservan como estaban.
- Comprobar la marca "Mad Transit" en OEPM o EUIPO (lo hace el usuario si quiere).
- Dominio propio.

## Nombres

| Qué | Antes | Después |
| --- | --- | --- |
| Nombre visible | Mad Mobility / BiciMAD Now | **Mad Transit** |
| Repo de GitHub | `jorgetroya80/mad-mobility` | `jorgetroya80/mad-transit` |
| Workspace pnpm (`package.json` raíz) | `mad-mobility` | `mad-transit` |
| Proyecto Gradle (`rootProject.name`) y jar | `mad-mobility-api` | `mad-transit-api` |
| `spring.application.name` | `mad-mobility-api` | `mad-transit-api` |
| Título OpenAPI | Mad Mobility API | Mad Transit API |
| Paquete Kotlin | `io.github.jorgetroya80.madmobility` | `io.github.jorgetroya80.madtransit` |
| Clase principal | `MadMobilityApplication` | `MadTransitApplication` |
| Prefijo de configuración | `mad-mobility.*` | `mad-transit.*` |
| URNs de error (`type` de Problem Details) | `urn:mad-mobility:problem:*` | `urn:mad-transit:problem:*` |
| Imagen Docker | `ghcr.io/jorgetroya80/mad-mobility-api` | `ghcr.io/jorgetroya80/mad-transit-api` |
| Servicios de Render | `mad-mobility-api`, `mad-mobility-web` | `mad-transit-api`, `mad-transit-web` |
| URLs públicas | `mad-mobility-{api,web}.onrender.com` | `mad-transit-{api,web}.onrender.com` |
| App de MobilityLabs | Mad Mobility | Mad Transit |

Las variables de entorno (`EMT_*`, `CORS_ALLOWED_ORIGINS`, `JAVA_TOOL_OPTIONS`) no llevan el nombre y no cambian.

## Assumptions

Por comprobar en el plan:

1. Renombrar un servicio de Render no cambia su subdominio `onrender.com`; por eso se crean servicios nuevos. Si Render sí lo cambiara, bastaría con renombrar.
2. Un sync del Blueprint con nombres de servicio nuevos crea servicios nuevos y no borra los viejos (Render conserva lo que desaparece del archivo, como pasó con la regla `/api/*`). Los viejos se borran a mano al final.
3. Renombrar el repo en GitHub mantiene redirecciones desde la URL antigua (git, releases y páginas), y no afecta a release-please, a los secretos, a la variable `DEPLOY_ENABLED` ni al Blueprint de Render, que sigue el repo por su id. Si el Blueprint pierde el repo, se recrea.
4. La imagen nueva `mad-transit-api` se publica como paquete nuevo en ghcr.io, público como el actual; si sale privado, se cambia su visibilidad a mano.
5. Ningún consumidor depende de los URNs de error: la web no los usa (comprobado con `git grep`). Aun así cambian el contrato público.

## Migración sin corte

El API nuevo debe existir antes de que la web apunte a él, y el deploy hook de un servicio solo acepta su propia imagen. Orden:

1. **Repo:** renombrar en GitHub y actualizar el remote local.
2. **API (`feat(api)!`):** paquete Kotlin, clase principal, prefijo de configuración, nombre Gradle y Spring, título OpenAPI, URNs, comentarios del `Dockerfile` e imagen en `release.yml`. Release **api 0.7.0** con `DEPLOY_ENABLED` en `false`, porque el hook viejo rechazaría la imagen nueva.
3. **Render (`ci`):** `render.yaml` con `mad-transit-api` (imagen `mad-transit-api:latest`) y `mad-transit-web` (`CORS_ALLOWED_ORIGINS` con el origen nuevo). El sync crea los servicios nuevos; se rellenan las credenciales de la EMT y los secretos `RENDER_DEPLOY_HOOK_*` con los hooks nuevos. `scripts/render-web-build.sh` usa la URL nueva del repo.
4. **Web (`feat(web)`):** título "Mad Transit" y `web/.env.production` con la URL del API nuevo. Release **web 0.4.0**; con `DEPLOY_ENABLED` otra vez en `true`, `deploy-web` la publica en el servicio nuevo.
5. **Cierre:** redirección del static site viejo a la URL nueva y borrado del API viejo; app de MobilityLabs; documentación; borrar la imagen vieja de ghcr.io si se quiere.

Cada PR que publica toca solo `api/` o solo `web/` (`release-scope`).

## Commands

```bash
# Comprobar que no queda el nombre viejo (fuera de los CHANGELOG y del histórico)
git grep -n -i -E "mad[-_ ]?mobility|madmobility|BiciMAD Now" -- . ':!*CHANGELOG.md'

# API
./api/gradlew -p api build
./api/gradlew -p api dockerImageTest

# Web
pnpm --filter web run lint
pnpm --filter web run typecheck
pnpm --filter web run test
pnpm --filter web run build

# Producción
curl -s https://mad-transit-api.onrender.com/info
curl -sI -H 'Origin: https://mad-transit-web.onrender.com' https://mad-transit-api.onrender.com/v1/bicimad/stations/25a
```

## Project Structure

```
api/src/{main,test,smokeTest}/kotlin/io/github/jorgetroya80/madtransit/   paquete movido (git mv)
api/settings.gradle.kts, api/build.gradle.kts                             nombre del proyecto y rutas de paquete
api/src/main/resources/application.yaml                                    nombre, título y prefijo mad-transit.*
api/Dockerfile, .github/workflows/release.yml                              imagen mad-transit-api
render.yaml, scripts/render-web-build.sh                                   servicios y URL del repo
web/index.html, web/src/pages/map/ui/MapPage.tsx, web/.env.production      título y URL del API
package.json, api/client/package.json, api/client/README.md                nombre y URL del repo
README.md, api/README.md, web/README.md, docs/                             documentación
```

## Code Style

- El paquete se mueve con `git mv` para conservar la historia; el contenido solo cambia en `package` e `import`.
- Los nombres siguen el patrón actual: `mad-transit` en kebab-case (repo, servicios, prefijo de configuración), `madtransit` en el paquete Kotlin, `MadTransit` en clases y "Mad Transit" en texto.

## Testing Strategy

| Qué | Cómo |
| --- | --- |
| API sin cambios de comportamiento | La suite completa (`./gradlew build`, JaCoCo ≥ 80 %) pasa tras mover el paquete; los tests que comprueban URNs y el prefijo de configuración se actualizan al nombre nuevo |
| Imagen | `dockerImageTest` con el jar `mad-transit-api-*.jar` |
| Web | Los tests del título (`AppLayout.test.tsx`, `MapPage.test.tsx`) esperan "Mad Transit"; lint, typecheck, test y build en verde |
| Nombre viejo | El `git grep` de Commands no devuelve nada fuera de lo excluido |
| Producción | SC de abajo con `curl` y el navegador |

## Boundaries

- **Siempre:** un PR por componente; el API nuevo en marcha antes de cambiar la URL en la web.
- **Preguntar antes:** borrar los servicios viejos de Render o la imagen vieja de ghcr.io (no se puede deshacer).
- **Nunca:** tocar los `CHANGELOG.md`; dejar `DEPLOY_ENABLED` en `true` mientras los hooks apuntan a servicios con otra imagen.

## Success Criteria

1. https://mad-transit-web.onrender.com muestra "Mad Transit" en la pestaña y en la página, y carga las estaciones desde `mad-transit-api.onrender.com` sin errores de CORS.
2. `/info` del API nuevo devuelve `"name":"mad-transit-api"` y la versión 0.7.0 o posterior; un error devuelve `type` `urn:mad-transit:problem:*`.
3. Una release de `api` y otra de `web` se despliegan solas en los servicios nuevos (`deploy-*` en verde).
4. El `git grep` de Commands no encuentra el nombre viejo fuera de lo excluido.
5. El repo se llama `jorgetroya80/mad-transit` y la app de MobilityLabs, "Mad Transit".
6. La suite del API y la de la web pasan sin bajar la cobertura.

## Open Questions

Ninguna. Resueltas por el usuario (2026-10-29):

1. **URNs de error:** cambian a `urn:mad-transit:problem:*`. Rompe el contrato público, de ahí `feat(api)!` y la 0.7.0; nadie los usa y el proyecto está en 0.x.
2. **Servicios viejos de Render:** el static site viejo redirige un tiempo a la URL nueva (`/*` → `https://mad-transit-web.onrender.com/*`, redirect permanente) para no romper enlaces compartidos; el API viejo se borra al terminar la migración. Más adelante se borra también el static site viejo.

## Changelog

- 2026-10-29: creada.
- 2026-10-29: aprobada con las recomendaciones (URNs cambian; el static site viejo redirige). La sección Problema describe solo el cambio de nombre.
