# Part 23 — Teacher mobile, Gate B

Дата: 04.09.2026. Figma: `VgVjQYWILLG9AC7Eh12VMk`. Новая страница: `27 · Mobile · Teacher`, `5807:142`.

## 1. Ответы на вопросы

- Владелец потребовал не ждать приёмки между ролями: Teacher начат только после внутреннего Gate A Admin и доведён до Gate B без промежуточной остановки.
- Явных продуктовых вопросов в сообщении владельца не было. Owner-only решения не подменены догадками: неподтверждённые write, privacy, aggregate и platform states исключены или явно disabled.
- Предыдущая задача Headman `01a0615f-80e5-75f3-9e1c-5101b9f32874` использована как принятый мобильный reference. В основном prompt также назван более ранний `01a05976-83e4-7511-9003-62d1668fe887`; организационное расхождение не описывает Figma и не блокировало роль.
- Teacher attendance оставлен read-only: поздние спеки и live desktop подтверждают просмотр, но не подтверждают mobile write-contract.
- Персональный student attendance detail не собран: поздние спеки 122–123 прямо запрещают этот переход для Teacher и имеют приоритет над generic probe из prompt.

## 2. Разбор расхождений

- До Teacher в файле было `28 страниц документа`; после создания страницы роли стало `29 страниц документа`.
- Ожидаемого файла/семейства `120-*` в `specs/wireframes/teacher/` нет; доступны `121–123`. Отсутствие названо, функции не достроены по догадке.
- Desktop/live frames подтверждают Teacher success-проекции, но backend read endpoints, authorization и aggregate contracts ещё не закрыты. Поэтому Figma показывает подтверждённые static fixtures, а canvas-заметки фиксируют backend pending; отчёт не утверждает готовность API.
- Generic Teacher probe в prompt допускает переход из roster в student detail, но поздние privacy-правила 122–123 его запрещают. В живом банке `0 персональных student detail`.
- Route/H1 `Посещаемость` и `Статистика` длиннее принятой однострочной mobile dock-копии. В nav стоят `Учёт` и `Итоги`; это adapter-copy, не переименование маршрутов.
- Первый Today dock был `390×76 px`; принятый live donor — `358×76 px`. Кадр исправлен до `358×76 px`, `x=16`, `y=752`, bottom inset `16 px` до продолжения роли.
- На closing-pass выбранные/невыбранные glyph в шести dock имели неверные binding. Исправлено: selected icon/label → `VariableID:10:20`, inactive → `VariableID:10:6`; все шесть root rerendered.

## 3. Функциональная карта

| Desktop family | Mobile outcome | Подтверждённые данные и граница |
|---|---|---|
| 121 День преподавателя | `30 · Сегодня` | Дата, текущая/следующая пара, мои назначения; read-only schedule-first |
| 122 Посещаемость | `31 · Посещаемость / lessons` → `31 · Пара / roster` | Group/subject/type filters, lesson list, 27 read-only student facts |
| 122 Student detail/write | исключено | Late specs запрещают персональный Teacher drilldown; write-contract отсутствует |
| 123 Статистика | две root-lens: по студентам / по моим группам | Четыре метрики, точные desktop fixtures; межгрупповое сравнение по предмету disabled |
| Common map | `34 · Карта` | Точный shared `MobileCampusMap`; empty-state честно показывает отсутствие схемы |
| Common profile | `39 · Профиль` + role-switch | Account-level routes, четыре effective role rows, current Teacher |

Все Teacher-domain mutations, bulk, grading, review, commit, ACK и optimistic success имеют outcome `excluded`, а не «будут работать позже».

## 4. Варианты архитектуры и критика

1. Lesson-first root → отдельный read-only roster. Выбран: root остаётся сканируемым, один tap раскрывает конкретную пару, Back ownership однозначен.
2. Lesson-first root → персональный student detail. Отклонён поздними specs 122–123 и privacy/authorization gap.
3. Mobile editable journal с status picker/bulk/review. Отклонён: нет подтверждённого права и полного revision/idempotency/conflict/ACK contract.
4. Одна смешанная статистика. Отклонена: студенты и группы имеют разные единицы сравнения. Выбраны две root-lens в одном route с явными tabs.
5. Teacher-specific map. Отклонён как второй смысл; переиспользован точный общий `map/MobileCampusMap`.

Critic сначала поставил `NO-GO` group statistics из-за отсутствующего видимого disclosure; обе group cards получили canonical `icon/next`, затем прошли `GO`. Ошибочное замечание critic о фамилиях student statistics снято прямым readback: live 123 и Figma совпадают — `Анна Белова`, `Дарья Егорова`; мутация не выполнялась.

## 5. Выбранное решение

Safe probe: `Сегодня → текущая Пара → read-only roster → Back`. Он проверяет day context, lesson context, длинный список, status semantics и detail ownership, не изображая несуществующий commit.

Полный банк дополняет probe root-экранами `Посещаемость`, двумя линзами `Статистика`, общей `Картой`, `Профилем` и общей сменой роли. Все product frames — canonical standalone PWA dark `390×844`.

## 6. Screen inventory в порядке canvas

| A | Node | Кадр | Тип | Parent → next | Verdict |
|---:|---|---|---|---|---|
| 34 | `5810:142` | `30 · Сегодня / default` | root | start → Пара / назначения | GO |
| 93 | `5820:142` | `31 · Пара / roster` | detail | Сегодня/Посещаемость → Back | GO |
| 34 | `5823:142` | `31 · Посещаемость / lessons` | root | dock → выбранная Пара | GO |
| 44 | `5825:142` | `32 · Статистика / по студентам` | root | dock → filters / group lens | GO |
| 43 | `5838:374` | `32 · Статистика / по моим группам` | root | tab → student lens по группе | GO |
| 7 | `5848:411` | `34 · Карта / default` | root | dock → external maps link | GO |
| 25 | `5850:585` | `39 · Профиль / default` | root | dock → account route / role switch | GO |
| 14 | `5854:582` | `39 · Профиль / смена роли` | task | Профиль → выбранная role root | GO |

Сумма восьми product frames: `294 A`. Пять family-labels и один board дают ещё `6 A`; page total `300 A`.

## 7. Типы экранов

- Root: 6 — Today, Attendance, две Statistics lens, Map, Profile. У каждого ровно один dock и нет product Back.
- Detail: 1 — roster. Один product Back, dock и MainButton отсутствуют.
- Task: 1 — role switch. Один product Back, dock и MainButton отсутствуют.
- Editor/review/ACK: 0 — соответствующие Teacher write-contracts не подтверждены.

## 8. Tap budget

- `Сегодня → текущая Пара`: 1 tap; `Back → Сегодня`: 1 tap.
- `dock Учёт → lesson`: 2 taps от другой root; 1 tap из `Посещаемость`.
- `dock Итоги → По моим группам → group student lens`: 3 taps от другой root; 2 taps внутри statistics.
- `dock Карта → Открыть корпус в картах`: 2 taps; внешний переход не маскируется как встроенный route.
- `dock Профиль → Сменить роль → роль`: 3 taps.
- Teacher-domain commit / irreversible action: 0 taps, потому что action отсутствует.

## 9. Что сознательно удалено или передано на большой экран

- Attendance status picker, edit, bulk, review, commit, success/ACK и grading.
- Персональная attendance-detail студента и источник/actor/time конкретной отметки.
- Межгрупповое сравнение одного предмета: показано disabled с причиной `Сравнение по одному предмету между группами пока недоступно`.
- Неподтверждённые export, freshness, offline queue, optimistic sync и desktop handoff.
- Map authoring/admin controls и связь карты с расписанием.

## 10. Reuse компонентов и иконок

- `shared/MobileBottomNav` variant `slots=5`, master `4175:353` — 6 Teacher instances.
- Canonical previous `4597:3579` — Today date pager, roster Back и role-switch Back; Today pager не считается product Back.
- Canonical next `1225:7531` и chevron-down `260:1687` — lesson/assignment/disclosure/filter affordances.
- `AttendanceStatusBadge` variants `513:22046 / 22050 / 22054 / 22062` — 27 пассивных facts.
- `map/MobileCampusMap` `4456:955` — один точный instance `358×472`, overrides `[]`.
- Profile icons `4734:146 / 154 / 162 / 172 / 180` и принятый общий account/profile pattern.

Мастера, component sets и icon masters не изменялись.

## 11. Предложенные gaps без самовольного создания

- Teacher day/lesson/roster read endpoints и effective authorization.
- Student/group aggregates, denominator, rounding, period and revision semantics.
- Map asset catalog, stable floor IDs, loader/auth contract.
- Privacy projection source/actor/time для attendance facts.
- Role-switch bootstrap/ACK и очистка role-scoped history/cache.
- Точный semantic radius token для пяти inherited profile-row `r18` либо отдельный ремонт общего pattern.
- HUG anatomy `AttendanceStatusBadge has-label=true`, из-за которой чистые instances воспроизводят root width/height override fields.

Ни один gap не превратился в новый token, component, icon, право или endpoint.

## 12. Состав и A каждого кадра

- `Сегодня`, `34 A`: role header, date pager, current lesson card, next lesson, two assignment rows, dock.
- `Пара / roster`, `93 A`: Back/title, lesson context, caption `Посещаемость · 27 студентов`, clipped vertical viewport и 27 rows.
- `Посещаемость / lessons`, `34 A`: role header, group/subject/class-type filters, three lesson cards, dock.
- `Статистика / по студентам`, `44 A`: role header, two tabs, group/subject/type filters, two student metric cards, dock.
- `Статистика / по моим группам`, `43 A`: role header, two tabs, disabled comparison explanation, class-type filter, two group metric cards, dock.
- `Карта`, `7 A`: role header, corpus and floor filters, exact shared map, external maps row, dock.
- `Профиль`, `25 A`: Ivan Kuznetsov account card and five account rows, dock.
- `Смена роли`, `14 A`: Back/title and four role rows in order Student, Headman, Teacher current, Admin.

## 13. Геометрические изменения

- Новая page 27: `0 → 300 A`; board: `0×0 → 908×4532 px`; direct page children: `0 → 1`.
- Восемь product frames: каждый `0×0 → 390×844 px`.
- Today dock: `390×76 → 358×76 px`; финал `x=16`, `y=752`, inset `16 px`.
- Roster viewport: `358×576 px`; content `358×1728 px`; контролируемое vertical overflow `1152 px`; horizontal escape `0 px`.
- Group selected tab: radius `16 → 16 px` при добавлении exact binding; profile card `24 → 24 px`.
- Avatar `r28 → radius/full=999` и role-switch Back `r22 → radius/full=999`: видимая круглая геометрия `0 px` дельты.
- Защищённые pages 16–25 и shared masters в Teacher phase: `0 A / 0 geometry`.

## 14. Consumers переиспользованных мастеров

| Master | Before → after | Teacher contribution | Master mutation |
|---|---:|---:|---:|
| MobileBottomNav `4175:353` | 75 → 81 | +6 | 0 |
| MobileCampusMap `4456:955` | 3 → 4 | +1 | 0 |
| previous `4597:3579` | 34 → 37 | +3 | 0 |
| next `1225:7531` | 61 → 71 | +10 | 0 |
| chevron-down `260:1687` | 232 → 239 | +7 | 0 |
| present / excused / absent / none | 32→44 / 8→14 / 9→14 / 5→9 | +12 / +6 / +5 / +4 | 0 |
| profile icons `4734:146/154/162/172` | 3 → 4 каждый | +1 каждый | 0 |
| account-history `4734:180` | 4 → 5 | +1 | 0 |

Рост — только новые instances на page 27; source nodes не изменены.

## 15. QA числами

- Product frames: 8; `390×844`: 8/8; wrong size: 0; A screens: 294; page A: 300.
- Roots/details/tasks: `6 / 1 / 1`; bad Back/nav ownership: 0; product Back: 2; MainButton: 0.
- Six docks: каждый `358×76`, `x=16`, `y=752`, inset 16; selected count 1; nested `BACKGROUND_BLUR` carrier 1, radius 25, token-bound; outside blur 0.
- Selected icon+label bindings correct: 6/6 to `VariableID:10:20`; inactive icon+label: 24/24 to `VariableID:10:6`.
- Uncontrolled bounds overflow: 0; clipping defects: 0. Единственный controlled overflow — roster `1728−576=1152 px`; horizontal escape 0.
- Text overflow / unintended ellipsis / literal Inter / missing fonts: `0 / 0 / 0 / 0`.
- Local unbound solid fills / strokes / gradients / image fills: `0 / 0 / 0 / 0`.
- Shadows / glow / layer blur outside nav: `0 / 0 / 0`.
- Interactive targets `<44×44`: 0. Passive status badge height 24 не является target.
- Roster: `27` rows; `12 present + 6 excused + 5 absent + 4 none = 27`; longest FIO 35 characters; symbol + word + color in every status.
- Teacher write / attendance edit / bulk / grading / personal student detail: `0 / 0 / 0 / 0 / 0`.
- Lesson cards without group context where multiple groups apply: 0.
- Rectangular underlay under rounded carriers, double Back/nav/action, color-only status, accidental fixed-height centered multiline: 0.
- Family labels outside product frames: 5; loose/orphan page nodes: 0.

## 16. Render каждого кадра и visual verdict

- `_work/renders/part-22-23/teacher-30-today.png` — day-first hierarchy, current lesson and assignments scan cleanly; GO.
- `_work/renders/part-22-23/teacher-31-roster.png` — lesson context and 27-row vertical continuation are explicit; GO.
- `_work/renders/part-22-23/teacher-31-lessons.png` — three filters and lesson-first list stay compact; GO.
- `_work/renders/part-22-23/teacher-32-students.png` — exact two-student four-metric fixtures remain readable; GO.
- `_work/renders/part-22-23/teacher-32-groups.png` — disabled reason and two disclosed group cards are explicit; GO.
- `_work/renders/part-22-23/teacher-34-map.png` — honest empty floor map, no admin/schedule fiction; GO.
- `_work/renders/part-22-23/teacher-39-profile.png` — common account hierarchy, one selected dock item; GO.
- `_work/renders/part-22-23/teacher-39-role-switch.png` — four large rows, current Teacher named, one Back; GO.
- `_work/renders/part-22-23/teacher-contact-sheet.png` — closing whole-role rhythm after final dock-color repair; visual orphans and overlap not found.

Final critic verdict: Gate B `GO`, 8/8 product frames.

## 17. PWA / TMA ownership

- Root canonical frame models standalone PWA: one product dock, no product Back. Keyboard state hides dock in implementation; it was not duplicated in this static bank.
- Detail/task canonical frame: one product Back, no dock and no MainButton. Browser-PWA uses history; TMA replaces product Back with native BackButton.
- Today’s left date-chevron is a pager control, not a screen Back.
- Safe area belongs to shell/platform adapter; fixed Telegram insets are not embedded in product geometry.
- Role switch action belongs to the selected role row; runtime must wait for server response, clear role-scoped history/cache and open that role’s root.

## 18. MCP calls по типам

Teacher phase is main-thread Figma ordinals `166–292`: `127` calls total.

- `use_figma`: 100 — 96 completed, 4 failed read/runtime calls.
- `get_screenshot`: 26 completed — four desktop references, individual product renders, repairs and contact-sheet renders.
- `search_design_system`: 1 completed.
- `whoami`: 0 in Teacher phase; the required main-thread call was made once in common reconnaissance.

Exact ordinal classification for every call is in `journal/state/part-22-23-mobile-transport-ledger-2026-09-04.json`.

## 19. Transport, partial mutation и repairs

- HTTP 429: 0; mutation timeout: 0; transport failure in Teacher phase: 0.
- Failed `use_figma`: 4 — unsupported `loadAllPagesAsync`, `TextNode.getRangeBoundingBox`, variant-incompatible property read and unguarded `absoluteRenderBounds`; all recovered by compatible bounded read, mutations 0.
- Partial mutation: 0; partial duplicate: 0; unresolved partial: 0.
- Product QA repair mutation calls: 8 — Today nav size, compact dock copy, group disclosure, clean roster badge rebuild, selected-tab radius binding, profile radius bindings, Back radius binding, six dock color bindings.
- One JS regex compilation failed before any Figma tool invocation and therefore is not a Figma transport event.
- Imported StatsByGroup reference read took more than 60 seconds but completed; it changed no canvas node and only resolved published references.

## 20. Baseline before / after

- Before: `journal/state/part-23-mobile-teacher-before-2026-09-04.json`; page count 28; Teacher page absent; Admin page `227 A`, hash `967f2796`.
- After: `journal/state/part-23-mobile-teacher-after-2026-09-04.json`; page count 29; Teacher page `300 A`, hash `5d35f2ee`; Admin page unchanged.
- Hash method fully specified in both snapshots: FNV-1a UTF-16 code units, LF-terminated rows, 11 named fields, geometry to 0.001.

## 21. Protected-page deltas

Expected pages 16–24: `0 A / 0 geometry`; factual: `0 A / 0 geometry`; agent-induced: `0 A / 0 geometry`; external: 0 during Teacher phase.

Page 25 before/after Teacher: `2611 → 2611 A`, hash `74980519 → 74980519`, factual `0 A / 0 geometry`, agent-induced `0 A / 0 geometry`. Earlier Admin-phase growth `2476 → 2611 A` is classified separately as external concurrent owner delta `+135 A`; it was neither normalized nor reverted.

Pages 16–25 all preserve their before Teacher A/hash/bounds. Shared-master mutation count: 0. New A in Teacher phase exist only on page 27.

## 22. Token mappings и fallbacks

- Nav glass blur: `component/mobile-bottom-nav/glass-background-blur`, `VariableID:4354:143`, radius 25; exactly one carrier per root.
- Nav paints: selected/on-accent `VariableID:10:20`; inactive/primary text `VariableID:10:6`.
- Group selected tab `r16`: bound to exact `radius/xl`, `VariableID:12:17`; numeric geometry unchanged.
- Profile card `r24`: bound to `radius/2xl`, `VariableID:1504:37363`; geometry unchanged.
- Avatar and role-switch Back: bound to `radius/full`, `VariableID:12:18`; effective circles unchanged.
- Five inherited profile rows remain `r18` literals. Exact semantic token is absent; no nearby value was bound by numeric coincidence and no token was created.
- Clean HUG rebuild proves root width/height override fields on labeled AttendanceStatusBadge are source behavior; accepted as a named master gap, not as page-local data.
- Non-semantic bindings to spacing/dialog/width scales: 0 found in Teacher product composition.

## 23. Открытые вопросы

1. Exact read endpoints and effective authorization for day, lessons, roster/journal and statistics.
2. Aggregates, denominators, rounding, period/type filters and revision snapshot for both statistics lenses.
3. Whether compact dock labels `Учёт` and `Итоги` are approved adapter-copy.
4. Privacy projection for status source, actor, manual/auto and changed-at; current bank deliberately omits it.
5. Floor asset IDs, floor catalog, loader and map authorization.
6. Owner choice for profile-row `r18` and future AttendanceStatusBadge HUG master repair; either requires a separate party before touching shared masters/tokens.
7. Role-switch bootstrap/ACK and role-scoped cache/history clearing.

## 24. Что не сделано и почему

- Teacher write, personal student detail, cross-group single-subject comparison and source/freshness fields: blocked by permission/privacy/backend gaps.
- Light theme, 320/360/430, tablet, desktop-responsive and complete PWA/TMA state matrix: outside this party.
- Loading/empty/error beyond the supported map empty-state, keyboard, offline/stale/conflict and long-data variants: next party only after owner visual review.
- Shared masters, token definitions, registry, specs and desktop pages: not changed by authorization boundary.
- Library publication: not performed; publication belongs to the owner.

## 25. Изменённые файлы

- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-23-mobile-teacher-before-2026-09-04.json:1–60` — before snapshot.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-23-mobile-teacher-after-2026-09-04.json:1–144` — Gate B snapshot.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-22-23-mobile-transport-ledger-2026-09-04.json:1–118` — exact call ranges, types and incidents.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\to-owner.md:934–993` — append-only owner gaps.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-23-mobile-teacher.md` — этот отчёт; точный финальный диапазон строк фиксируется closing-pass дополнением.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\_work\renders\part-22-23\teacher-*.png` — individual renders, desktop evidence and contact sheet; PNG не имеют строковых номеров.

`design/` и `specs/` не изменены; подтверждённого нового owner-rule для append-only спецификации не возникло.

## ✏️ 04.09.2026 — closing self-range

- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-23-mobile-teacher.md:1–265`.

## ✏️ 04.09.2026 — уточнение IA после независимого редакционного review

Это уточнение отменяет только неоднозначное сокращение переходов в строках
выше; состав и verdict кадров не меняются.

- Canonical parent `31 · Пара / roster` — выбранная пара. Primary entry в
  текущем банке: current lesson из `30 · Сегодня`. `31 · Посещаемость / lessons`
  является вторым entry/history source к тому же lesson-detail; Back возвращает
  фактический source через PWA history или native TMA BackButton.
- Переходы Today разделены: current lesson → roster; назначение группы
  `ИКБО-01-24 · Лекции` → Statistics с group context; назначение предмета
  `Компьютерные сети · ИКБО-03-24 · Лабораторная` → Attendance с subject/group
  context. Формулировка `назначения → Пара` выше не является route contract.
- `Посещаемость / lessons` и обе Statistics lens — contextual success frames
  с явно видимыми выбранными filters, не cold-entry/no-context states. Cold
  entry, reset filters и server restoration policy относятся к следующей state
  matrix; текущий отчёт не обещает число taps до получения такого контекста.
- Static success projection признана допустимой советом по late specs 121–123
  и live desktop fixtures. Это verdict Figma-композиции, не заявление о готовом
  backend: read/auth/aggregate gaps остаются открытыми.
- `Учёт` и `Итоги` приняты в текущем кадре как source-supported compact
  adapter-copy; owner-only пункт просит подтвердить долговременную терминологию,
  а не переименовывает `Посещаемость`/`Статистика` и не переводит roots в NO-GO.
- `Открыть корпус в картах` повторяет принятый common Student mobile pattern и
  обозначает внешний maps transition, не новый RutCampusTrack endpoint. Сам
  floor viewer остаётся честным empty-state до asset/catalog backend.

## ✏️ 04.09.2026 — финальный self-range после IA-уточнения

- Поздний точный диапазон, отменяющий прежний self-range: `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-23-mobile-teacher.md:1–296`.
