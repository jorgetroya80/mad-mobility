#!/usr/bin/env bash
# Pre-push: type-check only the parts of the monorepo changed since the upstream.
# Skips parts whose project does not exist yet.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

if base=$(git rev-parse --verify -q '@{push}' 2>/dev/null); then
  range="$base...HEAD"
elif base=$(git merge-base HEAD origin/main 2>/dev/null); then
  range="$base...HEAD"
else
  # First push: no upstream yet, compare against the empty tree.
  range="$(git hash-object -t tree /dev/null) HEAD"
fi

# shellcheck disable=SC2086 # range is intentionally split into two args for the first push
changed=$(git diff --name-only $range)

check_web=false
check_api=false
check_client=false
grep -q '^web/' <<<"$changed" && check_web=true
grep -q '^api/' <<<"$changed" && check_api=true
grep -q '^api/client/' <<<"$changed" && check_client=true

if $check_web; then
  if [ -f web/tsconfig.json ]; then
    echo "pre-push: type-checking web"
    pnpm --filter web run typecheck
  else
    echo "pre-push: web changed but web/tsconfig.json not found, skipping"
  fi
fi

if $check_api; then
  if [ -f api/build.gradle.kts ]; then
    echo "pre-push: type-checking api"
    ./api/gradlew -p api compileKotlin compileTestKotlin
  else
    echo "pre-push: api changed but api/build.gradle.kts not found, skipping"
  fi
fi

if $check_client; then
  echo "pre-push: type-checking api/client against the generated OpenAPI"
  ./api/gradlew -p api generateOpenApi
  pnpm --filter @jorgetroya80/bicimad-client run generate
  pnpm --filter @jorgetroya80/bicimad-client exec tsc --noEmit
fi

if ! $check_web && ! $check_api; then
  echo "pre-push: no api/ or web/ changes, nothing to type-check"
fi
