# Part 17 — mobile material: glass navigation and no decorative outlines

Дата: 01.09.2026. Figma: `VgVjQYWILLG9AC7Eh12VMk`.

## 1. Ответы на вопросы

- Glassmorphism применён к `shared/MobileBottomNav`; контент nav остаётся
  непрозрачным, стекло живёт отдельным фоновым слоем.
- Декоративные замкнутые обводки мобильных hero, строк и внешней плоскости nav
  удалены. Документационные shell/viewport frames не являются продуктовыми
  блоками и сохранены.
- Глубина строится surface/spacing и нейтральной `shadow/raised`. Новый
  цветной shadow token не создан: цветная illumination остаётся у текущей пары.
- Focus, поля/ошибки, secondary, dropzone, status ring, separators и
  increased/forced contrast не затрагивались.
- Массовая отметка не добавлена, Checkbox не менялся, светлая тема не
  собиралась, публикация не выполнялась.

## 2. Разбор и закрытые расхождения

1. Для OPACITY существовал спор единиц: предложение `0,78/0,90` конфликтовало
   с живым auth-токеном `60/84`. Трассировка binding доказала, что Figma хранит
   процентные пункты: `60` разрешается на node как `0,60`. Поэтому новые
   значения записаны `78/90`, а слой получает `0,78/0,90`.
2. Живой baseline содержал ровно 23 декоративных closed strokes по 1 px:
   20 fixture/product blocks и три nav masters. Все 23 закрыты до 0.
3. Detailed-аудит нашёл дополнительный старый QA-носитель рамки, который не
   был stroke: `QA ONLY · edge-gradient · fallback border/accent` 326×1 px,
   node `4204:220`. Новое решение владельца отменяет его; слой удалён. Поэтому
   page 22 имеет A-дельту −1 вместо первоначально ожидавшегося 0.
4. Два выхода probe B по X — `hero-glow` и `active-nav-glow` — намеренные
   подписанные QA-only тени. Они не являются геометрическим дефектом и
   сохранены.

## 3. Выбор решения

Выбрано:

- отдельный абсолютный `glass-surface` первым ребёнком каждого nav master;
- `surface/float` + opacity variable + bound background blur +
  `shadow/raised`, без border;
- borderless passive blocks при сохранении поверхностей, расстояний и одного
  осмысленного elevation;
- solid fallback без border: opacity 1, blur 0, surface/float,
  shadow/raised.

Отклонено:

- opacity на root nav: она приглушила бы иконки и подписи;
- auth-glass: другой смысл, opacity, blur и border contract;
- border + shadow + glow одновременно;
- отдельный цветной shadow token без самостоятельного смысла;
- тонкий gradient-edge вокруг hero;
- новая ось `material`: runtime fallback принадлежит shell/платформе.

## 4. Variables и Effect Style

- Variables: 246→248.
- Figma collection `Component`: 9→11.
- `component/mobile-bottom-nav/glass-surface-opacity`,
  `VariableID:4354:142`: FLOAT, OPACITY, dark 78, light 90, WEB syntax
  `var(--component-mobile-bottom-nav-glass-surface-opacity)`.
- `component/mobile-bottom-nav/glass-background-blur`,
  `VariableID:4354:143`: FLOAT, EFFECT_FLOAT, dark/light 20 px, WEB syntax
  `var(--component-mobile-bottom-nav-glass-background-blur)`.
- Effect Styles: 3→4.
- Новый `material/glass/chrome/base`,
  `S:74f40689948e6c97154baf3d34903f1bb263169b,`: BACKGROUND_BLUR radius связан
  с `VariableID:4354:143`; DROP_SHADOW color связан с существующим
  `color/shadow/raised` `VariableID:24:8`.
- Multiplicity новых имён: 1 / 1 / 1; дублей 0.
- `tokens-v2.json`: 252 entries после записи, component group 11.

## 5. Мастер и потребители

`shared/MobileBottomNav` set `4175:403`:

- variants `4175:281`, `4175:312`, `4175:353` остаются 390×76 px;
- созданы `glass-surface` `4355:319`, `4355:320`, `4355:321`;
- каждый 390×76, absolute 0/0, STRETCH/STRETCH, radius 24 через
  `radius/2xl`, opacity dark 0,78, stroke 0;
- root вариантов: fills 1→0, strokes 1→0, effects 2→0; материал перенесён на
  surface, а не удалён;
- item content opacity 1; порядок и размеры пунктов не изменились;
- прямые потребители 8→8; ширины 358×3, 390×4, 320×1;
- на всех восьми ширина glass-surface точно равна ширине instance;
- consumers на pages 16–20: 0 до и после.

## 6. Удалённые декоративные носители

- Три nav master strokes: 1 px→0.
- Семь current-lesson hero strokes: 1 px→0.
- Тринадцать lesson/list row strokes: 1 px→0.
- Итого closed decorative strokes: 23→0.
- Удалён QA edge-gradient `4204:220`, 326×1 px.
- Fill и effect style у 20 контентных блоков сохранены. Шесть foundation hero
  продолжают использовать `shadow/raised`; обычным строкам тень не добавлена.
- Новых иконок нет.

## 7. A и геометрия

| Страница | До | После | Дельта |
|---|---:|---:|---:|
| 16 auth | 890 A | 890 A | 0 A |
| 17 student | 748 A | 748 A | 0 A |
| 18 headman | 836 A | 836 A | 0 A |
| 19 teacher | 363 A | 363 A | 0 A |
| 20 admin | 286 A | 286 A | 0 A |
| 21 icons | 73 A | 73 A | 0 A |
| 22 foundations | 230 A | 229 A | −1 A |
| 23 components | 220 A | 223 A | +3 A |

MobileBottomNav set: 16→19 A с учётом set root; закрывающий обход отдельно
показал 18 descendants + 1 root. Существующие width/height изменились на
0 px. Геометрические изменения партии — три новых абсолютных surface 390×76
и удаление одного QA-слоя 326×1; геометрия остальных узлов 0.

## 8. Независимая проверка кадров

Проверены `4189:142`, `4190:215`, `4192:249`, `4202:142`, `4204:209`,
`4206:243`, `4219:145`, `4219:227` и мастер `4175:403`.

- actionable overflow X/Y: 0 / 0;
- intentional QA glow overflow X: 2;
- text overflow и лишние ellipsis: 0;
- прямоугольные подложки под скруглёнными элементами: 0;
- цели меньше 44×44 CSS px: 0;
- double bottom nav: 0; double Back: 0;
- перекрытие последней строки: 0; минимальный запас 100 px в compact TMA;
- первый CTA в compact TMA: Y 364, bottom 408 внутри root 764 px;
- material A/B/C CTA bottom: 348 / 348 / 384 px;
- core 390 CTA bottom 226 внутри 560 px;
- nav glass точно растягивается на 390 и stress 320;
- защищённые pages 16–20: A и геометрия без изменений;
- визуальный просмотр двух финальных renders: дефектов иерархии не найдено.

Рендеры:

- `_work/mobile-material-glass-borderless-final.png`;
- `_work/mobile-glass-core-stress-final.png`.

## 9. Транспорт

Транспортных отказов: 0. Два mutating-вызова отвечали дольше обычного, но
завершились полным commit. Слепых повторов 0, дублей 0, частичных writes 0.

## 10. Что не сделано и почему

- Light frames не собраны: dark принимается первой; light 90% — только режим
  утверждённой переменной.
- Student probe не начат в этой партии: правило процесса требует остановки и
  показа после одной material-партии.
- Массовая отметка не создана: запрещена.
- Checkbox не изменён: остаётся read-only исследованием.
- Colored shadow token не создан: отдельного смысла нет; существующая
  illumination hero уже несёт цветной акцент.
- Локальный `.agents/skills/rutcampustrack-design` не изменён: путь не входит
  в разрешённый список design-документов. Формулировка для синхронизации
  добавлена в `journal/to-owner.md`.
- Публикация не выполнена: её выполняет владелец.

## 11. Следующая партия

Student probe «Сегодня → текущая пара → отметка». Рекомендованный UX:
`Отметиться` в hero является commit и открывает task уже в checking/result —
1 product tap от «Сегодня»; OS permission считается отдельно. Сначала dark,
360 / 390 / 430 / stress 320, PWA browser/standalone и TMA overview/task,
затем обязательные states. Массовой отметки и Checkbox в пути нет.

## 12. Машиночитаемые снимки и изменённые файлы

- `_work/prompts/part-17-mobile-material-glass-no-border.md:1`;
- `journal/state/part-17-mobile-material-glass-before-2026-09-01.json:1`;
- `journal/state/part-17-mobile-material-glass-after-2026-09-01.json:1`;
- `design/tokens-v2.json:1151`;
- `design/brandbook-v2.md:803`;
- `design/mobile/RutCampusTrack_PWA_TMA_UX_Guide.md:1201`;
- `design/figma-spec-mobile.md:230`;
- `design/README.md:60`;
- `journal/DECISIONS.md:2207`;
- `journal/to-owner.md:707`;
- `_work/mobile-glass-nav-phase2.png`;
- `_work/mobile-material-glass-borderless-final.png`;
- `_work/mobile-glass-core-stress-final.png`;
- этот отчёт, строка 1.
