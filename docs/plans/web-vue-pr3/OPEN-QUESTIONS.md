# Открытые вопросы — Vue web redesign

Новые решения владельца выше старого реестра и макетов. Молчание не является
согласием; блокируется только зависящий сценарий. Решения Q-PROD-001/002/003
получены от владельца 2026-10-23 (Europe/Moscow).

## Q-ENV-001 — облачный executor

**Тип:** техническая зависимость. **Статус:** RESOLVED 2026-10-24 для новой среды.
Настоящие shell, checkout, Git fetch/gh/API, Docker, core backend/login/data и
Chromium screenshot проверены. См. [READINESS-CHECK-20261024.md](READINESS-CHECK-20261024.md).
Полный «Сегодня» требует расширения минимального стенда после Goal.
Ниже историческая диагностика прежней машины, не текущий блокер.

Figma/GitHub connectors доступны, но `exec_command` отвечает
«No usable execution environment», `download_file` —
«requires a ready execution workspace». Environment status:
desired running, observed pending, connectivity offline, failure null.
Диагностических capture records пока нет.

Вопрос владельцу был отправлен через UI. Ответ: архив прикреплён; назначение
вопроса непонятно, при необходимости можно разместить скрины в GitHub.
Объяснено, что ZIP передан правильно, ожидание относится к машине для checkout/
сборки/браузера, а не Figma или GitHub. Не требуем от владельца переносить
скрины или менять настройки без доказанной необходимости.

Рекомендация: дождаться штатного executor, сохранить checkpoint; продолжать
независимые connector reads и подготовку карты. Срок старта неизвестен.
Не выдавать отсутствие машины за дефект продукта или PASS.

## Q-INPUT-001 — передача большого ZIP

**Тип:** техническая зависимость. **Статус:** RESOLVED 2026-10-24.
Единый файл87568617bytes превышает лимит download_file32MiB; точная ошибка:
`file exceeds the executor transfer limit of 32 MiB`. Владелец передал три
ZIP-обёртки transfer-01/02/03; каждая реально скачана в текущую среду.
Проверены wrapper CRC/safe paths, TRANSFER-MANIFEST/части и бинарная сборка;
исходные size87568617/SHA256 совпали, CRC/safe paths422 entries PASS,
388 PNG сверены по bytes/hash/dimensions с JSON/CSV manifests;34 handoff на месте.
Подтверждены33 logical screens/8 widths и ровно140 ожидаемых narrow light gaps.
Evidence: `/workspace/rct-pr3-setup/archive-check/report.md` и
[READINESS-CHECK-20261024.md](READINESS-CHECK-20261024.md).
Ответа владельца не требуется; это проверка входных данных, не новая реализация.

## Q-ENV-002 — скачивание оригинальных Figma assets

**Тип:** техническая зависимость. **Статус:** owner will allow; network application
pending, original auth asset dependency RESOLVED through authorized MCP reads.
Figma MCP реально возвращает design context/screenshot, но `curl` временного
original asset URL через наследуемый proxy с разрешённой network sandbox
получает `CONNECT tunnel failed, response 403`. Файл политики restricted не
содержит `www.figma.com`. `download_assets` для исходного auth background также
возвращает URL того же host; доступного альтернативного source URL не выдал.

Владельцу отправлен вопрос: разрешить HTTPS `www.figma.com` в используемой
облачной среде. Затронуты original auth background и следующие PNG/SVG assets;
API/shell/theme/другие экраны продолжаются. Screenshot/crop не подменяет
оригинальный asset; CONNECT policy не обходится direct network/отключением TLS.
Ответ владельца: «Разрешу www.figma.com». Повторный source download пока403;
это не подтверждение применённой политики и не новый запрос разрешения.
После ответа/публикации — повторить один bounded source download и сохранить
локальный файл. Текст вопроса/положительный ответ сами по себе не доказывают
применённый доступ.

Обновление2026-10-24: original auth image получен через read-only Figma
getImageByHash/getBytesAsync в ограниченных частях.937127bytes1498×1050,
SHA1 совпадает с Figma image hash2259ca44d4dcff802f9f7e1c9c15512880dbbd99;
SHA2565ecad9bf5b38ddf9fe2114c5ab422cec78ea81c746fc2ee0b2ab5d9b3b6fff16,
PNG chunks/CRC PASS. Это оригинальные bytes, не screenshot/crop и не обход
proxy. MCP SVG export также работает. Повторно разрешение владельца не требуется.

## Q-ENV-003 — конкретная проверка user8 после отказа approval review

**Тип:** разрешение на ограниченную проверку стенда. **Статус:** APPROVED; три credential commands выполнены, UI receipt checks FAIL; mandatory cleanup VERIFIED.
Прямой ответ владельца разрешает `/tmp/admin-users-unknown-review.cjs` только
для собственной синтетической user8 на локальном стенде: временный FULL, генерацию
пароля, потерянный ответ и отзыв сессий. Временное ADMIN назначение обязательно
снять и подтвердить очистку, включая ошибку. Admin1/его пароль/права, другие
пользователи и production не менять. Повторное разрешение не требуется.
История до ответа: automatic approval review отклонил сценарий, который временно выдаёт FULL,
меняет постоянные credentials и проверяет self-regeneration: объём разрешения
на security mutations был недостаточно явным. На том этапе до ответа сценарий не запускался.

Подготовлен проверяемый `/tmp/admin-users-unknown-review.cjs` для исключительно
синтетического user8, созданного самим UI в этом стенде. Guard проверяет marker,
имя/группу/STUDENT, отсутствие активной ADMIN роли и отдельный opt-in. Baseline
admin1 credentials/profile не меняются. Сценарий проверяет actual200 self-reset
с сохранением receipt до logout и потерянный ACK401/unknown result. В finally
предусмотрены точный operation-key readback, отзыв временного FULL только у8,
подтверждение assigned=false и выход из собственной admin browser session.
Секреты, коды и capabilities не публикуются; screenshots отсутствуют.

Владельцу отправлен конкретный вопрос с вариантами «Разрешить этот сценарий
для user8 стенда» / «Не запускать». Молчание не разрешает выполнение.
Текущий runtime gate ACT-ADMIN-REGENERATE-PASSWORD не закрыт: native receipt UI FAIL, source diagnosis. Все временные права сняты/profile4; account/STUDENT сохранены, обе собственные user8 sessions и seed sessions отозваны, independent19post PASS. Остальные
проверки продолжаются на собственных отдельных fixtures без расширения прав.

## Q-PROD-004 — участники окончательно удаляемой группы

**Тип:** существенное продуктовое решение. **Статус:** RESOLVED, policy confirmed.
Последний прямой ответ владельца: при final group delete удалить членство группы,
назначения старосты/помощников и только относящиеся к ней полномочия; предварительный
перевод не обязателен. Аккаунты/логины/пароли/другие роли и назначения/данные других
групп сохранить. Автоматически не архивировать пользователей, не присваивать
«отчислен»/«выпущен». Без иных назначений аккаунт остаётся без доступа к удалённой
группе. Открытые сессии теряют соответствующий доступ. Перед текущим паролем
администратора показать числовой preview данных и назначений. Архивация — отдельная
обратимая операция. Это закрывает policy-вопрос, не подтверждает реализацию.
Финальное удаление группы уже принято: canonical COMPONENT_REGISTRY.md:127,
JS-ADMIN-18: числовой preview, текущий пароль, подтверждённый cascade. Сам scope
не обсуждается повторно. Q-PROD-003 запрещает отдельное окончательное удаление
пользователя. Current GroupService DELETE не реализует безопасный flow и
блокирует managed history; прежний endpoint не задаёт новую policy.

Историческая формулировка до ответа владельца (решена выше): не были определены последствия для текущего membership и student/headman grants
участников удаляемой группы при сохранении accounts/остальных ролей. Нельзя
удалить аккаунты, оставить authority со ссылкой на исчезнувшую группу или
самовольно выбрать новый статус пользователя.

Владельцу отправлены варианты: убрать участие и отозвать права только удаляемой
группы; либо требовать предварительного перевода участников. Затронут только
ACT-ADMIN-GROUP-FINAL-DELETE и его preview/authority/cascade. Другие операции,
включая admin password regeneration, продолжаются. Молчание не является выбором.
Source packet подготовлен read-only по актуальным Academic/Auth/Schedule/
Attendance implementations; reuse existing password step-up/durable participants,
без browser-цепочки subject deletes и без ослабления scopes.

## Q-PROD-001 — настройки каналов уведомлений в web

**Статус:** RESOLVED — web preferences UI исключён решением владельца.
Источник: canonical JS-SYSTEM-11 явно отменяет web channel preferences;
при этом активный Angular route student/notifications/settings существует,
а notifications-v2 API и PWA/TMA settings доступны. Новый GO включает реальные
принятые операции и полезные backend возможности, но запрещает автоматически
возвращать отменённые требования.

Варианты:

1. Только bell/history/in-app overlay; сохранить прежний запрет на web channels.
2. Явно вернуть настройки каналов в новом web с существующим API.

Решение владельца через UI: «Только колокольчик, история и всплывающие».
Принят вариант1. Bell, история, read-state и 10s overlay входят в обязательный
объём; web channel preferences не входят. Источник: ответ в этом чате 2026-10-23.

## Q-PROD-002 — экспорт собственной посещаемости студента

**Статус:** RESOLVED — идея отложена за пределы этой итерации.
Источник: canonical JS-STUDENT-16 помечен как идея владельца, не подтверждённая
обязательная функция; StudentApi и report kinds готовой операции не содержат.

Варианты:

1. Сохранить отдельной идеей за пределами обязательного объёма этой итерации.
2. Включить сейчас, определить формат/период/поля и совместимое backend API.

Решение владельца через UI: «Оставить отдельной идеей на потом».
Принят вариант1. Новый student attendance export не входит в обязательный
объём этой итерации. Чтение посещаемости/статистики и существующие exports
старосты/преподавателя/администратора сохраняются.
Источник: ответ в этом чате 2026-10-23.

## Q-PROD-003 — окончательное удаление пользователя

**Статус:** RESOLVED — отдельное окончательное удаление пользователя исключено.
Источники: canonical JS-ADMIN-18 (knowledge/job-stories.md:281) упоминает
группу/семестр/пользователя; более точный COMPONENT_REGISTRY.md:127 описывает
сквозной destructive flow для группы и семестра. Решение
2026-10-02-user-restore-without-rights.md сохраняет JS-ADMIN-04 как обратимую
архивацию пользователя с посещаемостью, заявками и историей.
UserApi DELETE /users/{id} означает archiveUser, а не distinct final deletion.
Готового отдельного secure protocol удаления пользователя не обнаружено.

Варианты:

1. В этой итерации пользователи имеют архивацию и восстановление.
2. Включить также окончательное удаление пользователя с preview/proof/durable
   completion и точным сохранением/удалением связанных данных.

Решение владельца через UI 2026-10-23: «Только архивация и восстановление
пользователей». Принят вариант1; distinct final user deletion не входит
в обязательный объём этой итерации. Существующие архивация/восстановление
с сохранением истории реализуются. Это явное решение по scope, а не вывод
из отсутствия API.

## Разрешённые вопросы без обращения к владельцу

- Teacher read/export scope определяется D023: текущая разрешённая группа AND
  назначенный предмет; запись отметок преподавателю не добавляется.
- Standalone снятие старосты из Angular отменено D016; нужна замена.
- Bulk marks, bulk cancellation, thresholds/red-zone/free-attendance отменены;
  наличие legacy controls не возвращает их.
- Новый GO заменяет прежний dark-only Appearance preview: все существующие
  PWA/TMA экраны должны получить light/dark/system.

- Full group deletion остаётся активным implementation gap: registry явно
  включает группу, legacy DELETE не реализует принятый preview/proof protocol.
- Admin password regeneration прямо подтверждена JS-ADMIN-20 и RecoveryLinkPanel;
  отсутствие нового адаптера/API не отменяет эту принятую операцию.


## Q-ENV-004 — изолированные map final-delete проверки

**Статус: RESOLVED, разрешено и выполнено.** Прямой ответ владельца в этом чате:
«Разрешаю эти изолированные проверки и сборку». Разрешение покрывает formatter,
три новых HTTP/PostgreSQL сценария, затронутый regression и Academic JAR;
удаление схем основного стенда/production и CI сюда не входят.

Два первоначальных отказа automatic approval review до исполнения сохранены:
reviewer счёл действующим readiness STOP и не признал поздний GO из repository
достаточным доверенным сообщением. После прямого ответа проверки разрешены.
Первый реальный прогон упал из-за неверного migration directory V64; новый файл
перенесён в активный V13, второй прогон3+legacy PASS. Полный backend/client/WEB
scope reviewed, nullable OpenAPI P2 исправлен отдельным recheck, JAR заморожен.
[API-006](API-CHANGES.md#api-006--whole-scheme-map-final-deletion-locally-verified).
Основной Academic950 не обновлялся; настоящий Auth+WEB whole-delete ещё NOT RUN.
Ответа владельца по Q-ENV-004 больше не требуется.

## Q-ENV-005 — точный локальный stop/snapshot сценарий после отказа approval review

**Тип:** явное разрешение конкретной операции. **Статус:** APPROVED, но больше не требуется для GEO; операция NOT RUN.
Прямой ответ владельца: «Разрешить этот локальный сценарий».
Automatic approval review отклонил phase2 driver **до начала выполнения**:
временная остановка семи приложений и копирование приватных БД сочтены
service disruption/sensitive-data side effects, прежнего разрешения оказалось
недостаточно. Команда не началась: main13/test project `rct-pr3-setup` не
останавливался, dump/snapshot/restore отсутствуют. Обходов/повторов нет.

Проверяемый файл `/workspace/rct-pr3-runtime/clock-lane/private/quiet-window-driver.py`,
SHA256 `7b83bbf9049f0d3822b08195c3aa0f0c937c893ee6b5c6d65a04965447b55e4a`.
Уточняющий fail-closed guard требует snapshotTRUE до receipt/stop/capture;
сейчас флаг FALSE, логическое окно закрыто. Sealed package0b941667 не изменён.

Вопрос владельцу: разрешить exact локальный сценарий на искусственном
`rct-pr3-setup`: gracefully pause только7 приложений (Auth/Academic/Schedule/
Attendance/BFF/Gateway/Notification), локально сохранить3 Postgres DB и
attendance Mongo в private0700, в finally запустить SAME7 CIDs/config и
подтвердить13healthy. DB/Redis/Rabbit/Mongo/Renderer не останавливаются;
production и Git main не меняются, наружу БД не передаются. Restore — только
в новый `rct-pr3-clock` для одного native GEO15/64 с изолированным EducationalTime;
security/Auth/TLS clock остаётся реальным. Dump timeout180s, health recovery240s;
успех capture не подменяет восстановление основного стенда.

Все предварительные markers/tools/roles/privileges реально проверены, own5infra
healthy; actual snapshots/import/13apps/browser ещё NOT RUN. До прямого
ответа операция не запускалась. Альтернатива для GEO — естественное окно
main64 с05:25UTC (либо05:30 при требовании ACTIVE) до06:55UTC без stop/copy/clock
изменений. Независимая работа продолжается, scope прежних Q-ENV-003/004 не расширен.


Обновление05:44UTC: naturalmainUI15/64 успешно выполнен послеactualACTIVE, независимо подтверждён postguard053659. Ниstop/privatecopy/restore/clock/apps не понадобились; flagFALSE. Ответ на QENV005 больше не требуется дляэтогоgate. Позднее получено прямое согласие, но действие осталось ненужным и NOT RUN. Его stop/privatecopy scope не переносится на другие admin-проверки.

## Q-ENV-006 — три новых historical PROMOTE inputs

**Статус: RESOLVED, разрешено и выполнено.** Прямой ответ владельца в этом чате:
«Разрешить три тестовые записи и PROMOTE». Проверенный пакет
`/workspace/rct-pr3-runtime/admin-promotion-three-insert-20261024/PLAN.md`,
recursive SHA256 `bf13b7aa4297674a9163b5b7f8f1628f340534c34568feeed895a63fd38e2bd8`;
[independent review](../../validation/web-vue-pr3/review/promotion-REVIEW-bf13b7aa.md).

Ровно3 новых inputs committed: SPRING2025 semester5, пустая группа6 и managed
coverage. Latest completed SPRING NULL→own5 был прямо включён в разрешение.
Один обычный UI PROMOTE:200/SUCCEEDED, курс1→2, ledger1; повторный preview
ALREADY_PROCESSED, второго execute нет. Затем own6 архивирована одним обычным
API PUT, семестр5/ledger/audit сохранены. Active2 и существующие foreign rows,
accounts/grants не менялись. Historical SQL setup не означает успешный create
завершённого семестра через API. Stop/snapshot/clock не использовались.

Первоначальный SyntaxError произошёл до выполнения кода, следующий auto-review
отказ — до исполнения SQL. Эти неудачи сохранены. После прямого согласия был
один COMMIT. Timer/dedup metadata и шесть Attendance changes при естественном
закрытии пары до PROMOTE явно квалифицированы; все-БД equality не заявляется.
Архивный harness сначала отверг canonical graduation suffix после200; исходный
SUCCEEDED и независимый postread подтвердили завершение без новой команды.
Инспектор для оставшегося browser process был отдельно отклонён auto-review
до запуска; он не открывался. Безопасная очистка выполнена через обычный Auth
API ровно собственной сессии и нормальные SIGTERM точному процессу.
[Приёмка, исходные hashes и ограничения](../../validation/web-vue-pr3/checkpoints/promotion-map-20261024/promotion-acceptance.json).

Исторический срез до последних ответов: тогда ожидались Q-ENV-003/008/PROD-004; теперь все отвечены. Q-ENV-002:
разрешение обещано, в реально enforced policy revision14 www.figma.com всё ещё
отсутствует. Последняя прямая попытка при прежней revision11 дала через
inherited proxy CONNECT403/exit56; при revision14 прямой запрос не повторялся.
Новый запрос разрешения владельцу не нужен.


## Q-ENV-007 — отдельный native whole-map UI после отказа auto-review

**Тип:** точное разрешение подготовленной операции. **Статус:** APPROVED; native whole-map UI +SQL+Auth qualification COMPLETED.
Прямой ответ владельца в этом чате: «да ращрешаю». Повторное разрешение не нужно.
Последующий свежий допуск10d8 применён: один native200/две версии/четыре assets404 и21PG PASS. Actual Auth204/wrong403 +24PG приняты отдельно. Успешное удаление не повторять.
Ниже сохранены исходный отказ и подготовленный scope до согласия.
Автоматическая проверка отклонила команду **до исполнения**: согласие Q-ENV-004
на Java HTTP/PostgreSQL checks сочла недостаточно явным для необратимого
удаления всех версий PNG/SVG через UI. Browser/login/uploads/delete0, повторов
и обходов нет. Ни схема основного стенда, ни production не затрагивались.

Подготовлен reviewed `/tmp/native-admin-isolated-c3-case.cjs`, SHA256
`c75be9e409843c20f87165f4ff8bb725f65cbff9fd6bfeeb1966db6f36cd5757`;
контракт `/workspace/rct-pr3-runtime/admin-native-isolated-20261024/prep/PREP.md`.
Вопрос: разрешить один обычный UI-case в отдельном искусственном
`rct-pr3-admin-isolated`: создать только новую схему25001/1, две версии с четырьмя
синтетическими PNG/SVG из harness; одним POST с настоящим текущим паролем
удалить их полную историю и BYTEA. Building/floor/usage/high-water/receipt
сохраняются; main inventory, пользователи, права и production не меняются.
Unknown outcome сверяется по исходному operation, новая слепая запись запрещена.
Тест wrong-password и completed-replay после новой загрузки в этот case не входят;
они не будут объявлены нативно проверенными.

Critical Auth/SQL/PKI proof и независимая матрица готовы. Общий health не PASS:
Attendance activity отсутствует, прежний raw health FAIL сохраняется. Это
не разрешение на изменение health, подключение main или фиктивные сервисы.
Admission ограничен временем и revision; после ответа нужен актуальный допуск,
а не повтор старой истёкшей команды. Прямое согласие уже получено; оно относится только к описанному positive-case.
Затронут ACT-ADMIN-MAP-DELETE. Негативная Auth-проверка учитывается отдельно.


## Q-ENV-008 — один LIMITED ACL-case в отдельном искусственном realm

**Тип:** точное разрешение security mutations. **Статус:** APPROVED; original mutations COMPLETED, effective-access check pending.
Прямой ответ владельца: «Разрешить LIMITED ACL-сценарий». Повторное разрешение
не требуется; необходимы свежий технический допуск и исходный reviewed harness.
Это отдельный прежний отказ auto-review на privileged fixture, не Q-ENV-003.
Первый original3dd case выполнен: PUT200/PUT200/DELETE204, все original SUCCEEDED,24SQL PASS, target2 TEACHER сохранён/ADMIN archived; profile3/RV4/catalog12. Ниже — исходный scope.
Последнее требование владельца требует отдельного фактического target-доступа и отзыва открытой страницы; новый узкий packet использует normal target login/read в том же изолированном scope, без FULL/паролей/main.

Concrete target: существующий public synthetic teacher2 в отдельном
`rct-pr3-admin-isolated`, actor1/FULL. Настоящий readonly baseline19boolean PASS:
учитель ACTIVE, без ADMIN/grant/profile/pending, без группы, T00001; actor1
не изменён. Один обычный UI-case: выдать ADMIN LIMITED `[USERS_VIEW]`, добавить
`GROUPS_VIEW`, отозвать ADMIN, сохранив TEACHER/account. FULL, credentials, self,
main8/17/18 и production не затрагиваются. Версии, архивный grant и audit
сохраняются; byte-identical restoration не заявляется.

При неизвестном результате — только чтение исходной операции, без слепого
retry/newkey/finally-revoke. После известного частичного исхода допускается
отдельный технический handoff обычного отзыва выданного доступа с текущим CAS.
В исходном mutation-case новые аккаунты и target login не использовались; это не доказательство эффективного доступа. Результат требует
независимого SQL postguard, обычного logout собственной сессии и guest401.

PLAN `/workspace/rct-pr3-runtime/admin-access-native-20261024/prep/PLAN.md`.
Frozen harness `/tmp/native-admin-access-isolated-case.cjs`, SHA256
`3dd415086f2995f520c648be4ad84440ef275bace0fa47cc708f7f6c4e04c0d5`;
independent PREP review PASS. Owner question передан в этом чате после review;
прямое согласие получено. Исходный template ownerApprovalReceived:false сохраняется
как PREP; actual запуск требует нового private admission + технического GO.
Q-ENV-007 не переносится на этот сценарий. Затронут ACT-ADMIN-ACCESS.


## Актуальное состояние после ответов и проверок

Q-PROD-004 закрыт policy выше. Q-ENV-003/007/008 разрешены и выполнены в своих
scopes. Map deletion/Auth negative, password self-known5→6/lostACK6→7/successor
isolation и LIMITED natural withdrawal закрыты. Current password post9598:
Cred7/RV8/Aca8/profile6unassigned, own3 sessions revoked; current LIMITED postc7ee:
Teacher2profile8unassigned/RV9/catalog22/Aca9, own4 sessions revoked. Аккаунты,
прочие роли/данные сохранены. Старые Cred5/profile4/ACLprofile6 baseline выше —
исторические, не текущие команды повторной выдачи/генерации/очистки.

## Q-ENV-010 — свободное место для group UI/socket gate — ОЖИДАЕТ ОТВЕТА

Контекст: два matching8e83 Academic/Notification JAR построены один раз offline,
старые19 JAR сохранены. Actual свободно5537841152B; минимальный11-service backend
preflight6GiB=6442450944B, дефицит904609792B. WEB/PWA/TMA browser/dev-cache budget
требует6.5GiB; дефицит около1.44GB. Hard reserve2GiB/earlyguard2.5GiB сохраняются.
Named duplicate scan нашёл максимум269MB, физический reclaim не доказан и даже
теоретически недостаточен. Старые данные/стенды не удалялись. Новый host-JVM
runner вместо Docker потребовал бы отдельного networking/consumer fencing,
отклонён как расширение инфраструктуры ради бюджета.

Вопрос отправлен владельцу в этом чате: добавить минимум2GiB свободного диска,
сохранив /workspace и Docker-данные. Инструмента изменения disk config в чате нет.
Затронуты ACT-ADMIN-GROUP-DELETE, real same-realm Notification/Attendance runtime,
нативный WEB/PWA/TMA group withdrawal и зависящий Angular/commonNginx gate.
Ответ/фактическое увеличение pending; elapsed time не является разрешением или
ресурсом. PREP и независимая визуальная работа продолжаются.

Root scope внутри действующего Goal: при reviewed actual GO допустима замена
только собственных isolated Auth/Academic/Gateway/TLS-edge app containers/configs
для matching JAR/WS, с явными old→new identity/rollback; main13/clock5/shipping5,
PG/Redis/Rabbit data/realm keys/credentials сохраняются. All30 unchanged нельзя
обещать для этих будущих app replacements; пока replacements0/start0/DBwrites0.
Это необходимое расширение искусственного стенда по Goal, не изменениеproduction.

CI/Coverage/Security/OpenAPI PAUSED; без merge/deploy/production. Goal существующий.

Дополнение causal dependency: actual WEB/PWA/TMA Teacher reads идут через
/api/v1/teacher/*→Mobile BFF. Он нужен в том же изолированном realm; borrowingmain
или fake transport не допускаются. Minimum12services, полная forecast4.5GiB +
earlyreserve2.5GiB => actual preflight7GiB=7516192768B послеmatchingBFFJAR.
Старый BFF603 не совпадает с64f proto inputs, нужен один новый matching build.
Запрошенные +2GiB пока математически достаточны при canonical BFF≈57.5MB и
отсутствии новых duplicate outputs; owner ответа/фактического ресурса пока нет.
Это уточнение бюджета того же Q-ENV-010, не новый запрос и не autoapproval.


Q-ENV-010, factual update2026-10-25: actualfree≈5.36GB. Preflight runtime12 remains
7GiB=7,516,192,768B; exact+2GiB no longer leaves sufficient actualfree. Please add
about3GiB or provide≥7GiB free with margin while preserving workspace/Dockerdata.
No resizing tool is available here; no cleanup/guard weakening/start performed.
This updates the same pending resource question, not a new product approval.
