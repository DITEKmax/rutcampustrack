#!/usr/bin/env bash
# Stop all backend services started by m07-g3-launch-services.sh.
# Kills gradle daemons + Java workers by saved PIDs, then taskkill orphaned
# java.exe as fallback (gradle wraps java through cmd.exe so direct kill
# sometimes leaves zombie JVMs).

set -u

for pid_file in /tmp/rct-logs/*.pid; do
  [ -f "$pid_file" ] || continue
  pid=$(cat "$pid_file")
  echo "[stop] kill $pid ($(basename "$pid_file"))"
  kill "$pid" 2>/dev/null || true
done

sleep 2

# Fallback — taskkill any orphaned java.exe / gradlew.bat
taskkill //F //IM java.exe 2>/dev/null | head -5 || true
taskkill //F //IM gradlew.bat 2>/dev/null | head -5 || true

echo "[stop] done"
