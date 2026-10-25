# Conditional source recheck — original group deletion resolution

**CONDITIONAL CLIENT/UI PASS по agreed resolve contract; новых подтверждённых P1/P2 в этом narrow delta нет.** Прежний permanentGET404 functional P2 закрыт на consumer стороне. Backendv6 atomic implementation/review и actual assembled runtime acceptance остаются обязательной зависимостью; whole feature/action PASS не заявляется.

Дата2026-10-24. Frozen original client `ae54606df05135e744c6cf52a1310979100c2472`, UI `f67db1face362a3031b4dff0b7eb20992b7192ad`, dependency d57a622b8 byte-equivalent по scoped client paths. Прежние immutable review/recheck сохранены. Sol6.1/high назначение сохраняется; runtime model telemetry недоступна.

## Functional P2: consumer closure

Прочитаны полный additive diff и оба resolve reports. Новый resolveGroupDeletion принимает только original operationId/groupId; делает один explicit POST `/api/academic/groups/{groupId}/deletions/{operationId}/resolve` без body/password/Idempotency-Key/new UUID. Он не использует auto401 refresh/POST replay. Captured owner проверяется перед запросом, после response и после body; только status200 + existing strict matching IDs/phase/retryable/counts/failure decoder возвращает receipt. Любая ошибка/неожиданный status/malformed/foreign/late-owner receipt сохраняется как sanitized UNKNOWN original IDs/status, без cause/body/password.

UI resolution вызывается только отдельным user button; mount/live/reconnect/GET404 его автоматически не запускают. Handler очищает password, refreshes current authority и требует GROUPS_MANAGE AND DATA_DELETE; flow использует frozen saved reference. Ответ идёт через тот же owner/ID-guarded accept. Pending PREPARING/COMMITTING/RELEASING сохраняет original reference/lock; failed/denied/404 resolution тоже сохраняет его. Authoritative CANCELLED освобождает intent, NOT_ACCEPTED даёт truthful zero-effect notice и возможность fresh preview/new user confirmation. COMPLETED сохраняет прежний terminal success cleanup. Сам UI не объявляет отсутствующий GET404 non-admission и не отменяет accepted command.

Следовательно normal wrong-password/stale-preview → originalGET404 теперь имеет explicit путь authoritative closure. При lost resolve ACK original GET может получить сохранённый terminal receipt; password-bearing confirm не повторяется. Это закрывает consumer dead end, **если** сервер реализует приведённую далее atomicity.

## Обязательный backend contract перед functional acceptance

Root/backend согласовали additive bodyless current-auth POST: missing original атомарно создаёт immutable CANCELLED/NOT_ACCEPTED zero-count receipt; original UUID uniqueness обязана блокировать поздний confirm этого intent. Если original уже принят, resolve возвращает/продвигает его существующий phase и не отменяет принятую операцию. Other actor/group не получает чужой receipt. Существующие owner/current-permission checks, exact original group binding и неизменяемый key обязательны.

Эти гарантии не доказываются DTO decoder или frontend mocks. Backendv6 ещё implementing/отдельно review: race late-confirm-vs-resolution, durable PK/tombstone, actual status mapping/counts и participant effects должны пройти backend owner gate до assembled runtime. Этот report не проверяет mutable backend заново и не принимает потенциально иную final server semantics. При отсутствующем/старом endpoint UI безопасно сохраняет unknown, но complete functional action ещё не готова. Final runtime wrong-password→resolve→freshpreview и accepted→resolve-pending остаются не выполненными этим report.

## Старые два P2 invariant сохраняются

Durable reference save по-прежнему precedes confirmation dispatch и WRITE failure throws; terminal remove best-effort. Unreadable/malformed slot не становится пустым. All post-send confirm4xx остаются UNKNOWN, originalGET404 ничего не освобождает. Resolve не меняет restore allowlist/userId storage binding, не добавляет SID/password, auto retry или fresh-key inference. GroupsPage delta только NOT_ACCEPTED notice; private selection/page cleared до потенциально failing reload как ранее. Нового mobile UI/config/styles/generated контракта нет.

## Проверки и пределы

Independent git diff --check обоих frozen commits exit0; scoped ae546↔d57a client tree diff пуст. Прочитаны новые causal tests: bodyless exact-original request; wrong-password→GET404→explicitCANCELLED→fresh preview; accepted COMMITTING retains lock; lost resolve ACK→originalGET; failure/malformed/owner withdrawal retains reference. Это meaningful source/port coverage, не backend atomicity/browser proof. Author19 client/19 state/typecheck/lint PASS прочитаны как author evidence, без повторного запуска.

Новых tests/framework/browser/HTTP/API/DB/network/build/compile/CI/product/root edits reviewer не делал. Full baseline review не повторялся. Единственная новая запись — этот внешний immutable report. STOP; процессов и ресурсов reviewer нет.
