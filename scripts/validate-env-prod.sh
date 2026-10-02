#!/usr/bin/env bash
# M13 G13 — pre-flight validator для .env.prod перед docker compose --env-file .env.prod -f docker-compose.prod.yml deploy.
#
# Проверяет:
#   1. Файл существует и читаемый.
#   2. Все required переменные определены и не равны "CHANGE_ME"
#      (либо substring "CHANGE_ME" в значении — частая ошибка copy-paste).
#   3. Формат каждой соответствует ожиданиям:
#      - GRPC_SECRET / INTERNAL_ISSUER_SECRET — base64 ≥ 32 bytes.
#      - ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN / SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN
#        / BOT_TO_NOTIFICATION_SERVICE_TOKEN — canonical unpadded base64url ровно 32 bytes (43 ASCII chars).
#      - MONGODB_REPLICA_SET_KEY — base64 ровно 1024 chars (756 raw bytes).
#      - BOT_TOKEN / TMA_BOT_TOKEN / BOT_ALERT_TOKEN — Telegram regex.
#      - VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY — base64url, нужная длина.
#      - VAPID_SUBJECT — mailto: URL либо https:// URL.
#      - SWAGGER_HTPASSWD — login:hash, hash начинается с $$apr1$$ или $$2y$$.
#      - ALERT_WEBHOOK_SECRET — hex 64 chars (32 bytes).
#      - PASSWORD'ы — длина ≥ 8.
#      - URL'ы (CORS_ALLOWED_ORIGIN, MINI_APP_URL, MINI_APP_WEB_URL,
#        NOTIFICATION_WS_ALLOWED_ORIGINS) — начинаются с https://.
#      - GATEWAY_PRIVATE_SUBNET — aligned RFC1918 parent IPv4 CIDR (/8..30).
#      - GATEWAY_NETWORK_GATEWAY — canonical usable host inside that parent.
#      - GATEWAY_NGINX_IPV4 — canonical usable host inside that parent;
#        this is the sole trusted reverse-proxy address.
#      - GATEWAY_DYNAMIC_IP_RANGE — aligned IPv4 CIDR fully inside the parent,
#        excluding both fixed hosts from Docker's dynamic allocation range.
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
    MINI_APP_URL MINI_APP_WEB_URL CORS_ALLOWED_ORIGIN
    GATEWAY_PRIVATE_SUBNET GATEWAY_NETWORK_GATEWAY GATEWAY_NGINX_IPV4
    GATEWAY_DYNAMIC_IP_RANGE
    GRPC_SECRET INTERNAL_ISSUER_SECRET
    ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN
    BOT_TO_NOTIFICATION_SERVICE_TOKEN
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

# Convert a canonical dotted-decimal IPv4 address to an unsigned integer.
# Leading-zero octets are rejected to avoid alternate numeric interpretations.
ipv4_to_int() {
    local address="$1"
    local original_ifs="$IFS"
    local -a octets=()
    local octet

    # Validate the complete dotted-decimal string before IFS splitting. Bash
    # read can discard a trailing empty field, which would otherwise let
    # `192.168.1.10.` through as four octets.
    if ! [[ "$address" =~ ^(0|[1-9][0-9]{0,2})(\.(0|[1-9][0-9]{0,2})){3}$ ]]; then
        return 1
    fi

    IFS='.' read -r -a octets <<< "$address"
    IFS="$original_ifs"
    if [ "${#octets[@]}" -ne 4 ]; then
        return 1
    fi

    for octet in "${octets[@]}"; do
        if ! [[ "$octet" =~ ^(0|[1-9][0-9]{0,2})$ ]]; then
            return 1
        fi
        if (( 10#$octet > 255 )); then
            return 1
        fi
    done

    printf '%u\n' "$((
        (10#${octets[0]} << 24) |
        (10#${octets[1]} << 16) |
        (10#${octets[2]} << 8) |
        10#${octets[3]}
    ))"
}

# Return network, broadcast and prefix for an aligned IPv4 CIDR.
cidr_bounds() {
    local cidr="$1"
    local address prefix address_int host_bits mask network broadcast

    if ! [[ "$cidr" =~ ^([^/]+)/((0|[1-9][0-9]?))$ ]]; then
        return 1
    fi
    address="${BASH_REMATCH[1]}"
    prefix="${BASH_REMATCH[2]}"
    if (( 10#$prefix < 8 || 10#$prefix > 30 )); then
        return 1
    fi
    if ! address_int=$(ipv4_to_int "$address"); then
        return 1
    fi

    host_bits=$((32 - 10#$prefix))
    mask=$(( (0xFFFFFFFF << host_bits) & 0xFFFFFFFF ))
    network=$((address_int & mask))
    if (( address_int != network )); then
        return 1
    fi
    broadcast=$((network | (0xFFFFFFFF ^ mask)))
    printf '%u %u %s\n' "$network" "$broadcast" "$prefix"
}

# Docker's private_net must remain within one RFC1918 range. Keep the range
# constants explicit so this validator does not depend on ambient tooling.
gateway_subnet=$(env_get GATEWAY_PRIVATE_SUBNET)
gateway_network_gateway=$(env_get GATEWAY_NETWORK_GATEWAY)
gateway_ipv4=$(env_get GATEWAY_NGINX_IPV4)
gateway_dynamic_range=$(env_get GATEWAY_DYNAMIC_IP_RANGE)
subnet_network=0
subnet_broadcast=0
subnet_prefix=0
network_gateway_address=0
gateway_address=0
dynamic_network=0
dynamic_broadcast=0
dynamic_prefix=0
subnet_valid=0
network_gateway_valid=0
gateway_valid=0
dynamic_valid=0

if subnet_bounds_value=$(cidr_bounds "$gateway_subnet"); then
    read -r subnet_network subnet_broadcast subnet_prefix <<< "$subnet_bounds_value"
    subnet_valid=1
else
    err "GATEWAY_PRIVATE_SUBNET must be an aligned RFC1918 IPv4 CIDR with prefix /8..30"
fi

if gateway_address=$(ipv4_to_int "$gateway_ipv4"); then
    gateway_valid=1
else
    err "GATEWAY_NGINX_IPV4 must be a canonical dotted-decimal IPv4 address"
fi

if network_gateway_address=$(ipv4_to_int "$gateway_network_gateway"); then
    network_gateway_valid=1
else
    err "GATEWAY_NETWORK_GATEWAY must be a canonical dotted-decimal IPv4 address"
fi

if dynamic_bounds_value=$(cidr_bounds "$gateway_dynamic_range"); then
    read -r dynamic_network dynamic_broadcast dynamic_prefix <<< "$dynamic_bounds_value"
    dynamic_valid=1
else
    err "GATEWAY_DYNAMIC_IP_RANGE must be an aligned IPv4 CIDR with prefix /8..30"
fi

if [ "$subnet_valid" -eq 1 ]; then
    private_match=0
    private_networks=(167772160 2886729728 3232235520)
    private_broadcasts=(184549375 2887778303 3232301055)
    for index in "${!private_networks[@]}"; do
        if (( subnet_network >= private_networks[index] &&
              subnet_broadcast <= private_broadcasts[index] )); then
            private_match=1
            break
        fi
    done
    if [ "$private_match" -eq 0 ]; then
        err "GATEWAY_PRIVATE_SUBNET must stay inside RFC1918 private ranges"
    fi
fi

if [ "$subnet_valid" -eq 1 ] && [ "$gateway_valid" -eq 1 ]; then
    if (( gateway_address <= subnet_network || gateway_address >= subnet_broadcast )); then
        err "GATEWAY_NGINX_IPV4 must be a usable host address inside GATEWAY_PRIVATE_SUBNET"
    else
        ok "GATEWAY_NGINX_IPV4 is the exact trusted edge inside GATEWAY_PRIVATE_SUBNET (/${subnet_prefix})"
    fi
fi

if [ "$subnet_valid" -eq 1 ] && [ "$network_gateway_valid" -eq 1 ]; then
    if (( network_gateway_address <= subnet_network || network_gateway_address >= subnet_broadcast )); then
        err "GATEWAY_NETWORK_GATEWAY must be a usable host address inside GATEWAY_PRIVATE_SUBNET"
    else
        ok "GATEWAY_NETWORK_GATEWAY is a usable host inside GATEWAY_PRIVATE_SUBNET (/${subnet_prefix})"
    fi
fi

if [ "$subnet_valid" -eq 1 ] && [ "$dynamic_valid" -eq 1 ]; then
    if (( dynamic_network < subnet_network || dynamic_broadcast > subnet_broadcast )); then
        err "GATEWAY_DYNAMIC_IP_RANGE must be fully contained in GATEWAY_PRIVATE_SUBNET"
    else
        ok "GATEWAY_DYNAMIC_IP_RANGE is contained in GATEWAY_PRIVATE_SUBNET (/${dynamic_prefix})"
    fi
fi

if [ "$gateway_valid" -eq 1 ] && [ "$network_gateway_valid" -eq 1 ]; then
    if (( gateway_address == network_gateway_address )); then
        err "GATEWAY_NETWORK_GATEWAY and GATEWAY_NGINX_IPV4 must be distinct usable hosts"
    fi
fi

if [ "$dynamic_valid" -eq 1 ]; then
    if [ "$gateway_valid" -eq 1 ] && (( gateway_address >= dynamic_network && gateway_address <= dynamic_broadcast )); then
        err "GATEWAY_NGINX_IPV4 must be outside GATEWAY_DYNAMIC_IP_RANGE"
    fi
    if [ "$network_gateway_valid" -eq 1 ] && (( network_gateway_address >= dynamic_network && network_gateway_address <= dynamic_broadcast )); then
        err "GATEWAY_NETWORK_GATEWAY must be outside GATEWAY_DYNAMIC_IP_RANGE"
    fi
fi

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

# Equivalent to DirectedServiceCredential.decodeCanonical: 43 base64url sextets
# encode 256 bits with two unused low bits, which MUST be zero in the last char.
# Restricting that last char rejects noncanonical aliases accepted by decoders.
# Pure Bash avoids storing decoded binary credentials or printing their values.
check_directed_service_token() {
    local var_name="$1"
    local val
    local LC_ALL=C
    val=$(env_get "$var_name")
    if ! [[ "$val" =~ ^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$ ]]; then
        err "$var_name must be canonical unpadded base64url for exactly 32 bytes (43 ASCII chars)"
    fi
}

check_directed_service_token ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN
check_directed_service_token SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN
check_directed_service_token BOT_TO_NOTIFICATION_SERVICE_TOKEN

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
for url_var in CORS_ALLOWED_ORIGIN MINI_APP_URL MINI_APP_WEB_URL; do
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
    echo -e "${GREEN}✓ Все validations passed. ${ENV_FILE} готов к docker compose --env-file \"${ENV_FILE}\" -f docker-compose.prod.yml up.${NC}"
    exit 0
else
    echo -e "${RED}✗ $FAIL_COUNT validation(s) failed. Исправьте и запустите снова.${NC}" >&2
    exit 3
fi
