# Teacher report ticket HTML negotiation — compact contract

## Goal
Teacher can redeem an HTML statistics report ticket and download the same report body available from the authenticated export endpoint.

## Context/evidence
- MAIN baseline: `c1b4bb210300fdbf3477507a6231cd114e0b3269`.
- Runtime run `20260925-201050455-qacvkez_` (root-owned, frozen runner): ticket issue returned 200; teacher ticket redemption returned 503; direct teacher stats export with `Accept: text/html` returned 406, while the same endpoint with `Accept: */*` returned 200/2152 bytes. HEADMAN redemption returned 200 with matching bytes. Root retained sanitized API evidence at `v2-requests-harness/.agent/student-role-orchestrator/requests-runtime/runs/20260925-201050455-qacvkez_/accepted-artifacts/api-acceptance.json`.
- `TeacherApi` declares `/stats/export` as `produces=application/octet-stream`; `TeacherApiController` sets the actual response media type from the selected export. Gateway currently sends the ticket's selected media type as `Accept`, then maps downstream 406 to 503.

## Relevant scope
- Worktree: `.agent/worktrees/map-usage-delivery-20260922`, branch `codex/teacher-ticket-html-negotiation-20260925`, based on MAIN above.
- Sole writer: assigned leaf.
- Product files: `services/api-gateway/src/main/java/ru/rutcampustrack/gateway/security/ReportDownloadTicketDownloadFilter.java` and its focused test.

## Required behavior
- For mobile-BFF export requests, negotiate with `Accept: */*` so its fixed octet-stream mapping does not reject a dynamically formatted export.
- Continue validating the actual downstream `Content-Type` against the stored ticket format before forwarding bytes.

## Constraints
- Do not alter ticket selectors, authentication/authorization, fixed routes, rate limits, TTL, size limits, or response media validation.
- Do not modify the root-owned frozen runtime runner/source or run Docker/full-suite checks.
- RULES source SHA-256 at MAIN: `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.

## Existing patterns
- Gateway dispatch remains fixed from the stored selector; `handleDownstreamResponse` already rejects missing or incompatible actual media types.
- `ReportDownloadTicketDownloadFilterTest` uses WireMock for downstream HTTP behavior.

## Acceptance criteria
- A valid teacher HTML response passes through with the original body and `text/html` response type.
- The mobile BFF receives `Accept: */*` for this dynamic export.
- A 2xx response with an unexpected actual media type remains rejected as 503.
- No authorization or ticket lifetime behavior changes.

## Verification
- Run only `ReportDownloadTicketDownloadFilterTest` after the fix using the root-approved single-process Gradle invocation; record exit code and evidence in `result.md`.
- Run `git diff --check`; record exit code and final diff inventory in `result.md`.
- Root will validate the live Auth/Gateway/BFF scenario after integrating this source fix.

## Do not
- Do not change global Spring proxy/security configuration, BFF endpoint declarations, report authorization, runtime harness, or unrelated Gateway behavior.
