FAIL — независимый S2 review snapshot до repair.

Проверенный snapshot:

- `docs/agent-workflow.md`: `FDF82050123E61B5C3700DED248C476ACF7B82B91399B2845062AA7EE4EA4FBF`
- `AGENTS.md`: `61A9F56E9CD57FD5B5B8E3FECD7AAC1954F1583B509424927A00557B39CE8619`
- staged global `AGENTS.md`: `373A150766D1864CE9035A18613D8779B39966C1E1CC08D0AEFB021F0B0DC71C`
- apply-script: `402E6B163D7E6B0924B71817DC0B0201054273AAD3FE2AA7CA465C885649AB2D`

Findings:

1. **S2 — неполный global routing contract.**
   Файлы: `.agent/routing-20260906/global-stage/AGENTS.md:3`, `.agent/routing-20260906/task-contract.md:24`.
   Evidence: global contract содержит root Astra medium, Luna high/max, Terra high и Sol high, но пропускает Astra low для очевидного contract, Astra medium/high для architecture/product/uncertainty/risk, Terra medium для сложных связей, маршруты S0–S4 и условие Sol xhigh с сохранением S3-защит.
   Impact: после global apply проекты без локального workflow получат неполную версию решения владельца.
   Проверка: прогнать таблицу решений для global-only S0, S3 architecture и S4 cases.

2. **S2 — отсутствует staged handoff с чтением критичных оригиналов.**
   Файлы: `docs/agent-workflow.md:53`, `docs/agent-workflow.md:70`, `docs/agent-workflow.md:90`, staged global role/AGENTS packet rules.
   Evidence: документы требуют compact evidence, но не требуют staged context handoff и адресного чтения критичных оригиналов senior planner/reviewer. `checks.json:34` проверяет лишь headings и contract sections.
   Impact: сжатый scout summary может стать единственным основанием contract и review, скрыв потерянное критичное условие.
   Проверка: packet с critical source reference должен явно заставить planner/reviewer открыть оригинал, сохраняя остальной контекст компактным.

3. **S2 — architecture review ошибочно ограничен наличием спора.**
   Файлы: `docs/agent-workflow.md:32`, `docs/agent-workflow.md:53`, `.codex/agents/reviewer.toml:2`, `docs/implementation/parallel-development.md:213`.
   Evidence: Astra architecture review/judge разрешён только при «реальном споре». Решение владельца маршрутизирует Astra medium/high также по критичности архитектуры, продукта, неопределённости и риска.
   Impact: согласованное, но опасное архитектурное решение может миновать требуемую senior-проверку.
   Проверка: S3 architecture case без разногласий, но с contract/data-loss boundary, должен маршрутизироваться на Astra по риску.

4. **S2 — reviewed stage не защищён и применение не транзакционно.**
   Файл: `.agent/routing-20260906/apply-global-routing.py:13`, `:82`.
   Evidence: hashes проверяются только для live targets. Первая запись происходит в `config.toml` на строке 87, а staged-файлы читаются позднее на строках 88–90; нет stage hashes, полного preflight, atomic replace или rollback.
   Impact: изменённый/отсутствующий staged-файл либо ошибка позднего copy может применить непроверенное содержимое или оставить частично изменённый global routing. Повтор заблокируют уже изменившиеся target hashes.
   Проверка: изменение/удаление staged role и injected failure на втором copy должны завершаться без изменения любого target.

5. **S2 — filesystem boundary не защищена от symlink/reparse targets.**
   Файл: `.agent/routing-20260906/apply-global-routing.py:22`, `:73`, `:84`.
   Evidence: `exists`, `read_bytes`, `write_text` и `copy2` следуют symlink/reparse paths. Broken role symlink возвращает `exists()==False`, совпадает с ожидаемым `None`, после чего copy может записать во внешний referent. Backup paths также не проверяются как обычные пути внутри global root.
   Impact: разрешённый global apply способен перезаписать файл вне `.codex` или повредить rollback-копию.
   Проверка: fixture с broken target symlink, symlinked backup leaf и reparse parent должен быть отвергнут до первой записи.

Repair contract:

- **Defect:** global/local policy неполон; architecture gate слишком узок; apply не фиксирует reviewed inputs и не гарантирует filesystem boundary/целостное применение.
- **Correction:** добавить полную model/effort и S0–S4 матрицу, staged handoffs и critical-original inspection; заменить dispute-only gate на risk/necessity gate. В script — проверять hashes и TOML всех staged inputs до записи, запрещать symlink/reparse paths, использовать уникальные backups, same-directory temporary outputs, atomic replace, rollback и readback.
- **Scope:** staged global instructions/roles, task contract, project workflow/reviewer/parallel routing, apply-script, checks и summary.
- **Verification:** explicit policy assertions; malformed/changed/missing stage; mid-commit failure; target/backup symlink cases; successful apply с readback hashes, TOML parse, целевыми значениями и сохранённой unrelated parsed-семантикой; затем свежая независимая recheck.

Текущий global config и рассчитанный proposed config независимо разобраны через allowlisted parser: оба валидны, целевые значения верны, unrelated parsed-семантика совпадает. Содержимое и секреты global config не выводились. Global apply не запускался, файлы не изменялись.
