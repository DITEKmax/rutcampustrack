# Profile actor3 own cleanup helper — source review

**CHANGES REQUIRED — один P2: uncertain new helper login не удерживает процесс/RAM recovery.** Остальной exact-target/request-scope/known-cleanup path проходит source review. Это PREP verdict, не actual cleanup или postguard PASS.

Проверены весь81-line `cleanup.cjs` и PREP.json; runner SHA256 `5e6a6569cb9cdb9b7f50fd4e9b91bd668b87d50a510fdc00fb511abf0994be20` совпал. Public baseline SHA256 `7e5a6a3a3daaa7a81c0ecc0bd7fe933832be7e0c545da31acf25b8ce00c23b51` также совпал. Private input/proof/guard/helper handles не читались. Existing Auth session/logout source прочитан адресно; никакого Auth/browser/API запуска reviewer не было.

## F1 — P2: UNKNOWN new login превращается в process exit

**Путь:** `cleanup.cjs:60–65,81`.

После phase `ONE_HELPER_LOGIN_PENDING` helper отправляет единственный login. При lost/unparseable response, отсутствующем token, failed JWT decode или failed canonical qualification code попадает в общий catch81, сохраняет fixed STOP и задаёт exitCode1. Promise завершается; keepalive/HOLD отсутствует. RAM cookies/token теряются, хотя новый helper login мог создать live session. Даже известный captured SID до `helperQualified=true` не получает finally logout, поскольку finally начинается только после login/claim parsing.

Это расходится с требованием **NewAuth unknown HOLD** и снова создаёт ситуацию «session создана, поддержанный recovery bearer/cookie недоступен». wx lock предотвращает blind rerun, но не сохраняет уже полученный RAM recovery context; independent SQL способен найти/квалифицировать session, однако сам не заменяет разрешённый own-session API cleanup.

**Минимальная correction:** различить definite rejected/no-new-session и unresolved/accepted-but-not-qualified helper login. Во втором случае сохранить попытку/phase/counts и известный SID, оставить процесс/RAM state в read-only hold до отдельного Root resolution; не выпускать новый login/DELETE/logout из catch и не выводить bearer/cookies. Definite known helper logout204+same-bearer401 может завершать helper как предусмотрено. Не нужен новый framework или успешный повторный auth для проверки source correction.

## Source, которое проходит

- Execute/env GO и новый wx0600 lock создаются до login; старые guards/held driver не сбрасываются и не извлекаются. Публичный original receipt и independent baseline pinned SHA; код проверяет immutable private old guard mode0400/hash/proof и original known PWA actor3 hold. Helper не запускает browser, не подключается к held TTY/CDP и не повторяет screenshot/original PWA flow.
- Допустимы только own current/session-list GET, один обычный actor3 login, **один exact old SID DELETE**, один qualified helper cookie-only logout. Нет Auth refresh, role PUT, admin/grants/other-user/business/pref operations, redirects, mutation fallback/retry. Бюджеты отдельные и fail-closed. Созданный helper SID должен отличаться от old SID.
- Canonical current200 требует user3/exact helper SID; live own list должна содержать exact old non-current SID с createdAt в исходном case window. Перед DELETE current повторно квалифицируется; после genuine204 требуется отсутствие old SID в own list и неизменность всех остальных live own handles кроме helper. Stale bearer401 не считается доказательством удаления old session.
- Cookie jar заполняется только ответами этого one-login HTTP context и остаётся в RAM. Existing AuthSessionController поддерживает cookie-only `/auth/logout`: logoutRefreshCookie отзывает его session и очищает cookie. Helper перед logout повторяет canonical current и проверяет204 + subsequent401 того же неизменённого bearer. Это один ordinary helper logout, не fallback исходного driver. Known helper finally-cleanup ограничен helperQualified; unknown DELETE/logout не повторяется.
- Private helper file fsync0600 содержит handles/attempt metadata без bearer/password/cookie; public output — fixed codes/statuses/booleans, raw errors не печатаются. Helper не объявляет самостоятельный DB preservation proof и не закрывает исходный held browser.

После F1 correction/recheck Root отдельно выдаёт bounded cleanup GO и выполняет предусмотренный independent PG post: exact old+new own sessions revoked, остальные identity/grants/profiles/session-revocation facts unchanged. Только этот post разрешает known-terminal close исходного driver; raw original FAIL/UNKNOWN и его PNG/GET200 observation остаются immutable. Новые security writes/оригинальный screenshot replay этим review не выполнялись и не запрашиваются.

Создан только этот отчёт; product/Git/runtime/private files не изменены. Reviewer tests/Auth/API/browser/DB/runtime/private reads=0. Requested Sol6.1/high; served-model telemetry недоступна.
