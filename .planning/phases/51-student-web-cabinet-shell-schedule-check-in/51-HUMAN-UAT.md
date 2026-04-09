---
status: partial
phase: 51-student-web-cabinet-shell-schedule-check-in
source: [51-VERIFICATION.md]
started: 2026-04-09T14:30:00Z
updated: 2026-04-09T14:30:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Dashboard — визуальный осмотр /student/dashboard после логина как STUDENT
expected: Greeting hero с time-based заголовком (Доброе утро/день/вечер) + live clock + chip-row сегодняшних пар + NextLessonCard + red-zone баннеры при необходимости. Hero animation routeFade (200ms) срабатывает при переходе на роут.
result: [pending]

### 2. Schedule — prev/next неделя, floating pill "Сегодня"
expected: При клике prev/next на /student/schedule заголовок недели обновляется в русском формате ('6-11 апр' или '30 мар - 4 апр'), список пар пере-загружается, pill "Сегодня" появляется при уходе с текущей недели, daySlide (150ms) анимация срабатывает при смене day-tab.
result: [pending]

### 3. Schedule — раскрытие детали пары
expected: По клику на строку пары inline-панель раскрывается под ней, показывая lesson number, type, room, teacher id, cancel reason. Повторный клик — сворачивает. `aria-expanded` отражает состояние. Только одна панель открыта за раз. Subway-rail визуальный язык (time rail + station dot).
result: [pending]

### 4. Checkin — hero card во время активной пары
expected: На /student/checkin во время активной пары (backend должен иметь ACTIVE lesson для группы студента) рендерится hero card с мигающей живой точкой + pill "Идёт сейчас" + название предмета (2xl font) + время HH:mm–HH:mm + "Ауд. {room}" + CTA кнопка "Отметиться" (min-height 48px) + счётчик "N отметилось" (aria-live='polite').
result: [pending]

### 5. Checkin — GPS flow с разрешённой геолокацией
expected: После клика "Отметиться" и разрешения геолокации кнопка проходит состояния: "Определяем координаты…" → "Отправляем отметку…" → badge "Вы отметились" при 2xx. POST /api/attendance/checkin с телом {lat, lng}.
result: [pending]

### 6. Checkin — GPS flow с запретом геолокации
expected: После клика "Отметиться" и отказа браузера в геолокации под кнопкой появляется inline error "Нет доступа к геолокации. Разрешите доступ в настройках браузера и попробуйте снова." Кнопка re-enable.
result: [pending]

### 7. Checkin — real-time counter от других студентов
expected: Пока /student/checkin открыт, при check-in другого студента из второй сессии на ту же ACTIVE пару STOMP envelope attendance.marked приходит, счётчик "N отметилось" инкрементируется в real-time БЕЗ перезагрузки страницы. Состояние кнопки остаётся "Отметиться" (не confirmed — другой user_id).
result: [pending]

### 8. Checkin — auto-confirm при check-in с другого устройства
expected: Пока /student/checkin открыт на первом устройстве, если тот же студент делает check-in со второго устройства — STOMP attendance.marked с совпадающими user_id И lesson_id приходит, состояние автоматически переходит в badge "confirmed" БЕЗ второго HTTP POST.
result: [pending]

### 9. Checkin — empty state
expected: При визите /student/checkin когда нет активной пары сегодня рендерится empty state: circle icon + заголовок "Нет активной пары" + body о 5-минутном окне. Если следующая пара PLANNED сегодня — hint card "Следующая пара" с названием предмета и временем начала. Иначе fallback "На сегодня пар больше нет".
result: [pending]

### 10. Sidebar — STUDENT роль
expected: При входе как plain STUDENT sidebar показывает "Главная / Расписание / Отметиться" под section label "Учёба", user-role chip под аватаром показывает "Студент". Нет видимых TEACHER/ADMIN пунктов.
result: [pending]

### 11. Accessibility — prefers-reduced-motion
expected: При включении системной настройки "уменьшить движение" мигающая точка на /student/checkin hero перестаёт анимироваться, pulse dot на dashboard отключён. Route fade-in анимации также приглушены.
result: [pending]

### 12. Dashboard — red-zone banners с реальными данными
expected: Dashboard рендерит один `<app-redzone-warning>` на каждый sub-threshold предмет с форматом "{subjectName} — посещаемость ниже порога ({N}%)" (процент округлён до целого). Цвет amber, иконка ph-warning duotone, корректное форматирование копирайта.
result: [pending]

## Summary

total: 12
passed: 0
issues: 0
pending: 12
skipped: 0
blocked: 0

## Gaps
