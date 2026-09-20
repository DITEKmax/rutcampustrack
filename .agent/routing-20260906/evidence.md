# Evidence — routing 2026-09-06

- Owner decision (06.09.2026): root Astra medium; Luna high scout/fallback;
  Luna max bounded implementation; Terra high or Sol high escalation; fresh Sol
  high important review; Sol xhigh only justified S4.
- Official source, checked before implementation:
  <https://learn.chatgpt.com/docs/config-schema.json>. It documents `agents.enabled`,
  `max_concurrent_threads_per_session`, `default_subagent_model` and
  `default_subagent_reasoning_effort`.
- Official source, checked before implementation:
  <https://learn.chatgpt.com/docs/agent-configuration/subagents>. It documents
  standalone and project role configuration; role files retain only behavior so
  explicit spawn model/effort remains authoritative.
- Runtime metadata supports `gpt-5.6-luna` with `max` effort. File configuration
  does not claim to change an already running root session.
- Active conflict found before edit: local config had Astra low and Terra medium
  fallback; `parallel-development.md` named Root Astra low and Terra developers.
