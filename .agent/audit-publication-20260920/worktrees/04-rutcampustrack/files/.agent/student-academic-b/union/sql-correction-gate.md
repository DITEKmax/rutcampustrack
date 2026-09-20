# B0 SQL correction gate

Date: 2026-09-08. Root reviewed the first WIP SQL diff against frozen C9/F7DF
invariants. This is a bounded correction inside the already granted four SQL
paths and their owned migration tests; it does not expand scope or require a
Terra escalation.

## Request/reference

Root requested recheck of migration invariants before the stable diff: V26
intent/dedupe durability and finite TTL; V17 retained physical history,
generation, binding ownership and replay; V25 deferred subject minimum and
assignment lifecycle. Frozen references are `union/contract.md` SHA
`C9C4DC2C4BE2229AFF3CEDB27E0E43634329EBA5AC83CE42A889E34C33DAFC74` and D
authority `backend-patch-contract.md` SHA
`F7DF5F4074AE4C205889A2EFCB4D3B8189ECCD7FCB8BE43A0C5AB1E4BE35D659`.

## Reproduction and evidence

1. V26 used a generated `timestamptz` expiry and an unconditional dedupe
   delete guard. PostgreSQL 16 may reject the generated expression or make the
   required day-end-plus-48-hour cleanup impossible. `campus_map_open_intent`
   was also mutable and dedupe FK did not prove floor/day matched the intent.
2. V17 bound physical lessons to `(occurrence_id,generation)`, making a retained
   old physical row incompatible with a restored generation. A partial unique
   `(occurrence,current_lesson)` also rejected more than one pending/active
   homework binding for one occurrence, and `ARCHIVED` pending rows could not
   retain a null homework. No recurring template/date origin key was present.
   Physical origin columns and durable replay identity were not guarded.
3. V25 enforced a deferred minimum only from child rows, so a new subject with
   no type could commit. Assignment delete was unguarded; the close trigger did
   not permit shortening an already known end date.

## Correction

- V26 stores an explicit expiry checked as `accepted_at + 48 hours`; intent
  identity/payload/timestamps are immutable, early delete is rejected, and
  expired intent/dedupe cleanup is permitted. Composite intent/dedupe identity
  proves owner, floor and UTC day; triggers keep aggregate increments atomic.
  Positive-ID checks cover every generated map ID.
- V17 references a stable occurrence identity while retaining generation on
  physical snapshots; it validates all immutable occurrence fields, protects
  physical origin/id, allows multiple distinct pending/active bindings, rejects
  duplicate non-null homework IDs, permits archived null pending history, adds
  recurring template/date uniqueness and immutable replay rows, and tests
  cancel/restore retention with generation increment.
- V25 adds a deferred parent subject check, forbids assignment deletion, allows
  a one-way close and shortening of an already known end date, and tests the
  required abort/constraint cases.

## Root decision

Apply these corrections in the current B0 files, then perform focused static
and PostgreSQL migration checks when the root lease is granted. No contract
redesign, external path, Terra escalation, or production operation is needed.
