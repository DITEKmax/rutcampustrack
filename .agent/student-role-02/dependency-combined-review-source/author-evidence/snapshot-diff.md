# OpenAPI и generated-types evidence

Baseline: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`. Снимки были получены
Java-first integration export с `-Popenapi.snapshot.update=true`, затем каждый
affected suite был повторно запущен без update. JSON не редактировался вручную.

| Snapshot | Результат semantic comparison с `snapshot-baseline/` |
| --- | --- |
| `academic.json` | 0 semantic deltas |
| `attendance.json` | Удалён явный `type: object` у `ErrorResponse.extras.additionalProperties` и `FieldError.rejectedValue`; 2 deltas |
| `auth.json` | Добавлены validation-required поля для `TmaAuthRequest`, `OtpVerifyRequest`, `OtpVerifyByCodeRequest`, `RefreshRequest`, `LoginRequest`, `ChangePasswordRequest`; 6 required-field deltas |
| `mobile-bff.json` | Удалены два времени `LessonSchedule.startsAt/endsAt.example`; 2 deltas |
| `notification.json` | Удалён явный map-value `type: object` у internal alert body и `NotificationHistoryDto.payload`; 2 deltas |
| `schedule.json` | Удалены четыре time examples (`startTime/ endTime` у двух схем); 4 deltas |

Final no-update command:

```text
.\gradlew.bat :services:academic-service:academic-app:integrationTest --tests "ru.rutcampustrack.academic.integration.OpenApiSnapshotIT" :services:attendance-service:attendance-app:integrationTest --tests "ru.rutcampustrack.attendance.integration.OpenApiSnapshotIT" :services:auth-service:auth-app:integrationTest --tests "ru.rutcampustrack.auth.integration.OpenApiSnapshotIT" :services:schedule-service:schedule-app:integrationTest --tests "ru.rutcampustrack.schedule.integration.OpenApiSnapshotIT" :services:notification-service:notification-app:integrationTest --tests "ru.rutcampustrack.notification.OpenApiSnapshotIT" :services:mobile-bff:mobile-bff-app:integrationTest --tests "ru.rutcampustrack.mobilebff.contractexport.OpenApiSnapshotIT" --rerun-tasks --no-parallel --console=plain
```

Exit `0`, duration `5m30s`, six suites / nine tests, failures `0`, errors `0`,
skipped `0`. Docker Testcontainers used the task-owned containers and cleaned
them on shutdown. The earlier sandbox-only invocation is retained in
`openapi-no-update-rerun.log` as environmental evidence: it exited `1` before
snapshot assertions because the Docker named pipe was unavailable inside the
sandbox.

`frontends/npm run generate:types` exited `0` and produced hash
`e991067b2f7b5fecda5bb1542f34586537e12771a83444733fb5d8f841b62b43`; the
subsequent `npm run generate:types:check` also exited `0` with the same hash.
The generated TypeScript change contains only the two `LessonSchedule` example
removals and the source hash header.

Raw Windows line-ending churn is not semantic drift. `git diff
--ignore-space-at-eol --numstat` reduces the mobile-BFF snapshot to `2/4`
lines and the generated TypeScript to `1/3`; the canonical no-update checks
compare normalized line endings on both sides. `academic.json` is byte-equal to
HEAD after Git's content comparison and is recorded as a generated
no-semantic-delta path in `source-manifest.json`.
