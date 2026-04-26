# M{N} — {Название milestone}

**Статус:** ⬜ не начат · ⏳ в работе · ✅ готов
**Старт / финиш:** YYYY-MM-DD / —
**Estimate:** X человеко-дней

---

## Scope

Какие пункты аудита закрывает. Ссылки — на `OWNER-ANSWERS.md` (Q-ID / P2-N/M)
и `COVERAGE-AUDIT.md`. Без пересказа — только список.

- Q-ID / P2-N/M — короткое описание
- ...

## Модули / изменения

Список создаваемых/меняемых Gradle-модулей, Docker-контейнеров, файлов
первого уровня. 1-2 строки на пункт. Без философии — только «что».

- `services/shared/shared-X/` — назначение в одной фразе
- `services/{service}/build.gradle.kts` — что меняется
- ...

## Acceptance criteria

3-7 измеримых критериев «milestone закрыт». Прогоняется разово в конце.

- [ ] Критерий 1 (пример: «GlobalExceptionHandler из shared-web используется во всех 4 backend-сервисах, локальные копии удалены»)
- [ ] ...

## Dependencies

- **Блокирует:** M{X} (почему)
- **Блокируется:** M{Y} (почему)
- **Parallel safe:** M{Z} (можно делать одновременно)

## Artifacts

Что остаётся после milestone в репо (новые файлы, новые ADR, новые runbook'и).

- `docs/architecture/architecture.md` — раздел «X»
- `docs/operations/runbooks/X.md` — новый
- ...

---

_Никаких «why», «motivation», «background» — это уже в 99-executive-summary.md
и OWNER-ANSWERS.md. Здесь только WHAT и DONE-критерии._
