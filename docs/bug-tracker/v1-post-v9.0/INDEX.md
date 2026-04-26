# v1-post-v9.0 — Bug Pool

Баги, найденные при ручном QA после закрытия milestone **v9.0 Frontend Unification** (2026-04-13).

**Контекст:** v9.0 ship'нулась 2026-04-13. Сразу после ручной QA-сессии собрано 9 проблем разной severity — от cosmetic до blocker. Все закрыты в рамках фаз 58-60 (v9.0 extension) и v0.0.0 hardening (M01-M14).

## Open

_(пусто)_

## In progress

_(пусто)_

## Fixed

- [BUG-001](./BUG-001-web-panel-specs-stale-displayName/report.md) — web-panel `.spec.ts` используют устаревшее `displayName` вместо split-ФИО (minor, web-panel) — закрыт в Phase 58
- [BUG-002](./BUG-002-favicon/report.md) — favicon в браузере не соответствует логотипу проекта (cosmetic, web-panel/landing/pwa) — закрыт в Phase 58
- [BUG-003](./BUG-003-naming/report.md) — login: `RutCampusTrack` → `RutTrack`, добавить лого, анимацию градиента, глазик пароля (cosmetic, web-panel) — закрыт в Phase 58
- [BUG-004](./BUG-004-IDshow/report.md) — плашка профиля показывает ID-цифру и не открывает модалку профиля/аватара (cosmetic+major, web-panel) — закрыт в Phase 58
- [BUG-005](./BUG-005-dashboard/report.md) — admin dashboard: некликабельные карточки, мусорные квадраты, переделать «быстрые действия» (cosmetic, web-panel/admin) — закрыт в Phase 58
- [BUG-006](./BUG-006-admin/report.md) — admin: поиск, ошибка создания пользователей, Telegram ID, единое имя группы, правила семестров (major, web-panel/admin) — закрыт в Phase 58
- [BUG-007](./BUG-007-teacher/report.md) — teacher журнал: кнопка поиска не выровнена с фильтрами (cosmetic, web-panel/teacher) — закрыт в Phase 58
- [BUG-008](./BUG-008-student+pwa/report.md) — PWA белый экран, неработающие student/headman страницы, UI-баги форм (blocker+cosmetic, pwa/web-panel) — закрыт в фазах 51-56 + Phase 59 (excuses backend)
- [BUG-009](./BUG-009-add%20kafka/report.md) — добавить ELK для сборки логов + Telegram-алерты + heartbeat бота (major, infra) — закрыт в M04 (Observability) + M09 (Alertmanager → bot → Telegram)

## Won't fix / Duplicate

_(пусто)_

---

**Формат строки:**
```
- [BUG-NNN](./BUG-NNN-название/report.md) — краткое описание (severity, component) — где закрыт
```

См. `../README.md` для общих инструкций.
