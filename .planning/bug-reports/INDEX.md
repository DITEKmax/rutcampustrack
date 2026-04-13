# v9.0 Bug Reports — Index

Статус багов, найденных при ручном QA после закрытия v9.0 (2026-04-13).

## Open

- [BUG-001](./BUG-001-web-panel-specs-stale-displayName/report.md) — web-panel `.spec.ts` используют устаревшее `displayName` вместо split-ФИО (minor, web-panel)
- [BUG-002](./BUG-002-favicon/report.md) — favicon в браузере не соответствует логотипу проекта (cosmetic, web-panel/landing/pwa)
- [BUG-003](./BUG-003-naming/report.md) — login: `RutCampusTrack` → `RutTrack`, добавить лого, анимацию градиента, глазик пароля (cosmetic, web-panel)
- [BUG-004](./BUG-004-IDshow/report.md) — плашка профиля показывает ID-цифру и не открывает модалку профиля/аватара (cosmetic+major, web-panel)
- [BUG-005](./BUG-005-dashboard/report.md) — admin dashboard: некликабельные карточки, мусорные квадраты, переделать "быстрые действия" (cosmetic, web-panel/admin)
- [BUG-006](./BUG-006-admin/report.md) — admin: поиск, ошибка создания пользователей, Telegram ID, единое имя группы, правила семестров (major, web-panel/admin)
- [BUG-007](./BUG-007-teacher/report.md) — teacher журнал: кнопка поиска не выровнена с фильтрами (cosmetic, web-panel/teacher)
- [BUG-008](./BUG-008-student+pwa/report.md) — PWA белый экран, неработающие student/headman страницы, UI-баги форм (blocker+cosmetic, pwa/web-panel)
- [BUG-009](./BUG-009-add%20kafka/report.md) — добавить ELK для сборки логов + Telegram-алерты + heartbeat бота (major, infra)

## In progress

_(пусто)_

## Fixed

_(пусто)_

## Won't fix / Duplicate

_(пусто)_

---

**Формат строки:**
```
- [BUG-NNN](./BUG-NNN-название/report.md) — краткое описание (severity, component)
```

См. `README.md` для инструкций.
