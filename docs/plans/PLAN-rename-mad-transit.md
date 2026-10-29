# Plan: renombrar el producto a Mad Transit

- Created: 2026-10-29
- Status: **approved** (2026-10-29)
- Spec: [SPEC-rename-mad-transit.md](../specs/SPEC-rename-mad-transit.md)

## Overview

Cambiar el nombre del producto a Mad Transit en el repo de GitHub, el código y la configuración del API, la imagen Docker, los servicios de Render, la web, la app de MobilityLabs y la documentación, sin dejar la web caída y sin perder el despliegue automático. El orden lo marca la spec (Migración sin corte).

## Decisiones de implementación

- **Cuatro PRs**, porque un PR que publica solo puede tocar `api/` o `web/` (`release-scope`):
  - `feat/api-mad-transit` → `feat(api)!: rename the api to mad transit` (T3, T4). Release api 0.7.0.
  - `ci/render-mad-transit` → `ci: deploy mad transit services on render` (T5). No publica.
  - `feat/web-mad-transit` → `feat(web): rename the web to mad transit` (T6). Release web 0.4.0.
  - `docs/mad-transit` → `docs: rename the product to mad transit` (T8).
- **Un commit por tarea.** Cuerpos de squash en prosa, sin bloques de código; el del PR del API lleva `BREAKING CHANGE:` por los URNs.
- **El paquete Kotlin se mueve con `git mv`** en un commit propio (T3), sin otros cambios, para que la historia de cada archivo siga y la revisión sea fácil. T3 toca más de 5 archivos: es un cambio mecánico que se verifica con la suite completa.
- **`DEPLOY_ENABLED=false` mientras dura la migración del API** (de T3 a T5): el hook del servicio viejo rechazaría la imagen `mad-transit-api` y `deploy-api` fallaría.
- **Lo que vive fuera de Git lo hace el usuario:** renombrar el repo, rellenar credenciales y hooks en Render, la regla de redirección del static site viejo, borrar el API viejo y la app de MobilityLabs. Borrar servicios o imágenes se pregunta antes.
- **Plan en el repo**, como en los otros módulos; no se usa `tasks/`.

## Grafo de dependencias

```
T1 renombrar el repo en GitHub (manual)
 └── T2 DEPLOY_ENABLED=false
       └── T3 mover el paquete Kotlin (git mv)
             └── T4 nombres, configuración, URNs e imagen → release api 0.7.0 (ghcr mad-transit-api)
                   └── T5 render.yaml con servicios mad-transit-* → sync crea los servicios, hooks nuevos
                         └── T6 web: título y URL del API nuevo → DEPLOY_ENABLED=true → release web 0.4.0
                               └── T7 redirección del static site viejo, borrar el API viejo, MobilityLabs
                                     └── T8 documentación, comprobación final y cierre
```

Todo es secuencial: cada paso necesita lo que publica el anterior.

## Task List

### Fase 1: repo y API (PR `feat(api)!`)

- [x] **T1: Renombrar el repo** (XS, ~10 min, **manual, usuario**)
  - Descripción: GitHub, Settings, Rename a `mad-transit`. Después, `git remote set-url origin https://github.com/jorgetroya80/mad-transit.git`.
  - Aceptación:
    - `gh repo view jorgetroya80/mad-transit` responde; la URL antigua redirige.
    - En Render, Manual Sync del Blueprint sin errores y un Manual Deploy del static site viejo termina bien (Assumption 3).
  - Verificación: `git remote -v`; `git fetch`; `gh variable list`; `gh secret list`.
  - Archivos: ninguno.
  - Dependencias: ninguna.

- [x] **T2: Parar el despliegue automático** (XS, ~2 min)
  - Descripción: `gh variable set DEPLOY_ENABLED --body false`.
  - Aceptación: `gh variable list` muestra `DEPLOY_ENABLED false`.
  - Archivos: ninguno.
  - Dependencias: T1.

- [x] **T3: Mover el paquete Kotlin** (M, ~30 min)
  - Descripción: `git mv` de `io/github/jorgetroya80/madmobility` a `.../madtransit` en `main`, `test` y `smokeTest`; `package` e `import` al paquete nuevo; ruta de JaCoCo en `build.gradle.kts`; clase `MadMobilityApplication` a `MadTransitApplication` (con su archivo); referencias de clase en `application.yaml` (`retry-exceptions`). Ningún otro cambio.
  - Aceptación:
    - `./gradlew build` en verde con la misma cobertura (JaCoCo ≥ 80 % por módulo).
    - `git log --follow` sigue la historia de un archivo movido.
    - No queda `madmobility` en `api/src` ni en `build.gradle.kts`.
  - Verificación: `./api/gradlew -p api build`; `git grep -n madmobility -- api`; `git log --follow --oneline api/src/main/kotlin/io/github/jorgetroya80/madtransit/MadTransitApplication.kt | tail -1`.
  - Archivos: todo `api/src/**/madmobility/**` (movido), `api/build.gradle.kts`, `api/src/main/resources/application.yaml`.
  - Dependencias: T2.

- [x] **T4: Nombres, configuración, URNs e imagen** (M, ~45 min)
  - Descripción: `rootProject.name = "mad-transit-api"`; en `application.yaml`, `spring.application.name`, título OpenAPI "Mad Transit API" y prefijo `mad-transit:`; `@ConfigurationProperties("mad-transit.*")` y los tests que fijan propiedades; URNs `urn:mad-transit:problem:*` y sus tests; comentarios y nombre del jar en `Dockerfile`; imagen `ghcr.io/jorgetroya80/mad-transit-api` en `release.yml`; URL del repo en `api/client/package.json` y `api/client/README.md`; `api/README.md`. PR `feat(api)!` con `BREAKING CHANGE:` en el cuerpo, en prosa.
  - Aceptación:
    - `git grep -n -i -E "mad[-_ ]?mobility|madmobility" -- api .github` solo encuentra `api/CHANGELOG.md`.
    - `./gradlew build` y `dockerImageTest` en verde.
    - Tras la release 0.7.0: `ghcr.io/jorgetroya80/mad-transit-api:0.7.0` existe y es pública (Assumption 4); `deploy-api` aparece omitido.
  - Verificación: `./api/gradlew -p api build`; `./api/gradlew -p api dockerImageTest`; `actionlint`; `gh api /users/jorgetroya80/packages/container/mad-transit-api --jq .visibility`.
  - Archivos: `api/settings.gradle.kts`, `api/src/main/resources/application.yaml`, las clases con `@ConfigurationProperties` y `Problem`, sus tests, `api/Dockerfile`, `.github/workflows/release.yml`, `api/client/package.json`, `api/client/README.md`, `api/README.md`.
  - Dependencias: T3.

### Checkpoint: API

- [x] api 0.7.0 publicada como `mad-transit-api`; `DEPLOY_ENABLED` sigue en `false`; el API viejo sigue sirviendo a la web.
- [x] Revisión del usuario.

### Fase 2: servicios nuevos en Render (PR `ci`)

- [ ] **T5: Servicios `mad-transit-*`** (S, ~45 min, **parte manual en Render**)
  - Descripción: `render.yaml` con `mad-transit-api` (imagen `mad-transit-api:latest`, `CORS_ALLOWED_ORIGINS=https://mad-transit-web.onrender.com`) y `mad-transit-web`; URL del repo en `scripts/render-web-build.sh`; `"name": "mad-transit"` en el `package.json` raíz. Tras el merge, el usuario hace Manual Sync: se crean los servicios nuevos (Assumption 2), rellena `EMT_CLIENT_ID` y `EMT_PASS_KEY`, y guarda los hooks nuevos con `gh secret set RENDER_DEPLOY_HOOK_API` y `RENDER_DEPLOY_HOOK_WEB`.
  - Aceptación:
    - `https://mad-transit-api.onrender.com/info` devuelve `mad-transit-api` 0.7.0; un `GET` con `Origin: https://mad-transit-web.onrender.com` recibe `Access-Control-Allow-Origin`.
    - Los servicios viejos siguen funcionando (la web vieja sigue cargando estaciones).
    - Si los subdominios nuevos no son exactamente `mad-transit-*.onrender.com`, se para y se corrigen las URLs antes de T6.
  - Verificación: validación del esquema de `render.yaml`; `curl` de arriba; `gh secret list`.
  - Archivos: `render.yaml`, `scripts/render-web-build.sh`, `package.json`.
  - Dependencias: checkpoint del API.

### Fase 3: web (PR `feat(web)`)

- [ ] **T6: La web pasa a Mad Transit** (S, ~30 min)
  - Descripción: título "Mad Transit" en `web/index.html` y `MapPage.tsx`, y en `MapPage.test.tsx` y `AppLayout.test.tsx` (primero los tests: RED); `web/.env.production` con `https://mad-transit-api.onrender.com`; `web/README.md`. Antes de mergear el PR de release, `gh variable set DEPLOY_ENABLED --body true`. La release web 0.4.0 se despliega en `mad-transit-web`.
  - Aceptación:
    - SC1: https://mad-transit-web.onrender.com muestra "Mad Transit" y carga estaciones sin errores de CORS.
    - SC3: `deploy-web` en verde sobre el servicio nuevo.
  - Verificación: `pnpm --filter web run lint`; `typecheck`; `test`; `build`; `grep -l mad-transit-api.onrender.com web/dist/assets/*.js`; navegador a 360 y 1280 px.
  - Archivos: `web/index.html`, `web/src/pages/map/ui/MapPage.tsx`, `web/src/pages/map/ui/MapPage.test.tsx`, `web/src/widgets/app-layout/ui/AppLayout.test.tsx`, `web/.env.production`, `web/README.md`.
  - Dependencias: T5.

### Checkpoint: producto nuevo en marcha

- [ ] La web y el API nuevos funcionan y se despliegan solos.
- [ ] Revisión del usuario en móvil y escritorio.

### Fase 4: cierre

- [ ] **T7: Servicios viejos y MobilityLabs** (S, ~20 min, **manual, usuario**)
  - Descripción: en `mad-mobility-web`, regla Redirect (301) `/*` → `https://mad-transit-web.onrender.com/*`; borrar el servicio `mad-mobility-api` (se confirma antes); en MobilityLabs, nombre "Mad Transit" y URL de la web nueva. La imagen vieja de ghcr.io se borra solo si el usuario lo pide.
  - Aceptación:
    - `curl -sI https://mad-mobility-web.onrender.com/?station=25A` devuelve 301 a `https://mad-transit-web.onrender.com/?station=25A`.
  - Verificación: `curl` de arriba.
  - Archivos: ninguno.
  - Dependencias: checkpoint del producto nuevo.

- [ ] **T8: Documentación, comprobación final y cierre** (M, ~45 min)
  - Descripción: nombre nuevo, URLs y badges en `README.md`; `CAPABILITY-MAP.md`, `docs/ideas/bicimad-now.md` y las specs y planes vigentes (no sus entradas de changelog cerradas); memoria del proyecto. Recorrer SC1-SC6; spec y plan a **implemented**. PR `docs`.
  - Aceptación:
    - SC4: el `git grep` de la spec solo encuentra `CHANGELOG.md`, entradas de changelog cerradas y la columna "Antes" de la spec.
    - SC1-SC6 anotados en el changelog del plan.
  - Verificación: `git grep` de la spec; `gh pr checks`.
  - Archivos: `README.md`, `docs/**`.
  - Dependencias: T7.

### Checkpoint final

- [ ] SC1-SC6 de la spec comprobados.
- [ ] Spec y plan pasan a **implemented**.

## Riesgos

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Renombrar el repo rompe el Blueprint o el build del static site | Alto | Se comprueba en T1, antes de tocar código; si falla, se recrea el Blueprint desde `mad-transit` |
| El sync con nombres nuevos borra o renombra los servicios viejos en vez de crear otros | Alto | Assumption 2; si el sync muestra borrados, no se aplica y se crean los servicios nuevos con un Blueprint nuevo |
| Los subdominios nuevos no quedan como `mad-transit-*` (nombre ocupado) | Medio | Se comprueba en T5 antes de compilar la web con la URL |
| `deploy-api` falla con la imagen nueva contra el servicio viejo | Medio | `DEPLOY_ENABLED=false` de T2 a T6 |
| La imagen nueva sale privada en ghcr.io y Render no puede bajarla | Medio | Se comprueba tras la release de T4 y se cambia la visibilidad |
| Enlaces compartidos a la web vieja | Bajo | Redirección 301 en T7 |

## Paralelización

Ninguna: un solo desarrollador y cada tarea depende de lo que publica la anterior.

## Changelog

- 2026-10-29: creado a partir de la spec aprobada.
- 2026-10-29: aprobado por el usuario.
- 2026-10-29: T1 hecha. Repo renombrado a `jorgetroya80/mad-transit` y remote local actualizado. La URL antigua redirige (301) y los assets de las releases siguen bajando por ella. Variables y secretos intactos. En Render, Manual Sync del Blueprint sin errores y Manual Deploy de `mad-mobility-web` en live (Assumption 3 comprobado).
- 2026-10-29: T2 hecha. `DEPLOY_ENABLED=false` hasta T6.
- 2026-10-29: T3 hecha (commit `refactor(api): move the kotlin package to madtransit`). `git mv` en `main`, `test` y `smokeTest`, más `MadTransitApplication` y su test. `./gradlew build`, `compileSmokeTestKotlin` y `jacocoTestCoverageVerification` en verde; `git log --follow` llega hasta el primer commit del API.
- 2026-10-29: T4 hecha. Primero los tests al prefijo `mad-transit.*` y a los URNs nuevos (RED: 21 de 188 fallan), luego el código (GREEN). `./gradlew build`, `dockerImageTest` (jar `mad-transit-api-*.jar`) y `actionlint` en verde; no queda el nombre viejo en `api/` ni en `.github/` salvo `api/CHANGELOG.md`. Pendiente: release 0.7.0 e imagen pública en ghcr.io.
- 2026-10-29: release api 0.7.0 (PR #67): `publish-api` y `publish-api-client` en verde, `deploy-api` omitido. `ghcr.io/jorgetroya80/mad-transit-api` es pública, con las etiquetas `0.7.0` y `latest` (Assumption 4). Los servicios viejos siguen sirviendo la web.
- 2026-10-29: T5, parte de código: `render.yaml` con `mad-transit-api` (imagen `mad-transit-api:latest`, CORS para `https://mad-transit-web.onrender.com`) y `mad-transit-web`; URL nueva del repo en `scripts/render-web-build.sh` (descarga `web-v0.3.2` desde `jorgetroya80/mad-transit`); `"name": "mad-transit"` en el `package.json` raíz. Esquema de Render, `shellcheck` y `pnpm install --frozen-lockfile` sin errores. Falta el sync en Render.
