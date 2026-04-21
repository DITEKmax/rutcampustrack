# M06 Decisions (Micro-ADR)

Фиксируй каждое решение которое не описано в OWNER-ANSWERS, но нужно
для реализации. Формат: `## YYYY-MM-DD — D{N}: короткий заголовок`,
дальше 3-10 строк: что выбрано, почему, альтернативы.

---

_Открытых развилок на старт M06 нет — scope зафиксирован в
OWNER-ANSWERS QD1/QD4/QD5/QD6 (строки 2074-2356) + P2-9/1..2
(строки 4043-4115). M05 defer'ы — из audit findings (Группа 8)._

---

## 2026-04-21 — D1: HEALTHCHECK через `wget`, без установки `curl`

**Выбрано:** в Dockerfile'ах 7 Java-сервисов —
`HEALTHCHECK CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1`,
**без** дополнительной установки `curl`.

**Почему:** `eclipse-temurin:21-jre-alpine` (runtime-образ всех 7
сервисов) уже содержит `wget` (busybox). OWNER-ANSWERS P2-9/1 (строка
4056) рекомендует `apk add --no-cache curl`, но это mostly для
удобства debug exec'ов (строка 4052). Использование `wget` даёт те же
функции в HEALTHCHECK без дополнительного слоя (~7MB + 20+ transitive
deps).

**Плюсы:**
- Меньше образ (не устанавливаем curl + OpenSSL userland).
- Последовательность: `docker-compose.prod.yml` уже использует
  `wget -qO- http://localhost:PORT/actuator/health` во всех 7
  healthcheck'ах — Dockerfile-директива совпадает 1:1.
- Меньше supply-chain-поверхность (trivy scan).

**Минусы:**
- `curl` удобнее для debug через `docker exec -it ... sh` (JSON
  parsing, `-v`, timing). Но для этого можно временно
  `apk add --no-cache curl` внутри `docker exec` сессии либо
  использовать bundled `java net.http` через script.

**Последствия:**
- Docker image без `curl` на ~7MB меньше.
- notification-bot остаётся с `curl` (python:3.12-slim, уже установлен
  в Dockerfile:3, health endpoint http://localhost:8081/health).
- `docs/dockerfile-conventions.md` (NEW-150) зафиксирует правило
  «HEALTHCHECK через wget для Java, curl только если он уже в base
  image».

**Альтернативы отклонены:**
- (a) `apk add --no-cache curl` — overkill для HEALTHCHECK.
- (b) `HEALTHCHECK` через `java -cp app:dependencies HealthCheck` —
  самописный класс на JarMode — overengineering.
