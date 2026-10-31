set -euo pipefail
umask 077
shellcheck --severity=warning scripts/*.sh
shellcheck --shell=bash --severity=warning scripts/local-release/*.sh
for helper in scripts/local-release/*.sh; do bash -n "$helper"; done
python3 scripts/local-release/parse-powershell.py
bash scripts/validate-grafana-dashboards.sh
