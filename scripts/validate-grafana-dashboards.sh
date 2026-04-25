#!/usr/bin/env bash
# M13 G17 — структурная валидация Grafana dashboard JSON-файлов.
#
# Live smoke (open Grafana → ensure non-zero data) deferred в G23 VPS dry-run
# per owner-policy (manual browser verification — на VPS deploy одним
# batch'ем). Здесь автоматический pre-flight: каждый JSON парсится, имеет
# uid + title, ≥ 1 panel (top-level либо вложенный в row).
#
# Используется в:
#   - локальный smoke перед docker compose up;
#   - ShellCheck/CI step (вместе с validate-env-prod.sh);
#   - VPS deploy runbook §pre-flight.
#
# Exit codes:
#   0 — все dashboards валидны.
#   1 — каталог не найден / нет JSON файлов.
#   2 — структурные ошибки (broken JSON, missing uid/title, 0 panels).

set -euo pipefail

DASH_DIR="${1:-infra/grafana/provisioning/dashboards}"
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

err() {
    echo -e "${RED}✗ FAIL${NC}: $*" >&2
}

ok() {
    echo -e "${GREEN}✓${NC} $*"
}

if [ ! -d "$DASH_DIR" ]; then
    err "Dashboard directory not found: $DASH_DIR"
    exit 1
fi

# Собираем JSON файлы (исключая dashboard.yml provisioning manifest).
shopt -s nullglob
JSON_FILES=("$DASH_DIR"/*.json)
shopt -u nullglob

if [ ${#JSON_FILES[@]} -eq 0 ]; then
    err "Нет .json файлов в $DASH_DIR"
    exit 1
fi

if ! command -v node >/dev/null 2>&1; then
    err "node не найден в PATH (нужен для JSON-парсинга)"
    exit 1
fi

echo "Validating ${#JSON_FILES[@]} dashboards in $DASH_DIR..."
echo

FAIL_COUNT=0

for f in "${JSON_FILES[@]}"; do
    # node вернёт код 0 + JSON line с метаданными или код 2 + сообщение об ошибке.
    if ! result=$(node -e '
        const fs = require("fs");
        const f = process.argv[1];
        let d;
        try {
            d = JSON.parse(fs.readFileSync(f, "utf-8"));
        } catch (e) {
            console.error("broken JSON:", e.message);
            process.exit(2);
        }
        const uid = d.uid || "";
        const title = d.title || "";
        if (!uid) { console.error("missing uid"); process.exit(2); }
        if (!title) { console.error("missing title"); process.exit(2); }
        const top = (d.panels || []).length;
        let nested = 0;
        for (const p of (d.panels || [])) if (p.panels) nested += p.panels.length;
        const total = top + nested;
        if (total === 0) { console.error("0 panels"); process.exit(2); }
        console.log(JSON.stringify({uid, title, top, nested, total}));
    ' "$f" 2>&1); then
        err "$(basename "$f"): $result"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        continue
    fi
    ok "$(basename "$f"): $result"
done

echo

if [ $FAIL_COUNT -gt 0 ]; then
    err "$FAIL_COUNT dashboards failed validation"
    exit 2
fi

ok "Все ${#JSON_FILES[@]} dashboards валидны"
echo
echo "Live smoke (non-zero данные в panels) — deferred в G23 VPS dry-run."
