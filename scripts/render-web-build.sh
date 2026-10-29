#!/usr/bin/env bash
# Render static site build: deploys the web-dist.tar.gz of the current web release instead of
# rebuilding it, so Render serves exactly what publish-web attached (and needs no npm token).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

version=$(sed -n 's/.*"web": *"\([^"]*\)".*/\1/p' .release-please-manifest.json)
if [[ -z "$version" ]]; then
  echo "No web version in .release-please-manifest.json" >&2
  exit 1
fi

url="https://github.com/jorgetroya80/mad-transit/releases/download/web-v${version}/web-dist.tar.gz"
echo "Deploying web ${version} from ${url}"

rm -rf dist
mkdir dist
curl --fail --silent --show-error --location --retry 3 "$url" | tar -xz -C dist
