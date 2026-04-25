#!/bin/sh
# M13 G14 — nginx fail-fast entrypoint.
#
# Проверяет что SWAGGER_HTPASSWD env var определён и не пустой ДО старта
# nginx. Без этой проверки nginx стартанул бы с пустым /etc/nginx/.htpasswd,
# и любой login на /swagger-ui (или /prometheus/, /alertmanager/) с пустым
# password проходил бы — silent security regression.
#
# Дополнительно валидирует format ($$apr1$$ или $$2y$$ — bcrypt) — те же
# правила что и в scripts/validate-env-prod.sh G13. Если SWAGGER_HTPASSWD
# протёк через compose-escape unescaped (single $ вместо $$), htpasswd line
# не содержит правильного hash → nginx auth_basic не срабатывает корректно.
#
# Exit codes: 1 на любой fail-fast condition.

set -eu

HTPASSWD_FILE="/etc/nginx/.htpasswd"

# 1. Required env var.
if [ -z "${SWAGGER_HTPASSWD:-}" ]; then
    echo "FATAL: SWAGGER_HTPASSWD env var is empty or unset." >&2
    echo "       Generate via: htpasswd -nB swagger | sed 's/\$/\$\$/g'" >&2
    echo "       (или openssl passwd -apr1 fallback — см. .env.prod.example)" >&2
    exit 1
fi

# 2. Format check — должно быть «login:hash» где hash начинается с $apr1$ либо $2y$.
# В runtime контейнера docker-compose уже декодировал $$ → $, проверяем единичные $.
case "$SWAGGER_HTPASSWD" in
    *:'$apr1$'* | *:'$2y$'*)
        ;;  # OK
    *)
        echo "FATAL: SWAGGER_HTPASSWD format invalid." >&2
        echo "       Expected: 'login:\$apr1\$...' (apr1) or 'login:\$2y\$...' (bcrypt)." >&2
        echo "       Got: ${SWAGGER_HTPASSWD%%:*}:<hash starts with $(echo "${SWAGGER_HTPASSWD#*:}" | cut -c1-10)...>" >&2
        echo "       Если в .env.prod написано \$\$apr1\$\$ — docker-compose decode'нул" >&2
        echo "       \$\$ → \$, но если видим \$\$ здесь, значит escape был неправильный." >&2
        exit 1
        ;;
esac

# 3. Materialize .htpasswd.
printf "%s\n" "$SWAGGER_HTPASSWD" > "$HTPASSWD_FILE"
chmod 644 "$HTPASSWD_FILE"

# 4. Sanity: htpasswd file не пустой после write.
if [ ! -s "$HTPASSWD_FILE" ]; then
    echo "FATAL: $HTPASSWD_FILE is empty after write — disk full?" >&2
    exit 1
fi

echo "[entrypoint] SWAGGER_HTPASSWD validated, .htpasswd materialized ($(wc -c < "$HTPASSWD_FILE") bytes)."

# 5. Background safety-net — reload nginx every 5 min (preserves M11 G4 behaviour
#    из inline command'а). Primary reload path: GitHub Actions deploy.yml.
( while :; do sleep 5m; nginx -s reload 2>/dev/null || true; done ) &

# 6. Foreground nginx.
exec nginx -g 'daemon off;'
