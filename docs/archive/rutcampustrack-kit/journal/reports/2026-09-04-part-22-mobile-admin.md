# Part 22 — Admin mobile, Gate A

Дата: 04.09.2026. Figma: `VgVjQYWILLG9AC7Eh12VMk`. Новая страница: `26 · Mobile · Admin`, `5631:142`.

## Ответы на вопросы

- Владелец потребовал сначала полностью закончить Admin, затем без промежуточной приёмки перейти к Teacher — Gate A выполнен именно как внутренний переход, не как остановка.
- Явных продуктовых вопросов в сообщении владельца не было. Неподтверждённые leaf-flow исключены из canonical bank и перечислены ниже, а не превращены в догадки.
- Переданная владельцем предыдущая задача Headman `01a0615f-80e5-75f3-9e1c-5101b9f32874` прочитана; в prompt партии указан другой исторический ID `01a05976-83e4-7511-9003-62d1668fe887`. Это организационное расхождение не описывает живой файл и не остановило работу.

## Разбор расхождений

- На старте было 27 страниц файла; после создания Admin стало 28 страниц. Единица — страницы документа.
- Page 25 в baseline имела `2476 A`; на Gate A живой файл даёт `2611 A`, factual delta `+135 A`. Оркестратор не нормализовал и не откатывал её: это `external concurrent owner delta`. Финальная agent-induced delta page 25 — `0 A`.
- Pages 16–24 сохранили исходные значения A: `890 / 748 / 836 / 363 / 286 / 142 / 229 / 796 / 2139 A`; дельта каждой — `0 A`.
- Сохранённые before-hash нельзя честно использовать как доказательство равенства: snapshot назвал поля и точность, но не зафиксировал кодировку и разделитель/финальный LF. Ни один проверенный serializer не воспроизвёл page-16 hash `3dce8c94`. Поэтому здесь не заявляется ложный `0 geometry` по несопоставимому hash; доказательства — отдельный A-обход, mutation-target ledger, отсутствие shared-master mutations и финальная проверка ancestry/orphans. Для следующего snapshot правило сериализации будет записано полностью.
- `docs/screens/admin/` из задания отсутствует; пять обязательных SVG прочитаны из фактического `design/mobile/assets/svg/`.
- Root label `Пользователи` физически переносился внутри 65.2 px nav-item. Route и H1 оставлены `Пользователи`, а только nav-copy сокращена до `Аккаунты`. Это локальная mobile copy-gap, не переименование домена.
- Desktop-спека 133 разрешает переходы, которых живой desktop-row не имеет; mobile detail оставлен только как projection уже загруженной записи, без read-by-id/deep link.

## Функциональная карта и coverage

| Desktop family / route | Mobile outcome | Источник и граница |
|---|---|---|
| 131 Dashboard | `40 · Обзор`, read-only | Только `studentsCount`, `teachersCount`, `groupsCount`; activity/delta/queues не подтверждены |
| 132 Пользователи | `41 search-list` + projection detail | Серверный поиск/пагинация; detail только из свежей list-record |
| 132 Create/edit/add role/status/transfer/recovery/archive | blocked / desktop-only | Нет permission, revision, idempotency, conflict и ACK/readback contract |
| 133 Группы | `42 list` + одно inline disclosure | Активные/черновики/архив, search, stable cursor; roster и attendance не входят |
| 133 Assign headman / promotion / destructive | blocked / desktop-only | Нет Admin roster endpoint, eligibility, atomicity, revision и receipt |
| 134 Семестры | `43` read-only list из `Ещё` | Конечный список; active и archive представлены структурными секциями |
| 134 Writes / export | desktop-only | Mobile bank не получает create/edit/activate/archive/delete/export |
| 135 Карта | blocked leaf, 0 кадров | Desktop 135 — authoring assets; read-model/API статуса отсутствуют |
| Common More | `49 · Ещё` | Содержит только прошедший data-gate route `Семестры` |
| Common Profile / roles | `49 · Профиль` + role-switch | Account-level pattern reuse; четыре роли из живого Admin header |

## Архитектурные варианты и критика

1. Отдельный detail для пользователей и групп. Плюс — scan остаётся плотным; минус — у групп нет подтверждённого read-by-id. Выбрано для пользователя только как list projection, а для групп вместо отдельного route выбрано inline disclosure.
2. Одно inline disclosure. Подходит группам: раскрыта максимум одна строка, cursor и набор страницы не меняются. Не подходит пользователям с несколькими role/status pairs — detail вынесен отдельно.
3. Уменьшенная desktop table. Отклонено: создаёт горизонтальный registry и теряет mobile scan.

Critic дважды ставил `NO-GO` раскрытию групп: collapsed-card дублировала детали, а cursor менялся `1–3 → 1–2`. Финал оставляет в collapsed только code + одну сводку, раскрытие добавляет срок и старосту, cursor остаётся `1–3 из 24`.

Для семестров critic отклонил низкоконтрастный disabled branch `EntityStatusCell` и затем экранный paint override. Финал не меняет мастер и не держит override: список сгруппирован секциями `Активный семестр` и `Архив`. Critic дал `GO`; в коде секции должны стать `section + heading + list`.

## Выбранное решение и probe

Внутренний безопасный probe: `Обзор → Аккаунты → поиск → Пользователь`. Он доказывает server-search, компактную list-card, многостатусную роль, projection-detail и отсутствие write-controls.

Admin bank состоит только из read-only triage/search/list/detail и общего role switch. Create-user, assign-headman, карта, bulk, export, destructive и фиктивный desktop-handoff не нарисованы.

## Screen inventory в порядке canvas

| A | Node | Кадр | Тип | Parent → next | Назначение / verdict |
|---:|---|---|---|---|---|
| 20 | `5646:142` | `40 · Обзор / default` | root | start → root tabs | Три live-scale counters, `GO-with-notes` по nav-copy |
| 36 | `5667:213` | `41 · Пользователи / search-list` | root | Обзор → detail | Search-first, три sample records, `GO-with-notes` |
| 16 | `5694:266` | `41 · Пользователь / detail из списка` | detail | search-list → Back | Только projection Ирины Соколовой, `GO` |
| 35 | `5704:270` | `42 · Группы / list` | root | root → disclosure | Три compact cards, `GO` |
| 44 | `5712:314` | `42 · Группы / одна раскрыта` | root | list → same root state | Одна раскрыта, две свёрнуты, `GO` |
| 19 | `5720:75290` | `43 · Семестры / list` | detail | Ещё → Back | Active/archive grouped list, `GO` |
| 11 | `5730:371` | `49 · Ещё / default` | root | root → Семестры | Только подтверждённый route, `GO` |
| 25 | `5737:5773` | `49 · Профиль / default` | root | root → role switch/settings | Общий account pattern, `GO` |
| 14 | `5737:5911` | `49 · Профиль / смена роли` | task | Профиль → новая root-role | 4 строки, current named, `GO` |

Сумма девяти screen roots: `220 A`. Шесть family-labels и board дают ещё `7 A`; page total `227 A`. Все кадры `390×844 px`. Board `0 → 908×5428 px`, `0 → 227 A`.

## Tap budget

- `Обзор → Аккаунты → focus поиска → detail`: 3 taps + typing.
- Восстановленный `Аккаунты → detail`: 1 tap.
- `Обзор → Группы → раскрыть`: 2 taps.
- `root → Ещё → Семестры`: 2 taps; из `Ещё` — 1 tap.
- `root → Профиль → Сменить роль → роль`: 3 taps.
- Admin-domain commit: 0; irreversible action: 0.

## Сознательно удалено / передано на большой экран

- Create/edit user, add/remove role, role status, transfer, recovery/reset, Telegram linking и archive.
- Create/edit/promote/archive/delete group и assign-headman.
- Semester create/edit/activate/archive/delete/export.
- Map authoring, upload/replace/delete SVG/PNG и неподтверждённый map status viewer.
- Bulk, export, destructive confirmation, review/submitting/ACK без server contract.
- Activity/delta/action tiles на Dashboard и handoff CTA без target/availability/return contract.

## Reuse map

- `shared/MobileBottomNav` `4175:353`, 6 новых экземпляров; expanded consumers `69 → 75`, мастер не менялся.
- Принятый Back `4781:167`: `44×44`, radius `22`, `icon/previous` `16×16` at `14/14`.
- Nav icons: overview `4164:844563`, users `4165:143`, groups `4165:844554`, more `1298:7535`, profile `4163:844554`.
- Search `1265:7545`, date `1265:7535`, next `1225:7531`, chevron-down `260:1687`, accepted up-rotation donor.
- `admin/EntityStatusCell` для user role/status; disabled hint скрыт только разрешённой visibility-override из-за живого defect `show-hint=false`.
- Profile icons `4734:146 / 154 / 162 / 172 / 180` и принятый Student/Headman account pattern.

## Геометрические изменения

- Защищённые страницы 16–24: `0 A`; shared masters: 0 изменений.
- Page 25 factual: `2476 → 2611 A`, external concurrent owner delta; agent-induced final `0 A`.
- Новая page 26: `0 → 227 A`; board `0×0 → 908×5428 px`; 9 кадров по `390×844 px`.
- Group disclosure: финал удерживает 3 записи и cursor `1–3 из 24`; expanded card `358×224`, collapsed cards `358×80`, cursor y `628–672` внутри content и 60 px до nav.
- Semesters после QA: четыре cards `358×104 → 358×80 px`; y `64/180/296/412 → 104/232/324/416`; добавлены headings y `68/196`.
- Три поздних кадра были перенесены из direct page children в board без изменения экранной x/y; loose nodes `3 → 0`.
- Неограниченных изменений существующей геометрии: 0.

## Consumers затронутых мастеров

Затронутых общих мастеров нет: mutations masters `0`, поэтому обязательных before/after consumer-перемеров для изменённого мастера нет. Для использованного, но не изменённого `MobileBottomNav` расширенный consumer count вырос `69 → 75` только за счёт шести экземпляров на новой page 26. Auth/student/headman экранные мастера не менялись.

## Литералы мастера / QA и привязка по смыслу

- Nav master/reference: `358×76` screen instance, bottom inset `16`, один nested `BACKGROUND_BLUR=25`, bound к `component/mobile-bottom-nav/glass-background-blur`; QA: 6/6 root nav имеют ровно один такой carrier, root blur 0.
- Back donor: radius `22` и `44×44`; QA: 3/3 detail/task Back ровно `44×44`, icon `16×16`.
- Page-local donor-matched corner radii: `16×46`, `18×5`, `22×3`, `24×7`, `28×1`; подходящего semantic radius binding у этих screen-compositions не обнаружено, токены не создавались.
- Local solid fills `0`, local solid strokes `0`; буквальный `Inter` `0`; missing fonts `0`; shadows `0`; layer blur `0`.
- Проверка «привязки не по смыслу»: nav blur привязан к mobile-nav blur token; active/archived semester paint override удалён; status roles остаются на EntityStatus semantics; misuse spacing/dialog/width token не найден.

## QA числами

- Screens: 9; wrong size: 0; root: 6; detail/task: 3; bad Back/nav ownership: 0.
- Screen geometry overflow: 0; render overflow: 0; clipping defects: 0.
- Text overflow, unintended ellipsis, missing font, Inter: 0 / 0 / 0 / 0.
- Touch targets `<44×44`: 0; SearchField: `358×44`; Back: 3 × `44×44`.
- Horizontal registry/scroll: 0; expanded group rows: 1; stable cursor: `1–3 из 24`.
- Admin mutation, bulk, destructive, export, handoff controls: 0 / 0 / 0 / 0 / 0.
- Bottom nav: 6 × `358×76`, inset 16, selected count 1 per root, nested token-bound blur carriers 6, outside blur 0.
- Board: one top-level board, 15 direct children, 9 screens, 6 family labels, orphan/loose nodes 0.

## Render и visual verdict

- `_work/renders/part-22-23/admin-40-overview.png` — scale hierarchy clear; GO-with-notes for local nav copy.
- `_work/renders/part-22-23/admin-41-users-list.png` — compact search cards; GO-with-notes for inactive fact/master hint defect.
- `_work/renders/part-22-23/admin-41-user-detail.png` — read-only projection; GO.
- `_work/renders/part-22-23/admin-42-groups-list.png` — collapsed scan clean; GO.
- `_work/renders/part-22-23/admin-42-groups-expanded.png` — honest disclosure, stable cursor; GO.
- `_work/renders/part-22-23/admin-43-semesters.png` — grouped active/archive, no status override; GO.
- `_work/renders/part-22-23/admin-49-more.png` — one honest route, no fake map/handoff; GO.
- `_work/renders/part-22-23/admin-49-profile.png` — common profile pattern; GO.
- `_work/renders/part-22-23/admin-49-role-switch.png` — 4 large rows and explicit current role; GO.
- `_work/renders/part-22-23/admin-contact-sheet.png` — role-wide render; visual gaps/overlays/orphans not found.

## PWA / TMA ownership

- Root: product bottom nav visible; product Back absent; Telegram Back/MainButton hidden; keyboard hides nav.
- Detail/task: bottom nav absent. PWA standalone shows one product Back; browser-PWA relies on history; TMA replaces product Back with native BackButton.
- Role switch: action belongs to role row; no second MainButton/sticky action. After server role switch runtime must clear role-scoped history/cache and open the selected role root.
- Safe-area is shell/adapter-owned; fixed Telegram insets were not embedded in product frames.

## Изолированные gaps

- Owner decision: canonical root nav term `Пользователи` versus compact mobile copy `Аккаунты`.
- Master gap: `EntityStatusCell state=disabled` uses `color/disabled/text` and is unreadable for meaningful archived status; master change is forbidden in this party. Grouped semester layout avoids the defect without a synonym.
- `show-hint=false` does not reliably hide the disabled EntityStatusCell hint until nested visibility override.
- Admin user/group read-by-id and deep-link contracts are absent; mobile detail remains list projection only.
- Admin effective permissions, revisions/ETags, idempotency, structured conflicts, receipts and post-ACK readback are absent.
- Foreign-group roster/assign-headman, map asset/status API, Dashboard activity aggregate and executable desktop handoff contract are absent.

## Transport и процесс

- `whoami` succeeded; no HTTP 429 and no mutation timeout/partial duplicate.
- Unsupported `loadAllPagesAsync` and two unsupported component-property reads failed during reconnaissance; no mutation.
- One EntityStatus hint call errored after a virtual node disappeared; exact readback showed state, idempotent retry completed, duplicate 0.
- One hash exploration referenced `PAGE.visible` and failed read-only; a later brute-force read ended `Transport closed`; no mutation.
- Early extra read-only subagent `figma_recon` called Figma contrary to the one-orchestrator transport discipline; it was interrupted and created nothing.
- Profile clone inherited its page-25 donor parent transiently; closing ancestry pass moved it to page 26. Final page 25 agent-induced delta and duplicate count are 0.
- Three late Admin frames were initially direct page children; closing pass reparented them. Final page 26 has exactly one board and no loose nodes.

## Что не сделано и почему

- Не собраны blocked/desktop-only leaf-flow из coverage matrix: отсутствуют подтверждённые server contracts или mobile authority.
- Не собраны light, 320/360/430, tablet, desktop adaptive и PWA/TMA state matrix: вне этой партии.
- Не изменены master, token, registry и specs; требуемые исправления мастера вынесены как gap.
- Не опубликована библиотека: публикация принадлежит владельцу после завершения части.

## Изменённые файлы

- `journal/state/part-22-mobile-admin-before-2026-09-04.json` — новый baseline snapshot.
- `journal/state/part-22-mobile-admin-after-2026-09-04.json` — этот Gate A snapshot.
- `journal/reports/2026-09-04-part-22-mobile-admin.md` — этот отчёт.
- `_work/renders/part-22-23/*.png` — девять Admin renders, промежуточные evidence и contact sheet.

## ✏️ 04.09.2026 — дополнение после общего Gate B

Это append-only дополнение закрывает поля role-report, которые на Gate A были
отложены до общего transport и protected-page closing-pass. Оно не меняет
screen inventory и не отменяет прежние решения.

### Уточнение verdict

Итог Admin: `7 GO + 2 GO-with-notes`. Помимо search-list, обзор имеет
`GO-with-notes` из-за локальной dock-копии `Аккаунты` при route/H1
`Пользователи`. Поле `critic=GO` у overview в историческом Gate A JSON —
состояние до этой итоговой классификации, а не изменение Figma.

### MCP calls по типам

Admin phase — main-thread Figma ordinals `13–165`: `153` calls.

- `use_figma`: `129` — `122 completed`, `7 failed`;
- `get_screenshot`: `24 completed`;
- `search_design_system` и `whoami`: `0` внутри Admin phase.

Ей предшествовала общая main-thread разведка: `12` calls — `whoami 1`,
`search_design_system 1`, `use_figma 6` (`4 completed / 2 failed`) и
`get_screenshot 4`. Ранний лишний `figma_recon` выполнил ещё `7` read-only
calls (`whoami 1`, `get_metadata 1`, `use_figma 5`), был остановлен и ничего
не изменил. Все ordinals и типы записаны в общем machine ledger.

### Transport counters и recovery

- HTTP 429: `0`; mutation timeout: `0`; transport failure: `1` тяжёлый
  read-only brute-force pass; failed `use_figma`: `7` в Admin phase.
- Partial mutation: `1`: скрытие disabled hint успело примениться до потери
  virtual handle. Выполнены exact readback → idempotent completion → повторный
  readback; partial duplicate `0`, unresolved partial `0`.
- Profile donor-parent и три loose page children были containment defects,
  а не transport partials. Они исправлены на разрешённой page 26; финально
  direct page children `1`, board children `15`, loose/orphan `0`.

### Baseline и общая protected-page проверка

Admin before: page count `27`, Admin page absent. Admin Gate A: page count
`28`, page 26 `227 A`. Teacher-before и общий Gate B независимо повторили
page 26 как `227 A`, hash `967f2796`, bounds `0/0/908/5428`, direct child `1`.

Pages 16–24: factual и agent-induced `0 A`; page 25 factual
`2476 → 2611 A`, external concurrent owner delta `+135 A`, agent-induced `0 A`.
Старый Admin-before geometry hash не используется для ложного равенства:
его serializer не воспроизводим полностью. Доказательства agent-induced
`0 geometry` — mutation-target ledger, отсутствие shared-master mutations,
closing ancestry/orphan pass и полное совпадение A; Teacher phase дополнительно
имеет сопоставимые before/after hashes для pages 16–25.

### Проверенные token mappings и gaps

Проверены mobile nav blur `VariableID:4354:143`, selected/on-accent и inactive
text/icon bindings, semantic status carriers, local solid paints/strokes и
радиусы screen compositions. Новых bindings «по совпадению числа» не сделано.
Конкретные gaps остаются: disabled contrast и ненадёжный `show-hint=false` у
`EntityStatusCell`, а также page-local donor-matched radii без точного
semantic binding. Мастера и token definitions не менялись.

### Точные файлы и строки после Gate B

- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-22-mobile-admin-before-2026-09-04.json:1–66`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-22-mobile-admin-after-2026-09-04.json:1–101`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-22-23-mobile-transport-ledger-2026-09-04.json:1–118`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\to-owner.md:934–993`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-22-mobile-admin.md` — полный текущий файл; точный диапазон фиксируется последним closing-pass дополнением.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\_work\renders\part-22-23\admin-*.png` — renders и contact sheet; PNG не имеют строковых номеров.

## ✏️ 04.09.2026 — closing self-range

- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-22-mobile-admin.md:1–253`.
