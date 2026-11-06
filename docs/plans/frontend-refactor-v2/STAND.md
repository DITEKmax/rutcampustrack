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

## Повторный запуск

Рабочая копия Windows находится в `C:/Users/maksd/.codex/worktrees/9689/rutcampustrack/.agent/worktrees/frontend-refactor-v2`. Команды ниже выполняются из её корня. Нужны Node.js 24.14, npm 11.9 и установленные зависимости в `frontends/node_modules`. Если их нужно восстановить, выполни `npm --prefix frontends ci`.

Конфигурации и сценарии хранятся в `.agent/evidence/student-mobile-v2` и `.agent/evidence/web-student-v2`. Их актуальная резервная копия находится в `.agent/evidence/frontend-refactor-v2-stand/fixtures-notifications.zip` исходной рабочей копии `9689/rutcampustrack`. Для новой рабочей копии этой ветки распакуй архив в её корень. В ZIP 25 файлов с относительными путями от `.agent/evidence`. SHA-256 `8346c02dda7dee8ac46a60d756e597ad1b1022bd202eaecf424ee4fb5f24df10`.

Каждая команда запускается в своём окне PowerShell.

    node frontends/node_modules/vite/bin/vite.js --config .agent/evidence/student-mobile-v2/pwa-qa/vite.config.mjs
    node frontends/node_modules/vite/bin/vite.js --config .agent/evidence/student-mobile-v2/tma-qa/vite.config.mjs
    node frontends/node_modules/vite/bin/vite.js --config .agent/evidence/web-student-v2/vite.config.mjs

Для HTML запусти две команды из исходной рабочей копии `9689/rutcampustrack`. Каталог Web-эталонов содержит четыре оригинальных HTML. Остальные оригиналы лежат по отдельным каталогам в `.agent/inputs/frontend-refactor-v2`. ZIP владельца сохранены в `C:/Users/maksd/OneDrive/Desktop/frontendrefactor`.

    python -m http.server 14274 --bind 127.0.0.1 --directory .agent/inputs/frontend-refactor-v2/checkin-impulse
    python -m http.server 14276 --bind 127.0.0.1 --directory .agent/inputs/frontend-refactor-v2/web-student

Замеры, снимки и журналы проверок находятся в `.agent/evidence` рабочей копии второго рефакторинга. Пути и границы проверок приведены в отчётах каждого архива. Для приёмки в установленной PWA и нативном Telegram нужно отдельно пройти открытие ссылки, геолокацию и скачивание на устройстве.
