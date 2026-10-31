# Independent S3 source review — scoped map deletion and receipt recovery

Вердикт: **PASS для combined frontend source; подтверждённых P1/P2 нет**. Actual SUT/browser whole-map deletion не выполнялось и этим review не принимается.

Дата: 2026-10-24. Frozen originals: shared API `a36ab62664751b31386e50b863e06c963c0912d6` и WEB `85d801b9077632f9f805fca37fbf7b79ba33d50d`. Scope — ровно shared `frontends/mobile-core/src/api/{types.ts,map-client.ts,map-client.test.ts}` и WEB `frontends/web-vue/src/features/admin/{MapPage.vue,map-deletion-state.test.ts}`. Dependency cherry `2ff1c9a7f` имеет одинаковые shared scoped files с original a36; не является вторым API delta для интеграции. Назначение — независимое Sol6.1/high review; runtime model telemetry недоступна.

## Scope/compatibility boundary

`AdminMapPlanDeletionPreview.deletionScope` — additive optional nullable enum. Client:245–246 допускает omitted/null как неизвестную capability и возвращает старый readable preview без повышения scope. Любое другое nonnull значение должно точно соответствовать captured intent: omitted format → ALL_VERSIONS; explicit png/svg → CURRENT_FORMAT. Existing target/format/digest/count/bytes validation сохранена; malformed или cross-intent enum не доходит до обычного action flow.

Requests/deletion results, endpoints, explicit selected-format mobile behavior, mutation retry flags и existing generation fences не меняются shared patch. В актуальном backend WT адресно сверены nullable compatibility constructor/enum и explicit ALL/CURRENT branches; Java/OpenAPI deployment и generation readiness принадлежат отдельному backend owner. Старый stand без marker не становится поддерживающим whole-history deletion.

## WEB destructive intent

- MapPage:78–90 разрешает action только при положительном совпадении server scope, selected building/floor и selector format. Нет inference из количества файлов, current pointer или прежнего false/static capability. Upload/edit context исключается. Current-plan-null/orphan floor может получить server whole-history preview, а подтверждение всё равно требует ALL_VERSIONS.
- Preview:262–277 требует fresh DATA_DELETE через `admin.run`, current CAMPUS_MAP_MANAGE, captured transport и post-read authority assertion; прежний preview/password очищается. Changing format сбрасывает preview/password, target/edit/upload context сбрасывает preview. UI честно различает whole history и current-format deletion, включая необратимость.
- Remove:296–324 требует unlocked state, текущие DATA_DELETE+CAMPUS_MAP_MANAGE, положительный scoped preview и текущий password. Operation UUID создаётся для этого explicit confirmed intent; reference фиксирует building/floor/format/previewDigest/operationId. После отправки password очищается в finally при любом исходе, включая permission/validation failure. Secret не входит в retained reference.

## Unknown outcome и authority

- `confirmDeletion` принимает success только после existing AdminMapClient validation: точные operationId/building/floor/format и status COMPLETED. Post-response generation/authority fences выполняются до снятия unknown; wrong receipt, malformed response, network или retired owner не создают ложный success.
- При неопределённой исходной мутации сохраняется исходный nonsecret reference. Locked state блокирует новый preview, новый delete и доступные target-changing controls; background refresh заблокирован. Route-leave/beforeunload guards сохраняются.
- `inspectDeletion` теперь является явно подписанной reauthenticated reconciliation-командой: current permission refresh, current password, **тот же** reference и только explicit submit. Он не читает pointer для угадывания результата, не генерирует UUID, не получает новый preview, не меняет target/format/digest и не выполняется автоматически. При любой ошибке reference остаётся, password очищается; отсутствие текущей схемы не трактуется как доказательство удаления истории.
- Existing POST client вызывает request с `allowRetry=false`: 401/transport failure не дают скрытого write replay. Read-side GET refresh/retry не превращается в delete. После validated receipt UI снимает unknown и обновляет inventory; новая version4 не считается целью исходной операции.
- Адресно проверена backend compatibility premise: planConfirmationTarget/deletePlan ищут original receipt и сверяют owner/building/floor/format/digest до live snapshot/deletion branch, возвращая original completion. Это подтверждает корректность использования той же identity со свежим password; backend whole-graph implementation/реальные HTTP/PG tests отдельно не пересматривались и не выдаются за собственную runtime evidence reviewer.

## Meaningful tests и проверки

Прочитаны новые API cases: omitted/null readability без capability; три согласованных request/scope variants; enum другого intent; malformed nonnull markers. Это validation поведения existing client, не copies enum declarations в отдельном mirror implementation.

Прочитаны compiled Vue setup/SSR state cases с настоящим AdminMapClient: old scope не отправляет delete; orphan history разрешает scoped flow; format switch инвалидирует capability; lost ACK удерживает reference; никакой новый write без password/explicit reconciliation; original receipt подтверждается при новой version4 без её удаления; network/stale/denied/wrong receipt сохраняют unknown; owner retirement и каждый required grant блокируют continuation. Mock server моделирует receipt semantics; actual backend equivalence сверена отдельно по source. Это meaningful causal handlers/state/rendered-control coverage, не actual DOM event/browser/SUT proof.

Авторские final client16/16 и compiled Vue/SSR12 PASS, scoped strict types/ESLint0 переданы в packet; reviewer не повторял suites/builds. Позднее уточнение root о nullable compatibility меняло accepted contract, поэтому targeted client rerun после него обоснован. Независимо выполнены frozen diff/source review, обе `git diff --check` exit0 и scoped a36↔2ff byte-equivalence check. Original WEB author HEAD соответствовал85d, scoped files читались по frozen git objects.

## Точные пределы

Reference здесь сохраняется в текущем mounted component; новый durable across-forced-reload recovery protocol patch не добавляет. Route/beforeunload protection не означает storage-backed recovery; такого runtime результата reviewer не заявляет. Current backend stand950 без additive capability остаётся unsupported для нового action до отдельно принятого rollout.

Нет reviewer запуска SUT/API/PG/browser/delete/upload/Gradle/Docker/build/CI или повторного16/12 suite. Реальный updated-backend + assembled WEB preview/reauth/whole-history deletion/orphan-history/lost-ACK-reconciliation acceptance остаётся **NOT RUN / pending**. Отдельные backend real checks, model server tests и source PASS не складываются в полный action PASS.

Продуктовые исходники, остальные features, конфигурации, credentials, БД, сеть и процессы reviewer не менял. Единственный созданный файл — этот внешний immutable report.
