# Reminder — точные получатели и retry до доставки

## Goal
Студент, уже отметившийся или выключивший reminders, не получает ложное напоминание через WS/WebPush. Недоступность проверки не превращается в разрешение доставки.

## Context/evidence
Принятое поведение: `docs/architecture/event-schemas.md:45`, `docs/architecture/architecture.md:458,465`; Schedule реально публикует `lesson.reminder` в фазах midpoint/near_end. До правки `ReminderAttendanceStateService.isMarked` преобразовывал Mongo read failure в false, а provider filter разрешал reminder. Проверка происходила после claim в async fanout; простой throw там не возвращал event в broker retry. Дополнительно group WS обходил per-user eligibility.

Source commits для точечного переноса: `37b5f15c93a68886ffd7bb6712e6ecc5dd348797` + `39b8dc1dc9d356d7a47b08c692291534cbf5a86f`. Baseline `aa4128886f61c0b0a301a06282d891311cf1e0dc` — обычный согласованный merge main `492c4f89603981f08fa1770aff6a3a2eb572ff8a`; всю ветку/merge переносить не требуется.

Независимое review, сообщённое root: первый freeze `37b5f15c` FAIL P1 — reminder resolver использовал общий preferences fail-open при недоступном Redis. Root разрешил reminder-only strict preferences read. Correction `39b8dc1d` affected recheck PASS, P1 закрыт. Root подтвердил, что легитимный пустой user hash сохраняет default enabled; failclosed относится к недоступному resolver/read.

## Relevant scope
Изменены четыре production-файла:

- `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/reminder/ReminderAttendanceStateService.java`: один строгий batch Mongo read, immutable разность current members минус marked IDs; exception не превращается в false.
- `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/preferences/NotificationPreferencesService.java`: reminder-only строгий read; Redis availability failures оборачиваются в retry-compatible `TransientDataAccessResourceException`. Общий `isEnabledForUser` не изменён.
- `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java`: positive lesson validation, current Academic → strict marks → strict preferences → immutable eligible IDs; default 3arg entry использует тот же resolver, 4arg async fanout использует snapshot без late eligibility reads.
- `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java`: только reminder получает sync gate до WS/commit, `/topic/user/{eligibleId}` и тот же snapshot для async WebPush; group reminder broadcast исключён; empty audience не вызывает WS/enqueue.

Изменены три существующих test-файла:

- `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/push/WebPushDeliveryServiceTest.java`: marked/preferences/current member filtering, immutable snapshot и отсутствие late reads, malformed lesson, отсутствующий prefs resolver, failure без provider send; необходимая адаптация существующих reminder fixtures.
- `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/EventConsumerTest.java`: personal WS и тот же snapshot, порядок resolver→WS→async enqueue, pending provider не блокирует return; lookup failure и empty audience ничего не отправляют.
- `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/event/EventIdempotentIT.java`: две parameterized проверки для marks/preferences: temporary recovery/replay и persistent failure/DLQ. Делегат запускает реальный resolver, реальный mark service/Mongo и реальную preferences implementation с injected Redis read failure; provider fanout mocked.

Новых/удалённых product/test файлов нет. Созданы только evidence: этот SUMMARY.md, results.json, r1 Gradle log/exit code, три исходных XML under r1-xml.

## Required behavior
Reminder eligibility вычисляется до любой WS-публикации и claim commit. Marked/current nonmember/opt-out исключены. Missing/malformed lesson и отсутствующие dependencies не разрешают delivery. Temporary Mongo/Redis failure откатывает claim; existing factory делает максимум 3 attempts с backoff; persistent failure сохраняет event в events DLQ без committed delivery claim. Provider остаётся async, join отсутствует. Персональные topics уже проверяют exact user identity и live admission; auth-контракт не изменяется.

## Constraints
Другие group/user/headman events и их preferences policy сохранены. История reminder не добавлялась: принятого требования её сохранять не найдено. Schema/proto, UI, Telegram, другие домены, deploy/push не изменялись. Прежние 31+4+8 проверки не повторялись. Sole writer WT, children не создавались.

## Existing patterns
Переиспользованы current Academic RPC gate, Mongo claim transaction, существующие personal STOMP topics, 4arg async provider entry и `notificationHistoryRabbitListenerContainerFactory` (3 attempts, Mongo/TransientDataAccess/Transaction exception retry, exhaustion → DLQ, no default requeue loop). Один batch mark query заменяет per-subscription reads. Query maxTime 3s ограничивает server execution; полного 3s network deadline этим не заявляется.

## Acceptance criteria
Unit actual resolver исключает marked 1, opt-out 2 и former member 4, сохраняя только 3; snapshot immutable и fanout не перечитывает marks/preferences. Missing/fractional/nonpositive lesson и missing prefs resolver failclosed.

Rabbit/Mongo recovery IT: current members 42/43/44, реально записанная отметка 42, реально прочитанный reminders=off для 43, default-enabled 44. Первый marks или Redis read failure не вызывает WS/enqueue; retry фиксирует ровно один claim, только `/topic/user/44`, только snapshot {44}. Provider future остаётся pending, claim уже committed. Replay не создаёт вторую публикацию/enqueue. Persistent marks или Redis failure: ровно 3 query attempts, ноль delivery claims/WS/enqueue, event с исходным ID удержан в events DLQ.

## Verification
Source `git diff --check`: PASS. Fresh affected independent recheck: PASS, root-reported. Один согласованный heavy run, JDK `C:/Users/maksd/.jdks/ms-21.0.10`, `TESTCONTAINERS_REUSE_ENABLE=false`; upfront root-authorized require_escalated из-за ранее установленного sandbox access failure generated JAR.

```text
./gradlew.bat :services:notification-service:notification-app:compileJava :services:notification-service:notification-app:compileTestJava :services:notification-service:notification-app:test --tests '*.EventConsumerTest.lessonReminder*' --tests '*.WebPushDeliveryServiceTest.sendToGroup_lessonReminder*' --tests '*.WebPushDeliveryServiceTest.reminderAudience*' --tests '*.WebPushDeliveryServiceTest.reminderMalformedLesson*' :services:notification-service:notification-app:integrationTest --tests '*.EventIdempotentIT.reminder*' --no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true
```

Session 19026: terminal exit 0, BUILD SUCCESSFUL 1m46s. Notification compileJava/compileTestJava PASS. Fresh unit XML: EventConsumerTest 2, WebPushDeliveryServiceTest 8; timestamps `2026-10-02T10:56:32/35`, LastWrite UTC `10:56:38`. Fresh EventIdempotentIT XML: 4, timestamp `2026-10-02T10:57:34`, LastWrite UTC `10:57:40`. Все failures/errors/skips 0. Три неизменённых XML скопированы в r1-xml. Baseline и terminal Testcontainers inventory EMPTY; Java процессов после terminal run нет. Heavy lease освобождён. Повторных/расширенных запусков не было.

## Do not / limitations
Это proof серверного решения recipients, реальной Mongo transaction и Rabbit retry/DLQ. Redis failure инъецирован в реальную preferences implementation, не заявляется внешняя Redis outage кампания. Provider send/Telegram и браузерный UI не проверялись; нет внешней exactly-once гарантии. Marks читаются из существующего `reminder_attendance_state` projection. Snapshot не отменяет отправку, уже запланированную до новой отметки/смены настройки/отзыва; задержка проекции не устраняется этим scope. PWA existing checkin handler не показывает reminder — client UI остаётся отдельно, принятого UI-результата здесь не заявляем.
