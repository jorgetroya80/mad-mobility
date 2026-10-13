# web

React frontend (Vite + TypeScript) for Mad Mobility.

## What it does

Shows BiciMAD stations on a map of Madrid, with how many bikes and free docks each one has, refreshed every minute. It gets the data from the [API](../api/README.md) only through the typed client `@jorgetroya80/bicimad-client`.

Today it shows the map of Madrid in a responsive layout (bottom sheet on mobile, side panel from tablet up). Stations come in the next module, `web-bicimad`.

## Requirements

- Node 24 and pnpm (via `corepack enable`).
- Access to GitHub Packages, where the API client is published. GitHub asks for a token even for public packages, and pnpm 12 does not expand env vars in a repository `.npmrc`, so the repo's `.npmrc` only routes the `@jorgetroya80` scope and the token lives in your user config:

  ```bash
  # ~/.npmrc
  //npm.pkg.github.com/:_authToken=${NODE_AUTH_TOKEN}

  # shell profile: classic PAT with read:packages
  export NODE_AUTH_TOKEN=<token>
  ```

  Without it, `pnpm install` fails with `401` for the whole workspace.

## Commands

Run from the repo root after `pnpm install`:

```bash
pnpm --filter web dev        # Vite on :5173, proxies /api to localhost:8080 (needs the API running)
pnpm --filter web dev:mock   # MSW in the browser, no backend needed
pnpm --filter web lint       # oxlint
pnpm --filter web format     # oxfmt (writes)
pnpm --filter web typecheck  # tsc --noEmit
pnpm --filter web test       # Vitest with coverage (>= 80% lines in shared/ and widgets/)
pnpm --filter web build      # type-check and build to web/dist
pnpm --filter web preview    # serve web/dist
```

`VITE_API_BASE_URL` sets the API base at build time: a path or an `http(s)` URL, `/api` by default.

## Architecture

[Feature-Sliced Design](https://feature-sliced.design) with the layers `app`, `pages`, `widgets` and `shared`. Each slice exposes its public API through its `index.ts`; import from there, not from internal files.

Code is formatted with oxfmt (on save and in pre-commit) and linted with oxlint.

Data powered by [EMT de Madrid](https://www.emtmadrid.es).
