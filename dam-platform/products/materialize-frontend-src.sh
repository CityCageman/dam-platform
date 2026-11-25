#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
for spec in asset:DAM platform:DPM access:DAP governance:DGP processing:DPP service:DSP analytics:DAN app:DAPP; do
  folder=${spec%%:*}; code=${spec##*:}
  dst="$ROOT/products/$folder/frontend-src"
  rm -rf "$dst"; mkdir -p "$dst"
  (cd "$ROOT/codebase/frontend" && tar -cf - \
    --exclude=node_modules --exclude=dist --exclude=dist-products \
    --exclude=.env --exclude=.env.* .) | (cd "$dst" && tar -xf -)
  printf 'VITE_PRODUCT_CODE=%s\n' "$code" > "$dst/.env.production"
  echo "$folder -> $dst ($code)"
done
