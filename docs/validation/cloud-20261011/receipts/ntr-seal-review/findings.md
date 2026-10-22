# Независимое завершённое NTR seal review

**APPROVE** для `d674ea1ff1d26bf40f2585e0a3438c1896e60a1b`, parent `fa02ce439dfb989fc28eaddcbf1a8840d16bf9ff`. Единственное прежнее blocking finding **NTR01/P2 — critical test quality закрыто**. Новых blocking findings нет. Можно интегрировать ограниченный шестифайловый пакет fa02 и однофайловую коррекцию d674 с прежними квалификациями evidence. **NP01/P2 accepted initial-password delivery остаётся GAP.** Полная Auth/notification/client приёмка этим review не установлена.

Это новое независимое read-only review критичных originals и завершение собственного пакета `/workspace/scratch/rct-validation/ntr_seal_review`. Предыдущее partial `map_validation_recheck/ntr01-recheck/findings.md` действительно содержит APPROVE, однако completion/transfer manifest там отсутствуют. Его bytes сохранены; прежний callback/completion не приписывается задним числом. Requested model/effort — только запись scoped packet; actual metadata UNKNOWN. Reviewer не создавал детей и runtime-ресурсов, не запускал Java/Gradle/Docker/apps/browser/mutations и не менял чужие исходники или shared docs.

## Принятый lifetime и качество oracle

Критичные current originals открыты отдельно: `WsTicketConcurrencyBoundaryIT.java`, `WsTicketService.java`, публичный/internal controllers, API `WsTicketResponse.java`, integration fixture и accepted lifetime в `docs/architecture/websocket-flow.md`. Основание 30 секунд — TASK-CONTRACT P09 и назначенный NTR packet; исторические неточные детали key/transport в websocket-flow не стали новыми требованиями. Production `TICKET_TTL`, `Instant.now()`, Redis set duration, admission и consume guards неизменны.

Current test задаёт независимые 30 секунд, не импортируя mutable production-константу. Issuance wall-clock bracket отдельно проверяет advertised `expiresAt` в `[issuanceStarted+30s−250ms, issuanceReturned+30s+250ms]`. Monotonic issuance→PTTL interval ограничен 5 секундами; Redis remaining должен лежать в `[30000−elapsedMillis−250,30000]`. Guard не допускает lower bound ниже 24750 ms. Это ограниченный регрессионный oracle, не новый пользовательский SLA или точная миллисекундная сертификация произвольных отклонений TTL.

Тест сохраняет реальное Redis expiry/key absence с wait cap32s, actual HTTP404 consume и actual logout в finally. Login/issue/logout helpers проверяют HTTP200/body/HTTP204; исключения и skips не проглатываются. Fixture использует RANDOM_PORT Auth, PostgreSQL16 и Redis7 с reuse=false. Issuance проходит live Auth admission; internal consume даёт404 для отсутствующего/expired Redis ticket.

До коррекции положительный TTL≤30000, eventual expiry и404 допускали 1-секундный ticket. Это source counterexample старого oracle, а не actual production TTL defect или дополнительный выполненный старый-test run.

## Реальные сохранённые red/green квитанции

Controlled test snapshot отличается от final ровно двумя временными строками: comment и `redis.expire(..., Duration.ofSeconds(1))` только для только что выданного synthetic ticket в собственном disposable Redis. Production service продолжал выдавать30s и advertised expiry. В current/d674 temporary control отсутствует.

| Phase | Session / native exit | Remaining / elapsed / minimum, ms | Selected case |
| --- | --- | --- | --- |
| red | 86023 / 1 | 993 / 209 / 29541 | 1 relevant lower-bound assertion FAIL, errors0/skips0 |
| restored green | 48663 / 0 | 29923 / 168 / 29582 | 1 PASS, errors0/skips0; testcase31.905s |

Original safe XML независимо разобраны: один exact выбранный testcase, totals совпадают с suite, properties пусты, stderr отсутствует, stdout содержит только fixed-label integer timings. Red падает с `java.lang.AssertionError` на `Redis retains the accepted 30-second issuance window`: 993 вне `[29541,30000]`. Предшествующие observation-budget и advertised30s assertions прошли; причиной не были setup, syntax, slow observation или skip. Green PASS включает actual expiry/key absence/HTTP404/finally logout по точному выполненному selected source.

Одинаковые argv/cwd содержат compileTestJava+integrationTest, одну выбранную TTL method и maxworkers1. Original append-only lifecycle даёт prelaunch→process pointer→terminal, exact sessions/native exits и restoration между red terminal и green prelaunch. Reviewer runtime replay NOT_RUN; native exit — факты оригинальных авторских квитанций, независимо проверенные parser.

Actual private raw red/green log bytes совпали с записанными SHA; в этот пакет попали только hashes и BUILD markers. Current original raw green XML совпал с green recorded SHA и safe timing projection. Original raw red XML был перезаписан Gradle green-run; его historical SHA сохранён, наличие original red raw bytes не утверждается. Raw application logs/XML properties/streams, request bodies и runtime credentials не копировались.

## Byte identity и source bridges

Final test = restored snapshot = d674 Git blob = green prelaunch/terminal/restoration SHA256 `4d025a60b627c6777676e5f23a2d7419308ca860e38a51fcaa6ba68cad6cba51`. Before snapshot равен fa02 Git blob; controlled snapshot совпадает с red hash и после удаления exact двух temporary lines равен final.

Все **292** immediate green prelaunch tracked inputs независимо равны current worktree и d674 Git. Red/green path sets одинаковы, отличается только controlled test; остальные291 равны fa02 Git. Все **пять** других fa02 package paths current=d674=fa02 и равны original prior review source bytes. Production context snapshots также current=d674=fa02. Worktree clean; diff fa02→d674 — только existing test +20/−2; diffcheckPASS.

Terminal test hashes exact. Для всех292 mid-run/terminal cleanup оригинал содержит авторские boolean attestations неизменности, но отдельный terminal per-file ledger не был сохранён. Свежая независимая проверка подтверждает292 prelaunch=current=final Git; эта граница provenance не скрыта. Оба runtime phases выполняли uncommitted WIP при HEADfa02; source bytes связывают их с d674, без ретроактивного execution-at-HEAD-d674 claim.

## Неизменённая часть prior review и остаток

Author sealed ZIP `f05c655bf04c06e5a409cc855e022f00790bcf11293140d4b250326d81f61317`:25 original members,24 payload hashes и manifest bytes проверены. Prior independent original ZIP `53a1ffbea1d68a2b2e0040cc56af388b72bda09211c6a6c114e5fde886af3214`:9 original receipts по byte index плюс98 nested original safe inputs/CRC проверены. Scoped product `otp_requested.py` и consumer failure/claim-release/DLQ chain открыты отдельно; оригинальные prior actual outcome counts сверены непосредственно.

Prior source approval sanitized provider failure propagation переиспользуется: NP02 provider500 before дал attempts1/success0/completed=true/mainACK1/DLQ0; after failure даёт completed=false, released claim, mainACK0/DLQ1. Manual same-event replay и duplicate дают attempts2/success1/completed=true/mainACK2/DLQ0, exit0. Это bounded manual recovery/dedup proof, без automatic provider retry обещания.

Прежние отдельные qualified proofs сохраняются: local aiogram/preferences HTTP4 с controlled binding/audience; Auth race/logout и старый expiry3; отдельно real native Socket/STOMP1 с controlled Auth authority; final Python9. Повторы не складываются как unique coverage. Старые отсутствующие full per-phase dependency histories, before prelaunch hash gaps и форматный AST bridge не заполняются новыми292 hashes задним числом. Real Academic/Mongo/Telegram, combined real Auth→WS logout chain и full client/current-final CI acceptance не установлены этим review. Separate recovery prior approval не переоценивалось.

NP01 original actual oracle остаётся `delivered_initial=false`. Generic Academic RPC current=d674=fa02 и сохраняет `.setInitialPassword("")`; accepted private first-start delivery требует отдельной реализации и review dedicated safe transport. Proposal не выдана за выполненную функцию. No API/proto/config/security policy widening здесь не принято.

Cleanup квалифицирован оригинальными авторскими квитанциями: NOTIFY3_RELEASED, ownedJava empty; exact два green startup IDs независимо сверены с original raw XML, subsequent author inspect exit1/absent. Red startup IDs отдельно не сохранены; exact red-ID cleanup UNKNOWN. Reviewer Docker/process readback NOT_RUN, собственных runtime-ресурсов0. Root сохраняет integration/hooks/remote CI/Coverage/Security ownership.

Созданы только собственные docs/hash/XML/archive audit receipts; product/test source edits0, modified/deleted чужих файлов0. Полный publishable output inventory — `transfer-manifest.json`; точные payload hashes — `immutable-review-manifest.json`; sealed bytes — `immutable-review-receipts.zip`. Собственный helper первый запуск остановился на неверно предположенном literal comment, после чтения exact original comment исправлен только own audit helper; следующий полный audit exit0. Это не product/runtime failure.

Следующий шаг: root интегрирует approved fa02 package+d674 correction по source bridge и сохраняет NP01/P2 GAP и другие обязательные family acceptance gaps.
