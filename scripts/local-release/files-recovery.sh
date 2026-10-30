set -euo pipefail
umask 077
export RCT_FILES_RECOVERY_DIR="$RCT_LOCAL_RELEASE_AUDIT/files-recovery"
export RCT_FILES_RECOVERY_PROJECT="rct-recovery-files-$RCT_LOCAL_RELEASE_NONCE"
export RCT_RECOVERY_PG_IMAGE=postgres@sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d
export RCT_RECOVERY_MONGO_IMAGE=mongo@sha256:1f995ad6fdb93244a1addab1b58f934a0bc2f5643c38e02f5e9d7f0c7d227a7b
cleanup() {
  original=$?
  trap - EXIT
  cleanup_status=0
  if [ -f "$RCT_FILES_RECOVERY_DIR/owned-projects.json" ]; then
    python3 scripts/test-recovery.py --work-dir "$RCT_FILES_RECOVERY_DIR" --project-base "$RCT_FILES_RECOVERY_PROJECT" --source-revision "$RCT_LOCAL_RELEASE_REVISION" --nonce "$RCT_LOCAL_RELEASE_NONCE" --cleanup || cleanup_status=$?
  fi
  printf '{"nativeExit":%d,"cleanupExit":%d}\n' "$original" "$cleanup_status" > "$RCT_LOCAL_RELEASE_AUDIT/files-recovery-exits.safe.json"
  if [ "$cleanup_status" -ne 0 ]; then exit 99; fi
  exit "$original"
}
trap cleanup EXIT
docker pull "$RCT_RECOVERY_PG_IMAGE"
docker pull "$RCT_RECOVERY_MONGO_IMAGE"
python3 scripts/test-recovery.py --work-dir "$RCT_FILES_RECOVERY_DIR" --project-base "$RCT_FILES_RECOVERY_PROJECT" --source-revision "$RCT_LOCAL_RELEASE_REVISION" --nonce "$RCT_LOCAL_RELEASE_NONCE" --pg-image "$RCT_RECOVERY_PG_IMAGE" --mongo-image "$RCT_RECOVERY_MONGO_IMAGE"
