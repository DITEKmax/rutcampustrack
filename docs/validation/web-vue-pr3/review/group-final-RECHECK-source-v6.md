# GROUP FINAL DELETE — узкий независимый SOURCEv6 recheck

Вердикт: **PASS по source additive resolve delta**. Подтверждённых новых P findings нет. Предыдущий SOURCEv5 PASS по F1–F3 переиспользован; полное review не повторялось. Это не compile/IT/runtime PASS.

## Граница

- Immutable `/workspace/rct-pr3-runtime/group-final-delete-20261024/review-source-v6/source/`, base `10d8b908ae9801b22018881389f21ef5c0188583`.
- Patch SHA-256 `dce498411920ec8942dd1dc715e94f1425f0daf147ab4543daf4a32282f8d84c`; manifest SHA-256 `ce9201457b8d7360f2644b76f960f46a8ccd86b83c3aa3fdf35263f0fd0f82ba`. Все 46 файлов прошли `sha256sum -c SHA256SUMS`.
- SOURCEv5→v6 содержит ровно 9 путей из `snapshot.json.deltaFromV5`: API, controller, coordinator comment, service, store, no-store filter, HTTP IT, fixture и новый `CachedImagesOnlyPullPolicy.java`. SOURCEv3 FAIL и SOURCEv5 recheck сохранены без изменений. Moving worktree не использован как evidence актуального продукта.

## Проверенное поведение

`POST /academic/groups/{id}/deletions/{operationId}/resolve` не требует body/password/idempotency header и возвращает прежний Operation JSON с HTTP 200. `GroupDeletionService.java:73` сначала требует текущую ADMIN authority с `GROUPS_MANAGE` и `DATA_DELETE`; это не повторный вызов password confirmation. Отрицательный/nonpositive group отвергается, положительный отсутствующий group разрешён как opaque receipt scope; group ID сериализуется строкой без потери точности.

`GroupDeletionStore.java:148` атомарно вставляет `CANCELLED/NOT_ACCEPTED` с пустыми domain/proof manifests и восемью обязательными нулевыми counts через `INSERT ... ON CONFLICT(operation_id) DO NOTHING`. Тот же PK используется acceptance. Если tombstone побеждает, поздний accept не может заменить immutable terminal row: проверка existing либо unique violation приводит к 409, а coordinator игнорирует CANCELLED. Если accept побеждает, resolve ничего не обновляет и возвращает именно accepted row. Это сохраняет PREPARING/COMMITTING и все остальные исходные фазы; resume может продвинуть принятую операцию по прежнему протоколу, но resolve не создаёт cancellation для неё.

При конфликте PK store возвращает исходную row, а service до вызова `advance` сравнивает actor и group и выдаёт одинаковый 404 для чужого scope. Поэтому resolve не меняет, не возобновляет и не раскрывает receipt уже существующей чужой операции. Для своего actor разрешён новый текущий session: recovery не привязан к старому HTTP session, тогда как первый необратимый Auth COMMIT по-прежнему проверяет исходный live proof из SOURCEv5. У terminal tombstone не появляется business/group fence: он не блокирует новую попытку с новым UUID и не изменяет существующие данные группы. Durable receipts остаются под прежним immutable trigger, GET 404 сам по себе не становится разрешением повторного удаления.

No-store filter расширен точным resolve path и сохраняет заголовок также для pre-controller validation/authentication failures. Delta coordinator содержит только уточняющий комментарий; исправления F1–F3, внутренний protocol и публичная Operation shape не менялись.

## Проверка тестового delta и пределы

Новые HTTP race cases используют реальные boundaries: первый приостанавливает ответ реального password proof перед acceptance и даёт resolve зафиксировать tombstone; второй приостанавливает PREPARE после durable acceptance при ещё открытом confirm HTTP request и проверяет возврат PREPARING с последующим завершением. SQL/authority outcomes не подменены. Исходы wrong password/stale digest разрешаются в terminal receipt; позднее подтверждение старого ключа отвергается. Проверки также охватывают чужого actor/group, непривилегированного actor, новый session того же actor и отсутствующий group ID больше `2^53`. Тело ответа и no-store проверяются существующим HTTP helper. Эти тесты прочитаны, но не выполнены.

`CachedImagesOnlyPullPolicy.shouldPull` всегда возвращает false. При `RCT_GROUP_DELETION_BOUNDED=1` fixture применяет её к своим PostgreSQL и Redis контейнерам; вне этого режима существующий запуск не меняется. Это ограниченный resource delta, не отключение бизнес-проверок. Применение политики к Testcontainers helper images зависит от отдельной runtime configuration и здесь не объявляется доказанным. Pull, Docker и инфраструктурные действия reviewer не выполнял.

Создан только этот отчёт. Reviewer compile/test/runtime runs = **0**; SOURCEv5/v6 и тесты не объявлены скомпилированными на основании SOURCEv4 compile PASS. Runtime lease остаётся у Root; клиентская интеграция и её conditional review — отдельная граница.
