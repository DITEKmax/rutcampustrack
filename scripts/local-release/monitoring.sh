set -euo pipefail
umask 077
mkdir -p build/monitoring
python3 scripts/test-env-prod.py --compose-config
python3 -B tests/harness/test_prepared_probe_projection.py
promtool check config --syntax-only infra/prometheus/prometheus.yml
promtool check rules infra/prometheus/rules/*.yml
amtool check-config infra/alertmanager/alertmanager.yml
python infra/prometheus/tests/check_formulas.py

python3 infra/prometheus/tests/check_delivery.py | tee build/monitoring/delivery.json
python3 infra/prometheus/tests/check_exporters.py | tee build/monitoring/exporters.json
python3 infra/prometheus/tests/check_pipeline.py | tee build/monitoring/pipeline.json
