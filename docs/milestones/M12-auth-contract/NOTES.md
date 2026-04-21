# M12 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу.

---

## Вопросы к owner'у до старта

1. **Docker image naming:** `auth-service` → `auth-app` или оставить
   `auth-service`? Pattern в проекте: image name = service name
   (не app-module). **Default:** оставить `auth-service` image name;
   поменять только build path внутри Dockerfile.

2. **Internal endpoints policy:** показывать в public `/v3/api-docs`
   или полностью скрыть?
   **Default:** `@Hidden` на `InternalAuthApi` interface — endpoints
   работают, но не появляются в swagger-ui и openapi spec (public
   frontend'ы о них не знают).

3. **Separate interface per internal controller?**
   `InternalIssuerController` и `InternalWsTicketController` — два
   разных internal flow. Варианты:
   - (a) Один `InternalAuthApi` interface покрывает оба
   - (b) Разделить на `InternalIssuerApi` + `InternalWsTicketApi`
   **Default:** (b) — разделить для чистоты границ.

4. **DTO records vs classes:** текущие 14 DTO — часть records,
   часть classes (с Lombok). Переводить все в records?
   **Default:** да, все request DTO → Java records; response DTO
   если используется HATEOAS `RepresentationModel` — оставляем как
   class без Lombok (см. правило в CLAUDE.md).

## Ожидаемые surprises

- **Lombok в WsTicketResponse** — может быть `@Data`/`@Builder`.
  После переноса в contract-модуль (БЕЗ Lombok) нужно руками
  написать getters/setters или перевести в record.
- **Package renaming сломает import'ы в 30+ файлах.**
  IntelliJ автоматически обновит, но IT-тесты (строковые package
  references в YAML/properties) могут требовать ручного grep'а.
- **Hibernate/Jackson ObjectMapper annotations на DTO** — если есть
  `@JsonProperty`/`@JsonIgnore`, нужно сохранить при переносе.
- **Tests с `@MockitoBean` на controller** могут использовать DTO
  classes не через contract package — потребуется fix imports.
- **`@Valid` annotations** остаются на method signatures (в controller,
  не в interface) — Spring Validation работает на runtime level.
- **OpenAPI duplicate paths** — если interface имеет @RequestMapping
  и controller тоже (accidental не удалили) — Spring выдаст
  "Ambiguous mapping" на startup. Проверить через `./gradlew bootRun`.

## Связь с другими milestones

### С M07 Frontend Hardening
- M07 Группа 3 создаёт openapi-typescript generator.
- **M12 regenerate** triggered вручную (или через pre-commit hook
  если M07 его добавит).
- **Порядок:** M07 → M12. Без M07 generator'а frontend migration
  в M12 придётся делать руками.

### С M10 Notification History
- Независим. M10 создаёт `notification-api-contract` (pattern reference),
  M12 создаёт `auth-api-contract` — параллельно.

### С M11 OpenAPI Polish
- **M11 → M12 обязательный порядок.**
- M11 определяет `@Schema` policy (description + example на DTO
  fields), `GlobalErrorResponsesCustomizer` применяет стандартные
  error responses ко всем endpoints.
- **M12 применяет M11 policy к auth-api-contract** сразу при
  создании (не потом retrofit).
- После M12 conformance CI gate (M11) расширяется на auth-service.

### С M08 Test Infrastructure
- AuthApiContractTest (ArchUnit rule) — использует ArchUnit framework
  из M02/M05/M08.
- IT тесты остаются зелёными после M12 (binary-compatible).
- Coverage gate (M08) для `auth-app` остаётся 60% (не pilot 70%).

## Binary-compatibility checklist

Критерии «binary-compatible refactor»:

- [ ] JSON payload каждого endpoint идентичен до/после (diff через
      postman collection или actual curl)
- [ ] HTTP status codes не изменились
- [ ] Error response shape (ErrorResponse RFC 9457) не изменился
- [ ] Content-Type headers не изменились (application/json для
      request, application/problem+json для errors)
- [ ] Endpoint URLs идентичны (/auth/login, /auth/otp/request, etc.)
- [ ] `@Valid` validation rules (constraints на DTO fields)
      перенесены вместе с DTO

## Deferred в v0.1+

- **Separate JWT issuer module** (если захотим extract JWT logic
  в shared-security pattern) — v0.1, не блокер.
- **OAuth2/OIDC migration** — v0.2+, другой scope.
- **Refresh-token rotation** с хранением fingerprint — v0.1+.

## Post-migration smoke checklist

Перед закрытием milestone:

- [ ] Login flow: `POST /auth/login` с валидным user → 200 + token
- [ ] Login flow invalid: `POST /auth/login` с неверным паролем → 401
      + RFC 9457 ErrorResponse
- [ ] OTP flow: `POST /auth/otp/request` → 204 (после M09)
- [ ] TMA flow: `POST /auth/tma/login` с корректным HMAC → 200 + token
- [ ] Refresh: `POST /auth/refresh` → 200 + new token
- [ ] Change-password: `PATCH /auth/password` → 204
- [ ] WS ticket: `POST /auth/ws-ticket` → 200 + ticket
- [ ] Internal issuer: `POST /internal/auth/issue` (с правильным
      internal JWT) → 200
- [ ] `/v3/api-docs` содержит public endpoints, НЕ содержит internal
- [ ] Frontend generated types регенерированы и login работает в
      PWA + web-panel

---
