set -euo pipefail
umask 077
service=$1
module=$2
export RCT_LOCAL_RELEASE_SERVICE="$service"
git -c core.fileMode=false diff --exit-code -- services proto event-schemas
case "$service" in
  attendance-service) bash scripts/local-release/reason-enum.sh; bash scripts/local-release/changed-domain.sh; bash scripts/local-release/stored-status.sh ;;
  schedule-service) bash scripts/local-release/oneoff.sh; bash scripts/local-release/changed-domain.sh ;;
  academic-service) bash scripts/local-release/changed-domain.sh; bash scripts/local-release/cache-commit.sh ;;
  notification-service|auth-service) bash scripts/local-release/changed-domain.sh ;;
esac
git -c core.fileMode=false diff --exit-code -- services proto event-schemas
flags=()
if [ "$service" = schedule-service ]; then flags+=(--no-build-cache); fi
if [ "$service" = auth-service ]; then flags+=(--refresh-dependencies); fi
outcome=failure
collect() {
  original=$?
  trap - EXIT
  RCT_CI_TEST_OUTCOME="$outcome" python3 scripts/collect-ci-test-outcomes.py --service "$service" --task test || original=1
  RCT_CI_TEST_OUTCOME="$outcome" python3 scripts/collect-ci-test-outcomes.py --service "$service" --task integrationTest --verify-ordinary-integration || original=1
  exit "$original"
}
trap collect EXIT
if [ "$service" = attendance-service ]; then
  bash scripts/local-release/attendance-coverage.sh
  ./gradlew "$module:bootJar" --no-daemon --max-workers=4
else
  ./gradlew "$module:test" "$module:integrationTest" "$module:jacocoTestReport" "$module:bootJar" --no-daemon --max-workers=4 "${flags[@]}"
fi
outcome=success
