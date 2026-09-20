PASS — Stage 1 принят. Блокирующих findings не обнаружено.

Проверено непосредственно по оригиналам и diff:

- Frozen contract `packet.md`: `BE5A0F...E4EF`, 16007 bytes.
- Auth13 manifest: `5DDABF...A5D18B`, 2958 bytes; все 13 файлов совпадают по SHA-256.
- Final gate: `EE2DFC...E4EF`, 4755 bytes.
- Все 15 source/test entries совпадают по SHA-256 и размеру; drift отсутствует.
- `InternalIssuerController.java` и `InternalIssuerIT.java` отсутствуют. Старый endpoint не имеет controller mapping; PostgreSQL HTTP IT подтверждает 404.
- Критичные inherited originals открыты: `SessionStatePort`, `JdbcSessionAuthority`, V24, `SecurityConfig`, `InternalIssuerSecretFilter`, `JwtAuthenticationFilter`. Принятые prior-scope hashes `JwtAuthenticationFilter`, `JdbcSessionAuthority` и V24 сохранены.

Correctness/security recheck:

- `SessionAdmissionService` разбирает только session-bound access, делает ровно один authoritative snapshot, не использует legacy identity, сверяет полный tuple и вызывает signer один раз.
- Свежий clock после snapshot и после signing закрывает ранее найденные expiry races.
- Invalid/revoked/grant/selectability/stale/unavailable outcomes отображаются в 401/403/409/503 с точным `extras.code` и `Cache-Control: no-store`.
- Producer подписывает RS256 и формирует внутренний JWT только из coherent `SessionSnapshot`.
- `InternalJwtValidator` до JJWT verification проверяет bounded strict raw header/payload: точный `alg=RS256`, обязательный непустой plain `kid`, optional `typ=JWT`, отсутствие лишних/дублирующихся header fields, duplicate JSON keys, UTF-8, размеры и nesting. Полный frozen identity tuple и configurable skew сохранены.
- Bearer/internal token не попадает в exception text, `toString`, problem body или логи; request/response DTO редактируют token в `toString`. Secret guard остаётся отдельной границей.

Runtime evidence сверено:

- Все семь final-gate XML byte-identical текущим Gradle reports.
- Shared: 22/22; Auth: 36/36; PostgreSQL IT: 6/6.
- Итого 64 tests, 0 failures, 0 errors, 0 skipped.
- Зафиксированы exit `0`, PostgreSQL 16.13, Flyway V1–V24 и очистка контейнеров.
- Исторические failures не скрыты: fixture expiry/group mismatch, sandbox classpath/ACL, неверное audience assertion, PostgreSQL timestamp invariant и неработоспособный JJWT `.sig().clear()` сохранены вместе с corrections и успешными reruns.
- Fresh shared recheck также совпадает с `5C7918...1342E3`, 3920 bytes.

Ограничения PASS: public lifecycle/refresh/logout/password, Gateway admission, BFF/WS и downstream consumer cutover относятся к Stage 2/B1a. Старые downstream test constructors и вызовы прежней сигнатуры `InternalJwtTestFactory.validToken` ещё требуют union-owner адаптации и полного downstream compile; текущий Stage 1 contract явно оставляет этот gate открытым. Код, tests, evidence, данные и внешнее состояние не менялись.
