# План переноса материалов (без выполнения переноса)

## Граница

Эта партия создаёт карту, а не `docs/sources/manifest.yaml` и не целевые файлы. Для каждого `copy-*` inventory содержит точный SHA-256 и предложенный destination. Единственный owner будущих `docs/sources/manifest.yaml` и workflow — root; writer копирует только после отдельной назначенной партии. Спецификации копируются без редактирования.

Предлагаемая запись manifest (не создана в этой партии):

```yaml
- source: kit:specs/example.md
  canonical_path: docs/product/specs/example.md
  status: copy-canonical
  owner: root
  supersedes: []
  decision_date: 2026-09-05
  hash: <inventory item sha256>
```

## Порядок и destinations

Карта охватывает 2,225 просмотренных файлов, 289 обойдённых каталогов и 118,021,433 известных байта; пять исключённых записей деревьев не обходились, их итоговые потомки неизвестны. Внешние кандидаты: 129 `copy-canonical`, 1,017 `copy-archive`, 109 точных `duplicate`; текущая история: 941 retained-in-place `duplicate`; всего 34 `exclude`, 0 `blocked`.

1. `kit/design/**` → `docs/design/**`, `kit/specs/**` → `docs/product/specs/**`; это кандидаты canonical, а не автоматическое продуктовое решение.
2. `design/01-wireframes/**` → `docs/wireframes/**`; `00-scans/**`, `03-brand/**`, `05-adaptive/**`, локальные `04-figma/**` → `docs/design/**`. Локальные Figma-derived отчёты читаемы как evidence; live Figma не открывался и не требуется. `04-figma/figma-spec-shadcn-overlay.md` — archive/superseded evidence, не implementation canon.
3. `design/knowledge/PROJECT_CONTEXT.md`, `A11Y_REQUIREMENTS.md`, `COMPONENT_REGISTRY.md`, `BRAND_DIRECTION.md` → `docs/product/reference-rutcampustrack-design/**`; `TRANSPORT.md` и `02-backend/**` → `docs/architecture/reference-rutcampustrack-design/**`.
4. `kit/journal/**`, `kit/tasks/**`, `_work/**`, historical instructions → `docs/archive/**` с сохранением source-relative path и provenance. Каждый внешний research report → `docs/research/reference-repo/repository-research-contour/<relative-path>`. Prompts/AGENTS/SKILL/SETUP — archive `.inactive`, не operational instructions.
5. Current repository history: `docs/**`, `.planning/**`, root `CLAUDE.md` — отдельный source `repo-history`; 941 запись сохраняет existing target address как `duplicate` с разрешимым `retained_address: repo-history:<relative-path>` и SHA-256 identity. В частности, `CLAUDE.md` остаётся `CLAUDE.md`, без `.inactive` destination. Исключены, но остаются на месте: 1 current workflow, 1 utility code file и 3 документа, заподозренных по пути как secret; их содержимое не читалось.
6. `copy-archive` binaries передавать обычным checksum-verified copy (без text conversion). Среди крупных: `filters-block.svg` 3,966,081 B, `headman-contact.png` 2,909,338 B, `RutCampusTrack_PWA_TMA_UX_Guide.pdf` 2,319,229 B. Для каждого сверить SHA-256 до и после копирования.

`duplicate` не копировать второй раз: retained address задан у каждой записи. При различном содержимом и общем target создать `docs/archive/collisions/<source-id>/<relative-path>` до любой canonical overwrite, указать provenance/old SHA-256 в manifest и только затем выбирать canonical target. Текущая карта не нашла таких target collisions.

## Приоритет

Текущее owner решение > поздние дополнения > решения > реестр > спецификация > принятый design report > research/archive. `kit/journal/DECISIONS.md:2551-2575` задаёт порядок реализации PWA+TMA → web и Vue/PCSS без Tailwind/shadcn. Это отменяет только ранний desktop-first порядок из `design/knowledge/PROJECT_CONTEXT.md:44-49`, а не различие самостоятельных PWA/TMA продуктов (`:47`).

`design/knowledge/TRANSPORT.md:8-31` фиксирует REST, один BFF для web/PWA/Telegram, gRPC вниз и domain rules в services; поэтому `02-backend/transport-decision.md` с per-surface BFF — archived supporting analysis. Local `04-figma/figma-spec-shadcn-overlay.md` также superseded для implementation этим owner решением.

Part 26 — узкое мобильное исключение: только Teacher Profile role entry и три named mobile role-gradient carriers; desktop, forms и auth в него не входят (`kit/journal/reports/2026-09-05-part-26-teacher-profile-role-entry-and-role-gradient.md:90-91,184-187`). Поэтому поздние `kit/design/mobile/**`, `figma-spec-mobile.md`, state ledger и visual assets остаются design evidence с journal decision above them, а не автоматическим переносом desktop semantics.

## Предпосылки следующей партии

Следующая партия создаёт registry и manifest по этой карте; отдельное owner-решение требуется только для продуктовых disputes из `conflicts.md`. Не переносить external URLs как зависимости: `design/README.md:4` (GitHub), `kit/journal/reports/2026-09-05-vue-bff-implementation-readiness.md:93` (Vue/VTU/MSW/Telegram) и archived Figma/MCP links — reference-only; package coordinates, lockfile и integration configuration не были обнаружены/не переносятся этой партией.

Конкретные отсутствующие auth-reference targets не фабриковать: `docs/wireframes/shared/001a-recovery.md` и `docs/wireframes/shared/001b-reset.md` упомянуты, но исходными файлами не предоставлены (`design/01-wireframes/00-overview.md:18-19`, `nav-integrity.md:46-53,96-98`). Канонический имеющийся материал — `docs/wireframes/shared/001-login.md`, где 001a/001b находятся внутри одного файла.
