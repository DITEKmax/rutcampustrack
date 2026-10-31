# Angular удалён из активной поставки

25 октября2026. Авторский source `ba8cde5aa360c5658398d4cd5936682d542ca7e2`, интеграция
`ddfca53015cb952ffd65e96689a2cba8574084f8`. [Независимый review](SOURCE-REVIEW.md): PASS.
Удалены433 tracked файла отдельного Angular package; сохранены общие ресурсы,
Vue/API и полезные проверки поведения. [Сверка117 принятых действий](accepted-actions-audit.csv)
не нашла утраченной принятой функции. Полный group UI перенесён владельцем на Ubuntu:
исторический статус этой строки in progress сохранён, локальный результат NOT_RUN.

WEB получил прежнюю гарантию user-controlled stale chunk recovery: явное действие,
не более одного reload на вкладку; неизвестные операции и drafts не перезапускаются.
Четыре поведения recovery, served oracle8, route controls19/47, scoped lint и formatter
inventory прошли. Formatter исправлен bounded16MiB buffer после фактического ENOBUFS;
правила проверок не ослаблены. Все4 CI-family workflows остаются workflow_dispatch/PAUSED.

[Root strict/typecheck + Vite WEB/TMA](strict-build-results.json) PASS, source ddfca530.
Они подтверждают сборку, не всю поставку. WEB `/` пригоден для reuse; TMA build default `/`
и прежний PWA default `/assets/` не подходят для prefix `/mini-app/` и `/app/`.
Поэтому только эти prefix builds повторяются с explicit production config.
Общий Nginx runtime, authenticated realtime, activation/update и WebPush учитываются отдельно.

[Остатки ignored Angular dependencies](ignored-legacy-cleanup.json) удалены адресно после
source integration; shared Vue dependencies сохранены. Логические531MB не были реальным
reclaim: actual available вырос только4096B из-за overlay. Git history не переписана,
rollback images/JARs/исходники других worktrees и recovery ZIP сохранены.
