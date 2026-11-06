# Локальный стенд второго рефакторинга

Стенд запускает компоненты ветки `codex/frontend-refactor-v2` с синтетическими данными. Настоящие API, учётные записи и Telegram на этих адресах не используются. Серверы слушают только `127.0.0.1`. При перезагрузке в той же вкладке сохраняются тема и настройки уведомлений. Остальные тестовые изменения сбрасываются.

## Что открыть

| Поверхность | Точки входа |
|---|---|
| PWA | [Студент](http://127.0.0.1:14272/viewport?width=390), [староста](http://127.0.0.1:14272/viewport?width=390&headman=routes), [преподаватель](http://127.0.0.1:14272/viewport?width=390&teacher=v3), [администратор](http://127.0.0.1:14272/viewport?width=390&admin=v2) |
| TMA | [Студент](http://127.0.0.1:14273/viewport?width=390), [староста](http://127.0.0.1:14273/viewport?width=390&headman=requests), [преподаватель](http://127.0.0.1:14273/viewport?width=390&teacher=v3), [администратор](http://127.0.0.1:14273/viewport?width=390&admin=v2) |
| Импульс | [Успех в PWA](http://127.0.0.1:14272/viewport?width=390&height=760&impulse=success&theme=dark), [ожидание в TMA](http://127.0.0.1:14273/viewport?width=390&impulse=pending&link=signed), [HTML эталона](http://127.0.0.1:14274/rct-checkin-impulse-final.html) |
| Web | [Студент](http://127.0.0.1:14220/viewport?width=1280&role=STUDENT&screen=home), [староста](http://127.0.0.1:14220/viewport?width=1280&role=HEADMAN&screen=home), [преподаватель](http://127.0.0.1:14220/viewport?width=1280&role=TEACHER&screen=home), [администратор](http://127.0.0.1:14220/viewport?width=1280&role=ADMIN&screen=home) |
| Web HTML | [Студент](http://127.0.0.1:14276/rct-web-student-audit.html), [староста](http://127.0.0.1:14276/rct-web-headman-audit.html), [преподаватель](http://127.0.0.1:14276/rct-web-teacher-audit.html), [администратор](http://127.0.0.1:14276/rct-web-admin-audit.html) |

Для мобильных ролей выбери роль внутри приложения. Меняй тему через профиль. На Web параметр `role` выбирает начальную роль, `width=390` даёт узкий экран, `font=125` увеличивает размер текста. Рядом с таблицами и редакторами могут быть собственные области прокрутки. Для просмотра отказов, частичных состояний и повторов используй адреса из [плана и отчётов](PLAN.md).

Чтобы проверить переключатели доставки, открой уведомления на стенде с тестовой привязкой Telegram. [PWA](http://127.0.0.1:14272/viewport?width=390&notifications=linked) · [TMA](http://127.0.0.1:14273/viewport?width=390&notifications=linked)

В «Учёте» перейди к первой неделе сентября. У «н» за 1 сентября доступны обе формы. На других парах этой недели есть примеры активных заявок обоих типов. В разрезе «По предметам» открой «Компьютерные сети» и нажми первый жёлтый сегмент в «Практике». Он показывает причину, комментарий и тестовую справку. [TMA с исчерпанным лимитом](http://127.0.0.1:14273/viewport?width=390&attendanceBudget=zero) показывает ноль попыток на следующем шаге.

На [стенде восстановления пароля](http://127.0.0.1:14277/viewport?width=390) введи логин `student.preview`, запроси код и введи `246810`. Для нового пароля подойдёт любая тестовая строка от 10 символов. После сохранения можно войти с этим паролем в той же вкладке. Состояние этой учётной записи хранится до перезагрузки.

В «Ещё → Заявки» тестовые открытые заявки можно отменить и найти в архиве. В [сценарии временного отказа](http://127.0.0.1:14273/viewport?width=320&text=125&requestCancel=fail-once) первая отмена показывает сообщение рядом с заявкой, повтор переносит её в архив.

## Повторный запуск

Рабочая копия Windows находится в `C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2`. Команды ниже выполняются из её корня. Нужны Node.js 24.14, npm 11.9 и установленные зависимости в `frontends/node_modules`. Если их нужно восстановить, выполни `npm --prefix frontends ci`.

Конфигурации и сценарии хранятся в `.agent/evidence/student-mobile-v2` и `.agent/evidence/web-student-v2`. Их актуальная резервная копия находится в `.agent/evidence/frontend-refactor-v2-stand/fixtures-student-requests.zip` исходной рабочей копии `9689/rutcampustrack`. Для новой рабочей копии этой ветки распакуй архив в её корень. В ZIP 28 файлов с путями от корня рабочей копии. SHA-256 `683a4ada92db2e252fbde699f2e9a3b169875d496fd85f7ab1ae2b76f88d3e7c`.

Каждая команда запускается в своём окне PowerShell.

    node frontends/node_modules/vite/bin/vite.js --config .agent/evidence/student-mobile-v2/pwa-qa/vite.config.mjs
    node frontends/node_modules/vite/bin/vite.js --config .agent/evidence/student-mobile-v2/tma-qa/vite.config.mjs
    node frontends/node_modules/vite/bin/vite.js --config .agent/evidence/web-student-v2/vite.config.mjs
    node frontends/node_modules/vite/bin/vite.js --config .agent/evidence/student-mobile-v2/pwa-qa/recovery.config.mjs

Для HTML запусти две команды из исходной рабочей копии `9689/rutcampustrack`. Каталог Web-эталонов содержит четыре оригинальных HTML. Остальные оригиналы лежат по отдельным каталогам в `.agent/inputs/frontend-refactor-v2`. ZIP владельца сохранены в `C:/Users/maksd/OneDrive/Desktop/frontendrefactor`.

    python -m http.server 14274 --bind 127.0.0.1 --directory .agent/inputs/frontend-refactor-v2/checkin-impulse
    python -m http.server 14276 --bind 127.0.0.1 --directory .agent/inputs/frontend-refactor-v2/web-student

Замеры, снимки и журналы проверок находятся в `.agent/evidence` рабочей копии второго рефакторинга. Пути и границы проверок приведены в отчётах каждого архива. Для приёмки в установленной PWA и нативном Telegram нужно отдельно пройти открытие ссылки, геолокацию и скачивание на устройстве.
