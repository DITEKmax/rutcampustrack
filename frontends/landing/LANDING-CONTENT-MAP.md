# Карта технического контента RutCampusTrack

Снимок локального Vue-лендинга от 13 ноября 2026. Диапазон — от [«Nginx»](http://127.0.0.1:14312/presentation/#architecture) до [«Геоотметки на трёх уровнях»](http://127.0.0.1:14312/presentation/#recovery-retention-verification-checks) включительно.

В диапазоне 436 заголовков. Это 47 существующих глав, 368 разборов и 21 вложенный заголовок демонстраций. «Вход и сервисы» — рабочее название для 13 вступительных разборов до первой главы. Заголовки IDE-кадров, кнопки и подписи к коду относятся к содержимому разборов.

В каждом варианте ниже все 48 групп исходного списка распределены по страницам ровно один раз. Владелец выбрал вариант А. Разборы и вложенные заголовки перенесены вместе со своей главой. Адреса варианта А доступны на локальном стенде, варианты Б и В сохранены для сравнения. Кэширование и SEO отложены.

| Вариант | Страниц | Разборов на странице | Принцип |
| --- | ---: | ---: | --- |
| А. Тематический | 10 | 10–51 | Понятная страница для каждой самостоятельной области |
| Б. Крупные направления | 5 | 50–100 | Короткое меню с большими страницами |
| В. Сценарный | 9 | 10–60 | Названия страниц отталкиваются от задач читателя |

## Вариант А. Тематические страницы

Принятый вариант. Плитки и H1 используют короткие названия ниже. Ссылки на исходные якоря в инвентаре перенаправляют на соответствующие темы. После составления карты к десяти техническим страницам добавлена отдельная страница дизайна.

### Архитектура и API

`/presentation/architecture/` · 24 разбора

Как запрос проходит от Nginx до сервисов и каким контрактом возвращается ответ.

[Вход и сервисы](http://127.0.0.1:14312/presentation/#architecture) · [Статусы HTTP и gRPC](http://127.0.0.1:14312/presentation/#api-statuses)

### Безопасность

`/presentation/security/` · 43 разбора

Вход, сеансы, управление ролями и доступ к конкретному объекту.

[Безопасность](http://127.0.0.1:14312/presentation/#security) · [Профиль и устройство](http://127.0.0.1:14312/presentation/#profile-device-settings) · [Сеансы и история аккаунта](http://127.0.0.1:14312/presentation/#account-sessions-history) · [Помощники старосты](http://127.0.0.1:14312/presentation/#headman-assistants) · [Доступ к паре и заявке](http://127.0.0.1:14312/presentation/#lesson-request-access)

### Данные

`/presentation/data/` · 36 разборов

Хранилища, транзакции, кэш и общие механизмы записи и доставки.

[Базы данных](http://127.0.0.1:14312/presentation/#databases) · [Транзакции и конкурентность](http://127.0.0.1:14312/presentation/#transactions) · [Кэширование](http://127.0.0.1:14312/presentation/#cache) · [Паттерны проекта](http://127.0.0.1:14312/presentation/#patterns)

### Надёжность

`/presentation/reliability/` · 50 разборов

Как ограничивают операции, расследуют сбои, проверяют изменения и доставляют сборку.

[Отказоустойчивость](http://127.0.0.1:14312/presentation/#resilience) · [Бюджеты операций](http://127.0.0.1:14312/presentation/#operation-budgets) · [Производительность](http://127.0.0.1:14312/presentation/#performance) · [Диагностика и согласованность](http://127.0.0.1:14312/presentation/#request-diagnostics) · [Восстановление, хранение и проверки](http://127.0.0.1:14312/presentation/#recovery-retention-verification) · [Тесты](http://127.0.0.1:14312/presentation/#tests) · [CI/CD](http://127.0.0.1:14312/presentation/#ci-cd)

### Администрирование

`/presentation/academic/` · 51 разбор

Как создают и поддерживают учебную структуру, составы, назначения и учётные записи.

[Жизненный цикл семестра](http://127.0.0.1:14312/presentation/#semester-lifecycle) · [Жизненный цикл группы](http://127.0.0.1:14312/presentation/#group-lifecycle) · [Жизненный цикл предмета](http://127.0.0.1:14312/presentation/#subject-lifecycle) · [Назначения преподавателей](http://127.0.0.1:14312/presentation/#teacher-assignments) · [Жизненный цикл пользователя](http://127.0.0.1:14312/presentation/#user-lifecycle) · [История после перевода студента](http://127.0.0.1:14312/presentation/#student-transfer-history) · [Обзор администратора](http://127.0.0.1:14312/presentation/#admin-overview)

### Расписание

`/presentation/schedule/` · 39 разборов

Как шаблоны становятся занятиями и как расписание, переносы и ДЗ видны по ролям.

[Конструктор расписания](http://127.0.0.1:14312/presentation/#schedule-constructor) · [Расписание по ролям](http://127.0.0.1:14312/presentation/#schedule-by-role) · [Жизненный цикл пары](http://127.0.0.1:14312/presentation/#lesson-lifecycle) · [Перенос и отмена пары](http://127.0.0.1:14312/presentation/#lesson-changes) · [Домашние задания](http://127.0.0.1:14312/presentation/#homework-lifecycle)

### Посещаемость

`/presentation/attendance/` · 34 разбора

Как появляется отметка, как её пересматривают и где живут подтверждающие документы.

[Геоотметка](http://127.0.0.1:14312/presentation/#geo-checkin) · [Жизненный цикл отметки](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle) · [Жизненный цикл заявки](http://127.0.0.1:14312/presentation/#request-lifecycle) · [Вложения в заявках](http://127.0.0.1:14312/presentation/#request-attachments)

### Журнал и отчёты

`/presentation/reports/` · 46 разборов

Как из отметок получаются таблица, проценты, рейтинг и готовые файлы.

[Журнал посещаемости](http://127.0.0.1:14312/presentation/#attendance-journal) · [Статистика посещаемости](http://127.0.0.1:14312/presentation/#attendance-statistics) · [Рейтинг студентов](http://127.0.0.1:14312/presentation/#student-ranking) · [Выгрузка журнала посещаемости](http://127.0.0.1:14312/presentation/#attendance-journal-export) · [Экспорт отчётов](http://127.0.0.1:14312/presentation/#report-export) · [Данные отчёта и контракт](http://127.0.0.1:14312/presentation/#report-data-contract)

### Уведомления и PWA

`/presentation/notifications/` · 35 разборов

Как событие доходит до пользователя и что происходит на клиенте при открытии или потере сети.

[События в реальном времени](http://127.0.0.1:14312/presentation/#realtime) · [Жизненный цикл уведомления](http://127.0.0.1:14312/presentation/#notification-lifecycle) · [Переходы из уведомлений](http://127.0.0.1:14312/presentation/#notification-actions) · [Telegram-бот](http://127.0.0.1:14312/presentation/#telegram-bot) · [Подписка Web Push](http://127.0.0.1:14312/presentation/#web-push-subscription) · [Офлайн-режим PWA](http://127.0.0.1:14312/presentation/#pwa-offline)

### Карта кампуса

`/presentation/campus/` · 10 разборов

Как публикуют планы этажей, читают версии и учитывают открытия.

[Карта кампуса](http://127.0.0.1:14312/presentation/#campus-map) · [Статистика карты](http://127.0.0.1:14312/presentation/#campus-map-statistics)

### Дизайн и типографика

[`/presentation/design/`](http://127.0.0.1:14312/presentation/design/) · четыре первых раздела. Учебный день как табло, типографика Onest, цветовые токены, шесть состояний из Figma и связь с Vue. Следующие разделы перечислены в [инвентаре](DESIGN-CONTENT-INVENTORY.md).

## Вариант Б. Пять крупных направлений

Подходит для короткого меню и последовательного чтения. «Учебный процесс и кампус» соберёт 100 разборов, поэтому на этой странице понадобится локальное оглавление.

### Архитектура и данные

`/presentation/platform/` · 60 разборов

Входной запрос, сервисы, контракты, хранение и общие механизмы.

[Вход и сервисы](http://127.0.0.1:14312/presentation/#architecture) · [Статусы HTTP и gRPC](http://127.0.0.1:14312/presentation/#api-statuses) · [Базы данных](http://127.0.0.1:14312/presentation/#databases) · [Транзакции и конкурентность](http://127.0.0.1:14312/presentation/#transactions) · [Кэширование](http://127.0.0.1:14312/presentation/#cache) · [Паттерны проекта](http://127.0.0.1:14312/presentation/#patterns)

### Доступ, PWA и уведомления

`/presentation/access-and-delivery/` · 78 разборов

Путь пользователя от входа и выбора роли до работы с доставкой и офлайн-данными.

[Безопасность](http://127.0.0.1:14312/presentation/#security) · [Профиль и устройство](http://127.0.0.1:14312/presentation/#profile-device-settings) · [Сеансы и история аккаунта](http://127.0.0.1:14312/presentation/#account-sessions-history) · [Помощники старосты](http://127.0.0.1:14312/presentation/#headman-assistants) · [Доступ к паре и заявке](http://127.0.0.1:14312/presentation/#lesson-request-access) · [События в реальном времени](http://127.0.0.1:14312/presentation/#realtime) · [Жизненный цикл уведомления](http://127.0.0.1:14312/presentation/#notification-lifecycle) · [Переходы из уведомлений](http://127.0.0.1:14312/presentation/#notification-actions) · [Telegram-бот](http://127.0.0.1:14312/presentation/#telegram-bot) · [Подписка Web Push](http://127.0.0.1:14312/presentation/#web-push-subscription) · [Офлайн-режим PWA](http://127.0.0.1:14312/presentation/#pwa-offline)

### Эксплуатация и выпуск

`/presentation/operations/` · 50 разборов

Лимиты, диагностика, восстановление, тесты и CI/CD.

[Отказоустойчивость](http://127.0.0.1:14312/presentation/#resilience) · [Бюджеты операций](http://127.0.0.1:14312/presentation/#operation-budgets) · [Производительность](http://127.0.0.1:14312/presentation/#performance) · [Диагностика и согласованность](http://127.0.0.1:14312/presentation/#request-diagnostics) · [Восстановление, хранение и проверки](http://127.0.0.1:14312/presentation/#recovery-retention-verification) · [Тесты](http://127.0.0.1:14312/presentation/#tests) · [CI/CD](http://127.0.0.1:14312/presentation/#ci-cd)

### Учебный процесс и кампус

`/presentation/education/` · 100 разборов

Администрирование, расписание, занятия, ДЗ и карты.

[Жизненный цикл семестра](http://127.0.0.1:14312/presentation/#semester-lifecycle) · [Жизненный цикл группы](http://127.0.0.1:14312/presentation/#group-lifecycle) · [Жизненный цикл предмета](http://127.0.0.1:14312/presentation/#subject-lifecycle) · [Назначения преподавателей](http://127.0.0.1:14312/presentation/#teacher-assignments) · [Жизненный цикл пользователя](http://127.0.0.1:14312/presentation/#user-lifecycle) · [История после перевода студента](http://127.0.0.1:14312/presentation/#student-transfer-history) · [Обзор администратора](http://127.0.0.1:14312/presentation/#admin-overview) · [Конструктор расписания](http://127.0.0.1:14312/presentation/#schedule-constructor) · [Расписание по ролям](http://127.0.0.1:14312/presentation/#schedule-by-role) · [Жизненный цикл пары](http://127.0.0.1:14312/presentation/#lesson-lifecycle) · [Перенос и отмена пары](http://127.0.0.1:14312/presentation/#lesson-changes) · [Домашние задания](http://127.0.0.1:14312/presentation/#homework-lifecycle) · [Карта кампуса](http://127.0.0.1:14312/presentation/#campus-map) · [Статистика карты](http://127.0.0.1:14312/presentation/#campus-map-statistics)

### Посещаемость, заявки и отчёты

`/presentation/attendance-and-reports/` · 80 разборов

Полный путь от геоотметки до файла и аналитики.

[Геоотметка](http://127.0.0.1:14312/presentation/#geo-checkin) · [Жизненный цикл отметки](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle) · [Жизненный цикл заявки](http://127.0.0.1:14312/presentation/#request-lifecycle) · [Вложения в заявках](http://127.0.0.1:14312/presentation/#request-attachments) · [Журнал посещаемости](http://127.0.0.1:14312/presentation/#attendance-journal) · [Статистика посещаемости](http://127.0.0.1:14312/presentation/#attendance-statistics) · [Рейтинг студентов](http://127.0.0.1:14312/presentation/#student-ranking) · [Выгрузка журнала посещаемости](http://127.0.0.1:14312/presentation/#attendance-journal-export) · [Экспорт отчётов](http://127.0.0.1:14312/presentation/#report-export) · [Данные отчёта и контракт](http://127.0.0.1:14312/presentation/#report-data-contract)

## Вариант В. Страницы по сценариям

Каждая страница начинается с действия. Подходит для чтения от запроса, занятия или инцидента к результату. Сквозные темы вроде лимитов и контроля доступа потребуют больше перекрёстных ссылок.

### Провести запрос через систему

`/presentation/stories/request/` · 40 разборов

Открыть приложение, проследить запрос и разобраться в ответе и задержке.

[Вход и сервисы](http://127.0.0.1:14312/presentation/#architecture) · [Статусы HTTP и gRPC](http://127.0.0.1:14312/presentation/#api-statuses) · [Диагностика и согласованность](http://127.0.0.1:14312/presentation/#request-diagnostics) · [Производительность](http://127.0.0.1:14312/presentation/#performance)

### Войти и получить доступ

`/presentation/stories/access/` · 52 разбора

Подтвердить личность, получить роль и проверить доступ к данным.

[Безопасность](http://127.0.0.1:14312/presentation/#security) · [Профиль и устройство](http://127.0.0.1:14312/presentation/#profile-device-settings) · [Сеансы и история аккаунта](http://127.0.0.1:14312/presentation/#account-sessions-history) · [Помощники старосты](http://127.0.0.1:14312/presentation/#headman-assistants) · [Доступ к паре и заявке](http://127.0.0.1:14312/presentation/#lesson-request-access) · [Жизненный цикл пользователя](http://127.0.0.1:14312/presentation/#user-lifecycle)

### Подготовить семестр и расписание

`/presentation/stories/semester/` · 51 разбор

Собрать учебную структуру и посмотреть, кто увидит занятия и историю.

[Жизненный цикл семестра](http://127.0.0.1:14312/presentation/#semester-lifecycle) · [Жизненный цикл группы](http://127.0.0.1:14312/presentation/#group-lifecycle) · [Жизненный цикл предмета](http://127.0.0.1:14312/presentation/#subject-lifecycle) · [Назначения преподавателей](http://127.0.0.1:14312/presentation/#teacher-assignments) · [История после перевода студента](http://127.0.0.1:14312/presentation/#student-transfer-history) · [Конструктор расписания](http://127.0.0.1:14312/presentation/#schedule-constructor) · [Расписание по ролям](http://127.0.0.1:14312/presentation/#schedule-by-role)

### Провести занятие и поставить отметку

`/presentation/stories/lesson/` · 60 разборов

Открыть пару, обработать изменение, поставить отметку и решить заявку.

[Жизненный цикл пары](http://127.0.0.1:14312/presentation/#lesson-lifecycle) · [Перенос и отмена пары](http://127.0.0.1:14312/presentation/#lesson-changes) · [Домашние задания](http://127.0.0.1:14312/presentation/#homework-lifecycle) · [Геоотметка](http://127.0.0.1:14312/presentation/#geo-checkin) · [Жизненный цикл отметки](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle) · [Жизненный цикл заявки](http://127.0.0.1:14312/presentation/#request-lifecycle) · [Вложения в заявках](http://127.0.0.1:14312/presentation/#request-attachments)

### Доставить событие в PWA

`/presentation/stories/notification/` · 35 разборов

Доставить уведомление, открыть объект и понять, что доступно без сети.

[События в реальном времени](http://127.0.0.1:14312/presentation/#realtime) · [Жизненный цикл уведомления](http://127.0.0.1:14312/presentation/#notification-lifecycle) · [Переходы из уведомлений](http://127.0.0.1:14312/presentation/#notification-actions) · [Telegram-бот](http://127.0.0.1:14312/presentation/#telegram-bot) · [Подписка Web Push](http://127.0.0.1:14312/presentation/#web-push-subscription) · [Офлайн-режим PWA](http://127.0.0.1:14312/presentation/#pwa-offline)

### Собрать журнал и выгрузить отчёт

`/presentation/stories/report/` · 50 разборов

Пройти от отдельных отметок до сводки, графика и файла.

[Журнал посещаемости](http://127.0.0.1:14312/presentation/#attendance-journal) · [Статистика посещаемости](http://127.0.0.1:14312/presentation/#attendance-statistics) · [Рейтинг студентов](http://127.0.0.1:14312/presentation/#student-ranking) · [Выгрузка журнала посещаемости](http://127.0.0.1:14312/presentation/#attendance-journal-export) · [Экспорт отчётов](http://127.0.0.1:14312/presentation/#report-export) · [Данные отчёта и контракт](http://127.0.0.1:14312/presentation/#report-data-contract) · [Обзор администратора](http://127.0.0.1:14312/presentation/#admin-overview)

### Найти аудиторию на карте

`/presentation/stories/campus/` · 10 разборов

Опубликовать план, открыть нужный этаж и увидеть статистику.

[Карта кампуса](http://127.0.0.1:14312/presentation/#campus-map) · [Статистика карты](http://127.0.0.1:14312/presentation/#campus-map-statistics)

### Сохранить изменение в нескольких сервисах

`/presentation/stories/consistency/` · 36 разборов

Пройти от записи и блокировки до кэша, повтора и доставки события.

[Базы данных](http://127.0.0.1:14312/presentation/#databases) · [Транзакции и конкурентность](http://127.0.0.1:14312/presentation/#transactions) · [Кэширование](http://127.0.0.1:14312/presentation/#cache) · [Паттерны проекта](http://127.0.0.1:14312/presentation/#patterns)

### Восстановиться после сбоя и выпустить изменение

`/presentation/stories/recovery/` · 34 разбора

Ограничить работу, восстановить систему и проверить изменение перед выпуском.

[Отказоустойчивость](http://127.0.0.1:14312/presentation/#resilience) · [Бюджеты операций](http://127.0.0.1:14312/presentation/#operation-budgets) · [Восстановление, хранение и проверки](http://127.0.0.1:14312/presentation/#recovery-retention-verification) · [Тесты](http://127.0.0.1:14312/presentation/#tests) · [CI/CD](http://127.0.0.1:14312/presentation/#ci-cd)

## Переходы между страницами в варианте А

- Из архитектуры вести к аутентификации в «Безопасность и доступ», а от RabbitMQ и Outbox — к паттернам данных и доставке уведомлений.
- Из геоотметки вести к её тестам в «Надёжность, тесты и поставка». Разбор «Геоотметка на трёх уровнях» принадлежит главе о восстановлении и проверках.
- Из журнала и отчётов вести к бюджетам операций и к проверкам ролей. История состава группы остаётся в учебном администрировании.
- Демонстрации остаются при соответствующих разборах. Например, две вкладки идут с транзакциями, а открытие задания — с переходами из уведомлений.

## Исходный список в порядке лендинга

У каждой главы и разбора указан текущий якорь. Вложенные заголовки идут под своим разбором. Если у вложенного заголовка нет собственного якоря, к нему ведёт ссылка разбора.

### Вход и сервисы

Вступительные разборы от Nginx до наблюдаемости.

- Nginx · [`#architecture`](http://127.0.0.1:14312/presentation/#architecture)
- API Gateway · [`#gateway`](http://127.0.0.1:14312/presentation/#gateway)
- Auth · [`#auth`](http://127.0.0.1:14312/presentation/#auth)
- Маршрутизация Gateway · [`#gateway-routing`](http://127.0.0.1:14312/presentation/#gateway-routing)
- Трасса и корреляция · [`#correlation`](http://127.0.0.1:14312/presentation/#correlation)
- Mobile BFF · [`#checkin`](http://127.0.0.1:14312/presentation/#checkin)
- Academic · [`#academic`](http://127.0.0.1:14312/presentation/#academic)
- Schedule · [`#schedule`](http://127.0.0.1:14312/presentation/#schedule)
- Attendance · [`#transaction`](http://127.0.0.1:14312/presentation/#transaction)
- Notification Web и бот · [`#notification`](http://127.0.0.1:14312/presentation/#notification)
- Outbox и RabbitMQ · [`#events`](http://127.0.0.1:14312/presentation/#events)
- Document Renderer · [`#reports`](http://127.0.0.1:14312/presentation/#reports)
- Наблюдаемость · [`#observability`](http://127.0.0.1:14312/presentation/#observability)

### Безопасность

[`#security`](http://127.0.0.1:14312/presentation/#security)

- Вход по паролю · [`#security-login`](http://127.0.0.1:14312/presentation/#security-login)
- Смена пароля и хранение хеша · [`#security-password`](http://127.0.0.1:14312/presentation/#security-password)
- Вход из Telegram Mini App · [`#security-tma`](http://127.0.0.1:14312/presentation/#security-tma)
- Вход по коду из Telegram · [`#security-telegram-code`](http://127.0.0.1:14312/presentation/#security-telegram-code)
- QR для входа в браузере · [`#security-qr`](http://127.0.0.1:14312/presentation/#security-qr)
- Сканирование и проверка · [`#security-qr-preview`](http://127.0.0.1:14312/presentation/#security-qr-preview)
- Подтверждение входа · [`#security-qr-decision`](http://127.0.0.1:14312/presentation/#security-qr-decision)
- Обмен и сессия браузера · [`#security-qr-exchange`](http://127.0.0.1:14312/presentation/#security-qr-exchange)
- Выдача access и refresh · [`#security-tokens`](http://127.0.0.1:14312/presentation/#security-tokens)
- Ротация refresh · [`#security-refresh`](http://127.0.0.1:14312/presentation/#security-refresh)
- Доступные роли · [`#security-role`](http://127.0.0.1:14312/presentation/#security-role)
- Выбор активной роли · [`#security-role-select`](http://127.0.0.1:14312/presentation/#security-role-select)
- Новый access token · [`#security-role-token`](http://127.0.0.1:14312/presentation/#security-role-token)
- Отзыв роли и обновление интерфейса · [`#security-role-revoked`](http://127.0.0.1:14312/presentation/#security-role-revoked)
- Полный и ограниченный доступ · [`#security-admin-access`](http://127.0.0.1:14312/presentation/#security-admin-access)
- Выдача и изменение прав · [`#security-admin-access-grant`](http://127.0.0.1:14312/presentation/#security-admin-access-grant)
- Отзыв прав администратора · [`#security-admin-access-revoke`](http://127.0.0.1:14312/presentation/#security-admin-access-revoke)
- Применение прав на сервере · [`#security-admin-access-commit`](http://127.0.0.1:14312/presentation/#security-admin-access-commit)
- Выход и отзыв сессии · [`#security-logout`](http://127.0.0.1:14312/presentation/#security-logout)
- Восстановление пароля · [`#security-recovery`](http://127.0.0.1:14312/presentation/#security-recovery)
- Код в Telegram · [`#security-recovery-delivery`](http://127.0.0.1:14312/presentation/#security-recovery-delivery)
- Проверка кода · [`#security-recovery-verify`](http://127.0.0.1:14312/presentation/#security-recovery-verify)
- Новый пароль и отзыв сессий · [`#security-recovery-complete`](http://127.0.0.1:14312/presentation/#security-recovery-complete)
- Аутентификация и права · [`#security-access`](http://127.0.0.1:14312/presentation/#security-access)
- Проверка сессии и отзыв · [`#security-session`](http://127.0.0.1:14312/presentation/#security-session)
- Доступ к расписанию группы · [`#security-group`](http://127.0.0.1:14312/presentation/#security-group)
- Ограничение частоты запросов · [`#security-rate-limit`](http://127.0.0.1:14312/presentation/#security-rate-limit)
- Внутренний JWT · [`#security-internal-token`](http://127.0.0.1:14312/presentation/#security-internal-token)

### Паттерны проекта

[`#patterns`](http://127.0.0.1:14312/presentation/#patterns)

- Transactional Outbox · [`#pattern-outbox`](http://127.0.0.1:14312/presentation/#pattern-outbox)
- Публикация из outbox · [`#pattern-outbox-publisher`](http://127.0.0.1:14312/presentation/#pattern-outbox-publisher)
- Конверт события и схема · [`#pattern-envelope`](http://127.0.0.1:14312/presentation/#pattern-envelope)
- Дедупликация потребителей · [`#pattern-consumer-dedup`](http://127.0.0.1:14312/presentation/#pattern-consumer-dedup)
- Dead-letter и парковка · [`#pattern-dlq`](http://127.0.0.1:14312/presentation/#pattern-dlq)
- Повтор HTTP-команды · [`#pattern-http-replay`](http://127.0.0.1:14312/presentation/#pattern-http-replay)
- Конкурентные записи · [`#pattern-concurrency`](http://127.0.0.1:14312/presentation/#pattern-concurrency)
- Восстановимый координатор · [`#pattern-coordinator`](http://127.0.0.1:14312/presentation/#pattern-coordinator)
- Адаптеры и пределы ожидания · [`#pattern-integrations`](http://127.0.0.1:14312/presentation/#pattern-integrations)
- Сигнал и повторное чтение · [`#pattern-invalidation`](http://127.0.0.1:14312/presentation/#pattern-invalidation)
- Один запрос на поколение · [`#pattern-async-owner`](http://127.0.0.1:14312/presentation/#pattern-async-owner)
- Версия ответа и локальный снимок · [`#pattern-conditional-reads`](http://127.0.0.1:14312/presentation/#pattern-conditional-reads)

### CI/CD

[`#ci-cd`](http://127.0.0.1:14312/presentation/#ci-cd)

- Когда запускаются проверки · [`#ci-cd-triggers`](http://127.0.0.1:14312/presentation/#ci-cd-triggers)
- Vue и браузерные сценарии · [`#ci-cd-frontend`](http://127.0.0.1:14312/presentation/#ci-cd-frontend)
- Проверки Java и Python · [`#ci-cd-backend`](http://127.0.0.1:14312/presentation/#ci-cd-backend)
- Coverage, OpenAPI и Security · [`#ci-cd-quality`](http://127.0.0.1:14312/presentation/#ci-cd-quality)
- Ручной запуск релиза · [`#ci-cd-release`](http://127.0.0.1:14312/presentation/#ci-cd-release)
- Образы и манифест релиза · [`#ci-cd-images`](http://127.0.0.1:14312/presentation/#ci-cd-images)
- Применение на сервере · [`#ci-cd-apply`](http://127.0.0.1:14312/presentation/#ci-cd-apply)

### Тесты

[`#tests`](http://127.0.0.1:14312/presentation/#tests)

- Unit: решение о повторе · [`#tests-java-unit`](http://127.0.0.1:14312/presentation/#tests-java-unit)
- Vitest: логика геоотметки · [`#tests-vue-unit`](http://127.0.0.1:14312/presentation/#tests-vue-unit)
- Integration: атомарность outbox · [`#tests-integration`](http://127.0.0.1:14312/presentation/#tests-integration)
- Изолированный стенд CI · [`#tests-stand`](http://127.0.0.1:14312/presentation/#tests-stand)
- Playwright: заявка из Today · [`#tests-browser`](http://127.0.0.1:14312/presentation/#tests-browser)
- Playwright: граница Telegram · [`#tests-browser-mocks`](http://127.0.0.1:14312/presentation/#tests-browser-mocks)

### Базы данных

[`#databases`](http://127.0.0.1:14312/presentation/#databases)

- Серверы и владельцы данных · [`#databases-ownership`](http://127.0.0.1:14312/presentation/#databases-ownership)
- PostgreSQL: Auth · [`#databases-auth`](http://127.0.0.1:14312/presentation/#databases-auth)
- PostgreSQL: Academic · [`#databases-academic`](http://127.0.0.1:14312/presentation/#databases-academic)
- PostgreSQL: Schedule · [`#databases-schedule`](http://127.0.0.1:14312/presentation/#databases-schedule)
- MongoDB: Attendance · [`#databases-attendance`](http://127.0.0.1:14312/presentation/#databases-attendance)
- MongoDB: Notification Web · [`#databases-notification`](http://127.0.0.1:14312/presentation/#databases-notification)
- Redis для временного состояния · [`#databases-redis`](http://127.0.0.1:14312/presentation/#databases-redis)
- Flyway и версии схем · [`#databases-flyway`](http://127.0.0.1:14312/presentation/#databases-flyway)

### Статусы HTTP и gRPC

[`#api-statuses`](http://127.0.0.1:14312/presentation/#api-statuses)

- 200, 201, 202 · успешные ответы · [`#api-statuses-success`](http://127.0.0.1:14312/presentation/#api-statuses-success)
- 204, 205, 301, 304 · без JSON · [`#api-statuses-empty`](http://127.0.0.1:14312/presentation/#api-statuses-empty)
- 400, 405, 406, 415 · контракт запроса · [`#api-statuses-validation`](http://127.0.0.1:14312/presentation/#api-statuses-validation)
- 401, 403 · сессия и доступ · [`#api-statuses-auth`](http://127.0.0.1:14312/presentation/#api-statuses-auth)
- 404, 409, 410, 422 · ресурс и состояние · [`#api-statuses-state`](http://127.0.0.1:14312/presentation/#api-statuses-state)
- 413, 416, 426, 429 · ограничения · [`#api-statuses-limits`](http://127.0.0.1:14312/presentation/#api-statuses-limits)
- 408, 425, 500, 502, 503, 504 · сбои · [`#api-statuses-failures`](http://127.0.0.1:14312/presentation/#api-statuses-failures)
- gRPC и поля результата · [`#api-statuses-rpc`](http://127.0.0.1:14312/presentation/#api-statuses-rpc)
- gRPC · правила перевода в REST · [`#api-statuses-rpc-mapping`](http://127.0.0.1:14312/presentation/#api-statuses-rpc-mapping)
- gRPC · конфликт, сбой и диагностика · [`#api-statuses-rpc-other`](http://127.0.0.1:14312/presentation/#api-statuses-rpc-other)
- Как клиент выбирает действие · [`#api-statuses-client`](http://127.0.0.1:14312/presentation/#api-statuses-client)

### События в реальном времени

[`#realtime`](http://127.0.0.1:14312/presentation/#realtime)

- От геоотметки к событию · [`#realtime-mark`](http://127.0.0.1:14312/presentation/#realtime-mark)
- RabbitMQ и выбор получателей · [`#realtime-rabbit`](http://127.0.0.1:14312/presentation/#realtime-rabbit)
- WebSocket и STOMP · [`#realtime-socket`](http://127.0.0.1:14312/presentation/#realtime-socket)
- Журнал обновляется через REST · [`#realtime-journal`](http://127.0.0.1:14312/presentation/#realtime-journal)
- Одно событие «Занятие началось» · [`#realtime-lesson`](http://127.0.0.1:14312/presentation/#realtime-lesson)
- Лента уведомлений в Web и PWA · [`#realtime-inbox`](http://127.0.0.1:14312/presentation/#realtime-inbox)
- Web Push через service worker · [`#realtime-push`](http://127.0.0.1:14312/presentation/#realtime-push)
- Доставка в Telegram · [`#realtime-telegram`](http://127.0.0.1:14312/presentation/#realtime-telegram)

### Кэширование

[`#cache`](http://127.0.0.1:14312/presentation/#cache)

- Группа из PostgreSQL через Redis · [`#cache-group`](http://127.0.0.1:14312/presentation/#cache-group)
- Ключи, значения и TTL · [`#cache-storage`](http://127.0.0.1:14312/presentation/#cache-storage)
- Что читают из кэша · [`#cache-active`](http://127.0.0.1:14312/presentation/#cache-active)
- Где кэш обходится · [`#cache-configured`](http://127.0.0.1:14312/presentation/#cache-configured)
- Изменение группы и инвалидация · [`#cache-write`](http://127.0.0.1:14312/presentation/#cache-write)
- Сбой Redis и возврат к базе · [`#cache-failure`](http://127.0.0.1:14312/presentation/#cache-failure)
- Кэш и рабочее состояние Redis · [`#cache-boundary`](http://127.0.0.1:14312/presentation/#cache-boundary)
- Локальный слой Attendance · [`#cache-local`](http://127.0.0.1:14312/presentation/#cache-local)

### Транзакции и конкурентность

[`#transactions`](http://127.0.0.1:14312/presentation/#transactions)

- PostgreSQL: активация семестра · [`#transactions-postgres`](http://127.0.0.1:14312/presentation/#transactions-postgres)
- A и B активируют один семестр · [`#transactions-postgres-ab`](http://127.0.0.1:14312/presentation/#transactions-postgres-ab)
- Снимок чтения и блокировка записи · [`#transactions-isolation`](http://127.0.0.1:14312/presentation/#transactions-isolation)
- MongoDB: отметка и квитанция · [`#transactions-mongo`](http://127.0.0.1:14312/presentation/#transactions-mongo)
- A и B отправляют одну геоотметку · [`#transactions-mongo-ab`](http://127.0.0.1:14312/presentation/#transactions-mongo-ab)
- Ручная отметка и ожидаемая версия · [`#transactions-revision`](http://127.0.0.1:14312/presentation/#transactions-revision)
  - Две вкладки · одна строка · [`#demo-tabs-title`](http://127.0.0.1:14312/presentation/#demo-tabs-title)
  - Вкладка A
  - Вкладка B
- Откат, неизвестный коммит и повтор · [`#transactions-retry`](http://127.0.0.1:14312/presentation/#transactions-retry)
- Границы между сервисами · [`#transactions-boundary`](http://127.0.0.1:14312/presentation/#transactions-boundary)

### Отказоустойчивость

[`#resilience`](http://127.0.0.1:14312/presentation/#resilience)

- Дедлайн чтения посещаемости · [`#resilience-timeout`](http://127.0.0.1:14312/presentation/#resilience-timeout)
- Повтор чтения в интерфейсе · [`#resilience-retry`](http://127.0.0.1:14312/presentation/#resilience-retry)
- Когда HTTP-сервис недоступен · [`#resilience-http`](http://127.0.0.1:14312/presentation/#resilience-http)
- Запись с неопределённым исходом · [`#resilience-writes`](http://127.0.0.1:14312/presentation/#resilience-writes)
- PWA в режиме чтения · [`#resilience-offline`](http://127.0.0.1:14312/presentation/#resilience-offline)
- Если недоступна проверка Auth · [`#resilience-authority`](http://127.0.0.1:14312/presentation/#resilience-authority)
- RabbitMQ недоступен после коммита · [`#resilience-outbox`](http://127.0.0.1:14312/presentation/#resilience-outbox)
  - Сбой и восстановление · [`#demo-outbox-recovery-title`](http://127.0.0.1:14312/presentation/#demo-outbox-recovery-title)
  - Локальное состояние · JSON · [`#demo-outbox-recovery-json-title`](http://127.0.0.1:14312/presentation/#demo-outbox-recovery-json-title)
- Возобновление операции между сервисами · [`#resilience-resume`](http://127.0.0.1:14312/presentation/#resilience-resume)
- Готовность и перезапуск контейнера · [`#resilience-restart`](http://127.0.0.1:14312/presentation/#resilience-restart)

### Бюджеты операций

[`#operation-budgets`](http://127.0.0.1:14312/presentation/#operation-budgets)

- Остаток времени для отчёта · [`#operation-budgets-remaining`](http://127.0.0.1:14312/presentation/#operation-budgets-remaining)
- Три чтения геолокации · [`#operation-budgets-geolocation`](http://127.0.0.1:14312/presentation/#operation-budgets-geolocation)
- Аренда и повторы задачи · [`#operation-budgets-attempts`](http://127.0.0.1:14312/presentation/#operation-budgets-attempts)
- Частота входящих команд · [`#operation-budgets-frequency`](http://127.0.0.1:14312/presentation/#operation-budgets-frequency)
- Очередь, параллелизм и размер файла · [`#operation-budgets-capacity`](http://127.0.0.1:14312/presentation/#operation-budgets-capacity)
- Память и время рендера · [`#operation-budgets-render`](http://127.0.0.1:14312/presentation/#operation-budgets-render)
- Доставка события и квота заявок · [`#operation-budgets-other`](http://127.0.0.1:14312/presentation/#operation-budgets-other)

### Производительность

[`#performance`](http://127.0.0.1:14312/presentation/#performance)

- Индексы PostgreSQL · [`#performance-postgres`](http://127.0.0.1:14312/presentation/#performance-postgres)
- Составные индексы MongoDB · [`#performance-mongo`](http://127.0.0.1:14312/presentation/#performance-mongo)
- Страницы по номеру · [`#performance-offset`](http://127.0.0.1:14312/presentation/#performance-offset)
- Страницы по курсору · [`#performance-cursor`](http://127.0.0.1:14312/presentation/#performance-cursor)
- Одно пакетное чтение по ID · [`#performance-bulk`](http://127.0.0.1:14312/presentation/#performance-bulk)
- Ограничение параллельных чтений · [`#performance-batches`](http://127.0.0.1:14312/presentation/#performance-batches)
- Код по маршруту и по действию · [`#performance-web`](http://127.0.0.1:14312/presentation/#performance-web)
- Размер сборки лендинга · [`#performance-landing`](http://127.0.0.1:14312/presentation/#performance-landing)
- Статические файлы и установка PWA · [`#performance-pwa`](http://127.0.0.1:14312/presentation/#performance-pwa)

### Офлайн-режим PWA

[`#pwa-offline`](http://127.0.0.1:14312/presentation/#pwa-offline)

- Оболочка на устройстве · [`#pwa-offline-install`](http://127.0.0.1:14312/presentation/#pwa-offline-install)
- Что перехватывает worker · [`#pwa-offline-requests`](http://127.0.0.1:14312/presentation/#pwa-offline-requests)
- Снимок студента в IndexedDB · [`#pwa-offline-storage`](http://127.0.0.1:14312/presentation/#pwa-offline-storage)
- Контекст снимка и живая сессия · [`#pwa-offline-ownership`](http://127.0.0.1:14312/presentation/#pwa-offline-ownership)
- Расписание и задания без сети · [`#pwa-offline-screens`](http://127.0.0.1:14312/presentation/#pwa-offline-screens)
- Когда открывается снимок · [`#pwa-offline-admission`](http://127.0.0.1:14312/presentation/#pwa-offline-admission)
- Выход и очистка устройства · [`#pwa-offline-cleanup`](http://127.0.0.1:14312/presentation/#pwa-offline-cleanup)
- Проверка после возврата сети · [`#pwa-offline-reconnect`](http://127.0.0.1:14312/presentation/#pwa-offline-reconnect)
- Если ответ на отметку потерян · [`#pwa-offline-pending`](http://127.0.0.1:14312/presentation/#pwa-offline-pending)

### Экспорт отчётов

[`#report-export`](http://127.0.0.1:14312/presentation/#report-export)

- Какие отчёты и форматы доступны · [`#report-export-formats`](http://127.0.0.1:14312/presentation/#report-export-formats)
- Запрос недельного журнала · [`#report-export-request`](http://127.0.0.1:14312/presentation/#report-export-request)
- Повтор и ожидание результата · [`#report-export-status`](http://127.0.0.1:14312/presentation/#report-export-status)
- Фоновая задача и повтор после сбоя · [`#report-export-worker`](http://127.0.0.1:14312/presentation/#report-export-worker)
- От плана до PDF · [`#report-export-render`](http://127.0.0.1:14312/presentation/#report-export-render)
- Хранение и готовность файла · [`#report-export-retention`](http://127.0.0.1:14312/presentation/#report-export-retention)
- Короткий билет на скачивание · [`#report-export-ticket`](http://127.0.0.1:14312/presentation/#report-export-ticket)
- Как скачивает PWA · [`#report-export-pwa`](http://127.0.0.1:14312/presentation/#report-export-pwa)
- Скачивание в Telegram Mini App · [`#report-export-telegram`](http://127.0.0.1:14312/presentation/#report-export-telegram)

### Вложения в заявках

[`#request-attachments`](http://127.0.0.1:14312/presentation/#request-attachments)

- Заявка и файлы в одном запросе · [`#request-attachments-upload`](http://127.0.0.1:14312/presentation/#request-attachments-upload)
- Лимиты на клиенте и сервере · [`#request-attachments-limits`](http://127.0.0.1:14312/presentation/#request-attachments-limits)
- Проверка формата и размера · [`#request-attachments-validation`](http://127.0.0.1:14312/presentation/#request-attachments-validation)
- Где сохраняются байты · [`#request-attachments-storage`](http://127.0.0.1:14312/presentation/#request-attachments-storage)
- Студент открывает свою заявку · [`#request-attachments-own`](http://127.0.0.1:14312/presentation/#request-attachments-own)
- Доступ старосты и преподавателя · [`#request-attachments-review`](http://127.0.0.1:14312/presentation/#request-attachments-review)
- Документ в посещаемости · [`#request-attachments-attendance`](http://127.0.0.1:14312/presentation/#request-attachments-attendance)
- Год хранения и очистка · [`#request-attachments-retention`](http://127.0.0.1:14312/presentation/#request-attachments-retention)
- Открытие, предпросмотр и освобождение URL · [`#request-attachments-preview`](http://127.0.0.1:14312/presentation/#request-attachments-preview)

### Геоотметка

[`#geo-checkin`](http://127.0.0.1:14312/presentation/#geo-checkin)

- Как PWA получает позицию · [`#geo-checkin-browser`](http://127.0.0.1:14312/presentation/#geo-checkin-browser)
- Позиция и разрешение в Mini App · [`#geo-checkin-telegram`](http://127.0.0.1:14312/presentation/#geo-checkin-telegram)
- Три чтения в одном бюджете · [`#geo-checkin-acquisition`](http://127.0.0.1:14312/presentation/#geo-checkin-acquisition)
- Команда и диагностика · [`#geo-checkin-wire`](http://127.0.0.1:14312/presentation/#geo-checkin-wire)
- Серверный радиус 400 м · [`#geo-checkin-radius`](http://127.0.0.1:14312/presentation/#geo-checkin-radius)
- Когда разрешена геоотметка · [`#geo-checkin-eligibility`](http://127.0.0.1:14312/presentation/#geo-checkin-eligibility)
- Отметка или заявка на подтверждение · [`#geo-checkin-outcomes`](http://127.0.0.1:14312/presentation/#geo-checkin-outcomes)
- Новая геопопытка после ожидания · [`#geo-checkin-cooldown`](http://127.0.0.1:14312/presentation/#geo-checkin-cooldown)
- Повтор команды и повтор транзакции · [`#geo-checkin-retry`](http://127.0.0.1:14312/presentation/#geo-checkin-retry)

### Жизненный цикл заявки

[`#request-lifecycle`](http://127.0.0.1:14312/presentation/#request-lifecycle)

- Подать уважительную причину · [`#request-lifecycle-excuse`](http://127.0.0.1:14312/presentation/#request-lifecycle-excuse)
- Подать «Забыл отметиться» · [`#request-lifecycle-late`](http://127.0.0.1:14312/presentation/#request-lifecycle-late)
- Пять ручных заявок на семестр · [`#request-lifecycle-quota`](http://127.0.0.1:14312/presentation/#request-lifecycle-quota)
- Открыть и проверить заявку · [`#request-lifecycle-review`](http://127.0.0.1:14312/presentation/#request-lifecycle-review)
- Одобрить уважительную заявку · [`#request-lifecycle-approve-excuse`](http://127.0.0.1:14312/presentation/#request-lifecycle-approve-excuse)
- Одобрить позднюю отметку или отклонить · [`#request-lifecycle-approve-late`](http://127.0.0.1:14312/presentation/#request-lifecycle-approve-late)
- Отмена и повтор решения · [`#request-lifecycle-recovery`](http://127.0.0.1:14312/presentation/#request-lifecycle-recovery)
- Как интерфейс показывает результат · [`#request-lifecycle-refresh`](http://127.0.0.1:14312/presentation/#request-lifecycle-refresh)

### Перенос и отмена пары

[`#lesson-changes`](http://127.0.0.1:14312/presentation/#lesson-changes)

- Выбрать новый слот в журнале · [`#lesson-changes-choose`](http://127.0.0.1:14312/presentation/#lesson-changes-choose)
- Что проверяет Schedule · [`#lesson-changes-guards`](http://127.0.0.1:14312/presentation/#lesson-changes-guards)
- Исходная и новая пара · [`#lesson-changes-schedule`](http://127.0.0.1:14312/presentation/#lesson-changes-schedule)
  - Перенести пару · [`#demo-lesson-transfer-title`](http://127.0.0.1:14312/presentation/#demo-lesson-transfer-title)
  - Локальное состояние · JSON · [`#demo-lesson-transfer-json-title`](http://127.0.0.1:14312/presentation/#demo-lesson-transfer-json-title)
- Отметки и заявки при переносе · [`#lesson-changes-participants`](http://127.0.0.1:14312/presentation/#lesson-changes-participants)
- Проверить итог переноса · [`#lesson-changes-completion`](http://127.0.0.1:14312/presentation/#lesson-changes-completion)
- Отменить пару из журнала · [`#lesson-changes-cancel`](http://127.0.0.1:14312/presentation/#lesson-changes-cancel)
- Отмена пары и её отметки · [`#lesson-changes-cancelled`](http://127.0.0.1:14312/presentation/#lesson-changes-cancelled)
- Заявки и отдельное событие разового слота · [`#lesson-changes-cancel-requests`](http://127.0.0.1:14312/presentation/#lesson-changes-cancel-requests)
- Когда узнают об изменении · [`#lesson-changes-notifications`](http://127.0.0.1:14312/presentation/#lesson-changes-notifications)

### Домашние задания

[`#homework-lifecycle`](http://127.0.0.1:14312/presentation/#homework-lifecycle)

- Выдать задание своей группе · [`#homework-lifecycle-assign`](http://127.0.0.1:14312/presentation/#homework-lifecycle-assign)
- Привязать к паре или дате · [`#homework-lifecycle-bind`](http://127.0.0.1:14312/presentation/#homework-lifecycle-bind)
- Опубликовать после подтверждения · [`#homework-lifecycle-publish`](http://127.0.0.1:14312/presentation/#homework-lifecycle-publish)
- Изменить задание с проверкой версии · [`#homework-lifecycle-edit`](http://127.0.0.1:14312/presentation/#homework-lifecycle-edit)
- Прочитать и отметить выполнение · [`#homework-lifecycle-student`](http://127.0.0.1:14312/presentation/#homework-lifecycle-student)
- Получить событие о публикации · [`#homework-lifecycle-notify`](http://127.0.0.1:14312/presentation/#homework-lifecycle-notify)
- Сохранить задание при переносе пары · [`#homework-lifecycle-transfer`](http://127.0.0.1:14312/presentation/#homework-lifecycle-transfer)
- Открыть архив заданий · [`#homework-lifecycle-archive`](http://127.0.0.1:14312/presentation/#homework-lifecycle-archive)
- Снять задание с публикации · [`#homework-lifecycle-withdraw`](http://127.0.0.1:14312/presentation/#homework-lifecycle-withdraw)

### Конструктор расписания

[`#schedule-constructor`](http://127.0.0.1:14312/presentation/#schedule-constructor)

- Переключить учебную неделю · [`#schedule-constructor-weeks`](http://127.0.0.1:14312/presentation/#schedule-constructor-weeks)
- Собрать шаблон пары · [`#schedule-constructor-create`](http://127.0.0.1:14312/presentation/#schedule-constructor-create)
- Создать пары на даты семестра · [`#schedule-constructor-materialize`](http://127.0.0.1:14312/presentation/#schedule-constructor-materialize)
- Обработать уже прошедшие даты · [`#schedule-constructor-historical`](http://127.0.0.1:14312/presentation/#schedule-constructor-historical)
- Повторить запрос без второй серии · [`#schedule-constructor-replay`](http://127.0.0.1:14312/presentation/#schedule-constructor-replay)
- Рассчитать изменение и сверить версию · [`#schedule-constructor-preview`](http://127.0.0.1:14312/presentation/#schedule-constructor-preview)
- Сопоставить будущие пары по неделям · [`#schedule-constructor-future`](http://127.0.0.1:14312/presentation/#schedule-constructor-future)
- Дождаться переноса связанных данных · [`#schedule-constructor-apply`](http://127.0.0.1:14312/presentation/#schedule-constructor-apply)
- Проверить, можно ли удалить серию · [`#schedule-constructor-delete`](http://127.0.0.1:14312/presentation/#schedule-constructor-delete)

### Жизненный цикл семестра

[`#semester-lifecycle`](http://127.0.0.1:14312/presentation/#semester-lifecycle)

- Создать семестр · [`#semester-lifecycle-create`](http://127.0.0.1:14312/presentation/#semester-lifecycle-create)
- Активировать сейчас или запланировать · [`#semester-lifecycle-activate`](http://127.0.0.1:14312/presentation/#semester-lifecycle-activate)
- Выполнить план по московской дате · [`#semester-lifecycle-queue`](http://127.0.0.1:14312/presentation/#semester-lifecycle-queue)
- Переключить активный семестр · [`#semester-lifecycle-switch`](http://127.0.0.1:14312/presentation/#semester-lifecycle-switch)
- Начать архивирование · [`#semester-lifecycle-archive`](http://127.0.0.1:14312/presentation/#semester-lifecycle-archive)
- Закрыть записи и сохранить историю · [`#semester-lifecycle-effects`](http://127.0.0.1:14312/presentation/#semester-lifecycle-effects)
- Восстановить и затем активировать · [`#semester-lifecycle-restore`](http://127.0.0.1:14312/presentation/#semester-lifecycle-restore)
- Отличить архив от удаления · [`#semester-lifecycle-delete`](http://127.0.0.1:14312/presentation/#semester-lifecycle-delete)

### Жизненный цикл группы

[`#group-lifecycle`](http://127.0.0.1:14312/presentation/#group-lifecycle)

- Создать группу в реестре · [`#group-lifecycle-create`](http://127.0.0.1:14312/presentation/#group-lifecycle-create)
- Назначить или заменить старосту · [`#group-lifecycle-headman`](http://127.0.0.1:14312/presentation/#group-lifecycle-headman)
- Перевести студента в другую группу · [`#group-lifecycle-transfer`](http://127.0.0.1:14312/presentation/#group-lifecycle-transfer)
- Записать членство на дату перевода · [`#group-lifecycle-history`](http://127.0.0.1:14312/presentation/#group-lifecycle-history)
- Дождаться подтверждения операции · [`#group-lifecycle-confirm`](http://127.0.0.1:14312/presentation/#group-lifecycle-confirm)
- Архивировать группу вручную · [`#group-lifecycle-archive`](http://127.0.0.1:14312/presentation/#group-lifecycle-archive)
- Перевести курс или завершить выпуск · [`#group-lifecycle-cycle`](http://127.0.0.1:14312/presentation/#group-lifecycle-cycle)
- Сохранить расписание и посещаемость · [`#group-lifecycle-effects`](http://127.0.0.1:14312/presentation/#group-lifecycle-effects)
- Вернуть группу из архива · [`#group-lifecycle-restore`](http://127.0.0.1:14312/presentation/#group-lifecycle-restore)

### Назначения преподавателей

[`#teacher-assignments`](http://127.0.0.1:14312/presentation/#teacher-assignments)

- Открыть предмет своей группы · [`#teacher-assignments-roles`](http://127.0.0.1:14312/presentation/#teacher-assignments-roles)
- Задать тип пары и период · [`#teacher-assignments-add`](http://127.0.0.1:14312/presentation/#teacher-assignments-add)
- Проверить дату и подтвердить замену · [`#teacher-assignments-prepare`](http://127.0.0.1:14312/presentation/#teacher-assignments-prepare)
- Согласовать замену с расписанием · [`#teacher-assignments-confirm`](http://127.0.0.1:14312/presentation/#teacher-assignments-confirm)
- Переключить шаблоны серии · [`#teacher-assignments-series`](http://127.0.0.1:14312/presentation/#teacher-assignments-series)
- Сохранить ID пары и её историю · [`#teacher-assignments-pairs`](http://127.0.0.1:14312/presentation/#teacher-assignments-pairs)
- Проверить доступ к журналу · [`#teacher-assignments-journal`](http://127.0.0.1:14312/presentation/#teacher-assignments-journal)
- Открыть своё расписание на день · [`#teacher-assignments-snapshot`](http://127.0.0.1:14312/presentation/#teacher-assignments-snapshot)
- Закрыть назначение без замены · [`#teacher-assignments-close`](http://127.0.0.1:14312/presentation/#teacher-assignments-close)

### Жизненный цикл предмета

[`#subject-lifecycle`](http://127.0.0.1:14312/presentation/#subject-lifecycle)

- Создать предмет для своей группы · [`#subject-lifecycle-create`](http://127.0.0.1:14312/presentation/#subject-lifecycle-create)
- Изменить название и типы занятий · [`#subject-lifecycle-edit`](http://127.0.0.1:14312/presentation/#subject-lifecycle-edit)
- Проверить последствия удаления · [`#subject-lifecycle-preview`](http://127.0.0.1:14312/presentation/#subject-lifecycle-preview)
- Подтвердить намерение · [`#subject-lifecycle-auth`](http://127.0.0.1:14312/presentation/#subject-lifecycle-auth)
- Проверить ход принятой операции · [`#subject-lifecycle-replay`](http://127.0.0.1:14312/presentation/#subject-lifecycle-replay)
- Закрепить снимок у участников · [`#subject-lifecycle-commit`](http://127.0.0.1:14312/presentation/#subject-lifecycle-commit)
- Убрать пары, шаблоны и домашние задания · [`#subject-lifecycle-schedule`](http://127.0.0.1:14312/presentation/#subject-lifecycle-schedule)
- Сохранить части заявок по другим предметам · [`#subject-lifecycle-attendance`](http://127.0.0.1:14312/presentation/#subject-lifecycle-attendance)

### Жизненный цикл пользователя

[`#user-lifecycle`](http://127.0.0.1:14312/presentation/#user-lifecycle)

- Создать пользователя · [`#user-lifecycle-create`](http://127.0.0.1:14312/presentation/#user-lifecycle-create)
- Согласовать профиль и учётную запись · [`#user-lifecycle-coordinate`](http://127.0.0.1:14312/presentation/#user-lifecycle-coordinate)
- Начать историю членства · [`#user-lifecycle-membership`](http://127.0.0.1:14312/presentation/#user-lifecycle-membership)
- Проверить последствия архивации · [`#user-lifecycle-preview`](http://127.0.0.1:14312/presentation/#user-lifecycle-preview)
- Подтвердить архив с учётом полномочий · [`#user-lifecycle-guards`](http://127.0.0.1:14312/presentation/#user-lifecycle-guards)
- Отозвать роли и сессии · [`#user-lifecycle-archive`](http://127.0.0.1:14312/presentation/#user-lifecycle-archive)
- Восстановить аккаунт · [`#user-lifecycle-restore`](http://127.0.0.1:14312/presentation/#user-lifecycle-restore)
- Назначить роли заново · [`#user-lifecycle-roles`](http://127.0.0.1:14312/presentation/#user-lifecycle-roles)
- Проверить уже принятую операцию · [`#user-lifecycle-recovery`](http://127.0.0.1:14312/presentation/#user-lifecycle-recovery)

### Жизненный цикл уведомления

[`#notification-lifecycle`](http://127.0.0.1:14312/presentation/#notification-lifecycle)

- Найти получателей по событию · [`#notification-lifecycle-audience`](http://127.0.0.1:14312/presentation/#notification-lifecycle-audience)
- Проверить преподавателя и администратора · [`#notification-lifecycle-permissions`](http://127.0.0.1:14312/presentation/#notification-lifecycle-permissions)
- Записать событие в ленту · [`#notification-lifecycle-history`](http://127.0.0.1:14312/presentation/#notification-lifecycle-history)
- Доставить Web Push в PWA · [`#notification-lifecycle-push`](http://127.0.0.1:14312/presentation/#notification-lifecycle-push)
- Настроить типы и каналы · [`#notification-lifecycle-settings`](http://127.0.0.1:14312/presentation/#notification-lifecycle-settings)
- Отправить сообщение в Telegram · [`#notification-lifecycle-telegram`](http://127.0.0.1:14312/presentation/#notification-lifecycle-telegram)
- Прочитать ленту текущей роли · [`#notification-lifecycle-inbox`](http://127.0.0.1:14312/presentation/#notification-lifecycle-inbox)
- Изменить состояние одной карточки · [`#notification-lifecycle-read`](http://127.0.0.1:14312/presentation/#notification-lifecycle-read)
- Отметить всё прочитанным · [`#notification-lifecycle-all`](http://127.0.0.1:14312/presentation/#notification-lifecycle-all)

### Жизненный цикл пары

[`#lesson-lifecycle`](http://127.0.0.1:14312/presentation/#lesson-lifecycle)

- Пара появляется в расписании · [`#lesson-lifecycle-schedule`](http://127.0.0.1:14312/presentation/#lesson-lifecycle-schedule)
- Автоматическое начало · [`#lesson-lifecycle-start`](http://127.0.0.1:14312/presentation/#lesson-lifecycle-start)
- Из транзакции в уведомления · [`#lesson-lifecycle-outbox`](http://127.0.0.1:14312/presentation/#lesson-lifecycle-outbox)
- Когда можно отметиться · [`#lesson-lifecycle-window`](http://127.0.0.1:14312/presentation/#lesson-lifecycle-window)
- Два момента для напоминаний · [`#lesson-lifecycle-reminders`](http://127.0.0.1:14312/presentation/#lesson-lifecycle-reminders)
- После отметки напоминаний меньше · [`#lesson-lifecycle-marked`](http://127.0.0.1:14312/presentation/#lesson-lifecycle-marked)
- Закрытие и пропущенный проход · [`#lesson-lifecycle-close`](http://127.0.0.1:14312/presentation/#lesson-lifecycle-close)
- Что меняется в журнале · [`#lesson-lifecycle-journal`](http://127.0.0.1:14312/presentation/#lesson-lifecycle-journal)

### Жизненный цикл отметки

[`#attendance-mark-lifecycle`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle)

- Отметка студента · [`#attendance-mark-lifecycle-checkin`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle-checkin)
- Одна запись на пару и студента · [`#attendance-mark-lifecycle-storage`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle-storage)
- Кто может менять журнал · [`#attendance-mark-lifecycle-access`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle-access)
- Правка и конфликт версий · [`#attendance-mark-lifecycle-revision`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle-revision)
- Решение заявки и снятие отметки · [`#attendance-mark-lifecycle-decisions`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle-decisions)
- Что видно в истории пары · [`#attendance-mark-lifecycle-audit`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle-audit)
- Запись и сигнал обновления · [`#attendance-mark-lifecycle-events`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle-events)
- Как меняются доли в статистике · [`#attendance-mark-lifecycle-statistics`](http://127.0.0.1:14312/presentation/#attendance-mark-lifecycle-statistics)

### Статистика посещаемости

[`#attendance-statistics`](http://127.0.0.1:14312/presentation/#attendance-statistics)

- Как считается процент · [`#attendance-statistics-denominator`](http://127.0.0.1:14312/presentation/#attendance-statistics-denominator)
  - Калькулятор четырёх пар · [`#demo-attendance-calculator-title`](http://127.0.0.1:14312/presentation/#demo-attendance-calculator-title)
  - Локальное состояние · JSON · [`#demo-attendance-calculator-json-title`](http://127.0.0.1:14312/presentation/#demo-attendance-calculator-json-title)
- Студент · личная статистика · [`#attendance-statistics-student`](http://127.0.0.1:14312/presentation/#attendance-statistics-student)
- Студент · предмет и график · [`#attendance-statistics-student-filters`](http://127.0.0.1:14312/presentation/#attendance-statistics-student-filters)
- Староста · сводка группы · [`#attendance-statistics-headman`](http://127.0.0.1:14312/presentation/#attendance-statistics-headman)
- Староста · сегодня и динамика · [`#attendance-statistics-headman-time`](http://127.0.0.1:14312/presentation/#attendance-statistics-headman-time)
- Преподаватель · группа и предмет · [`#attendance-statistics-teacher`](http://127.0.0.1:14312/presentation/#attendance-statistics-teacher)
- Преподаватель · один студент · [`#attendance-statistics-teacher-student`](http://127.0.0.1:14312/presentation/#attendance-statistics-teacher-student)
- Администратор · сравнение групп · [`#attendance-statistics-admin`](http://127.0.0.1:14312/presentation/#attendance-statistics-admin)
- Ноль процентов и отсутствие данных · [`#attendance-statistics-empty`](http://127.0.0.1:14312/presentation/#attendance-statistics-empty)

### Рейтинг студентов

[`#student-ranking`](http://127.0.0.1:14312/presentation/#student-ranking)

- Сессия и семестр · [`#student-ranking-identity`](http://127.0.0.1:14312/presentation/#student-ranking-identity)
- Кто входит в состав · [`#student-ranking-cohort`](http://127.0.0.1:14312/presentation/#student-ranking-cohort)
- Общий знаменатель группы · [`#student-ranking-held`](http://127.0.0.1:14312/presentation/#student-ranking-held)
- Почему места идут 1, 2, 2, 4 · [`#student-ranking-ties`](http://127.0.0.1:14312/presentation/#student-ranking-ties)
  - Место по точной доле · [`#demo-student-ranking-title`](http://127.0.0.1:14312/presentation/#demo-student-ranking-title)
  - Локальное состояние · JSON · [`#demo-student-ranking-json-title`](http://127.0.0.1:14312/presentation/#demo-student-ranking-json-title)
- Своё место и отсутствие данных · [`#student-ranking-empty`](http://127.0.0.1:14312/presentation/#student-ranking-empty)
- Что может увидеть староста · [`#student-ranking-headman`](http://127.0.0.1:14312/presentation/#student-ranking-headman)
- Как раскрывается список · [`#student-ranking-pages`](http://127.0.0.1:14312/presentation/#student-ranking-pages)

### Журнал посещаемости

[`#attendance-journal`](http://127.0.0.1:14312/presentation/#attendance-journal)

- Староста · список одной пары · [`#attendance-journal-headman`](http://127.0.0.1:14312/presentation/#attendance-journal-headman)
- Права и пустая отметка · [`#attendance-journal-permissions`](http://127.0.0.1:14312/presentation/#attendance-journal-permissions)
- Преподаватель · выбор журнала · [`#attendance-journal-teacher`](http://127.0.0.1:14312/presentation/#attendance-journal-teacher)
- Как строится таблица · [`#attendance-journal-matrix`](http://127.0.0.1:14312/presentation/#attendance-journal-matrix)
- Отсутствие, заявка и прочерк · [`#attendance-journal-cells`](http://127.0.0.1:14312/presentation/#attendance-journal-cells)
- Страницы и общий итог · [`#attendance-journal-pages`](http://127.0.0.1:14312/presentation/#attendance-journal-pages)
- График группы и студента · [`#attendance-journal-chart`](http://127.0.0.1:14312/presentation/#attendance-journal-chart)
- Порог подсвечивает долю · [`#attendance-journal-threshold`](http://127.0.0.1:14312/presentation/#attendance-journal-threshold)
  - Порог и округление · [`#demo-journal-threshold-title`](http://127.0.0.1:14312/presentation/#demo-journal-threshold-title)
  - Локальное состояние · JSON · [`#demo-journal-threshold-json-title`](http://127.0.0.1:14312/presentation/#demo-journal-threshold-json-title)

### Выгрузка журнала посещаемости

[`#attendance-journal-export`](http://127.0.0.1:14312/presentation/#attendance-journal-export)

- Пять форматов журнала · [`#attendance-journal-export-formats`](http://127.0.0.1:14312/presentation/#attendance-journal-export-formats)
- Староста · отчёт по неделям · [`#attendance-journal-export-weekly`](http://127.0.0.1:14312/presentation/#attendance-journal-export-weekly)
- Староста · журнал по предмету · [`#attendance-journal-export-subject`](http://127.0.0.1:14312/presentation/#attendance-journal-export-subject)
- Матрица в файле старосты · [`#attendance-journal-export-headman-file`](http://127.0.0.1:14312/presentation/#attendance-journal-export-headman-file)
- Преподаватель · период или пара · [`#attendance-journal-export-teacher`](http://127.0.0.1:14312/presentation/#attendance-journal-export-teacher)
- Отбор пар и исторического состава · [`#attendance-journal-export-plan`](http://127.0.0.1:14312/presentation/#attendance-journal-export-plan)
- Отметки и итоги преподавателя · [`#attendance-journal-export-teacher-file`](http://127.0.0.1:14312/presentation/#attendance-journal-export-teacher-file)
  - Превью выгрузки журнала · [`#demo-journal-export-title`](http://127.0.0.1:14312/presentation/#demo-journal-export-title)
  - Локальное состояние · JSON · [`#demo-journal-export-json-title`](http://127.0.0.1:14312/presentation/#demo-journal-export-json-title)
- Формирование и скачивание файла · [`#attendance-journal-export-binary`](http://127.0.0.1:14312/presentation/#attendance-journal-export-binary)

### Карта кампуса

[`#campus-map`](http://127.0.0.1:14312/presentation/#campus-map)

- Публикация плана этажа · [`#campus-map-publish`](http://127.0.0.1:14312/presentation/#campus-map-publish)
- Версия плана и ревизия каталога · [`#campus-map-versions`](http://127.0.0.1:14312/presentation/#campus-map-versions)
  - Версии плана этажа · [`#demo-campus-versions-title`](http://127.0.0.1:14312/presentation/#demo-campus-versions-title)
  - Локальное состояние · JSON · [`#demo-campus-versions-json-title`](http://127.0.0.1:14312/presentation/#demo-campus-versions-json-title)
- Каталог корпусов и этажей · [`#campus-map-manifest`](http://127.0.0.1:14312/presentation/#campus-map-manifest)
- От этажа к файлу · [`#campus-map-asset`](http://127.0.0.1:14312/presentation/#campus-map-asset)
- Формат и масштаб на устройстве · [`#campus-map-viewer`](http://127.0.0.1:14312/presentation/#campus-map-viewer)

### Статистика карты

[`#campus-map-statistics`](http://127.0.0.1:14312/presentation/#campus-map-statistics)

- Что считается открытием · [`#campus-map-statistics-open`](http://127.0.0.1:14312/presentation/#campus-map-statistics-open)
- Повтор и новый просмотр · [`#campus-map-statistics-retry`](http://127.0.0.1:14312/presentation/#campus-map-statistics-retry)
- Счётчики по этажу и UTC · [`#campus-map-statistics-aggregate`](http://127.0.0.1:14312/presentation/#campus-map-statistics-aggregate)
- Кто видит статистику карты · [`#campus-map-statistics-admin`](http://127.0.0.1:14312/presentation/#campus-map-statistics-admin)
- Обновление обзора и периоды · [`#campus-map-statistics-dashboard`](http://127.0.0.1:14312/presentation/#campus-map-statistics-dashboard)

### Помощники старосты

[`#headman-assistants`](http://127.0.0.1:14312/presentation/#headman-assistants)

- Права в своей группе · [`#headman-assistants-grant`](http://127.0.0.1:14312/presentation/#headman-assistants-grant)
- Обновление по снимку · [`#headman-assistants-change`](http://127.0.0.1:14312/presentation/#headman-assistants-change)
- Снятие последнего права · [`#headman-assistants-revoke`](http://127.0.0.1:14312/presentation/#headman-assistants-revoke)
- Доступ во время работы · [`#headman-assistants-refresh`](http://127.0.0.1:14312/presentation/#headman-assistants-refresh)

### Доступ к паре и заявке

[`#lesson-request-access`](http://127.0.0.1:14312/presentation/#lesson-request-access)

- Пара в расписании и отмена · [`#lesson-request-access-lesson`](http://127.0.0.1:14312/presentation/#lesson-request-access-lesson)
- Студент · своя заявка · [`#lesson-request-access-student`](http://127.0.0.1:14312/presentation/#lesson-request-access-student)
- Староста · решение по заявке · [`#lesson-request-access-decision`](http://127.0.0.1:14312/presentation/#lesson-request-access-decision)
- Преподаватель · разрешённые пары заявки · [`#lesson-request-access-teacher`](http://127.0.0.1:14312/presentation/#lesson-request-access-teacher)

### Обзор администратора

[`#admin-overview`](http://127.0.0.1:14312/presentation/#admin-overview)

- Текущие числа и прирост · [`#admin-overview-scale`](http://127.0.0.1:14312/presentation/#admin-overview-scale)
- Сегодня, вчера и график · [`#admin-overview-window`](http://127.0.0.1:14312/presentation/#admin-overview-window)
- Что считается отметкой · [`#admin-overview-activity`](http://127.0.0.1:14312/presentation/#admin-overview-activity)
- Количество и процентные пункты · [`#admin-overview-comparison`](http://127.0.0.1:14312/presentation/#admin-overview-comparison)

### Расписание по ролям

[`#schedule-by-role`](http://127.0.0.1:14312/presentation/#schedule-by-role)

- Студент · сегодня и семестр · [`#schedule-by-role-student`](http://127.0.0.1:14312/presentation/#schedule-by-role-student)
- Староста · дата и номер пары · [`#schedule-by-role-headman`](http://127.0.0.1:14312/presentation/#schedule-by-role-headman)
- Преподаватель · пары и назначения · [`#schedule-by-role-teacher`](http://127.0.0.1:14312/presentation/#schedule-by-role-teacher)
- Администратор · чтение через API · [`#schedule-by-role-admin`](http://127.0.0.1:14312/presentation/#schedule-by-role-admin)

### История после перевода студента

[`#student-transfer-history`](http://127.0.0.1:14312/presentation/#student-transfer-history)

- Команда перевода · [`#student-transfer-history-command`](http://127.0.0.1:14312/presentation/#student-transfer-history-command)
- Одна дата для двух интервалов · [`#student-transfer-history-memberships`](http://127.0.0.1:14312/presentation/#student-transfer-history-memberships)
- Состав на дату пары · [`#student-transfer-history-journal`](http://127.0.0.1:14312/presentation/#student-transfer-history-journal)
  - Состав групп после перевода · [`#demo-student-transfer-title`](http://127.0.0.1:14312/presentation/#demo-student-transfer-title)
  - Локальное состояние · JSON · [`#demo-student-transfer-json-title`](http://127.0.0.1:14312/presentation/#demo-student-transfer-json-title)
- Своя история и рейтинг · [`#student-transfer-history-ranking`](http://127.0.0.1:14312/presentation/#student-transfer-history-ranking)

### Переходы из уведомлений

[`#notification-actions`](http://127.0.0.1:14312/presentation/#notification-actions)

- От события к действию · [`#notification-actions-contract`](http://127.0.0.1:14312/presentation/#notification-actions-contract)
- Сеанс и выбранная роль · [`#notification-actions-identity`](http://127.0.0.1:14312/presentation/#notification-actions-identity)
- Загрузка объекта перед показом · [`#notification-actions-read`](http://127.0.0.1:14312/presentation/#notification-actions-read)
  - Одно задание · два канала · [`#demo-homework-notifications-title`](http://127.0.0.1:14312/presentation/#demo-homework-notifications-title)
  - Локальное состояние · JSON · [`#demo-homework-notifications-json-title`](http://127.0.0.1:14312/presentation/#demo-homework-notifications-json-title)

### Telegram-бот

[`#telegram-bot`](http://127.0.0.1:14312/presentation/#telegram-bot)

- Личный чат и привязка · [`#telegram-bot-menu`](http://127.0.0.1:14312/presentation/#telegram-bot-menu)
- ДЗ, пары и учёт группы · [`#telegram-bot-reads`](http://127.0.0.1:14312/presentation/#telegram-bot-reads)
- Кнопка ведёт в Mini App · [`#telegram-bot-button`](http://127.0.0.1:14312/presentation/#telegram-bot-button)

### Подписка Web Push

[`#web-push-subscription`](http://127.0.0.1:14312/presentation/#web-push-subscription)

- Разрешение и подписка · [`#web-push-subscription-enable`](http://127.0.0.1:14312/presentation/#web-push-subscription-enable)
- Доставка и открытие PWA · [`#web-push-subscription-deliver`](http://127.0.0.1:14312/presentation/#web-push-subscription-deliver)
- Отключение и очистка · [`#web-push-subscription-disable`](http://127.0.0.1:14312/presentation/#web-push-subscription-disable)

### Профиль и устройство

[`#profile-device-settings`](http://127.0.0.1:14312/presentation/#profile-device-settings)

- Профиль, роль и аватар · [`#profile-device-settings-profile`](http://127.0.0.1:14312/presentation/#profile-device-settings-profile)
- Тема и ревизия устройства · [`#profile-device-settings-theme`](http://127.0.0.1:14312/presentation/#profile-device-settings-theme)
- Геолокация и камера · [`#profile-device-settings-permissions`](http://127.0.0.1:14312/presentation/#profile-device-settings-permissions)

### Сеансы и история аккаунта

[`#account-sessions-history`](http://127.0.0.1:14312/presentation/#account-sessions-history)

- Активные сеансы и это устройство · [`#account-sessions-history-live`](http://127.0.0.1:14312/presentation/#account-sessions-history-live)
- Завершение одного сеанса · [`#account-sessions-history-revoke`](http://127.0.0.1:14312/presentation/#account-sessions-history-revoke)
- Выход на всех устройствах · [`#account-sessions-history-all`](http://127.0.0.1:14312/presentation/#account-sessions-history-all)
- История действий аккаунта · [`#account-sessions-history-history`](http://127.0.0.1:14312/presentation/#account-sessions-history-history)

### Диагностика и согласованность

[`#request-diagnostics`](http://127.0.0.1:14312/presentation/#request-diagnostics)

- Одна отметка в логах и трассе · [`#request-diagnostics-trace`](http://127.0.0.1:14312/presentation/#request-diagnostics-trace)
- HTTP 202 и потерянный ответ · [`#request-diagnostics-accepted`](http://127.0.0.1:14312/presentation/#request-diagnostics-accepted)
- Продолжение по сохранённой фазе · [`#request-diagnostics-phases`](http://127.0.0.1:14312/presentation/#request-diagnostics-phases)
- Квитанция, ревизия и event_id · [`#request-diagnostics-repeats`](http://127.0.0.1:14312/presentation/#request-diagnostics-repeats)
- Московская дата и UTC · [`#request-diagnostics-time`](http://127.0.0.1:14312/presentation/#request-diagnostics-time)
- Пусто, ноль, ошибка и запрет · [`#request-diagnostics-states`](http://127.0.0.1:14312/presentation/#request-diagnostics-states)
- Заявка и геоотметка · [`#request-diagnostics-pending`](http://127.0.0.1:14312/presentation/#request-diagnostics-pending)

### Данные отчёта и контракт

[`#report-data-contract`](http://127.0.0.1:14312/presentation/#report-data-contract)

- Состав и пары на выбранные даты · [`#report-data-contract-history`](http://127.0.0.1:14312/presentation/#report-data-contract-history)
- Таблица, страницы и формат файла · [`#report-data-contract-file`](http://127.0.0.1:14312/presentation/#report-data-contract-file)
- Страница, итоги и выгрузка · [`#report-data-contract-selection`](http://127.0.0.1:14312/presentation/#report-data-contract-selection)
- DTO и снимок OpenAPI · [`#report-data-contract-snapshot`](http://127.0.0.1:14312/presentation/#report-data-contract-snapshot)
- Проверка JSON в клиенте · [`#report-data-contract-runtime`](http://127.0.0.1:14312/presentation/#report-data-contract-runtime)

### Восстановление, хранение и проверки

[`#recovery-retention-verification`](http://127.0.0.1:14312/presentation/#recovery-retention-verification)

- Манифест и пустая цель · [`#recovery-retention-verification-bundle`](http://127.0.0.1:14312/presentation/#recovery-retention-verification-bundle)
- Проверка после восстановления · [`#recovery-retention-verification-readback`](http://127.0.0.1:14312/presentation/#recovery-retention-verification-readback)
- Сеанс, билет и файл отчёта · [`#recovery-retention-verification-short-lived`](http://127.0.0.1:14312/presentation/#recovery-retention-verification-short-lived)
- Вложения и история действий · [`#recovery-retention-verification-history`](http://127.0.0.1:14312/presentation/#recovery-retention-verification-history)
- Геоотметка на трёх уровнях · [`#recovery-retention-verification-checks`](http://127.0.0.1:14312/presentation/#recovery-retention-verification-checks)
