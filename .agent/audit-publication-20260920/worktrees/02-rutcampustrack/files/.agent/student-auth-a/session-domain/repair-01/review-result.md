FAIL

Frozen scope стабилен: baseline/HEAD `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; packet, shared contract и manifest совпали с заданными SHA-256. Все 16 source-файлов совпали с manifest по SHA-256 и размеру. Три пары JUnit XML byte-identical: 17 тестов, 0 failures/errors/skips. Runtime/build повторно не запускался по ограничению задания.

Findings:

1. **HIGH — `SessionSnapshot.java:48-55,80-82` — не обеспечен fail-closed для `SUSPENDED`.**  
   Evidence: конструктор проверяет принадлежность и наличие `activeRole` в списке, но не `activeRole.isSelectable()`. Для `SUSPENDED` метод `isReadOnly()` возвращает `false`.  
   Impact: downstream-код, проверяющий наличие активной роли и `!isReadOnly()`, может воспринять suspended grant как writable authority. Это нарушает frozen contract: suspended denied/nonselectable.  
   Reproduction: создать один `RoleGrant(STUDENT, SUSPENDED)`, передать его одновременно как `activeRole` и единственный элемент `roles`. Snapshot успешно создаётся, `activeRole != null`, `isReadOnly() == false`.

2. **MEDIUM — `SessionStatePort.java:301-310` — logout result допускает ложный durable success.**  
   Evidence: при `alreadyRevoked=true` конструктор `RevokeResult` не требует, чтобы snapshot был revoked.  
   Impact: ошибочный adapter может вернуть успешный идемпотентный logout для живой сессии; вызывающая сторона очистит cookie, хотя серверная сессия останется действующей.  
   Reproduction: создать live `SessionSnapshot` и вызвать `RevokeResult.success(snapshot, true)` — исключения нет.

3. **MEDIUM — `ActiveRolePolicy.java:51-69,81-99` — публичный overload позволяет обойти ownership validation.**  
   Evidence: `Evaluation` и `Evaluation.success()` публичны; их можно создать вручную. `select(Evaluation, role)` повторно не проверяет user ownership или дубликаты.  
   Impact: публичный domain API способен вернуть успешную selection с чужим grant, вопреки требованию foreign grants rejected.  
   Reproduction: создать foreign `RoleGrant`, затем `Evaluation.success(List.of(foreign), foreign)` и вызвать `select(evaluation, STUDENT)`; результат `OK`.

4. **MEDIUM — `SessionLifecycleService.java:136-149,301-310` — validation и replacement hash не связаны одним секретом.**  
   Evidence: policy проверяет `newPassword`, а transaction port получает независимо переданный `replacementHash`.  
   Impact: trusted integration может по ошибке проверить один пароль и сохранить hash другого. В A нет внешнего пути передачи hash, поэтому это contract gap, а не доказанный внешний exploit.  
   Reproduction: передать policy-valid decoy в `newPassword` и hash другого слабого пароля в `replacementHash`; capturing port получит hash без ошибки. Frozen B1 contract не фиксирует same-secret derivation.

5. **MEDIUM — тестовый evidence не покрывает заявленные acceptance criteria.**  
   `SessionLifecycleServiceTest.java:42-290` не проверяет current logout, expired/revoked session negatives и authority failures для session commands. `PasswordPolicyTest.java:11-60` не проверяет категорию `S`, отсутствие normalization и lone low surrogate. Тестов на findings 1–4 также нет.  
   Impact: зелёные 17 тестов не подтверждают весь frozen packet.

Repair contract:

- **Defect:** закрыть четыре boundary-инварианта и недостающий verification.
- **Evidence:** findings 1–5 выше.
- **Correction:** запретить nonselectable `activeRole` в snapshot; требовать revoked snapshot для любого успешного `RevokeResult`; убрать/закрыть forgeable `select(Evaluation, …)` либо повторно валидировать ownership; закрепить same-secret derivation в trusted hasher/B1 adapter contract; добавить перечисленные behavior-focused negatives.
- **Scope:** соответствующие domain-типы/service и три focused test-класса; shared contract меняет только назначенный root writer, если binding остаётся обязанностью B1.
- **Verification:** повторить exact focused Gradle command, подтвердить новые negative cases и неизменность fixed refresh/role/password semantics, обновить manifest hashes/evidence.
- После исправления обязательна **fresh independent recheck** затронутых boundary и тестов. SQL transaction, HTTP/BFF/JWT и runtime остаются явно отложенными и не являются findings этого review.
