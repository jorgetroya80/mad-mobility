# web

React frontend (Vite + TypeScript) for Mad Mobility.

## What it does

Shows BiciMAD stations on a map of Madrid, with how many bikes and free docks each one has, refreshed every minute. It gets the data from the [API](../api/README.md) only through the typed client `@jorgetroya80/bicimad-client`.

- Lists the stations within 500 m of you (or of Puerta del Sol if location is off), ordered by the API: those with bikes (or free docks) first.
- Filter by bikes or free docks; availability shows as color and number on the map and in the list.
- Station detail with bikes, free docks, status and last update; shareable links such as `/?station=538&need=docks`, and the Back button closes the detail.
- Search by station number; favorites stored in the browser (`localStorage`).
- Responsive: bottom sheet on mobile, side panel from tablet up.

Live at [mad-mobility-web.onrender.com](https://mad-mobility-web.onrender.com). Each release attaches `web-dist.tar.gz` and deploys it to Render, which serves it as is. In that build the browser calls the API directly at `https://mad-mobility-api.onrender.com`, which allows the web's origin through CORS; see [Deployment](../README.md#deployment).

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
pnpm --filter web test       # Vitest with coverage (>= 80% lines per layer)
pnpm --filter web build      # type-check and build to web/dist
pnpm --filter web preview    # serve web/dist
```

`VITE_API_BASE_URL` sets the API base at build time: a path or an `http(s)` URL, `/api` by default. [`.env.production`](.env.production) sets it to the deployed API for `build`, so `preview` calls that API too (CORS only allows the deployed web, so the requests fail from localhost); `dev` and `dev:mock` keep `/api`.

## Architecture

[Feature-Sliced Design](https://feature-sliced.design) with the layers `app`, `pages`, `widgets`, `features`, `entities` and `shared`. Routing and URL state (`?station`, `?need`) use TanStack Router. Each slice exposes its public API through its `index.ts`; import from there, not from internal files.

Code is formatted with oxfmt (on save and in pre-commit) and linted with oxlint.

Data powered by [EMT de Madrid](https://www.emtmadrid.es).
