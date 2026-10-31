# Independent combined frontend review — final group deletion

**CHANGES REQUIRED — два P2 в recovery intent durability/non-admission classification.** Новых подтверждённых P1 нет. Source review не принимает runtime destructive action.

Дата2026-10-24. Frozen original client `ffc986256d73f95a56af1ca2bd38a43d4ff16914` (base10d8), frozen WEB `38aa0211fc9fcb48d4688e42f34839298bbb0d32`, dependency abca40d7a byte-equivalent ffc по двум client paths. Scope ровно client+test и четыре UI/state/test файла. Прочитаны оба author READY reports и все шесть scoped файлов. Назначение Sol6.1/high сохраняется; runtime model telemetry недоступна.

## P2 — failed durable intent save не останавливает password-bearing POST

**Путь:** `frontends/web-vue/src/features/admin/GroupDeletionPanel.vue:41–48`, `group-deletion-state.ts:104–108`.

Panel persist callback перехватывает любую ошибку sessionStorage.setItem и возвращает success. Flow после save(reference) устанавливает memory state и вызывает confirmGroupDeletion. Поэтому комментарий persist BEFORE sending обеспечивает порядок вызовов, но не долговечность original key.

**Causal case:** browser sessionStorage quota/SecurityError → пользователь подтверждает deletion → server принимает original UUID, ответ теряется → reload/crash. SessionStorage не содержит reference, memory исчезает. Новый mount не знает UUID, не может выполнить mandated original-operation GET и предлагает fresh preview/new intent. Даже если сервер потом завершит/отменит прежнюю операцию, клиент не способен подтвердить её результат по исходному receipt. Это конкретная потеря recovery, не пожелание улучшить storage API.

**Исправление:** не отправлять destructive command при неуспешном durable save исходного reference; показать безопасную ошибку до POST или использовать действительно сохраняемый fallback. Отдельное удаление terminal reference может оставаться best-effort: его неуспех оставляет безопасный GET-only recovery. Password/SID/токены не добавлять в storage.

## P2 — всякий POST4xx ошибочно считается доказанным отсутствием принятой операции

**Пути:** `frontends/mobile-core/src/features/admin-groups/admin-groups-client.ts:392–397`; `frontends/web-vue/src/features/admin/group-deletion-state.ts:118–129`.

Client сводит любой4xx кроме408 к sanitized AdminGroupsApiError без semantic admission discriminator. State на любом таком error удаляет durable original reference и разрешает fresh preview/new UUID. HTTP403/401/409 сами по себе не являются гарантией, что original command не был durably принят.

**Конкретный текущий server case:** прочитанный GroupDeletionService.confirm:61 сначала выполняет transactional store.accept, затем :68 вызывает status(key); status:72 заново authorize() через live admin authority. Если права отозваны после accept до второго authorize, исходная operation уже сохранена, но POST заканчивается403. Browser ещё может иметь прежний locally current identity, поэтому transport owner assertion не заменяет server permission check. Client/UI считают response definite rejection и теряют единственный original key вместо сохранения GET-only recovery после восстановления прав. Revoked authority не должна раскрывать receipt, но это не делает durable operation несуществующей.

Backend tree в предоставленном WT ещё dirty/under correction: он не назван frozen product SHA. Observation зафиксирован SHA256 GroupDeletionService.java `82cd6ae7bc3b2d64afc2253de7160e6d755a0186218b4034e36f79e4d82e4d08`, source control flow описан выше. Frozen DTO/API не обещает глобальное «любой4xx означает no-admission». Finding относится к frontend assumption; если отдельный финальный backend контракт устранит все post-admission4xx, его нужно явно подтвердить перед закрытием этого замечания.

**Исправление:** сохранять original reference для post-send ответов, не доказывающих non-admission. Fresh preview допустим после достоверного explicit zero-effect rejection/terminal CANCELLED, а не по одному HTTP status. Можно использовать узкий проверенный allowlist стабильных безопасных machine codes, сохранив password/body redaction; ambiguous/opaque4xx должны оставаться original GET recovery. Не добавлять automatic POST retry и не сохранять arbitrary Problem Details. Existing state tests165–178 и client181–216 сейчас закрепляют blanket4xx assumption и не доказывают серверную zero-effect семантику.

## Проверенное поведение без новых findings

- Java GroupDeletionModels/Api и controller phases/counts/status mapping прочитаны адресно в actual group-final-delete WT (предоставленный `/source` subpath отсутствует; реальные paths находятся непосредственно под services). Preview/confirm/status routes, UUID header-only key, exact body password+digest, decimal string IDs, required8/additive safe nonnegative integer counts, explicit phase/retryable/failure decoding согласованы. Confirm200 допускает только COMPLETED, confirm202 — остальные saved phases; GET200 explicit saved phase. Unknown/malformed receipt не превращается в success.
- Client sends exactly one confirmation через direct fetcher с captured-owner assertions, вне legacy identity replay/401 POST retry. Lost response/5xx/408/malformed ACK возвращают sanitized UNKNOWN reference без cause/body/password. Known4xx body тоже sanitizes; проблема выше — classification, не утечка password.
- Flow сохраняет только userId/groupId/UUID/digest, restore allowlist отбрасывает чужого actor/arbitrary fields, не хранит SID/password. Recovery — explicit same-original-key GET; missing/denied/unavailable GET сохраняет reference. Retryable pending не приводит к POST. Terminal COMPLETED/CANCELLED освобождает reference; complete clears selection/roster/private page до registry reload, даже если load падает.
- UI проверяет GROUPS_MANAGE AND DATA_DELETE через fresh authority перед preview/submit/recover. Permission withdrawal clears password/private preview/progress; private reference сохраняется. Scope transport+state fences не применяют late old-owner response к новой authority; retiring owner clearPrivate не требует current assertion. Shell key by session generation и existing authority cleanup были прочитаны только как релевантные consumers, без повторного review всей Auth.
- Preview numeric/current-password modal использует существующий ConfirmDialog и inherited styles. No per-component theme/global PCSS/config/dependency edits. Live privilege refresh теперь допускается при selected/preview/pending, domain registry reload deferred. Нет новой mobile UI; existing shared PWA/TMA legacy methods byte-unchanged по additive diff.

## Evidence и пределы

Прочитаны meaningful author tests:13 client cases (strict DTO/foreign receipt/transport/owner/error sanitization/original recovery),14 state cases (lost ACK+reload/same key, pending/no replay, terminal cancel, owner loss, storage allowlist). Они проверяют state/ports, не actual Vue mounted permission withdrawal или реальный сервер. Storage-write-failure и accepted-then4xx случаи ими не проверены. Existing17 legacy tests/typecheck/lint PASS принимаются только как переданная author evidence; reviewer их не повторял и не превращает count в feature acceptance.

Reviewer pure checks: git diff --check обоих frozen commits exit0; scoped client trees ffc↔abca identical. Read frozen sources and targeted unchanged ownership/transport/modal/live glue. No новые tests/framework, browser/backend/API/DB/network/build/Gradle/CI/containers/source edits. Backend transaction/privacy wholegraph review отдельно owned и pending corrections; его findings не переоткрывались. Local destructive UI acceptance, keyboard/viewport and assembled runtime/build остаются отдельными gates. Новая action count этим review не принимается.

Source attribution SHA256: frozen client admin-groups-client.ts `aa46af8368f76121e3ad3e6d761ee9ead80a3077c82e15c3a56645372a605b52`; state `48cf1824bf3483bc4d8f6e002ae4ca2167133631ac3534b4e3991b595c725e20`; panel `e6d3e32e29d4c5994d8b597f6af75c947fdc443f2f29245476ffa7c7260cef73`.

Единственная новая запись reviewer — этот внешний immutable report. STOP; процессов/ресурсов reviewer нет.
