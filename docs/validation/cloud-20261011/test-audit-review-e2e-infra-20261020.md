# Independent E2E / EF / INFRA review

Final source decision after all narrowed rechecks, including the HTTP500 fixture/producer correction: **APPROVE** for the frozen 45-path manifest `f511757bdbcab2ecc52e550e238fe5353dc16bf2d2b97c06f268934420c3c74d`. All three source-confirmed P2 findings are resolved and the final CI delta is approved; runtime acceptance is not implied. This covers the original 41-path review plus the focused correction scope, not the entire PR.

Reviewer task: `/root/review_e2e_infra`; requested Sol6.1/high; actual runtime model/effort metadata UNKNOWN. No children. No product/test/config edits, test execution, builds, browser, DB, CI, Docker/native runtime, deployment, network mutation, or other-chat communication. This report is the only written artifact.

Baseline: `2eca01a3ef4c65cc2ed1f1ccc6bb7c50eb211615`. Reviewed worktree: `/workspace/work/test-audit-implementation/e2e_infra`. RULES and CURRENT.md read; current owner implementation-audit assignment takes precedence over historical STOP/proposal-only material.

Manifest `/workspace/work/test-audit-implementation/e2e_infra-manifest.json` SHA256 before and after: `d95cc9d0de22c36b78041005bcc73818a972b654bd89b369fce21d7b4d9fa7a9`. All 41 paths match their declared bytes; the declared deletion is absent. `.github/workflows/ci.yml` is explicitly pending a narrow final source-pin delta. The present review does not approve that future delta.

## Final CI identity/routing recheck — approved

Frozen `.github/workflows/ci.yml` SHA256: `a116f3beedfa00fcb35dd92548bb64d49b7a44ccfe85187760511b2538697085`. Final 43-path manifest SHA256 before/after: `99bfd6f2a6eb62eb0435376a4748605c5d5c17aecba65166de4b828c7d66a29f`; every declared file matches. All 42 non-CI path hashes are unchanged from the approved core manifest. Integrated root HEAD observed during source verification: `e38e72a29854a18cf1ee3e84926f9a57d698b630`.

The pre-routing CI snapshot independently hashes to the approved `43b4bed0b3d0685566bb3e54c349d594e648ff2cc8085558d15d73f93f8bddcf`. I read the complete diff and independently reconstructed exact final CI bytes from that snapshot using only verified source identities, the two updated in-memory counterexample hashes, current assertion/method/frame coordinates and the scoped Auth expected-JSON assertion. The Auth target replacement occurs once inside the Auth candidate script; identical target strings in other services remain untouched. Exact reconstruction excludes any other trigger, job, step, mutation, admission, status, schema, privacy, count, timeout, resource, or negative-expectation change in this final delta.

Personally verified against current integrated source:

- All eight changed Java source SHA256 values and all 21 corresponding CI occurrences match actual files. Exact methods and current asserted lines match: Homework audit1334; Auth purpose-proof41/67/helper406 and LOGIN-proof denial549; student projection450/588; initial-membership685/776–778; captured-teacher502/547; blocked-lesson manual-request denial3862.
- Both changed test counterexamples recompute from the original unchanged one-occurrence archive/identity anchors: retained-completion timestamp digest `cc70eaabe6fc07262b9a9464c75c0c53deb1bcf4da84546baef464ac187cc74d` and existing-account identity digest `4c165afc0076d36800bf697b5f4bd5a4902c84cadf5d1e998d5e51a857bac2e9`. Their derived rejection lines are415 and382. No target source was mutated.
- Existing progress/audit/admin/student/backfill/teacher production anchors each occur exactly once in actual current source. All four boundary dictionaries retain current test/production SHA bindings, exact methods and rejection source lines. Their negative hashes recompute correctly, including the intended composed Attendance budget-plus-blocked guard mutation and Auth digest-plus-challenge-code guard mutation. Original replacement order and negative expectations are unchanged.
- Auth OpenAPI candidate now binds to the actual `.isEqualTo(semantic(mapper.readTree(expected)));` assertion of `apiDocsMatchesSnapshot`; both sides remain semantic JSON, matching the current test contract. Exact failure method/frame/status and official generator admission remain unchanged.
- Three passive E2E source-hash literals match the actual two current files. Old Git baseline source hashes are the previously pinned `47a94b8a...` and `c20ce79f...`. Independently compared actual text: hosted-current-authority callback and complete D002 reconnect callback are byte-identical to baseline; semester callback from its exact title through the full ordinary MARK call is byte-identical. Current titles are hosted5943, D0023958 and semester1585; actual operation sites are hosted6064 and semester1811. All six changed title/frame occurrences bind those same operations. The later semester shared UI observer extraction does not affect the ordinary MARK producer boundary.

The passive schemas, prerequisites, private source gates, UNKNOWN qualification, exact count/type/bounds, and failure-only receiver admission remain unchanged by exact-delta comparison. Inventory/source-AST receipts were used as pointers; decisions above follow direct source reads and independent static byte/hash/line calculations. No CI script or target callback was executed, no product source edited, no native process/database/browser/deployment invoked, and no B013–15/rejected Mongo/Chromium experiment replayed. The previously reviewed pure recovery source/dictionary/AST control remains unchanged. Actual deployment stays disabled/not run.

No blocking finding remains for this final CI source delta. The approval is source-only and preserves E2E-R001 BLOCKED_CONDITIONAL_SCOPE for unsupported positive legacy upload/approval; source-ready retired410 refusal is still the qualified implemented boundary.

## Narrow correction recheck — final source approval

Current 43-path manifest SHA256 before/after recheck: `29e0f0a029695826db5c80d0548fda7772f3400861eeaff2a8904bfc1389ed29`. All declared hashes match, declared deletion remains absent. Of the original 41 paths, 37 retain their previously reviewed hashes; the other four are shared-logback build wiring, route helper, HTML helper and CI. Those four and the two newly added controls were re-read with the actual affected product readers/templates/API callers. No duplicate full audit was performed.

| Correction | Recheck |
| --- | --- |
| F1 / INFRA-R03,R04 | RESOLVED. Each actual Gradle Test task declares protected config inputs: recursive default/prod YAML matching the existing Java reader, exact seven service XMLs, and shared base/masking source. Relative path sensitivity preserves file additions/removals/content invalidation. Existing hardened parsers, logger/masking behavior and mandatory root dependencies remain. |
| F2 / E2E-R002 | RESOLVED. Same-origin GET observers attach before navigation; all route-specific companion reads require exact200, finished body and decoded object. Dashboard stats/groups and both teacher four-read forkJoins are covered; correct group late-checkin endpoint is observed. Loaded cards/rows/counters/options or real supported empty states are checked against completed payloads, with actual skeletons/errors excluded. Canonical paths, current-account callers, separate forbidden-route checks, cleanup, axe and original deadlines remain. |
| F3 / E2E-R005,EF-R03 | RESOLVED. An independent whole-output raw/entity-decoded privacy pass precedes the inert DOM table parser, so parser-discarded duplicate attributes cannot remove private values from scrutiny. Exact table/column/row semantics and DOM-value privacy are retained. Entity-encoded duplicate-title regression is included in both raw privacy and native-parser control vectors. |

New controls import and exercise actual shared helpers through the existing TS transform; they do not copy product rendering or replace browser acceptance. Route controls use public event/response and assertion adapters to check complete companions plus rendered counter sensitivity. Privacy controls call the actual whole-output helper; the browser-free entity fallback fails closed for unsupported named entities and is not represented as native DOM proof. CI changes during correction add only these two Node control calls at the existing control step.

Evidence read, not executed by this reviewer: `config-inputs-pass1.log`, `config-inputs-pass2.log` and `config-inputs-final.log` all record successful builds and both configuration Test gates **EXECUTED**. The final run records `AUDIT_CONFIG_INPUT_MEMBERSHIP yaml=true xml=true shared=true`; compilation/resource dependencies were UP-TO-DATE. Own/tmp input-only mutation plus read-only actual config membership is composed proof; it is not represented as a mutation/rejection run against production config. `web-panel-route.log` records 4positive/13negative PASS, and `registry-raw-privacy.log` records 1positive/6negative PASS. Existing prepared-JDK parser/logger gate results remain applicable because Java test/product bytes are unchanged.

Reviewed correction hashes:

- shared-logback build: `c61998f894025cc45afa4206d2e7e54b389d8565059cf8ac8d58e941acabf25a`
- route helper: `a10ba3fc9b5895134cabf5f4ceeb27305630b19354f1aaf63d6ffeea40d60913`
- route control: `d6293f9b1de0903fef00024cc88e9ac96af0fbb03a129fab6702af116be371da`
- HTML helper: `e0eae6cdf5ff89eb09e3fa63c2b2657c17df27ad311aa7dd7b68525a2534d40b`
- privacy control: `11c13160af70c81efd1f7f24581d4d1395877ced24be5416818c6b41d4075e3a`
- CI current calls/wiring only: `43b4bed0b3d0685566bb3e54c349d594e648ff2cc8085558d15d73f93f8bddcf`; future final source pins remain pending.

No new blocking finding. No runtime/test/build/browser/DB/native/deploy action was performed by this reviewer. Local Chromium remains forbidden; actual Angular/native DOM/nginx/Alertmanager/runtime behavior remains unexecuted. Deploy workflow bytes are unchanged from the first review and actual deployment remains disabled/not run. E2E-R001 positive legacy condition remains BLOCKED_CONDITIONAL_SCOPE; only source-ready retired410 safe refusal is approved.

## Original findings — all resolved by the narrow recheck above

1. **P2 — service configuration changes do not invalidate the new mandatory Gradle gates.** `services/shared/shared-logback/build.gradle.kts:33-39` registers Test tasks whose tracked inputs are their shared-logback test classes/runtime classpath, but the service YAML/XML files actually read by the tests are outside that classpath and are not declared as task inputs. The former root tasks' `inputs.files` declarations were removed at `build.gradle.kts:219-220` and `:232-233`. Trigger: run a gate successfully, change only a service application.yml/application-prod.yml to quoted DEBUG or change a service logback-spring.xml to unmasked output, then invoke the same gate. Gradle may consider its dependent Test task UP-TO-DATE, or reuse a cached result, without evaluating the changed service configuration. Impact: mandatory logging/privacy gates can give a false green on the exact files they protect. Minimum correction: declare the actual default/prod YAML collection and seven exact service XML files as inputs of the respective Test tasks, retaining their current scope and fail-closed parsers. Forced execution is also sufficient if chosen intentionally. No runtime reproduction was performed.

2. **P2 — allowed-route helper accepts a static shell before feature data has settled.** `tests/e2e/fixtures/web-panel-route.ts:5,17,33-34`. The admin dashboard readiness selector `.dashboard__actions` is unconditional static markup (`frontends/web-panel/src/app/features/admin/dashboard/admin-dashboard.component.html:102`). Its real loading elements are `.stat-card__skeleton` (`.../stat-card/stat-card.component.ts:46-48`), absent from the helper's loading denylist. Trigger: the stats HTTP response completes with 200, but the dashboard callback leaves loading true and never renders the data. Decoded response, mounted owner, static quick actions, no listed spinner, and no error all still pass. Teacher stats likewise exposes the `Выберите группу` empty state while its initial forkJoin has not completed (`.../teacher/stats/stats-page.component.ts:88-110`, `.html:47-59`); completion of assignments alone does not settle the other required loads. Impact: E2E-R002 can still report successful feature navigation while only the shell/pre-load empty state is present. Minimum correction: use route-specific public controls/rendered data or a supported empty state tied to completion of the actual relevant loads; avoid treating unconditional presentation containers as settlement. Preserve canonical paths, owned screen, current account, no target data on denial, axe, and original deadlines. No browser reproduction was performed.

3. **P2 — normalized DOM privacy scan misses secret-bearing tokens discarded by HTML parsing.** `tests/e2e/fixtures/registry-html-boundary.ts:48-62`. The previous whole-content decoded privacy check was replaced with values collected only from DOMParser's resulting tree. HTML parsing discards duplicate attributes. Trigger: place a forbidden value in a second duplicate safe attribute, for example change the positive control's table opening to `<table class="safe" title="public" title="synthetic&#45;secret">`. The normalized DOM retains only the first title; table shape/columns/rows remain exact and `forbiddenAbsent` sees no forbidden value, although the downloaded bytes carry it. This applies to real expected credential, LIMITED Telegram-ID, or nonmatching-record values too. Impact: the shared user/group HTML oracle loses the required sensitive-value absence guarantee for all output bytes. Minimum correction: preserve an independent raw/decoded-content privacy check that covers discarded parser tokens, or reject those malformed/duplicate constructs before accepting semantic output; add a negative vector for an entity-encoded forbidden value in a duplicate attribute. Keep native inert DOM parsing for table semantics and RAM-only closed-boolean results. No actual leak or browser experiment is claimed.

## All assigned audit IDs

| ID | Review result / retained boundary |
| --- | --- |
| E2E-D001 | No finding. Only the named permanently skipped unsupported bulk/WebSocket file is deleted; distinct individual-mark/history guarantees are not presented as equivalents. |
| E2E-D002 | No finding. Exactly four cosmetic assertions removed; theme selection/persistence/media/global boundary, password change and old-authority denial remain. |
| E2E-D003 | No finding. Passive HTML tag assertion only; exact ticket/pending/autoAbsent, accessible readonly state and action denial survive. |
| E2E-D004 | No finding. Duplicate object serialization equality only; structural lesson/ordered-roster equality and bearer/refresh revocation survive. |
| E2E-D005 | No finding. Only duplicated headman-to-student fallback case removed; same account/projects/student route survivor remains and is strengthened. |
| EF-D02 | No finding. Internal count/icon/action ordering assertion removed; controlled visibility/removal handshake and all observable postconditions/negatives remain. |
| E2E-R001 | Conditional scope remains blocked. Safe 410 refusal with actual Angular 10MiB multipart selection/submission, safe error and no-created-ticket readback is source-ready. Current ExcuseController unconditionally retires creation; no legacy positive201/approval/forwarding or Vue equivalence is claimed. |
| E2E-R002 | APPROVED SOURCE after F2 recheck (original finding 2 resolved). Canonical singular route, exact owner, HTTP200/body, denial of both forbidden destinations and original axe are present; payload-bound loaded readiness now replaces the static shell. |
| E2E-R003 | No finding. Holds genuine owned upstream upload response, asserts busy/disabled guard against keyboard/requestSubmit and exactonePOST, releases/unroutes in finally; successful request/attachment/cancel readbacks remain. Atomic MS-R02 counterpart also exercises real route-driven unmount/remount, clipping, root-font/viewport changes and keyboard editing/restoration. |
| E2E-R004 | No finding. Real Material/generated-login/Telegram-ID setup; exact201/owned identity; exact private credential equality in owned table row after reload and before independent same-user login. Actual UserAssembler/table carrier supports this despite immediate dialog close. |
| E2E-R005 | APPROVED SOURCE after F3 recheck (original finding 3 resolved). Shared semantic parser preserves ordered distinct group projection/filter expectations, but independent whole-output privacy now covers discarded parser tokens. |
| EF-R01 | No finding. Live caller/control use same ordinary exported TS observer/oracle and existing transform; public fake request/response replaces vendor internals; period/bucket/5metrics/fraction/delta/state/button and missing-replacement deadline controls retained. |
| EF-R02 | No finding. Live and control share ordinary terminal/UI exports; exact operation/action/semester, three receipts, inactive/unscheduled authority, replay identity and all terminal/UI negative controls remain. |
| EF-R03 | APPROVED SOURCE after F3 recheck (original finding 3 resolved). User FULL/LIMITED and group callers share native inert parser with their distinct projections; whole-output privacy now supplements normalized-DOM checks. |
| INFRA-R01 | No finding. Exact health/login/current token+cookie+role, owned lesson/date/HAL and logout204/old-session401; private bounded input/output and HTTP timeouts. |
| INFRA-R02 | No finding. Current PEM JSON, exact authorized group200/id/name, noJWT401 and separately admitted actor403; transport/body/status failures stay failed. |
| INFRA-R03 | APPROVED SOURCE after F1 recheck (original finding 1 resolved). Actual Spring YAML parser and quoted/dotted/nested/comment/dev exemption controls exist, and actual gate inputs now track protected files. |
| INFRA-R04 | APPROVED SOURCE after F1 recheck (original finding 1 resolved). Hardened XML and effective Logback loading of all seven service configs with real JSON/masked message exist, and actual gate inputs now track protected files. |
| INFRA-R05 | No finding. Missing required dashboard/Docker/rules/container/gateway/TTL prerequisites produce INCOMPLETE/nonzero; original failures remain primary and required checks remain. |
| INFRA-R06 | No finding. Deploy diff is confined to the smoke block plus helper; expected40hex image tag, real served index/ownedmainJS bytes, exact status/MIME and finite bounds replace size heuristic. Triggers/permissions/SSH/release commands unchanged. Actual Deploy remains disabled_manually/not run. |
| INFRA-R07 | No finding in changed scope. Independent literal maps cover category/code vectors; production collector unchanged, existing privacy/cap/overflow/source/scope/status/admission controls retained. |
| INFRA-R08 | No finding. Existing authenticated receiver must receive exact different-service firing labels; exact same-service inhibition and prior auth/firing/resolved/dedup remain. |
| INFRA-R09 | No finding. Identity-set/multiplicity and all-target admission before any PG/Mongo query replace incidental order; rejecting target, foreign-label no queries, no writes and immutable bundle hashes remain. |

Additional minimal TB-R08 root wiring: no source finding in the reviewed consumer/CI allocation. Existing owned Rabbit cohort only, private UUID vhost and URL, one CPU, exact120s child bound, bounded private output, duplicate-field/type/shape/phase checks, four exact phases, own cleanup and primary-failure preservation. Producer replay-commit barrier correction is separately pending and is not accepted by this consumer review.

## Limits and required next steps

Author-provided browser-free controls and prepared-JDK configuration-gate PASS results were read as evidence; this reviewer ran none. They do not establish actual browser/DB/HTTP/native Alertmanager/nginx/deployment behavior. Legacy-owned dedicated config preserves original five project-case pairs by source, strict private reporter admission and artifacts off; no skip/xfail/retry/timeout/threshold relaxation found. Full old3+new7 privacy gates and collector fail-closed production sources were not changed by this inventory.

The three findings have been independently rechecked and resolved. The final narrow ci.yml source-pin/selected-line routing delta has now been independently reviewed and approved above. E2E-R001 positive legacy condition remains BLOCKED_CONDITIONAL_SCOPE unless owner scope changes; safe410 source behavior must not be relabeled as positive upload/approval proof. No acceptance, integration, runtime, or deployment claim follows from this review.

## Published preflight correction — HTTP500 identity fixtures and producer gate

Decision: **APPROVE** for the exact three-file correction against published `857841889860f3e943c8a02119f05ca270a06395`. The published CI's existing pure preflight exposed stale positive fixture coordinates, and the hosted private witness producer retained its former title-line gate. This correction binds both to the already approved current test source; no product/runtime behavior is changed.

Final 45-path manifest SHA256 before/after: `f511757bdbcab2ecc52e550e238fe5353dc16bf2d2b97c06f268934420c3c74d`; all declared hashes match. Current correction hashes:

| File | SHA256 |
| --- | --- |
| `.github/workflows/ci.yml` | `1722a8e229ffe54dce4c3dedd330a999f6c41b6dc8dbcba526adfe86da286835` |
| `tests/e2e/fixtures/http500-diagnostic-contract.py` | `f3b526a04b44a320c5d4bd08768a5e514b27326a4e0f5f7b3815e550f442f77b` |
| `tests/e2e/fixtures/mobile-dataset.ts` | `66d4b793d92c0d6df61e9c3578945e976fa853c4c7d772e53038d4e9560b059c` |

Personally read the entire three-file patch and surrounding admission/control source, then independently compared actual published Git bytes with frozen current bytes. The dataset change is exactly one `info.line !== 5948` to `5943` literal replacement. CI changes exactly the two existing dataset source-hash literals from `8af45478...` to the actual new dataset hash; both source gates remain mandatory. No other producer/receiver logic changes.

The control still has exactly the same20 test methods and definition names. Static Python AST comparison identifies only the six expected changed definitions: `current_receipt`, `homework_receipt`, `test_homework_legacy_and_exact_d002_extension_admission`, `test_d002_schema_source_bindings_fail_before_runtime`, `test_signature_negative_controls`, and `test_hosted_admission_remains_disjoint`. Their changes rebind positive fixtures/source assertions and add explicit stale-coordinate negatives. All other parser, privacy, correlation, schema, bounds and source-binding definitions are AST-identical.

Direct current source reads confirm ordinary title/call1585/1811, D002 title3958, and hosted title/call5943/6064. The fixture retains explicit rejection of retired ordinary1657/1883, D0023959/3959 and hosted5948/6069, including disjoint ordinary-versus-hosted admission. The producer still requires the exact hosted title/file, HEADMAN session, Chromium project/output directory, disposable loopback flags, exact run/clock directory identities, numeric run/attempt bounds, owned non-symlink modes, actual500 PUT attendance-mark path, and first-only private canonical request UUID witness. Existing title/actor/context/path/request gates, response handling, privacy, source AST integrity, correlation and receiver UNKNOWN qualification are unchanged.

Author evidence read: the existing pure control reports20cases/4failures/0errors before and20cases/0failures/0errors after; no runtime main/network/container calls are claimed. This reviewer did not execute the control, CI scripts, browser, native processes, databases, product operations, or any prior rejected experiment. Source-only byte/hash/AST comparison and report writing were the only actions. Earlier approved43-path core and final Java/E2E routing review remain applicable outside these exact corrections. No blocking finding remains; E2E-R001 unsupported positive legacy condition retains its prior qualification.


## К4 — независимое review точной CI provenance correction

# Независимое review CI Academic source-provenance rebind — APPROVE

**APPROVE SOURCE**, blocking findings **0**. Одобрены только восемь scalar bindings в `.github/workflows/ci.yml`, необходимые после уже одобренных пяти Academic fixture corrections. Runtime/CI acceptance **NOT_RUN**, причины исторических failures **UNKNOWN**. Это не review всего PR, inventory319 либо новый аудит 263 IDs.

Reviewer независим от автора CI delta; дочерние агенты не создавались. Requested model/effort `gpt-6.1-sol/high`; фактические runtime model/effort **UNKNOWN**. Прочитаны root AGENTS, canonical RULES/CURRENT, workflow, актуальные validation CURRENT/TEST-AUDIT-IMPLEMENTATION и два fresh source reports `review-academic-http-correction.md`, `review-academic-exports-correction.md`. Текущий owner GO на DELETE94/REWRITE169 и узкий provenance rebind передан root из прямого user packet; исторический `.agent` CURRENT не принят за новое разрешение или новый STOP. Риск: сохранить привязку существующих negative/restore/ordinary-green проверок к точным текущим source bytes и assertion frames.

## Проверенный снимок

- Repo: `/workspace/work/pr2-review/repo`; исходный проверенный HEAD `f10d465f8c37a4d33530790e2f8b881e8f964ca7`. Во время завершения отчёта root продвинул HEAD до `f42bf461daed8e6688358deef9619414596b0c87`. Независимый final readback подтвердил: parent нового commit — исходный HEAD, единственный committed path — CI, committed CI bytes равны frozen after. Source выводы сохранены; reviewer commit не создавал.
- Frozen inputs: `/workspace/work/test-audit-implementation/academic-ci-rebinding/{before-ci.yml,after-ci.yml,projection.safe.json}`.
- Before CI SHA256 `1722a8e229ffe54dce4c3dedd330a999f6c41b6dc8dbcba526adfe86da286835` — совпал побайтно с CI исходного `f10d465f` через `git show`.
- After CI SHA256 `499275b889c607bc84fad37c4f11665592f80daf2f40820115cabffb82d03321` — совпал побайтно с текущим рабочим CI.
- Оба файла имеют 7397 строк. Независимое сравнение обнаружило ровно строки **589, 602, 889, 898, 899, 903, 1545, 1902**. Последовательное применение шести точных substitutions из projection с проверкой multiplicity восстанавливает весь after побайтно. Значения дополнительно независимо выведены из текущих source bytes, а не приняты только из projection.

## Восемь bindings: 2 + 1 + 2 + 2 + 1

| Назначение | Строки CI | Проверенное значение |
| --- | --- | --- |
| RestApiIT source admission и exact restore | 589, 1545 | `8ee9bb192b91e0803913bc16fb8486039678634603043e576bdd3eb6906566cb` вместо `4cccd16f419fdb812114369fe6aaf52ad6c33f279a5772443dab5090e1b61893` |
| Существующий identity-negative SHA | 602 | `97fbfa97a70815f0283d9572ea278248467fc059d94444202417a203d780fd39` вместо `4c165afc0076d36800bf697b5f4bd5a4902c84cadf5d1e998d5e51a857bac2e9` |
| AcademicExportJobsIT admission и ordinary-green source target | 889, 1902 | `b454db34df04d223ad893769ecc392e445b84e22eb5eb18816eb8ae78e944a7b` вместо `30afcc02d68553512cd322724485e3bacf8d5d5df16c85610070946b5c3e1b63` |
| Teacher declaration/assertion zero-based indices | 898, 899 | `[501] → [510]`, `[546] → [555]`, фактические строки 511 и 556 |
| Teacher proof ruleLine | 903 | `547 → 556` |

`RestApiIT.java` и `AcademicExportJobsIT.java` независимо прочитаны из текущего repo; их SHA равны таблице, bytes равны HEAD и path status чист. Teacher declaration — `teacherReadyArtifactRequiresEveryCapturedCurrentAssignmentAfterScopeShrinks`, строка 511. Строка 556 содержит `assertThatThrownBy(() -> local.require(captured, true))`; proof ruleLine указывает именно на этот denial oracle. Его method selector не менялся.

Для identity сценария `duplicateIdentityResolvesToExistingAccountAndRoleFailuresLeaveCommittedStateUnchanged` статически разобран AST встроенного `PY_DOMAIN_RED`. Через `ast.literal_eval` прочитаны только заранее выбранные literal assignments; сам встроенный gate не исполнялся. Before/after AST узлов `identity_oracle`, `identity_update`, `teacher_anchor` совпали. Единственный identity anchor (SHA256 `b4edd9f463a027676b37a62e82d76dad4b0fc4601d354eb30943a8850388de1b`) и unchanged update bytes (SHA256 `8144ce9b18e6e5025886743c3cc155411d9a4d70b33aa34c2d9c2ff86d084c34`) дают в памяти negative `97fbfa97…`. Обратная замена восстанавливает исходные bytes. Расчёт существующей формулой даёт named rule frame `RestApiIT.java:382`. На диск negative не записывался.

`LocalReportAuthority.java` равен HEAD и чист по path status. Его unchanged SHA256 `2ed6ed5d37c319871a88469064595c65749d7404ac13fabd4a2c544bc90210de`; unchanged unique anchor `if (ready && !current.containsAll(identity.teacherScope())) throw denied();` при существующем удалении только в памяти даёт прежний negative SHA256 `7b5466a6c9fb694cea5b0d6417f31f6cc7c7d0afdc43a88023e0fd9212fe9bdc`. Production bytes и mutation не меняются.

## Сохранённые callers и ограничения

Весь CI вне восьми substitutions побайтно прежний; SHA256 последовательности остальных строк, соединённых LF без конечного LF, `dd78332f387c087250d2dca4ff298c7d3b7b65a07d69bbb310932e857fbc52f1`. Поэтому rebind не меняет schemas, predicates, gates, deadlines, selectors или privacy policy. Адресно прочитаны необходимые unchanged callers:

- Native gate сохраняет step timeout 8 минут и subprocess timeout 360 секунд, admission по HEAD/чистому status, exact method selectors, XML bounds 32 MiB, запрет DOCTYPE/ENTITY, точные suite/case names и counts 1 test / 1 failure / 0 errors / 0 skipped. Identity требует прежний label, true→false assertion и единственный точный named frame; teacher требует прежний missing-throwable message и единственный точный named frame.
- `finally` сохраняет исходные bytes, проверяет equality и чистый status; RestApiIT restore теперь связан с теми же actual source bytes, что admission. Teacher source/production restoration использует прежние `testSha`/`productionSha` и status guards.
- Следующий ordinary integration gate и safe collector получают прежние service/task, method/package и source paths. Ordinary-green требует `collection == RESULTS`, exact PR/checkout HEAD, run/attempt/job/service/task, successful test step, ровно один matching case, `SOURCE_METHOD`, exact source/hash и `PASSED`. Перепривязан только SHA teacher source. NO_RESULTS не превращён в PASS.
- `scripts/collect-ci-test-outcomes.py` равен HEAD, SHA256 `3b6d9196e018d4bbb048fe3c06c59b26d7d128931975132620ffb36fb43661d0`; source registry/hash и NO_RESULTS/NOT_ASSESSED поведение неизменны. Существующие safe-output/private-log границы не изменены.

Canonical `TEST-AUDIT-20261020.md` и read ledger побайтно равны HEAD (SHA256 соответственно `1fe9ac3821844bf9c105c4bcef1442c266dc21f2e2ff75e198c3365f989b6786`, `f1ec609fb283de80ac26da1b3eb7a3e50aa76b78a0f249c3aacd9faf0a8e75e3`). Их содержание и весь matrix/inventory заново не аудировались. Первоначально porcelain содержал **9 paths total: CI + 8 docs**; после root commit — только те же 8 docs. Эти чужие pending docs не изменялись reviewer и не входят в одобрение CI delta.

Проверки: read-only `git show/status/rev-parse`, byte/line comparisons, SHA256 и AST parsing/literal processing двух embedded Python sections. Gradle, compile, tests, embedded native gate, новые counterexamples, DB/Mongo/browser/Chromium, CI dispatch/rerun/cancel, commit/push не выполнялись. Raw private XML/logs, PII, secrets и произвольные diagnostics не читались. Единственная запись reviewer — этот внешний отчёт.

Frozen B013–B015, T008/protected rights, rejected Mongo PUT500/Chromium equivalents не переоткрывались. Это rebind уже существующего принятого routing, без нового эксперимента и без ослабления требований. Merge остаётся BLOCKED по фактическим красным gates и неразобранным failures; source approval не устанавливает runtime PASS и не снимает owner ограничения.
