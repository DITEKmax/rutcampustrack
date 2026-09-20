# B0 implementation addendum

Date: 2026-09-08. This addendum is part of the fresh implementation packet and
is saved before the first product-source mutation.

## Recorded source/authority deltas

- `frontends/mobile-core/src/features/map/map-contract.ts` is informational and
  unfrozen live drift. Preserve the old recorded SHA256
  `CB2B037252953B324F0A4375B367662DFD567A50A42ADC04DD219AF4D9602CD1` in the
  evidence; independently observed live SHA256 is
  `31A32B3BF2539F2D060B0DB3DA6B2BD4E6BDA724A019B38EBEB168109DCF90AA`.
  B0 imports only the frozen snapshot bytes listed in `paths.json` and does not
  rewrite or import live TS drift.
- Map wire authority is frozen at
  `C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/.agent/student-map/backend-patch-contract.md`,
  SHA256 `F7DF5F4074AE4C205889A2EFCB4D3B8189ECCD7FCB8BE43A0C5AB1E4BE35D659`,
  together with the root B0 corrections. This authority controls the additive
  `StudentMapApi`/model shape and V26-compatible metadata semantics.
- The 2026-09-08 local reservation is recorded in `contract.md`: C remains the
  temporary sole writer for `proto/attendance.proto`; it is outside B0.

## Decision

No product, contract, or scope redesign is made by this leaf. The frozen
contract, its exact 32-path boundary, and the accepted import manifests remain
authoritative. Any required delta discovered during implementation must be
recorded as a bounded root decision before writing outside that boundary.

## Finite wire ledger recorded before proto edit

Academic additions use only new tags and preserve all existing fields:

- `UserResponse.roles=10`, `UserResponse.roles_version=11`;
- `UserRoleGrant.grant_id=1`, `role=2`, `status=3`, optional `group_id=4`;
- `GetOwnUser(Empty) -> UserResponse`;
- `AcademicSubjectInfo.group_id=4`, `lesson_types=5`;
- `TeacherSubjectInfo.assignment_id=6`, `semester_id=7`, `lesson_type=8`,
  `valid_from=9`, `valid_until_exclusive=10`;
- `AssignmentInfo.id=1`, `teacher_id=2`, `subject_id=3`, `group_id=4`,
  `semester_id=5`, `lesson_type=6`, `valid_from=7`,
  `valid_until_exclusive=8`;
- `GetAssignmentsByIds`, `AssignmentsByIdsRequest.assignment_ids=1`,
  `AssignmentsByIdsResponse.assignments=1`;
- `HomeworkInfo.completed_at=10`, `binding_id=11`, `occurrence_id=12`,
  `current_lesson_id=13`, `binding_revision=14`, `archived=15`;
- `ReserveHomeworkBindingRequest.occurrence_id=1`, `request_key=2`,
  `expected_revision=3`, `payload_hash=4`; `ConfirmHomeworkBindingRequest`
  uses `binding_id=1`, `homework_id=2`, `request_key=3`;
- `HomeworkBindingResponse.binding_id=1`, `occurrence_id=2`,
  `current_lesson=3`, optional `homework_id=4`, `state=5`, `revision=6`,
  `group_id=7`, `subject_id=8`, `semester_id=9`, `date=10`,
  `lesson_number=11`.

Schedule additions preserve reserved `LessonResponse.teacher_id` tag/name 5:

- `LessonResponse.occurrence_id=16`, `assignment_id=17`, `semester_id=18`,
  `assigned_teacher_id=19`, `lesson_type=20`, `generation=21`,
  `revision=22`, `valid_from=23`, `valid_until=24`, `current=25`;
- `LessonInfo.occurrence_id=7`, `assignment_id=8`, `semester_id=9`,
  `teacher_id=10`, `lesson_type=11`, `generation=12`, `revision=13`,
  `status=14`;
- `GetOccurrenceHistory`, `OccurrenceHistoryRequest.occurrence_id=1`,
  `OccurrenceHistoryResponse.instances=1`, `entries=2`;
- `LessonLifecycleEntry.revision=1`, `action=2`, `lesson=3`, optional
  `target_lesson=4`, `generation=5`, optional `reason=6`, `actor_id=7`,
  `occurred_at=8`.

Binding RPC names are `ReserveHomeworkBinding`, `ConfirmHomeworkBinding`,
and `GetHomeworkBindings` (`HomeworkBindingsRequest.occurrence_ids=1`,
`HomeworkBindingsResponse.bindings=1`). No `attendance.proto` edit or
unlisted transport/RPC is authorized in B0.
