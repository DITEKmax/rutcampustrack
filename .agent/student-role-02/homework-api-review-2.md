FAIL — 1 HIGH finding, Critical нет.

1. **HIGH — Java-first OpenAPI и generated TypeScript теряют `completedAt`.**

   - **File:line:** [StudentApiModels.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-api/services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java:206), [mobile-bff.json](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-api/docs/openapi/mobile-bff.json:612), [mobile-bff.ts](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-api/frontends/mobile-core/src/api/generated/mobile-bff.ts:242).
   - **Evidence:** Java records объявляют nullable `completedAt` обязательным полем у `StudentHomeworkItem` и `StudentHomeworkCompletion`, а runtime GET действительно сериализует его, что подтверждает [StudentHomeworkHttpGrpcIT.java](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/homework-api/services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/runtime/StudentHomeworkHttpGrpcIT.java:158). Canonical OpenAPI содержит только `id`/`completed` у PUT response и не содержит `completedAt` у обоих schemas; поиск дал `0` совпадений. Generated TS повторяет дефект: поля отсутствуют в обоих типах.
   - **Impact:** типизированный UI не может использовать серверный timestamp для «Выполнено сегодня». Это блокирует основную цель date-delta и позволяет canonical contract расходиться с фактическим JSON.
   - **Reproduction:** в mobile-core обратиться к `item.completedAt` для `StudentHomeworkItem` либо к `result.completedAt` для `StudentHomeworkCompletion`; TypeScript выдаст отсутствие свойства, хотя HTTP GET/PUT возвращает его.

**Repair contract**

- **Defect:** Springdoc/export исключает два runtime-поля `completedAt`.
- **Evidence:** Java model/runtime содержат поле; OpenAPI и generated TS не содержат.
- **Correction:** в Java-first BFF source явно экспортировать оба `completedAt` как required nullable `string/date-time`, затем перегенерировать canonical OpenAPI и mobile-core types.
- **Scope:** BFF contract/OpenAPI customization при необходимости, `docs/openapi/mobile-bff.json`, generated `mobile-bff.ts` и focused contract assertions. Не менять Academic date/domain logic, четыре предыдущих repair и UI.
- **Verification:** OpenAPI update и обязательный no-update pass; оба schemas содержат required nullable `completedAt`; TS generation/drift, mobile-core typecheck/lint/contract/fixtures; HTTP GET и PUT `true` возвращают timestamp, PUT `false` — `null`; финальная SHA/diff check. После исправления обязательна свежая независимая recheck затронутого diff.

Остальная bounded-проверка не выявила нового correctness/authz/error-handling/data-loss дефекта. Четыре прежних MEDIUM repair подтверждены HTTP matrix: overflow и strict boolean дают 400 без mutation RPC, отсутствующий active semester даёт 503, `no-store` присутствует включая 401.

Evidence проверено по оригиналам:

- HEAD `8002b9ea4356b10779c5bb9a6d99746d32d78ae2`; 25/25 SHA совпадают с frozen manifest; scoped `diff --check` exit 0.
- Фактические XML: Academic 152/152, PostgreSQL/date-delta 6/6, BFF HTTP/local-gRPC 7/7, BFF query 6/6; failures/errors/skipped — 0.
- [4601-142.txt](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/design-context/4601-142.txt) показывает задания на 1 и 2 сентября, а [4601-848636-refreshed-2026-09-07.txt](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/student-role-02/design-context/4601-848636-refreshed-2026-09-07.txt) группирует оба под «Выполнено сегодня».
- Academic OpenAPI residue только raw newline: filtered hash совпадает с HEAD `ee4580139e3b4f358b2b7ef9b6594862e574932f`, semantic diff пуст.
- Gitleaks report пуст. Известный отдельный dependency FAIL с 51 уникальной HIGH/CRITICAL уязвимостью остаётся открытым; этот verdict не является полным security/role PASS.
