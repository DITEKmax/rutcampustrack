**FAIL**

1. **HIGH — Redis-сбой превращает уже committed logout/password change в HTTP 500.**  
   **Файл:** [AuthSessionController.java:90](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java:90), также строки 93, 102 и 131.  
   **Evidence:** durable вызов завершается до `invalidateWsTickets`, а [WsTicketService.java:114](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/WsTicketService.java:114) не обрабатывает Redis exceptions. Они доходят до generic 500 в [GlobalExceptionHandler.java:198](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/shared/shared-web/src/main/java/ru/rutcampustrack/shared/web/exception/GlobalExceptionHandler.java:198). Frozen contract требует, чтобы Redis был только optional after-commit signal ([shared-integration-contract.md:94](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/student-auth-a/session/shared-integration-contract.md:94).  
   **Impact:** PostgreSQL уже отзывает сессию или меняет пароль, но клиент получает 500 без очистки refresh-cookie. Повтор операции уже не соответствует состоянию, которое видел клиент; особенно опасен password change, где credential уже изменён.  
   **Воспроизведение:** заставить `wsTicketService.invalidateAllFor()` бросить `RedisConnectionFailureException` после успешного `logout`, `logoutAll` или `changePassword`; операция в БД выполнена, HTTP-ответ становится 500.  
   **Исправление:** после успешного commit выполнять Redis cleanup best-effort с журналированием/метрикой и всегда возвращать 204 с очищенной cookie. PostgreSQL failure по-прежнему должен давать 503 без очистки cookie.

2. **MEDIUM — cookie-only logout не удаляет действующие WS tickets.**  
   **Файл:** [AuthSessionController.java:85](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/AuthSessionController.java:85).  
   **Evidence:** без bearer `current == null`; после `logoutRefreshCookie()` строка 91 передаёт `null`, и строки 136–139 пропускают cleanup. [AuthSessionControllerTest.java:124](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/controller/AuthSessionControllerTest.java:124) прямо закрепляет `verifyNoInteractions(wsTicketService)`. При этом ticket остаётся доступен для consume без повторной SQL admission через [WsTicketService.java:97](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/WsTicketService.java:97) и [InternalWsTicketController.java:31](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/controller/InternalWsTicketController.java:31).  
   **Impact:** ticket, выпущенный перед истечением access token, остаётся пригодным до 30 секунд после успешного cookie-only logout.  
   **Воспроизведение:** выпустить WS ticket, вызвать `/auth/logout` только с валидной refresh-cookie, затем consume ticket — он всё ещё возвращает claims.  
   **Исправление:** вернуть из `logoutRefreshCookie` подтверждённый user identity/revoke result и после durable успеха выполнить best-effort cleanup для этого пользователя.

3. **MEDIUM — OTP проверяется и удаляется неатомарно.**  
   **Файл:** [OtpService.java:129](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:129), [OtpService.java:194](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:194), удаление начинается на строке 216.  
   **Evidence:** обе ветки сначала читают proof, а затем отдельными Redis-командами удаляют ключи и создают PostgreSQL session. Два запроса могут оба прочитать proof до первого удаления. Это нарушает сохранение OTP replay rules из [shared-integration-contract.md:90](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/student-auth-a/session/shared-integration-contract.md:90).  
   **Impact:** один одноразовый OTP может создать несколько независимых аутентифицированных сессий.  
   **Воспроизведение:** синхронно отправить два запроса verify с одним валидным кодом; оба проходят локальную проверку и могут вызвать `issueSession`.  
   **Исправление:** сделать проверку и consume proof одной атомарной Redis-операцией с единственным победителем; только победитель получает право создать сессию.

4. **MEDIUM — часть PostgreSQL failures теряет обязательный код `AUTHORITY_UNAVAILABLE`.**  
   **Файлы:** [AuthService.java:487](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java:487), [OtpService.java:126](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:126), [OtpService.java:208](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/OtpService.java:208), [TmaService.java:64](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/TmaService.java:64).  
   **Evidence:** эти JPA lookups находятся вне exception translation. `DataAccessException` попадает в generic 500, тогда как password login уже правильно переводит тот же класс отказов в `AUTHORITY_UNAVAILABLE` на [AuthService.java:102](C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java:102).  
   **Impact:** клиенты не могут отличить временную недоступность authority от внутреннего дефекта и не получают согласованный 503 contract.  
   **Воспроизведение:** настроить соответствующий `UserRepository` lookup на выброс `DataAccessResourceFailureException`; endpoint возвращает generic 500 вместо 503 с `extras.code=AUTHORITY_UNAVAILABLE`.  
   **Исправление:** единообразно переводить repository dependency failures в typed `AuthSessionException`, сохраняя 401 для реального отсутствия пользователя и отсутствие побочных эффектов.

**Repair contract**

- **Defects:** четыре расхождения выше.
- **Correction scope:** `AuthSessionController`, auth logout result seam, `OtpService`, `AuthService`, `TmaService` и узкие unit/integration tests. Gateway/BFF и новый WS protocol не требуются.
- **Verification:** Redis-down tests для logout/logout-all/password; cookie-only logout с заранее выпущенным ticket; конкурентный OTP test с ровно одним успехом и одной созданной сессией; repository-failure tests с 503 и `AUTHORITY_UNAVAILABLE`; затем прежние Stage 2 suites и OpenAPI compare.
- После исправления обязателен **свежий независимый recheck** стабильного diff и новых evidence.

Stable manifest подтверждён: SHA-256 `95A17B…C50DA1`, 59 166 bytes, все 70 source hashes совпадают, tombstones отсутствуют. `git diff --check` завершился с exit 0. Сохранённые 57/57 unit и 47/47 integration результаты внутренне согласованы, но не покрывают указанные Redis-failure, cookie-ticket и OTP-concurrency сценарии, поэтому их недостаточно для PASS. Gateway/BFF, полный WS protocol/open-socket revalidation, frontend и genuine TMA host QA остаются заявленными ограничениями Stage 2.
