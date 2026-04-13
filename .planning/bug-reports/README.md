# v9.0 Bug Reports — Ручное тестирование после закрытия milestone

Эта папка — для накопления багов/замечаний, найденных при ручном QA после закрытия v9.0 (2026-04-13). Каждый баг = отдельный `.md` файл + опционально скриншоты/видео рядом.

## Как добавлять баги

1. **Имя файла:** `BUG-NNN-короткое-название.md` (NNN — автоинкремент: 001, 002, ...).
   Пример: `BUG-001-headman-journal-mobile-overflow.md`

2. **Скриншоты/видео:** кладите рядом с `.md` файлом. В имя добавляйте префикс бага.
   Пример: `BUG-001-01.png`, `BUG-001-before.png`, `BUG-001-console.png`, `BUG-001-reproduce.mp4`

3. **Шаблон:** копируйте `_TEMPLATE.md` в новый файл и заполняйте. Или создавайте с нуля по структуре ниже.

## Структура каждого bug-report

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
- Браузер/устройство: <Chrome 128 / Safari iOS 17 / Firefox / и т.д.>
- URL: <https://ruttrack.site/... или file:// ...>
- Пользователь/роль: <student00001 / headman_assistant / admin / etc>
- Дата/время: <если применимо>
- Сеть: <WiFi / 4G / offline — если относится>

## Скриншоты / видео
- ![BUG-NNN-01](./BUG-NNN-01.png) — <что на скрине>
- [BUG-NNN-repro.mp4](./BUG-NNN-repro.mp4) — <описание видео>

## Логи / console / network (опционально)
<Вставить relevant куски из DevTools console / backend logs>

## Проанализированные причины (опционально)
<Если уже есть догадка — здесь. Если нет — оставить пустым.>

## Предполагаемый fix (опционально)
<Какой файл/компонент править, если понятно>
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

Когда баг `fixed`: добавить ссылку на commit(s) в секцию `## Fix` в конце `.md` файла. Не удалять файл — он остаётся как история.

## Индекс багов

Ведётся вручную в `INDEX.md` по мере добавления. Каждая запись — одна строка:

```markdown
- [BUG-001](./BUG-001-название.md) — <одна строка описания> (severity, status)
```

## Что НЕ сюда

- Feature requests / enhancements → `/gsd-add-backlog` или `/gsd-note`
- Security findings (systematic) → будут в отдельной папке `.planning/milestones/v9.0-security-audit/` после `cso` skill запуска
- UX findings (systematic) → будут в отдельной папке `.planning/milestones/v9.0-ux-audit/` после `ux-audit` skill запуска
