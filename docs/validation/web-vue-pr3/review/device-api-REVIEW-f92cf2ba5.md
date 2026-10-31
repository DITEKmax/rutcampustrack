# Независимое review browser device preferences

Результат: **PASS source review; подтверждённых P1/P2 нет**.

Ревизии: `60346b26b..f92cf2ba5`; 15 файлов. Reviewer независимо прочитал Git diff, новые API/DTO/controller/service/handler/migration и negative/concurrency HTTP tests, существующие AuthService.admit, JdbcSessionAuthority, оба JWT filters и применимые exception/security contracts. Read-only; продуктовых правок, Gradle/Docker/install/CI, commits/push не выполнял. Модель задания явно gpt-6.1-sol/high; runtime telemetry недоступна, подтверждение фактической модели не заявляется.

## Проверенные инварианты

- Context хранит только SHA-256 случайного 32-byte selector, theme/revision/expiry; нет account/SID/IP/UA linkage. Logout не удаляет этот контекст; API не требует совпадения аккаунта с cookie.
- Cookie исключительно selector: __Host-, Secure, HttpOnly, SameSite Strict, Path /, без Domain. Bearer обязателен; service повторяет live admission внутри preference transaction. Новые bootstrap allowances ограничены точными GET/PUT и точным путём в Auth и Gateway; соседние routes/methods не добавлены.
- Оба TransactionTemplate используют один внедрённый PlatformTransactionManager и default REQUIRED. JdbcSessionAuthority.snapshot берёт user и session FOR UPDATE; внешний transaction DevicePreferencesService сохраняет их до preference commit, затем берёт device row FOR UPDATE. Existing logout/revoke использует тот же порядок user/session.
- CAS сравнивает revision под row lock, увеличивает один раз, повтор old revision получает conflict. Controller формирует 200 только после возврата TransactionTemplate.execute, включая commit. Storage/commit exception сворачивается в sanitized unavailable; auth failure сохраняет отдельный код.
- Expired/unknown PUT требует GET/reconcile; GET выдаёт новый случайный context. Cleanup ограничен batch, работает только по expired device rows, использует FOR UPDATE SKIP LOCKED и не удаляет session/audit.
- API документирует unknown PUT outcome/readback; ошибки/response body не возвращают cookie/selector/SQL. no-store выставлен на успешные responses и рассматриваемые problem responses. Refresh cookie/CSRF/PWA/TMA protocol код не изменён.

## Проверки и пределы

- `git diff --check 60346b26b..f92cf2ba5` — PASS (exit 0).
- HTTP suite содержит существенные проверки реальной PostgreSQL: durable context/account switch, cookie-only denial, stale/revoked/bootstrap admission, CAS race/replay, expiry/validation, storage outage/deferred commit failure, lock serialization versus logout.
- Reviewer эти runtime tests не запускал: HEAVY lease принадлежит backend_device. Их PASS не заявляется. В исходном f92cf2ba5 read-only fixture ожидал selected-access JWT для expelled; наблюдаемая узкая незакоммиченная correction переводит проверку на bootstrap + /auth/session readOnly role. Автору/root об этом сообщено; финальный test result/revision обязателен для приёмки.
- Generated OpenAPI snapshot/types — заявленный follow-up, не проверенный реализованный output. Browser frontend reconciliation и полноценная PWA/TMA runtime compatibility не входят в этот source diff и этим review не объявлены PASS.

При изменении product logic/contracts требуется narrow independent recheck соответствующих мест и влияния. Этот отчёт относится только к указанной стабильной ревизии.
