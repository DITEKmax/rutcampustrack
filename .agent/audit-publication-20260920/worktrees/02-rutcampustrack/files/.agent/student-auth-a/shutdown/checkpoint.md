# A — STOPPED, пауза владельца

2026-09-08. Все прежние GO и reservations поставлены на паузу. НЕ возобновляться по времени, сообщению другого агента или прежнему разрешению. Требуется новое прямое задание владельца. Разработка, review, проверки и runtime остановлены; после команды выполнялась только инвентаризация и сохранение shutdown checkpoint. Reset/clean/rollback/commit/deploy/удаления данных не выполнялись.

## Checkout и WIP
- Outer cwd: C:/Users/maksd/.codex/worktrees/1456/rutcampustrack
- Nested UI cwd: C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/worktrees/profile-ui
- Оба HEAD: 8002b9ea4356b10779c5bb9a6d99746d32d78ae2.
- Полный outer dirty inventory: соседние git-status.txt и wip-manifest.json, 83 записи, точные пути/SHA256/bytes; вложенный worktree отмечен отдельно. Manifest SHA256 6B3FD373D4A6C3E690A1D7C2CCA71FA3DAC93920961A8C91C4DD884E5BB34077. Сам каталог shutdown исключён из собственного manifest.
- UI shutdown checkpoint: C:/Users/maksd/.codex/worktrees/1456/rutcampustrack/.agent/worktrees/profile-ui/.agent/profile-ui/shutdown-checkpoint.md, SHA256 35944719E9578D10672CC7B2723C13BFAB8286824C2341AF0796D7853F1E0658. В нём полные 38 product paths и hashes, evidence paths и реальные последние checks.
- Существенное расхождение ownership, обнаруженное при shutdown: дополнительные 7 Vue и profile-state.test.ts лежат также во внешнем frontends/mobile-core/src/features/profile; .agent/profile-ui/{progress.md,status.md} также во внешнем checkout. Возможны misplaced default-cwd patch writes. Всё сохранено без переноса/исправления/удаления. Не импортировать эти копии как проверенную финальную UI-партию. Сначала после прямого resume сверить обе версии и источник каждой правки.
- Собственный pending documentation patch был прерван: repair-01/root-checks.md и session-jdbc/preflight.md успели появиться, session/ws-preflight.md не появился. Это WIP, не основание запускать следующую партию.

## Статус каждого ребёнка A
| Задача | Статус на паузе |
| --- | --- |
| profile_ui | STOPPED, FINAL получен; checkpoint сохранён; своих devserver/watchers не запускал |
| session_domain_recheck | INTERRUPTED по STOP; независимый verdict НЕ получен, review не завершён |
| session_domain_repair | Ранее FINAL RELEASED; 21 actual tests PASS, source/evidence frozen |
| session_domain | Ранее FINAL RELEASED; исходная партия 17 tests, затем Sol findings и repair-01 |
| session_architecture | Ранее COMPLETED; source-based консультация baseline, не runtime-verdict нового flow |
| auth_purpose | Ранее implementation/evidence закончены, затем прерван после provenance correction; не активен |
| purpose_review | Ранее COMPLETED; bounded purpose review сохранён |
| purpose_evidence_recheck | Ранее COMPLETED; byte-copy provenance recheck PASS сохранён |

Актуальный live inventory перед финалом: profile_ui completed STOPPED, session_architecture completed, session_domain_recheck interrupted. Других активных детей нет. Не возобновлять ни одного без новой прямой команды.

## Что готово и actual evidence
- Purpose4: отдельно принятый bounded diff с strict token_use и 13 focused tests; provenance correction-01 содержит точные binary XML. Manifest и independent reports сохранены в .agent/student-auth-a/purpose. Это не доказывает durable revocation.
- Pure session domain: 13 main + 3 test classes. Repair-01 исправляет selectable snapshot, revoked-result invariant, private Evaluation overload; фиксирует trusted exact-newPassword hashing obligation. Final manifest .agent/student-auth-a/session-domain/repair-01/manifest.json SHA256 2200AED1C86FA3D54BC2AF1C3E6BD642A2923B36858406B349255935BEA8BA64.
- Последний actual Gradle: .\gradlew.bat :services:auth-service:auth-app:test --tests ru.rutcampustrack.auth.session.ActiveRolePolicyTest --tests ru.rutcampustrack.auth.session.PasswordPolicyTest --tests ru.rutcampustrack.auth.session.SessionLifecycleServiceTest --no-daemon --max-workers=1. Start 2026-09-08T00:13:42.109Z, end 00:14:26.769Z, exit 0, BUILD SUCCESSFUL 42s; 4+6+11=21 tests, 0 failures/errors/skips. Root подтвердил 16 source SHA/bytes и 3 binary XML pairs, 0 mismatches. Gradle lease RELEASED и принят parent. Повторных тестов после STOP нет.
- Domain review gate OPEN: fresh Sol был запущен после FINAL автора, но остановлен владельцем до verdict. Не считать исправленный домен окончательно принятым.
- UI WIP: все 7 Vue, typed state/ports, scoped PCSS, 27 SVG (root подтвердил 27 исходных SHA без расхождений). Последние actual checks по shutdown автора: Vitest 16 PASS exit 0; vue-tsc PASS exit 0 до последних правок; ESLint --max-warnings=0 FAIL (0 errors/39 warnings), после --fix exit 0 оставались 17 require-default-prop warnings. Последний default-prop patch прерван/возможно частичный. Текущий WIP нельзя объявлять целиком прошедшим checks. Browser/screenshots/keyboard/themes/responsive/independent UI review не выполнены.

## Owned runtime и остановка
- Outer A не запускал devserver, watcher, контейнер или PostgreSQL DB. Собственный Gradle завершился с exit 0 до STOP, lease освобождён; новых тестовых процессов не запускалось.
- UI writer: Vitest/vue-tsc/ESLint процессы завершились в своих actual command results; devserver/watcher/browser harness не запускался. Чужие node.exe не остановлены. Win32_Process attribution недоступен из-за permissions; неизвестные чужие процессы оставлены нетронутыми.
- Read-only reviewer не имел назначенного runtime; прерван.
- Root pending tool patch остановлен, UI pending patch остановлен. Данные/volumes/каталоги не удалены. Нет известного собственного продолжающегося runtime PID. Не заявляется, что все системные порты/процессы машины свободны: широкая process/port инвентаризация ограничена permissions.

## Оценка готовности направления
Frontend: есть каркас всех экранов и локальная логика, но это непринимаемый WIP до сверки misplaced copies, завершения lint и визуальной/интерактивной проверки, independent review и подключения к реальному shell/API.
Backend: purpose boundary принят, domain прошёл focused tests, но recheck открыт. Реальные JDBC/Auth HTTP/JWT admission/WS flows ещё не реализованы; integrated auth/profile не готов.

## План и ownership только после прямого resume
1. Сверить outer/nested UI WIP с checkpoint hashes, не теряя ни одной версии. Фиксировать корректный cwd и sole writer; исключить повторение misplaced patch writes.
2. Завершить свежую независимую Sol high recheck frozen domain manifest; не повторять Gradle без нового source/failure evidence.
3. Получить от parent фактический tested/reviewed B0 handoff, открыть critical SQL/DTO originals и сверить hashes. Затем новый frozen nine-section packet и отдельный GO/lease для B1a.
4. B1a зарезервирован (сейчас пауза): только new auth/session/jdbc/JdbcSessionAuthority.java и test/session/jdbc/JdbcSessionAuthorityIT.java + own evidence. Два существующих atomic ports, fresh PostgreSQL16/reuse=false/task DB rct_student_auth. Никаких B/global/reused DB. Read-only gated draft: session-jdbc/preflight.md. Не начинался.
5. B1b отдельный finite packet: Auth controllers/JDBC reads/login-refresh-OTP-TMA/JWT original-access admission. Parent принял extras.code + stable type, Auth-local all-validation secret redaction/no-store и exact validated password-to-hash. Политика atomic SQL/fixed refresh expiry/bootstrap остаётся в frozen shared-integration-contract.md + addendum. Не начинался.
6. Отдельный будущий WS packet после B1: Auth issue/consume + notification open socket inbound/outbound authority. Exact source findings: SubscriptionAuthInterceptor.java31-42 passes non-SUBSCRIBE/SEND/null/unknown destination; WebSocketConfig56-58 only inbound; WsTicketClient46 no-op onStatus4xx может разобрать identity-shaped error body, 56/60 raw logging. Негативы SEND/unknown/terminal group/live outbound revoke/401-403-404 identity JSON before deserialization. Протокол и exact shared paths ещё не frozen; не начинался.
7. UI: завершить source-bound styling/light icon contrast/default-prop cleanup и текущие state edge cases; meaningful checks и browser 320/390px, light/dark/system, keyboard/200%; independent review. Затем parent wiring. Genuine TMA и SQL/HTTP/Gateway/WS/scanner/PWA two-tab/cache purge остаются открытыми интеграционными gates.

Parent owns final reservations/union integration; B owns shared migrations/DTO/proto/export; C Gateway; A ограничен domain/JDBC/Auth/profile и отдельно назначенным WS follow-on. Никаких shared/shell/generated правок без следующего finite packet. Автоматического возобновления или heartbeat нет.
