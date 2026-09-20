PASS — только corrected shared-shell foundation, baseline `8002b9ea…`. Блокирующих findings нет.

Подтверждено:

- `push/replace/back/goRoot` уведомляют Vue shell; no-op root не уведомляет.
- Initial, cross-root и replace nested-history возвращаются в owning root и затем останавливаются.
- Actual Vue probes подтвердили `host A→B→null`, отписки, сброс keyboard, очистку Back и unmount.
- Видимое «Учёт» имеет accessible name «Посещаемость. Раздел пока недоступен».
- `control/min-touch=44` корректно отображён в `2.75rem`; Boolean default и Onest сохранены.
- Snapshot стабилен: HEAD, status и SHA-256 критичных файлов не изменились за review.

Reviewer checks: 10/10 tests, typecheck, scoped lint, component probes и `git diff --check` — exit 0. Full lint — exit 1 только из-за неизменённого baseline defect `fixture-transport.test.ts:94`; это отдельный integration gate. Авторский PWA/TMA build — exit 0, повторно не запускался из-за read-only/no-data-write ограничения.

Auth, persistence и API в scope не затронуты. Real TMA host, real API и полный 39-screen student role остаются последующими gates; данный PASS не является PASS всей роли.
