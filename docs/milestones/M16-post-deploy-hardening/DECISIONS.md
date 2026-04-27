# M16 Decisions

Architectural choices с обоснованием. Только то, что не очевидно из кода.

---

## D1 — Headman rate-limit: 300 req/min (повышен с 120)

**Контекст:** в `future-ideas.md` § F05 предлагался Redis-перенос с
сохранением лимита 120/min. По факту M15 staging staros'а делали bulk-mark
группы из 30 студентов меньше чем за минуту, упирались в лимит, бросали
повторы → жалобы.

**Решение:** при переносе в Redis (G7) одновременно поднять лимит до
300/min. Это ~5 запросов в секунду — реально использовать только при
bulk-операциях, не злоупотребление.

**Trade-off:** более слабая защита от compromised headman account, но
рост legitimate traffic compatibility критичнее. Если будет abuse —
понизить через env var (`ACADEMIC_HEADMAN_RL_PER_MINUTE`).

---

## D2 — OTel migration: Java HTTP/protobuf, Python gRPC (mixed-mode)

**Контекст:** Tempo поддерживает оба (4317 gRPC, 4318 HTTP). При
проверке кода обнаружено, что Java-сервисы и Python-бот используют
**разные** OTel exporters:

- Java (Spring Boot Micrometer) → HTTP/protobuf, default port 4318
- Python (notification-bot) → gRPC через
  `opentelemetry.exporter.otlp.proto.grpc.trace_exporter.OTLPSpanExporter`,
  default port 4317

**Что было сломано:** все клиенты были сконфигурированы на 4317.
Java-сервисы шлют HTTP frame в gRPC-порт → Connection reset → шум в
логах. Python-бот шлёт gRPC в gRPC-порт → работало.

**Решение:** mixed-mode.

- Java → 4318 + path `/v1/traces`
- Python → 4317 (без пути)

**Альтернатива:** унифицировать на gRPC (добавить
`opentelemetry-exporter-otlp` в Java и заменить Spring HTTP exporter).

**Почему не выбрали унификацию:** Spring Boot 3.4 Micrometer Tracing
использует HTTP exporter из коробки. Замена требует ручной
TracerProvider конфигурации, конфликт с Micrometer auto-config,
~1д работы + риск регрессии. Mixed-mode даёт zero-cost win.

**Trade-off:** в DECISIONS зафиксировано смешение протоколов. Tempo
парсит оба, но команда должна помнить разницу при дебаге OTel issues.

**Verify-checkpoint:** в M16 G1 implementation проверено через grep
импортов — `.proto.grpc.` (Python bot) vs Java Micrometer default
(HTTP). После migration лог-pattern в проде:
- Java: `OkHttp http://tempo:4318/v1/traces` — успех
- Python: `gRPC tempo:4317` — успех

---

## D3 — Audit log: SPI в shared-web, реализация в academic-app

**Контекст:** `@AdminAction` aspect живёт в `shared-web` (используется
всеми сервисами). Запись в `audit_log` таблицу должна быть в БД,
которая принадлежит конкретному сервису. Shared-модуль не должен знать
про academic_db.

**Решение:** SPI pattern.

```java
// shared-web/audit/AuditLogStorage.java
public interface AuditLogStorage {
    void store(AuditLogEntry entry);
}
```

Aspect вызывает SPI, конкретный сервис предоставляет implementation
(`JdbcAuditLogStorage` в academic-app использует local DataSource).

**Trade-off:** нужно реализовать storage в каждом сервисе который
использует `@AdminAction`. Сейчас только academic — других ADMIN-actions
не предполагается. Если появятся — schedule/attendance ADMIN endpoints
должны будут предоставить свой storage. Это нормально (audit log
хранится в БД того сервиса, который владеет данным доменом).

---

## D4 — TBD: mTLS Alertmanager → notification-web путь

Зафиксируется при выполнении G8.

Кандидаты:
1. Linkerd auto-mTLS sidecar — overhead (extra container per service), но zero-config
2. Custom certs + nginx proxy — контроль, но manual rotation
3. Минимальный путь: только `cap_drop: NET_RAW` для cadvisor + node-exporter + blackbox-exporter, оставив plaintext (sniffing capability убрана у потенциального compromised peer, MitM проблема остаётся)

Решение принимается с учётом текущей архитектуры (нет other Linkerd
usage → adding sidecar = большая зависимость).
