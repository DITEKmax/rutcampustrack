# Narrow frontend correction recheck — final group deletion

**Оба прежних P2 закрыты. Общий functional verdict: CHANGES REQUIRED — один отдельный P2 в recovery не принятого validation command.** Это не возврат к status-only unlock и не требование automatic POST retry.

Дата2026-10-24. Frozen original client `3f7d4744a6a369833c8ead3834d5e5add46c99dc` atopffc, UI `a087458e5b2487911a543382361a6f71a6bb1225`; dependency cherry c5cdf1b9b совпадает по двум scoped client files. Предыдущий REVIEW-ffc986256-38aa0211.md остаётся неизменным. Sol6.1/high назначение сохранено, runtime telemetry недоступна.

## Original P2 #1 CLOSED: durability перед POST

Panel теперь применяет bindGroupDeletionRecovery. setItem failure превращается в sanitized GroupDeletionStorageError WRITE, выходит из `save(reference)` до state transition/confirm dispatch. Preview остаётся доступным для безопасного повторного сохранения; target не мутируется. Terminal removeItem best-effort оставляет лишь GET-only terminal hint. Initial read denial/malformed saved reference создаёт recoveryUnavailable lock, не masquerades as empty slot; восстановление принимает allowlisted same-actor original key/group/digest и не retargets уже известный intent. Password, SID, token/cause в storage/error не добавляются.

## Original P2 #2 CLOSED: accepted then opaque4xx

Client post-send catch теперь всегда UNKNOWN с original operation/group IDs и status, без response/problem/password/cause. UI удалил blanket4xx clear-reference. Accepted-late403, missing404 и permission errors не теряют key, не создают replacement UUID и не запускают второй POST. Это устраняет исходную небезопасную inference о non-admission. Tests source добавляет causal accepted→403→temporarily404→original COMMITTING, а state — original receipt recovery без затрагивания later data.

## NEW P2 — normal validation refusal превращается в permanent original-key dead end

**Пути:** `mobile-core/src/features/admin-groups/admin-groups-client.ts:392–395` в3f7; `web-vue/src/features/admin/group-deletion-state.ts:172–202` вa087; `GroupDeletionPanel.vue:269–288` recovery; original GroupsPage.vue:50–54/327–330 lock.

**Causal case:** получить valid numeric preview → ввести неверный текущий password → backend отказывает до store.accept, не создаёт operation. Аналогично stale preview конфликт возникает до accept. Теперь reference уже durably saved; POST failure становится UNKNOWN; единственная команда UI — GET original operation. При отсутствии операции backend отвечает404, state не меняется. Повтор GET и reload/new session того же actor восстанавливают тот же reference и снова404. Backend status не создаёт terminal non-admission receipt. Нет server-authoritative resolution, поэтому исправить пароль/получить fresh preview для нового подтверждения невозможно; original pending lock блокирует остальные group writes и route leave. То же возможно при owner loss до фактического dispatch после успешного durable save.

Это существенный обычный пользовательский путь validation/recovery, не косметическая safe limitation. Current correction безопасно сохраняет неопределённость, но complete action должен иметь путь её завершения. Отказ не снимает необходимость исходных safety fixes.

**Закрытие:** согласованный backend/client/UI original-operation resolution, который надёжно различает/атомарно закрывает non-admission и уже принятую операцию под тем же frozen key/target/digest/current authority. После explicit authoritative terminal zero-effect result можно освободить reference и отдельно предложить fresh preview/new user confirmation; pending/unknown/403/404 сами по себе этого не позволяют. Backend owner уже обсуждает minimal contract; конкретный endpoint/phase не выдумывается этим review. Не добавлять forget-key, status-only unlock, automatic POST replay или blind new UUID. Требуется короткая recheck этого нового contract/consumer delta перед functional acceptance.

## Проверки и границы

Прочитаны author correction reports и весь narrow diff3f7/a087 (client/test + panel/state/test); GroupsPage unchanged. Independent git diff --check обоих frozen corrections exit0; client tree diff3f7↔c5cdf по двум files пуст. Existing reported14client/16state cases, types/lint PASS приняты как author evidence, без повторного запуска. Added storage-failure tests проверяют target remains untouched, read lock/terminalcleanup; causal late403 tests не закрывают нормальный never-admitted command. Новый permanent-404 risk прямо отражён как safe limitation в author correction report; здесь он квалифицирован как blocking functionality.

Full DTO/phase/permission/theme baseline review не повторялся; backend full-source/transaction review остаётся отдельно owned. Не было новых tests/framework/browser/DB/API/network/compile/build/CI/product/root edits. Q3/c5a ownership root handles separately, unrelated scope не открывался. Единственная запись — этот immutable external report. STOP; ресурсов/процессов reviewer нет.
