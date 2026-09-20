# Teacher/admin — постоянный ремонт общих мастеров

Дата: 25.08.2026.

## 1. Ответы на вопросы

1. **`shared/TextInput`:** постоянно применён radius 10→16 у восьми веток и
   busy clip. Повторная дельта 30 auth / 48 student / 33 dark-headman кадров —
   0 / 0 / 0. В auth живые экземпляры сохранили radius 999.
2. **`shared/FileUploadField`:** восемь Dropzone постоянно переведены 10→24.
   Idle Dropzone изменён 360×268 FIXED→360×154 HUG; прежний пустой хвост 73 px
   устранён. Дельта — 0 / 0 / 0.
3. **`shared/DefinitionList`:** stack-строка 40→46, numeric pair 98→58,
   stack-корень 144→150. Шесть student-кадров 108 выросли 1853→1859 px;
   рост принят владельцем как устранение обрезки содержимого.
4. **Обрезка `DefinitionList`:** в мастере содержимое заканчивается ровно на
   46 и 58 px. Во всех шести кадрах 108 проверено 60 внутренних границ;
   вертикальных выходов — 0.
5. **`schedule/LessonCard`:** не менялся и не измерялся. Разные карточки на
   101 и 111 сохраняют свои радиусы по решению владельца.

## 2. Разбор живого файла

Исходные значения совпали с решением: `TextInput` — 9 проверяемых узлов с
radius 10 и binding `radius/md`; `FileUploadField` — 8 Dropzone с radius 10,
idle 360×268 FIXED; `DefinitionList` — 40 px при содержимом до 46 и 98 px при
содержимом до 58. Стоп-ворот не сработал.

Число развёрнутых экранных ссылок `TextInput` изменилось относительно
откатного отчёта: живой повторный обход дал 24 / 48 / 60 ссылок в 12 / 18 /
21 кадрах вместо 24 / 51 / 51 в 12 / 19 / 18. Это не A и не число
потребителей; состав физических кадров 30 / 48 / 33 совпал.

## 3. Постоянные изменения

| Мастер | До | После | Потребители A до→после |
|---|---|---|---:|
| `shared/TextInput` | radius 10, `radius/md` | radius 16, `radius/xl` | 56→56 |
| `shared/FileUploadField` | Dropzone 10; idle 268 FIXED | Dropzone 24; idle 154 HUG | 15→15 |
| `shared/DefinitionList` | item 40; numeric 98; stack 144 | item 46; numeric 58; stack 150 | 37→37 |

Экземпляры не создавались и не удалялись; поэтому число прямых физических A
до и после одинаково.

## 4. Геометрия кадров

| Шаг | Auth 30 | Student 48 | Headman dark 33 |
|---|---:|---:|---:|
| после `TextInput` | 0 | 0 | 0 |
| после `FileUploadField` | 0 | 0 | 0 |
| итог после `DefinitionList` | 0 | 6 | 0 |

Изменились только шесть кадров 108: dark/light × 1920/1440/1280, каждый
1853→1859 px. Остальные 72 измеряемых кадра сохранили размеры.

## 5. Закрывающие проверки

- `TextInput`: 9/9 узлов имеют radius 16 и binding `radius/xl`; отклонений 0.
- `FileUploadField`: 8/8 Dropzone имеют radius 24 и binding `radius/2xl`;
  idle HUG 154, содержимое до 138, нижний padding 16.
- `DefinitionList`: item 46 / content 46; numeric 58 / content 58;
  stack 150. В шести 108 — 0 вертикальных выходов из 60 проверок.
- Страницы 16, 17 и 18 вручную не правились: изменения пришли через мастера.

## 6. Что изменилось геометрически

- `FileUploadField state=idle`: 360×268→360×154.
- `DefinitionList stack item`: 960×40→960×46.
- `DefinitionList numeric-content / pair-5`: 404×98→404×58.
- `DefinitionList variant=stack`: 960×144→960×150.
- Шесть кадров 108: 1853→1859 px.
- `TextInput` и остальные измеренные кадры: 0 px.

## 7. Что не сделано и почему

- `schedule/LessonCard` не менялся и не измерялся по прямому решению владельца.
- Новые компоненты, варианты, переменные и токены не создавались.
- Публикация не выполнялась.

## 8. Изменённые файлы

- `design/figma-spec.md:944` — `TextInput`; `:1139` — `FileUploadField`;
  `:3575` — `DefinitionList`.
- `design/brandbook-v2.md:868` — глобальные радиусы `TextInput` и Dropzone.
- `journal/DECISIONS.md:480` — постоянное решение владельца по партии B.
- `_work/prompts/part-15-teacher-admin-repair-b.md:47` — отмена откатного
  ограничения и окончательный состав ремонта.
- `journal/reports/2026-08-25-part-15-teacher-admin-typography-repair-b.md:130`
  — дополнение прежнего откатного отчёта.
- `journal/reports/2026-08-25-part-15-teacher-admin-shared-repair.md:1` — этот
  закрывающий отчёт.
