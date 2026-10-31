# Independent portable local group review — CHANGES REQUIRED

Commit `7f85860ef`, branch `codex/pr3-local-group-handoff`, worktree `/workspace/rct-worktrees/group-final-delete`. Reviewer independent of author; requested `gpt-6.1-sol/high`, telemetry unavailable/no fallback. Scope: 20 new files only under `tests/e2e/pr3-local/` (2,900 lines), read-only source/contracts review. Product, Docker/Auth/DB/browser/build/CI/owner-local execution: **0**.

Owner steering applied: full six-view group UI gate is owner-local Ubuntu/WSL **NOT_RUN**. Cloud group gate is cancelled; this review neither requests nor executes it. Existing old local checkout/activate/resources must remain untouched. Three findings below form one correction packet for the existing writer.

## Findings

### P1 — Every prepared case stops with EEXIST before opening the browser

**Path/line:** `tests/e2e/pr3-local/runtime/native-cohort.cjs:121` (local adaptation `V8 = ROOT` at line8); `tests/e2e/pr3-local/local.py:80`.

`prepare` necessarily creates `ROOT/private`, writes the generated config/state/secrets there, and fixture adds its durable state there. The cohort now shares that same ROOT/private directory but still calls `fs.mkdirSync(PRIVATE, { mode: 0o700 })` without `recursive`. Existing-directory mkdir throws EEXIST on every normal documented `case` invocation. This is before the intent/browser try block, so neither six passive views nor the deletion case can run, and only the generic pre-initialization STOP appears. The old two-package driver used a separate private directory; collapsing V8 and ROOT changed this precondition.

**Correction:** admit/check the expected existing nonsymlink private directory and keep its permissions; preserve the exclusive intent-file check as the one-attempt boundary. Do not delete/recreate private state to make mkdir succeed. A small filesystem-only control with a prepared private directory can verify this path without any runtime action.

### P2 — Generated startup/mount inputs are not actually frozen before use

**Path/line:** `tests/e2e/pr3-local/local.py:155`, `:185`, `:191`; `tests/e2e/pr3-local/runtime/runtime.py:59`, `:65`.

The public generated manifest deliberately excludes everything inside PRIVATE, including `private/local.env`. That file carries operational bindings (`LANE_ROOT`, `LANE_PRIVATE`, `GROUP_*_JAR`, UID/GID) as well as secrets. `runtime.source()` verifies the original pinned JAR files and current image tags but never verifies the effective Compose mount bindings/container Image IDs. Additionally, `start`/`frontends` call `source()` but do not call `package_hash()` before using generated compose/frontend configs. Thus an edited generated compose/config can be used by startup before a later fixture admission detects it; an edited `local.env` can point a JAR mount at different bytes while all original JAR hashes and the package hash still pass. The stated exact-source/JAR/frozen local configuration admission does not establish which source is actually running.

**Correction:** check generated-package integrity before startup/source-server launch and bind the generated env/effective mount configuration privately to the prepared nonce/source/JAR pins. Verify actual owned service Image/mount metadata against those pins before API/fixture/delete admission. Do not print config/secret values or expand checks into a new framework.

### P2 — Early startup reserve/timeout exits leave the detached services running

**Path/line:** `tests/e2e/pr3-local/local.py:192`–`:202` (primary line200); guarded stop exists only at `:179`–`:182`.

After `compose up -d` finishes, the launcher waits up to360s for health in a separate loop outside `guarded`. Disk dropping below2.5GiB, an exited service, a readiness deadline, or Ctrl-C in this loop exits to main without stopping the newly detached owned containers. The only exact-ID startup closure runs when the earlier `guarded(... phase=start)` process itself is stopped. On the normal detached-health path the advertised early-stop guard therefore stops monitoring while Java/DB/broker processes remain active and can continue consuming the shared local disk; `stop` then refuses because `services-ready.json` was never written.

**Correction:** cover the whole startup/readiness lifetime with the same bounded guard and exact nonce-qualified container closure on failure/interruption, retaining volumes/keys/logs. Preserve refusal to touch foreign or unqualified IDs. No destructive cleanup or retry is needed. A fake metadata/process control is sufficient to verify readiness failure closure.

## Positive scope / reviewed limits

- Exact new `~/rutcampustrack-pr3` path, local Docker unix socket, sanitized child env, nonce labels, foreign-container metadata baseline, no published DB/broker ports, no global prune/down-v/reset/rekey are explicit. Owned SQL/Mongo fixture IDs are new and narrow; no post-bootstrap Auth SQL role/password writes were introduced.
- Compared the 595-line cohort against preserved reviewed-a23 source: the seven-context/numeric-preview/original UUID/Auth-handle capture/physical WS/personal hint/request-start causal assertions/current-password/unknown HOLD/exact SID cleanup logic is retained. The local co-location adaptation causes finding1; accepted cloud outcomes were not treated as local PASS.
- Read the twelve-service Compose, local secret generation/PKI/TLS proxy, canonical PostgreSQL first-volume init, baseline callbacks, Schedule ACL callback, Mongo distinct principals, calendar API DTO/service paths, normal group/user/subject/Auth fixture paths, and postproof. No further confirmed API/SQL/TLS contract defect was identified by reading; this is not a boot/runtime claim.
- Source contract/README/summary keep owner-local gate NOT_RUN until actual owner output. Root/common Nginx and native Telegram/WebPush provider claims stay separate. COMMIT selected-grant withdrawal with a live SID for another role is preserved; no blanket revocation or unknown-operation re-confirmation was added.

Review result: **CHANGES REQUIRED** for the three source defects; no cloud or owner-local execution requested. After the same writer applies one batch, targeted source re-review can close these findings. Full local UI remains NOT_RUN until the owner runs the corrected pinned package and supplies actual output.
