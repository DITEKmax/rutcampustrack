# Evidence

## Source evidence

- Added `shared/port/JournalAttachmentPort` as the single pair lifecycle and
  availability boundary; `AttendanceAttachmentService` implements it against
  the existing `request_attachments` collection and pair key.
- `MarkingService` now checks live attachment availability before retaining an
  `EXCUSED` attachment, clears metadata on invalid transitions, and applies the
  same cleanup to batch and clear paths.
- `StudentCheckinService`, `AttendanceWritePortImpl`, and
  `StudentRequestService` clear journal bytes/metadata on non-excused writes;
  approval paths retain only a live existing journal attachment.
- `ReportService` filters stale journal metadata through the lifecycle port.
- `StudentCheckinTransactionIT` now exercises manual mark, late decision, and
  geo write transitions while retaining a separate request-owned document. Its
  direct `AttendanceWritePortImpl.markWithLesson` cases cover both old-status
  branches: stale legacy metadata is cleared on non-EXCUSED → EXCUSED, while a
  live attachment is retained on EXCUSED → EXCUSED.
- A source self-review caught the ordering dependency in
  `AttendanceWritePortImpl`; the lifecycle helper now reads the old status
  before the new status is assigned. The final IT-only recheck was run after
  this correction.
- Frontend handoff imported the exact 11 paths from the frozen old worktree;
  journal date now delegates to `domain/homework.moscowDate` and controls
  implement the fresh picker/EXCUSED semantics.

## Source handoff

Parent `/root` was sent an ACK and a heavy-batch request through the Codex
collaboration thread. Heavy lease was not assumed before the request.

The final root-granted IT-only runtime handle `29410` completed successfully
and was released. Its 22-test XML has zero skipped/failures/errors. The two
earlier terminal failures were fixture-only and each was corrected with new
evidence before the preceding successful run; the final run covered the
ordering correction.
