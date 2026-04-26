# RutCampusTrack

Микросервисная система учёта посещаемости для РУТ (МИИТ).

![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-green)
![Python 3.12](https://img.shields.io/badge/Python-3.12-blue)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)
![CI](https://github.com/maksd/rutcampustrack/actions/workflows/ci.yml/badge.svg)

---

## Стек технологий

| Компонент | Технология |
|-----------|-----------|
| Бизнес-сервисы | Java 21 + Spring Boot 3.4 |
| API Gateway | Spring Cloud Gateway 4.x |
| Telegram Bot | Python 3.12 + Aiogram 3.x |
| Сборка | Gradle 8.12 (Kotlin DSL) |
| gRPC | grpc-spring-boot-starter |
| Message Broker | RabbitMQ 3.13 |
| СУБД (структура) | PostgreSQL 16 |
| СУБД (посещаемость) | MongoDB 7 |
| Кэш / OTP | Redis 7 |
| Контейнеризация | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| PWA (мобильный) | React + Vite + TypeScript |
| Mini App (Telegram) | React + Vite + TypeScript |
| Веб-панель (админка) | Angular 18 + TypeScript |
| Лендинг | HTML + CSS |
| Reverse Proxy (prod) | Nginx + Let's Encrypt |

---

## Архитектура

Система построена на принципах **database-per-service** и **contract-first**. Каждый сервис владеет только своей базой данных; прямых межсервисных SQL/Mongo-запросов нет. Синхронное межсервисное взаимодействие -- gRPC. Асинхронные события -- RabbitMQ (fanout exchange). Клиенты (PWA, Mini App, Web Panel) видят единственный внешний порт: API Gateway `:8080` в dev-режиме, nginx `:443` в production.

### Топология сервисов

```
+----------------------------------------------------------------------+
|                        DOCKER PRIVATE NETWORK                        |
|                                                                      |
|  Клиенты --> [API Gateway :8080]  Spring Cloud Gateway               |
|  (Web Panel, PWA, Mini App)                                          |
|               |  JWT-валидация (публичный ключ, локально)            |
|               |  Маршрутизация по пути                               |
|               |  Rate Limiting, Correlation ID                       |
|               |                                                      |
|               +---> [Auth Service :9090]          -> Redis (OTP, JWT) |
|               +---> [Academic Service :9091]      -> PostgreSQL       |
|               |                                      + Redis (кэш)   |
|               +---> [Schedule Service :9092]      -> PostgreSQL       |
|               +---> [Attendance Service :9093]    -> MongoDB          |
|                      +-- checkin/  (домен отметок)                   |
|                      +-- report/   (домен отчётов, изолирован)       |
|                                                                      |
|  [Notification Web :9094]  Java                                      |
|     +-- WebSocket push    -> Web Panel, PWA (real-time)              |
|     +-- Web Push adapter  -> Service Worker -> PWA (background push) |
|     +-- REST: /push/subscribe, /vapid-public-key                     |
|  [Notification Bot]        Python -- Telegram уведомления            |
|                                                                      |
|  [RabbitMQ :5672]  <-- события от Schedule, Attendance               |
|  [Redis :6379]     <-- OTP, кэш Academic, reminder msgs, VAPID keys |
+----------------------------------------------------------------------+
```

### Матрица: сервис -- хранилище

| Сервис | PostgreSQL | MongoDB | Redis | RabbitMQ |
|--------|-----------|---------|-------|----------|
| Auth Service | -- | -- | OTP, JWT key | -- |
| Academic Service | academic_db | -- | кэш | Publish: `group.updated`, `semester.archived` |
| Schedule Service | schedule_db | -- | -- | Publish: `lesson.started`, `lesson.closed` |
| Attendance Service | -- | attendance_db | -- | Publish: `attendance.marked`, `attendance.session.closed` |
| Notification Web | -- | push_subscriptions | VAPID keys | Consume: все события |
| Notification Bot | -- | -- | reminder msgs | Consume: все события |

---

## Структура проекта

```
rutcampustrack/
+-- proto/                              gRPC контракты (.proto)
+-- event-schemas/                      JSON Schema для событий RabbitMQ
+-- services/
|   +-- api-gateway/                    Spring Cloud Gateway (:8080)
|   +-- auth-service/                   JWT, OTP, Redis (:9090)
|   +-- academic-service/
|   |   +-- academic-api-contract/      DTO, интерфейсы
|   |   +-- academic-app/              (:9091)
|   +-- schedule-service/
|   |   +-- schedule-api-contract/
|   |   +-- schedule-app/              (:9092)
|   +-- attendance-service/
|   |   +-- attendance-api-contract/
|   |   +-- attendance-app/            (:9093)
|   +-- notification-web/              WebSocket + Web Push (:9094)
|   +-- notification-bot/              Python Aiogram
+-- frontends/
|   +-- pwa/                            React PWA (мобильный клиент)
|   +-- mini-app/                       React (Telegram Mini App)
|   +-- web-panel/                      Angular (админка)
|   +-- landing/                        HTML + CSS
+-- nginx/                              Reverse proxy + SSL config
+-- .github/workflows/                  CI + Deploy pipelines
+-- docker-compose.yml                  Dev инфраструктура
+-- docker-compose.prod.yml             Production compose (17 сервисов)
+-- build.gradle.kts                    Корневой Gradle
```

---

## Роли

| Роль | Возможности |
|------|-------------|
| ADMIN | Управление пользователями, группами, семестрами. Настройка глобального порога красной зоны посещаемости. |
| TEACHER | Read-only журнал посещаемости, статистика по группам и предметам. Без Telegram-бота. |
| STUDENT | Геоотметка на парах, excuse-тикеты, трекер домашних заданий. Уведомления через Telegram и PWA. |
| STUDENT (староста) | Расширенные права: ручное изменение статусов, управление порогами по группе/предмету, делегирование помощнику старосты. |

---

## Быстрый старт (Development)

### Требования

- Java 21 (рекомендуется Microsoft OpenJDK или Temurin)
- Docker + Docker Compose v2
- Node.js 18+ (для фронтендов)

### Шаги

```bash
# 1. Клонировать репозиторий
git clone https://github.com/maksd/rutcampustrack.git
cd rutcampustrack

# 2. Поднять инфраструктуру (PostgreSQL x2, MongoDB, Redis, RabbitMQ)
docker compose up -d

# 3. Проверить, что все контейнеры healthy
docker compose ps

# 4. Собрать все Java-сервисы
./gradlew build

# 5. Запустить отдельный сервис (например Auth)
./gradlew :services:auth-service:bootRun

# 6. Запустить фронтенд (PWA)
cd frontends/pwa
npm install
npm run dev
```

### Порты

| Сервис | Порт | Доступ |
|--------|------|--------|
| API Gateway | 8080 | Единственный внешний порт |
| Auth Service | 9090 | Внутренний |
| Academic Service | 9091 | Внутренний |
| Schedule Service | 9092 | Внутренний |
| Attendance Service | 9093 | Внутренний |
| Notification Web | 9094 | Внутренний |
| RabbitMQ Management | 15672 | Dev only |

### Тестовые учётные записи

Логины для dev-окружения: `student`, `teacher`, `admin`. Пароли задаются в seed-данных при инициализации Auth Service (см. `services/auth-service/src/main/resources/`).

---

## API документация (Swagger UI)

Swagger UI агрегирует спецификации всех сервисов через API Gateway. Сервисы используют springdoc-openapi с аннотациями `@Operation` / `@ApiResponse` в контрактных интерфейсах.

| Окружение | URL |
|-----------|-----|
| Development | `http://localhost:8080/swagger-ui.html` |
| Production | `https://{domain}/swagger-ui.html` |

### Группировка API

| Группа | Базовый путь | Описание |
|--------|-------------|----------|
| Auth | `/api/auth/**` | Логин, OTP, refresh, logout |
| Academic | `/api/academic/**` | Группы, студенты, семестры, предметы |
| Schedule | `/api/schedule/**` | Расписание, пары, аудитории |
| Attendance | `/api/attendance/**` | Отметки, журнал, отчёты |
| Notifications | `/api/notifications/**` | Web Push подписки, VAPID ключ |

Все эндпоинты, кроме `/api/auth/login` и `/api/auth/otp/*`, требуют JWT-токен в заголовке `Authorization: Bearer <token>`. Доступ ограничен по ролям (ADMIN, TEACHER, STUDENT) -- подробности в Swagger-аннотациях каждого эндпоинта.

---

## Тестирование

```bash
# Все Java-сервисы (unit + integration тесты)
./gradlew test

# Отдельный сервис
./gradlew :services:academic-service:academic-app:test
./gradlew :services:schedule-service:schedule-app:test
./gradlew :services:attendance-service:attendance-app:test

# Python bot (pytest)
cd services/notification-bot
pip install -r requirements-test.txt
python -m pytest tests/ -v

# Frontend (PWA)
cd frontends/pwa
npm test
```

Общее количество тестов: 350+ (Java: ~250, Python: ~108, Frontend: тесты компонентов).

---

## CI/CD

Проект использует два workflow в GitHub Actions.

### CI (`ci.yml`)

Запускается на каждый push и pull request. Три параллельных job:

1. **Java Build & Test** -- сборка и тестирование всех Java-сервисов через `./gradlew build` (Java 21, Temurin)
2. **Python Lint & Test** -- линтинг (ruff check + ruff format) и тесты notification-bot (pytest)
3. **Frontend Build & Test** -- npm ci, тесты и сборка PWA, Mini App, Web Panel

### Deploy (`deploy.yml`)

Запускается на push в ветку `main`. Два этапа:

1. **Build and Push to GHCR** -- собирает 11 Docker-образов и пушит в GitHub Container Registry:
   - `ghcr.io/maksd/rutcampustrack/api-gateway:latest`
   - `ghcr.io/maksd/rutcampustrack/auth-service:latest`
   - `ghcr.io/maksd/rutcampustrack/academic-service:latest`
   - `ghcr.io/maksd/rutcampustrack/schedule-service:latest`
   - `ghcr.io/maksd/rutcampustrack/attendance-service:latest`
   - `ghcr.io/maksd/rutcampustrack/notification-web:latest`
   - `ghcr.io/maksd/rutcampustrack/notification-bot:latest`
   - `ghcr.io/maksd/rutcampustrack/pwa-nginx:latest`
   - `ghcr.io/maksd/rutcampustrack/mini-app-nginx:latest`
   - `ghcr.io/maksd/rutcampustrack/web-panel-nginx:latest`
   - `ghcr.io/maksd/rutcampustrack/landing-nginx:latest`

2. **Deploy to VPS** -- подключение по SSH, `docker compose pull && docker compose up -d`

---

## Развёртывание (Production)

### Требования к серверу

- Ubuntu 22.04+ (или другой Linux с systemd)
- Docker + Docker Compose v2
- 4 GB+ RAM
- Порты 80 и 443 открыты в firewall
- Домен с DNS A-записью, указывающей на IP сервера

### GitHub Secrets

Для работы deploy workflow необходимо настроить следующие секреты в настройках репозитория (Settings -> Secrets and variables -> Actions):

| Секрет | Описание |
|--------|----------|
| `VPS_HOST` | IP-адрес или hostname VPS |
| `VPS_USER` | Имя пользователя SSH (например `deploy`) |
| `SSH_PRIVATE_KEY` | Приватный SSH-ключ для подключения к VPS |

### Переменные окружения (.env.prod)

На VPS в директории `/opt/rutcampustrack` создать файл `.env.prod` со следующими переменными:

```bash
# --- База данных ---
POSTGRES_ACADEMIC_PASSWORD=your_secure_password_here
POSTGRES_SCHEDULE_PASSWORD=your_secure_password_here

# --- RabbitMQ ---
RABBITMQ_USER=rct_user
RABBITMQ_PASSWORD=your_secure_password_here

# --- Telegram ---
BOT_TOKEN=your_telegram_bot_token_here
TMA_BOT_TOKEN=your_telegram_bot_token_here
MINI_APP_URL=https://t.me/YourBot/checkin

# --- SSL ---
DOMAIN=your-domain.ru
CERTBOT_EMAIL=your-email@example.com

# --- Web Push (VAPID) ---
VAPID_PUBLIC_KEY=your_vapid_public_key_here
VAPID_PRIVATE_KEY=your_vapid_private_key_here
VAPID_SUBJECT=mailto:noreply@your-domain.ru
```

**Внимание:** никогда не коммитьте `.env.prod` в репозиторий. Файл добавлен в `.gitignore`.

### Первое развёртывание

```bash
# 1. Подключиться к VPS
ssh deploy@your-server-ip

# 2. Клонировать репозиторий
git clone https://github.com/maksd/rutcampustrack.git /opt/rutcampustrack
cd /opt/rutcampustrack

# 3. Создать .env.prod (см. шаблон выше)
nano .env.prod

# 4. Авторизоваться в GHCR для pull образов
echo $GITHUB_TOKEN | docker login ghcr.io -u maksd --password-stdin

# 5. Выпустить SSL-сертификат (запускается один раз)
chmod +x nginx/scripts/init-letsencrypt.sh
./nginx/scripts/init-letsencrypt.sh

# 6. Запустить все 17 сервисов
docker compose -f docker-compose.prod.yml up -d

# 7. Проверить статус
docker compose -f docker-compose.prod.yml ps
```

### Обновление

После мерджа в `main` workflow `deploy.yml` автоматически:
1. Собирает 11 Docker-образов
2. Пушит их в GHCR
3. Подключается к VPS по SSH
4. Выполняет `docker compose -f docker-compose.prod.yml pull`
5. Выполняет `docker compose -f docker-compose.prod.yml up -d --remove-orphans`

Ручное обновление не требуется.

### SSL сертификаты

Первичный выпуск сертификата выполняется скриптом `./nginx/scripts/init-letsencrypt.sh`. После этого контейнер `certbot` в `docker-compose.prod.yml` автоматически проверяет и обновляет сертификат каждые 12 часов.

Ручное обновление при необходимости:

```bash
docker compose -f docker-compose.prod.yml exec certbot certbot renew
```

---

## Документация

- [Архитектура](docs/architecture/architecture.md) -- детальное описание сервисов, gRPC-контрактов, событий
- [Job Stories](docs/product/job-stories.md) -- бизнес-требования и сценарии использования
- [Схема БД](docs/architecture/database-schema.md) -- структура таблиц и коллекций
- [Дизайн-решения](docs/product/design-decisions.md) -- UI/UX, иконки, анимации, брендинг

---

## Лицензия

Файл лицензии пока не добавлен. Планируется MIT.
