# Narrow independent headman recheck — e4cbce048

**PASS: три P2 из REVIEW-3ffb3792f закрыты в заданном source scope. Новых P1/P2 в correction не обнаружено.** Это не означает завершение всех runtime gates HEADMAN38.

Frozen source `/workspace/rct-worktrees/headman-web`, exact HEAD `e4cbce0484f12cc973dab97a0a54f17ebd9f5497`, tracked clean. Scope только delta3ff→e4: SchedulePage/RequestsPage/SubjectsPage, new replacement preflight helper и семь mounted-SFC regression cases. Closed124/f0/a6/f108 baseline не переоткрывался. Продукт, зависимости, серверы, fixtures/Git refs не менялись; без children/browser/Gradle/CI/повторной широкой проверки. Создан только этот immutable report. Запрошенная модель/effort gpt-6.1-sol/high; независимой runtime telemetry нет.

## 1. Schedule latest semester owner — CLOSED

load/read захватывают semester query и общий readRevision. Публикация rows/assignments/pending/restored owner, ошибки и loading принадлежат только актуальному query/run. Init continuation также проверяет owner до присвоения semester catalog/default. Смена semester вызывает load; publishedSemester+loading fence не разрешает редактирование старой сетки под новым semester. Вспомогательный read использует ту же revision дисциплину. Старый A response/error после B не может заменить актуальную projection.

Прочитаны mounted-SFC cases slow archive→active, old helper read и obsolete failure; они проверяют итоговые rows/assignments/loading/writable/error, а не внутренний порядок вызовов.

## 2. Not-admitted keyed EXCUSE decision — CLOSED

После restore + authoritative GET=PENDING UI показывает исходное решение и явное «Продолжить исходное сохранение». replayReady связан с тем же pending object и доступен только при наличии исходной keyed selection. sendPending(true) сохраняет original key/revision/excluded set/reason, использует shared helper с повторным GET перед write, проверяет current owner/pending identity/writable непосредственно на boundary. Другая terminal receipt разрешает conflict без POST; потеря authority не разрешает replay. Это explicit same-command recovery, не automatic blind/new-key retry.

Прочитаны реальные mounted SFC + typed HeadmanRequestsApi causal cases: lost not-admitted POST→remount→GET PENDING→explicit replay с deep-equal original body, а также write authority loss и другая receipt без второго POST.

**Предел: LATE_CHECKIN не получил выдуманный idempotency protocol.** Текущий legacy domain не содержит immutable key/receipt, поэтому его unknown outcome остаётся GET-only. Это отдельно зафиксированный API/продуктовый recovery gap/вопрос root, не основание слепо повторять legacy mutation и не объявленный PASS положительного LATE recovery. Исходный воспроизведённый P2 этого recheck был keyed EXCUSE и исправлен.

## 3. Replacement local invalid-date lock — CLOSED

replacementDraftInput проверяет календарную дату, own group, active semester, существующее назначение этого semester, известного другого teacher и допустимый период **до** UUID/freeze/persistence. Wire preflight проверяет сериализуемые поля. Ошибка локального draft оставляет editor незаблокированным без fetch. Restore освобождает только заведомо не передававшийся typed-invalid payload без receipt; syntactically valid unknown intent и его исходный key остаются. Existing receipt не удаляется из-за ошибочных полей recovery.

Прочитаны mounted-SFC + actual typed API cases invalid date→editable/0fetch→исправленный один POST, освобождение старого непередаваемого pending и сохранение valid unknown original key. Source gate этого finding закрыт.

## Evidence и оставшиеся runtime границы

Собственное: exact revision/clean status и scoped `git diff --check 3ffb3792f e4cbce048 -- frontends/web-vue/src/features/headman` — exit0. Прочитаны все семь meaningful causal cases в recovery-flows.test.ts и correction source. Авторские7PASS/web typecheck/diffcheck PASS переданы и описаны в `/tmp/rct-headman/CHECKPOINT-e4cbce048.md`; reviewer их повторно не запускал и собственным browser/backend PASS не объявляет.

Прочитан новый внешний `/tmp/rct-headman/helper-cas-results.json`: own student6/group1 assign201, concurrent revoke, stale expected snapshot409 ASSISTANT_PERMISSIONS_CONFLICT, actual rights после409 остаются MARK_ATTENDANCE, corrected choice200, conditional empty last-right200→inactive с сохранением historical permissions. Это подтверждает новый backend950 runtime CAS gate в описанном объёме и supersedes старое «CAS не deployed» из HANDOFF3ff; не подтверждает автоматически delegated navigation/live revocation.

Остаются отдельно: positive nonself request decision/reload на реальном backend; actor14 applicant POST401 пока не имеет доказанной продуктовой причины. Авторский checkpoint явно сообщает, что subsequent same-bearer authoritative session/Today/options GET200 не доказали null-token/Auth explanation; reviewer такой root cause не утверждает и новых14POST не отправлял. Также остаются назначенные root HEADMAN live/draft, subject-journal export, affected journal768/visual и delegated permission navigation/revocation gates. Их наличие не превращено в новый source finding и не скрыто общим PASS.

Все три finding данного narrow recheck закрыты. Предыдущие immutable reports сохранены; расширенный fullsource rerun для этого correction не требуется. Готовность/accepted counts38 ведёт root по совокупности независимого source и реального runtime evidence.
