# Diff inventory

## Backend

- Added `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/JournalAttachmentPort.java`.
- Updated `.../marking/AttendanceAttachmentService.java`.
- Updated `.../marking/MarkingService.java`.
- Updated `.../checkin/AttendanceWritePortImpl.java`.
- Updated `.../student/StudentCheckinService.java`.
- Updated `.../studentrequest/StudentRequestService.java`.
- Updated `.../report/ReportService.java`.

## Tests

- Updated `.../report/ReportServiceTest.java` with expired metadata filtering.
- Updated `.../student/StudentCheckinTransactionIT.java` with cross-writer
  lifecycle/retention sequence and direct `markWithLesson` old-status branch
  assertions.
- Updated `.../studentrequest/StudentRequestServiceAuthorizationTest.java` for
  the lifecycle port constructor.

## Frontend handoff (11 frozen paths)

- Added the four `frontends/mobile-core/src/features/headman-journal/*` paths.
- Updated schedule screen/PCSS, mobile-core index, PWA auth/App, and TMA
  session/App as listed in the frozen `.agent/headman-journal-ui/diff.md`.
- The journal Vue/PCSS and client test contain the new Moscow/control fixes.

## Final verification correction

- `AttendanceWritePortImpl.markWithLesson` evaluates the previous status before
  assigning the new status, so EXCUSED retention and non-EXCUSED cleanup use the
  same invariant as the other journal writers.
- Final bounded IT recheck: handle `29410`, exit 0, 22/22, suite 10.581s.
