set -euo pipefail
report="${RCT_GITLEAKS_REPORT:-results.sarif}"
test ! -e "$report"
umask 077
metadata_dir="$(mktemp -d "$RUNNER_TEMP/gitleaks-metadata.XXXXXX")"
export RCT_GITLEAKS_METADATA_CONFIG="$metadata_dir/config.toml"
log_opts="$(python3 scripts/gitleaks_metadata.py)"
test "$(uname -m)" = x86_64
tool_dir="$(mktemp -d "$RUNNER_TEMP/gitleaks-8.24.3.XXXXXX")"
curl --fail --silent --show-error --location \
  https://github.com/gitleaks/gitleaks/releases/download/v8.24.3/gitleaks_8.24.3_linux_x64.tar.gz \
  --connect-timeout 10 --max-time 120 --retry 0 --output "$tool_dir/archive.tar.gz"
printf '%s  %s\n' \
  9991e0b2903da4c8f6122b5c3186448b927a5da4deef1fe45271c3793f4ee29c \
  "$tool_dir/archive.tar.gz" | sha256sum --check --status
tar -xzf "$tool_dir/archive.tar.gz" -C "$tool_dir" gitleaks
scan_status=0
"$tool_dir/gitleaks" detect --source . --redact=100 --exit-code=2 \
  --report-format=sarif --report-path="$report" \
  --config="$RCT_GITLEAKS_METADATA_CONFIG" --gitleaks-ignore-path=.gitleaksignore --log-opts="$log_opts" || scan_status=$?
if [[ "$scan_status" -eq 0 || "$scan_status" -eq 2 ]]; then
  if [[ -s "$report" ]]; then
    if [ -n "${GITHUB_OUTPUT:-}" ]; then echo 'has_report=true' >> "$GITHUB_OUTPUT"; fi
  elif [[ "$scan_status" -eq 0 ]]; then
    exit 1
  fi
fi
exit "$scan_status"
