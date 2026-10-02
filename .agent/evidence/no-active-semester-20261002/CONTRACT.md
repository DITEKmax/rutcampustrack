# Attendance: отсутствие активного семестра

## Goal
После архивирования последнего активного семестра Attendance перестаёт использовать его ID; после явной активации следующего ленивое чтение получает новый ID. Scope S2/S3: корректность истории состояния.

## Context/evidence
Главный открыл main a5974af8 и передал fresh packet. RULES SHA256 A208AA4380B64376A4EAD645AA0731C9F574077107DC9C44356F5C04D80F28FA проверен; CURRENT PRODUCT GO 2026-10-02. Прочитаны root/assigned-worktree AGENTS, services/tests AGENTS, workflow и rct-verification; актуальный RULES отменяет несовместимые старые routing/check lists в worktree. Решение docs/product/decisions/2026-09-29-semester-archive-write-lock.md: архивирование деактивирует, восстановление не активирует. Original refresh присваивал ID только после успешного RPC; getActiveSemester переводит NOT_FOUND в ResourceNotFoundException, прочие статусы в AcademicServiceUnavailableException. EventConsumer.handleSemesterArchived вызывает refresh; StudentRequestService.requireCurrentSemester отвергает null/<=0. Вывод: NOT_FOUND сохранял старый nonnull ID и мешал lazy discovery. Переключение active→active не является целью этой коррекции.

## Relevant scope
Sole product writer /root/a_no_active_semester_1002, fresh developer с явными gpt-6.1-sol/high в root packet; отдельные actual metadata поля инструментом не предоставлены. No children/Terra. Worktree C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/admin-group-promotion-20260927, HEAD 4c6d93c18b156790da5888a8d2de6cead64b2acb. Только SemesterCacheService.java и новый узкий SemesterCacheServiceTest.java. git diff a5974af8 HEAD на SemesterCacheService/AcademicGrpcClient/attendance build пуст. Главный — main integrator. Чужой dirty .agent/transfer-attendance-evidence.md сохранён; стартовый SHA256 EBAA795EA95F3D1F787A9A2146133445010F170E41EA1952341CF8E61FD3C73F. UI bb31a117 не переносится. Собственное evidence только в этом каталоге.

## Required behavior
Авторитетный NOT_FOUND очищает cached ID и завершает refresh успешно. Следующее чтение при null повторно обращается к Academic, обнаруживая следующую активацию без нового события. Transport failure отличается: refresh propagates AcademicServiceUnavailableException и не меняет старое значение; cold/lazy lookup сохраняет прежнее catch/log/null поведение.

## Constraints
Не вводить новую TTL/concurrency/cache architecture, продуктовые правила, RPC/events/proto/API или broad merge. Отдельного решения продукта/контракта не требуется. При новых существенных вопросах — delta для root. WARN/ERROR не меняет код без связи с целью и воспроизведения. Git ignore/profile предупреждения среды не являются продуктовым дефектом.

## Existing patterns
volatile nullable ID, startup refresh с catch, lazy refresh при null; реальный AcademicGrpcClient и Mockito blocking stub/reflection как AcademicGrpcClientTest, JUnit5/AssertJ.

## Acceptance criteria
C1 active11→NOT_FOUND: refresh succeeds и getter возвращает null. C2 последующая активация12 без refresh/event: getter возвращает12. C3 UNAVAILABLE при пустом кэше возвращает null по existing lazy policy; после загрузки11 refresh throws unavailable и getter сохраняет11. Проверяются значения/исключения, не число вызовов.

## Verification
До source ready: baseline comparison, scoped diff/check, foreign hash. После heavy lease: ./gradlew.bat :services:attendance-service:attendance-app:test --tests ru.rutcampustrack.attendance.semester.SemesterCacheServiceTest --no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain --system-prop=org.gradle.java.compile-classpath-packaging=true. Regression включает real client exception mapping и local cache state machine. DB/fullstack не доказываются этим тестом и не нужны для согласованного локального инварианта. До lease Gradle/Docker запрещены. Независимое review назначает root; source-ready не DONE.

## Do not
Не трогать Academic, EventConsumer, StudentRequestService, ReportService кроме чтения; чужой код/docs/configs/resources, generated output/contracts, framework, UI, push/deploy/main integration. Не создавать детей/новые стенды; не заявлять fullbackend acceptance.

