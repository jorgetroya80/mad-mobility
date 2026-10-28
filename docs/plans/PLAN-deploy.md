# Plan: deploy

- Created: 2026-10-28
- Status: **approved** (2026-10-28)
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
                         └── T7 SC1-SC10 en producción y cierre
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

- [ ] **T2: Blueprint y build de la web** (M, ~1 h, **requiere cuenta de Render**)
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

- [ ] Assumptions 1-5 y 7 resueltos y anotados. Si el 1 falla: parar, cambiar la spec al plan B y revisar con el usuario.
- [ ] Revisión del usuario de la web en `onrender.com`.

### Fase 2: despliegue automático (mismo PR `ci`)

- [ ] **T3: Jobs `deploy-api` y `deploy-web`** (S, ~30 min)
  - Descripción: `deploy-api` llama a `RENDER_DEPLOY_HOOK_API` con `imgURL=ghcr.io/jorgetroya80/mad-mobility-api:<api-version>` (paso de Code Style de la spec); `deploy-web` llama a `RENDER_DEPLOY_HOOK_WEB` (o a la API REST de Render, según el Assumption 3). `timeout-minutes` en los dos y `permissions: {}`. No cambian los `if` ni los `needs`. Secretos creados en GitHub con `gh secret set`.
  - Aceptación:
    - SC10: con `DEPLOY_ENABLED` sin definir, los jobs aparecen como omitidos.
    - Ningún paso imprime la URL del hook.
  - Verificación: `actionlint`; revisión del log de un job (en T5/T6) buscando `api.render.com/deploy`.
  - Archivos: `.github/workflows/release.yml`.
  - Dependencias: T2.

- [ ] **T4: Documentación y PR de `ci`** (S, ~30 min)
  - Descripción: URL pública y sección de despliegue en `README.md`, `api/README.md` y `web/README.md` (cómo recrear el Blueprint, qué secretos hay, plan B si Render cambia). Fila `deploy` de `CAPABILITY-MAP.md` con su spec. Changelog de spec y plan. PR `ci: deploy api and web to render`. Tras el merge: cambiar la rama del Blueprint a `main` y `gh variable set DEPLOY_ENABLED --body true`.
  - Aceptación:
    - CI en verde (`pr-title`, `release-scope`, `ci-ok`).
    - El Blueprint sigue a `main` y Render no despliega nada nuevo al cambiar de rama (mismo `render.yaml`).
  - Verificación: `gh pr checks`; `gh variable list`; panel de Render.
  - Archivos: `README.md`, `api/README.md`, `web/README.md`, `docs/specs/CAPABILITY-MAP.md`, `docs/specs/SPEC-deploy.md`, este plan.
  - Dependencias: T3.

### Checkpoint: pipeline

- [ ] PR de `ci` mergeado, `DEPLOY_ENABLED=true` y Blueprint en `main`.
- [ ] Revisión del usuario antes de los fixes.

### Fase 3: cambios de código, cada uno con su release

- [ ] **T5: IP del cliente detrás de Render** (S, ~30 min)
  - Descripción: `RateLimitFilter` usa `True-Client-Ip` como clave del cubo y, si no está o viene vacía, `remoteAddr`. Tests (TDD) en `RateLimitFilterMvcTest`: con capacidad baja, dos `True-Client-Ip` distintos tienen cubos independientes; sin la cabecera se usa `remoteAddr`; un `X-Forwarded-For` distinto no da un cubo nuevo. PR `fix(api): …`; al mergear el PR de release `api` 0.6.1, `deploy-api` despliega esa imagen.
  - Aceptación:
    - El test falla sin el cambio de configuración y pasa con él.
    - SC3 en producción: el `429` de una IP no afecta a otra (móvil con datos y portátil).
    - SC5: `/info` devuelve `build.version` 0.6.1.
  - Verificación: `./api/gradlew -p api test --tests '*RateLimitFilterMvcTest'`; `./api/gradlew -p api build`; `curl -s https://<api>.onrender.com/info`.
  - Archivos: `api/src/main/kotlin/io/github/jorgetroya80/madmobility/shared/web/RateLimitFilter.kt`, `api/src/test/kotlin/io/github/jorgetroya80/madmobility/shared/web/RateLimitFilterMvcTest.kt`.
  - Dependencias: checkpoint del pipeline.

- [ ] **T6: Atribución "Powered by EMT de Madrid"** (XS, ~15 min)
  - Descripción: `DataAttribution` muestra "Powered by EMT de Madrid" con enlace a `https://www.emtmadrid.es`; el test lo comprueba. PR `fix(web): …`; al mergear el PR de release `web` 0.3.1, `deploy-web` publica su tarball.
  - Aceptación:
    - SC7 completo en producción.
    - SC6: `index.html` servido igual al del tarball de `web-v0.3.1`.
    - El orden de tabulación y el tamaño de los controles de SC13 de `web-bicimad` no cambian.
  - Verificación: `pnpm --filter web exec vitest run src/shared/ui/data-attribution`; `pnpm --filter web run lint`; `pnpm --filter web run typecheck`; `pnpm --filter web run build`; `curl -s https://<web>.onrender.com/index.html | diff - <(tar -xzOf web-dist.tar.gz ./index.html)`.
  - Archivos: `web/src/shared/ui/data-attribution/DataAttribution.tsx`, `web/src/shared/ui/data-attribution/DataAttribution.test.tsx`.
  - Dependencias: checkpoint del pipeline (independiente de T5).

- [ ] **T7: Comprobación final y cierre** (S, ~30 min)
  - Descripción: recorrer SC1-SC10 en producción; spec y plan a **implemented**; fila `deploy` de `CAPABILITY-MAP.md` a implemented; sección Deployment de `docs/ideas/bicimad-now.md` con la plataforma elegida. PR `docs: …`.
  - Aceptación:
    - SC1-SC10 anotados en el changelog del plan.
  - Verificación: `gh pr checks`.
  - Archivos: `docs/specs/SPEC-deploy.md`, este plan, `docs/specs/CAPABILITY-MAP.md`, `docs/ideas/bicimad-now.md`.
  - Dependencias: T5, T6.

### Checkpoint final

- [ ] SC1-SC10 de la spec comprobados.
- [ ] Spec y plan pasan a **implemented**; `CAPABILITY-MAP.md` actualizado.

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

Un solo desarrollador: orden T1 → T7. T5 y T6 son independientes entre sí.

## Changelog

- 2026-10-28: creado a partir de la spec aprobada.
- 2026-10-28: aprobado por el usuario.
- 2026-10-28: T1 en curso. App "Mad Mobility" registrada en MobilityLabs, pendiente de aprobación. Mientras tanto, T2 usa `EMT_EMAIL` y `EMT_PASSWORD` en Render (riesgo previsto); al aprobarse, se cambian por `EMT_CLIENT_ID` y `EMT_PASS_KEY` y se cierra T1.
- 2026-10-28: T1 hecha. App "Mad Mobility" aprobada. Con solo `EMT_CLIENT_ID` y `EMT_PASS_KEY`, `bootRun` en local y el API en Render hacen login y `/v1/bicimad/stations` devuelve 200. En Render el email y la contraseña quedan vacíos. Si falta una de las dos claves de la app, el API no arranca (`EMT_PASS_KEY is missing (EMT_CLIENT_ID is set)`) y Render mantiene la versión anterior.
- 2026-10-28: T2 en curso. Render ejecuta `pnpm install` antes del build command de un static site, y GitHub Packages responde 401 sin token; se desactiva con `SKIP_INSTALL_DEPS=true`. `/` necesita su propia cabecera `no-cache` (el navegador no pide `/index.html`). Assumption 1 comprobado: el rewrite `/api/*` pasa ruta completa y query string, el cliente recibe `ETag` y 304, y los assets se sirven antes que el rewrite `/*`. Con la regla temporal `/echo/*`: `If-None-Match` no llega al destino (el 304 lo resuelve el CDN de Render); `X-Forwarded-For` llega con la IP del cliente a la izquierda, pero un cliente puede anteponer otra; `True-Client-Ip` y `Cf-Connecting-Ip` llevan la IP real y Cloudflare sobrescribe los valores que manda el cliente. Arranque del API en Render: unos 2 min desde "Starting service" hasta el puerto (JVM ~65 s, contexto ~35 s).
- 2026-10-28: T5 cambia a `True-Client-Ip` (opción A del usuario; ver changelog de la spec).
- 2026-10-28: T2, Assumption 3 comprobado: el static site tiene Deploy Hook. Assumption 2 falla en la primera prueba: tras 18 min dormido, la primera respuesta tarda unos 7 min porque el API arranca dos veces. El primer intento muere sin log tras el contexto de Spring (14:25:31); el segundo arranca a las 14:29:11 y da "Started in 63.9 seconds". Sin "Out of memory" en los logs (falta mirar Events). Causas posibles: memoria (heap al 75 % de 512 MB) o el health check, que reinicia el API antes de que abra el puerto. Arreglo en `render.yaml` sin tocar la imagen: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60 -XX:TieredStopAtLevel=1 -Xss512k`. Pendiente: Manual Sync y deploy, línea "Started" nueva y repetir el arranque en frío.
