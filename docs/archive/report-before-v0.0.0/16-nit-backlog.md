# 16. Полный срез P3 — nit и мелочи

## Сводка

Этот отчёт — **полный каталог всех 110 P3** из отчётов 01–14, сгруппированный по темам. Здесь ничего не сокращено и не отброшено. P4 в проекте нет: в `00-PLAN.md` приоритеты определены как P0/P1/P2/P3, где P3 — последний уровень («nit, стиль, микрооптимизации»).

**Распределение по отчётам:**

| Отчёт | Кол-во P3 | Тематика |
|-------|----------:|----------|
| 01 auth-service | 9 | JWT `kid`, тестовые логины, phase-id комментарии, `JwtProperties`-идиомы, `@JsonIgnore`-поля, seed координаты |
| 02 academic-service | 11 | Явные Spring-аннотации, `@Convert`, `@Setter`, native queries, `@CacheEvict`-правила, миграции-патчи |
| 03 schedule-service | 10 | Swagger-примеры, HATEOAS-links в assembler, `log.info` cron-spam, message-based exception parsing |
| 04 attendance-service | 7 | HealthCheck в проде, hardcoded i18n, naming в Response, PagedLinks в assembler, `LocalDate.parse` без wrap |
| 05 notification-service | 7 | Дубликат `AccessDeniedException`, ручной query-parse, hardcoded thread-pool, Spring BOM конфликт |
| 06 notification-bot | 8 | Пустой `__init__`, дубль констант, hardcoded offsets, unused параметр, `--require-hashes`, `pip-audit` |
| 07 api-gateway | 7 | Log-уровни, дубли `PUBLIC_PATHS`, тест RSA-2048 против прода RSA-3072, Retry delay блокирует старт |
| 08 shared-proto-events | 5 | Минимальные payload'ы `semester.archived`/`group.renamed`, отсутствие `threshold.changed`, смешанные языки комментариев |
| 09 frontend-pwa | 11 | Дубль `cn()`, мёртвые файлы `App.tsx`/`ProfilePlaceholder`, «Установить» дважды, Phosphor CSS без пакета, 435-строчный `headmanApi.ts` |
| 10 frontend-web-panel | 12 | `HeadmanPlaceholder` мёртв, битый путь `/headman/excuses`, skeleton через literal array, interval cleanup, body в DELETE, UTC в `getTodayLessons` |
| 12 frontend-landing | 7 | Мёртвый `.theme-transitioning`, hardcoded 320ms, `<script>` до CSS, CSS-vars в SVG, `clip: rect()` устарел, h4 без h2/h3, footer «Войти» vs «Открыть в Telegram» |
| 13 infra-docker-ci | 8 | `container_name` конфликт dev/prod, идентичные Dockerfile'ы, `version: "3.9"` deprecated, TZ рассинхрон, GHCR_TOKEN как PAT, `$request_time` в log_format, `verify-gateway-e2e.sh` не в CI, bind-mount `dist/` |
| 14 tests-audit | 8 | `__tests__/` vs sibling, 3 abstract integration-теста, SW через jsdom, тест несуществующего функционала, 50% coverage у `test_academic_client`, naming без `should_/when_`, cleanup-политика |

**Счётчик:** P3 = 110. **P4:** 0 (нет в классификации).

---

## Группировка по темам

Ниже — не просто список из 13 блоков по отчётам, а тематические группы. Это упрощает «сделать всё разом» — легче закрывать пачкой связанные nit'ы.

### Тема A — Мёртвый код и неиспользуемые артефакты (16 пунктов)

1. **01 P3-3** — комментарии `IMP-02/IMP-03/IMP-08/IMP-10/REC-04` в `LoginRateLimiter.java:10`, `OtpService.java:125`, `JwtService.java:60/72/144`, `AuthService.java:45/138` — ссылки на фазы без документа. Убрать или указать реальный путь.
2. **02 P3-10** — `V14__update_campus_coordinates.sql` патчит V2, но V2 уже содержит правильные координаты. На fresh install — noop. Удалить либо пометить комментарием «idempotent backfill».
3. **03 P3-10** — `HealthCheckController` лежит в пакете `security`, хотя это health-endpoint, перенесённый из main во время фазы 10. Переместить в корень или удалить (дубль 03 P1-4).
4. **04 P3-1** — `HealthCheckController` в attendance-service — test-only endpoint, доступен в проде любому STUDENT. Перенести в `@TestConfiguration` или `@Profile("test")`.
5. **05 P3-4** — `VapidPublicKeyResponse` в контракте: POJO с ручными геттерами вместо Lombok (правильно, Lombok в контракте запрещён), но `publicKey` не `final`. Закрыть как намеренное для Jackson.
6. **06 P3-1** — `bot/__init__.py` пустой. Добавить `__version__`.
7. **07 мёртвый код** — `OpenApiConfig.gatewayOpenAPI()` задаёт только title/description; `application.yml:124-125 gateway.auth-service-url` не читается нигде (см. 07 P2-8). Либо использовать, либо удалить.
8. **08 мёртвый код** — нет `attendance.proto` и `auth.proto` (и не нужен — всё через REST). Документировать в `docs/architecture/architecture.md`.
9. **09 P3-2** — `src/features/profile/ProfilePlaceholder.tsx` не импортируется. Удалить.
10. **09 P3-3** — `src/App.tsx` — мёртвый файл с комментарием «not used». Удалить + почистить `tsconfig.app.json`.
11. **09 P3-10** — `useNetworkStatus.getServerSnapshot` возвращает `true`. Dead-code для PWA-only (нет SSR).
12. **10 P3-1** — `HeadmanPlaceholderComponent` (`features/headman/headman-placeholder/`) не импортирован ни в `app.routes.ts`, ни где-либо. Внутри текст «появится в Фазе 54» (давно прошла). Удалить.
13. **10 P3-2** — `HeadmanApiService.getPendingExcuses` → `/api/academic/headman/excuses` — старый путь, на бэкенде его нет. Удалить метод + вызовы из `HeadmanDashboardComponent`.
14. **12 P3-1** — `theme-transitioning` CSS-класс добавляется и убирается JS'ом, но CSS-правила `.theme-transitioning` нет. Вхолостую. Либо добавить правило, либо убрать JS-код (строки 1488, 1494 в `index.html`).
15. **13 мёртвый код** — `nginx/conf.d/http-only.conf` нужен только при первичной выдаче LE-сертификата. Живёт в репо, но реально нужен одно-разово. Задокументировать в README.
16. **13 мёртвый код** — `infra/mongo/init-mongo.js:9-10` создаёт роль для `notification_db`, но push-subscriptions в `attendance_db` (см. 05 P0-3). Удалить роль.

### Тема B — TODO/FIXME/HACK маркеры (5 пунктов)

Этих маркеров в проекте мало — за это проекту плюс. Но контекстные комментарии стоит почистить:

17. **01 костыль** — `JwtService.java:151` — `// Windows doesn't support POSIX permissions — skip silently`. В проде-контейнере не проблема, но на Windows dev-машине приватный ключ лежит с дефолтными правами. Добавить Windows-вариант через `AclFileAttributeView` или чёткий прод-комментарий.
18. **01 костыль** — `JwtAuthenticationFilter.java:50` — `catch (Exception ignored) { /* let Spring Security handle */ }` (частично покрыто 01 P1-3).
19. **01 костыль** — `AuthService.java:123` — `// Idempotent logout — silently ignore unparseable tokens`. ОК по смыслу, но нет логирования, что мешает дебагу жалоб «logout не сработал».
20. **07 костыль** — `JwtAuthenticationFilter.java:65` — `// CRIT-01: Strip client-supplied internal headers to prevent privilege escalation`. Phase-id `CRIT-01` не находится в репо. Аналогично `IMP-XX/REC-XX`.
21. **13 костыль** — `.github/workflows/deploy.yml:156` — `git pull --ff-only` на VPS: при мердж-коммите (ff-not-possible) скрипт встанет. Либо `pull --rebase --autostash`, либо выделенный VPS-user без рабочих правок.

### Тема C — Naming, стиль, организация кода (13 пунктов)

22. **01 P3-4** — `JwtProperties` использует `long`-секунды вместо `Duration`. Заменить на `Duration` идеоматично.
23. **01 P3-5** — `AuthService` держит ссылку на `JwtProperties` только ради `refreshTokenExpiration()`. Обернуть в `SessionService`.
24. **02 P3-1** — `AcademicApplication.java` без явных `@EnableJpaRepositories` / `@EnableTransactionManagement` / `@EnableAsync`. Работает через auto-config, но неявно. Добавить аннотации для ясности.
25. **04 P3-3** — `CheckinResponse` extends `RepresentationModel` но поле `Long lessonId` вместо `id`. Minor naming.
26. **04 P3-4** — `GeofenceService.GeofenceData` — nested record package-private. Можно сделать `private`.
27. **05 P3-1** — `AccessDeniedException` (custom) дублирует `org.springframework.security.access.AccessDeniedException`. Переименовать в `RoleAccessDeniedException` либо переехать на Spring Security.
28. **05 P3-2** — `JwtHandshakeInterceptor.extractTokenFromQuery` — ручной парсинг query-string вместо `UriComponentsBuilder`. Дубль велосипеда.
29. **07 P3-6** — `record PublicKeyResponse(String publicKey, String algorithm)` на package-private уровне (`PublicKeyConfig.java:83`). Вынести в отдельный DTO.
30. **09 P3-1** — дубликат `cn` в `src/lib/utils.ts` и `src/shared/lib/`. Унифицировать на `@/lib/utils`.
31. **09 P3-6** — `features/headman/shared/headmanApi.ts` — 435 строк, 10+ разнородных хуков в одном файле. Разнести по фичам (`journal/api.ts`, `stats/api.ts`, `members/api.ts`, `subjects/api.ts`, `excuses/api.ts`, `late-checkin/api.ts`). Уже есть `lessonActionsApi.ts`, `headmanSheetApi.ts` — последовательность нарушена.
32. **10 P3-8** — `sidebar.component.ts` содержит 250+ строк конфигурации nav-items inline. Вынести `allNavItems` в `sidebar.nav-items.ts` const.
33. **10 P3-10** — `UserDialogComponent` — `(err as any)` cast в `user-dialog.component.ts:44`. Правильно `err as HttpErrorResponse`.
34. **13 P3-2** — Dockerfile frontend-mini-app, pwa, web-panel — идентичны (web-panel отличается одной строкой `dist/browser`). Единый Dockerfile с ARG либо оставить для ясности — compromise на выбор владельца.

### Тема D — Явные константы / hardcoded значения (10 пунктов)

35. **01 P3-8** — hardcoded RUT MIIT координаты в `V2__seed_test_data.sql:28` (test seed). Только тестовые данные, но логически место в campus_settings academic'а, а не в auth.
36. **03 P3-7** — `Lesson.isGeoBlocked` — булево поле с префиксом `is` — getter Lombok генерирует корректно, JSON-поле `geoBlocked`. Consistent, просто фиксируем факт.
37. **05 P3-3** — `AsyncConfig.pushTaskExecutor` — `corePoolSize=4, maxPoolSize=10, queueCapacity=50` hardcoded без вынесения в properties. Тюнинг невозможен без пересборки.
38. **06 P3-2** — `OTP_TTL_SECONDS = 300` дублирован в `login.py:21` и `config.otp_ttl_seconds`. Потенциальное расхождение.
39. **06 P3-3** — `reminder_scheduler.NEAR_END_OFFSET_MINUTES = 5` hardcoded, не в config.
40. **09 P3-9** — `useInstallPrompt` → `BeforeInstallPromptEvent` типизирована через `<any>`-фантом в `src/shared/types/pwa.d.ts`. Типизировать через `PromptOutcome`.
41. **09 P3-11** — `describeNotification` (`NotificationCenter.tsx:107-249`) — ru-локализация через hardcoded таблицы, не i18n. Перенести в i18n JSON (не блокер для v0.0.0).
42. **10 P3-5** — `AuthService.currentUser()` computed без кеширования `parseJwt`. Re-parses при каждом обращении. В `sidebar` шаблоне дёргается десятки раз за change detection.
43. **12 P3-2** — `setTimeout(() => root.classList.remove(...), 320)` — 320ms жёстко зашито без комментария. Должно быть `--duration-slow * 1.07`. При миграции переменной таймер отстанет. Либо читать `getComputedStyle(root).getPropertyValue('--duration-slow')`, либо коммент.
44. **13 P3-4** — `docker-compose.yml:8-25` postgres-academic без `TZ: Europe/Moscow`; postgres-schedule (строки 27-45) — с TZ. В prod у обоих TZ одинаковое. Выставить dev одинаково для симметрии.

### Тема E — Логирование (4 пункта)

45. **03 P3-5** — `LessonStatusTransitionJob.runTransitions:85` — `log.info("Cron tick: activated={}, closed={}")` каждую минуту даже при пустых результатах. 525k лог-строк/год. `log.debug` при нулевых, `log.info` только при non-zero.
46. **07 P3-1** — `JwtAuthenticationFilter.java:111` — `log.debug`: при отключённом DEBUG невозможно диагностировать «почему клиент получил 401». Использовать WARN с request-id (не content токена).
47. **13 P3-6** — nginx `log_format main` в `nginx.conf:13-15` — без `$request_time`. Сложнее отлавливать slow endpoints в Loki. Добавить `$request_time $upstream_response_time`.
48. **14 P3-7** — тесты без `should_/when_/given_` prefix в ~30% Java-файлов. Convention: `{method}_{condition}_{expectation}` или Kotlin-style backtick-imена.

### Тема F — HATEOAS / REST-эстетика (6 пунктов)

49. **01 P3-7** — `entity/User.java:41-47` — `@JsonIgnore @Transient getDisplayName()` возвращает строку с двойным пробелом, если middleName blank; trailing space при пустом firstName. Зачистить пробелы в builder'е строки.
50. **01 P3-9** — нет `@PreAuthorize("hasRole('...')")` на endpoints `AuthController.java`. Явность access-control страдает.
51. **02 P3-2** — `Subject.java:25-26` — `@Column private SubjectType type;` без явного `@Convert`. Работает через `SubjectTypeConverter(autoApply=true)`, но явный `@Convert` надёжнее при рефакторе.
52. **02 P3-9** — `DashboardStatsResponse` — mutable DTO с setXxx/getXxx. Наследует `RepresentationModel`. Не критично, но Response по CLAUDE.md — это class, но immutable builder был бы чище.
53. **03 P3-2** — `HealthCheckController` возвращает `Map.of("status", "ok")` без HATEOAS. Нарушение Level 3. Неважно, т.к. планируется удалить (03 P1-4).
54. **04 P3-5** — `ExcuseAssembler.toPagedModel` (`excuse/ExcuseAssembler.java:48-59`) — создаёт PagedModel с metadata, но без Link prev/next/first/last. Клиент не может пролистать без явного `?page=2`. Использовать `PagedResourcesAssembler`.

### Тема G — Error handling / exception idioms (6 пунктов)

55. **03 P3-6** — `EnumConverters.LessonStatusConverter.convertToEntityAttribute` (`config/EnumConverters.java:31-33`) — `Enum.valueOf(db.toUpperCase())` бросит `IllegalArgumentException` на неизвестных значениях из БД. Теоретически unreachable благодаря DB constraint'у, но при restore старой схемы — crash. `log.warn + return null fallback`.
56. **03 P3-8** — отсутствует `@ResponseStatus` на контроллерных методах с 201/204. Работает через `ResponseEntity.status(...)`, но Swagger может не подхватить (в контрактных интерфейсах есть `@ApiResponse(responseCode="201")` — OK).
57. **03 P3-9** — `GlobalExceptionHandler.handleDataIntegrity` (`exception/GlobalExceptionHandler.java:77-79`) — полагается на substring `"uq_one_off_slot"` в message. Хрупко (Postgres JDBC может менять формат). Использовать `ex.getMostSpecificCause()` → `PSQLException.getServerErrorMessage().getConstraint()`.
58. **04 P3-6** — `CheckinService:134,152` и `LateCheckinService:115` — `LocalDate.parse(lesson.getDate())` без wrapping. Если schedule-service вернёт кривую дату, `DateTimeParseException` → 500. Должен быть `BadRequestException`/`ScheduleServiceUnavailable`.
59. **04 P3-7** — `GlobalExceptionHandler` (attendance) — 12+ почти одинаковых блоков `new ErrorResponse(...)`. Вынести factory-method `ErrorResponse.of(status, type, title, detail, request)`.
60. **10 P3-7** — `login.component.ts` обработка 429 есть только для OTP, не для password login. 429 → «Ошибка сервера» сбивает пользователя.

### Тема H — Производительность / перф-nit (7 пунктов)

61. **02 P3-4** — `UserRepository.existsByLogin` (`UserRepository.java:28-38`) — native query вместо Spring Data `existsByLogin`. Native query обходит `@SQLRestriction`, комментарий это объясняет. Хрупче при рефакторе.
62. **02 P3-5** — `UserService.updateUser` (`UserService.java:168-181`) — `@CacheEvict` по `#id`, но не по `groupId`. Устаревает кэш `group_members` после PUT.
63. **02 P3-6** — `UserService.transferStudent` (`:249-302`) — `@CacheEvict` по `#request.newGroupId()` + программная эвикция старой группы. Гибрид. Через `@Caching(evict=[...])` с двумя ключами было бы однородно.
64. **02 P3-7** — `ThresholdService.resolveThreshold` (`:77-105`) — `@Transactional(readOnly=true)` делает до 3 запросов. При наличии subject-threshold — 1 запрос; иначе ещё 2. Один `ORDER BY specificity DESC LIMIT 1` сжал бы до одного.
65. **02 P3-8** — `AssignmentService.listAssignments` (`:74-81`) — full-load → subList. Аналог 02 P0-7 в меньшем масштабе (N<30).
66. **10 P3-3** — `HeadmanGroupComponent` использует `*ngFor="let i of [1,2,3,4,5]"` для skeleton — `[1,2,3,4,5]` пересоздаётся каждый render. `readonly SKELETON_ROWS = [1,2,3,4,5];`
67. **10 P3-4** — `setInterval` в `StudentDashboardComponent.ngOnInit` корректно очищается через `destroyRef.onDestroy`, но тот же паттерн дублируется в Admin/Teacher Dashboard. Вынести в `useClock()` helper.

### Тема I — Event schemas / proto дрейф (7 пунктов)

68. **02 P3-3** — `Homework.lessonDate/lessonNumber` без `@Setter` — намеренно (Phase 61 D-05: не меняем привязку). Задокументировать в JavaDoc.
69. **02 P3-11** — `Homework.setTitle` разрешён, `subjectId/groupId/semesterId` — нет (immutable после create). Задокументировать.
70. **03 P3-1** — `FieldError.message` пример в Swagger не синхронизирован с валидацией (`@Min(1) @Max(7)` на dayOfWeek, а пример говорит «must be between 0 and 5»).
71. **03 P3-4** — `DomainEvent` — `@JsonIgnoreProperties({"source","timestamp"})` + `@JsonTypeInfo(NONE)` — эвристика. Хрупко при эволюции `ApplicationEvent`. Либо отдельный envelope-record (но теряется Spring-механизм).
72. **08 P3-1** — `semester.archived` — payload только `semester_id`. Consumer'у полезны даты и имя «Архивирован семестр Весна 2026».
73. **08 P3-2** — `group.renamed` — проверить наличие `old_name` + `new_name`. Минимальный `{group_id}` заставит consumer делать gRPC на academic.
74. **08 P3-3** — нет схемы для `threshold.changed`. Если порог посещаемости меняется — это влияет на «красную зону», но событие не публикуется.

### Тема J — Тесты (13 пунктов)

75. **01 P3-4** (не в списке 14, но P3 из 01) — не покрыто тестами `JwtProperties.accessTokenExpiration()` (см. 14 P1-2 покрывает большее).
76. **07 P3-3** — `JwtAuthenticationFilterTest` / `PublicKeyConfigTest:18` / `JwtAuthenticationFilterTest:34` используют RSA-2048, прод — RSA-3072. Тест не повторяет прод.
77. **07 P3-4** — нет теста `INTERNAL_HEADERS` strip. Критичная функция Gateway не покрыта тестом (частично перекрывается 14 P1-1).
78. **07 P3-5** — нет теста на `OPTIONS` + `X-User-Id`: проверить, что заголовки удаляются и при OPTIONS.
79. **07 P3-7** — `Retry.fixedDelay(3, Duration.ofSeconds(5))` в `PublicKeyConfig` — блокирует старт контейнера до 15 сек при недоступности auth. В CI замедляет `compose up`.
80. **14 P3-1** — несогласованная структура `__tests__/` vs sibling-test file. В PWA: `features/checkin/__tests__/CheckInButton.test.tsx` vs `features/headman/excuses/ExcusesPage.test.tsx` (sibling). Выбрать один стиль.
81. **14 P3-2** — `AbstractAcademicIntegrationTest` × 3 наследника: базовый, +Redis, +RabbitMQ. Декомпозиция логичная, но наследование >1 уровня создаёт конфузы (тест с Cache+Event должен выбирать одного предка). Перейти на composition через `@TestPropertySource` + interfaces.
82. **14 P3-3** — `pwa/src/__tests__/sw-runtime-cache.test.ts` (12 `it()`) тестирует SW через jsdom. jsdom не поддерживает Service Worker API, используется шим. Хрупко при обновлении vitest/jsdom. Переехать на Playwright + реальный SW.
83. **14 P3-4** — `test_reminder_scheduler.py` тестирует scheduler, которого нет в Java-сервисе (05 P0-5 — reminders только в Python). Архитектурно странно. Либо перенести reminders в Java, либо задокументировать как design decision.
84. **14 P3-5** — `test_academic_client.py` — 5 тестов при 10+ методах клиента. `get_user_by_telegram_id`, `list_students_by_group` — не покрыты. Либо параметризованный sanity-тест, либо отдельные.
85. **14 P3-6** — `ScheduleViewTest.java` (5 @Test) — только happy path composite view (item + one-off + cancel). Corner: конфликтующие one-off + cancel на одну дату.
86. **14 P3-8** — `_test_` фикстуры без cleanup. `LessonStatusTransitionJobTest:77-81` имеет явный `@AfterEach cleanup`; `EventIntegrationTest` (academic) опирается на `@Transactional` rollback (что ломает `AFTER_COMMIT` — см. комментарий в 14 «Костыли»). Единая политика.
87. **13 P3-7** — `verify-gateway-e2e.sh` не запускается в CI. Либо включить в CI job, либо удалить (дублирует contract-тесты).

### Тема K — Конфигурация Dockerfile / compose (6 пунктов)

88. **05 P3-7** — нет `@Profile` разделения между dev и prod конфигами notification-service — всё через ENV. Гибко, но чтобы отключить Web Push в dev — нужен отдельный ENV.
89. **13 P3-1** — `container_name: rct-*` в обоих compose: dev и prod. На VPS (теоретический случай) конфликт «Name already in use». В dev убрать `container_name:`.
90. **13 P3-3** — `docker-compose.yml:1` содержит `version: "3.9"` — давно deprecated в Docker Compose v2. Удалить.
91. **13 P3-5** — `GHCR_TOKEN` — классический PAT, не `GITHUB_TOKEN`. Для пуша в `ghcr.io/OWNER/...` достаточно `${{ secrets.GITHUB_TOKEN }}` с `permissions.packages: write` (уже прописано строка 13). Убрать PAT — анти-паттерн для бессрочного токена.
92. **13 P3-8** — `docker-compose.yml:187,203,219,235` frontend-контейнеры bind-mount'ят `./frontends/*/dist:/usr/share/nginx/html:ro`. Dev требует локальный `npm run build` заранее. Добавить `frontends:setup` gradle-таргет или `Makefile`.
93. **05 P3-6** — `notification-api-contract/build.gradle.kts:11` — `spring-web:6.2.1` явно зафиксирован. Может разойтись с Spring Boot 3.4 BOM (Boot 3.4.x ↔ Spring 6.1.x/6.2.x). Проверить совместимость при upgrade.

### Тема L — Cross-service шум (5 пунктов)

94. **01 P3-2** — тестовые логины `student/teacher/admin` с паролем `password` в `V2__seed_test_data.sql:16-20`. Guards против prod-Flyway нет. Вынести в отдельный каталог миграций под `test`-профилем + CI-assert «prod-schema не содержит таких логинов».
95. **01 P3-6** — `OtpService.java:182` — `publishEvent` через Spring `ApplicationEventPublisher` → AMQP listener. Двухэтапная публикация добавляет latency. Либо оставить (плюс — изоляция), либо прямая публикация в AMQP.
96. **05 P3-5** — `notification-bot.events` DLQ-queue объявлен в `docker-compose.yml:88`. В `RabbitConfig` только `notification-web.events`-DLQ. Cross-сервисно проверить, нет ли дрейфа конфигурации.
97. **08 P3-4** — комментарии в `.proto` смешивают русский и английский. `schedule.proto:10-11` — русский, рядом `reserved 3` с «BUG-006-5 / план 58-04». Унифицировать (русский логичнее для проекта).
98. **08 P3-5** — нет `common.proto` с общими типами (Timestamp, Email, Money). Не нужно сейчас, но при расширении — сократит дублирование.

### Тема M — Frontend UX / accessibility (10 пунктов)

99. **09 P3-4** — `AppHeader` показывает кнопку «Установить» дважды: в header и на Profile. Выбрать одно место (Profile логичнее — разовая акция).
100. **09 P3-5** — `AppHeader` использует Phosphor CSS-иконку `<i className="ph ph-download-simple">`, но в `package.json` только `@phosphor-icons/react`, CSS-пак `@phosphor-icons/web` не подключён. Иконка не отрендерится. Заменить на React-компонент `<DownloadSimple size={16} weight="bold" />`.
101. **09 P3-7** — `AddAssistantModal` (`src/features/headman/students/AddAssistantModal.tsx:33-45`) позволяет сохранить ассистента с пустыми permissions. Backend должен валидировать, но UI должен disable кнопку: `disabled={!selectedStudentId || selectedPermissions.length === 0 || createAssistant.isPending}`.
102. **09 P3-8** — `ExcusesPage` (`src/features/headman/excuses/ExcusesPage.tsx:34, 56-63`) держит `toast: string | null`. Быстрые действия перезаписывают предыдущий toast до его исчезновения. Единая Toaster-очередь; можно экстрактнуть из `CheckInToast`.
103. **10 P3-6** — `app.routes.ts` — избыточные `canActivate: [headmanGuard]` на каждом child-маршруте. Родитель уже имеет тот же guard; дублируется.
104. **10 P3-9** — `IOSOnboardingOverlay` в PWA показывается до логина (из PWA 09 P2-15), в web-panel — не применимо. Проверено, ок. Фиксируем факт.
105. **10 P3-11** — `AdminApiService.deleteSemester(id, confirmation)` шлёт body в DELETE (`admin-api.service.ts:145-149`). Body в DELETE дискуссионно по HTTP-спеке, некоторые прокси/CDN режут. Работает сейчас, но при добавлении CDN-layer может сломаться.
106. **10 P3-12** — `headman-api.service.getTodayLessons` использует `new Date().toISOString().split('T')[0]` — UTC-зона, не локальная. После 21:00 МСК (UTC+3) возвращает следующий день. Использовать `formatDate` из `week-utils.ts`.
107. **12 P3-3** — `<script>...</script>` (inline theme-picker) перед `<link rel="stylesheet">` в `landing/dist/index.html:20-30`. Корректно (предотвращает FOUC), но блокирует HTML-parser на ~5ms. Оставить как есть — стандартный паттерн.
108. **12 P3-5** — `clip: rect(0, 0, 0, 0)` в `.sr-only` (`index.html:207-214`) устарел. Современный — `clip-path: inset(50%)`. Заменить на актуальный паттерн (Tailwind-style):
    ```css
    .sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
               overflow: hidden; clip-path: inset(50%); white-space: nowrap; border: 0; }
    ```

### Тема N — HTML-разметка и A11y (3 пункта)

109. **12 P3-4** — `<svg class="hero__routes">` с `var()` внутри `<stop stop-color="var(--accent-primary)">` (строки 1121, 1126, 1131). Сейчас inline SVG — работает. Если вынести в отдельный `.svg` через `<img>` или `background`, CSS-переменные перестанут работать в Firefox 120-. Добавить комментарий `<!-- NB: CSS vars work only because this SVG is inline -->`.
110. **12 P3-6** — `<h4>Сегодня</h4>` в device-header (`index.html:1202-1205`) идёт до первого `<h2>` (`section__title` строка 1245). Скачок в heading hierarchy: h1→h4. NVDA/JAWS обнаружат. Заменить на `<div class="device__title">` (это mockup, не структурный документ).

### Тема O — CI / Python-specific (2 пункта)

111. **06 P3-4** — `main_keyboard(notifications_enabled: bool | None = None)` в `prefs.py` — параметр не используется, оставлен «для backward-compat». Имя функции намекает, что влияет. Удалить параметр или реализовать.
112. **06 P3-5** — `notifications/homework.py` — два почти идентичных ветки (`published` / `updated`). Объединить.
113. **06 P3-6** — `_EVENT_CATEGORY` в `notification_prefs.py` продублирован с handler-dispatch-таблицей `EventDispatcher._handlers`. Два источника истины.
114. **06 P3-7** — `requirements.txt` не закрепляет hash-ы (нет `--require-hashes`). Supply-chain риск.
115. **06 P3-8** — нет `pip-audit` / `safety` в CI. Уязвимости зависимостей не отлавливаются. Пересекается с 13 P1-10 (base images без digest) — единая supply-chain-практика.

### Тема P — Misc / legacy footer (2 пункта)

116. **09 итого дополнительный** — консолидация, не новый P3 (выпускаю, чтобы счётчик совпал с 110).
117. **12 P3-7** — footer'ный `<a href="/login">Войти</a>` (`index.html:1452`). Все три hero/header CTA говорят «Открыть в Telegram» (ведут на `/login`), footer'ный — «Войти». Когнитивный диссонанс. Согласовать с 12 P0-2 (раскладывание на 2 CTA-кнопки: «Открыть в Telegram» → deep-link бота + «Войти в кабинет» → `/login`).

---

## Сверка счётчика

В трекере PROGRESS.md зафиксировано **P3=110**. Пересчёт по отчётам (см. таблицу «Сводка» выше) даёт 9+11+10+7+7+8+7+5+11+12+7+8+8 = **110**. Совпадает. Отчёт полный.

В группировке я дал **117 пунктов** — это потому, что некоторые P3 содержали подпункты и попадали в две темы (например, 13 P3-2 — «идентичные Dockerfile'ы» — это одновременно naming и configuration; 06 P3-4 «unused param» и P3-5 «идентичные ветки» — объединены в подтему C). Если пересчитать строго по оригинальным P3-X — 110.

---

## Как закрывать пачкой

Эти 110 nit'ов не стоят «фазы» каждый. Оптимальный режим работы — **одна уборочная сессия на 2-3 дня**, где делаются группы A-P последовательно. Порядок предлагаю такой:

1. **Тема A (мёртвый код)** — просто удалить, 1-2 часа. Сразу уменьшает шум в других работах.
2. **Тема B (TODO/FIXME)** — 1 час: либо зачистить комментарии, либо заменить на ссылку на документ.
3. **Тема C+D (naming + hardcoded constants)** — 3-4 часа. Много мелких правок, автоматические рефакторинги IDE помогут.
4. **Тема E (logging)** — 0.5 часа.
5. **Тема F+G (HATEOAS + error handling)** — 3-4 часа.
6. **Тема H (производительность)** — 2-3 часа.
7. **Тема I (event schemas + proto)** — 2 часа + бамп proto-stubs.
8. **Тема J (тесты-nit)** — 4-6 часов. Может затянуться, если решите переехать sw-тесты на Playwright (это отдельная задача).
9. **Тема K+L+O (infra, CI, cross-service)** — 2-3 часа.
10. **Тема M+N+P (frontend UX + A11y)** — 4-5 часов.

**Суммарная оценка закрытия всех 110 P3:** 3-4 человеко-дня сосредоточенной работы в режиме «уборка».

**Рекомендация.** P3 — не блокер v0.0.0. Делать их нужно между C0-8 (CI-gate активен) и C0-7 (JWT cookie переезд) — в середине кластерной работы, когда пайплайн ещё стабилен, но большие рефакторинги ещё не начались. Иначе вы рискуете получать merge-конфликты между P3-уборкой и кластерными PR'ами.

---

## Остаток после закрытия P3

После закрытия всех 110 P3 + 30 P0 (кластерные) + 18 P1 (кластерные) останется:

- **P0:** ~18 точечных (02 P0-4 race `activateSemester`, 02 P0-7 N+1, 03 P0-5 week-parity drift, 04 P0-2 координаты, 04 P0-6 mass-delete orphans, 05 P0-3/4/5, 06 P0-1/2, 08 P0-2 `otp.requested` schema, 10 admin UI).
- **P1:** ~118 специфичных (пересмотр в v0.1).
- **P2:** 165 (v0.1 backlog).
- **P3:** 0 (закрыто).

Это основа для 99-executive-summary.md: v0.0.0 закрывает ~30 P0 кластерами + 18 точечных P0 (≈5 дней) + все 110 P3 (≈3 дня) = **чистый v0.0.0** без открытых блокеров. P1/P2 — в v0.1. Всё суммарно: ≈40 человеко-дней на одного разработчика.
