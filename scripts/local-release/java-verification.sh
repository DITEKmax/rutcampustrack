set -euo pipefail
umask 077
python3 scripts/check-published-migrations.py
set -euo pipefail
umask 077
controls=(tests/harness/test_ci_test_outcomes.py
          tests/harness/test_gateway_smoke.py
          tests/harness/test_deploy_prerequisites.py
          tests/harness/test_served_web_panel.py
          scripts/test-recovery-failure-boundaries.py)
for control in "${controls[@]}"; do
  log="$RUNNER_TEMP/$(basename "$control").private.log"
  if python3 -B "$control" > "$log" 2>&1; then
    printf 'PASS bounded control %s\n' "$control"
  else
    status=$?
    printf 'FAIL bounded control %s (exit %d)\n' "$control" "$status"
    exit "$status"
  fi
done
for script in smoke-prod verify-gateway-e2e preflight-deploy verify-deploy; do
  bash -n "scripts/$script.sh"
done

./gradlew verifyNoDebugInProd verifyLogbackJsonInAllServices --no-daemon
