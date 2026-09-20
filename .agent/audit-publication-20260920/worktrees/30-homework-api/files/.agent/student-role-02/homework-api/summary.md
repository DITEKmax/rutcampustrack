# Student homework API summary

Реализован frozen student homework feed и desired-state completion surface
через Academic proto/domain/gRPC, Mobile BFF Java contract/client/query и
Java-first OpenAPI + generated mobile-core TypeScript. Completion использует
подписанный `STUDENT` Internal JWT, активный account/group/semester guard и
атомарный idempotent write поверх существующего unique completion constraint.
Read path сохраняет Moscow/default range, явную in-semester validation,
детерминированную сортировку и `null` для отсутствующей ссылки; GET/PUT
ответы имеют `no-store`.

Добавлены focused domain, BFF query и gRPC identity tests; исправлен только
необходимый exhaustive `ProblemCode` mapping в существующем
`MobileAttendanceClient` и strict-stubbing setup нового теста. Canonical
`docs/openapi/mobile-bff.json` экспортирован из Java annotations, после чего
сгенерирован `frontends/mobile-core/src/api/generated/mobile-bff.ts`.

Final checks: contract/proto compile, mobile-bff compile, 8 Academic tests,
3 BFF query tests, OpenAPI export, TS generation/drift, typecheck, lint,
frontend contract/fixture checks и diff check — все `exit 0`. Точный packet,
evidence, diagnostics, checks и diff manifest находятся в `.agent/`.

Ограничения: полноценный product runtime и реальная PostgreSQL concurrency
не запускались; это parent integration gate вместе с независимым Sol review.
