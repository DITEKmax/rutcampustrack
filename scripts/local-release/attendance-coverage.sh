set -euo pipefail
umask 077
service=attendance-service
module=:services:attendance-service:attendance-app
export RCT_LOCAL_RELEASE_SERVICE="$service"
outcome=failure
collect() {
  original=$?
  trap - EXIT
  RCT_CI_TEST_OUTCOME="$outcome" python3 scripts/collect-ci-test-outcomes.py --service "$service" --task test || original=1
  RCT_CI_TEST_OUTCOME="$outcome" python3 scripts/collect-ci-test-outcomes.py --service "$service" --task integrationTest --verify-ordinary-integration || original=1
  exit "$original"
}
trap collect EXIT
# Same three tasks as the attendance job in .github/workflows/coverage.yml.
# The module owns the bounded 1024m/one-fork policy for both CI and this command.
python3 -B scripts/attendance-memory/run.py --arms target
outcome=success
