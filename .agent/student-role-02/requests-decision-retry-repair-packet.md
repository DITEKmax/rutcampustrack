# Requests decision retry repair — frozen contract

07.09.2026. Bounded S2 repair within S3 student role. Fresh Luna max developer, sole writer of assigned worktree. No children.

## Goal
Fix independently confirmed false409 after a successful decision commit whose acknowledgement is lost, for excuse and late-checkin requests.
## Context/evidence
Original goal active-contract.md, domain requests-domain-packet.md and requests-domain-review-repair-packet.md. Review2 result requests-domain-review-2-result.md; frozen32 baseline requests-review-2-source/manifest.json. Root opened critical service methods1043–1132,1614–1649 and confirmed whole-body retry hits terminal conflict. Prior six findings are closed and must stay closed. This localized new MEDIUM uses default fresh Luna, not automatic Terra escalation.
## Relevant scope
Only .agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java and focused tests in that module needed for retry/authorization assertions. Assigned writer may update only its .agent/student-role-02/requests-decision-retry-repair evidence. No main source writes. Worktree HEAD8002b9ea4356b10779c5bb9a6d99746d32d78ae2 contains accepted uncommitted32 baseline files; preserve them. You are not alone; other worktrees are active, do not revert others' work.
## Required behavior
Ambiguous commit recovery returns persisted decision only if same authorized actor and requested outcome match; excuse normalized comment must also match. Revalidate authority/group and reject self-decision before any replay result. Cancellation, expired or contradictory outcomes, another actor and different comments must not gain replay success. Avoid broad weakening of generic retries/create receipt semantics. A narrowly scoped ambiguous-recovery path is preferred; explain any ordinary exact-match decision replay semantics if used. No duplicate terminal event or attendance write from recovery.
## Constraints
Read project workflow, services/AGENTS.md and tests/AGENTS.md plus rct-verification. No transport/proto/bot/dependencies/config changes, production operations or secrets. No commits. No other writer in this worktree. Frozen snapshot is readonly. No claiming mock tests prove durable Mongo behavior.
## Existing patterns
Existing executeWithRetry classification, normalizedComment, requireDecisionAuthority, decisionBy/status fields, real transaction/outbox IT wiring. Root accepted current durable15 and focused49 evidence, but new fault scenarios require new evidence.
## Acceptance criteria
Both decision kinds recover from labelled exception injected AFTER actual successful transaction commit with one persisted terminal event. Uncommitted transient failure still retries successfully. Opposite outcome and mismatched actor/comment remain conflicts or denied as applicable. Preserve previous authorization, cancellation, PRESENT priority and outbox rollback invariants. Zero scope drift.
## Verification
Run relevant focused tests, compile and diffcheck; run correct integrationTest task for any Mongo IT (not test) and inspect fresh XML timestamp/counts. Stop owned runtimes after checks. Record HEAD, commands, exit codes, source SHA, XML and environment. Return stable manifest and concise evidence for fresh Sol recheck. Do not repeat broad checks absent change or concern.
## Do not
Do not edit other worktrees or main docs, rewrite review findings, broaden transport contracts, silently swallow exceptions, weaken authority, spawn agents or claim full-role/security PASS. If proof requires broader scope, report concrete defect/evidence before changing it.
