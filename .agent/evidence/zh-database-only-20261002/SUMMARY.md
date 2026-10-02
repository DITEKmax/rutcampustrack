# Ж: явный DB-only recovery, 2026-10-02

## Compact contract
Goal: оператор создаёт и восстанавливает backup фактического backend без искусственного пустого files directory.
Context/evidence: main9aaf5889; prior fffb9337 full-files drill independently reviewed/PASS; source reading proves request/journal Binary in attendance_db and map BYTEA in academic_db, Homework stores text/link. No additional persistent user filesystem store exists in current implementation.
Relevant scope: assigned pwa-install-delivery-20260923 worktree, safe normal merge main9aaf5889 ->5135540f; sole author zh_server_recovery_1002 Sol6.1high. Product inventory only scripts/recovery.py, scripts/preflight-deploy.sh, docs/operations/runbooks/backup-restore.md. Own evidence only. Foreign RULES/LEAF/source.diff and synthetic artifacts preserved.
Required behavior: explicit mutuallyexclusive --no-files/--files-dir; manifest files_mode; reject mismatch/unknown/hiddenfiles before credentials or target writes; legacy format1 file bundles supported with files mode. Hash, archive, label, fresh-target and failure safeguards retained.
Constraints: no deploy/realdata/backup deletion/secrets reads/children; no new storage/topology validator; runtime only after root lease + independent stable review. RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA.
Existing patterns: stdlib recovery, immutable payload inventory, explicit target project, quiesced writers.
Acceptance: DB-only roundtrip preserves PG/Mongo including Binary; wrong mode refused before target writes; no filesystem payload directory created; old files supported; preflight uses actual Python/recovery dependency and no obsolete GPG warning.
Verification: S2 static/negative checks now; minimal new no-files synthetic runtime only after root lease, prior full-files drill not repeated. Independent review PASS86e35277, no blockers; root granted exact minimal synthetic lease afterward.
Do not: no services/proto/frontend/compose/secret changes; no additional framework/infrastructure project/full suite.

## Inventory
Modified scripts/recovery.py: explicit files mode + manifest marker, old bundle fallback files, DB-only requires empty inventory/tar, mode guard before target access, no files directory for database-only.
Modified scripts/preflight-deploy.sh: require recovery.py and Python3.9+; remove obsolete .backup-passphrase warning. Existing checks retained.
Modified docs/operations/runbooks/backup-restore.md: actual DB attachment/map/homework stores and named volumes, JWT/TLS/Redis/Rabbit/monitoring limits; remove unsupported missing-attachment-mount blocker; show explicit --no-files/current-backend and file mode alternative. Offsite/secrets/queue recovery limits remain.
Created this summary + unchanged static logs; no product files deleted.

## Checks
PASS Python compile, Bash syntax and git diff --check; no application/containers run.
PASS CLI exactly one mode (missing/both exit2); no output created.
PASS database-only dry-run no files directory; legacy format1 files accepted.
PASS no-files over legacy files and empty legacyfiles; files over DB-only; unknownmode and declaredfiles in DB-only rejected before any credential/Docker/target access.
Second static-2 validates same negative cases after consolidating mode check into one manifest read; no repeated product runtime.
PASS runbook source links. Logs static-1.log/static-2.log; retained synthetic static bundle copies .agent/zh-no-files-static outside commit.

## State
Reviewed source86e35277 unchanged. Minimal authorized DB-only roundtrip PASS; exact owned cleanup PASS. Existing full-files recovery acceptance unchanged and was not repeated. No owned runtime processes/containers/resources/children; one heavy lease released. Integration remains root responsibility. No production-ready claim.

## Runtime result and bounded fixture correction
Exact approved new workdir .agent/recovery-zh1002-no-files and projects rct-recovery-zh1002-no-files-src/-dst; preflight absent/EMPTY confirmed. Cached postgres:16/mongo:7.0, tmpfs DB, no ports, max3 active; source stopped before target. Tiny PG BYTEA fixture in both DBs, BSON Binary fixture in both Mongo DBs. Source revision86e35277, product code unchanged after review.
Initial one-off evidence driver run-nofiles.py/session55403 exit1 failed at Mongo fixture CLI before any bundle/target creation. PostgreSQL fixtures already existed, source three healthy/OOMfalse, Mongo docs0. Diagnostics: constructor/syntax/read-query PASS; exact fixture wrapped in try succeeded, both Binary docs inserted. Original stderr intentionally suppressed by recovery wrapper, so underlying initial CLI reason is not established; no claim of a diagnosed product defect. Root allowed bounded fixture correction/continuation without repeated healthy startup.
Resume-nofiles.py/session52067 continued only from source backup stage: PASS exit0. Exact PG data/schema/sequences + BYTEA, both Mongo documents/indexes/options + Binary matched restored source. Explicit files mode over database-only bundle refused before target write; no source/target filesystem payload directory created. PASS.json receipt retained/copied.
Separate approved cleanup: python scripts/test-recovery.py --work-dir .agent/recovery-zh1002-no-files --project-base rct-recovery-zh1002-no-files --cleanup. PASS exit0; final exact src/dst containers/networks/volumes EMPTY; bundle/artifacts retained, no existingdata/backup/deploy touched. Heavy lease released before evidence work. No old fullfiles rerun, unrelated checks or product fixes.
Logs runtime-1.log/runtime-2.log, diagnostic-1..4.log, cleanup-1.log are unchanged. One-off driver source retained for audit in this evidence directory, not a new reusable product harness. Bundle/synthetic credential artifacts remain outside commit in assigned workdir.
