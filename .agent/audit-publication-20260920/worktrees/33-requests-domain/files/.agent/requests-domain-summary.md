# Requests-domain handoff summary

## Diff

The working tree contains the student-request domain package and its tests,
three new transport-neutral contract enums, immutable lesson/attachment
fields on attendance entities, Mongo indexes, descriptor-only excuse event
payload support, event-schema additions and the gRPC cancellation mapping.
The final authz correction requires an exact `STUDENT` role for student and
headman decision scopes. Existing foreign modifications in the worktree were
left intact; no commit, reset or cleanup was performed.

Key symbols for root review:

- `StudentRequestService.submitExcuse`, `submitLateCheckin`, `list`, `get`,
  `options`, `cancel`, `download`, `expireAttachments`.
- `StudentRequestService.decideExcuse`, `decideLateCheckin`,
  `resolveLessons`, `consumeBudget`, `executeWithRetry`.
- `StudentRequestDomainIT` and `StudentRequestServiceAuthorizationTest`.
- `MongoConfig.initIndexes` student request indexes.
- `ExcuseEventPublisher.publishRequested(..., descriptors)`.

## Acceptance and checks

The final focused unit/regression command passed 55 tests with exit code 0.
The final Mongo integration command passed 10 tests with exit code 0 on a
real Mongo 7 replica-set primary. Attendance production/test compilation,
changed event JSON parsing and `git diff --check` also passed with exit code 0.
Full command records are in `.agent/checks.json`.

## Limitations and required root deltas

- Public REST/gRPC/BFF, HTTP/gRPC 24 MiB transport limits and generated
  contracts are intentionally deferred to their assigned writer.
- Legacy `/attendance/excuses` and `/attendance/late-checkin` creation paths
  are not routed here yet; root must assign the bounded adapter/retirement
  delta before exposing the new flow, so they cannot bypass this domain's
  budget and eligibility rules.
- Existing `EventConsumer` approval paths still use legacy services; root must
  choose the bounded decision adapter before claiming integrated approval
  behavior.
- notification-bot expects legacy base64 attachment payloads; an explicit
  consumer/schema delta is required for descriptor-only retained attachments.
- The existing automatic-checkin proto has no `PRESENT_PRIORITY` value; the
  current mapping is `UNSPECIFIED` pending root's contract decision.
- No production runtime, browser/API transport flow, deployment, migration or
  independent Sol review was performed in this leaf. Domain acceptance is
  complete for the recorded criteria; root owns integration and fresh review.
