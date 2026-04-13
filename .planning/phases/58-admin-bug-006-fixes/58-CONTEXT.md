# Phase 58: Admin Functionality — BUG-006 Fixes — Context

**Gathered:** 2026-04-14 (по результатам разведки в рамках сессии багфиксов)
**Status:** Ready for planning
**Source bug-report:** `.planning/bug-reports/BUG-006-admin/report.md` (с секцией «Ответы автора»)

<domain>
## Phase Boundary

Семь блоков работ в admin-разделе web-panel и academic-service, найденных при ручном QA после v9.0:

1. **Поиск пользователей** — серверный фильтр по login/ФИО/группе.
2. **Сообщения об ошибках при создании пользователя** — backend возвращает 500 при unique-нарушениях, фронт показывает generic «Не удалось сохранить».
3. **Telegram ID студента** — обязательное поле при создании; админ может редактировать.
4. **Init password в списке пользователей** — backend уже отдаёт `initialPassword` в `UserResponse` (Фаза B багфиксов), но frontend-тип/таблица не подхватили.
5. **Группы: единое поле «Название»** — слить `name` и `code` в одно поле, удалить дублирование. Производственных данных нет (только тестовая группа), миграция допустима.
6. **Автопереименование групп при переходе курса** — за 2 недели до начала осеннего семестра группа XXXX1-NNN становится XXXX2-NNN и т.д. Сейчас НЕ реализовано.
7. **Семестры — правила валидации** — запрет создания с прошлыми/перекрывающимися датами; запрет редактирования завершённых.

**Out of scope:**
- Тикеты о пропуске (BUG-008 backend) — Фаза 59.
- Любые UI-косметические правки админа (уже сделаны в Фазе A багфиксов 2026-04-13).
- Профиль/аватары (Фаза B багфиксов).
</domain>

<decisions>
## Implementation Decisions (зафиксированы пользователем 2026-04-14)

### Поиск пользователей (BUG-006 п.1)
- **D-01:** Параметр `search` (опциональный) добавляется в `UserApi.listUsers()` контракт + `UserService` + `UserRepository`.
- **D-02:** Реализация — JPA Specification API с `LIKE` по `lower(login)`, `lower(last_name||' '||first_name||' '||COALESCE(middle_name,''))`. Группа — отдельный фильтр (опционально потом). Учитывать prepared-statement индекс по `lower(...)` если профайлинг покажет проблему.
- **D-03:** Frontend `users-page.component.ts` уже шлёт параметр — backend должен начать его уважать.
- **D-04:** Ожидаемая нагрузка: 300–8000 пользователей. ILIKE достаточно, full-text не нужен.

### Обработка ошибок создания (BUG-006 п.2)
- **D-05:** В `GlobalExceptionHandler` добавить `@ExceptionHandler(DataIntegrityViolationException.class)` → HTTP 409 Conflict + RFC7807 ProblemDetail с детализацией поля (login / email / telegram_id / employee_number).
- **D-06:** Frontend `user-dialog.component.ts` — отдельные сообщения по 409 (с распознаванием поля из `detail`), общее по 500.
- **D-07:** Дополнительная защита на уровне сервиса — проверять уникальность telegramId/email до save() и кидать осмысленный exception (`ConflictException`?).

### Telegram ID студента (BUG-006 п.3)
- **D-08:** `CreateUserRequest.telegramId` — обязательное **только для STUDENT**. Для TEACHER/ADMIN остаётся опциональным.
- **D-09:** Валидация — `@AssertTrue` метод в DTO, либо сервисная проверка (рекомендуется второй вариант — проще тестировать).
- **D-10:** Frontend `user-dialog.component.ts` — поле телеграма отображается всегда, помечается required при `role=student`. Подсказка «Без Telegram ID студент не сможет получать уведомления и подтверждать через бота».
- **D-11:** Уникальность telegram_id — уже есть в БД (UNIQUE). Поведение при повторе — 409 (через D-05).

### Init password в списке (BUG-006 п.4)
- **D-12:** TS-тип `UserResponse` (admin/shared/types.ts) — добавить `initialPassword?: string | null`.
- **D-13:** В `users-page.component.html` — новая колонка «Начальный пароль» (только admin). Если null — прочерк. Иначе — мoноширинный текст + кнопка копирования.
- **D-14:** Колонка скрывается, если нет ни одного пользователя в выдаче с непустым `initialPassword` (защита от мусора в шапке).

### Группа = единое поле (BUG-006 п.5)
- **D-15:** Миграция V8 в academic-service: `UPDATE groups SET name = COALESCE(code, name)` (тест-данные); `ALTER TABLE groups DROP COLUMN code`.
- **D-16:** Также V8: `ALTER TABLE groups ALTER COLUMN name TYPE VARCHAR(32); ADD CONSTRAINT groups_name_unique UNIQUE(name)`. (Длина 32 — формат `XXXx-NNN` укладывается с запасом.)
- **D-17:** Test V1__baseline.sql (auth-service test/resources) обновить аналогично — `code` колонку убрать, name `VARCHAR(32) UNIQUE`.
- **D-18:** Entity `Group.code` поле удалить. DTO: `GroupResponse`/`CreateGroupRequest`/`UpdateGroupRequest` — поле `code` удалить.
- **D-19:** `GroupService.existsByCode` → `existsByName` (или просто `existsByName` уже есть, тогда удалить дубликат).
- **D-20:** Frontend `group-dialog.component.ts` — одно поле «Название (формат XXXx-NNN)», паттерн валидации `^[A-ZА-Я]{2,4}\d-\d{3}$` (см. D-22 ниже про точный регэксп).
- **D-21:** Все тесты пройти: backend (`GroupServiceTest`, `GroupRepositoryTest`), frontend (`groups-page.component.spec.ts`).

### Автопереименование групп (BUG-006 п.6)
- **D-22:** Формат имени группы: `{NAME}{COURSE}{TYPE}-{NUMBER}` где
  - NAME: 2–4 буквы (направление, например ИВТ, УВП, БМ)
  - COURSE: 1 цифра (1–6)
  - TYPE: 1 цифра (тип курса — бакалавриат/магистратура/аспирантура; 1=бакалавр, 2=магистр, 3=аспирант)
  - NUMBER: 3 цифры порядкового номера (не используется бизнес-логикой)
  - Пример: `ИВТ11-001` → 1 курс бакалавриата, группа 001.
  - Регэксп для валидации: `^[A-ZА-ЯЁ]{2,4}[1-6][1-3]-\d{3}$`
- **D-23:** Парсим курс из имени (без новой колонки в БД). Метод `parseCourse(name): int` в Group entity или утилитном сервисе.
- **D-24:** Scheduled job `GroupPromotionService.promoteAll()` — `@Scheduled(cron = "0 0 3 * * *")` (ежедневно ночью), внутри:
  1. Получить активный «осенний» семестр (по правилу: если у семестра в имени `осен` — это осенний; альтернатива — поле `season` в Semester entity, см. D-29).
  2. Если до даты начала ≤14 дней И ещё не переименовано (флаг `promoted_for_semester_id` в groups, см. D-25) → promote.
  3. Promote: `course = course + 1`. Если `course > 4 и type=1` (бакалавриат окончен), `course > 2 и type=2` (магистратура), `course > 4 и type=3` (аспирантура) — архивировать (`is_active=false`).
- **D-25:** Миграция V9: `ALTER TABLE groups ADD COLUMN promoted_for_semester_id BIGINT NULL REFERENCES semesters(id)`.
- **D-26:** Manual override (admin может triggernuть промоушен сейчас) — `POST /api/academic/groups/promote` (только ADMIN, dry-run flag).
- **D-27:** Покрытие тестами: правильный парсинг, вычисление «через 2 недели», корректные переходы (1→2, 4→архив для бакалавров и т.д.), идемпотентность (повторный run не promote-ит дважды для одного семестра).

### Семестры — валидация (BUG-006 п.7)
- **D-28:** В `SemesterService.create()`:
  - Запрет: `dateFrom < today` → BadRequest «Нельзя создать семестр в прошлом».
  - Запрет: пересечение с любым существующим семестром (любого статуса) → Conflict «Даты пересекаются с {имя_семестра}».
- **D-29:** Опционально расширить Semester: поле `season` (`AUTUMN`/`SPRING`) для D-24. Можно вычислять по дате (если dateFrom между июлем и декабрём — осенний, иначе весенний). Решение: вычислять, поле не нужно.
- **D-30:** В `SemesterService.update()`:
  - Запрет: если `dateTo < today` (семестр завершён) → 409 «Нельзя редактировать завершённый семестр».
  - Иначе разрешено менять имя/даты, с теми же проверками что и D-28.
- **D-31:** Дополнительная DB-миграция V10: `ALTER TABLE semesters ADD CONSTRAINT semesters_no_overlap EXCLUDE USING gist (daterange(date_from, date_to, '[]') WITH &&)` — атомарная защита от race condition. Требует `CREATE EXTENSION IF NOT EXISTS btree_gist;`.
- **D-32:** Frontend `semester-dialog.component.ts`: добавить asyncValidator на пересечение (вызывает `GET /semesters?overlapWith=...`) для UX до отправки. Поле минимальной даты `dateFrom = today`.

### Claude's Discretion

- Точные тексты ошибок и подсказок (русские, лаконичные).
- Реализация Specification API в одном Specifications-классе vs inline.
- Использовать ли Liquibase/Flyway для extension `btree_gist` в отдельной миграции (вероятно V8.5 = `V8a__enable_btree_gist.sql`).
- UI-подача колонки initialPassword — отдельная или toggleable.
- Какие именно spec-тесты добавить на frontend (приоритет — user-dialog, group-dialog, semester-dialog).

### Deferred Ideas (OUT OF SCOPE)

- Excuses backend (Фаза 59).
- Восстановление архивированных групп (роль архивирования группы — отдельная кнопка администратора).
- Мерж дубликатов пользователей.
- Импорт пользователей CSV-файлом.
- Soft-delete семестров (сейчас удаления нет вообще, и слава богу).
</decisions>

## Risk Register

| Риск | Вероятность | Митигация |
|---|---|---|
| Миграция V8 ломает CI-тесты, использующие старое поле `code` | Высокая | До миграции — `grep -r "\.code" services/academic-service/src/test` и поправить тесты в том же коммите |
| `btree_gist` extension недоступен на target Postgres | Низкая | Проверить версию (Postgres ≥ 9.0 — есть всегда). В проде Postgres 16 (см. docker-compose). |
| Scheduled job promote создаёт race с админом, который вручную переименовывает группу | Средняя | Идемпотентность через `promoted_for_semester_id`. Manual override (D-26) ставит тот же флаг. |
| Серверный поиск по 8000 пользователей даёт >300мс latency | Низкая | Добавить функциональный индекс `lower(login)`, `lower(last_name||first_name)`. Мерить после реализации. |
| Уже залогиненные админы кешируют старый JWT без поля `avatar_id` (Фаза B) — не относится к 58, но проверить на регрессию | Низкая | Не наша забота: cookie/JWT не зависят от schema БД. |

## Connections to other phases

- **Фаза B багфиксов (готова)** — `UserResponse.initialPassword` уже в backend. D-12/D-13 — только frontend.
- **Фаза 59 (Excuses)** — независима, можно делать параллельно.
- **GSD-state**: фаза должна получить статус Ready после `/gsd-discuss-phase` (если есть открытые вопросы у Claude по D-22 формату).
