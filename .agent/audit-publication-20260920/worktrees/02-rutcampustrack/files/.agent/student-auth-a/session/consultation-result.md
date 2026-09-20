FAIL — baseline `8002b9ea4356b10779c5bb9a6d99746d32d78ae2` не выполняет принятый контракт профиля, ролей и немедленного отзыва. Сама Direction A после уточнений пригодна к freeze.

Исходные решения подтверждены в [Р-6](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/docs/architecture/reference-rutcampustrack-design/backend-conflicts.md:144), [108-student-settings.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/wireframes/student/108-student-settings.md:113) и сохранённых финальных кадрах. Они требуют:

- массив ролей с отдельным статусом;
- активную роль в сессии, одинаковую во вкладках;
- default `STUDENT`, иначе `TEACHER`, никогда автоматический `ADMIN`;
- `EXPELLED`, `GRADUATED`, `ARCHIVED` как переключаемые read-only роли;
- смену пароля и logout-all со сбросом всех сессий, включая текущую;
- политику пароля: минимум 12 Unicode code points, цифра `Nd`, спецсимвол `P`/`S`, максимум 72 UTF-8 bytes, без normalization/truncation;
- сессии и историю только из серверных данных; отсутствующие device/location остаются `null`.

Критичные findings:

- **CRITICAL — `AuthService.java:145`.** Пароль коммитится в Postgres до Redis `KEYS`/delete, а Gateway на [JwtAuthenticationFilter.java:96](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/api-gateway/src/main/java/ru/rutcampustrack/gateway/filter/JwtAuthenticationFilter.java:96) проверяет только JWT. **Impact:** после смены пароля или logout старый access/internal JWT способен продолжать мутации до expiry. **Repro:** войти, сохранить access JWT, сменить пароль, повторить защищённую команду старым JWT.

- **HIGH — `User.java:61`, `JwtService.java:126`.** Роль и статус глобальны для пользователя, `sid`/session version/roles version отсутствуют. **Impact:** server-owned переключение роли и сходимость вкладок невозможны; статус одной роли нельзя отделить от другой. **Repro:** попытаться представить одному user одновременно `STUDENT` и `TEACHER` с разными статусами.

- **HIGH — `AuthService.java:108`.** Refresh выполняет `hasKey → delete → DB read → set` без общей транзакции. **Impact:** параллельные refresh дают гонку, а сбой после delete оставляет клиента без действующего refresh. **Repro:** два одновременных `/auth/refresh` с одной cookie либо остановка DB после удаления Redis key.

- **HIGH — `AuthService.java:61`, `academic/entity/User.java:15`.** Login запрещён всем не-`ACTIVE`, а глобальный `@SQLRestriction` скрывает `ARCHIVED`. Одновременно [UserRepository.java:44](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/UserRepository.java:44) включает в групповой список любой статус кроме archived. **Impact:** терминальный пользователь не получает собственное read-only представление, тогда как expelled может попасть в peer roster. **Repro:** назначить `EXPELLED`/`ARCHIVED`, проверить login, `/me` и список группы.

- **HIGH — `InternalJwtIssuerClient.java:63`.** Internal JWT кэшируется по `(userId, role)`, хотя выдача принимает также group/headman; issuer на [InternalIssuerController.java:31](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/InternalIssuerController.java:31) подписывает входные claims без сверки с authority. **Impact:** после смены группы, роли или статуса до четырёх минут используются устаревшие полномочия. **Repro:** дважды вызвать `issueFor` для одинаковых user/role и разных group/headman — второй вызов получает кэш первого.

- **HIGH — `ChangePasswordRequest.java:11`.** Сейчас принимаются 8 символов с lower/upper/digit и не проверяется UTF-8 byte length. **Impact:** API принимает пароль, запрещённый финальным UX, а многобайтовая строка может пройти DTO и сломаться на BCrypt. **Repro:** `Abcdefg1` проходит текущую Bean Validation; строка до 72 Java chars может превышать 72 UTF-8 bytes.

- **MEDIUM — `AuthApi.java:38`.** Нет current-session, role switch, sessions list, logout-all и account-history API. **Impact:** семь финальных состояний можно заполнить только фиктивными данными. **Repro:** перечислить mappings `AuthApi`: присутствуют login/refresh/logout/OTP/TMA/password, требуемых операций нет.

Архитектурный freeze:

- Единственная durable authority — PostgreSQL `academic_db`. Auth и Academic уже подключены к ней; Auth Flyway выключен, миграциями владеет Academic.
- `user_role_grants(id, user_id, role, status, group_id, created_at, updated_at, UNIQUE(user_id,role))`. `user_role` получает `HEADMAN`, `account_status` — `GRADUATED`. В `users` добавляется `roles_version BIGINT`; триггер увеличивает его при изменении grants.
- `auth_sessions(sid UUID, user_id, active_role_grant_id NULL, session_version, current_refresh_jti, previous_refresh_jti NULL, refresh_expires_at, created_at, last_seen_at, revoked_at, revoke_reason, auth_method, client_label NULL, location_label NULL)`. Composite FK `(active_role_grant_id,user_id)` запрещает выбрать чужой grant.
- `account_security_events(id, user_id, sid NULL, event_type, occurred_at, auth_method NULL, client_label NULL, location_label NULL)` и keyset index `(user_id, occurred_at DESC, id DESC)`.
- Старые `users.role/status/is_headman/group_id` остаются только до coordinated cutover. Dual-write источником истины не считается.
- Каждый защищённый ingress вызывает Auth admission/exchange синхронно. Auth сверяет `sid`, subject, revoke/expiry, session version, active grant, `roles_version` и текущий status; затем выдаёт internal JWT с `sid/sv/rv/role/status/readOnly`. Gateway cache internal JWT удаляется.
- Linearization point отзыва — commit Postgres. Запросы, admission которых начался после commit, отклоняются. Уже допущенная мутация может завершиться. Универсальная повторная проверка перед commit другого сервиса не обеспечивает строгую межбазовую линейность; это нельзя обещать через TTL или cache invalidation.
- Downstream boundary и каждый mutation handler отклоняют `readOnly=true`. Auth-операции switch/logout/password остаются доступны.
- Active roster содержит только активные `STUDENT` grants. Терминальный пользователь читает только собственные данные; `ownRank=null`, если его нет в roster. Нельзя автоматически добавлять self или возвращать peers.
- `SUSPENDED` не объявляется терминальным: до решения владельца он fail-closed и не выбирается. При admin-only login создаётся сессия с `active_role_grant_id=NULL`; доступны только session/list/select/logout, затем пользователь явно выбирает `ADMIN`.
- Refresh — строгий SQL CAS под `SELECT FOR UPDATE`: current JTI сдвигается в previous, создаётся новый current, fixed `refresh_expires_at` не продлевается. Concurrent loser по previous JTI получает `409 REFRESH_ALREADY_ROTATED`; неизвестный/более старый JTI — `401 REFRESH_REJECTED`. Потерянный успешный response требует нового login, что является явным availability tradeoff.
- Password change блокирует user row, повторно сверяет исходный hash, одним commit обновляет hash, отзывает все sessions и пишет event. Redis используется лишь как необязательный after-commit сигнал. DB failure возвращает typed `503`; нельзя отвечать success или стирать cookie, если отзыв не сохранён.
- Ws-ticket должен получить `sid/sv/rv/readOnly`; уже открытые sockets требуют проверки перед сообщением/закрытия при отзыве. Текущий payload [WsTicketService.java:20](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/WsTicketService.java:20) этого не содержит.

Предлагаемый будущий API:

- `GET /auth/session` → `CurrentSessionResponse(sessionId, sessionVersion, rolesVersion, activeRole?, roles[], readOnly, passwordPolicy)`.
- `PUT /auth/session/active-role` с `SelectActiveRoleRequest(role, expectedSessionVersion)` → новый access + session snapshot; refresh не ротируется.
- `GET /auth/sessions?cursor&limit` → `SessionPage(items,nextCursor)`.
- `POST /auth/logout` — текущий `sid`; `POST /auth/logout-all` — все, включая текущую.
- `GET /auth/account-history?cursor&limit` → keyset `AccountEventPage`.
- `POST /internal/auth/admit` получает access token, проверяет durable state и возвращает per-request internal JWT; кэш запрещён.

Typed errors: `INVALID_SESSION`, `SESSION_REVOKED`, `SESSION_STALE`, `SESSION_VERSION_CONFLICT`, `ROLE_NOT_GRANTED`, `ROLE_NOT_SELECTABLE`, `REFRESH_ALREADY_ROTATED`, `REFRESH_REJECTED`, `CURRENT_PASSWORD_INVALID`, `PASSWORD_POLICY_VIOLATION`, `AUTHORITY_UNAVAILABLE`.

Девятисекционный пакет ближайшему Auth-only developer:

1. **Goal:** реализовать чистую domain-модель session/role/password без подключения schema/Gateway.
2. **Context/evidence:** baseline, Р-6, финальные кадры, findings выше.
3. **Relevant scope:** новые `auth/session/model/{AuthRole,RoleStatus,RoleGrant,SessionState,SessionSnapshot,SessionRevokeReason,AuthMethod,SecurityEvent}.java`, `auth/session/port/{SessionStatePort,CredentialSessionTransactionPort}.java`, `auth/session/{ActiveRolePolicy,PasswordPolicy,SessionLifecycleService}.java` и одноимённые unit tests.
4. **Required behavior:** default selection, nullable admin bootstrap, terminal read-only, version conflicts, strict refresh CAS outcomes, revoke-all/password transaction command.
5. **Constraints:** никаких JPA entities, controller wiring, миграций, proto, Gateway, Redis authority и изменений горячих `JwtService/AuthService/JwtAuthenticationFilter`.
6. **Existing patterns:** Java records, typed RFC 9457 errors, Java-first contracts; адаптеры добавляются следующим отдельным writer.
7. **Acceptance:** все переходы выражены доменными результатами без fake grants и автоматического role fallback.
8. **Verification:** unit tests default/terminal/suspended/admin-only/switch conflict/concurrent refresh/revoke-all/password Unicode policy; затем integration gates с реальными Postgres/Auth/Gateway.
9. **Do not:** не назначать версию миграции заранее, не трогать token-purpose diff другого writer, shared claims, Gateway/BFF/proto/config.

Repair contract: **defect** — отсутствует durable session/role authority; **evidence** — findings выше; **correction** — SQL schema + Auth domain + per-request admission + downstream read-only guard; **scope** — сначала перечисленный Auth-only пакет, затем отдельные migration/Auth wiring/Gateway/shared-security/Ws/BFF пакеты; **verification** — старые access/refresh запрещены после commit, две вкладки сходятся, terminal mutations запрещены, own terminal reads не раскрывают peers, DB/Redis failure tests, PWA cache purge для schedule/homework/maps, независимый Sol high recheck.

Консультация файлов не меняла. В начале checkout был чист на baseline; во время review появился чужой unstaged `token_use` diff в `JwtService` и Auth filter. `git diff --check` завершился с exit 0, `git diff --exit-code` — exit 1 из-за этого параллельного diff; он исключён из данного verdict. После каждой repair-партии обязательна свежая независимая recheck затронутого стабильного diff.

