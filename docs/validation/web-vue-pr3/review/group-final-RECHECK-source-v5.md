# GROUP FINAL DELETE — узкий независимый SOURCEv5 recheck

Вердикт: **PASS по source recheck F1–F3 и двухфайловому SOURCEv5 delta**. Новых подтверждённых P findings в этих границах нет. Это не runtime PASS и не повторное полное review изменения.

## Зафиксированная граница

- Base: `10d8b908ae9801b22018881389f21ef5c0188583`.
- Проверены immutable snapshots `/workspace/rct-pr3-runtime/group-final-delete-20261024/review-source-v{3,4,5}/source/`; неизменённые Auth dependencies прочитаны через `git show` base. Текущие продуктовые файлы автора не использованы для оценки.
- SOURCEv5 patch SHA-256: `516af78ebf73082eb6e63ba34f13bc09ff68a09cc7beabf3287dd778dc1261d2`; SHA256SUMS SHA-256: `c7351b85e907acfece8747ba8dec7a5a2de49945a993b6c4bc784098ea31ea67`. `sha256sum -c SHA256SUMS`: все 45 файлов совпали.
- Diff SOURCEv4→v5 содержит ровно `GroupDeletionService.java` и `GroupDeletionHttpIT.java`. Предыдущий FAIL `REVIEW-source-v3.md` сохранён без изменений.
- Scope: исправление прежних F1/F2/F3, их причинное влияние и указанный delta. Известный отдельный gap normal wrong-password/stale-digest rejection до создания operation и последующего клиентского GET 404 в этот recheck не включён; автор исправляет его отдельно.

## Закрытие прежних findings

**F1 (прежний P1) закрыт в source.** Сохранённый `proof_receipt` теперь входит в `GroupDeletionStore.Saved` и exact command первого, Auth, участника. В `AuthGroupDeletionParticipant.java:113` первый COMMIT до удаления grants вызывает admission точного bound receipt. `AuthPasswordStepUpService.java:138` проверяет исходную session identity, текущую выбранную активную ADMIN role, permissions, session/roles versions, credentials/binding/profile generations и срок receipt; после session row lock повторно читает live authority. Проверка и удаление grants выполняются в одной Auth-транзакции под существующими global/identity locks.

Известный отрицательный admission сохраняет Auth `RELEASED` с нулём необратимых эффектов и возвращает `commitRejected`; coordinator прекращает дальнейшие COMMIT и переводит Academic в `RELEASING`. Неизвестный исход RPC оставляет Academic `COMMITTING`. При повторе исходная identity/receipt сверяется с immutable Auth epoch: `RELEASED` снова даёт явный отказ, `DELETED` возвращает прежний результат без повторной проверки уже истёкшей authority. Тем самым потерянный положительный ответ не превращается в rollback, а потерянный отрицательный ответ можно безопасно разрешить при повторе. `beginCommit` по-прежнему фиксирует локальный intent до Auth admission; право отменить этот intent ограничено явным durable zero-effect Auth rejection.

**F2 (прежний P1) закрыт в source.** `group_deletion_manifest_closed` в `V14__final_group_deletion.sql:65` проверяет фактические CASCADE/SET NULL/SET DEFAULT FK edges: dependent row, отсутствующая в точном target inventory, блокирует snapshot/accept/beginCommit до первого необратимого эффекта. Historical foreign-group threshold теперь даёт 409 и сохраняется. `guard_group_deletion_write` отдельно блокирует любой effect вне exact manifest в deletion context; обычные subject references берут fence группы предмета даже при собственном ненулевом `group_id`. Новый threshold trigger и исправленная проверка API не позволяют создать исходную cross-group комбинацию. Чужая строка не добавляется в разрешённое удаление.

**F3 (прежний P2) закрыт в source.** Attendance PREPARE транзакционно сохраняет lesson→group/semester scope для всего Schedule lesson manifest, включая lessons без Attendance rows (`AttendanceGroupDeletionParticipant.java:81`). Snapshot/counts/digest/delete выбирают как `group_id=target`, так и точные `lesson_id`, поэтому обычные null-group cancellation markers и старые pair rows попадают в inventory; противоречащие owner/semester отклоняются. Pair writer разрешает lesson-only scope и берёт тот же group fence. Mongo aspect больше не пропускает protected mutation с неизвестным scope. COMMIT сохраняет tombstones для всех manifest lessons, включая no-row lessons; реальный cancellation handler проверяет их до создания marker/pair, а RELEASE удаляет подготовленный mapping и снимает fence. Данные чужой группы и неразрешимые foreign scopes не становятся частью удаления.

## SOURCEv5 delta и ограничения evidence

`GroupDeletionService.acceptedResponse` убирает только вторую авторизацию внутри уже допущенного POST. Первичная `authorize`, exact replay identity/digest check и actor check ответа сохранены; самостоятельный GET status по-прежнему авторизуется. Решение о первом необратимом эффекте остаётся за описанной выше live Auth admission, поэтому выдача durable результата POST не разрешает удаление после отзыва полномочий.

Изменённый HTTP IT действительно вызывает реальный Auth participant до искусственной потери ответа и содержит оба исхода: grants уже удалены и grants сохранены/epoch RELEASED. Это meaningful source coverage ambiguous reply recovery, а не transport stub вместо Auth admission. Новые F1 revoke/expiry, F2 historical foreign-reference и F3 real cancellation/no-row случаи соответствуют прежним найденным рискам. Schedule/Attendance transport doubles в Academic HTTP IT остаются; отдельные participant IT используют реальные PostgreSQL/Mongo boundaries. Это не полный живой четырёхсервисный путь.

Reviewer не запускал Gradle, Docker, БД, HTTP, сеть или тесты; compile/test/runtime runs reviewer = **0**. Указанный в snapshot compile PASS относится к SOURCEv4; SOURCEv5 compile/IT и результат применения динамической SQL migration этим source review не подтверждены. Создан только этот отчёт; продукт, прежний FAIL и runtime resources не изменялись.
