# PROGRESS — Vue web redesign

Существующий draft [PR №3](https://github.com/DITEKmax/rutcampustrack/pull/3),
ветка `codex/web-vue-redesign`. Goal запущен владельцем после readiness.
Опубликован checkpoint e48b56984; source настоящего GEO — 1aea, текущего
исправления подписи — 43b7, тестовых адаптеров — eca7d06f4. Образы a813 сохраняют
свои прежние точные revisions. Всегда получать актуальный remote HEAD.

**Implemented 115/117 (98,29%). Locally verified 111/117 (94,87%).**
Исходные 117 active действий сохранены. Темы, ширины, кадры и assertions
не считаются отдельными действиями. Полная замена Angular ещё не завершена.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH |6|6/6 (100%)|6/6 (100%)|
| SHARED |15|15/15 (100%)|15/15 (100%)|
| STUDENT |16|16/16 (100%)|16/16 (100%)|
| HEADMAN |38|38/38 (100%)|38/38 (100%)|
| TEACHER |8|8/8 (100%)|8/8 (100%)|
| ADMIN |34|32/34 (94,12%)|28/34 (82,35%)|

Новый законченный сценарий: обычный UI check-in студента15 на ACTIVE64,
один POST200 → PRESENT/STUDENT_GEO, подтверждённый Today и attendance.
Независимое чтение БД подтвердило единственную новую отметку15, сохранность
чужих отметок, protected53, occurrence45rev1 и геозоны. Main13 остались healthy.
Браузерный геопровайдер Playwright явно симулирован на действующую серверную
точку кампуса; физическое присутствие не заявляется. Собственная сессия вышла
с204, контексты и браузеры закрыты. [Acceptance/receipts](../../validation/web-vue-pr3/checkpoints/native-geo-20261024/acceptance.json).
Одной подписью исправлено обозначение незавершённого текущего периода;
типы/ESLint/runtime-чтение и независимое Root review прошли без нового check-in.

Ранее приняты native Late53 → решение Headman12 → PRESENT/late_checkin,
STOMP/REST в двух вкладках без refresh и потери focus, отзыв открытого private
представления, create/export recovery при неизвестном результате, helper CAS,
names-only archive lookup и настоящие PDF/HTML/XLSX через Renderer/LibreOffice.
[Приёмка ролей](ROLE-ACCEPTANCE-20261024.md) · [Карта](SCREEN-ACTION-MAP.csv) ·
[Ранее принятые receipts](../../validation/web-vue-pr3/checkpoints/roles-20261024/manifest.json).

Остаются шесть ADMIN gates: запись прав, PROMOTE, semester ACTIVATE,
FULL/self-password unknown-result, whole-map final delete и group final delete.
Read/preview/scheduling/cancel не засчитываются как эти положительные записи.
Нужны ответы Q-PROD-004 и Q-ENV-003/004/006 после точных отказов automatic
approval review. Зависимые команды не выполнялись и не обходились. PROMOTE
имеет preview409: completed SPRING отсутствует, legacy1.typeNULL сохранён;
подготовленные historical inputs не вставлялись, SQL attempts0.
[Вопросы и причины](OPEN-QUESTIONS.md). Q-ENV-005 больше не нужен для GEO:
stop/snapshot/copy/restore/clock/apps не выполнялись, snapshotFALSE;
собственные пять clock infra-контейнеров остаются healthy и не используются.

Глобальные delivery gates: удалить Angular после замены возможностей,
завершить outer edge/production source wiring и достаточные итоговые проверки.
Vue e2e edge и строгий served-artifact oracle прошли source review;
config0 и восемь harness cases не являются outer-runtime PASS.
Четыре helper-файла исправлены после двух P2; auth/role/config и оставшиеся
create-user/EXCUSE сценарии перенесены на текущие Vue/API-контракты.
Отдельно исправлены семь has-локаторов и CRC тестового PNG; source review и
narrow recheck прошли. Статический DOM probe подтвердил owned/foreign границы,
но не является Vue/SUT browser PASS. Actual private-runner сценарии остаются
итоговым runtime gate. [Source receipts](../../validation/web-vue-pr3/checkpoints/vue-test-wiring-final-20261024/manifest.json).
[Shipping](SHIPPING-CHECKPOINT-20261024.md) · [Angular inventory](ANGULAR-REMOVAL-INVENTORY.md).

[Галереи](../../validation/web-vue-pr3/runtime-gallery/index.html) содержат
201 исходный runtime-кадр. [Единый HTML](../../validation/web-vue-pr3/runtime-gallery/PORTABLE.md)
имеет поиск и закреплённые URL изображений. Reference/Figma отделены.
Mobile: 24 семейства source и 140 сохранённых samples; narrow recheck12 после
двух P2 прошёл. Ранний cleanup exit1 указан отдельно. Signed Telegram и внешняя
Push delivery этим не подтверждаются.

Настоящий main13: immutable Authf3/Academic950/Gateway3c02 и qualified603
Schedule/Attendance/BFF/Notification/Renderer с инфраструктурой. ZIP целиком
проверен: 87568617 байт, ожидаемый SHA, CRC, безопасные пути, 422 записи,
388PNG+34. GitHub MCP, gh/API и Git работают независимо. Figma MCP context,
screenshot и original auth bytes доступны; www.figma.com пока отсутствует
в фактической policy7 и даёт CONNECT403 после обещания владельца применить доступ.
Три imagesa813 и inner-static12/6/4 прошли, но не являются новой сборкой43b7.
VFS diskfull, восстановление exact30 owned-unused cache IDs и initial PNG403
сохранены в shipping receipts; чужие состояния и volumes не потеряны.

CI/Coverage/Security/OpenAPI **PAUSED**, только workflow_dispatch, не PASS.
Без main writes, merge, deploy, production, firewall и global Docker cleanup.
Существующий PWA WebPush сохранён; web channels исключены, свой student export
отложен, пользователи только архивируются/восстанавливаются, teacher grants
сохраняют ограничение assigned group AND subject.
