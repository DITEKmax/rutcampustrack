# Phase 6: REST API + HATEOAS - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-30
**Phase:** 06-rest-api-hateoas
**Areas discussed:** Авторизация по ролям, Структура API контрактов, Создание пользователя и пароль, Семестр и каскадные операции

---

## Авторизация по ролям

### Как проверять роли?

| Option | Description | Selected |
|--------|-------------|----------|
| RequestContext + аннотации | RequestContext извлекает заголовки через Filter, кастомная @RequireRole на методах контроллера. Без Spring Security | ✓ |
| Spring Security filter chain | SecurityFilterChain с кастомным фильтром, @PreAuthorize("hasRole('ADMIN')") | |
| Ручная проверка в сервисах | Каждый сервис-метод проверяет роль программно (if/switch) | |

**User's choice:** RequestContext + аннотации (Рекомендуется)
**Notes:** Легковесный подход без Spring Security. Gateway уже валидирует JWT и инжектит заголовки

### Как headman-assistant наследует права?

| Option | Description | Selected |
|--------|-------------|----------|
| Permission check в сервисе | Сервис проверяет: если роль=STUDENT и is_headman=false, ищем запись в headman_assistants с нужным permission | ✓ |
| Аннотация @RequirePermission | Кастомная аннотация на методах контроллера, AOP проверяет и роль, и assistant permissions | |

**User's choice:** Permission check в сервисе (Рекомендуется)
**Notes:** Проверка в сервисном слое через HeadmanAssistantRepository

---

## Структура API контрактов

### Группировка контрактных интерфейсов

| Option | Description | Selected |
|--------|-------------|----------|
| По домену | UserApi, GroupApi, SemesterApi и т.д. — 8 интерфейсов. Каждый содержит методы для всех ролей этого домена | ✓ |
| По ролям | AdminUserApi, HeadmanSubjectApi и т.д. — ~15+ мелких интерфейсов | |
| Гибрид | По домену, но сложные домены (User) разбить по ролям | |

**User's choice:** По домену (Рекомендуется)
**Notes:** 8 контрактных интерфейсов, @RequireRole на отдельных методах

### Структура URL путей

| Option | Description | Selected |
|--------|-------------|----------|
| По ресурсу | /api/academic/users, /api/academic/groups и т.д. — REST-каноничный | ✓ |
| По ролям | /api/academic/admin/users, /api/academic/headman/subjects | |
| Смешанный | Общие ресурсы + специфичные (/api/academic/me/profile) | |

**User's choice:** По ресурсу (Рекомендуется)

### Эндпоинты студента

| Option | Description | Selected |
|--------|-------------|----------|
| /users/me + /groups/my | Отдельные эндпоинты, ID из X-User-Id | ✓ |
| /users/{id} с проверкой | Тот же эндпоинт, сервис проверяет id == X-User-Id | |

**User's choice:** /users/me + /groups/my (Рекомендуется)

---

## Создание пользователя и пароль

### Передача креденшиалов

| Option | Description | Selected |
|--------|-------------|----------|
| Plain пароль в ответе | POST /users генерит login + random пароль, хранит BCrypt hash, возвращает plain одноразово | ✓ |
| Пароль при /start бота | POST /users не возвращает пароль. Генерация при первом /start в Telegram (зависит от v4.0) | |

**User's choice:** Plain пароль в ответе (Рекомендуется)

### Хеширование пароля

| Option | Description | Selected |
|--------|-------------|----------|
| Academic хеширует сам | BCrypt в Academic Service. Он владеет users таблицей | ✓ |
| gRPC вызов Auth Service | Academic отправляет plain пароль Auth Service через gRPC | |

**User's choice:** Academic хеширует сам (Рекомендуется)

---

## Семестр и каскадные операции

### Confirmation phrase при удалении семестра

| Option | Description | Selected |
|--------|-------------|----------|
| Фраза в request body | DELETE /semesters/{id} с телом {"confirmation": "название семестра"} | ✓ |
| Фраза в заголовке | DELETE с заголовком X-Confirmation | |
| Двухэтапное удаление | POST /delete-request → DELETE с фразой | |

**User's choice:** Фраза в request body (Рекомендуется)

### Каскад при revoke headman

| Option | Description | Selected |
|--------|-------------|----------|
| В сервисе атомарно | @Transactional: снять is_headman + bulk deactivate assistants | ✓ |
| Event-driven | headman.revoked событие → listener деактивирует assistants | |

**User's choice:** В сервисе атомарно (Рекомендуется)

---

## Claude's Discretion

- DTO field naming and granularity
- Service layer internal structure
- Assembler implementation details
- Pagination defaults
- Validation constraints

## Deferred Ideas

None — discussion stayed within phase scope
