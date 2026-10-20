# Реализация принятого аудита тестов PR #2

К0: owner GO на все 94 DELETE и 169 REWRITE. Это 263 группы методов/cases/assertions, не число файлов и не готовность продукта. Исходный аудит и read ledger сохраняются. [Матрица всех IDs](test-audit-implementation-20261020.csv), [принятый список](TEST-AUDIT-20261020.md).

Baseline PR: `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`; main/merge-base: `3908fad9624f97aec8891fc79338ceb6b0616bf3`. Исходный аудит закреплён на `47daac1bb3d48eb2a912689a66d95b938798c284`. Source applicability проверяется для каждого текущего оригинала. PR open/ready/unmerged. Root — единственный Git/taskdoc интегратор существующей PR-ветки; продукт не меняется ради тестов.

| Scope / sole writer | DELETE | REWRITE |
| --- | ---: | ---: |
| Academic — audit_academic | 21 | 37 |
| Auth/Gateway/Shared/Notification/BFF/renderer — audit_auth_shared | 15 | 30 |
| Attendance — audit_attendance | 9 | 24 |
| Schedule/Telegram — audit_schedule_bot | 15 | 27 |
| Angular/Mobile/PWA/TMA — audit_frontend | 28 | 34 |
| E2E/infra/CI — audit_e2e_infra | 6 | 17 |
| Всего | 94 | 169 |

Все writers работают в отдельных worktrees от baseline; target files scopes не пересекаются. Shared helpers имеют назначенного автора; изменения consumers согласуются до переноса. Все новые агенты запрошены Sol6.1/high/forknone; фактическая model metadata UNKNOWN. Свежие read-only reviewers проверят новые гарантии и итоговый diff независимо от авторов.

К1: локально интегрированы DELETE 43/94 и REWRITE 65/169: frontend/atomic E2E counterpart `c959ab61` и Auth/Shared `d8c36db2`. [Независимый frontend review](test-audit-review-frontend-20261020.md) SOURCE_APPROVE для62ID; Auth/Shared review PENDING. E2E-R003 перенесён вместе с MS-R02 counterpart: старый арифметический autosize oracle заменён проверками в существующем реальном browser/server сценарии. Все 263 IDs назначены. SOURCE_READY, INTEGRATED и SOURCE_APPROVE не равны runtime VERIFIED. Матрица отдельно хранит counterpart, verification, review и deviation/blocker. Ошибочный старый совет корректируется по текущим источникам с независимой проверкой; QUESTION_ONLY и защищённые controls сохраняются.

Для точных frontend bytes выполнены focused mobile/PWA/TMA 419 PASS, contract 5 PASS, дополнительный App/auth 59 PASS, strict foundation TypeScript PASS. Web focused: 250 PASS и один обычный FAIL R-A09 (UTC вместо московской даты после 21:00Z); этот отрицательный oracle сохранён, продукт не изменён. Причина подтверждается `HeadmanApiService.getTodayLessons` и Schedule `ClockConfig`; независимый reviewer проверяет применимость требования. E2E counterpart пока SOURCE_ONLY: локальный браузер не запускался. Inventory/hashes и per-ID результаты собраны авторами; root проверил baseline/current hashes при переносе. Неизменённые product и dependency inputs не перенесены из writer worktrees.

К2 в работе: все263 ID разобраны, имеют текущие symbols/counterparts и результат в матрице; непрочитанного/неназначенного хвоста нет. Локально интегрированы и прошли scoped независимый source review DELETE84/94 и REWRITE140/169 (224ID); runtime ограничения хранятся отдельно. Дополнительные commits: Attendance `ac0f0517` и две independently rechecked corrections `52b5b638`, Academic `648c0137` и owner-DDL correction `0bce5f35`, Schedule Java `4e4e28ed`. [Auth/Shared review](test-audit-review-auth-shared-20261020.md), [Academic review](test-audit-review-academic-20261020.md), [Attendance review](test-audit-review-attendance-20261020.md). BS-R11 shared helper и actual Channel consumers проверены независимо: Academic4, Attendance5, Schedule9 вызовов с deadline/count oracle; это не измерение end-to-end timeout.

Остаются Telegram17ID и ещё22 E2E/infra ID. Независимое review обнаружило и вернуло авторам test-harness/oracle defects: несогласованную дату seed (исправлена), слишком раннюю остановку broker replay до commit, потерянные distinct notification scenarios/privacy assertions, отсутствующие Gradle input dependencies, static-shell readiness и DOM-normalization privacy blind spot. Это не изменения продукта. R001 positive legacy upload остаётся условным ограничением: production create/multipart retired410, безопасный refusal реализован; не заявляется201/approval или эквивалентность Vue. Подтверждённые новые отрицательные product regressions остаются обычными failing tests. После corrections нужен узкий independent recheck и final CI source identity mapping.

Последние JVM checks: Academic final compile PASS +104 focused cheap tests PASS до owner-DDL delta; owner-DDL последняя compile PASS13s. Attendance compile+117 initial focused cases с двумя fixture assertion corrections и адресными rechecks; последние AO-R08/AO-R10 compile PASS11.6s. Schedule final compile PASS+48 focused pure tests PASS. Ни одна из этих компиляций не объявлена DB/broker acceptance. Historical261/104 и другие пересекающиеся counts не суммируются.

Один heavy Gradle lease передаётся последовательно: Academic initial compile/unit → Auth/Shared → Attendance → E2E config gates → Schedule. Новые Academic deep rewrites требуют отдельной компиляции; initial 261 PASS на них не распространяются. JDK21 и Gradle8.12 доступны в подготовленном runtime; per-command system CA trust/TLS сохраняются. Локальный Docker socket недоступен; реальные DB acceptance ожидают штатного изолированного CI, это NOT_RUN, а не PASS. Light focused Python/Node checks ограничены CPU/memory/output, без live providers/browser experiments. Production/VPS/SSH/deploy не выполняются. Deploy257646584 остаётся disabled_manually. B013–B015, rejected Mongo/Chromium equivalents и запрет раскрытия непроверенного diagnostics сохраняются.

До переработки: Security/Coverage/OpenAPI на baseline SUCCESS; built-image scan SKIPPED. Notification127 SOURCE_METHOD PASS после единственного адресного recovery-run. Push CI failed D002 deadline и reconciliation STRANDED; PR CI failed Gateway OpenAPI Academic TimeoutError после браузерного DOCX PASS. Их причины UNKNOWN. Исчезновение имени/теста не доказывает устранение поведения; К4 отдельно сверит эту применимость.

Следующие checkpoints: К1 — первые интегрированные проверяемые пакеты; К2 — все263 IDs обработаны или имеют конкретное evidence-backed отклонение/блокер; К3 — независимое review и достаточные проверки стабильного diff; К4 — актуальный CI и классификация оставшихся отказов. Прежний code APPROVE исторический и не распространяется на новые тестовые bytes. Merge только после завершения пакета, нового APPROVE и зелёных обязательных gates; Deploy не включать.
