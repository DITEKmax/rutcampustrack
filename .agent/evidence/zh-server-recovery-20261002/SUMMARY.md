# Ж — PG/Mongo/files recovery, 2026-10-02

## Contract
Goal: оператор получает повторяемый backup и доказательство точного восстановления PG/Mongo/files в disposable isolated targets. Risk S3 (потеря данных). Writer: zh_server_recovery_1002, runtime gpt-6.1-sol/high; children none. Worktree: pwa-install-delivery-20260923, initial b5fffad1, normal merge accepted main ab6888c0 -> e3a668b8. Foreign dirty RULES/LEAF-PACKET/source.diff preserved. RULES A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA; owner GO 2026-10-02 from root.

Required behavior: explicit containers/credentials/files/output; operator quiescence; immutable complete bundle + SHA256; no default production target/deletion/retention; restore accepts positively labeled explicit fresh disposable targets; exact PG data/schema/sequences, both Mongo documents/indexes/options, file paths/bytes; nonzero failures. Existing backup/restore wrappers and runbook reused. No services/proto/frontend/common stand/global config/secrets reads. Runtime only after root exact heavy lease, cleanup only exact disposable targets after authorization. Acceptance: exact synthetic drill plus negative guards, independent review and integration by root. Runtime not yet executed; source-ready only.

## Changed inventory
Created: scripts/recovery.py (stdlib operator path), scripts/test-recovery.py (synthetic drill driver), this evidence.
Modified: scripts/backup.sh, scripts/restore.sh, scripts/test-restore.sh (thin forwarding wrappers), docker-compose.test-restore.yml (unique Compose scope, labels, tmpfs, limits, no host ports), docs/operations/runbooks/backup-restore.md (operator inputs/quiescence/rollback/limits), infra/cron/rutcampustrack-backup (inactive template until authorized quiescing wrapper).
Deleted: none. Foreign WIP not staged.

## Checks
PASS python -m py_compile scripts/recovery.py scripts/test-recovery.py.
PASS Git Bash bash -n scripts/backup.sh scripts/restore.sh scripts/test-restore.sh.
PASS Docker Compose config --quiet with synthetic environment and --env-file NUL (no daemon mutation).
PASS backup --dry-run and test-recovery --dry-run; no containers/resources created.
PASS pure stdlib bundle integrity check; corrupt payload refused; tar path ../outside refused before extraction. Retained synthetic artifacts: .agent/recovery-zh1002-static.
PASS --cleanup --dry-run refusal (exit1 before reading marker or accessing Docker).
PASS git diff --check.
Readonly Docker image inventory: cached postgres:16 / mongo:7.0 present. No pull/build/run.

## Pending runtime plan
New .agent/recovery-zh1002-run; project-base rct-recovery-zh1002, projects -src/-dst; PG16 + official Mongo7.0 cached images; max3 active DB containers (256m+256m+512m), no ports, tmpfs, project-scoped labeled network. Source stopped before target. New synthetic rows/indexes/sequences, two Mongo DBs, Unicode/binary/nested/empty-dir files. Negative overwrite/corruption/project/nonempty checks. Artifacts/target retained. Separate cleanup command validates marker, all container/network labels, absence of persistent volumes; deletes only these projects, retains backup/files.

## Limits
No production deploy/restore/migration executed. Prod files mount unspecified: production recovery remains blocked until all actual persistent stores are wired and covered. No offsite job/encryption/secret recovery or RPO/RTO claim. Quiescence is an explicit operator prerequisite, not a global snapshot mechanism. File ownership/mode/mtime restored separately by operator. Source/target database tools must have compatible versions; PG16 baseline preserved. Mongo views unsupported. Production activation/cleanup of existing data requires separate approval.
