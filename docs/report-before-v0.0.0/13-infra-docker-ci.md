# 13. Инфраструктура: Docker, nginx, CI/CD, monitoring

## Сводка

Инфраструктура разложена «по слоям» достаточно чисто: dev-compose (`docker-compose.yml`) и prod-compose (`docker-compose.prod.yml`) не переиспользуют override-файл, а живут как две независимые копии — это упрощает чтение, но создаёт риск дрейфа (healthcheck/env добавляются в dev, забываются в prod, и наоборот). Java-Dockerfile'ы сделаны в едином стиле: multi-stage (builder → layertools extractor → JRE-runtime), non-root пользователь, BuildKit-кэш градла. Корневой nginx реализует TLS-термирование, HSTS, CSP, единую точку входа (`/login`→web-panel, `/app/`→PWA, `/presentation/`→landing) и WS-прокси для STOMP. CI (`ci.yml`) строит/тестирует Java-матрицу + Python-бот + три фронта, отдельный `deploy.yml` собирает 11 образов в GHCR и деплоит по SSH на VPS.

Проблемы — в деталях, и они преимущественно P0/P1:
1. В `init-letsencrypt.sh` certbot выпускает сертификат под `--cert-name rutcampustrack`, но `nginx/conf.d/default.conf:24-25` ожидает его в `/etc/letsencrypt/live/ruttrack.site/` — пути расходятся, после первого запуска nginx упадёт.
2. CI на `main` и `deploy.yml` — **две независимые пуш-триггера**: сборка образов в `deploy.yml` не зависит от результата `ci.yml`. Красный CI не блокирует выкат.
3. CSP в корневом nginx (`default-src 'self'; connect-src 'self' wss:; font-src 'self';`) ломает лендинг (Fontshare/Google Fonts/unpkg/jsdelivr). Это было зафиксировано в отчёте 12 как P0-1 — здесь подтверждаем, что проблема в **инфра-конфиге**, а не в лендинге.
4. `.env.prod` лежит в рабочей копии с **реальными** секретами продакшена (Telegram токены, GHCR PAT, Grafana/DB пароли). Файл гитигнорирован, но ротация при утечке рабочей копии не предусмотрена.
5. Deploy по SSH тянет `:latest` теги вместо pinned `${{ github.sha }}` — невозможен воспроизводимый откат, plus race между новым `git pull` и старым `pull` образов.
6. Нет rate-limit на уровне nginx — голый reverse-proxy без limit_req даёт неограниченные RPS к Gateway (см. сквозную проблему 07 P1).
7. Mini-app в `deploy.yml` пушится без `:${{ github.sha }}` тега — несимметрично.
8. Frontend-nginx контейнеры (pwa/web-panel/landing/mini-app) не имеют `healthcheck` и не указаны в `depends_on` корневого nginx — если pwa-nginx упал, главный nginx продолжает слать 502 без алерта.
9. Монтирование `/var/run/docker.sock` в промтейле + `privileged: true` в cadvisor — стандартный для observability паттерн, но это **полный root на хост** для пары контейнеров.
10. `GF_SECURITY_ADMIN_PASSWORD` в `grafana.ini` подтягивается переменной окружения, но `grafana.ini` bind-mounted read-only без interpolation — при первом запуске подставится `${GF_SECURITY_ADMIN_PASSWORD}` литерально (Grafana с 8.х-версии поддерживает `${VAR}` в `.ini`, так что, скорее всего, работает; уточнить).

**Счётчики**: P0=4, P1=11, P2=13, P3=8.

## Структура модуля

```
docker-compose.yml               ← dev (infra + notifications + 4 статических фронта, ports: 4200/3000/8081/80)
docker-compose.prod.yml          ← prod (полный стек: 5 сервисов + bot + gateway + reverse-proxy + monitoring + frontends)
.dockerignore                    ← исключает .env, *.key, *.pem, .git, docs/, .planning/
.env                             ← dev-пароли (rct_dev_pass)
.env.prod                        ← РЕАЛЬНЫЕ prod-секреты (гитигнорирован, но лежит в рабочей копии)
.gitignore                       ← .env, .env.prod в исключениях

nginx/
├── nginx.conf                   ← worker_processes auto, server_tokens off, client_max_body_size 12m
├── conf.d/
│   ├── default.conf             ← HTTPS-конфиг: TLS1.2/1.3, HSTS, CSP, swagger/grafana behind basic_auth
│   └── http-only.conf           ← bootstrap для первичной выдачи сертификата
├── scripts/init-letsencrypt.sh  ← первичная выдача LE-сертификата через certbot sidecar
└── (dhparam.pem)                ← генерируется на VPS, не в репо

.github/workflows/
├── ci.yml                       ← Java (6 сервисов × check) + Python (ruff + pytest) + frontend (3 × npm test+build)
└── deploy.yml                   ← push на main: собирает 11 образов в GHCR → SSH deploy на VPS

infra/
├── grafana/grafana.ini          ← root_url = https://ruttrack.site/grafana/, admin_user=admin, telegram alerting
├── grafana/provisioning/        ← 4 dashboard JSON + prometheus + loki datasources
├── loki/loki.yml                ← tsdb, retention 168h (7 дней), filesystem storage
├── promtail/promtail.yml        ← docker_sd_configs через /var/run/docker.sock, push → loki:3100
├── prometheus/prometheus.yml    ← node-exporter + cadvisor + 5 Spring-сервисов /actuator/prometheus
└── mongo/init-mongo.js          ← создаёт app-user для notification_db + attendance_db

services/*/Dockerfile            ← 7 штук (6 Java + 1 Python)
frontends/*/Dockerfile           ← 4 штуки (pwa/web-panel/mini-app/landing)
scripts/verify-gateway-e2e.sh    ← ручной smoke-тест Gateway (curl'ы), не в CI
```

## Критичные проблемы (P0)

### P0-1: 🔧 TO-FIX через rename cert-name — `init-letsencrypt.sh` и `default.conf` расходятся в пути к сертификату
**Статус (2026-04-18):** будет закрыто фиксом C0-10 — переименовать cert-name на `ruttrack.site` + `--force-renewal`. См. `OWNER-ANSWERS.md` 02-Q-le-cert.



- **Где:**
  - `nginx/scripts/init-letsencrypt.sh:64,86` — `--cert-name rutcampustrack`
  - `nginx/conf.d/default.conf:24-25` — `ssl_certificate /etc/letsencrypt/live/ruttrack.site/fullchain.pem;`
- **Что:** certbot при флаге `--cert-name rutcampustrack` складывает сертификат в `/etc/letsencrypt/live/rutcampustrack/`, а не `/live/ruttrack.site/`. После первичной выдачи сертификата по инструкции `init-letsencrypt.sh` nginx пытается подхватить несуществующий файл.
- **Дополнительно:** строка 23 скрипта — `DOMAIN="${DOMAIN:?Set DOMAIN in .env.prod (e.g. rutcampustrack.ru)}"`. Hint указывает на старый домен (`rutcampustrack.ru`), но это только сообщение в ошибке — значение подставляется из `$DOMAIN`.
- **Риск:** nginx не стартует после первого запуска скрипта — фикс сертификата руками, либо копирование `/live/rutcampustrack/ → /live/ruttrack.site/` (ломает auto-renew certbot).
- **Как чинить:**
  - Убрать `--cert-name rutcampustrack` (по умолчанию certbot возьмёт cert-name = первый `-d`-домен, т.е. `ruttrack.site`). Или
  - Изменить `default.conf:24-25` на `/etc/letsencrypt/live/rutcampustrack/...` и оставить cert-name, но тогда обновить hint на 23-й строке.
  - В любом варианте — сначала протестировать renew (`certbot renew --dry-run`).
- **Зависимости:** certbot sidecar в `docker-compose.prod.yml:482-491` — renew вызывается в loop, путь renew'а определяется самим certbot'ом, так что «рабочий» сертификат живёт, где его поставил первичный выпуск. Это живой сейчас артефакт в `certbot-conf` volume — локальный запуск воспроизвести нельзя без wipe volume.

### P0-2: 🔧 TO-FIX через branch protection — CI и deploy не связаны — красный CI не блокирует выкат в прод
**Статус (2026-04-18):** будет закрыто фиксом C0-8 — branch protection + required status checks (без required reviews) + `workflow_run` trigger в `deploy.yml`. См. `OWNER-ANSWERS.md` 02-Q-ci-deploy-gate.



- **Где:** `.github/workflows/ci.yml:3-7` (`on: push/pull_request: branches: ['**']`), `.github/workflows/deploy.yml:3-5` (`on: push: branches: [main]`).
- **Что:** оба workflow запускаются независимо от одного и того же события (push в main). В `deploy.yml` **нет** `needs:` на `ci.yml`; билды `deploy.yml:build-push` и `deploy.yml:deploy` стартуют параллельно с `ci.yml`.
- **Риск:** коммит с падающими тестами (Java/Python/Frontend) попадает на VPS. Prod может уйти в состояние, в котором не собирается в CI.
- **Как чинить:**
  - Объединить — пусть `deploy.yml` триггерится `workflow_run` после успешного `ci.yml`, либо переместить build-push в тот же `ci.yml` под условие `if: github.ref == 'refs/heads/main' && success()`.
  - Альтернатива: включить branch protection на main с обязательными CI-проверками (достаточно дёшево и не требует правок workflow).
- **Зависимости:** после фикса можно дополнительно поднять smoke-тест `/login` (deploy.yml:215-222) в блокирующий gate — сейчас он выполняется, но через `appleboy/ssh-action`.

### P0-3: ✅ ACCEPTED (частично) + 🔧 .env.prod.example — `.env.prod` с реальными prod-секретами лежит в рабочей копии
**Статус (2026-04-18):** Файл никогда не попадал в git (подтверждено владельцем). Загружается напрямую на VPS. **Ротация секретов отклонена** (нет признаков утечки). **`.env.prod.example` будет создан** в репо для документации/читаемости (видно какие переменные используются). Risk-сценарии (скриншот/бекап/compromised dev machine) приняты владельцем. См. `OWNER-ANSWERS.md` 02-Q-secrets-rotation.



- **Где:** `/.env.prod:10-37` (в `.gitignore`, но на диске).
- **Что:** файл содержит:
  - `BOT_TOKEN=8744653460:AAF-...` — живой Telegram Bot токен (тот же, что `TMA_BOT_TOKEN`)
  - `GHCR_TOKEN=ghp_Sg0ps...` — Personal Access Token с правами на `packages: write` (тот же, что в GitHub Secrets)
  - `VAPID_PRIVATE_KEY=jdsKNZs...` — приватный ключ Web Push
  - `BOT_ALERT_TOKEN=7985631653:AAFpd3Z...` — Grafana alert bot
  - `SWAGGER_PASSWORD`, `MONGO_ROOT_PASSWORD`, `GRAFANA_PASSWORD`, `GRPC_SECRET`, все DB-пароли
- **Риск:** файл гитигнорирован, но:
  1. Любой backup/скриншот/шаринг рабочей копии утекает в публику.
  2. Claude/любой инструмент, анализирующий репо, видит все секреты (сейчас — мы).
  3. `cleaner/prune` на рабочей машине может потерять файл — нет шаблона `.env.prod.example` в репо (был удалён в 2185bec).
  4. BOT_TOKEN в файле совпадает для TMA и обычного; если токен скомпрометирован — отвалится и Mini App auth.
- **Как чинить:**
  - Создать `.env.prod.example` с заглушками-шаблонами (одновременно вернуть `.env.example` для dev).
  - Ротировать все секреты из файла: Telegram BotFather (`/revoke` + `/newtoken`), GHCR PAT (пересоздать), VAPID (`npx web-push generate-vapid-keys` → обновить на клиенте), DB-пароли (через `ALTER ROLE` и deploy), GRAFANA_PASSWORD.
  - Хранить `.env.prod` на VPS (уже есть — `/opt/rutcampustrack/.env.prod`), а в рабочей копии оставить только `.env.prod.example`.
- **Зависимости:** 08-shared — proto-канал `initial_password`. Ротация BOT_TOKEN = рестарт `notification-bot` + `auth-service` (TMA-валидация).

### P0-4: 🔧 TO-FIX через self-host лендинга — CSP корневого nginx блокирует внешние CDN лендинга
**Статус (2026-04-18):** будет закрыто фиксом C0-6 — лендинг переходит на self-hosted assets, CSP не меняется. См. `OWNER-ANSWERS.md` 02-Q-csp-landing.



- **Где:** `nginx/conf.d/default.conf:40`
  ```
  Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-hashes' 'sha256-MhtPZXr7+...'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' wss:; font-src 'self';
  ```
- **Что:** лендинг (`/presentation/`) тянет:
  - `api.fontshare.com/v2/...` (Fontshare inline style) — блокируется `style-src 'self' 'unsafe-inline'`, ну хотя inline-style проходит через 'unsafe-inline'. **Внешние stylesheet'ы блокируются.**
  - `fonts.googleapis.com/css2?family=...` — блокируется.
  - `fonts.gstatic.com/...` — блокируется `font-src 'self'`.
  - `unpkg.com/gsap@3/...` или `cdn.jsdelivr.net/...` — блокируется `script-src 'self' ...`.
- **Риск:** лендинг в проде визуально ломается (отсутствуют шрифты, GSAP-анимации не запускаются). Подтверждено отчётом 12 P0-1.
- **Как чинить:** два варианта:
  1. **Self-host**: склонировать все CDN-ресурсы в `frontends/landing/dist/assets/vendor/`, переписать `<link>/<script>` на локальные пути. Тогда текущий CSP `'self'` работает без правок.
  2. **Whitelist**: расширить CSP:
     ```
     script-src 'self' 'unsafe-hashes' 'sha256-...' https://unpkg.com https://cdn.jsdelivr.net;
     style-src 'self' 'unsafe-inline' https://api.fontshare.com https://fonts.googleapis.com;
     font-src 'self' https://api.fontshare.com https://fonts.gstatic.com;
     connect-src 'self' wss: https://api.fontshare.com;
     img-src 'self' data: https://*.fontshare.com;
     ```
     и обязательно — SRI на всех внешних `<script integrity="sha384-...">`.
- **Зависимости:** фикс в одном месте — `nginx/conf.d/default.conf` — чинит все фронты сразу. Внутренние nginx фронтов (pwa/web-panel/landing/mini-app) свои CSP не ставят, так что конфликтов при `proxy_pass` нет.
- **Тонкий момент:** CSP ставится с `always` — применится даже при 4xx/5xx ответах `proxy_pass`. nginx **не** переписывает CSP upstream'а, если тот уже поставил свою политику, но внутренние nginx её не ставят — значит, корневая пробьётся всегда.

## Серьёзные проблемы (P1)

### P1-1: Deploy тянет `:latest` вместо pinned `:${{ github.sha }}`

- **Где:** `docker-compose.prod.yml:114` (и ещё 10 сервисов), `.github/workflows/deploy.yml:166` — `docker compose ... pull` подхватывает `:latest`.
- **Что:** в prod-compose прописано `image: ghcr.io/ditekmax/rutcampustrack/*-service:latest`. `deploy.yml` билдит два тега (`:${sha}` + `:latest`) и пушит, а затем делает `compose pull` на latest.
- **Риск:**
  - Невозможен воспроизводимый откат: `git revert` + redeploy всё равно подтянет `:latest`, который к тому моменту мог сдвинуться.
  - Race: если второй деплой успевает `push latest` до того как первый закончил `compose up`, — у второго деплоя оказываются смешанные версии.
  - Горячий откат = ручной `docker pull ghcr.io/.../service:${oldSha} && docker tag ... :latest && docker compose up -d`.
- **Как чинить:**
  - Параметризовать `docker-compose.prod.yml` на `${IMAGE_TAG}` (`image: ghcr.io/.../*:${IMAGE_TAG:-latest}`), передавать `IMAGE_TAG=${{ github.sha }}` в SSH-скрипт через env.
  - Или — хранить SHA в `/opt/rutcampustrack/.deployed-sha` и использовать в `docker compose pull`.

### P1-2: `deploy.yml` делает `docker compose up -d` дважды подряд

- **Где:** `.github/workflows/deploy.yml:167-169`:
  ```
  docker compose ... up -d --remove-orphans
  sleep 30
  docker compose ... up -d --remove-orphans
  ```
- **Что:** второй `up -d` повторяется после 30 сек сна без объяснения в комментарии. По смыслу — попытка «устаканить» сервисы, у которых `depends_on: condition: service_healthy` не сразу становится доступным.
- **Риск:** маскирует настоящую проблему порядка startup'а. `depends_on: service_healthy` должен сам дождаться — если не дожидается, значит у какого-то сервиса healthcheck не видит готовность.
- **Как чинить:** убрать второй `up -d`, разобраться, почему первый не добивает. Если причина — `auth-service` долго генерит JWT-ключи при первом старте, увеличить `start_period` и/или вынести генерацию в init-job.

### P1-3: Нет rate-limit на уровне корневого nginx

- **Где:** `nginx/nginx.conf`, `nginx/conf.d/default.conf` — нет `limit_req_zone`, `limit_req`, `limit_conn`.
- **Что:** корневой прокси даёт неограниченный RPS ко всем `/api/*`, включая `/api/auth/otp/verify` (отчёт 07 P1 — rate-limit отсутствует и в Spring-фильтре, и в nginx).
- **Риск:** брутфорс OTP-кода занимает ~3 часа при 10⁴ кодов и ~100 RPS. Также — DoS одного сервиса валит Gateway, потому что Gateway ограничен HikariCP/Netty thread pool'ами.
- **Как чинить:**
  ```
  http {
    limit_req_zone $binary_remote_addr zone=api_common:10m rate=30r/s;
    limit_req_zone $binary_remote_addr zone=api_otp:10m    rate=5r/m;
  }
  location /api/auth/otp/ { limit_req zone=api_otp burst=3 nodelay; proxy_pass ...; }
  location /api/           { limit_req zone=api_common burst=50 nodelay; proxy_pass ...; }
  ```

### P1-4: Mini-app в `deploy.yml` без SHA-тега

- **Где:** `.github/workflows/deploy.yml:113` — `tags: ghcr.io/.../mini-app-nginx:latest` (нет `:${{ github.sha }}`).
- **Что:** для всех остальных 10 образов в deploy.yml список тегов — `:${{ github.sha }},*:latest`. Только mini-app выпадает из паттерна.
- **Риск:** невозможно восстановить конкретную версию mini-app через GHCR, рэджистр потеряет старый образ при retention-политике.
- **Как чинить:** добавить `:${{ github.sha }}` к тегам mini-app.

### P1-5: Reverse-proxy nginx sleeps 5m → reload в фоне

- **Где:** `docker-compose.prod.yml:478-480` — `command: "/bin/sh -c 'while :; do sleep 5m; nginx -s reload; done & nginx -g \"daemon off;\"'"`
- **Что:** внутри контейнера запущен вечный цикл, каждые 5 минут делающий `nginx -s reload`. Комментарий выше говорит: safety net для случаев, когда `deploy.yml`-шаг `nginx -s reload` не прошёл.
- **Риск:**
  - Любой файл в `/etc/letsencrypt/`, который сертбот обновил, подхватывается с задержкой до 5 мин — OK.
  - Но каждый reload завершает keepalive-соединения и приводит к sawtooth-графику latency. 5 минут — короткий период для reload'а; обычно 1–24h.
  - Если кто-то положит в `nginx/conf.d/*.disabled` кривой файл, а `include /etc/nginx/conf.d/*.conf` это не подхватит (расширение `.disabled`), то reload не упадёт — но это только случайно работает.
- **Как чинить:** увеличить период до `sleep 1h`, либо заменить на `inotifywait` на каталог `/etc/letsencrypt/live`. Подумать, не проще ли делегировать certbot-renew `deploy-hook` или ручной Slack-алерт.

### P1-6: cadvisor с `privileged: true` и Docker socket в promtail

- **Где:**
  - `docker-compose.prod.yml:388` — `privileged: true` для cadvisor;
  - `docker-compose.prod.yml:447-448` — `/var/run/docker.sock:/var/run/docker.sock:ro` в promtail.
- **Что:** стандартные для observability паттерны, но оба дают доступ к докер-демону (ro в одном случае, rw в другом). Компрометация любого из двух контейнеров = root на хост.
- **Риск:** supply-chain атака на образы `grafana/promtail:latest` или `gcr.io/cadvisor/cadvisor:latest` (оба закреплены на `latest`!) — полный контроль над хостом.
- **Как чинить:**
  - Закрепить образы по sha256 digest: `image: grafana/promtail@sha256:...`.
  - cadvisor: рассмотреть замену на `docker exec` в prometheus node-exporter или urovni hostmetrics в OTel.
  - В крайнем случае: хотя бы `:ro` на `/var/run/docker.sock` в промтейле уже стоит — это минимальная защита.

### P1-7: `docker-compose.yml` и `docker-compose.prod.yml` — независимые копии

- **Где:** `docker-compose.yml` vs `docker-compose.prod.yml`.
- **Что:** нет `.override.yml`-паттерна. prod-compose заново перечисляет все сервисы (базовые + приложения + monitoring + frontends + nginx). Общие куски (`postgres-*`, `mongo-attendance`, `redis`, `rabbitmq`, `notification-web`, `notification-bot`, `*-nginx` для фронтов) продублированы.
- **Риск:**
  - Дрейф: в dev добавили `TZ: Europe/Moscow` для postgres-schedule, в prod — тоже, но для postgres-academic забыли.
  - Новые env-переменные в сервисах легко забываются в одном из файлов.
  - В dev-compose отсутствует `SPRING_PROFILES_ACTIVE`, в prod `SPRING_PROFILES_ACTIVE: prod` — есть (один раз не выставим в prod → default-профиль с DEBUG).
- **Как чинить:** принять паттерн `docker-compose.yml` (базовый, нейтральный) + `docker-compose.override.yml` (dev, автоматически подтягивается) + `docker-compose.prod.yml` (production overrides с `-f` флагом). Либо `docker-compose.prod.yml` с `extends`-секциями, если хочется явности.

### P1-8: Фронт-nginx (pwa/web-panel/landing/mini-app) без healthcheck и `depends_on`

- **Где:** `docker-compose.prod.yml:497-543`.
- **Что:** все четыре фронт-контейнера объявляют только `build` + `image` + `expose: 80` + сеть + `restart: unless-stopped`. Без healthcheck'а reverse-proxy `rct-nginx` (в `depends_on:api-gateway: service_healthy`) не ждёт готовности фронтов, а получается, что фронт-контейнеры могут не запуститься, а основной nginx уже стартует.
- **Риск:**
  - Во время первого деплоя после `git pull` — есть окно, когда `/app/` отдаёт 502 (pwa-nginx ещё не успел).
  - Frontend-crash (например, синтаксическая ошибка в кастомной nginx.conf) не детектируется, а `restart: unless-stopped` попытается поднять бесконечно.
- **Как чинить:**
  - Добавить `healthcheck: test: ["CMD-SHELL", "wget -qO- http://localhost/ || exit 1"]`.
  - В `rct-nginx.depends_on` добавить все четыре фронта с `condition: service_healthy`.

### P1-9: `certbot` sidecar запускает renew каждые 12h, но не умеет reload nginx

- **Где:** `docker-compose.prod.yml:490`:
  ```
  entrypoint: "/bin/sh -c 'trap exit TERM; while :; do certbot renew --quiet; sleep 12h; done'"
  ```
- **Что:** после успешного renew сертификата certbot не перезагружает nginx. Реальный reload делает либо `sleep 5m; nginx -s reload` loop (P1-5), либо следующий `deploy.yml` push на main.
- **Риск:** сертификат обновлён, но nginx держит старый в памяти до 5 минут — в редком случае это 300 секунд HTTPS-сбоев.
- **Как чинить:** добавить `--deploy-hook "docker exec rct-nginx nginx -s reload"` в certbot команду. Либо, как сейчас, положиться на 5-минутный loop (P1-5).

### P1-10: Публичные Docker-образы (base images) закреплены только по тегу

- **Где:**
  - `services/*/Dockerfile`: `FROM eclipse-temurin:21-jdk-alpine`, `FROM eclipse-temurin:21-jre-alpine`, `FROM eclipse-temurin:21-jdk-jammy`
  - `services/notification-bot/Dockerfile:1`: `FROM python:3.12-slim`
  - `frontends/*/Dockerfile`: `FROM node:22-alpine`, `FROM nginx:1.27-alpine`
  - `docker-compose.prod.yml`: `postgres:16`, `mongo:7`, `redis:7-alpine`, `rabbitmq:3.13-alpine`, `grafana/loki:latest`, `prom/prometheus:latest`, `prom/node-exporter:latest`, `gcr.io/cadvisor/cadvisor:latest`, `grafana/grafana:latest`, `grafana/promtail:latest`, `certbot/certbot`
- **Что:** ни один `FROM` не пинится по `@sha256:...` digest. `grafana/*` и все промежуточные observability-образы закреплены на `:latest`.
- **Риск:**
  - Supply-chain: публикатор grafana/loki сможет подменить образ при следующем `docker pull`.
  - Непредсказуемость: одна и та же `docker-compose.prod.yml` может развернуть разные minor-версии.
- **Как чинить:**
  - Renovate/Dependabot для `docker-compose.prod.yml` и Dockerfile'ов.
  - Как минимум зафиксировать observability-образы на конкретные теги: `grafana/loki:3.2.0`, `prom/prometheus:v2.55.0` и т.д.
  - Для критичных (postgres, redis, rabbit) — рассмотреть `@sha256:` digest.

### P1-11: CI триггерится на `branches: ['**']` — билдится на всех feature-ветках без фильтра

- **Где:** `.github/workflows/ci.yml:4-7`.
- **Что:** `push` и `pull_request` на `'**'` запускают полный CI на каждой ветке. Нет фильтра по path (то есть изменение только `docs/` полностью прогоняет Java+Python+Frontend).
- **Риск:** минуты GitHub Actions уходят в никуда. Не P0, но — выматывание лимитов.
- **Как чинить:**
  - `paths-ignore: ['docs/**', '.planning/**', '*.md']`.
  - Отдельно — изменение только в `frontends/pwa/` не должно перестраивать `services/*`. Но matrix-джобы уже независимы, это чинится только path filter'ом.

## Средние (P2)

### P2-1: Два Telegram-бот токена (BOT_TOKEN и TMA_BOT_TOKEN) — одно значение

- **Где:** `.env.prod:10-11` — оба `8744653460:AAF-guQdXoMB...`.
- **Что:** `BOT_TOKEN` используется ботом (notification-bot, polling), `TMA_BOT_TOKEN` — auth-service для валидации Mini App initData. Предполагается, что это разные токены.
- **Риск:** путаница при ротации — если Mini App пользователь начнёт присылать initData от другого бота, а локальные пароли совпадают, разница не детектируется.
- **Как чинить:** либо документировать, что для MVP это один и тот же бот (=> удалить TMA_BOT_TOKEN из .env.prod, оставить только BOT_TOKEN), либо завести отдельный Mini App бот и положить его токен.

### P2-2: Prometheus/Grafana/Loki — на `:latest`

- **Где:** `docker-compose.prod.yml:392,411,429,442,362,375`.
- **Что:** см. P1-10; наиболее неустойчивая часть — observability. Каждое `docker compose pull` может подтянуть несовместимую major-версию (например, Loki v3 → storage schema incompat).
- **Как чинить:** указать версии явно, пройтись по CHANGELOG при обновлении.

### P2-3: nginx `client_max_body_size 12m` — глобально

- **Где:** `nginx/nginx.conf:20`.
- **Что:** 12 МБ на все маршруты, включая `/api/auth/login` (там достаточно <1 KB). Для excuse-files этот лимит может быть тесен: студент прикладывает несколько PDF — размер суммарно >10 MB.
- **Риск:**
  - Атака «большие JSON» на публичные эндпоинты.
  - Недостаточно для больших pdf'ок.
- **Как чинить:** `location`-level `client_max_body_size`:
  - `/api/excuse/` → 25m (attachments)
  - всё остальное `/api/*` → 2m.

### P2-4: В CI нет coverage-агрегации

- **Где:** `.github/workflows/ci.yml` — нет JaCoCo report, нет Istanbul.
- **Что:** `./gradlew check` запускает тесты, но не публикует coverage. Нет gate «coverage ≥ 60%».
- **Как чинить:** добавить jacoco plugin в root `build.gradle.kts` (уже может быть, проверить), собирать `jacocoTestReport` и публиковать в PR через `madrapps/jacoco-report@v1`.

### P2-5: `.env.prod` не содержит `DOMAIN` и `CERTBOT_EMAIL`

- **Где:** `.env.prod` — нет `DOMAIN=ruttrack.site` и `CERTBOT_EMAIL=...`.
- **Что:** `init-letsencrypt.sh:18-24` требует обе переменные. На VPS они, видимо, есть, но локально при попытке протестировать скрипт — будет ошибка.
- **Как чинить:** добавить в `.env.prod`:
  ```
  DOMAIN=ruttrack.site
  CERTBOT_EMAIL=maks.ditkovskiy.05@mail.ru
  ```

### P2-6: `.env` (dev) — пароли все одинаковые `rct_dev_pass`

- **Где:** `.env:4-10`.
- **Что:** общий пароль для Postgres × 2, Mongo, Redis, RabbitMQ. Тестирование RBAC невозможно — у всех одни и те же creds.
- **Как чинить:** развести в dev разные значения (хоть `rct_pg_pass`, `rct_redis_pass` и т.д.), чтобы ошибка в `SPRING_DATASOURCE_URL` сразу вылетала.

### P2-7: Gateway actuator с `show-details: always`

- **Где:** `services/api-gateway/src/main/resources/application.yml:134-136`:
  ```
  endpoint.health.show-details: always
  endpoint.prometheus.access: unrestricted
  ```
- **Что:** если reverse-proxy когда-нибудь проксирует `/actuator/*` наружу (в nginx сейчас только `/api/*` проксируется), — в `/health` утекут все downstream-healthcheck'и с именами сервисов, версиями БД, именами коллекций/схем.
- **Риск:** сейчас не доступно снаружи, но — ошибка в конфиге (например, добавить `location /health { proxy_pass http://rct-api-gateway:8080; }`) сразу открывает.
- **Как чинить:** `show-details: when_authorized`, и роль `management` в Spring Security. Или `show-details: never` в prod-профиле.

### P2-8: `/actuator/prometheus` не аутентифицирован

- **Где:** `infra/prometheus/prometheus.yml:14-37` — scrape_configs без `basic_auth`/`bearer_token`.
- **Что:** Prometheus скрейпит `auth-service:9090/actuator/prometheus` без авторизации. Любой контейнер в `private_net` может читать метрики (включая rate OTP-ошибок, rate login-попыток — по ним можно судить о brute force).
- **Риск:** минорный — private_net, чужих контейнеров там нет. Но supply-chain (скомпрометированный `cadvisor` или `promtail`) получит метрики.
- **Как чинить:** `management.endpoints.web.exposure.include` оставить только `health,info`, а `prometheus` вынести на `management.server.port: 9099` с Spring Security basic-auth.

### P2-9: Loki retention 168h (7 дней)

- **Где:** `infra/loki/loki.yml:40-41`.
- **Что:** 7 дней логов — недостаточно для разбора инцидента, если отчёт о баге приходит через 2 недели.
- **Как чинить:** поднять до 30–45 дней (`retention_period: 720h`). Хранилище — filesystem на VPS; следить за размером.

### P2-10: `infra/loki/loki.yml:36-37` — dangling alertmanager_url

- **Где:** `ruler.alertmanager_url: http://localhost:9093`.
- **Что:** alertmanager на 9093 не запущен; этот порт вообще занят attendance-service (но это другой контейнер).
- **Риск:** loki-rules не доставятся никуда. Проявится только при первом rule'е.
- **Как чинить:** удалить `ruler` секцию, либо запустить alertmanager.

### P2-11: `infra/mongo/init-mongo.js` создаёт пользователя для `notification_db`

- **Где:** `infra/mongo/init-mongo.js:9-10`.
- **Что:** создаётся роль `readWrite` на `notification_db`, хотя notification-service v5.0 хранит push-subscriptions в **attendance_db** (см. отчёт 05 P1-2). Namespace `notification_db` не используется.
- **Риск:** мёртвая роль.
- **Как чинить:** убрать пару строк с `notification_db` (или, наоборот, мигрировать push-subs в отдельную БД; выбор остаётся отчёту 05).

### P2-12: `POSTGRES_ACADEMIC_PASSWORD` используется и для schedule в некоторых контейнерах

- **Где:** `docker-compose.prod.yml:154-159` — academic-service получает `POSTGRES_ACADEMIC_PASSWORD`; `docker-compose.prod.yml:186-190` — schedule-service получает только `POSTGRES_SCHEDULE_PASSWORD`. Auth-service (`docker-compose.prod.yml:118`) получает `POSTGRES_ACADEMIC_PASSWORD`.
- **Что:** auth-service подключается к той же `postgres-academic` БД (см. отчёт 01 P0-1 — общая БД с academic). Это корректное следствие решения «общей БД», но только запутывает: три сервиса читают одну переменную.
- **Риск:** ротация `POSTGRES_ACADEMIC_PASSWORD` требует рестарта трёх сервисов одновременно.
- **Как чинить:** либо документировать в `.env.prod.example`, либо (лучше) развести auth и academic по разным БД — тогда пароли разные.

### P2-13: В deploy.yml нет image signing / SBOM

- **Где:** `.github/workflows/deploy.yml`.
- **Что:** `docker/build-push-action@v7` умеет `provenance: true` и cosign-signing, но они не настроены. SBOM не генерируется.
- **Риск:** нет цепочки доверия «кто и что запушил».
- **Как чинить:** добавить `provenance: true`, `sbom: true`, cosign key (`sigstore/cosign-installer`) — отнимет ~30 сек, даст подпись.

## Мелкие и nit (P3)

### P3-1: Имена контейнеров конфликтуют между dev и prod

- **Где:** и в dev, и в prod compose: `container_name: rct-*`. При запуске dev на машине, где уже стоит prod (теоретически тот же VPS), `docker compose up -d` в dev упадёт с «Conflict. Name already in use».
- **Как чинить:** в dev убрать `container_name:`, оставить автосгенерированные.

### P3-2: Dockerfile frontend-mini-app, pwa, web-panel — идентичны

- **Где:** `frontends/pwa/Dockerfile`, `frontends/web-panel/Dockerfile`, `frontends/mini-app/Dockerfile`. Web-panel отличается одной строкой (`dist/browser` вместо `dist`).
- **Как чинить:** либо один общий Dockerfile в корне с ARG, либо оставить как есть для ясности.

### P3-3: `docker-compose.yml` содержит `version: "3.9"` — давно deprecated

- **Где:** `docker-compose.yml:1`.
- **Что:** начиная с Docker Compose v2 поле `version:` игнорируется.
- **Как чинить:** удалить строку. (`docker-compose.prod.yml` уже без `version:`.)

### P3-4: pg-academic postgres-schedule без `TZ: Europe/Moscow` в dev

- **Где:** `docker-compose.yml:8-25` — postgres-academic без TZ; `docker-compose.yml:27-45` — postgres-schedule с TZ. В prod — у обоих postgres TZ одинаковое (только у schedule).
- **Как чинить:** выставить TZ обоим, для симметрии.

### P3-5: `GHCR_TOKEN` — классический PAT, не GITHUB_TOKEN

- **Где:** `.github/workflows/deploy.yml:25` — `${{ secrets.GHCR_TOKEN }}`.
- **Что:** для пуша в `ghcr.io/OWNER/...` достаточно `${{ secrets.GITHUB_TOKEN }}` с `permissions.packages: write`. Использовать персональный PAT — анти-паттерн (этот токен висит бессрочным; если утечёт — доступ к пуш любых пакетов в organization).
- **Как чинить:** заменить на `${{ secrets.GITHUB_TOKEN }}`, добавить `packages: write` в permissions (уже есть: строка 13).

### P3-6: nginx `log_format main` — без `$request_time`

- **Где:** `nginx/nginx.conf:13-15`.
- **Что:** access лог не содержит latency — сложнее отлавливать slow endpoints в Loki.
- **Как чинить:** добавить `$request_time $upstream_response_time` в log_format.

### P3-7: `verify-gateway-e2e.sh` не используется в CI

- **Где:** `scripts/verify-gateway-e2e.sh`.
- **Что:** ручной smoke-тест, проверяет auth-flow через Gateway. Не запускается в CI, не запускается в deploy'е.
- **Как чинить:** либо превратить в `docker compose -f docker-compose.yml up -d && ./scripts/verify-gateway-e2e.sh` в отдельном job'е CI, либо удалить (дублирует логику contract-тестов).

### P3-8: `docker-compose.yml` frontend-контейнеры монтируют `./frontends/*/dist:/usr/share/nginx/html:ro`

- **Где:** `docker-compose.yml:187,203,219,235`.
- **Что:** dev-compose в отличие от prod не собирает образы, а bind-mount'ит локальный `dist/`. Это значит, перед `docker compose up -d` надо локально выполнить `npm run build` в каждом фронте.
- **Риск:** dev-загрузка сломана пока фронт не собран. Скрипта для этого нет.
- **Как чинить:** добавить `frontends:setup` таргет в корневом Gradle (вызывающий npm build'ы), или `Makefile`, или хотя бы README-шаг.

## Мёртвый код

- `nginx/conf.d/http-only.conf` — используется только при первичной выдаче LE-сертификата. Логика переключения `default.conf ↔ default.conf.disabled` вручную в `init-letsencrypt.sh:42-49,91-92`. Файл живёт в репо, но реально нужен только единожды на VPS.
- `infra/mongo/init-mongo.js:9-10` — роль `notification_db` не используется ни в одном сервисе (P2-11).
- `scripts/verify-gateway-e2e.sh` — не подключен к CI (P3-7).

## Костыли и TODO/FIXME

- `docker-compose.prod.yml:477-480`:
  ```
  # Background loop reloads nginx every 5 minutes as a safety net for cases
  # when deploy.yml's explicit `nginx -s reload` step is skipped or fails.
  # Primary reload path is GitHub Actions deploy.yml after git pull.
  command: "/bin/sh -c 'while :; do sleep 5m; nginx -s reload; done & nginx -g \"daemon off;\"'"
  ```
  Автор комментирует костыль честно — это не TODO, а конструкция на случай сбоев деплоя.
- `.github/workflows/deploy.yml:156` — `git pull --ff-only` на VPS вперёд `docker compose pull`. Если в репо протянули мердж-коммит (ff-not-possible), скрипт встанет — нужно либо `pull --rebase` + `--autostash`, либо отдельный VPS-user без рабочих правок.
- `.github/workflows/deploy.yml:168-169` — дублирующий `up -d` через `sleep 30` (см. P1-2).
- `nginx/scripts/init-letsencrypt.sh:23` — hint `"(e.g. rutcampustrack.ru)"` — старый домен в подсказке.
- `nginx/conf.d/default.conf:22-23` — комментарий `# Requires SSL certificate at /etc/letsencrypt/live/ruttrack.site/` и `--cert-name rutcampustrack` в скрипте — рассинхрон (см. P0-1).

## Тесты (деплой)

Единственный релевантный тест — `.github/workflows/deploy.yml:215-222`: smoke-тест `/login`:
```bash
LOGIN_SIZE=$(curl -s -o /dev/null -w "%{size_download}" https://ruttrack.site/login)
if [ "$LOGIN_SIZE" -lt 5000 ]; then
  echo "FATAL: /login returned $LOGIN_SIZE bytes..."
  exit 1
fi
```
Полезно — поймает кейс из Phase 50, когда catch-all `location /` мисруутил на mini-app. Но:
- Проверяет только один путь, не проверяет `/api/auth/login`, `/app/`, `/presentation/`, `/api/ws/`.
- Критерий — размер страницы, а не контент. Если web-panel превратится в 4.5 KB-страницу (минификация чуть изменится) — упадёт ложно.
- Не проверяет валидность TLS-сертификата: `curl -s` не проверяет subject.

**Предложение:** заменить на многошаговый smoke:
```bash
curl -sS --fail https://ruttrack.site/login | grep -q 'rutcampustrack'
curl -sS --fail https://ruttrack.site/app/ | grep -q 'rutcampustrack'
curl -sS --fail https://ruttrack.site/api/auth/publicKey | grep -q 'BEGIN PUBLIC KEY'
```

`scripts/verify-gateway-e2e.sh` — не в CI (P3-7).

## Соответствие CLAUDE.md

| Правило из CLAUDE.md                                                | Статус | Комментарий |
|---------------------------------------------------------------------|:------:|-------------|
| Docker Compose запуск `docker compose up -d` для инфры              |   ✅   | Описано в CLAUDE.md, воспроизводимо |
| `$env:JAVA_HOME = "C:\Users\maksd\.jdks\ms-21.0.9"; .\gradlew.bat build` |   ⚠    | В CI стоит `temurin` 21, отличается от локального ms-21.0.9 — возможны JVM-специфичные баги |
| Роли и порты из CLAUDE.md                                           |   ✅   | gateway 8080, auth 9090, academic 9091, schedule 9092, attendance 9093, notification 9094 — соответствуют |
| PostgreSQL × 2 + MongoDB + Redis + RabbitMQ                         |   ✅   | Все запускаются |
| JWT keys — монтируются через `jwt-keys` named volume                |   ✅   | В compose есть, в deploy.yml инициализируется через alpine+openssl |
| URL Layout (см. CLAUDE.md раздел «URL Layout v9.0»)                 |   ⚠    | `/`, `/login`, `/admin/`, `/teacher/`, `/student/`, `/headman/`, `/app/`, `/presentation/`, `/mini-app/`, `/api/*` — все маршруты есть в default.conf; но `/mini-app/` отсутствует в документации `docs/url-layout.md` (или наоборот) — проверить синхронизацию |

## Зависимости между проблемами

- **P0-1 (LE cert-name mismatch)** — блокирует любой редеплой с нуля. До фикса — рабочая конфигурация держится на единожды выпущенном сертификате в volume.
- **P0-2 (CI↔deploy decoupling)** — не блокирует релиз, но увеличивает вероятность мёрджа плохого кода в main. Решается branch protection (5 минут работы).
- **P0-3 (`.env.prod` с секретами)** — требует ротации ВСЕХ секретов. Сцепляется с 08-shared P0-1 (initial_password в proto) — оба меняют контекст секретов, но не зависят друг от друга.
- **P0-4 (CSP блокирует лендинг)** — чинится либо тут (nginx whitelist), либо в 12 (self-host). Любой вариант закрывает обе P0.
- **P1-1 (`:latest` теги)** — зависит от P0-2: если CI блокирует, `deploy.yml` передаёт SHA в `.env.prod`/compose; иначе — решение локальное в compose.
- **P1-3 (no rate-limit в nginx)** — дополняет 07 P1 (нет rate-limit в Gateway). Логически — или в gateway spring-cloud-gateway `RequestRateLimiterGatewayFilterFactory`, или здесь в nginx. Оба слоя — best practice, но достаточно одного.
- **P1-8 (фронт-nginx без healthcheck)** — сочетается с P1-9 (certbot без reload hook): оба увеличивают время недоступности после редеплоя на 30–300 секунд.
- **P2-7 (actuator show-details) + P2-8 (prometheus unauth)** — единый фикс: вынести actuator на management.server.port с basic-auth.

## Вопросы к владельцу проекта

1. ✅ **init-letsencrypt**: cert-name `rutcampustrack` выбран намеренно (как абстрактное имя для дальнейшей поддержки нескольких доменов) или это legacy из старого домена? Если legacy — надо ли переименовывать existing volume `certbot-conf` (сломается renew) или достаточно переиздать сертификат под `--cert-name ruttrack.site`?
   → **AUTO-RESOLVED через 02-Q-le-cert (2026-04-18)**: **(a)** Переименовать на `ruttrack.site` + `--force-renewal` + обновить script. ~30 мин downtime для https. См. `OWNER-ANSWERS.md` 02-Q-le-cert.
2. ✅ **CSP стратегия для лендинга**: self-host CDN или whitelist? Self-host увеличит bundle, но отвяжет от uptime Fontshare/Google.
   → **AUTO-RESOLVED через 02-Q-csp-landing (2026-04-18)**: **(a) Self-host**. CSP корневого nginx не меняется. См. `OWNER-ANSWERS.md` 02-Q-csp-landing.
3. ✅ **`.env.prod` в рабочей копии**: нужен ли шаблон `.env.prod.example` в репо? (Сейчас нет ни его, ни `.env.example`.)
   → **AUTO-RESOLVED через 02-Q-secrets-rotation (2026-04-18)**: **ДА, создаём** `.env.prod.example` с inline-комментариями для читаемости (видно какие переменные используются). Ротация секретов НЕ делается (файл не утекал по подтверждению владельца). См. `OWNER-ANSWERS.md` 02-Q-secrets-rotation.
4. **Deploy rollback-стратегия**: как планируется откат? Если `:latest` — то через ручное `docker pull :${oldSha} && docker tag`. Не пора ли параметризовать `docker-compose.prod.yml` на `IMAGE_TAG`?
5. ✅ **Rate-limit**: где предполагается — в Gateway (Spring Cloud Gateway Redis-based rate limiter) или в nginx (`limit_req_zone`)? Ответ определяет, куда вкладываться.
   → **AUTO-RESOLVED через 02-Q-rate-limit (2026-04-18)**: выбран **Spring Cloud Gateway + Redis**. Nginx-вариант отклонён. Связанная P1-3 (rate-limiting в nginx) → ❌ ОТКЛОНЁН. См. `OWNER-ANSWERS.md` 02-Q-rate-limit.
6. **BOT_TOKEN vs TMA_BOT_TOKEN**: один бот или два? Если один — убрать TMA_BOT_TOKEN из `.env.prod`.
7. **Observability versioning**: ОК ли фиксировать `grafana/loki` на конкретную major-версию или нужна постоянная `:latest`?
8. **Monitoring retention**: 7 дней Loki — достаточно? (Запросы на продакшне могут показывать проблему неделей позже.)
9. **Swagger basic-auth**: `SWAGGER_PASSWORD=k9wHs9pkEv` в `.env.prod:25`, но в `nginx/conf.d/default.conf:91` используется `auth_basic_user_file /etc/nginx/.htpasswd` — а файла `.htpasswd` в `nginx/` нет в репо. Генерируется на VPS руками? Или sync-скрипт?
10. **Phase 50 smoke-тест** (`LOGIN_SIZE < 5000`) — порог 5000 байт эмпирический; стоит ли ужесточить до проверки `<title>` или характерного маркера в HTML?
11. **Auth-service — общая БД с academic** (см. 01 P0-1): требуется ли отдельный `postgres-auth` контейнер в compose?
12. **`nginx/dhparam.pem` 2048 бит** (`deploy.yml:157`) — 2048 — минимум; стоит 3072 или 4096 для соответствия 2026 NIST?
