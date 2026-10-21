# PR2 — закрытая диагностика после5033

**SOURCE_READY, runtime readiness BLOCKED.** Цель — установить причины оставшихся failures и исправлять подтверждённые ошибки без ослабления поведения. Source approval не означает готовность PR. Merge/main/deploy запрещены; Deploy257646584 disabled_manually. Audit94DELETE/169REWRITE исторически завершён как source implementation, новой кампании нет.

Main/merge-base3908fad9624f97aec8891fc79338ceb6b0616bf3. Предыдущий опубликованный HEAD84e28aa0006a64557046eeb4154854dcc4c77a63/sourcec843ca3fd181a10c496a9133c8540f3552ed5ee8. Новый source **b31bfc2b25a3bbc4535f56e94e34b292bff63579**. После source меняются только CURRENT/HANDOFF; final docs HEAD отдельный. Подробный предыдущий handoff с Notification/native evidence, CI9 и semantic OpenAPI review сохранён в Git на5033.

## 84e: собственная workflow-size regression и correction

Push39577619176 на84e завершён FAILURE до jobs; check_suite107252273721 содержит0 check runs. PR CI не создан. Workflow вырос с510662 до514209 UTF-8 bytes, что превышает [официальный500KB limit](https://docs.github.com/en/actions/reference/limits) (512000B). YAML parse этот предел не проверил. Exact hosted annotation недоступна; нарушение размера независимо доказано. Это новый дефект собственного diagnostic package, не Attendance assertion или environment cause.

ONLY Attendance producer вынесен в scripts/ci/attendance-openapi-candidate.py. Его10033B и SHA9b18421e7e281f3cf632b03efdee91ba83d49b2e9aa310e60281e532b73e5046 byte-identical прежнему inline program. Wrapper сохраняет umask077 и python3 в прежнем repository-root cwd; checkout включает новый tracked script. Step name/id/if/env, все другие steps/order/uploads, GITHUB_OUTPUT, relative paths, pins, child flags/timeouts/schema/primary FAIL/privacy неизменны.

Workflow502389B<=512000, запас9611B; SHA63fee721b1635ef62068eb3fda1bbd37190bbdf0aab4394daf6a02ca626f58ba. Exact inverse/program bytes+AST/YAML semantics/compile/mode0644/size и13 pure controls PASS. Первая static проверка остановилась на inherited0600; mode нормализован до0644 до единственного финального13case invocation, program bytes не менялись. Независимая scoped recheck APPROVE_SOURCE. Hosted acceptance PENDING: нужен созданный новый CI run с jobs, старый source approval не заменяет этот факт.

Last read84e09:41UTC: Security39577631989/OpenAPI39577631981 SUCCESS (built-image SKIPPED); Coverage39577632014 in_progress,7success/1failure/1in_progress, failed Attendance Run tests with coverage. Новые case counts/threshold verdict без receipts не придуманы. Это не results5033 и не результаты нового sourceb31.

## Точный delta

Совокупно после5033: три modified source paths и один created,0deleted. Текущая size correction относительно84e — только workflow+новый script:

- .github/workflows/ci.yml
- scripts/ci/attendance-openapi-candidate.py (created)
- infra/prometheus/tests/check_pipeline.py
- tests/harness/test_prepared_probe_projection.py

Product/services/frontend/API/schema/DB/proto/event/generated/dependencies/lockfiles/committed OpenAPI snapshot/scanner policy/thresholds/required gates unchanged. Новых skip/xfail/business retries/deadline increases/exclusions нет.

| Scope | Изменение | Сохранённые guards и проверка |
| --- | --- | --- |
| Official Attendance candidate |13 fixed producer stages, FAILED/NOT_APPLICABLE/GENERATED, bounded childExit; один closed safe artifact с current source/run/head/checkout binding. | Source/status/method/hash/XML/assertion/frame/wrapper/baseline/update/schema/change guards прежние; ordinary FAIL сохранён. Early exit0 остаётся0; child check=True/selector/update flag/360s/stdout suppression прежние. Original candidate upload/proof unchanged. Compile/YAML/exact inverse и13 pure controls PASS; independent APPROVE_SOURCE. |
| Monitoring projection | Один admission_reason из9 static codes различает reader rejection и допущенную неклассифицированную signature. | Category/source_stage после удаления reason IDENTICAL5033; fixed known file/method/source, regular/nofollow/nonblock/read-once/stability/freshness/byte caps/XML guards прежние.11 existing controls и malformed vectors/mutations сохранены;11 pure controls/24 consumer cases PASS; independent APPROVE_SOURCE. |

Stage writer допускается после working/HEAD source/hash/unique-anchor и finite current-context validation. Failure до binding/external kill может не дать artifact: отсутствие UNKNOWN. Exclusive write и stage=true запрещают stale upload; writer error nonzero без exception text. Safe JSON содержит только current binding/configured known selector/hash/fixed stage/state/flags/bounded exit. Никаких XML/exception text/type/stack/body/IDs/credentials/PII/signed URLs. Новые test identities/opaque mapping не добавлены.

Monitoring KNOWN_CASE_NOT_ADMITTED — один общий код для всех unsupported cases, без фактической identity/count. READ_ERROR — coarse qualification прежнего catch, не установленная причина. Terminal signature не становится authenticated first cause. Primary CalledProcessError/FAIL и consumer predicate прежние.

Root лично открыл critical originals/patches/reports и сверил reviewed hashes. Requested independent helpers Sol6.1/high/forknone, actual runtime metadata UNKNOWN. Root sole Git/docs integrator; author checks не выдаются за независимый runtime.

## Последний completed hosted checkpoint5033

Все attempt1; cancellation/rerun0:

| Gate | Run | Actual |
| --- | --- | --- |
| Push CI |39565785630|FAILURE23success/2failure: Attendance/Monitoring |
| PR CI |39565791710|FAILURE23success/2failure: Attendance/Monitoring |
| Coverage |39565791632|FAILURE8success/1failure/1skip: Attendance test step |
| Security |39565791684|SUCCESS; built-image scan SKIPPED |
| OpenAPI |39565791678|SUCCESS |

CI9 push changed-data counterexamples step11 и enum proof14 SUCCESS: прежний SOURCE_ADMISSION mismatch устранён в actual execution; ordinary assertion — другой факт.

Attendance407unit PASS,255IT PASS/2FAILED/0skip в relevant push/PR/Coverage receipts. Один SOURCE_METHOD OpenApiSnapshotIT.apiDocsMatchesSnapshot/sourceSHA cbfb5fc2fe25e11424e50ea6e0b5a7ddcf4a4a9750a20a5bcdd57a3b962533fe; другой ORDINAL_ONLY/classname+method null, не сопоставляется. Closed ASSERTION доказан для opaque Coverage record; named FAILED не устанавливает type/frame/HTTP/JSON delta. JAVA_HEAP не наблюдался; historical OOM cause не закрыта. Official candidate step26 FAILED, artifact отсутствует. Type/frame mismatch сам по себе дал бы exit0: другая producer/child phase пока UNKNOWN. Snapshot не переписывался. Coverage test-step FAIL не означает установленный низкий процент.

Monitoring delivery/cleanup PASS. PR exporters PASS; push FAIL/PREPARED_TRANSACTION_NATIVE_CHILD_EXIT/AssertionError/DURABLE_ORIGINAL_PRESENT после immediate GetEmpty. probe_returncode0 — Mongo readiness, не child success. Оба pipeline FAIL/ACTUAL_BACKGROUND_GRADLE/CalledProcessError1; native FAILED_TASK/ATTENDANCE_INTEGRATION_TEST_TASK, background UNKNOWN/UNKNOWN. Новая projection не применяется к старому исходу задним числом.

Client/browser/lifecycle/Python/Academic/Notification/Schedule jobs SUCCESS. Push remaining5 group10PASS/0FAIL/0SKIP, role/logout7PASS/0FAIL/0SKIP. Новые Notification/Schedule case counts без чтения receipts не придуманы; previous84 counts/current-method evidence сохранены в handoff5033. Старый activity failure не объявлен причинно исправленным. Исторические D002/PUT500/429/transferdeadline/heap и другие UNKNOWN не объединены общей причиной.

## Source investigation broker barrier: PARTIAL

Лично прочитаны production event_consumer, synthetic prepared_broker_probe и public cached aio-pika9.5.3/aiormq6.8.1. Awaited client close отменяет reader/writer и ждёт local callbacks/socket shutdown, не broker Connection.CloseOk/requeue completion. Queue.get timeout2 — RPC/execution timeout; Basic.GetEmpty возвращает None сразу. Immediate GetEmpty после local close не доказывает потерю обоих durable outcomes. Exact actual hosted aiormq binding/cutpoint/broker order UNKNOWN.

Но first-ready wait может пропустить extra outcome, ещё UNACKED старого соединения к следующему snapshot. Даже first+second2s windows при прежних nominal2+2 caps — finite observation без broker-settlement premise. Proposal **PARTIAL/DO_NOT_IMPLEMENT_IN_CURRENT_PACKAGE**. Strict probe/product/four boundaries/exact-one/raw-vs-prepared/payload/provider/replay commit/ACK/cleanup assertions unchanged. Это предел source reasoning, не fixed actual cause. Нового management API, retry policy или real broker experiment нет; local prepared R2 STOP не повторялся.

## Root offline inspection: только metadata

V1 на clean5033 остановлен через4.109s OWN_DESCENDANT_DETACHED: Gradle single-use daemon отделился от исходной группы. Payload/exit неизвестны, source/index unchanged; post-metadata live Java0/только zombies. Это defect внешнего guard, не Attendance cause. Raw stdout/stderr unread.

В новом private v2 guardian исправлены proven parent/start ownership и pidfd cleanup. Init после output-path inverse byte-identical. Bounds unchanged:2CPU/RSS3GiB/wall600s/private128MiB+positive cache growth/diskfloor4GiB/sharedreserve1GiB.40 pure/mock controls PASS/real signals0; independent proposal/final APPROVE_INSPECTION_ONLY. ONE root v2 run COMPLETE16.362s/exit0/guardnull/reapedtrue/source+index unchanged. Peak RSS582959104B/private+cache1515669B.254 external artifacts, Testcontainers1.20.4/coreSHA bb5701004c1c867350f56297b0b34343e3be31fdc7e86fd4683b7d8e3e1f8178/public Ryuk0.11.0;62 task metadata nodes. Safe receipt SHA e9b78983f0065d54357600800230971e6819c8bc7467c8b43006332b48c753ac.

Compile/Test/app/DB/Docker/pull0. Это root offline resolution, не actual hosted classpath/native conformance или resource-fit proof. Missing source tags mongo7.0/redis7.2 — CACHE_MISSING, не доказанный NO_FIT. Native/images NOT_RUN_READY; Notification-selected1.21.4/Ryuk0.12.0 не переносится. Дальнейшая необходимость определяется новой closed ordinary CI projection, новый native/pull пакет не готовится.

## Custody и продолжение

Cloud running/connected/exec подтверждены; около17GiB RAM/11GiB disk available, swap0, cleanup0. Hosted heap failure не доказывает cloud OOM. Original23 ZIP617/9requested4b3/13original3aa/10original84/7original5033 сохранены без перепаковки; API/run/attempt/head/size/SHA/CRC/path checks выполнены. Current5033 IDs:12329713632,12330820133,12330206539,12330347658,12330766003,12330183166,12330122934. PR/Coverage carrier2a25e5cc157eb32f82a9f0b5da0ec8d9a06518fa: parents main+5033, treea1ce8c045b9567fc783a04405411ed39126139c6 точно tree5033; carrier не переименовывается branch SHA.

Exact source manifests/reviews/safe receipts/v1+v2 helpers — в собственной /workspace/work/pr2-failure-corrections-20261021. Ignored evidence не подменяет published source/hosted outcomes. WIP/worktrees/foreign paths/data/ZIPs сохранены. F01–F09/N1/N2/session/live admission/late generation/uncertain keys/authz/CAS/history/outbox/ACK/DLQ/privacy unchanged. B013–B015/rejected Mongo/Chromium equivalents/local prepared R2 STOP не обходились. No raw private reports/logs/real credentials/PII/signed URLs; no production/VPS/SSH/provider/Telegram/migration/secrets/firewall/backup.

Далее: taskdocs-only equivalence, unchanged-policy full-history scanner, expected refs/Deploy readback и один normal push; читать новые ordinary attempt1 statuses/safe artifacts без blind rerun/cancel. Старый PASS/FAIL не приписывать новым bytes. До доказанных фактов не менять product/snapshot/probe. Merge/deploy запрещены.
