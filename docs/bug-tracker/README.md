# Bug Tracker — Ручное QA

Каталог багов/замечаний, найденных при ручном тестировании. Структурирован по **пулам** (версиям сбора): каждый пул — отдельная папка `vN-context/`, чтобы не смешивать баги, найденные в разных циклах QA.

## Структура

```
docs/bug-tracker/
├── README.md              ← этот файл (общая инструкция)
├── _TEMPLATE.md           ← шаблон одного report.md
├── INDEX.md               ← глобальный индекс всех пулов
└── vN-context/
    ├── INDEX.md           ← локальный индекс этого пула
    └── BUG-NNN-slug/
        ├── report.md
        └── *.png / *.mp4 / *.log  (артефакты)
```

## Как создать новый пул

Когда после очередного релиза/milestone проводится новый ручной QA-сеанс:

1. Создай папку `vN-context/`, где:
   - `vN` — инкрементная версия пула (`v1`, `v2`, ...)
   - `context` — короткий описательный суффикс: `post-v9.0`, `post-m16`, `pre-release-audit` и т.п.

   Пример: `v2-post-v10.0/`

2. Скопируй `_TEMPLATE.md` для шаблона INDEX'а (или просто создай `vN-context/INDEX.md` по аналогии с существующими пулами).

3. Добавь строку в глобальный `INDEX.md` с кратким описанием пула.

## Как добавить баг в текущий пул

1. **Создай папку:** `vN-context/BUG-NNN-короткое-название/` (NNN — автоинкремент в рамках пула, начиная с 001).
   Пример: `v1-post-v9.0/BUG-010-pwa-offline-broken/`

2. **Внутри папки:**
   - `report.md` — описание бага (скопируй из `../../_TEMPLATE.md`)
   - любые скриншоты/видео/логи с произвольными именами

3. **Добавь строку в локальный `INDEX.md`** этого пула.

4. **Ссылки в `report.md`** — относительные, внутри своей папки:
   ```markdown
   ![Mobile overflow](./screenshot-mobile.png)
   [Console log](./console.log)
   ```

## Структура `report.md`

```markdown
# BUG-NNN: Краткое название

**Найден:** YYYY-MM-DD
**Severity:** blocker / critical / major / minor / cosmetic
**Status:** open / in-progress / fixed / wontfix / duplicate
**Component:** pwa / web-panel / mini-app / landing / backend:auth / backend:academic / backend:schedule / backend:attendance / backend:notification / infra
**Role affected:** ADMIN / TEACHER / STUDENT / HEADMAN / all / anonymous
**Phase reference (если связано):** v9.0 / Phase NN (опционально)

## Описание
<Что происходит. Одно-два предложения.>

## Шаги для воспроизведения
1. ...
2. ...
3. ...

## Ожидаемое поведение
<Как должно работать>

## Фактическое поведение
<Что происходит вместо этого>

## Окружение
- Браузер/устройство:
- URL:
- Пользователь/роль:
- Дата/время:
- Сеть:

## Скриншоты / видео
- ![screenshot](./screenshot-mobile.png) — <что на скрине>
- [repro.mp4](./repro.mp4) — <описание видео>

## Логи / console / network (опционально)
<куски из DevTools console / backend logs, или ссылка на ./console.log>

## Проанализированные причины (опционально)
<Если уже есть догадка>

## Предполагаемый fix (опционально)
<Какой файл/компонент править>

## Fix (заполнить когда status = fixed)
- Commit(s):
- Phase/plan:
- Notes:
```

## Severity guide

- **blocker** — фича полностью не работает, блокирует других пользователей. Пример: «login падает 500, никто не может войти»
- **critical** — потеря данных, security, или core flow сломан. Пример: «отметка не сохраняется»
- **major** — фича работает неправильно, есть workaround. Пример: «красная зона считается с неверным порогом, но журнал корректный»
- **minor** — мелкая ошибка, не влияет на основной flow. Пример: «неправильный падеж в notification»
- **cosmetic** — визуальная погрешность. Пример: «кнопка на 2px съезжает при theme toggle»

## Status workflow

```
open → in-progress → fixed
           ↓
       wontfix / duplicate
```

Когда баг `fixed`: заполнить `## Fix` секцию в `report.md` (commit SHA, phase/plan). Папку **НЕ удалять** — она остаётся как история. Локальный `INDEX.md` обновить — переместить строку в раздел Fixed.

## Что НЕ сюда

- **Feature requests / enhancements** → `/gsd-add-backlog` или `/gsd-note`
- **Security findings (systematic)** → `docs/security/SECURITY-AUDIT.md` или скилл `cso`
- **UX findings (systematic)** → отдельный UX-audit отчёт после скилла `ux-audit`

## История переноса

До 2026-04-27 этот каталог жил в `.planning/bug-reports/`. Перенесён в `docs/bug-tracker/` потому что bug tracker — это часть **продуктовой документации**, а не GSD-инструментария. Существовавшие 9 багов помещены в `v1-post-v9.0/` (найдены при ручном QA после релиза v9.0).
