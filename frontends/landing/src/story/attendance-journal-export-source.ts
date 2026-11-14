import type { StoryOperation } from './story-source'

export const attendanceJournalExportOperations: readonly StoryOperation[] = [
  {
    "id": "attendance-journal-export-formats",
    "title": "Пять форматов журнала",
    "description": "Староста и преподаватель могут выбрать DOCX, PDF, PNG, HTML или XLSX. PNG возвращается ZIP-архивом изображений страниц. Каталоги журнала не предлагают CSV. Преподаватель получает варианты из Mobile BFF. Староста получает форматы из Attendance вместе с недельным выбором или отдельно для предметного журнала.",
    "frames": [
      {
        "title": "Каталог старосты и расширения файлов",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportFormat.java",
        "language": "java",
        "lines": [
          [
            10,
            "enum HeadmanWeeklyReportFormat {"
          ],
          [
            11,
            "    DOCX(\"docx\", \"Word (.docx)\", ReportApi.DOCX_MEDIA_TYPE, \"docx\"),"
          ],
          [
            12,
            "    PDF(\"pdf\", \"PDF (.pdf)\", ReportApi.PDF_MEDIA_TYPE, \"pdf\"),"
          ],
          [
            13,
            "    PNG(\"png\", \"PNG, архив страниц (.zip)\", ReportApi.ZIP_MEDIA_TYPE, \"zip\"),"
          ],
          [
            14,
            "    HTML(\"html\", \"HTML (.html)\", ReportApi.HTML_MEDIA_TYPE, \"html\"),"
          ],
          [
            15,
            "    XLSX(\"xlsx\", \"Excel (.xlsx)\", ReportApi.XLSX_MEDIA_TYPE, \"xlsx\");"
          ],
          [
            46,
            "    HeadmanWeeklyExportFormatOption toOption() {"
          ],
          [
            47,
            "        return new HeadmanWeeklyExportFormatOption(code, label, contentType, extension);"
          ],
          [
            48,
            "    }"
          ],
          [
            49,
            ""
          ],
          [
            50,
            "    static List<HeadmanWeeklyExportFormatOption> catalogue() {"
          ],
          [
            51,
            "        return Arrays.stream(values()).map(HeadmanWeeklyReportFormat::toOption).toList();"
          ],
          [
            52,
            "    }"
          ]
        ]
      },
      {
        "title": "Каталог преподавателя",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            63,
            "    private static final List<TeacherApiModels.ExportFormat> JOURNAL_EXPORT_FORMATS ="
          ],
          [
            64,
            "            List.of("
          ],
          [
            65,
            "                    new TeacherApiModels.ExportFormat("
          ],
          [
            66,
            "                            \"docx\","
          ],
          [
            67,
            "                            \"Word (.docx)\","
          ],
          [
            68,
            "                            \"application/vnd.openxmlformats-officedocument.wordprocessingml.document\","
          ],
          [
            69,
            "                            \"docx\"),"
          ],
          [
            70,
            "                    new TeacherApiModels.ExportFormat("
          ],
          [
            71,
            "                            \"pdf\", \"PDF (.pdf)\", \"application/pdf\", \"pdf\"),"
          ],
          [
            72,
            "                    new TeacherApiModels.ExportFormat("
          ],
          [
            73,
            "                            \"png\", \"PNG, архив страниц (.zip)\", \"application/zip\", \"zip\"),"
          ],
          [
            74,
            "                    new TeacherApiModels.ExportFormat("
          ],
          [
            75,
            "                            \"html\", \"HTML (.html)\", \"text/html; charset=UTF-8\", \"html\"),"
          ],
          [
            76,
            "                    new TeacherApiModels.ExportFormat("
          ],
          [
            77,
            "                            \"xlsx\","
          ],
          [
            78,
            "                            \"Excel (.xlsx)\","
          ],
          [
            79,
            "                            \"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\","
          ],
          [
            80,
            "                            \"xlsx\"));"
          ],
          [
            596,
            "    public TeacherApiModels.ExportFormatsResponse journalExportFormats() {"
          ],
          [
            597,
            "        requireTeacher();"
          ],
          [
            598,
            "        return new TeacherApiModels.ExportFormatsResponse(JOURNAL_EXPORT_FORMATS);"
          ],
          [
            599,
            "    }"
          ]
        ]
      },
      {
        "title": "Форма ответа каталога преподавателя",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/TeacherApiModels.java",
        "language": "java",
        "lines": [
          [
            168,
            "    public record ExportFormat(String code, String label, String contentType, String extension) {}"
          ],
          [
            169,
            ""
          ],
          [
            170,
            "    @Schema(name = \"TeacherExportFormats\")"
          ],
          [
            171,
            "    public record ExportFormatsResponse(List<ExportFormat> formats) {"
          ],
          [
            172,
            "        public ExportFormatsResponse {"
          ],
          [
            173,
            "            formats = List.copyOf(formats);"
          ],
          [
            174,
            "        }"
          ],
          [
            175,
            "    }"
          ]
        ]
      },
      {
        "title": "Каталог преподавателя · вариант PNG",
        "language": "json",
        "example": [
          "{",
          "  \"formats\": [",
          "    {",
          "      \"code\": \"png\",",
          "      \"label\": \"PNG, архив страниц (.zip)\",",
          "      \"contentType\": \"application/zip\",",
          "      \"extension\": \"zip\"",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-export-weekly",
    "title": "Староста · отчёт по неделям",
    "description": "Из журнала староста выбирает формат и недели активного семестра. Сервер предлагает понедельники до текущей недели в границах семестра. Для одной текущей недели клиент отправляет HEADMAN_WEEKLY_CURRENT, для остальных выборов — HEADMAN_WEEKLY_SELECTED. Список ограничен 64 неделями. Выбранная на экране пара не задаёт колонки отчёта.",
    "frames": [
      {
        "title": "Мобильный экран собирает выбранные недели",
        "path": "frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue",
        "language": "typescript",
        "lines": [
          [
            448,
            "  const format = selectedWeeklyFormat.value;"
          ],
          [
            449,
            "  if (!api || !canExportWeekly() || weeklyExportBusy.value) return;"
          ],
          [
            450,
            "  if (resumedRequest) {"
          ],
          [
            451,
            "    if ("
          ],
          [
            452,
            "      !reportDownload ||"
          ],
          [
            453,
            "      !['HEADMAN_WEEKLY_CURRENT', 'HEADMAN_WEEKLY_SELECTED'].includes(resumedRequest.kind)"
          ],
          [
            454,
            "    )"
          ],
          [
            455,
            "      return;"
          ],
          [
            456,
            "  } else if (!options || !format) return;"
          ],
          [
            457,
            "  const weekStarts ="
          ],
          [
            458,
            "    options?.weeks"
          ],
          [
            459,
            "      .filter((week) => selectedWeeklyStarts.value.includes(week.weekStart))"
          ],
          [
            460,
            "      .map((week) => week.weekStart) ?? [];"
          ],
          [
            461,
            "  if (!resumedRequest && weekStarts.length === 0) {"
          ],
          [
            462,
            "    weeklyExportError.value = 'Выбери хотя бы одну неделю.';"
          ],
          [
            463,
            "    weeklyExportStatus.value = null;"
          ],
          [
            464,
            "    return;"
          ],
          [
            476,
            "    if (reportDownload) {"
          ],
          [
            477,
            "      const request ="
          ],
          [
            478,
            "        resumedRequest ?? toHeadmanWeeklyReportRequest(options!.weeks, weekStarts, format!);"
          ]
        ]
      },
      {
        "title": "Два вида недельного запроса",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts",
        "language": "typescript",
        "lines": [
          [
            228,
            "export function toHeadmanWeeklyReportRequest("
          ],
          [
            229,
            "  weeks: readonly HeadmanWeeklyWeekOption[],"
          ],
          [
            230,
            "  selectedWeekStarts: readonly string[],"
          ],
          [
            231,
            "  format: HeadmanWeeklyExportFormat,"
          ],
          [
            232,
            "): ReportDownloadTicketRequest {"
          ],
          [
            233,
            "  if (!isReportDownloadFormat(format.code)) {"
          ],
          [
            234,
            "    throw new RangeError('Сервер вернул неподдерживаемый формат недельного отчёта.')"
          ],
          [
            235,
            "  }"
          ],
          [
            236,
            "  const selected = new Set(selectedWeekStarts)"
          ],
          [
            237,
            "  const orderedWeeks = weeks.filter((week) => selected.has(week.weekStart))"
          ],
          [
            238,
            "  const firstWeek = orderedWeeks[0]"
          ],
          [
            239,
            "  if (!firstWeek) throw new RangeError('Выбери хотя бы одну неделю из доступных.')"
          ],
          [
            240,
            "  if (orderedWeeks.length === 1 && firstWeek.current) {"
          ],
          [
            241,
            "    return {"
          ],
          [
            242,
            "      kind: 'HEADMAN_WEEKLY_CURRENT',"
          ],
          [
            243,
            "      headmanWeeklyCurrent: {"
          ],
          [
            244,
            "        weekStart: firstWeek.weekStart,"
          ],
          [
            245,
            "        format: format.code,"
          ],
          [
            246,
            "      },"
          ],
          [
            247,
            "    }"
          ],
          [
            248,
            "  }"
          ],
          [
            249,
            "  return {"
          ],
          [
            250,
            "    kind: 'HEADMAN_WEEKLY_SELECTED',"
          ],
          [
            251,
            "    headmanWeeklySelected: {"
          ],
          [
            252,
            "      weekStarts: orderedWeeks.map((week) => week.weekStart),"
          ],
          [
            253,
            "      format: format.code,"
          ],
          [
            254,
            "    },"
          ],
          [
            255,
            "  }"
          ],
          [
            256,
            "}"
          ]
        ]
      },
      {
        "title": "Доступные недели до текущей",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            308,
            "    static List<HeadmanWeeklyWeekOption> activeSemesterWeeks("
          ],
          [
            309,
            "            LocalDate semesterStart, LocalDate semesterEnd, LocalDate today) {"
          ],
          [
            310,
            "        if (semesterStart == null"
          ],
          [
            311,
            "                || semesterEnd == null"
          ],
          [
            312,
            "                || semesterEnd.isBefore(semesterStart)"
          ],
          [
            313,
            "                || today == null) {"
          ],
          [
            314,
            "            throw new ReportValidationException(\"Invalid active-semester week range\");"
          ],
          [
            315,
            "        }"
          ],
          [
            316,
            "        if (today.isBefore(semesterStart)) return List.of();"
          ],
          [
            317,
            "        LocalDate lastAvailableDate = today.isAfter(semesterEnd) ? semesterEnd : today;"
          ],
          [
            318,
            "        LocalDate firstMonday = mondayOf(semesterStart);"
          ],
          [
            319,
            "        LocalDate lastMonday = mondayOf(lastAvailableDate);"
          ],
          [
            320,
            "        ArrayList<HeadmanWeeklyWeekOption> result = new ArrayList<>();"
          ],
          [
            321,
            "        LocalDate weekStart = firstMonday;"
          ],
          [
            322,
            "        int weekOfSemester = 1;"
          ],
          [
            323,
            "        while (!weekStart.isAfter(lastMonday)) {"
          ],
          [
            324,
            "            LocalDate weekEnd = weekStart.plusDays(6);"
          ],
          [
            325,
            "            boolean current = !today.isBefore(weekStart) && !today.isAfter(weekEnd);"
          ],
          [
            335,
            "            weekStart = weekStart.plusWeeks(1);"
          ],
          [
            336,
            "            weekOfSemester++;"
          ],
          [
            337,
            "        }"
          ],
          [
            338,
            "        return List.copyOf(result);"
          ],
          [
            339,
            "    }"
          ]
        ]
      },
      {
        "title": "Нормализация и предел выбора",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            780,
            "    private List<LocalDate> normalizeWeekStarts("
          ],
          [
            781,
            "            List<LocalDate> requestedWeekStarts, SemesterResponse semester, LocalDate today) {"
          ],
          [
            782,
            "        if (requestedWeekStarts == null"
          ],
          [
            783,
            "                || requestedWeekStarts.isEmpty()"
          ],
          [
            784,
            "                || requestedWeekStarts.stream().anyMatch(Objects::isNull)) {"
          ],
          [
            785,
            "            throw new ReportValidationException(\"Выбери хотя бы одну неделю\");"
          ],
          [
            786,
            "        }"
          ],
          [
            787,
            "        if (requestedWeekStarts.size() > MAX_SELECTED_WEEKS) {"
          ],
          [
            788,
            "            throw new ReportValidationException(\"Можно выбрать не более 64 недель за один раз\");"
          ],
          [
            789,
            "        }"
          ],
          [
            790,
            "        Set<LocalDate> available ="
          ],
          [
            791,
            "                activeSemesterWeeks("
          ],
          [
            792,
            "                                parseUpstreamDate(semester.getDateFrom(), \"semester date_from\"),"
          ],
          [
            793,
            "                                parseUpstreamDate(semester.getDateTo(), \"semester date_to\"),"
          ],
          [
            794,
            "                                today)"
          ],
          [
            795,
            "                        .stream()"
          ],
          [
            796,
            "                        .map(HeadmanWeeklyWeekOption::getWeekStart)"
          ],
          [
            797,
            "                        .collect(java.util.stream.Collectors.toSet());"
          ],
          [
            798,
            "        List<LocalDate> normalized ="
          ],
          [
            799,
            "                requestedWeekStarts.stream().distinct().sorted(Comparator.naturalOrder()).toList();"
          ],
          [
            800,
            "        for (LocalDate weekStart : normalized) {"
          ],
          [
            801,
            "            if (!available.contains(weekStart)) {"
          ],
          [
            802,
            "                throw new ReportValidationException("
          ],
          [
            803,
            "                        \"Week is outside the available active-semester range: \" + weekStart);"
          ],
          [
            804,
            "            }"
          ],
          [
            805,
            "        }"
          ]
        ]
      },
      {
        "title": "Параметры HEADMAN_WEEKLY_SELECTED",
        "path": "services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/IssueReportDownloadTicketRequest.java",
        "language": "java",
        "lines": [
          [
            620,
            "    public record HeadmanWeeklySelectedParameters("
          ],
          [
            621,
            "            @NotNull @Size(min = 1, max = 64) List<@NotNull LocalDate> weekStarts,"
          ],
          [
            622,
            "            @NotNull ReportDownloadFormat format) {"
          ],
          [
            623,
            "        public HeadmanWeeklySelectedParameters {"
          ],
          [
            624,
            "            weekStarts = immutable(weekStarts);"
          ],
          [
            625,
            "        }"
          ],
          [
            626,
            "    }"
          ]
        ]
      },
      {
        "title": "POST · две недели в Excel",
        "language": "json",
        "example": [
          "{",
          "  \"requestId\": \"00000000-0000-4000-8000-000000000001\",",
          "  \"report\": {",
          "    \"kind\": \"HEADMAN_WEEKLY_SELECTED\",",
          "    \"headmanWeeklySelected\": {",
          "      \"weekStarts\": [",
          "        \"2026-09-07\",",
          "        \"2026-09-14\"",
          "      ],",
          "      \"format\": \"xlsx\"",
          "    }",
          "  }",
          "}"
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-export-subject",
    "title": "Староста · журнал по предмету",
    "description": "В мобильной статистике по предмету есть «Журнал по предмету» для активного семестра. Запрос задаёт предмет, от одного до трёх типов занятий и выбранные недели. Без фильтра недель сервер берёт период от начала семестра до конца текущей недели, ограниченный концом семестра. Здесь сохраняются заголовки отменённых пар, а недельный отчёт их исключает.",
    "frames": [
      {
        "title": "Вход из статистики по предмету",
        "path": "frontends/mobile-core/src/features/headman-stats/HeadmanStatsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1963,
            "      <template v-else-if=\"statsTool === 'export'\">"
          ],
          [
            1964,
            "        <ExportButton"
          ],
          [
            1965,
            "          v-if=\"api && selectedSubjectId !== null && block === 'subject'\""
          ],
          [
            1966,
            "          label=\"Журнал по предмету\""
          ],
          [
            1967,
            "          :port=\"reportDownload\""
          ],
          [
            1968,
            "          :formats=\"() => api!.subjectJournalFormats()\""
          ],
          [
            1969,
            "          :weeks=\"() => api!.subjectJournalWeeks()\""
          ],
          [
            1970,
            "          :request=\""
          ],
          [
            1971,
            "            (format, weekStarts) => ({"
          ],
          [
            1972,
            "              kind: 'HEADMAN_SUBJECT_JOURNAL',"
          ],
          [
            1973,
            "              headmanSubjectJournal: {"
          ],
          [
            1974,
            "                subjectId: selectedSubjectId!,"
          ],
          [
            1975,
            "                lessonTypes: [...selectedTypes],"
          ],
          [
            1976,
            "                ...(weekStarts ? { weekStarts } : {}),"
          ],
          [
            1977,
            "                format,"
          ],
          [
            1978,
            "              },"
          ],
          [
            1979,
            "            })"
          ],
          [
            1980,
            "          \""
          ],
          [
            1981,
            "          :scope-key=\"JSON.stringify([selectedSubjectId, selectedTypes, semesterId])\""
          ],
          [
            1982,
            "          :disabled=\""
          ],
          [
            1983,
            "            offline ||"
          ],
          [
            1984,
            "              !canView ||"
          ],
          [
            1985,
            "              selectedTypes.length === 0 ||"
          ],
          [
            1986,
            "              semesterId !== semesterCatalog?.activeSemesterId"
          ],
          [
            1987,
            "          \""
          ],
          [
            1988,
            "          @error=\"emit('error', $event)\""
          ]
        ]
      },
      {
        "title": "Предмет из своей группы и активный семестр",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            397,
            "    TeacherAttendanceExportModel buildSubjectJournalModel("
          ],
          [
            398,
            "            ru.rutcampustrack.attendance.contract.dto.report.HeadmanSubjectJournalExportRequest"
          ],
          [
            399,
            "                    request) {"
          ],
          [
            400,
            "        ensureHeadman();"
          ],
          [
            401,
            "        if (request == null"
          ],
          [
            402,
            "                || request.subjectId() == null"
          ],
          [
            403,
            "                || request.subjectId() <= 0"
          ],
          [
            404,
            "                || request.lessonTypes() == null"
          ],
          [
            405,
            "                || request.lessonTypes().isEmpty()"
          ],
          [
            406,
            "                || request.lessonTypes().size() > 3"
          ],
          [
            407,
            "                || request.lessonTypes().stream().anyMatch(Objects::isNull)) {"
          ],
          [
            408,
            "            throw new ReportValidationException(\"Выбери предмет и типы занятий\");"
          ],
          [
            409,
            "        }"
          ],
          [
            410,
            "        long groupId = requestContext.getGroupId();"
          ],
          [
            411,
            "        SemesterResponse semester = requireActiveSemester(academicGrpcClient.getActiveSemester());"
          ],
          [
            412,
            "        LocalDate from = parseUpstreamDate(semester.getDateFrom(), \"semester date_from\");"
          ],
          [
            413,
            "        LocalDate end = parseUpstreamDate(semester.getDateTo(), \"semester date_to\");"
          ],
          [
            414,
            "        if (end.isBefore(from))"
          ],
          [
            415,
            "            throw new AcademicServiceUnavailableException(\"Некорректный период семестра\");"
          ],
          [
            416,
            "        LocalDate to = mondayOf(LocalDate.now(clock)).plusDays(6);"
          ],
          [
            417,
            "        if (to.isAfter(end)) to = end;"
          ],
          [
            418,
            "        if (to.isBefore(from)) throw new ReportValidationException(\"Семестр ещё не начался\");"
          ],
          [
            419,
            "        var subject ="
          ],
          [
            420,
            "                academicGrpcClient"
          ],
          [
            421,
            "                        .getSubjectDetailsByIds(List.of(request.subjectId()))"
          ],
          [
            422,
            "                        .get(request.subjectId());"
          ],
          [
            423,
            "        if (subject == null || subject.groupId() != groupId)"
          ],
          [
            424,
            "            throw new AccessDeniedException(\"Предмет не принадлежит твоей группе\");"
          ]
        ]
      },
      {
        "title": "Выбор типов и недель для предметной матрицы",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            428,
            "        List<String> types ="
          ],
          [
            429,
            "                request.lessonTypes().stream()"
          ],
          [
            430,
            "                        .map(HeadmanWeeklyReportService::normalizeType)"
          ],
          [
            431,
            "                        .distinct()"
          ],
          [
            432,
            "                        .sorted()"
          ],
          [
            433,
            "                        .toList();"
          ],
          [
            434,
            "        Set<String> actual ="
          ],
          [
            435,
            "                subject.lessonTypes().stream()"
          ],
          [
            436,
            "                        .map(HeadmanWeeklyReportService::normalizeType)"
          ],
          [
            437,
            "                        .collect(java.util.stream.Collectors.toSet());"
          ],
          [
            438,
            "        if (!actual.containsAll(types)"
          ],
          [
            439,
            "                || types.stream()"
          ],
          [
            440,
            "                        .anyMatch(type -> !Set.of(\"lecture\", \"practice\", \"lab\").contains(type))) {"
          ],
          [
            441,
            "            throw new ReportValidationException(\"Выбери существующие типы занятий предмета\");"
          ],
          [
            442,
            "        }"
          ],
          [
            443,
            "        Set<LocalDate> weeks ="
          ],
          [
            444,
            "                request.weekStarts() == null || request.weekStarts().isEmpty()"
          ],
          [
            445,
            "                        ? Set.of()"
          ],
          [
            446,
            "                        : Set.copyOf("
          ],
          [
            447,
            "                                normalizeWeekStarts("
          ],
          [
            448,
            "                                        request.weekStarts(), semester, LocalDate.now(clock)));"
          ],
          [
            453,
            "        var model ="
          ],
          [
            454,
            "                buildPeriodModel("
          ],
          [
            455,
            "                        groupId,"
          ],
          [
            456,
            "                        group.getName(),"
          ],
          [
            457,
            "                        semester,"
          ],
          [
            458,
            "                        from,"
          ],
          [
            459,
            "                        end,"
          ],
          [
            460,
            "                        from,"
          ],
          [
            461,
            "                        to,"
          ],
          [
            462,
            "                        clock.instant(),"
          ],
          [
            463,
            "                        request.subjectId(),"
          ],
          [
            464,
            "                        types,"
          ],
          [
            465,
            "                        weeks,"
          ],
          [
            466,
            "                        true);"
          ]
        ]
      },
      {
        "title": "Параметры HEADMAN_SUBJECT_JOURNAL",
        "path": "services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/IssueReportDownloadTicketRequest.java",
        "language": "java",
        "lines": [
          [
            394,
            "    public record HeadmanSubjectJournalParameters("
          ],
          [
            395,
            "            @NotNull @Positive Long subjectId,"
          ],
          [
            396,
            "            @NotNull @Size(min = 1, max = 3) List<@NotBlank String> lessonTypes,"
          ],
          [
            397,
            "            @Size(max = 64) List<@NotNull LocalDate> weekStarts,"
          ],
          [
            398,
            "            @NotNull ReportDownloadFormat format) {"
          ],
          [
            399,
            "        public HeadmanSubjectJournalParameters {"
          ],
          [
            400,
            "            lessonTypes = immutable(lessonTypes);"
          ],
          [
            401,
            "            weekStarts = immutable(weekStarts);"
          ],
          [
            402,
            "        }"
          ],
          [
            403,
            "    }"
          ]
        ]
      },
      {
        "title": "POST · практики предмета за одну неделю",
        "language": "json",
        "example": [
          "{",
          "  \"requestId\": \"00000000-0000-4000-8000-000000000002\",",
          "  \"report\": {",
          "    \"kind\": \"HEADMAN_SUBJECT_JOURNAL\",",
          "    \"headmanSubjectJournal\": {",
          "      \"subjectId\": 19,",
          "      \"lessonTypes\": [",
          "        \"practice\"",
          "      ],",
          "      \"weekStarts\": [",
          "        \"2026-09-14\"",
          "      ],",
          "      \"format\": \"pdf\"",
          "    }",
          "  }",
          "}"
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-export-headman-file",
    "title": "Матрица в файле старосты",
    "description": "Сервер проверяет VIEW_STATS и берёт группу из контекста. Колонки сортируются по дате, времени, номеру и ID пары. Строки объединяют участников исторического состава по ID и сортируют по имени. Сохранённые отметки начавшихся пар дают +, н или у. Ячейка без записи, вне состава или в отменённой колонке остаётся пустой. В обоих вариантах старосты файл содержит матрицу без итоговых процентов.",
    "frames": [
      {
        "title": "Право чтения своей группы и символы отметок",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            836,
            "    private void ensureHeadman() {"
          ],
          [
            837,
            "        Long groupId = requestContext.getGroupId();"
          ],
          [
            838,
            "        if (groupId == null || groupId <= 0) {"
          ],
          [
            839,
            "            throw new AccessDeniedException(\"Headman group is not available in request context\");"
          ],
          [
            840,
            "        }"
          ],
          [
            841,
            "        if (!academicGrpcClient.hasAssistantPermission(groupId, \"VIEW_STATS\")) {"
          ],
          [
            842,
            "            throw new AccessDeniedException(\"Отсутствует право VIEW_STATS\");"
          ],
          [
            843,
            "        }"
          ],
          [
            844,
            "    }"
          ],
          [
            845,
            ""
          ],
          [
            846,
            "    private static String symbolFor(AttendanceStatus status) {"
          ],
          [
            847,
            "        return switch (status) {"
          ],
          [
            848,
            "            case PRESENT -> \"+\";"
          ],
          [
            849,
            "            case ABSENT -> \"н\";"
          ],
          [
            850,
            "            case EXCUSED, FREE_ATTENDANCE -> \"у\";"
          ],
          [
            851,
            "            case CANCELLED -> \"\";"
          ],
          [
            852,
            "        };"
          ],
          [
            853,
            "    }"
          ]
        ]
      },
      {
        "title": "Недельная модель исключает отменённые колонки",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            498,
            "        return buildPeriodModel("
          ],
          [
            499,
            "                groupId,"
          ],
          [
            500,
            "                groupName,"
          ],
          [
            501,
            "                semester,"
          ],
          [
            502,
            "                semesterStart,"
          ],
          [
            503,
            "                semesterEnd,"
          ],
          [
            504,
            "                weekStart,"
          ],
          [
            505,
            "                weekStart.plusDays(6),"
          ],
          [
            506,
            "                generatedAt,"
          ],
          [
            507,
            "                null,"
          ],
          [
            508,
            "                List.of(),"
          ],
          [
            509,
            "                Set.of(),"
          ],
          [
            510,
            "                false);"
          ],
          [
            511,
            "    }"
          ],
          [
            763,
            "            if (!includeCancelled && \"CANCELLED\".equals(timing.status())) continue;"
          ],
          [
            764,
            "            result.add("
          ],
          [
            765,
            "                    new WeeklyLesson("
          ],
          [
            766,
            "                            lesson, timing, timing.date(), LocalTime.parse(lesson.getStartTime())));"
          ],
          [
            767,
            "        }"
          ],
          [
            768,
            "        result.sort("
          ],
          [
            769,
            "                Comparator.comparing(WeeklyLesson::date)"
          ],
          [
            770,
            "                        .thenComparing(WeeklyLesson::startTime)"
          ],
          [
            771,
            "                        .thenComparingInt(value -> value.lesson().getLessonNumber())"
          ],
          [
            772,
            "                        .thenComparingLong(value -> value.lesson().getId()));"
          ],
          [
            773,
            "        if (result.size() > 5_000) {"
          ],
          [
            774,
            "            throw new ReportExportTooLargeException("
          ],
          [
            775,
            "                    \"В выбранной неделе слишком много пар для передачи. Выбери другой диапазон.\");"
          ],
          [
            776,
            "        }"
          ]
        ]
      },
      {
        "title": "Состав считывается на каждую дату",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            585,
            "        Map<LocalDate, GroupMembersResponse> rosterByDate = new HashMap<>();"
          ],
          [
            586,
            "        Map<Long, WeeklyStudent> rowsById = new LinkedHashMap<>();"
          ],
          [
            587,
            "        Map<Long, Set<Long>> membersByLesson = new HashMap<>();"
          ],
          [
            588,
            "        for (WeeklyLesson lesson : lessons) {"
          ],
          [
            589,
            "            GroupMembersResponse roster ="
          ],
          [
            590,
            "                    rosterByDate.computeIfAbsent("
          ],
          [
            591,
            "                            lesson.date(),"
          ],
          [
            592,
            "                            date -> {"
          ],
          [
            593,
            "                                GroupMembersResponse value ="
          ],
          [
            594,
            "                                        academicGrpcClient.getGroupMembers("
          ],
          [
            595,
            "                                                groupId, date, semester.getId());"
          ],
          [
            596,
            "                                validateHistoricalRoster(value, date, semester.getId());"
          ],
          [
            597,
            "                                return value;"
          ],
          [
            598,
            "                            });"
          ],
          [
            599,
            "            Set<Long> memberIds = new HashSet<>();"
          ],
          [
            600,
            "            for (StudentInfo student : roster.getStudentsList()) {"
          ],
          [
            608,
            "                WeeklyStudent row ="
          ],
          [
            609,
            "                        rowsById.computeIfAbsent("
          ],
          [
            610,
            "                                student.getUserId(),"
          ],
          [
            611,
            "                                ignored ->"
          ],
          [
            612,
            "                                        new WeeklyStudent("
          ],
          [
            613,
            "                                                student.getUserId(),"
          ],
          [
            614,
            "                                                student.getDisplayName(),"
          ],
          [
            615,
            "                                                lesson.date()));"
          ],
          [
            616,
            "                if (!lesson.date().isBefore(row.nameAsOf)) {"
          ],
          [
            617,
            "                    row.displayName = student.getDisplayName();"
          ],
          [
            618,
            "                    row.nameAsOf = lesson.date();"
          ],
          [
            619,
            "                }"
          ],
          [
            620,
            "            }"
          ],
          [
            621,
            "            membersByLesson.put(lesson.lesson().getId(), memberIds);"
          ],
          [
            622,
            "        }"
          ]
        ]
      },
      {
        "title": "Пустые ячейки и строки без процентных итогов",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            662,
            "        List<Row> rows ="
          ],
          [
            663,
            "                rowsById.values().stream()"
          ],
          [
            664,
            "                        .sorted("
          ],
          [
            665,
            "                                Comparator.comparing("
          ],
          [
            666,
            "                                                (WeeklyStudent row) -> row.displayName,"
          ],
          [
            667,
            "                                                String.CASE_INSENSITIVE_ORDER)"
          ],
          [
            668,
            "                                        .thenComparingLong(row -> row.studentId))"
          ],
          [
            669,
            "                        .map("
          ],
          [
            670,
            "                                student -> {"
          ],
          [
            671,
            "                                    Map<Long, Cell> cells = new LinkedHashMap<>();"
          ],
          [
            672,
            "                                    for (WeeklyLesson lesson : lessons) {"
          ],
          [
            673,
            "                                        long lessonId = lesson.lesson().getId();"
          ],
          [
            674,
            "                                        if (!membersByLesson"
          ],
          [
            675,
            "                                                        .getOrDefault(lessonId, Set.of())"
          ],
          [
            676,
            "                                                        .contains(student.studentId)"
          ],
          [
            677,
            "                                                || !lesson.timing().hasStarted(generatedAt)"
          ],
          [
            678,
            "                                                || \"CANCELLED\".equals(lesson.timing().status()))"
          ],
          [
            679,
            "                                            continue;"
          ],
          [
            680,
            "                                        AttendanceRecord mark ="
          ],
          [
            681,
            "                                                attendance.get("
          ],
          [
            682,
            "                                                        new AttendanceKey("
          ],
          [
            683,
            "                                                                lessonId, student.studentId));"
          ],
          [
            684,
            "                                        if (mark == null"
          ],
          [
            685,
            "                                                || mark.status() == null"
          ],
          [
            686,
            "                                                || mark.status() == AttendanceStatus.CANCELLED)"
          ],
          [
            687,
            "                                            continue;"
          ],
          [
            688,
            "                                        cells.put("
          ],
          [
            689,
            "                                                lessonId,"
          ],
          [
            690,
            "                                                new Cell("
          ],
          [
            691,
            "                                                        symbolFor(mark.status()),"
          ],
          [
            692,
            "                                                        mark.status(),"
          ],
          [
            693,
            "                                                        true));"
          ],
          [
            694,
            "                                    }"
          ],
          [
            695,
            "                                    return new Row("
          ],
          [
            696,
            "                                            student.studentId,"
          ],
          [
            697,
            "                                            student.displayName,"
          ],
          [
            698,
            "                                            cells,"
          ],
          [
            699,
            "                                            Optional.empty());"
          ]
        ]
      },
      {
        "title": "Дата, номер, предмет, тип и состояние в заголовке",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyTabularRenderer.java",
        "language": "java",
        "lines": [
          [
            230,
            "    private static String columnHeader(Column column) {"
          ],
          [
            231,
            "        StringBuilder value = new StringBuilder(column.date().toString());"
          ],
          [
            232,
            "        if (column.startTime() != null) value.append('\\n').append(column.startTime());"
          ],
          [
            233,
            "        if (column.lessonNumber() > 0) value.append(\"\\n№ \").append(column.lessonNumber());"
          ],
          [
            234,
            "        if (!column.subjectLabel().isBlank()) value.append('\\n').append(column.subjectLabel());"
          ],
          [
            235,
            "        String type = column.typeLabel().isBlank() ? column.lessonType() : column.typeLabel();"
          ],
          [
            236,
            "        if (!type.isBlank()) value.append('\\n').append(type);"
          ],
          [
            237,
            "        String state = stateLabel(column.state());"
          ],
          [
            238,
            "        if (!state.isBlank()) value.append('\\n').append(state);"
          ],
          [
            239,
            "        return value.toString();"
          ],
          [
            240,
            "    }"
          ],
          [
            241,
            ""
          ],
          [
            242,
            "    private static String stateLabel(String state) {"
          ],
          [
            243,
            "        if (state == null) return \"\";"
          ],
          [
            244,
            "        return switch (state.toUpperCase(Locale.ROOT)) {"
          ],
          [
            245,
            "            case \"PLANNED\" -> \"Запланировано\";"
          ],
          [
            246,
            "            case \"ACTIVE\" -> \"Идёт\";"
          ],
          [
            247,
            "            case \"CLOSED\" -> \"Проведено\";"
          ],
          [
            248,
            "            case \"CANCELLED\" -> \"Отменено\";"
          ],
          [
            249,
            "            default -> \"\";"
          ],
          [
            250,
            "        };"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-export-teacher",
    "title": "Преподаватель · период или пара",
    "description": "Выгрузка из таблицы берёт семестр, группу, предмет, типы и выбранный период. С pastOnly она включает только CLOSED. Отменённые пары могут быть видны в истории на экране, но не входят в эту выгрузку. Из карточки пары преподаватель отправляет её дату и физический ID с выключенным pastOnly. В запрос не входят страница, пороги, выделение строки или студент графика.",
    "frames": [
      {
        "title": "Кнопка выгрузки периода берёт текущий контекст",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue",
        "language": "typescript",
        "lines": [
          [
            321,
            "async function exportJournal(): Promise<void> {"
          ],
          [
            322,
            "  const api = props.api;"
          ],
          [
            323,
            "  const value = effectiveQuery.value;"
          ],
          [
            324,
            "  const port = props.reportDownload;"
          ],
          [
            325,
            "  const lessonId = props.selectedLessonId;"
          ],
          [
            326,
            "  if (!api || !value || !canExport.value) return;"
          ],
          [
            327,
            "  const current = ++exportRevision;"
          ],
          [
            328,
            "  const context = journalContextKey(value);"
          ],
          [
            329,
            "  const format = selectedFormat.value;"
          ],
          [
            330,
            "  const active = () =>"
          ],
          [
            331,
            "    !disposed &&"
          ],
          [
            332,
            "    props.active &&"
          ],
          [
            333,
            "    current === exportRevision &&"
          ],
          [
            334,
            "    api === props.api &&"
          ],
          [
            335,
            "    port === props.reportDownload &&"
          ],
          [
            336,
            "    lessonId === props.selectedLessonId &&"
          ],
          [
            337,
            "    context === journalContextKey(effectiveQuery.value) &&"
          ],
          [
            338,
            "    format === selectedFormat.value;"
          ],
          [
            339,
            "  const selector = {"
          ],
          [
            340,
            "    ...value,"
          ],
          [
            341,
            "    pastOnly: true,"
          ],
          [
            342,
            "    ...(lessonId !== null ? { lessonId: String(lessonId) } : {}),"
          ],
          [
            343,
            "  };"
          ],
          [
            347,
            "  try {"
          ],
          [
            348,
            "    if (port) {"
          ],
          [
            349,
            "      const result = await port.download("
          ],
          [
            350,
            "        toTeacherJournalReportRequest(selector, format),"
          ],
          [
            351,
            "        active,"
          ],
          [
            352,
            "        (state) => {"
          ],
          [
            353,
            "          if (active()) exportStatus.value = exportStateLabel(state);"
          ],
          [
            354,
            "        },"
          ]
        ]
      },
      {
        "title": "Карточка открывает выгрузку выбранной пары",
        "path": "frontends/mobile-core/src/features/teacher/TeacherLessonScreen.vue",
        "language": "typescript",
        "lines": [
          [
            98,
            "        v-if=\"state\""
          ],
          [
            99,
            "        class=\"teacher-lesson-detail__download\""
          ],
          [
            100,
            "      >"
          ],
          [
            101,
            "        <button"
          ],
          [
            102,
            "          ref=\"downloadButton\""
          ],
          [
            103,
            "          class=\"teacher-screen__icon-button\""
          ],
          [
            104,
            "          type=\"button\""
          ],
          [
            105,
            "          aria-label=\"Скачать посещаемость этой пары\""
          ],
          [
            106,
            "          @click=\"emit('open-export', state.lesson.id)\""
          ],
          [
            107,
            "        >"
          ],
          [
            108,
            "          <MobileIcon name=\"download\" />"
          ],
          [
            109,
            "        </button>"
          ],
          [
            110,
            "        <TeacherLessonExportControl"
          ],
          [
            111,
            "          :api=\"api\""
          ],
          [
            112,
            "          :lesson=\"state.lesson\""
          ],
          [
            113,
            "          :report-download=\"reportDownload\""
          ],
          [
            114,
            "          :open-signal=\"exportOpenSignal\""
          ]
        ]
      },
      {
        "title": "Отдельная пара задаёт дату, тип и ID",
        "path": "frontends/mobile-core/src/features/teacher/TeacherLessonExportControl.vue",
        "language": "typescript",
        "lines": [
          [
            95,
            "async function download(format: TeacherExportFormatCode): Promise<void> {"
          ],
          [
            96,
            "  const api = props.api;"
          ],
          [
            97,
            "  if (!api || preparing.value || formatsLoading.value) return;"
          ],
          [
            98,
            "  const current = ++revision;"
          ],
          [
            99,
            "  const selectedContext = context();"
          ],
          [
            100,
            "  const port = props.reportDownload;"
          ],
          [
            101,
            "  const lesson = props.lesson;"
          ],
          [
            102,
            "  const query = {"
          ],
          [
            103,
            "    semesterId: lesson.semesterId,"
          ],
          [
            104,
            "    groupId: lesson.groupId,"
          ],
          [
            105,
            "    subjectId: lesson.subjectId,"
          ],
          [
            106,
            "    lessonTypes: [lesson.lessonType],"
          ],
          [
            107,
            "    dateFrom: lesson.date,"
          ],
          [
            108,
            "    dateTo: lesson.date,"
          ],
          [
            109,
            "    lessonId: String(lesson.id),"
          ],
          [
            110,
            "    pastOnly: false,"
          ],
          [
            111,
            "  } as const;"
          ],
          [
            122,
            "  try {"
          ],
          [
            123,
            "    if (port) {"
          ],
          [
            124,
            "      const result = await port.download("
          ],
          [
            125,
            "        toTeacherJournalReportRequest(query, format),"
          ],
          [
            126,
            "        isCurrent,"
          ],
          [
            127,
            "        (value) => {"
          ],
          [
            128,
            "          if (isCurrent()) status.value = exportStateLabel(value);"
          ],
          [
            129,
            "        },"
          ]
        ]
      },
      {
        "title": "Поля селектора TEACHER_JOURNAL",
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
        "title": "Поля запроса преподавателя",
        "path": "services/auth-service/auth-api-contract/src/main/java/ru/rutcampustrack/auth/dto/IssueReportDownloadTicketRequest.java",
        "language": "java",
        "lines": [
          [
            512,
            "    public record TeacherJournalParameters("
          ],
          [
            513,
            "            @NotNull @Positive Long semesterId,"
          ],
          [
            514,
            "            @NotNull @Positive Long groupId,"
          ],
          [
            515,
            "            @NotNull @Positive Long subjectId,"
          ],
          [
            516,
            "            @NotNull @Size(min = 1, max = 3) List<@NotBlank @Size(max = 64) String> lessonTypes,"
          ],
          [
            517,
            "            LocalDate dateFrom,"
          ],
          [
            518,
            "            LocalDate dateTo,"
          ],
          [
            519,
            "            @NotNull ReportDownloadFormat format,"
          ],
          [
            520,
            "            boolean pastOnly,"
          ],
          [
            521,
            "            @Pattern(regexp = \"[1-9][0-9]{0,18}\") String lessonId) {"
          ],
          [
            522,
            "        public TeacherJournalParameters {"
          ],
          [
            523,
            "            lessonTypes = immutable(lessonTypes);"
          ],
          [
            524,
            "        }"
          ]
        ]
      },
      {
        "title": "POST · одна пара преподавателя",
        "language": "json",
        "example": [
          "{",
          "  \"requestId\": \"00000000-0000-4000-8000-000000000003\",",
          "  \"report\": {",
          "    \"kind\": \"TEACHER_JOURNAL\",",
          "    \"teacherJournal\": {",
          "      \"semesterId\": 7,",
          "      \"groupId\": 41,",
          "      \"subjectId\": 19,",
          "      \"lessonTypes\": [",
          "        \"practice\"",
          "      ],",
          "      \"dateFrom\": \"2026-09-14\",",
          "      \"dateTo\": \"2026-09-14\",",
          "      \"lessonId\": \"731\",",
          "      \"pastOnly\": false,",
          "      \"format\": \"docx\"",
          "    }",
          "  }",
          "}"
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-export-plan",
    "title": "Отбор пар и исторического состава",
    "description": "Клиент отправляет параметры задачи через POST. Academic фиксирует снимок пар и состава на их даты для области запроса. Attendance читает этот план для выгрузки. Сервис ограничивает колонки семестром, группой, предметом, типами и датами. Если передан lessonId, должна остаться ровно одна допустимая пара. Даты без явного выбора берутся из семестра.",
    "frames": [
      {
        "title": "POST передаёт идентификатор команды и параметры отчёта",
        "path": "frontends/mobile-core/src/shared/report-download-client.ts",
        "language": "typescript",
        "lines": [
          [
            487,
            "        : await this.authenticated("
          ],
          [
            488,
            "            '/api/auth/export-jobs',"
          ],
          [
            489,
            "            'POST',"
          ],
          [
            490,
            "            { requestId: saved.requestId, report: request },"
          ],
          [
            491,
            "            current,"
          ],
          [
            492,
            "          )"
          ],
          [
            493,
            "      let job = parseExportJob(await this.json(created, current), request)"
          ]
        ]
      },
      {
        "title": "План переносит даты и ID из запроса",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/AcademicReportPlanBuilder.java",
        "language": "java",
        "lines": [
          [
            86,
            "            case TEACHER_JOURNAL -> {"
          ],
          [
            87,
            "                var p = report.teacherJournal();"
          ],
          [
            88,
            "                var range = local.semesterRange(p.semesterId());"
          ],
          [
            89,
            "                var request ="
          ],
          [
            90,
            "                        TeacherAttendanceExportRequest.newBuilder()"
          ],
          [
            91,
            "                                .setSemesterId(p.semesterId())"
          ],
          [
            92,
            "                                .setGroupId(p.groupId())"
          ],
          [
            93,
            "                                .setSubjectId(p.subjectId())"
          ],
          [
            94,
            "                                .addAllLessonTypes(p.lessonTypes())"
          ],
          [
            95,
            "                                .setFormat(p.format().code())"
          ],
          [
            96,
            "                                .setReportKind("
          ],
          [
            97,
            "                                        TeacherAttendanceReportKind"
          ],
          [
            98,
            "                                                .TEACHER_ATTENDANCE_REPORT_KIND_SUBJECT_JOURNAL)"
          ],
          [
            99,
            "                                .setDateFrom("
          ],
          [
            100,
            "                                        (p.dateFrom() == null ? range.from() : p.dateFrom())"
          ],
          [
            101,
            "                                                .toString())"
          ],
          [
            102,
            "                                .setDateTo("
          ],
          [
            103,
            "                                        (p.dateTo() == null ? range.to() : p.dateTo()).toString())"
          ],
          [
            104,
            "                                .setPastOnly(p.pastOnly());"
          ],
          [
            105,
            "                if (p.lessonId() != null) request.setLessonId(Long.parseLong(p.lessonId()));"
          ],
          [
            106,
            "                plan.setTeacherJournal(request);"
          ]
        ]
      },
      {
        "title": "План сохраняет исторический состав на даты пар",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exportjobs/ReportPlanSnapshotReader.java",
        "language": "java",
        "lines": [
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
        "title": "Attendance использует сохранённый план",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/PlannedReportRenderer.java",
        "language": "java",
        "lines": [
          [
            58,
            "            case \"TEACHER_JOURNAL\" -> {"
          ],
          [
            59,
            "                var result ="
          ],
          [
            60,
            "                        TeacherAttendanceExportService.forPlan("
          ],
          [
            61,
            "                                        plan, reports.forPlan(plan), converter)"
          ],
          [
            62,
            "                                .export(source.getTeacherJournal(), plan.getUserId());"
          ],
          [
            63,
            "                yield response("
          ],
          [
            64,
            "                        result.getContent().toByteArray(),"
          ],
          [
            65,
            "                        result.getContentType(),"
          ],
          [
            66,
            "                        result.getFileName());"
          ],
          [
            67,
            "            }"
          ],
          [
            81,
            "            case \"HEADMAN_WEEKLY_CURRENT\", \"HEADMAN_WEEKLY_SELECTED\" -> {"
          ],
          [
            82,
            "                var selection = source.getWeekly();"
          ],
          [
            83,
            "                List<LocalDate> dates = dates(selection.getWeekStartsList());"
          ],
          [
            84,
            "                var planned = weekly.forPlan(plan);"
          ],
          [
            85,
            "                var result ="
          ],
          [
            86,
            "                        \"HEADMAN_WEEKLY_CURRENT\".equals(source.getReportKind())"
          ],
          [
            87,
            "                                ? planned.exportSingleWeek(dates.getFirst(), selection.getFormat())"
          ],
          [
            88,
            "                                : planned.exportSelectedWeeks("
          ],
          [
            89,
            "                                        new HeadmanWeeklyExportRequest("
          ],
          [
            90,
            "                                                dates, selection.getFormat()));"
          ],
          [
            91,
            "                yield response(result.content(), result.contentType(), result.fileName());"
          ]
        ]
      },
      {
        "title": "Фильтры и ограничение конкретным ID",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java",
        "language": "java",
        "lines": [
          [
            181,
            "        List<LessonResponse> lessons = new ArrayList<>();"
          ],
          [
            182,
            "        for (LessonResponse lesson :"
          ],
          [
            183,
            "                schedule.getLessonsByGroup("
          ],
          [
            184,
            "                                groupId,"
          ],
          [
            185,
            "                                semesterId,"
          ],
          [
            186,
            "                                period.from().toString(),"
          ],
          [
            187,
            "                                period.to().toString())"
          ],
          [
            188,
            "                        .getLessonsList()) {"
          ],
          [
            189,
            "            if (lesson.getGroupId() != groupId"
          ],
          [
            190,
            "                    || lesson.getSemesterId() != semesterId"
          ],
          [
            191,
            "                    || lesson.getSubjectId() != subjectId"
          ],
          [
            192,
            "                    || types.stream()"
          ],
          [
            193,
            "                            .noneMatch(type -> type.equalsIgnoreCase(lesson.getLessonType()))) {"
          ],
          [
            194,
            "                continue;"
          ],
          [
            195,
            "            }"
          ],
          [
            196,
            "            if (request.hasLessonId() && lesson.getId() != request.getLessonId()) continue;"
          ],
          [
            197,
            "            if (request.getPastOnly() && !\"closed\".equalsIgnoreCase(lesson.getStatus())) continue;"
          ],
          [
            198,
            "            if (lesson.getId() <= 0) {"
          ],
          [
            199,
            "                throw new AcademicServiceUnavailableException("
          ],
          [
            200,
            "                        \"Schedule returned an invalid lesson identity\");"
          ],
          [
            201,
            "            }"
          ],
          [
            202,
            "            LocalDate lessonDate = parseDate(lesson.getDate(), \"lesson.date\");"
          ],
          [
            203,
            "            if (!lessonDate.isBefore(period.from()) && !lessonDate.isAfter(period.to()))"
          ],
          [
            204,
            "                lessons.add(lesson);"
          ],
          [
            205,
            "        }"
          ],
          [
            206,
            "        if (request.hasLessonId() && lessons.size() != 1) {"
          ],
          [
            207,
            "            throw new BadRequestException(\"lesson_id does not match the eligible journal context\");"
          ],
          [
            208,
            "        }"
          ],
          [
            209,
            "        lessons.sort("
          ],
          [
            210,
            "                Comparator.comparing("
          ],
          [
            211,
            "                                (LessonResponse value) -> parseDate(value.getDate(), \"lesson.date\"))"
          ],
          [
            212,
            "                        .thenComparing("
          ],
          [
            213,
            "                                value -> parseTime(value.getStartTime()),"
          ],
          [
            214,
            "                                Comparator.nullsLast(Comparator.naturalOrder()))"
          ],
          [
            215,
            "                        .thenComparingInt(LessonResponse::getLessonNumber)"
          ],
          [
            216,
            "                        .thenComparingLong(LessonResponse::getId));"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-export-teacher-file",
    "title": "Отметки и итоги преподавателя",
    "description": "Файл содержит имена, даты и номера пар, символы отметок и итоговые счётчики с процентами по студентам. В выгрузке закрытых пар отсутствие записи у участника обозначается «н». В карточке одной пары пустая отметка участника может отображаться точкой, а вне состава ячейка остаётся пустой. Подробный текст причины и документы в модель ячейки не входят.",
    "frames": [
      {
        "title": "Выгрузка читает исторические списки",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/planned/PlannedTeacherJournalReader.java",
        "language": "java",
        "lines": [
          [
            43,
            "        for (LessonResponse lesson : lessons) {"
          ],
          [
            44,
            "            if (lesson.getGroupId() != first.getGroupId()"
          ],
          [
            45,
            "                    || lesson.getSubjectId() != first.getSubjectId()"
          ],
          [
            46,
            "                    || lesson.getSemesterId() != first.getSemesterId()) throw invalid();"
          ],
          [
            47,
            "            if (pastOnly && !completed(lesson)) throw invalid();"
          ],
          [
            48,
            "            var roster = reports.getTeacherLessonAttendance(lesson.getId(), teacherId);"
          ],
          [
            49,
            "            for (var entry : roster.getEntries()) {"
          ],
          [
            50,
            "                var student ="
          ],
          [
            51,
            "                        students.computeIfAbsent("
          ],
          [
            52,
            "                                entry.getUserId(),"
          ],
          [
            53,
            "                                id ->"
          ],
          [
            54,
            "                                        TeacherJournalStudent.newBuilder()"
          ],
          [
            55,
            "                                                .setStudentId(id)"
          ],
          [
            56,
            "                                                .setDisplayName(entry.getDisplayName()));"
          ],
          [
            57,
            "                boolean marked = entry.getStatus() != null;"
          ],
          [
            58,
            "                String status = marked ? entry.getStatus() : \"\";"
          ],
          [
            59,
            "                String symbol = entry.getSymbol() == null ? \"\" : entry.getSymbol();"
          ],
          [
            60,
            "                // Preserve the existing completed-history display; this never creates a mark."
          ],
          [
            61,
            "                if (pastOnly && \"closed\".equalsIgnoreCase(lesson.getStatus()) && !marked) {"
          ],
          [
            62,
            "                    status = \"absent\";"
          ],
          [
            63,
            "                    symbol = \"н\";"
          ],
          [
            64,
            "                }"
          ],
          [
            65,
            "                student.addCells("
          ],
          [
            66,
            "                        TeacherJournalCell.newBuilder()"
          ],
          [
            67,
            "                                .setLessonId(lesson.getId())"
          ],
          [
            68,
            "                                .setStatus(status)"
          ],
          [
            69,
            "                                .setSymbol(symbol)"
          ],
          [
            70,
            "                                .setRecordPresent(marked)"
          ],
          [
            71,
            "                                .setSource(entry.getSource() == null ? \"\" : entry.getSource()));"
          ],
          [
            72,
            "            }"
          ]
        ]
      },
      {
        "title": "Строки соединяются по ID, пары читаются пачками",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java",
        "language": "java",
        "lines": [
          [
            321,
            "        for (String type : types) {"
          ],
          [
            322,
            "            List<Long> ids ="
          ],
          [
            323,
            "                    lessons.stream()"
          ],
          [
            324,
            "                            .filter(lesson -> type.equalsIgnoreCase(lesson.getLessonType()))"
          ],
          [
            325,
            "                            .map(LessonResponse::getId)"
          ],
          [
            326,
            "                            .toList();"
          ],
          [
            327,
            "            for (int offset = 0; offset < ids.size(); offset += JOURNAL_BATCH_SIZE) {"
          ],
          [
            328,
            "                List<Long> batch ="
          ],
          [
            329,
            "                        ids.subList(offset, Math.min(ids.size(), offset + JOURNAL_BATCH_SIZE));"
          ],
          [
            330,
            "                TeacherJournalResponse journal = journalReader.read(teacherId, batch, pastOnly);"
          ],
          [
            331,
            "                for (TeacherJournalStudent student : journal.getStudentsList()) {"
          ],
          [
            332,
            "                    MutableRow row ="
          ],
          [
            333,
            "                            rows.computeIfAbsent("
          ],
          [
            334,
            "                                    student.getStudentId(),"
          ],
          [
            335,
            "                                    ignored ->"
          ],
          [
            336,
            "                                            new MutableRow("
          ],
          [
            337,
            "                                                    student.getStudentId(),"
          ],
          [
            338,
            "                                                    student.getDisplayName()));"
          ],
          [
            344,
            "                        if (!journalableLessonIds.contains(cell.getLessonId())) continue;"
          ],
          [
            345,
            "                        if (row.cells.containsKey(cell.getLessonId())) {"
          ],
          [
            346,
            "                            throw new ReportExportUnavailableException("
          ],
          [
            347,
            "                                    \"Journal returned a duplicate lesson cell\");"
          ],
          [
            348,
            "                        }"
          ],
          [
            349,
            "                        String symbol = value(cell.getSymbol());"
          ],
          [
            350,
            "                        if (!cell.getRecordPresent() && symbol.isBlank()) symbol = \"·\";"
          ],
          [
            351,
            "                        row.cells.put("
          ],
          [
            352,
            "                                cell.getLessonId(),"
          ],
          [
            353,
            "                                new TeacherAttendanceExportModel.Cell("
          ],
          [
            354,
            "                                        symbol,"
          ],
          [
            355,
            "                                        parseStatus(cell.getStatus()),"
          ],
          [
            356,
            "                                        cell.getRecordPresent()));"
          ]
        ]
      },
      {
        "title": "Сортировка строк и метрики для выбранных пар",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java",
        "language": "java",
        "lines": [
          [
            362,
            "        Map<Long, TeacherAttendanceExportModel.Metrics> metricsByStudent = new HashMap<>();"
          ],
          [
            363,
            "        for (ReportService.TeacherStudentStats student : stats.students()) {"
          ],
          [
            364,
            "            TeacherAttendanceExportModel.Metrics metrics = metrics(student);"
          ],
          [
            365,
            "            metricsByStudent.put(student.studentId(), metrics);"
          ],
          [
            366,
            "            rows.computeIfAbsent("
          ],
          [
            367,
            "                    student.studentId(),"
          ],
          [
            368,
            "                    ignored -> new MutableRow(student.studentId(), student.displayName()));"
          ],
          [
            369,
            "            if (metrics.denominator() > 0 && rows.get(student.studentId()).cells.isEmpty()) {"
          ],
          [
            370,
            "                throw new ReportExportUnavailableException("
          ],
          [
            371,
            "                        \"Attendance metrics have no matching journal cells\");"
          ],
          [
            372,
            "            }"
          ],
          [
            373,
            "        }"
          ],
          [
            374,
            ""
          ],
          [
            375,
            "        List<TeacherAttendanceExportModel.Row> resultRows ="
          ],
          [
            376,
            "                rows.values().stream()"
          ],
          [
            377,
            "                        .sorted("
          ],
          [
            378,
            "                                Comparator.comparing("
          ],
          [
            379,
            "                                                (MutableRow row) -> row.displayName,"
          ],
          [
            380,
            "                                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))"
          ],
          [
            381,
            "                                        .thenComparingLong(row -> row.studentId))"
          ],
          [
            382,
            "                        .map("
          ],
          [
            383,
            "                                row -> {"
          ],
          [
            384,
            "                                    Map<Long, TeacherAttendanceExportModel.Cell> orderedCells ="
          ],
          [
            385,
            "                                            new LinkedHashMap<>();"
          ],
          [
            386,
            "                                    for (TeacherAttendanceExportModel.Column column : columns) {"
          ],
          [
            387,
            "                                        TeacherAttendanceExportModel.Cell cell ="
          ],
          [
            388,
            "                                                row.cells.get(column.lessonId());"
          ],
          [
            389,
            "                                        if (cell != null) orderedCells.put(column.lessonId(), cell);"
          ],
          [
            390,
            "                                    }"
          ],
          [
            391,
            "                                    return new TeacherAttendanceExportModel.Row("
          ],
          [
            392,
            "                                            row.studentId,"
          ],
          [
            393,
            "                                            row.displayName,"
          ],
          [
            394,
            "                                            orderedCells,"
          ],
          [
            395,
            "                                            java.util.Optional.of("
          ],
          [
            396,
            "                                                    metricsByStudent.getOrDefault("
          ],
          [
            397,
            "                                                            row.studentId, emptyMetrics())));"
          ]
        ]
      },
      {
        "title": "Модель ячейки хранит символ, статус и наличие записи",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportModel.java",
        "language": "java",
        "lines": [
          [
            113,
            "    public record Row("
          ],
          [
            114,
            "            long studentId,"
          ],
          [
            115,
            "            String displayName,"
          ],
          [
            116,
            "            Map<Long, Cell> cellsByLessonId,"
          ],
          [
            117,
            "            Optional<Metrics> metrics) {"
          ],
          [
            118,
            "        public Row {"
          ],
          [
            119,
            "            displayName = Objects.requireNonNullElse(displayName, \"\");"
          ],
          [
            120,
            "            cellsByLessonId ="
          ],
          [
            121,
            "                    Collections.unmodifiableMap("
          ],
          [
            122,
            "                            new LinkedHashMap<>("
          ],
          [
            123,
            "                                    Objects.requireNonNull(cellsByLessonId, \"cellsByLessonId\")));"
          ],
          [
            124,
            "            metrics = Objects.requireNonNull(metrics, \"metrics\");"
          ],
          [
            125,
            "        }"
          ],
          [
            126,
            "    }"
          ],
          [
            127,
            ""
          ],
          [
            128,
            "    /**"
          ],
          [
            129,
            "     * A missing key in {@code cellsByLessonId} means there is no cell for that student/lesson pair."
          ],
          [
            130,
            "     * It must not be interpreted as an absent mark."
          ],
          [
            131,
            "     */"
          ],
          [
            132,
            "    public record Cell(String symbol, AttendanceStatus status, boolean recordPresent) {"
          ],
          [
            133,
            "        public Cell {"
          ],
          [
            134,
            "            symbol = Objects.requireNonNullElse(symbol, \"\");"
          ],
          [
            135,
            "        }"
          ],
          [
            136,
            "    }"
          ]
        ]
      },
      {
        "title": "DOCX рисует пустую ячейку и отдельные итоги",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java",
        "language": "java",
        "lines": [
          [
            179,
            "        for (int rowIndex = 0; rowIndex < model.rows().size(); rowIndex++) {"
          ],
          [
            180,
            "            Row row = model.rows().get(rowIndex);"
          ],
          [
            181,
            "            boolean alternate = rowIndex % 2 == 1;"
          ],
          [
            182,
            "            xml.append(rowStart(false));"
          ],
          [
            183,
            "            xml.append(cell(Integer.toString(rowIndex + 1), widths[0], false, true, alternate, 17));"
          ],
          [
            184,
            "            xml.append(cell(displayName(row), widths[1], false, false, alternate, 17));"
          ],
          [
            185,
            "            for (int lessonIndex = firstColumn; lessonIndex < endColumn; lessonIndex++) {"
          ],
          [
            186,
            "                Column column = model.columns().get(lessonIndex);"
          ],
          [
            187,
            "                var mark = row.cellsByLessonId().get(column.lessonId());"
          ],
          [
            188,
            "                // A missing lesson key remains blank; absence is never inferred by the renderer."
          ],
          [
            189,
            "                String symbol = mark == null ? \"\" : (mark.symbol().isBlank() ? \"·\" : mark.symbol());"
          ],
          [
            190,
            "                xml.append("
          ],
          [
            191,
            "                        cell("
          ],
          [
            192,
            "                                symbol,"
          ],
          [
            193,
            "                                widths[lessonIndex - firstColumn + 2],"
          ],
          [
            194,
            "                                false,"
          ],
          [
            195,
            "                                true,"
          ],
          [
            196,
            "                                alternate,"
          ],
          [
            197,
            "                                19));"
          ],
          [
            198,
            "            }"
          ],
          [
            199,
            "            xml.append(\"</w:tr>\");"
          ],
          [
            238,
            "            String[] values = {"
          ],
          [
            239,
            "                displayName(row),"
          ],
          [
            240,
            "                Integer.toString(metrics.presentCount()),"
          ],
          [
            241,
            "                Integer.toString(metrics.excusedCount()),"
          ],
          [
            242,
            "                Integer.toString(metrics.absentCount()),"
          ],
          [
            243,
            "                Integer.toString(metrics.presentOrExcusedCount()),"
          ],
          [
            244,
            "                Integer.toString(metrics.denominator()),"
          ],
          [
            245,
            "                percent(metrics.percentPresent(), metrics.denominator()),"
          ],
          [
            246,
            "                percent(metrics.percentPresentOrExcused(), metrics.denominator()),"
          ],
          [
            247,
            "                percent(metrics.percentExcused(), metrics.denominator()),"
          ],
          [
            248,
            "                percent(metrics.percentAbsent(), metrics.denominator())"
          ]
        ]
      },
      {
        "title": "Числовые столбцы и доли в XLSX",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java",
        "language": "java",
        "lines": [
          [
            736,
            "                values.add("
          ],
          [
            737,
            "                        SheetValue.text("
          ],
          [
            738,
            "                                safeCellValue(row.cellsByLessonId().get(column.lessonId()))));"
          ],
          [
            739,
            "            }"
          ],
          [
            740,
            "            TeacherAttendanceExportModel.Metrics metrics = row.metrics().orElseThrow();"
          ],
          [
            741,
            "            values.add(SheetValue.number(metrics.denominator()));"
          ],
          [
            742,
            "            addMetricValues("
          ],
          [
            743,
            "                    values,"
          ],
          [
            744,
            "                    metrics.presentCount(),"
          ],
          [
            745,
            "                    metrics.percentPresent(),"
          ],
          [
            746,
            "                    metrics.denominator());"
          ],
          [
            747,
            "            addMetricValues("
          ],
          [
            748,
            "                    values,"
          ],
          [
            749,
            "                    metrics.presentOrExcusedCount(),"
          ],
          [
            750,
            "                    metrics.percentPresentOrExcused(),"
          ],
          [
            751,
            "                    metrics.denominator());"
          ],
          [
            752,
            "            addMetricValues("
          ],
          [
            753,
            "                    values,"
          ],
          [
            754,
            "                    metrics.excusedCount(),"
          ],
          [
            755,
            "                    metrics.percentExcused(),"
          ],
          [
            756,
            "                    metrics.denominator());"
          ],
          [
            757,
            "            addMetricValues("
          ],
          [
            758,
            "                    values, metrics.absentCount(), metrics.percentAbsent(), metrics.denominator());"
          ],
          [
            759,
            "            appendSheetValuesRow(sheet, rowIndex++, values, false);"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-export-binary",
    "title": "Формирование и скачивание файла",
    "description": "DOCX собирается по шаблону. PDF и PNG получаются из DOCX, а HTML и XLSX формируются как таблицы. Для преподавателя DOCX содержит отдельный раздел итогов. Лимит выгрузки преподавателя — 5 000 пар, оба сервиса ограничивают размер ответа 20 МиБ. Скачивание возвращает байты с Content-Type и именем в Content-Disposition.",
    "frames": [
      {
        "title": "Шаблоны DOCX для недельного и предметного отчётов",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceDocxRenderer.java",
        "language": "java",
        "lines": [
          [
            54,
            "    /** Renders one weekly report or one subject journal according to its frozen model kind. */"
          ],
          [
            55,
            "    public byte[] render(TeacherAttendanceExportModel model) {"
          ],
          [
            56,
            "        Objects.requireNonNull(model, \"model\");"
          ],
          [
            57,
            "        ReportKind kind = model.context().kind();"
          ],
          [
            58,
            "        String template ="
          ],
          [
            59,
            "                switch (kind) {"
          ],
          [
            60,
            "                    case WEEKLY_ATTENDANCE -> WEEKLY_TEMPLATE;"
          ],
          [
            61,
            "                    case SUBJECT_JOURNAL, HEADMAN_SUBJECT_JOURNAL -> SUBJECT_TEMPLATE;"
          ],
          [
            62,
            "                };"
          ],
          [
            63,
            "        TemplatePackage templatePackage = readTemplate(template);"
          ],
          [
            64,
            "        return writePackage("
          ],
          [
            65,
            "                templatePackage.entries(), renderDocumentXml(templatePackage.documentXml(), model));"
          ],
          [
            66,
            "    }"
          ],
          [
            124,
            "        documentXml ="
          ],
          [
            125,
            "                replacePlaceholderParagraph("
          ],
          [
            126,
            "                        documentXml, \"MATRIX_TABLE\", selection + matrixTables(model));"
          ],
          [
            127,
            "        String summaryTable = context.kind() == ReportKind.SUBJECT_JOURNAL ? summary(model) : \"\";"
          ],
          [
            128,
            "        documentXml ="
          ],
          [
            129,
            "                replacePreviousTitleAndPlaceholderParagraph("
          ],
          [
            130,
            "                        documentXml, \"Итоги по студентам\", \"SUMMARY_TABLE\", summaryTable);"
          ]
        ]
      },
      {
        "title": "Староста получает DOCX, конверсию или таблицу",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanWeeklyReportService.java",
        "language": "java",
        "lines": [
          [
            216,
            "        byte[] content;"
          ],
          [
            217,
            "        switch (format) {"
          ],
          [
            218,
            "            case DOCX -> content = docxRenderer.render(models);"
          ],
          [
            219,
            "            case HTML -> content = tabularRenderer.renderHtml(models);"
          ],
          [
            220,
            "            case XLSX -> content = tabularRenderer.renderXlsx(models);"
          ],
          [
            221,
            "            case PDF, PNG -> {"
          ],
          [
            222,
            "                byte[] docx = docxRenderer.render(models);"
          ],
          [
            223,
            "                TargetFormat target ="
          ],
          [
            224,
            "                        format == HeadmanWeeklyReportFormat.PDF"
          ],
          [
            225,
            "                                ? TargetFormat.PDF"
          ],
          [
            226,
            "                                : TargetFormat.PNG_PAGES_ZIP;"
          ],
          [
            227,
            "                try {"
          ],
          [
            228,
            "                    content ="
          ],
          [
            229,
            "                            documentRendererGrpcClient.convertDocxForHeadmanWeeklyExport("
          ],
          [
            230,
            "                                    docx, target);"
          ],
          [
            231,
            "                } catch (StatusRuntimeException ex) {"
          ],
          [
            243,
            "        if (content == null || content.length == 0) {"
          ],
          [
            244,
            "            throw new ReportExportUnavailableException("
          ],
          [
            245,
            "                    \"Сервер формирования не вернул файл журнала\");"
          ],
          [
            246,
            "        }"
          ],
          [
            247,
            "        if (content.length > MAX_EXPORT_RESPONSE_BYTES) {"
          ],
          [
            248,
            "            throw new ReportExportTooLargeException("
          ],
          [
            249,
            "                    \"Экспорт превышает общий лимит ответа 20 МиБ. Выбери меньше недель или формат\""
          ],
          [
            250,
            "                            + \" HTML/XLSX.\");"
          ],
          [
            251,
            "        }"
          ],
          [
            252,
            "        return new HeadmanWeeklyExportResult(fileName, format.contentType(), content);"
          ]
        ]
      },
      {
        "title": "Рендеринг преподавателя и ZIP страниц",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java",
        "language": "java",
        "lines": [
          [
            1007,
            "        private byte[] render("
          ],
          [
            1008,
            "                TeacherAttendanceExportModel model,"
          ],
          [
            1009,
            "                TeacherAttendanceDocxRenderer docxRenderer,"
          ],
          [
            1010,
            "                DocumentRendererGrpcClient renderer) {"
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
          ],
          [
            1021,
            "            };"
          ],
          [
            1022,
            "        }"
          ]
        ]
      },
      {
        "title": "Ограничение числа пар и размера файла",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/teacher/TeacherAttendanceExportService.java",
        "language": "java",
        "lines": [
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
      },
      {
        "title": "Бинарный ответ и заголовки скачивания",
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
      }
    ]
  }
]
