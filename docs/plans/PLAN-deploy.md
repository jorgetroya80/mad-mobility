# Plan: deploy

- Created: 2026-10-28
- Status: **implemented** (2026-10-28)
- Spec: [SPEC-deploy.md](../specs/SPEC-deploy.md)

## Overview

Desplegar el API (imagen de ghcr.io) y la web (`web-dist.tar.gz` de la release) en Render free con un Blueprint (`render.yaml`), completar `deploy-api` y `deploy-web` en `release.yml` con deploy hooks, y hacer los dos cambios de código que pide la spec: cabeceras de proxy en el API y "Powered by EMT de Madrid" en la web.

## Decisiones de implementación

- **Tres ramas y tres PRs**, porque un PR que publica solo puede tocar `api/` o `web/` (`release-scope`):
  - `ci/render-deploy` → PR `ci: deploy api and web to render` (T2-T4). No publica: puede tocar los READMEs de `api/` y `web/`.
  - `fix/api-client-ip` → PR `fix(api): rate limit by the real client ip behind render` (T5).
  - `fix/web-emt-attribution` → PR `fix(web): credit emt as powered by emt de madrid` (T6).
- **Un commit por tarea.** Cuerpos de los squash en prosa, sin bloques de código (release-please).
- **Primero lo arriesgado (T2):** el Blueprint se crea en Render desde la rama `ci/render-deploy` con lo que ya está publicado (`api` 0.6.0, `web` 0.3.0). Así se comprueban los Assumptions 1, 3, 4, 5 y 7 antes de escribir los jobs. Si el rewrite falla (Assumption 1), se para, se cambia la spec al plan B (URL absoluta + CORS) y se replanifica.
- **Las cabeceras del rewrite se miran con un destino de eco.** Durante T2, una regla temporal `/echo/*` → `https://httpbin.org/anything/*` muestra qué cabeceras llegan (`X-Forwarded-For`, `If-None-Match`). Se quita antes del commit de T2.
- **Los fixes van después del PR de `ci`**, así sus releases son la primera prueba real del pipeline: `api` 0.6.1 comprueba SC5 y `web` 0.3.1 comprueba SC6.
- **Plan en el repo.** Igual que en los otros módulos, las casillas de este documento son la fuente de verdad del progreso; no se usa `tasks/`.

## Grafo de dependencias

```
T1 alta de la app en MobilityLabs (manual)
 └── T2 Blueprint + build de la web, creado desde la rama (spike)
       └── T3 jobs deploy-* con deploy hooks
             └── T4 READMEs, PR de ci, merge y DEPLOY_ENABLED=true
                   ├── T5 fix(api) IP del cliente      → release api 0.6.1 → deploy-api
                   └── T6 fix(web) atribución EMT   → release web 0.3.1 → deploy-web
                         └── T7 CORS en el API (render.yaml)
                               └── T8 fix(web) URL directa del API → release web 0.3.2 → deploy-web
                                     └── T9 quitar el rewrite /api, SC1-SC10 y cierre
```

T5 y T6 son independientes entre sí.

## Task List

### Fase 1: Render con lo ya publicado (PR `ci`, rama `ci/render-deploy`)

- [x] **T1: Alta de la app en MobilityLabs** (XS, ~15 min, **manual, usuario**)
  - Descripción: registrar la app en MobilityLabs y obtener `X-ClientId` y `passKey`. Probarlos en local con `EMT_CLIENT_ID` y `EMT_PASS_KEY` en `api/.env.local` (sin email ni contraseña).
  - Aceptación:
    - `./api/gradlew -p api bootRun` arranca y `GET /v1/bicimad/stations` devuelve 200 con las credenciales de la app.
  - Verificación: `curl -s localhost:8080/v1/bicimad/stations | head -c 200`; `curl -s localhost:8080/health`.
  - Archivos: ninguno del repo (`api/.env.local` está ignorado).
  - Dependencias: ninguna.

- [x] **T2: Blueprint y build de la web** (M, ~1 h, **requiere cuenta de Render**)
  - Descripción: `render.yaml` como en la sección Code Style de la spec, con la imagen `:0.6.0` (T3 cambia el tag en cada despliegue). `scripts/render-web-build.sh` (`set -euo pipefail`) lee la versión de `web` del manifest y descarga y extrae `web-dist.tar.gz` en `dist/`. Push de la rama y creación del Blueprint en Render desde `ci/render-deploy`, rellenando `EMT_CLIENT_ID` y `EMT_PASS_KEY`. Comprobar y anotar en el changelog:
    - Assumption 1: con la regla temporal `/echo/*` llegan `X-Forwarded-For` con la IP del cliente, la query string e `If-None-Match`; `/api/v1/bicimad/stations` responde 200 con `ETag`; un segundo `GET` con `If-None-Match` da 304; `/assets/...` sirve el archivo y no `index.html`.
    - Assumption 2: tras 15 min sin tráfico, la web vuelve a cargar las estaciones sin recargar a mano.
    - Assumption 3: el static site tiene deploy hook.
    - Assumption 4: el build de Render descarga la release sin token.
    - Assumption 5: tiempo de arranque del API en los logs de Render ("Started ... in X seconds"); si pasa de 3 min, ajustar `JAVA_TOOL_OPTIONS` en Render.
    - Assumption 7: cómo se valida el Blueprint.
  - Aceptación:
    - SC1, SC2, SC7 (atribución de OpenFreeMap) y SC8 se cumplen con `api` 0.6.0 y `web` 0.3.0.
    - SC9: `grep -r EMT_ dist` sin resultados; `render.yaml` sin secretos.
    - La regla `/echo/*` no está en el commit.
  - Verificación: `bash scripts/render-web-build.sh` en local y `diff` de `dist/index.html` con el del tarball; las comprobaciones con `curl` de Commands en la spec; navegador a 360 y 1280 px.
  - Archivos: `render.yaml`, `scripts/render-web-build.sh`.
  - Dependencias: T1.

### Checkpoint: spike

- [x] Assumptions 1-5 y 7 resueltos y anotados. Si el 1 falla: parar, cambiar la spec al plan B y revisar con el usuario.
- [x] Revisión del usuario de la web en `onrender.com`.

### Fase 2: despliegue automático (mismo PR `ci`)

- [x] **T3: Jobs `deploy-api` y `deploy-web`** (S, ~30 min)
  - Descripción: `deploy-api` llama a `RENDER_DEPLOY_HOOK_API` con `imgURL=ghcr.io/jorgetroya80/mad-mobility-api:<api-version>` (paso de Code Style de la spec); `deploy-web` llama a `RENDER_DEPLOY_HOOK_WEB` (o a la API REST de Render, según el Assumption 3). `timeout-minutes` en los dos y `permissions: {}`. No cambian los `if` ni los `needs`. Secretos creados en GitHub con `gh secret set`.
  - Aceptación:
    - SC10: con `DEPLOY_ENABLED` sin definir, los jobs aparecen como omitidos.
    - Ningún paso imprime la URL del hook.
  - Verificación: `actionlint`; revisión del log de un job (en T5/T6) buscando `api.render.com/deploy`.
  - Archivos: `.github/workflows/release.yml`.
  - Dependencias: T2.

- [x] **T4: Documentación y PR de `ci`** (S, ~30 min)
  - Descripción: URL pública y sección de despliegue en `README.md`, `api/README.md` y `web/README.md` (cómo recrear el Blueprint, qué secretos hay, plan B si Render cambia). Fila `deploy` de `CAPABILITY-MAP.md` con su spec. Changelog de spec y plan. PR `ci: deploy api and web to render`. Tras el merge: cambiar la rama del Blueprint a `main` y `gh variable set DEPLOY_ENABLED --body true`.
  - Aceptación:
    - CI en verde (`pr-title`, `release-scope`, `ci-ok`).
    - El Blueprint sigue a `main` y Render no despliega nada nuevo al cambiar de rama (mismo `render.yaml`).
  - Verificación: `gh pr checks`; `gh variable list`; panel de Render.
  - Archivos: `README.md`, `api/README.md`, `web/README.md`, `docs/specs/CAPABILITY-MAP.md`, `docs/specs/SPEC-deploy.md`, este plan.
  - Dependencias: T3.

### Checkpoint: pipeline

- [x] PR de `ci` mergeado, `DEPLOY_ENABLED=true` y Blueprint en `main`.
- [x] Revisión del usuario antes de los fixes.

### Fase 3: cambios de código, cada uno con su release

- [x] **T5: IP del cliente detrás de Render** (S, ~30 min)
  - Descripción: `RateLimitFilter` usa `True-Client-Ip` como clave del cubo y, si no está o viene vacía, `remoteAddr`. Tests (TDD) en `RateLimitFilterMvcTest`: con capacidad baja, dos `True-Client-Ip` distintos tienen cubos independientes; sin la cabecera se usa `remoteAddr`; un `X-Forwarded-For` distinto no da un cubo nuevo. PR `fix(api): …`; al mergear el PR de release `api` 0.6.1, `deploy-api` despliega esa imagen.
  - Aceptación:
    - El test falla sin el cambio de configuración y pasa con él.
    - SC3 en producción: el `429` de una IP no afecta a otra (móvil con datos y portátil).
    - SC5: `/info` devuelve `build.version` 0.6.1.
  - Verificación: `./api/gradlew -p api test --tests '*RateLimitFilterMvcTest'`; `./api/gradlew -p api build`; `curl -s https://<api>.onrender.com/info`.
  - Archivos: `api/src/main/kotlin/io/github/jorgetroya80/madmobility/shared/web/RateLimitFilter.kt`, `api/src/test/kotlin/io/github/jorgetroya80/madmobility/shared/web/RateLimitFilterMvcTest.kt`.
  - Dependencias: checkpoint del pipeline.

- [x] **T6: Atribución "Powered by EMT de Madrid"** (XS, ~15 min)
  - Descripción: `DataAttribution` muestra "Powered by EMT de Madrid" con enlace a `https://www.emtmadrid.es`; el test lo comprueba. PR `fix(web): …`; al mergear el PR de release `web` 0.3.1, `deploy-web` publica su tarball.
  - Aceptación:
    - SC7 completo en producción.
    - SC6: `index.html` servido igual al del tarball de `web-v0.3.1`.
    - El orden de tabulación y el tamaño de los controles de SC13 de `web-bicimad` no cambian.
  - Verificación: `pnpm --filter web exec vitest run src/shared/ui/data-attribution`; `pnpm --filter web run lint`; `pnpm --filter web run typecheck`; `pnpm --filter web run build`; `curl -s https://<web>.onrender.com/index.html | diff - <(tar -xzOf web-dist.tar.gz ./index.html)`.
  - Archivos: `web/src/shared/ui/data-attribution/DataAttribution.tsx`, `web/src/shared/ui/data-attribution/DataAttribution.test.tsx`.
  - Dependencias: checkpoint del pipeline (independiente de T5).

- [x] **T7: CORS en el API** (XS, ~20 min)
  - Descripción: `CORS_ALLOWED_ORIGINS=https://mad-mobility-web.onrender.com` en el servicio del API de `render.yaml`. Va en el PR `ci` con el cambio de spec y plan (rama `docs/deploy-direct-api`). Al mergear, el Blueprint sincroniza y el API se redespliega con CORS; la web 0.3.1 sigue usando el rewrite mientras tanto.
  - Aceptación:
    - Un `GET` al API con `Origin: https://mad-mobility-web.onrender.com` devuelve `Access-Control-Allow-Origin` con ese origen; con otro `Origin`, no.
  - Verificación: `curl -sI -H 'Origin: https://mad-mobility-web.onrender.com' https://mad-mobility-api.onrender.com/v1/bicimad/stations/25a`; lo mismo con `Origin: https://example.com`; validación del esquema de `render.yaml`.
  - Archivos: `render.yaml`, `docs/specs/SPEC-deploy.md`, este plan.
  - Dependencias: T6.

- [x] **T8: La web llama al API directamente** (XS, ~20 min)
  - Descripción: `web/.env.production` con `VITE_API_BASE_URL=https://mad-mobility-api.onrender.com`; `web/README.md` lo explica. PR `fix(web): call the api directly in production`; al mergear el PR de release `web` 0.3.2, `deploy-web` la despliega.
  - Aceptación:
    - El bundle de `pnpm --filter web run build` contiene la URL del API; `dev` y los tests siguen usando `/api`.
    - SC2 y SC3 en producción: el móvil con datos carga las estaciones mientras una ráfaga desde otra IP recibe 429.
  - Verificación: `pnpm --filter web run lint`; `typecheck`; `test`; `build`; `grep -o 'mad-mobility-api.onrender.com' web/dist/assets/*.js`; consola del navegador sin errores de CORS.
  - Archivos: `web/.env.production`, `web/README.md`.
  - Dependencias: T7.

- [x] **T9: Quitar el rewrite `/api`, comprobación final y cierre** (S, ~30 min)
  - Descripción: quitar la regla `/api/*` de `render.yaml` y la mención del mismo origen en los READMEs; recorrer SC1-SC10 en producción; spec y plan a **implemented**; fila `deploy` de `CAPABILITY-MAP.md` a implemented; sección Deployment de `docs/ideas/bicimad-now.md` con Render. PR `ci: …`.
  - Aceptación:
    - `/api/...` en la web ya no llega al API (sirve `index.html`).
    - SC1-SC10 anotados en el changelog del plan.
  - Verificación: `gh pr checks`; `curl` de SC1-SC10.
  - Archivos: `render.yaml`, `README.md`, `web/README.md`, `docs/specs/SPEC-deploy.md`, este plan, `docs/specs/CAPABILITY-MAP.md`, `docs/ideas/bicimad-now.md`.
  - Dependencias: T8.

### Checkpoint final

- [x] SC1-SC10 de la spec comprobados.
- [x] Spec y plan pasan a **implemented**; `CAPABILITY-MAP.md` actualizado.

## Riesgos

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| El rewrite de Render no conserva cabeceras o no acepta comodines | Alto | Se comprueba primero (T2); plan B de la spec: URL absoluta + CORS, que obliga a compilar la web con `VITE_API_BASE_URL` en `publish-web` |
| Spring Boot no arranca a tiempo con 0,1 CPU, o Render lo mata por memoria | Medio | Medido en T2; `JAVA_TOOL_OPTIONS` en Render sin tocar la imagen |
| El alta en MobilityLabs tarda o la rechazan | Medio | T2 puede empezar con `EMT_EMAIL` y `EMT_PASSWORD` en Render y cambiar a las de la app después |
| Render cambia o retira el free tier | Bajo | Todo está en `render.yaml` y en artefactos ya publicados; mover a otra plataforma es cambiar T2-T3 |
| La región no se puede cambiar después de crear el servicio | Bajo | `frankfurt` fijado en `render.yaml` desde el primer commit |
| Un fallo del deploy hook deja la release publicada sin desplegar | Bajo | El job falla en rojo (`curl --fail`) y se relanza; el artefacto ya está publicado |

## Paralelización

Un solo desarrollador: orden T1 → T9. T5 y T6 son independientes entre sí.

## Changelog

- 2026-10-28: creado a partir de la spec aprobada.
- 2026-10-28: aprobado por el usuario.
- 2026-10-28: T1 en curso. App "Mad Mobility" registrada en MobilityLabs, pendiente de aprobación. Mientras tanto, T2 usa `EMT_EMAIL` y `EMT_PASSWORD` en Render (riesgo previsto); al aprobarse, se cambian por `EMT_CLIENT_ID` y `EMT_PASS_KEY` y se cierra T1.
- 2026-10-28: T1 hecha. App "Mad Mobility" aprobada. Con solo `EMT_CLIENT_ID` y `EMT_PASS_KEY`, `bootRun` en local y el API en Render hacen login y `/v1/bicimad/stations` devuelve 200. En Render el email y la contraseña quedan vacíos. Si falta una de las dos claves de la app, el API no arranca (`EMT_PASS_KEY is missing (EMT_CLIENT_ID is set)`) y Render mantiene la versión anterior.
- 2026-10-28: T2 en curso. Render ejecuta `pnpm install` antes del build command de un static site, y GitHub Packages responde 401 sin token; se desactiva con `SKIP_INSTALL_DEPS=true`. `/` necesita su propia cabecera `no-cache` (el navegador no pide `/index.html`). Assumption 1 comprobado: el rewrite `/api/*` pasa ruta completa y query string, el cliente recibe `ETag` y 304, y los assets se sirven antes que el rewrite `/*`. Con la regla temporal `/echo/*`: `If-None-Match` no llega al destino (el 304 lo resuelve el CDN de Render); `X-Forwarded-For` llega con la IP del cliente a la izquierda, pero un cliente puede anteponer otra; `True-Client-Ip` y `Cf-Connecting-Ip` llevan la IP real y Cloudflare sobrescribe los valores que manda el cliente. Arranque del API en Render: unos 2 min desde "Starting service" hasta el puerto (JVM ~65 s, contexto ~35 s).
- 2026-10-28: T5 cambia a `True-Client-Ip` (opción A del usuario; ver changelog de la spec).
- 2026-10-28: T2, Assumption 3 comprobado: el static site tiene Deploy Hook. Assumption 2 falla en la primera prueba: tras 18 min dormido, la primera respuesta tarda unos 7 min porque el API arranca dos veces. El primer intento muere sin log tras el contexto de Spring (14:25:31); el segundo arranca a las 14:29:11 y da "Started in 63.9 seconds". Sin "Out of memory" en los logs (falta mirar Events). Causas posibles: memoria (heap al 75 % de 512 MB) o el health check, que reinicia el API antes de que abra el puerto. Arreglo en `render.yaml` sin tocar la imagen: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:TieredStopAtLevel=1 -Xss512k`. Pendiente: Manual Sync y deploy, línea "Started" nueva y repetir el arranque en frío.
- 2026-10-28: T2, Assumption 2 y 5 comprobados con el ajuste de la JVM: Spring arranca en 31 s (antes 64-72 s). Con el API dormido desde las 15:50:38 UTC, la primera petición por `/api` responde 200 en 162 s, sin doble arranque (SC4 < 3 min, con poco margen). Una pestaña de la web abierta mantiene el API despierto (consulta cada 60 s).
- 2026-10-28: T2 hecha y checkpoint del spike superado. El usuario revisa la web en móvil y a 1280 px: estaciones, panel lateral y atribución de OpenFreeMap y OpenStreetMap (SC1, SC7 parcial). SC2, SC8 y SC9 comprobados con `curl`.
- 2026-10-28: T3 hecha con el agente `devops-engineer` y las reglas de `github-actions-creator`. `deploy-api` llama al hook con `imgURL` (versión exacta) y `deploy-web` hace `POST` al hook del static site; `permissions: {}`, `timeout-minutes: 5`, sin `${{ }}` en `run:`. `render.yaml` pasa la imagen a `:latest`: con un tag fijo, un Sync del Blueprint volvería a esa versión. Secretos `RENDER_DEPLOY_HOOK_API` y `RENDER_DEPLOY_HOOK_WEB` creados por el usuario. `actionlint` sin errores. La primera ejecución real se comprueba en T5 y T6.
- 2026-10-28: T4: READMEs (raíz, `api`, `web`) con la URL pública y la sección Deployment; fila `deploy` de `CAPABILITY-MAP.md` con su spec, en approved. PR `ci: deploy api and web to render`.
- 2026-10-28: T4 hecha. PR #57 mergeado (`e011ddb`); el Blueprint de Render sigue a `main` y `DEPLOY_ENABLED=true`. La ejecución de Release del merge deja `deploy-*` omitidos (sin release).
- 2026-10-28: T5 hecha. PR #58 (TDD: dos de los tres tests nuevos fallaban antes del cambio) y release `api` 0.6.1 (PR #59). Primera ejecución real de `deploy-api`: llama al hook con `imgURL=...:0.6.1`, el log muestra `HOOK: ***`, y `/info` devuelve 0.6.1 unos 90 s después (SC5). En producción, 120 peticiones a la vez desde una IP: 90 sin cabecera dan 37 × 429 y 30 con `X-Forwarded-For` distintos dan 21 × 429, así que cambiar esa cabecera ya no salta el límite. Falta ver dos IPs reales con cubos separados (SC3), en T7.
- 2026-10-28: T6 hecha. PR #60 (TDD: el test de `DataAttribution` falló antes del cambio) y release `web` 0.3.1 (PR #61). Primera ejecución real de `deploy-web`: `POST` al hook, el log muestra `HOOK: ***`, y a los 37 s el `index.html` servido es idéntico al del tarball de `web-v0.3.1` (SC6). El bundle servido contiene "Powered by" y ningún `EMT_` (SC7, SC9).
- 2026-10-28: T7 original parada. SC3 falla: con la misma IP, `/api` por el rewrite da 429 y la llamada directa al API, 200; durante 3 min de ráfaga desde una IP, el móvil con datos (otra IP) no carga. El rewrite hace una segunda petición desde Render que vuelve a pasar por Cloudflare y le pone a `True-Client-Ip` la IP de Render. El usuario elige el plan B (llamada directa con CORS): T7 pasa a ser CORS en el API, T8 la URL directa en la web y T9 el cierre, que también quita el rewrite. SC1, SC2 (con el rewrite), SC4-SC10 ya comprobados.
- 2026-10-28: T7 hecha. PR #62. El sync del Blueprint falla con "not found: render.yaml": un Blueprint de Render no puede cambiar de rama y el suyo seguía en `ci/render-deploy`, borrada al mergear el PR #57. Por eso el cambio de rama de T4 no llegó a aplicarse. `CORS_ALLOWED_ORIGINS` se añade a mano en el servicio, con el mismo valor que `render.yaml`. Comprobado: con `Origin` de la web, `GET` y preflight devuelven `Access-Control-Allow-Origin` y `Access-Control-Expose-Headers: ETag, X-Request-Id`; con otro `Origin`, Spring responde 403. Pendiente para T9: recrear el Blueprint desde `main`.
- 2026-10-28: T8 hecha. PR #63 y release `web` 0.3.2 (PR #64); `deploy-web` la publica y la URL del API va en el chunk `config-*.js` servido. El Blueprint vuelve a sincronizar desde `main` (el usuario cambia su rama en Blueprints; Manual Sync sin "not found"). SC3 comprobado: durante 3 min de ráfaga directa al API desde una IP, que recibe 429, el móvil con datos (otra IP) carga las estaciones y su detalle.
- 2026-10-28: T9 hecha. Rewrite `/api/*` fuera de `render.yaml`, del README y del panel de Render. SC1-SC10 en producción: SC1 web en móvil y escritorio, `?station` directo; SC2 la web llama al API con CORS (`Access-Control-Allow-Origin` para su origen, 403 para otros); SC3 una IP en ráfaga recibe 429 mientras otra carga; SC4 arranque en frío en 162 s; SC5 `deploy-api` despliega 0.6.1; SC6 `index.html` igual al tarball de `web-v0.3.1` y `web-v0.3.2`; SC7 "Powered by EMT de Madrid" y atribución del mapa; SC8 `no-cache` en `/` y `/index.html`, `immutable` en `/assets/*`; SC9 sin secretos en `render.yaml`, bundle ni logs; SC10 `deploy-*` omitidos sin `DEPLOY_ENABLED`. Spec, plan y fila `deploy` de `CAPABILITY-MAP.md` en implemented.
