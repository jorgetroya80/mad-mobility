# web/AGENTS.md

React, Vite and TypeScript on Node 24, with pnpm. The rules in the [root AGENTS.md](../AGENTS.md) apply too.

## Commands

```bash
pnpm --filter web dev:mock       # app with MSW in the browser, no API needed
pnpm --filter web lint           # oxlint
pnpm --filter web format         # oxfmt (writes)
pnpm --filter web typecheck      # tsc --noEmit
pnpm --filter web test           # Vitest, unit tests only
pnpm --filter web test:coverage  # Vitest with coverage (at least 80 % of lines per layer)
pnpm --filter web depcruise      # layer and boundary rules
pnpm --filter web build          # type-check and build to web/dist
```

Run a single file with `pnpm --filter web exec vitest run <path>`.

## Architecture

- Feature-Sliced Design with the layers `app`, `pages`, `widgets`, `features`, `entities` and `shared`. A layer only imports from the layers below it, and a slice never imports a sibling slice. `pnpm --filter web depcruise` checks it.
- Each slice exposes its public API through its `index.ts`. Import from there, never from internal files.
- `maplibre-gl` is only used inside `shared/ui/base-map`.
- The URL (`?station`, `?need`) is read and written only through TanStack Router, never through `window.location` or `history`.
- The API is called only through `@jorgetroya80/mad-transit-client`. Never hand-write API types or compute distances and ordering in the client: the API does it.
- `localStorage` only stores the favorites.

## Style and tests

- Match the surrounding code: its comment density, naming and idioms. Comments explain why, not what.
- No `oxlint-disable`, `@ts-ignore` or `@ts-expect-error`. Fix the code instead.
- Tests use Vitest, Testing Library and MSW. Query by role and accessible name, as a user would.
- Accessibility is part of done: real buttons and links, labels, keyboard focus, and color never alone.
- UI text is in Spanish; code and comments are in English.
