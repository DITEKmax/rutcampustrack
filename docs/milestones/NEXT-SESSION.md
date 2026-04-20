# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Этого достаточно —
Opus сам откроет файлы и поймёт где мы остановились.

---

Продолжай работу над v0.0.0 milestones.

Контекст:
1. Архитектурный аудит завершён, зафиксирован в `docs/report-before-v0.0.0/`
   (16 отчётов + OWNER-ANSWERS.md 6400 строк + COVERAGE-AUDIT.md 354
   пункта + 99-executive-summary.md roadmap).
2. Рабочий процесс — lightweight milestones без GSD-orchestrator'а.
   Индекс: `docs/milestones/README.md`.
3. Активный milestone: **M05 Performance**. Группы 1-2 ✅ закрыты,
   следующая — **Группа 3 (Caffeine cache, ~1 день)** (P2-10/3 из
   OWNER-ANSWERS 3756-3810).

Что делать:
1. Прочитай `docs/milestones/M05-performance/PLAN.md` — scope и модули.
2. Прочитай `docs/milestones/M05-performance/CHECKLIST.md` — пункты
   Группы 1-2 помечены `[x]`, начни с Группы 3.
3. Прочитай `docs/milestones/M05-performance/NOTES.md` — snapshot,
   открытые развилки, D1-D5.
4. Прочитай `docs/milestones/M05-performance/DECISIONS.md` — micro-ADR
   за прошлые сессии. Следующие решения пиши в том же формате (D6, D7, ...).
5. `git log --oneline -10` — последние коммиты (`6802e7f`, `83ed387`,
   `ea7a390`, и дальше M04).
6. Проверь docker-compose: `docker compose ps` — контейнеры
   postgres-academic/postgres-schedule/mongo-attendance должны быть
   healthy. Если остановлены — `docker compose up -d postgres-academic
   postgres-schedule mongo-attendance`. Schemas и seed уже применены
   с прошлой сессии (id ≥ 900000).
7. Продолжай с Группы 3 по CHECKLIST — первая невыполненная галочка.
8. После каждой завершённой группы — отчитайся коротко (1-2 строки)
   и жди подтверждения перед следующей. Если пользователь говорит
   «go» — работай молча дальше.

Правила:
- Русский язык в отчётах / NOTES / ответах (технические термины /
  код — оригинал).
- Не звать `gsd-*` агентов. `Explore` для «найти все X»,
  `bug-hunter` / `code-reviewer` / `security-auditor` — в Группе 9
  audit'а.
- Surprise / отклонение от плана → NOTES.md + спросить до продолжения.
- Micro-решение (не в OWNER-ANSWERS) → DECISIONS.md (D6+).
- Закрыл пункт CHECKLIST → `[x]` через Edit.
- Hook-reminder'ы READ-BEFORE-EDIT после Read в той же сессии — ложные.
- Push на origin / создание PR — только с явного `go` пользователя.
- `CHANGELOG.md [Unreleased]` обновляй при значимых изменениях.

Когда milestone закрыт:
1. Все пункты CHECKLIST отмечены `[x]`.
2. Все acceptance criteria в PLAN.md пройдены (`./gradlew build`
   зелёный + integration-тесты + ArchUnit + CI-lint).
3. Post-mortem секция дописана в PLAN.md.
4. Статус в `docs/milestones/README.md` → ✅ готов.
5. Тег `git tag v0.0.0-alpha.6` на последнем коммите milestone'а
   (локально, без push).
6. Сообщить пользователю финальный summary + ссылку на следующий
   milestone по dependency graph (M06 Ops / M07 Frontend / M08 Tests).

Старт:
> Читаю PLAN → CHECKLIST → NOTES → DECISIONS → git log. Через минуту
> скажу где стартуем (Группа 3) и что буду делать первым.

---

## Hand-off после M05 Группы 2 (2026-04-20)

**Состояние M05:** ⏳ **в работе.** 2/10 групп закрыто. Последний
коммит `6802e7f`.

### Итоги Группы 1 (commit `83ed387` + scope `ea7a390`)

**Composite indexes + perf baseline (P2-10/1).** Уточнения scope
зафиксированы в DECISIONS D1-D4.

- **schedule_db V12** — partial composite `idx_lessons_item_date ON
  lessons (schedule_item_id, date) WHERE status != 'cancelled'`. D1 —
  `lessons.group_id` не существует; композит на FK покрывает IN+BETWEEN.
- **academic_db V17** — `idx_tsg_group_semester` +
  `idx_hw_group_semester` на `(group_id, semester_id)`. D2 — таблицы
  `user_groups` нет; индексы на реальных hot queries
  `findByGroupIdAndSemesterId`.
- **attendance Mongo** — compound `lcr_group_status_created (group_id,
  status, created_at)` на `late_checkin_requests` через
  `MongoConfig.initIndexes()`. **Закрывает 04 P2-9:** COLLSCAN →
  IXSCAN, docsExamined 6000 → 120 (50× reduction), SORT ушёл.
- **Деферрено:** D3 (group_id, lesson_id) на Mongo `attendances` —
  нет hot query-потребителя. D4 `schedule_one_off_lessons` UNIQUE
  уже в V4.
- **Regression-guard tests:** `LessonPerformanceIT`,
  `AcademicPerformanceIT`, `LateCheckinPerformanceIT`. Best times:
  8 / 8 / 8 / 10 ms на лимите 50 ms.
- **Runbook:** `docs/performance-indexes.md` — EXPLAIN before/after
  по 4 hot queries, процесс добавления новых индексов.
- **Seed:** `docs/milestones/M05-performance/seed-perf.sql` +
  `seed-perf.js` — idempotent, id ≥ 900000. Применён к dev-БД в
  docker-compose.

### Итоги Группы 2 (commit `6802e7f`)

**Preventive N+1 guard (P2-10/2, NEW-143).** Системный аудит
Repository-слоя (Explore) показал: все JPA entity в schedule +
academic используют FK как Long, нет `@ManyToOne/@OneToMany`. N+1
невозможен by design. Scope переформулирован на preventive-only (D5).

- **ArchUnit `RepositoryNPlusOneGuardTest`** в
  `schedule/arch/` + `academic/arch/`. Две rule'а:
  1. `entitiesMustNotUseJpaRelations` — фиксирует v0.0.0 invariant.
  2. `repositoriesReturningCollectionsMustGuardNPlusOne` —
     активируется при появлении первой relation; требует Pageable /
     @EntityGraph / *Projection / JOIN FETCH.
- **Sanity-verify:** временный `@ManyToOne` в `Lesson` →
  `entitiesMustNotUseJpaRelations` failed с сообщением «Поле
  `Lesson.scheduleItemRelation` помечено JPA relation...» → edit
  откачен, build зелёный.
- **Reference projection:** `LessonDetailsProjection` +
  `LessonRepository.findLessonDetails` — native JOIN `lessons` +
  `schedule_items` в одном SELECT (10 полей), whitelist'ится ArchUnit.
- **Docs:** `architecture.md §11` — runbook «JPA convention: FK как
  Long, без entity relations (NEW-143)» с rationale, образцом
  `collect itemIds → findByIdIn` (`LessonService.massCancelLessons:137-142`),
  action-plan «когда relation всё-таки нужна».
- **Tests:** schedule 111/111, academic 201/201, attendance 158/158 ✅.

### M05 Scope остался

| # | Группа | Est | Статус |
|---|--------|-----|--------|
| 1 | Composite indexes + perf baseline | ~3ч | ✅ |
| 2 | Preventive N+1 guard (NEW-143) | ~2ч | ✅ |
| **3** | **Caffeine cache для справочников + RBAC (P2-10/3)** | **~1д** | **⬜ next** |
| 4 | Batch endpoints (P2-10/4) | ~1д | ⬜ |
| 5 | SQL-aggregate vs stream (P2-10/5) | ~1д | ⬜ |
| 6 | HikariCP tuning (P2-10/6) | ~2ч | ⬜ |
| 7 | Cleanup push-subs + retention (P2-10/7) | ~3ч | ⬜ |
| 8 | gRPC hot-path: parallel + deadlines + metrics (P2-10/8) | ~1д | ⬜ |
| 9 | Audit (bug-hunter + code-reviewer + security) | — | ⬜ |
| 10 | Documentation + закрытие milestone | — | ⬜ |

### Группа 3 Scope (предварительно — читай PLAN.md и OWNER-ANSWERS 3756-3810)

P2-10/3 Caffeine cache:

- Caffeine dep в shared-web (api) или per-сервис `CacheConfig`.
- Namespaces + TTL: `semester` (5м), `subject`/`group` (10м),
  `rbac` (1м).
- `@Cacheable` на:
  - `getActiveSemester()` в academic — часто зовётся.
  - `isHeadmanFor(userId, groupId)` — RBAC hotspot (hundred/min).
  - `getSubject(id)`, `getGroupById(id)` — справочники.
- `@CacheEvict` на write-side: `activateSemester`,
  `update/delete subject/group`, `changeHeadman`.
- `CaffeineCacheMetrics.monitor(meterRegistry, cache, name)` — gauges
  `cache.size`, `cache.gets{result=hit|miss}` в Grafana.
- Integration-тест: counter hits > misses после warm-up.
- `docs/caching-strategy.md` (NEW-144) — TTL matrix + invalidation
  triggers + migration-plan на Redis при multi-instance.

Ожидаемый выигрыш: снижение latency P2-10/8 (hot-path gRPC
`isHeadmanFor` per-request → cached per 60s).

### Состояние окружения

- Docker-compose containers: `rct-postgres-academic`,
  `rct-postgres-schedule`, `rct-mongo-attendance` — **healthy**.
  Schemas мигрированы Flyway V1..V17 / V12. Seed применён
  (600 schedule_items, 12k lessons, 20 groups, 523 users, 300 subjects,
  1800 TSG, 1800 homeworks, 6000 late_checkin_requests).
- Mongo admin user создан через localhost exception:
  `rct_user:rct_dev_pass` (roles: root@admin). В seed-perf.js указан
  connection string.

### Последние коммиты

```
6802e7f feat(arch): preventive N+1 guard ArchUnit rule (M05 Группа 2, NEW-143)
83ed387 feat(perf): composite indexes + perf baseline (M05 Группа 1)
ea7a390 docs(m05): уточнение scope Группы 1 после аудита схемы БД (D1-D4)
1d5a203 docs(m05): scaffold milestone + hand-off после M04
135d226 docs(m04): CHECKLIST отметка v0.0.0-alpha.5 tag (325d25d)
```

72+ коммитов локально ahead origin. Tags `v0.0.0-alpha.2..5` локальные.
Push отложен до конца v0.0.0.

### Действия, ожидающие `go` пользователя

1. `git push origin main` — 72+ коммитов не на origin.
2. `git push origin --tags` — 4 tags локальные.
3. Старт Группы 3 по CHECKLIST M05.

### Source of truth для v0.0.0

- `docs/report-before-v0.0.0/99-executive-summary.md` — roadmap.
- `docs/report-before-v0.0.0/OWNER-ANSWERS.md` (строки 3756-3810 для
  P2-10/3 / Группа 3 Caffeine).
- `docs/report-before-v0.0.0/COVERAGE-AUDIT.md` — 354 пункта.
- `docs/milestones/README.md` — индекс milestones + статусы.
- `docs/milestones/M05-performance/{PLAN,CHECKLIST,NOTES,DECISIONS}.md`
  — per-milestone artefacts.
- `docs/performance-indexes.md` — runbook M05 G1.
- `docs/architecture.md §11` — JPA convention runbook (M05 G2).
