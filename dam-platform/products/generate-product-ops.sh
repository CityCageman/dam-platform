#!/usr/bin/env bash
set -euo pipefail
RELEASE="$1"; CODE="$2"; FOLDER="$3"; VERSION="$4"; SERVICES="$5"; PORTS="$6"
mkdir -p "$RELEASE/bin" "$RELEASE/systemd"
cat > "$RELEASE/bin/health.sh" <<EOS
#!/usr/bin/env bash
set -euo pipefail
ok=0
for i in \$(seq 1 30); do
  ok=1
  for svc in $PORTS; do
    port="\${svc##*:}"
    curl -sS --max-time 2 -o /dev/null "http://127.0.0.1:\$port/" 2>/dev/null || ok=0
  done
  [ "\$ok" = "1" ] && break
  sleep 1
done
for svc in $PORTS; do
  name="\${svc%%:*}"; port="\${svc##*:}"
  if curl -sS --max-time 2 -o /dev/null "http://127.0.0.1:\$port/" 2>/dev/null; then
    echo "[UP] \$name:\$port"
  else
    echo "[DOWN] \$name:\$port"; exit 1
  fi
done
EOS
cat > "$RELEASE/bin/migrate.sh" <<'EOS'
#!/usr/bin/env bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-/etc/dam-products/$(basename "$(dirname "$BASE")").env}"
[ -f "$ENV_FILE" ] && set -a && . "$ENV_FILE" && set +a
: "${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL is required}"
psql "$SPRING_DATASOURCE_URL" -v ON_ERROR_STOP=1 -f "$BASE/db/migrate.sql"
[ -f "$BASE/db/base-migrate.sql" ] && psql "$SPRING_DATASOURCE_URL" -v ON_ERROR_STOP=1 -f "$BASE/db/base-migrate.sql" || true
EOS
cat > "$RELEASE/bin/serve-frontend.sh" <<'EOS'
#!/usr/bin/env bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${FRONTEND_PORT:-8088}"
[ -d "$BASE/frontend" ] || { echo "frontend/ missing"; exit 1; }
exec python3 -m http.server "$PORT" --directory "$BASE/frontend"
EOS
cat > "$RELEASE/bin/rollback.sh" <<'EOS'
#!/usr/bin/env bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
PREFIX="$(dirname "$BASE")"
prev="$(ls -1dt "$PREFIX"/releases/*/ 2>/dev/null | sed -n 2p)"
[ -n "$prev" ] || { echo "no previous release"; exit 1; }
[ -f "$PREFIX/current/bin/stop.sh" ] && "$PREFIX/current/bin/stop.sh" || true
ln -sfn "${prev%/}" "$PREFIX/current"
"$PREFIX/current/bin/start.sh"
echo "rolled back to ${prev%/}"
EOS
for svc in $SERVICES; do
cat > "$RELEASE/systemd/dam-product-${FOLDER}-${svc}.service" <<EOS
[Unit]
Description=DAM product ${CODE} service ${svc}
After=network.target

[Service]
Type=simple
User=dam
Group=dam
WorkingDirectory=@PREFIX@/current
EnvironmentFile=-/etc/dam-products/${FOLDER}.env
ExecStart=/usr/bin/java -Xmx512m -Xms256m -jar @PREFIX@/current/jars/${svc}-${VERSION}-exec.jar --spring.profiles.active=prod
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
StandardOutput=append:@PREFIX@/current/logs/${svc}.log
StandardError=append:@PREFIX@/current/logs/${svc}.log

[Install]
WantedBy=multi-user.target
EOS
done
cat > "$RELEASE/bin/install-systemd.sh" <<'EOS'
#!/usr/bin/env bash
set -euo pipefail
BASE="$(cd "$(dirname "$0")/.." && pwd)"
PREFIX="$(dirname "$BASE")"
for unit in "$BASE"/systemd/*.service; do
  name="$(basename "$unit")"
  sed "s#@PREFIX@#$PREFIX#g" "$unit" > "/etc/systemd/system/$name"
done
systemctl daemon-reload
for unit in "$BASE"/systemd/*.service; do systemctl enable --now "$(basename "$unit")"; done
EOS
cat > "$RELEASE/bin/uninstall-systemd.sh" <<EOS
#!/usr/bin/env bash
set -euo pipefail
for unit in /etc/systemd/system/dam-product-${FOLDER}-*.service; do
  [ -e "\$unit" ] || continue
  systemctl disable --now "\$(basename "\$unit")" 2>/dev/null || true
  rm -f "\$unit"
done
systemctl daemon-reload 2>/dev/null || true
EOS
chmod +x "$RELEASE"/bin/*.sh
