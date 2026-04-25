#!/usr/bin/env bash
# M13 G25.3 — генерация self-signed TLS сертификата для e2e nginx.
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

if [ -f "${CERTS_DIR}/server.crt" ] && [ -f "${CERTS_DIR}/server.key" ]; then
  echo "Test certs already exist in ${CERTS_DIR} — skip generation."
  exit 0
fi

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

echo "Done:"
ls -l "${CERTS_DIR}/server."*
