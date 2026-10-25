# Targeted independent recheck — CHANGES REQUIRED (one remaining boundary)

Commit `4b9c086798f83062f43d5428dee413224a0e8d77`, after `7f85860ef`, clean assigned `group-final-delete` worktree. Requested reviewer model/effort unchanged: `gpt-6.1-sol/high`; telemetry unavailable/no fallback. Only the correction diff and essential affected admission/ownership/startup/isolation boundaries were read. Previous a23 cohort semantics review reused. No source edits or runtime/Auth/DB/Docker/browser/build/CI/full-suite actions.

## Mapping

| Previous finding | Recheck |
| --- | --- |
| P1 existing private mkdir/EEXIST | **CLOSED in source.** `prepared-private.cjs:3` admits the existing exact nonsymlink directory, UID and mode0700. Cohort line121 calls that helper. Original fsynced exclusive intent remains; state is not recreated. Filesystem-only evidence explicitly covers preserved original intent/EEXIST, permissions and symlink. |
| P2 startup/input/source/JAR/image/mount freeze | **CLOSED in source.** `runtime.source():75` starts package/private-input verification; prepare binds immutable private env/config/PKI/keys/baseline hashes to source+nonce. `mount_pins()` records bind/secret/named-volume/RW pins, ownership checks actual service name, nonce, image ID and full mount set. Start/frontends enter through source admission, and API phases require the same12ready IDs plus finite isolation receipt. No secret values are printed. |
| P2 detached readiness reserve/timeout/Ctrl-C | **Original paths corrected; remaining termination boundary below.** Compose/readiness/isolation and final ready now share `start()` try/closure; reserve/timeout/KeyboardInterrupt cause qualified exact-ID stop with intent/receipt and retained volumes. Partial known startup can stop without a ready milestone or DB; unknown fixture/original case remains HOLD. |

## Remaining P2 — Parent SIGTERM bypasses owned child/startup closure

**Path/line:** `tests/e2e/pr3-local/local.py:190`, `:197`, `:228` (entrypoint `:305`).

The requested essential boundary includes SIGTERM. The launcher creates each child with `start_new_session=True` but installs no SIGTERM handler. `guarded()` catches only KeyboardInterrupt. A SIGTERM delivered to the Python launcher uses Python's default process termination, so neither that catch nor `start()`'s BaseException handler runs; the independently grouped compose/readiness/isolation child and detached owned containers can survive the launcher without the promised qualified stop/closure receipt. The fake interrupt controls raise KeyboardInterrupt and therefore do not establish this path. A generic exception while polling resources also bypasses `guarded()` child reaping, even though the outer startup closure may stop the currently observed containers.

**Required narrow correction:** make orderly parent termination enter the existing interruption/unknown handling and ensure the exact spawned child group is terminated/reaped on every guarded exit before qualified startup closure. Keep metadata qualification and unknown-HOLD rules; do not kill unqualified IDs or add deletion/retry behavior. Add a pure fake/handler control for the installed termination handler and child closure; no real process signal or owner-local execution is required to close source review.

## Other affected boundaries

TTY admission now refuses noninteractive getpass fallback before password entry. Finite Schedule own CRUD rollback/cross-DB CONNECT/DDL/TRUNCATE denial and distinct Mongo-principal own CRUD abort/cross-DB denial are ported from preserved180f; password enters the owned PostgreSQL process via stdin. Those probes remain source PREP/NOT_RUN. No a23 original UUID/one confirmation/status-only/physical WS/hint causal boundary/passive six views/focus/draft/Auth/SID cleanup assertion was weakened (cohort changed only its directory admission line).

Author-provided targeted fake/filesystem/syntax results are reused; they are not a boot or owner-local PASS. First CHANGES REQUIRED evidence and intermediate harness failures remain documented. Owner-local six-view gate stays **NOT_RUN until actual owner output review**; cloud execution stays cancelled. One remaining source correction and targeted recheck are required before source PASS.
