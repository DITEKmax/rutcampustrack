# Part 26 — Teacher Profile role entry и единый mobile role-gradient

Дата: 05.09.2026. Figma: `design-system/ui-kit-rct`, file key
`VgVjQYWILLG9AC7Eh12VMk`.

## Ответы на вопросы

Строки смены роли в Teacher Profile не было, потому что закрытое решение
Part 24 прямо убирало второй entry point и оставляло его только на главной.
Новое указание владельца главнее: строка `Сменить роль` возвращена в
`5850:585`. Это отменяет Part 24 только для entry point Teacher Profile; роль
под именем и второй RoleSwitcher в карточку профиля не возвращались.

Кнопки исправлены на обеих ролях: Teacher `5810:145` и Admin `5896:452` /
`5901:455` теперь буквально используют gradient принятого Headman donor
`4997:144`, сохраняя собственные размеры, radius, текст и иконку.

## Разбор и стоп-ворота

- Задание сохранено отдельным Part 26. Оно затрагивает один Teacher route
  entry и fill трёх локальных screen-carrier; общего role-switch master здесь
  нет.
- Live baseline Admin перед любой записью дважды устойчиво дал **659 A** /
  `a2e0b1cb`, тогда как closing Part 25 записан как **668 A** / `f6e9fe24`.
  Расхождение — **−9 A**; обе величины посчитаны в одной единице A страницы
  26: PAGE исключён, внутрь instances обход не выполнялся.
- Разведка показала, что до Part 26 отсутствовали девять текстовых carrier и
  16 подписей KPI снова имели LEFT вместо принятого CENTER. Это не результат
  текущей записи: gradient-write не меняет A и не затрагивает KPI/copy.
  Внешнее состояние не откатывалось. Восстановление этих элементов остановлено
  до решения владельца — считать 659 A новым baseline или вернуть Part 25.
- Teacher baseline совпал с закрытием Part 24: **361 A** / `0e9802c5`.
  Headman donor page совпала с checkpoint: **2611 A** / `74980519`.
- Для Profile найден существующий pattern на Headman `5271:1011` и готовый
  task `5854:582`; новый screen, master или вариант не нужен.
- Code Connect в репозитории отсутствует: frontend ещё не создан, поиск
  `*.figma.ts|tsx|js|jsx` вернул 0 файлов. Все зависимости разрешены живыми
  узлами текущего Figma-файла; внешние assets не использовались.
- Блокирующих противоречий внутри узкого scope Part 26 после отделения
  предшествующего Admin delta: 0.

## Живой итог

| Page | До | После | Фактическая дельта партии |
|---|---:|---:|---:|
| 25 · Mobile · Headman | 2611 A / `74980519` | 2611 A / `74980519` | 0 A, hash без изменения |
| 26 · Mobile · Admin | 659 A / `a2e0b1cb` | 659 A / `a2e0b1cb` | 0 A, paint-only |
| 27 · Mobile · Teacher | 361 A / `0e9802c5` | 364 A / `c679c0c9` | +3 A |

Admin board остался 908×11700 px, 28 прямых детей: 23 product-frame и пять
family label. Teacher board остался 908×4532 px, 14 прямых детей и девять
product-frame. Все затронутые product-frame остаются 390×844 px.

Teacher Profile `5850:585` вырос с 21 до 24 A ровно на объявленные FRAME,
INSTANCE и TEXT. Других структурных изменений страницы 27 нет.

## Что изменено

### Teacher Profile

В `5850:586` после `profile-card` добавлена локальная строка:

- carrier `6035:2022`, 358×56 px, x16/y171;
- `icon/role-switch` instance `6035:2023`, master `4734:146`;
- текст `6035:2024` — `Сменить роль`, Onest SemiBold 15.

Окончательный порядок content: заголовок y20, identity-card y63, `Сменить
роль` y171, `Оформление` y239, `Безопасность` y307, `Активные сеансы` y375,
`История аккаунта` y443. Последняя строка заканчивается на y499, dock
начинается на y752: запас 253 px, переполнение 0 узлов.

Profile остаётся root: один dock, Back 0. Существующий task `5854:582`
остаётся task: dock 0, один Back; в нём четыре server-provided role option и
текущая роль отмечена состоянием строки/check, а не подписью `Текущая роль`.

### Единый role-gradient

У donor `4997:144` copied только `fills`:

- `GRADIENT_LINEAR`, opacity 1;
- stops: `#3B1A7A` в 0, `#5729A1` в 0,52, `#2E1F4D` в 1;
- transform `[[0.75, 0.2, 0.05], [-0.2, 0.75, 0.2]]`;
- stroke 0 paints, effect 0 effects.

Exact JSON-сверка закрывающего read-pass: donor = Teacher `5810:145` = Admin
`5896:452` = Admin `5901:455` по gradient paint. В Admin и Teacher banks иных
gradient-node после правки нет.

Контраст на худшем stop: primary text **7,93:1**, secondary icon **5,45:1**.
Это прямое owner-исключение для трёх названных mobile carrier. Оно не делает
raw gradient общим token/style и не переносит его на desktop, формы или auth.

## Геометрические изменения

Все размеры ниже — px с живого файла.

- Teacher Profile: добавлена строка 358×56 при y171; четыре прежние строки
  сдвинулись на +68 по y: 171→239, 239→307, 307→375, 375→443.
- Teacher role trigger сохранил 144×44 и radius 999.
- Оба Admin role trigger сохранили 166×44 и radius 24.
- Размеры трёх product-frame, обеих board и положение dock не менялись.
- Admin geometry hash остался `a2e0b1cb`; Headman donor hash остался
  `74980519`. Teacher hash изменился только из-за объявленной строки.

## Masters и потребители

Consumer-count ниже — прямые физические instances, это не единица A.

| Master | До | После | Причина |
|---|---:|---:|---|
| `icon/role-switch` `4734:146` | 2 | 3 | новый instance `6035:2023` в Teacher Profile |

После: `4805:3949`, `5271:1012`, `6035:2023`. Сам icon master не менялся.
Shared-master writes = 0; AppShell, AppHeader, dock, RoleSwitcher, buttons и
icons остались без записи. Поэтому обязательный auth/student remeasure для
изменения общего мастера не активировался. Auth writes = 0, Student writes =
0, Headman writes = 0; donor читался read-only.

## Литералы, variables и смысловые привязки

Проверка выполнена отдельным обходом после mutation-pass.

| Область | Donor/master | Screen QA |
|---|---|---|
| Role gradient | локальный donor 118×44, r24, raw linear-gradient, border/effect 0 | exact fill на трёх целях; Teacher 144×44/r999, Admin 166×44/r24 |
| Profile row | Headman pattern 358×56; icon master не менялся | Teacher 358×56; surface/text/icon semantic bindings сохранены; literal r18 |

- Unbound local SOLID fills/strokes: Admin 0 paints, Teacher 0 paints.
- Три raw gradient paints посчитаны отдельно как прямое owner-исключение, а
  не спрятаны в ноль solid-audit.
- Font family изменённых и проверенных product texts: только Onest; Inter 0
  text nodes; missing fonts 0 text nodes; collapsed text 0 nodes.
- Touch targets изменённых элементов меньше 44 px: 0 carriers.
- Outside-screen geometry: 0 nodes. Invalid root/task nav ownership: 0 screens.
- Проверка «привязки не по смыслу»: новый glyph остаётся на semantic
  text/icon binding, строка — на surface/text bindings. Неверных solid
  bindings в изменённых узлах: 0 paints.
- Пять Teacher Profile rows — `6035:2022`, `5850:597`, `5850:600`,
  `5850:603`, `5850:606` — имеют literal radius 18 px. Точного semantic token
  для этого значения нет; близкий token не подставлялся. Это известный
  неблокирующий design-system gap.

## Независимые gates

- Functional analyst: GO; маршрут, четыре роли, Back/dock и отсутствие
  ложной backend-логики подтверждены.
- Mobile architect: GO. Первоначальный NO-GO был отозван после доказательства,
  что Admin 659 A и сбитые Part 25 элементы уже существовали в baseline до
  root-write. Их статус — STOP/REPORT, а не дефект текущей партии.
- Critic/platform QA: запрошенные изменения GO; blockers 0 и polish defects 0
  внутри разрешённого scope. Безусловный closeout всей партии — HOLD, пока
  владелец не классифицирует предшествующую Admin-дельту 668→659 A. Donor
  неизменён, gradient exact, clipping 0, размеры и навигация сохранены.
- Финальный read-only checksum другим проходом повторил 2611/659/364 A и
  hashes `74980519` / `a2e0b1cb` / `c679c0c9`.
- Свежие full-screen renders: 4/4, каждый PNG непустой; все четыре просмотрены
  визуально.

## Требуемая классификация владельца

Нужно выбрать одно из двух, прежде чем считать весь Admin-bank повторно
закрытым:

1. `659 A / a2e0b1cb` и LEFT у 16 KPI label — намеренная поздняя правка.
   Тогда она датированно отменяет соответствующую часть Part 25 и становится
   новым Admin baseline.
2. Это случайная внешняя регрессия. Тогда отдельной repair-партией вернуть
   CENTER и девять смысловых text carrier с provenance, а не просто добрать
   счётчик до 668 A.

До этого решения четыре текущие owner-правки закончены и проверены, но полный
closeout Part 26 остаётся HOLD.

## Что не сделано и почему

- Не восстановлены девять отсутствующих Admin copy/text carrier и CENTER у
  16 KPI label: расхождение существовало до Part 26 и может быть внешней
  правкой владельца. Нужен явный выбор baseline; чужое состояние не
  нормализовано молча.
- Библиотека не публиковалась: публикацию выполняет владелец.
- Shared masters, components, variants, styles, tokens и variables не
  создавались и не менялись: все три paint correction локальны, а Profile
  использует существующие pattern/icon/task.
- Другие Profile и role triggers не менялись: owner scope назвал Teacher
  Profile и ровно три carrier.
- Light theme, desktop, дополнительные ширины и loading/empty/error/offline
  states не проектировались: они не входили в точечную правку.
- Runtime visibility при одной effective role и server ACK/session/cache
  contract смены роли не выдумывались; gaps записаны владельцу.
- Пять literal r18 не заменялись близким token и новый token не заводился без
  отдельного решения.
- `specs/` не редактировались: они принадлежат владельцу.

## Изменённые файлы

- `design/figma-spec-mobile.md`: строка 651 — датированная отмена Part 24 и
  mobile gradient exception;
- `journal/DECISIONS.md`: строка 2532 — новое решение владельца;
- `journal/to-owner.md`: строка 9 — runtime/token gaps и отмена прежнего числа
  profile rows;
- `_work/prompts/part-26-teacher-profile-role-entry-and-role-gradient-2026-09-05.md`:
  строка 1 — промпт партии;
- `journal/state/part-26-teacher-profile-role-entry-and-role-gradient-after-2026-09-05.json`:
  строка 1 — финальный state;
- `journal/reports/2026-09-05-part-26-teacher-profile-role-entry-and-role-gradient.md`:
  стабильная ссылка на строку 1;
- `_work/renders/part-26/01-teacher-today.png`,
  `02-teacher-profile.png`, `03-admin-overview-today.png`,
  `04-admin-overview-all-time.png`: binary, номера строк неприменимы.
