# PROGRESS — Vue web redesign

Существующий draft [PR №3](https://github.com/DITEKmax/rutcampustrack/pull/3),
ветка `codex/web-vue-redesign`. Goal запущен владельцем после readiness и
продолжается. Текущий проверенный frontend source `0421d344c`, backend map source
`1fe1588dd`, release-input source `8e280baef`. Всегда получать актуальный remote HEAD.

**Implemented 116/117 (99,15%). Locally verified 113/117 (96,58%).**
Исходные117 active действий сохранены. Темы, ширины, кадры и assertions
не считаются отдельными действиями. Полная замена Angular ещё не завершена.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH |6|6/6 (100%)|6/6 (100%)|
| SHARED |15|15/15 (100%)|15/15 (100%)|
| STUDENT |16|16/16 (100%)|16/16 (100%)|
| HEADMAN |38|38/38 (100%)|38/38 (100%)|
| TEACHER |8|8/8 (100%)|8/8 (100%)|
| ADMIN |34|33/34 (97,06%)|30/34 (88,24%)|

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

Полное удаление схемы реализовано в backend, общем клиенте и WEB: все версии,
PNG/SVG и BYTEA, точный scope preview, rollback, high-water версии и безопасный
replay исходного намерения. Реальные HTTP/PostgreSQL проверки3+legacy regression,
четыре бизнес-проверки scope, OpenAPI snapshot/nullable corpus,16 client cases,
12 WEB state cases и независимые reviews PASS. Строгие сборки WEB/PWA/TMA на
`c3c08a935` PASS. В backend IT подтверждение пароля явно stubbed; настоящий полный
Auth+WEB сценарий пока NOT RUN. Основной стенд сохраняет Academic950: новый UI
без подтверждённого server scope отключает полное удаление.
[Контракт и совместимость](API-CHANGES.md#api-006--whole-scheme-map-final-deletion-source-accepted-runtime-pending).

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

Остаются четыре ADMIN gates: запись прав, FULL/self-password unknown-result,
whole-map full UI и group final delete. Q-ENV-003, Q-ENV-007 и Q-PROD-004 ожидают
владельца; user8 заморожен. MAP UI automatic approval review отклонил **до
исполнения**: Q-ENV-004 на HTTP/PostgreSQL checks сочтён недостаточным согласием
на удаление всех новых PNG/SVG через UI. Браузер/login/uploads/delete0;
повторов и обходов нет. Подготовленный reviewed case затрагивает только новую
схему25001/1 в отдельном искусственном realm, не main inventory.

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

Галерея содержит204 настоящих runtime PNG, включая PROMOTE, fresh ADMIN home
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
