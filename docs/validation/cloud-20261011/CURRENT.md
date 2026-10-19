# CURRENT — отдельные Auth/Academic БД, отчёты и приложения

Goal Q031/D037 **IN_PROGRESS**, без дедлайна: [GO](TASK-ADDENDUM-SPLIT-APPS-20261018.md),
[TASK18](TASK-CONTRACT-20261018.md), [TASK17](TASK-CONTRACT-20261017.md), [ASYNC](TASK-ADDENDUM-ASYNC-20261017.md).
Ветка codex/server-validation-20261011; [draft PR2](https://github.com/DITEKmax/rutcampustrack/pull/2).
Starting26cbe5912f6683a81780a831609dbddbc26feba4; опубликованный HEAD/remote/PR:
**8c5b752d88ea81049ff30920cc451c6f271f1947**. WIP: taskdocs и адресные corrections ниже; без main/merge/deploy.

## Конечный план

[Матрица v3 FROZEN](selected-results-20261018-v3.csv)/[веса](selected-results-20261018-v3.meta.json):
**31/39, вес154/196=78.57% плана**, 29 DONE_CHANGED +2 ALREADY_SATISFIED; это не production readiness.
Критерии/веса/знаменатель сохранены. [v1](selected-results-20261018.csv)23/25,110/123,
[v2](selected-results-20261018-v2.csv)24/38,114/188 и прежние TASK17 9/9 сохранены в своей применимости.

| КТ | Закрыто / всего | Вес / всего | % плана |
| --- | --- | --- | --- |
| КТ2: границы/AAA/контракты/сохранность | 12/14 | 58/71 | 81.69 |
| КТ3: бот/приложения | 6/9 | 26/37 | 70.27 |
| КТ4: изменённые цепочки/мониторинг/задания | 13/15 | 70/83 | 84.34 |
| КТ5: итоговые gates/review/handoff | 0/1 | 0/5 | 0 |

| Открытый ID | Конкретный остаток / следующий шаг |
| --- | --- |
| U14.4 | Два18weekDOCX/lost202/reconnect/currentrights и short3week уже PASS. FULL guard теперь realIT/browserPASS. Остались LIMITED capability stale-ticket и renderer-outage: исправлены два403 contract expectations и owned job-table probe; actual rerun нужен. |
| U14.5 | Samearchive/DLQ/replay PASS сохранён; история/переход семестра теперь browser PASS на8c5. Subject BEFORE controlMarks1 FAIL5805: причинаUNKNOWN, добавлены только finite failure branches. PR samearchive actual replay/dedup/restoration PASS12156719443/8966a06b; controlled GET readiness retry сохраняет прежние10s/все business guards. |
| U14.6 | OpenAPI8c5 SUCCESS. ADMIN21 fixture исправляет доказанный возврат permissions→edit→detail→list; original search/teacher binding/current-rights assertions сохранены. Radio430/групповой ticket922 UNKNOWN; тотже radio oracle перенесён из helper к3 callers. Schedule historical seed исправлен к own Academic eligibility; accepted UI rerun нужен. |
| U14.7 | Q033 CLI: synthetic config owner/umask исправлен nonroot UID:GID/0700/0600/readonly. MISSING_CONFIG вместоCONFIG далEXECUTE/DB_CONNECT: passwordfallback пустой. Auth main guard исправлен/independentAPPROVE/Native39PASS; FRESH/retry/login ещё NOT_RUN. |
| U20.1 | Hosted childFAIL на current-headman roster/statistics; найден test casing mismatch lowercase/UPPERCASE,2 semantic comparisons исправлены. Academic Pythonbridge такжеFAIL; exactcause ещё UNKNOWN. |
| U20.2 | Настоящие current-lesson roster/statistics/none/revoke через hosted bot; неотмеченный не считается отсутствующим. Зависит U20.1. |
| U20.3 | Teacher Today/empty/current rights/mixed roles через hosted bot; teacher-only/admin-only без student HW. Зависит U20.1. |
| U25 | Финальные совместимые CI/Coverage/review. Restore relation ACL: target default grants и exact owner-only/NULL representation, strict compare сохранён. Existing counterexample бьёт невызываемый helper: узкий harness fix/review. |

## Текущий пакет

8c5 pushCI39180701603/PRCI39180706957/Coverage39180706924 завершились FAIL;
Security39180706976/OpenAPI39180707029/Monitoring/Python SUCCESS.
Auth207PASS/0FAIL/0SKIP, включая33 store cases. AcademicPR520PASS/1FAIL/1SKIP:
HEADMAN CAS/current grants/history и FULL/LIMITED/revoke report tests PASS; остался Pythonbridge, CLI SKIPPED.
**U14.3 DONE_CHANGED**:12156207894/23e5a9de +12156407708/9a213c05, применимый reviewed source8c5/merge95661.
Apps push/PR SUCCESS с прежним64MiB gate; PR merge95661a6e и8c5 имеют одинаковый tree04d976a6.
PR Apps artifact12156441745/f8036344: две18week render операции, RSSdelta51421184B,
CPUratio1.0387; error grouping/privacy/restart/private links/telemetry-outage PASS. Не VPS capacity.
Semester browser12156248039/5769292f PASS13.957s; subject12156083988/e9c7301e FAIL5805.
Registry8c5 browser12156549221/eaf58cd0:4PASS/2FAIL; FULL HTML/PDF/PNG PASS, LIMITED stale-ticket174/outage helper277 ещё FAIL.
Historical2aa CI/Coverage/AppsPRRSS FAIL с UNKNOWN exclusive cause сохранены; это не текущий Appsblocker.
8c5 дваDOCX PASS15.037s12156174098/fcab08ca; весьpipeline/cleanupPASS3a8aa2da:
четыре metric deltas2,8targetsUP/426panelqueries и6HTTP/OpenAPI documents приняты.

Native39 PASS52.90s: exact38 compile-only3targets, Authmain EXECUTED/tests UP_TO_DATE; RSS691089408B/output87365140B.
Exact AuthCommand127109e5 +прочие inputs стабильны; canonical check39 SHA460eb4f5. Lease/freeze RELEASED/liveJava0.
Готовится один checkpoint9source: reviewed recovery ACL correction/Auth missing-config guard/ordinary counterexample3fields; ADMIN21 navigation/radio localization,
schedule own eligibility, subject count diagnostic/bounded GET readiness, bot casing2expr, registry capability403/table owner, exact AssertJ diagnostic space. Source-ready, не runtimePASS.
Precommit source4/2/3/final2 PASS1.58s/1.42s/1.73s/1.42s без drift; bot Ruff0.17 actualCIcwd PASS0.05s. Auth guard также precommitPASS; formatter/review завершены, mandatory runtime остаётся открытым.
Normalpush сохраняет текущие8c5 runs: в CI/Coverage нет cancel-in-progress; no cancel/rerun.
[Owners/resources](resources.md), [protocol](BOUNDARY-PROTOCOL-20261018.md), [decisions](decisions.md),
[questions](questions.md); история один раз в [attempts](attempts.ndjson), exact WIP в ignored current-wip/latest-checkpoint.safe.json.
Local Docker/DB/pulls NOT_RUN: workspace ниже4GiB Dockerreserve/VFSsameFS. Без cleanup/reset/prune; foreign25 сохранены.
Вопросов владельцу нет. **B013/B014/B015 FROZEN**. DEFERRED_OWNER/LOCAL: реальные Telegram/push/install/Safari,
alertbot/offhost probe/owner stand/deploy/backup/VPS/multiworker; это не PASS.
