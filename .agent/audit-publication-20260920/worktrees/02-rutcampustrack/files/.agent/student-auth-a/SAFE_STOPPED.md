# SAFE_STOPPED

Timestamp: `2026-09-11T00:38:08.5016927+03:00`

Причина: владелец запросил безопасную остановку. После запроса не запускались новые stages, writers, Gradle/runtime, reviews, export/import или исправления. Уже начатое финальное read-only review завершилось и сохранено дословно.

## Принято и завершено в этой линии

- Auth contracts, pure session domain и PostgreSQL authority/migration V24 ранее прошли применимые checks и независимое review.
- Profile UI ранее прошёл source fidelity, browser/runtime и независимое review.
- Stage 1 admission/internal JWT producer завершён и принят свежим независимым review: `PASS`, блокирующих findings нет.
- Shared strict RS256/internal identity subset прошёл отдельную независимую recheck и был принят union-owner.
- Финальный Stage 1 gate: `64/64` tests, `0` failures, `0` errors, `0` skipped; source guard `0` drift; PostgreSQL 16.13; Flyway V1–V24; контейнеры очищены.

## Частично завершено

- Общая история server-owned roles/session/revocation имеет проверенные contracts, domain/JDBC authority, UI и Stage 1 producer, но ещё не собрана целиком через downstream services и клиентские wiring.
- Старые downstream test constructors и вызовы прежней сигнатуры `InternalJwtTestFactory.validToken` требуют адаптации union-owner и полного downstream compile gate.

## Не начато в этой линии

- Stage 2 public session lifecycle: refresh CAS, logout/logout-all, password transaction, session/history endpoints и соответствующее wiring.
- Gateway admission cutover.
- Mobile BFF, WebSocket и downstream consumer cutover.
- Полный union runtime всей истории.

## Возобновление

Следующий безопасный шаг: начать Stage 2 только по отдельному явному решению владельца после сверки текущего union baseline; затем отдельно выполнить Gateway/BFF/WS cutover и полный downstream compile/runtime gate.

## Состояние остановки

- Активных writers и runtime-команд нет.
- Финальный reviewer завершён.
- Docker-контейнеры этой линии очищены, heavy-runtime lease освобождён.
- Working tree остаётся намеренно dirty: содержит принятые артефакты нескольких согласованных scopes; reset/revert/stage/commit не выполнялись.
