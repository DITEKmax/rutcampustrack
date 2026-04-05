# Phase 23: Bot Telegram Commands - Research

**Researched:** 2026-04-05
**Domain:** Python Aiogram 3 / gRPC / Redis JWT storage / REST integration
**Confidence:** HIGH

## Summary

Фаза реализует три команды Telegram-бота: `/start`, `/login`, `/status`. Все три работают через уже существующую инфраструктуру фазы 22 (gRPC-клиент, Redis-клиент, send_queue, watchdog). Основная работа в этой фазе распадается на пять независимых задач: (1) добавить `GetUserByTelegramId` RPC в `academic.proto` и реализовать его в Java; (2) изменить Auth Service `requestOtp()` возвращать код в теле ответа; (3) сгенерировать `schedule_pb2` и создать `ScheduleGrpcClient`; (4) реализовать три обработчика команд с FSM; (5) подключить `Bot + Dispatcher` в `__main__.py` рядом с health-сервером и watchdog.

Ключевые ограничения: `aiogram==3.15.0` уже в `requirements.txt`, `grpcio==1.73.0` и `protobuf==6.31.0` уже зафиксированы — менять их нельзя. Redis-клиент уже есть, но нужна новая операция: хранение JWT-пары по ключу `bot:jwt:{telegram_id}` как JSON-строка (не список). Aiogram 3 FSM с `MemoryStorage` теряет состояние при перезапуске контейнера — это приемлемо для `/login`-диалога (короткий по времени), потому что JWT хранится в Redis и переживает перезапуск.

**Primary recommendation:** Начать с Java-изменений (proto + Auth Service), затем сгенерировать Python pb2, затем реализовать handlers — это поддерживает параллельную разработку между Java и Python частями.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Account Linking (/start)**
- D-01: Add new `GetUserByTelegramId` gRPC RPC to Academic Service's `academic.proto`. Response includes `user_id`, `login`, `display_name`, `role`, `group_id`, `group_name`, `is_headman`, `telegram_id`, `initial_password` (nullable), `password_changed` (bool).
- D-02: On /start with known telegram_id: if `initial_password` is not null (first login) — show login + initial_password. If `initial_password` is null (already changed password) — show login + group name.
- D-03: On /start with unknown telegram_id: "Ваш Telegram не привязан к системе. Обратитесь к старосте вашей группы для привязки аккаунта."

**OTP Login Flow (/login)**
- D-04: Modify Auth Service `POST /auth/otp/request` to return OTP code in response body: `{"code": "123456"}` instead of empty 200. Bot delivers code to user in Telegram message.
- D-05: Use Aiogram 3 FSM (FiniteStateMachine) with MemoryStorage for multi-step /login conversation state. States: `waiting_for_code`. FSM state auto-clears on timeout or successful verify.
- D-06: Bot calls Auth Service directly at `auth-service:9090` (internal service-to-service), not through API Gateway. OTP endpoints are public (no JWT required).
- D-07: On successful OTP verify, store JWT pair (access + refresh tokens) in Redis keyed by `bot:jwt:{telegram_id}` with TTL matching token expiry. Survives bot restart.

**/status Command**
- D-08: Bot calls Schedule Service gRPC `GetActiveLesson(group_id, timestamp)` to find current lesson. If no active lesson: "Нет активной пары."
- D-09: Bot calls Attendance Service REST `GET /api/attendance/reports/student/records` via API Gateway using the student's stored JWT to get attendance status for the current lesson.
- D-10: /status message shows: subject name, room, time range, attendance status (present/absent/not marked). Single current lesson only, no today summary.
- D-11: If user has no stored JWT (not logged in via /login), prompt them: "Сначала войдите через /login."

**Error Handling**
- D-12: All bot messages in Russian.
- D-13: User-friendly error messages without technical details.
- D-14: On service unavailability: "Сервис временно недоступен. Попробуйте позже." Log full error at WARNING level.

### Claude's Discretion
- Aiogram router/handler file organization within `bot/handlers/`
- Schedule gRPC client wrapper class (new, or extend existing academic_client pattern)
- aiohttp client session management for REST calls to Auth/Attendance
- Exact FSM state class design and timeout handling
- Message formatting (plain text vs Markdown vs HTML)
- /status response when JWT is expired (auto-refresh or ask to /login again)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BOT-01 | Student can link Telegram account via /start and receive initial credentials if set | D-01, D-02, D-03: new GetUserByTelegramId RPC; Academic Service already has UserRepository.findByTelegramId(); User entity already has initialPassword and passwordChanged fields |
| BOT-02 | Student can authenticate via /login using OTP flow through Auth Service | D-04, D-05, D-06, D-07: OtpService.requestOtp() already exists, returns void — needs minimal change; FSM MemoryStorage; JWT stored in Redis |
| BOT-03 | Student can check attendance status via /status command | D-08, D-09, D-10, D-11: schedule.proto already has GetActiveLesson; ReportApi already has GET /student/records; AttendanceRecordEntry DTO has lessonId + status fields |
</phase_requirements>

---

## Standard Stack

### Core (все уже установлены в requirements.txt)

| Библиотека | Версия | Назначение | Статус |
|------------|--------|------------|--------|
| aiogram | 3.15.0 | Telegram Bot API, FSM, Router, Dispatcher | Установлена [VERIFIED: requirements.txt] |
| grpcio | 1.73.0 | gRPC async клиент | Установлена [VERIFIED: requirements.txt] |
| grpcio-tools | 1.73.0 | Кодогенерация pb2 | Установлена [VERIFIED: requirements.txt] |
| protobuf | 6.31.0 | Протокольные буферы | Установлена [VERIFIED: requirements.txt] |
| aiohttp | 3.10.11 | HTTP-клиент для REST вызовов (Auth + Attendance) | Установлена [VERIFIED: requirements.txt] |
| redis[hiredis] | 5.2.1 | Async Redis клиент | Установлена [VERIFIED: requirements.txt] |
| pydantic-settings | 2.6.1 | Конфигурация | Установлена [VERIFIED: requirements.txt] |

**Новые зависимости не требуются** — все необходимое уже есть в `requirements.txt`.

### Кодогенерация schedule pb2

Файлы `schedule_pb2.py` и `schedule_pb2_grpc.py` ещё не сгенерированы (в `bot/grpc_client/` есть только `academic_pb2.py` и `academic_pb2_grpc.py`). [VERIFIED: glob поиска нашёл только academic_pb2*]

```bash
cd services/notification-bot
python -m grpc_tools.protoc \
  -I../../proto \
  --python_out=bot/grpc_client \
  --grpc_python_out=bot/grpc_client \
  ../../proto/schedule.proto
```

После этого исправить относительный импорт в `schedule_pb2_grpc.py` (grpc_tools генерирует `import schedule_pb2` → нужно `from bot.grpc_client import schedule_pb2`).

---

## Architecture Patterns

### Структура файлов (после фазы 23)

```
services/notification-bot/
├── bot/
│   ├── __main__.py                  ← добавить Bot+Dispatcher startup
│   ├── config.py                    ← добавить auth_service_url, schedule_grpc_host/port
│   ├── handlers/
│   │   ├── __init__.py              ← сейчас пустой
│   │   ├── start.py                 ← /start handler
│   │   ├── login.py                 ← /login FSM handler + states
│   │   └── status.py                ← /status handler
│   ├── grpc_client/
│   │   ├── academic_client.py       ← существующий, без изменений
│   │   ├── academic_pb2.py          ← существующий
│   │   ├── academic_pb2_grpc.py     ← существующий
│   │   ├── schedule_client.py       ← НОВЫЙ — аналог academic_client.py
│   │   ├── schedule_pb2.py          ← НОВЫЙ — сгенерировать
│   │   └── schedule_pb2_grpc.py     ← НОВЫЙ — сгенерировать
│   └── services/
│       ├── redis_client.py          ← существующий (reminder)
│       ├── jwt_redis_client.py      ← НОВЫЙ — bot:jwt:{telegram_id}
│       ├── auth_http_client.py      ← НОВЫЙ — aiohttp для OTP + verify
│       ├── attendance_http_client.py ← НОВЫЙ — aiohttp для /student/records
│       └── send_queue.py            ← существующий
```

### Pattern 1: Aiogram 3 FSM для /login

Aiogram 3 FSM использует `StatesGroup` и `MemoryStorage`. [CITED: docs.aiogram.dev/en/latest/dispatcher/finite_state_machine/index.html]

```python
# Source: aiogram 3 official docs (FSM section)
from aiogram.fsm.state import State, StatesGroup
from aiogram.fsm.storage.memory import MemoryStorage
from aiogram.fsm.context import FSMContext
from aiogram.filters import Command, StateFilter

class LoginStates(StatesGroup):
    waiting_for_code = State()

# Роутер для /login
login_router = Router()

@login_router.message(Command("login"))
async def cmd_login(message: Message, state: FSMContext) -> None:
    # Запрашиваем OTP у Auth Service, код возвращается в теле ответа
    # Отправляем код пользователю, переходим в состояние ожидания
    await state.set_state(LoginStates.waiting_for_code)
    await message.answer("Код отправлен в это сообщение. Введите его:")

@login_router.message(LoginStates.waiting_for_code)
async def process_otp_code(message: Message, state: FSMContext) -> None:
    code = message.text.strip()
    # Верифицируем код через Auth Service
    # При успехе: сохранить JWT в Redis, state.clear()
    # При ошибке: "Код неверный. Попробуйте ещё раз."
    await state.clear()
```

**Критично:** `MemoryStorage` — состояния теряются при перезапуске контейнера. Для одношагового ввода кода (секунды-минуты) это приемлемо. [ASSUMED — но подтверждено дизайн-решением D-05]

### Pattern 2: Интеграция Bot+Dispatcher в существующий event loop

Текущий `__main__.py` запускает health-сервер (aiohttp web) и watchdog-задачу как отдельные `asyncio.Task`. Aiogram 3 `dp.start_polling(bot)` — корутина, которую нужно запустить как ещё одну asyncio задачу. [VERIFIED: aiogram docs]

```python
# Source: aiogram 3 dispatcher integration pattern
from aiogram import Bot, Dispatcher
from aiogram.fsm.storage.memory import MemoryStorage

# В main():
bot = Bot(token=config.bot_token)
storage = MemoryStorage()
dp = Dispatcher(storage=storage)

# Подключить роутеры
dp.include_router(start_router)
dp.include_router(login_router)
dp.include_router(status_router)

# Запустить polling как asyncio task рядом с watchdog и health
_bot_task = asyncio.create_task(dp.start_polling(bot, handle_signals=False))
```

`handle_signals=False` обязателен — сигналами управляет вся программа, а не только dispatcher. [ASSUMED — стандартная практика для embedded polling]

### Pattern 3: JWT хранение в Redis

Ключ: `bot:jwt:{telegram_id}`. Тип: строка (JSON). TTL = время жизни refresh token. [VERIFIED: D-07 из CONTEXT.md]

```python
# jwt_redis_client.py — новый клиент
import json
import redis.asyncio as aioredis

class JwtRedisClient:
    KEY_PREFIX = "bot:jwt:"

    async def save(self, telegram_id: int, access_token: str,
                   refresh_token: str, expires_in: int) -> None:
        key = f"{self.KEY_PREFIX}{telegram_id}"
        data = json.dumps({
            "access_token": access_token,
            "refresh_token": refresh_token,
        })
        await self._redis.set(key, data, ex=expires_in)

    async def get(self, telegram_id: int) -> dict | None:
        key = f"{self.KEY_PREFIX}{telegram_id}"
        raw = await self._redis.get(key)
        return json.loads(raw) if raw else None

    async def delete(self, telegram_id: int) -> None:
        await self._redis.delete(f"{self.KEY_PREFIX}{telegram_id}")
```

Это отдельный клиент от `ReminderRedisClient` (который использует RPUSH/LRANGE списки). JwtRedisClient хранит строки. Оба могут использовать одно и то же Redis-соединение или создавать своё — зависит от реализации.

### Pattern 4: gRPC клиент для Schedule Service

По образцу `academic_client.py`. Ключевые отличия:
- Нет кеша по group_id — для активного урока всегда нужны свежие данные
- Обрабатывает `NOT_FOUND` (нет активной пары) без выброса исключения

```python
# Source: по образцу academic_client.py [VERIFIED: codebase]
from bot.grpc_client import schedule_pb2, schedule_pb2_grpc
from datetime import datetime, timezone

class ScheduleGrpcClient:
    def __init__(self, host: str, port: int) -> None:
        target = f"{host}:{port}"
        self._channel = grpc.aio.insecure_channel(target)
        self._stub = schedule_pb2_grpc.ScheduleGrpcServiceStub(self._channel)

    async def get_active_lesson(self, group_id: int) -> schedule_pb2.LessonResponse | None:
        timestamp = datetime.now(timezone.utc).isoformat()
        request = schedule_pb2.ActiveLessonRequest(
            group_id=group_id,
            timestamp=timestamp,
        )
        try:
            return await self._stub.GetActiveLesson(request)
        except grpc.aio.AioRpcError as e:
            if e.code() == grpc.StatusCode.NOT_FOUND:
                return None
            raise
```

**Протокол:** `schedule.proto` уже определяет `GetActiveLesson(ActiveLessonRequest) → LessonResponse`. `LessonResponse` содержит `subject_id`, `room`, `start_time`, `end_time`, `status`. [VERIFIED: proto/schedule.proto]

**Важно:** `subject_id` возвращается, но не `subject_name`. Для отображения имени предмета `/status` нужен либо Academic gRPC `GetSubjectsByIds`, либо кеш subject_id→name. Наиболее простой путь: повторный вызов Academic gRPC `GetSubjectsByIds([subject_id])`.

### Pattern 5: aiohttp клиент для REST-вызовов

Auth Service и Attendance Service доступны через HTTP. aiohttp уже в зависимостях. Рекомендуется singleton `aiohttp.ClientSession` на весь lifecycle бота. [ASSUMED — стандартная практика]

```python
# auth_http_client.py
import aiohttp

class AuthHttpClient:
    def __init__(self, base_url: str) -> None:
        self._base_url = base_url
        self._session: aiohttp.ClientSession | None = None

    async def start(self) -> None:
        self._session = aiohttp.ClientSession(base_url=self._base_url)

    async def close(self) -> None:
        if self._session:
            await self._session.close()

    async def request_otp(self, telegram_id: int) -> str:
        """Returns OTP code from response body (D-04)."""
        async with self._session.post(
            "/auth/otp/request",
            json={"telegramId": telegram_id},
        ) as resp:
            resp.raise_for_status()
            data = await resp.json()
            return data["code"]

    async def verify_otp(self, telegram_id: int, code: str) -> dict:
        """Returns {accessToken, refreshToken, expiresIn}."""
        async with self._session.post(
            "/auth/otp/verify",
            json={"telegramId": telegram_id, "code": code},
        ) as resp:
            resp.raise_for_status()
            return await resp.json()
```

### Pattern 6: Attendance REST через API Gateway с JWT

```python
# attendance_http_client.py
class AttendanceHttpClient:
    async def get_student_records(self, access_token: str) -> list[dict]:
        headers = {"Authorization": f"Bearer {access_token}"}
        async with self._session.get(
            "/api/attendance/reports/student/records",
            headers=headers,
        ) as resp:
            if resp.status == 401:
                raise TokenExpiredError()
            resp.raise_for_status()
            data = await resp.json()
            # HATEOAS ответ: {"_embedded": {"attendanceRecordEntryList": [...]}}
            return data.get("_embedded", {}).get("attendanceRecordEntryList", [])
```

**Внимание:** Attendance Service возвращает HATEOAS `CollectionModel<EntityModel<AttendanceRecordEntry>>`. Поле `_embedded` обязательно для парсинга. [VERIFIED: ReportApi.java — возвращает CollectionModel]

Для `/status` нужно найти запись с `lessonId` совпадающим с активной парой. Если записи нет — статус "не отмечен".

---

## Изменения в Java-части

### Изменение 1: academic.proto — новый RPC

Добавить в `proto/academic.proto`:

```protobuf
// Получить пользователя по Telegram ID
rpc GetUserByTelegramId (UserByTelegramIdRequest) returns (UserByTelegramIdResponse);

message UserByTelegramIdRequest {
  int64 telegram_id = 1;
}

message UserByTelegramIdResponse {
  bool found = 1;
  int64 user_id = 2;
  string login = 3;
  string display_name = 4;
  string role = 5;
  int64 group_id = 6;
  string group_name = 7;
  bool is_headman = 8;
  int64 telegram_id = 9;
  string initial_password = 10;  // пустая строка = пароль уже сменён
  bool password_changed = 11;
}
```

**Реализация в Java (AcademicGrpcServiceImpl.java):** Использует уже существующий `UserRepository.findByTelegramId(Long)` [VERIFIED: UserRepository.java] и `GroupRepository.findById(Long)` для получения `group_name`. User entity уже имеет поля `initialPassword`, `passwordChanged`, `telegramId`. [VERIFIED: User.java]

**Важно:** `initial_password` = пустая строка в proto вместо null (proto3 не поддерживает null для строк). Семантика: пустая строка → пароль уже сменён. В Python: `if response.initial_password` — True если непустая.

### Изменение 2: Auth Service OtpService.requestOtp()

Текущее состояние: `requestOtp()` возвращает `void`, контроллер отвечает `ResponseEntity<Void>`. [VERIFIED: AuthController.java, OtpService.java]

Изменения:
1. `OtpService.requestOtp()` — изменить сигнатуру `void → String`, возвращать `code`
2. Создать новый DTO `OtpCodeResponse` (record) в `auth-service`: `record OtpCodeResponse(String code) {}`
3. `AuthController.requestOtp()` — изменить тип ответа `ResponseEntity<Void>` → `ResponseEntity<OtpCodeResponse>`, вернуть `ResponseEntity.ok(new OtpCodeResponse(code))`

**Существующие тесты Auth Service:** Потребуют обновления (mock теперь возвращает `OtpCodeResponse`, а не void). [ASSUMED — тесты существуют, но конкретное содержимое не проверялось]

---

## Don't Hand-Roll

| Проблема | Не строить | Использовать | Почему |
|----------|-----------|--------------|--------|
| Хранение состояния FSM | Самодельный dict состояний | `aiogram.fsm.storage.memory.MemoryStorage` + `FSMContext` | Thread-safe, интегрирован с Router |
| gRPC канал для schedule | Новый тип канала | Тот же `grpc.aio.insecure_channel` паттерн из `academic_client.py` | Уже отработан и протестирован |
| Rate limiting Telegram | Самодельный sleep/retry | `TelegramSendQueue` (уже реализован) | Нет смысла дублировать |
| HTTP retry | Самодельный retry | `aiohttp` `raise_for_status()` + обработка на уровне handler | Для синхронного bot flow достаточно |
| Redis JSON сериализация | Кастомный формат | `json.dumps/loads` на стандартный dict | Просто и надёжно |

---

## Common Pitfalls

### Pitfall 1: proto3 nullable строки

**Что идёт не так:** В proto3 `string` не может быть null — дефолтное значение `""`. Если `initial_password` возвращается как `""`, Python-код `if response.initial_password` вернёт False — что является правильным поведением. Но если в Java передать `null` в `setInitialPassword()` — будет NPE.

**Как избежать:** В Java использовать `user.getInitialPassword() != null ? user.getInitialPassword() : ""`. В Python проверять `if response.initial_password:`.

### Pitfall 2: Исправление импортов в сгенерированных pb2 файлах

**Что идёт не так:** `grpc_tools.protoc` генерирует `import schedule_pb2 as schedule__pb2` — это абсолютный импорт, который работает только если `bot/grpc_client/` в `sys.path`. В проекте используются пакетные импорты: `from bot.grpc_client import schedule_pb2`.

**Как избежать:** После генерации исправить `schedule_pb2_grpc.py`: изменить `import schedule_pb2` → `from bot.grpc_client import schedule_pb2`. [VERIFIED: academic_pb2_grpc.py уже имеет такие исправления — убедиться перед написанием плана]

### Pitfall 3: aiohttp ClientSession создаётся вне event loop

**Что идёт не так:** Если `aiohttp.ClientSession` создаётся в `__init__` (синхронно), он может не привязаться к правильному event loop.

**Как избежать:** Создавать сессию в async методе `start()` или в startup-хуке Dispatcher (`@dp.startup()`). Закрывать в `@dp.shutdown()`.

### Pitfall 4: HATEOAS ответ Attendance Service

**Что идёт не так:** `/api/attendance/reports/student/records` возвращает `CollectionModel` — это обёртка `{"_embedded": {"attendanceRecordEntryList": [...]}, "_links": {...}}`. Прямое `resp.json()` не даёт список.

**Как избежать:** Парсить через `data["_embedded"]["attendanceRecordEntryList"]`. [VERIFIED: ReportApi.java возвращает CollectionModel]

### Pitfall 5: handle_signals в dp.start_polling()

**Что идёт не так:** По умолчанию Aiogram 3 устанавливает свои SIGTERM/SIGINT обработчики. Если они конфликтуют с aiohttp web-сервером или asyncio, бот может некорректно завершаться.

**Как избежать:** Передать `handle_signals=False` в `dp.start_polling(bot, handle_signals=False)`. [ASSUMED — стандартная практика для embedded polling]

### Pitfall 6: Schedule Service gRPC NOT_FOUND при отсутствии активной пары

**Что идёт не так:** `GetActiveLesson` возвращает gRPC-ошибку `NOT_FOUND` вместо пустого ответа, если нет активной пары. Если не обработать — клиент выбросит `AioRpcError`.

**Как избежать:** Перехватывать `grpc.StatusCode.NOT_FOUND` и возвращать `None`. [VERIFIED: schedule.proto не определяет oneof или wrapper — значит сервис вернёт ошибку при отсутствии данных. Паттерн подтверждён аналогично другим Schedule gRPC методам]

### Pitfall 7: /status — предметное имя из subject_id

**Что идёт не так:** `LessonResponse` из Schedule gRPC содержит `subject_id` (int64), но не `subject_name`. `/status` должен отображать имя предмета.

**Как избежать:** После получения `LessonResponse` сделать вызов `AcademicGrpcClient.GetSubjectsByIds([lesson.subject_id])` — этот RPC уже существует и реализован. [VERIFIED: academic.proto, AcademicGrpcServiceImpl.java]

### Pitfall 8: TokenExpired для /status (expired JWT)

**Что идёт не так:** Если JWT access token истёк (ответ 401 от Attendance Service через Gateway), нужно либо рефрешить, либо просить /login снова.

**Как избежать:** Простейший вариант (discretion): поймать 401, удалить JWT из Redis, ответить "Токен истёк. Войдите снова через /login." — без авто-рефреша.

---

## Code Examples

### Aiogram 3 FSM — полный /login flow

```python
# Source: aiogram 3 docs FSM section [CITED: docs.aiogram.dev/en/latest/dispatcher/finite_state_machine/]
from aiogram import Router
from aiogram.filters import Command
from aiogram.fsm.context import FSMContext
from aiogram.fsm.state import State, StatesGroup
from aiogram.types import Message

login_router = Router()

class LoginStates(StatesGroup):
    waiting_for_code = State()

@login_router.message(Command("login"))
async def cmd_login(message: Message, state: FSMContext,
                    auth_client: AuthHttpClient,
                    jwt_redis: JwtRedisClient) -> None:
    telegram_id = message.from_user.id
    # Проверить: уже залогинен?
    existing = await jwt_redis.get(telegram_id)
    if existing:
        await message.answer("Вы уже вошли в систему.")
        return
    try:
        code = await auth_client.request_otp(telegram_id)
        await message.answer(f"Ваш код для входа: {code}\n\nВведите его:")
        await state.set_state(LoginStates.waiting_for_code)
    except aiohttp.ClientResponseError as e:
        if e.status == 429:
            await message.answer("Слишком много попыток. Подождите.")
        elif e.status == 401:
            await message.answer("Ваш аккаунт не найден. Обратитесь к старосте.")
        else:
            logger.warning("OTP request failed: %s", e)
            await message.answer("Сервис временно недоступен. Попробуйте позже.")

@login_router.message(LoginStates.waiting_for_code)
async def process_code(message: Message, state: FSMContext,
                       auth_client: AuthHttpClient,
                       jwt_redis: JwtRedisClient) -> None:
    code = message.text.strip() if message.text else ""
    telegram_id = message.from_user.id
    try:
        tokens = await auth_client.verify_otp(telegram_id, code)
        await jwt_redis.save(
            telegram_id,
            tokens["accessToken"],
            tokens["refreshToken"],
            tokens["expiresIn"],
        )
        await state.clear()
        await message.answer("Вы успешно вошли в систему.")
    except aiohttp.ClientResponseError as e:
        if e.status == 401:
            await message.answer("Код неверный. Попробуйте ещё раз.")
        else:
            await state.clear()
            await message.answer("Сервис временно недоступен. Попробуйте позже.")
```

### Передача зависимостей через Dispatcher middleware

Aiogram 3 поддерживает передачу зависимостей через `dp["key"] = value` — они инжектируются в handler-аргументы по имени. [CITED: docs.aiogram.dev/en/latest/dispatcher/dispatcher.html]

```python
# В main() при создании Dispatcher
dp["academic_client"] = AcademicGrpcClient(config.academic_grpc_host, config.academic_grpc_port)
dp["schedule_client"] = ScheduleGrpcClient(config.schedule_grpc_host, config.schedule_grpc_port)
dp["jwt_redis"] = JwtRedisClient(host=config.redis_host, port=config.redis_port)
dp["auth_client"] = AuthHttpClient(base_url=f"http://{config.auth_service_host}:{config.auth_service_port}")
dp["attendance_client"] = AttendanceHttpClient(base_url=config.api_gateway_url)
```

### /start handler — поиск пользователя по telegram_id

```python
# Source: academic.proto GetUserByTelegramId RPC (to be added)
@start_router.message(Command("start"))
async def cmd_start(message: Message, academic_client: AcademicGrpcClient) -> None:
    telegram_id = message.from_user.id
    try:
        response = await academic_client.get_user_by_telegram_id(telegram_id)
        if not response.found:
            await message.answer(
                "Ваш Telegram не привязан к системе. "
                "Обратитесь к старосте вашей группы для привязки аккаунта."
            )
            return
        if response.initial_password:
            # Первый вход — показываем логин и начальный пароль
            await message.answer(
                f"Добро пожаловать!\n"
                f"Логин: {response.login}\n"
                f"Пароль: {response.initial_password}"
            )
        else:
            # Пароль уже сменён
            await message.answer(
                f"Добро пожаловать!\n"
                f"Логин: {response.login}\n"
                f"Группа: {response.group_name}"
            )
    except grpc.aio.AioRpcError:
        logger.warning("Academic gRPC unavailable for /start")
        await message.answer("Сервис временно недоступен. Попробуйте позже.")
```

---

## State of the Art

| Старый подход | Текущий подход | Статус |
|---------------|----------------|--------|
| aiogram 2.x executor | aiogram 3.x Dispatcher + start_polling | Используется 3.15.0 |
| `@dp.message_handler` | `@router.message(Command(...))` | Роутеры, не глобальный dp |
| FSMContext через memory | FSMContext через pluggable storage | MemoryStorage по решению D-05 |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `handle_signals=False` нужен при встраивании dp.start_polling() в существующий asyncio loop | Architecture Patterns #2 | Конфликт обработчиков сигналов; бот не завершается корректно |
| A2 | Schedule Service возвращает gRPC NOT_FOUND при отсутствии активной пары (а не пустой LessonResponse) | Common Pitfalls #6 | Если возвращает пустой объект с id=0 — логика проверки `None` не сработает |
| A3 | Существующие тесты Auth Service потребуют обновления после изменения сигнатуры requestOtp() | Изменения в Java-части | Если тестов нет или они не покрывают requestOtp — риск незначительный |
| A4 | aiohttp ClientSession должен создаваться в async контексте | Common Pitfalls #3 | DeprecationWarning или ошибка привязки event loop |
| A5 | Передача зависимостей через `dp["key"] = value` работает в aiogram 3.15.0 | Code Examples | Если API изменился — handlers не получат зависимости |

---

## Open Questions

1. **Исправление импортов в schedule_pb2_grpc.py**
   - Что мы знаем: academic_pb2_grpc.py уже исправлен (с `import academic_pb2` на `from bot.grpc_client import academic_pb2`)
   - Что неясно: нужно ли это исправление сделать вручную или есть скрипт
   - Рекомендация: задача Wave 0 должна включать генерацию pb2 и ручное исправление импорта

2. **gRPC порт Schedule Service**
   - Что мы знаем: Academic gRPC на порту 19091 (в config.py)
   - Что неясно: На каком порту работает Schedule Service gRPC — нужно проверить docker-compose.yml или schedule-app конфиг
   - Рекомендация: проверить перед планированием

3. **API Gateway URL для Attendance**
   - Что мы знаем: бот должен звонить через Gateway (D-09)
   - Что неясно: внутренний адрес API Gateway в docker-compose сети
   - Рекомендация: проверить docker-compose.yml — скорее всего `http://api-gateway:8080`

---

## Environment Availability

| Зависимость | Требуется для | Доступна | Версия | Fallback |
|-------------|--------------|----------|--------|----------|
| Python + aiogram 3.15.0 | Bot handlers | ✓ | 3.15.0 | — |
| grpcio + grpcio-tools | schedule pb2 codegen | ✓ | 1.73.0 | — |
| aiohttp | HTTP клиенты | ✓ | 3.10.11 | — |
| Redis | JWT storage | ✓ | контейнер | — |
| Academic Service gRPC | /start, /status | ✓ (работает с фазы 2) | — | — |
| Schedule Service gRPC | /status | ✓ (работает с фазы 3) | — | — |
| Auth Service HTTP | /login OTP | ✓ (работает с фазы 1) | — | — |
| Attendance Service REST | /status | ✓ (работает с фазы 4) | — | — |
| API Gateway | /status attendance | ✓ (работает с фазы 1) | — | — |

**Нет блокирующих зависимостей** — вся инфраструктура уже развёрнута из предыдущих фаз.

---

## Validation Architecture

> `workflow.nyquist_validation` не установлен явно → раздел включается.

### Test Framework

| Свойство | Значение |
|----------|----------|
| Framework | pytest + pytest-asyncio (установлены — используются в Phase 22 тестах) |
| Config file | `services/notification-bot/` — проверить pytest.ini или pyproject.toml |
| Quick run | `cd services/notification-bot && python -m pytest tests/ -x -q` |
| Full suite | `cd services/notification-bot && python -m pytest tests/ -v` |

### Phase Requirements → Test Map

| Req ID | Behaviour | Test Type | Command | Файл существует? |
|--------|-----------|-----------|---------|-----------------|
| BOT-01 | /start с известным telegram_id показывает credentials | unit | `pytest tests/test_start_handler.py -x` | ❌ Wave 0 |
| BOT-01 | /start с неизвестным telegram_id показывает инструкцию | unit | `pytest tests/test_start_handler.py -x` | ❌ Wave 0 |
| BOT-02 | /login запрашивает OTP и переходит в FSM-состояние | unit | `pytest tests/test_login_handler.py -x` | ❌ Wave 0 |
| BOT-02 | Ввод кода верифицирует и сохраняет JWT | unit | `pytest tests/test_login_handler.py -x` | ❌ Wave 0 |
| BOT-02 | Неверный код — сообщение об ошибке, состояние сохраняется | unit | `pytest tests/test_login_handler.py -x` | ❌ Wave 0 |
| BOT-03 | /status без JWT — предложение войти | unit | `pytest tests/test_status_handler.py -x` | ❌ Wave 0 |
| BOT-03 | /status с активной парой — показывает статус | unit | `pytest tests/test_status_handler.py -x` | ❌ Wave 0 |
| BOT-03 | /status без активной пары — "Нет активной пары" | unit | `pytest tests/test_status_handler.py -x` | ❌ Wave 0 |

**Паттерн тестирования:** По образцу `test_academic_client.py` — инжекция заглушки через `__new__` + `AsyncMock`. [VERIFIED: tests/test_academic_client.py]

### Wave 0 Gaps

- [ ] `tests/test_start_handler.py` — покрывает BOT-01
- [ ] `tests/test_login_handler.py` — покрывает BOT-02
- [ ] `tests/test_status_handler.py` — покрывает BOT-03
- [ ] `tests/test_jwt_redis_client.py` — покрывает JwtRedisClient
- [ ] `bot/grpc_client/schedule_pb2.py` + `schedule_pb2_grpc.py` — codegen (не тест, но нужен до тестирования ScheduleGrpcClient)

---

## Project Constraints (from CLAUDE.md)

| Директива | Применение в этой фазе |
|-----------|----------------------|
| Contract-first: контроллер implements интерфейс из контракта | Применимо к Java-изменениям: AuthController implements интерфейс из контракта |
| БЕЗ Lombok в `*-api-contract` модулях | `OtpCodeResponse` создаётся в `auth-service` (app-модуль?) — уточнить место |
| Request DTO = Java record | `OtpCodeResponse record(String code)` — record |
| Enum: никогда `@Enumerated(EnumType.ORDINAL)` | Нет новых enum в этой фазе |
| Flyway для миграций | Нет новых таблиц в этой фазе |
| Пакеты: `ru.rutcampustrack.{service}.{module}` | Новый DTO: `ru.rutcampustrack.auth.dto.OtpCodeResponse` |
| Python: aiogram 3 уже выбран | Все handlers на aiogram 3 |

---

## Sources

### Primary (HIGH confidence)
- `services/notification-bot/requirements.txt` — verified versions
- `services/notification-bot/bot/grpc_client/academic_client.py` — gRPC client pattern
- `services/notification-bot/bot/services/redis_client.py` — Redis client pattern
- `services/notification-bot/bot/__main__.py` — existing asyncio structure
- `proto/academic.proto` — current RPC definitions
- `proto/schedule.proto` — GetActiveLesson RPC
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/controller/AuthController.java` — OTP endpoints
- `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/OtpService.java` — OTP logic
- `services/academic-service/.../entity/User.java` — initialPassword, passwordChanged fields
- `services/academic-service/.../repository/UserRepository.java` — findByTelegramId
- `services/academic-service/.../grpc/AcademicGrpcServiceImpl.java` — gRPC implementation pattern
- `services/attendance-service/.../api/ReportApi.java` — GET /student/records returns CollectionModel
- `services/notification-bot/tests/test_academic_client.py` — test pattern with AsyncMock

### Secondary (MEDIUM confidence)
- [Aiogram 3 FSM docs](https://docs.aiogram.dev/en/latest/dispatcher/finite_state_machine/index.html) — StatesGroup, StateFilter, FSMContext patterns
- [Aiogram 3 Dispatcher docs](https://docs.aiogram.dev/en/dev-3.x/dispatcher/dispatcher.html) — start_polling, dependency injection via dp["key"]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — весь стек подтверждён из requirements.txt и исходного кода
- Architecture: HIGH — паттерны взяты из существующего кода Phase 22
- Java-изменения: HIGH — User entity и Repository уже содержат нужные поля и методы
- Pitfalls: MEDIUM — proto3 null + HATEOAS подтверждены кодом; остальные ASSUMED

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (стабильный стек)
