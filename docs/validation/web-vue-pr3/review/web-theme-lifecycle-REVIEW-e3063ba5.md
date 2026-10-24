# WEB native theme lifecycle — scoped PREP review

**FAIL — два blocking P2 в safety-critical lifecycle.** Root execution GO не выдавать до correction и narrow recheck их влияния. Runtime/browser/Auth/API/DB не выполнялись.

Reviewed runner SHA256: `e3063ba5d19e2ea8403bc00427b6170470084cfac7f6958700e476641361bdf6`.
Exact Root `f32ff22baaa8d68de03d7606d0799f4586ca17f1`; product base `d69ac9159e3bfff169c4b2bd25adbcc7d9def771`.
Scope: `web-theme-lifecycle-prep/{runner.cjs,PREP.json,QUALIFICATION.md}`. Accepted Student29afe6 lifecycle использован как baseline, но новые два Auth attempts, middle native logout и device CAS проверены как самостоятельный риск. Не повторяется успешная backend HTTP6/device/mobile-host кампания.

## Findings — один набор

**F1 / P2 — нет response-capture barrier перед зависимыми forwards.** `runner.cjs:66`–`88`, `runner.cjs:95`–`109`.

Auth response наблюдается через async `response.json()` job; accepted bearer/SID и durable save обновляются позднее в строках77–78/51–56. Route guard с этими jobs не синхронизирован: все GET/HEAD/OPTIONS в строке98 и ws-ticket в строке102 немедленно выпускаются; native role PUT в строке101 достаточно `loginStatus===200`, который устанавливается уже по response headers в строке69, без captured/fsynced login response или canonical own SID. Capture failure в строке87 лишь устанавливает flag; read/ws paths этого flag не проверяют.

Допустимая последовательность без изменения продукта: сервер возвращает login/role200; приложение первым разбирает body и отправляет следующий canonical/session, device GET/ws-ticket либо role PUT, пока reviewer-observer ещё ждёт body или fsync. Эти forwards уже могут произойти до durable handle qualification; если capture/fsync/transport затем не завершится, driver остановится только позднее, после продолженной UI/API цепочки, с неполным handle. `login()`/`confirmOwner()` drain после UI ожиданий не создаёт барьер для запросов, которые само приложение отправляет раньше. Поэтому заявленное в PREP/QUALIFICATION «handle fsynced before dependent continuation» не следует из кода.

Нужно закрыть forward/capture окно с момента разрешённой Auth mutation. Login, возвращающий только accessToken, требует staged admission: durable preintent → accepted token capture → разрешённый canonical own-session GET → durable exact SID/owner/role qualification → зависимые API. Role response также не должен выпускать последующие зависимости до capture. Capture/fsync failure должен немедленно запечатывать дальнейшие forwards и сохранять доступные RAM handles в HOLD, без повторного login/role/refresh. Одна лишь дополнительная проверка после `page.waitForResponse()` окно не закрывает.

**F2 / P2 — корректный Root terminal proof не закрывает зависший транспорт.** `runner.cjs:43`, `runner.cjs:172`, `runner.cjs:195`.

`drain()` требует полного исчезновения `observerJobs`, `authFlights` и `prefFlights`; каждая попытка истекает через6s. После capture deadline cleanup сначала делает тот же drain. В HOLD команда `close-known-after-root-proof` снова вызывает `await drain()` **до проверки** exact свежего Root proof. Sealed/stop guard запрещает новые requests, но не завершает уже forwarded fetch/response-body observer. Обычные login/role/logout `createAuthClient` и device transport используют fetch без собственного конечного timeout; Playwright page defaultTimeout не ограничивает сетевой fetch приложения.

Например, native logout204 уже committed на сервере, но его транспорт/body никогда не завершается. Root независимо доказывает actual terminal state обоих attempts/наблюдённых SID и noPendingOwnAuth. Клиентский request всё равно остаётся в `authFlights`; каждая команда с корректным proof отвергается `OBSERVERS_NOT_DRAINED`, браузер остаётся удержанным бесконечно. То же возможно для зависшего preference GET/PUT, хотя preference не является новым Auth authority. Это ломает обещанный finite capture → sealed HOLD → fresh exact Root terminal close путь именно в unknown outcome, для которого он нужен.

Нужен bounded quiescence механизм: остановить/запечатать capture и дальнейшие callbacks, сохранить полный наблюдённый scope, а затем допускать закрытие известного backend-terminal run по свежему exact Root proof даже при неразрешённом клиентском транспорте. Позднее изменение handle/scope должно отвергать прежний proof. Не устранять finding простым признанием зависшего запроса успешным, blind retry или helper logout.

## Остальной проверенный scope

Source binding согласован: фактический runner hash = PREP.sha256, exact Rootf32 подтверждён, relevant d69→f32 tracked diff пуст, tracked working status relevant paths пуст; все14 source pins совпадают. `run/private/own-session-and-run-scope.json` отсутствовал. Reviewer не создавал reservation.

Native UI selectors соответствуют текущим WEB ThemeSelect/AppShell/RoleSwitcher/AuthFrame. Один fresh context, bounded two login slots и разные canonical user3/STUDENT SID предусмотрены; one-shot reservation и запреты debug/foreign HTTP origins сохранены. Preference PUT допускается только в трёх явных manual phases, на текущем RAM bearer, с exact theme/expectedRevision shape, observed revision и одним forward на phase. В logout/relogin phase PUT запрещён. Fresh initial null/revision0, cookie identity continuity, acknowledged dark revision после real relogin GET и OS-only no-extra-PUT observations предусмотрены. Backend CAS/account/device security acceptance переиспользуется без нового suite.

Middle logout требует native UI POST204, captured old bearer401 и terminal slot0 перед вторым login; неизвестный sent logout запрещает helper cleanup/new login. Final cleanup требует canonical exact current own SID, one-shot technical selfDELETE204 и тот же old bearer401; native UI logout claim не подменяется final technical cleanup. Эти границы разумны, но F1/F2 остаются блокирующими для надёжного unknown handling.

Four PNG maximum и `personallyInspected:false` не выдают PREP за visual acceptance. Новый собственный device не перезаписывает existing preference; null не восстанавливается несуществующим PUT/DB cleanup. QENV010 runtime12/one-delete остаётся независимым STOP: ~5.38GB<7GiB. CI/manual PAUSED; no merge/deploy/global cleanup.

Reviewer: public source/text/Git/hash reads only; no runner/test/syntax execution, browser/Auth/application network/DB/Docker/fixtures/private values. Авторский syntax PASS учтён как авторское evidence. Создан только этот report; repo/product/shared docs/runtime source не изменены. Requested `gpt-6.1-sol/high`; actual runtime model telemetry unavailable. No children.
