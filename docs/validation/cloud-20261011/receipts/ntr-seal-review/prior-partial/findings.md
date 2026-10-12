# Независимая LIGHT recheck NTR01

**APPROVE** для `d674ea1ff1d26bf40f2585e0a3438c1896e60a1b`, parent `fa02ce439dfb989fc28eaddcbf1a8840d16bf9ff`. Единственный prior blocking finding **NTR01/P2, critical test quality — закрыт**. Новых blocking findings нет. Приёмка ограничена test-only correction, чувствительностью oracle и сохранённым bounded evidence; это не whole Auth/notification/client PASS. **NP01/P2 остаётся GAP.**

Reviewer независим от автора, retained requested `gpt-6.1-sol/xhigh`, actual metadata **UNKNOWN**, fallback NONE. FULL contract/CURRENT/decisions/questions/resources/packet и prior immutable notification/recovery review восстановлены. Пишет только `/workspace/scratch/rct-validation/map_validation_recheck/ntr01-recheck`; предыдущие 58 map files и их immutable archive побайтно проверены неизменными. Java/Gradle/Docker/apps/browser/mutations/children/author/product/rootdocs writes — NOT_RUN/NONE.

## Source и oracle

`/workspace/rct-worktrees/notification-provider/services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/WsTicketConcurrencyBoundaryIT.java:72` задаёт независимые 30 секунд, не импортируя mutable production `TICKET_TTL`. Accepted basis — owner TASK-CONTRACT P09/назначенный packet и сохранённый lifetime в `docs/architecture/websocket-flow.md:46,49–50`; устаревшие детали key/transport этого документа не стали новым правилом.

Строки 75–90 охватывают issuance wall-clock bracket, monotonic issuance→PTTL observation и bounded elapsed compensation. Guard `elapsedMillis <= 5000` не позволяет lower bound опуститься ниже `30000−5000−250 = 24750 ms`. Redis remaining должен лежать в `[30000−elapsed−250,30000]`. Advertised `expiresAt` проверяется отдельно в issuance bracket+30s±250ms, до TTL assertion. Slow observation/wall-clock anomaly требует fixture/infrastructure квалификации; данный тест не задаёт новый пользовательский SLA или точную миллисекундную сертификацию всех возможных TTL отклонений.

Строки 92–96 сохраняют real Redis key expiry с bounded32s wait, реальный HTTP404 consume и logout в finally. Issue/login helpers проверяют HTTP200/body, logout helper — HTTP204; exceptions/skips не проглатываются. `AbstractIntegrationTest` использует RANDOM_PORT Auth server и fresh PG/Redis с reuse=false. Production WsTicketService/controllers/Auth/session fixture/real UTC clocks unchanged: HTTP issuance проходит current admission, response несёт реальный expiry; internal consume обращается к Redis и возвращает404 для expired key. Clock/production TTL/config/grants/guards не менялись.

Ранее `remaining>0 && remaining<=30000` и eventual expiry допускали one-second lifetime. Это был prior source/test-oracle counterexample, не actual production TTL bug и не отдельный runtime старого теста.

## Значимый red и восстановленный green

Авторский controlled snapshot отличается от final target ровно двумя временными строками после issuanceReturned: comment и `redis.expire`1s только для этого just-issued synthetic capability в своём disposable Redis. Product service продолжал выдавать30s и advertised expiry. Temporary control absent в current/final Git test.

| Phase | Selected testcase | Remaining / elapsed / minimum, ms | Result |
| --- | --- | --- | --- |
| red, session86023/native1 | 1 | 993 / 209 / 29541 | 1 meaningful lowerbound assertion failure, errors0/skips0 |
| green, session48663/native0 | 1 | 29923 / 168 / 29582 | PASS, errors0/skips0; testcase31.905s |

Оба safe XML независимо разобраны: testcase totals равны suite declarations, one exact selected method, properties пусты, stderr удалён; stdout содержит только fixed-label integer timings. Red failure type `java.lang.AssertionError` и сообщение именно `Redis retains the accepted 30-second issuance window`, 993L outside `[29541L,30000L]`. Следовательно guard и preceding advertised30s assertion прошли; это не syntax/setup/slow-measurement failure. Green выполняет actual expiry/key absence/404/finally logout по exact source, а не немедленно принимает shortened TTL.

Red/green argv/cwd совпадают, task compileTestJava+integrationTest, одна заданная TTL method, maxworkers1. Immediate prelaunch capture сохранён до running session pointer и terminal. Собственный parser проверил lifecycle timestamps и session/native-exit records из safe projection авторского append-only attempts. Native exits — авторские receipts; reviewer process/runtime replay не выполнял.

Raw red/green logs локально совпали с recorded hashes; экспортированы только build markers/hashes. Green original raw XML hash совпал с record; red original raw XML historical hash retained, overwritten Gradle XML не выдан за red. Никакие raw application logs/XML streams/request bodies/token/key values не копируются. Сохранены уже проверенные автором selected safe XML.

## Exact restoration и source bridge

Before-test snapshot равен fa02 Git blob, current test = restored target = d674 Git blob = green prelaunch/terminal/restoration SHA256 `4d025a60b627c6777676e5f23a2d7419308ca860e38a51fcaa6ba68cad6cba51`. Controlled snapshot matches red prelaunch/terminal SHA и после удаления ровно two temporary lines побайтно равен restored target. Restoration записана после red terminal и до green prelaunch.

Все **292** immediate green tracked source/config/dependency bytes равны current worktree и d674 Git. Red/green sets совпадают; hash меняется только у выбранного test, остальные291 также равны fa02 Git. Все **пять** sibling package paths current=d674=fa02 и равны original prior reviewer source hashes. Production snapshot sources и accepted lifetime doc отдельно сверены с current/Git; generic Academic RPC file unchanged и сохраняет `.setInitialPassword("")`.

Final worktree clean; difffa02→d674 — ровно one modified existing test, +20/−2, diffcheckPASS. Оба actual phases исполняли uncommitted test WIP при HEADfa02; точные hashes связывают их с d674. Не заявляется ретроактивный execution HEADd674.

## Reuse, cleanup и границы

Проверены авторские sealed25 original members/24 manifest file hashes и archive checksum. Prior root original nine review outputs и nested98 safe input members сверены с byte index/hashes; original sole NTR01/P2 verdict не переписан. Unaffected fa02 review переиспользуется с прежними ограничениями: NP02 actual provider failure/loss→DLQ/manual recovery/dedup, NP03 controlled binding/audience+actual local provider HTTP4, prior Auth race/logout и separately controlled Auth real Socket1, Python9. Повторы не суммируются как unique coverage. Старые missing full per-phase dependency sets остаются UNKNOWN; новый292 ledger не заполняет историю NP02-before/NP03/NP04 задним числом.

Cleanup — сохранённые авторские terminal receipts: NOTIFY3_RELEASED, ownedJava empty; два exact green startup container IDs взяты автором из original XML, subsequent inspect exit1/absent. Red startup IDs отдельно не сохранены; exact red-ID cleanup и независимый Docker readback не выдуманы. Reviewer новых runtime resources не создавал.

**NP01 accepted private first-start initial credential delivery не реализована/не закрыта:** prior actual `delivered_initial=false`, generic blank redaction protected, dedicated transport остаётся отдельным предложением. Никакое new security policy/contract/API/proto/config widening не принято этой recheck.

Normal commit native0 и absent common pre-commit hook — авторские recorded facts, не unexecuted hookPASS. Root сохраняет integration/final hooks/CI/Coverage/Security ownership. Native complete private chain/real bot/Telegram/browser/full client/current-final CI acceptance — NOT_RUN/UNKNOWN. Scoped APPROVE позволяет интегрировать reviewed fa02 six-file package плюс one-file correction, сохраняя NP01 и остальных обязательных family gaps.

Evidence: `source-equivalence.json`, `accepted-context-source-audit.json`, `oracle-evidence-audit.json`, `safe-session-provenance.json`, `safe-log-identity-audit.json`, `prior-review-reuse.json`, `cleanup-qualification.json`, `original-safe-evidence-audit.json`, `prior-map-preservation.json`. Own safe immutable manifest/archive сопровождают verdict. Reviewer helper первая попытка остановилась на неверном literal comment match до source/XML audit; literal исправлен в own audit script, subsequent full auditPASS. Это не product/test run failure.
