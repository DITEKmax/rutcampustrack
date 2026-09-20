# Compact contract — STUDENT-ROLE-02

Date: 2026-09-06  
Status: PREPARATION / FREEZE_PENDING  
Risk: S1 for this preparation-only change; the future product story remains S3 until auth, host and data paths are verified.  
Baseline: main@8002b9ea4356b10779c5bb9a6d99746d32d78ae2

## 1. Goal

Подготовить bounded contract для полного student role одновременно в PWA и TMA.
Контракт фиксирует общий mobile-core, shell adapters, 39 live screen/state
узлов, очередность партий и gates. В этой задаче создаются только evidence
артефакты в .agent/student-role-02/; product code, Figma и внешнее состояние
не меняются.

## 2. Context / evidence

- docs/agent-workflow.md и корневой AGENTS.md, а также frontends/AGENTS.md,
  services/AGENTS.md, tests/AGENTS.md прочитаны. Требуется nine-section
  contract, один writer на область, fresh shell checks и независимый Sol review.
- HEAD/ветка и до-записный Git inventory зафиксированы в baseline.json.
  Рабочее дерево уже dirty; чужие tracked и untracked изменения сохраняются.
- .agent/js-student-01-main-merge/apply-result.json содержит 76 restored
  collisions; их hash coverage сохранён ссылкой в baseline, private env не
  копировался и не читался.
- Owner decision от 2026-09-06: BOTH PWA/TMA supersedes прежнюю оговорку
  «только PWA». Общие screens/components, logic/API/validation/command errors
  принадлежат sole writer mobile-core; shell adapters разделены.
- Root verified live Figma metadata: board node 4572:3062, file
  VgVjQYWILLG9AC7Eh12VMk, date 2026-09-06. Metadata не является
  design_context. Root read screenshots/context for Today, Attendance days,
  Homework feed and Profile; остальные matrix cells остаются context-pending.
- Existing evidence подтверждает только Today и четыре student API calls:
  session, today, schedule, checkin. Остальные перечисленные экраны в Vue
  сейчас NOT IMPLEMENTED; matrix не маскирует этот gap.
- Ownership gateway в доступной поверхности не exposed. Это записано как
  limitation; ownership не выдумывается и не захватывается.

## 3. Relevant scope

Owned writer scope — только новая директория .agent/student-role-02/:
contract.md, baseline.json, decisions.md, status.md, checks.json, matrix.md,
summary.md.

Future ownership after freeze:

- mobile-core: common screens/components, shared flow logic, API/transport
  contracts, validation and command-error mapping.
- Separate shell adapters: auth/session; geolocation/device; Back/navigation/
  host lifecycle.
- PWA shell: install, service worker and offline read policy for schedule and
  homework.
- TMA shell: online lifecycle and genuine Telegram host boundary.

Batch outline from the owner: shared shell foundation; Today + attendance +
requests; homework; stats + map; profile/auth; shell install/offline/lifecycle
and final E2E.

## 4. Required behavior

Обе оболочки должны получить один common student flow и одинаковую семантику
states/errors после freeze. Shared logic не дублируется в PWA/TMA.

- Common layer owns request shapes, validation, loading/empty/error states and
  command-error presentation.
- PWA keeps the owner-approved offline boundary: install/SW plus stable schedule
  and homework reads; mutations and fresh eligibility are not inferred offline.
- TMA remains online-only, uses the adapter boundary for host lifecycle and must
  be verified in a genuine Telegram host before final TMA acceptance.
- R2 governs requests: five individual manual petitions per semester to replace
  н with +; excuse tickets у are unlimited. Automatic requests do not consume
  the five-petition limit. R11 allows only excuse ticket у and forbids geo
  check-in and н-to-+ replacement in that blocked state.
- R4 governs the final mobile subset: four server metrics/counts and the user's
  own rank only where the contract supplies it. No invented ranking, export, QR
  or login UI.
- Homework is a date feed; this later decision supersedes old day/week/month
  wording. Retention is one year with an explicit expired state. R11 permits
  only у tickets and blocks check-in and н-to-+ replacement. Light mode keeps
  the same layout with existing semantic modes and
  canonical token aliases; new token names/values require a decision.
- Shell-specific APIs stay behind adapters. Telegram host types do not leak into
  domain/mobile-core.
- Each future batch follows: common sole writer -> separate shell adapter
  worktrees -> integrator and fresh scenario checks per shell -> fresh Sol high
  review/repairs -> next batch.

## 5. Constraints

- Do not alter any existing owner file, product code, generated contract,
  lockfile, config or docs outside the owned directory.
- Do not create commits or worktrees yet; root coordinates future Git mutations.
- Do not create children or a second orchestrator/control plane.
- Do not read/copy private env, secrets, build/cache/git data, or external
  applications. No deploy, migration, reset, push or Figma write.
- Owner decisions outrank older «only PWA» material. A missing product/contract/
  scope decision is recorded as a required delta for root, not redesigned here.
- API/DTO details for the non-Today screens remain pending; this preparation
  does not claim that the full API freeze or generated DTOs are complete.
- Sol xhigh may consult on substantive ambiguity only; it cannot override the
  owner/design and cannot be the sole reviewer of its own decision.

## 6. Existing patterns

docs/agent-workflow.md is the routing and evidence pattern. Existing
vertical-js-student-01 artifacts show the baseline/checks/status shape and
Java-first API evidence. frontends/AGENTS.md requires shared mobile-core,
separate PWA/TMA bootstrap/adapters, PCSS/rem and one cache/query owner. The
current four student calls are the only confirmed API seam for this batch.

## 7. Acceptance criteria

Preparation is accepted only when all of the following are true:

1. This contract has all nine sections and says PREPARATION / FREEZE_PENDING.
2. matrix.md contains exactly 39 root-provided live nodes and the columns
   node, screen-state-transition, story, API, existing, gap, acceptance, PWA,
   TMA.
3. baseline.json anchors HEAD/branch/status, per-file dirty tracked hashes,
   untracked docs/source status inventory, exclusions and the 76-entry prior
   manifest reference without copying private data.
4. checks.json records commands, exit codes, revision, environment, runtime
   evidence and limitations. JSON parse and owned-scope diff checks pass.
5. status.md, decisions.md and summary.md explicitly distinguish evidence, open
   gates and non-claims. No full matrix/freeze/implementation PASS is asserted.
6. The only new paths are under .agent/student-role-02/; all pre-existing
   changes remain byte-for-byte outside this directory.

Future implementation acceptance is separate: fresh PWA and TMA scenario checks,
real API/runtime evidence, genuine Telegram host check, and independent Sol high
review are required. A PWA result may be accepted with an explicit TMA host
OPEN gate when that host is unavailable; that does not close TMA.

## 8. Verification

For this preparation run: parse all JSON artifacts; verify revision and branch;
compare pre-write status inventory to post-write status and require additions
only below .agent/student-role-02/; inspect the owned diff. Product runtime is
N/A because no product behavior changed. Record every mechanical command and
its exit code in checks.json.

For later implementation: run common checks, fresh PWA checks including install/
SW/offline schedule+homework, fresh TMA online checks, then the genuine Telegram
host check and Sol review. WARN/ERROR changes code only after request link,
reproduction, new evidence, correction and recheck.

## 9. Do not

Do not implement screens, APIs, auth, adapters, service workers, offline storage,
Telegram integration or tests in this preparation scope. Do not claim that the
39-node inventory is a completed design or that Today proves the other screens.
Do not silently revive the only-PWA clause, invent unavailable gateway evidence,
use browser simulation as a genuine TMA host check, or close an OPEN host gate.
