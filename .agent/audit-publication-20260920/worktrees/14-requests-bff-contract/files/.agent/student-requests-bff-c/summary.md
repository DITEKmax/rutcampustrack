# Student requests BFF C summary

Статус: `READY_FOR_REVIEW / RELEASED_RUNTIME`.

Цель bounded S2 выполнена в выделенном worktree: BFF сохраняет типизированные
4xx и cooldown semantics, переводит серверные/неизвестные/повреждённые ошибки и
локальные неожиданные сбои в безопасный HTTP 500 `INTERNAL_ERROR`, а
`StudentRequestApiModels.Detail` сериализует `reason`, `comment`, `decision` как
явные `null` при отсутствии значений.

Изменённая production-область состоит ровно из трёх файлов:

- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentRequestApiModels.java`
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`

Добавлены ровно два focused test-файла:

- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/contract/StudentRequestDetailJsonTest.java`

Проверки:

- immutable import guard: exit `0`, `IMPORT_VERIFY_PASS productPaths=82 repairs=2`;
- `git diff --check`: exit `0`;
- единственный GO-authorized Gradle selector: exit `0`, `BUILD SUCCESSFUL`,
  39 actionable tasks;
- focused XML: 17 + 2 tests, failures/errors/skipped `0/0/0` в обоих классах.

Runtime service integration: `N/A` для unit/serialization lane. Никакие
Testcontainers, сервисы, внешние RPC, данные или процессы не запускались этим
leaf; после завершения lease активных собственных процессов нет.

Ограничения: full HTTP/Gateway/Nginx/Redis/Mongo integration и independent Sol
review остаются root-owned gates. Этот leaf не редактировал их область, OpenAPI,
proto, generated files, configs или lockfiles и не создавал commit.
