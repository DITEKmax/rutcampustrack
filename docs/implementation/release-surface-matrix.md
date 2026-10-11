# Матрица surface и release

Решение владельца 2026-09-27: целевой релиз — Vue PWA/TMA для всех ролей. React PWA, React Mini App и Angular web-panel выводятся после готовности и приёмки новых клиентов; ожидать новую web-панель не нужно. Ниже сохранён исторический срез от06.09, не текущее состояние реализации. Актуальное состояние — .agent/orchestration-v2/CURRENT.md. Возраст/префикс API не основание удаления: маршруты с потребителями в новых клиентах остаются.

Дата: 06.09.2026. Она фиксирует порядок владельца, не назначает релиз.

| Surface | Current delivery | Target direction | First gate | Release status |
|---|---|---|---|---|
| PWA student | React/Vite, existing | Vue/PCSS shared mobile-core | contract + S-01/S-02 design packet + SW policy | candidate, unassigned |
| Telegram Mini App | React/Vite, existing | Vue/PCSS shared mobile-core + Telegram adapter | contract + real init-data/host verification | candidate, unassigned |
| Web | Angular web-panel, existing | later after PWA/TMA | separate owner packet and replacement coverage | deferred by sequence |
| Legacy landing | static existing | no replacement requested | none | out of scope |

PWA offline remains only schedule and homework; TMA remains online. Cache/query owner,
offline writes, attachments, user switch and expired session are unresolved policy, not
defaults to implement. The matrix has no mobile omission that cancels a web/server story.

