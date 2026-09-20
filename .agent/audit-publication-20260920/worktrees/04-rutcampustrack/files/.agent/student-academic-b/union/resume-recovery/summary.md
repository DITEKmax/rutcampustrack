# Source recovery summary

Status: FROZEN_PASS

Scope: exact d3 revision `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`; 39 accepted non-overlap destinations (18 M + 21 A). The three d3/dependency overlaps remain dependency-winner bytes. Import ordering is recorded as a late guarded recovery; no original sequence claim is made.

Criteria: exact39=PASS; snapshots81=PASS; overlapWinners=PASS; effective42=PASS; foreignScope=PASS; productScope=PASS; trackedDiff=PASS; frozenSources=PASS; preservationSentinels=PASS.

Evidence: packet, preflight, mutation, manifest, checks in this directory. The exact per-path Git object IDs, source/raw SHA256 values, raw before/after values, statuses, 81 snapshot hashes, 3 overlap hashes, four SQL WIP hashes, and five numeric accepted hashes are in `manifest.json`.

Checks: mechanical checks record commands and exit codes in `checks.json`. A verification-evidence literal was corrected after reproducing its one-character SHA typo; this changed evidence files only. Product runtime is N/A by contract for source recovery. The final verification command exited 0 only when every mechanical criterion above passed.

Diff: tracked d3 scope is represented by 18 modified entries from `git diff --name-status -- <39 accepted d3 paths>`; the 21 additions are captured through `git status --porcelain=v1 --untracked-files=all` and exact manifest rows. No status changed outside the 39 product paths and this evidence directory.

Limitations: this leaf performed no product runtime/Gradle/npm/container/SQL/DTO/UI work and does not claim B0/full-role PASS. Root must accept this frozen source layer before the next fresh packet.
