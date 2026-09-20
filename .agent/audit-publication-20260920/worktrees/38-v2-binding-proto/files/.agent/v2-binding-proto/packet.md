# L3 — Homework binding proto ownership correction

Статус: frozen source-only implementation packet with recorded authorized test
addenda. Исполнитель: fresh Luna max;
Terra запрещена, детей нет. Worktree: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/v2-binding-proto`, ветка `codex/v2-binding-proto`.

Rules: `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md`;
CURRENT amendment; LESSONS-L3 frozen packet. Rules SHA256:
`B256A175274987DA9710D804B3C050A5DBCB47D8448CC03644D74168B52A437A`.
Base revision: `b8220ac92125a8afa37598b270aa4fab7aa1f470` (E).

## 1. Goal

Перенести только декларации Homework binding в owning Schedule gRPC contract;
lifecycle/entity/handler implementation не заявляется.

## 2. Context / evidence

Root/lessons открыли `proto/academic.proto`, `proto/schedule.proto` и V17.
Schedule V17 владеет `lesson_homework_bindings`; scoped Java search не нашёл
handwritten binding consumers/handlers в `services`.

## 3. Relevant scope

Основные product files: `proto/academic.proto`, `proto/schedule.proto`.
По L3 scoped compile-blocker correction разрешены только два дополнительных
test files: `services/schedule-service/schedule-app/src/test/java/ru/rutcampustrack/schedule/security/ScheduleUserContextFilterIT.java`
и `ScheduleUserContextFilterStrictModeIT.java`. После H8 main отдельно
разрешил только `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contractexport/OpenApiSnapshotIT.java`
для исправления одного устаревшего JWT-вызова; R5 сохраняет свой независимый
worktree. Shared factory/production не трогаются. Metadata:
`.agent/v2-binding-proto/*`; `AGENTS.md` содержит pointer
на owner RULES/CURRENT/LESSONS. Оригинальный checkout и E worktree не изменяются.

## 4. Required behavior

Переместить `ReserveHomeworkBinding`, `ConfirmHomeworkBinding`,
`GetHomeworkBindings` в `ScheduleGrpcService` вместе с
`HomeworkBindingState`, `ReserveHomeworkBindingRequest`,
`ConfirmHomeworkBindingRequest`, `HomeworkBindingResponse`,
`HomeworkBindingsRequest`, `HomeworkBindingsResponse`.
Сохранить имена, номера, wire types, optionality и enum values: Reserve 1–4,
Confirm 1–3, Response 1–11, `current_lesson` tag 3 типа
`rutcampustrack.schedule.LessonInfo`, `occurrence_ids = 1`, `bindings = 1`,
enum values 0–3. Удалить academic import только после проверки нулевых refs.
Approved test corrections используют complete current JWT fixture API и
сохраняют исходные assertions. Mobile token keeps user `100L`, group `10L`,
fixed session UUID, versions `1L`, `"STUDENT"`, `"ACTIVE"`, headman false and
read-only false.

## 5. Constraints

Нет generated source edits, config/dependency/lockfile changes, runtime или
Gradle до отдельного heavy lease. Разрешённые расширения были узкими и
записаны main: исправить текущие API-вызовы в двух Schedule security test
files, сохранив expiry/signature/role/legacy assertions; затем исправить только
один stale four-argument `JWT.validToken` в названном mobile
`OpenApiSnapshotIT.java`, сохранив все assertions. Shared factory/production не
менять. Не добавлять compatibility alias, reverse import/cycle, consumers,
handlers или lifecycle code. Не захватывать чужие изменения; unexpected
handwritten refs требуют report/scope extension.

## 6. Existing patterns

Оба proto используют `syntax = proto3`, `java_multiple_files = true`; Schedule
namespace — `rutcampustrack.schedule` и
`ru.rutcampustrack.schedule.grpc`. Пять модулей генерируют shared proto.

## 7. Acceptance criteria

RPCs существуют только у Schedule; binding messages/enum генерируются в
Schedule namespace; source field/enum/service comparison показывает byte-level
паритет wire-контракта кроме namespace relocation; `LessonInfo` тот же; после
удаления import нет Academic refs/cycle; unrelated declarations unchanged.
Все три approved test files компилируются с current fixture API, сохраняя
expiry/signature/role/legacy semantics и mobile OpenAPI assertions.

## 8. Verification

До/после: source extraction, file hashes, exact two-file diff и case-insensitive
reference search с revision/command/exit code/environment/evidence. Gradle
`generateProto`, `compileJava`, `compileTestJava` для пяти модулей запускать
только по main heavy lease. H1/H2/H5/H6/H7/H8 были bounded historical attempts;
после UUID/mobile corrections H9 canonical fifteen-task union returned exit 0,
with all five modules reached. Runtime для declaration-only scope: N/A.
Independent Sol high full diff review — позже у main.

## 9. Do not

Не менять V17/entity/DTO/events/lifecycle/content/completion/UI/parity,
producer code, shared JWT factory, production code или тесты вне трёх approved
files; generated files, build/config/dependencies/docs вне local metadata, не
импортировать whole checkpoint, не spawn children/reviewer, не push/deploy/main
merge.

## Addendum: H1 correction gate

H1 reproduced five compile errors caused by the two approved Schedule security
tests using removed factory methods/old arity. Main decision permits only those
two test files to call the current nine-argument `validToken` and complete
`buildToken` with expired/current or independently signed keys. Further
unrelated compile errors require a new root decision.
