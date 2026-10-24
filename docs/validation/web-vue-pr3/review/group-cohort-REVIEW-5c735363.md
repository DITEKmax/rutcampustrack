# Независимый source review cohort PREP — 5c735363

**CHANGES REQUIRED — пять P2 ниже. Runtime GO отсутствует.** Pending frontend binding — ожидаемый fail-closed gate, не самостоятельный дефект. До приватных inputs/запуска проверяются WAIT state и null binding, поэтому текущий пакет не исполняется.

Независимо проверен manifest SHA256 `5c7353633b995174ff4a6d4d6506a3830c7be8c22ce282577a5f05a6617422c3`: все **6 файлов / 55892 B** совпали. Рассмотрены целиком native-cohort.cjs, cohort-proof.py, freeze.py, bindings/GO/README и конкретные связанные Auth/TMA/v3 proof contracts. Принятый runtime v3 `180fecc3` и его 20 backend cases не переоткрывались. Reviewer: только публичные исходники/metadata/hash; private credentials/fixtures/env, браузеры/API/Docker/БД/runtime/tests —0. Назначение модели gpt-6.1-sol/high; actual model telemetry недоступна.

## F1 — P2: Auth write/capture barrier неполон

`native-cohort.cjs:12–15,146–183,190–220`.

Первичный login/refresh получает предварительный authCheckpoint, но PUT `/api/auth/session/active-role` лишь выставляет RAM `roleForwarded` и немедленно forward: exact attempt/SID/expected version не сохраняются перед мутацией. Успешный ответ этого PUT обновляет RAM bearer/session, но не попадает в ветку `ACCEPTED_AUTH_RESPONSE` и не сохраняет новый token/storage через authCheckpoint. Сохранение наступает только позже при canonical checkpoint. Кроме того, `authCaptureInFlight` блокирует только следующие auth POST: обычные GET и этот PUT могут пройти, пока accepted response ещё асинхронно захватывается. Это не заявленная гарантия «own accepted handles before further requests». `save()` также не делает fsync перед внешним forward, в отличие от принятого v3 intent protocol.

Следствие: потеря/остановка после accepted role change либо гонка последующей команды оставляет durable handle со старым bearer/cookies и без точного pending role command; неизвестное изменение уже произошло. Общий catch сохраняет только общую стадию и не восстанавливает эту корреляцию.

Минимальное закрытие: для каждого разрешённого Auth write, включая role PUT, durable/fsynced attempt до forward; accepted headers/body/latest same-SID token/storage до выпуска следующего зависимого API-запроса. Барьер должен завершать capture либо HOLD, не выдавать новый auth/retry. Не требуется новый общий framework или повтор auth campaigns.

## F2 — P2: quiesce смотрит другой Rabbit vhost

`cohort-proof.py:23–29` вызывает `rabbitmqctl list_queues` без `-p`, то есть проверяет `/`. Все relevant apps frozen v3 используют `SPRING_RABBITMQ_VIRTUAL_HOST: pr3-admin-isolated`.

Если default vhost пуст, подготовка остановится после создания семи own sessions; если в нём есть пустые очереди, возможна ложная quiescence при pending доставке в нужном vhost. Проверка outbox status=sent не доказывает завершённую обработку Notification consumer, и baseline history/push/audience может ещё меняться от fixture events.

Закрытие: ограничить queue query точным `pr3-admin-isolated` и существующим bounded accounting; отсутствие/ошибка его queues — STOP до delete. Не использовать global drain/purge или менять брокер.

## F3 — P2: итоговый cohort receipt не подключён к mandatory v3 clock restoration

`native-cohort.cjs:430–440` пишет только собственный `private/receipt.json`, состояние COMPLETE и отдельные checks. В неизменном v3 `calendar-control.py:26–27` restore-clock читает **v3/private/native-case-result.json** и требует `allPassed`, `phase == COMPLETED`, `cleanupComplete`, а также v3/native-after.json. Cohort этот case-result файл и два обязательных booleans не создаёт; описанного проверяемого adapter шага в пакете нет.

Следствие: даже полностью успешная cohort не даёт восстановить исходные teaching clock/semester по принятому протоколу. Calendar restore создаст ещё один Admin context, затем остановится на отсутствующем/несовместимом proof. Самостоятельное ручное объявление PASS/копирование незавершённого receipt не является допустимым bridge.

Закрытие: один явный source-reviewed handoff/adapter к существующему restore input после original COMPLETED, всех passive checks, independent postproof и exact own cleanup. Коррелировать source/package/UUID/SIDs и неизменный v3 receipt schema, не ослаблять restore guard и не писать успешный result на unknown. Runtime/clock source framework менять не нужно.

## F4 — P2: разрешённый TMA readmission несовместим с same-SID cleanup oracle

`native-cohort.cjs:160–170,208–215,313–315`; фактический frozen Auth `TmaService.authenticateWithInitData` вызывает `AuthService.issueSession`, который создаёт `UUID.randomUUID()` для **новой** сессии. Это не refresh старого SID.

После withdrawal драйвер явно разрешает один normal owner `/api/auth/tma`. Такой принятый ответ создаёт новый own SID; canonical snapshot затем закономерно выставляет `successor`, а cleanup требует original SID и отказывается продолжать. Старый Headman SID при COMMIT остаётся live/unselected, новый также может быть live. Один initial-SID bearer401 не означает revocation ни одного из них. Аналогичная общая «same live SID normal readmission» формулировка в README не соответствует этому TMA endpoint.

Закрытие до irreversible case: связать oracle с реально принятым TMA owner contract. Если допускается normal readmission с новым SID, учитывать его отдельным явно own, canonical-qualified handle и заранее разрешённым точным cleanup scope, сохраняя исходный live/unselected SID и запрет подменить его successor'ом. Если этот bounded cleanup не разрешён, не допускать такой путь как завершаемый cohort. Не маскировать проблему fake401/revoked claim, ручным refresh, новым initData/login либо снятием successor fence. Pending binding сам по себе не меняет immutable Auth issuance contract.

## F5 — P2: «автоматический refresh после hint» измеряется временем ответа

`native-cohort.cjs:180–181,397–402` и reused observer teacherRetained записывают/check response time, но не начало соответствующего GET. Перед delete `settle()` ждёт только jobs для уже полученных response events, а не outstanding network requests.

Поэтому catalog/stats GET, начатый до hint и завершившийся после него, удовлетворяет oracle. У control roster неизменные данные, поэтому такой старый stats GET особенно легко засчитать за требуемый новый automatic refresh. При подходящем времени pending catalog GET тоже может прочитать уже удалённый target. Событие+верные итоговые значения тогда не доказывают, что focused owner действительно инициировал обновление от live invalidation; исходный APP defer может быть скрыт.

Закрытие: пассивно коррелировать request start/handle с response и требовать relevant catalog/control-stats GET, начатые после exact group.updated hint, под тем же owner/query. Сохранить отсутствие кликов/focus/reload/forced reconnect. Это исправление oracle существующего единственного case, не требование дополнительной destructive попытки.

## Остальные source выводы

- До private read остаются frozen driver/v3 hashes, frontend state/manifest/Root HEAD и runtime admission. 7-GiB preparation admission, QENV010 PENDING и отдельный Root GO не заменяются example boolean; браузерный monitor ограничен собственным browser. Final accepted source binding всё ещё необходим и будет отдельным delta recheck.
- Один browser, семь новых контекстов и explicit expected users/roles; fixtures и fixed origins ограничены owned realm. TMA helper использует принятый own selector в RAM; signer/initData не сохраняются и native Telegram provider success не заявлен. Новый own token/storage private output явно отделён от recovery archives.
- UI confirmation ограничен original UUID, password/digest/group scope; повторные confirm/resolve/business writes блокируются. Original UUID записывается перед forward, дальнейший статус только GET; неизвестный результат не запускает finally logout/clock restore/reconfirm. Admin cleanup последний. F1 требует довести заявленную durability/barrier до всех Auth мутаций, а не расширить их бюджет.
- PWA cleanup применяет реальный cookie-only native fetch и own context metadata без URL-filter Secure-cookie false negative; bearer-only WEB/TMA проверяют exact header. canonical current SID/user и same-bearer401 после acknowledged204 отделены от SQL revocation. Нельзя объявлять group COMMIT отзы́вом SID. При unknown сохраняются contexts/handles, дальнейших cleanup запросов нет.
- Scope/data preservation SQL/Mongo supplement и пассивные шесть views выбраны по цели; старый WEB reference не объявлен all-three PASS. Existing55client/20boundary проверки не пересчитывались как runtime acceptance. Недостатки F2/F5 относятся к конкретной достоверности новых oracle.

Все найденные существенные P1/P2 приведены выше одним packet. Никакой actual delete, Auth, cleanup, DB write или resource/capacity изменение reviewer не выполнял.
