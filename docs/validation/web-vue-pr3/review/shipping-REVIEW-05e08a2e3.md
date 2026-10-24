# Independent config review — isolated Vue WEB edge

Вердикт: **PASS в пределах двухфайлового source/config delta**. Подтверждённых P1/P2 нет. Это не runtime/production replacement PASS.

Дата: 2026-10-24. Frozen original commit: `05e08a2e3fddc909f5fd10ee064dd022f68a04ae`; parent: `f8239f166789fb9fe8cbc7b063c1097a8d11e21d`. Ровно два файла: `docker-compose.e2e.yml` и `tests/e2e/infra/nginx/default.conf`, 18 добавлений/8 удалений. Назначение — независимое Sol6.1/high review; runtime model telemetry недоступна.

## Проверенные свойства

- `docker-compose.e2e.yml:615` добавляет `web-nginx`, используя существующий `./frontends` build context и реальный `web-vue/Dockerfile`, image `rct/web-nginx:${IMAGE_TAG:-e2e-local}`, только internal port80 и existing `private_net`. Host ports, новые secrets, volume mounts, profiles или внешняя сеть не добавлены. Compose service DNS `web-nginx` совпадает с upstream в nginx; оба сервиса принадлежат одной сети.
- Edge `depends_on.web-nginx.condition=service_started` добавлен рядом с такими же dependency существующих static PWA/TMA. Это обеспечивает порядок старта/DNS и не заявляет health readiness: новый статический сервис не имеет healthcheck. Actual readiness/HTTP проверка до browser gate остаётся отдельной runtime задачей; existing API-gateway dependency `service_healthy` сохранён.
- `tests/e2e/infra/nginx/default.conf:78` заменяет прежние root302→PWA и catch-all404 на `proxy_pass http://web-nginx:80` с сохранением Host. Отсутствие URI suffix у proxy_pass сохраняет исходный путь/query; `/login`, protected deep links, `/assets/...` поступают в WEB без prefix stripping. Vue `createWebHistory()` и Vite default root base согласованы с этим mount.
- Существующие более специфичные `/api/ws/`, excuse upload exact location, `/api/`, `/app/` и `/mini-app/` выигрывают перед root `/`. API/WS forwarding headers, TLS, upload limit, student-request no-store policy и mobile prefix stripping побайтно сохранены. WEB server получает только остальные root requests; worker/PWA/TMA routing не переносится на WEB.
- Referenced WEB static nginx сохраняет `/usr/share/nginx/html`, HTML no-store, SPA fallback и missing `/assets/`404. Его blob совпадает с ранее рассмотренным shipping config `4445a2082`. Actual referenced Dockerfile дополнительно содержит уже существующие в parent optional build-secret CA и readable static output; эти изменения не входят в текущий двухфайловый delta. Build output остаётся `web-vue/dist`, а не Angular/mobile dist.

## Независимые проверки

- Прочитан inventory `/workspace/rct-pr3-runtime/WEB-E2E-WIRING-05e08a2e3.md`, frozen diff, полные затронутые nginx locations и нужный compose service/network context; адресно сверены referenced Dockerfile/static config/router/Vite base.
- `git diff --check 05e08a2e3^ 05e08a2e3` — exit0. Author worktree HEAD совпал с frozen revision, status clean во время проверки.
- In-memory PyYAML сравнение parent и current: после удаления ровно нового `web-nginx` и edge dependency весь parsed compose равен исходному. Новый service и dependency отдельно сравнены с exact ожидаемыми полями — PASS.
- Exact text comparison nginx: после только заявленных root/comment replacements весь файл равен current. Все прочие API/WS/upload/mobile/TLS/privacy/header blocks byte-unchanged — PASS.

Авторский `docker compose ... config --quiet` exit0 передан в inventory; reviewer его не повторял и private environment file не читал. Новые tests для basic wiring не создавались.

## Явные пределы

Docker/build/up/nginx syntax/runtime DNS/HTTP/browser/network/CI не запускались. Фактические HTTPS root/deep-link refresh, module MIME/bytes, original PNG, missing hashed asset404, no-store HTML, cookie/API/WS/privacy semantics и mobile PWA worker/version/TMA routing через этот assembled outer edge остаются pending. Предыдущее standalone image evidence не доказывает данный outer-edge integration.

Production compose/global nginx/CSP, application source/Angular/locks/workflow triggers вне delta и не менялись этим commit. Legacy Angular controls, окончательная замена Angular и production/global shipping остаются незакрытыми; PASS не расширяется на них. Reviewer не менял продуктовые файлы или процессы; единственный созданный файл — этот внешний immutable report.
