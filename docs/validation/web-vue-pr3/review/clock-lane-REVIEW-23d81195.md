# Clock startup correction review — 23d81195

Verdict: **PASS for the bounded proposed startup repair**. No confirmed P1/P2 blocker found in the corrective source/plan. This is independent source review, not execution approval, successful recovery or readiness acceptance.

Reviewed current package digest `23d81195e97b61af4861369322f64f49d8bf9bb02f7aa213da1411db098db732` against archived f7, STARTUP-CORRECTION.md, STARTUP-CORRECTION-VALIDATION.json, the recovery script/plan, and changed prepare/compose/wrapper/resource/recipe files. Recomputed the current and archived package digests successfully. Recomputed plan SHA256 exactly `f46848b7f58b6f6515f214716659732a1b0947773f2b8a29f41b4dfff93af765`.

## Exact repair boundary

- Apply requires current exact-package admission, the separate `failedOwnInfraRecoveryApproved=true` and exact plan hash, explicit snapshot=false, absence of snapshot/import/actor/case artifacts, approved images and current budget. It rejects an existing exclusive operation receipt and creates one before mutation, so interrupted partial recovery cannot silently replay.
- The only reset targets are the plan's original exited postgres-academic and mongo-attendance CIDs and their exact new bootstrap volumes. Initial guards check service/project/namespace labels, image selector/id, creation times, exit1/non-OOM state, volume metadata, local driver/options/mountpoint/destination, main-volume exclusion and references from all containers. Before each individual non-forced removal the script rechecks unchanged main/preserved infra and the original target identity/state. A volume gaining a reference is rejected, and engine non-forced removal remains the final in-use protection.
- Thirteen main CIDs/images/sorted mount sets/healthy states must match the plan. Three preserved lane CIDs must retain their image/time/mounts/network names and a full Config/HostConfig fingerprint excluding only the explicitly reviewed memory/CPU fields. The two healthy SchedulePG/Redis remain running and unchanged.
- Rabbit is updated and started as the **same original CID**, admitted initially as exit137/OOM=true. Only its memory768MiB and CPU.25 are changed; its volume is not reset. Unchanged MemorySwap and all other effective settings remain covered by the fingerprint. The intentional stale Compose config-hash is disclosed; infra-up and later separately gated apps-up use `--no-recreate`. Infra-up checks exact corrected identities/config/quotas before and actual healthy readiness afterward.

Independent pure plan checks confirmed eighteen disjoint IDs (13 main +2 failed +3 preserved), exact two failed service names, exact preserved service set, and no overlap between failed volumes, preserved volumes and the five main volume exclusions. These are checks of the frozen plan, not a fresh Docker inventory.

## Corrective inputs and permissions

- prepare_runtime.write now calls fchmod on the opened descriptor, so the requested0600/0444 modes are not reduced by umask077. The two public init scripts are actually0644 at review and byte-identical to archived f7. Their individual read-only mounts permit the container's bootstrap UID to read/source them without exposing private parent directories.
- Existing repair changes only the listed lane auth-secret files0400→0444 after checking owned regular paths and unchanged byte hashes. Private directories must remain0700 and TLS/JWT private keys0600. It does not widen key permissions or rewrite application credentials.
- The replica key replacement is exactly one MONGODB_REPLICA_SET_KEY line, standard Base64 of48 random bytes; all other env lines remain identical. Exclusive temporary creation/0600 and atomic replace are used. New prepare_runtime generation uses the same corrected encoding. No existing private value was read by this reviewer.
- Mongo logging is redirected to the owned persistent `/bitnami/mongodb/startup.log`. The first fatal remains correctly labelled UNPROVEN; invalid Base64 is a separately confirmed prerequisite failure, not claimed proof of the suppressed startup cause.
- Resource declarations independently sum to6976MiB/2.15CPU and match the Rabbit correction. The existing budget guard is still executed before repair/start; it was not weakened. No health-test timeout change, broad cleanup, main restart, snapshot, import, app/browser action, fixture rebuild or new dataset initialization is part of this repair.

## Actual evidence versus proposal

The supplied public correction/validation records report prior f7 native exits: fixture/control0, prepare0, official five-input isolation0, Compose config0, initial infra-up1. They preserve PostgreSQL init permission denial, unproven Mongo first fatal plus separately invalid key, and Rabbit's later137/OOM under the old quota. The new correction and readiness are explicitly unexecuted. Private logs/credentials were not accessed, and no fresh Docker readback was performed here.

After root's separate exact technical repair authorization, actual corrected Compose configuration, exact recovery execution receipt, recreated PG/Mongo bootstrap and all-five infra health must still succeed. Any new Mongo failure must be diagnosed from its private regular log and reported; it is not permission for automatic reset/retry or guard changes. Main snapshot/clone/application/browser/native-GEO stages remain separate later gates, with no approval implied here.

Allowed independent commands were limited to reads, pure hashes/JSON/YAML/resource/identity checks and nine Python AST syntax parses. No recovery function was imported/executed; no Docker, Gradle, browser, network, deletion, update, runtime/private-file write, product edit or child agent was used. Only this immutable report was written. Requested model remains `gpt-6.1-sol/high`; runtime telemetry is unavailable and no fallback/telemetry claim is made.
