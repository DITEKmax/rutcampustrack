# Промпт для следующей сессии — M16 Post-Deploy Hardening (G8 + G9)

Скопируй всё ниже в новый чат с Opus 4.7 (1M context).

---

## Контекст (что было сделано)

**Milestone M16 — Post-Deploy Hardening.** 9 групп, 7 закрыты на
2026-04-27, осталось 2 (G8 + G9). Папка milestone'а:
`docs/milestones/M16-post-deploy-hardening/` с PLAN.md, CHECKLIST.md,
NOTES.md, DECISIONS.md.

**M15 First VPS Deploy** закрыт `v0.0.0-alpha.16` 2026-04-27. Retrospective
short в `docs/milestones/M15-first-vps-deploy/` (PLAN+NOTES, без
CHECKLIST/DECISIONS). 4 hotfix-коммита: `b8cf106` `c7b2b93` `c3ff148`
`8d7c168`.

### Закрытые группы M16 (8 коммитов на main)

```
7a280a01 refactor(academic): headman rate-limit moved to Redis, raised to 300/min (M16 G7)
5c3b7e93 feat(audit): @AdminAction real handler via SPI + 5 user actions (M16 G6)
b0e351ee fix(nginx,scripts): runtime DNS for upstream + verify-deploy false-positives (M16 G5)
221daf6b fix(loki): wait-for-ready before promtail push to fix startup race (M16 G4)
ce5c404d feat(auth): /otp/verify-by-code brute-force counter + alert (M16 G3)
f9cbca52 fix(bot): propagate handler exceptions for DLQ routing + 7d retention (M16 G2)
fe652149 fix(otel): use HTTP/protobuf port 4318 for Java services (M16 G1)
a13fc2db docs(milestones): add M15 retrospective + M16 post-deploy hardening plan
```

| # | Группа | Статус | Главное |
|---|--------|--------|---------|
| G1 | OTel exporter port (4317→4318) | ✅ | Java HTTP /v1/traces, Python gRPC остаётся 4317 (mixed-mode, D2) |
| G2 | Bot dispatcher idempotency + DLQ retention | ✅ | Idempotency уже была M13 G8; реальный fix — убран swallow в dispatch + DLQ TTL 7d (D5) |
| G3 | OTP brute-force counter | ✅ | `verifyOtpByCode` 20 mismatch/5min/IP → 429, alert OtpBruteForceSuspect (D6) |
| G4 | Loki InstancesCount | ✅ | startup race; `min_ready_duration: 15s` + healthcheck-gated promtail (D7) |
| G5 | nginx DNS race + verify-deploy fixes | ✅ | `resolver 127.0.0.11` + 8 переменных-upstream'ов (D8); 4 false-positive в verify-deploy (D9) |
| G6 | @AdminAction real audit log | ✅ | V19+V20 миграции, SPI shared-web, JdbcAuditLogStorage, 5 actions в UserController (D10) |
| G7 | Headman RL Redis 300/min | ✅ | Was 120, bumped 300; Redis primary + InMemory fallback (D11 fail-open vs G3 fail-closed; D12 dual-impl) |
| **G8** | **mTLS Alertmanager → notification-web** | **⏳ TODO** | Decision pending |
| **G9** | **cadvisor de-privileged** | **⏳ TODO** | privileged:true → cap_add SYS_PTRACE |

### Verify на VPS (отложено для всех 7 групп)

Все verify-шаги отложены до redeploy `v0.0.0-alpha.17` (или как назовём
после M16). Список того что проверить **на VPS**:
1. **G1**: `docker logs rct-auth-service --since 1h | grep -c "Connection reset"` → 0
2. **G2**: handler-bug → message в DLQ → DLQBacklog alert (>10/5min)
3. **G3**: 21-я подряд `/auth/otp/verify-by-code` с одного IP → 429
4. **G4**: `docker logs rct-loki --since 24h | grep -c "InstancesCount"` → 0-1/день
5. **G5**: `docker compose restart grafana && sleep 5 && curl /grafana/` → 200/302 без manual nginx restart
6. **G6**: `SELECT * FROM audit_log WHERE action='user.archive'` → видны записи admin actions
7. **G7**: bulk-mark группы 30 студентов <30 сек проходит без RESOURCE_EXHAUSTED; `redis-cli KEYS "rl:headman:*"` показывает entries

---

## Что осталось — G8 + G9

### G8 — mTLS Alertmanager → notification-web (~1-2д)

**Source:** `future-ideas.md` MED-11. Текущий flow:
```
Alertmanager → POST http://notification-web:9094/internal/alert
              (Bearer auth, plaintext HTTP в private_net)
```

**Threat:** compromised контейнер в `private_net` (cadvisor /
node-exporter / blackbox-exporter — все имеют `cap_add: NET_RAW` для
sniffing) → может перехватывать Bearer token из трафика → подделывать
alerts.

**3 кандидата (зафиксированы в DECISIONS § D4):**

1. **Linkerd auto-mTLS sidecar** — каждый контейнер получает client cert,
   шифрование автоматически. Тяжело: extra container per service,
   helm chart, learning curve. **Vetoed:** нет other Linkerd usage,
   adding sidecar = большая зависимость.

2. **Custom certs + nginx mTLS proxy** между Alertmanager и
   notification-web. Контроль, но manual cert rotation. Internal CA +
   client cert для Alertmanager + server cert для notification-web.

3. **Минимальный путь:** только `cap_drop: NET_RAW` для cadvisor +
   node-exporter + blackbox-exporter, оставив plaintext. Sniffing
   capability убрана у потенциального compromised peer, MitM
   проблема остаётся theoretical.

**Рекомендация:** начни с **варианта 3** (10 минут, реально снижает
risk surface) + добавь его в G9 (cadvisor de-priv). Для **полного
mTLS** (вариант 2) нужен:
- internal CA (генерация скриптом)
- mount certs в Alertmanager + notification-web
- `tls_config` в alertmanager.yml webhook
- Промежуточный nginx или native mTLS поддержка в Spring Boot
  notification-web (через `server.ssl.client-auth=need`)
- `secret-rotation.md` обновить с rotation flow CA

Это **~1-2 дня работы** при первом подходе. Можно разделить на
**G8a — cap_drop NET_RAW (5 минут)** в один коммит и **G8b — full
mTLS (1-2д)** в отдельный.

**Файлы которые трогать:**
- `docker-compose.prod.yml` — cadvisor/node-exporter/blackbox-exporter `cap_drop: [NET_RAW]`
- `infra/alertmanager/alertmanager.yml` — `tls_config` если G8b
- (новый) `infra/internal-ca/` — gen scripts если G8b
- `services/notification-service/notification-app/src/main/resources/application.yml` — server.ssl если G8b
- `docs/operations/runbooks/secret-rotation.md` — раздел про CA rotation если G8b

### G9 — cadvisor de-privileged (~0.5д)

**Source:** `future-ideas.md` MED-12. Текущий `docker-compose.prod.yml`:

```yaml
cadvisor:
  image: ...
  privileged: true
  volumes:
    - /:/rootfs:ro
    - /var/lib/docker:/var/lib/docker:ro
    ...
```

`privileged: true` = root host access если cadvisor скомпрометирован.

**Fix per cadvisor docs:**
1. Заменить `privileged: true` на `cap_add: [SYS_PTRACE]`
2. Audit метрик: какие `container_*` метрики продолжают работать?
3. Возможно убрать mount `/var/lib/docker:ro` если container metadata
   не нужна (только image labels — mostly cosmetic)

**G8a + G9 связаны** — в обоих случаях редактируется
`docker-compose.prod.yml` для cadvisor секции. Логично сделать в
**одном PR**: `security: drop privileged + NET_RAW from infra
containers (M16 G8a + G9)`.

**Файлы:**
- `docker-compose.prod.yml` — cadvisor + node-exporter + blackbox-exporter sections
- `docs/operations/monitoring/observability.md` — обновить если что-то по метрикам сломалось

**Verify на VPS:**
- container_* метрики по-прежнему в Prometheus (`up{job="cadvisor"}` = 1)
- Grafana dashboard `rct-containers` рендерится
- `ContainerMemoryHigh` alert срабатывает на artificial stress test

---

## Pre-flight для следующей сессии

```bash
cd C:/Users/maksd/IntelliJIDEA/rutcampustrack
git status --short  # должно быть clean
git log --oneline -3  # последний = 7a280a01 (M16 G7)
git branch --show-current  # main
```

Прочитай для контекста:
- `docs/milestones/M16-post-deploy-hardening/PLAN.md` — scope
- `docs/milestones/M16-post-deploy-hardening/CHECKLIST.md` — что сделано (G1-G7), что осталось (G8-G9)
- `docs/milestones/M16-post-deploy-hardening/DECISIONS.md` — D1-D12 архитектурные решения
- `docs/milestones/M16-post-deploy-hardening/NOTES.md` — surprises и lessons
- `docs/archive/future-ideas.md` § "Pre-v0.1" — MED-11 (mTLS) и MED-12 (cadvisor)

## Что я (Claude в новой сессии) должен сделать

### 1. Подтвердить состояние репо

Прочитай `git log -10`, убедись что 8 M16 коммитов на месте, рабочая
директория чистая.

### 2. Спросить пользователя

Какой подход к G8:
- **(a)** только G8a (cap_drop NET_RAW) + G9 — bundle ~30 минут работы, базовая защита, end of M16
- **(b)** полный G8 (mTLS) + G9 — ~1.5-2 дня работы, complete defense-in-depth
- **(c)** только G9 — close M16 минимально, отложить G8 в M17 (новый milestone)

Моя рекомендация — **(a)** или **(c)**. Полный mTLS большой и не критичный
для текущего scale. Acceptable defer to M17 если появится дополнительный
threat signal или horizontal scale.

### 3. Применить выбранный подход

Делай атомарные коммиты per-task (как делал G1-G7). Обновляй
CHECKLIST/DECISIONS/NOTES per-group. После закрытия M16 целиком:
- Tag `v0.0.0-alpha.17` или `v0.1.0-rc.1` — обсуди с user'ом
- Update `CLAUDE.md` статус M16 на ✅
- Подготовь `NEXT-SESSION.md` для M17 если будет

## Стиль работы (важно)

Сессия G1-G7 показала эти паттерны:
- **Атомарные коммиты** — один commit per group, conventional commits
- **NOTES.md живой лог** — сюрпризы, отклонения, lessons learned
- **DECISIONS.md** — каждое нетривиальное решение с trade-off + альтернативами
- **CHECKLIST.md** галочки `[x]` после реализации
- **Не спамить тулы** — Edit прошёл успешно несмотря на read-before-edit reminder, продолжай работу
- **Verify на VPS отложен** — для всех G* мы только compile + unit tests, real verify будет на VPS
- **Скоуп reduction OK** — G2 переориентирован (idempotency была), G6 v1 без diff, G7 dual-impl. Записывай в DECISIONS.md если меняешь scope vs initial plan.

## Команды которые могут понадобиться

```bash
# Компиляция
$env:JAVA_HOME = "C:\Users\maksd\.jdks\ms-21.0.9"
.\gradlew.bat :services:notification-service:notification-app:compileJava

# Тесты конкретного сервиса
.\gradlew.bat :services:academic-service:academic-app:test --console=plain

# Validate nginx config
docker run --rm -v "$(pwd)/nginx/conf.d/default.conf:/etc/nginx/conf.d/default.conf:ro" -v "$(pwd)/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:alpine nginx -t

# Validate compose syntax
docker compose -f docker-compose.prod.yml config --quiet

# Bash syntax
bash -n scripts/verify-deploy.sh
```

## Контекст пользователя

- Git user: `maksd`
- VPS deployed: `https://ruttrack.site` (M15)
- Working dir: `C:\Users\maksd\IntelliJIDEA\rutcampustrack`
- Owner предпочтения (память): отчёты на русском, audit-разметка экономно, M16 reduced scope OK
- Owner ранее уточнил: headman RL **300/min** (не 120 из future-ideas) — этот выбор уже применён в G7

## Финальный TL;DR для нового Claude

1. Прочитай этот файл целиком
2. Спроси пользователя: G8a+G9 / G8b+G9 / только G9
3. Делай атомарными коммитами с conventional commit style
4. После каждого G — обнови CHECKLIST/DECISIONS/NOTES
5. После закрытия M16 — обсуди tag и обнови CLAUDE.md
