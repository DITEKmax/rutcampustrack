# В — точная пара вложения и выполненность после отмены

Завершено и интегрировано root: main `c3db875f` + `920bfc8f`, runtime `7bed9a98` + `14b67750`. Scoped source commit `8bf60228`, fixture-only correction `9aa19043`. Parent `eb5ecba6` is the normal merge of accepted main `9aaf5889`; only the two scoped commits were integrated, without worktree/merge/UI ancestry. UI `bb31a117` remains separate. Risk S3: cross-user data isolation, transactional cancellation, historical rows.

Пользовательский результат:

- Староста принимает решение по заявке: переход посещаемости очищает журнальное вложение только нужной пары student/lesson. Перекрёстная пара с обменёнными ID и request-owned evidence сохраняют свои байты.
- Студент отправляет «Выполнено» или снимает выполненность из устаревших данных после отмены пары: сервер блокирует запись в уже архивированное ДЗ, сохраняя прежние строки выполненности и их timestamps.

Основная логика:

```java
journalAttachmentPort.delete(semesterId, lessonId, studentId, groupId);
archiveCoordinator.lockAndRefresh(homework);
// Existing group/semester/ACTIVE checks then precede completion writes.
```

Canonical journal delete order matches the existing adapter and majority of writers: semester, lesson, student, group. The two request-domain callers now follow it; no adapter behavior or existing correct callers changed. Homework completion reuses `HomeworkBindingArchiveCoordinator.lockAndRefresh`: existing semester fence -> binding advisory lock -> EntityManager refresh. Group/semester/ACTIVE validation uses the refreshed entity; current membership/read-only checks and semester archive guards remain.

Modified product (3):

- [JournalAttachmentPort.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/shared/port/JournalAttachmentPort.java): canonical parameter semantics aligned with existing implementation.
- [StudentRequestService.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java): two journal delete calls corrected to the exact operational pair.
- [HomeworkStudentService.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java): existing binding coordinator injected/reused before reading mutable publication state.

Modified tests/fixtures (3):

- [StudentRequestDomainIT.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java): one real Mongo regression exercises direct excuse and late-request decisions, exact and crossed ID pairs, preserved foreign bytes and request evidence.
- [HomeworkStudentCompletionConcurrencyIT.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentCompletionConcurrencyIT.java): one parametrized real PG regression loads ACTIVE into the first transaction's persistence context, commits actual cancellation in another transaction, then requires complete/uncomplete rejection and unchanged stored completion rows. Fixture creates the accepted active student role grants with required timestamps.
- [HomeworkStudentServiceTest.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/homework/HomeworkStudentServiceTest.java): constructor fixture updated; existing stale primary-role/status stubs aligned with current signed-role/active-grant validation. No new unit methods or additional unit run.

Created evidence in this directory (12): SUMMARY.md; r1-start.txt, r1-exit.txt, r1-stdout.log, r1-stderr.log, r1-StudentRequestDomainIT.xml, r1-HomeworkStudentCompletionConcurrencyIT.xml; r2-start.txt, r2-exit.txt, r2-stdout.log, r2-stderr.log, r2-HomeworkStudentCompletionConcurrencyIT.xml. Deleted: none. Foreign `.agent/transfer-attendance-evidence.md` and other untracked files preserved.

Verification and corrections:

- Fresh independent source review root-reported PASS at `8bf60228`; affected independent fixture recheck root-reported PASS at `9aa19043`.
- R1 session `99472`, source `8bf60228`, one sequential Gradle batch: affected Attendance/Academic compileJava and compileTestJava PASS; only two agreed IT selectors. Actual fresh Mongo XML tests=1/failures=0/errors=0/skipped=0. PG two parameters failed BEFORE the race in `@BeforeEach`: new user_role_grants fixture omitted mandatory created_at/updated_at. Classification: fixture-only, actual V24 schema NOT NULL. Original log/XML preserved, product code unchanged.
- Correction `9aa19043` adds only those timestamps with NOW(), NOW() to the fixture INSERT. No weakened assertion, product retry alteration, migration or new test.
- R2 session `38294`, final `9aa19043`: Academic compileTestJava plus ONLY affected PG selector. Actual fresh XML tests=2/failures=0/errors=0/skipped=0, completed=true 0.85s, completed=false 0.149s. PASS exit0. Mongo PASS not repeated.
- R1 build 2m44s; R2 build 1m20s. Both use --no-daemon --no-parallel --max-workers=1 --no-problems-report --system-prop=org.gradle.java.compile-classpath-packaging=true, Testcontainers reuse=false, approved escalation for known generated-JAR/cache access. No ACL/file-clean workaround.
- Exact selectors: `*StudentRequestDomainIT.directRequestDecisionsDeleteOnlyJournalBytesForTheExactStudentLessonPair`; `*HomeworkStudentCompletionConcurrencyIT.cancellationCommittedAfterActiveSelectionRejectsCompletionWithoutChangingStoredRows`. Existing accepted geo/request/retention cases and broad suites were not rerun.
- Elevated read-only Docker inventory `docker ps -a --filter label=org.testcontainers=true` returned EMPTY after each terminal run. No owned process/container remains; heavy lease released.

Final product hashes: JournalAttachmentPort `47925FBC145B0AFBC68D49F95802DB2F27B53AE5115ACC7A8D78AB541636B74E`; StudentRequestService `29AE9C0150F36B9E7E84D46BE963704EE0CAB0FBD0EC920F78698BC582EF1077`; HomeworkStudentService `F56B8CA6BDD149A11166B9C2B223C706A0F5D1722E06127B8B9B381B27969B2F`. R2 changed no product bytes.

Limits: bounded server package, not complete backend/stage В or production acceptance. No frontend/Figma, HTTP end-to-end pass, migrations/proto/new layers, production data, push or deploy. Existing accepted geo pending/retry evidence remains reused: `.agent/evidence/geo-pending-20260930/root-targeted-summary.json`.
