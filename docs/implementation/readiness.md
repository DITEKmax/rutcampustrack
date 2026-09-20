# Readiness этапов 09–10

Дата: 06.09.2026. Подготовка отделена от готовности конкретной реализации.

| Объект | Статус | Evidence / следующий gate |
|---|---|---|
| Подготовительный registry | DONE/PASS | `docs/product/job-stories.yaml`: 147 stable IDs, latest design source и repository history разделены; final Sol review confirms 62 symmetric trace edges. |
| Backend delta | DONE/PASS | `backend-delta.yaml`: 175 request IDs, статические пути и runtime boundary отделены; final Sol review confirms 62 symmetric trace edges. |
| Contract freeze | BLOCKED | Не выбран owner/canonical artifact и revision: spec-first или Java-first/exported OpenAPI. |
| Vue/PCSS foundation | NOT-STARTED | Это delivery status, не отсутствующее продуктовое решение. Текущие PWA/TMA React, web-panel Angular защищены картой retirement. |
| Figma packet первого flow | BLOCKED | Нужны конкретные node links/states/assets; извлечение начинается только в stage 09. |
| Runtime environment | SKIPPED (docs-only) | Для этой документационной партии приложение не менялось; runtime продукта не является применимой проверкой. |
| Independent Sol high review | PASS | History FAIL6 → FAIL1 → PASS preserved in `.agent/migration/`; final focused review has findings 0. |

## Candidate first story: auth/session → «Сегодня»

Это candidate, не выбранный релиз. До реализации нужны: contract projection для ролей,
статусов, effective permissions, группы и активного семестра (`AU-10`, `AC-09`);
owner/canonical contract revision; Figma packet S-01/S-02; PWA/TMA cache-owner policy;
изолированное runtime environment. Server ACK без optimistic increment/offline queue
подтверждён source decision, но не заменяет contract или runtime check.

## Граница партий

Stage 09: назначить первый story packet, contract owner/revision и design packet.
Stage 10: только после этого FE и BE developers в разных worktrees на одинаковом
baseline; shared contracts/generated types/lockfile/config/docs — один owner;
интегратор — назначенный developer. Эта подготовка на этой границе останавливается.

## Предложение технической раскладки, не ADR

Предложение для первого packet: `frontends/mobile-core/` с публичными Vue feature
APIs (`features/<story>/{api,model,types,ui,lib,index.ts}`), PCSS modules и одним
transport owner; `frontends/pwa-vue/` и `frontends/tma-vue/` только bootstrap/host
adapters. Это не утверждает новые package names, не создаёт workspace и не меняет
текущие React clients. После contract freeze назначенный BE developer может быть
single owner spec-first artifact как **proposed technical choice**, пока владелец не
примет ADR или не назначит иной owner.
