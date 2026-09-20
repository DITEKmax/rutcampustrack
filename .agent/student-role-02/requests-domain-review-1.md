**FAIL** — независимый S3 review выявил 4 HIGH и 2 MEDIUM доменных дефекта. После исправления обязательна свежая независимая recheck затронутого diff.

Стабильность цели подтверждена: `HEAD 8002b9ea4356b10779c5bb9a6d99746d32d78ae2`, все 28 product-файлов совпали с SHA-256 [manifest](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/pause-2026-09-07-1115/requests-domain/manifest.json). Критичные оригиналы открыты независимо: [frozen contract](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/requests-domain-packet.md:19), [source resolution](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/excuse-source-resolution.md:23), [priority source](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/architecture/reference-rutcampustrack-design/backend-conflicts.md:550).

1. **HIGH — доменная граница непригодна для заявленного gRPC/Rabbit transport.**  
   File: [StudentRequestService.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:139), [RequestContext.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/security/RequestContext.java:17).  
   Evidence: все публичные операции читают servlet request-scoped `RequestContext`; существующий gRPC путь получает validated claims через `io.grpc.Context` и передаёт явный immutable `Identity` в [AttendanceStudentGrpcServiceImpl.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java:52).  
   Impact: последующий gRPC/Rabbit adapter получит `ScopeNotActiveException` либо будет вынужден подделывать servlet context, разрушая auth boundary.  
   Repro: вызвать `submitExcuse`, `list` или decision method из gRPC/event thread без активного HTTP request.  
   Correction: явный immutable identity/actor input по образцу `StudentCheckinModels.Identity`; проверка role/group/headman внутри domain, а internal decision actor — по persisted resource group, без fake servlet context.

2. **HIGH — terminal events нарушают собственные JSON schemas.**  
   Files: [StudentRequestService.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:331), [LateCheckinEventPublisher.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/latecheckin/LateCheckinEventPublisher.java:78), [late_checkin.decided.json](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/event-schemas/late_checkin.decided.json:60), [ExcuseEventPublisher.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseEventPublisher.java:152), [excuse.decided.json](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/event-schemas/excuse.decided.json:45).  
   Evidence: late cancellation emits `cancelled_by_student`, schema permits `student_cancelled`; EXCUSE cancellation emits status `cancelled`, schema разрешает только approved/rejected; optional null decision comment всегда помещается в payload, но schema принимает только string.  
   Impact: durable outbox содержит события, которые schema validator/consumer может отвергнуть или отправить в DLQ.  
   Repro: schema-validate emitted events для manual late cancellation, EXCUSE cancellation и EXCUSE decision с null comment.  
   Correction: закрепить одно wire-value для student cancellation, добавить `cancelled` в excuse schema и согласовать nullable/omitted comment.

3. **HIGH — староста может принять решение по собственной заявке.**  
   File: [StudentRequestService.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:1023).  
   Evidence: проверяются caller/headman/group, но actor не сравнивается с `ticket.studentId`/`request.studentId`; исходный guard сохранён в [ExcuseService.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/excuse/ExcuseService.java:293).  
   Impact: пользователь, ставший старостой после submit, может одобрить собственный EXCUSE или late request.  
   Repro: SUBMITTED/PENDING request с owner=900, context user=900/group=10/STUDENT/headman=true, затем `decide*`.  
   Correction: до locks/writes отвергать validated actor, совпадающего с owner, для обоих kinds.

4. **HIGH — EXCUSE approval может вернуть ложный успех без отметки «у».**  
   File: [StudentRequestService.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:1041).  
   Evidence: после pair-lock re-read запись EXCUSED выполняется только для null/ABSENT. Принятый источник требует, чтобы тикет перекрывал non-PRESENT состояния.  
   Impact: тикет становится APPROVED, но журнал может остаться `FREE_ATTENDANCE`.  
   Repro: submit на ABSENT, pair-coordinated writer меняет запись на FREE_ATTENDANCE, затем approve.  
   Correction: сохранять PRESENT; переводить применимые live non-PRESENT состояния, включая FREE_ATTENDANCE, в EXCUSED. CANCELLED lesson должен обрабатываться отдельно согласно lifecycle отменённой пары.

5. **MEDIUM — options и enforcement расходятся по бюджету.**  
   File: [StudentRequestService.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:706).  
   Evidence: `lateCheckinEligible` не учитывает remaining; enforcement жёстко сравнивает used с 5, хотя options показывает persisted `limit`.  
   Impact: UI разрешает submit при remaining=0; после увеличения лимита показывает оставшиеся попытки, которые сервер не принимает.  
   Repro: budget `{limit:5,used:5}` или `{limit:7,used:5}`, CLOSED/ABSENT lesson, затем options и submit.  
   Correction: единый effective persisted limit с default=5, атомарное условное списание и `lateCheckinEligible=false` при remaining=0.

6. **MEDIUM — detail не применяет логическое истечение вложения.**  
   File: [StudentRequestService.java](/C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-domain/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java:938).  
   Evidence: download проверяет `now >= expiresAt`, а detail возвращает persisted `ACTIVE` до ежедневного sweeper. Тест сначала вызывает download и тем самым скрывает расхождение.  
   Impact: карточка показывает активное вложение, но скачивание сразу отвечает 410.  
   Repro: перевести clock ровно на `expiresAt`, вызвать только `get(requestId)`.  
   Correction: проецировать logical EXPIRED по clock до sweeper, сохраняя metadata; download остаётся 410, bytes очищает scheduled job/согласованный expiry path.

Repair contract:

- **Defect/evidence:** шесть дефектов выше на стабильном 28-file diff.
- **Correction:** только domain identity/authz, terminal schemas/publishers, EXCUSE priority, budget и attachment projection.
- **Scope:** `studentrequest/**`, связанные domain models/tests, event publishers/schemas; без legacy routes, BFF/frontend, bot delivery и proto transport.
- **Verification:** contract tests трёх невалидных event cases; negative self-decision и non-servlet identity tests; budget exhausted/increased cases; logical detail expiry; real Mongo barrier tests cancel-vs-decision и approval-vs-PRESENT/FREE_ATTENDANCE с фактическим outbox.
- **Independent recheck:** обязательный fresh reviewer после нового stable manifest/diff и успешных checks.

Текущий evidence подтверждает Mongo replica set 10/10 и focused suites 45/45, то есть 55 тестов суммарно. Записи, называющие focused run «55», арифметически неверны. Кроме того, текущие 10 Mongo cases не воспроизводят конкурентные cancel/approval и PRESENT races, хотя это требуется acceptance contract.

Известные legacy-create, EventConsumer, notification-bot, public transport и proto `PRESENT_PRIORITY` gaps остаются downstream scope и не использовались как дополнительные причины FAIL.
