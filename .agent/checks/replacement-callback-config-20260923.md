# Replacement callback — конфигурационное evidence

Base `c7436b3bb240605c99795e63b800745c0d7b964d`; branch `codex/replacement-callback-config-20260923`; risk S3 (service identity/auth). Применимые правила: MAIN `.agent/orchestration-v2/RULES.md`, SHA256 `F4986A1834A9175ADBB7DCB3C49483168111A0B60C7EB13ACDA9FBF9D74927F8`.

## Scope и критерии

Когда преподаватель заменяет преподавателя, Schedule должен аутентифицировать новый Schedule→Academic callback отдельной направленной service credential. Переменная `SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN` задаёт ключ `grpc.service-identity.schedule-to-academic-token` в обоих сервисах; оба контейнера получают её в e2e/prod с обязательной compose-подстановкой. Прямой `ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN` остаётся отдельным и неизменённым. Пустой/отсутствующий reverse credential закрывает callback.

## Evidence

- `AcademicGrpcClient.getPreparedAssignmentCloseOperation()` читает reverse token, проверяет canonical encoding и прекращает вызов при невалидном значении (`services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java:187`). Academic callback interceptor читает тот же ключ и создаёт credential через `tryCreate(SCHEDULE_SERVICE, ACADEMIC_SERVICE, token)`; без неё защищённый метод не аутентифицируется. `RequiredSecretsValidator` завершает запуск, если переменная из `required-env-vars` отсутствует.
- До этой правки оба application.yml и compose содержали только forward mapping. Базовое live-воспроизведение замены завершилось HTTP 503 на реальном POST (session `68332`); cleanup прошёл. Runtime owner ведёт отдельную повторную lifecycle-проверку на исправленной конфигурации.
- В diff только placeholders и required-variable списки; значения credential не читались и не добавлялись.

## Проверки

- `git rev-parse HEAD` — exit 0, `c7436b3bb240605c99795e63b800745c0d7b964d`.
- `git diff --check -- docker-compose.e2e.yml docker-compose.prod.yml services/academic-service/academic-app/src/main/resources/application.yml services/schedule-service/schedule-app/src/main/resources/application.yml` — exit 0.
- `rg -n -C 1 "schedule-to-academic-token|ACADEMIC_TO_SCHEDULE_SERVICE_TOKEN|SCHEDULE_TO_ACADEMIC_SERVICE_TOKEN" services/academic-service/academic-app/src/main/resources/application.yml services/schedule-service/schedule-app/src/main/resources/application.yml docker-compose.e2e.yml docker-compose.prod.yml` — exit 0; reverse key/required env есть в обоих приложениях, env передаётся обоим сервисам в обоих compose; forward mapping сохранён.
- Новые тесты, Gradle и Docker не запускались: это базовый config wiring; post-change live runtime проверяет отдельный владелец.

## Diff и ограничения

Созданы/изменены для этого пакета: `docker-compose.e2e.yml`, `docker-compose.prod.yml`, `services/academic-service/academic-app/src/main/resources/application.yml`, `services/schedule-service/schedule-app/src/main/resources/application.yml`, этот evidence-файл. До завершения post-change runtime PASS по сценарию не утверждается. Чужие изменения в `.agent/orchestration-v2/LEAF-PACKET.md`, `.agent/orchestration-v2/RULES.md` и `.agent/teacher-replacement-ui-20260923/source.diff` сохранены отдельно.
