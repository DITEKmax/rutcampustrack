# Ж: явный DB-only recovery, 2026-10-02

## Compact contract
Goal: оператор создаёт и восстанавливает backup фактического backend без искусственного пустого files directory.
Context/evidence: main9aaf5889; prior fffb9337 full-files drill independently reviewed/PASS; source reading proves request/journal Binary in attendance_db and map BYTEA in academic_db, Homework stores text/link. No additional persistent user filesystem store exists in current implementation.
Relevant scope: assigned pwa-install-delivery-20260923 worktree, safe normal merge main9aaf5889 ->5135540f; sole author zh_server_recovery_1002 Sol6.1high. Product inventory only scripts/recovery.py, scripts/preflight-deploy.sh, docs/operations/runbooks/backup-restore.md. Own evidence only. Foreign RULES/LEAF/source.diff and synthetic artifacts preserved.
Required behavior: explicit mutuallyexclusive --no-files/--files-dir; manifest files_mode; reject mismatch/unknown/hiddenfiles before credentials or target writes; legacy format1 file bundles supported with files mode. Hash, archive, label, fresh-target and failure safeguards retained.
Constraints: no deploy/realdata/backup deletion/secrets reads/children; no new storage/topology validator; runtime only after root lease + independent stable review. RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA.
Existing patterns: stdlib recovery, immutable payload inventory, explicit target project, quiesced writers.
Acceptance: DB-only roundtrip preserves PG/Mongo including Binary; wrong mode refused before target writes; no filesystem payload directory created; old files supported; preflight uses actual Python/recovery dependency and no obsolete GPG warning.
Verification: S2 static/negative checks now; minimal new no-files synthetic runtime only after root lease, prior full-files drill not repeated. Independent review pending.
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
Source-ready; independent review and minimal authorized DB-only roundtrip pending. Existing full-files recovery acceptance unchanged. No owned runtime processes/containers/resources; no heavy lease requested/held yet. No production-ready claim.
