# R5 summary

Status: `SOURCE_AND_PURE_CHECKS_PASS_BUNDLED4_PENDING_SOL_REVIEW`  
Base: `b8220ac92125a8afa37598b270aa4fab7aa1f470`  
Branch: `codex/v2-requests-contract`  
Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-requests-contract`

Implemented the bounded R5 corrections for R1 findings 1 and 4. Versioned
production/E2E templates now carry explicit non-secret private network inputs;
the validator enforces canonical RFC1918 CIDR and exact usable Nginx host
membership; CI and documented Compose callers pass their env files explicitly
and run a clean E2E config gate. `StudentApi.submitExcuse` now marks the
generated multipart request body required at operation level while preserving
the inferred required `request` part, and `OpenApiSnapshotIT` guards the
emitted top-level required request body.

The first granted H3 canonical snapshot command exited 1 because the unquoted
dotted `-Popenapi.snapshot.update=true` was parsed as a task. A fresh H4 native
argument-array invocation fixed that parsing issue, then exited 1 while
compiling the unrelated attendance API contract because
`ru.rutcampustrack.shared.web.api.exception.ErrorResponse` was missing (49
compiler errors). H10 used the same native argument array in the narrow
escalated context; compilation passed, the integrationTest task ran and wrote
the canonical OpenAPI snapshot, then Gradle exited 1 because its problems
report destination already existed. H10 was released without retry. H11
verified `--no-problems-report`, then completed canonical snapshot update and
normal assertion, offline frontend generation and guard, and synthetic Compose
config. Generated artifacts were not hand-edited. The required independent Sol
recheck remains the next gate. Canonical artifact hashes are recorded in
`runtime-evidence.json`; the TS artifact now makes `submitExcuse.requestBody`
required while retaining optional `files`.
Light-lane Git Bash syntax passed (0), shellcheck was unavailable (1), and
synthetic validator acceptance/negative fixtures returned the expected 0/3
script codes. H11 Gradle/npm/Compose checks passed with exit 0; the source diff
whitespace check passed with exit code 0. No production env or secrets were
read.

Bundled2 then corrected the four Compose callers in
`nginx/scripts/init-letsencrypt.sh` to use one quoted Bash argument array with
the explicit `.env.prod` input. Git Bash syntax and the corrected literal
caller assertion passed with exit 0; the initial regex probe exited 1 because
its pattern did not cover array syntax and was recorded as a harness-only
correction. The script, certbot, openssl, nginx and production Compose remain
unexecuted. H16 synthetic production Compose config is recorded as historical exit 0 with the isolated fixture.
A commit could not be staged because the worktree Git index lock was permission
denied (exit 128); no escalation or bypass was attempted, and the stable
uncommitted diff remains available for root integration.

No R4 UI files or unrelated role/import paths were changed. No product,
contract or scope delta was needed; any later generation/check failure should
be recorded by main as a new evidence packet before correction.

## Bundled4 status

The source extension is stable and pure checks pass. F1 preflight now preserves
the direct Compose config exit: isolated mock exit 0 produced preflight exit 0,
and mock exit 17 without an error keyword produced preflight exit 4 while
reporting exit 17. F2 static inventory found 35 Compose occurrences across the
seven named script/document paths, all with explicit --env-file tuples. F3
syntax and the validator matrix passed: the documented pair returned 0 and
nine malformed/private boundary cases, including trailing-dot IP and CIDR,
returned 3. H16 synthetic production Compose config is historical exit 0 with
fixture SHA-256
1B5EC4E173079019AB38A6ADFD4185FD8EA4B1255B851160E5055C5A7206525E and is
resolved in the checks record.

The next gate is parent integration and a fresh independent Sol review.
Shellcheck remains unavailable (exit 1; no installation attempted). No real
preflight, production Docker config after this source-only extension, startup,
bootstrap, TLS, deployment or provisioning ran. The worktree remains
uncommitted for parent integration.