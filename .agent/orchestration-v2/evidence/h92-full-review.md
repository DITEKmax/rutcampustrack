FAIL — полный diff из 16 файлов.

Найден один блокирующий дефект в обязательном security-тесте. Подтверждённого дефекта production-кода нет.

- **Severity: Medium — reverse-audience тест проверяет неизвестный токен, а не настроенный credential другой аудитории.**  
  **File:** [ServiceIdentityServerInterceptorTest.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-l5b-service-identity/services/shared/shared-security/src/test/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptorTest.java:46), также строки 97–102.  
  **Evidence:** `reverseCredential` объявлен на строках 46–47, но тест создаёт interceptor с `List.of(academicCredential)`. Поэтому `REVERSE_TOKEN` отвергается как неизвестный. Фактический target guard сейчас корректен в [ServiceIdentityServerInterceptor.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-l5b-service-identity/services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/grpc/ServiceIdentityServerInterceptor.java:102), но обязательный reverse-audience сценарий его не проверяет.  
  **Impact:** удаление `target.equals(credential.target())` или аналогичная регрессия останется незамеченной существующим тестом. Frozen contract требует отрицательный сценарий для настроенного credential обратного направления.  
  **Reproduction:** в изолированной mutation-проверке убрать target guard на строке 104 и запустить существующий `reverseAudienceCredentialIsUnauthenticated`; он останется зелёным, поскольку reverse credential отсутствует в коллекции.

Repair contract:

- **Defect:** обязательная проверка audience binding подменена проверкой неизвестного токена.
- **Evidence:** объявленный `reverseCredential` не используется; interceptor получает только `academicCredential`.
- **Correction:** передать `List.of(academicCredential, reverseCredential)` и сохранить ожидание `UNAUTHENTICATED` с недопуском handler.
- **Scope:** только `ServiceIdentityServerInterceptorTest.java`; production, docs и build менять не требуется.
- **Verification:** новый freeze/hash, повтор targeted shared-security batch с нулевым drift остальных файлов, затем независимый полный recheck всего task diff.

Проверено без повторного запуска tests: H92 — 22 shared tests PASS, H91 — 42 app tests PASS; H91 остаётся overall FAIL из-за сохранённой исторической compile-test ошибки. Freeze [h92-source-freeze.json](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/h92-source-freeze.json) имеет ожидаемый SHA-256 `9CCA55FD…964`, все 16 путей прочитаны напрямую. Ограничение evidence сохраняется: TLS доказан локальными Netty test handlers, без deployed RPC/TLS readiness и без активации assignment closing.

Требуется независимый recheck после исправления. Слот освобождён.
