set -euo pipefail
umask 077
chmod u+x gradlew
python3.12 -c 'import sys; assert sys.version_info[:2]==(3,12)'
for tool in git docker bash; do command -v "$tool" >/dev/null; done
test "$(docker --host=unix:///var/run/docker.sock info --format '{{.OSType}}')" = linux
