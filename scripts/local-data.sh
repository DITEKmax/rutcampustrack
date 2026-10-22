#!/usr/bin/env bash
# Only the six local datastores; never starts a bot or application provider.
set -euo pipefail
env_file="$(realpath "${1:?Usage: local-data.sh /private/.env config|up|ps}")"
action="${2:-config}"
[[ "$action" == config || "$action" == up || "$action" == ps ]] || exit 2
cd "$(dirname "$0")/.."
python3 - "$env_file" <<'PY'
import sys
from pathlib import Path
text = Path(sys.argv[1]).read_text()
if "COMPOSE_PROJECT_NAME='rct-local-data'" not in text or "LOCAL_ONLY_DISABLED_TOKEN" not in text:
    raise SystemExit('Local-only environment required')
PY
compose=(docker compose --project-name rct-local-data --env-file "$env_file"
  -f docker-compose.prod.yml -f docker-compose.operator.yml -f docker-compose.local-data.yml)
if [[ "$action" == config ]]; then
  "${compose[@]}" config --quiet
elif [[ "$action" == up ]]; then
  "${compose[@]}" up -d --wait postgres-academic postgres-schedule mongo-attendance redis redis-cache rabbitmq
else
  "${compose[@]}" ps
fi
