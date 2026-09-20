PASS — ограниченный S3 Auth token-purpose scope на `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`.

Блокирующих findings нет. Стабильный четырёхфайловый diff выполняет frozen contract:

- [JwtService.java](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:132) подписывает `access`/`refresh`/`internal`, строго проверяет signature, issuer, audience, purpose и обязательные claims.
- [JwtAuthenticationFilter.java](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/config/JwtAuthenticationFilter.java:34) принимает только access, проверяет известную роль и очищает authentication при невалидном bearer.
- Все production-caller’ы сверены: `AuthService`, `TmaService`, `OtpService` передают extractor’ам только refresh; [WsTicketController.java](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/WsTicketController.java:48) использует строгий access alias.
- П persistence/data-loss поверхность не менялась.

Первичные XML подтверждают 13 тестов, 0 failures/errors/skips: 8 parser + 5 filter. `git diff --check` — exit `0`. Хэши всех четырёх файлов совпали с manifest после проверки.

Ограничения: reviewer не повторял Gradle lease. Full HTTP/Gateway, downstream token exchange, revocation, security scanner и внешний runtime не проверены; эти интеграционные S3 gates остаются OPEN, поэтому этот PASS не доказывает готовность всей auth/session истории.

