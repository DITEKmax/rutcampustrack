set -euo pipefail
umask 077
# Selected repeat only. No unit/coverage/full-service PASS is inferred.
git -c core.fileMode=false diff --exit-code -- services proto event-schemas
./gradlew :services:academic-service:academic-app:integrationTest \
  --tests ru.rutcampustrack.academic.grpc.RedisHeadmanRateLimiterIT \
  --tests ru.rutcampustrack.academic.subjectdeletion.SubjectDeletionHttpIT \
  --tests ru.rutcampustrack.academic.botcredentials.BotCredentialBrokerBridgeIT \
  --rerun-tasks --no-daemon --max-workers=4
