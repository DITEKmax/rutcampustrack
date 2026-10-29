set -euo pipefail
umask 077
metadata_dir="$(mktemp -d "$RUNNER_TEMP/gitleaks-public-object-preparation.XXXXXX")"
export RCT_GITLEAKS_METADATA_CONFIG="$metadata_dir/config.toml"
python3 scripts/gitleaks_metadata.py --prepare-public-objects
