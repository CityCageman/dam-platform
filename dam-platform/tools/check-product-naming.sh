#!/usr/bin/env bash
# 产品命名门禁：新产品包不得再使用 dam- 前缀
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAIL=0
for manifest in "$ROOT"/products/*/product.yaml; do
  [ -f "$manifest" ] || continue
  product="$(basename "$(dirname "$manifest")")"
  while IFS= read -r service; do
    case "$service" in
      dam-*) echo "[FAIL] $product: 服务名仍使用 dam- 前缀: $service"; FAIL=1 ;;
    esac
  done < <(awk '/^services:/{f=1;next}/^[a-zA-Z]/{f=0}f && /^[[:space:]]*-/{gsub(/^[[:space:]]*-[[:space:]]*/,"");print}' "$manifest")
  remote="$(awk '/^[[:space:]]*remote:/{print $2;exit}' "$manifest")"
  case "$remote" in
    dam-*) echo "[FAIL] $product: 前端 remote 仍使用 dam- 前缀: $remote"; FAIL=1 ;;
  esac
done
if [ "$FAIL" -eq 0 ]; then echo "[OK] 产品命名门禁通过"; fi
exit "$FAIL"
