# Independent served Vue artifact oracle review

Вердикт: **PASS для source/harness delta; подтверждённых P1/P2 нет**. Полный shipping/replacement runtime этим review не подтверждён.

Дата: 2026-10-24. Frozen commit: `8b8f3c7ac91fb92543749c09b20dc38ed4ab5230`; parent: `05e08a2e3fddc909f5fd10ee064dd022f68a04ae`. Ровно два файла: `scripts/verify-served-web-panel.py` и `tests/harness/test_served_web_panel.py`. Назначение — независимое Sol6.1/high review; runtime model telemetry недоступна.

## Результат source review

- `Entrypoint`/`entry_asset` (oracle:21–44) заменяют Angular-specific selector на ровно один element с `id=app` и ровно один module script. Подсчёт root охватывает все типы elements; второй `span#app` тоже делает artifact ambiguous. Missing/inline/несколько module entries не допускаются. Non-UTF8 HTML отклоняется.
- Asset selector ограничен root `/assets/`, одним basename и hashed JS suffix длиной минимум восемь допустимых символов. Slash после prefix, relative/external/protocol-relative URLs, percent-encoding, query и fragment не проходят fullmatch. Полученный путь безопасно ограничивает `docker exec ... cat /usr/share/nginx/html/...` и соответствующий HTTP fetch; новый selector не открывает traversal или caller-supplied filesystem path.
- Один и тот же `entry_asset` выбирает module из expected container index и повторно разбирает served index. SHA256 equality HTML связывает этот выбор с тем же artifact; не появляется отдельного, менее строгого пути выбора served script.
- `main` (72–83) сохраняет exact 40 lowercase-hex revision, container `rct-web-panel-nginx` и exact image string `ghcr.io/ditekmax/rutcampustrack/web-panel-nginx:<revision>`. Base дополнительно ограничен HTTP(S) origin root без path/query/fragment и непустого userinfo, что соответствует неизменному production caller. Container/image tag не подменены dev service или arbitrary input.
- Сохранены HTTP200, HTML MIME, JavaScript MIME allowlists, nonempty module, SHA256 expected/served HTML и module equality, command/HTTP timeouts10s и body bounds32MiB. Неправильные MIME, SPA fallback вместо JS, пустой/чужой module или index не дают PASS. Safe CLI failure output и nonzero exit остаются без печати response bodies/command stderr.
- Caller в `scripts/deploy-release.sh:76` не меняется: oracle остаётся перед записью deployed-SHA marker. Angular root/main артефакт теперь намеренно FAIL, пока Vue replacement реально не выполнен; проверка не ослаблена до status-only.

## Harness и независимые проверки

Существующие восемь unittest cases прочитаны, повторно не запускались. Они охватывают exact small artifact, status/MIME/index/module mismatch и empty module; Angular/неверный/duplicate root; duplicate/nonmodule/unhashed/relative/external/traversal/query-bearing entry; exact revision image mismatch при тех же HTTP bytes; actual loopback HTTP путь `main` с image reads из memory; fallback HTML для module; invalid revision/non-root base. Loopback HTTP и fake image files здесь явно тестовая обвязка, а не реальный deployed container acceptance.

Прочитан `/workspace/rct-pr3-runtime/SERVED-VUE-ORACLE-8b8f3c7ac.md`; авторский unittest8 PASS и local dist format PASS не выдаются за собственные запуски reviewer. Отсутствующий pytest не устанавливался. Независимо: frozen two-file diff/source review, `git diff --check` exit0, AST parse обоих Python файлов без исполнения/import — PASS. Author HEAD совпал с frozen revision, status clean во время проверки.

## Пределы

Oracle доверяет существующему deployment boundary: exact named container и его `.Config.Image` revision tag; это не новая криптографическая attestation image provenance. Gate проверяет index и entry module, а не полный граф chunks/CSS, успешный Vue mount, роли, API или весь пользовательский flow. Эти границы указываются явно, без расширения narrow source-review scope.

Никаких Docker/build/remote HTTP/deploy/CI/browser операций или повторного harness запуска reviewer не выполнял. Реальная Vue shipping image/container/HTTPS outer-edge проверка с правильным revision tag остаётся pending. Current Angular deployment, legacy Angular controls и global replacement delivery gate этим отчётом не принимаются. Продуктовые файлы/конфигурации/процессы не менялись; единственная запись — этот внешний immutable report.
