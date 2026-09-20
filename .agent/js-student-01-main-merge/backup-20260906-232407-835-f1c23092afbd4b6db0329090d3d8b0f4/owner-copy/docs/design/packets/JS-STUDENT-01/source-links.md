# Источники и границы истории — JS-STUDENT-01

## Основная история

- Основной scope: `JS-STUDENT-01` — «Сегодня: текущая или ближайшая пара и
  отметка посещаемости» в
  [`job-stories.yaml`](../../../product/job-stories.yaml).
- Связанная session projection: `JS-SYSTEM-14`. Отдельный UI логина не выбран и не
  создаётся по этой связи.
- Подготовительная метка `auth/session → «Сегодня»` из
  [`readiness.md`](../../../implementation/readiness.md) не является отдельным
  story ID.

## Сопоставление захваченных состояний

| Figma node | Состояние | Допустимая связь с историями | Граница доказательства |
| --- | --- | --- | --- |
| `4581:3062` | default | JS-STUDENT-01 | UI-представление Today |
| `4585:218` | geo-unconfirmed | JS-STUDENT-01, JS-STUDENT-02 | UI не фиксирует backend/автозаявку |
| `4585:848020` | attendance-confirmed | JS-STUDENT-01 | UI-представление результата |
| `4585:848126` | absent-actions | JS-STUDENT-03; JS-STUDENT-06 | В Figma написано «Забыл отметиться»; каноническая copy для будущей реализации — «Был, но забыл отметиться» из JS-STUDENT-06 |
| `4588:527` | reason-form | JS-STUDENT-03, JS-STUDENT-27 | Доказаны только «Болезнь», textarea и picker 10 MB/2 файла; не весь workflow |
| `4588:848409` | pending-request | совместимо с JS-STUDENT-02/06 | Источник заявки в UI не указан; это не доказательство JS-STUDENT-26 |

Конфликт фразы absent-actions зафиксирован без молчаливого override: будущая
реализация использует каноническую JS-STUDENT-06, согласно приоритету источников и
решению владельца от 06.09.2026.

## Design sources

- [`figma-spec-mobile.md`](../../figma-spec-mobile.md) — design primitives.
- [`COMPONENT_REGISTRY.md`](../../COMPONENT_REGISTRY.md) — component ownership.
- [`tokens-v2.json`](../../tokens-v2.json) и [`brandbook-v2.md`](../../brandbook-v2.md)
  — token names and meaning.
- [`BRAND_DIRECTION.md`](../../../product/reference-rutcampustrack-design/BRAND_DIRECTION.md)
  и [`A11Y_REQUIREMENTS.md`](../../../product/reference-rutcampustrack-design/A11Y_REQUIREMENTS.md).

Live node links и hash-addressed local bytes перечислены в `manifest.json`.
