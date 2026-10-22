#!/usr/bin/env bash
# Real Gateway/Auth/Academic smoke with exact success, PEM and no-token denial.
# Private JSON on stdin: {login,password,role,groupId,groupName,forbiddenActor}.
# forbiddenActor must be a separately owned actor denied this group.
set -euo pipefail
exec "${PYTHON:-python3}" "$(dirname "$0")/gateway-smoke.py" gateway "${GATEWAY_URL:-http://localhost:8080}"
