# PR2 — уточнение закрытой диагностики на базе a6ff

**SOURCE_READY; runtime readiness BLOCKED.** Оставшиеся failures не объявляются исправленными по source approval. Merge/main/deploy запрещены; Deploy257646584 disabled_manually. Предыдущая история и packets сохранены в Git на a6ff и5033; audit94/169 не возобновляется.

Main/merge-base3908fad9624f97aec8891fc79338ceb6b0616bf3. Предыдущий опубликованный HEADa6ffb068f8ae5a53c600860528757285b26472bf/sourceb31bfc2b25a3bbc4535f56e94e34b292bff63579. Новый executable source **9e10a6c042e78d2cade243012702cf7fb2d3e683**. После source только CURRENT/HANDOFF; final documentation HEAD отдельный.

## Что установлено на a6ff

Собственный workflow-size дефект84e закрыт: вместо0jobs появились нормальные push/PR jobs. CI workflow502389B<=512000, запас9611B. Новый пакет его не меняет. Исходная YAML parse проверка сама по себе не защищала platform limit; historical failure не переименован в Attendance cause.

Last metadata read2026-10-21 10:21:47UTC, все attempt1:

| Gate | Run | Actual |
| --- | --- | --- |
| Push CI |39580943358|IN_PROGRESS:19success,2failure,3in_progress; Attendance/Monitoring failed |
| PR CI |39580956789|IN_PROGRESS:19success,2failure,3in_progress; Attendance/Monitoring failed |
| Coverage |39580956793|IN_PROGRESS:7success,1failure,1in_progress; Attendance test step failed |
| Security |39580957032|SUCCESS; built-image scan SKIPPED |
| OpenAPI |39580956777|SUCCESS |

Notification/Schedule/Python jobs SUCCESS. Academic и browser groups ещё выполнялись в данном read. Старый5033 PASS не выдаётся за новый terminal status, aggregate не считается независимой причиной. Coverage fail на выполнении тестов не устанавливает низкий процент покрытия.

Attendance current originals:407unit PASS;255IT PASS/2FAILED/0skip. Named OpenApiSnapshotIT.apiDocsMatchesSnapshot sourceSHA cbfb5fc2fe25e11424e50ea6e0b5a7ddcf4a4a9750a20a5bcdd57a3b962533fe и один ORDINAL_ONLY, classname/method null. Opaque identity не восстанавливалась.

Push artifact12337386415 (518B, SHA6172814130bbe2db827990f700604c07d4b56774d98f29a5bbae1730a68d7db7) и PR12337911563 (546B, SHA34f5023fc2d1adc323903f334b5b6a1f56bd16cd1a41df54739f4edd12803306) подтверждают FAILED/GENERATED_CHANGE_GUARDS/childExit0. Все предшествующие source/status/method/XML/assertion/exact-drift-frame/wrapper/baseline guards и generated schema пройдены; original ordinary FAIL сохранён. Это не доказательство выбранного child execution, готового candidate или конкретного failed guard. Source RCA не подтвердила deterministic umask/mode/routing/generated-path culprit; snapshot в PR не менялся.

Monitoring push artifact12337970581 exporters4phase PASS; PR12338835316 exporters FAIL/PREPARED_TRANSACTION_NATIVE_CHILD_EXIT/AssertionError, native DURABLE_ORIGINAL_PRESENT, cleanup PASS. probe_returncode0 относится к Mongo readiness, не child success. Оба pipeline FAIL/ACTUAL_BACKGROUND_GRADLE/CalledProcessError1/FAILED_TASK/ATTENDANCE_INTEGRATION_TEST_TASK. Background UNKNOWN/UNKNOWN/KNOWN_CASE_NOT_ADMITTED локализует reader rejection до suite/case gate, но не actual condition или Java first cause.

## Точная новая delta и сохранённые guarantees

Три modified source/control paths, без новых/удалённых файлов:

- scripts/ci/attendance-openapi-candidate.py: только6 fixed stage assignments перед6 прежними assertions/verify_wrapper. Legacy entry сохранён для начального git-diff exception. Ни выражения guards, ни порядок, child args/check/360s, source/frame pins, binding, official publication, exit/state/proof/privacy не изменены.
- infra/prometheus/tests/check_pipeline.py: два прежних OR gates разделены в прежнем short-circuit порядке на6 static rejection reasons. Whole-pipeline AST inverse восстанавливает прежние predicates/dispositions/category/source_stage и все остальные statements. Фактические keys/names/counts/values/types/frames/paths/messages/ordinals не выводятся.
- tests/harness/test_prepared_probe_projection.py: все11 controls сохранены;10 методов AST identical, прежние malformed vectors и mutation prefix сохранены, добавлены11 synthetic shape/priority variants. Result/source_sha consumer и primary FAIL прежние.

Workflows, services/frontends, API/schema/DB/proto/events/generated/dependencies/lockfiles/committed OpenAPI snapshot, exporter/probe, scanner policy/coverage thresholds/required gates byte-identical a6ff. Нет skip/xfail, новых business retries, warming, deadline increases или exclusions.

Root лично прочитал changed originals/callers/diff/author proof и независимый combined report. Independent Sol6.1/high review **APPROVE_SOURCE**, report SHA4861f0997d0f601941eb0b6aa3a945f7155abce5246c921735c74ff47d268f76. Actual runtime model/effort UNKNOWN. Source approval не заменяет hosted execution нового9e10.

Проверки: producer compile/exact byte+AST inverse/placement PASS; прежние13 pure controls валидны и переиспользованы. Reviewer нашёл P3 во внешнем v1 adapter:6 новых cases вызывали NameError, а не AssertionError. V1 inputs/results сохранены, утверждение квалифицировано. Новый external v2 adapter передаёт literal sentinel и явно проверяет exact AssertionError; один bounded run только6 corrected cases PASS, source bytes неизменны. V2 receipt SHA1b97c1dce99b52ca313f880095835c575dd5e87e28d3e9d7b3d7babaa4066a9f. Reviewer статически перепроверил v2 и закрыл finding. Это synthetic projection controls, не actual guard/business execution.

Monitoring11 pure controls PASS одним final-byte run0.203s;24 consumer cases PASS; whole-pipeline inverse и retained-oracle checks PASS. Root повторно не гонял unchanged suites. Эти проверки не устанавливают underlying Java cause.

## Остаток и ограничения

Broker source investigation сохраняет прежний предел: cached aio-pika9.5.3/aiormq6.8.1 awaited close не ждёт broker CloseOk/requeue completion; immediate GetEmpty не доказывает durable loss. Exact hosted binding/order/cutpoint UNKNOWN. First-ready polling может скрыть extra outcome, ещё UNACKED; strict probe и product/four boundaries/replay/ACK/provider/exact-one assertions неизменны. Speculative correction PARTIAL/DO_NOT_IMPLEMENT; local prepared R2 не повторялся.

Root offline inspection5033 остаётся только metadata: v1 guardstop, v2 COMPLETE16.362s, Testcontainers1.20.4/public Ryuk0.11.0/254artifacts/62task nodes, compile/Test/app/DB/Docker/pull0. Current a6ff native feasibility:579source inputs5.04MB byte-identical5033, original test всегда требует Mongo/Rabbit/Redis. Image/storage availability, private compile peak и own-container isolation не доказаны. NOT_RUN_READY, а не установленный NO_FIT; новый native/pull пакет не готовился. Прежние128MiB private+positivecachegrowth/1GiB sharedreserve/RSS3GiB/2CPU/diskfloor4GiB не снижались.

Cloud exec работает; около17GiB available RAM/11GiB disk, swap0, cleanup0. Hosted OOM не переносится на cloud host. Все23original ZIP617 и последующие originals/evidence/v1/v2 helpers/worktrees/WIP/foreign data сохранены без перепаковки. Новый a6ff пакет7ZIP получен напрямую штатным artifact tool, API run/attempt/head/size/digest/CRC/paths проверены. Current IDs:12336562028,12337970581,12337386415,12336403478,12337911563,12338835316,12338575251.

PR/Coverage carrieredc639c5925200bb769c9a37a540f33601e8cfd0: parents main+a6ff, treed4ce1ebf91806380f8d9777781fffd31ece1c3bd точно treea6ff. Monitoring source_sha на PR — carrier, не branch head; equivalence доказана, carrier не переименован. Raw private XML/HTML/logs, credentials/PII и signed URLs не читались/публиковались.

F01–F09/N1/N2/session/live admission/late generation/uncertain command keys/authz/CAS/history/outbox/ACK/DLQ/privacy сохранены. Исторические heap/D002/SETUP500/activity/429/transfer deadline и другие UNKNOWN не объединены общей причиной. B013–B015, opaque mapping, rejected Mongo/Chromium equivalents/local prepared R2 STOP сохраняются. Production/VPS/SSH/provider/Telegram/migrations/secrets/firewall/backup исключены.

Далее — unchanged-policy full-history scanner, expected refs/Deploy и отсутствие cancel concurrency в неизменённых workflows; один normal push новой revision, без отмен/слепых reruns. Читать current safe artifacts и исправлять лишь доказанные причины. Ни current red gate, ни новый diagnostic source approval не дают готовности к merge; merge/deploy запрещены.
