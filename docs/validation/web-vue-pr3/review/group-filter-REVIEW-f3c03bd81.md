# Независимое group filter/export review f3c03bd81 +6289ab528

Результат: **correction required: один подтверждённый P2**.

Scope: frozen `52eb8520c..f3c03bd81b3d44873abff2ad7126d9a000e51381` плюс frontend-only6289ab528bd664eec619cdea14403926a10da690, всего17файлов. Reader использовал gitshow/diff, не moving worktree. Read-only, без source edits/commits/push/Gradle/Docker/install/CI/heavy/детей. Запрошенная модельgpt-6.1-sol/high; runtime telemetry недоступна. Map/assistant CAS не открывались.

## P2 — legitimate async filtered ADMIN_USERS export отклоняется из-за numeric JsonNode type

**Место:** `services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/export/AdminRegistryExportService.java:313–315`.

Новый `RegistryExportModels.Users.groupId` имеет Java type Long. Exact selector guard сравнивает `mapper.readTree(storedJson).equals(mapper.valueToTree(query))`. Для обычного groupId11 JSON parse даёт IntNode, а valueToTree Long record component даёт LongNode. ObjectNode/JsonNode equality считает их разными, хотя на wire оба равны числу11.

**Реальный путь:** ADMIN_USERS job с groupId11 → `AcademicReportRenderController.java:55–63` convertValue stored selector в Users record → registry.users с реальными jobId/lease/readScope → unconditional asyncGeneration (users:161) → строка314 получает false →403 «Разрез задания изменился». Пользовательская выгрузка выбранной группы не может подготовить файл. Это не authz broadening; допустимый exact admitted selector ошибочно отвергается.

**Почему текущий PASS этого не покрывает:** новый UserSearchIT проверяет direct `/academic/registry-exports/users` без job/lease, а asyncGeneration при обоих null возвращает false до comparison (строка292). ExportJobServiceAuthorityTest проверяет Objects.equals на typed groupId в UserRegistryReadScope, где Long/Long равны, но не последующий JSON guard. Поэтому реальный query/direct-export PASS совместим с данным failure.

**Независимое подтверждение и actual mapper:** bounded JShell probe с установленным Jackson2.21.4, Java21, heap128MiB, без Spring/Gradle/DB, получил `storedGroup=IntNode,queryGroup=LongNode,equal=false` на record(Long groupId,String role,String format) со значением11L. Проверен именно конфигурационный путь injected mapper: AdminRegistryExportService принимает общий Spring ObjectMapper; Academic WebConfig добавляет только case-insensitive enums; shared JacksonConfig меняет unknown enums/unknown properties/date timestamps, не numeric node coercion. USE_LONG_FOR_INTS/custom Long serializer/JsonNodeFactory не найдены в Academic/shared source. CacheConfig создаёт отдельный локальный Redis mapper, не этот bean. JavaTime/enum configuration не нормализует numeric node type. Первая local JShell попытка имела runtime classpath failure; повтор с теми же тремя Jackson jars на execution parent classpath подтвердил causal case. Это не продуктовый compile/runtime failure и не запуск тяжёлого suite.

Нормализовать обе стороны в одинаковое JSON representation или сравнивать typed canonical selectors, сохраняя запрет удаления/изменения groupId и всех остальных selector fields. Нужен адресный regression именно async job/render guard с малым положительным groupId; проверка только typed requireSelector недостаточна. Старый absent-group bindingHash/wire должен сохраниться.

Finding немедленно передан root и backend owner; исправления/root integration остаются за ними.

## Остальной scoped source без новых P1/P2

- JPA matchesGrant помещает STUDENT/HEADMAN role/status/group в один EXISTS; native ADMIN_ARCHIVE_FILTERS повторяет те же role/status conditions в group EXISTS. При TEACHER/ADMIN выбранный role/status проверяется отдельно от retained STUDENT/HEADMAN group link. No-role group filter допускает только эти group-bearing grants. Датированная student_group_history не используется и transferred-away history не расширяет результат.
- Обе ветки используют bound numeric group parameter, без SQL string interpolation. Native page/count разделяют один ADMIN_ARCHIVE_FILTERS; EXISTS не размножает пользователей. JPA/native sort получают stable id tie-break. Existing roleStatus-without-role guard и archived account semantics сохранены.
- UserController остаётся selected ADMIN/USERS_VIEW; IdentityRegistryQueryBarrier/live Auth admission не обходится. Unknown positive group возвращает empty page, не требует lookup existence; invalid group<=0 отвергается до SQL, invalid lexical ID — на HTTP conversion.
- Direct registry export и report read используют тот же UserService predicate. Stored UserRegistryReadScope явно сравнивает groupId вместе с search/role/status/roleStatus/sorts/format; removal/change не расширяют admission. Остальные async lease/actor/kind/expiry guards не ослаблены.
- Auth canonical ADMIN_USERS suffix с groupId добавляется только при non-null; отсутствующий group не меняет старую последовательность и пропускается на JSON wire через NON_NULL. Positive validation есть в Auth/Academic DTO, service и shared manual clients. Frontend6289 только передаёт existing uppercase account status parameter. Генератор ограничивает Academic regeneration новым flag, Schedule/Auth outputs не переписывает.

## Evidence и пределы

Прочитаны summary/checks/artifacts JSON из `/tmp/group-filter-*-20261024*`. Reviewer самостоятельно разобрал XML:

- run2 UserSearchIT: archive/status и invalid/unknown/permission сценарии PASS; mixed scenario тогда FAIL fixture до GET.
- run3 UserSearchIT: исправленный mixed/transfer/HEADMAN/paging/direct-export сценарий PASS, failures/errors0. Это не повторная приёмка остальных сценариев.
- run2 ExportJobServiceAuthorityTest10, AdminUsersSelectorTest1, Academic/Auth OpenApiSnapshotIT по1 — failures/errors0.

Прочитанные tests содержательны: same-grant coupling, mixed teacher/graduated/suspended links, transfer-history exclusion, count/paging, matching direct export, retained archived/restored grants, wrong role/LIMITED без USERS_VIEW, invalid/unknown IDs, removal/change stored selector, old absent hash literal/wire. Они не покрывают указанный async JSON boundary.

`git diff --check 52eb8520c..6289ab528` — PASS. Generated Academic diff содержит только новые optional groupId в соответствующих operation/schema types; Auth snapshot не менялся. Авторские client28/status10/formatter/drift/build PASS сообщены evidence, заново не запускались. JAR sourceCommit metadata f3c03bd81 из artifacts JSON прочитана; source/artifact match/rollout/root runtime не объявляются независимо выполненными.

Требуется узкая correction/recheck P2 до source gate. Новых broader protocol/endpoint/migration требований этот review не вводит.
