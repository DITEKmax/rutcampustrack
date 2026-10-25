# Независимое source review Angular removal — PASS

Ревизия: `ba8cde5aa360c5658398d4cd5936682d542ca7e2`; база: `7ef451a6cfd7f0e0134816eb4ea90ca290829668`.
Worktree: `/workspace/rct-worktrees/web-map`, ветка `codex/pr3-angular-removal`.
Reviewer: назначен `gpt-6.1-sol/high`; telemetry недоступна, fallback не заявлен. Авторство продукта отсутствует.

**Блокирующих замечаний и подтверждённых регрессий в изменённом source scope не найдено. Source PASS.** Это разрешает принять данный source cleanup в рамках нового решения владельца; runtime/build готовность этим отчётом не установлена.

## Проверенный scope и доказательства

- Полный diff: 449 путей, точное совпадение с `changed-files.tsv`: 433 удаления исключительно внутри `frontends/web-panel/`, 14 изменений, 2 добавления. Shared/mobile/backend, E2E fixtures/specs, private результаты и внешние runtime receipts в этом commit не изменены. Удалённый пакет содержит Angular source/config/lock/generated dist; общие assets и источники Vue не удалены.
- `accepted-actions-audit.csv`: 117 уникальных IDs точно совпадают с 117 active строками `SCREEN-ACTION-MAP.csv`; роли AUTH6/SHARED15/STUDENT16/HEADMAN38/TEACHER8/ADMIN34. Все `current_source` существуют. Статусы совпадают: 116 locally verified и 1 in progress (`ACT-ADMIN-GROUP-FINAL-DELETE`). Оставшиеся 6 map rows сохраняют прежние deferred/excluded/candidate статусы; знаменатель не сокращён и group pending не объявлен PASS.
- Сопоставлены прежние Angular auth/guards/profile/notifications/student/headman/admin/teacher transport и routes с сохранёнными Vue pages и mobile-core clients. Текущие API/BFF операции, shared theme/avatar/map/notifications, exports и role/authority fences сохранены; принятые замены не изменяются commit. Неиспользуемые прежние endpoint/helpers не являются отдельными принятыми действиями. Сохранённые Vue E2E oracle/specs не заменены удалением покрытия.
- Единственная отдельная bootstrap-гарантия старого Angular — recovery от stale lazy chunk — перенесена в `frontends/web-vue/src/app/chunk-recovery.ts:18`, `:30`, `:57`, wiring `main.ts:11`, notice `App.vue:41`. Ошибка только показывает recovery; reload вызывается явной кнопкой, с повторной проверкой прежнего per-tab storage key. Отказ storage запрещает reload. Нет автоматического reload, повторной мутации или обхода auth; existing view и beforeunload guards сохраняются. Четыре имеющихся теста покрывают именно эти риски; повторно не запускались.
- CI/Coverage/Security/OpenAPI сохраняют только `workflow_dispatch`. Численные thresholds/права/denial checks не ослаблены. WEB tests добавлены в CI; coverage включает прежние источники и дополнительно WEB/shared. Security исключение всего Angular каталога удалено, WEB image добавлен в scan matrix; M15 Dockerfile exception заменяет точный старый путь точным Vue путём. Dependabot остаётся на каноническом `/frontends` workspace. `frontends/package.json` и lock byte-неизменны; Angular packages в canonical lock отсутствуют.
- CSP меняет только Angular lazy-CSS inline-event hash/unsafe-hashes на `script-src self`; прочие security directives сохранены. Formatter сохраняет evidence exclusions/classification; bounded `maxBuffer=16MiB` устраняет фактическое переполнение git inventory, без ослабления проверки.
- Dev/prod/Docker wiring уже направлен на Vue и сохранён; source root/deep-link/assets fallback остаётся в `web-vue/nginx.conf`. Common Nginx API/SockJS/PWA/TMA routing commit не меняет. Подозрение об exact `/api/ws` не подтверждается: SockJS использует `/info` и session paths под действующим `/api/ws/` prefix.

## Ограничения

Только чтение source и metadata; build/typecheck/Docker/browser/Auth/CI/replay не запускались. Существующие результаты из HANDOFF переиспользованы, не выданы за новые проверки. Новая assembled Vue/common Nginx квалификация выполняется отдельно назначенным runtime owner. Full group6-view UI остаётся owner Ubuntu **NOT_RUN**, по новому решению владельца не блокирует source cleanup. WebPush delivery PASS не заявлен.

Известные Root doc corrections из HANDOFF (актуальные README/CLAUDE/formatter README и shared CSS header reference) остаются частью документационного завершения; они не означают потерю реализации. Root-only ignored legacy node_modules/.angular этим review не удалялись. Merge/push/deploy/production/new environment/Goal/PR и workflow dispatch отсутствуют.

Новые проверки: только read-only git diff/inventory, CSV ID/status/path сверка, canonical lock inspection и source review. Product tests/build/runtime runs: **0**.
