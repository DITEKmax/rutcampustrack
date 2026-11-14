import type { StoryOperation } from './story-source'

export const reportDataContractOperations: StoryOperation[] = [
  {
    "id": "report-data-contract-history",
    "title": "Состав и пары на выбранные даты",
    "description": "После перевода 17 ноября студент остаётся в историческом составе группы 41 на 16 ноября. На 17 ноября состав уже учитывает членство в группе 42. План отчёта сохраняет физические пары и составы на их даты. Academic читает свои данные в REPEATABLE_READ и перепроверяет полномочия в этом снимке. Расписание загружается отдельно до чтения, а Attendance использует план для построения таблицы.",
    "frames": [
      {
        "title": "Дата вступления входит в состав, дата выхода исключена",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/history/HistoricalMembershipService.java",
        "language": "java",
        "lines": [
          [
            187,
            "        List<StudentGroupHistory> activeRows ="
          ],
          [
            188,
            "                histories.stream()"
          ],
          [
            189,
            "                        .filter(row -> !row.getJoinedAt().isAfter(asOfDate))"
          ],
          [
            190,
            "                        .filter("
          ],
          [
            191,
            "                                row ->"
          ],
          [
            192,
            "                                        row.getLeftAt() == null"
          ],
          [
            193,
            "                                                || asOfDate.isBefore(row.getLeftAt()))"
          ],
          [
            194,
            "                        .toList();"
          ],
          [
            195,
            "        Map<Long, StudentGroupHistory> activeByUser = new LinkedHashMap<>();"
          ],
          [
            196,
            "        for (StudentGroupHistory row : activeRows) {"
          ],
          [
            197,
            "            if (activeByUser.put(row.getUserId(), row) != null) {"
          ],
          [
            198,
            "                throw HistoricalMembershipException.precondition(\"Overlapping membership history\");"
          ],
          [
            199,
            "            }"
          ]
        ]
      },
      {
        "title": "Пары загружаются перед чтением снимка Academic",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/AcademicReportPlanBuilder.java",
        "language": "java",
        "lines": [
          [
            51,
            "    public ReportRenderPlan build(ExportJobRepository.Job job) {"
          ],
          [
            52,
            "        if (TransactionSynchronizationManager.isActualTransactionActive())"
          ],
          [
            53,
            "            throw new IllegalStateException(\"Report plan transport under transaction\");"
          ],
          [
            54,
            "        var discovery = snapshots.discover(job);"
          ],
          [
            55,
            "        var lessons = new ArrayList<LessonResponse>();"
          ],
          [
            56,
            "        var report = job.identity().report();"
          ],
          [
            57,
            "        var teacherScope ="
          ],
          [
            58,
            "                job.identity().actor().role().equals(\"TEACHER\")"
          ],
          [
            59,
            "                        ? local.teacherScope(job.identity().actor().userId())"
          ],
          [
            60,
            "                        : null;"
          ],
          [
            61,
            "        lessons.addAll(readLessons(job, discovery, teacherScope));"
          ],
          [
            70,
            "            // Current Auth admission and its local rechecks must finish before the owner RR read."
          ],
          [
            71,
            "            admittedScope ="
          ],
          [
            72,
            "                    new ReportPlanSnapshotReader.HeadmanScope("
          ],
          [
            73,
            "                            job, headmanScopes.read(job.identity().actor(), request.build()));"
          ],
          [
            74,
            "        }"
          ],
          [
            75,
            "        remainingMillis(job);"
          ],
          [
            76,
            "        var plan ="
          ],
          [
            77,
            "                snapshots"
          ],
          [
            78,
            "                        .snapshot("
          ],
          [
            79,
            "                                job,"
          ],
          [
            80,
            "                                discovery,"
          ],
          [
            81,
            "                                lessons,"
          ],
          [
            82,
            "                                educationalTime.clock().instant(),"
          ],
          [
            83,
            "                                admittedScope)"
          ],
          [
            84,
            "                        .toBuilder();"
          ]
        ]
      },
      {
        "title": "Снимок Academic фиксирует полномочия, пары и составы",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ReportPlanSnapshotReader.java",
        "language": "java",
        "lines": [
          [
            175,
            "    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)"
          ],
          [
            176,
            "    public ReportRenderPlan snapshot("
          ],
          [
            177,
            "            ExportJobRepository.Job job,"
          ],
          [
            178,
            "            Discovery discovery,"
          ],
          [
            179,
            "            List<LessonResponse> lessons,"
          ],
          [
            180,
            "            Instant generatedAt,"
          ],
          [
            181,
            "            HeadmanScope admittedScope) {"
          ],
          [
            182,
            "        local.requireWithinSnapshot(job.identity(), false);"
          ],
          [
            183,
            "        if (!Objects.equals(discovery.semesterId(), job.identity().semesterId()))"
          ],
          [
            184,
            "            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);"
          ],
          [
            185,
            "        var scope = requireHeadmanScope(job, discovery, admittedScope);"
          ],
          [
            186,
            "        if (lessons.size() > 5000) throw ExportJobRepository.error(HttpStatus.PAYLOAD_TOO_LARGE);"
          ],
          [
            187,
            "        var p = job.identity().report();"
          ],
          [
            328,
            "        plan.addAllLessons("
          ],
          [
            329,
            "                lessons.stream()"
          ],
          [
            330,
            "                        .sorted("
          ],
          [
            331,
            "                                Comparator.comparing(LessonResponse::getDate)"
          ],
          [
            332,
            "                                        .thenComparingInt(LessonResponse::getLessonNumber)"
          ],
          [
            333,
            "                                        .thenComparingLong(LessonResponse::getId))"
          ],
          [
            334,
            "                        .toList());"
          ],
          [
            343,
            "            var dates ="
          ],
          [
            344,
            "                    lessons.stream()"
          ],
          [
            345,
            "                            .filter(l -> l.getGroupId() == groupId)"
          ],
          [
            346,
            "                            .map(l -> LocalDate.parse(l.getDate()))"
          ],
          [
            347,
            "                            .distinct()"
          ],
          [
            348,
            "                            .sorted()"
          ],
          [
            349,
            "                            .toList();"
          ],
          [
            350,
            "            for (var date : dates) {"
          ],
          [
            351,
            "                var roster = reads.fetchHistoricalGroupMembers(groupId, date, semester.getId());"
          ],
          [
            352,
            "                var response ="
          ],
          [
            353,
            "                        GroupMembersResponse.newBuilder()"
          ],
          [
            354,
            "                                .setAsOfDate(date.toString())"
          ],
          [
            355,
            "                                .setSemesterId(semester.getId());"
          ],
          [
            356,
            "                for (User user : roster.students()) {"
          ],
          [
            357,
            "                    uniqueStudents.add(user.getId());"
          ],
          [
            358,
            "                    response.addStudents(student(user));"
          ],
          [
            359,
            "                }"
          ],
          [
            360,
            "                plan.addRosters("
          ],
          [
            361,
            "                        ReportGroupRoster.newBuilder().setGroupId(groupId).setRoster(response));"
          ]
        ]
      },
      {
        "title": "Модель файла хранит строки, колонки и метрики",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportModel.java",
        "language": "java",
        "lines": [
          [
            15,
            "/**"
          ],
          [
            16,
            " * Immutable, renderer-neutral input for teacher attendance exports."
          ],
          [
            17,
            " *"
          ],
          [
            18,
            " * <p>Rows and columns identify the exact lesson/student records used to produce this report."
          ],
          [
            19,
            " * Metrics are calculated once by the server for this same context and are only displayed by format"
          ],
          [
            20,
            " * renderers."
          ],
          [
            21,
            " */"
          ],
          [
            22,
            "public record TeacherAttendanceExportModel(Context context, List<Column> columns, List<Row> rows) {"
          ],
          [
            23,
            "    public TeacherAttendanceExportModel {"
          ],
          [
            24,
            "        context = Objects.requireNonNull(context, \"context\");"
          ],
          [
            25,
            "        columns = List.copyOf(Objects.requireNonNull(columns, \"columns\"));"
          ],
          [
            26,
            "        rows = List.copyOf(Objects.requireNonNull(rows, \"rows\"));"
          ],
          [
            27,
            "        if (context.kind() == ReportKind.SUBJECT_JOURNAL"
          ],
          [
            28,
            "                && rows.stream().anyMatch(row -> row.metrics().isEmpty())) {"
          ],
          [
            29,
            "            throw new IllegalArgumentException("
          ],
          [
            30,
            "                    \"Subject-journal rows require server-supplied metrics\");"
          ],
          [
            31,
            "        }"
          ]
        ]
      }
    ]
  },
  {
    "id": "report-data-contract-file",
    "title": "Таблица, страницы и формат файла",
    "description": "Модель отчёта хранит колонки пар, строки студентов и рассчитанные сервером метрики. В недельном DOCX старосты каждая следующая выбранная неделя начинается с разрыва страницы. В XLSX преподавателя числа и доли сохраняются как числа. Пустой знаменатель даёт пустую ячейку процента. PDF и ZIP страниц PNG строятся преобразованием DOCX при выборе соответствующего формата. HTTP отдаёт байты с Content-Type и именем в Content-Disposition.",
    "frames": [
      {
        "title": "Недельный DOCX соединяет документы разрывом страницы",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java",
        "language": "java",
        "lines": [
          [
            68,
            "    /** Renders already server-bounded weekly models in caller order, with a page break per week. */"
          ],
          [
            69,
            "    public byte[] render(List<TeacherAttendanceExportModel> weeklyModels) {"
          ],
          [
            70,
            "        if (weeklyModels == null || weeklyModels.isEmpty()) {"
          ],
          [
            71,
            "            throw new ReportValidationException(\"At least one weekly attendance model is required\");"
          ],
          [
            72,
            "        }"
          ],
          [
            73,
            "        List<TeacherAttendanceExportModel> models = List.copyOf(weeklyModels);"
          ],
          [
            74,
            "        if (models.stream()"
          ],
          [
            75,
            "                .anyMatch(model -> model.context().kind() != ReportKind.WEEKLY_ATTENDANCE)) {"
          ],
          [
            76,
            "            throw new ReportValidationException("
          ],
          [
            77,
            "                    \"A weekly DOCX batch can contain only WEEKLY_ATTENDANCE models\");"
          ],
          [
            78,
            "        }"
          ],
          [
            79,
            ""
          ],
          [
            80,
            "        TemplatePackage templatePackage = readTemplate(WEEKLY_TEMPLATE);"
          ],
          [
            81,
            "        List<String> documents ="
          ],
          [
            82,
            "                models.stream()"
          ],
          [
            83,
            "                        .map(model -> renderDocumentXml(templatePackage.documentXml(), model))"
          ],
          [
            84,
            "                        .toList();"
          ],
          [
            85,
            "        String documentXml = documents.size() == 1 ? documents.get(0) : mergeDocumentXml(documents);"
          ],
          [
            86,
            "        return writePackage(templatePackage.entries(), documentXml);"
          ],
          [
            501,
            "    private static String mergeDocumentXml(List<String> renderedDocuments) {"
          ],
          [
            502,
            "        String base = renderedDocuments.get(0);"
          ],
          [
            503,
            "        StringBuilder body = new StringBuilder();"
          ],
          [
            504,
            "        for (int index = 0; index < renderedDocuments.size(); index++) {"
          ],
          [
            505,
            "            if (index > 0) {"
          ],
          [
            506,
            "                body.append(PAGE_BREAK);"
          ],
          [
            507,
            "            }"
          ],
          [
            508,
            "            body.append(extractBodyContent(renderedDocuments.get(index)));"
          ],
          [
            509,
            "        }"
          ],
          [
            510,
            "        return replaceBodyContent(base, body.toString());"
          ],
          [
            511,
            "    }"
          ]
        ]
      },
      {
        "title": "Числа Excel, имя и выбор формата",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java",
        "language": "java",
        "lines": [
          [
            57,
            "    private static final String XLSX_MIME ="
          ],
          [
            58,
            "            \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\";"
          ],
          [
            257,
            "        String extension = format == ExportFormat.PNG ? \"zip\" : format.code();"
          ],
          [
            258,
            "        String filename ="
          ],
          [
            259,
            "                \"teacher-journal-g\""
          ],
          [
            260,
            "                        + groupId"
          ],
          [
            261,
            "                        + \"-s\""
          ],
          [
            262,
            "                        + subjectId"
          ],
          [
            263,
            "                        + \"-\""
          ],
          [
            264,
            "                        + SAFE_DATE.format(period.from())"
          ],
          [
            265,
            "                        + \"-\""
          ],
          [
            266,
            "                        + SAFE_DATE.format(period.to())"
          ],
          [
            267,
            "                        + \".\""
          ],
          [
            268,
            "                        + extension;"
          ],
          [
            269,
            "        return TeacherAttendanceExportResponse.newBuilder()"
          ],
          [
            270,
            "                .setContent(com.google.protobuf.ByteString.copyFrom(content))"
          ],
          [
            271,
            "                .setFileName(filename)"
          ],
          [
            272,
            "                .setContentType(format.contentType())"
          ],
          [
            883,
            "            if (value.number() != null) {"
          ],
          [
            884,
            "                sheet.append(\" t=\\\"n\\\"><v>\").append(value.number()).append(\"</v></c>\");"
          ],
          [
            885,
            "            } else {"
          ],
          [
            886,
            "                sheet.append(\" t=\\\"inlineStr\\\"><is><t xml:space=\\\"preserve\\\">\")"
          ],
          [
            887,
            "                        .append(escapeXml(value.text()))"
          ],
          [
            888,
            "                        .append(\"</t></is></c>\");"
          ],
          [
            889,
            "            }"
          ],
          [
            890,
            "        }"
          ],
          [
            891,
            "        sheet.append(\"</row>\");"
          ],
          [
            892,
            "    }"
          ],
          [
            893,
            ""
          ],
          [
            894,
            "    private static void addMetricValues("
          ],
          [
            895,
            "            List<SheetValue> values, int count, BigDecimal percent, int denominator) {"
          ],
          [
            896,
            "        values.add(SheetValue.number(count));"
          ],
          [
            897,
            "        values.add(denominator == 0 ? SheetValue.text(\"\") : SheetValue.percentage(percent));"
          ],
          [
            898,
            "    }"
          ],
          [
            968,
            "        private static SheetValue number(int value) {"
          ],
          [
            969,
            "            return new SheetValue(\"\", Integer.toString(value), 0);"
          ],
          [
            970,
            "        }"
          ],
          [
            971,
            ""
          ],
          [
            972,
            "        private static SheetValue percentage(BigDecimal value) {"
          ],
          [
            973,
            "            return new SheetValue("
          ],
          [
            974,
            "                    \"\", value.movePointLeft(2).stripTrailingZeros().toPlainString(), 2);"
          ],
          [
            975,
            "        }"
          ],
          [
            978,
            "    private enum ExportFormat {"
          ],
          [
            979,
            "        DOCX(\"docx\", DOCX_MIME),"
          ],
          [
            980,
            "        PDF(\"pdf\", \"application/pdf\"),"
          ],
          [
            981,
            "        PNG(\"png\", \"application/zip\"),"
          ],
          [
            982,
            "        HTML(\"html\", HTML_MIME),"
          ],
          [
            983,
            "        XLSX(\"xlsx\", XLSX_MIME);"
          ],
          [
            1011,
            "            return switch (this) {"
          ],
          [
            1012,
            "                case DOCX -> docxRenderer.render(model);"
          ],
          [
            1013,
            "                case PDF ->"
          ],
          [
            1014,
            "                        renderer.convertDocxForTeacherExport("
          ],
          [
            1015,
            "                                docxRenderer.render(model), TargetFormat.PDF);"
          ],
          [
            1016,
            "                case PNG ->"
          ],
          [
            1017,
            "                        renderer.convertDocxForTeacherExport("
          ],
          [
            1018,
            "                                docxRenderer.render(model), TargetFormat.PNG_PAGES_ZIP);"
          ],
          [
            1019,
            "                case HTML -> html(model);"
          ],
          [
            1020,
            "                case XLSX -> xlsx(model);"
          ]
        ]
      },
      {
        "title": "Скачивание возвращает байты и заголовки",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherApiController.java",
        "language": "java",
        "lines": [
          [
            134,
            "        HttpHeaders headers = new HttpHeaders();"
          ],
          [
            135,
            "        headers.setContentType(MediaType.parseMediaType(download.contentType()));"
          ],
          [
            136,
            "        headers.setContentLength(download.bytes().length);"
          ],
          [
            137,
            "        headers.setContentDisposition("
          ],
          [
            138,
            "                ContentDisposition.attachment()"
          ],
          [
            139,
            "                        .filename(download.filename(), StandardCharsets.UTF_8)"
          ],
          [
            140,
            "                        .build());"
          ],
          [
            141,
            "        headers.setCacheControl(CacheControl.noStore());"
          ],
          [
            142,
            "        return new ResponseEntity<>("
          ],
          [
            143,
            "                download.bytes(), headers, org.springframework.http.HttpStatus.OK);"
          ],
          [
            144,
            "    }"
          ]
        ]
      },
      {
        "title": "Поля ответа gRPC для XLSX · представление JSON",
        "language": "json",
        "example": [
          "{",
          "  \"fileName\": \"teacher-journal-g41-s9-2026-11-16-2026-11-30.xlsx\",",
          "  \"contentType\": \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "report-data-contract-selection",
    "title": "Страница, итоги и выгрузка",
    "description": "Страница API содержит часть колонок. Для итогов сервер получает весь набор пар по выбранным фильтрам, включая колонки за пределами страницы. Выгрузка заново отбирает занятия по семестру, группе, предмету, типам и датам. Указанный ID ограничивает файл одной парой. Номер страницы и порог подсветки в селектор файла не входят. Журнал с метриками и экспорт преподавателя ограничены 5 000 парами. В Attendance ответ экспорта ограничен 20 МиБ.",
    "frames": [
      {
        "title": "Клиент получает страницы и запрашивает итоги с первой",
        "path": "frontends/mobile-core/src/features/teacher/teacher-journal-model.ts",
        "language": "typescript",
        "lines": [
          [
            86,
            "export async function loadJournalPages("
          ],
          [
            87,
            "  api: Pick<TeacherApi, 'journal'>,"
          ],
          [
            88,
            "  query: TeacherJournalQuery,"
          ],
          [
            89,
            "  isCurrent: () => boolean,"
          ],
          [
            90,
            "): Promise<TeacherJournalResponse | null> {"
          ],
          [
            91,
            "  if (teacherJournalTypes(query).length === 0 || !isCurrent()) return null"
          ],
          [
            92,
            "  let merged: TeacherJournalResponse | null = null"
          ],
          [
            93,
            "  for (let page = 0; ; page += 1) {"
          ],
          [
            94,
            "    const next = await api.journal({"
          ],
          [
            95,
            "      ...query,"
          ],
          [
            96,
            "      page,"
          ],
          [
            97,
            "      pageSize: 100,"
          ],
          [
            98,
            "      includeMetrics: page === 0,"
          ],
          [
            99,
            "      metricsStudentId: undefined,"
          ],
          [
            100,
            "    })"
          ],
          [
            101,
            "    if (!isCurrent()) return null"
          ],
          [
            102,
            "    if ("
          ],
          [
            103,
            "      next.page !== page ||"
          ],
          [
            104,
            "      (next.hasMore &&"
          ],
          [
            105,
            "        (next.lessons.length === 0 || (page + 1) * next.pageSize >= next.totalLessons))"
          ],
          [
            106,
            "    )"
          ],
          [
            107,
            "      throw new Error('Сервер вернул несогласованные страницы журнала.')"
          ],
          [
            108,
            "    merged = mergeJournalPage(merged, next)"
          ],
          [
            109,
            "    if (!next.hasMore) {"
          ]
        ]
      },
      {
        "title": "BFF передаёт отдельно ID страницы и полный набор ID для итогов",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            529,
            "        if (includeMetrics && concrete.size() > 5_000) {"
          ],
          [
            530,
            "            throw invalidStats(\"Слишком много занятий для статистики журнала\");"
          ],
          [
            531,
            "        }"
          ],
          [
            532,
            "        long offset = (long) page * pageSize;"
          ],
          [
            533,
            "        int start = offset >= concrete.size() ? concrete.size() : (int) offset;"
          ],
          [
            534,
            "        int end = Math.min(concrete.size(), start + pageSize);"
          ],
          [
            535,
            "        List<LessonResponse> pageLessons = concrete.subList(start, end);"
          ],
          [
            536,
            "        if (pageLessons.isEmpty() && !includeMetrics) {"
          ],
          [
            537,
            "            return new TeacherApiModels.JournalResponse("
          ],
          [
            538,
            "                    List.of(), List.of(), Instant.now(), page, pageSize, concrete.size(), false);"
          ],
          [
            539,
            "        }"
          ],
          [
            540,
            "        List<Long> ids = pageLessons.stream().map(LessonResponse::getId).toList();"
          ],
          [
            541,
            "        TeacherJournalResponse response ="
          ],
          [
            542,
            "                includeMetrics"
          ],
          [
            543,
            "                        ? attendance.teacherJournal("
          ],
          [
            544,
            "                                ids,"
          ],
          [
            545,
            "                                pastOnly,"
          ],
          [
            546,
            "                                true,"
          ],
          [
            547,
            "                                concrete.stream().map(LessonResponse::getId).toList(),"
          ],
          [
            548,
            "                                metricsStudentId)"
          ],
          [
            549,
            "                        : pastOnly"
          ],
          [
            550,
            "                                ? attendance.teacherJournal(ids, true)"
          ],
          [
            551,
            "                                : attendance.teacherJournal(ids);"
          ]
        ]
      },
      {
        "title": "Параметры выгрузки отделены от параметров просмотра",
        "path": "frontends/mobile-core/src/features/teacher/teacher-client.ts",
        "language": "typescript",
        "lines": [
          [
            130,
            "export function toTeacherJournalReportRequest("
          ],
          [
            131,
            "  query: TeacherJournalQuery & { readonly lessonId?: string | null },"
          ],
          [
            132,
            "  format: TeacherExportFormatCode,"
          ],
          [
            133,
            "): ReportDownloadTicketRequest {"
          ],
          [
            134,
            "  return {"
          ],
          [
            135,
            "    kind: 'TEACHER_JOURNAL',"
          ],
          [
            136,
            "    teacherJournal: validatedTeacherJournalReportSelector({"
          ],
          [
            137,
            "      semesterId: query.semesterId,"
          ],
          [
            138,
            "      groupId: query.groupId,"
          ],
          [
            139,
            "      subjectId: query.subjectId,"
          ],
          [
            140,
            "      lessonTypes: teacherJournalTypes(query),"
          ],
          [
            141,
            "      ...(query.dateFrom !== undefined ? { dateFrom: query.dateFrom } : {}),"
          ],
          [
            142,
            "      ...(query.dateTo !== undefined ? { dateTo: query.dateTo } : {}),"
          ],
          [
            143,
            "      ...(query.pastOnly !== undefined ? { pastOnly: query.pastOnly } : {}),"
          ],
          [
            144,
            "      ...(query.lessonId != null ? { lessonId: query.lessonId } : {}),"
          ],
          [
            145,
            "      format,"
          ],
          [
            146,
            "    }),"
          ],
          [
            147,
            "  }"
          ],
          [
            148,
            "}"
          ]
        ]
      },
      {
        "title": "Лимиты числа пар и размера файла",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java",
        "language": "java",
        "lines": [
          [
            50,
            "    private static final DateTimeFormatter SAFE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;"
          ],
          [
            51,
            "    private static final int JOURNAL_BATCH_SIZE = 100;"
          ],
          [
            52,
            "    private static final int MAX_EXPORT_LESSONS = 5_000;"
          ],
          [
            53,
            "    private static final int MAX_EXPORT_RESPONSE_BYTES = 20 * 1024 * 1024;"
          ],
          [
            217,
            "        if (lessons.stream().map(LessonResponse::getId).distinct().count() != lessons.size()) {"
          ],
          [
            218,
            "            throw new AcademicServiceUnavailableException("
          ],
          [
            219,
            "                    \"Schedule returned duplicate lesson identities\");"
          ],
          [
            220,
            "        }"
          ],
          [
            221,
            "        if (lessons.size() > MAX_EXPORT_LESSONS) {"
          ],
          [
            222,
            "            throw Status.RESOURCE_EXHAUSTED"
          ],
          [
            223,
            "                    .withDescription(\"Teacher journal exceeds the supported export size\")"
          ],
          [
            224,
            "                    .asRuntimeException();"
          ],
          [
            225,
            "        }"
          ],
          [
            247,
            "        byte[] content = format.render(model, docxRenderer, renderer);"
          ],
          [
            248,
            "        if (content == null || content.length == 0) {"
          ],
          [
            249,
            "            throw new ReportExportUnavailableException("
          ],
          [
            250,
            "                    \"Teacher attendance export returned no content\");"
          ],
          [
            251,
            "        }"
          ],
          [
            252,
            "        if (content.length > MAX_EXPORT_RESPONSE_BYTES) {"
          ],
          [
            253,
            "            throw Status.RESOURCE_EXHAUSTED"
          ],
          [
            254,
            "                    .withDescription(\"Teacher attendance export exceeds the 20 MiB response limit\")"
          ],
          [
            255,
            "                    .asRuntimeException();"
          ]
        ]
      }
    ]
  },
  {
    "id": "report-data-contract-snapshot",
    "title": "DTO и снимок OpenAPI",
    "description": "Поле current в ответе сеансов имеет тип boolean. OpenApiSnapshotIT сверяет документацию запущенного Auth со снимком docs/openapi/auth.json. В сохранённой схеме current обязателен. Workflow openapi-drift запускает офлайн-проверку, что из этого снимка получаются те же TypeScript-типы.",
    "frames": [
      {
        "title": "Java DTO требует булево поле current",
        "path": "services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/AuthSessionSummary.java",
        "language": "java",
        "lines": [
          [
            9,
            "@JsonInclude(JsonInclude.Include.NON_NULL)"
          ],
          [
            10,
            "@Schema(name = \"AuthSessionSummary\")"
          ],
          [
            11,
            "public record AuthSessionSummary("
          ],
          [
            12,
            "        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sessionId,"
          ],
          [
            13,
            "        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authMethod,"
          ],
          [
            14,
            "        @Schema(nullable = true) String clientLabel,"
          ],
          [
            15,
            "        @Schema(nullable = true) String locationLabel,"
          ],
          [
            16,
            "        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,"
          ],
          [
            17,
            "        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant lastSeenAt,"
          ],
          [
            18,
            "        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean current) {"
          ]
        ]
      },
      {
        "title": "Тест сравнивает OpenAPI сервиса с каноническим снимком",
        "path": "services/auth-service/auth-app/src/test/java/ru/rutcampustrack/auth/integration/OpenApiSnapshotIT.java",
        "language": "java",
        "lines": [
          [
            37,
            "    private static final Path SNAPSHOT_PATH ="
          ],
          [
            38,
            "            Paths.get(\"../../..\")"
          ],
          [
            39,
            "                    .resolve(\"docs\")"
          ],
          [
            40,
            "                    .resolve(\"openapi\")"
          ],
          [
            41,
            "                    .resolve(SERVICE_NAME + \".json\")"
          ],
          [
            42,
            "                    .toAbsolutePath()"
          ],
          [
            43,
            "                    .normalize();"
          ],
          [
            44,
            ""
          ],
          [
            45,
            "    @Autowired TestRestTemplate restTemplate;"
          ],
          [
            46,
            ""
          ],
          [
            47,
            "    @Test"
          ],
          [
            48,
            "    void apiDocsMatchesSnapshot() throws IOException {"
          ],
          [
            49,
            "        String actual = fetchAndNormalize();"
          ],
          [
            58,
            "        assertThat(SNAPSHOT_PATH)"
          ],
          [
            59,
            "                .as("
          ],
          [
            60,
            "                        \"Snapshot file missing — run with -Popenapi.snapshot.update=true to create\""
          ],
          [
            61,
            "                            + \" %s\","
          ],
          [
            62,
            "                        SNAPSHOT_PATH)"
          ],
          [
            63,
            "                .exists();"
          ],
          [
            64,
            ""
          ],
          [
            65,
            "        String expected = Files.readString(SNAPSHOT_PATH, StandardCharsets.UTF_8);"
          ],
          [
            66,
            "        ObjectMapper mapper = new ObjectMapper();"
          ],
          [
            67,
            "        assertThat(semantic(mapper.readTree(actual)))"
          ],
          [
            68,
            "                .as("
          ],
          [
            69,
            "                        \"OpenAPI spec drifted from committed snapshot. To regenerate: ./gradlew\""
          ],
          [
            70,
            "                            + \" :services:auth-service:auth-app:integrationTest --tests\""
          ],
          [
            71,
            "                            + \" OpenApiSnapshotIT -Popenapi.snapshot.update=true\")"
          ],
          [
            72,
            "                .isEqualTo(semantic(mapper.readTree(expected)));"
          ]
        ]
      },
      {
        "title": "Снимок требует boolean и указывает current в required",
        "path": "docs/openapi/auth.json",
        "language": "json",
        "lines": [
          [
            3981,
            "      \"AuthSessionSummary\" : {"
          ],
          [
            3982,
            "        \"type\" : \"object\","
          ],
          [
            3983,
            "        \"properties\" : {"
          ],
          [
            3984,
            "          \"sessionId\" : {"
          ],
          [
            3985,
            "            \"type\" : \"string\""
          ],
          [
            4003,
            "          },"
          ],
          [
            4004,
            "          \"current\" : {"
          ],
          [
            4005,
            "            \"type\" : \"boolean\""
          ],
          [
            4006,
            "          }"
          ],
          [
            4007,
            "        },"
          ],
          [
            4008,
            "        \"required\" : [ \"authMethod\", \"createdAt\", \"current\", \"lastSeenAt\", \"sessionId\" ]"
          ],
          [
            4009,
            "      },"
          ]
        ]
      },
      {
        "title": "GitHub Actions сверяет сгенерированные типы",
        "path": ".github/workflows/openapi-drift.yml",
        "language": "config",
        "lines": [
          [
            33,
            "      - name: Install mobile Vue workspace dependencies"
          ],
          [
            34,
            "        working-directory: frontends"
          ],
          [
            35,
            "        run: npm ci --no-audit --no-fund"
          ],
          [
            36,
            "      - name: Check canonical mobile snapshots (offline)"
          ],
          [
            37,
            "        working-directory: frontends"
          ],
          [
            38,
            "        run: |"
          ],
          [
            39,
            "          npm run generate:types:check"
          ],
          [
            40,
            "          npm run generate:auth-types:check"
          ],
          [
            41,
            "          npm run generate:academic-schedule-types:check"
          ]
        ]
      }
    ]
  },
  {
    "id": "report-data-contract-runtime",
    "title": "Проверка JSON в клиенте",
    "description": "Генератор строит AuthSessionSummary из того же снимка Auth. PWA типизирует поле как current: boolean, а после получения JSON проверяет значение во время выполнения. Строка «true» вызывает ошибку разбора. При таком ответе приложение не принимает карточку сеанса как подтверждённую.",
    "frames": [
      {
        "title": "Генератор связывает снимок и выходной TS-файл",
        "path": "frontends/mobile-core/scripts/generate-auth-types.mjs",
        "language": "typescript",
        "lines": [
          [
            7,
            "const specUrl = new URL('../../../docs/openapi/auth.json', import.meta.url)"
          ],
          [
            8,
            "const outputUrl = new URL('../src/api/generated/auth.ts', import.meta.url)"
          ],
          [
            9,
            "const checkOnly = process.argv.includes('--check')"
          ],
          [
            10,
            ""
          ],
          [
            11,
            "// Canonical LF keeps the source hash identical across Windows and Linux checkouts."
          ],
          [
            12,
            "const specText = (await readFile(specUrl, 'utf8')).replace(/\\r\\n/g, '\\n')"
          ]
        ]
      },
      {
        "title": "Обязательное булево поле в типах",
        "path": "frontends/mobile-core/src/api/generated/auth.ts",
        "language": "typescript",
        "lines": [
          [
            597,
            "        AuthSessionSummary: {"
          ],
          [
            598,
            "            authMethod: string;"
          ],
          [
            599,
            "            clientLabel?: string;"
          ],
          [
            600,
            "            /** Format: date-time */"
          ],
          [
            601,
            "            createdAt: string;"
          ],
          [
            602,
            "            current: boolean;"
          ],
          [
            603,
            "            /** Format: date-time */"
          ],
          [
            604,
            "            lastSeenAt: string;"
          ],
          [
            605,
            "            locationLabel?: string;"
          ],
          [
            606,
            "            sessionId: string;"
          ]
        ]
      },
      {
        "title": "PWA принимает только boolean",
        "path": "frontends/pwa-vue/src/auth-client.ts",
        "language": "typescript",
        "lines": [
          [
            541,
            "function adaptSessionSummary(value: unknown, operation: string): ProfileSessionSummary {"
          ],
          [
            542,
            "  if (!isRecord(value))"
          ],
          [
            543,
            "    throw new AuthPayloadError(operation, 'Сервер вернул некорректный сеанс', value)"
          ],
          [
            544,
            "  const session = value as GeneratedAuthSessionSummary"
          ],
          [
            545,
            "  if ("
          ],
          [
            546,
            "    !isNonEmptyString(session.sessionId) ||"
          ],
          [
            547,
            "    !isNonEmptyString(session.authMethod) ||"
          ],
          [
            548,
            "    !isNonEmptyString(session.createdAt) ||"
          ],
          [
            549,
            "    !isNonEmptyString(session.lastSeenAt) ||"
          ],
          [
            550,
            "    typeof session.current !== 'boolean'"
          ],
          [
            551,
            "  ) {"
          ],
          [
            552,
            "    throw new AuthPayloadError(operation, 'Сервер вернул неполные данные сеанса', value)"
          ],
          [
            553,
            "  }"
          ]
        ]
      },
      {
        "title": "Отклонённое поле в ответе сеансов",
        "language": "json",
        "example": [
          "{",
          "  \"current\": \"true\"",
          "}"
        ]
      }
    ]
  }
]
