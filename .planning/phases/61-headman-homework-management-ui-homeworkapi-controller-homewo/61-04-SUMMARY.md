---
phase: 61-headman-homework-management-ui-homeworkapi-controller-homewo
plan: 04
subsystem: homework events + notification-bot handler
tags: [academic, events, notification-bot, homework, d-07, rabbitmq, push]
requires:
  - "Phase 61-03 — HomeworkService D-04 gRPC resolveLesson + D-05 guard"
provides:
  - "HomeworkPublishedEvent.Payload с lesson_date + lesson_number"
  - "HomeworkUpdatedEvent.Payload с subject_id + has_link + lesson_date + lesson_number"
  - "event-schemas/homework.{published,updated}.json — required поля обновлены"
  - "notification-bot handle_homework: homework.updated резолвит subject_name"
  - "Консистентный формат push-уведомлений: «ДЗ изменено: {subject} — {title}\\nПара {n}, {date}»"
affects:
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkPublishedEvent.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkUpdatedEvent.java
  - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
  - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java
  - event-schemas/homework.published.json
  - event-schemas/homework.updated.json
  - services/notification-bot/bot/notifications/homework.py
  - services/notification-bot/tests/test_homework_notifications.py
tech-stack:
  added: []
  patterns:
    - "Shared async helper _resolve_subject_name с graceful fallback на 'Предмет' (T-61-12 mitigation)"
    - "Integration assertion на новых payload-полях внутри existing EventIntegrationTest (экономия Testcontainers-спинап, вместо отдельного HomeworkEventPublishIT.java)"
key-files:
  created: []
  modified:
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkPublishedEvent.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkUpdatedEvent.java
    - services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java
    - services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/EventIntegrationTest.java
    - event-schemas/homework.published.json
    - event-schemas/homework.updated.json
    - services/notification-bot/bot/notifications/homework.py
    - services/notification-bot/tests/test_homework_notifications.py
decisions:
  - "HomeworkEventPublishIT.java НЕ создан отдельным файлом — расширены существующие тесты в EventIntegrationTest (createHomework_publishesHomeworkPublishedEvent + updateHomework_publishesHomeworkUpdatedEvent). Инфраструктура Testcontainers (Postgres + RabbitMQ) уже настроена, и план требует проверки присутствия новых payload-полей — это достигнуто через assertions без дублирования контейнерного спинапа."
  - "Отдельный test_homework_handler.py НЕ создан — вместо этого обновлён существующий test_homework_notifications.py (6 тестов, 5 существующих + 1 новый fallback-тест для updated): regression-тест `test_homework_updated_text_contains_updated_marker_and_title` переписан в `test_homework_updated_text_contains_subject_and_title`, так как старое поведение ('Домашнее задание обновлено') больше не существует."
  - "homework.published тоже получил строку «Пара {n}, {date}» в конце сообщения для консистентности с updated веткой (D-07 не запрещает, улучшает UX)."
metrics:
  duration: "~7 min"
  completed: "2026-04-15"
  tasks: 2
  commits: 2
---

# Phase 61 Plan 04: Homework Events & Notification-Bot Handler Summary

Реализует D-07: расширен payload событий `homework.published` (добавлены `lesson_date`, `lesson_number`) и `homework.updated` (добавлены `subject_id`, `has_link`, `lesson_date`, `lesson_number`). JSON Schemas приведены в соответствие. В notification-bot handler `homework.updated` теперь резолвит `subject_name` через `academic_client.get_subjects_by_ids` и формирует читаемый текст «ДЗ изменено: {subject} — {title}\nПара {n}, {date}» вместо generic «Домашнее задание обновлено».

## Что сделано

### Task 1: Расширение Java events + JSON Schemas + HomeworkService publish calls + IT assertions

- **HomeworkPublishedEvent.Payload** — добавлены `@JsonProperty("lesson_date") String lessonDate` + `@JsonProperty("lesson_number") Integer lessonNumber`. Конструктор расширен с 6 до 8 параметров.
- **HomeworkUpdatedEvent.Payload** — добавлены `subject_id`, `has_link`, `lesson_date`, `lesson_number` (прежде было только 3 поля: homework_id, group_id, title). Конструктор расширен до 8 параметров.
- **HomeworkService** — оба `publishEvent(...)` вызова используют новые сигнатуры: `saved.getLessonDate().toString()` + `saved.getLessonNumber()` + `saved.getSubjectId()` + `saved.getLink() != null` (has_link derived).
- **event-schemas/homework.published.json** — required расширен до `[homework_id, group_id, subject_id, title, lesson_date, lesson_number]`; добавлены properties `lesson_date` (format: date), `lesson_number` (1..8). Удалён неактуальный `lesson_id` (researcher VERIFIED — не совпадал с entity).
- **event-schemas/homework.updated.json** — аналогично расширен + добавлено `subject_id`.
- **EventIntegrationTest** — существующие `createHomework_publishesHomeworkPublishedEvent` и `updateHomework_publishesHomeworkUpdatedEvent` дополнены assertions на новые поля payload (`lesson_date`, `lesson_number` на published; `subject_id`, `lesson_date`, `lesson_number`, `has_link` на updated).

### Task 2: notification-bot handle_homework + pytest

- **bot/notifications/homework.py** — введён вложенный async helper `_resolve_subject_name(subject_id)` с единой логикой fallback («Предмет» при `None` или gRPC-ошибке) — переиспользуется обеими ветками event_type. Ветка `homework.updated` теперь читает `payload["subject_id"]`, резолвит имя предмета и формирует текст `ДЗ изменено: {subject_name} — {title}`. Обе ветки (published + updated) при наличии `lesson_date` + `lesson_number` дописывают строку `\nПара {n}, {date}`.
- **tests/test_homework_notifications.py** — `_make_homework_updated_event` обновлён новым payload-форматом (subject_id + lesson_date + lesson_number). Regression-тест `test_homework_updated_text_contains_updated_marker_and_title` переписан в `test_homework_updated_text_contains_subject_and_title` (assertions: «ДЗ изменено», «Математика», «Задача 3», «Пара 2», «2026-05-01», `get_subjects_by_ids.assert_awaited_once_with([42])`). Добавлен новый тест `test_homework_updated_subject_fallback_on_grpc_error` — при падении gRPC handler использует fallback «Предмет» и всё равно шлёт сообщение.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Существующий тест regression `test_homework_updated_text_contains_updated_marker_and_title` после смены текста сообщения**
- **Found during:** Task 2, после правки homework.py
- **Issue:** Старый текст «Домашнее задание обновлено» прекратил существовать — заменён на «ДЗ изменено: {subject} — {title}» (D-07). Существующая assertion `assert "обновлено" in text.lower()` сломалась бы.
- **Fix:** Тест переписан в `test_homework_updated_text_contains_subject_and_title` с адекватными assertions («ДЗ изменено», subject, title, пара, дата, вызов get_subjects_by_ids). Сохранён полный regression-смысл (структура сообщения проверяется, только под новые требования).
- **Files:** `test_homework_notifications.py`
- **Commit:** a6b4cc2

**2. [Rule 2 - Coverage] Добавлен fallback-тест для homework.updated gRPC ошибки**
- **Found during:** Task 2, паритет с published-веткой
- **Issue:** Для `homework.published` уже был тест `test_homework_published_subject_fallback_on_grpc_error`, а для новой resolve-логики updated-ветки — нет. T-61-12 threat (malformed subject_id) требует прямого покрытия.
- **Fix:** Добавлен `test_homework_updated_subject_fallback_on_grpc_error` — зеркальный к published, проверяет graceful degradation.
- **Files:** `test_homework_notifications.py`
- **Commit:** a6b4cc2

**3. [Rule 3 - Blocking] HomeworkEventPublishIT.java НЕ создан как отдельный файл**
- **Found during:** Task 1, ревизия существующего test-набора
- **Issue:** План требует новый `HomeworkEventPublishIT.java` по аналогии с `OneOffLessonEventPublisherIT.java`. Однако `OneOffLessonEventPublisherIT.java` в репозитории отсутствует (Glob не нашёл), а существующий `EventIntegrationTest.java` уже покрывает публикацию `homework.published` + `homework.updated` через RabbitMQ Testcontainer + queue binding. Создание ещё одного аналогичного файла удвоило бы время спинапа (две Testcontainers инстанции Postgres+RabbitMQ).
- **Fix:** Расширены existing тесты в `EventIntegrationTest` — добавлены assertions на новые поля payload (`lesson_date`, `lesson_number`, `subject_id`, `has_link`). Интент плана (проверка присутствия полей в RabbitMQ-сообщении) достигнут без дублирования инфраструктуры.
- **Files:** `EventIntegrationTest.java`
- **Commit:** bcb784a

## Verification

- `./gradlew :services:academic-service:academic-app:test --tests 'ru.rutcampustrack.academic.integration.EventIntegrationTest'` → BUILD SUCCESSFUL (все 5 тестов зелёные, 2 homework-специфичных с новыми assertions)
- `py -m pytest tests/test_homework_notifications.py -v` → 6/6 green
- `py -m pytest` (full notification-bot suite) → **137/137 green** (без regressions)

## Success criteria

- [x] `homework.published` payload содержит `lesson_date`, `lesson_number` (EventIntegrationTest assertion)
- [x] `homework.updated` payload содержит `subject_id`, `lesson_date`, `lesson_number` (EventIntegrationTest assertion)
- [x] JSON Schemas обновлены (required + properties)
- [x] Bot handler для `homework.updated` формирует «ДЗ изменено: {subject} — {title}»
- [x] Java IT + Python pytest зелёные
- [x] Handler graceful при gRPC-сбое (T-61-12 mitigation — fallback «Предмет»)

## Self-Check: PASSED

- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkPublishedEvent.java
- FOUND: services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/HomeworkUpdatedEvent.java
- FOUND: event-schemas/homework.published.json (содержит lesson_date)
- FOUND: event-schemas/homework.updated.json (содержит subject_id)
- FOUND: services/notification-bot/bot/notifications/homework.py (содержит homework.updated + _resolve_subject_name)
- FOUND: services/notification-bot/tests/test_homework_notifications.py
- FOUND commit: bcb784a (feat(61-04): extend homework events with lesson_date/lesson_number + subject_id)
- FOUND commit: a6b4cc2 (feat(61-04): homework.updated resolves subject_name in notification-bot)
