# Notification WebSocket isolation — 2026-09-27

## Compact contract

1. **Goal.** Личные решения по пропускам/опозданиям, отметки и домашние напоминания получает только соответствующий студент; клиент не может публиковать broker-сообщения.
2. **Context/evidence.** База `fb45dc0d137b269c35595d7e370e774c21018077`: `EventConsumer` отправлял любой event с `group_id` в общий group topic; `SubscriptionAuthInterceptor` пропускал незнакомые SUBSCRIBE и все SEND. `TicketHandshakeInterceptor` уже записывает `user_id`, `group_id`, `role`, `is_headman` в session attributes. `ExcuseEventPublisher.publishDecided` подтверждает реальную форму `excuse.decided`: `user_id` есть, `group_id` нет. Канонический `RULES.md` SHA256: `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.
3. **Relevant scope.** Sole writer в `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/teacher-export-ui-20260924`, branch `codex/notification-ws-isolation-20260927`, baseline `fb45dc0d`. Только `EventConsumer`, `SubscriptionAuthInterceptor` и их целевые тесты плюс эта заметка.
4. **Required behavior.** Пять существующих user-scoped event types идут на `/topic/user/{positive user_id}`; личный маршрут работает и без `group_id`. Положительный точный integral ID обязателен, fallback в group запрещён. Shared fanout ограничен существующими group notification types; два `*.requested` остаются на headman destination. SUBSCRIBE принимает только exact own-user, own-group или разрешённый headman destination. Неизвестные/wildcard/malformed destinations и все client SEND отклоняются. Push invocation/audience остаются на существующем `WebPushDeliveryService` и проходят только с валидным group ID, как прежде.
5. **Constraints.** Используются существующие ticket session attributes, broker и `WebPushDeliveryService`; без JWT в URL, новой auth-схемы, очереди, proto или frontend-изменений. Production операции не выполнялись.
6. **Existing patterns.** User-scoped типы совпадают с `WebPushDeliveryService.USER_SCOPED_EVENT_TYPES`; group whitelist соответствует его текущим shared `PUSH_EVENT_TYPES`, исключая личные типы. Headman routing оставлен по `HEADMAN_ONLY_EVENTS`.
7. **Acceptance criteria.** `excuse.decided` без group ID доставляется только user destination; напоминание с group ID доставляется персонально и сохраняет push-вызов; invalid IDs/неизвестные события не отправляются в group; доступ проверяется по ticket identity; forged SEND и неподдерживаемые подписки отклоняются.
8. **Verification.** Один focused Gradle batch и `git diff --check`; ниже указаны revision, команда, exit code, среда и test-result XML. Независимый Sol review ожидает stable commit.
9. **Do not.** Не менять push-контракт/получателей, не рассылать личные payloads в group, не принимать произвольные broker destinations и не объявлять полный realtime/production readiness.

## Checks and runtime evidence

- `.\gradlew.bat :services:notification-service:notification-app:test --tests "ru.rutcampustrack.notification.event.EventConsumerTest" --tests "ru.rutcampustrack.notification.config.SubscriptionAuthInterceptorTest" --tests "ru.rutcampustrack.notification.config.WebSocketConfigTest" --system-prop=org.gradle.java.compile-classpath-packaging=true --no-daemon --no-parallel --max-workers=1 --no-problems-report` — **exit 0**, Windows PowerShell, Gradle wrapper 8.12, OpenJDK `21.0.10` Microsoft build; session `4387` завершён. Результат: `EventConsumerTest` 20/20, `SubscriptionAuthInterceptorTest` 4/4, `WebSocketConfigTest` 1/1, всего 25/25, failures/errors/skipped = 0. JUnit evidence: `services/notification-service/notification-app/build/test-results/test/TEST-ru.rutcampustrack.notification.event.EventConsumerTest.xml`, `services/notification-service/notification-app/build/test-results/test/TEST-ru.rutcampustrack.notification.config.SubscriptionAuthInterceptorTest.xml`, `services/notification-service/notification-app/build/test-results/test/TEST-ru.rutcampustrack.notification.config.WebSocketConfigTest.xml`.
- Критерий маршрута проверен на реальном вызове `EventConsumer` с Mockito-captured destination и полным `{type,payload}`: `excuse.decided` с producer payload без group ID → `/topic/user/7`; `homework.due_reminder` → `/topic/user/7` и прежний `sendToGroup(42, ...)`. Negative cases покрывают invalid IDs, чужой user/group, неизвестные/wildcard/malformed destinations и SEND.
- `git diff --cached --check` для итоговых пяти staged-файлов — **exit 0**.
- Компиляция вывела removal deprecation warnings только в неизменённых `PushMongoConfig.java` и `NotificationHistoryMongoConfig.java`, плюс стандартный JVM CDS warning. Они не связаны с заданным поведением и не менялись.

## Diff and limitations

- `EventConsumer.java`: точные user/group ID; персональная маршрутизация; allowlist; неизвестный fanout игнорируется; push сохраняет прежнего получателя.
- `SubscriptionAuthInterceptor.java`: точная проверка ticket identity и известных destination; fail closed для неизвестного SUBSCRIBE и SEND.
- `EventConsumerTest.java`: фактическая форма `excuse.decided`, private routing, push сохранение, invalid ID и unknown event.
- Новый `SubscriptionAuthInterceptorTest.java`: own/foreign user, group/headman permission, malformed/unknown/wildcard destinations и SEND.
- Полный Spring WebSocket endpoint и клиентский UI-flow не запускались; тесты проверяют фактический output EventConsumer и interceptor отдельно, но не browser/TMA delivery. Отзыв уже открытых WS-сессий после ticket validation не входит в scope и не проверен. Эти результаты не являются заявлением о полной production readiness.
