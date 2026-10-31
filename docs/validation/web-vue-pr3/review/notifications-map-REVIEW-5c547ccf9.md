# Independent notifications + map review

**Notifications43440e419: CHANGES REQUIRED — 2 × P2.**

**Map5c547ccf9: CHANGES REQUIRED — 1 × P2.** P1 не найдено.

## Scope и независимость

Notifications — immutable `43440e419726c33312c273b074f9482efc21f431`: store/cards/host/history/actions/tests и узкие owned shell/registry/student-homework-deeplink changes. Map — immutable `5c547ccf906bc163b9666e0f44b74865a5f3c7fa`: шесть файлов viewer/state/hook/tests/styles/registry. Auth/profile26 не переоткрывались; shared API/UnreadState/MutationQueue/STOMP/MapClient прочитаны только как зависимости контракта.

Checkout `/workspace/rct-worktrees/web-foundation` во время review продолжил движение до205ddcbf3. Перед causal checks scoped diff подтвердил, что notifications logic/cards/history равны434, map-state/use-map равны5c. Поздние69f dev-server и205 explicit map labels не входят в этот review; выводы относятся к frozen commits и не смешивают их с будущими corrections.

Reviewer read-only: без source edits/commits/install/browser/backend publications/asset inventory mutations/Figma/Gradle/CI/children. Создан только этот новый immutable report. Запрошенная модель/effort gpt-6.1-sol/high; независимой runtime telemetry нет, фактическая модель не утверждается.

## Notifications P2 — refresh первой страницы оставляет устаревший cursor и теряет часть истории

Место: `/workspace/rct-worktrees/web-foundation/frontends/web-vue/src/features/notifications/notifications.ts:109`.

`nextCursor` принимается из latest first page только при `!initialized`. При live/reconnect refresh первая страница объединяется со старыми items, но cursor остаётся от первой исторической загрузки. Если за время отсутствия/задержки пришло больше одной страницы новых событий, между latest first page и прежним initial page возникает неполученный промежуток. `more()` продолжает со старого cursor, перескакивает промежуток и может закончиться null; пользователю недоступна часть настоящей истории.

Causal factory harness с минимальным серверным cursor model, pageSize2 (тот же случай для production pageSizeN и burst>N): initial server `[a,b,c,d]`, загружены `[a,b]`, cursor=b. Затем server `[x,y,z,a,b,c,d]`, live page `[x,y]` возвращает cursor=y. Store его игнорирует, запросы получаются `[first,first,b]`; после `more()` загружены x/y/a/b/c/d, **z отсутствует, nextCursor=null**. Ни последующие first-page refresh, ни more уже z не получают, пока store не сброшен.

Исправление: rebase/согласовать pagination с последним first-page cursor либо дочитать gap до ранее известного anchor, сохраняя id dedup и writeRevision guards. Не заменять это загрузкой всей БД одним запросом. Нужна одна targeted reconnect/burst case, не повтор существующих toast/ACK тестов.

## Notifications P2 — после неизвестного read-state результата UI не даёт запустить reconciliation

Место: `/workspace/rct-worktrees/web-foundation/frontends/web-vue/src/features/notifications/NotificationsPage.vue:43`; model recovery — `notifications.ts:268`, disabled control — `NotificationCard.vue:70`.

При lost response и неудачном/неподтверждающем readback model выставляет uncertain=true. Mark buttons всех cards и mark-all disabled. Model экспортирует `reconcile()`, но ни history, ни bell/toast host его не вызывает: в UI только текст «Проверь уведомление…». `load()` также не снимает uncertain. Даже после восстановления сети и подтверждённого сервером результата пользователь не может выполнить предусмотренную проверку; все read-state действия остаются заблокированы до пересоздания admitted store/logout-login.

Адресный exact-factory harness: `setReadState` бросает TypeError до admission, readback возвращает прежнее readAt=null → uncertain=true, exported reconcile=function, notice требует проверки. Полный поиск по notifications source подтверждает отсутствие UI вызова reconcile. Позитивный lost-ACK receipt автора покрывает **успешный** немедленный readback, поэтому эту ветку не проверяет.

Исправление: сделать явное доступное recovery действие для uncertain state и связать его с model reconciliation/status. Сохранять запрет blind retry; если актуальное состояние не подтверждает исходное изменение, нужен честный разрешимый выбор текущего состояния/новой явной команды по правилам CAS, а не тихий повтор старой записи.

## Map P2 — refresh того же плана прерывает незавершённый второй формат и больше его не загружает

Место: `/workspace/rct-worktrees/web-foundation/frontends/web-vue/src/features/map/map-state.ts:85` (abort:56, parallel assets99–118).

`loadFloor(true)` сначала abort предыдущего floorController. Early-preserve branch затем считает план полностью пригодным, если существует **любой** preview и нет assetErrors. Когда SVG уже пришёл, а ready PNG ещё в полёте, live/focus refresh abort PNG. Новая загрузка видит тот же plan/готовый SVG и выходит из branch без повторного получения PNG. Старое завершение PNG отклоняется sequence guard, abort error намеренно не добавляет assetErrors. Итог: DTO png.state=ready, PNG отсутствует в assets, error пуст, floorLoading=false; download PNG недоступен, а кнопки retry failed assets нет.

Causal exact-state harness: оба formats ready; SVG resolve сразу, PNG deferred. После появления SVG выполнить same-plan `state.load()`, затем завершить старый PNG. Результат `pngRequests=1`, `exposedFormats=['svg']`, `assetErrors={}`, `floorLoading=false`, plan.png.state=ready. Fake client игнорирует abort, но старый result всё равно корректно отклонён sequence; реальный abort даёт тот же отсутствующий формат.

Исправление: preserve должен учитывать полноту всех ready formats или независимо продолжать/перезапускать отсутствующие форматы, сохраняя готовый preview URL/zoom/focus. Не отменять незавершённую sibling загрузку с последующим complete early return. Достаточен один partial-formats + same-plan refresh check.

## Рассмотренные границы без дополнительных findings

Notifications: captured identity/generation/role/context guards защищают protected reads и финальный route commit. Queue сериализует mutations; writeRevision/readRevision отбрасывают старый inbox после read ACK, UnreadState — старый count. Unknown branch не отправляет blind повтор. Realtime использует общий существующий invalidation bus; preferences appPush/mute соблюдаются без нового channel UI. Toast хранится отдельно от истории и истекает примерно за10s; retained cards не удаляются вместе с toast. Scope dispose очищает interval/items/toasts/count owner. Protected deeplink читает target до navigation; изменён только адресный homework date/hash/focus consumer.

Map: selected floor sequence/AbortController подавляют поздние старые plan/assets; Blob URL создаётся только после current check, освобождается при смене floor/version/dispose. PNG используется при SVG network failure, оба native downloads используют локальные проверенные assets; click guard проверяет owner/url. Viewer имеет keyboard zoom/pan/reset и native scroll, mouse drag, zoom continuation revision guard. Видимый decoded preview вызывает один own-open intent; неизвестный analytics outcome не переотправляется сам по себе. Fake rooms/legend из отсутствующих DTO не добавлены. Новый finding касается completeness при same-plan refresh, не общей race-защиты floor switch.

Registry/shell: notifications route скрыта из nav, native bell в общей shell, карта доступна существующим четырём signed roles. Accepted AUTH/profile scopes не оценивались повторно.

## Проверки, receipts и пределы

Собственное: read-only source/dependency inspection; scoped equality к frozen source; `git diff --check 43440e419^ 43440e419` и `git diff --check 5c547ccf9^ 5c547ccf9` — exit0. Лёгкие dependency-local Node/TypeScript+realVue source-causality harness без backend и записи source: notifications cursor/recovery, map partial formats. Первичный cursor prototype уточнён до серверного cursor model с реально существующим промежуточным z; финальный результат выше относится к уточнённому fixture. Это адресные воспроизведения конкретного риска, не новые сохранённые mirror tests или повторная full-suite кампания.

Прочитаны frozen notification/map tests, без повторного запуска: existing coverage защищает old inbox vs ACK/count,10s toast/history, stale floor/authority/assets/dispose и PNG fallback/unknown analytics. Новые приведённые gaps этим coverage не закрыты.

Внешнее `/workspace/rct-pr3-runtime/web-foundation/notifications-live.json` прочитано как **авторское** реальное evidence434: две owned publications,7STOMP MESSAGE, simultaneous toasts/expiry с retained history, read-state200lost ACK/readback без повтора, unread/mark-all200, protected homework deeplink/focus, bell Enter/Escape,300/768/1440 root16/root20, errors[]. Оно не выдано за собственный browser PASS и не покрывает burst gap или unreachable uncertain recovery.

Map actual gate на момент задания выполняется автором; reviewer не делал HTTP/view analytics/browser и не менял9091 assets/inventory. Реальная визуальная/keyboard/download/same-version-focus приёмка карты остаётся за отдельным runtime receipt/root. Никаких утверждений о полном browser compatibility или всех viewport/theme combinations этим source review нет.

Нужны две scoped notifications corrections и одна scoped map correction, затем независимый narrow recheck. Два verdicts разделены: завершение одного не требует повторного полного review другого.
