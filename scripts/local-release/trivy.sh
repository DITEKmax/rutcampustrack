set -euo pipefail
umask 077
snapshot="$RCT_LOCAL_RELEASE_AUDIT/tracked-scan"
mkdir -m 700 "$snapshot"
git archive --format=tar HEAD > "$RCT_LOCAL_RELEASE_AUDIT/tracked.tar"
python3 scripts/local-release/tracked-snapshot.py "$RCT_LOCAL_RELEASE_AUDIT/tracked.tar" "$snapshot"
cd "$snapshot"
set +e
trivy fs . --severity HIGH,CRITICAL --exit-code 1 --ignore-unfixed --skip-dirs 'docs,.planning,.github,frontends/**/node_modules,**/build,**/dist' --format table
fs_status=$?
trivy fs . --severity HIGH,CRITICAL --exit-code 0 --ignore-unfixed --skip-dirs 'docs,.planning,.github,frontends/**/node_modules,**/build,**/dist' --format sarif --output "$RCT_LOCAL_RELEASE_AUDIT/trivy-fs.sarif"
sarif_status=$?
trivy config . --severity HIGH,CRITICAL --exit-code 1 --skip-dirs docs,.planning --ignorefile .trivyignore.yaml --format sarif --output "$RCT_LOCAL_RELEASE_AUDIT/trivy-config.sarif"
config_status=$?
set -e
test "$fs_status" -eq 0 && test "$sarif_status" -eq 0 && test "$config_status" -eq 0
