# Независимый source review runtime PREP aaa98662

Вердикт: **FAIL — два P2 в calendar/fixture API-подготовке**. Это замечания к подготовке стенда, не доказанные дефекты продукта. До их закрытия не допускать `calendar-setup`, `calendar-restore` и `fixture-api` по этому пакету. Само review не разрешает запуск остальных фаз: свежий ресурсный admission и отдельный Root GO остаются обязательными.

Проверен immutable пакет `/workspace/rct-pr3-runtime/group-final-native-20261024/v2-notification`: SHA256 manifest `aaa98662876197d61f00638d4925612def7b42fed7fb0dbf8bfb9fd683bc5717`, все **31 файла / 159013 B** совпали. Прочитаны применимые AGENTS/RULES/CURRENT. Назначение модели — gpt-6.1-sol/high; независимая runtime telemetry модели недоступна. Продукт, приватные конфиги/credentials, браузеры, API, Docker и БД reviewer не запускал и не читал.

## F1 — P2: ошибка/unknown при подготовке теряет собственные живые Auth-сессии

Места: `calendar.cjs:8,14,26–30`, `fixture-api.cjs:8,15,28,32–36`.

`login()` держит API-контекст, refresh cookies и bearer только в локальном `a` и возвращает его лишь после session GET и выбора роли. Если login уже принят, а следующий GET/PUT возвращает ошибку или теряет ответ, вызывающий код ещё не получил `actor`, SID/контекст не сохранены. Для fixture есть дополнительное окно: headman, teacher и controlHeadman регистрируются в `actors` только после завершения всех трёх login; ошибка второго/третьего теряет уже созданные предыдущие сессии. Даже после успешного возврата последующая ошибка calendar/fixture заканчивается `dispose()`/выходом процесса; durable SID/cookies для этих акторов сохраняются только в самом конце успешной fixture. Calendar не сохраняет их вообще.

Следствие: разрешённая одиночная подготовка может оставить живую собственную Admin/Headman/Teacher-сессию без удержанного контекста и точного cleanup handle. Read-only `calendar-control.py inspect` восстанавливает учебное состояние, но не эти Auth-контексты. Оригинальные audit keys не являются session handles. Это мешает подтвердить завершение именно своих сессий и провоцирует дополнительный login для восстановления после unknown.

Минимальное закрытие: зарегистрировать контекст до первого login POST; немедленно сохранять полученные собственные SID/actor и необходимые private handles, до следующей мутации; квалифицировать ожидаемого актора. Для принятого/неизвестного login или неизвестной дальнейшей мутации сохранить доступный RAM/context в HOLD и не делать новый login, DELETE, logout или finally-компенсацию ради зелёного результата. Регистрировать fixture-акторов по одному. Успешные подготовительные сессии передавать дальнейшему владельцу явно; cleanup только после известного результата и точной корреляции собственного SID. Это один локальный lifecycle fix, новый общий framework не нужен.

## F2 — P2: Root admission проверяет один realm, API-команды могут уйти в другой

Места: `calendar.cjs:11–14`, `fixture-api.cjs:10–16`, обе функции `login()`/`invoke()`.

После проверки package/Root HEAD/контейнеров изолированного realm оба runner берут произвольный `cfg.baseURL` из переданного файла без сравнения с замороженным origin. APIRequestContext затем отправляет туда Admin login/password и команды. Проверка `userId === 1` выполняется только после login и выбора роли и не идентифицирует realm: такой ID есть и в других стендах. В calendar собственный FileClock уже меняется до первого такого запроса.

Следствие: ошибочно переданный конфиг другого стенда проходит локальный infrastructure admission, может получить приватные credentials и, при подходящих credentials, создать/активировать семестр или fixtures вне разрешённого realm. Заявление «только own realm» в примерном JSON этого не ограничивает.

Минимальное закрытие: до clock write и любого Auth-запроса проверить точный замороженный origin подготовки (`http://127.0.0.1:14250`, если сохраняется текущий WEB proxy), отсутствие URL credentials/path/query/fragment и соответствие Root phase GO; запретить перенаправление Auth/мутаций за его пределы. Оставить один известный адрес, без endpoint discovery/fallback. Нужна локальная scope-проверка, не новая система admission.

## Остальная source-квалификация

- Шесть JAR независимо совпали по SHA256, размеру и встроенному source commit: Auth/Schedule/Attendance `64f0ea29`, Academic/Notification/BFF `8e83a2d9`. Expanded manifest `31a5aa9bab8084b707d48652d9ab78bb019995de758667ab3efb907cca0b5028` содержит 1530 входов; все текущие Root backend/proto bytes совпали. В новом Academic JAR присутствует PropertiesLauncher; старый hash в `academic-classloader-static-check.json` — историческое свидетельство, не проверка нового JAR. Его динамический teaching-clock результат ещё не заявлен.
- Compose сохраняет исходные PG/Redis/Rabbit definitions, realm network/keys и старые данные. Новые пять сервисов приватные, cached-only, с caps; четыре разрешённые замены выполняются stop-before-recreate. Параллельный старый Auth/Academic consumer этим порядком не создаётся. Нет `down`, prune, DB copy/reset или downgrade.
- Новый Schedule principal NOINHERIT без memberships/DDL/TRUNCATE/подключения к Auth/Academic; новый DB owner — существующий trusted migrator. Followup callback ограничен `schedule_db` и явным runtime allowlist после точного 42109 callback. Mongo — отдельные readWrite principals двух собственных DB; реальное auth/transaction/cross-DB поведение ещё предстоит проверить подготовленной фазой.
- Teaching clock смонтирован директорией, поэтому atomic replacement файла виден сервису. Меняется только собственный FileClock, не OS/Auth/main. Известное восстановление требует COMPLETED + afterproof + cleanup, возвращает исходные clock bytes, затем нормально активирует старый semester2; финальная проверка учитывает только разрешённые monotonic version +2. Автоматического restore/повторной activation после unknown нет; inspect выполняет только чтение. Замечание F1 касается сохранения Auth-контекста такого unknown, а не требования blindly restore.
- Telegram adapter ограничивает известный own selector и два новых публичных synthetic IDs, держит HMAC/initData в RAM; не печатает signer, не выбирает альтернативные credentials и не вызывает Telegram provider. Реальные `/auth/tma`, actor/role и физический WS — ещё критерии runtime, не source PASS.
- Ресурсные admission fail closed: при текущих ~5.43 GB первый порог 7 GiB не проходит. QENV010/+2 GiB остаётся **PENDING, не разрешение и не применённое увеличение**. Есть 2.5 GiB early guard/250 ms и ограниченное закрытие только свежих own процессов, без удаления volumes/данных. Фактические новые materialization/migration/health/resource результаты этим review не подтверждены.
- `native-group-flow.cjs` явно является старой WEB-only reference. Его нельзя запускать как приёмку трёх клиентов или считать его finally-cleanup допуском для отдельного нового six-view driver. Последний не входит в данный manifest и требует собственного frozen source admission до единственного delete. Все шесть живых role views, реальные WS/read/DOM эффекты, независимый postproof и известное завершение собственных сессий остаются отдельными runtime-критериями. Принятые backend/client проверки не переоткрывались.

Все обнаруженные существенные замечания к данному пакету приведены выше одним packet. Дополнительных P1/P2 по просмотренным infrastructure/pins/migration/clock/Telegram исходникам не обнаружено.
