# Границы и известные пробелы — JS-STUDENT-01

## Захваченное Figma-evidence

- Захвачены шесть тёмных экранов `390×844`: default, geo-unconfirmed,
  attendance-confirmed, absent-actions, reason-form и pending-request. Другие темы,
  ширины и хосты этим evidence не доказаны.
- R001 был transport-truncated: сохранённых raw context, screenshot, asset bytes и
  revision для него нет. R006 содержит лишь часть переменных; R011 заменяет его для
  retained family variables. R007 помечен `svgAssetsTruncated=true` и не является
  полным export.
- Figma revision и native `capturedAt` не вернула: снимок не pinned и не atomic.
  `call-log.json` сохраняет reader-local before/after timing proxies R003–R011;
  для R001/R002 proxy отсутствует. Локальное `assembledAt` — время сборки пакета,
  не timestamp Figma.
- Логин отдельно не захвачен и не дорисовывается этим пакетом.

## Состояния следующей реализации

Решение владельца от **06.09.2026**: отсутствие Figma-узлов для loading, error,
offline, responsive, PWA-host и TMA-host **не блокирует** design packet. Их
проектируют при реализации по существующим tokens, компонентам, логике интерфейса
и предметной области. Это план реализации, а не Figma-evidence и не изменение
контракта.

Contract/backend/authz policy, host integration и runtime-проверки остаются
отдельными implementation gates; этот пакет их не придумывает и не проверяет.

## Активы

В generated code присутствуют 58 source-URL references, сохранённые как 16
канонических SVG-байтов по SHA-256. Literal `550e8400…0000.png` исключён из
inventory: это пример URL в boilerplate-тексте ответа, не ссылка из generated code.
Его ранний HTTP 404 сохранён только как диагностическая запись.
