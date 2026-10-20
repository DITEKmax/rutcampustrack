#!/usr/bin/env bash
# Run against an assigned synthetic stand only. Private JSON arrives on stdin:
# {login,password,role,groupId,lessonId,date}. Credentials never enter argv/output.
set -euo pipefail
exec "${PYTHON:-python3}" "$(dirname "$0")/gateway-smoke.py" schedule "${1:-http://localhost}"
