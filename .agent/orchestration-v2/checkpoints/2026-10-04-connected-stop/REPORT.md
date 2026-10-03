# STOP — Telegram, WebPush, восстановление приложения — 2026-10-04

Остановка выполнена по явному запросу владельца. Все8 дочерних агентов completed; девять приложений/Bot/Nginx gracefully stopped21:30:15.109Z. Все26 контейнеров сохранены, source/targetDB6 и freshRabbit/Redis running. Public tunnel закрыт21:13:35Z, прежняя кнопка бота восстановлена/readbackMATCH21:13:34Z; exact public process check21:20:09Z remaining0. Backup/keys/артефакты/сети не удалялись. Push/deploy/чужие процессы не затрагивались.

Main a4816865, baseline b2853288. Отдельный coherent frontend WIP e39786b0 сохранён в codex/webpush-connected-1003 / .agent/worktrees/v2-runtime-build, tracked clean. Продолжение только после GO. Чужие dirty AGENTS/RULES/docs/harness сохранены; checkout целиком clean не объявляется.

## Сделано для пользователя

1. Тестовый Telegram-бот привязан через API к тестовому аккаунту. Владелец получил welcome и два различных уведомления о переименовании группы. Подтверждена отдельная системная доставка WebPush Chrome/Edge, а не только строка на странице.
2. Исправлен WebPush boundary: AES128GCM, успех только для2xx, 404/410 удаляют истёкшие подписки; ответ освобождается, чувствительные endpoint/exception исключены из delivery logs. Это не обещание exactly-once внешней доставки.
3. Исправлена ошибка пустой истории уведомлений: пустой HAL без `_embedded` допустим только при валидных нулевых totals; неверный/непустой ответ остаётся ошибкой. Реальный PWA login → notifications/settings принят.
4. Настоящий Mini App прошёл signed login и student authority bootstrap. Реальные Today/HW/statistics/attendance/profile запросы200, rolePUT200; владелец подтвердил работу студента. Schedule и полное close/reopen отдельно не подтверждены. Публичные404 старосты относятся к student-only test edge. Локальные PWA Today/journal старосты загрузились без404; недели и пять форматов отчёта показаны, скачивание не выполнялось. Полная TMA всех ролей не принята.
5. Реальное восстановление приложения завершило исходный pending перенос после восстановления DB и замены брокера: ДЗ следует новой паре, прежняя посещаемость и история сохранены; один точный повтор исходного запроса не изменил данные и не создал дублей.
6. Кнопки роли на главных студента/старосты подключены к существующему выбору роли в отдельном source-ready коммите; Back возвращает на исходную главную. Это ещё НЕ принятый live результат: typecheck/review/integration/UI остаются.

Основная изменённая логика WebPush в main:
```java
if (status < 200 || status >= 300) {
    throw new HttpResponseException(status, "Push provider rejected notification");
}
deliveredEndpoints.add(sub.getEndpoint());
```
Ответ освобождается через finally; только принятые provider endpoints участвуют в обновлении last_seen.

## Файлы

Полный точный Git inventory: [FILES.tsv](FILES.tsv). Main15 уникальных source/test/config/tooling файлов:4 созданы/11 изменены/0 удалены. WIP e39786b0 меняет ещё7 existing файлов. Всего22 с учётом WIP, но не22 принятых функций. Root docs/evidence отдельно.

Продуктовая логика: WebPushDeliveryService.java, TicketHandshakeInterceptor.java, notifications-client.ts. Existing tests: WebPushDeliveryServiceTest.java, PushLibraryCompatibilityTest.java, TicketHandshakeInterceptorTest.java, notifications-client.test.ts.

Созданы: scripts/telegram-test-edge.mjs, scripts/telegram-test-edge.test.mjs, scripts/test-application-recovery.py, scripts/test-application-recovery-checks.py. Изменены tooling/config: scripts/recovery.py, docker-compose.e2e.yml, docker-compose.test-restore.yml, tests/e2e/.env.ci. Исправления стенда не считаются новыми пользовательскими функциями.

WIP7: TodayScreen.vue, today-screen.pcss, HeadmanHomeScreen.vue, HeadmanScheduleScreen.vue, StudentFeatureOwner.vue, navigation.ts, navigation.test.ts. Root STOP docs: CURRENT.md, SLOTS.md, CONNECTED-ACCEPTANCE-20261003.md, metrics/packages.csv, этот REPORT и FILES.tsv. Секретные файлы внеGit, не включать в checkpoint commit.

## Приёмка и ограничения

- Независимые affected reviews provider/config/edge/native helper пройдены; адресные provider/emptyHAL/edge checks пройдены. Полные suites ради отчёта не повторялись.
- Native restore: начальный exit1 сохранён; после исправления timezone/эквивалентной PG serialization точная native schema/data/Mongo validation49.37s PASS. Намеренно изменённый CHECK отвергнут, scratchDB0, архивы не применялись повторно. Проверка использует настоящий PG parser/pg_dump, не regex ослабление.
- Application acceptance20:23:28Z,8.47s: COMPLETED, outboxsent3, participantreceipts2, Academicreceipt1, HWhistory1, pastmark1, Studenthistory2; один exact accepted replay, состояние стабильно. ACK .agent/evidence/telegram-recovery-retry-1003/application-recovery-final-ack.json, SHAafa9c2c083be8a699b0669f7f1ec21aa66a4655b34a4ed0606f7ccd49ae69b49.
- WIP role navigation5PASS/diff-checkPASS. Vue typecheck unresolved: разрешение sockjs-client/vite типов окружения; ограниченные попытки остановлены, install/stubs/lockfile не менялись. Review/build/runtime дляWIP не выполнены.
- Истёкший JWT не используется как доказательство отзыва сессии после restore. DB fixture не доказывает восстановление настоящих objectstore assets/offsite. ws-ticket409 рядом с200 при сменах роли наблюдался; причина не установлена.
- Screenshot локального журнала: .agent/evidence/telegram-recovery-retry-1003/pwa-headman-journal-local.jpg; emptyhistory: pwa-empty-history.jpg там же.

## А–Ж и весь известный остаток

Это инженерный ориентир реализации ±5–10п.п., не измеренный accepted/total и не production-ready формула. Сохранён прежний смешанный состав пакетов: frontend перенос в следующий этап не повышает проценты. Полный denominator147 историй заново не аудировался.

| Пакет | Перед итерацией | STOP | Реализовано и что осталось |
|---|---:|---:|---|
| А Семестры, архив, удаление |95%|95%|Серверный lifecycle, archive edit barrier, деактивация/восстановление, завершение прерванного удаления приняты раньше. Остался полный административный UI цикл и все связанные состояния. Узкая сверка нового server-only дефекта не выявила; это не новая полная приёмка147 историй.|
| Б Доступ, администрирование |95%|95%|Roles/session/recovery/OTP/QR API и restore account безправ реализованы. Теперь реальный Telegram auth/link подтверждён. Остались firstlogin/password/recovery/accountswitch UI, изменения статуса в открытой сессии, source-ready headerrole WIP, все роли настоящегоTMA.|
| В Учебный процесс |92%|92%|Recurring lifecycle, перенос/отмена/восстановление, journal/geo/server rules, ДЗ/заявки и consumers реализованы. Recovery подтвердил сохранность связанной операции. Остались полные клиентские пути ролей, реальные гео/вложения/частичные отказы/retry/пустые и архивные состояния.|
| Г Расчёты, выгрузки |92%|92%|Единые расчёты/фильтры/рейтинги, weekly/subject/roster и пять форматов реализованы. Остались все client download contexts, фактическое сохранение файлов, читаемость/device/TMA пути. Новые генераторы сегодня не добавляли.|
| Д Карты |92%|92%|Серверные публикация/замена/ACL/views и окончательное удаление версий реализованы; PWA edit/reload/preview приняты раньше. Остались полный клиентский цикл/отсутствующий и заменённый план, finaldelete UI, TMA/Figma.|
| Е Уведомления, внешние каналы |85%|92%|Durable preferences/audience/history/revocation реализованы; теперь реальная Telegram/WebPush доставка, исправления provider/пустой истории, studentTMAbootstrap. Остались все типы/получатели, reconnect/deeplinks/перевод группы, optout/accountswitch на реальном устройстве и полныйTMA ролей. Best-effort provider не durable exactly-once.|
| Ж Оболочка, эксплуатация |55%|70%|Принято native+application recovery исходногоpending transfer. Остались постоянный стенд/persistentvolumes/все роли; PWA install/update/offline/cache/platform; production config/secrets/domain/TLS, migrationrollback, monitoring/alerts/load/releasegates. Offsite/retention/RPO/RTO отложены владельцем доdeploy. Сам deploy отдельно разрешается.|

Общий функциональный ориентир около90%, диапазон85–95. Публичный production не принят; точный общий release процент без checklist не рассчитан. Проценты пакетов не усредняются; тесты/файлы не являются формулой готовности. А–Д server implementation близки к завершению, но готовый backend source не равен готовомуPWA/TMA.

## Продолжение после GO и ускорение

1. e39786b0: одно независимое source review/typecheck в полноценном existing окружении → интеграция готового → одна совместная frontend build/bounded roleBack UI.
2. Постоянный локальный teststand с persistentvolumes, данными всех ролей и сохранёнными подключениями; reuse8JAR/bundles, secrets внеGit.
3. До внешнего теста фиксировать все необходимые зависимые bootstrap APIs/методы. Student-only edge не обслуживаетHeadman; нужно отдельно согласованное all-role test connection.
4. Frontend/Figma по независимым ролям/экранам с3–5 исполнителями, одним владельцем общих компонентов/navigation/session. Независимое review прав и межсервисных изменений сохранить; базовый UI не создавать новую mock/integration/browser триаду.
5. Одна приёмка совместимых изменений, достаточные affected checks; принятые unchanged provider/restore не повторять. После повторной одинаковой harness ошибки менять подход/сохранятьWIP, не расширять тестовый проект.

Факты времени: одна frontendbuild28.3s, reuse8JAR+botimage; initialREADY4m29s, restored6cores111s; native49.37s/app8.47s. Полного timeledger нет: процент экономии и сравнение Luna/Sol не доказаны. Существенное время ушло на нашу конфигурацию: TLSservername/staleproxyIP/PGtimezone-DDL/неправильныйrolecontext/edgeallowlist-method/type dependencies. Это стоимость работы, а не новые функции. Основной резерв — меньше пересозданий и ручных handoff, стабильный интеграционный стенд. Дополнительные агенты полезны только для независимых ownership scopes.

## Сохранность и запуск завтра

STOP ACK .agent/evidence/telegram-recovery-retry-1003/user-stop-final-ack.json, SHADD7A6490196965969FEE3AD719A149AB5A8FBF4242C72D66588DDF293058AB3B. Private native bundle/capture: C:/Users/maksd/AppData/Local/Temp/rct-application-recovery-1003-20261003-184420960-xp9jpqsx. Код, evidence, images и готовые bundles сохранены. Public permission истекло; новые внешние соединения требуют нового разрешения. Пользовательский browser оставлен.

LiveDB сейчас tmpfs: при остановке DB/перезапускеDocker потеряются их текущие данные. Долговременная контрольная точка — nativebackup/capture и код/артефакты; не обещание вечной сохранности живых контейнеров. Не удалять backup, ключи, сети, контейнеры. ПослеGO сначала выбрать сохранённый baseline и ресурсы; не запускать заново все принятые проверки.
