# DATE homework notification — source-ready S1

Ученик получает выбранную дату ДЗ при homework.published и homework.updated: `Дата: 2026-10-05`, без вымышленной пары. Legacy/no-mode и LESSON с номером сохраняют `Пара: №2, 2026-10-05`. DATE также не использует устаревший номер. Описание, ссылка, аудитория и SendTask поля сохранены.

Продуктовый diff: только services/notification-bot/bot/notifications/homework.py, 5 добавленных/2 удалённых строки в `_build_card`. Основная логика: дата есть → numbered non-DATE сохраняет пару; иначе выводится дата. Digest/reminder прочитаны: уже выводят дату и не создают пару при null number; без изменения. Product tests не изменены/созданы.

Criteria и ownership: CONTRACT.md. Baseline f5b2ed147a43c50bb25636ed5241027169a9e804; owned file equals main a3d64e0f before edit (exit0). Tracked WT initially clean; foreign untracked evidence untouched. Current main dirty docs/instructions observed and untouched. Worktree old AGENTS requirements superseded by current canonical RULES, hash matches root packet. No redesign/product/scope decision needed. Root integrates commit, no main write/push/deploy here.

Evidence: product.diff; checks.json; runtime-after.log contains actual local message text for both DATE event types. Baseline reproduced six failures (runtime-before-utf8.log, exit1); after scoped fix all ten local cases PASS (exit0); syntax and scoped whitespace checks PASS (exit0). First probe log preserved: emoji failed cp1251 stdout after first reproduced date failure; subsequent invocation uses Python -X utf8. This harness correction did not change product. Three probe invocations, no repeated PASS or suite expansion; post-fix handler invocation 0.394s. Git ignore/cache permission warnings unrelated to this formatting request, no code response.

Runtime boundary: actual consumer and SendTask exercised with synthetic academic response and recording bot/queue. aiogram.Bot import placeholder is annotation-only; installed aiogram/integration/provider delivery are NOT verified. Default Python312 lacks aiogram, so pytest not run; no installation/network/secrets/live sends. No Docker/Gradle. Requested model Sol6.1/high; runtime model fields not exposed. No children/Terra.

Exact files:
- Modified product: services/notification-bot/bot/notifications/homework.py.
- Created evidence/harness: .agent/evidence/homework-date-bot-20261002/{CONTRACT.md, SUMMARY.md, checks.json, probe.py, product.diff, runtime-before.log, runtime-before-utf8.log, runtime-after.log, syntax.log, diff-check.log}.
- Deleted: none.

Готовность: source-ready для локальной интеграции root; серверный producer contract относится к Academic author, внешняя Telegram доставка остаётся неподтверждённой.
