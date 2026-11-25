#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/codebase/frontend"
mkdir -p dist-products
for code in DAM DPM DAP DGP DPP DSP DAN DAPP; do
  echo "=== frontend $code"
  VITE_PRODUCT_CODE="$code" npm run build
  rm -rf "dist-products/$code"
  cp -a dist "dist-products/$code"
done
echo "frontend products: $ROOT/codebase/frontend/dist-products"
