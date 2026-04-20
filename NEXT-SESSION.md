# Next Session Pointer

**Активный milestone:** M05 Performance (⏳ в работе, 2/10 групп закрыто).

**В новом терминале напиши:**

```
Прочитай docs/milestones/NEXT-SESSION.md и продолжай
```

Или короче:

```
Продолжай M05
```

## Быстрые ссылки

- `docs/milestones/NEXT-SESSION.md` — полный промпт + hand-off после Группы 2.
- `docs/milestones/M05-performance/PLAN.md` — scope + acceptance criteria.
- `docs/milestones/M05-performance/CHECKLIST.md` — 10 групп задач (Группы 1-2 ✅).
- `docs/milestones/M05-performance/NOTES.md` — snapshot + открытые развилки.
- `docs/milestones/M05-performance/DECISIONS.md` — D1-D5.

## Состояние (2026-04-20)

- **Группа 1 (Composite indexes + perf baseline)** ✅ commit `83ed387`
  (+ scope refinement `ea7a390`). Indexes: schedule V12, academic V17,
  Mongo `lcr_group_status_created`. Regression tests 8/8/8/10 ms на
  лимите 50ms.
- **Группа 2 (Preventive N+1 guard, NEW-143)** ✅ commit `6802e7f`.
  Системный аудит показал: все JPA entity используют FK как Long, нет
  relations. Scope переформулирован на preventive (D5): ArchUnit +
  LessonDetailsProjection + `architecture.md §11` runbook.
- **Следующая — Группа 3 (Caffeine cache, ~1 день)**: справочники
  (semester/subject/group) + RBAC (isHeadmanFor). P2-10/3 из
  OWNER-ANSWERS 3756-3810.
- 72+ коммитов ahead origin, 4 tag'а локальные. Push отложен до конца
  v0.0.0.
- Docker-compose поднят (postgres-academic, postgres-schedule,
  mongo-attendance — healthy). Schemas мигрированы, seed применён.
