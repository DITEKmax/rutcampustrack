# Scope — decision retry repair

Date: 2026-09-07. Branch: `codex/student-role-02-requests-domain`. HEAD:
`8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

Risk is S2 for this bounded repair inside the broader S3 student domain. The
request is to repair the independently reproduced false `409` returned when a
decision transaction commits but its acknowledgement is reported with
MongoDB `UnknownTransactionCommitResult`.

The repair-owned code scope is limited to:

- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java`
- focused decision scenarios in
  `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java`
- this evidence directory.

The frozen source snapshot at `C:\Users\maksd\IntelliJIDEA\rutcampustrack\.agent\student-role-02\requests-review-2-source`
and its 32-file dirty implementation baseline are preserved. The repair does
not touch transport, proto, BFF, frontend, bot, config, dependencies,
production data, shared runtime, or other worktrees. No commit is created.

No product or public contract decision was needed. The recorded root delta is
the narrow decision-only recovery path required by the review defect: recover
only an exact terminal result for the authorized actor, requested outcome and
(for EXCUSE) normalized comment; otherwise retain the existing conflict/retry
behavior.
