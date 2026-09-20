# Guarded local main fast-forward: preparation contract

## 1. Goal

Подготовить reviewable exact-file backup и guarded PowerShell 7 script для
локального fast-forward main на явно переданный FinalSHA. В текущем ходе
выполняются только preparation и read-only preflight; merge/switch не запускаются.

## 2. Context / evidence

- Root: C:\Users\maksd\IntelliJIDEA\rutcampustrack.
- Текущий root checkout: branch codex/materials-transfer, HEAD =
  87784165874e2da6fc261abc1c01584e24624289.
- Local main существует и указывает на тот же base; local master отсутствует.
- Active integration worktree:
  .agent/worktrees/js-student-01/integration, current HEAD =
  1f73c1ec81186ed7dd0cf22451ff7282e09dea5d; earlier observed
  623460ef03a8ff33264acd2d4a98d908a8f86b0e and requested survey SHA
  f85a4d27b9093bad6d60096552f306e8f3e2222b также доступны.
- Existing tracked dirty state: modified docs/INDEX.md and deleted
  skills-lock.json; staged diff отсутствует.
- Read-only survey for current integration HEAD found 76 incoming leaf
  collisions: 72 byte-identical and 4 different. The four different paths are
  AGENTS.md, docs/agent-workflow.md,
  .agent/vertical-js-student-01/be-progress.json, and
  docs/implementation/parallel-development.md.
- Root AGENTS.md and docs/agent-workflow.md are untracked, differ from the
  integration copies, and have later UTC write times. Their bytes remain the
  owner copy and are reported as instruction conflicts.
- The earlier reported 76 = 58 identical + 18 different is not reproduced by
  the current byte comparison (76 = 72 + 4); the delta is recorded rather than
  silently normalized.

## 3. Relevant scope

Only .agent/js-student-01-main-merge/ is writable for this task:

- guarded-main-ff-merge.ps1
- contract.md
- evidence.md
- checks.json
- summary.md

The source integration worktree, product code, root instructions, existing
documents, Git refs, and runtime state are read-only inputs.

## 4. Required behavior

guarded-main-ff-merge.ps1 requires explicit FinalSHA, ExpectedRootHEAD, and
ExpectedBranch; it defaults to dry-run. Before any apply mutation it verifies
repository top-level, current branch and HEAD, local main base, commit existence
and ancestry, no Git operation in progress, no staged changes, no
tracked-dirty overlap with incoming paths, and safe leaf paths.

The preflight computes every incoming leaf collision with a non-tracked file
already at that exact root path. For each collision the apply path copies the
owner bytes to a new owner-copy backup, records owner SHA-256, size, Git blob,
incoming Git blob, and classification in a new manifest, and verifies the copy
before moving any owner path. It uses exact leaf operations only; no recursive
move/delete, stash, clean, reset, or worktree metadata operation is used.

Only an explicit Apply switch may switch to local main and run git merge
--ff-only. After the fast-forward, every quarantined owner file is restored to
its original path and SHA-256-verified; every pre-existing tracked-dirty file
is checked against its recorded present/absent snapshot; the resulting main
HEAD must equal FinalSHA. Instruction-file conflicts require the additional
explicit AllowInstructionConflicts switch after root resolves them.

## 5. Constraints

- Do not execute merge, branch switch, move, delete, stash, reset, clean, or
  worktree operations during preparation.
- Do not touch integration files, product files, global config, existing docs,
  or unrelated .agent artifacts.
- Do not read or print secret contents. Sensitive paths are reported by address
  and skipped for content inspection.
- Preserve docs/INDEX.md bytes and the deleted state of skills-lock.json.
- Keep the script local to the intended root and reject reparse points/path
  escapes.
- The actual local merge remains separately gated after independent Sol review.

## 6. Existing patterns

The implementation follows the project PowerShell 7 migration/routing
playbooks: SHA-256 via Get-FileHash, Git object checks through
--no-optional-locks, exclusive manifest creation, and explicit evidence with
revision/command/exit code/environment. Existing owner artifacts are treated as
untracked work and are never bulk-copied or recursively moved.

## 7. Acceptance criteria

1. All preparation artifacts are confined to the assigned directory.
2. PowerShell parser reports zero syntax errors.
3. Dry-run on the frozen root reports root/branch/main stability, zero staged
   changes, zero dirty incoming overlap, and the bounded 76-collision survey.
4. The script has a dry-run default and an explicit apply gate; apply backs up
   each owner leaf plus SHA-256 manifest before any mutation.
5. Restore verification, tracked-dirty verification, and final main SHA
   verification are present by construction; no apply was executed in this task.
6. Existing tracked dirty docs/INDEX.md and deleted skills-lock.json are
   recorded and preserved by the no-overlap guard.
7. Root/integration instruction conflict is recorded for root resolution.

## 8. Verification

Run the PowerShell AST parser and the default dry-run with the exact frozen base
and candidate SHA. Recheck Git status/diff and the bounded collision survey
without reading secrets. Record command, exit code, environment, revision, and
evidence in checks.json. Product runtime is N/A: this scope prepares a local
Git operation and changes no product behavior. Actual Apply is not an accepted
preparation check and remains pending independent Sol PASS plus root execution.

## 9. Do not

Do not perform the local merge now, do not switch branches now, do not alter
main/master refs, do not overwrite newer root instructions, do not commit, do
not modify integration or product code, and do not redesign the contract.
