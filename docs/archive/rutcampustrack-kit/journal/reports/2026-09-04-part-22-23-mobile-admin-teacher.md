# Parts 22–23 — Mobile Admin + Teacher, общий Gate B

Дата: 04.09.2026. Figma: `VgVjQYWILLG9AC7Eh12VMk`. Порядок работы: Admin Gate A → Teacher Gate B.

## Ответы на вопросы

- Основной prompt выполнен как единая партия: Admin полностью закрыт внутренним QA, затем без ожидания владельца собран Teacher; финальная остановка происходит только сейчас.
- Предыдущий task Headman `01a0615f-80e5-75f3-9e1c-5101b9f32874` прочитан и использован как live mobile precedent. Отличающийся исторический ID внутри prompt отмечен как организационное расхождение.
- Блокирующих всю партию продуктовых вопросов не возникло. Неподтверждённые leaf-flow изолированы и не превратились в фиктивные права, API или success states.
- Созданы ровно две новые страницы в существующем файле: `26 · Mobile · Admin` и `27 · Mobile · Teacher`. Отдельный файл, light, другие ширины и публикация не создавались.

## Итог партии

- Admin: 9 product frames, `220 A` в кадрах, `227 A` на page 26.
- Teacher: 8 product frames, `294 A` в кадрах, `300 A` на page 27.
- Всего: 17 product frames, `514 A` внутри кадров, 11 family labels + 2 boards, `527 A` на двух новых страницах.
- Размер всех product frames: `390×844 px`; dark canonical; неправильных размеров 0.
- Verdict: Admin `7 GO + 2 GO-with-notes`; Teacher `8 GO`; unresolved `NO-GO` 0.
- Каждый frozen supported frame имеет отдельный readback и individual render; обе роли имеют closing contact sheet.

Figma:

- Admin: `https://www.figma.com/design/VgVjQYWILLG9AC7Eh12VMk?node-id=5631-142`
- Teacher: `https://www.figma.com/design/VgVjQYWILLG9AC7Eh12VMk?node-id=5807-142`

## Screen maps

### Admin

`Обзор → Аккаунты → server search/list → Пользователь projection → Back`

`Группы → одна inline раскрыта → тот же root state`

`Ещё → Семестры → Back`

`Профиль → Сменить роль → root выбранной роли`

### Teacher

`Сегодня → текущая Пара → read-only roster → Back`

`Учёт / Посещаемость → filters → выбранная Пара → roster`

`Итоги / Статистика ⇄ По студентам / По моим группам → group student lens`

`Карта → внешний maps link`

`Профиль → Сменить роль → root выбранной роли`

## Admin: кадры и переходы

| Family | Кадр | Назначение → следующий переход | A | Render |
|---|---|---|---:|---|
| 40 Overview | `40 · Обзор / default` | Три подтверждённых counter → root-tab | 20 | `_work/renders/part-22-23/admin-40-overview.png` |
| 41 Users | `41 · Пользователи / search-list` | Server search и три records → projection detail | 36 | `_work/renders/part-22-23/admin-41-users-list.png` |
| 41 Users | `41 · Пользователь / detail из списка` | Read-only projection Ирины Соколовой → Back | 16 | `_work/renders/part-22-23/admin-41-user-detail.png` |
| 42 Groups | `42 · Группы / list` | Три compact records → inline disclosure | 35 | `_work/renders/part-22-23/admin-42-groups-list.png` |
| 42 Groups | `42 · Группы / одна раскрыта` | Одна expanded row, cursor стабилен → collapse | 44 | `_work/renders/part-22-23/admin-42-groups-expanded.png` |
| 43 Semesters | `43 · Семестры / list` | Active/archive sections → Back | 19 | `_work/renders/part-22-23/admin-43-semesters.png` |
| 49 Common | `49 · Ещё / default` | Единственный прошедший data-gate route → Семестры | 11 | `_work/renders/part-22-23/admin-49-more.png` |
| 49 Common | `49 · Профиль / default` | Account routes → role switch/settings | 25 | `_work/renders/part-22-23/admin-49-profile.png` |
| 49 Common | `49 · Профиль / смена роли` | Четыре роли, current Admin → selected root | 14 | `_work/renders/part-22-23/admin-49-role-switch.png` |

Contact sheet: `_work/renders/part-22-23/admin-contact-sheet.png`.

## Teacher: кадры и переходы

| Family | Кадр | Назначение → следующий переход | A | Render |
|---|---|---|---:|---|
| 30 Today | `30 · Сегодня / default` | Current/next lesson и assignments → Пара | 34 | `_work/renders/part-22-23/teacher-30-today.png` |
| 31 Attendance | `31 · Пара / roster` | 27 read-only student facts → Back | 93 | `_work/renders/part-22-23/teacher-31-roster.png` |
| 31 Attendance | `31 · Посещаемость / lessons` | Group/subject/type filters и lessons → roster | 34 | `_work/renders/part-22-23/teacher-31-lessons.png` |
| 32 Statistics | `32 · Статистика / по студентам` | Две student cards × 4 metrics → filters/group tab | 44 | `_work/renders/part-22-23/teacher-32-students.png` |
| 32 Statistics | `32 · Статистика / по моим группам` | Disabled reason + two group cards → student lens | 43 | `_work/renders/part-22-23/teacher-32-groups.png` |
| 34 Map | `34 · Карта / default` | Shared empty floor map → external maps | 7 | `_work/renders/part-22-23/teacher-34-map.png` |
| 39 Common | `39 · Профиль / default` | Иван Кузнецов и account routes → role switch | 25 | `_work/renders/part-22-23/teacher-39-profile.png` |
| 39 Common | `39 · Профиль / смена роли` | Четыре роли, current Teacher → selected root | 14 | `_work/renders/part-22-23/teacher-39-role-switch.png` |

Contact sheet: `_work/renders/part-22-23/teacher-contact-sheet.png`.

## Сравнительная role-матрица

| Смысл / элемент | Admin mobile | Headman accepted precedent | Teacher mobile |
|---|---|---|---|
| Attendance status carrier | Не используется как Teacher journal control; admin entity status — read fact внутри record | Может быть direct attendance action при подтверждённом праве и write contract | Только пассивный факт `символ + слово + цвет`; edit targets 0 |
| Chevron / next | Открывает projection detail или локальный disclosure | Может вести в task/editor/detail по принятому flow | Ведёт к lesson/group lens; status badge сам не интерактивен |
| Dense list | Server search/cursor; максимум одна expanded group row | Допускает оперативные действия роли | Read-only roster с контролируемым vertical overflow |
| Domain write | Исключён без permission/revision/idempotency/conflict/ACK | Допустим только в уже принятых Headman contracts | Полностью исключён для attendance/statistics |
| Personal student detail | User account projection только из list-record | Допустим в собственном role scope по принятому банку | Запрещён late specs 122–123; 0 кадров |
| Map | Admin authoring/status leaf исключён | Принятый общий root-pattern | Точный shared read-only map; backend assets pending |
| Dock labels | `Обзор · Аккаунты · Группы · Ещё · Профиль` | Принятые compact labels Headman | `Сегодня · Учёт · Итоги · Карта · Профиль` |

Одинаковая круглая attendance-графика не получает одинаковое поведение по сходству формы: у Headman она может быть действием только при подтверждённом праве, у Teacher это факт и не touch target.

## Ключевые role boundaries

### Admin

- Probe: `Обзор → Аккаунты → search/list → projection detail`.
- Create/edit user, role/status mutation, assign-headman, bulk, destructive, export, semester writes, map authoring и фиктивный handoff отсутствуют.
- User detail не обещает read-by-id/deep link; он отображает уже загруженную запись.
- Groups раскрывают максимум одну строку; cursor остаётся `1–3 из 24`.
- Семестры разделены headings `Активный семестр` / `Архив`, чтобы не использовать дефектный disabled-status contrast и не делать paint override.

### Teacher

- Attendance имеет `0` edit/bulk/review/commit/grading controls.
- Roster: `12 present + 6 excused + 5 absent + 4 none = 27`; badge — fact, не target.
- Источник/actor/time отметки не показаны без privacy projection.
- Персональный student detail намеренно исключён поздними specs 122–123.
- Group statistics показывает точную disabled-причину неподдержанного межгруппового сравнения.
- Map переиспользует `4456:955` без admin controls и без фиктивной связи с расписанием.

## PWA / TMA ownership

- 12 root frames: один product dock, product Back 0.
- 5 detail/task frames: один product Back, dock 0, MainButton 0.
- Canonical frame показывает standalone PWA. Browser-PWA использует history; TMA заменяет product Back native BackButton. Safe area и keyboard принадлежат adapter/shell.
- Role switch не получает второй sticky action. После server ACK runtime очищает role-scoped history/cache и открывает выбранный root; сам ACK contract остаётся gap.
- Полный platform matrix не строился; Telegram-specific дублирующие кадры отсутствуют.

## Общий QA summary

- Product frames: 17; wrong size: 0; roots: 12; detail/task: 5; double Back/nav/action: 0.
- Product A: `220 + 294 = 514`; page A: `227 + 300 = 527`.
- Bottom nav: 12 × `358×76`, `x=16`, `y=752`, bottom inset 16; selected item 1; nested blur carrier 1; blur radius 25; outside nav blur 0.
- Selected nav icon+label use `VariableID:10:20`; inactive use `VariableID:10:6`; final mismatches 0.
- Product Back: `3 Admin + 2 Teacher = 5`, each `44×44`; MainButton 0.
- Uncontrolled bounds overflow / clipping defects / horizontal registries: `0 / 0 / 0`.
- Controlled overflow: Teacher roster only — content `358×1728` inside `358×576`, vertical continuation `1152`, horizontal escape 0.
- Text overflow / unintended ellipsis / Inter / missing fonts: `0 / 0 / 0 / 0`.
- Touch targets `<44×44`: 0; passive 24px attendance badges are not targets.
- Local unbound solid fills / strokes / gradients / image fills: `0 / 0 / 0 / 0`.
- Shadows / glow / layer blur outside nav: `0 / 0 / 0`.
- Admin bulk/destructive/export/handoff: `0 / 0 / 0 / 0`.
- Teacher write/bulk/grading/personal detail: `0 / 0 / 0 / 0`.
- Shared-master mutations / created tokens / created components / created icons / publication: `0 / 0 / 0 / 0 / 0`.
- Board hierarchy: Admin 1 board + 15 children; Teacher 1 board + 13 children; loose/orphan/duplicate nodes 0.

## Геометрия и protected-page delta

Новые страницы:

- page 26 `0 → 227 A`, board `0×0 → 908×5428 px`, hash `967f2796`;
- page 27 `0 → 300 A`, board `0×0 → 908×4532 px`, hash `5d35f2ee`.

Teacher-before → Gate B использует один полностью записанный FNV serializer; pages 16–25 совпали по A, hash и bounds:

| Page | A | Hash | Factual ΔA / geometry | Agent-induced |
|---|---:|---|---|---|
| 16 Auth | 890 | `455cd388` | 0 / 0 | 0 / 0 |
| 17 Student | 748 | `5bc52169` | 0 / 0 | 0 / 0 |
| 18 Headman desktop | 836 | `0acf793c` | 0 / 0 | 0 / 0 |
| 19 Teacher desktop | 363 | `96e171a8` | 0 / 0 | 0 / 0 |
| 20 Admin desktop | 286 | `401f7db5` | 0 / 0 | 0 / 0 |
| 21 Icons | 142 | `0ce38243` | 0 / 0 | 0 / 0 |
| 22 Mobile foundations | 229 | `dea5888a` | 0 / 0 | 0 / 0 |
| 23 Mobile components | 796 | `a83b3f15` | 0 / 0 | 0 / 0 |
| 24 Mobile Student | 2139 | `d4743e24` | 0 / 0 | 0 / 0 |
| 25 Mobile Headman | 2611 | `74980519` | 0 / 0 during Teacher | 0 / 0 |

Across the earlier Admin phase, page 25 factual A changed `2476 → 2611`, `+135 A`; mutation ledger and no-master policy classify it as `external concurrent owner delta`, never as agent work. Pages 16–24 stayed at `890 / 748 / 836 / 363 / 286 / 142 / 229 / 796 / 2139 A`.

The Admin-before geometry hash serializer was not reproducible enough for a truthful before/after equality claim. Therefore agent-induced geometry evidence for that phase is the exact mutation-target ledger, `0` shared-master writes, independent ancestry/orphan pass and unchanged A; no baseline was restored over owner work.

## Consumers и token audit

- MobileBottomNav expanded consumers: common start 69 → Admin 75 → Teacher 81; only 12 new instances, master changes 0.
- Teacher additions: map `3→4`, previous `34→37`, next `61→71`, chevron-down `232→239`; attendance variants `32→44 / 8→14 / 9→14 / 5→9`.
- Nav blur token `VariableID:4354:143`, selected `VariableID:10:20`, inactive `VariableID:10:6` verified.
- Teacher selected-tab `r16` → exact `radius/xl`; profile card `r24` → exact `radius/2xl`; avatar and Back → `radius/full`, effective geometry unchanged.
- Five inherited Teacher profile rows remain literal `r18`: exact semantic token absent. No numeric-neighbor substitution and no new token.
- Clean rebuild of 27 labeled AttendanceStatusBadge instances reproduced root width/height override fields; this is a named HUG master gap for another party.
- Admin gaps remain `EntityStatusCell disabled` contrast and unreliable `show-hint=false`; masters were not changed.
- No semantic bindings to unrelated spacing/dialog/width variables were accepted.

## Transport summary

Main task Figma calls: `292`; early extra read-only `figma_recon`: `7`; combined: `299`.

| Phase | Calls | Breakdown |
|---|---:|---|
| Common main recon | 12 | whoami 1; search 1; use_figma 6; screenshots 4 |
| Admin | 153 | use_figma 129 (`122 ok / 7 failed`); screenshots 24 |
| Teacher | 127 | use_figma 100 (`96 ok / 4 failed`); screenshots 26; search 1 |
| Extra read-only recon | 7 | whoami 1; metadata 1; use_figma 5 |

Combined tools: whoami 2, metadata 1, search 2, use_figma `227 completed + 13 failed`, screenshot 54.

- HTTP 429: 0; mutation timeout: 0; transport failure: 1 read-only heavy pass.
- Partial mutation: 1 Admin hint visibility operation; exact readback and idempotent completion succeeded. Partial duplicate 0; unresolved partial 0.
- Teacher transport partials: 0. Four failed reads were replaced by compatible bounded audits.
- `figma_recon` violated the orchestrator-only transport rule, was interrupted and created/changed 0 nodes. It is preserved in the ledger, not hidden.
- Admin Profile transient donor-parent placement and three loose frames were repaired on page 26; final page25 agent-induced delta and final loose nodes are 0.
- No blind retry after mutation timeout occurred.

Exact per-call ordinal classification is in `journal/state/part-22-23-mobile-transport-ledger-2026-09-04.json`.

## Owner-only gaps одним пакетом

1. Approve or reject compact dock copy: Admin `Аккаунты`, Teacher `Учёт` and `Итоги`, while domain route/H1 names stay full.
2. Admin write contracts: effective permissions, revision/ETag, idempotency, structured validation/conflict, ACK and post-ACK readback for users, roles, groups, headman assignment and semesters.
3. Admin read-by-id/deep-link for user detail, foreign roster/eligibility, map status/read-model, Dashboard activity aggregate and executable desktop handoff.
4. Teacher exact read endpoints/auth for day, lesson list, roster/journal and statistics; aggregates, denominators, rounding, filters and snapshot revision.
5. Teacher privacy projection for attendance source/actor/manual-auto/changed-at. Personal student detail stays prohibited unless a later direct owner decision changes specs 122–123.
6. Map floor assets, stable floor IDs, catalog/loader and authorization.
7. Role-switch bootstrap/ACK and role-scoped cache/history clearing.
8. Owner decision for five profile-row `r18` literals and possible labeled AttendanceStatusBadge HUG master repair; both require a separate master/token party.
9. Scope of the next visual party: platform adapters, 320/360/430, keyboard, state matrix, long data, increased contrast and light.

Full append-only wording is in `journal/to-owner.md:934–993`.

## Что не сделано и почему

- No unsupported Admin/Teacher leaf-flow was drawn merely to fill coverage.
- No light, 320/360/430, tablet, desktop-responsive or full PWA/TMA variants: explicitly outside scope before owner review.
- No loading/error/offline/stale/conflict/freshness matrix except an already supported map empty-state: missing contracts and next-party scope.
- No shared master, token definition, registry, desktop page or `specs/` file changed.
- No library publication and no browser/native Figma control.
- No next party started; the required terminal condition is manual owner review.

## Изменённые файлы

- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-22-mobile-admin-before-2026-09-04.json:1–66`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-22-mobile-admin-after-2026-09-04.json:1–101`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-23-mobile-teacher-before-2026-09-04.json:1–60`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-23-mobile-teacher-after-2026-09-04.json:1–144`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\state\part-22-23-mobile-transport-ledger-2026-09-04.json:1–118`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-22-mobile-admin.md` — existing Gate A report plus append-only Gate B supplement.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-23-mobile-teacher.md` — Teacher role report.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-22-23-mobile-admin-teacher.md` — этот общий отчёт.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\to-owner.md:934–993` — append-only owner-only package.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\_work\renders\part-22-23\*.png` — Admin/Teacher individual renders, references and contact sheets; PNG не имеют строковых номеров.

Точные self-ranges трёх отчётов фиксируются последним append-only closing-pass дополнением после проверки файлов.

## ✏️ 04.09.2026 — closing report ranges

- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-22-mobile-admin.md:1–253`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-23-mobile-teacher.md:1–265`.
- `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-22-23-mobile-admin-teacher.md:1–241`.

## ✏️ 04.09.2026 — уточнение Teacher IA после независимого review

Уточнение отменяет только двусмысленную сокращённую стрелку в прежней строке
Today; product inventory, Figma и Gate B verdict не меняются.

- Primary path: `Сегодня → current lesson → roster`. Canonical parent roster —
  выбранная пара; `Посещаемость / lessons` — alternate entry/history source к
  тому же lesson-detail, а не второй canonical parent.
- Today assignment routes различаются: group assignment → Statistics с group
  context; subject assignment → Attendance с subject/group context. Назначения
  не ведут в roster автоматически.
- Attendance и Statistics кадры являются явно contextual success states с
  видимыми выбранными filters. Cold-entry/no-context, reset и restoration policy
  не построены и не включены в заявленный tap budget; это отдельная state matrix.
- Совет принял static success projections по late specs 121–123 и live desktop
  fixtures. Это не подтверждает backend readiness: read/auth/aggregate gaps
  сохраняются и family labels находятся вне product frames.
- Compact `Аккаунты`, `Учёт`, `Итоги` остаются adapter-copy при полных route/H1;
  их долговременная терминология вынесена владельцу, но текущий source-supported
  выбор не считается unresolved NO-GO.
- Teacher map — honest empty-state точного shared viewer. External row
  `Открыть корпус в картах` заимствован из принятого common Student pattern и
  не заявляет внутренний map endpoint или загруженный floor asset.

## ✏️ 04.09.2026 — финальные report ranges после IA-уточнения

- Admin: `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-22-mobile-admin.md:1–253`.
- Teacher: поздний диапазон `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-23-mobile-teacher.md:1–296` отменяет прежние 1–265.
- Общий: поздний диапазон `C:\Users\maksd\ruttrack\rutcampustrack-kit\journal\reports\2026-09-04-part-22-23-mobile-admin-teacher.md:1–271` отменяет прежние 1–241.
