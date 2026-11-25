#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CODE="${1:?usage: build-product.sh <DAM|DPM|DAP|DGP|DPP|DSP|DAN|DAPP> [version]}"
VERSION="${2:-$(python3 - "$ROOT" "$CODE" <<'PY'
import sys,yaml,glob,os
root,code=sys.argv[1],sys.argv[2]
for p in glob.glob(root+'/products/*/product.yaml'):
 d=yaml.safe_load(open(p))
 if d.get('code')==code: print(d.get('version','1.0.0')); break
PY
)}"
PRODUCT_DIR="$(python3 - "$ROOT" "$CODE" <<'PY'
import sys,yaml,glob,os
root,code=sys.argv[1],sys.argv[2]
for p in glob.glob(root+'/products/*/product.yaml'):
 d=yaml.safe_load(open(p))
 if d.get('code')==code:
  print(os.path.dirname(p)); break
else: raise SystemExit('unknown product '+code)
PY
)"
SERVICES="$(python3 - "$PRODUCT_DIR" <<'PY'
import sys,yaml
print(' '.join(yaml.safe_load(open(sys.argv[1]+'/product.yaml'))['services']))
PY
)"
if [ "${STANDALONE:-0}" = "1" ]; then
  case " $SERVICES " in *" platform-gateway "*) ;; *) SERVICES="$SERVICES platform-gateway platform-auth" ;; esac
fi
MODULES="$(python3 - "$ROOT" "$SERVICES" <<'PY'
import sys,json
root,services=sys.argv[1],sys.argv[2].split()
cat=json.load(open(root+'/products/service-catalog.json'))
print(','.join(cat[s]['module'] for s in services))
PY
)"
echo "=== build $CODE $VERSION"
echo "services: $SERVICES"
cd "$ROOT/codebase/backend"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || true)}"
MAVEN_OPTS=()
[ "${MAVEN_OFFLINE:-1}" = "1" ] && MAVEN_OPTS+=(-o)
mvn -B "${MAVEN_OPTS[@]}" -DskipTests -Dmaven.repo.local="${MAVEN_REPO:-$ROOT/../../.dsh-m2repo}" -pl "$MODULES" -am package

RELEASE="$PRODUCT_DIR/dist/$VERSION"
rm -rf "$RELEASE"
mkdir -p "$RELEASE"/{jars,bin,db,systemd,frontend}
for svc in $SERVICES; do
  jar="$ROOT/codebase/backend/$(python3 - "$ROOT" "$svc" <<'PY'
import sys,json
print(json.load(open(sys.argv[1]+'/products/service-catalog.json'))[sys.argv[2]]['module'])
PY
)/target/${svc}-${VERSION}-exec.jar"
  [ -f "$jar" ] || { echo "missing jar: $jar" >&2; exit 1; }
  cp "$jar" "$RELEASE/jars/"
done
cp "$PRODUCT_DIR/product.yaml" "$RELEASE/"
[ -f "$PRODUCT_DIR/db/migrate.sql" ] && cp "$PRODUCT_DIR/db/migrate.sql" "$RELEASE/db/"
if [ "${STANDALONE:-0}" = "1" ] && [ -f "$ROOT/products/base/db/migrate.sql" ]; then cp "$ROOT/products/base/db/migrate.sql" "$RELEASE/db/base-migrate.sql"; fi
mkdir -p "$RELEASE/deploy"
[ -f "$PRODUCT_DIR/deploy/gateway-routes.yaml" ] && cp "$PRODUCT_DIR/deploy/gateway-routes.yaml" "$RELEASE/deploy/"
PRODUCT_FOLDER="$(basename "$PRODUCT_DIR")"
cat > "$RELEASE/bin/start.sh" <<EOS
#!/usr/bin/env bash
set -euo pipefail
BASE="\$(cd "\$(dirname "\$0")/.." && pwd)"
ENV_FILE="\${ENV_FILE:-/etc/dam-products/${PRODUCT_FOLDER}.env}"
[ -f "\$ENV_FILE" ] && set -a && . "\$ENV_FILE" && set +a
mkdir -p "\$BASE/logs"
for j in "\$BASE"/jars/*-exec.jar; do
  n="\$(basename "\$j" -1.0.0-exec.jar)"
  nohup java \${JAVA_OPTS:--Xmx512m -Xms256m} -jar "\$j" --spring.profiles.active=prod >>"\$BASE/logs/\$n.log" 2>&1 &
  echo \$! > "\$BASE/logs/\$n.pid"
done
EOS
cat > "$RELEASE/bin/stop.sh" <<'EOS'
#!/usr/bin/env bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
for p in "$BASE"/logs/*.pid; do [ -f "$p" ] || continue; kill "$(cat "$p")" 2>/dev/null || true; rm -f "$p"; done
EOS
cat > "$RELEASE/bin/install-product.sh" <<EOS
#!/usr/bin/env bash
set -euo pipefail
PREFIX="\${1:-/opt/dam-products/${PRODUCT_FOLDER}}"
CODE="${CODE}"
VERSION="${VERSION}"
BASE="\$(cd "\$(dirname "\$0")/.." && pwd)"
mkdir -p "\$PREFIX/releases/\$VERSION"
cp -a "\$BASE"/. "\$PREFIX/releases/\$VERSION/"
ln -sfn "\$PREFIX/releases/\$VERSION" "\$PREFIX/current"
ENV_DIR="\${ENV_DIR:-/etc/dam-products}"
if ! mkdir -p "\$ENV_DIR" 2>/dev/null; then ENV_DIR="\$PREFIX/etc"; mkdir -p "\$ENV_DIR"; fi
[ -f "\$ENV_DIR/${PRODUCT_FOLDER}.env" ] || cat > "\$ENV_DIR/${PRODUCT_FOLDER}.env" <<'ENV'
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/damplatform
SPRING_DATASOURCE_USERNAME=dam
SPRING_DATASOURCE_PASSWORD=ChangeMe_2026
SA_TOKEN_JWT_SECRET_KEY=ChangeMe_JWT_Secret_At_Least_32_Chars
ENV
echo "installed \$CODE \$VERSION -> \$PREFIX/current"
echo "database: source \$ENV_DIR/${PRODUCT_FOLDER}.env && psql <database-url> -f \$PREFIX/current/db/migrate.sql"
echo "start: \$PREFIX/current/bin/start.sh"
EOS
cat > "$RELEASE/bin/uninstall.sh" <<EOS
#!/usr/bin/env bash
set -euo pipefail
PREFIX="\${1:-/opt/dam-products/${PRODUCT_FOLDER}}"
[ -f "\$PREFIX/current/bin/stop.sh" ] && "\$PREFIX/current/bin/stop.sh" || true
rm -rf "\$PREFIX"
echo "removed \$PREFIX (database is not dropped)"
EOS
chmod +x "$RELEASE"/bin/*.sh
cat > "$RELEASE/README.md" <<EOM
# $CODE $VERSION

独立产品发布目录。

服务：
\`\`\`text
$SERVICES
\`\`\`

安装：
\`\`\`bash
sudo ./bin/install-product.sh --prefix /opt/dam-products/$(basename "$PRODUCT_DIR")
\`\`\`

数据库迁移：
\`\`\`bash
psql "\$DB_URL" -f db/migrate.sql
\`\`\`
EOM
echo "release: $RELEASE"
tar -czf "$PRODUCT_DIR/dist/${CODE}-${VERSION}.tar.gz" -C "$PRODUCT_DIR/dist" "$VERSION"
echo "archive: $PRODUCT_DIR/dist/${CODE}-${VERSION}.tar.gz"
find "$RELEASE" -maxdepth 2 -type f | sort
