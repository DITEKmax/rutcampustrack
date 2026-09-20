# Requests isolation — compact contract

Ревизия: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e`; риск: S3; writer: fresh
Luna max; worktree: `requests-isolation`; owner scope: только два integration
теста. Дети не создаются.

## 1. Goal

Воспроизвести и исправить combined isolation failure между `EventConsumerIT` и
`RabbitDecisionRetryIT`, сохранив фактические три попытки listener/DLQ и
поведение девяти lesson/semester cases. Full Requests transport acceptance не
входит в scope.

## 2. Context / evidence

Релизный источник остановлен и read-only:
`C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/student-role-02/requests-transport`.
Frozen manifest: `.agent/student-role-02/diff.json`, SHA256
`4725C54BF3F6300557697C4A756ED1565D5CCD8005F229C6C60AE3250744B2C4`, 91
entries = 82 product + 9 evidence. Source HEAD и назначенный baseline совпадают.
Исторический combined failure: 11 tests, `RabbitDecisionRetryIT` transient
case, line 93, `WantedButNotInvoked`; logged
`ResourceNotFoundException LateCheckinRequest507f1f77bcf86cd799439011notfound`
вместо настроенного Academic dependency failure. Гипотеза — distinct cached
Spring contexts вместе с extra `@MockitoBean StudentRequestService` и shared
static broker/fixed queue; свежая repro обязательна.

## 3. Relevant scope

Сначала импортировать ровно 82 product paths из frozen manifest и сверить source
и destination SHA256. Единственные product files, разрешённые для repair:

- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/EventConsumerIT.java`
- `services/attendance-service/attendance-app/src/test/java/ru/rutcampustrack/attendance/integration/RabbitDecisionRetryIT.java`

Собственные packet/evidence/checks/manifest находятся в
`.agent/student-requests-isolation-c/`; старые evidence в tracked product не
копируются. Shared generated/proto/config, production code, lockfiles и build
не изменяются.

## 4. Required behavior

После guarded import выполнить exact unmodified combined classes первым.
Зафиксировать test counts, XML/logs, broker queue/consumer/context/event
identity и доказать доставку intended listener без stale DLQ/pollution.
Bounded repair должен сохранять malformed decision DLQ exact event id,
transient decision ровно 3 mock calls и matching DLQ event id, а также все
9 lesson/semester cases; cached contexts не конкурируют за сообщения.

## 5. Constraints

Команды Gradle: `--no-parallel --max-workers=1`; initial runtime — только после
GO root, с command-scoped `TESTCONTAINERS_REUSE_ENABLE=false`, проверкой
effective config, Ryuk/labels/ports/lifecycle и owned cleanup. Порты/ресурсы
task-owned (`18530..18539`, DB/network/container prefix
`rct_student_gateway_requests`); чужие процессы/контейнеры не трогать. После
initial reproduction Gradle повторно не запускать до нового окна root. Не
менять production retry, assertions, sleeps/order forcing/skips/masks и не
использовать упрощённый `@DirtiesContext`, который не доказывает listener
isolation.

## 6. Existing patterns

`AbstractAttendanceIntegrationTest` использует static Mongo/Rabbit/Redis и
cached contexts; `@MockitoBean` differences создают distinct contexts.
RabbitConfig задаёт bounded three-attempt retry и fixed Attendance queue/DLQ;
fixture ранее проходил по классам отдельно, но combined 11/1 fail. Purge-only
недостаточно без доказанной consumer/context boundary.

## 7. Acceptance criteria

Есть свежая pre-fix combined reproduction с immutable XML/logs и объяснением
точной причины. После минимальной правки только в двух разрешённых IT combined
11 tests проходят с exact event matching, active-consumer isolation и сохранённым
поведением остальных fixture cases. Есть stable scope diff, hash guard для всех
82 imported entries, checks/runtime/evidence и честные limitations. Full
transport PASS не заявляется.

## 8. Verification

Записывать revision, exact command, exit code, Windows/OpenJDK/Docker runtime,
resources, test counts и artifact paths в `.agent/student-requests-isolation-c/`.
Первичная команда:

```text
.\gradlew.bat :services:attendance-service:attendance-app:integrationTest --tests "*EventConsumerIT" --tests "*RabbitDecisionRetryIT" --no-parallel --max-workers=1 --console=plain
```

Сначала сохранить failed XML/logs; повторные Gradle checks требуют окна root.
После repair нужен focused combined runtime, затем root организует fresh Sol
high independent review. Runtime N/A для full transport/config scopes.

## 9. Do not

Не писать parent/main/source worktree; не импортировать старые evidence как
product; не менять `AbstractAttendanceIntegrationTest`, production listener,
proto/config/build или shared generated files; не регенерировать; не удалять
чужие процессы/данные; не выполнять deploy/migration/secrets/real Telegram;
не эскалировать Terra без defect/complexity gate с request, reproduction,
новым evidence, correction и решением root.
