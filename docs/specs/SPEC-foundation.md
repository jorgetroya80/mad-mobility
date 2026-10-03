# Spec: foundation

- Created: 2026-10-03
- Status: **approved** (2026-10-03)
- Plan: [PLAN-foundation.md](../plans/PLAN-foundation.md)

Módulo `foundation` del [Capability Map](CAPABILITY-MAP.md). Repositorio: [jorgetroya80/mad-mobility](https://github.com/jorgetroya80/mad-mobility) (público, vacío, rama por defecto `main`). Define el repositorio, las validaciones locales (Husky), la integración continua, el versionado y la publicación de artefactos **antes de escribir código de aplicación**.

## Objective

Que cada cambio que llegue a `main` esté validado automáticamente y que cada release se genere sin pasos manuales, desde el primer commit.

- Como desarrollador, quiero que un commit con mensaje inválido se rechace en local, para que el historial sea apto para release-please.
- Como desarrollador, quiero que `git push` compruebe los tipos solo de la parte que cambió, para detectar errores sin esperar a CI.
- Como desarrollador, quiero que CI ejecute únicamente los jobs afectados por el cambio (`api/`, `web/`, `openapi/`), para tener feedback rápido.
- Como mantenedor, quiero que release-please versione `api` y `web` de forma independiente, con CHANGELOG y etiqueta propios.
- Como mantenedor, quiero que una release publique artefactos desplegables (imagen Docker de la API, build estático del frontend) sin depender todavía de una plataforma de despliegue.

**Fuera de alcance:** despliegue a una plataforma concreta (módulo `deploy`), código de aplicación, Dependabot para `api/`.

## Tech Stack

| Herramienta    | Versión                                               | Uso                                                                          |
| -------------- | ----------------------------------------------------- | ---------------------------------------------------------------------------- |
| Java           | 24 (Eclipse Temurin)                                  | Toolchain de Gradle y CI. Nota: no es LTS; su última actualización es 24.0.2 |
| Spring Boot    | 4.0.5 (fija)                                          | Se usará desde `api-core`; aquí solo se fija en `gradle.properties`          |
| Gradle         | wrapper (última 9.x compatible con Java 24)           | Build de `api/`                                                              |
| Node.js        | 24 LTS                                                | Tooling raíz y `web/`                                                        |
| pnpm           | 12.x (`packageManager: pnpm@12.8.1`)                  | Workspace raíz y `web/`                                                      |
| Husky          | 9.x                                                   | Git hooks                                                                    |
| commitlint     | `@commitlint/cli` + `@commitlint/config-conventional` | Validar mensajes de commit                                                   |
| lint-staged    | última                                                | Formato y lint de archivos en stage                                          |
| release-please | `googleapis/release-please-action@v5`, modo manifest  | Versionado y releases                                                        |
| Dependabot     | —                                                     | Actualización de dependencias npm (`web/` y raíz)                            |
| actionlint     | `rhysd/actionlint`                                    | Lint de workflows                                                            |

## Commands

```bash
# Instalación (activa Husky vía script "prepare")
corepack enable
pnpm install

# Validaciones manuales equivalentes a los hooks
pnpm exec commitlint --from origin/main         # mensajes de commit de la rama
pnpm exec lint-staged                             # lo que hace pre-commit
pnpm run typecheck:changed                        # lo que hace pre-push

# Por parte (cuando existan los proyectos)
pnpm --filter web run typecheck                   # tsc --noEmit
./api/gradlew -p api compileKotlin compileTestKotlin
./api/gradlew -p api spotlessApply

# Workflows
actionlint
```

## Project Structure

```
mad-mobility/
  .github/
    workflows/
      ci.yml                 PR y push a main: jobs filtrados por ruta + job agregador ci-ok
      pr-title.yml           valida el título del PR como Conventional Commit
      release.yml            release-please + publicación de artefactos + deploy (placeholder)
    dependabot.yml           npm (raíz y web/), semanal, agrupado
  .husky/
    commit-msg               commitlint
    pre-commit               lint-staged
    pre-push                 scripts/typecheck-changed.sh
  scripts/
    typecheck-changed.sh     detecta partes cambiadas y comprueba tipos solo de esas
  api/
    gradle.properties        placeholder: version=0.0.0 (anotado para release-please)
  web/
    package.json             placeholder: name, version 0.0.0, private
  openapi/                   bicimad.json generado por springdoc (lo crea api-bicimad; nunca se edita a mano)
  docs/
  package.json               raíz privada: scripts, devDependencies de tooling, packageManager
  pnpm-workspace.yaml        packages: [web]
  commitlint.config.mjs
  lint-staged.config.mjs
  release-please-config.json
  .release-please-manifest.json
  .nvmrc                     24
  .gitignore                 incluye .env.local
```

`api/gradle.properties` y `web/package.json` son placeholders mínimos para que release-please y Dependabot funcionen desde el primer día. Los proyectos reales se crean en `api-core` y `web-shell`.

## Conventions

### Commits (Conventional Commits)

Formato: `<type>(<scope>)?: <descripción en imperativo>`

- Tipos: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `revert`.
- Scopes permitidos (opcionales): `api`, `web`, `contract`, `ci`, `deps`, `docs`, `repo`.
- `feat` sube minor, `fix`/`perf` suben patch, `!` o `BREAKING CHANGE:` sube major (minor mientras la versión sea 0.x).

```
feat(web): add station list bottom sheet
fix(api): retry EMT login on code 80
ci: add path filter for openapi changes
feat(contract)!: rename need=docks to need=slots
```

### Ramas y merges

- Trunk-based: `main` protegida, cambios solo vía Pull Request.
- Squash merge únicamente. El título del PR es el mensaje del commit en `main`, por eso se valida en `pr-title.yml`.
- Ramas: `<type>/<descripción-corta>`, por ejemplo `feat/station-list`.

### Hooks de Husky

| Hook         | Acción                             | Objetivo de tiempo |
| ------------ | ---------------------------------- | ------------------ |
| `commit-msg` | `pnpm exec commitlint --edit "$1"` | < 1 s              |
| `pre-commit` | `pnpm exec lint-staged`            | < 10 s             |
| `pre-push`   | `scripts/typecheck-changed.sh`     | < 60 s             |

**lint-staged (`pre-commit`):**

- `web/**/*.{ts,tsx}`: `eslint --fix` y `prettier --write`.
- `*.{json,md,yml,yaml,css}`: `prettier --write`.
- `api/**/*.{kt,kts}`: `./api/gradlew -p api spotlessApply` (una sola ejecución por commit).
- Cada regla se omite si su proyecto aún no existe.

**`scripts/typecheck-changed.sh` (`pre-push`):** solo comprobación de tipos, sin tests ni lint.

1. Calcula los archivos cambiados entre el upstream (`@{push}`, o `origin/main` si no existe) y `HEAD`.
2. Si cambió `web/**`: `pnpm --filter web run typecheck` (`tsc --noEmit`).
3. Si cambió `api/**`: `./api/gradlew -p api compileKotlin compileTestKotlin`.
4. Si cambió `openapi/**`: ejecuta ambas (el contrato generado afecta a las dos partes).
5. Si no cambió ninguna, o el proyecto aún no existe, termina con éxito sin hacer nada.

```bash
#!/usr/bin/env bash
set -euo pipefail
base=$(git rev-parse --verify -q '@{push}' 2>/dev/null || git merge-base HEAD origin/main)
changed=$(git diff --name-only "$base"...HEAD)
check_web=false; check_api=false
grep -q '^web/' <<<"$changed" && check_web=true
grep -q '^api/' <<<"$changed" && check_api=true
grep -q '^openapi/' <<<"$changed" && { check_web=true; check_api=true; }
if $check_web && [ -f web/tsconfig.json ]; then pnpm --filter web run typecheck; fi
if $check_api && [ -f api/build.gradle.kts ]; then ./api/gradlew -p api compileKotlin compileTestKotlin; fi
```

### CI (`ci.yml`)

- Disparadores: `pull_request` y `push` a `main`. `concurrency` por rama, cancelando ejecuciones anteriores.
- Permisos mínimos por defecto (`contents: read`).
- Job `changes`: `dorny/paths-filter` con filtros `api`, `web`, `contract`, `workflows`.
- Job `api` (si `api` o `contract`): `actions/setup-java` (Temurin 24), `gradle/actions/setup-gradle`, `./gradlew -p api build`. Se omite si `api/build.gradle.kts` no existe.
- Job `web` (si `web` o `contract`): `pnpm/action-setup`, `actions/setup-node` (24, caché pnpm), `pnpm install --frozen-lockfile`, `lint`, `typecheck`, `test`, `build`. Se omite si `web/tsconfig.json` no existe.
- Job `contract` (si `contract`): lint de `openapi/bicimad.json` (por ejemplo con Redocly CLI). Se omite si el archivo no existe.
- Comprobación de desajuste del contrato: el job `api` regenera el OpenAPI con springdoc y falla si difiere de `openapi/bicimad.json`. El paso concreto se define en `api-bicimad`; aquí solo se reserva.
- Job `tooling` (si cambian `package.json`, `pnpm-lock.yaml`, `pnpm-workspace.yaml`, `.nvmrc`, `*.config.mjs` o `.prettierrc.json`): `pnpm install --frozen-lockfile` con pnpm 12 (aplica sus políticas, como `minimumReleaseAge`) y `prettier --check .`.
- Job `workflows` (si `workflows`): `actionlint`.
- Job `ci-ok`: depende de todos, `if: always()`, falla si alguno falló o se canceló; los omitidos cuentan como éxito. **Es el único check requerido** en la protección de `main`.

### Release (`release.yml`)

- Disparador: `push` a `main`.
- Job `release-please`: `googleapis/release-please-action@v5` con `release-please-config.json` y `.release-please-manifest.json`. Expone `api--release_created`, `api--tag_name`, `web--release_created`, `web--tag_name`.
- Job `publish-api` (si `api--release_created` y existe `api/Dockerfile`): construye y publica `ghcr.io/jorgetroya80/mad-mobility-api:<versión>` y `:latest`. Permiso `packages: write`.
- Job `publish-web` (si `web--release_created`): `pnpm --filter web build` y adjunta `web-dist.tar.gz` a la release.
- Jobs `deploy-api` y `deploy-web`: placeholders que solo se ejecutan si la variable de repositorio `DEPLOY_ENABLED == 'true'`. Se completan en el módulo `deploy`.
- Publicación y despliegue van en el mismo workflow porque las releases creadas con `GITHUB_TOKEN` no disparan otros workflows.
- release-please usa el `GITHUB_TOKEN` automático (sin secretos), igual que en `artwork-search`. Como los eventos de ese token no inician workflows, el PR de release no ejecuta los checks requeridos por sí solo: **antes de mergearlo hay que cerrarlo y reabrirlo** (o editarlo). Por eso `ci.yml` escucha también el tipo `edited`.

**`release-please-config.json`:**

```json
{
  "$schema": "https://raw.githubusercontent.com/googleapis/release-please/main/schemas/config.json",
  "include-component-in-tag": true,
  "bump-minor-pre-major": true,
  "separate-pull-requests": false,
  "packages": {
    "api": {
      "release-type": "simple",
      "component": "api",
      "extra-files": [{ "type": "generic", "path": "gradle.properties" }]
    },
    "web": {
      "release-type": "node",
      "component": "web"
    }
  }
}
```

`.release-please-manifest.json`: `{ "api": "0.0.0", "web": "0.0.0" }`.
`api/gradle.properties`: `version=0.0.0 # x-release-please-version`.

**Dónde vive la versión:** el manifest de la raíz es el registro de release-please (última versión publicada de cada componente). Cada componente guarda además su versión en el archivo nativo de su herramienta de build, para que el build la use sin depender de release-please: `api/gradle.properties` (Gradle la lee como `project.version`, que llega al nombre del jar, a `/actuator/info` y a la etiqueta Docker) y `web/package.json`. release-please actualiza manifest y archivos nativos en el mismo PR de release; nunca se editan a mano.

Resultado: un único PR de release ("chore: release main") con las versiones de las partes que cambiaron; etiquetas `api-v0.1.0` y `web-v0.1.0`; un `CHANGELOG.md` en `api/` y otro en `web/`.

### Dependabot (`dependabot.yml`)

- Ecosistema `npm`, directorio `/` (workspace pnpm, cubre raíz y `web/`): semanal (lunes), un grupo para minor/patch y PRs separados para major, prefijo `chore(deps)`, `cooldown` de 10 días (pnpm 12 rechaza versiones publicadas hace menos de ~10 días y Dependabot ejecuta pnpm 11).
- Ecosistema `github-actions`, directorio `/`: semanal (lunes), un único grupo, prefijo `ci(deps)`. Mantiene actualizados los SHA fijados y sus comentarios de versión.
- Los prefijos pasan commitlint y `pr-title`, y no generan releases.
- `api/` (Gradle) sigue fuera de Dependabot.

### Configuración manual del repositorio en GitHub

- Branch protection en `main`: PR obligatorio, check requerido `ci-ok` y `pr-title`, solo squash merge, historial lineal, borrar ramas tras merge.
- Settings > Actions: permitir que GitHub Actions cree y apruebe pull requests (lo necesita release-please).
- Workflow permissions por defecto: solo lectura; cada job pide los permisos que necesita.

## Testing Strategy

Este módulo no tiene código de aplicación; se verifica con escenarios reproducibles:

- `actionlint` en local y en CI para todos los workflows.
- `bash -n scripts/typecheck-changed.sh` y `shellcheck` sobre el script.
- Escenarios manuales de los criterios de éxito (abajo), ejecutados una vez al terminar el módulo y documentados en el PR.

## Boundaries

- **Always:** Conventional Commits; permisos mínimos en cada workflow; todas las acciones fijadas por SHA de commit con la versión en un comentario (`uses: owner/action@<sha> # vX.Y.Z`) y las imágenes Docker por digest; hooks que no fallen si un proyecto aún no existe; `pnpm install --frozen-lockfile` en CI.
- **Ask first:** añadir checks requeridos nuevos; cambiar la estrategia de versionado o los componentes de release-please; añadir secretos al repositorio; ampliar Dependabot a Gradle o Actions; hooks que superen los objetivos de tiempo.
- **Never:** commitear `.env.local` o credenciales; usar `--no-verify` como práctica habitual; `pull_request_target` con checkout de código del PR; dar `write-all` a un workflow; desplegar desde un PR.

## Success Criteria

1. `git commit -m "update stuff"` se rechaza en local; `git commit -m "docs: add capability map"` se acepta.
2. Un commit con un `.md` mal formateado sale formateado por Prettier.
3. `git push` de una rama que solo cambia `web/` ejecuta solo `tsc --noEmit` de `web`; una que solo cambia `docs/` no ejecuta ninguna comprobación.
4. Un PR que solo cambia `docs/` pasa `ci-ok` sin ejecutar los jobs `api` ni `web`.
5. Un PR con título `Update stuff` falla `pr-title`; con `feat(web): add map` pasa.
6. Tras mergear un `feat(web): ...` en `main`, release-please abre o actualiza un PR que sube solo `web` (0.0.0 -> 0.1.0) y modifica `web/CHANGELOG.md`.
7. Al mergear el PR de release se crea la etiqueta `web-v0.1.0` y una GitHub Release; `publish-web` adjunta `web-dist.tar.gz` (cuando exista el proyecto `web`).
8. Los jobs `deploy-*` aparecen como omitidos mientras `DEPLOY_ENABLED` no sea `true`.
9. Dependabot abre como máximo un PR agrupado semanal de minor/patch con prefijo `chore(deps)`.
10. `actionlint` no reporta errores.

## Open Questions

- Ninguna.

## Changelog

- 2026-10-03: contrato código primero (springdoc). `openapi/` contiene un archivo generado; el job `contract` hace lint y la comprobación de desajuste se reserva para `api-bicimad`. Sin impacto en el plan.
- 2026-10-03: `release-please-action` pasa de `@v4` a `@v5` (última versión mayor; solo cambia el runtime a Node 24).
- 2026-10-03: release-please usa `GITHUB_TOKEN` (sin PAT); el PR de release se cierra y reabre a mano para disparar los checks. Todas las acciones se fijan por SHA. Dependabot no cubre GitHub Actions, así que actualizar esos SHA es manual.
- 2026-10-03: Dependabot cubre también `github-actions` (prefijo `ci(deps)`) para actualizar los SHA fijados.
- 2026-10-03: Dependabot npm con `cooldown` de 10 días; nuevo job `tooling` en CI que valida la raíz del monorepo con `pnpm install --frozen-lockfile` y Prettier (el PR #12 de Dependabot pasó CI con un lockfile que pnpm 12 rechaza).
