# Delivery policy — 2026-09-20

Результат: инструкции теперь направляют работу на завершённые пользовательские сценарии, прямые bounded assignments, достаточные проверки и измеримую готовность. Владелец общается только через главный чат оркестратора. Product STOP не снят; продуктовые исходники/тесты не менялись, новые чаты не создавались, модели/fast mode/global config не менялись. Единственный read-only source scout завершён, все slots FREE.

## Изменённые файлы

- [AGENTS.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/AGENTS.md): короткий entrypoint, приоритет новой policy, отчёт файлов, снята обязательная отдельная стадия переноса перед кодом.
- [tests/AGENTS.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/tests/AGENTS.md): checks по риску, запрет низкоценных новых tests/необоснованных повторов.
- [frontends/AGENTS.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/frontends/AGENTS.md): единый pointer, UI проверяется в затронутом scope; дизайн/PCSS ограничения сохранены.
- [services/AGENTS.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/services/AGENTS.md): единый pointer, event tests только по изменённому риску; защита данных/прав сохранена.
- [docs/agent-workflow.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/agent-workflow.md): короткий маршрут и обязательный точный файловый summary.
- [RULES.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/RULES.md): канон delivery policy, on-demand leads, один канал владельца, corrections пакетом, targeted recheck.
- [CURRENT.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/CURRENT.md): компактный STOP и точное продолжение вместо длинной хронологии.
- [SLOTS.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/SLOTS.md): все slots FREE, product STOP.
- [REGISTRY.json](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/REGISTRY.json): удалены устаревшие ACTIVE состояния, зарегистрирован новый RULES SHA.

## Созданные файлы

- [docs/product/READINESS.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/product/READINESS.md): источники147 stories/175 requests/27 уникальных spec screens, формулы и статусы без выдуманного completion.
- [docs/product/decisions/2026-09-20-delivery-policy.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/docs/product/decisions/2026-09-20-delivery-policy.md): решение владельца, область отмены прежних правил и registry limitation.
- [DELIVERY-POLICY-PACKET.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/DELIVERY-POLICY-PACKET.md): scope этой документационной работы.
- [evidence/delivery-policy-doc-checks.json](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/evidence/delivery-policy-doc-checks.json): ссылки, JSON/hash и scoped diff evidence.
- Этот [DELIVERY-POLICY-SUMMARY.md](C:/Users/maksd/IntelliJIDEA/rutcampustrack/.agent/orchestration-v2/DELIVERY-POLICY-SUMMARY.md).
- В archive/2026-09-20-before-delivery-policy/ сохранены9 точных прежних файлов с суффиксом.inactive: AGENTS.md; tests/AGENTS.md; frontends/AGENTS.md; services/AGENTS.md; docs/agent-workflow.md; .agent/orchestration-v2/RULES.md; CURRENT.md; SLOTS.md; REGISTRY.json (последние четыре с полным исходным вложенным путём).

Удалённых файлов нет. .agents/.codex skills и global инструкции не правились; новый project owner override определён явно. Старые worktree packets перед будущим dispatch получают короткое дополнение с текущим RULES SHA; historical evidence hashes не переписываются.

## Проверка и границы

Проверены12 scoped documents: локальные Markdown links существуют, REGISTRY JSON читается, RULES hash совпадает. Scoped git diff --check после удаления двух лишних EOF blank lines exit0. Это документационная проверка, product runtime/tests N/A. Отдельного reviewer не создавали.

Не проведена новая приёмка147 сценариев/экранов/всех сервисов: READINESS задаёт базу и формулу, unassessed не выдаётся за готовое или отсутствующее. Полноту release scope и текущие accepted evidence предстоит связывать по мере задач без массового повторного прогона.

Дальше — только по product GO владельца: известные хвосты двух веток → интеграция → risk review → реальная ретроспективная цепочка. Знакомые команды/сохранённые результаты используются повторно. Перемещать worktrees/чистить тесты в рамках этой задачи не требовалось.
