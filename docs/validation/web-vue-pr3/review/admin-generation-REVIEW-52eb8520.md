# Независимое auth/data review52eb8520

Результат: **PASS backend source review; новых подтверждённых backend P1/P2 нет**. Известная межпакетная compatibility dependency ручного mobile decoder остаётся открытой и отмечена ниже.

Scope: frozen `3c02a3241..52eb8520cf8ed8da08d24575219f3d0988667960`, 19 файлов (GET credential/version + POST generated password + shared Headman mutation extraction + AuthV16 + generated contracts + целевые tests). Прочитан Git diff/точный gitshow, не moving worktree. Reviewer продуктовый код/общие docs не менял, Gradle/Docker/CI/install/fullsuite не запускал, commits/push не выполнял, детей не создавал. Запрошенная модельgpt-6.1-sol/high; фактическая runtime model telemetry недоступна.

## Authz, данные и concurrency

- Новый AdminGeneratedPasswordService использует существующий AuthAdminAccess.authorize(USER_CREDENTIALS_MANAGE): текущий selected ADMIN/ACTIVE, live session/grants, actual FULL/LIMITED profile. FULL может target active ADMIN и self; LIMITED не может target active ADMIN. Нового self-ban нет. GET не превращён в публичный/cookie-auth disclosure: JWT обязателен, bootstrap allowance не расширялся.
- Порядок блокировок: existing global admin advisory lock → actor/target auth_identities по возрастанию → actor session + target sessions по sid → auth.admit повторно. TransactionTemplate REQUIRED охватывает admission/credential read-or-mutation/audit/outbox/commit. Auth.admit/JdbcSessionAuthority присоединяется к тому же transaction, повторные locks уже принадлежат caller. Другие policy writers используют соответствующие identity/global locks; logout/role/session changes не могут пройти между admission и credential commit.
- CAS проверяет canonical positive long expectedCredentialsVersion под identity lock, SQL update также содержит exact version. Auth tuple trigger увеличивает credentials_version один раз. Повтор той же revision конфликтует и не возвращает секрет победителя. Два администратора сериализованы и получают ровно один committed winner.
- Shared GeneratedPasswordMutation сохраняет прежние Headman SecureRandom12/bcrypt, reset-ticket invalidation, all-session revocation, account PASSWORD_CHANGED и metadata-only PasswordChangedOutbox. Ни ADMIN operation, ни extraction не копируют headman group eligibility в ADMIN. Existing Headman eligibility/lock checks остаются до helper; текущий reader понимает admin_reset.
- Self success возвращает committed receipt200, хотя target all-session revocation включает actor session. Следующий запрос старым bearer отвергается. Unknown self outcome нельзя восстановить blind POST/new version: нужен fresh permitted administrator/recovery и readback. Frontend volatile receipt/UX в этом backend scope не реализовывались и здесь не принимаются.

## Secret boundary, rollback и API

- AdminCredentialView гарантирует state/nullable invariants и redacted toString; generated password проходит только через permitted no-store response. Own-changed password возвращает USER_CHANGED/origin=null/password=null. InitialCredentialReader extraction не переносит secret/version в Academic projection.
- Audit записывает только credentialsVersion/state/origin, outbox — только notification metadata. Generated password/hash не добавлены в события/audit/исключения. Service ловит также commit exceptions после TransactionTemplate.execute и заменяет их sanitized unavailable без cause/message;200 формируется controller только после успешного возврата transaction.
- No-store controller + ранний scoped servlet filter покрывают успешные disclosures и рассматриваемые auth/validation/error responses. Existing refresh/logout cookies и CSRF protocol не менялись. Target missing/fenced/archived не становится generation success. Ошибки используют RFC9457; ожидаемые разные envelopes AdminAccessException flat code и Auth/credential extras.code сохраняются.
- V16 только расширяет Auth origin check новым admin_reset; existing normalization/version/bot invalidation triggers применяются без переписывания истории/данных. Existing password-reset-link operation остался неизменным.
- OpenAPI/TS отражают обе новые protected operations, явный200 application/json/AdminCredentialView, обязательные nullable origin/password и string credential versions. Только Headman origin enum расширен. SHA generated source соответствует snapshot.

## Значимые negative tests и evidence

Прочитан источник AdminGeneratedPasswordHttpIT: old password/access/refresh/reset ticket denial после mutation; durable readback и old-revision replay; own change clearing generated material; full/limited/admin/self; no bearer/wrong role/bootstrap/no permission/stale/revoked; missing/fenced/archived; invalid input redaction; two-admin CAS; deferred audit и outbox commit failure.

Последние failure tests сравнивают credential tuple, sessions, reset tickets, events/outbox/audit до/после; они проверяют rollback пользовательских эффектов, а не mock call counts. Read disclosure также не возвращает secret, если audit commit не состоялся. Existing выбранные Headman tests проверяют значимые последствия extraction. Дублировать DevicePreferencesHttpIT для этого пакета не требовалось.

Reviewer независимо прочитал XML attributes evidence (нового запуска нет):

- `/tmp/admin-generated-password-run2-xml/TEST-ru.rutcampustrack.auth.integration.AdminGeneratedPasswordHttpIT.xml`: tests6/failures0/errors0.
- `/tmp/admin-generated-password-run1-xml/TEST-ru.rutcampustrack.auth.integration.HeadmanCredentialsHttpIT.xml`: tests4/failures0/errors0.
- `/tmp/admin-generated-password-run1-xml/TEST-ru.rutcampustrack.auth.config.JwtAuthenticationFilterPurposeTest.xml`: tests9/failures0/errors0.
- `/tmp/admin-generated-password-metadata-xml/TEST-ru.rutcampustrack.auth.integration.OpenApiSnapshotIT.xml`: tests1/failures0/errors0.

Дополнительные независимые дешёвые проверки: `git diff --check 3c02a3241..52eb8520` PASS; JSON comparison/Draft2020-12 validation PASS: только два новых admin paths, existing operations неизменны, bearerAuth и explicit200 schema у обоих, все5response fields required, USER_CHANGED null values валидны, admin_reset GENERATED валиден, numeric version отвергается, generated file SHA совпадает. Реальную БД повторно не запускал.

Авторский summary/checks JSON прочитаны: `/tmp/admin-generated-password-summary-20261024.md`, `/tmp/admin-generated-password-checks-20261024.json`. Final own JAR SHA3f8d9d0e9ea3bd944b774c2a05246df9b3a35caa2d19aae8b8570aba68c5dc05 сообщён автором; rollout/stand comparison root/runtime-owned, самостоятельно не заявляются.

## Известные пределы и межпакетные зависимости

- Ручной mobile decoder `frontends/mobile-core/src/features/headman-group/headman-group-client.ts:650` в52eb ещё принимает только initial/headman_reset. После admin generation для student backend Headman GET законно возвращает admin_reset; старый PWA/TMA adapter отвергнет такой ответ. Это уже явно назначенный root frontend follow-up, не скрытый backend PASS для end-to-end compatibility. Generated Auth types обновлены, ручной consumer ещё требует своего correction/review.
- UI self200 volatile receipt, copy-before-navigation и unknown-self recovery ведёт отдельный consumer owner. Backend review не подтверждает его реализацию, browser runtime/visual readiness, delivery/production migration или весьPR3.

При изменении policy/locking/transaction/schema или consumer reconciliation требуется соответствующая узкая independent recheck. Этот неизменяемый verdict относится к указанному backend commit.
