# Requests transport — staged independent review

07.09.2026. S3. Fresh Sol high read-only reviewer, fork none, no children. Not dispatched until stable source handoff and root freeze appendix.

## Goal

Review the real BFF/REST/gRPC/bot transport over accepted Requests domain without conflating direct-service acceptance with the deferred full ingress path.

## Context/evidence

Read requests-transport-implementation-packet.md (binding root clarifications), unchanged requests-transport-decision-result.md, requests-domain-review-3-result.md and excuse-source-resolution.md. Baseline d3c31acb8cce53791a4981e5858a37d44fdc9a0e plus accepted32 manifest requests-review-3-source/manifest.json. Domain review3 accepted previous repair and recorded the bounded recovery-read/dependency-outage limitation. Root will attach the final combined manifest, exact diff and check artifacts. A domain PASS is not transport acceptance.

## Relevant scope

Attendance public/bot gRPC adapters, legacy REST seams, BFF contracts/controllers/error/identity handling, proto, event publisher/consumer path, notification bot gRPC/queue/callback flow, typed StudentApi/multipart/binary helpers and generated contracts/tests. Open critical original production paths and direct callers independently. Main/all worktrees/evidence read-only; return review text for root to save unchanged.

## Required behavior

Verify JWT-protected student service and exact-service fail-closed bot secret cannot bypass one another. Actor/group/self authority must derive from persisted request and current server authority on every file fetch/decision; no caller-supplied public actor. Typed no-store errors preserve distinctions among invalid request/key, conflict, expired/missing/foreign files, dependency outage and unexpected programmer failure. Public projections omit decidedBy. Preserve origin/PRESENT_PRIORITY, cooldown Retry-After and unrelated Homework/Today errors.

Review actual multipart/binary behavior: browser boundary, repeated files, JSON part, original idempotency key and replayable same-session retry, download Blob/header handling. Old student-create routes terminate with410 before dependencies/business writes; necessary legacy decisions delegate to accepted domain. Remove actual base64 publishing/consumption, not just a reachable branch. Bot fetches required attachments before enqueue, shows pending until authoritative decided event, handles duplicate/stale/transient failures and two-phase dedup leases without false completion or exactly-once claims. Lost-ACK recovery must preserve one persisted effect.

## Constraints

Do not author repairs, change tests, use author reasoning as evidence, send real Telegram/OTP messages or change runtime data outside assigned test resources. No re-opening accepted policy without a new defect. Gateway/nginx/XFF/dependency configuration remains separately owned. Do not demand an unapproved production operation as verification.

## Existing patterns

Read current signed internal identity, structured gRPC error mapping, domain receipts/transactions/outbox and actual Rabbit/Redis bot consumers. Five reasons, OTHER/comment limits, 0..2 files,10MiB each/20MiB total, MIME/signature/extension rules are frozen. Authenticating a field value is not cryptographic producer authentication; inspect the real broker trust boundary and public ingress exclusions.

## Acceptance criteria

Return bounded PASS or actionable severity/file:line/evidence/impact/reproduction findings. Verify real Mongo/gRPC/BFFHTTP and Rabbit/dedup/retry/DLQ behavior with fake external delivery, Python/frontend checks, Java-first generation and stable source hashes. Confirm missing/forged identity, wrong role, foreign/self/expired requests and files, invalid bot secret/actor, duplicate/replay/stale and second-attachment failure paths. Preserve existing domain one-budget/receipt/request/outbox invariants. State explicitly that final Gateway/nginx2x10MiB ingress/no-store/oversize/no-write checks remain OPEN until ordered configuration work; do not promote direct BFF tests to full ingress or full-role PASS.

## Verification

Read exact commands/exits/environment/revision and captured artifacts. Run bounded independent rechecks for critical uncertainty with isolated resources and no overwriting evidence. All changed/generated paths must be accounted for in final source manifest, including imported32 and narrowly permitted domain helpers. Rehash after review. Generic StudentApi same-session401 retry is not proof of cross-login generation safety; that separately confirmed repair belongs to shell adapters. No simulated browser as genuine Telegram evidence.

## Do not

No product edits, children, hidden compatibility routes, message-string error parsing, fresh keys on ambiguous retries, weakened authorization, real external sends, stale evidence as PASS, or conflation of deferred ingress with a completed full-role flow.
