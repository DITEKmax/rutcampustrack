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

К0: DELETE 0/94 и REWRITE 0/169 принятых после проверки; все 263 IDs назначены и IN_PROGRESS. SOURCE_READY не равен VERIFIED. Матрица отдельно хранит counterpart, verification, review и deviation/blocker. Ошибочный старый совет корректируется по текущим источникам с независимой проверкой; QUESTION_ONLY и защищённые controls сохраняются.

Один heavy Gradle/Docker lease: Academic compile/unit; остальные heavy ждут. JDK21 и Gradle8.12 доступны в подготовленном runtime; per-command system CA trust/TLS сохраняются. Light focused Python/Node checks ограничены CPU/memory/output, без live providers/browser experiments. Production/VPS/SSH/deploy не выполняются. Deploy257646584 остаётся disabled_manually. B013–B015, rejected Mongo/Chromium equivalents и запрет раскрытия непроверенного diagnostics сохраняются.

До переработки: Security/Coverage/OpenAPI на baseline SUCCESS; built-image scan SKIPPED. Notification127 SOURCE_METHOD PASS после единственного адресного recovery-run. Push CI failed D002 deadline и reconciliation STRANDED; PR CI failed Gateway OpenAPI Academic TimeoutError после браузерного DOCX PASS. Их причины UNKNOWN. Исчезновение имени/теста не доказывает устранение поведения; К4 отдельно сверит эту применимость.

Следующие checkpoints: К1 — первые интегрированные проверяемые пакеты; К2 — все263 IDs обработаны или имеют конкретное evidence-backed отклонение/блокер; К3 — независимое review и достаточные проверки стабильного diff; К4 — актуальный CI и классификация оставшихся отказов. Прежний code APPROVE исторический и не распространяется на новые тестовые bytes. Merge только после завершения пакета, нового APPROVE и зелёных обязательных gates; Deploy не включать.
