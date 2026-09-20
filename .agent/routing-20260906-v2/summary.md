# Summary — routing instructions v2

Статус: PROTECTED_APPLIED; READY_FOR_FRESH_SOL_REVIEW.  
Изменение: общий canonical route теперь ведёт от Astra root через bounded
read-only coordinators одного уровня (если полезны) к свежим Luna max leaves;
coordinator может spawn назначенные leaves только при явном назначении root,
nested coordinators и leaf children запрещены. Terra high проходит только
evidence-backed defect/complexity gate; fresh Sol high остаётся независимым
важным review, а Sol xhigh разрешён лишь как явно обоснованный S4 review.

Проектные docs больше не повторяют общую model/risk таблицу. Локальные
`.codex`-роли и config, а также global `AGENTS.md`/roles, подготовлены в stage;
текущие protected targets применены root’ом через разрешённый filesystem path.
Config stage сохранял только
поддержанные schema fields и default `gpt-5.6-luna`/`max`.

По новому решению владельца в `docs/agent-workflow.md` добавлен один локальный
абзац для будущих партий: после freeze API/shared components независимые экраны
идут параллельными fresh Luna max заданиями, затем для каждой роли выполняется
отдельный end-to-end сценарий от входа до результата с negative cases, при
необходимости используется объединённый Docker runtime и проводится fresh
независимый Sol important review. Legacy compatibility layer не добавляется;
проверяются принятые новые flows, authz и data invariants. Текущая партия имеет
пять defects, TMA checks отложены владельцем, финальную main integration root
выполнит позже с сохранением pre-dirty состояния.

Проверено: active/staged TOML и JSON-compatible manifest parse, PowerShell AST,
canonical/no-conflict text assertions, staged/live hash inventory, staged-only
guard, isolated project success with backup/readback, stale-live refusal and
mid-commit rollback. Все exit codes и environment записаны в `checks.json`.
Product runtime N/A; observed four-slot overlap is runtime evidence for this
session only and does not prove config effectiveness. После TMA root получил
реальный `agent thread limit reached` при spawn интегратора, пока root+BE+PWA и
завершающий nested leaf были видны; interrupt leaf слот не освободил. Свободный
spawn определяется фактическим результатом и состоянием открытых/завершающихся
children, а не одним running count.

Свежая повторная проверка полного `docs/sources/manifest.yaml` остановилась на
строке 31196 из-за независимого malformed fragment `"decision]cate"`; файл не
переписывался, точный exit code записан в `checks.json`.

Root actions: obtain the independent fresh Sol review and final acceptance.
Protected apply already passed for all nine targets; backup is recorded in
`evidence.md`/`checks.json`. A running session may retain prior parameters until
its lifecycle boundary.
