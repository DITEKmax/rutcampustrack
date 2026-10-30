set -euo pipefail
umask 077
mkdir -p build/monitoring
python3 scripts/test-env-prod.py --compose-config
if [ "${RCT_LOCAL_RELEASE_MODE:-}" = failed-ci ]; then
  python3 - <<'PY'
import hashlib,json,pathlib
root=pathlib.Path.cwd(); receipt=json.loads((root/'build/monitoring/prepared-control.safe.json').read_text())
assert receipt['result']=='PASS' and receipt['nativeExit']==0 and receipt['testsRun']==14 and receipt['failures']==[]
assert all(hashlib.sha256((root/name).read_bytes()).hexdigest()==expected for name,expected in receipt['sourceSha256'].items())
PY
else
  python3 -B scripts/prepared-projection-control.py
fi
promtool check config --syntax-only infra/prometheus/prometheus.yml
promtool check rules infra/prometheus/rules/*.yml
amtool check-config infra/alertmanager/alertmanager.yml
python infra/prometheus/tests/check_formulas.py

python3 infra/prometheus/tests/check_delivery.py | tee build/monitoring/delivery.json
python3 infra/prometheus/tests/check_exporters.py | tee build/monitoring/exporters.json
python3 infra/prometheus/tests/check_pipeline.py | tee build/monitoring/pipeline.json
