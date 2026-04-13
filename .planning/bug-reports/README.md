# Bug Reports — Ручное тестирование

Эта папка — для накопления багов/замечаний, найденных при ручном QA (начато после закрытия v9.0, 2026-04-13). Переиспользуется для всех будущих milestone.

Каждый баг = **отдельная папка** `BUG-NNN-краткое-название/` со своим `report.md` и всеми артефактами (скрины, видео, логи) внутри.

## Как добавлять баги

1. **Создай папку:** `BUG-NNN-короткое-название/` (NNN — автоинкремент по `INDEX.md`: 001, 002, ...).
   Пример: `BUG-001-headman-journal-mobile-overflow/`

2. **Внутри папки:**
   - `report.md` — описание бага (скопировать из `../_TEMPLATE.md`)
   - любые скриншоты, видео, логи — с произвольными именами
   - Пример содержимого:
     ```
     BUG-001-headman-journal-mobile-overflow/
     ├── report.md
     ├── screenshot-mobile.png
     ├── screenshot-desktop.png
     ├── console.log
     └── repro.mp4
     ```

3. **Добавь строку в `INDEX.md`** (одна строка на баг).

4. **Ссылки в `report.md`** — относительные, внутри своей папки:
   ```markdown
   ![Mobile overflow](./screenshot-mobile.png)
   [Console log](./console.log)
   ```

## Структура report.md

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

- **blocker** — фича полностью не работает, блокирует других пользователей. Пример: "login падает 500, никто не может войти"
- **critical** — потеря данных, security, или core flow сломан. Пример: "отметка не сохраняется"
- **major** — фича работает неправильно, есть workaround. Пример: "красная зона считается с неверным порогом, но журнал корректный"
- **minor** — мелкая ошибка, не влияет на основной flow. Пример: "неправильный падеж в notification"
- **cosmetic** — визуальная погрешность. Пример: "кнопка на 2px съезжает при theme toggle"

## Status workflow

```
open → in-progress → fixed
           ↓
       wontfix / duplicate
```

Когда баг `fixed`: заполнить `## Fix` секцию в `report.md` (commit SHA, phase/plan). Папку НЕ удалять — она остаётся как история.

## Индекс багов

Ведётся вручную в `INDEX.md`. Каждая запись — одна строка:

```markdown
- [BUG-001](./BUG-001-headman-journal-mobile-overflow/report.md) — <одна строка описания> (severity, status)
```

## Что НЕ сюда

- Feature requests / enhancements → `/gsd-add-backlog` или `/gsd-note`
- Security findings (systematic) → `.planning/milestones/v9.0-security-audit/` (после `cso` skill)
- UX findings (systematic) → `.planning/milestones/v9.0-ux-audit/` (после `ux-audit` skill)
