# Self8 native harness — узкий F1–F3 recheck

**PASS по source closure F1–F3 и причинному влиянию corrections. Новых подтверждённых blocking findings нет.** Это PREP acceptance, не actual native/runtime PASS или technical/browser GO. Application c586 source review и прежний `REVIEW-native-a1329d5a.md` CHANGES REQUIRED сохранены; full review не повторялось.

Frozen harness SHA256 `d89694a68108403c68e80cb9247be45102ff5c7d2d470bdb55c7ca12c86d32e5`; FREEZE SHA256 `f2e4fd00600bcd824fc4ccc293ba8c04b032cac6ca19e381bcdd96d764838291`. Оба пересчитаны, все10 frozen files совпали; публичные templates JSON parse PASS. Прочитаны CORRECTION, affected code/predicates/cleanup/close paths и exact-source offline selfcheck/log. Author syntax PASS и16 offline predicate assertions приняты как existing evidence, не перезапускались. Requested Sol6.1/high; actual served-model telemetry недоступна.

## F1 — закрыт

`credentialRevoked` (`:46`) разрешает causal revocation только для exact SID, перечисленного **до forwarding** реально COMMITTED typed200 generation (`:69–71`). Позднее созданный successor SID старым COMMIT не квалифицируется. В withdraw дополнительно требуются old-session401 и эта causal связь (`:97`).

При отсутствии такого COMMIT `cleanCapturedOwn` (`:108–113`) требует реальный own snapshot200 с exact user8/SID и canonical live-own-session list, сохраняет single DELETE attempt до отправки и принимает настоящий204. Exact current-own SID DELETE поддерживается существующими AuthSessionApi/AuthService/JdbcSessionAuthority; адресно проверено, что операция транзакционно отзывает этот SID, включая текущий. Session401 после ADMIN revoke больше не превращается в revocation proof.

Own cleanup запускается **до** ADMIN revoke (`:115–116`). Если квалификация не удалась, known revoke всё равно может завершиться, но ownQualified=false блокирует seed logout/cleanupDone и сохраняет hold. Исходный failure-before-first-credential сценарий больше не оставляет stale bearer401 как успешную очистку.

## F2 — закрыт

Каждый submitted login сохраняет единственную отправку, start/finish window, actual HTTP outcome и accepted SID перед доставкой ответа клиенту (`:57–58,62,73,79–80`). Повторная отправка той же попытки блокируется. `loginResolved` (`:45`) требует definite401/403 rejection либо captured accepted SID с qualified cleanup; UNKNOWN+noSID не разрешается пустым sessions array.

`cleanup` требует разрешения всех own attempts перед seed logout и всех actors после него (`:116`); pre-grant cleanup branch также требует `actors.every(loginResolved)` (`:135`). Поэтому ambiguous own login с пустыми generations/handles допускает известный access revoke, но не закрытие module/browser или потерю seed recovery. Дополнительного login/password retry для resolution нет.

## F3 — закрыт

`scopeOf`/`persist` (`:41–43`) теперь связывают actual in-memory run/admission/root/harness/target, profile, armed/pending/unknown, **все** access originals/body/status и credential attempts/versions/SIDs/pre-COMMIT SID inventories/outcomes, а также login windows/handles. Password исключён из correlation scope; secretCaptured boolean не раскрывает значение. На каждый scope change обновляется timestamp.

Close требует exact current-run realpath/0600 proof, actual source guard, scope/actor digests и полный original-key set. `terminalMatches` (`:47`) отвергает proof до последнего изменения, будущую observation, age>=5min и expiry позднее5min от observation. Root по-прежнему должен реально установить terminal/account/student/authority/all-logins/all-sessions facts; template flags сами proof не образуют.

Перед qualification `sealAndDrain` (`:49,124`) блокирует новые browser API и дожидается tracked login/credential route.fetch, authority requests и async observers. Обе перехваченные route.fetch delivery ветки await завершение до decrement flight counter. После принятия proof finalizer повторяет seal/drain и digest comparison; drift возвращает hold, перед close есть синхронная повторная проверка (`:136–137`). При normal automatic cleanup finalizer также повторно требует resolved login set после drain. Late accepted login больше не может молча расширить закрываемый handle set.

## Сохраняемые пределы

Admission/sourceguards и известный конечный бюджет не ослаблены: own8 generations5→6→7, один FULL grant4→5 и revoke5→6; исходные Q3 операции не повторяются. Unknown credential outcome по-прежнему блокирует новые mutations и требует квалифицированного Root terminal close. Safe natural retirement остаётся `KNOWN_OUTPUT_UNOBSERVED`, без дополнительного credential POST ради PASS. Successor login/receipt isolation остаётся под принятым application c586 и native assertions. Новый DELETE ограничен уже captured/current-own/live-correlated SID; all-session/other-actor cleanup не добавлен.

Создан только этот отчёт. Reviewer выполнил public source/evidence/SHA/JSON reads и адресную проверку существующего Auth contract; product/runtime/private fixtures не изменялись, credentials/handles не читались. Reviewer build/test/browser/API/DB/runtime/private reads=0. Fresh actual Root admission/readiness/baseline и отдельный one-browser GO остаются за Root; текущий Cred5 baseline и actual native result этим review не изменены. Дополнительные успешные тестовые кампании для source closure не требуются.
