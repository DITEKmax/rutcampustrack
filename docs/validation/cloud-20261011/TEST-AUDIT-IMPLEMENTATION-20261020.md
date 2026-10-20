# Реализация принятого аудита тестов PR #2

К2: **DELETE94/94, REWRITE169/169 обработаны**. Это263 группы методов/cases/assertions, не число файлов или готовность продукта. Каждый ID имеет текущие symbols, counterpart, verification, review и deviation в [матрице](test-audit-implementation-20261020.csv). Исходный [аудит](TEST-AUDIT-20261020.md) и [read ledger](test-audit-read-ledger-20261020.csv) сохранены побайтно. QUESTION_ONLY, «Сохраняем» и frozen controls не превращались в задания на удаление.

Baseline PR `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`; main/merge-base `3908fad9624f97aec8891fc79338ceb6b0616bf3`; исходный аудит закреплён на `47daac1bb3d48eb2a912689a66d95b938798c284`. Новый исполнительный audit snapshot: `e38e72a29854a18cf1ee3e84926f9a57d698b630`. Последующие документы отделены от него. Root — единственный Git/taskdoc интегратор; шесть sole writers работали в независимых worktrees. Продукт, migrations, generated, зависимости/lockfiles и scanner rules не менялись. Deploy workflow изменён только в разрешённом smoke-test блоке; сам workflow остаётся disabled_manually.

| Scope / sole writer | DELETE | REWRITE | Независимый review |
| --- | ---: | ---: | --- |
| Academic — audit_academic |21|37|[58ID](test-audit-review-academic-20261020.md)|
| Auth/Gateway/Shared/Notification/BFF/renderer — audit_auth_shared |15|30|[45ID](test-audit-review-auth-shared-20261020.md)|
| Attendance — audit_attendance |9|24|[33ID](test-audit-review-attendance-20261020.md)|
| Schedule/Telegram — audit_schedule_bot |15|27|[42ID](test-audit-review-schedule-bot-20261020.md)|
| Angular/Mobile/PWA/TMA — audit_frontend |28|34|[62ID](test-audit-review-frontend-20261020.md)|
| E2E/infra/CI — audit_e2e_infra |6|17|[23ID](test-audit-review-e2e-infra-20261020.md)|
| Всего |94|169|263 уникальных ID|

Все новые writers/reviewers запрошены Sol6.1/high/forknone; actual runtime model/effort metadata UNKNOWN. Root сохраняет назначенную reviewer route; actual metadata UNKNOWN. Авторы не одобряли собственный новый diff. SOURCE_APPROVE не означает runtime PASS или merge readiness.

К1/K2 integration commits: frontend +atomic browser counterpart `c959ab61`, Auth/Shared `d8c36db2`, Attendance `ac0f0517` +correction `52b5b638`, Academic `648c0137` +owner-DDL correction `0bce5f35`, Schedule Java `4e4e28ed`, Telegram `8e0f3e87`, E2E/infra `a19c94a2`, final exact CI identity bindings `e38e72a2`. Документальные checkpoints перечисляются Git отдельно.

[Точный inventory317 code/test/tooling paths](test-audit-files-20261020.csv):261 modified,40 created,16 deleted. Root сверил каждую запись с frozen writer manifest; unowned paths и byte mismatches0. Inventory SHA256 canonical entries: `0c4463386abfff31e5f5ea1950222d8eb6a01949499971e818c07851b6f4c79b`. Это контроль custody, не процент качества. Полезные units для parser/state/clock/generation/расчётов сохранены; endpoint/transaction/authz гарантии перенесены на реальные HTTP/DB/migrations там, где нужна их семантика. Удалённые source/representation/call-order дубликаты не подменяют surviving counterparts.

К3: все доменные source reviews APPROVE после corrections. [Независимый интеграционный review](test-audit-review-integration-20261020.md) дал APPROVE SOURCE на exact `e38e72a29854a18cf1ee3e84926f9a57d698b630`, blocking findings0; final317 source paths совпадают с union manifests. Узкий final CI identity review также APPROVE: exact `a116f3beedfa00fcb35dd92548bb64d49b7a44ccfe85187760511b2538697085`; reviewer независимо восстановил final delta из прежнего approved CI, hashes/coordinates/negative digests совпали. Review исправил реальные дефекты новой обвязки: fixture dates, owner-only DDL fault injection, exact settled race/envelope facts, broker replay commit barrier, потерянные distinct notification states/privacy/actions, Gradle configuration inputs, static UI readiness и HTML parser privacy blind spot. Каждая correction получила адресный recheck; продукт не подгонялся.

## Границы и технические отклонения

- E2E-R001: действующий legacy create/multipart API безусловно retired410. Реализована реальная Angular10MiB загрузка с безопасным отказом, error/no-success/no-created-ticket readback. Unsupported positive201/approval/forwarding остаётся **условным NOT_RUN**, а не выполненным обещанием. Vue покрытие отдельно и не выдаётся за legacy equivalent. Старый совет скорректирован по текущим originals и независимому review; API не возрождался.
- AO-R09: retired410 legacy endpoint не может служить HTTP201 oracle. Проверяется actual proxied domain service с request identity, реальным outbox rollback и сохраняемой транзакционной гарантией; не заявлен успешный legacy endpoint.
- MS-R02 старый arithmetic autosize удалён атомарно с реальным browser counterpart E2E-R003: remount/long draft/font/viewport/clipping/keyboard/shrink и cleanup; дешёвый teardown unit сохранён. Локальный browser NOT_RUN.
- BS-R11: actual Channel observations Academic4/Attendance5/Schedule9 проверяют возвращённый CallOptions deadline и число реальных calls, включая getters. Это не end-to-end transport timeout measurement. BS-R14 lazy isolated containers/namespaces и started-only shutdown проверены с consumers.
- Final CI delta меняет лишь необходимые exact current source hashes/method/assertion/frame coordinates после принятых rewrites, сохраняя schemas/admission/status/count/NO_RESULTS/negative expectations и закрытую privacy projection. Passive diagnostics не запускают frozen Mongo/Chromium experiments. Retained pure recovery dictionary/AST health control не вызывает native Mongo.

## Фактически выполненные адресные проверки

Frontend: mobile419/contract5/targeted App-auth59 и strictTS PASS; Web250PASS плюс один обычный R-A09FAIL. Auth/Shared: compile9modules, focused units, HTTP/gRPC1, Notification4, renderer9 PASS. Academic: final compile+104cheap tests PASS до единственной owner-DDL correction; latest correction compile PASS13s. Attendance: compile и focused rechecks PASS после двух fixture assertions; последние AO-R08/AO-R10 compile PASS11.6s. Schedule: final compile+48pure tests PASS. Telegram: Python AST/Ruff/check-format33paths PASS; focused public-path runs оставляют16 ordinary product-failing cases (не16уникальных дефектов), с двумя исправленными fixture failures. Пересекающиеся counts не суммируются; поздняя targeted проверка не объявлена полным rerun.

E2E/infra: реальные parser/Logback Gradle gates EXECUTED/PASS с полными protected config inputs; browser-free route4positive/13negative и raw privacy1positive/6negative controls PASS; другие bounded controls и source syntax/type/format checks отражены в матрице. Native browser/DOM/nginx/Alertmanager/deploy acceptance этим не доказывается. Actual Rabbit probe4 interrupted transaction phases подключён к существующему allocated broker cohort, own UUID vhost, finite120s/private projection/cleanup; локально NOT_RUN.

Один heavy Gradle lease использовался последовательно; локальный Docker socket недоступен, поэтому реальные DB/broker acceptance ожидают обычного isolated hosted CI. Не применялись sudo/alternate daemon/browser sandbox bypass. Это NOT_RUN, не PASS. Ни thresholds/exclusions, ни assertions/deadlines/retries/authz/CAS/outbox/ACK/DLQ/privacy/required gates не ослаблены; новые failures не skip/xfail.

## Отрицательные результаты, которые сохранены

Независимые reviewers подтвердили source applicability следующих текущих failures: Angular учебная дата UTC вместо Москвы после21:00Z; Telegram canonical requested display не передаётся renderer, неверный N2 target при alternate ticket_id, emitted terminal resolution_reason удаляется, actual producer new_name теряется, alert parsed-text bounds/entity и raw formatted user/chat ID privacy. Это обычные failing cases; продуктовые correction не входят в audit implementation. Дополнительно AD-R05 real-PG negative проверяет SQL CHECK допускающий LESSON+NULL через UNKNOWN; локальный DB runtime NOT_RUN, поэтому это source finding/pending hosted proof. Точные гарантии и ограничения находятся в scoped reviews/матрице.

## К4 — до публикации нового HEAD

На baseline: Security39481663732/Coverage39481663820/OpenAPI39481663485 SUCCESS, built-image scan SKIPPED. Notification127 SOURCE_METHOD PASS после единственного адресного recovery-run, без найденного source defect/новой правки. PushCI39481649904 failed D002 current-session deadline и reconciliation STRANDED/main incomplete/cleanupPASS. PRCI39481663471 attempt2 failed Gateway OpenAPI Academic TimeoutError после browserDOCX PASS. Причины UNKNOWN; audit не объявлен их исправлением.

Новый пакет ещё не опубликован и его hosted gates **NOT_RUN**. После стабильного push требуется readback exact HEAD/run/attempt и отдельная классификация прежних и новых отказов. Исчезновение имени/job не доказывает исправления поведения; NO_RESULTS/NOT_EXECUTED не PASS. Старые F01–F09/N1/N2/codeAPPROVE исторические для прежних bytes; новая audit source acceptance отделена от product/merge readiness.

Merge BLOCKED до нового независимого APPROVE, достаточных обязательных зелёных gates и обязательного К4 отчёта о реальных остатках. Deploy257646584 остаётся disabled_manually; VPS/SSH/production/provider/Telegram не выполняются. B013–B015/rejected Mongo/Chromium equivalents и запрет раскрытия unvalidated diagnostics сохранены. Чужие WIP/worktrees/data/foreign paths сохранены, cleanup0.
