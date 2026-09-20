# Integration order — root staging, 07.09.2026 19:57 UTC

Not acceptance or permission to merge. Main remains at 8002b9ea4356b10779c5bb9a6d99746d32d78ae2; common isolated baseline is d3c31acb8cce53791a4981e5858a37d44fdc9a0e. Active writers remain isolated. Root compared current product path inventories of homework-ui, dependency-checks and requests-transport; these inventories are provisional while writers run.

## Direct overlaps

The dependency and Requests branches both modify docs/openapi/mobile-bff.json and frontends/mobile-core/src/api/generated/mobile-bff.ts. Their bytes cannot be selected independently: dependency updates change the schema exporter while Requests adds API operations/types. The final integrator must regenerate both artifacts from the combined accepted Java contracts and dependency/exporter configuration, then run snapshot checks without UPDATE and the generated-type drift check. Preserve Requests' accepted generator behavior; do not solve line-ending differences by discarding new API operations or by copying an older snapshot wholesale.

No current product path overlaps between shared Homework UI and either other writer. Homework adapter ownership excludes StudentApi/generated types; use caller closures to bind retry to an auth generation. Baseline import must retain source manifest hashes before new adapter edits begin.

## Ordered acceptance and work

1. Presentation repair supplies stable source, distinct-title/description and absent-description proofs, five final captures and applicable checks. Fresh Sol high rechecks the two confirmed findings and related presentation behavior. Root accepts exact source before adapter dispatch.
2. Dependency reconciliation supplies its complete combined manifest, all mandatory checks and a classification of imported versus newly changed paths. Fresh Sol high reviews the actual combined dependency/config/test/snapshot diff and compatibility/security evidence.
3. Requests transport supplies its combined accepted-domain plus new transport manifest, real transport tests, Python checks and contract generation. Fresh Sol high reviews stable transport evidence. Domain acceptance alone does not cover bot authorization, BFF ingress or attachment delivery.
4. A designated integration developer applies accepted sources to a separate checkout, retaining all incoming manifests, regenerates the two overlapping artifacts and verifies combined checks. No concurrent edits to shared contracts/config/generated files. Integration into main is a later scoped action preserving the 47 owner files.
5. Gateway forwarding/single-dispatch and Requests edge-size limits depend on the accepted dependency configuration. Freeze the forwarding decision first; use one sequential writer for Gateway/nginx/compose configuration. Local test runtime does not authorize deploy/firewall/production actions.

## Limits

Full-role criteria remain open: shell adapters, Requests screens/forms, profile/session authority, subject/assignment/occurrence and attendance/statistics foundation, map, real flows and genuine Telegram evidence. Existing fixture or component passes must not be promoted to those untested scopes. Original failing checks and screenshots remain immutable evidence.

## Local Docker preflight

Root `docker ps --format '{{.Names}}|{{.Status}}|{{.Ports}}'` returned exit1 inside sandbox (dockerDesktopLinuxEngine pipe unavailable). The exact read-only command retried through normal require_escalated returned exit0 with no containers. Docker Desktop processes were present. This is a sandbox access boundary, not an unavailable engine or product defect; no restart, data reset or runtime mutation was performed. Both active backend writers were informed. This preflight is not a product runtime PASS.
