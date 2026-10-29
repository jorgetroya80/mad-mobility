# Spec: deploy

- Created: 2026-10-28
- Status: **implemented** (2026-10-28)
- Plan: [PLAN-deploy.md](../plans/PLAN-deploy.md)

Módulo `deploy` del [Capability Map](CAPABILITY-MAP.md). Publica el API y la web en [Render](https://render.com), en el plan gratuito, y completa los jobs `deploy-api` y `deploy-web` de `release.yml`, que hoy son placeholders.

## Objective

- Como usuario, quiero abrir una URL pública y ver las estaciones de BiciMAD cerca de mí, sin instalar nada.
- Como mantenedor, quiero que cada release de `api` o de `web` se despliegue sola, con el mismo artefacto que se publicó (imagen Docker o `web-dist.tar.gz`), sin volver a compilar.
- Como mantenedor, quiero la infraestructura descrita en el repo (`render.yaml`), para poder recrearla y revisarla en un PR.
- Como mantenedor, quiero cumplir las condiciones de uso de la EMT y de OpenFreeMap antes de publicar.

**Fuera de alcance:** dominio propio (se usan los subdominios `onrender.com`); planes de pago; varias instancias; un servicio que haga ping para que el API no se duerma; mensaje en la web de "el servidor está despertando"; entornos de preview; monitorización externa; teselas propias.

## Decisiones

- **Render para las dos partes, plan gratuito** (decisión del usuario, 2026-10-28). Alternativas descartadas: Google Cloud Run (pide tarjeta), Koyeb (pide tarjeta con retención de 29 $), Fly.io (sin plan gratuito para cuentas nuevas), GitHub Pages para la web (se prefiere todo en un sitio).
- **API: web service con imagen prebuilt** (`runtime: image`) desde `ghcr.io/jorgetroya80/mad-transit-api`, que es pública. Render no compila: despliega la imagen que `publish-api` ya probó con `dockerImageTest` y la comprobación de arranque. Región Frankfurt, la más cercana a Madrid.
- **Web: static site que descarga el artefacto de la release.** El build command de Render no ejecuta `pnpm install`: lee la versión de `web` en `.release-please-manifest.json` y descarga `web-dist.tar.gz` de la GitHub Release `web-v<versión>`. Así se evita el token de GitHub Packages en Render, y lo publicado es exactamente lo que adjuntó `publish-web`.
- **La web llama al API directamente, con CORS** (plan B, decisión del usuario del 2026-10-28). `web/.env.production` fija `VITE_API_BASE_URL=https://mad-transit-api.onrender.com`, así que el build de producción, y por tanto `web-dist.tar.gz`, lleva esa URL. El API permite ese origen con `CORS_ALLOWED_ORIGINS=https://mad-transit-web.onrender.com` en `render.yaml`. En desarrollo no cambia nada: `dev` y `dev:mock` siguen usando `/api` y el proxy de Vite.
  - Por qué se descartó el rewrite `/api/*` del static site, la idea inicial: funciona como proxy, pero hace una segunda petición desde Render al API que vuelve a pasar por Cloudflare, y ese segundo Cloudflare escribe en `True-Client-Ip` la IP de Render. Todos los usuarios de la web compartían así unos pocos cubos de rate limit, y un solo cliente podía bloquear a todos. Comprobado en T7: con la misma IP, `/api` por el rewrite daba 429 y la llamada directa al API, 200. La prueba de cabeceras de T2 no lo detectó porque su destino de eco (`httpbin.org`) estaba fuera de Render.
  - Coste aceptado: el artefacto de la web queda atado a la URL del API, y la web y el API ya no comparten origen.
- **Despliegue por deploy hooks, sin auto-deploy.** `deploy-api` llama al hook del API con `imgURL=ghcr.io/jorgetroya80/mad-transit-api:<versión>`; `deploy-web` llama al hook del static site. Las URLs de los hooks son secretos del repo. Se activa con `DEPLOY_ENABLED=true`, como ya prevé `foundation`.
- **Blueprint en `render.yaml`** en la raíz del repo. Los secretos van con `sync: false` y se rellenan una vez en el panel de Render.
- **Credenciales de app registrada.** Antes de publicar se registra la app en MobilityLabs y el API usa `EMT_CLIENT_ID` + `EMT_PASS_KEY`, no el email y la contraseña personales.

## Assumptions

Por comprobar en la fase de plan:

1. El rewrite de un static site de Render a una URL externa funciona como proxy para `GET` con query string, conserva `ETag`/`If-None-Match` y `Cache-Control`, y añade `X-Forwarded-For` con la IP del cliente. Además, el comodín de origen pasa al destino (`/api/*` → `https://<api>.onrender.com/*`), las reglas se aplican en orden y un archivo que existe en `dist` tiene prioridad sobre el rewrite `/*`. La documentación dice que el destino puede ser "a full, publicly accessible URL", pero no habla de cabeceras, comodines ni timeouts.
2. El rewrite espera lo bastante cuando el API está dormido. Si corta antes de que el API despierte, la web muestra un error y TanStack Query reintenta; se acepta si la segunda o tercera petición funciona.
3. Los static sites de Render tienen deploy hook (la documentación de deploy hooks solo habla de servicios). Si no lo tienen, `deploy-web` usa la API REST de Render (`POST /v1/services/{id}/deploys`) con un API key como secreto.
4. El build de un static site tiene `curl` y `tar` y acceso a GitHub. Las releases del repo son públicas, así que no hace falta token.
5. Spring Boot arranca en el free tier (512 MB, 0,1 CPU según fuentes no oficiales) en menos de 3 min, con `-XX:MaxRAMPercentage=75` (ya en el `Dockerfile`). Si no, se ajustan `JAVA_TOOL_OPTIONS` en Render (por ejemplo `-XX:TieredStopAtLevel=1`, `-Xss512k`) sin tocar la imagen.
6. El health check `/health/ready` no depende de la EMT (SC10 de `api-core`), así que Render no reinicia el API cuando la EMT cae.
7. El CLI de Render valida un Blueprint en local (`render blueprints validate`). Si no existe o pide sesión, la validación es crear el Blueprint en el panel, que muestra los errores antes de aplicar nada.

## Tech Stack

- Render: web service `runtime: image`, plan `free`, región `frankfurt`; static site `runtime: static`; Blueprint `render.yaml`.
- Imagen: `ghcr.io/jorgetroya80/mad-transit-api` (Java 25 JRE, ya publicada por `publish-api`).
- Web: `web-dist.tar.gz` de la GitHub Release `web-v<versión>` (ya adjuntado por `publish-web`).
- GitHub Actions: `curl` a los deploy hooks; sin acciones nuevas de terceros.
- Spring Boot 4.1.1: `RateLimitFilter` lee `True-Client-Ip`.

## Commands

```bash
# Validar el Blueprint (si el CLI lo permite, ver Assumptions)
render blueprints validate render.yaml

# Workflows
actionlint

# Build command del static site (lo ejecuta Render)
VERSION=$(sed -n 's/.*"web": *"\([^"]*\)".*/\1/p' .release-please-manifest.json) \
  && mkdir -p dist \
  && curl -fsSL "https://github.com/jorgetroya80/mad-transit/releases/download/web-v${VERSION}/web-dist.tar.gz" | tar -xz -C dist

# Tests de los cambios de código
./api/gradlew -p api test --tests '*RateLimitFilterMvcTest'
pnpm --filter web exec vitest run src/shared/ui/data-attribution

# Comprobaciones tras desplegar
curl -s https://<api>.onrender.com/info
curl -sI https://<web>.onrender.com/index.html
curl -s "https://<web>.onrender.com/api/v1/bicimad/stations?near=40.4168,-3.7038&radius=500"
```

## Project Structure

```
render.yaml                         Blueprint: API (image) y web (static)
scripts/render-web-build.sh         build command del static site: descarga web-dist.tar.gz
.github/workflows/release.yml       deploy-api y deploy-web con deploy hooks
api/src/main/kotlin/.../shared/web/RateLimitFilter.kt   IP del cliente desde True-Client-Ip
api/src/test/kotlin/.../shared/web/RateLimitFilterMvcTest.kt   cubos por True-Client-Ip
web/src/shared/ui/data-attribution/      texto "Powered by EMT de Madrid" y su test
README.md, api/README.md, web/README.md  URL pública y despliegue
```

## Code Style

`render.yaml` sigue el estilo de los workflows: comentarios en inglés solo donde la razón no es obvia, y sin valores secretos.

```yaml
services:
  - type: web
    name: mad-transit-api
    runtime: image
    image:
      url: ghcr.io/jorgetroya80/mad-transit-api:latest
    plan: free
    region: frankfurt
    healthCheckPath: /health/ready
    envVars:
      # Registered MobilityLabs app; filled once in the Render dashboard
      - key: EMT_CLIENT_ID
        sync: false
      - key: EMT_PASS_KEY
        sync: false
      # The web calls the API from its own origin
      - key: CORS_ALLOWED_ORIGINS
        value: https://mad-transit-web.onrender.com

  - type: web
    name: mad-transit-web
    runtime: static
    # Deploys the web-dist.tar.gz of the current web release, never rebuilds it
    buildCommand: ./scripts/render-web-build.sh
    staticPublishPath: ./dist
    autoDeployTrigger: off
    routes:
      - type: rewrite
        source: /*
        destination: /index.html
    headers:
      - path: /assets/*
        name: Cache-Control
        value: public, max-age=31536000, immutable
      - path: /index.html
        name: Cache-Control
        value: no-cache
```

El build command va en `scripts/render-web-build.sh` (`set -euo pipefail`) en vez de en línea, para leerlo y probarlo en local.

Los jobs de despliegue no imprimen la URL del hook:

```yaml
- name: Trigger the Render deploy
  env:
    HOOK: ${{ secrets.RENDER_DEPLOY_HOOK_API }}
    IMAGE: ghcr.io/jorgetroya80/mad-transit-api:${{ needs.release-please.outputs.api-version }}
  run: curl --fail --silent --show-error --get --data-urlencode "imgURL=$IMAGE" "$HOOK" > /dev/null
```

## Testing Strategy

| Qué | Cómo | Dónde |
| --- | --- | --- |
| Rate limit detrás del proxy | `MockMvc` con dos `True-Client-Ip` distintos: el `429` de uno no afecta al otro; sin cabecera (o vacía) se sigue usando `remoteAddr`; `X-Forwarded-For` se ignora | `RateLimitFilterMvcTest` |
| Atribución | Testing Library: texto "Powered by EMT de Madrid" y enlace a `https://www.emtmadrid.es` | `DataAttribution.test.tsx` |
| Build del static site | Ejecutar `scripts/render-web-build.sh` en local y comparar `dist/index.html` con el del tarball | Manual, en el PR de `ci` |
| Workflows y Blueprint | `actionlint` (ya en CI) y validación del Blueprint (Assumption 7) | CI y local |
| Despliegue | SC1-SC10 contra las URLs públicas con `curl` y el navegador (360 y 1280 px) | Manual, tras la primera release desplegada |

Sin tests end-to-end, como en el resto del proyecto. La cobertura de JaCoCo y Vitest no baja.

## Cambios en el código

### API (`fix(api)`, sale en una release de `api`)

- `RateLimitFilter` toma la IP del cliente de la cabecera `True-Client-Ip` y, si no está o viene vacía, de `request.remoteAddr`. Sin esto, detrás del proxy de Render `remoteAddr` es siempre la IP del proxy: todos los usuarios compartirían un único cubo de 60 peticiones.
- Por qué `True-Client-Ip` y no `X-Forwarded-For` (decisión del usuario, 2026-10-28, tras la prueba de T2): Cloudflare, delante de Render, escribe en `True-Client-Ip` la IP real y sobrescribe el valor que mande el cliente; en `X-Forwarded-For` conserva lo que mande el cliente a la izquierda, así que se puede falsear. Las peticiones directas a `<api>.onrender.com` también pasan por Cloudflare.
- Límite conocido: la cabecera es propia de Render/Cloudflare. En otra plataforma sin ella, el filtro vuelve a `remoteAddr` (un cubo compartido), sin romper nada. En local, un cliente puede mandar la cabecera; da igual, porque no hay proxy.

### Web (`fix(web)`, sale en una release de `web`)

- `web/.env.production` con `VITE_API_BASE_URL=https://mad-transit-api.onrender.com` (ver Decisiones).

- `DataAttribution` dice "Powered by EMT de Madrid", con enlace a `https://www.emtmadrid.es`. Las condiciones de MobilityLabs exigen ese texto; hoy pone "Datos: EMT Madrid MobilityLabs".
- La atribución del mapa ("OpenFreeMap © OpenMapTiles Data from OpenStreetMap") la añade MapLibre desde el estilo. No cambia.

### Infraestructura (`ci`)

- `render.yaml`: servicio `mad-transit-api` (image, `free`, `frankfurt`, `healthCheckPath: /health/ready`, variables `EMT_CLIENT_ID` y `EMT_PASS_KEY` con `sync: false`, `CORS_ALLOWED_ORIGINS` con el origen de la web) y static site `mad-transit-web` (`scripts/render-web-build.sh`, `staticPublishPath: ./dist`, `autoDeployTrigger: off`, rewrite `/*` a `/index.html`, cabeceras de caché). El rewrite `/api/*` se quita cuando la web ya llama al API directamente. Ver Code Style.
- Cabeceras del static site: `/assets/*` con `Cache-Control: public, max-age=31536000, immutable` (nombres con hash de Vite); `/index.html` con `Cache-Control: no-cache`.
- `release.yml`: `deploy-api` y `deploy-web` llaman a su deploy hook con `curl --fail` y los secretos `RENDER_DEPLOY_HOOK_API` y `RENDER_DEPLOY_HOOK_WEB`. Las condiciones `if` y los `needs` actuales no cambian.
- READMEs: URL pública, cómo se despliega y cómo recrear el Blueprint.

## Entrega

Cada PR de release toca solo `api/` o solo `web/` (ver la regla de `ci.yml`):

1. `fix(api)`: cabeceras de proxy. Al mergear su release, la imagen nueva queda en ghcr.io.
2. `fix(web)`: atribución de la EMT. Al mergear su release, el `web-dist.tar.gz` nuevo queda en la GitHub Release.
3. `ci`: `render.yaml`, jobs `deploy-*` y READMEs.
4. Manual, fuera de Git: alta de la app en MobilityLabs, creación del Blueprint en Render, secretos en Render y en GitHub, `DEPLOY_ENABLED=true`.

## Boundaries

- **Siempre:** los secretos de la EMT solo en Render (`sync: false`) y los deploy hooks solo en secretos de GitHub; nunca en `render.yaml`, en logs ni en el bundle de la web.
- **Preguntar antes:** pasar a un plan de pago, añadir dominio propio, cambiar de región (no se puede cambiar después de crear el servicio).
- **Nunca:** un ping programado para mantener el API despierto; compilar la imagen o la web en Render.

## Success Criteria

1. `https://<web>.onrender.com` carga el mapa con las estaciones; `?station=<número>` abre el detalle al entrar directamente o al recargar.
2. La web pide las estaciones a `https://mad-transit-api.onrender.com/v1/bicimad/...` y recibe 200 con `Access-Control-Allow-Origin: https://mad-transit-web.onrender.com`, sin errores de CORS en la consola. Otro origen no recibe esa cabecera.
3. Dos IPs distintas tienen cubos de rate limit independientes en producción: el `429` de una no afecta a la otra.
4. Con el API dormido, la primera visita muestra las estaciones en menos de 3 min sin recargar a mano.
5. Una release de `api` despliega la imagen de esa versión: `/info` devuelve `build.version` igual a la etiqueta `api-v<versión>`.
6. Una release de `web` despliega su `web-dist.tar.gz`: el `index.html` servido es idéntico al del tarball de la release.
7. Se ve "Powered by EMT de Madrid" con enlace a la web de la EMT, y la atribución de OpenFreeMap y OpenStreetMap en el mapa.
8. `/assets/*` responde con `immutable` y `/index.html` con `no-cache`.
9. Ningún secreto aparece en `render.yaml`, en los logs de GitHub Actions ni en el bundle (`grep` de `EMT_` en `dist`).
10. Con `DEPLOY_ENABLED` distinto de `true`, los jobs `deploy-*` siguen omitidos (SC8 de `foundation`).

## Open Questions

- Ninguna. Las dudas técnicas están en Assumptions y se cierran en el plan.

## Changelog

- 2026-10-28: creada. Plataforma elegida: Render para el API y la web (decisión del usuario). Incluye la revisión de las condiciones de uso de MobilityLabs y OpenFreeMap.
- 2026-10-28: revisión con la skill `spec-driven-development`: añadidas Tech Stack, Commands, Project Structure, Code Style y Testing Strategy; build command en `scripts/render-web-build.sh`; `autoDeployTrigger: off` en el static site; orden de los rewrites y Assumption 7 (validar el Blueprint).
- 2026-10-28: rate limit por `True-Client-Ip` en vez de `server.forward-headers-strategy` + `X-Forwarded-For` (decisión del usuario, opción A), tras comprobar en T2 que `X-Forwarded-For` se puede falsear detrás de Render.
- 2026-10-28: aprobada por el usuario; plan en [PLAN-deploy.md](../plans/PLAN-deploy.md).
- 2026-10-28: implementación de T1-T4 en la rama `ci/render-deploy`. Cambios sobre lo escrito: `SKIP_INSTALL_DEPS=true` en el static site (Render ejecuta `pnpm install` por defecto y GitHub Packages responde 401), cabecera `no-cache` también en `/`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:TieredStopAtLevel=1 -Xss512k` en el API (arranque en frío de ~7 min a 162 s) e imagen `:latest` en el Blueprint (el hook fija la versión). Detalle en el changelog del plan.
- 2026-10-28: la web llama al API directamente con CORS (plan B; decisión del usuario, opción 1) en lugar del rewrite `/api/*`, porque con el rewrite todos los usuarios compartían el cubo del rate limit (ver Decisiones). Cambian Decisiones, Code Style, Infraestructura y SC2.
- 2026-10-28: implementada. SC1-SC10 comprobados en producción (detalle en el changelog del plan). Se quita el rewrite `/api/*` de `render.yaml`; un sync del Blueprint no borra reglas, así que también se quita en el panel de Render.
