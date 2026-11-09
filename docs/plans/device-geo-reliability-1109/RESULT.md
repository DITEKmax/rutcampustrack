# Локальный результат: бот, память, разрешения и геоотметки

Пакет интегрирован в локальную ветку `codex/device-geo-reliability-1109`. Последний product commit — `5177c7fb5`; baseline — `637ffc551`. Реализация, адресные локальные проверки и независимые reviews завершены. Push, production migration и deploy этого пакета не выполнялись.

## Геоотметка по последнему решению владельца

Радиус —400м от55.788328,37.606637. Первое свежее измерение внутри радиуса сразу идёт на сервер. Если точка снаружи, приложение автоматически делает ещё до двух свежих измерений, всего максимум три в общем бюджете28с. После внешних измерений отправляется последняя свежая валидная точка; при недоступности позиции — UNAVAILABLE. Сервер автоматически подтверждает присутствие либо создаёт прежнюю заявку старосте. Промежуточные измерения не создают отдельные заявки.

Порог погрешности50м и новый ручной выбор «повторить/продолжить» удалены. Accuracy остаётся диагностикой. Центр и радиус клиент получает от сервера; конечное решение остаётся серверным. При отсутствии подсказки геозоны Today продолжает открываться, acquisition остаётся ограниченным, а сервер проверяет итоговую точку.

Отмена, выход со страницы, отключение доступа и смена аккаунта останавливают клиентскую операцию. Перед первым POST сохраняются проверка доступности пары и свежести позиции. После потерянного ответа повторяется первоначальный command/key, без подмены координат. Временное окно и cooldown не менялись.

В настройках можно повторить запрос после закрытого/неотвеченного первого запроса, выключить и снова включить использование геолокации. Для системного запрета показаны восстановление через настройки и повторная проверка; приложение не подделывает системное разрешение. Проверка в профиле не создаёт отметок/заявок и не подтверждает QR-вход.

## Остальные изменения пакета

- Бот получил периодическое восстановление клавиатур по актуальным ролям, пагинацию полного каталога привязок Auth, durable pending/receipt/UI marker, ограниченные повторы и обработку429/terminal outcomes. Warning снимается после восстановления; исторический счётчик не используется как вечный активный сбой.
- Для Attendance подготовлен лимит1024MiB вместо768MiB с фиксированным heap max460MiB/initial230MiB. Полный локальный процесс выдержал105 отметок и три последовательных раунда DOCX, каждый одновременно с35 check-ins: peak working set790.957MiB, запас22.758%. Это измерение Windows процесса с native Mongo в WSL, не Linux cgroup/production замер.
- Геодиагностика проходит HTTP/proto/audit, прежние receipts/fingerprints совместимы. Отдельный bounded best-effort report завершения получения позиции не содержит координат/ID и не влияет на auth или отметку. Provider acceptance WebPush теперь отделён от expired/retryable/skipped/uncertain; acceptance не называется доставкой или прочтением.
- gRPC Grafana использует корректные recordings; 1ч/24ч проверены на14.5млн synthetic samples при прежнем max-samples500000. ConflictException корректно становится409 без частичных записей. Старые pending-заявки подтверждены реальной проверкой; добавлен ограниченный cached age gauge.

## Экраны, которые нужно посмотреть

1. PWA/TMA: профиль всех четырёх ролей — пункт «Доступ к устройству» и новый вложенный экран геолокации/камеры.
2. PWA/TMA: «Вход в браузере» — запуск/отказ/остановка камеры, native QR и резервный browser flow; прежнее подтверждение входа сохранено.
3. PWA/TMA студента: «Сегодня» и экран отметки из уведомления — автоматические повторы, ожидание/отмена и итоговая отметка/заявка. Принятые3D объекты и переходы сохранены.
4. WEB студента: блок текущей пары на «Сегодня» — тот же общий механизм геооперации. Общий Web-профиль не переделан.

Расписание старосты и owners преподавателя/администратора затронуты только подключением общего profile/device lifecycle. Остальные экраны и общий принятый дизайн не переделывались. Подробный перечень и ручные шаги — [SCREENS.md](SCREENS.md).

Открытые стенды: [PWA](http://127.0.0.1:14272/viewport?width=390), [TMA](http://127.0.0.1:14273/viewport?width=390). Реальные Vue-компоненты работают с serve-only synthetic транспортом и датчиками; никаких настоящих permission grants, Telegram/WebPush сообщений или production запросов. IAB вкладки оставлены открытыми на восстановленном доступе: тёмная PWA и светлая TMA. QA-счётчик вне приложения в продукт не входит.

## Принятые проверки

Независимые reviews и scoped rechecks — PASS, blocking findings нет. Проверены реальные PostgreSQL миграции/данные, Redis recovery и signed HTTP→TCPgRPC→Mongo. На сервере300м дали PRESENT приaccuracy1000м,401м — одну PENDING_CONFIRMATION. Cold/expired advisory policy не вызывает сетевой RPC и не ломает Today. Официальные API generation/snapshot и адресные strict TypeScript/118 affected behavior cases прошли.

В реальном Vue synthetic QA: outside→outside→inside дали3 измерения/1 POST/PRESENT; outside×3 —1 POST/одну pending; accuracy500м в центре дала первый же PRESENT. В PWA/TMA повтор после закрытого запроса и PWA off→on сработали без перезагрузки, без бизнес-мутаций. Непрерывный camera lifecycle проверен на synthetic video. Полные команды, revisions, первичные логи, исправленные findings и failed attempts — [VERIFICATION.md](VERIFICATION.md).

## Ограничения и backlog

Настоящие Android/iOS PWA и Telegram permission dialogs/GPS/native QR ещё требуют ручной приёмки. Полный Vue→настоящий BFF report delivery и визуальный WEB preview не запускались; Web behavior/strict/source review проверены. Полный legacy migration chain1..64 не запускался: проверены production chain1..18/upgrade17→18 и отдельная V64 SQL на соответствующем DDL.

B03 live profile/notifications/ws-ticket trace не получен: существующие generation/stale guards прошли, неподтверждённой правки нет. B06 Alloy replay и исходная причина старых35s/Telegram transient не установлены; source checkpoint/readiness сохранены, существующий Auth cold recovery тест прошёл. C01 observer deployment и общая диагностика cache age/invalidation остаются backlog. A02 суточный native plateau, Linux cgroup/pagecache и более высокая конкурентность отдельно не доказаны. Оставшиеся пункты явно указаны в [PLAN.md](PLAN.md), общий production процент не вычислялся.

## Будущая доставка и rollback

После отдельного разрешения: backend-first. Применить подходящую цепочке Academic новую V18/V64, не изменяя старые migration checksums; перезапустить все Academic replicas — существующий случайный Redis namespace на процесс обеспечит новое чтение400. Проверить GetCampusGeofence400, затем обновить/restart Attendance volatile cache и проверить Today400. После совместимых Attendance/BFF API поставить новые frontends. Auth catalogue API поставить до нового bot worker. Memory flags применяются вместе с source compose; recordings/rules — через принятый infra delivery.

Smoke:400policy вRPC/Today,300м PRESENT/401м pending, immutable retry без дублей, доступность старых заявок, bot backlog/alert recovery и effective1024/460/230. Это будущие production действия, сейчас они не выполнены. Redis DEL/SCAN/FLUSH для этого изменения не требуется.

При откате сначала вернуть frontends/bot на ранее принятые artifacts, оставляя совместимые новые backend contracts до ухода новых клиентов. Не откатывать реальные отметки/заявки/receipts/audit или Flyway history вместе с кодом. Возможное обратное изменение радиуса — только новым forward migration по отдельному решению владельца; слепой downgrade за новые enum/поля недопустим.

Точный Created/Modified/Deleted список107 tracked files — [INVENTORY.md](INVENTORY.md):101 source/config/contract/test и6 документов. Удалений нет. Чужой WIP и служебные локальные status/checks/metrics не включены в product commits.
