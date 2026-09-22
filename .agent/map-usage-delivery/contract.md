# Map usage delivery contract

## Goal

At frozen baseline `368e99de779a72c33c831243dcf310fffb4422d1`, record one
successful user-facing floor-plan opening and expose the persisted per-floor
count to ADMIN.  The future ADMIN home must obtain its total by summing the
same floor-day aggregate; no second counter is introduced.

Ownership is this worktree only:
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/map-usage-delivery-20260922`.
MAIN, App/session/auth/users, the V29 admin-semester writer and foreign
worktrees are outside the writer boundary. V30 is reserved here only if the
frozen V26 schema needs a bounded additive correction.

## Context and evidence

- `docs/wireframes/admin/135-admin-map.md`: floor demand is the number of
  openings on student map screen 105; it is the same product-event subsystem
  as the map-view metric on admin home 131. The server calculates demand and
  the client does not accumulate it. A number must carry its period.
- `docs/wireframes/admin/131-admin-home.md`: map views are the product event
  “user opened floor scheme”; the future activity block consumes one shared
  aggregate.
- `docs/wireframes/student/105-student-map.md`: selecting a floor reloads
  its published plan; the user-facing viewer is the event boundary.
- V26 already contains `campus_map_floor_daily_demand`, immutable open-intent
  rows and a trigger-backed demand dedupe. `proto/academic.proto` already
  declares `RecordCampusFloorOpen`.
- The previous map package deliberately left this follow-up open:
  `.agent/maps-delivery-20260921/summary.md`.
- `.agent/orchestration-v2/MAPS.md` records the privacy-preserving owner-key
  constraint; the owner decision below resolves the provider and count rule.

## Relevant scope

- Academic campus-map service/repository and existing Academic gRPC handler.
- Existing mobile-BFF map contract, facade and Academic client.
- Existing shared `CampusMapClient`, `AdminMapClient`, `MapScreen.vue` and
  `AdminMapScreen.vue` in `frontends/mobile-core`.
- V26 demand tables/triggers; add V30 only for an approved bounded schema
  correction.

## Required behavior

### Event boundary

The client sends one event only after the selected floor has a published plan
and the current SVG viewer emits a successful `load` event. Manifest/catalog
reads, plan metadata reads, PNG-only downloads, the SVG transport before the
viewer load, SVG+PNG parallel transport by itself, upload processing and ADMIN
upload-form preview do not count. Selecting a different floor or plan creates
a new event; retrying the same logical opening reuses the same UUID.

### External API proposal

The already accepted additive route is:

```text
POST /api/v1/map/buildings/{buildingId}/floors/{floorId}/opens
Header: Idempotency-Key: <canonical UUID>
Response: 204 No Content
```

`400` is used for a missing/non-canonical UUID or malformed numeric path,
`401` for an invalid/expired session, `403` for a role outside the existing
campus-map reader scope or a non-active reader identity, `404` for a missing
or inactive building/floor pair, and `503` for a map dependency failure.
The endpoint is additive and shares the current `/api/v1/map` BFF route.

The Academic gRPC call remains the existing
`RecordCampusFloorOpen(CampusMapOpenRequest)` with server-derived actor
identity; `intent_id` is the UUID above. Neither the REST body nor the gRPC
request accepts a user id or an arbitrary group id.

### Persistence and read model

The V30 transaction inserts a privacy-preserving open intent and lets the
existing trigger create/increment the floor-day aggregate once per distinct
intent. ADMIN map responses expose a period-labelled per-floor count. The
shared service owns the floor-count query and a `total(period)` query that sums
that same table; the future home reuses those methods rather than maintaining
another counter.
The client refetches the admin inventory after changes and renders the returned
server count.

### Authorization

The event call reuses `CampusMapReadService` authorization: valid signed
identity, active permitted reader role, and an existing active floor belonging
to the requested active building. It must not weaken Student/Headman-as-Student
or active Teacher scope, and ADMIN upload/preview is never routed through the
event call.

## Constraints

- Preserve V26 transactional/trigger and retry-idempotency invariants while
  applying the approved V30 correction for distinct openings; keep existing
  map read and upload behavior.
- Do not add a telemetry framework, a dashboard overhaul, a second counter,
  general refactors, lockfile changes, production migration, deploy or push.
- Do not touch global session/auth/user ownership, V29 migration or foreign
  worktrees. The two approved map-client constructor hunks in
  `frontends/pwa-vue/src/App.vue` and `frontends/tma-vue/src/App.vue` only pass
  the existing session-generation callback; no other App wiring changes are in
  scope.
- No personal viewing history is required. Any dedupe material must contain
  only the minimum pseudonymous/event fields needed for retry protection and
  its accepted retention policy.
- Heavy Gradle/Docker checks require root's lease. Light frontend checks are
  allowed after implementation.

## Existing patterns

- `CampusMapReadService` is the canonical role/floor validator.
- `CampusMapReadRepository` owns fixed parameterized JDBC map reads; the new
  demand queries should follow that boundary and use one transaction for the
  dedupe insert plus aggregate update.
- `AcademicGrpcServiceImpl` obtains claims from the signed internal context;
  `MapAcademicClient` maps gRPC statuses to the existing BFF problem model.
- `CampusMapClient` already retries one `401` request after session refresh;
  its event call must preserve the same bounded retry behavior while retaining
  the same idempotency UUID.
- Existing Vue screens use the shared clients and request freshness guards. The
  map client captures the current session generation around transport, and the
  map screen aborts pending asset/event requests on unmount or floor context
  changes.

## Acceptance criteria

1. A real successful floor view records one persisted event; two asset
   downloads for that view do not create two events.
2. A transport retry with the same UUID leaves the floor-day count unchanged;
   independent accepted openings follow the owner-approved distinct-opening
   rule.
3. Invalid/expired roles and non-existing/inactive floor pairs cannot record
   demand; ADMIN upload preview cannot record demand.
4. ADMIN refetch shows a persisted period-labelled count for each floor, and
   the reusable server total is the sum of those same aggregate rows.
5. Existing manifest/plan/asset and admin upload flows remain behaviorally
   unchanged.

## Verification

- `git diff --check` and scoped source/type checks.
- Focused Academic service tests for authorization, floor/building binding,
  idempotency, distinct-opening semantics and total-from-floor aggregate.
- Existing PostgreSQL migration/reader harness extended only for the real
  query/trigger and retention rules; cleanup deletes retained child rows before
  expired intents and leaves the aggregate untouched. No duplicate wiring suite.
- Focused BFF contract/client checks for `204`, UUID forwarding and status
  mapping; focused mobile-core typecheck and event freshness check.
- Root-owned runtime acceptance remains a separate gate: real PWA/TMA floor
  open followed by ADMIN refetch.

## Do not

- Do not silently reinterpret the accepted API or invent a new BFF/gRPC layer.
- Do not reuse `GRPC_SECRET` or an auth signing secret as `owner_hmac` without
  root's explicit decision.
- Do not add a second counter or bypass the V30 owner/intent dedupe; every
  distinct logical opening must reach the shared floor-day aggregate once.
- Do not claim the user-facing/runtime criterion complete from source checks
  alone.

## Owner decisions (2026-09-22)

1. **Dedicated owner key:** use a configured, dedicated HMAC-SHA256 provider
   with its own setting. Never reuse auth/gRPC secrets, hard-code a production
   default, print the secret, or rotate/deploy it in this package. If the
   setting is absent, the event endpoint fails closed while map reads remain
   available. Runtime may provide an ephemeral in-memory dedicated key for
   non-production harnesses only.
2. **Every logical opening:** each new logical floor-opening UUID increments
   the aggregate once. A transport retry with the same UUID is a no-op. The
   V26 owner/floor/day suppression is not the required metric; an additive V30
   correction is approved to remove that suppression while retaining
   owner/intent idempotency and existing aggregate history.
3. **Initial period:** admin responses use `all_time`, computed from the same
   daily aggregate rows. A `today` period is outside this package until a
   product timezone rule exists.
4. **Generation boundary and retention:** a pending opening is valid only for
   the captured session generation and current floor request. Unmount/context
   invalidation aborts its transport. Production cleanup runs after the
   database's accepted-plus-48-hours intent deadline and UTC-day-plus-3-days
   dedupe deadline, in child-before-parent order, without decrementing history.
