#!/usr/bin/env bash
# RCT_INITIAL_ADMIN_DELIVERY_V1
# GitHub runner only. Private files are never Actions artifacts or arguments.
set -euo pipefail
umask 077
# Keep the inherited request shell-local until the dedicated materializer.
export -n INITIAL_ADMIN_REQUEST
bootstrap="${BOOTSTRAP_INITIAL_ADMIN:-false}"
[[ "$bootstrap" == true || "$bootstrap" == false ]] || exit 2
if [[ "$bootstrap" == true ]]; then
  [[ "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]] || exit 2
  grep -qx '# RCT_INITIAL_ADMIN_DEPLOY_V1' scripts/deploy-release.sh || exit 2
fi
[[ "${DEPLOY_SHA:-}" =~ ^[0-9a-f]{40}$ ]] || exit 2
[[ "$(git rev-parse HEAD)" == "$DEPLOY_SHA" ]] || exit 2
[[ "${VPS_HOST:-}" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]+$ ]] || exit 2
[[ "${VPS_USER:-}" =~ ^[a-z_][a-z0-9_-]*$ ]] || exit 2
[[ "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ && "${GITHUB_RUN_ATTEMPT:-}" =~ ^[0-9]+$ ]] || exit 2
python3 scripts/release-images.py "${RELEASE_IMAGE_MANIFEST:-release-images.json}" "$DEPLOY_SHA"
private="$(mktemp -d)"
incoming_created=false
cleanup() {
  if [[ "$incoming_created" == true ]]; then
    # shellcheck disable=SC2029 # Inputs are locally validated numeric run identifiers.
    ssh "${ssh_opts[@]}" "$remote" "rm -f '$incoming/PROD_ENV' '$incoming/PROD_SECRET_FILES_B64' '$incoming/GHCR_TOKEN' '$incoming/INITIAL_ADMIN_REQUEST' '$incoming/source.tar'" || true
  fi
  rm -rf -- "$private"
}
trap cleanup EXIT
export RCT_PRIVATE_DELIVERY="$private"
INITIAL_ADMIN_REQUEST="${INITIAL_ADMIN_REQUEST:-}" python3 - <<'PY'
import os
from pathlib import Path
root = Path(os.environ['RCT_PRIVATE_DELIVERY'])
for name in ('PROD_ENV', 'PROD_SECRET_FILES_B64', 'SSH_KNOWN_HOSTS', 'SSH_PRIVATE_KEY', 'GHCR_TOKEN'):
    value = os.environ.get(name, '')
    if not value or len(value.encode()) > 48 * 1024:
        raise SystemExit('Required GitHub Secret missing or oversized: ' + name)
    (root / name).write_text(value)
    (root / name).chmod(0o600)
if os.environ.get('BOOTSTRAP_INITIAL_ADMIN') == 'true':
    value = os.environ.get('INITIAL_ADMIN_REQUEST', '')
    if not value or len(value.encode()) > 8192:
        raise SystemExit('Initial administrator request missing or oversized')
    (root / 'INITIAL_ADMIN_REQUEST').write_text(value, encoding='utf-8')
    (root / 'INITIAL_ADMIN_REQUEST').chmod(0o600)
PY
unset INITIAL_ADMIN_REQUEST
if [[ "$bootstrap" == true ]]; then
  python3 scripts/bootstrap-initial-admin.py --request-file "$private/INITIAL_ADMIN_REQUEST" --validate-only
fi
# Validate the whole delivery before SSH or any VPS writes.
python3 scripts/deployment-secrets.py validate --env "$private/PROD_ENV" --bundle "$private/PROD_SECRET_FILES_B64"
unset PROD_ENV PROD_SECRET_FILES_B64 SSH_PRIVATE_KEY GHCR_TOKEN SSH_KNOWN_HOSTS
cp "${RELEASE_IMAGE_MANIFEST:-release-images.json}" "$private/release-images.json"
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
files=("$private/release-images.json" "$private/source.tar" "$private/REVISION" "$private/PROD_ENV" "$private/PROD_SECRET_FILES_B64" "$private/GHCR_TOKEN")
if [[ "$bootstrap" == true ]]; then files+=("$private/INITIAL_ADMIN_REQUEST"); fi
scp "${ssh_opts[@]}" "${files[@]}" "$remote:$incoming/"
# No git pull: release configs/scripts and IMAGE_TAG refer to the same revision.
# Public bind-mounted configs must be readable by image users; private work keeps umask 077.
# shellcheck disable=SC2029 # Validated SHA/run identifiers; no secret interpolation.
ssh "${ssh_opts[@]}" "$remote" "set -eu; umask 077; mkdir '$release'; (umask 022; tar -xf '$incoming/source.tar' -C '$release'); cd '$release'; bash scripts/deploy-release.sh '$DEPLOY_SHA' '$incoming' '$bootstrap'"
