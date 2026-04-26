# 06. Notification Bot (Python / notification-bot) — отчёт аудита

## Сводка

Notification Bot — Python-контейнер на **Aiogram 3.15** + **aio-pika 9.5** + **grpc.aio** + **redis.asyncio 5.2** + **aiohttp 3.10**, работает в режиме long polling (`dp.start_polling`) и подписан на fanout-exchange `rut-uit.events`. Код занимает ~3 940 строк Python (без учёта сгенерированных `*_pb2*.py`): 69 файлов в пакете `bot/`, из которых ~790 строк — это сгенерированные gRPC-стабы (`academic_pb2*.py`, `schedule_pb2*.py`). Тесты — 25 файлов, ~4 240 строк, ≈108 тест-кейсов (совпадает с CLAUDE.md). Сравнительно с Java-сервисами кодовая база компактная, ответственность ясная: бот — это **event-forwarder для Telegram-канала + три интерактивных хендлера (`/start`, `/login`, `/status`)** + **callback-хендлеры старостовских решений** + **таймер-шедулер для reminder-ов (NOTIF-02/NOTIF-03)**. FSM-состояний нет вообще — все сценарии однокадровые (bottoneclick → publish event). Собственного `Middleware` нет — роль-чеки делаются либо на уровне DTO-пейлоада (в `handle_headman_alert` фильтруем `is_headman=True`), либо в логике хендлера через gRPC-lookup (`get_user_by_telegram_id`).

Код удивительно чист для Python-проекта: нет ни одного `bare except`, ни одного `eval`, ни одного `print` в продакшн-коде, ни одного `asyncio.run` вне единого entry-point `bot/__main__.py`. Маркеров TODO/FIXME/XXX/HACK в `bot/` тоже нет. Type hints присутствуют почти везде (кроме части тестов и некоторых `**kwargs` в нотификационных хендлерах, где типы размыты). Watchdog-цикл для consumer-а реализован корректно, healthcheck aiohttp на `/health` мониторит оба asyncio-таска (watchdog + polling). DLQ для RabbitMQ настроен. Token-bucket rate-limiter (30 msg/sec) в `TelegramSendQueue` с ретраями при `TelegramRetryAfter`. Два Redis-трекера идемпотентно очищают OTP-сообщения и reminder'ы.

Тем не менее есть **настоящие дыры**:

1. **P0 — `grpc.aio.insecure_channel(...)` без TLS** для Academic и Schedule. Единственная защита — `x-grpc-secret` metadata, но при `config.grpc_secret = ""` (default) secret-проверка серверной стороной в большинстве Java-интерцепторов просто пропускает запрос. В проде bot и services в одной docker-сети, но при компрометации любого соседа атакующий может звать gRPC без препятствий.
2. **P0 — `bot_token = "placeholder"` и `grpc_secret = ""` как default в `Settings`.** Если `.env` отсутствует, бот попытается стартовать с placeholder-токеном (Aiogram словит `TelegramUnauthorizedError`) и без gRPC-секрета (Java-сервисы его отклонят, но если они сконфигурированы без проверки — пройдёт). Нет валидации `bot_token` на старте.
3. **P0 — Пароль (`initial_password`) отправляется в `/start` в открытом Telegram-чате** как `<code>pass123</code>` — Telegram хранит историю чатов на серверах MTProto в зашифрованном виде, но оператор Telegram (и любой, кто получит доступ к аккаунту пользователя) увидит пароль. Нет «покажу один раз и удалю», нет `spoiler`-обёртки, нет `self-destruct` timer. Это прямое нарушение OWASP A02:2021 Cryptographic Failures для случая «initial password distribution».
4. **P1 — `handle_excuse_decision` / `handle_late_checkin_decision` (callback-хендлеры) НЕ проверяют, что нажавший кнопку — староста именно той группы, откуда пришёл запрос.** Любой пользователь бота, угадав callback-data формата `ex:approve:<uuid>`, может отправить решение. Я не нашёл ни одного места, где `callback.from_user.id → AcademicGrpcClient.get_user_by_telegram_id → проверка is_headman=True` встроено перед `event_publisher.publish`.
5. **P1 — Reminder-шедулер привязан к памяти процесса (`self._timers: dict[int, list[asyncio.Task]]`).** При рестарте бота (даже graceful) все запланированные напоминания теряются. Event `lesson.started` не переигрывается (Redis ключ `reminder:msgs:{lesson_id}:{user_id}` уже создан → `lesson_started` handler не упадёт, но таймеры — пустые). Результат: студенты не получат второе и третье напоминание, если бот перезапустили в середине пары.
6. **P1 — Use of naive `datetime.now()` без tz** в `reminder_scheduler._parse_hhmm_today`. Работает только при `TZ=Europe/Moscow` внутри контейнера, и только если Schedule service отдаёт те же «Moscow-local»-строки. В `Dockerfile` переменной `TZ` нет, значит, контейнер стартанёт в UTC (если хост не пробросил `/etc/localtime`). Шедулер может ошибаться на 3 часа → reminder в 06:00 UTC (= 09:00 MSK) или вообще в прошлом.
7. **P1 — `EventDispatcher.dispatch` ловит все исключения** и логирует их, но rabbit-сообщение всё равно ACK-ается (`async with message.process()`). Нет пути «ретрай с backoff» или «отправить в DLQ» — если handler упал из-за transient-ошибки (например, Academic-gRPC тайм-аут), событие теряется навсегда. DLQ настроен на уровне consumer-а, но handler-exception туда не попадает — `async with message.process()` поглощает всё, что не выбрасывается из `queue.iterator()`.
8. **P1 — Нет rate-limiting на per-user уровне для `/login` и других команд.** Auth-service возвращает 429, но до того бот сам не ограничивает, как часто пользователь может жать `/login` → auth получает spam, и если auth сконфигурирован со слабым rate-limit, злоумышленник выжигает OTP-квоту жертвы.
9. **P1 — `handle_headman_alert` пересылает файл-аттачмент, закодированный в base64 в RabbitMQ-событии.** Размер лимитирован `MAX_FORWARDED_FILE_BYTES = 10 MB`, но base64 в JSON-evelope в фанауте → каждый consumer (notification-web, если он тоже подписан на `excuse.requested`) тоже получает ~13 MB raw + network overhead. Это не «файл не хранится в системе» — он **хранится в RabbitMQ queue до ACK всеми consumer-ами** (durable=true).
10. **P2 — parse_mode="HTML" с `<code>{code}</code>` и `<b>{group_name}</b>`** безопасен **только** если исходные данные не содержат `<`, `>`, `&`. `group_name` приходит из gRPC Academic — admin-контролируемый, но не экранируется. Если admin задаст группу с именем `<script>alert(1)</script>` или `</b>xxx`, Telegram либо отрендерит некорректно, либо получит BAD_REQUEST. Нет `html.escape()`.
11. **P2 — `config.bot_token` логируется** через `pydantic-settings` default `__repr__` может засветить токен в tracebacks. Не видел следа утечки, но `BaseSettings` ничего не маскирует.
12. **P2 — Нет graceful shutdown на SIGTERM** в `bot/__main__.py`. `handle_signals=False` отключено у `dp.start_polling`, но сам main-loop не регистрирует signal handler. Docker пришлёт SIGTERM → Python умрёт с `KeyboardInterrupt` (или не умрёт, если сигнал поймает корневая asyncio-loop). `finally: await send_queue.shutdown()...` выполнится, только если `asyncio.wait` завершился сам — что возможно лишь при crash одной из тасок.

**Счётчики:** **P0=3, P1=8, P2=10, P3=8**.

Тестов (25 файлов, ~108 test cases): хороший охват нотификационных хендлеров (lesson_started, lesson_closed, attendance_marked, excuse.decided, homework, one_off_created/cancelled, group_renamed/archived, headman_alerts) + services (send_queue, jwt_redis_client, redis_client, otp_message_tracker отсутствует!) + handlers (/start, /status, /login) + infrastructure (consumer watchdog, event_dispatcher, grpc-клиенты). **Не покрыты**: `NotificationPrefsClient` (категории, глобальный toggle), `RequestMessageTracker`, `OtpMessageTracker` напрямую (только через /login), `EventPublisher` (publish happy/unhappy), callback-хендлеры `excuse.py` и `late_checkin.py` (нулевое покрытие!), весь `bot.handlers.prefs` (inline menu + callbacks), AttendanceHttpClient, AuthHttpClient напрямую. **Сомнительные тесты**: `test_reminder_scheduler.py` мокает модуль `datetime` через `patch("bot.services.reminder_scheduler.datetime")` — хрупко, не проверяет TZ-bugs. `test_send_queue.py` использует `patch("asyncio.sleep", new_callable=AsyncMock)` — может исказить token-bucket. Интеграционных тестов (`Testcontainers Redis` или `Testcontainers RabbitMQ`) нет — `fakeredis` покрывает только Redis-level.

---

## Структура модуля

```
notification-bot/
├── Dockerfile                                  — 17 строк, python:3.12-slim, non-root user
├── .dockerignore                               — исключает tests/, requirements-test.txt, pytest.ini
├── .env.example                                — 8 переменных: BOT_TOKEN, RABBITMQ_URL, ACADEMIC_GRPC_*, REDIS_*, HEALTH_PORT
├── pyproject.toml                              — ruff: target=py312, line-length=120, E/F/W/I
├── pytest.ini                                  — asyncio_mode = auto
├── requirements.txt                            — 9 пакетов, все пиннутся к patch-версии
├── requirements-test.txt                       — pytest>=8, pytest-asyncio>=1.1, fakeredis>=2.34
│
├── bot/
│   ├── __init__.py                             — пусто
│   ├── __main__.py                             — 220 строк, entry-point: aiohttp health server + RabbitMQ watchdog + bot polling + DI через dp[key]
│   ├── config.py                               — 56 строк, pydantic-settings BaseSettings, env_file=.env
│   │
│   ├── consumers/                              — RabbitMQ ingestion
│   │   ├── __init__.py
│   │   ├── event_consumer.py                   — 71 строка, declare_exchange FANOUT + DLQ direct + queue с x-dead-letter-*
│   │   └── event_dispatcher.py                 — 257 строк, dict[event_type → handler], import внутри __init__ (avoid circular)
│   │
│   ├── handlers/                               — Aiogram Router-ы (user-initiated)
│   │   ├── __init__.py                         — 15 строк, re-export router-ов
│   │   ├── start.py                            — 56 строк, /start — account linking + показ креды
│   │   ├── login.py                            — 87 строк, /login — OTP через auth-service HTTP
│   │   ├── status.py                           — 97 строк, /status — active lesson + JWT
│   │   ├── prefs.py                            — 144 строки, reply-клавиатура + inline-меню категорий
│   │   ├── excuse.py                           — 66 строк, callback_query F.data.startswith("ex:")
│   │   └── late_checkin.py                     — 62 строки, callback_query F.data.startswith("lcr:")
│   │
│   ├── notifications/                          — event-driven handlers (RabbitMQ → send)
│   │   ├── __init__.py                         — пусто
│   │   ├── lesson_started.py                   — 82 строки, inline WebAppInfo кнопка «Отметиться»
│   │   ├── lesson_cancelled.py                 — 61 строка
│   │   ├── lesson_one_off_created.py           — 64 строки (60-04)
│   │   ├── lesson_one_off_cancelled.py         — 60 строк (60-04)
│   │   ├── lesson_closed.py                    — 62 строки, delete reminder msgs + cancel_lesson
│   │   ├── attendance_marked.py                — 75 строк, cleanup reminders при present/excused/free
│   │   ├── homework.py                         — 79 строк, published + updated
│   │   ├── headman_alerts.py                   — 245 строк, excuse/late_checkin.requested → старостам
│   │   ├── student_alerts.py                   — 99 строк, *.decided → студенту
│   │   ├── group_renamed.py                    — 62 строки (BUG-006-6)
│   │   ├── group_archived.py                   — 60 строк (BUG-006-6)
│   │   └── otp_verified.py                     — 80 строк, cleanup OTP messages
│   │
│   ├── services/                               — ресурсы и утилиты
│   │   ├── __init__.py                         — пусто
│   │   ├── redis_client.py                     — 68 строк, ReminderRedisClient (list RPUSH/LRANGE)
│   │   ├── jwt_redis_client.py                 — 74 строки, bot:jwt:{telegram_id} → JSON
│   │   ├── notification_prefs.py               — 146 строк, глобальный toggle + hash категорий
│   │   ├── otp_message_tracker.py              — 88 строк, атомарный pop через pipeline(transaction=True)
│   │   ├── request_message_tracker.py          — 87 строк, список (chat_id, message_id) для sync TG↔Web
│   │   ├── reminder_scheduler.py               — 120 строк, asyncio.create_task для mid/end ремайндеров
│   │   ├── send_queue.py                       — 113 строк, token bucket 30/sec + 3 retry + prefs check
│   │   ├── event_publisher.py                  — 64 строки, fanout publish (excuse.decision, late_checkin.decision)
│   │   ├── auth_http_client.py                 — 46 строк, POST /auth/otp/request
│   │   └── attendance_http_client.py           — 50 строк, GET /api/attendance/reports/student/records
│   │
│   └── grpc_client/                            — сгенерированные stubs + clients
│       ├── __init__.py                         — пусто
│       ├── academic_client.py                  — 76 строк, 4 метода + 5-мин in-memory cache
│       ├── academic_pb2.py                     — 72 строки (generated)
│       ├── academic_pb2_grpc.py                — 490 строк (generated)
│       ├── schedule_client.py                  — 42 строки, GetActiveLesson
│       ├── schedule_pb2.py                     — 42 строки (generated)
│       └── schedule_pb2_grpc.py                — 199 строк (generated)
│
└── tests/
    ├── __init__.py                             — пусто
    ├── conftest.py                             — 10 строк, fake_redis fixture
    ├── fixtures/
    │   ├── excuse_decided.json
    │   └── excuse_requested.json
    └── test_*.py                                — 23 теста-файла (см. раздел «Тесты»)
```

### Заметки по структуре

- **Hexagonal разделение чёткое**: handlers/ не импортируют notifications/, notifications/ не импортируют handlers/. grpc_client/ и services/ — низкоуровневая инфраструктура. Но `EventDispatcher` импортирует notifications внутри `__init__` (чтобы избежать circular), а `login.py` импортирует `cleanup_otp_messages` из `notifications/otp_verified.py` — нарушение изоляции «handlers не знают notifications» (но семантически объяснимо: `/login` и `otp.verified` работают с одним трекером).
- **Нет пакета `keyboards/`**: все inline/reply клавиатуры собираются inline в хендлерах. Для бота такого размера — приемлемо, но `main_keyboard()` живёт в `prefs.py` и импортируется из `start.py` — некрасиво.
- **Нет пакета `middlewares/`** — все checks встроены в хендлеры.
- **Нет пакета `states/`** — FSM ни разу не используется, `MemoryStorage()` создаётся в `__main__.py` ради самого `Dispatcher`, но не хранит ничего. Это **положительное решение** — для однокадровых сценариев FSM переусложнит код.
- **DI через `dp[key] = value`** — штатный Aiogram 3 pattern. Всего 9 инжектятся: academic_client, schedule_client, jwt_redis, auth_client, attendance_client, prefs_client, otp_tracker, event_publisher, request_tracker.

---

## Критичные проблемы (P0)

### P0-1: gRPC-каналы к Academic и Schedule — `insecure_channel`, без TLS; secret-метадата опциональна

**Где**: `bot/grpc_client/academic_client.py:17`, `bot/grpc_client/schedule_client.py:19`.

```python
self._channel = grpc.aio.insecure_channel(target)
# ...
self._metadata = (("x-grpc-secret", grpc_secret),) if grpc_secret else ()
```

**Что**: канал строится как `insecure_channel` — трафик идёт в открытом виде по HTTP/2 без TLS. Аутентификация сведена к shared-secret в custom-header `x-grpc-secret`. Если `config.grpc_secret = ""` (дефолт в `Settings`), metadata-кортеж пустой — бот звонит Java-сервисам без какой-либо аутентификации.

**Риск**: в рамках docker-bridge-сети — внутренняя угроза (соседний контейнер, лазерная прошивка). В проде `rutcampustrack_private_net` + host isolation нивелирует снаружи, но:
- любой скомпрометированный контейнер в том же network видит gRPC-трафик bot↔academic;
- если IDS/IPS-решение развернёт wireshark-like probe — пароли-отчёты (в Academic есть `initial_password`!) пойдут в plaintext;
- при `grpc_secret=""` — nothing stops a rogue actor from calling gRPC as `notification-bot`.

**Как чинить**:
1. Перевести на `grpc.aio.secure_channel` с mTLS (сертификаты в `/certs/` через secret-volume) **или** перейти на service-mesh Istio/Linkerd с auto-mTLS.
2. Сделать `grpc_secret` обязательным: `Field(..., min_length=32)` — бот не стартует без ключа.
3. Синхронно ужесточить Java-сервер: отвергать вызовы без `x-grpc-secret`.

**Зависимости**: coordinate with academic-service (01-report P0-4), schedule-service, attendance-service.

### P0-2: default BOT_TOKEN="placeholder" и GRPC_SECRET="" — бот стартует без фейл-быстро

**Где**: `bot/config.py:5, 25`.

```python
class Settings(BaseSettings):
    bot_token: str = "placeholder"
    ...
    grpc_secret: str = ""
```

**Что**: если `.env` не смонтирован (например, в новой прод-машине или в CI при запуске smoke-теста), бот стартует с placeholder-токеном. Aiogram 3 при первом `getMe` выбросит `TelegramUnauthorizedError`, а `dp.start_polling` умрёт. Watchdog перезапустит… но отсутствие токена будет не очевидно из `/health` (он видит `_bot_task.done() == True` и возвращает 503). **Diagnostic-ценность**: 0. Аналогично с `grpc_secret=""` — бот будет спамить gRPC без auth и получать Unauthenticated в лог.

**Риск**: долгая диагностика при incident. Worse — secret `""` может совпасть с дефолтом на сервере, если кто-то забыл переменную, и gRPC пройдёт.

**Как чинить**:
```python
from pydantic import Field, field_validator

class Settings(BaseSettings):
    bot_token: str = Field(..., min_length=40)  # реальный токен длиннее
    grpc_secret: str = Field(..., min_length=32)

    @field_validator("bot_token")
    def validate_token(cls, v: str) -> str:
        if v == "placeholder" or not v or ":" not in v:
            raise ValueError("BOT_TOKEN must be set and valid")
        return v
```

**Зависимости**: —.

### P0-3: ✅ ACCEPTED — initial_password отправляется в plaintext Telegram-чат без self-destruct
**Статус:** by design (см. `OWNER-ANSWERS.md` 06-Q2 + 01-Q1 + Meta M1, 2026-04-18). Бот показывает пароль при каждом `/start`, пока пользователь не сменит. Show-once / spoiler / autodelete / magic-link — все варианты отклонены владельцем. Trust-модель: «если ты в моём TG-аккаунте, ты и есть владелец». Ниже — оригинальное описание.

**Где**: `bot/handlers/start.py:32-42`.

```python
if response.initial_password:
    await message.answer(
        f"Добро пожаловать, {response.display_name}!\n\n"
        f"Ваш логин: <code>{response.login}</code>\n"
        f"Ваш пароль: <code>{response.initial_password}</code>\n\n"
        "Используйте эти данные для входа в веб-панель.\n"
        "После входа смените пароль.",
        reply_markup=keyboard,
        parse_mode="HTML",
    )
```

**Что**: пароль отображается в открытом виде в истории Telegram-чата. Хранится у Telegram на их серверах (MTProto encrypts in transit, но сервер Telegram может читать не-secret chats). История сохраняется у пользователя на всех его устройствах. Если кто-то получит SIM-swap / угонит TG-аккаунт — увидит пароль (даже если пользователь его уже сменил — в истории остался).

Дополнительно: academic-service возвращает `initial_password` через gRPC UserByTelegramIdResponse **даже если пользователь уже менял пароль** (это исправлено в 01-report P0, но здесь нужно перепроверить).

**Риск**: OWASP A02 Cryptographic Failures. Компрометация TG-аккаунта → утечка университетского аккаунта.

**Как чинить**:
1. Вместо пароля — отправлять magic link / OTP-код, действующий 5 минут.
2. Если сохраняем текущую схему: обернуть пароль в `<tg-spoiler>{password}</tg-spoiler>` (Telegram spoilers), и через 60s после отправки — `bot.delete_message(chat_id, message_id)`. Использовать `asyncio.create_task` с деферd-cleanup (паттерн уже есть в `login.py`).
3. Не показывать `initial_password` повторно: academic должен отдавать `initial_password` только если `password_changed_at IS NULL` **и** `initial_password_shown_at IS NULL`. После первого чтения в academic — set `initial_password_shown_at = now()`, и следующий `/start` увидит пустую строку.

**Зависимости**: academic-service (UserByTelegramIdResponse — нужно добавить `initial_password_shown_at` на БД).

---

## Серьёзные проблемы (P1)

### P1-1: callback-хендлеры excuse/late_checkin не проверяют роль старосты

**Где**: `bot/handlers/excuse.py:25-52`, `bot/handlers/late_checkin.py:25-52`.

```python
@excuse_router.callback_query(F.data.startswith("ex:"))
async def handle_excuse_decision(callback: CallbackQuery, **data) -> None:
    ...
    await event_publisher.publish(
        "excuse.decision",
        {"ticket_id": ticket_id, "approved": approved, "decision_by": callback.from_user.id},
    )
```

**Что**: callback-data — это обычная строка в Telegram-сообщении. Чтобы её отправить, нужно нажать кнопку. Кнопки бот рассылает только старостам через `handle_headman_alert` (фильтр `is_headman=True`). **НО**: любой пользователь, получивший старое сообщение со старостой (forward) или восстановивший callback_data через Telegram API (для добавленных в группу ботов), может отправить callback-query. Aiogram **передаст его в хендлер**, хендлер `publish`-нет `excuse.decision` в RabbitMQ без проверки `callback.from_user.id` → `is_headman=True`.

Attendance-service (consumer) тоже не проверяет авторизацию decision-by (см. 04-report). Итог: **любой авторизованный пользователь бота, знающий `ticket_id` (UUID), может принять/отклонить чужой тикет**.

**Риск**: целостность excuse-процесса. Школьник отменяет свой тикет за себя. Или принимает чужой.

**Как чинить**:
```python
@excuse_router.callback_query(F.data.startswith("ex:"))
async def handle_excuse_decision(callback: CallbackQuery, academic_client, **data) -> None:
    user = await academic_client.get_user_by_telegram_id(callback.from_user.id)
    if not user.found or not user.is_headman:
        await callback.answer("Только староста может принимать решения", show_alert=True)
        return
    # + cross-check that ticket_id belongs to user.group_id — requires academic/attendance lookup
    ...
```

**Зависимости**: нужен метод в attendance-service для lookup excuse-ticket → group_id; **или** academic gRPC может отдать `is_headman_of_group(group_id)`.

### P1-2: Reminder-шедулер хранит таски в памяти, при рестарте теряет все таймеры

**Где**: `bot/services/reminder_scheduler.py:68` (`self._timers: dict[int, list[asyncio.Task]]`).

**Что**: `schedule_reminders` создаёт две `asyncio.Task` (mid + near_end) и сохраняет в dict. При рестарте бота — словарь пустой. Redis-ключи `reminder:msgs:{lesson_id}:{user_id}` остаются (TTL 24 ч), но таймеры — нет. Следствие: после рестарта mid/near_end reminder'ы не сработают. Event `lesson.started` уже ACK-ан — повторно не прилетит.

**Риск**: snadly silent: пользователи просто не получат напоминаний. Никакой метрики / alert-а.

**Как чинить**:
- Вариант A: Celery + Redis beat / APScheduler с PersistentJobStore. Слишком тяжело для текущего масштаба.
- Вариант B: при старте бота — прочитать Schedule gRPC, найти все активные lesson-ы, пересоздать таймеры.
- Вариант C: дублировать логику в notification-web (Java Scheduled) — но в CLAUDE.md reminder'ы отданы боту.
- Минимум: `logger.warning` при старте, если Redis содержит reminder-ключи с TTL > 0 — alert оператору.

**Зависимости**: —.

### P1-3: Naive datetime.now() без TZ в ReminderScheduler → окно reminder'а может быть сдвинуто на часы

**Где**: `bot/services/reminder_scheduler.py:31-35`.

```python
def _parse_hhmm_today(s: str) -> datetime:
    h, m = map(int, s.split(":"))
    return datetime.now().replace(hour=h, minute=m, second=0, microsecond=0)
```

**Что**: `datetime.now()` без `timezone.utc` возвращает naive datetime в local-time процесса. Docker-контейнер стартует в `UTC` (если нет `TZ` env var). Schedule service отдаёт `start_time`/`end_time` как `HH:MM` в **Moscow-local** (см. phase-26 реcёрч). Итог: если контейнер в UTC, `_parse_hhmm_today("09:00")` даст `2026-04-17 09:00 UTC` = `12:00 MSK`, reminder пойдёт на 3 часа позже.

В `Dockerfile`:
```dockerfile
FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends curl && ...
```
`TZ` не установлена, `tzdata` не проверен (в slim-образе может и не быть), `/etc/localtime` не смонтирован.

**Риск**: reminder'ы приходят через 3 ч после пары (если контейнер в UTC), либо за 3 ч до пары (если в другом TZ).

**Как чинить**:
1. В `Dockerfile`: `ENV TZ=Europe/Moscow` + `RUN apt-get install -y tzdata && ln -sf /usr/share/zoneinfo/$TZ /etc/localtime`.
2. Лучше — пересчитать логику в UTC: schedule-service должен отдавать `start_time_utc` (ISO timestamp с tz), бот делает `datetime.now(timezone.utc)`.
3. Добавить тест, который замораживает UTC-время и проверяет корректность delay.

**Зависимости**: potentially schedule-service API change.

### P1-4: Исключения в handler → event ACK-ается, но retry нет

**Где**: `bot/consumers/event_consumer.py:58-69`.

```python
async with queue.iterator() as queue_iter:
    async for message in queue_iter:
        async with message.process():
            try:
                body = json.loads(message.body)
                event_type = body.get("event_type", "unknown")
                if dispatcher:
                    await dispatcher.dispatch(body)
            except json.JSONDecodeError:
                logger.error("Failed to decode message body: %s", message.body[:200])
            except Exception:
                logger.exception("Handler failed for event, acking anyway")
```

**Что**: `message.process()` — context manager, который ACK-ает сообщение при успешном выходе. Все exceptions внутри ловятся и логируются. Это гарантирует «no infinite requeue», но **теряет события при transient-сбоях** (Redis down, gRPC timeout). DLQ настроен, но срабатывает только при `message.reject(requeue=False)` — явного reject нет, все ACK.

**Риск**: при 30-секундном сбое Academic gRPC **пачка** уведомлений потеряется навсегда. Нет метрики «failed dispatches».

**Как чинить**: реализовать retry-wrapper:
```python
async def dispatch_with_retry(self, event, attempts=3):
    for attempt in range(attempts):
        try:
            await self._dispatch_raw(event)
            return
        except (grpc.aio.AioRpcError, redis.RedisError, asyncio.TimeoutError):
            if attempt == attempts - 1:
                raise  # let caller reject → DLQ
            await asyncio.sleep(2 ** attempt)
```

И в consumer — `except Exception: await message.reject(requeue=False)` вместо `process()`.

**Зависимости**: —.

### P1-5: Нет per-user rate-limit на команды бота

**Где**: везде, где есть `@router.message(Command(...))`.

**Что**: `/login`, `/status`, `/start`, `SETTINGS_LABEL`, `LOGIN_LABEL` reply-кнопки — все отрабатывают мгновенно, без троттлинга. Одиночный пользователь может слать `/login` 30 раз/сек, каждый раз Authenticate service запрашивает OTP. Auth-service возвращает 429 (сам по себе устойчив), но:
- в боте telegram-message об ошибке (отправка × 30) → Telegram API 429 → аккаунт бота может быть throttled глобально;
- auth-service OTP-квота на пользователя может быть выработана до того, как легитимная попытка.

**Риск**: DoS на bot, DoS на auth-service, DoS на конкретного пользователя (нарушается availability).

**Как чинить**: Aiogram 3 middleware `ThrottlingMiddleware`:
```python
from aiogram.dispatcher.middlewares.base import BaseMiddleware

class ThrottlingMiddleware(BaseMiddleware):
    def __init__(self, redis: Redis, limit: int = 5, window: int = 60):
        ...
    async def __call__(self, handler, event, data):
        telegram_id = event.from_user.id
        key = f"throttle:{telegram_id}"
        count = await self.redis.incr(key)
        if count == 1:
            await self.redis.expire(key, self.window)
        if count > self.limit:
            return await event.answer("Слишком много запросов.")
        return await handler(event, data)
```

**Зависимости**: —.

### P1-6: file_payload_b64 передаётся в RabbitMQ-сообщении (до 10 MB × N consumer-ов × durable)

**Где**: `bot/notifications/headman_alerts.py:101-114`.

**Что**: excuse-ticket с аттачментом → attendance-service (или кто-то ещё) публикует `excuse.requested` с `payload.file_payload_b64` (base64-encoded). Бот декодирует и рассылает старостам через `bot.send_document`. Проблема: fanout-exchange → все подписчики получают весь envelope; каждый сохраняет в свою durable queue → если N consumer-ов × 10 MB × M pending messages = взрыв очереди.

CLAUDE.md утверждает «файлы пересылаются старосте через Telegram (не хранятся в системе)». Факт: они **хранятся** в RabbitMQ до ACK, плюс в памяти Java-producer, плюс в памяти Python-consumer.

**Риск**: RabbitMQ OOM, disk fill при падении consumer-ов; network overhead.

**Как чинить**: producer (attendance-service) пусть заливает файл в object storage (S3 / MinIO / filesystem with TTL), в event шлёт **URL + checksum + filename**. Бот стягивает URL и форвардит в Telegram. После пересылки — можно сразу удалить (`DELETE /files/{id}` или TTL 1 ч).

**Зависимости**: attendance-service изменение API; MinIO в docker-compose.

### P1-7: Graceful shutdown отсутствует — `main()` ждёт `FIRST_EXCEPTION`, а не SIGTERM

**Где**: `bot/__main__.py:190-216`.

```python
try:
    done, pending = await asyncio.wait(
        [_consumer_task, _bot_task],
        return_when=asyncio.FIRST_EXCEPTION,
    )
    ...
except asyncio.CancelledError:
    logger.info("Main cancelled, shutting down")
finally:
    # Cleanup
```

**Что**: `asyncio.wait` с `FIRST_EXCEPTION` разблокируется, только если одна из тасок упадёт. При SIGTERM от Docker (10-секундный graceful window перед SIGKILL) — Python получит `KeyboardInterrupt` (если нет signal handler), `main()` exception → `finally` выполнится, но за 10 сек может не успеть закрыть все клиенты (aio-pika connection, 9 Redis pool, 2 gRPC channel, 2 aiohttp session).

**Риск**: данные в `TelegramSendQueue` теряются (sent=0, pending=N). gRPC half-closed. Redis TCP-connections в TIME_WAIT.

**Как чинить**:
```python
import signal

async def main() -> None:
    shutdown_event = asyncio.Event()
    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, shutdown_event.set)
    ...
    await asyncio.wait(
        [_consumer_task, _bot_task, asyncio.create_task(shutdown_event.wait())],
        return_when=asyncio.FIRST_COMPLETED,
    )
```

**Зависимости**: —.

### P1-8: `/login` expiry-task не отменяется при успешном verify — орфаны asyncio.Task

**Где**: `bot/handlers/login.py:55` и `bot/notifications/otp_verified.py:68-80`.

**Что**: после успешного `/login` создаётся `asyncio.create_task(_cleanup_after_expiry(...))`. Этот task спит 300 сек, потом читает `tracker.pop(...)`. Если за 300 сек пришёл `otp.verified` — tracker уже очищен, `pop()` вернёт `None`, task завершится без действий. **НО** до этого он висит в event loop. Если пользователь жмёт `/login` 100 раз подряд — будет 100 pending-тасок.

Хуже: при рестарте бота `asyncio.create_task` тасок не сохраняются, но tracker-ключи с TTL 300 сек остаются в Redis → орфанные записи, которые никто не удалит до TTL.

**Риск**: утечка памяти при abuse; orphan Redis-keys.

**Как чинить**:
- отменять предыдущий task при новом `/login` от того же юзера (хранить `dict[telegram_id, Task]`);
- либо перенести cleanup на Redis TTL + periodic scan (`SCAN 0 MATCH otp_msgs:* COUNT 100` раз в минуту).

**Зависимости**: —.

---

## Средние проблемы (P2)

### P2-1: parse_mode="HTML" с не-экранированными gRPC-данными

**Где**: `bot/handlers/start.py:34-50`, `bot/notifications/group_renamed.py:40`, `bot/notifications/group_archived.py:38`.

Сценарий: админ задаёт группу `<script>alert(1)</script>`. Бот берёт `group.name` из Academic, подставляет `<b>{group_name}</b>` и шлёт в Telegram. Telegram либо отрендерит `<script>` как HTML (они escape это, но `<b>` внутри `<b>` — BAD_REQUEST). Аналогично `display_name`, `login` в `/start`.

**Фикс**: `import html; text = f"...<b>{html.escape(group_name)}</b>..."`.

### P2-2: bot_token может попасть в traceback через pydantic

**Где**: `bot/config.py:5`. BaseSettings по умолчанию НЕ маскирует секреты в `__repr__`. Если где-то `logger.exception("config=%s", config)` — токен в лог.

**Фикс**: `bot_token: SecretStr`.

### P2-3: `EventDispatcher.__init__` импортирует notifications внутри — тесты мокают с риском

**Где**: `bot/consumers/event_dispatcher.py:46-55`. Ленивые импорты сделаны «чтобы избежать circular imports at module level», но circular там реально нет — handlers принимают protocol-like параметры, никаких Django-style cycles.

**Фикс**: перенести импорты на top-of-module, убрать комментарий.

### P2-4: send_queue.put блокируется — но очередь без maxsize

**Где**: `bot/services/send_queue.py:32`: `self._queue: asyncio.Queue[SendTask] = asyncio.Queue()`.

**Что**: `asyncio.Queue()` без `maxsize=N` — unbounded. При 30 msg/sec и event-storm (рассылка в группу 100 человек × 5 событий/минуту) очередь растёт линейно, RSS бота растёт.

**Фикс**: `asyncio.Queue(maxsize=10_000)` + `try: self._queue.put_nowait(task); except QueueFull: logger.warning("dropping")`.

### P2-5: Telegram max caption = 1024 — truncate на `[:1020] + "…"` ломает HTML entities

**Где**: `bot/notifications/headman_alerts.py:160`.

```python
caption = text if len(text) <= 1024 else text[:1020] + "…"
```

**Что**: если срез пришёлся на середину `<code>...`, Telegram получит незакрытый тег → 400.

**Фикс**: использовать `aiogram.utils.text_decorations.html_decoration.quote` или обрезать по пробелу + проверка балансировки тегов.

Сейчас у хендлера `parse_mode` не выставлен для send_document, так что эта problem-theoretical, но стоит профилактики.

### P2-6: academic_client cache — не thread-safe между корутинами (не критично, но)

**Где**: `bot/grpc_client/academic_client.py:32-42`.

В asyncio параллельные корутины выполняются cooperatively, между `await` переключения нет — но _read-modify-write_ с async-пуском между ними возможен: если две корутины одновременно позвонят `get_group_members(42)` с cache-miss, обе `await self._stub.GetGroupMembers`, обе запишут в `self._cache[42]` — race не разрушительная (результат один и тот же), но лишний gRPC-вызов.

**Фикс**: `asyncio.Lock()` per-key или `asyncio.Event` pattern.

### P2-7: `MemoryStorage()` — FSM state теряется при рестарте

**Где**: `bot/__main__.py:105`. Сейчас FSM вообще не используется, но если появится (напр., многошаговый excuse-wizard) — состояние пропадёт.

**Фикс**: `RedisStorage.from_url(redis_url)` (Aiogram 3 поддерживает).

### P2-8: healthcheck проверяет только `task.done()`, не liveness

**Где**: `bot/__main__.py:36-42`.

```python
async def health_handler(request: web.Request) -> web.Response:
    if _consumer_task is None or _consumer_task.done():
        raise web.HTTPServiceUnavailable(text='{"status":"DOWN","reason":"watchdog_dead"}')
```

**Что**: task может быть stuck в deadlock (asyncio lock never released) — `.done()` будет `False`, health — UP, но бот ничего не делает. Healthcheck должен проверять «heartbeat»: последний обработанный event < N секунд назад.

**Фикс**: в `EventDispatcher.dispatch` писать `time.monotonic()` в shared var; в health — проверять `now - last_activity < 300`.

### P2-9: нет CSRF-защиты для callback_query — Telegram не предоставляет token

Telegram не умеет CSRF, но callback_data — строка-секрет. В текущей схеме `ticket_id` — UUID, угадать нельзя; но если атакующий слил в Discord old-сообщение со старостой, любой может нажать кнопку из web Telegram даже после «решение принято» (рекомендован MTProto-forward). См. P1-1.

### P2-10: Dockerfile ставит `curl` для healthcheck, но `HEALTHCHECK` директивы нет

**Где**: `Dockerfile:3`.

```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends curl && ...
```

**Что**: `curl` установлен, но `HEALTHCHECK CMD curl -f http://localhost:8081/health || exit 1` не написан. Docker не будет знать, что контейнер unhealthy. `curl` добавляет ~2 MB bloat без пользы.

**Фикс**: добавить `HEALTHCHECK --interval=30s --timeout=5s CMD curl -f http://localhost:8081/health || exit 1`.

---

## Мелкие и nit (P3)

### P3-1: `bot/__init__.py` пустой — ожидается, что будет содержать `__version__`
### P3-2: `OTP_TTL_SECONDS = 300` продублирован в `login.py:21` и `config.otp_ttl_seconds` — расхождение может возникнуть
### P3-3: `reminder_scheduler.NEAR_END_OFFSET_MINUTES = 5` hardcoded — не в config, не параметризуется
### P3-4: `main_keyboard(notifications_enabled: bool | None = None)` — параметр не используется, оставлен для «backward-compat», но имя функции намекает, что он влияет
### P3-5: `notifications/homework.py` — два почти идентичных ветки (`published` / `updated`) можно объединить
### P3-6: `_EVENT_CATEGORY` в `notification_prefs.py` продублирован с handler-dispatch-таблицей `EventDispatcher._handlers` — два источника истины
### P3-7: `requirements.txt` не закрепляет hash-ы — pip-install без `--require-hashes`
### P3-8: нет `pip-audit` / `safety` в CI — уязвимости в зависимостях не отлавливаются

---

## OTP-раздел (flow end-to-end)

**Схема (из изученного кода)**:

1. Пользователь в боте жмёт `🔑 Получить код для входа` или вводит `/login`.
2. `cmd_login` (`bot/handlers/login.py`) → `auth_client.request_otp(telegram_id)` → POST `/auth/otp/request` на auth-service (порт 9090, напрямую, не через Gateway — `api_gateway_url` не используется здесь).
3. Auth-service возвращает JSON `{"code": "123456"}` (6-digit).
4. Бот отправляет `message.answer("Ваш код для входа: <code>{code}</code>...", parse_mode="HTML")`.
5. `otp_tracker.store(telegram_id, chat_id, user_message_id, bot_message_id)` — в Redis ключ `otp_msgs:{telegram_id}`, TTL=300 сек.
6. `asyncio.create_task(_cleanup_after_expiry(...))` — сон 300 сек, потом `cleanup_otp_messages`.
7. Пользователь на веб-панели вводит OTP → auth-service verify → publish `otp.verified` в RabbitMQ с `payload.telegram_id`.
8. `EventDispatcher` → `handle_otp_verified` → `tracker.pop(telegram_id)` (атомарно через Redis pipeline `get + delete`) → если было сохранено — `bot.delete_message(chat_id, bot_message_id)` + `bot.delete_message(chat_id, user_message_id)`.
9. Если expiry task сработал раньше verify — тоже `tracker.pop` + delete_message. Race-free благодаря атомарному `pop()`.

**Плюсы**:
- Атомарность `pop()` через Redis pipeline (transaction=True).
- TelegramBadRequest при double-delete swallow-ится (idempotent).
- Нет хранения кода в Redis на стороне бота — только `message_id`.
- Expiry-task повторяет expiry auth-service.

**Минусы (помимо уже перечисленных P0/P1)**:
- Код (6-digit) живёт в Telegram-истории до 5 минут — Telegram-клиенты могут кэшировать, backup-ы делать.
- `/login` не чистит предыдущий `otp_msgs:{telegram_id}`: если у пользователя был ключ с предыдущего запроса, `store` перезапишет (SET ex), но старые message_id — потеряны, messages остаются в чате до 5 мин.
- Если user_message_id — это сам `/login`, бот удаляет команду пользователя тоже — это user-friendly, но может смутить (команда исчезла).
- `auth_client.request_otp` не передаёт `chat_id` / `bot_message_id` в auth — auth не знает, что bot отправил. Если auth получит `otp.verified` но `telegram_id` не тот (чужой перехватчик через MTProto), бот пошлёт delete_message не тому чату. Нужен secondary proof (например, `chat_id` в `otp.requested` + auth включает в envelope).
- Нет анти-спама на `/login` — см. P1-5.

---

## Reminders-раздел

**Схема (NOTIF-01 — NOTIF-05, фаза 24-25)**:

1. Schedule-service / auto-scheduler публикует `lesson.started` с `payload.lesson_id, group_id, subject_id, start_time, end_time, room`.
2. `EventDispatcher._handle_lesson_started_with_scheduling`:
   - `handle_lesson_started`: resolve subject_name через Academic gRPC (5-мин cache); для каждого `student` в группе с `telegram_id != 0` → `bot.send_message` с inline-кнопкой `WebAppInfo(url=config.mini_app_url/checkin?lesson_id=...)`; сохранить `message_id` в Redis `reminder:msgs:{lesson_id}:{user_id}` (RPUSH + EXPIRE 86400).
   - `reminder_scheduler.schedule_reminders(lesson_id, group_id, start_time, end_time)` → два `asyncio.create_task(_send_reminder_after(...))`.
3. `_send_reminder_after` спит до mid/near-end, потом для каждого студента: `get_message_ids(lesson_id, user_id)` — если пустой (значит, отметился или пара закрылась), skip; иначе `send_queue.put(SendTask(..."Напоминание: отметьтесь на паре!"))` → после успешного send → `redis_client.add_message_id(lesson_id, user_id, msg_id)`.
4. `attendance.marked` с `status ∈ {present, excused, free_attendance}` → `handle_attendance_marked` → `bot.delete_message` для всех сохранённых `message_ids` + `redis_client.delete_key`.
5. `lesson.closed` → `reminder_scheduler.cancel_lesson(lesson_id)` (cancel pending timer tasks) + `bot.delete_message` для всех студентов + `redis_client.delete_key`.

**Edge cases / риски**:
- **Timer in-memory** (P1-2): рестарт процесса = пропущенные reminder'ы.
- **Naive datetime** (P1-3): контейнер без TZ → все reminder'ы сдвинуты.
- **Race**: attendance.marked может прийти до того, как `bot.send_message` в lesson.started доехал до Redis (не успели `add_message_id` записать). Тогда `get_message_ids` вернёт пустой → delete не сделан, orphan message in Telegram. Вероятность мала (30 ms gap), но есть.
- **Lesson.cancelled** не вызывает `reminder_scheduler.cancel_lesson` — только `lesson.closed`. Если auto-scheduler публикует `lesson.cancelled` (через `lesson_cancelled.py`) без `lesson.closed` — таймеры остаются.
- **Multiple devices**: в Telegram пользователь видит одно сообщение на всех своих устройствах (это свойство Telegram) — бот хранит один `message_id`, что корректно.
- **Одна подписка**: если student присоединился к двум группам (теоретически) — `get_group_members` вернёт его в обеих, он получит 2 сообщения. В CLAUDE.md student состоит только в одной группе — не проблема.
- **Text reminder захардкожен**: `"Напоминание: отметьтесь на паре!"` — тот же текст на mid и на near-end. Хорошо бы различать («5 минут до конца пары!» для near-end).

---

## Excuse-тикеты

**Схема (59-*)**:

1. Студент создаёт excuse-ticket через **pwa** (не через бота!) — бот excuse не принимает от студента. В CLAUDE.md фраза «студент создаёт через бота» — **устаревшая**, в коде бота нет handler-а для создания тикета.
2. Attendance-service валидирует, сохраняет, публикует `excuse.requested` с `payload.group_id, user_id, student_name, excuse_type, lessons[], comment, ticket_id, file_payload_b64 (optional), file_name`.
3. Бот → `handle_headman_alert` (фильтр `is_headman=True, telegram_id > 0`) → рассылает старостам с inline-кнопками `[✅ Одобрить | ❌ Отклонить]`.
4. Если есть `file_payload_b64` ≤ 10 MB — `bot.send_document` с caption и buttons; иначе `bot.send_message`.
5. Каждая отправка → `request_tracker.add("excuse", ticket_id, chat_id, message_id)` (для последующего sync TG ↔ Web).
6. Староста жмёт кнопку → `excuse_router.handle_excuse_decision` → `event_publisher.publish("excuse.decision", {ticket_id, approved, decision_by})` + локальный edit сообщения (добавить «✅ Одобрено» / «❌ Отклонено», убрать клавиатуру).
7. Attendance-service consumer-ит `excuse.decision`, применяет решение в БД, публикует `excuse.decided`.
8. Бот → `_handle_excuse_decided` → (а) `handle_student_alert` уведомит студента через Telegram; (б) `_close_tracked_messages("excuse", ticket_id, verdict)` — у всех остальных старост уберёт кнопки и добавит reply с вердиктом (sync).

**Плюсы**:
- base64 в event — позволяет файлу пройти через fanout без shared storage.
- tracking chat_id+message_id в Redis — sync TG ↔ Web работает bidirectionally.
- edit_message_reply_markup(reply_markup=None) + send_message(reply_to) — минимально инвазивный UX.

**Минусы**:
- **P1-1**: ни роли, ни группы старосты не проверяются в callback.
- **P1-6**: файлы в RabbitMQ — нарушение «не хранится в системе».
- Caption truncation на 1024 (P2-5).
- `_excuse_type_label` — маппинг codes в русские строки: если attendance добавит новый тип, бот покажет raw code.
- Edit `caption`/`text` по текущей message не сохраняет оригинал (P2 Telegram API не возвращает body) — в `_close_tracked_messages` сделан `edit_message_reply_markup(None)` + отдельный reply, что элегантно.

CLAUDE.md фраза «студент создаёт через бота → файлы пересылаются старосте через Telegram (не хранятся в системе)» не соответствует реальности: студент создаёт через PWA, бэкенд эмбеддит файл в event, файл лежит в RabbitMQ до ACK.

---

## Геоотметка через Telegram

Специфического handler-а для гео-отметки из чата с ботом нет. Вместо этого в `lesson_started` рассылается **inline WebApp button** (`WebAppInfo(url=.../checkin?lesson_id=...)`), которая открывает mini-app. Логика геоотметки — в mini-app / PWA, не в Python-боте. Это правильно (бот не умеет геокодинг, Telegram не шлёт geo автоматически в inline-кнопках).

---

## Staroste / headman features

- Принять/отклонить `excuse.requested` и `late_checkin.requested` — через callback кнопки (P1-1).
- Добавить/удалить one-off lesson — **в коде бота нет handler-а**, это делается в web-panel (или PWA), бот только уведомляет группу через `handle_lesson_one_off_created/cancelled`.
- Открыть пару вручную — **в коде бота нет**. Authority — schedule-service.
- Просмотр статистики группы — **в коде бота нет**. Это в web-panel.
- Делегирование помощникам — **в коде бота нет**.

Бот старосты — это **workflow-button only**, реальная логика в Java-сервисах.

---

## Админ features

В боте нет admin-команд. Admin работает через web-panel. Это правильно — разделение ответственности.

---

## Ошибки / устойчивость

- `TelegramBadRequest` обрабатывается в 3 местах: `attendance_marked.py`, `lesson_closed.py`, `otp_verified.py` — корректно (idempotent delete).
- `grpc.aio.AioRpcError NOT_FOUND` конвертируется в `None` в `ScheduleGrpcClient.get_active_lesson`.
- `TokenExpiredError` в `AttendanceHttpClient` → `status.py` удаляет JWT из Redis и просит relogin.
- Нет обработки `TelegramForbiddenError` (bot blocked by user) — при `bot.send_message` в заблокированного юзера `send_queue._send_with_retry` сделает 3 retry (каждый раз `TelegramForbiddenError`), пометит failed. Нужно **перевести user в "disabled"** в БД/Redis и не слать ему до явной re-link.
- Нет `asyncio.TimeoutError` handling — gRPC по умолчанию без deadline. Если Academic зависнет, `handle_lesson_started` будет ждать вечно → отправка lesson.started занимает «до 5 минут» (Cache TTL спасает последующие вызовы).
- Нет circuit breaker для auth/academic/attendance — при массовом сбое сервиса бот будет делать 30 запросов/сек в него.

---

## Локализация

- Все user-facing тексты на русском, hardcoded в исходниках (ожидаемо для single-region).
- i18n middleware **отсутствует**, как и таблицы переводов.
- `parse_mode="HTML"` используется только где нужно выделить логин/пароль/название группы (5 мест). `MarkdownV2` не используется — хорошо (MarkdownV2 escaping — главная дыра Aiogram, тут её избежали).
- `html.escape()` на вводных данных **не делается** (P2-1).

---

## Тесты (Python)

### pytest setup

- `pytest.ini`: `asyncio_mode = auto` — все async-функции автоматически coroutine-тесты.
- `requirements-test.txt`: `pytest>=8.0`, `pytest-asyncio>=1.1.0`, `fakeredis[aioredis]>=2.34.0`.
- `conftest.py` — одна fixture `fake_redis` на `fakeredis.FakeAsyncRedis(decode_responses=True)`.
- `tests/fixtures/` — 2 JSON-файла с готовыми RabbitMQ-envelope-ами.
- 25 test-файлов, ~108 test cases, ~4 240 строк.

### Что покрыто хорошо

1. **Service-level**: `ReminderRedisClient` (happy + ConnectionError), `JwtRedisClient` (save/get/delete/TTL/Redis down), `TelegramSendQueue` (order, rate-limit, 429 retry, max retries, shutdown logs), `AcademicGrpcClient` (cache hit/expired/invalidate + error propagation), `ScheduleGrpcClient` (found/NOT_FOUND/other error).
2. **Notification handlers**: `lesson_started`, `lesson_closed`, `lesson_cancelled`, `attendance_marked` (все 4 статуса), `homework` (published + updated), `headman_alerts` (6 сценариев: role filter, text content, fallback, no-headman), `student_alerts` (approved/rejected/missing user_id/unknown status), `otp_verified`, `group_renamed/archived` (с 154/115 строк), `lesson_one_off_created/cancelled`.
3. **Handlers**: `/start` (4 сценария), `/login` (happy + 401/429/5xx), `/status` (no JWT, no active lesson, present, not-marked, token expired).
4. **Infrastructure**: `EventDispatcher` (routing + unknown + handler exception + has-all-event-types + reminder_scheduler DI), `run_with_watchdog` (restart on failure, propagate CancelledError, restart on normal exit), healthcheck (UP/DOWN).
5. **Reminder-шедулер**: delay calc (mid/near-end/zero/past), schedule/cancel lifecycle, skip-when-no-key, send-and-store.

### Что покрыто плохо / не покрыто

1. **НОЛЬ покрытия callback-хендлеров** (P1-1 — это и причина, почему нашёлся issue!):
   - `bot/handlers/excuse.py` — нет теста.
   - `bot/handlers/late_checkin.py` — нет теста.
   - Callback F.data parsing (bad input, unknown action, missing ticket_id) — никто не проверяет.
2. **НОЛЬ покрытия `bot/handlers/prefs.py`**:
   - `main_keyboard()` — есть в импортах `start.py`, но функциональных тестов нет.
   - `cmd_open_settings`, `cb_toggle_global`, `cb_toggle_category` — нет тестов.
3. **NotificationPrefsClient** — нет теста на `is_enabled(telegram_id, category)`, `set_category`, `get_categories`. Есть косвенный через `send_queue`.
4. **OtpMessageTracker** — нет unit-теста (только через `/login` и `otp_verified`).
5. **RequestMessageTracker** — нет теста.
6. **EventPublisher** — нет теста (publish happy/unhappy, reconnect).
7. **AuthHttpClient** — нет теста (покрывается через /login).
8. **AttendanceHttpClient** — нет теста (покрывается через /status).
9. **bot/__main__.py** — `create_clients()`, `run_health_server()` — нет теста.
10. **Integration-тестов с реальным RabbitMQ нет** (`Testcontainers` не используется).
11. **Интеграционных тестов Aiogram webhook / polling round-trip нет**.

### Некорректные / подозрительные тесты

1. **`test_reminder_scheduler.py:60-68`** — мокает модуль `datetime`, но внутри `_parse_hhmm_today` вызывается `datetime.now()` дважды — один раз в `_parse_hhmm_today`, второй в `midpoint_delay_seconds` itself. `mock_dt.now.return_value = frozen_now` — ок для обоих вызовов, но `datetime.now().replace(...)` требует, чтобы `datetime.now()` возвращал реальный objs с `.replace` — MagicMock подменяет всё, включая `.replace`. Тест проходит «потому что мок прозрачен», но правильнее использовать `freezegun`.
2. **`test_send_queue.py:104-113`** (`test_429_retry_after`): `patch("asyncio.sleep", new_callable=AsyncMock)` — глобальный patch на `asyncio.sleep` может сломать token-bucket (`_consume_token` тоже sleep-ит). Здесь работает только потому, что bucket pre-filled.
3. **`test_consumer_watchdog.py:83-112`** (`test_health_up_during_reconnect`): мутирует `main_module._consumer_task` / `_bot_task` — global state. Если тесты запускаются параллельно, race возможен. (`pytest-asyncio` по умолчанию sequential, но `pytest-xdist` сломает.)
4. **`test_login_handler.py:51-56`** (`_drain_tasks`): отменяет все живые asyncio-таски в event loop — может отменить таски других fixtures/pytest-asyncio internals. Хрупко.
5. **`test_excuse_decided.py:16`**: `FIXTURE_PATH = Path(__file__).parent / "fixtures" / "excuse_decided.json"` — не параметризовано, если фикстура изменится — тесты ломаются silently.

### Кандидаты на рефакторинг

- Общий `_make_student` / `_make_member` / `_make_event` дублируется в 5+ тест-файлах — вынести в `tests/factories.py`.
- `_capturing_send_queue()` тоже дублируется — helper.
- Мокать `bot` через `MagicMock(spec=Bot)` (Aiogram 3 Bot) вместо plain `MagicMock` — ловит метод-не-существует ошибки.

---

## Безопасность (checklist)

- [x] Secrets в ENV (`.env` + `pydantic-settings`), не в коде.
- [ ] **Secrets не маскируются** в `__repr__` → могут попасть в traceback (P2-2).
- [x] MarkdownV2 НЕ используется → no escape-баги. HTML используется в 5 местах.
- [ ] **HTML не экранируется** для gRPC-данных (group_name, display_name, login) — P2-1.
- [ ] **Нет rate-limiting** per-user (P1-5).
- [ ] **Role checks**: `is_headman` проверяется в `handle_headman_alert` (recipient-filter), но НЕ в callback-хендлерах (P1-1).
- [x] Input validation: callback-data parsed в 3 части и проверяется, `F.data.startswith("ex:")` работает.
- [x] user_id берётся из `event.from_user.id` (Telegram API), не из text.
- [ ] **gRPC без TLS**, secret опционален (P0-1).
- [ ] **bot_token имеет unsafe default** (P0-2).
- [ ] **Пароль в открытом чате** (P0-3).
- [x] TelegramBadRequest handling — idempotent deletes.
- [x] DLQ на RabbitMQ настроен (но handler exceptions не попадают туда, P1-4).
- [x] JWT expiry detected (`TokenExpiredError`) → Redis-cleanup.
- [x] `redis.asyncio` + password из env (pwd в url-encode-ит, корректно).
- [x] `aiohttp.ClientTimeout(total=10)` на auth-client (fail-fast).
- [ ] **Нет timeout** на attendance-client aiohttp (default infinite!).
- [ ] **Нет timeout** на gRPC вызовах (deadline).

---

## Зависимости между проблемами

```
P0-1 (insecure gRPC) ──┐
                       ├── связан с 01-auth P0-4 и с 02/03/04 P0 (все сервисы принимают x-grpc-secret опционально)
P0-2 (bot_token=placeholder) ─ независимо, simple fix
P0-3 (пароль в чате) ──┐
                       ├── требует изменения academic-service: initial_password_shown_at
                       └── может быть частично фикситься на стороне бота (spoiler + delete)

P1-1 (role check in callback) ─ зависит от P0-1 (без auth gRPC небезопасен для вызова из бота),
                                 но сам фикс — добавить lookup в callback handler

P1-2 (in-memory timers) + P1-3 (naive datetime) = «reminder'ы ненадёжны» — хотите исправить один, решайте оба одновременно
                                                   P1-3 тривиально (ENV TZ), P1-2 требует архитектурного решения

P1-4 (retry) + P1-7 (graceful shutdown) — оба про «событие не теряется», лучше фиксить пакетно:
  добавить retry-wrapper + signal-handler + await queue.join() на shutdown.

P1-5 (rate-limit) — middleware, независимо.

P1-6 (base64 в RabbitMQ) — требует attendance-service изменения, крупный redesign.

P1-8 (orphan tasks) — изолированный, добавить dict[telegram_id, Task] + cancel-on-new.

P2-1 (html.escape) — может быть исправлено одним sweep-ом по 5 файлам.
P2-8 (liveness check) — улучшает operability, независимо.
```

**Приоритет фиксов перед релизом**:
1. P0-2 (config validation) — **30 минут**.
2. P0-3 (пароль) — **4-8 часов** (требует academic migration).
3. P1-3 (TZ в Dockerfile) — **15 минут**.
4. P1-1 (role check) — **2 часа** (gRPC lookup + тест).
5. P1-5 (rate-limit middleware) — **3 часа**.
6. P2-1 (html escape) — **30 минут**.
7. P2-10 (HEALTHCHECK директива) — **5 минут**.

Остальное — в следующий релиз.

---

## Вопросы к владельцу проекта

1. **CLAUDE.md утверждает «студент создаёт excuse через бота»** — в коде бота этого handler-а нет. Устарела ли спецификация, или это ещё не имплементировано?
2. ✅ **Где хранится `initial_password` после первой раздачи?** Нужна ли история показа (чтобы `/start` не показывал пароль повторно)?
   → **ACCEPTED BY OWNER (2026-04-18)**: история показа НЕ ведётся, пароль показывается при каждом `/start` пока не сменён. См. `OWNER-ANSWERS.md` 06-Q2.
3. **TZ контейнера** — какой ожидаем (UTC / Europe/Moscow)? Тестировали ли reminder'ы на разных TZ?
4. **`grpc_secret` — обязательно или опционально**? Сейчас дефолт `""` = отсутствие проверки на стороне сервера, что соответствует?
5. **Рестарт бота в прод**е — как часто? Планируется ли HA-режим (2+ replicas)? Сейчас reminder-таймеры in-memory не переживут рестарт.
6. **Файлы excuse-тикетов** — действительно ли нет requirement хранить их 30+ дней для аудит-фокуса (проверки старостой)? Иначе base64-в-RabbitMQ — неправильное решение.
7. **polling vs webhook в прод**е: сейчас `dp.start_polling` — это полится c api.telegram.org каждые ~1 сек. За день это ~86 400 запросов (tariff Telegram — free, но latency 500-2000 ms). Webhook-ом могло бы быть ~50 ms. Рассматривался ли переход?
8. **Healthcheck с `curl` vs Python-based** — зачем тянуть 2 MB `curl`?

---

## Приложение А — Список найденных файлов и их размеры

```
69 .py файлов в bot/, из них ~790 строк сгенерированы (academic_pb2*.py + schedule_pb2*.py).
Чистый «написанный» код: ~3 150 строк.
Тестов: 25 файлов, ~4 240 строк.
Test-to-code ratio: ≈1.35 — хорошо для Python-проекта.
Самый большой модуль: headman_alerts.py (245 строк) — OK, это сложный UX-путь.
Самый большой test: test_headman_alerts.py (332 строки) — OK, 6 сценариев.
```

## Приложение Б — Анти-паттерны, НЕ найденные в коде

За время аудита **не найдено**:

- `bare except:` — 0 случаев.
- `except:` без Exception — 0 случаев.
- `eval(` / `exec(` — 0 случаев.
- `print(` в продакшн-коде — 0 случаев.
- `asyncio.run(` вне `__main__` — 0 случаев.
- TODO / FIXME / XXX / HACK — 0 случаев.
- hardcoded credentials (кроме дефолта в config) — 0 случаев.
- `os.environ["...";]` напрямую (вместо pydantic) — 0 случаев.
- `time.sleep` (блокирующий) в async-коде — 0 случаев.
- `requests` / `urllib3` (sync HTTP) — 0 случаев, только `aiohttp`.
- `__future__ import annotations` несогласованное — 2 файла имеют, остальные нет (nit).

Это хороший результат для Python-проекта на 3 000+ строк. Большая часть проблем — **архитектурные и security-specific**, не анти-паттерны стиля.
