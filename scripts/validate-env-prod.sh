#!/usr/bin/env bash
# M13 G13 — pre-flight validator для .env.prod перед docker compose deploy.
#
# Проверяет:
#   1. Файл существует и читаемый.
#   2. Все 22 required переменные определены и не равны "CHANGE_ME"
#      (либо substring "CHANGE_ME" в значении — частая ошибка copy-paste).
#   3. Формат каждой соответствует ожиданиям:
#      - GRPC_SECRET / INTERNAL_ISSUER_SECRET — base64 ≥ 32 bytes.
#      - MONGODB_REPLICA_SET_KEY — base64 ровно 1024 chars (756 raw bytes).
#      - BOT_TOKEN / TMA_BOT_TOKEN / BOT_ALERT_TOKEN — Telegram regex.
#      - VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY — base64url, нужная длина.
#      - VAPID_SUBJECT — mailto: URL либо https:// URL.
#      - SWAGGER_HTPASSWD — login:hash, hash начинается с $$apr1$$ или $$2y$$.
#      - ALERT_WEBHOOK_SECRET — hex 64 chars (32 bytes).
#      - PASSWORD'ы — длина ≥ 8.
#      - URL'ы (CORS_ALLOWED_ORIGIN, MINI_APP_URL, NOTIFICATION_WS_ALLOWED_ORIGINS) —
#        начинаются с https://.
#
# Использование:
#   scripts/validate-env-prod.sh                    # default: ./.env.prod
#   scripts/validate-env-prod.sh /path/to/.env.prod # custom path
#
# Exit codes:
#   0 — всё ок, готово к deploy.
#   1 — file errors (missing/unreadable).
#   2 — missing required vars.
#   3 — format errors.

set -euo pipefail

ENV_FILE="${1:-.env.prod}"
FAIL_COUNT=0
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

err() {
    echo -e "${RED}✗ FAIL${NC}: $*" >&2
    FAIL_COUNT=$((FAIL_COUNT + 1))
}

ok() {
    echo -e "${GREEN}✓${NC} $*"
}

warn() {
    echo -e "${YELLOW}!${NC} $*"
}

# -----------------------------------------------------------------------------
# Pre-check: file existence
# -----------------------------------------------------------------------------
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}✗ FAIL${NC}: $ENV_FILE not found." >&2
    echo "Создайте через: cp .env.prod.example .env.prod" >&2
    exit 1
fi

if [ ! -r "$ENV_FILE" ]; then
    echo -e "${RED}✗ FAIL${NC}: $ENV_FILE not readable (chmod 600?)." >&2
    exit 1
fi

# Парсим .env.prod как dot-env (key=value), не как shell script.
# Pure bash parsing — пароли могут содержать ;, ~, ', ( и др. shell-special
# chars без quoting (Docker-compose тоже не делает shell evaluation).
# Заполняем ассоциативный массив ENV[var]=value.
declare -A ENV
while IFS= read -r line || [ -n "$line" ]; do
    # Skip пустые строки и комментарии.
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    # Парсим key=value (key — bash identifier, value — всё после первого =).
    if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
        key="${BASH_REMATCH[1]}"
        value="${BASH_REMATCH[2]}"
        # Strip surrounding double-quotes если есть.
        if [[ "$value" =~ ^\"(.*)\"$ ]]; then
            value="${BASH_REMATCH[1]}"
        fi
        ENV[$key]="$value"
    fi
done < "$ENV_FILE"

# Helper: получить значение из ENV map.
env_get() {
    echo "${ENV[$1]:-}"
}

# -----------------------------------------------------------------------------
# Required vars (must be defined + not "CHANGE_ME")
# -----------------------------------------------------------------------------
REQUIRED_VARS=(
    POSTGRES_ACADEMIC_PASSWORD POSTGRES_SCHEDULE_PASSWORD
    MONGO_USER MONGO_PASSWORD MONGO_ROOT_PASSWORD
    MONGO_NOTIFICATION_USER MONGO_NOTIFICATION_PASSWORD MONGODB_REPLICA_SET_KEY
    REDIS_PASSWORD
    RABBITMQ_USER RABBITMQ_PASSWORD
    BOT_TOKEN TMA_BOT_TOKEN BOT_ALERT_TOKEN
    VAPID_PUBLIC_KEY VAPID_PRIVATE_KEY VAPID_SUBJECT
    MINI_APP_URL CORS_ALLOWED_ORIGIN
    GRPC_SECRET INTERNAL_ISSUER_SECRET
    ALERT_WEBHOOK_SECRET GRAFANA_PASSWORD
    SWAGGER_HTPASSWD
)

echo "Validating $ENV_FILE..."
echo

# 1. Existence + non-placeholder
MISSING_FOUND=0
for var in "${REQUIRED_VARS[@]}"; do
    val=$(env_get "$var")
    if [ -z "$val" ]; then
        err "$var is not set"
        MISSING_FOUND=1
    elif [[ "$val" == *CHANGE_ME* ]]; then
        err "$var contains 'CHANGE_ME' placeholder — заполните реальным значением"
        MISSING_FOUND=1
    fi
done

if [ $MISSING_FOUND -eq 1 ]; then
    echo
    echo -e "${RED}Required vars не заполнены — заполните placeholder'ы и запустите снова.${NC}" >&2
    exit 2
fi

ok "Все ${#REQUIRED_VARS[@]} required vars определены и не CHANGE_ME"

# -----------------------------------------------------------------------------
# Format validation
# -----------------------------------------------------------------------------

# Helper: check минимальную длину
check_min_length() {
    local var_name="$1"
    local min_len="$2"
    local val
    val=$(env_get "$var_name")
    if [ ${#val} -lt "$min_len" ]; then
        err "$var_name length ${#val} < $min_len chars (weak)"
    fi
}

# Helper: regex match
check_regex() {
    local var_name="$1"
    local pattern="$2"
    local description="$3"
    local val
    val=$(env_get "$var_name")
    if ! [[ "$val" =~ $pattern ]]; then
        err "$var_name не соответствует формату ($description). Got: ${val:0:20}..."
    fi
}

# Passwords — minimum 8 chars (production hardening).
check_min_length POSTGRES_ACADEMIC_PASSWORD 8
check_min_length POSTGRES_SCHEDULE_PASSWORD 8
check_min_length MONGO_PASSWORD 8
check_min_length MONGO_ROOT_PASSWORD 16
check_min_length MONGO_NOTIFICATION_PASSWORD 8
check_min_length REDIS_PASSWORD 8
check_min_length RABBITMQ_PASSWORD 8
check_min_length GRAFANA_PASSWORD 8

# GRPC_SECRET / INTERNAL_ISSUER_SECRET — base64-encoded ≥ 32 bytes.
# Base64 of 32 bytes = 44 chars (с padding). Проверяем length ≥ 32.
check_min_length GRPC_SECRET 32
check_min_length INTERNAL_ISSUER_SECRET 32

# MONGODB_REPLICA_SET_KEY — base64 of 756 raw bytes = 1024 chars.
# Mongo требует ровно эту длину для keyfile.
mongo_key=$(env_get MONGODB_REPLICA_SET_KEY)
if [ "${#mongo_key}" -lt 1000 ] || [ "${#mongo_key}" -gt 1030 ]; then
    err "MONGODB_REPLICA_SET_KEY length ${#mongo_key} != ~1024 (expected 756 raw bytes base64). Generate: openssl rand -base64 756 | tr -d '\\n'"
fi

# M13 G24-fix-4: explicit guard против dev-placeholder из docker-compose.yml.
# Dev compose имеет fallback `rct_dev_replica_set_key_must_be_replaced_in_prod_environment_security`
# на случай если MONGODB_REPLICA_SET_KEY не выставлен. Если оператор
# скопирует dev .env → .env.prod без замены, length check (69 chars)
# тоже ловит — но явный value-check даёт actionable сообщение.
DEV_PLACEHOLDER="rct_dev_replica_set_key_must_be_replaced_in_prod_environment_security"
if [ "$mongo_key" = "$DEV_PLACEHOLDER" ]; then
    err "MONGODB_REPLICA_SET_KEY = dev placeholder из docker-compose.yml. Это ПУБЛИЧНО известный keyfile (любой кто читает репо может authenticate в RS). Сгенерируй уникальный: openssl rand -base64 756 | tr -d '\\n'"
fi

# Telegram bot tokens — формат: digits:chars (e.g. "8744653460:AAF-guQdXoMBDmS...").
TELEGRAM_REGEX='^[0-9]{9,12}:[A-Za-z0-9_-]{30,}$'
check_regex BOT_TOKEN "$TELEGRAM_REGEX" "Telegram bot token (digits:chars)"
check_regex TMA_BOT_TOKEN "$TELEGRAM_REGEX" "Telegram bot token (digits:chars)"
check_regex BOT_ALERT_TOKEN "$TELEGRAM_REGEX" "Telegram bot token (digits:chars)"

# VAPID — base64url. Public 87 chars (P-256 EC), private 43 chars.
# Допускаем небольшой drift (web-push lib может padding'ом колебаться).
vapid_pub=$(env_get VAPID_PUBLIC_KEY)
vapid_priv=$(env_get VAPID_PRIVATE_KEY)
if [ "${#vapid_pub}" -lt 80 ] || [ "${#vapid_pub}" -gt 90 ]; then
    err "VAPID_PUBLIC_KEY length ${#vapid_pub} unexpected (P-256 EC = 87 chars). Generate: npx web-push generate-vapid-keys"
fi
if [ "${#vapid_priv}" -lt 40 ] || [ "${#vapid_priv}" -gt 50 ]; then
    err "VAPID_PRIVATE_KEY length ${#vapid_priv} unexpected (= 43 chars). Generate: npx web-push generate-vapid-keys"
fi

# VAPID_SUBJECT — mailto: либо https:// URL (web-push spec).
vapid_subj=$(env_get VAPID_SUBJECT)
if ! [[ "$vapid_subj" =~ ^(mailto:|https://) ]]; then
    err "VAPID_SUBJECT должен начинаться с 'mailto:' либо 'https://' (web-push RFC8292)"
fi

# URL fields.
for url_var in CORS_ALLOWED_ORIGIN MINI_APP_URL; do
    val=$(env_get "$url_var")
    if ! [[ "$val" =~ ^https:// ]]; then
        err "$url_var должен начинаться с https://"
    fi
done

# NOTIFICATION_WS_ALLOWED_ORIGINS (default — может быть не задан, ок).
ws_origins=$(env_get NOTIFICATION_WS_ALLOWED_ORIGINS)
if [ -n "$ws_origins" ]; then
    if ! [[ "$ws_origins" =~ ^https:// ]]; then
        err "NOTIFICATION_WS_ALLOWED_ORIGINS должен начинаться с https://"
    fi
fi

# SWAGGER_HTPASSWD — формат «login:$$apr1$$...» либо «login:$$2y$$...».
# Двойные `$$` — обязательны для docker-compose escape.
swagger_pwd=$(env_get SWAGGER_HTPASSWD)
if ! [[ "$swagger_pwd" =~ ^[a-zA-Z0-9_]+:\$\$(apr1|2y) ]]; then
    err "SWAGGER_HTPASSWD format error: должен быть 'login:\$\$apr1\$\$...' или 'login:\$\$2y\$\$...' (DOUBLE dollar для docker-compose escape!). Generate: htpasswd -nB swagger | sed 's/\$/\$\$/g'"
fi

# ALERT_WEBHOOK_SECRET — hex 64 chars (32 bytes).
alert_secret=$(env_get ALERT_WEBHOOK_SECRET)
if ! [[ "$alert_secret" =~ ^[a-f0-9]{64}$ ]]; then
    warn "ALERT_WEBHOOK_SECRET не выглядит как hex 64 chars (openssl rand -hex 32). Проверить вручную."
fi

# ADMIN_TELEGRAM_IDS (optional but recommend non-empty for prod).
admin_ids=$(env_get ADMIN_TELEGRAM_IDS)
if [ -z "$admin_ids" ] || [ "$admin_ids" = "0" ]; then
    warn "ADMIN_TELEGRAM_IDS пуст или = 0 — никто не получит alert'ы. Рекомендуется выставить comma-separated user IDs."
fi

# IMAGE_TAG — обычно либо latest либо semver.
image_tag=$(env_get IMAGE_TAG)
if [ "$image_tag" = "latest" ]; then
    warn "IMAGE_TAG=latest — для prod рекомендуется immutable tag (vX.Y.Z) после release."
fi

# -----------------------------------------------------------------------------
# Result
# -----------------------------------------------------------------------------
echo
if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}✓ Все validations passed. .env.prod готов к docker compose up.${NC}"
    exit 0
else
    echo -e "${RED}✗ $FAIL_COUNT validation(s) failed. Исправьте и запустите снова.${NC}" >&2
    exit 3
fi
