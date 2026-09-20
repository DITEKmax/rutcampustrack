# FAIL — S2 independent Sol high review

Изменений reviewer: 0. Этот файл сохраняет результат review до writer-fixes;
он не является повторным review.

1. **S1 — закрытые R-27…R-31 повторно открыты.**
   `backend-conflicts.md:6,68-72,405-523` говорит, что все 31 пункта закрыты.
   Генератор знал лишь 20 `revised`, 2 `accepted`, 3 `superseded`; `AC-38`,
   `SC-03`, `AT-18`, `MAP-01`, `X-07` оставались `open/needs-decision`.
   Impact: принятые контракты снова отправляются владельцу и могут быть пропущены.

2. **S1 — criteria теряют continuation story, включая OTP boundary.**
   `generate-preparation-registry.ps1` сохранял лишь строку definition. Для
   `JS-SYSTEM-03`, `job-stories.md:857-861` отсутствовали web reset,
   server-supplied TTL/attempt count, purpose `login/password_reset` и role fallback.
   Impact: web flow, policy и authorization boundary могли быть реализованы неверно.

3. **S1 — story/backend trace содержит ложные связи и не двунаправлен.**
   Registry-parser переносил `$requestId` с table row в последующий prose,
   создавая false `X-12` links, включая `JS-ADMIN-10`, `JS-HEADMAN-23`,
   `JS-STUDENT-WEB-07`, `JS-TEACHER-05`. Независимая backend manual map дала
   11 story-side и 57 backend-side pairs: 55 missing from stories, 9 missing
   from backend. Impact: contract/test ownership мог быть назначен неверно.

4. **S1 — 20 new stories неверно классифицированы.**
   Latest source имеет 147 definitions, repository history — 82, поэтому direct
   latest-only definitions = 65. Hardcoded list содержал 45. Ошибочны
   `JS-ADMIN-15..26`, `JS-TEACHER-09..15`, `JS-HEADMAN-23`; последний получил
   non-existent history supersedence. Impact: недостоверные coverage/provenance.

5. **S2 — отменённый atomic multi-diff снова запрашивается.**
   `journal/to-owner.md:879-886` отменяет staged/global review и atomic multi-diff;
   остаются per-row revision/ETag, idempotency и stale conflict. Delta и backlog
   всё ещё называли atomic flow owner question. Impact: неверное направление контракта.

6. **S2 — playbook provenance self-source и duplicate source key.**
   Post-transfer entry называл source kit, но показывал canonical target как
   `source_root/source_path`, брал target hash и не включался в source verifier.
   Это был единственный duplicate source key. Existing archive entry по-прежнему
   проверял external drift; finding относится к invalid canonical-copy lineage,
   а не к global drift blindness.

## Проверки reviewer

- `verify-preparation-manifest.ps1`: PASS — checked 2,205; excluded 34;
  retained duplicates resolved 109; failures 0.
- JSON parse, unique 147 story IDs and 175 request IDs, literal static paths and
  ranges: PASS.
- Runtime/build: не запускались, поскольку product code не менялся и команды
  создают build/cache output.

## Статус после writer-fixes

Все шесть замечаний адресованы writer в последующем diff. Требуется отдельный
targeted Sol re-review; до него итог подготовки остаётся **FAIL/PENDING re-review**.
