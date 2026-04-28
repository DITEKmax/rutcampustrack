# M17 — Понедельный отчёт старосты в деканат (Word/.docx)

**Статус:** ⬜ не начат
**Старт / финиш:** — / —
**Estimate:** ~10-12 человеко-дней (включая slack на правки шаблона деканатом)

---

## Scope

Староста группы должен один раз в неделю отдавать в деканат бумажный отчёт
посещаемости в формате `.docx`, оформленном по строгому образцу деканата
(`ПонедельныйШаблон.pdf` от 2026-04-28). Цель milestone — кнопка «Скачать»,
которая подставляет данные из БД в готовый шаблон и отдаёт `.docx`-файл,
готовый к печати/подписи.

Эта milestone закрывает **только понедельную форму**. Сводная семестровая
статистика — отдельный шаблон, отдельный milestone (см. M18 в backlog).

## Структура шаблона (что заполняется)

- **Шапка:** дата (per-day, формат ДД.ММ.ГГГГ) + группа + семестр + курс +
  учебный год.
- **Таблица:** 6 колонок-дней (пн-сб), под каждым — **ровно 5 подколонок**
  (одна на пару). Под каждой подколонкой — название предмета (вертикально)
  + тип пары (ЛК / ПЗ / ЛЗ).
- **Строки:** 35 фиксированных строк студентов (нумерация 1..35,
  алфавитный порядок по фамилии — backend уже сортирует через ICU
  collation, V21+V22).
- **Клетки:** один из трёх символов: `+` (present), `н` (absent), `у`
  (excused). `сп`/free_attendance маппится в `у`. Cancelled-пары
  не появляются вообще.
- **Подвал:** «Староста группы: ФИО» + место для подписи.

## Модули / изменения

### Backend (attendance-service)

- `services/attendance-service/attendance-app/src/main/resources/report-templates/headman-weekly.docx`
  — эталонный шаблон с плейсхолдерами docxtemplater (`{date}`,
  `{#students}{lastName}{/students}` и т.д.). **Источник** — оригинальный
  `.docx` от старосты, обработанный вручную через `docxtemplater` syntax.
- `services/attendance-service/attendance-api-contract/.../report/`
  — новый эндпоинт `GET /api/attendance/reports/headman-weekly`
  с параметрами `groupId`, `weekStart` (ISO date — понедельник),
  `weeks` (опционально — список дат для слияния). Response:
  `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
  (если 1 неделя) либо `application/zip` (если несколько).
- `services/attendance-service/attendance-app/src/main/java/.../report/HeadmanWeeklyReportService.java`
  — собирает данные за неделю: расписание (gRPC schedule-service),
  attendance-cells (Mongo), мета группы (gRPC academic-service).
- `services/attendance-service/attendance-app/src/main/java/.../report/DocxRenderer.java`
  — обёртка над docx4j или Apache POI XWPF (выбор библиотеки —
  см. NOTES.md «Open question 11»). Подставляет данные в шаблон.

### Frontend (web-panel)

- `frontends/web-panel/src/app/features/headman/weekly-report/`
  — новый компонент с UI: выбор недели (или диапазона недель),
  кнопка «Скачать .docx», loading state.
- Точное место входа в навигации — обсудим на старте (вкладка в
  Headman section). PWA в этой milestone **не трогается** —
  staros для отчёта работает с десктопа.

### Тесты

- IT на новый эндпоинт: подкладывает синтетические данные через
  Testcontainers (Mongo + Postgres), генерирует docx, парсит обратно
  через POI, проверяет 5-7 ключевых ячеек.
- Unit на маппинг данных: статус cells → символы (`+`/`н`/`у`),
  обработка переведённых студентов (длинное тире), исключение
  cancelled пар.
- Snapshot test на структуру docx (опционально — байтовое сравнение
  плохая идея из-за timestamp-метаданных в docx).

## Acceptance criteria

- [ ] `GET /api/attendance/reports/headman-weekly?groupId=X&weekStart=YYYY-MM-DD`
  возвращает валидный `.docx` для существующей группы и недели в
  пределах активного семестра.
- [ ] Структура файла **бит-в-бит совпадает** с эталоном:
  35 строк студентов, 6 дней × 5 подколонок, шапка, подвал.
- [ ] Алфавитный порядок студентов корректен с учётом «Ё»
  (использует backend ICU sort из V21+V22).
- [ ] Студент, переведённый в группу позже, чем дата отчёта:
  фамилия зачёркнута, в клетках длинное тире (`—`).
- [ ] Cancelled-пары отсутствуют, free_attendance отображается как `у`.
- [ ] Кнопка «Скачать» в web-panel доступна старосте, недоступна
  обычному студенту/преподавателю/админу (RBAC).
- [ ] Выбор нескольких недель → ZIP с N файлами либо один многостраничный
  docx (TBD — см. NOTES Open question 7).
- [ ] При >5 пар в день эндпоинт возвращает 422 с осмысленным
  сообщением (не молчаливо обрезает).
- [ ] При >35 студентов в группе — то же самое (422).
- [ ] Контракт + OpenApiSnapshotIT обновлены.
- [ ] Деканат принял один реальный сгенерированный отчёт.

## Dependencies

- **Блокируется:** ничем критическим. Может быть запущен сразу.
- **Parallel safe:** все остальные milestones — изменения локализованы
  в attendance-service + новый компонент в web-panel.
- **Внешние блокеры (актуальное на 2026-04-28):**
  - ⏳ **Эталонный `.docx` от пользователя** — обещан «на днях».
    PDF получен и проанализирован, но для шаблонизации нужен исходный
    Word-файл (плейсхолдеры в нередактируемый PDF не вставить).
  - ✅ **Ответы на 10 open questions** — получены. См. NOTES.md.
  - 🟡 **Q12** (точка входа в web-panel) — решим в G7, не блокирует G1-G6.

## Artifacts

- `docs/milestones/M17-headman-weekly-report/` — эта папка.
- `services/attendance-service/.../report-templates/headman-weekly.docx`
  — шаблон в репо как ассет.
- `docs/api/headman-weekly-report.md` — описание эндпоинта.

---

_Заполнить дату старта при начале работы. До тех пор — milestone в
ожидании ответов и эталона._
