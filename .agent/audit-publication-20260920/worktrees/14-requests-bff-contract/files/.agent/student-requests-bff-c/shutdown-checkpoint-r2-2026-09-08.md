# Shutdown checkpoint r2 — Requests BFF correction

Дата: 2026-09-08 (Europe/Moscow)
Статус: `PAUSED_BY_OWNER`

## Состояние

- Worktree: `C:/Users/maksd/.codex/worktrees/e31c/rutcampustrack/.agent/worktrees/requests-bff-contract`.
- HEAD: `d3c31acb8cce53791a4981e5858a37d44fdc9a0e` (detached).
- Frozen correction contract SHA-256:
  `887F7C949491C91C5E655D67981C237E393000B30BCC2CBC8EC7EE5DE60D7C9C`.
- Independent P2 FAIL review SHA-256:
  `4D37F06BE84606436FE8D375C70484838F0C3D195222C3698F142DABDFC6C93C`.
- Fresh r2 packet и before-хэши сохранены до product edit в
  `.agent/student-requests-bff-c/resume-packet-r2-2026-09-08.md` и
  `.agent/student-requests-bff-c/before-hashes-r2-2026-09-08.json`.
- Owner pause принят после завершения bounded product/test mutation. После
  паузы не выполнялись новые product/test edits, checks, Gradle, runtime или
  review.

## Текущие SHA correction scope

- `services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java`
  — `E7CF94B0229F3D86909917288F4BA5BC97F6D9754D04DF7673A10BE4BEC1D28F`.
- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClient.java`
  — `2947BA32E53A618E2E638CB6CCC26A8CE406D42613C7DB9ADB5684FD9ACF4D3C`.
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/grpc/MobileAttendanceClientErrorTest.java`
  — `2064F18E775A72FDEF9EF6A4BFD27642D9AFC647C8F28FFFFDFA3680B6E6CA9`.

Frozen five-manifest files remain at their before values:

- `StudentRequestApiModels.java` —
  `651B83E0E7B629F2750EE51231915B9A55D072E3F04FB31EDD6C0D4864198286`.
- `StudentRequestDetailJsonTest.java` —
  `3F6027AE9E51D4EF743FEE0ACCF20B1CFC7AFCC32FE0B7B67765A9307B44D2C9`.

## Изменения после предыдущей owner pause

- Сохранён fresh nine-section r2 packet и before-хэши.
- В собственные reviews добавлены verbatim-копии frozen contract и P2 FAIL
  review; исходные документы не изменялись.
- В `StudentApiModels.java` удалён только случайный
  `EligibilityReason.INTERNAL_ERROR`; `ProblemCode.INTERNAL_ERROR` сохранён.
- В `MobileAttendanceClient.java` добавлен bounded raw-status guard до
  `StatusProto` extraction: `UNAUTHENTICATED`/`PERMISSION_DENIED` сохраняют
  401/403, `UNAVAILABLE`/`DEADLINE_EXCEEDED` — 503, а
  `INTERNAL`/`UNKNOWN`/`DATA_LOSS` — generic 500 при любом typed detail.
- В `MobileAttendanceClientErrorTest.java` добавлены observable checks для
  contradictory auth/dependency details, raw `DATA_LOSS` и повреждённых
  auth/transport trailers; прежний malformed `UNAVAILABLE` expectation
  заменён на raw 503 semantics.
- `StudentRequestApiModels.java`, `StudentRequestDetailJsonTest.java` и весь
  inherited dirty набор не редактировались и не очищались.

## Dirty/scope guard

- До correction в checkout уже были импортированные dirty paths (46 tracked
  и 45 untracked по source handoff), включая чужие OpenAPI/proto/FE/BE/bot
  изменения; они сохранены. Reset, checkout, clean и commit не выполнялись.
- До owner pause зафиксированы: owned tracked `git diff --check` exit `0`,
  owned style guard `PASS`, frozen-two hash guard `PASS` и enum guard `PASS`.
- Полный `git diff --check` имел exit `1` из-за inherited trailing whitespace
  в `docs/openapi/mobile-bff.json`; этот unrelated файл не изменялся.
- Финитный source-manifest guard перед pause дал exit `1`:
  `productPaths=82`, mismatches `3` (`EventConsumerIT.java`,
  `RabbitDecisionRetryIT.java`, `StudentRequestApiModels.java`), accepted
  repair mismatches `0`. Это не является fresh import PASS для correction;
  guard не исправлялся во время owner pause.

## Проверка и runtime

- Исторические 19 PASS и прежние 17+2 focused PASS относятся к до-correction
  WIP и не закрывают эту коррекцию.
- Fresh focused Gradle selector после correction не запускался; correction
  остаётся `UNTESTED`.
- Product runtime, Testcontainers, RPC, HTTP, Mongo, Redis и внешние сервисы
  не запускались. Собственных процессов, Gradle lease и listeners нет.

## Открытые gates и next steps

- Открыт новый explicit root GO/lease на exact selector:
  ` .\\gradlew.bat :services:mobile-bff:mobile-bff-app:test --tests ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClientErrorTest --tests ru.rutcampustrack.mobilebff.contract.StudentRequestDetailJsonTest --no-parallel --max-workers=1 --console=plain`
- После GO: выполнить только этот selector, записать revision/environment/
  start/end/exit code/XML counts и обновить r2 checks/runtime evidence.
- Затем повторить finite hash/import/scope guard и передать стабильный diff на
  fresh independent Sol high recheck. До этих gates status остаётся
  `PAUSED_BY_OWNER`; historical PASS не переименовывается в fresh proof.
