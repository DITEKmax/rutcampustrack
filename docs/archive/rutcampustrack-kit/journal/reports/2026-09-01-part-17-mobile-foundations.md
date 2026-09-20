# Part 17 — mobile foundations

Дата: 01.09.2026. Figma: `VgVjQYWILLG9AC7Eh12VMk`.

## 1. Ответы на вопросы

- Foundation-партия собрана и закрыта независимым readback и render-review.
- Массовой отметки нет: найдено 0 product actions и 0 attendance batch wiring.
- Checkbox только исследован: 0 мутаций, оба существующих владельца и их
  потребители совпадают с baseline.
- Новые variables и styles не создавались: решение по token batch остановлено
  до показа визуальных проб.
- Публикация не выполнялась.

## 2. Разбор и границы партии

### Что было непонятно или расходилось

1. В задании назван `design/mobile/ux-guide.md`; в workspace живой источник —
   `design/mobile/RutCampusTrack_PWA_TMA_UX_Guide.md`. Работа велась по живому
   файлу целиком; короткого пути в workspace нет.
2. Mobile-guide §12.5 ограничивает экран одной focal gradient area, но позднее
   прямое решение владельца разрешает для PWA/TMA несколько подсвеченных мест.
   Для mobile действует позднее решение; запрет кодировать статус градиентом и
   требование solid-подложки под dense data сохраняются.
3. Шапка закрытого component registry говорила, что PWA/TMA там не описаны.
   Владелец принял два новых публичных shared-смысла; реестр дополнен
   датированной записью с явной частичной отменой прежней границы.
4. Foundation prompt называл host action единым компонентом. После разбора
   физические носители оставлены в adapters/QA: Telegram Button принадлежит
   host, PWA sticky action — продукту. Общей остаётся semantic command, но
   ложный мастер общей геометрии не создан.
5. До сборки было два владельца точного смысла `shared/Checkbox`. Выбор одного,
   merge и rewiring остановлены как отдельный repair-gate.
6. `design/mobile/mobile-kit-placement.md` называл 47 462 A, живой baseline —
   47 483 A, расхождение 21 A. Baseline партии снят заново с живого файла.

### Что уже было закрыто и не входило

- Desktop и узкий web-адаптив завершены; страницы 16–20 — только контрольная
  группа, а не источник mobile-layout.
- Ролевые mobile-экраны, светлая тема, Checkbox repair и публикация не входили.
- Production-ответы по TMA launch points, MainButton policy, cache/privacy и
  filesLinks не блокировали статический foundation и остались открыты.

## 3. До → после: независимые снимки

Единица — A: физический узел дерева документа без DOCUMENT/PAGE и без захода
внутрь INSTANCE.

| Метрика | До | После | Дельта |
|---|---:|---:|---:|
| Физические страницы | 22 страницы | 25 страниц | +3 страницы |
| Последняя логическая страница | 20 | 23 | +3 |
| Полный файл | 47 483 A | 48 006 A | +523 A |
| Пакет страниц 1–13 | — | 33 061 A | — |
| Пакет страниц 14–25 | — | 14 945 A | — |
| Страницы 16–20 | 3 123 A | 3 123 A | 0 A |
| Новые variables | 246 variables | 246 variables | 0 variables |
| Новые styles | 13 styles | 13 styles | 0 styles |

Машиночитаемые снимки:

- before: `journal/state/mobile-zero-baseline-2026-09-01.json`;
- after: `journal/state/part-17-mobile-foundations-after-2026-09-01.json`.

Геометрическая дельта существующих узлов — 0. Все +523 A находятся только на
трёх новых страницах. Внутри новой партии до closing были исправлены:

- task card 158→82 px после удаления misleading local-draft attendance status;
- comment field Y 306→230 px;
- horizontal padding item нижней навигации 4→0 px для stress 320;
- luminous edge пробы B 2→1 px, чтобы не выглядеть focus-ring.

## 4. Страницы 21–23

| Страница | Page id | Корневой кадр | Геометрия | Вес |
|---|---|---|---:|---:|
| `21. Иконки — системные` | `4159:142` | `4160:142` | 1440×683 px | 73 A |
| `22. Основания — mobile` | `4159:143` | `4188:142` | 1440×4839 px | 230 A |
| `23. Компоненты — mobile` | `4159:144` | `4168:142` | 1340×3889 px | 220 A |

На page 21 один верхнеуровневый frame с каталогом 14 masters. На page 22 в
порядке идут восемь adapter-zone cards, shell matrix и три material probes.
На page 23 — private nav item states, два публичных component sets и пять QA
fixtures. QA не вложены в product masters.

## 5. Компоненты, варианты и потребители

### `shared/MobileBottomNav`

Set `4175:403`: 16 A, 3 варианта, 0→8 прямых потребителей.

| Вариант | Node id | Размер | Вес | Ширина item при 390 |
|---|---|---:|---:|---:|
| `slots=3` | `4175:281` | 390×76 px | 4 A | 122 px |
| `slots=4` | `4175:312` | 390×76 px | 5 A | 90,5 px |
| `slots=5` | `4175:353` | 390×76 px | 6 A | 71,6 px |

Padding 8 px, gap 4 px. Видимость, platform, route depth, keyboard и theme не
стали variant axes: ими владеет shell. В stress instance `4219:228` при
viewport 320 CSS px пять целей имеют минимум 57,59999×60 CSS px; 0 целей ниже
44×44 CSS px, 0 переносов подписей.

### `__MobileBottomNavItem`

Private set `4170:206`: 49 A, 8 вариантов по 6 A,
`selected=false|true × interaction=default|hover|pressed|focused`, 0→12
прямых потребителей внутри публичных nav masters. Вариант 64×60 px, padding
4/0/4/0 px, gap 4 px. Публичным Vue API не является.

### `shared/MobileSyncStatus`

Set `4178:271`: 31 A, 10 вариантов, 0→3 прямых потребителей. Матрица:
`presentation=inline|banner × state=local-draft|outbox|sending|synced|conflict`.
`stale`, `freshness`, `asOf`, `serverTime`, timestamp и `freshness-mark` —
0 вариантов и 0 текстовых совпадений. Все три потребителя находятся только в
runtime QA fixture; attendance task не использует local/outbox.

### Иконки

Все 14 masters имеют бокс 16×16 px, size binding `VariableID:46:2`, stroke
binding `VariableID:10:7`, unbound paints 0.

| Смысл | Node id | Вес | Прямые потребители после |
|---|---|---:|---:|
| `today` | `4161:143` | 3 A | 8 |
| `homework` | `4162:143` | 3 A | 0 |
| `attendance` | `4163:143` | 3 A | 0 |
| `profile` | `4163:844554` | 3 A | 0 |
| `requests` | `4164:143` | 2 A | 0 |
| `statistics` | `4164:844553` | 2 A | 0 |
| `map` | `4164:844558` | 2 A | 0 |
| `overview` | `4164:844563` | 5 A | 0 |
| `users` | `4165:143` | 3 A | 0 |
| `groups` | `4165:844554` | 4 A | 0 |
| `local-draft` | `4165:844561` | 3 A | 2 |
| `outbox` | `4165:844567` | 2 A | 2 |
| `sync` | `4167:143` | 2 A | 2 |
| `conflict` | `4167:844553` | 2 A | 2 |

Новых дубликатов найденного существующего смысла не создано. `Inter` в новых
страницах — 0 совпадений.

## 6. Оболочки и восемь adapter zones

| Кадр | Node id | Геометрия | Viewport | Вес | Back | Primary | Dock |
|---|---|---:|---:|---:|---|---|---:|
| PWA browser root | `4189:142` | 430×952 px | 390×844 px | 20 A | browser | product | 1 |
| PWA standalone task | `4189:229` | 430×952 px | 390×844 px | 16 A | product | product | 0 |
| TMA overview compact | `4190:215` | 430×764 px | 390×640 px | 20 A | none | product | 1 |
| TMA overview expanded | `4192:249` | 430×968 px | 390×844 px | 20 A | none | product | 1 |
| TMA task compact | `4193:283` | 430×764 px | 390×640 px | 17 A | Telegram | Telegram | 0 |
| TMA task expanded | `4199:287` | 430×978 px | 390×844 px | 18 A | Telegram | Telegram | 0 |

Для шести кадров: overflow 0, truncation 0, double Back 0, double bottom panel
0. В каждом compact-вьюпорте первое действие видно.

| Adapter zone | Spec node | Живое доказательство | Fallback |
|---|---|---|---|
| `hostChrome` | `4188:149` | browser/standalone/TMA shell + host fixture | product chrome только где host им не владеет |
| `primaryAction` | `4188:152` | PWA sticky против TMA `MainButton` | inline/product sticky |
| `viewport` | `4188:155` | TMA compact 640 и expanded 844 | stable last-known layout до пересчёта |
| `safeArea` | `4188:158` | shell annotations + keyboard fixtures | runtime CSS/Telegram insets, не фиксированное число |
| `keyboard` | `4188:161` | PWA и TMA keyboard fixtures | скрыть dock; вернуть MainButton после blur |
| `theme` | `4188:164` | runtime fixture | themeParams / prefers-color-scheme → semantic mode |
| `storageOffline` | `4188:167` | local/outbox/conflict fixture | server ACK; для attendance offline queue отсутствует |
| `filesLinks` | `4188:170` | open/download/external fixture | сохранить ссылку + inline capability notice |

## 7. Material probes, QA и stress

### Визуальные пробы

| Проба | Node id | Геометрия | Вес | QA literals |
|---|---|---:|---:|---:|
| A · restrained solid | `4202:142` | 430×942 px | 25 A | 0 |
| B · layered illumination | `4204:209` | 430×942 px | 29 A | 4 |
| C · Telegram-native solid | `4206:243` | 430×942 px | 26 A | 0 |

У B два intentional glow-overflow и четыре явно подписанных QA-only узла:
hero glow, active-nav glow, hero gradient и edge gradient 1 px. Они не входят
в product masters. После критики hotspot затемнён, opacity снижена, edge
ослаблен с 2 до 1 px, выбранный nav-item переведён с белой инверсии на
`accent/soft`, чтобы не конкурировать с белой CTA.

Рекомендация оркестратора: принять гибрид A+B — solid иерархию A как общий
core, illumination B только для текущего hero и активной навигации. C оставить
проверкой Telegram host shell, а не третьей отдельной темой продукта.

### QA fixtures

| Fixture | Node id | Геометрия | Вес |
|---|---|---:|---:|
| Core single check-in | `4219:142` | 572×800 px | 20 A |
| TMA host actions | `4221:243` | 572×496 px | 17 A |
| PWA keyboard | `4222:243` | 572×746 px | 23 A |
| TMA keyboard recovery | `4223:243` | 572×746 px | 25 A |
| Runtime theme/offline/files | `4225:243` | 572×602 px | 23 A |

Во всех пяти: overflow 0, truncation 0, цели <44 px — 0. В keyboard fixtures
dock=0. В TMA `MainButton` скрыт на время ввода; клавиша «Готово» принадлежит
IME и не изображает второе product action.

Stress 320 в foundation доказывает нижнюю навигацию, но не полный student root.
Полный 320 root с hero, строками, safe area и последним элементом обязан войти
в следующую student probe party.

Рендеры:

- `_work/mobile-foundations-material-probes.png`;
- `_work/mobile-foundations-shell-matrix.png`;
- `_work/mobile-foundations-qa-fixtures.png`;
- `_work/mobile-foundations-core-stress320.png`;
- `_work/mobile-foundations-bottom-nav-final.png`;
- `_work/mobile-foundations-sync-status-final.png`;
- `_work/mobile-foundations-nav-item-states-final.png`;
- `_work/mobile-foundations-page21-icons.png`.

## 8. Безопасность и запреты

- Mass attendance actions: 0.
- Attendance selection/checkbox wiring: 0.
- Attendance offline queue: 0.
- Checkbox instances на pages 21–23: 0.
- Основной Checkbox `71:86`: 4→4 варианта, 59→59 потребителей,
  348×236→348×236 px.
- Локальный auth Checkbox `1541:4015`: 4→4 варианта, 3→3 потребителя,
  384×236→384×236 px.
- Double Back: 0; double bottom panel: 0.
- QA nodes внутри product masters: 0.
- Публикация: 0.

## 9. Токены, стили, литералы и привязка по смыслу

Проверены существующие покрытия: semantic base/raised/float surfaces,
primary/secondary/muted/on-accent text, subtle/default/strong/accent/focus
borders, now/soft accent, success/warning/danger/info states, hairline/emphasis,
min-touch, icon 16/20, радиусы 10/12/16/24/full, spacing 0–40, Onest text
styles и эффекты raised/overlay/sticky-x.

Итог после readback: 6 collections, 246 variables
(157 color, 87 float, 2 string), 0 mobile-named variables; 0 paint, 10 text,
3 effect, 0 grid styles. Создано 0 variables и 0 styles.

| Запрос guide §12.6 | Результат foundation | Временный fallback |
|---|---|---|
| `gradient/hero/current` | кандидат только после выбора B/hybrid | `accent/soft` на solid surface |
| `gradient/edge/selected` | кандидат после выбора | solid accent border |
| `surface/accent-tinted` | существующий `color/accent/soft` проверен | raised surface |
| `material/glass/chrome/base` | не создан | float solid + raised effect |
| `material/glass/chrome/strong` | не создан | overlay solid |
| `surface/data/solid` | покрывается base/raised | raised surface |
| `radius/mobile/hero` | значение 24 покрыто `radius/2xl` | radius 16 compact |
| `shape/fluid/current` | mask preset, не scalar token; отложен | rounded 24 |
| `shape/cut/managed` | отложен до managed probe | rounded 16 |
| `pattern/campus/grid-or-dots` | asset + opacity; отложен | no pattern |
| `motion/morph/standard` | Figma motion не строится; durations есть | fade 120–180 ms в коде |
| `motion/morph/expressive` | отложен до Vue motion spec | standard morph/fade |
| `motion/feedback/press` | текущие pressed state layers покрывают probe | color/outline state |
| `elevation/floating/nav` | raised effect + border покрывают fallback | solid raised dock |
| `elevation/floating/batch` | не применён; mass attendance запрещён | overlay только для допустимого context action |
| `noise/subtle` | asset; отложен | none |

Пары master/QA: product masters имеют gradients/glow 0, unbound solid paints 0,
bad radius bindings 0, bad spacing bindings 0 и `Inter` 0; QA A/C имеют
литералы 0, QA B — ровно 4 подписанных material literals. Известный долг:
`radius/2xl` (`VariableID:1504:37363`) имеет scope `ALL_SCOPES`; значение верно,
scope не исправлялся без отдельного решения.

## 10. Независимые closing checks

- Полный A пересчитан новым depth-first обходом двумя пакетами 13+12 страниц,
  не рабочими счётчиками сборки: 33 061 + 14 945 = 48 006 A.
- Pages 21–23 отдельно дали 73 + 230 + 220 = 523 A.
- Pages 16–20 повторно дали 890 / 748 / 836 / 363 / 286 A, дельта 0 A.
- Variables/styles, Checkbox, components и consumers перечитаны после сборки.
- Shell, material, QA и stress отрендерены после последних исправлений и
  просмотрены отдельно от числового аудита.
- Closing master audit: overflow 0, truncation 0, bad font 0, unbound solid
  paints 0, product gradients/glows 0, stale selected icon overrides 0.

## 11. Транспорт, публикация и открытые решения

Три mutating-вызова завершились `Transport closed`: page 21 wrapper, private
nav item и TMA compact shell. Каждая мутация сначала была перечитана; все три
оказались закоммичены. Слепых повторов 0, незакоммиченных writes 0.

Непередаточных диагностических ошибок — 10, все без изменения canvas:
unsupported nested property, `findAncestor`, pre-append property,
unsupported diagnostic getter, `setPluginData`, `resolvedVariableModes`,
FRAME/SECTION assumption, два неверных readback match и syntax error closing
audit. После исправления каждый нужный проход выполнен успешно.

Публикация не выполнялась. Владельцу остаётся:

1. выбрать A, B или рекомендованный гибрид A+B;
2. после выбора разрешить или отклонить единый token batch;
3. отдельно открыть Checkbox repair-gate, когда он понадобится mobile-формам;
4. до production закрыть TMA launch points, MainButton policy, routes,
   theme precedence, cache/privacy, filesLinks и client matrix.

## 12. Изменённые файлы

- `design/COMPONENT_REGISTRY.md:227–280`; `design/figma-spec-mobile.md:1–194`; `journal/DECISIONS.md:2145–2170`; `journal/to-owner.md:650–670`; `journal/state/mobile-zero-baseline-2026-09-01.json:1–138`; `journal/state/part-17-mobile-foundations-run-2026-09-01.json:1–209` (closing append: 185–209); `journal/state/part-17-mobile-foundations-after-2026-09-01.json:1–263`; этот отчёт: строки 1–322. Бинарные render-артефакты без номеров строк: `_work/mobile-foundations-page21-icons.png`, `_work/mobile-foundations-page23-components.png`, `_work/mobile-foundations-page23-components-after.png`, `_work/mobile-bottom-nav-after-contrast.png`, `_work/mobile-foundations-nav-item-states-final.png`, `_work/mobile-foundations-bottom-nav-final.png`, `_work/mobile-foundations-sync-status-final.png`, `_work/mobile-foundations-shell-matrix.png`, `_work/mobile-foundations-material-probes.png`, `_work/mobile-foundations-qa-fixtures.png`, `_work/mobile-foundations-core-stress320.png`.

Figma: новые pages `4159:142`, `4159:143`, `4159:144`; существующие pages
16–20 не изменены. Публикация не выполнялась.

## 13. Что не сделано и почему

- Новые token values/variables/styles не созданы: владелец поставил gate до
  показа проб.
- Student probe и остальные ролевые экраны не собраны: одна партия — одна
  остановка; foundation сначала принимается владельцем.
- Полный root на stress 320 не собран: это обязательная часть student probe,
  а foundation проверяет только shell и navigation primitive.
- Светлая mobile-тема не собрана: dark принимается первой.
- Checkbox не ремонтировался: разрешён только read-only аудит.
- Не добавлены freshness/timestamps: политика свежести не принята.
- Не решены runtime-вопросы TMA: статический foundation не даёт права
  придумывать платформенный контракт.
- Публикация не выполнена: её делает только владелец.
