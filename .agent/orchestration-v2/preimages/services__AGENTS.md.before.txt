# Backend / BFF

- Java 21, Spring Boot, Gradle wrapper; версии/команды — из build и CI. Сверяй
  технические инварианты с новым требованием. BFF формирует публичный ответ, доменный
  сервис владеет правилами, auth операции и транзакцией; клиент не заменяет authz.
- До реализации согласуй contract diff и единый источник генерации; до ADR не создавай
  конкурирующие ручные OpenAPI/Java DTO. REST снаружи, gRPC внутри; детали — из
  принятого TRANSPORT после переноса.
- Для изменения события укажи producer, schema/version, exchange, routing key,
  queue/consumers, outbox, dedup, retry/DLQ, ordering и тесты повторной доставки.
- Не объединяй feature с посторонним большим refactor: зафиксируй текущее поведение,
  внеси feature, проверь; refactor отдельно. Старые данные/клиенты не требуют
  совместимости, но это не разрешает удалять БД: baseline и seeds — в чистой среде.
- Auth/authz, concurrency, schema migration, data-loss риск — S3.
  Проверяй отказ в доступе, чужой scope, повторные команды, частичные отказы
  и применимые временные/транзакционные инварианты.
- Реальные endpoints, integration behavior и логи проверяй по `rct-verification`.
  Не отключай защиту и не ослабляй тест только для зелёного результата.
