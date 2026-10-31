set -euo pipefail
umask 077
bash ./gradlew --no-daemon --max-workers=2 --console=plain \
  '-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=2' \
  --system-prop=org.gradle.java.compile-classpath-packaging=true \
  :services:shared:shared-observability:compileJava \
  :services:shared:shared-observability:compileTestJava \
  :services:shared:shared-observability:test --tests '*SafeErrorEventTest' \
  :services:attendance-service:attendance-app:observabilityProbeClasspath

python3 infra/observability-apps/check_apps.py
