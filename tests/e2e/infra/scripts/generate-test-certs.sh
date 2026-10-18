#!/usr/bin/env bash
# M13 G25.3 — генерация self-signed TLS сертификатов для e2e nginx,
# Schedule, Academic, Attendance и Auth gRPC. Each gRPC server cert is trusted only by
# clients of that service.
#
# Sertcfffts go в `tests/e2e/infra/certs/` (gitignored). Идемпотентно —
# regenerate'ит если файлов нет, иначе skip. CN=localhost, SAN=localhost.
#
# Запускается:
#   - локально перед `docker compose -f docker-compose.e2e.yml up`
#   - в CI job step (см. .github/workflows/ci.yml e2e-auth)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERTS_DIR="${SCRIPT_DIR}/../certs"

mkdir -p "${CERTS_DIR}"

verify_windows_container_key_readable() {
  local key_path="$1"
  local uid="$2"
  local gid="$3"
  local helper_image="eclipse-temurin:21-jre-alpine"
  local source_path

  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker Desktop is required to verify ${key_path} from Windows Git Bash." >&2
    return 1
  fi
  if ! command -v cygpath >/dev/null 2>&1; then
    echo "cygpath is required to verify ${key_path} from Windows Git Bash." >&2
    return 1
  fi
  if ! MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker image inspect "${helper_image}" >/dev/null 2>&1; then
    echo "Cannot verify Docker Desktop or local helper image ${helper_image}; ensure Docker Desktop is available and preload the image before generating certificates." >&2
    return 1
  fi

  source_path="$(cygpath -am "${key_path}")" || {
    echo "Cannot convert the private-key path for Docker Desktop." >&2
    return 1
  }

  # Only the one server key is mounted, read-only. The helper has no network,
  # a read-only root filesystem, no capabilities, and never prints key bytes.
  if ! MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker run \
    --pull=never \
    --rm \
    --network none \
    --read-only \
    --cap-drop ALL \
    --security-opt no-new-privileges:true \
    --user "${uid}:${gid}" \
    --mount "type=bind,source=${source_path},target=/server-key,readonly" \
    --entrypoint /bin/sh \
    "${helper_image}" \
    -c 'test "$(id -u)" = "$1" && test "$(id -g)" = "$2" && test -r /server-key && cat /server-key >/dev/null' \
    sh "${uid}" "${gid}" >/dev/null 2>&1; then
    echo "Docker Desktop could not verify that container UID:GID ${uid}:${gid} can read ${key_path}." >&2
    return 1
  fi
}

set_server_key_owner() {
  local key_path="$1"
  local owner="$2:$3"
  local mode="${4:-600}"
  local reader_uid="${5:-$2}"
  local actual_owner
  local actual_mode
  local platform

  platform="$(uname -s)"
  case "${platform}" in
    MINGW*|MSYS*)
      if ! verify_windows_container_key_readable "${key_path}" "${reader_uid}" "$3"; then
        return 1
      fi
      return
      ;;
    Linux)
      ;;
    *)
      echo "Unsupported certificate provisioning platform ${platform}; use Linux or Windows Git Bash with Docker Desktop." >&2
      return 1
      ;;
  esac

  actual_mode="$(stat -c '%a' "${key_path}")"
  if [ "${actual_mode}" != "${mode}" ] && ! chmod "${mode}" "${key_path}" 2>/dev/null; then
    if ! command -v sudo >/dev/null 2>&1 || ! sudo -n chmod "${mode}" "${key_path}"; then
      echo "Cannot restrict ${key_path} to mode ${mode}." >&2
      return 1
    fi
  fi
  actual_owner="$(stat -c '%u:%g' "${key_path}")"
  if [ "${actual_owner}" != "${owner}" ] && ! chown "${owner}" "${key_path}" 2>/dev/null; then
    if ! command -v sudo >/dev/null 2>&1 || ! sudo -n chown "${owner}" "${key_path}"; then
      echo "Cannot assign ${key_path} to container UID:GID ${owner}." >&2
      return 1
    fi
  fi
  actual_owner="$(stat -c '%u:%g' "${key_path}")"
  actual_mode="$(stat -c '%a' "${key_path}")"
  if [ "${actual_mode}" != "${mode}" ]; then
    if ! command -v sudo >/dev/null 2>&1 || ! sudo -n chmod "${mode}" "${key_path}"; then
      echo "Cannot verify mode ${mode} for ${key_path}." >&2
      return 1
    fi
    actual_mode="$(stat -c '%a' "${key_path}")"
  fi
  if [ "${actual_owner}" != "${owner}" ] || [ "${actual_mode}" != "${mode}" ]; then
    echo "Private-key ownership check failed for ${key_path}: expected ${owner}:${mode}." >&2
    return 1
  fi
}

if [ ! -f "${CERTS_DIR}/server.crt" ] || [ ! -f "${CERTS_DIR}/server.key" ]; then
  echo "Generating self-signed RSA-2048 cert (CN=localhost, 365d) in ${CERTS_DIR}..."
# `//CN=localhost` префикс на Windows Git Bash MSYS избегает конверсии
# /CN в C:\CN. На Linux/macOS '/CN=localhost' работает напрямую.
SUBJ="/CN=localhost"
if [ -n "${MSYSTEM:-}" ] || [ -n "${WSL_DISTRO_NAME:-}" ]; then
  SUBJ="//CN=localhost"
fi

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "${CERTS_DIR}/server.key" \
  -out "${CERTS_DIR}/server.crt" \
  -subj "${SUBJ}" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

chmod 644 "${CERTS_DIR}/server.crt"
chmod 600 "${CERTS_DIR}/server.key"
else
  echo "Nginx test certs already exist in ${CERTS_DIR} — keep them."
fi

if [ ! -f "${CERTS_DIR}/schedule-server.crt" ] || [ ! -f "${CERTS_DIR}/schedule-server.key" ]; then
  echo "Generating self-signed RSA-2048 Schedule gRPC cert (SAN=schedule-service) in ${CERTS_DIR}..."
  SCHEDULE_SUBJ="/CN=schedule-service"
  if [ -n "${MSYSTEM:-}" ] || [ -n "${WSL_DISTRO_NAME:-}" ]; then
    SCHEDULE_SUBJ="//CN=schedule-service"
  fi
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "${CERTS_DIR}/schedule-server.key" \
    -out "${CERTS_DIR}/schedule-server.crt" \
    -subj "${SCHEDULE_SUBJ}" \
    -addext "subjectAltName=DNS:schedule-service,DNS:localhost,IP:127.0.0.1"
  chmod 644 "${CERTS_DIR}/schedule-server.crt"
  set_server_key_owner "${CERTS_DIR}/schedule-server.key" 10002 10002
else
  set_server_key_owner "${CERTS_DIR}/schedule-server.key" 10002 10002
  echo "Schedule gRPC test certs already exist in ${CERTS_DIR} — keep them."
fi

if [ ! -f "${CERTS_DIR}/academic-server.crt" ] || [ ! -f "${CERTS_DIR}/academic-server.key" ]; then
  echo "Generating self-signed RSA-2048 Academic gRPC cert (SAN=academic-service) in ${CERTS_DIR}..."
  ACADEMIC_SUBJ="/CN=academic-service"
  if [ -n "${MSYSTEM:-}" ] || [ -n "${WSL_DISTRO_NAME:-}" ]; then
    ACADEMIC_SUBJ="//CN=academic-service"
  fi
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "${CERTS_DIR}/academic-server.key" \
    -out "${CERTS_DIR}/academic-server.crt" \
    -subj "${ACADEMIC_SUBJ}" \
    -addext "subjectAltName=DNS:academic-service"
  chmod 644 "${CERTS_DIR}/academic-server.crt"
  set_server_key_owner "${CERTS_DIR}/academic-server.key" 10001 10001
else
  set_server_key_owner "${CERTS_DIR}/academic-server.key" 10001 10001
  echo "Academic gRPC test certs already exist in ${CERTS_DIR} — keep them."
fi

# The Auth listener is a separate private TLS service. Its key never goes to clients.
if [ ! -f "${CERTS_DIR}/auth-server.crt" ] || [ ! -f "${CERTS_DIR}/auth-server.key" ]; then
  echo "Generating self-signed RSA-2048 Auth gRPC cert (SAN=auth-service) in ${CERTS_DIR}..."
  AUTH_SUBJ="/CN=auth-service"
  if [ -n "${MSYSTEM:-}" ] || [ -n "${WSL_DISTRO_NAME:-}" ]; then
    AUTH_SUBJ="//CN=auth-service"
  fi
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "${CERTS_DIR}/auth-server.key" \
    -out "${CERTS_DIR}/auth-server.crt" \
    -subj "${AUTH_SUBJ}" \
    -addext "subjectAltName=DNS:auth-service"
  chmod 644 "${CERTS_DIR}/auth-server.crt"
  set_server_key_owner "${CERTS_DIR}/auth-server.key" 0 10004 640 65534
else
  set_server_key_owner "${CERTS_DIR}/auth-server.key" 0 10004 640 65534
  echo "Auth gRPC test certs already exist in ${CERTS_DIR} — keep them."
fi

# File-only synthetic credentials: Docker mounts individual files, never this directory.
# Readable file mode supports different non-root container UIDs; the host parent is 0700.
AUTH_SECRETS_DIR="${CERTS_DIR}/auth-secrets"
if [ -L "${AUTH_SECRETS_DIR}" ]; then
  echo "Refusing a linked synthetic Auth secret directory." >&2
  exit 1
fi
mkdir -p "${AUTH_SECRETS_DIR}"
chmod 700 "${AUTH_SECRETS_DIR}"
for name in bot-to-read-token mobile-bff-to-auth-token academic-to-auth-token attendance-to-auth-token auth-to-academic-presentation-token mobile-bff-to-academic-token mobile-bff-to-attendance-token mobile-bff-to-schedule-token attendance-bot-read-to-academic-token attendance-bot-read-to-schedule-token academic-report-to-attendance-token academic-report-to-schedule-token notification-to-academic-token bot-to-auth-token pg-legacy-runtime-password pg-migration-password pg-monitor-password pg-auth-runtime-password pg-auth-migration-password pg-academic-runtime-password; do
  path="${AUTH_SECRETS_DIR}/${name}"
  if [ -L "${path}" ] || { [ -e "${path}" ] && [ ! -f "${path}" ]; }; then
    echo "Refusing a non-regular synthetic Auth secret file." >&2
    exit 1
  fi
  if [ ! -f "${path}" ]; then
    if [[ "${name}" == *-token ]]; then
      # Existing directed-credential validators require canonical base64url(32 bytes).
      (umask 077; set -o noclobber; openssl rand -base64 32 | tr '/+' '_-' | tr -d '=\r\n' > "${path}")
    else
      (umask 077; set -o noclobber; openssl rand -hex 32 | tr -d '\n' > "${path}")
    fi
  fi
  chmod 444 "${path}"
done

if [ ! -f "${CERTS_DIR}/attendance-server.crt" ] || [ ! -f "${CERTS_DIR}/attendance-server.key" ]; then
  echo "Generating self-signed RSA-2048 Attendance gRPC cert (SAN=attendance-service) in ${CERTS_DIR}..."
  ATTENDANCE_SUBJ="/CN=attendance-service"
  if [ -n "${MSYSTEM:-}" ] || [ -n "${WSL_DISTRO_NAME:-}" ]; then
    ATTENDANCE_SUBJ="//CN=attendance-service"
  fi
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "${CERTS_DIR}/attendance-server.key" \
    -out "${CERTS_DIR}/attendance-server.crt" \
    -subj "${ATTENDANCE_SUBJ}" \
    -addext "subjectAltName=DNS:attendance-service"
  chmod 644 "${CERTS_DIR}/attendance-server.crt"
  set_server_key_owner "${CERTS_DIR}/attendance-server.key" 10003 10003
else
  set_server_key_owner "${CERTS_DIR}/attendance-server.key" 10003 10003
  echo "Attendance gRPC test certs already exist in ${CERTS_DIR} — keep them."
fi

if [ ! -f "${CERTS_DIR}/mobile-bff-server.crt" ] || [ ! -f "${CERTS_DIR}/mobile-bff-server.key" ]; then
  echo "Generating self-signed MobileBFF private gRPC test certificate."
  MOBILE_BFF_SUBJ="/CN=mobile-bff"
  if [ -n "${MSYSTEM:-}" ] || [ -n "${WSL_DISTRO_NAME:-}" ]; then MOBILE_BFF_SUBJ="//CN=mobile-bff"; fi
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
    -keyout "${CERTS_DIR}/mobile-bff-server.key" \
    -out "${CERTS_DIR}/mobile-bff-server.crt" \
    -subj "${MOBILE_BFF_SUBJ}" -addext "subjectAltName=DNS:mobile-bff"
  chmod 644 "${CERTS_DIR}/mobile-bff-server.crt"
fi
set_server_key_owner "${CERTS_DIR}/mobile-bff-server.key" 10007 10007

echo "Done:"
ls -l "${CERTS_DIR}/server."* "${CERTS_DIR}/schedule-server."* "${CERTS_DIR}/academic-server."* "${CERTS_DIR}/attendance-server."* "${CERTS_DIR}/auth-server."* "${CERTS_DIR}/mobile-bff-server."*
