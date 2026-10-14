# IN_PROGRESS — точка продолжения

Обновлено 2026-10-14T05:54:41.196216+00:00. После resume/compaction сначала перечитать полный TASK-CONTRACT.md (SHA256 `62afda54233d3f27e894d0108c6d12a99a217f11aa686a33550739a93d6c75ed`), decisions.md, questions.md, resources.md и нужные receipts. D002–D014, упрощение процесса и постоянная авторизация владельца действуют. Root requested/selected Sol6.1 ULTRA; дети явно Sol6.1/high/forknone; actual metadata UNKNOWN.

Репозиторий `/workspace/rutcampustrack`; ветка `codex/server-validation-20261011`. Baseline/main `3908fad9624f97aec8891fc79338ceb6b0616bf3`. Последний опубликованный local=remote=draft PR head: **838d671760633bcc3aed0524ea05cd63e7a76f3e**. PR https://github.com/DITEKmax/rutcampustrack/pull/2 открыт как draft в main, не merged. Реальный push646801/session51292 завершён0/reaped; отдельные Git+authenticated PR readback совпали (current-838d-real-push-readback.safe.json). Новые Source-изменения ниже ещё не committed/pushed. Последняя серверная Java correction99f19; текущая product-config correction ещё WIP.

## Реальный результат текущего CI

Exact838d/pull_request/attempt1: CI38516604489 FAILURE (единственный E2E115696708820, остальные17jobsSUCCESS); Coverage38516604495/Security38516604506/OpenAPI38516604484SUCCESS. E2E15м38с, boot582с. Observer завершён, pending GET нет; current-838d-gates-metadata.safe.json и /workspace/scratch/rct-validation/10ab_ci_observer/838d-current.safe.json.

- FIRST3 и remaining5 steps SUCCESS; прежние8 настоящих browser/backend PASS переиспользуются без нового скачивания.
- Гео8: настоящий PWA PASS9303мс/0errors — PRESENT, точный replay, запрет изменённого payload без изменения durable mark, D005 после ручного ABSENT запрещает координаты и UNAVAILABLE без автоматического запроса. Receipt current-838d-geo.safe.json. Это не foreign/cutoff/quota/fullQ08 proof.
- Отмена/restore20: настоящий Vue PASS62652мс/0errors — отменённый журнал, история, архив LESSON-ДЗ, сохранение DATE-ДЗ, restore/reload без оживления старых отметок/вложения/ДЗ; проверены запреты прав. Receipt current-838d-lesson.safe.json. Это не весь Q07.
- Admin452 FAIL: закрытая диагностика дала **archive POST503** на718. Причина подтверждена Source: Academic Compose не передаёт INTERNAL_ISSUER_SECRET, свойство default blank, AuthUserArchiveClient бросает503 до HTTP. **DF-ACADEMIC-INTERNAL-CONFIRM-CONFIG-001/P2**. Root исправил только по одной ссылке на существующую переменную в docker-compose.e2e.yml/prod.yml; независимое review APPROVE, YAML before/after других env полей равны. После ещё NOT_RUN. Никакие значения секретов не читались. Receipt current-838d-admin-lifecycle.safe.json и Q04-academic-confirm-config-independent-review.safe.md.
- Q06 assignment594: prepare PASS45187мс, pending PASS203мс, resume FAIL1462мс/1error, cause UNKNOWN/старый fallback frame594. Recovery/Q05/role SKIPPED. Контейнеры и certificate cleanup SUCCESS; fresh-target cleanup SKIP, targets не создавались. Receipt current-838d-assignment.safe.json. Sameauthor исправляет конкретную слепую точку безопасной диагностики, не ожидаемое поведение.

## Ближайший связный пакет и следующий шаг

1. Q06 diagnostic4611131922a8c86358d1987427324c5bf32abef3 exact4files интегрирован; whole privacy review APPROVE (Q06-current-resume-privacy-independent-review.safe.md). Exact assignment Source-frame/закрытый первый DELETE status, исходные204/503 assertions/права/история/часы сохранены. Author6checksPASS44.205с/oldfallback→newframe proofPASS/allreaped54673closed/TMPgone. Первый optional control escapingFAIL сохранён и исправлен. Unique actual product cause всё ещё UNKNOWN. Новый case598, marker460/assert461; следующий consumer должен привязать новые reporter/privacy Source hashes.
2. Root WIP уже содержит Q09 exactb492cf2a (201lines, helper nonauthor edit + student completion + revocation/noeffect) и Q12 exact0c532275 (250lines, ADMIN API publication → realVue SVG/PNG download/replacement/old assets + denials). Whole и targeted wiring reviews APPROVE; Q12 SVG oracle P2 исправлен и независимо rechecked, meaningful old-PNG-fallback/new-SVG controlPASS. Root private selectors/default exclusion/type include/CI beforeQ06/P20 готовы. Combined4NodechecksPASS/allreaped99981closed/TMPgone. Actual Q09/Q12 NOT_RUN.
3. Q15 existing files-mode drill в отдельном обычном CI job; не повторять проваленный VM/VFS запуск. Старый Mongo512MiB OOM до seed; новая синтетическая Compose квота1GiB +2×256MiB PG, max3active/noports/nopersistentvolumes. Неизменённый existing test-recovery.py проверяет настоящие backup/restore/bytes/inventory и refusals. Два P2 диагностического stderr/cleanup reserve исправлены/rechecked APPROVE; newjob20min/steps18min including cleanup3, drill8 unchanged. Контролируемый old stderr leak RED → current closed output GREEN, no Docker/network. Actual newjob NOT_RUN. Files CLI proof не равен modes/API/fullP20.
4. Сохранить Q06 готовый outcome/review, выполнить обычные hooks для целого пакета, один commit/push только назначенной ветки и normal CI. Получить реальный archive204/role flow, Q06 exact failure location/status или PASS, затем P20 verifier/Q05. Не повторять unchanged FAIL; не ослаблять assertions. В существующий E2E добавлен минимальный receipt трёх Git SHA для обязательного actual checkout критерияF; YAML/4inlinePython parse/closedcheckout local exclusivewrite control PASS; все6ordinaryhooksPASS/reaped85820closed; следующий шаг normalcommit/push.

Существенные receipts выше лежат в этом каталоге receipts/. У Q09/Q12/Q15 author/review файлы одноимённые. Root общего Source writer, никаких параллельных common/config/generated/lockfile писателей. Native pointers/resources см. resources.md. Root finalcombined formatter/typecheck PASS/allreaped58450closed/TMPgone; ordinary hooks pointer /workspace/scratch/rct-validation/root_Q04_Q15_publish/q06-q09-q12-{process,terminal}.safe.json complete0/reaped85820closed; heavy lease отсутствует; author processes смотреть по собственному outcome, не запускать дубликат.

## Конечный остаток: 16 пересекающихся пакетов, не проценты готовности

Матрица scenarios.csv:218 пересекающихся families,2узкихPASS/211UNKNOWN/5ownerEXCLUDED. boundary-scenarios.csv:52 границы. verified-cases.csv:103 уникальных конечных PASS records; это не103 независимые функции. Не повышать весь family доPASS по частичному proof.

| ID | Уже достаточное → конкретный остаток |
|---|---|
| Q01 | Настоящий журнал старосты/помощника, roster, marks,68Bfile,foreign denial,logout,teacher read/write-denial,livehelper revoke PASS; основной D013 путь закрыт конечным proof. |
| Q02 | 5 реальных клиентских случаев PASS: LIMITED/empty+retry/live revoke/TMA reuse-suspend/D002 offlineHW reconnect-isolation-logout; отдельные Q03/Q14 границы не скрыты. |
| Q03 | 10 именованных исторических Auth proofs+актуальная identity; нужен настоящий role/logout-all/absolute expiry/reset/readback/Auth restart. Текущий role830 SKIP. |
| Q04 | Admin/grant/archive/group seams; исправить config503 и проверить actual archive/restore/no old authority,dated membership/promotion; Q012 отдельно pending. |
| Q05 | Moscow calendar meaningful RED→GREEN,16unit+2realPG PASS; actual3participant semester archive/restore/replay/history/delete/cache missed-event ещё не исполнены, fixture/config reviewed. |
| Q06 | Настоящие prepare+Schedule outage pending PASS; resume FAIL UNKNOWN. Нужны closure retry/adoptB/replaceC/oldteacher-history/current scope на реальном пути. |
| Q07 | 18 именованных local DB seams+actual Vue cancel/journal/restore PASS; остались отдельные generation/parity/current-teacher/transfer-replay/roomID-ETag границы. |
| Q08 | Настоящее geo/replay/D005 PASS+старые realMongo proofs; остаток exactwindows/request quota+terminal races/teacher metrics/dashboard outage,client paths. |
| Q09 | Local cutoff/helper/completion seams и новый meaningful Vue fixture reviewed; actual helper edit/completion/revoke ещё NOT_RUN, отдельные transfer/archive/unsafeURL/races остаются. |
| Q10 | Broker/outbox/dedup/preferences/tickets proofs+actual openWS revoke; остаток distinct routing→durable consumer effect/ACK/audience/binding/recovery/browser/inbox. |
| Q11 | Renderer/job/audit/revoke/5format seams; реальные async→bytes→applicable browser download/filter/order/FULL TelegramID/no credentials/livegrants связывать с existing proof и оставшимся actual flow. |
| Q12 | PNG/SVG validation proofs; новый реальный map flow Source reviewed, actualNOT_RUN. Остаток upload/current/version/download/deletion-retry/old-links/negative SVG viewer; только legend EXCLUDED D004. |
| Q13 | JWT/locale/redaction/TLS seams/P11 realTCP fixed+chunked413; недостающие current consumers/cache actor/foreign type/ETag/legacy enumtransition/protected service boundary. B014 frozen. |
| Q14 | Готовые Vue client capabilities/requests/history/inbox/map/export/errors/SW schedule/install/update/WebKit/nativeprovider gaps; не строить role×browser×format crossproduct. |
| Q15 | Schedule V17→V30 historicalupgrade finitePASS; fb4 realPGMongo→freshrestore→app/history/Mongoattachment/pending-transfer PASS (--no-files). Текущий P20 verifier cause UNKNOWN, новая fullfiles CLI job NOT_RUN; остальные accepted historicaldatasets/routedoutage-recovery/drain/config/provider границы остаются. |
| Q16 | NonAngular formatter/checks/reviews достаточны по сохранённым receipts. После mandatory acceptance нужны current all3 green/actual checkout/head equality/D009 docs equivalence/единый handoff. |

## Блокеры и границы полномочий

B013 T008 rights sensitivity и B014 GatewayIP checking: **PLATFORM_CONTENT_BLOCKED/FROZEN**, никаких agent/model/rephrase/launcher/equivalent CI обходов. Q012 standalone headman removal pending owner, блокирует только эту развилку. Q001–11 resolved. Независимая исполнимая работа есть: не WAIT/DONE.

No main push/merge/Ready/manualdeploy/production/Angular/newweb/privatecredentials/hiddenorchestrator. Angular433 tracked bytes/modes и25machine workingcopies сохранены. FullstandSTOPPED/original5vol/nativeMongo/keys/data160fence23c83 не трогать; ~5.6GB disk ниже неизменённого fullstand9,380,818,650B floor, никакого unchanged VFS/MongoOOM retry/caplower/backupdelete. Реальные production providers/hard resource isolation/P19drain/full filesystem API consumer не доказаны. RememberMe semantics не приняты/автор не назначен; LoginRequest/AuthController/AuthCookies unchanged.

История хранится один раз в attempts.ndjson/evidence/Git; CURRENT только актуальный указатель. Не создавать новые общие frameworks/wrappers или копии прежнего evidence. Следующий основной шаг — ordinary hooks → один coherent commit/push → normalCI: archive204/role, Q09/Q12, Q06 actual location/status, P20/Q05, files CLI и actual checkout receipt.
