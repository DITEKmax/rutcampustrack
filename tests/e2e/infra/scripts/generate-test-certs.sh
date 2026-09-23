#!/usr/bin/env bash
# M13 G25.3 — генерация self-signed TLS сертификатов для e2e nginx,
# Schedule gRPC и Academic gRPC. Each gRPC server cert is trusted only by
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

set_server_key_owner() {
  local key_path="$1"
  local owner="$2:$3"
  local actual_owner
  local actual_mode

  if [ "$(uname -s)" != "Linux" ]; then
    echo "Cannot verify Linux container ownership for ${key_path} on this platform; provision these e2e certificates on Linux." >&2
    return 1
  fi

  actual_mode="$(stat -c '%a' "${key_path}")"
  if [ "${actual_mode}" != "600" ] && ! chmod 600 "${key_path}" 2>/dev/null; then
    if ! command -v sudo >/dev/null 2>&1 || ! sudo -n chmod 600 "${key_path}"; then
      echo "Cannot restrict ${key_path} to mode 0600." >&2
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
  if [ "${actual_mode}" != "600" ]; then
    if ! command -v sudo >/dev/null 2>&1 || ! sudo -n chmod 600 "${key_path}"; then
      echo "Cannot verify mode 0600 for ${key_path}." >&2
      return 1
    fi
    actual_mode="$(stat -c '%a' "${key_path}")"
  fi
  if [ "${actual_owner}" != "${owner}" ] || [ "${actual_mode}" != "600" ]; then
    echo "Private-key ownership check failed for ${key_path}: expected ${owner}:600." >&2
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

echo "Done:"
ls -l "${CERTS_DIR}/server."* "${CERTS_DIR}/schedule-server."* "${CERTS_DIR}/academic-server."*
