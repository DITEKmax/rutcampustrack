set -euo pipefail
umask 077
chmod u+x gradlew
test "$(uname -m)" = x86_64
test "$(node --version)" = v24.14.0
test "$(npm --version)" = 11.9.0
python3.12 -c 'import sys; assert sys.version_info[:2]==(3,12)'
java -version 2>&1 | grep -E 'version "21[."]'
for tool in docker bash curl tar openssl git certutil; do command -v "$tool" >/dev/null; done
test "$(docker --host=unix:///var/run/docker.sock info --format '{{.OSType}}')" = linux
python3.12 -m venv "$RCT_LOCAL_RELEASE_AUDIT/venv"
"$RCT_LOCAL_RELEASE_AUDIT/venv/bin/python" -m pip install --timeout 30 --retries 0 ruff==0.17.0 PyYAML==6.0.3 aio-pika==9.5.3 -r services/notification-bot/requirements.txt
npm --prefix frontends ci --cache "$RCT_LOCAL_RELEASE_AUDIT/cache/npm" --fetch-retries=0 --fetch-timeout=60000
npm --prefix tests/e2e ci --cache "$RCT_LOCAL_RELEASE_AUDIT/cache/npm" --fetch-retries=0 --fetch-timeout=60000
cd tests/e2e
npx --no-install playwright install chromium
