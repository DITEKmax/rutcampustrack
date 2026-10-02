# В — преподаватель читает историческую заявку и вложение активной группы

Source-ready: `a8de03a1`, comment-only `2e6cac79`. Normal merge of accepted main `492c4f89` is parent `2f495142`. Root integrates only the two scoped commits; UI `bb31a117` and worktree/merge ancestry stay separate. Risk S3: current read authorization for retained student evidence.

Пользовательский результат: преподаватель с текущим активным назначением в группе открывает историческую строку посещаемости, карточку оправдания и её доступный файл, даже если занятие состоялось до его назначения. Бывший преподаватель без текущего назначения в этой группе не получает карточку или байты. Занятия чужой группы исключаются из смешанной заявки. Права изменять посещаемость/занятия/ДЗ не расширены.

Основание: решение владельца `docs/product/decisions/2026-09-23-teacher-replacement-and-group-attendance.md`, актуальный root contract; root открыл критичные исходники и подтвердил `job-stories.md:1009/1016` (заявка и вложения из ячейки для старосты/преподавателя).

Основная логика существующего reader:

```java
LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
reportService.authorizeTeacherLesson(lesson, teacherId);
```

Карточка и download переиспользуют ту же текущую group authority, что и журнал. Проверяется каждое занятие, чужой scope исключается. Существующие проверки request ID, owner student ID, descriptor/document ACTIVE и expiry сохранены. Неиспользуемый прежний own-lesson helper и только его LessonResponse matcher удалены; LessonInfo matcher статистики сохранён.

Изменены product/source (3):

- [TeacherAttendanceReadGrpcService.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java:520) — ticket projection использует существующий current-group gate для detail и download.
- [ReportService.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java:1234) — удалён больше не используемый historical own-lesson read helper с его matcher; исправлен устаревший комментарий reader.
- [teacher_reads.proto](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/proto/teacher_reads.proto:20) — одна строка комментария о current group authority; RPC/поля/контракт неизменны.

Изменены tests/fixture (2):

- [TeacherAttendanceReadGrpcServiceTest.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcServiceTest.java:52) — existing mixed projection fixture согласована с current-group gate; test-only Context fixture переиспользуется IT, production identity keys остаются package-private. Новых unit методов нет.
- [StudentRequestDomainIT.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927/services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestDomainIT.java:1085) — одна реальная Mongo проверка actual reader + actual ReportService + actual AttendanceReadPort + сохранённые заявка/строка/байты. Active replacement teacher читает старую строку, detail и exact bytes; чужая группа отсутствует в detail; inactive former teacher получает NOT_FOUND для detail/download; сохранённый файл после чтения неизменен.

Созданы evidence (8): SUMMARY.md; r1-start.txt, r1-end.txt, r1-exit.txt, r1-stdout.log, r1-stderr.log, r1-StudentRequestDomainIT.xml, cleanup.txt. Удалённых файлов нет. Чужой `.agent/transfer-attendance-evidence.md` и остальные unrelated WIP сохранены.

Проверка:

- Scoped `git diff --check` PASS. Root сообщил fresh independent `review_teacher_read_1002` PASS на `a8de03a1`, без blockers. Единственный nonblocking устаревший proto comment исправлен отдельно `2e6cac79` по явному расширению scope.
- ONE HEAVY session `68716`, source `a8de03a1` + comment-only `2e6cac79`, терминальный exit0, `BUILD SUCCESSFUL in 2m 4s`. Attendance compileJava и compileTestJava PASS.
- Свежий actual XML timestamp `2026-10-02T10:49:34`, last-write local `13:49:55`: tests=1, failures=0, errors=0, skipped=0; exact selected testcase `activeGroupTeacherReadsHistoricalTicketAndBytesWhileFormerTeacherIsDenied()` time 2.107s. Log/XML сохранены, broad suites/старые PASS не повторялись.
- Approved upfront escalation применяется только из-за ранее подтверждённого sandbox generated-JAR/cache access; no ACL/file-clean workaround. Testcontainers reuse=false; task-owned UUID Mongo container/database.
- После terminal read-only Docker inventory `docker ps -a --filter label=org.testcontainers=true` exit0 EMPTY. Собственных live process/container нет; heavy lease RELEASE.

Exact command (assigned worktree, PowerShell; stdout/stderr направлены в r1 logs):

```powershell
$env:TESTCONTAINERS_REUSE_ENABLE = 'false'
.\gradlew.bat :services:attendance-service:attendance-app:compileJava :services:attendance-service:attendance-app:compileTestJava :services:attendance-service:attendance-app:integrationTest --tests '*StudentRequestDomainIT.activeGroupTeacherReadsHistoricalTicketAndBytesWhileFormerTeacherIsDenied' --continue --no-daemon --no-parallel --max-workers=1 --no-problems-report --system-prop=org.gradle.java.compile-classpath-packaging=true
```

Достаточный уровень — реальная Mongo для изменённого read ACL и bytes. External Academic/Schedule responses и authenticated gRPC Context являются scoped test fixtures; это не live HTTP admission/JWT validation acceptance. Изменение серверное, bounded: не полная готовность backend/stage В; no UI/Figma, schema/proto contract, migrations, production data, новые write grants, push/deploy.
