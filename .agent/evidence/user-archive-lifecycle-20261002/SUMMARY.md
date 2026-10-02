# Б — JS-ADMIN-04: preview, protected archive, restore без прав

Риск S3. Sole writer: Б, `map-usage-delivery-20260922`; RULES SHA256
`A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA`.
Решение владельца 2026-10-02: восстановить аккаунт без активных прав, роли назначить заново.
Auth OTP/password/outbox и прежние пакеты здесь повторно не проверялись.

## Результат для пользователя

ADMIN получает server preview сохранённых связей/отметок и локальных опасных последствий;
архивирует пользователя защищённой операцией с UUID и preview digest. Для активного
TEACHER/HEADMAN Auth проверяет текущий пароль действующего ADMIN с отдельным purpose
USER_ARCHIVE и общим лимитом попыток. Архивирование отзывает права, выбор роли в
существующих сессиях и помощников; сохраняет credentials/login, grant IDs и историю.
Receipt делает повтор одной операции безопасным, повтор по-прежнему проверяет пароль.

Restore возвращает видимый редактируемый аккаунт без активных grants, текущей группы,
старосты, помощников или выбора роли. Старые сессии не возрождаются. Явное новое
назначение STUDENT требует выбранной группы и начинает новый интервал TODAY,
сохраняя закрытую историю. Самоархивация/последний ADMIN защищены.

## Контракт

- GET `/api/academic/users/{id}/archive-preview`: `userId`, `linkedGroupCount`,
  `attendanceMarksCount`, `activeHeadmanGroupCount`, `soleTeacherAssignmentCount`,
  `requiresPassword`, `academicObservedAt`, `attendanceObservedAt`,
  `attendanceSnapshotDigest`, `previewDigest`, `expiresAt` (10 минут).
- POST `/{id}/archive`: `{operationId, previewDigest, password?}` → 204.
  Несвежий Academic impact → 409 `archive_preview_stale`; неправильный пароль 403,
  отозванный доступ 401, лимит 429, недоступная dependency 503.
- POST `/{id}/restore`: `{operationId}` → 204; новый restore уже неархивного target
  → 409 без мутации. Повтор того же receipt → 204.
- Legacy DELETE `/{id}` и PATCH status=ARCHIVED → 409 `protectedroute_required`.
  Подключение frontend к защищённому маршруту — отдельный следующий этап.
- Auth POST `/internal/auth/confirm-user-archive`:
  `{internalToken,password,purpose:USER_ARCHIVE,targetId,operationId,previewDigest}`;
  существующий issuer secret, no-store, redacted DTO, без нового ticket/session.
- Attendance `PreviewUserImpact` считает все сохранённые marks user_id включая
  историю. Timestamp/digest описывают наблюдённый count, не атомарную distributed version.
  RPC защищён существующим service secret constant-time policy; Mongo read-only.

## Review и corrections

Product freeze `d8be82aa`, tests/gate `c42f14c3`. Auth/proto/Attendance independent PASS.
Academic initial FAIL P1/P2 исправлены в `02a58212`: sorted ADMIN+target
FOR NO KEY UPDATE → sorted groups FOR UPDATE → canonical target lock upgrade;
helper INSERT FK KEY SHARE может завершиться до передачи group lock. Локальный
fingerprint v2 включает group/headman/soleTeacher counts и dangerous flag; final
prepare пересчитывает его после remote proof. Affected product review PASS.

PG regressions `48436660`: реальная гонка помощника/архивации с наблюдением
pg_stat_activity Lock и FK INSERT; изменение предупреждения soleTeacher 0→1
только через grant другого преподавателя делает прежний preview недействительным.
Test adequacy PASS. R1 выявил только fixture ошибки: spy Spring proxy не unwrap;
DELETE synthetic semester запрещён существующим receipt/history guard, дальнейшие
seed столкнулись с overlap constraint. `8ecbff35` — fixture-only correction:
plain real-SQL repository spy и один retained class-owned semester, деактивация
между случаями, очистка собственных users/groups. Guard не отключён. Affected PASS.

## Достаточная проверка

JDK 21.0.10; один worker, no-daemon/no-parallel, classpath-packaging=true;
TESTCONTAINERS_REUSE_ENABLE=false. Точные команды/revisions — `run-checks.ps1`,
`run-academic-checks.ps1` и соответствующие JSON.

| Run | Revision | Проверка | Result |
| --- | --- | --- | --- |
| R1 session 66254 | 48436660 | Auth/Academic/Attendance compileJava + compileTestJava | 6 PASS |
| R1 | 48436660 | Attendance UserImpactIT | 1 PASS, 0 failure/error/skip |
| R1 | 48436660 | Auth две выбранные InternalSessionAdmissionIT methods | 2 PASS, 0 failure/error/skip |
| R1 | 48436660 | Academic UserArchiveLifecycleIT | 4 fixture FAIL, 0 error/skip, preserved XML |
| R2 session 78707 | 8ecbff35 | Academic compileTestJava + UserArchiveLifecycleIT | 4 PASS, 0 failure/error/skip |

Итог: 7 выбранных IT PASS. Независимые R1 PASS не повторялись.
R2 подтверждает transactional rollback/receipt retry, restore без прав и новое
TODAY назначение, protected legacy routes/self safeguard, stale local impact,
group serialization/FK compatibility/отзыв помощника без возврата после restore.
Auth подтверждает dedicated password purpose/live authority/shared attempts и
запрет role admission для восстановленного аккаунта до явного назначения.
Mongo подтверждает исторический count и неизменность исходных документов.

Raw fresh XML сохранены локально в `r1-xml/` и `r2-xml/`; compact totals/cases/hashes
в `xml-results.json`. Логи: `archive-checks-20261002-142354-538.log` (exit1),
`academic-r2-20261002-143040-805.log` (exit0, BUILD SUCCESSFUL 1m6s).
XML R2 создан 14:31:47 MSK, после R2; raw лог/XML не входят в product commit.

Cleanup проверен после обоих terminal. R2 fresh PostgreSQL `497d2c9490ee` и Ryuk
`11ca54e13dd3` созданы 14:31:17 MSK; reuse hash label отсутствовал. После terminal
оба exact ID отсутствуют, Testcontainers EMPTY. Manual removal не выполнялся.
Факты/время — `cleanup.json`. Heavy lease возвращён root.

Ограничения: component IT, не combined HTTP/реальный Telegram/готовый UI.
Remote count наблюдается отдельно от Academic transaction. Production migration,
push, deploy, изменения чужих данных не выполнялись. Main integration — root.

## Конкретный inventory (23 source/test файла)

Созданы (13):

- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/user/UserArchiveModels.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AttendanceUserImpactGrpcClient.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/AuthUserArchiveClient.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserArchiveRepository.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserArchiveService.java`
- `services/academic-service/academic-app/src/main/resources/db/migration/V44__user_archive_receipts.sql`
- `services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/UserArchiveLifecycleIT.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceUserImpactGrpcSecretInterceptor.java`
- `services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceUserImpactGrpcServiceImpl.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/grpc/UserImpactIT.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/api/InternalUserArchiveConfirmationApi.java`
- `services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/ConfirmUserArchiveRequest.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/InternalUserArchiveConfirmationController.java`

Изменены (10):

- `proto/attendance.proto`
- `services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/UserApi.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRoleGrantReader.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java`
- `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserService.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/exception/SemesterDeletionConfirmationExceptionHandler.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/security/InternalIssuerSecretFilter.java`
- `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/SemesterDeletionConfirmationService.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/arch/AuthApiContractTest.java`
- `services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/InternalSessionAdmissionIT.java`

Удалено: 0. Tracked generated source/config/lockfiles не менялись.
Чужие dirty evidence и старые Auth raw artifacts сохранены.
