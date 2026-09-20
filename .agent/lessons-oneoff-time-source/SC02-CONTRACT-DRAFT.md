# SC-02 — bounded engineering contract draft, 2026-09-20

Status: root review required; no product implementation, bell values or admin policy approved. Risk S2; reads/config resolution affect future S3 physical writer but do not replace accepted L5B gates.

## 1. Goal

Provide one canonical university-wide lesson-time directory for Schedule grid clients and server-side one-off creation. A created physical lesson receives immutable resolved start/end snapshots; no client timetable guessing or inference from an occupied recurring template.

## 2. Context/evidence

Source report RESULT.md SHA8D5C7F8F9A3A5835BBDE90B06A8E6F895443DCA3FF3364080D67D1BF80343A41 and sources.json SHACE907015A48B6CAE19E342BF6D6D5F1DFD0A284B9B56F053FD2578D6793197A1. Canonical backend-requests.md:196 SC-02 requires lesson numbers/start/end shared across university plus study-day count; original marks API missing. SC-20:224 discusses subject replacement/template history, not permission to overwrite lesson-time history. Current CreateOneOffLessonRequest has date/number/room but no times; ScheduleItem times are per-template. ClockConfig supplies Europe/Moscow. Root independently verified these originals. Schedule baseline426a15b6; accepted L5B v3 protocol remains separate prerequisite for actual physical writes.

## 3. Relevant scope / ownership

Proposed domain authority: Schedule owns validated directory selection/read contract and physical time snapshots. One designated future developer owns shared REST/OpenAPI/generated contracts and any persistence/config format. Source of real bell values, data maintainer and administration rights remain OPEN; assigning Schedule technical ownership does not invent the university's data owner.

Scope here: directory read API, one-off server resolution, immutable physical snapshots, unavailable behavior and client consumption. Excluded: directory admin CRUD, production data seeding, migration/backfill, bell-time overrides, full template regeneration and any lifecycle activation. Source report covered scout12 plus lead3 exact originals; archived ч5/screens117/118 source and real values are not inspected/proven.

## 4. Required behavior

### Read contract proposed for root freeze

Add a Schedule read endpoint, proposed name `GET /schedule/time-slots?date=YYYY-MM-DD` (name is engineering draft, not discovered existing API). Authenticated existing schedule readers receive the same university-wide directory; group-specific/timezone variants are not invented. Return an opaque immutable `directoryVersion`, IANA timezone, ordered slots `{lessonNumber,startTime,endTime}`, and authoritative study-day metadata sufficient for screens117/118. Do not infer Monday–Saturday merely from reference scans. Exact representation of study days/count and initial single-version/effective-date storage must be frozen before implementation.

Date identifies the intended lesson date. If versioning/effective ranges are adopted, the server must resolve exactly one approved directory version valid for that date; zero/ambiguous versions fail closed. No `latest` fallback for historical/future dates whose validity is unknown. A simpler initial single approved directory is possible only with an explicit covered-date policy; this draft does not decide that it applies forever.

Validate loaded source: unique positive slot numbers compatible with existing public bounds1..8 until an explicit contract change; parseable LocalTime values with start<end; valid timezone; no duplicate/ambiguous applicable version; study-day metadata internally consistent. Cross-midnight slots, overlaps, holidays and special-day schedules are OPEN policy if source data requires them, not silently normalized. Do not introduce unsupported special scheduling features.

### One-off creation and snapshot propagation

Input continues to identify exact assignment/date/lessonNumber/room according to future L5B contract. The client does not supply authoritative start/end as an alternative to SC-02. Server resolves the slot for the requested date and carries `{directoryVersion,lessonNumber,startTime,endTime,timezone}` from a validated immutable directory version into the same Schedule creation transaction as origin/occurrence/physical row/current pointer/outbox. Retain directory provenance (exact storage fields to be frozen with L5B schema); downstream reads/events use physical snapshots, not a mutable directory lookup by number.

Directory resolution must not cause an RPC under L5B row locks. If config is loaded before tx, it must be an immutable identified version whose validity is verified for the requested date; a mutable in-memory object/name alone is insufficient. If selection can change concurrently, pin/check a persisted version/selection revision in the same DB transaction or reject/retry before mutation according to the frozen config mechanism. That choice is still engineering OPEN; no stale selection may silently become a different time snapshot after acceptance.

Missing/unvalidated directory, missing provenance or unresolved date coverage → typed `time-directory-unavailable`/503 with no origin/physical/binding/outbox creation. A well-formed directory with an invalid requested lessonNumber yields request validation error (existing bounds unchanged), not invented time or an unrelated template lookup. Auth denial remains403; invalid date/number format400. Read failure is typed unavailable, never `200 []` or fabricated default study days. Ordinary replay of a previously accepted one-off returns original physical snapshot/operation outcome, even if the directory later changes; it does not re-resolve and create a second lesson.

No automatic rewrite of already materialized physical times after directory change. Future unmaterialized generation and treatment of existing future lessons require the separately acknowledged effective-change/template cutover policy; neither SC-20 nor this draft authorizes bulk mutation. Transfer's accepted explicit new time contract R25 remains separate; allowed differences from directory require explicit validation policy before integrating the two paths.

### Client consumption

Screens117/118 load the read directory for the intended date/covered period, render slot labels from server times and retain version context for preview. If create waits while directory selection changes, server response is authoritative and the UI must refresh/show the returned snapshot; an optional expected directory version may provide explicit conflict UX if root chooses it. A client-supplied version cannot authorize an obsolete selection.

Unavailable directory blocks new one-off scheduling controls with retry feedback; existing lesson cards remain readable from their physical snapshots. No hardcoded bell values, copying another group's template, offline authoritative creation or treating stale cached values as write permission. Accepted E session/offline boundaries remain unchanged.

## 5. Constraints / unresolved gates

Established: university-wide directory requirement; one-off current DTO gap; server time comparisons currently Europe/Moscow; retained physical identity/history; no guessed values.

OPEN data/product decisions: actual approved bell values and source; authoritative study days; maintainer/admin mutation rights; date coverage/effective changes; permitted per-lesson exceptions/overrides. Root first follows existing SC-02 ч5 S-2/screens117/118 provenance; ask owner only for genuinely absent material policy/data after source resolution. Do not ask the already answered choice “directory or arbitrary explicit inputs”.

OPEN engineering decisions: storage/config mechanism, read endpoint/schema, version identifier/selection atomicity, cache invalidation, error code naming and provenance fields. Root owns shared freeze. This draft is not an ADR approval and does not authorize migration or deployment.

## 6. Existing patterns

Use current Schedule authentication/error transport, Clock abstraction and L5B transaction/outbox/replay conventions. LocalTime is already used by recurring ScheduleItem; snapshot storage is required by L5B physical model. Moscow Clock supports deterministic temporal tests but must not be mistaken for bell-time data. Existing DTO lessonNumber1..8 remains until a reviewed boundary change.

## 7. Acceptance criteria

- Authorized clients read one consistent approved directory for requested date, same slots across groups; denied callers receive no protected data.
- Real one-off operation resolves exact slot and stores/returns immutable physical time + provenance. Same-key replay retains identical IDs/times.
- Missing/ambiguous/unapproved source fails closed with zero domain/outbox writes; empty success/default times are impossible.
- Concurrent selection change cannot combine versionA provenance with versionB times; physical history remains unchanged after new directory selection.
- Client preview and created response use server authority; unavailable data does not disable reading already snapshotted history.
- Real values/policy and all OPEN engineering choices are frozen before implementation/activation; no inference from generic university timetables.

## 8. Verification

Current stage: document/source inspection only, runtime N/A. Future bounded checks after freeze: directory schema/date-selection tests; real Schedule creation transaction snapshots/no-write unavailable cases; controlled directory-selection race; unchanged-history/replay; client contract/error behavior and authorized reader/creator paths. Integrate with L5B matrix rather than duplicate tests of unchanged handlers. Root allocates heavy queue and fresh review; no tests started by this draft.

## 9. Do not

Do not invent bell values, silently permit manual overrides, assume study days from scans, infer one-off time from recurring occupancy, mutate retained physical history, backfill ambiguous records, expose admin writes without policy, start another leaf or modify product/config/proto. Only lead-owned source-resolution documentation is written here.
