# Первый FULL администратор при ручном Deploy

Операция создаёт первый FULL аккаунт через существующий bootstrap Auth/Academic.
Она доступна только будущему ручному Deploy с явным bootstrap_initial_admin=true.
По умолчанию флаг выключен; автоматический workflow_run не получает запрос.
Подготовка файлов и загрузка GitHub Secret сами по себе не создают аккаунт
и не запускают Deploy.

## Подготовка в Windows

Используй PowerShell 7. Каталог должен уже существовать, принадлежать тебе и
разрешать доступ только твоему Windows SID и SYSTEM. Helper проверяет ACL целевых
файлов и отсутствие reparse points во всём пути, отказывается перезаписывать
запрос/README. Исходные семь production secrets и 41-file bundle не меняются.

Скопируй проверенный scripts/Prepare-InitialAdminRequest.ps1 в свой игнорируемый
каталог .secrets/production и запускай копию, даже если основной checkout
отстаёт от release. Пример из корня твоего checkout:

~~~powershell
$private = Join-Path (Get-Location) '.secrets/production'
& (Join-Path $private 'Prepare-InitialAdminRequest.ps1') -Directory $private
& (Join-Path $private 'Prepare-InitialAdminRequest.ps1') -Directory $private -CheckOnly
~~~

Создаются только INITIAL_ADMIN_REQUEST и INITIAL_ADMIN_README.txt. Запрос:
version=1, новый operationId, login rootadmin, имя Админ, отчество Админович,
фамилия Администратов и случайный временный пароль. Пароль —
6 независимых выборов из встроенного ASCII словаря, разделённых дефисами,
случайный знак и 6 цифр; минимум 67 бит случайности,12+ Unicode scalar values
и не более 72 UTF-8 байт. Используется системный криптографический генератор.
В терминал пароль/JSON не выводятся.

operationId — постоянный идентификатор этой операции. Сохрани **весь исходный
файл без изменений** для повторного запуска после частичного сбоя. Повторная
генерация даёт другой запрос и не является восстановлением. Bootstrap возобновляет
только ту же операцию с теми же данными; существующий другой администратор
отклоняется. Механизм не сбрасывает пароль и не выдаёт права существующему
пользователю.

## Будущая загрузка запроса и запуск

Следующие команды выполняет владелец после разрешения конкретного выпуска.
Они здесь как инструкция; публикация кода не загружает secret и не включает
PRODUCTION_DEPLOY_ENABLED.

~~~powershell
$private = Join-Path (Get-Location) '.secrets/production'
$requestFile = Join-Path $private 'INITIAL_ADMIN_REQUEST'
$OutputEncoding = [Text.UTF8Encoding]::new($false)
Get-Content -LiteralPath $requestFile -Raw -Encoding utf8 |
    gh secret set INITIAL_ADMIN_REQUEST --repo ditekmax/rutcampustrack
if ($LASTEXITCODE -ne 0) { throw 'GitHub Secret upload failed.' }
~~~

Передавай JSON только через stdin; не используй --body, аргументы с паролем,
Actions variables, обычный .env, artifacts или production bundle.
Не выводи запрос через Get-Content без pipe и не публикуй снимки его содержимого.

При будущем разрешённом ручном запуске workflow **Deploy** укажи полный
commit_sha, причину и bootstrap_initial_admin=true. Выбранная revision должна
поддерживать эту операцию: старый/отсутствующий delivery script отклоняется до SSH.
Неверный, пустой, слишком большой или нестрогий JSON также отклоняется до SSH.
Флаг не отменяет общий production gate, проверки подписей и готовности сервисов.

Запрос доставляется отдельным файлом0600 в приватный incoming directory. После
миграций/healthy Compose и проверки выпуска существующий Python CLI запускает
one-shot контейнеры с явными --project-name rutcampustrack, --env-file .env.prod,
обоими production/operator compose files и выбранным IMAGE_TAG.
Секреты попадают в Auth только через private stdin; profile —
в Academic через тот же закрытый канал. Ошибка bootstrap завершает доставку
неуспешно, .deployed-sha не записывается. Уже начатая операция при этом может
потребовать точного повтора исходного запроса; это не откат БД.

Временные runner/remote копии удаляются при успехе и ошибке. Локальный исходный
файл и GitHub Secret сохраняются для повторов; автоматического удаления нет.

## Вход и завершение

Скопируй пароль в буфер без печати в терминал:

~~~powershell
Set-Clipboard -Value ((Get-Content -LiteralPath $requestFile -Raw -Encoding utf8 |
    ConvertFrom-Json).initialPassword)
~~~

Войди как rootadmin, подтверди доступ и смени временный пароль средствами
приложения. Эта инструкция не обещает автоматического принуждения к смене пароля.
После подтверждённого входа и смены пароля владелец может удалить GitHub Secret
INITIAL_ADMIN_REQUEST, очистить буфер (Set-Clipboard -Value '') и определить
защищённое хранение/удаление локального исходного запроса. Не удаляй единственный
исходный запрос во время частичного сбоя.
