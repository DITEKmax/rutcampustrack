# M03a Decisions

Micro-ADR формата «решение + причина» для выборов, которые НЕ покрыты
OWNER-ANSWERS.md. Каждая запись — 5-10 строк, не больше.

Не дублировать сюда:
- Решения из OWNER-ANSWERS.md (на них ссылаются через Q-ID / P2-N/M).
- Общие архитектурные принципы (они в `docs/architecture.md` / CLAUDE.md).
- Детали реализации (они в коде + DECISIONS не для how, а для why).

Дублировать сюда:
- Выборы между равнозначными опциями.
- Отклонения от типового подхода с пояснением.
- Trade-off'ы, которые будут актуальны через полгода.

---

## 2026-04-19 — Разделение M03 на M03a + M03b

**Выбрано:** (b) разбиение на M03a (Internal JWT + rate-limit, 5-8д) и
M03b (JWT cookie + ws-ticket + logout, 8-12д).
**Отвергнуто:** (a) монолитный M03 на ~14-18д с одним breaking change релизом.
**Причина:** Промежуточный tag `v0.0.0-alpha.3` после M03a даёт раннюю
стабилизацию Internal JWT и rate-limit без риска откатывать весь cookie-flow
если что-то сломается во frontend migration. Breaking change остаётся одним
релизом (M03b), но Internal JWT уходит раньше и независимо.
**Последствия:** Два post-mortem'а + два bug-hunter прохода (удвоение overhead
на ~1д). M03b depends-on M03a (Internal JWT — prerequisite для ws-ticket
endpoint защиты). README.md обновляется — M03 → M03a/M03b в таблице.

---

## 2026-04-19 — Shared RSA keypair для Internal JWT

**Выбрано:** (a) Shared RSA keypair auth-service + Gateway. Приватный ключ в
env var `JWT_PRIVATE_KEY_PEM` читается обоими сервисами. Публичный ключ
downstream тянут из существующего `/internal/jwt-public-key` на auth-service
(с периодическим refresh).
**Отвергнуто:** (b) отдельная Gateway keypair; (c) Gateway pull приватного
ключа из auth-service через internal endpoint.
**Причина:** Минимум движущихся частей — одна keypair, одна env var для
ротации, auth-service уже публикует public key и downstream готовы
потреблять. Gateway и auth-service и так в одной docker private-net — shared
secret приемлем для v0.0.0.
**Последствия:** `.env.prod.example` (M06) документирует `JWT_PRIVATE_KEY_PEM`
как shared. Ротация ключа — рестарт обоих сервисов синхронно. `InternalJwtIssuer`
в Gateway и `JwtTokenService` в auth-service читают из одного property.

## 2026-04-19 — Default `legacy-headers-enabled=true` + strict toggle последним commit'ом

**Выбрано:** (a) `legacy-headers-enabled: true` по-умолчанию в prod при первом
деплое M03a. Переключение на `false` — отдельным commit'ом после UAT golden
path, последний commit milestone'а перед тегом `v0.0.0-alpha.3`.
**Отвергнуто:** (b) `false` сразу — breaking change с первого деплоя без
rollback-safety.
**Причина:** Двухшаговый rollout снижает риск — если Gateway issuer упадёт
после деплоя, legacy `X-User-*` сохранит работу системы до hotfix. UAT
между двумя commit'ами даёт возможность убедиться что Internal JWT flow
действительно работает для всех ролей.
**Последствия:** Группа 13 CHECKLIST — strict toggle как отдельный commit.
UAT checklist (admin/teacher/student/headman golden path) обязателен перед
toggle. После toggle — `X-User-*` strip в Gateway issuer filter.

---

_Формат записи:_

## YYYY-MM-DD — Короткое название решения

**Выбрано:** X
**Отвергнуто:** Y, Z
**Причина:** одна-две фразы.
**Последствия:** что теперь иначе в будущем (опционально).
