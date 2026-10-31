# Actual0583 — read-only transport/cause assessment

**Вывод: обнаружен harness qualification gap; точная причина failed request и дефект продукта не установлены.** Generic classifier превращает любой relevant failed Auth/preferences request в global Auth capture failure, хотя failed read или transport terminal event после durable ACK сами по себе не означают неизвестную mutation. Исходный machine FAIL/HOLD сохраняется; никакой retrospective PASS или повторный Auth этим assessment не разрешается.

Scope: source runner SHA256 `0583ee119c273d545dfe50ff12fc96bc4bb50678e04b974397cc83d5c4288351`, Root343, public `run/raw-receipt-before-rootproof.json`, `run/receipt.json` и `run/qualified-343.json` shape. Не читались private handles/cookie/bearer/credential values; SQL и backend terminal state reviewer не проверял. Предыдущий narrow source PASS F1/F2 не выявил этот слишком широкий classifier; фактический run выявил дополнительный риск его влияния на lifecycle qualification.

## Наблюдения и предел причинного вывода

Public raw receipt содержит:

- `AUTH_OR_PREF_TRANSPORT_FAILED` в `NATIVE_MIDDLE_LOGOUT`, затем `AUTH_CAPTURE_FAILED` в capture/cleanup;
- native logout forwarded/status204, но `sameOldBearerStatus:null`, `confirmed:false`; второй captured own handle отсутствует;
- real login200, initial preference GET null/revision0, три native preference PUT200: light1/system2/dark3; theme observations и WT-01/02/03 сохранены;
- blocked/pageErrors пусты, captureComplete/completed false; owned browser transport quiesced/closed, UNKNOWN_HOLD;
- ни failed method, ни failed safe endpoint, ни request failure category/status/correlation в raw receipt не записаны.

Root отдельно сообщил scope facts: все6 mutating intents (guest refresh401/login200/три preference200/logout204) имеют `ACK_CAPTURED_DURABLE`; pending body/Auth/preferences0; один exact own Student3 SID. Эти private scope facts используются здесь как вход Root, а не как независимая проверка reviewer. Root read-only terminal SQL/proof остаётся отдельным шагом.

Следовательно, нельзя назвать фактический failed endpoint или method, объявить `ERR_ABORTED`, приписать abort native logout/router cancellation либо доказать product bug. Возможность безопасного read cancellation при logout объясняет, почему классификатор должен различать состояния, но не доказывает именно такую причину этого run. Нельзя также выводить old bearer401 или session revocation из одного logout204/browser close: в raw эти критерии ещё не выполнены.

## Подтверждённый source gap и минимальный correction contract

`runner.cjs:139`–`146`: `relevant` объединяет все authFlights/preferences requests без различения метода и роли запроса. В строке142 любой `requestfailed && relevant && !sealed` вызывает `captureFailure(AUTH_OR_PREF_TRANSPORT_FAILED)`, который в строках50–53 устанавливает global `authObserverFailed`, seal/stop. Этот branch выполняется до отдельной проверки unresolved mutating intent. Даже read-only GET или mutation, уже имеющая `ACK_CAPTURED_DURABLE`, могут попасть в общую эскалацию. Поэтому наличие этого error code не является доказательством unresolved mutation.

Влияние наблюдаемо по коду: nativeLogout после204 выполняет `drain()` (строка277), а drain требует `!authObserverFailed` (строка111). После общей эскалации driver не доходит до same-old-bearer read; `snapshotFor` также прямо запрещён при authObserverFailed. Затем cleanup тоже не проходит drain, и quiesce уничтожает usable browser context. This run остаётся FAIL; classifier correction должна применяться к новому admitted scope, без редактирования raw.

Минимальная безопасная правка должна:

1. Записывать отдельную safe diagnostic запись failed request: request correlation ID, method, whitelisted safe path либо path hash без query, phase/loginIndex, наблюдённый HTTP status или null и ограниченную failure category. Не писать body/headers/credentials/SID/cookie/raw URL или произвольный network error text. Существующий raw не дополнять вымышленной причиной.
2. Обязательно сохранять global seal/HOLD при unresolved forwarded mutation (`FORWARD_UNKNOWN`, `HEADERS_RECEIVED`, неизвестный ACK/body), issuance/canonical handle capture failure, owner/SID mismatch, fsync/evidence failure и позднем новом handle. Без canonical own SID read failure не должен автоматически разрешать dependent effects или cleanup.
3. Для отдельно установленного read-only failure при уже durably captured canonical own SID, без pending unknown mutation, сохранять diagnostic/неполный read criterion, а не объявлять unknown mutation. Аналогично failed transport terminal event после durable canonical ACK не должен отменять уже принятый ACK. Это не означает считать сам failed read успешным200 или игнорировать required relogin GET.
4. Применить то же различение к async response-body path (`runner.cjs:148`–`202`): иначе read abort после headers снова попадёт в общий `catch -> captureFailure` (строка196), даже если requestfailed classifier исправлен. Исключение для known read transport/body failure не должно поглощать fsync, authority/schema/owner или mutation-body errors. Final dependent API остаются за существующим capture barrier.
5. После acknowledged native logout и только при known canonical scope позволять штатную bounded same-old-bearer GET qualification. Если она401 и logout204, первый SID terminal; затем разрешён ровно предусмотренный второй normal login. Не добавлять refresh/login/logout/selfDELETE retry, helper cleanup или возобновление старого HOLD scope. Если terminal qualification сама неизвестна — truthful STOP/HOLD по исходным exact-own правилам.

Это harness correction; evidence не требует продуктовой правки, нового backend suite или повторения успешной device HTTP6 кампании. Точный source diff/freeze должен пройти narrow classifier/influence recheck перед новым отдельным Root GO. Производный PWA7bb не получает GO или actual qualification из этого report; его аналогичный classifier надо согласовать по тому же contract до исполнения.

## Можно ли закончить relogin на прежнем device после quiesce?

**В текущем0583 — нет поддерживаемого retained device context/cookie, которым можно безопасно продолжить.** `cookieHash()` (строка264) получает cookie в локальную переменную и возвращает только SHA256; долгоживущая `initialCookieHash` содержит digest, а не raw cookie. StorageState/cookie backup не создаётся. `quiesceForRoot()` закрывает page и Chromium; context был непостоянным `browser.newContext()`, без persistent userDataDir. Node references `context/browser` не сохраняют доступный cookie jar после закрытия Chromium. Исходный `__Host-rct_device` нельзя восстановить из hash или заново прочитать через закрытый context. Bearer в RAM не является device identity.

HOLD допускает только Root terminal close; старый run не имеет resume/relogin команды, stop/seal не снимаются. Добывать hidden Playwright state, реконструировать device cookie, создавать его по hash, переписывать reservation или подменять тот же device новым нельзя. Root proof закрывает terminal cleanup и сохраняет original FAIL; он не возвращает уничтоженный браузер и не доказывает same-device relogin.

Минимальный оставшийся actual case после corrections и отдельного GO: **новый fresh dedicated device**, normal Student3 login/canonical SID → real initial null/revision0 → один native manual-dark CAS/ACK на этом новом device → native UI logout204 + его exact old bearer401 → normal второй login на том же всё ещё живом device → real dark GET с **той же новой committed revision**, без PUT после logout → exact current-own final selfDELETE204/same bearer401. Cookie identity continuity проверяется в RAM до/после обеих границ. Это новый scoped suffix case; прежняя dark revision3/device не заявляются переиспользованными. Достаточен один новый final relogin frame, если Root фиксирует такой bounded scope.

Первые три manual/system/live-OS theme observations и их original PNG сохраняются и могут переиспользоваться без повторного capture/light/system campaign. `qualified-343.json` содержит author personal-inspection flags; reviewer здесь PNG не осматривал и не повышает их до loaded-content/whole-screen readiness. Loading qualification учитывается отдельно. Не нужно повторять эти три frames ради завершения только missing relogin criterion.

Created: только этот assessment. Code/product/shared docs/raw receipt/private state не изменены. Reviewer execution/Auth/browser/network/DB/tests —0. Root343/source0583 подтверждены source hash; actual terminal proof остаётся у Root. Runtime12 QENV010 STOP и manual CI PAUSED не меняются. Requested gpt-6.1-sol/high; actual model telemetry unavailable. No children.
