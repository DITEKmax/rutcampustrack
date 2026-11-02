#!/usr/bin/env bash
# RCT_INITIAL_ADMIN_DEPLOY_V1
# Called only by the gated workflow, from its exact-SHA source checkout.
set -euo pipefail
umask 077
unset INITIAL_ADMIN_REQUEST
sha="${1:?exact SHA required}"
incoming="${2:?private incoming directory required}"
bootstrap="${3:-false}"
[[ "$bootstrap" == true || "$bootstrap" == false ]] || exit 2
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || exit 2
[[ "$incoming" =~ ^/opt/rutcampustrack/incoming/[0-9]+-[0-9]+$ ]] || exit 2
[[ "$(cat "$incoming/REVISION")" == "$sha" ]] || exit 2
trap 'rm -f "$incoming/PROD_ENV" "$incoming/PROD_SECRET_FILES_B64" "$incoming/GHCR_TOKEN" "$incoming/INITIAL_ADMIN_REQUEST"' EXIT
if [[ "$bootstrap" == true ]]; then
  python3 scripts/bootstrap-initial-admin.py --request-file "$incoming/INITIAL_ADMIN_REQUEST" --validate-only
fi
python3 scripts/release-images.py "$incoming/release-images.json" "$sha"
export IMAGE_TAG="$sha"
secrets_root=/opt/rutcampustrack-secrets/active
# Refuse adopting existing data with a newly generated credential set.
if [[ ! -d "$secrets_root" ]]; then
  for volume in pg-academic-data pg-schedule-data mongo-data redis-data rabbitmq-data; do
    if docker volume inspect "rutcampustrack_$volume" >/dev/null 2>&1; then
      echo 'Existing datastore volume without managed inputs; explicit adoption required' >&2
      exit 1
    fi
  done
fi
# All bundle/env/TLS/permission checks occur before any service operation.
python3 scripts/deployment-secrets.py install --env "$incoming/PROD_ENV" --bundle "$incoming/PROD_SECRET_FILES_B64"
# CI user owns the 0700 vault; env copy stays owner-only in this source release.
install -m 600 "$incoming/PROD_ENV" .env.prod
compose=(docker compose --project-name rutcampustrack --env-file .env.prod
  -f docker-compose.prod.yml -f docker-compose.operator.yml)
"${compose[@]}" config --quiet 2>/dev/null
# Existing public certificate volumes and ACME bootstrap transition are manual.
docker volume inspect rutcampustrack_certbot-conf rutcampustrack_certbot-www >/dev/null
if docker ps --format '{{.Names}}' | grep -qx ruttrack-acme-bootstrap; then
  echo 'ACME bootstrap is still active; manual approved cutover required' >&2
  exit 1
fi
# Private GHCR login uses stdin and an ephemeral config; no shared login mutation.
export DOCKER_CONFIG="$incoming/docker-auth"
mkdir -m 700 "$DOCKER_CONFIG"
trap 'rm -f "$incoming/PROD_ENV" "$incoming/PROD_SECRET_FILES_B64" "$incoming/GHCR_TOKEN" "$incoming/INITIAL_ADMIN_REQUEST"; rm -rf -- "$DOCKER_CONFIG" "$incoming/jwt-candidate"' EXIT
docker login ghcr.io -u ditekmax --password-stdin < "$incoming/GHCR_TOKEN" >/dev/null 2>&1
"${compose[@]}" pull --quiet
python3 scripts/release-images.py "$incoming/release-images.json" "$sha" --verify-local
# Named JWT volume is never replaced. Its initializer refuses partial keysets.
docker volume create rutcampustrack_jwt-keys >/dev/null
# Host creates a candidate; existing complete keysets are always preserved.
mkdir -m 700 "$incoming/jwt-candidate"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$incoming/jwt-candidate/private.key" 2>/dev/null
openssl pkey -in "$incoming/jwt-candidate/private.key" -pubout -out "$incoming/jwt-candidate/public.key" 2>/dev/null
cat /proc/sys/kernel/random/uuid > "$incoming/jwt-candidate/kid.txt"
docker run --rm --pull never --user 0 --network none --entrypoint sh \
  --mount type=volume,source=rutcampustrack_jwt-keys,target=/keys,volume-nocopy \
  --mount "type=bind,source=$incoming/jwt-candidate,target=/candidate,readonly" \
  "ghcr.io/ditekmax/rutcampustrack/auth-service:$sha" -ec '
  count=0
  for file in private.key public.key kid.txt; do
    [ ! -L "/keys/$file" ] || exit 1
    [ ! -e "/keys/$file" ] || count=$((count + 1))
  done
  if [ "$count" = 0 ]; then
    cp /candidate/private.key /candidate/public.key /candidate/kid.txt /keys/
  elif [ "$count" != 3 ]; then
    echo "Partial JWT keyset: refusing implicit rotation" >&2; exit 1
  fi
  for file in private.key public.key kid.txt; do [ -s "/keys/$file" ] || exit 1; done
  chown 100:101 /keys /keys/private.key /keys/public.key /keys/kid.txt
  chmod 755 /keys; chmod 600 /keys/private.key /keys/kid.txt; chmod 644 /keys/public.key
  cat /keys/private.key' > "$incoming/jwt-candidate/installed-private.key"
docker run --rm --pull never --user 0 --network none --entrypoint cat \
  --mount type=volume,source=rutcampustrack_jwt-keys,target=/keys,readonly,volume-nocopy \
  "ghcr.io/ditekmax/rutcampustrack/auth-service:$sha" /keys/public.key > "$incoming/jwt-candidate/installed-public.key"
openssl pkey -in "$incoming/jwt-candidate/installed-private.key" -pubout \
  -out "$incoming/jwt-candidate/derived-public.key" 2>/dev/null
cmp -s "$incoming/jwt-candidate/derived-public.key" "$incoming/jwt-candidate/installed-public.key"
[ -f nginx/dhparam.pem ] || openssl dhparam -out nginx/dhparam.pem 2048 2>/dev/null
"${compose[@]}" up -d --pull never --no-build --wait --wait-timeout 180
docker exec rct-nginx nginx -t
docker exec rct-nginx nginx -s reload
python3 scripts/verify-served-web-panel.py https://ruttrack.site "$sha"
admin_state="$(python3 scripts/initial-admin-status.py)"
case "$admin_state" in
  COMPLETE)
    echo 'Initial administrator bootstrap already complete; creation skipped'
    ;;
  ABSENT)
    if [[ "$bootstrap" == true ]]; then
      python3 scripts/bootstrap-initial-admin.py --request-file "$incoming/INITIAL_ADMIN_REQUEST" \
        --project-name rutcampustrack --env-file .env.prod --image-tag "$sha" \
        --compose-file docker-compose.prod.yml --compose-file docker-compose.operator.yml
      # A successful protocol command is not a substitute for its durable, ready result.
      [[ "$(python3 scripts/initial-admin-status.py --require-ready)" == COMPLETE ]] || exit 2
    fi
    ;;
  *) echo 'Initial administrator state is unresolved; deployment held' >&2; exit 2 ;;
esac
printf '%s\n' "$sha" > /opt/rutcampustrack/.deployed-sha
