# PROGRESS — Vue web redesign

Существующий draft [PR №3](https://github.com/DITEKmax/rutcampustrack/pull/3),
ветка `codex/web-vue-redesign`. Goal запущен владельцем после readiness и
продолжается. Прежний собранный frontend source `0421d344c`; текущий интегрированный frontend source `a851d5cf7`, backend map source
`1fe1588dd`, release-input source `8e280baef`. Всегда получать актуальный remote HEAD.

**Implemented 116/117 (99,15%). Locally verified 116/117 (99,15%).**
Исходные117 active действий сохранены. Темы, ширины, кадры и assertions
не считаются отдельными действиями. Полная замена Angular ещё не завершена.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH |6|6/6 (100%)|6/6 (100%)|
| SHARED |15|15/15 (100%)|15/15 (100%)|
| STUDENT |16|16/16 (100%)|16/16 (100%)|
| HEADMAN |38|38/38 (100%)|38/38 (100%)|
| TEACHER |8|8/8 (100%)|8/8 (100%)|
| ADMIN |34|33/34 (97,06%)|33/34 (97,06%)|

После явного разрешения Q-ENV-006 добавлены ровно три historical inputs:
SPRING2025, собственная пустая группа6 и managed coverage. Один обычный UI
PROMOTE вернул200, исходная операция SUCCEEDED, курс1→2. Повторный preview
показал ALREADY_PROCESSED; второй execute не выполнялся. Независимое чтение
подтвердило ровно один ledger. Группа затем архивирована одним обычным PUT;
семестр5, ledger и audit сохранены, active2 не менялся. Архивное имя с суффиксом
«(выпуск 2026)» сначала не совпало с ожиданием harness; результат разрешён
через исходную операцию и независимый postread, без повторной записи.
Оставшаяся собственная тестовая сессия отозвана обычным Auth API, новая закрыта;
удержанный процесс и девять потомков завершены. [Приёмка и ограничения](../../validation/web-vue-pr3/checkpoints/promotion-map-20261024/promotion-acceptance.json).

Фоновые изменения Attendance при закрытии пары произошли до PROMOTE. Исходные
расхождения сохранены и квалифицированы; равенство всех БД не заявляется.
Protected53 и собственная GEO15/64 сохранились; main13 остались healthy.

Полное удаление схемы принято локально: один whole-delete200 на настоящем
изолированном Auth/Academic/Gateway удалил две версии и четыре PNG/SVG;
21 независимая SQL-проверка подтвердила очистку графа и сохранение корпуса,
этажа, usage, high-water2 и receipt. Успешное удаление не повторялось.
Дополнительная реальная проверка Auth дала correct204 и wrong403
CURRENT_PASSWORD_INVALID, счётчик0→1 без блокировки; собственная сессия отозвана,
24 независимые проверки authority/map/semester/session прошли. Raw probe
native1/passedfalse сохранён: direct Auth guest403 вместо ожидавшегося401.
Независимое source review квалифицирует403 как правильный отказ этого boundary;
Gateway без Bearer отвечает401. Старые сессии сравнивались по counts/revocation,
без отсутствовавшего per-ID snapshot. Основной Academic950 не заменялся:
полное удаление там отключено до подтверждённого server scope.
[Контракт и совместимость](API-CHANGES.md#api-006--whole-scheme-map-final-deletion-locally-verified).

Ранее приняты native GEO15/64 и Late53→решение Headman12→PRESENT/late,
STOMP/REST в двух вкладках без refresh и потери focus, отзыв открытого private
представления, create/export recovery, helper CAS, names-only archive lookup
и настоящие PDF/HTML/XLSX через Renderer/LibreOffice. Геопровайдер браузера
Playwright симулирован; физическое присутствие не заявляется.
[Приёмка ролей](ROLE-ACCEPTANCE-20261024.md) · [Карта](SCREEN-ACTION-MAP.csv) ·
[GEO receipts](../../validation/web-vue-pr3/checkpoints/native-geo-20261024/acceptance.json).

Fresh-role defect закрыт на `0421d344c`: настоящий login200 → ADMIN200 →
UI admin-access/me200 → `/admin/dashboard`. Принудительное продолжение home
наблюдает текущую authority identity; stale-response fence сохранён, одинаковый
refresh не перенаправляет. Начальный P2 race, исправляющий review и ограничение
старого before observer сохранены. Strict WEB build и actual after PASS,
logout204/guest401/browser closed. ACT-ROLE-SELECT локально подтверждён;
доступность всех dashboard metrics этим случаем не проверялась.

ACTIVATE принят по JS-ADMIN-09 в отдельном `rct-pr3-admin-isolated`: настоящий
Auth, один create201 и один activate200. Независимый read-only SQL подтвердил
единственный active2, прежний1 сохранён кроме activefalse, outbox1+1.
Teaching time2029 изменён только в Academic fixture; Auth/TLS/JWT/browser clocks
реальны. Raw postread **exit1/passedfalse** сохранён: только same7Healthyfalse,
из-за отсутствующего Attendance activity. Независимая квалификация принимает
конкретное действие и обязательные producer effects; общий health, async
consumer/provider delivery и полный dashboard не объявлены PASS.
[Приёмка и исходные receipts](../../validation/web-vue-pr3/checkpoints/role-semester-20261024/manifest.json).

Остаётся group action gate поведения; закрытые пакеты показаны отдельно:

| Действие | Подтверждено | Остаток |
|---|---|---|
| LIMITED ADMIN — локально принято | v2 Users200/Groups403→Groups200; v3 original grant/revoke, idle withdrawal3708ms, actual oldADMIN/session403, Teacher/profile200. Independentpostc7ee profile8unassigned/RV9/Aca9, все4 own sessions revoked, чужие данные сохранены | Закрыто. Raw driver exit1 сохранён и квалифицирован как TEST enum gap; ACL/cleanup не повторять |
| Генерация пароля user8 — локально принято | Sourcec586 integrated7ad6+authority2d integrated5e6: actual self-known5→6 receipt/login, next normal login clears receipt; lostACK6→7 unknown/nooutput/native401/noextraPOST. Independentpost9598 confirms Cred7/RV8/Aca8/profile6unassigned, STUDENTgroup1/other17/seedFULL preserved, all3own sessions revoked | Закрыто. Earlier raw FAIL и3original commands retained; no replay |
| Окончательное удаление группы | Backend/Vue source в отдельных worktrees; SOURCEv4 compile четырёх сервисов PASS; SOURCEv5 независимо закрывает F1–F3, SOURCEv8 closes13 Academic/Auth HTTP/PG/Redis cases (8 corrected+5 reused); Schedule3 closed (2PASS reused+one v10RELEASE receipt fix) | Attendance4 actualMongo PASS after corrected resource admission; all20 boundary cases closed. Root integration/four source-matched JARs and законченный UI-сценарий, в том числе отзыв доступа; разрешение original intent после неверного пароля/устаревшего preview |

[Self-password acceptance](../../validation/web-vue-pr3/checkpoints/remaining-admin-20261024/self-password-acceptance.json) · [Latest qualified ADMIN partial results](../../validation/web-vue-pr3/checkpoints/remaining-admin-20261024/limited-v2-partial.json) · [Academic sourcev8 result](../../validation/web-vue-pr3/checkpoints/remaining-admin-20261024/academic-v8-qualification.md).

Q-PROD-004 и Q-ENV-003/007/008 отвечены; вопросов, ожидающих владельца,
на этом срезе нет. Группа удаляет только своё членство, назначения старосты/
помощников и полномочия. Аккаунты, пароли, прочие роли и другие группы сохраняются;
автоматической архивации/отчисления/выпуска нет. Открытые сессии теряют доступ.
Preview числовой, подтверждение текущим паролем администратора; архивация отдельно.
Неизвестный результат разрешается по исходному намерению. GET404 сам по себе
не разрешает новую команду: добавляется атомарный resolve исходного UUID,
который возвращает принятый original или фиксирует CANCELLED до позднего принятия.

Q-ENV-004 разрешён и выполнен в пределах tests/build. Q-ENV-005 разрешён, но
stop/snapshot/copy/clock для GEO не понадобились и не выполнялись; разрешение
не переносится на другие цели. Main13/clock5 не останавливались и не копировались.
[Вопросы и причины](OPEN-QUESTIONS.md).

Глобальные delivery gates: убрать активный Angular после замены возможностей,
завершить outer edge/source wiring и достаточные проверки изменённых границ.
Vue e2e adapters/oracle и три release-input конфига прошли source review;
исправленный dev static nginx подтвердил6 HTTP-проверок с неизменными правами
хоста и обычным worker. Это не runtime PASS всей поставки. Workflow CI/Coverage/Security/OpenAPI — только
workflow_dispatch, **PAUSED**, не PASS. Без dispatch/merge/deploy/production.

Общий gallery index содержит249 настоящих runtime PNG (portable204 пока исторический snapshot), включая PROMOTE, fresh ADMIN home
и ACTIVATE. Каждый кадр имеет собственную revision и manifest;
Figma/reference не выдаются за реализацию. [Галерея](../../validation/web-vue-pr3/runtime-gallery/index.html) ·
[Review manifest](../../validation/web-vue-pr3/review/manifest.json) ·
[Shipping](SHIPPING-CHECKPOINT-20261024.md) · [Angular inventory](ANGULAR-REMOVAL-INVENTORY.md).

Figma MCP и оригинальные auth bytes доступны. В текущей enforced network policy
revision14 `www.figma.com` отсутствует; последнее прямое скачивание через proxy
давало CONNECT403. Новое разрешение не требуется, ожидается применение настройки.
ZIP полностью проверен:87568617bytes, ожидаемый SHA256,422entries/388PNG+34.

Full production Compose source-model пока не PASS: один readonly config-запуск
остановился на отсутствующей `ACADEMIC_GRPC_TLS_AUTHORITY`. Production значения
не искались, фиктивные bindings не подставлялись. DEV/full и изменённый prod
WEB fragment подтверждены отдельно; deploy и cutover не выполнялись.


## Готовность по отдельным измерениям

- Действия: accepted source116/117; поведение116/117. Это доля действий, не процент всего PR.
- Визуал: конечная матрица33 web-вью +50 мобильных Screen-компонентов, покрытие PWA/TMA отдельно; статусы готово/исправить/не проверено. Базовые пригодные кадры переиспользуются; полный visual PASS не заявлен. Исходный bare-loading и cap3 воспроизведены, затем исправлены77f. Runtime подтвердил четыре toast по10.127/10.089/10.176/9.987s и историю; Student425light/Headman300light/Teacher1440dark skeleton. Эти bounded criteria закрыты; исходные rawFAIL и cleanup limits сохранены, остальные темы/адаптив/состояния не объявлены PASS.
- Angular/build: Angular остаётся активным; финальная очистка и общий Nginx deep-link/static/API/realtime/PWA/TMA/WebPush checkpoint впереди. Ранее проверенные внутренние shipping images и static serving повторно не запускаются без изменённой зависимости.

[Ближайшие пакеты, зависимости и условия закрытия](CURRENT-PRIORITIES.md).

[Новый checkpoint map/ACL/password/visual](../../validation/web-vue-pr3/checkpoints/admin-visual-20261024/manifest.json).
Визуальные before/after имеют разные постоянные ID и revision. Старые204 кадра
переиспользуются с прежними qualifications; новые16 не увеличивают action count.
Academic SOURCEv6 Java/testClasses compile PASS; первый IT остановлен guard без
XML/behavior verdict, не PASS. Повтор обоснован фактическим transient991MB и
обратимым освобождением766MB чистых исторических копий docs в worker worktrees.
БД/очереди/volumes/JAR/cache не очищались, >=2GiB reserve не снижен.

Второй Academic IT сохранил резерв: foreign-group threshold preservation PASS,
12 случаев остановлены на helper duplicate no-store/отсутствующем test pgcrypto.
Только test helper исправлен вv7; повторяются12 failed invocations, успешный
foreign-case переиспользован. Это не full group-action PASS.

Shell восстановлен, source/WIP потерь не найдено. Сохранена переносимая копия165
unpublished commits +11worktrees, без private runtime data. [Сохранность и правила
возобновления](RECOVERY-CHECKPOINT-20261024.md). Три map metadata SHA matches
классифицированы и устранены lossless field projection, staged Gitleaks PASS.

PWA visual пакет:16 actual baseline PNG (Today12 +history4), реальныеAPI/ownlogout204/guest401. Колокольчик/назад/история подтверждены; первоначальный locatorFAIL сохранён. Narrow notification title исправленCSS7877:3actualafter/control5e6,300light/dark без orphan,1440light байтыPNG совпадают с before. [Before/after галерея](../../validation/web-vue-pr3/runtime-gallery/mobile-observed-20261024/index.html). Индекс239PNG/portable204 snapshot; это не доля visualготовности.


## Последний завершённый пакет: LIMITED, live-source и мобильный визуал

LIMITED принят по [qualified v3 receipt](../../validation/web-vue-pr3/checkpoints/remaining-admin-20261024/limited-v3-acceptance.json).
Raw FAIL не превращён в raw PASS: драйвер ожидал401/200, фактический authority
endpoint вернул403. Продукт естественно убрал private таблицу за3708ms без focus/
reload; новый TEACHER получил профиль200. Postc7ee подтвердил профиль8unassigned,
RV9/catalog22/Aca9, неизменённые account/TEACHER/Cred1/Binding1/чужие данные и отзыв
четырёх собственных сессий. Прежние v2/profile6 и helper checkpoints исторические.
Повторять привилегии/очистку не требуется.

Group64f backend +ae546 client +f67 Vue +3e174 retirement интегрированы. Все20
boundary cases переиспользованы, четыре64f JAR сохранены. Root assembled WEB build
наe715 PASS; затем из-за изменённых shared/App зависимостей все3 Vue build
наa851 PASS (strict vue-tsc включён). Подтверждён APP gap natural live delivery: корректный API deny сам по
себе не убирал private projection у idle клиентов.8e83 (интегрированb039) добавляет
точный frozen audience в outbox и скрытый personal hint, active PWA/TMA refresh,
reconnect admission; F1 review обнаружил потерю pending export при transient503,
исправление сохраняет исходный requestId и draft. Полный review +narrow recheck,
55 client cases,6 Notification units,2 actual Academic HTTP/PG/Auth и1 actual Mongo
transaction PASS. Успешные неизменённые18 boundary cases не повторялись.
Дальше matching JARs/real same-realm Notification и один UI delete/open-access flow;
whole group action ещё не принят,117/117 не заявляется.

Gallery249 includes5 controlled TMA +5 compact-nav after shots; actual Auth/data
и logout204/guest401, synthetic Telegram host/theme/signature явно обозначены.
Native Telegram provider не проверен. Profile300 естественно показал bare loading;
c35 loading skeleton reviewed/integrateda851, after2 held-read кадра ещё впереди.
[Мобильные кадры](../../validation/web-vue-pr3/runtime-gallery/mobile-controlled-20261024/index.html).
Число кадров не является процентом visual готовности.
