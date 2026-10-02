# Ж — mobile-bff monitoring, source ready

Эксплуатационная конфигурация теперь включает внутренний `mobile-bff:9080/actuator/prometheus` в scrape jobs; существующий critical ServiceDown (`up == 0`, `for: 1m`) охватывает BFF без нового правила. Runtime registry добавлен по repo pattern. Root проверил стабильный diff и scope, дополнительных замечаний нет. Это repository preparation, не deploy и не runtime acceptance.

Source commit: **691577c9**, parent **83f3472f1b3e17672fe352f880683d00203af61d**. Три baseline blobs совпали с main50f6eabb, переноса старого кода нет. Diff: **3 файла, +11/-1**, сохранён в `source.diff`. Интеграция main — root.

## Inventory

Modified product/config/docs:
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/pwa-install-delivery-20260923/infra/prometheus/prometheus.yml` — job mobile-bff, path /actuator/prometheus, target mobile-bff:9080.
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/pwa-install-delivery-20260923/services/mobile-bff/mobile-bff-app/build.gradle.kts` — runtimeOnly micrometer-registry-prometheus.
- `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/pwa-install-delivery-20260923/docs/operations/monitoring/alerts.md` — BFF в ServiceDown, private_net endpoint, непубликуемый порт.

Created evidence, all under `C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/worktrees/pwa-install-delivery-20260923/.agent/evidence/mobile-monitoring-20261002/`:
- PACKET.md — contract/root scope delta, baseline and ownership.
- SUMMARY.md — этот итог.
- checks.json — criteria/commands/exit codes/environment/limitations.
- check_config.py — одноразовая evidence проверка YAML/source agreement; не product test suite.
- config-check.log — PASS, exit0.
- diff-check.log — пустой stdout успешного diff-check, exit0.
- artifact.log — точная команда и результат read-only frozen JAR inspection, exit0.
- source.diff — стабильный diff трёх файлов относительно main50f6eabb.

Deleted: none. Другие tracked dirty RULES/LEAF-PACKET и untracked файлы сохранены. Compose/security/backend Java/frontend не менялись. Нет детей, Terra, push, deploy, containers или heavy builds.

## Criteria → evidence

Source-level PASS на 691577c9 (проверен тот же diff перед commit): один job, правильные internal host/port/path, shared private_net, no published BFF ports, exposed prometheus path, generic ServiceDown, registry declaration. `python .agent/evidence/mobile-monitoring-20261002/check_config.py` exit0; `git diff --check -- <3 inventory paths>` exit0. Environment Windows, PowerShell7.6.5 -NoProfile, Python3.12.10/PyYAML6.0.3. 1 config run, 1 diff-check run, repeats PASS нет. Product/harness tests не добавлялись.

Frozen artifact evidence: JAR SHA2cac823da8937cc3818e0f513eb3443f531cd8b94fe666f2b5e955c03a42bc84 из manifest2948e60e содержит micrometer-core/observation, Prometheus registry отсутствует. Это подтверждение исходного дефекта; JAR/runtime не менялись.

Source commit сначала завершился exit128 из-за sandbox read-only Git metadata (index.lock), затем разрешённая scoped операция с require_escalated прошла exit0. Никаких auto-review rejection не было. Git ignore/recovery permission warnings не связаны с пользовательским сценарием и не изменяли код.

Финальный расширенный `git diff --check 83f3472f..HEAD -- <3 inventory paths> .agent/evidence/mobile-monitoring-20261002` дал exit2 только на source.diff:6/12/27: это обязательный пробел context-empty lines внутри сохранённого unified diff. Product diff-check exit0 сохраняется. Raw patch сохранён неизменным, эта особенность вложенного diff-artifact не является дефектом продукта. Evidence text проверен отдельно с исключением source.diff.

## Runtime и ограничения

**Runtime endpoint PASS 2026-10-02**, см. [RUNTIME.md](RUNTIME.md): bounded BFF bootJar и actual loopback HTTP200/Prometheus JVM/process samples подтверждены после root-authorized packaging correction **3db2cc02** (единственная добавленная строка shared-logback dependency в том же buildfile). Свой PID35356/listener49771 отсутствуют, heavy lease освобождён. Main monitoring source d03d48ac + evidenceafbf2aad; logging correction интегрирована root как **706a09e5**, runtime outcome/cleanup приняты.

Frozen stand2948 не менялся; promtool локально отсутствует. Prometheus service scrape/alert evaluation и provider delivery не проверены. Перед отдельно разрешённым deploy остаются promtool config validation и scrape/alert smoke. Дополнительный точный inventory, commands/exit codes, сохранённые failures, причина повторов и cleanup — в RUNTIME.md/runtime-checks.json.

## Model/effort evidence

Root подтвердил actual spawn call: agent_type=developer (роль фиксирует gpt-6.1-sol/high), explicit model=gpt-6.1-sol, reasoning_effort=high, fork_turns=none; returned task_name=/root/zh_mobile_monitoring_1002. Отдельные response поля actual model/effort tool не возвращает, поэтому независимого runtime metadata подтверждения пары нет. Настройки не подменялись. No children.
