# RutCampusTrack

Микросервисная система учёта посещаемости для вуза.

## Стек технологий

| Компонент | Технология |
|-----------|-----------|
| Бизнес-сервисы | Java 21 + Spring Boot 3.4 |
| API Gateway | Spring Cloud Gateway |
| Telegram Bot | Python 3.12 + Aiogram 3.x |
| Сборка | Gradle 8.12 (Kotlin DSL) |
| gRPC | grpc-spring-boot-starter |
| Message Broker | RabbitMQ 3.13 |
| СУБД (структура) | PostgreSQL 16 |
| СУБД (посещаемость) | MongoDB 7 |
| Кэш / OTP | Redis 7 |
| Контейнеризация | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Mini App | React + Vite + TypeScript |
| Веб-панель | Angular + TypeScript |

## Структура проекта

```
rutcampustrack/
├── proto/                              gRPC контракты (.proto)
├── event-schemas/                      JSON Schema для событий RabbitMQ
├── services/
│   ├── api-gateway/                    Spring Cloud Gateway (:8080)
│   ├── auth-service/                   JWT, OTP, Redis (:9090)
│   ├── academic-service/
│   │   ├── academic-api-contract/      DTO, интерфейсы, enum-ы
│   │   └── academic-app/              Spring Boot app (:9091)
│   ├── schedule-service/
│   │   ├── schedule-api-contract/
│   │   └── schedule-app/              (:9092)
│   ├── attendance-service/
│   │   ├── attendance-api-contract/
│   │   └── attendance-app/            (:9093)
│   ├── notification-web/              WebSocket push (:9094)
│   └── notification-bot/              Python Aiogram
├── frontends/
│   ├── mini-app/                       React (Telegram Mini App)
│   ├── web-panel/                      Angular (админка)
│   └── landing/                        HTML + CSS
├── docs/                               Документация
├── docker-compose.yml                  Инфраструктура
└── build.gradle.kts                    Корневой Gradle
```

## Быстрый старт (dev)

```bash
# 1. Клонировать
git clone https://github.com/YOUR_USERNAME/rutcampustrack.git
cd rutcampustrack

# 2. Создать .env
cp .env.example .env

# 3. Поднять инфраструктуру
docker compose up -d

# 4. Проверить
docker compose ps

# 5. Собрать Java-сервисы
./gradlew build
```

## Порты

| Сервис | Порт | Доступ |
|--------|------|--------|
| API Gateway | 8080 | Единственный внешний |
| Auth Service | 9090 | Внутренний |
| Academic Service | 9091 | Внутренний |
| Schedule Service | 9092 | Внутренний |
| Attendance Service | 9093 | Внутренний |
| Notification Web | 9094 | Внутренний |
| RabbitMQ Management | 15672 | Dev only |

## Документация

- [Архитектура](docs/architecture.md)
- [Job Stories](docs/job-stories.md)
- [Схема БД](docs/database-schema.md)
- [Отчёт Фазы 0](docs/phase-0-report.md)
