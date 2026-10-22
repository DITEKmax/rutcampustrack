#!/usr/bin/env bash
# GitHub runner only. Private files are never Actions artifacts or arguments.
set -euo pipefail
umask 077
[[ "${DEPLOY_SHA:-}" =~ ^[0-9a-f]{40}$ ]] || exit 2
[[ "$(git rev-parse HEAD)" == "$DEPLOY_SHA" ]] || exit 2
[[ "${VPS_HOST:-}" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]+$ ]] || exit 2
[[ "${VPS_USER:-}" =~ ^[a-z_][a-z0-9_-]*$ ]] || exit 2
[[ "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ && "${GITHUB_RUN_ATTEMPT:-}" =~ ^[0-9]+$ ]] || exit 2
private="$(mktemp -d)"
incoming_created=false
cleanup() {
  if [[ "$incoming_created" == true ]]; then
    # shellcheck disable=SC2029 # Inputs are locally validated numeric run identifiers.
    ssh "${ssh_opts[@]}" "$remote" "rm -f '$incoming/PROD_ENV' '$incoming/PROD_SECRET_FILES_B64' '$incoming/GHCR_TOKEN' '$incoming/source.tar'" || true
  fi
  rm -rf -- "$private"
}
trap cleanup EXIT
export RCT_PRIVATE_DELIVERY="$private"
python3 - <<'PY'
import os
from pathlib import Path
root = Path(os.environ['RCT_PRIVATE_DELIVERY'])
for name in ('PROD_ENV', 'PROD_SECRET_FILES_B64', 'SSH_KNOWN_HOSTS', 'SSH_PRIVATE_KEY', 'GHCR_TOKEN'):
    value = os.environ.get(name, '')
    if not value or len(value.encode()) > 48 * 1024:
        raise SystemExit('Required GitHub Secret missing or oversized: ' + name)
    (root / name).write_text(value)
    (root / name).chmod(0o600)
PY
# Validate the whole delivery before SSH or any VPS writes.
python3 scripts/deployment-secrets.py validate --env "$private/PROD_ENV" --bundle "$private/PROD_SECRET_FILES_B64"
unset PROD_ENV PROD_SECRET_FILES_B64 SSH_PRIVATE_KEY GHCR_TOKEN SSH_KNOWN_HOSTS
git archive --format=tar "$DEPLOY_SHA" > "$private/source.tar"
printf '%s\n' "$DEPLOY_SHA" > "$private/REVISION"
ssh_opts=(-i "$private/SSH_PRIVATE_KEY" -o IdentitiesOnly=yes -o BatchMode=yes
  -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$private/SSH_KNOWN_HOSTS")
incoming="/opt/rutcampustrack/incoming/$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
release="/opt/rutcampustrack/releases/$DEPLOY_SHA-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
remote="$VPS_USER@$VPS_HOST"
# shellcheck disable=SC2029 # Validated SHA/run identifiers; no secret interpolation.
ssh "${ssh_opts[@]}" "$remote" "umask 077; mkdir -p /opt/rutcampustrack/incoming /opt/rutcampustrack/releases; mkdir '$incoming'; test ! -e '$release'"
incoming_created=true
scp "${ssh_opts[@]}" "$private/source.tar" "$private/REVISION" "$private/PROD_ENV" "$private/PROD_SECRET_FILES_B64" "$private/GHCR_TOKEN" "$remote:$incoming/"
# No git pull: release configs/scripts and IMAGE_TAG refer to the same revision.
# shellcheck disable=SC2029 # Validated SHA/run identifiers; no secret interpolation.
ssh "${ssh_opts[@]}" "$remote" "set -eu; umask 077; mkdir '$release'; tar -xf '$incoming/source.tar' -C '$release'; cd '$release'; bash scripts/deploy-release.sh '$DEPLOY_SHA' '$incoming'"
