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

**Тип:** разрешение на ограниченную проверку стенда. **Статус:** waiting for owner.
Automatic approval review отклонил сценарий, который временно выдаёт FULL,
меняет постоянные credentials и проверяет self-regeneration: объём разрешения
на security mutations был недостаточно явным. До ответа не запускается.

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
Затронут runtime gate ACT-ADMIN-REGENERATE-PASSWORD; user8 заморожен. Остальные
проверки продолжаются на собственных отдельных fixtures без расширения прав.

## Q-PROD-004 — участники окончательно удаляемой группы

**Тип:** существенное продуктовое решение. **Статус:** waiting for owner.
Финальное удаление группы уже принято: canonical COMPONENT_REGISTRY.md:127,
JS-ADMIN-18: числовой preview, текущий пароль, подтверждённый cascade. Сам scope
не обсуждается повторно. Q-PROD-003 запрещает отдельное окончательное удаление
пользователя. Current GroupService DELETE не реализует безопасный flow и
блокирует managed history; прежний endpoint не задаёт новую policy.

Не определены последствия для текущего membership и student/headman grants
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


## Q-ENV-004 — изолированные map final-delete проверки после отказа auto-review

**Тип:** конкретное разрешение на локальные проверки. **Статус:** waiting for owner.
Подготовлены6 source/test/migration файлов в `/workspace/rct-worktrees/device-api`,
reviewable contract `/tmp/map-final-delete-contract-assessment-20261024.md`.
Scope: локальный formatter5Java,3 CampusMapWholeDeletionHttpIT плюс один затронутый
selected-format regression, отдельная synthetic PostgreSQL и Academic bootJar.
Никаких удалений схем рабочего стенда/production, dispatch CI или нового Goal.

Automatic approval review дважды отклонил точную команду: сначала сослался на
исходный readiness STOP, затем заявил, что последующий GO существует только
в недоверенном документе репозитория, а не доверенном user message. Исполнитель
не запустил formatter/Gradle/Docker, не выполнял обход, source coherent WIP.
Map checks/build NOT RUN, не PASS. Владельцу отправлен Q-ENV-004 через основной
чат с выбором разрешить эти изолированные проверки/сохранить подготовленный код.
До ответа зависит только этот map backend gate; независимая работа продолжается.

Дополнительный отказ скрипту создания LIMITED администратора для create receipt
не обходился: скрипт не запускался. Безопасная альтернатива убрала создание
администратора и изменение прав: existing seed + обычный собственный STUDENT18,
реальный потерянный201→same-operation resume→volatile receipt PASS; отдельный
component case проверяет отображение с минимальными capabilities. Настоящий
новый LIMITED actor не создан и такой runtime PASS не заявляется. Нового вопроса
владельцу для этой уже выполненной альтернативы не требуется.

Latest Q-ENV-002 probe2026-10-24 after owner response: connected/running, policy revision7 enforced, www.figma.com absent; actual curl through inherited proxy exit56 CONNECT403/http000/0bytes. No repeated permission request; no direct-network/TLS bypass. MCP/original-image read result remains valid separately.
