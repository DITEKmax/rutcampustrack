# Vue ONE_OFF create

## Goal
Староста создаёт разовую пару через публичный API и после обновления видит серверную canonical pair для существующих операций переноса/ДЗ.

## Context/evidence
Frozen baseline996f590ca5b3fa947e2da111a332a3f58232da0a; branchcodex/vue-oneoff-create-1002. Root approved backend990cd74e and opened current CreateOneOffLessonRequest. Canonical main RULES SHA A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA. Existing generated snapshot lacks assignment/time/physical identity, so local narrow DTOs follow current Java contracts; generated sources unchanged.

## Relevant scope
Sole writer: mobile-core schedule HeadmanScheduleScreen.vue, headman-schedule-client.ts, one targeted adjacent test. Own evidence folder only. Root requested forwarding existing reportDownload prop into HeadmanGroupScreen; G owns that child.

## Required behavior
Exact assignmentId/group/subject/date/number/start/end/classroom; ONE_OFF POST with UUID Idempotency-Key. Persist immutable request/key to sessionStorage before POST; retry and refresh reuse the exact request, never a fabricated recurring fallback. Canonical GET /one-off-lessons uses current semester dates and exact semester filter; server physicalLessonId opens existing lesson management. Context/session changes invalidate pending UI publication and generation-bound HTTP responses/retries. Recurring creation retained.

## Constraints
Use existing classes/components; no redesign/new tokens/styles. Intent scope includes user/session/role/group, contains no bearer. Unknown outcome is never discarded due to timeout or a new account. Unavailable/corrupted storage fails closed.

## Existing patterns
Existing recurring form, generation-bound API and headman journal lesson management. Current ONE_OFF public response projects origin plus current physical pointer. Existing report download port is forwarded only.

## Acceptance criteria
Create→server canonical refresh→visible one-off pair; exact retry identity; scope isolation and stale response guard; no locally inserted recurring slot. Integrated browser path remains pending root runtime lease.

## Verification
Three meaningful tests: lost response/reloaded intent and canonical read; stale session/401 no new-account retry; storage/physical identity fail closed. Vue/PWA typecheck, scoped lint and diffcheck. No build/stand/browser/full suite without lease.

## Do not
No App/shared/homework/generated/config/backend changes; no children, main integration, push/deploy or foreign cleanup. Preserve foreign evidence and runtime cache.
