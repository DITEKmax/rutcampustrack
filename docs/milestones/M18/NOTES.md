# M18 Notes

Живой файл для отклонений, измерений, замечаний QA и вопросов по реализации.

---

## 2026-05-02

- M18 стартует после проверки шаблона M17: тестовое заполнение заменяет 1152/1152 placeholders, Word открывает файл, PDF получается в 1 страницу.
- Пользователь подтвердил: отмененные пары не включаются в отчет; дни без пар остаются пустыми.
- Пользователь подтвердил: маппинг статусов `+`, `н`, `у`.
- Пользователь подтвердил: предпросмотр не нужен, файл скачивается сразу.
- Пользователь подтвердил: несколько недель можно выбирать не подряд.
- Пользователь подтвердил: для нескольких недель `docx/pdf` должны быть многостраничными файлами, `png` можно отдавать архивом.
- Пользователь подтвердил: нижние поля "староста"/"подпись" заполняются вручную.
- G1-G2 старт: API-контракт зафиксирован в `ReportApi` и `docs/api/headman-weekly-report.md`; добавлен список недель активного семестра по правилу D10.
- G3 реализован как сбор `HeadmanWeeklyReportModel`: группа берется из `RequestContext`, активный семестр/группа/состав из academic-service, расписание из schedule-service, посещаемость через `AttendanceReadPort.findByGroupAndDateRange`. Отмененные пары исключаются, пустые дни Mon-Sat остаются в модели пустыми.
- Ограничение текущего шаблона: модель содержит Mon-Sat (`TEMPLATE_DAYS=6`), поэтому непустое воскресенье сейчас дает `422`. Для воскресных занятий нужен новый шаблон или отдельное решение.
- G5 DOCX подключен без внешнего LibreOffice: `DocxRenderer` заменяет placeholders в `word/document.xml`, для нескольких недель объединяет body нескольких заполненных страниц с `w:br w:type="page"`. Unit-тесты проверяют отсутствие `${...}` и page break.
- G6 добавлен как отдельный `document-renderer-service`: gRPC-only `DocumentRendererGrpcService`, `proto/document_renderer.proto`, Dockerfile с LibreOffice Writer + Poppler, compose definitions dev/prod/e2e. `attendance-service` вызывает renderer для PDF/PNG, multi-week PNG собирает ZIP из отдельных PNG по неделям.
- Docker доступен через elevated shell; `docs/openapi/attendance.json` обновлен штатно через `OpenApiSnapshotIT -Popenapi.snapshot.update=true`, затем проверен без update-флага. `-Dopenapi.snapshot.update=true` не подходит для этого repo: `integrationTest` прокидывает именно Gradle property `-Popenapi.snapshot.update=true`.
- G8 Web-panel: на `headman/weekly-journal` добавлены формат `docx/pdf/png`, скачивание открытой недели, modal со списком недель активного семестра и non-consecutive multi-week export.
- G9 PWA: на странице `Группа` добавлен блок отчетов старосты с выбором одной недели, bottom sheet для нескольких недель и blob-download с mobile-friendly object URL cleanup.
- PWA runtime cache: добавлен cache matcher только для metadata endpoint `/api/attendance/reports/headman-weekly/weeks`; binary export `/current` намеренно исключен из service-worker cache.
- UI экспорта скрывается по `isHeadman`; backend остается источником RBAC и возвращает `403` при прямых запросах не-старосты.
- G10 unit coverage расширен для `png` single-week, multi-week `docx/pdf`, PNG ZIP и запрета export для не-старосты до внешних gRPC/Mongo вызовов.
- Добавлен `ReportControllerMvcTest`: MVC-slice проверяет HTTP mappings, binary headers/body для single-week `docx/pdf/png`, multi-week PNG ZIP и ProblemDetails `403/422/503` без Docker/Testcontainers.
- Добавлен `HeadmanWeeklyReportControllerIT`: полный Spring context + Testcontainers Mongo/Rabbit/Redis проверяет single-week `docx/pdf/png` и multi-week `docx/pdf/png zip`; renderer-service мокается на gRPC-границе.
- QA DOCX/PDF/PNG: `DocxRendererTest` с `M18_QA_ARTIFACTS=true` генерирует `attendance-app/build/m18-qa/headman-weekly-single.docx` и non-consecutive `headman-weekly-multi.docx`. Образ `rct-document-renderer-qa` открыл DOCX через LibreOffice headless, сконвертировал PDF/PNG; single-week PDF = 1 страница, multi-week PDF = 2 страницы, PNG визуально проверены.
- QA нашел два дефекта до приемки: LibreOffice в контейнере не стартовал из-за non-writable `/app` HOME, а single-week DOCX распадался на 2 PDF-страницы из-за переноса двузначных номеров в узком столбце `№`. Исправлено: `ProcessRunner` задает writable `HOME`/`XDG_*` внутри temp-dir, `DocxRenderer` расширяет number column до 560 dxa.
- Проверка файла с реальными данными старосты принята как post-release/prod smoke: владелец выполнит ее после первого реального экспорта на prod. Для закрытия M18 блокером не считается, так как тестовые DOCX/PDF/PNG, non-consecutive export, API, UI и Testcontainers IT закрыты.

## Open questions

- Q1: закрыто D10. Неделя 1 — неделя `понедельник-воскресенье`, в которую попадает дата начала активного семестра; дальше по понедельникам до недели окончания семестра включительно.

## Technical notes

- DOCX должен оставаться primary artifact. PDF/PNG генерируются из уже заполненного DOCX, иначе появится риск расхождения внешнего вида.
- По правилам `CLAUDE.md` renderer-service не делаем ad-hoc REST-контейнером. Целевой вариант — Spring Boot app с gRPC-контрактом в `proto/document_renderer.proto`, без public REST и без Gateway route.
- Для renderer-service нужен deterministic font set: Arial/Times New Roman или совместимые шрифты должны быть доступны в контейнере, иначе PDF/PNG могут отличаться от Word.
- Для PNG надо заранее выбрать DPI. Предложение: 200 DPI для читаемости и умеренного размера файла.
- Для multi-week PNG ZIP имена файлов внутри архива должны включать номер недели и даты, например `week-05_27.04.2026_03.05.2026.png`.
- Для PWA скачивание blob на iOS/Android нужно проверить отдельно: мобильные браузеры по-разному обрабатывают `Content-Disposition`.
