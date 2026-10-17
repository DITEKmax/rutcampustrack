---
name: rct-verification
description: Runtime-проверка, разбор ошибки и evidence RutCampusTrack.
---

1. Прочитай criteria и `tests/AGENTS.md`, найди команды в scripts/build/CI и сопоставь
   checks с риском и изменённым поведением. Выполни применимые mechanical checks,
   затем приложение/сервис; в `.agent/checks.json` запиши revision, command, exit code,
   среду и evidence.
2. UI: локальный flow, состояния/keyboard, темы, spec widths, screenshot vs Figma;
   production PWA SW отдельно от mocks. Используй доступный browser tool, не выдумывай
   MCP. Backend: endpoints и межсервисное поведение; для auth/data-loss — negative
   cases и scanners, которые LLM-отчёт не заменяет.
3. WARN/ERROR → service/request → correlation/trace → hypothesis → code → reproduce →
   failing test (если полезен) → fix → повтор нужного check. Не каждый лог — defect,
   не глуши ошибку. Реальный результат доказывает сценарий, не продукт; если test
   противоречит требованию, установи причину; старый зелёный test не отменяет новое.
4. Reviewer получает criteria, план при наличии, стабильный diff и evidence без
   transcript автора; его ответ — отдельный неизменённый артефакт. Явно обозначай
   PASS/FAIL/BLOCKED и gaps.
