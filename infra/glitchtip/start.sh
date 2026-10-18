#!/bin/sh
set -eu
# Read only explicitly mounted operator-owned files, never discover host secrets.
export DATABASE_URL="$(python -c 'from pathlib import Path; from urllib.parse import quote; print("postgres://glitchtip:" + quote(Path("/run/secrets/glitchtip-db-password").read_text().strip(), safe="") + "@glitchtip-db:5432/glitchtip")')"
export SECRET_KEY="$(cat /run/secrets/glitchtip-secret-key)"
[ "${#SECRET_KEY}" -ge 32 ] || { echo 'GlitchTip signing key must contain at least 32 characters' >&2; exit 1; }
if [ "$#" -gt 0 ]; then exec "$@"; fi
# Initialize only this dedicated telemetry database before creating its fixed projects.
python manage.py migrate --no-input --skip-checks
python manage.py maintain_partitions
python manage.py createcachetable
python /observer/bootstrap-projects.py
export SKIP_INIT=true
exec ./bin/start.sh
