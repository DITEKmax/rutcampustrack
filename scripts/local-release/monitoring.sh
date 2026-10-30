set -euo pipefail
umask 077
mkdir -p build/monitoring
# Keep the primary failing stage even when no downstream pipeline receipt exists.
# The receipt contains only a finite stage, native status and source identity.
stage=TOOLS
finish() {
  local status=$? receipt_status=0
  trap - EXIT
  python3 - "$stage" "$status" <<'PY_STAGE' || receipt_status=$?
import json, pathlib, sys
sys.path.insert(0, str(pathlib.Path.cwd() / 'scripts'))
from local_release_context import source_context
stage, status = sys.argv[1], int(sys.argv[2])
assert stage in {'TOOLS', 'ENVIRONMENT', 'PREPARED_CONTROL', 'PROMETHEUS_CONFIG', 'PROMETHEUS_RULES', 'ALERTMANAGER_CONFIG', 'FORMULAS', 'DELIVERY', 'EXPORTERS', 'PIPELINE'}
value = {'schema': 1, 'stage': stage, 'native_exit_code': status,
         'result': 'PASS' if status == 0 else 'FAIL',
         'source_sha': source_context()['checkoutSha']}
path = pathlib.Path('build/monitoring/chain.safe.json')
with path.open('x') as output:
    json.dump(value, output, sort_keys=True)
    output.write('\n')
path.chmod(0o600)
PY_STAGE
  if [ "$status" -eq 0 ] && [ "$receipt_status" -ne 0 ]; then status=$receipt_status; fi
  exit "$status"
}
trap finish EXIT
# Local callers supply the same task-private binaries as the CI job.
test -x "${PROMTOOL:?PROMTOOL required}"
test -x "${PROMETHEUS_BIN:?PROMETHEUS_BIN required}"
test -x "${ALERTMANAGER_BIN:?ALERTMANAGER_BIN required}"
stage=ENVIRONMENT
python3 scripts/test-env-prod.py --compose-config
stage=PREPARED_CONTROL
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
stage=PROMETHEUS_CONFIG
promtool check config --syntax-only infra/prometheus/prometheus.yml
stage=PROMETHEUS_RULES
promtool check rules infra/prometheus/rules/*.yml
stage=ALERTMANAGER_CONFIG
amtool check-config infra/alertmanager/alertmanager.yml
stage=FORMULAS
python infra/prometheus/tests/check_formulas.py

stage=DELIVERY
python3 infra/prometheus/tests/check_delivery.py | tee build/monitoring/delivery.json
stage=EXPORTERS
python3 infra/prometheus/tests/check_exporters.py | tee build/monitoring/exporters.json
stage=PIPELINE
python3 infra/prometheus/tests/check_pipeline.py | tee build/monitoring/pipeline.json
