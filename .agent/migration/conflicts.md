# Конфликты и блокеры карты

| Статус | Evidence | Воздействие и правило разрешения |
|---|---|---|
| Resolved | `kit/journal/DECISIONS.md:2551-2567` vs `design/knowledge/PROJECT_CONTEXT.md:44-49,89` | Desktop-first отменён только для порядка кода: PWA+TMA → web. Не выводить из desktop wireframes мобильную спецификацию. |
| Resolved | `design/knowledge/TRANSPORT.md:8-31` vs `design/02-backend/transport-decision.md:266-278,356-387` | One BFF выше старого per-surface recommendation. BFF только composition/forms; domain rules в services. Старый документ archive/reference. |
| Resolved | `kit/journal/DECISIONS.md:2571-2575` vs `design/04-figma/figma-spec-shadcn-overlay.md` | Vue/PCSS, rem, без Tailwind/shadcn(-vue); overlay хранится как superseded design evidence. |
| Blocked | `kit/journal/to-owner.md:9-37`; readiness report `:74-77` | Owner должен решить first PWA/TMA release scope, cache owner, role visibility/gradient tokens, radius и Admin 668/659 discrepancy. Карта не синтезирует решение. |
| Watch | `design/README.md:217+`; `01-wireframes/00-overview.md:1-8,54-85` | Count 27 vs 26/30 и missing auth specs. Сохранять версии и provenance, не выбирать «правильную» по mtime. |
| Исключено | inventory category `secret-by-path`, `config-by-path`, `code-by-path`, `excluded-tree` | Итоговая карта не читает и не хеширует исключённые по пути секреты или конфиги; код и конфиги не переносятся. В repo-history это 1 current workflow, 1 utility code file и 3 документа, заподозренных по пути как secret; они сохранены in place, без чтения. В раннем отброшенном черновике генератора `.mcp.json` ошибочно был хеширован по классификации пути, без печати содержимого; в итоговом inventory у него null hash/destination, и использовать можно только итоговую карту. У пяти не обойдённых деревьев (`.git` ×4, `_work/tmp`) неизвестны итоги потомков. |

Нет target-path collisions среди остающихся `copy-canonical`/`copy-archive` candidates после exact-duplicate collapse; semantic collisions выше не разрешаются копированием.
