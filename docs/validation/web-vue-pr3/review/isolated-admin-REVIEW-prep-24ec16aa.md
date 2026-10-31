# Independent review — fresh admin lane PREP

Frozen package: `24ec16aa93498b6e62a2e16f4acb90cda50f1f64303bbbf96100d2d2ee9acb09`, `/workspace/rct-pr3-runtime/admin-isolated-20261024`.

Verdict: **materialize-only design is acceptable after the known PLAN authority/enum wording is corrected and a new exact phase GO is issued; CHANGES REQUIRED before any infrastructure/application launch**. There is no requirement to implement the future launcher before materialization. Materialization itself does not call Docker, a DB, another lane or an application: it creates fresh files/keys and copies/explodes pinned public artifacts under its own fixed path. This review does not issue permission, materialize anything or establish startup PASS.

## Grouped findings

### P2 — public PostgreSQL init script is not readable by the initialization account

Locations: `compose.yml:10`; current `init-roles.sh` filesystem metadata; `materialize.py:17–60`; PLAN.md:41.

The bind-mounted public init script is currently mode0600, owner/group1000:1000. The Compose service uses the official `postgres:16` entrypoint with no user override. Official PostgreSQL initialization runs its init scripts after switching to the postgres account; owner-only1000 permissions do not make this script readable there. Materialization never changes this existing public file's mode, despite PLAN promising public init0644. Generated secret files are correctly0444; that does not fix the init script.

A later fresh PG startup can fail while processing `/docker-entrypoint-initdb.d/10-rct-roles.sh`, before the split Auth/Academic roles/databases are provisioned. This is a **future infra phase blocker, not a reason to require Docker launch before materialize**. Preserve byte content and explicitly make the public copied script readable, as already intended (0644), and verify its final mode/readability for the actual image init UID before launch. Do not repair this by running PostgreSQL as the application user or by weakening the role guards. Any failed initial volume must remain a reported partial state, without automatic restart/reset guessing.

### P2 — Compose interpolation can silently prefer inherited host values over the fresh lane file

Locations: `ops.py:57–59`; `materialize.py:35–40`; PLAN.md:37/42–43.

`compose()` passes `--env-file private/lane.env` but leaves the child environment inherited. Its wrapper unsets Docker endpoint selector variables only. Docker Compose gives an inherited process variable precedence over an env-file interpolation value. Consequently a caller with, for example, `REDIS_PASSWORD`, `INTERNAL_ISSUER_SECRET`, `GRPC_SECRET`, `LANE_PRIVATE` or `LANE_ROOT` already exported can override the newly generated lane value or mount location. The code comment that all expansion comes from the new owned material is not true for that input.

No current secret reuse was observed or inspected. The defect is the deterministic precedence for that caller state, which violates the new-realm-only/fresh-credential boundary. This affects future config/startup calls, not the current materialize implementation. Before phase launch, construct a child environment that removes all Compose interpolation keys (or explicitly supplies the canonical lane values) while preserving managed proxy/CA/DOCKER_CONFIG inputs and the explicit local Docker endpoint rules. A pure hostile-synthetic inherited-variable probe can prove the isolation without printing credentials or contacting another lane. Do not clear the whole process environment or copy main secret files to solve it.

### Known PLAN corrections — authoritative scope and capability value

- PLAN.md:3 must not extend QENV004 into a general stand/deletion authorization. QENV004 covered the earlier precise local isolated checks/build. The fresh synthetic lane is authorized under the current Goal and still needs the new exact phase-specific Root GO; QENV005 main stop/snapshot scope is not reused.
- PLAN.md:19 says `WHOLE_PLAN deletionScope`; the actual accepted response capability is **ALL_VERSIONS**, with CURRENT_FORMAT for explicit png/svg. Require the real response field and matching whole-history counts/digest; do not relax the client gate to match the mistaken text. Normal upload may create its own owned inventory; do not require an invented alternate provisioning operation.

These two known document corrections must be folded into the re-frozen packet. They are not new business questions.

### Gateway provenance qualification — no rebuild demanded

Actual pinned593f47 archive hash matches artifacts.json, but its embedded `git.commit.id` is60346b26b, while artifacts.json attributes source3c02a3241. Root supplied the historical pre-final-commit build qualification: final Java was built before generated-contract/final-commit writes; the narrow603..3c02 Gateway product delta concerns the device-preferences bootstrap allowlist. Record the distinction honestly in the new lane artifact qualification: embedded revision603; attributed precommit source-equivalence3c02 based on its existing receipt (and a narrow exact593 bytecode check if needed). Do not rewrite old immutable receipts or claim independently source-matched metadata3c02. No new Gateway runtime defect was established, and this does not require rebuilding an already pinned accepted artifact for these admin routes.

## Accepted preparation boundaries

- Recomputed package hash equals exact24ec16aa. Both Python files parse; every listed source file hash and all six listed artifact hashes match. Auth embedded sourcef3 and Academic embedded source1fe match their records. Package source files were read without executing their functions or changing them.
- Pure YAML checks confirm seven services,3392MiB/1.65CPU total caps, no image builds, pull_policy never on every service, no privileged/host-network mode, no external networks/volumes, no main/clock bind path and exactly one external teaching-clock mount, on Academic. Immutable Auth/Gateway symlink mounts avoid duplicate JAR materialization; Academic is exploded from its exact hash after archive traversal checks. Final phase must still verify the actual local image IDs/config/CIDs/labels/mounts; this review did not invoke Docker.
- `ops.py` has separate exact package/project/scope/expiry/mode0600 phase admission and excludes main stop/data copy. Its current infra-up/apps-up paths reject deliberately. Preflight checks existing main13/clock5 health/projections, fresh namespace/ports/subnet and live resource limits. A future phase-specific launcher is still required before infra/apps startup, and must handle already-created own resources rather than calling the fresh-only preflight unchanged. This is an acknowledged future gate, not a materialize prerequisite.
- Fresh public callbacks are atomic, require the explicit disposable marker, correct separate database/migrator and empty unmarked state; replay preserves the immutable baseline and does not reset later mutations. Academic does not fabricate Auth catalog authority: its real directed TLS client must consume the final Auth catalog. The fixture is legitimate synthetic initial input, not an API-created main actor or copied main authority.
- Fresh key filenames match Auth JwtService. Auth/Academic TLS leaf SANs/authority names and trust-file wiring are consistent with their pinned production configuration. Directed token file sources are paired across the two services; current-password confirmation goes through the actual Auth controller/service with current live session/ADMIN access and BCrypt check. HTTP internal issuer and JWT keys are new inputs; no fake identity headers, session or password bypass is proposed.
- Accepted external ClockInitializer registers PRIMARY EducationalTime, not Clock; explicit service opt-in/absolute bounded instant/marker are required. Only Academic receives the fixture classpath and mount. Auth/Gateway do not receive it, and application identities outside the allowed educational set are rejected. Security/session/JWT/TLS/OS time must remain real. No browser Date pin or response interception is proposed.
- The strict Node/Vite TLS path preserves inherited public CA trust and adds only this new CA, secure:true; browser-facing loopbackHTTP still needs actual isSecureContext/cookie/Origin acceptance before the user action. No global CA/NSS changes or TLS bypass. Unsupported absent-service panels remain honest failures, not mocks.

## Resource observations and launch limits

Pure host reads during this review observed cpu.max400000/100000 (4CPU) and4541800448 free bytes (~4.23GiB); private/runtime directories were absent. Frozen author preflight-final records main13/clock5 healthy, anon5440471040 bytes,16GiB memory limit, new full caps3556769792 bytes and1.5GiB reserve, below90%. These live health/image/socket facts are **author evidence, not independent Docker verification**. The disk estimate plus4GiB initial/2GiB remaining guards is tight but supports bounded fresh preparation; repeat immediately per admitted phase, keep old18 untouched, no new image build/pull or global cleanup. The future launcher must enforce the post-step reserve and bounded logs/assets before proceeding. No permission to stop main/clock is inferred if capacity fails.

## Pure verification and explicit remaining gates

Executed only local text/AST/YAML/JSON/hash/archive/filesystem reads plus managed environment status (current/enforced policy; no credential values). Native pure checks passed for exact package/source/artifact pins and topology/cap totals. No Docker socket/up/config command, Gradle, browser, HTTP/API, DB, key generation, materialization or private-secret read was executed by this reviewer. Only this report was written.

Root reports the fresh offline fixture/control source build now passed44s/four tests and the accepted fixture hash remained unchanged; the first socket EPERM attempt was retained. Those later source-control results do not substitute for the still-pending actual1fe production-classpath typed/mixed security control after materialize. No actual app/bootstrap/catalog/TLS/clock/password or admin action PASS is claimed. Require the completed actual-classpath control and newly reviewed bounded launcher/materialized config before infra/apps phase GO; require real seven-service readiness and current-password proof before separate browser case GO.

Requested assignment remains GPT-6.1 Sol/high. No runtime model telemetry or silent fallback claim is made.
