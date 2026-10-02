# Отмена разовой пары — личная история

## Goal
После удаления разовой пары событие `lesson.one_off.cancelled` сохраняется в личной истории адресатов и доступно при последующем входе.

## Context/evidence
Принятый тип описан в `event-schemas/lesson.one_off.cancelled.json` и реально публикуется Schedule `OneOffLessonService` через `OneOffLessonCancelledEvent`. Live delivery уже поддерживает этот тип. History processor ранее возвращал пустой mapping, фиксируя claim без записи истории. Решение владельца `docs/product/decisions/2026-09-25-notification-history-membership.md` требует личной сохраняемой истории и адресатов по дате события.

Source commit: `b64fec68a7a8e9c3fd55f2ae955db18c81d2e618`. Baseline: `fd9908bdb29cf6f0e69b743b744b99cd4d1fc217`, обычный merge принятого main `9aaf5889db29c2646c1d8a441355b368df9e81a8`. Для main переносится только source commit, не вся ветка. Root сообщил fresh independent review `review_e_history_1002`: PASS, blockers отсутствуют.

## Relevant scope
Изменены ровно два существующих файла:

- `services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryEventProcessor.java`: mapping в существующий `LESSON_CANCELLED`, включение в существующую dated group branch и whitelist `group_id`, `subject_id`, `date`, `lesson_number`.
- `services/notification-service/notification-app/src/test/java/ru/rutcampustrack/notification/history/NotificationHistoryConsumerIT.java`: один сценарий `oneOffCancellationKeepsDatedRecipientHistoryAndReadStateOnReplay`.

Созданных/удалённых product/test файлов нет. Evidence этого scope: данный SUMMARY.md, results.json, r1/r2 Gradle logs и exit codes, один неизменённый XML под r2-xml.

## Required behavior
Адресаты определяются существующим Academic lookup as-of `occurred_at`, с существующей календарной зоной Europe/Moscow. Дата самой отменённой пары не заменяет дату события. История содержит минимальный snapshot реальных полей producer без выдуманного `lesson_id`. Поздний участник не получает старую запись. Committed replay не создаёт дублей, новых адресатов и не сбрасывает прочтение.

## Constraints
Schema, proto, provider/live delivery, membership logic, UI и другие scopes не изменены. Общий lookup retry/rollback остался в уже проверенной общей dated group branch; отдельного повторного failure test не добавлялось. Внешний Telegram/provider остаётся за пределами этого scope.

## Existing patterns
Переиспользованы `GROUP_EVENT_TYPES`, `groupDisplayPayload`, `mapType`, существующая Mongo transaction с event claim и уникальными recipient rows. IT использует существующие настоящие Rabbit/Mongo containers и mock только Academic authority/provider.

## Acceptance criteria
В IT published envelope имеет occurred_at `2026-04-24T21:30:00Z`, то есть Moscow date `2026-04-25`, а lesson date `2026-05-01`. История содержит две строки для 42/43 и не содержит 44, вступившего позже. Payload равен четырём разрешённым полям; `semester_id`, неизвестный `private_detail` и `lesson_id` отсутствуют. После markRead и replay те же две строки, тот же ID/readAt/payload и один claim, повторного authority lookup нет.

## Verification
`git diff --check`: PASS до freeze. JDK `C:/Users/maksd/.jdks/ms-21.0.10`; `TESTCONTAINERS_REUSE_ENABLE=false`. Root выделил один heavy lease. Exact invocation в назначенном worktree:

```text
./gradlew.bat :services:notification-service:notification-app:compileJava :services:notification-service:notification-app:compileTestJava :services:notification-service:notification-app:integrationTest --tests '*.NotificationHistoryConsumerIT.oneOffCancellationKeepsDatedRecipientHistoryAndReadStateOnReplay' --no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true
```

R1 session 90498: exit 1, BUILD FAILED 28s до Notification compile/test на `auth-api-contract:compileJava`: AccessDeniedException существующего generated `shared-web-api-0.1.0.jar`. Свежего XML/тестового результата нет. Diagnostic: jar читается File.OpenRead, ACL разрешает CodexSandboxUsers Modify и owner FullControl, живых Java процессов после terminal failure нет. Никакие ACL, product source или чужие файлы не изменялись.

Root разрешил один corrected retry exact same command через require_escalated для sandbox compiler access. R2 session 77337: exit 0, BUILD SUCCESSFUL 1m27s. Notification compileJava/compileTestJava PASS. Fresh XML: tests 1, failures 0, errors 0, skipped 0; timestamp `2026-10-02T10:12:21`, LastWrite UTC `2026-10-02T10:12:25`. Копия исходного XML сохранена в r2-xml. Baseline и terminal `docker ps -a --filter label=org.testcontainers` EMPTY; Java процессов после terminal run нет. Heavy lease освобождён. Более широкие или повторные прошедшие тесты не запускались.

## Do not / limitations
Доказательство покрывает настоящий broker и persisted Mongo history для нового события; Academic датированная аудитория в этом IT задана mock согласно принятому контракту. Новый браузерный вход, внешний Telegram/WebPush provider и production deployment не выполнялись. Записи ранее пропущенных и уже claimed событий не backfill-ятся этой правкой; исправление действует для новых событий. Нет заявления внешней exactly-once delivery.
