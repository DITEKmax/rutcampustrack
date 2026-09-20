# Decisions and required deltas — STUDENT-ROLE-02

## 2026-09-06 — owner: BOTH shells

Owner decision recorded for this batch: the student role is delivered for both
PWA and TMA. This supersedes the earlier only-PWA clause. The old wording remains
historical material; it is not an implementation command.

## 2026-09-06 — owner: common versus shell ownership

mobile-core is the sole writer for common screens/components, flow logic,
API/transport contracts, validation and command-error mapping. Separate adapters
own auth/session, geolocation/device, and Back/navigation/host. PWA owns install,
service worker and offline schedule/homework behavior. TMA owns its online
lifecycle and host boundary.

## 2026-09-06 — owner: R2 manual petitions and R11 blockage

The later R2 decision is canonical for the conflict with older limits: a student
may make five individual manual petitions per semester to replace н with +.
Excuse tickets у are unlimited. An automatic request created by the geo/check-in
flow does not consume the five-petition limit. R11 blocks geo check-in and the
н-to-+ replacement; only the excuse ticket у is allowed in that blocked state.
This is recorded as an owner decision, not redesigned from old material.

## 2026-09-06 — owner: runtime acceptance

The final TMA check must run in a genuine Telegram host. Browser simulation is
partial evidence only. If the genuine host is unavailable, PWA may be accepted
with an explicit TMA host OPEN gate; TMA itself remains open.

## 2026-09-06 — routing and review guard

The common-first batch route is: shared shell foundation; Today + attendance +
requests; homework; stats + map; profile/auth; shell install/offline/lifecycle
and final E2E. Each shell gets fresh scenario checks after adapter integration,
then a fresh independent Sol high review. Sol xhigh is authorized as a
consultant for substantive ambiguity only; it cannot override owner/design and
cannot review its own decision as the sole reviewer.

## 2026-09-06 — evidence boundaries

Root supplied and verified live metadata for board 4572:3062, file
VgVjQYWILLG9AC7Eh12VMk, dated 2026-09-06. Metadata is not design_context.
Root context/screenshot evidence currently covers Today, Attendance days,
Homework feed and Profile; other matrix cells remain pending. The existing Vue
evidence covers Today plus four student calls session/today/schedule/checkin;
other screens are recorded as not implemented.

## Required deltas for root before freeze

These are explicit deltas, not redesign proposals:

1. Expand and approve API/response/error contracts for Attendance, Reason,
   Homework, Stats, Map, Requests and Profile before their common components are
   implemented.
2. Confirm the batch split and acceptance owner for each of the 39 states, then
   freeze the matrix with the consultant's evidence where context is pending.
3. Provide task-owned PWA/TMA runtime resources and schedule a genuine Telegram
   host check; if unavailable, record the explicit TMA OPEN gate.
4. Resolve ownership through the available gateway before assigning code writers.
   The gateway was not exposed to this preparation worker, so no ownership claim
   is made here.

## Observed design follow-ups

Root observed four disabled navigation items, a disabled role control, extra
semester details absent from the final frame, and a title-width code risk from
padding plus ellipsis. The current fixture title is fully visible; do not state
that screenshot truncation has been reproduced. Screenshot comparison remains
pending, and no code change is authorized by this observation.
# 2026-09-07 — bounded shell repair token mapping

Root verified existing canonical `docs/design/tokens-v2.json` key `control/min-touch` at line1026, value44, documented minimum touch target. The shell repair writer may add CSS alias `--rct-control-min-touch: 2.75rem` and use it for Back control minimum block/inline size. This is a code alias of an existing canonical token under the accepted source-resolution rule; no Figma/design token is created or changed. Ownership extends only to that alias in mobile-core styles/tokens.pcss. All four independent review findings still require correction/recheck.
