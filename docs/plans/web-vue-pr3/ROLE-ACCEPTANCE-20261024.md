# Приёмка ролей — 2026-10-24

Промежуточный срез существующего PR3. Собранный frontend revision
`0421d344c9697d58efc8f039b74c5bdf594c6eb4`; исходный знаменатель117 active
сохранён. **Implemented116/117 (99,15%); locally verified113/117 (96,58%).**
Реализация означает принятый после независимого review исходный код.
Локальная проверка означает достаточное подтверждение пользовательского действия
на настоящем изолированном backend вместе с адресными проверками поведения.
Темы, ширины, assertions и screenshots не увеличивают число действий.

| Роль | Active | Implemented | Locally verified |
|---|---:|---:|---:|
| AUTH | 6 | 6/6 (100%) | 6/6 (100%) |
| SHARED | 15 | 15/15 (100%) | 15/15 (100%) |
| STUDENT | 16 | 16/16 (100%) | 16/16 (100%) |
| HEADMAN | 38 | 38/38 (100%) | 38/38 (100%) |
| TEACHER | 8 | 8/8 (100%) | 8/8 (100%) |
| ADMIN | 34 | 33/34 (97,06%) | 30/34 (88,24%) |

## Что подтверждено

AUTH6, общий профиль/история/аватар/пароль/сессии, роли/logout и устройство
light/dark/system приняты ранее. Добавлены bell/history/read-state, накопление
временных уведомлений около10s, reconnect и настоящий map browse/download.
Каналы web не возвращены. После отзыва делегированных прав обновляются меню
и уже открытый private subtree; API403 не подменяется сохранёнными таблицами.
Одинаковый grant при повторном чтении сохраняет draft/DOM/focus.

STUDENT: Today/расписание, собственная посещаемость/причины, статистика предметов
и рейтинг с реальной пагинацией, запросы/отмена/вложение, прежние два действия
ДЗ и PWA banner. EXCUSE unknown result восстанавливается тем же намерением,
без слепой новой записи. После исправления live refresh обычный hover/focus
не блокирует получение данных; выбранные типы, намеренно пустой фильтр и focus
сохраняются, dirty draft откладывает замену данных до закрытия.

Нативная заявка STUDENT15 на Late53 принята одной командой200, затем HEADMAN12
одобрил её одной командой200. Сервер подтвердил APPROVED/decisionBy12 и
ABSENT/auto_scheduler → PRESENT/late_checkin. Реальный STOMP вызвал обычные REST
reads в двух открытых студентских представлениях: решение и отметка появились
без refresh, navigation, injected events или новой команды студента. Focused
mark остался тем же DOM. Metadata53, весь64, отметка12 и остальные участники53
не изменились. Положительная GEO15/64 позже принята отдельно; её native receipts доступны в PROGRESS.

HEADMAN38: журнал и отдельные отметки/audit/geo-block, отмена/восстановление/
перенос/аудитория, очередь и решения/вложения, состав/помощники/CAS/revoke,
credentials/recovery/budget, предметы/преподаватели/удаление с proof, регулярное
и разовое расписание, ДЗ с историей, статистика/trend и три вида экспорта.
Проверены настоящие foreign-student decisions, cross-client live update и
PDF через реальный LibreOffice. Старое воспроизведение delegated view defect
сохранено отдельно от исправленного runtime PASS.

TEACHER8 приняты ранее: назначенная группа **и** назначенный предмет, только
разрешённые чтение/экспорт. Новых прав преподавателя не добавлено.

ADMIN30 подтверждены на настоящем стенде: dashboard/activity, реестры и фильтры,
создание/редактирование/роль/перевод/архивация/восстановление пользователя,
recovery, группы/староста/история/архивация/восстановление, семестровые операции
включая активацию, схемы кроме final delete, посещаемость и экспорты.
Проверены потерянный201 → явное продолжение того же create → volatile receipt,
потерянное принятие export → тот же key/selector/job и GET-retry известного job
после потери download; изменения фильтров не создают другое намерение.
Переименование открытой группы использует актуальное имя при последующей
архивации; восстановлено исходное имя и состояние own fixture.

ACTIVATE подтверждён отдельно на настоящем Auth/Academic/Gateway с новыми
синтетическими БД: create201, один activate200, uniqueactive2, предыдущий1
сохранён кроме деактивации, outbox1+1. Raw composite postread exit1/false
сохранён: отсутствует Attendance activity. Независимый source/evidence review
принимает действие по JS-ADMIN-09, без общего health или async-delivery PASS.
Fresh ADMIN selection после исправления0421 снова проходит canonical home;
dashboard metrics этого небольшого realm частично недоступны и не приняты.
[Новая приёмка и ограничения](../../validation/web-vue-pr3/checkpoints/role-semester-20261024/manifest.json).

## Четыре оставшихся action gates

| Action | Source | Недостающее подтверждение / зависимость |
|---|---|---|
| ACT-ADMIN-ACCESS | implemented | Настоящая запись/отзыв прав. Read-only USERS_VIEW-only lookup и cancel/draft проверены; попытка отдельного privileged fixture отклонена approval review и не выполнялась. |
| ACT-ADMIN-REGENERATE-PASSWORD | implemented | FULL/self и unknown-result сценарий own8 ожидает ответ Q-ENV-003 после отказа approval review;8 заморожен. |
| ACT-ADMIN-MAP-DELETE | implemented | Whole-version/BYTEA purge, nullable scope, API+WEB reviewed и объединённые сборки PASS. HTTP/PostgreSQL использует Auth stub; full actual Auth+WEB NOT RUN. Auto-review отклонил запуск до исполнения; Q-ENV-007 ожидает владельца. Основной Academic950 не обновлён. |
| ACT-ADMIN-GROUP-FINAL-DELETE | blocked | Q-PROD-004: судьба membership и grants участников. Аккаунты не удаляются; решение владельца не выдумывается. |

Проценты не включают глобальное завершение поставки: пока остаются активный
Angular, подготовка Vue image/static serving и переключение build/oracle wiring,
сборка итоговой runtime HTML-галереи и итоговые проверки изменённых границ.
Удаление Angular последует после замены принятых возможностей. Эти задачи
нельзя скрыть за116/117. CI/Coverage/Security/OpenAPI **PAUSED**, не PASS.

## Независимое review и настоящие artifacts

Новые immutable reports доступны в [review manifest](../../validation/web-vue-pr3/review/manifest.json):
HEADMAN3ff/e4/905, ADMIN6dd/3d, notifications-map5c/857,
STUDENT324/757 и common role-home/delegated-boundary a075/39.
Начальные P2 сохранены рядом с исправляющими rechecks.

Стенд13healthy, отдельные synthetic fixtures, queues/DB/volumes проекта
`rct-pr3-setup`. Auth sourcef3c03bd81, immutable SHA256
`32bda499a73a5368d64126fbcba49e378d5e81ea349a4b20f7f671c1357c4035`;
Academic source950e621b0, immutable SHA256
`fa360caa53e5710da9225e3a6a328da7e613992e97bbc49b8b78256a3e54db6c`.
CAS/GroupOptions950 реально развернуты. Gateway source3c02,
Schedule/Attendance/BFF60346b26 подтверждены внешними source-matched receipts
(у последних нет встроенного Git SHA; это явно не embedded provenance).
Map usage dedicated HMAC настроен только во внешнем приватном env-file;
первое открытие204 увеличивает счётчик0→1, replay204 оставляет1.
PNG/SVG assets и inventory при этом не удалялись.

Исходные безопасные receipts, точные paths/hashes и ограничения собраны в
[role checkpoint](../../validation/web-vue-pr3/checkpoints/roles-20261024/manifest.json).
Private sessions/credentials, recovery fragments, capability URLs и plaintext
command keys не публикуются. Галерея содержит оригинальные runtime PNG;
Figma/reference отдельно, не доказательство реализации.

Shipping preparation original4445 → root820d8fe99 independently reviewed PASS: exact avatar catalog moved into shared consumer, Vue image/static config added, PWA/TMA workspace COPY inputs corrected. Final assembled strict WEB build PASS at820. Actual image build/nginx HTTP and production-wiring replacement remain pending; Angular has not been removed. [Inventory](ANGULAR-REMOVAL-INVENTORY.md) documents exact remaining active references and unchanged PWA WebPush chain.

Actual shipping images/inner nginx checks now PASS atsourcea813; initialPNG403 и VFS/disk incident сохранены в [shipping checkpoint](SHIPPING-CHECKPOINT-20261024.md). Outer wiring/Angular removal остаются. Added ADMIN24 render gallery exact634cd и real canonical home FULL1→dashboard/USERS_VIEW17→users, no access writes.

PROMOTE принят после явного Q-ENV-006: один UI200, курс1→2, ровно1ledger, повторный preview ALREADY_PROCESSED. Own6 архивирована обычным API, цикл5/ledger сохранены; активный2 не менялся. [Приёмка, квалификации фоновых изменений и cleanup](../../validation/web-vue-pr3/checkpoints/promotion-map-20261024/promotion-acceptance.json).
