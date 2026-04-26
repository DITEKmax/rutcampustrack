# M14 Notes

Живой файл. Сюда — отклонения от плана, измерения, surprises, вопросы
к владельцу, технические долги для будущих milestones.

---

## 2026-04-26

- Стартовая позиция: M13 ✅, тег `v0.0.0-alpha.15`. Четыре аудита (G26 code review,
  G26 test audit, G27 CSO comprehensive, G27 tech debt) дали ~70 findings. Триаж
  показал что только **9 групп** действительно блокируют first VPS deploy либо
  имеют trivial fix — остальное в `docs/future-ideas.md` (pre-v0.1) либо в
  `docs/deferred-ideas.md` (v0.1+).
- Принципы триажа:
  1. **Блокирует deploy функционально** (HIGH-05 PKCS#1 → first deploy упадёт) — must.
  2. **Runtime guard против operator mistake** (HIGH-06 fail-fast secrets) — must.
  3. **Окно эксплуатации между deploy и first user** (CRIT-01 IDOR на private_net) — must.
  4. **Trivial cost / non-trivial impact** (CRIT-02 SHA-pin, HIGH-07 aiohttp bump) — must.
  5. **CI gate compromised без fix** (G26 false-pass tests) — must, иначе зелёное CI = ложное чувство безопасности.
  6. **Performance / scaling tech debt** — gate'нуто на real-user signal (Grafana latency на teacher journal, решение о horizontal scale).
- Оценка: 3-4 часа на всё M14 если без surprises. Один сеанс.

### Открытые решения

- **G7 категория E (`role-student.spec.ts`):** путь A (удалить 2 теста) или путь B (seed `student_plain`)?
  - **Path A cost:** ~5 мин, минус 2 теста coverage негативного RBAC.
  - **Path B cost:** ~30-45 мин (Flyway seed migration в test profile + fixtures + verify).
  - **Lean toward A:** RBAC уже покрыт `SecurityIdorIT` на backend; e2e дублирующий тест избыточен. Запишу окончательное решение когда дойду до G7.

### Вопросы к владельцу

_(пока нет; добавлю если возникнут при выполнении)_

### Технические долги, открываемые в M14

**G1 surprise — `application-test.yml` асимметрия между сервисами:**
academic/schedule/attendance имеют `application-test.yml` с явным
`legacy-headers-enabled: true` (artifact M03a — когда defaults флипались
впервые, тесты получили локальный override, чтобы не переписывать на
Internal JWT). Notification-app аналогичного override НЕ имел — пришлось
добавить inline через `@SpringBootTest properties` в SecurityIdorIT.
Долгосрочное решение — мигрировать все IDOR/security IT на `InternalJwtTestFactory`
из shared-security testFixtures (тогда test-profile override становится
ненужным). Записано в `docs/deferred-ideas.md` как кандидат на v0.1
test cleanup PR.

### Измерения

- **G1 IT runtime:** 5m33s (4× SecurityIdorIT + 3× *StrictModeIT параллельно через single-task gradle invocation, `--no-daemon`).
- **G1 commit footprint:** 5 application.yml + 1 .env.prod.example + 1 test fixup = 7 files / 34 insertions / 10 deletions.
