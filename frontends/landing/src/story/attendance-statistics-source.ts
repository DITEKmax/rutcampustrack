import type { StoryOperation } from './story-source'

export const attendanceStatisticsOperations: readonly StoryOperation[] = [
  {
    "id": "attendance-statistics-denominator",
    "title": "Как считается процент",
    "description": "В примере два студента и три закрытые пары. У первого одна отметка «присутствовал», одна уважительная и одна пустая ячейка. У второго две отметки «присутствовал» и одна уважительная. Отменённая четвёртая пара исключена. Для первого студента знаменатель равен 3. Для группы знаменатель равен 6. В этих ячейках три присутствия и две уважительные причины. Доля присутствия группы 50%, с уважительными около 83,3%. В групповых отчётах свободное посещение тоже входит в уважительные.",
    "frames": [
      {
        "title": "Исторический состав для каждой пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1364,
            "        Map<String, GroupMembersResponse> rosters = new java.util.HashMap<>();"
          ],
          [
            1365,
            "        for (LessonInfo lesson : lessons) {"
          ],
          [
            1366,
            "            GroupStatsAccumulator group ="
          ],
          [
            1367,
            "                    groups.computeIfAbsent("
          ],
          [
            1368,
            "                            lesson.getGroupId(),"
          ],
          [
            1369,
            "                            ignored -> new GroupStatsAccumulator(lesson.getGroupId()));"
          ],
          [
            1370,
            "            boolean counted = eligible.test(lesson);"
          ],
          [
            1371,
            "            if (counted) group.lessonsCount++;"
          ],
          [
            1372,
            "            StatsCounter lessonCounter = counted && series != null ? new StatsCounter() : null;"
          ],
          [
            1373,
            "            if (lessonCounter != null) series.put(lesson.getLessonId(), lessonCounter);"
          ],
          [
            1374,
            "            GroupMembersResponse roster ="
          ],
          [
            1375,
            "                    rosters.computeIfAbsent("
          ],
          [
            1376,
            "                            lesson.getGroupId()"
          ],
          [
            1377,
            "                                    + \":\""
          ],
          [
            1378,
            "                                    + lesson.getSemesterId()"
          ],
          [
            1379,
            "                                    + \":\""
          ],
          [
            1380,
            "                                    + lesson.getDate(),"
          ],
          [
            1381,
            "                            ignored -> historicalMembersFor(lesson));"
          ],
          [
            1382,
            "            Map<Long, AttendanceRecord> records ="
          ],
          [
            1390,
            "            for (StudentInfo member : roster.getStudentsList()) {"
          ],
          [
            1391,
            "                if (member.getUserId() <= 0) {"
          ],
          [
            1392,
            "                    throw new AcademicServiceUnavailableException("
          ],
          [
            1393,
            "                            \"Academic returned an invalid student identity\");"
          ],
          [
            1394,
            "                }"
          ],
          [
            1404,
            "                if (!counted) continue;"
          ],
          [
            1405,
            "                AttendanceRecord record = records.get(member.getUserId());"
          ],
          [
            1406,
            "                // Closed lessons normally have an AUTO_SCHEDULER ABSENT record."
          ],
          [
            1407,
            "                // Treat a missing historical mark as ABSENT so the denominator is"
          ],
          [
            1408,
            "                // the passed lesson roster, never the number of stored documents."
          ],
          [
            1409,
            "                AttendanceStatus status ="
          ],
          [
            1410,
            "                        record == null || record.status() == null"
          ],
          [
            1411,
            "                                ? AttendanceStatus.ABSENT"
          ],
          [
            1412,
            "                                : record.status();"
          ],
          [
            1413,
            "                if (student != null) student.counter.add(status);"
          ],
          [
            1414,
            "                group.counter.add(status);"
          ]
        ]
      },
      {
        "title": "Что идёт в числители и знаменатель",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1898,
            "        void add(AttendanceStatus status) {"
          ],
          [
            1899,
            "            if (status == AttendanceStatus.CANCELLED) return;"
          ],
          [
            1900,
            "            denominator++;"
          ],
          [
            1901,
            "            switch (status) {"
          ],
          [
            1902,
            "                case PRESENT -> {"
          ],
          [
            1903,
            "                    present++;"
          ],
          [
            1904,
            "                    presentOrExcused++;"
          ],
          [
            1905,
            "                }"
          ],
          [
            1906,
            "                case EXCUSED, FREE_ATTENDANCE -> {"
          ],
          [
            1907,
            "                    presentOrExcused++;"
          ],
          [
            1908,
            "                    excused++;"
          ],
          [
            1909,
            "                }"
          ],
          [
            1910,
            "                case ABSENT -> absent++;"
          ],
          [
            1911,
            "                case CANCELLED -> {"
          ],
          [
            1912,
            "                    /* handled above */"
          ],
          [
            1913,
            "                }"
          ],
          [
            1914,
            "            }"
          ],
          [
            1915,
            "        }"
          ],
          [
            1916,
            ""
          ],
          [
            1917,
            "        TeacherMetric presentMetric() {"
          ],
          [
            1918,
            "            return TeacherMetric.of(present, denominator);"
          ],
          [
            1919,
            "        }"
          ],
          [
            1920,
            ""
          ],
          [
            1921,
            "        TeacherMetric presentOrExcusedMetric() {"
          ],
          [
            1922,
            "            return TeacherMetric.of(presentOrExcused, denominator);"
          ],
          [
            1923,
            "        }"
          ]
        ]
      },
      {
        "title": "Формула процента",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1811,
            "    public record TeacherMetric(int numerator, int denominator, double percent) {"
          ],
          [
            1812,
            "        private static TeacherMetric of(int numerator, int denominator) {"
          ],
          [
            1813,
            "            return new TeacherMetric("
          ],
          [
            1814,
            "                    numerator,"
          ],
          [
            1815,
            "                    denominator,"
          ],
          [
            1816,
            "                    denominator == 0 ? 0.0 : numerator * 100.0 / denominator);"
          ],
          [
            1817,
            "        }"
          ],
          [
            1818,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-statistics-student",
    "title": "Студент · личная статистика",
    "description": "PWA и Mini App запрашивают личную статистику в Mobile BFF для активного семестра. Attendance получает разрешённый учебный контекст и читает отметки пользователя. В held входят закрытые пары. Пустая ячейка закрытой пары даёт ABSENT. Открытые и запланированные пары учитываются в planned, отменённые и исходные экземпляры после переноса исключаются.",
    "frames": [
      {
        "title": "Mobile BFF сверяет активный семестр",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java",
        "language": "java",
        "lines": [
          [
            198,
            "    public StudentStatisticsResponse statistics(long requestedSemesterId) {"
          ],
          [
            199,
            "        SemesterResponse active = requireActiveSemester(requestedSemesterId);"
          ],
          [
            200,
            "        StudentAttendanceProjectionResponse response ="
          ],
          [
            201,
            "                attendance.projection(requestedSemesterId, null, \"weeks\", List.of());"
          ],
          [
            202,
            "        return statisticsResponse(response, active);"
          ],
          [
            203,
            "    }"
          ],
          [
            375,
            "    private SemesterResponse requireActiveSemester(long requestedSemesterId) {"
          ],
          [
            376,
            "        requireStudent(false);"
          ],
          [
            377,
            "        SemesterResponse active = academic.activeSemester();"
          ],
          [
            378,
            "        if (active.getId() != requestedSemesterId) {"
          ],
          [
            379,
            "            throw new MobileBffException("
          ],
          [
            380,
            "                    HttpStatus.FORBIDDEN,"
          ],
          [
            381,
            "                    ProblemCode.OUT_OF_SCOPE,"
          ],
          [
            382,
            "                    \"Семестр не входит в scope студента\");"
          ],
          [
            383,
            "        }"
          ],
          [
            384,
            "        return active;"
          ],
          [
            385,
            "    }"
          ]
        ]
      },
      {
        "title": "Attendance строит проекцию для пользователя из сессии",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            113,
            "    public void getStudentAttendanceProjection("
          ],
          [
            114,
            "            StudentAttendanceProjectionRequest request,"
          ],
          [
            115,
            "            StreamObserver<StudentAttendanceProjectionResponse> observer) {"
          ],
          [
            116,
            "        try {"
          ],
          [
            117,
            "            if (projectionService == null) {"
          ],
          [
            118,
            "                throw new AcademicServiceUnavailableException("
          ],
          [
            119,
            "                        \"Student attendance projection is not configured\");"
          ],
          [
            120,
            "            }"
          ],
          [
            121,
            "            InternalJwtClaims claims = requireClaims();"
          ],
          [
            122,
            "            var projection ="
          ],
          [
            123,
            "                    projectionService.project("
          ],
          [
            124,
            "                            claims,"
          ],
          [
            125,
            "                            request.getSemesterId(),"
          ],
          [
            126,
            "                            request.hasSubjectId() ? request.getSubjectId() : null,"
          ],
          [
            127,
            "                            request.getRange(),"
          ],
          [
            128,
            "                            request.getLessonTypesList());"
          ],
          [
            129,
            "            observer.onNext("
          ],
          [
            130,
            "                    toProto(projection, projectionRequestOptions(claims, request, projection)));"
          ]
        ]
      },
      {
        "title": "Свой учебный контекст и свои записи",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            93,
            "        requireStudent(claims);"
          ],
          [
            94,
            "        if (semesterId <= 0) {"
          ],
          [
            95,
            "            throw new StudentCheckinException("
          ],
          [
            96,
            "                    StudentCheckinException.Code.INVALID_REQUEST,"
          ],
          [
            97,
            "                    \"semester_id должен быть положительным\");"
          ],
          [
            98,
            "        }"
          ],
          [
            99,
            "        String normalizedRange = normalizeRange(range);"
          ],
          [
            100,
            "        Set<String> normalizedTypes = normalizeTypes(requestedTypes);"
          ],
          [
            101,
            ""
          ],
          [
            102,
            "        StudentProjectionScopeResponse scope ="
          ],
          [
            103,
            "                academicGrpcClient.resolveStudentProjectionScope(semesterId);"
          ],
          [
            104,
            "        validateScope(scope, claims, semesterId, subjectId);"
          ],
          [
            105,
            "        Map<Long, AcademicSubjectInfo> subjects = subjectMap(scope);"
          ],
          [
            106,
            "        if (subjectId != null && !subjects.containsKey(subjectId)) {"
          ],
          [
            107,
            "            throw new StudentCheckinException("
          ],
          [
            108,
            "                    StudentCheckinException.Code.OUT_OF_SCOPE, \"Предмет не входит в student scope\");"
          ],
          [
            109,
            "        }"
          ],
          [
            111,
            "        LocalDate serverDate = parseRequiredDate(scope.getServerDate(), \"server_date\");"
          ],
          [
            112,
            "        // Materialize the complete authorized membership view first.  Subject"
          ],
          [
            113,
            "        // and lesson-type filters affect only the returned slice; they must"
          ],
          [
            114,
            "        // never change semester totals or the canonical rank denominator."
          ],
          [
            115,
            "        List<Lesson> allLessons = loadLessons(scope, semesterId, subjects, null, Set.of());"
          ],
          [
            116,
            "        Map<Long, AttendanceRecord> ownMarks ="
          ],
          [
            117,
            "                markIndex("
          ],
          [
            118,
            "                        attendanceReadPort.findByUserId(claims.userId(), semesterId),"
          ],
          [
            119,
            "                        allLessons,"
          ],
          [
            120,
            "                        claims.userId(),"
          ],
          [
            121,
            "                        semesterId);"
          ]
        ]
      },
      {
        "title": "Как считается held и что исключается",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculator.java",
        "language": "java",
        "lines": [
          [
            297,
            "            AttendanceStatus status = occurrence.attendanceStatus();"
          ],
          [
            298,
            "            switch (state) {"
          ],
          [
            299,
            "                case PLANNED, ACTIVE -> {"
          ],
          [
            300,
            "                    // Check-in can legitimately persist a mark before the"
          ],
          [
            301,
            "                    // schedule transition reaches CLOSED. It stays outside H."
          ],
          [
            302,
            "                    rejectIncludedStatus(id, status);"
          ],
          [
            303,
            "                    plannedCount = addOne(plannedCount, \"planned count\");"
          ],
          [
            304,
            "                }"
          ],
          [
            305,
            "                case CLOSED -> {"
          ],
          [
            306,
            "                    plannedCount = addOne(plannedCount, \"planned count\");"
          ],
          [
            307,
            "                    heldCount = addOne(heldCount, \"held count\");"
          ],
          [
            308,
            "                    if (status == null) {"
          ],
          [
            309,
            "                        absentCount = addOne(absentCount, \"absent count\");"
          ],
          [
            310,
            "                        missingClosedCount = addOne(missingClosedCount, \"missing closed count\");"
          ],
          [
            311,
            "                    } else {"
          ],
          [
            312,
            "                        switch (status) {"
          ],
          [
            313,
            "                            case PRESENT -> presentCount = addOne(presentCount, \"present count\");"
          ],
          [
            314,
            "                            case ABSENT -> absentCount = addOne(absentCount, \"absent count\");"
          ],
          [
            315,
            "                            case EXCUSED -> excusedCount = addOne(excusedCount, \"excused count\");"
          ],
          [
            316,
            "                            case FREE_ATTENDANCE ->"
          ],
          [
            317,
            "                                    throw StudentProjectionException.unsupportedAttendanceStatus("
          ],
          [
            318,
            "                                            id, status);"
          ],
          [
            319,
            "                            case CANCELLED ->"
          ],
          [
            320,
            "                                    throw StudentProjectionException.invalidOccurrence("
          ],
          [
            321,
            "                                            \"CLOSED occurrence \""
          ],
          [
            322,
            "                                                    + id"
          ],
          [
            323,
            "                                                    + \" has CANCELLED attendance mark\");"
          ],
          [
            324,
            "                        }"
          ],
          [
            325,
            "                    }"
          ],
          [
            326,
            "                }"
          ],
          [
            327,
            "                case CANCELLED, TRANSFERRED_OUT -> {"
          ],
          [
            328,
            "                    // Historical excluded rows may retain an invalidated mark."
          ],
          [
            329,
            "                    // Lifecycle decides exclusion, but FREE_ATTENDANCE remains"
          ],
          [
            330,
            "                    // an explicit unsupported-data failure."
          ],
          [
            331,
            "                    rejectUnsupportedStatusOnly(id, status);"
          ],
          [
            336,
            "        int presentOrExcusedCount ="
          ],
          [
            337,
            "                addCounts(presentCount, excusedCount, \"present + excused count\");"
          ],
          [
            338,
            "        return new Metrics("
          ],
          [
            339,
            "                plannedCount,"
          ],
          [
            340,
            "                heldCount,"
          ],
          [
            341,
            "                presentCount,"
          ],
          [
            342,
            "                presentOrExcusedCount,"
          ]
        ]
      },
      {
        "title": "Контракт личной статистики",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java",
        "language": "java",
        "lines": [
          [
            350,
            "    public record StudentAttendanceMetricValue("
          ],
          [
            351,
            "            int count, @Schema(nullable = true) Double percent) {}"
          ],
          [
            363,
            "    public record StudentAttendanceMetricSet("
          ],
          [
            364,
            "            StudentAttendanceMetricValue present,"
          ],
          [
            365,
            "            StudentAttendanceMetricValue presentOrExcused,"
          ],
          [
            366,
            "            StudentAttendanceMetricValue excused,"
          ],
          [
            367,
            "            StudentAttendanceMetricValue absent,"
          ],
          [
            368,
            "            int held,"
          ],
          [
            369,
            "            int planned) {}"
          ],
          [
            583,
            "    public record StudentStatisticsResponse("
          ],
          [
            584,
            "            StudentAttendanceMetricSet metrics,"
          ],
          [
            585,
            "            StudentAttendanceOwnRank ownRank,"
          ],
          [
            586,
            "            List<StudentAttendanceSeriesPoint> semesterSeries,"
          ],
          [
            587,
            "            List<StudentStatisticsSubject> subjects,"
          ],
          [
            588,
            "            @JsonProperty(\"_links\") Map<String, Link> links) {}"
          ]
        ]
      },
      {
        "title": "Личные метрики первого студента · фрагмент ответа",
        "language": "json",
        "example": [
          "{",
          "  \"metrics\": {",
          "    \"present\": {",
          "      \"count\": 1,",
          "      \"percent\": 33.33",
          "    },",
          "    \"presentOrExcused\": {",
          "      \"count\": 2,",
          "      \"percent\": 66.67",
          "    },",
          "    \"excused\": {",
          "      \"count\": 1,",
          "      \"percent\": 33.33",
          "    },",
          "    \"absent\": {",
          "      \"count\": 1,",
          "      \"percent\": 33.33",
          "    },",
          "    \"held\": 3,",
          "    \"planned\": 3",
          "  }",
          "}"
        ]
      }
    ]
  },
  {
    "id": "attendance-statistics-student-filters",
    "title": "Студент · предмет и график",
    "description": "В деталях предмета можно выбрать типы пар и шаг графика по дням или неделям. Выбор типов меняет показатели предмета. Дни и недели меняют группировку точек графика. Сводка семестра и рейтинг запрашиваются отдельно. Рейтинг доступен при разрешённой видимости и наличии группы для сравнения. Личный расчёт поддерживает PRESENT, EXCUSED и ABSENT. Хранимый FREE_ATTENDANCE считается неподдерживаемым состоянием.",
    "frames": [
      {
        "title": "Обзор и предмет используют разные запросы",
        "path": "frontends/mobile-core/src/api/student-client.ts",
        "language": "typescript",
        "lines": [
          [
            157,
            "  getStatistics("
          ],
          [
            158,
            "    semesterId: string,"
          ],
          [
            159,
            "    signal?: AbortSignal,"
          ],
          [
            160,
            "  ): Promise<StudentStatisticsOverviewResponse> {"
          ],
          [
            161,
            "    const params = new URLSearchParams({ semesterId })"
          ],
          [
            162,
            "    return this.request("
          ],
          [
            163,
            "      `/api/v1/student/statistics?${params.toString()}`,"
          ],
          [
            164,
            "      signal ? { signal } : undefined,"
          ],
          [
            165,
            "    )"
          ],
          [
            166,
            "  }"
          ],
          [
            167,
            ""
          ],
          [
            168,
            "  getStatisticsSubject("
          ],
          [
            169,
            "    subjectId: string,"
          ],
          [
            170,
            "    semesterId: string,"
          ],
          [
            171,
            "    range: 'days' | 'weeks',"
          ],
          [
            172,
            "    types: readonly string[] = [],"
          ],
          [
            173,
            "    signal?: AbortSignal,"
          ],
          [
            174,
            "  ): Promise<StudentStatisticsSubjectDetailResponse> {"
          ],
          [
            175,
            "    const params = new URLSearchParams({ semesterId, range })"
          ],
          [
            176,
            "    for (const type of types) params.append('types', type)"
          ],
          [
            177,
            "    return this.request("
          ],
          [
            178,
            "      `/api/v1/student/statistics/subjects/${encodeURIComponent(subjectId)}?${params.toString()}`,"
          ],
          [
            179,
            "      signal ? { signal } : undefined,"
          ],
          [
            180,
            "    )"
          ]
        ]
      },
      {
        "title": "Показатели выбранного предмета",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java",
        "language": "java",
        "lines": [
          [
            228,
            "    public StudentStatisticsSubjectDetailResponse statisticsSubject("
          ],
          [
            229,
            "            long requestedSemesterId, long subjectId, String range, List<String> types) {"
          ],
          [
            230,
            "        SemesterResponse active = requireActiveSemester(requestedSemesterId);"
          ],
          [
            231,
            "        StudentAttendanceProjectionResponse response ="
          ],
          [
            232,
            "                attendance.projection("
          ],
          [
            233,
            "                        requestedSemesterId, subjectId, range, types == null ? List.of() : types);"
          ],
          [
            234,
            "        ru.rutcampustrack.attendance.grpc.StudentAttendanceSubject subject ="
          ],
          [
            235,
            "                response.getSubjectsList().stream()"
          ],
          [
            236,
            "                        .filter(value -> value.getSubjectId() == subjectId)"
          ],
          [
            237,
            "                        .findFirst()"
          ],
          [
            244,
            "        return new StudentStatisticsSubjectDetailResponse("
          ],
          [
            245,
            "                Long.toString(subject.getSubjectId()),"
          ],
          [
            246,
            "                subject.getName(),"
          ],
          [
            247,
            "                subject.getAvailableTypesList().stream()"
          ],
          [
            248,
            "                        .map(StudentQueryService::lessonType)"
          ],
          [
            249,
            "                        .toList(),"
          ],
          [
            250,
            "                subject.getSelectedTypesList().stream()"
          ],
          [
            251,
            "                        .map(StudentQueryService::lessonType)"
          ],
          [
            252,
            "                        .toList(),"
          ],
          [
            253,
            "                metricSet(subject.getSelectedAggregate()),"
          ],
          [
            254,
            "                subject.getSeriesList().stream().map(StudentQueryService::seriesPoint).toList(),"
          ],
          [
            255,
            "                subject.getTypeCardsList().stream().map(StudentQueryService::typeCard).toList());"
          ]
        ]
      },
      {
        "title": "Типы ограничивают срез для графика",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            130,
            "        List<Lesson> selectedLessons ="
          ],
          [
            131,
            "                materializedAll.stream()"
          ],
          [
            132,
            "                        .filter(lesson -> subjectId == null || lesson.subjectId() == subjectId)"
          ],
          [
            133,
            "                        .filter("
          ],
          [
            134,
            "                                lesson ->"
          ],
          [
            135,
            "                                        normalizedTypes.isEmpty()"
          ],
          [
            136,
            "                                                || normalizedTypes.contains(lesson.lessonType()))"
          ],
          [
            137,
            "                        .toList();"
          ],
          [
            138,
            "        MetricsBundle overall = metrics(selectedLessons);"
          ],
          [
            139,
            "        List<Day> days = days(selectedLessons, serverDate);"
          ],
          [
            140,
            "        List<Lesson> groupLessons = currentGroupLessons(scope, claims, semesterId);"
          ],
          [
            141,
            "        Graph graph ="
          ],
          [
            142,
            "                new Graph("
          ],
          [
            143,
            "                        series(selectedLessons, BucketUnit.DAY, serverDate),"
          ],
          [
            144,
            "                        groupSeries("
          ],
          [
            145,
            "                                series(selectedLessons, BucketUnit.WEEK, serverDate),"
          ],
          [
            146,
            "                                scope,"
          ],
          [
            147,
            "                                groupLessons,"
          ],
          [
            148,
            "                                semesterId,"
          ],
          [
            149,
            "                                subjectId,"
          ],
          [
            150,
            "                                normalizedTypes,"
          ],
          [
            151,
            "                                serverDate));"
          ],
          [
            1160,
            "    private static String normalizeRange(String range) {"
          ],
          [
            1161,
            "        String normalized ="
          ],
          [
            1162,
            "                range == null || range.isBlank() ? \"weeks\" : range.trim().toLowerCase(Locale.ROOT);"
          ],
          [
            1163,
            "        if (!normalized.equals(\"days\") && !normalized.equals(\"weeks\")) {"
          ],
          [
            1164,
            "            throw new StudentCheckinException("
          ],
          [
            1165,
            "                    StudentCheckinException.Code.INVALID_REQUEST,"
          ],
          [
            1166,
            "                    \"range должен быть days или weeks\");"
          ],
          [
            1167,
            "        }"
          ],
          [
            1168,
            "        return normalized;"
          ]
        ]
      },
      {
        "title": "Условия видимости рейтинга",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            956,
            "    private static boolean rankingVisible(StudentProjectionScopeResponse scope) {"
          ],
          [
            957,
            "        return scope.getRankVisibility()"
          ],
          [
            958,
            "                        == StudentProjectionRankVisibility"
          ],
          [
            959,
            "                                .STUDENT_PROJECTION_RANK_VISIBILITY_VISIBLE"
          ],
          [
            960,
            "                && scope.getRankEligible()"
          ],
          [
            961,
            "                && scope.hasRankGroupId();"
          ],
          [
            962,
            "    }"
          ]
        ]
      },
      {
        "title": "Проекция проверяет допустимый статус",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            1082,
            "    private static AttendanceStatus canonicalStatus(AttendanceStatus status, long lessonId) {"
          ],
          [
            1083,
            "        if (status == null) {"
          ],
          [
            1084,
            "            throw StudentProjectionException.invalidOccurrence("
          ],
          [
            1085,
            "                    \"attendance record \" + lessonId + \" has no persisted status\");"
          ],
          [
            1086,
            "        }"
          ],
          [
            1087,
            "        if (status == AttendanceStatus.FREE_ATTENDANCE) {"
          ],
          [
            1088,
            "            throw StudentProjectionException.unsupportedAttendanceStatus(lessonId, status);"
          ],
          [
            1089,
            "        }"
          ],
          [
            1090,
            "        if (status == AttendanceStatus.CANCELLED) {"
          ],
          [
            1091,
            "            throw StudentProjectionException.invalidOccurrence("
          ],
          [
            1092,
            "                    \"attendance record \" + lessonId + \" has CANCELLED status\");"
          ],
          [
            1093,
            "        }"
          ],
          [
            1094,
            "        return status;"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-statistics-headman",
    "title": "Староста · сводка группы",
    "description": "Attendance берёт группу из сессии и проверяет VIEW_STATS. Это же право позволяет помощнику читать статистику. Запрос выбирает доступный семестр, предмет и типы пар. Фильтры таблицы ограничивают список студентов по имени или показателям. Сводка складывает счётчики всех отобранных строк до пагинации, поэтому смена страницы сохраняет знаменатель.",
    "frames": [
      {
        "title": "Группа из контекста и право VIEW_STATS",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            2156,
            "    private long ensureHeadman() {"
          ],
          [
            2157,
            "        Long groupId = requestContext.getGroupId();"
          ],
          [
            2158,
            "        if (groupId == null || groupId <= 0) {"
          ],
          [
            2159,
            "            throw new AccessDeniedException(\"Headman group is not available in request context\");"
          ],
          [
            2160,
            "        }"
          ],
          [
            2161,
            "        if (!academicGrpcClient.hasAssistantPermission(groupId, \"VIEW_STATS\")) {"
          ],
          [
            2162,
            "            throw new AccessDeniedException(\"Отсутствует право VIEW_STATS\");"
          ],
          [
            2163,
            "        }"
          ],
          [
            2164,
            "        return groupId;"
          ],
          [
            2165,
            "    }"
          ]
        ]
      },
      {
        "title": "Семестр проверяется по контексту Academic",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            564,
            "    private HeadmanStatsScopeResponse requireScope(long groupId, Long semesterId, Long studentId) {"
          ],
          [
            565,
            "        if (semesterId != null && semesterId <= 0)"
          ],
          [
            566,
            "            throw new BadRequestException(\"Укажи корректный ID семестра\");"
          ],
          [
            567,
            "        var scope ="
          ],
          [
            568,
            "                studentId == null"
          ],
          [
            569,
            "                        ? academicGrpcClient.resolveHeadmanStatsScope(groupId, semesterId)"
          ],
          [
            570,
            "                        : academicGrpcClient.resolveHeadmanStatsScope("
          ],
          [
            571,
            "                                groupId, semesterId, studentId);"
          ],
          [
            572,
            "        if (scope == null"
          ],
          [
            573,
            "                || scope.getGroupId() != groupId"
          ],
          [
            574,
            "                || scope.getMembershipActive() < 0"
          ],
          [
            575,
            "                || scope.getMembershipTotal() < scope.getMembershipActive()"
          ],
          [
            576,
            "                || semesterId != null"
          ],
          [
            577,
            "                        && (!scope.hasSelectedSemester()"
          ],
          [
            578,
            "                                || scope.getSelectedSemester().getId() != semesterId)"
          ],
          [
            579,
            "                || semesterId == null"
          ],
          [
            580,
            "                        && scope.hasActiveSemesterId()"
          ],
          [
            581,
            "                        && (!scope.hasSelectedSemester()"
          ],
          [
            582,
            "                                || scope.getSelectedSemester().getId()"
          ],
          [
            583,
            "                                        != scope.getActiveSemesterId())) {"
          ],
          [
            584,
            "            throw new AcademicServiceUnavailableException("
          ],
          [
            585,
            "                    \"Academic returned mismatched statistics authority\");"
          ],
          [
            586,
            "        }"
          ]
        ]
      },
      {
        "title": "Запрос и размер страницы",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            590,
            "    public HeadmanStatsResponse query(HeadmanStatsQueryRequest request) {"
          ],
          [
            591,
            "        long groupId = ensureHeadman();"
          ],
          [
            592,
            "        int page = requirePage(request);"
          ],
          [
            593,
            "        int size = requirePageSize(request);"
          ],
          [
            594,
            "        List<HeadmanStatsSort> sorts = validateSorts(request.sorts());"
          ],
          [
            595,
            "        List<HeadmanStatsFilter> filters = validateFilters(request.filters());"
          ],
          [
            596,
            "        List<String> requestedTypes = validateTypes(request.lessonTypes(), request.subjectId());"
          ],
          [
            597,
            "        StatsData data ="
          ],
          [
            598,
            "                calculate("
          ],
          [
            599,
            "                        groupId,"
          ],
          [
            600,
            "                        request.semesterId(),"
          ],
          [
            601,
            "                        request.subjectId(),"
          ],
          [
            602,
            "                        requestedTypes,"
          ],
          [
            603,
            "                        sorts,"
          ],
          [
            604,
            "                        filters);"
          ],
          [
            605,
            "        SummaryTickets summaryTickets = summaryTickets(data.rows());"
          ],
          [
            606,
            "        int total = data.rows().size();"
          ],
          [
            607,
            "        int from = (int) Math.min((long) page * size, total);"
          ],
          [
            608,
            "        int to = Math.min(from + size, total);"
          ],
          [
            609,
            "        List<StudentRow> pageRows = data.rows().subList(from, to);"
          ],
          [
            610,
            "        boolean previous = page > 0 && total > 0;"
          ],
          [
            611,
            "        boolean next = (long) (page + 1) * size < total;"
          ],
          [
            612,
            "        return new HeadmanStatsResponse("
          ],
          [
            613,
            "                data.context(),"
          ],
          [
            614,
            "                data.summary(),"
          ],
          [
            615,
            "                summaryTickets,"
          ],
          [
            616,
            "                total,"
          ],
          [
            617,
            "                pageRows,"
          ]
        ]
      },
      {
        "title": "Сводка суммирует все строки после фильтра",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            1265,
            "        List<StudentRow> visibleRows ="
          ],
          [
            1266,
            "                students.values().stream()"
          ],
          [
            1267,
            "                        .map(StudentAccumulator::toRow)"
          ],
          [
            1268,
            "                        .filter(row -> matchesFilters(row, filters))"
          ],
          [
            1269,
            "                        .sorted((left, right) -> compareRows(left, right, requestedSorts))"
          ],
          [
            1270,
            "                        .toList();"
          ],
          [
            1271,
            "        ReportService.StatsCounter summaryCounter = new ReportService.StatsCounter();"
          ],
          [
            1272,
            "        visibleRows.forEach("
          ],
          [
            1273,
            "                row -> {"
          ],
          [
            1274,
            "                    StudentAccumulator student = students.get(row.studentId());"
          ],
          [
            1275,
            "                    if (student != null) summaryCounter.merge(student.counter);"
          ],
          [
            1276,
            "                });"
          ],
          [
            1277,
            "        Metrics summary = metrics(summaryCounter);"
          ],
          [
            1278,
            "        EmptyState emptyState = visibleRows.isEmpty() ? EmptyState.FILTERED_EMPTY : EmptyState.NONE;"
          ],
          [
            1279,
            "        if (visibleRows.isEmpty() && students.isEmpty()) emptyState = EmptyState.NO_MEMBERS;"
          ]
        ]
      },
      {
        "title": "Поле фильтра и границы запроса",
        "path": "services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/HeadmanStatsQueryRequest.java",
        "language": "java",
        "lines": [
          [
            6,
            "public record HeadmanStatsQueryRequest("
          ],
          [
            7,
            "        Long subjectId,"
          ],
          [
            8,
            "        List<String> lessonTypes,"
          ],
          [
            9,
            "        Integer page,"
          ],
          [
            10,
            "        Integer size,"
          ],
          [
            11,
            "        List<HeadmanStatsSort> sorts,"
          ],
          [
            12,
            "        List<HeadmanStatsFilter> filters,"
          ],
          [
            13,
            "        @jakarta.validation.constraints.Positive Long semesterId) {"
          ]
        ]
      },
      {
        "title": "Клиент отправляет фильтры в Attendance",
        "path": "frontends/mobile-core/src/features/headman-stats/headman-stats-client.ts",
        "language": "typescript",
        "lines": [
          [
            436,
            "  async query(query: HeadmanStatsQuery, signal?: AbortSignal): Promise<HeadmanStatsResponse> {"
          ],
          [
            437,
            "    validateQuery(query)"
          ],
          [
            438,
            "    const value = await this.request<unknown>('/api/attendance/reports/headman/stats/query', {"
          ],
          [
            439,
            "      method: 'POST',"
          ],
          [
            440,
            "      body: JSON.stringify(query),"
          ],
          [
            441,
            "      ...(signal ? { signal } : {}),"
          ],
          [
            442,
            "    })"
          ],
          [
            443,
            "    return normalizeResponse(value)"
          ],
          [
            444,
            "  }"
          ]
        ]
      },
      {
        "title": "POST · выбор семестра, предмета и фильтра",
        "language": "json",
        "example": [
          "{",
          "  \"semesterId\": 7,",
          "  \"subjectId\": 19,",
          "  \"lessonTypes\": [",
          "    \"practice\"",
          "  ],",
          "  \"filters\": [",
          "    {",
          "      \"field\": \"presentPercent\",",
          "      \"minimum\": 25",
          "    }",
          "  ],",
          "  \"page\": 0,",
          "  \"size\": 20",
          "}"
        ]
      }
    ]
  },
  {
    "id": "attendance-statistics-headman-time",
    "title": "Староста · сегодня и динамика",
    "description": "В семестровую сводку попадает CLOSED после фактического начала. Виджет «Сегодня» учитывает и уже начавшиеся незакрытые пары. По каждой паре берётся исторический состав. Пока отметки нет, ячейка считается ABSENT, поэтому доля «Сегодня» может меняться во время пары. Отдельный запрос динамики раскладывает результат по дням или неделям в пределах семестра.",
    "frames": [
      {
        "title": "Отмена и перенос исключены из расчёта",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            1995,
            "            boolean excluded ="
          ],
          [
            1996,
            "                    rawStatus.equals(\"cancelled\")"
          ],
          [
            1997,
            "                            || rawStatus.equals(\"transferred\")"
          ],
          [
            1998,
            "                            || rawStatus.equals(\"deleted\");"
          ],
          [
            1999,
            "            all.add("
          ],
          [
            2000,
            "                    new StatsLesson("
          ],
          [
            2001,
            "                            lesson,"
          ],
          [
            2002,
            "                            date,"
          ],
          [
            2003,
            "                            normalize(lesson.getLessonType()),"
          ],
          [
            2004,
            "                            !excluded,"
          ],
          [
            2005,
            "                            rawStatus.equals(\"closed\")"
          ],
          [
            2006,
            "                                    && JournalLessonPolicy.requireTiming(lesson).hasStarted(now)));"
          ]
        ]
      },
      {
        "title": "Сводка выбирает завершённые пары и типы",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            1127,
            "        List<StatsLesson> selectedLessons ="
          ],
          [
            1128,
            "                semesterLessons.stream()"
          ],
          [
            1129,
            "                        .filter(StatsLesson::completed)"
          ],
          [
            1130,
            "                        .filter("
          ],
          [
            1131,
            "                                lesson ->"
          ],
          [
            1132,
            "                                        subjectId == null"
          ],
          [
            1133,
            "                                                || lesson.lesson().getSubjectId() == subjectId)"
          ],
          [
            1134,
            "                        .filter("
          ],
          [
            1135,
            "                                lesson ->"
          ],
          [
            1136,
            "                                        requestedTypes.isEmpty()"
          ],
          [
            1137,
            "                                                || requestedTypes.contains("
          ],
          [
            1138,
            "                                                        normalize(lesson.lessonType())))"
          ],
          [
            1139,
            "                        .toList();"
          ],
          [
            1205,
            "                                    lesson.date(),"
          ],
          [
            1206,
            "                                    date -> {"
          ],
          [
            1207,
            "                                        GroupMembersResponse loaded ="
          ],
          [
            1208,
            "                                                academicGrpcClient.getGroupMembers("
          ],
          [
            1209,
            "                                                        groupId, date, semester.getId());"
          ],
          [
            1210,
            "                                        validateHistoricalRoster(loaded, date, semester.getId());"
          ],
          [
            1211,
            "                                        return loaded;"
          ],
          [
            1212,
            "                                    });"
          ],
          [
            1213,
            "            Set<Long> lessonMembers = new LinkedHashSet<>();"
          ],
          [
            1214,
            "            for (StudentInfo member : roster.getStudentsList()) {"
          ],
          [
            1215,
            "                long studentId = member.getUserId();"
          ],
          [
            1216,
            "                lessonMembers.add(studentId);"
          ],
          [
            1225,
            "                AttendanceRecord record ="
          ],
          [
            1226,
            "                        attendanceByCell.get(new AttendanceKey(lesson.lesson().getId(), studentId));"
          ],
          [
            1227,
            "                AttendanceStatus status ="
          ],
          [
            1228,
            "                        record == null || record.status() == null"
          ],
          [
            1229,
            "                                ? AttendanceStatus.ABSENT"
          ],
          [
            1230,
            "                                : record.status();"
          ],
          [
            1231,
            "                accumulator.counter.add(status);"
          ],
          [
            1232,
            "                if (status == AttendanceStatus.PRESENT && record != null) {"
          ],
          [
            1233,
            "                    accumulator.sources.addDirect(record.source());"
          ],
          [
            1234,
            "                }"
          ]
        ]
      },
      {
        "title": "Сегодня учитывает начавшиеся пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            296,
            "        long groupId = ensureHeadman();"
          ],
          [
            297,
            "        Instant now = clock.instant();"
          ],
          [
            298,
            "        LocalDate today = now.atZone(JournalLessonPolicy.MOSCOW).toLocalDate();"
          ],
          [
            299,
            "        var scope = requireScope(groupId, null);"
          ],
          [
            300,
            "        ReportService.StatsCounter counter = new ReportService.StatsCounter();"
          ],
          [
            301,
            "        List<AttendanceRecord> records ="
          ],
          [
            302,
            "                attendanceReadPort.findByGroupAndDateRange(groupId, today, today);"
          ],
          [
            303,
            "        for (var semester : scope.getSemestersList()) {"
          ],
          [
            304,
            "            if (today.isBefore(parseSemesterDate(semester.getDateFrom(), \"start\"))"
          ],
          [
            305,
            "                    || today.isAfter(parseSemesterDate(semester.getDateTo(), \"end\"))) continue;"
          ],
          [
            315,
            "            var attendance = validateAttendanceRecords(records, groupId, lessonsById(lessons));"
          ],
          [
            316,
            "            var roster = academicGrpcClient.getGroupMembers(groupId, today, semester.getId());"
          ],
          [
            317,
            "            validateHistoricalRoster(roster, today, semester.getId());"
          ],
          [
            318,
            "            for (StatsLesson lesson : lessons) {"
          ],
          [
            319,
            "                if (!lesson.inOptions()"
          ],
          [
            320,
            "                        || !JournalLessonPolicy.requireTiming(lesson.lesson()).hasStarted(now))"
          ],
          [
            321,
            "                    continue;"
          ],
          [
            322,
            "                for (long studentId : validateRoster(roster)) {"
          ],
          [
            323,
            "                    var record ="
          ],
          [
            324,
            "                            attendance.get(new AttendanceKey(lesson.lesson().getId(), studentId));"
          ],
          [
            325,
            "                    counter.add("
          ],
          [
            326,
            "                            record == null || record.status() == null"
          ],
          [
            327,
            "                                    ? AttendanceStatus.ABSENT"
          ],
          [
            328,
            "                                    : record.status());"
          ],
          [
            329,
            "                }"
          ],
          [
            330,
            "            }"
          ]
        ]
      },
      {
        "title": "Границы дней и недель для динамики",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            1377,
            "    private static List<TrendBucket> trendBuckets("
          ],
          [
            1378,
            "            Context context,"
          ],
          [
            1379,
            "            HeadmanStatsTrendQueryRequest.Mode mode,"
          ],
          [
            1380,
            "            LocalDate weekStart,"
          ],
          [
            1381,
            "            HeadmanStatsTrendQueryRequest.BucketUnit unit) {"
          ],
          [
            1382,
            "        LocalDate semesterFrom = context.semesterFrom();"
          ],
          [
            1383,
            "        LocalDate semesterTo = context.semesterTo();"
          ],
          [
            1384,
            "        if (semesterFrom == null || semesterTo == null) return List.of();"
          ],
          [
            1385,
            "        if (mode == HeadmanStatsTrendQueryRequest.Mode.WEEK"
          ],
          [
            1386,
            "                || unit == HeadmanStatsTrendQueryRequest.BucketUnit.DAY) {"
          ],
          [
            1387,
            "            LocalDate from ="
          ],
          [
            1388,
            "                    mode != HeadmanStatsTrendQueryRequest.Mode.WEEK"
          ],
          [
            1389,
            "                                    || weekStart.isBefore(semesterFrom)"
          ],
          [
            1390,
            "                            ? semesterFrom"
          ],
          [
            1391,
            "                            : weekStart;"
          ],
          [
            1392,
            "            LocalDate requestedTo ="
          ],
          [
            1393,
            "                    mode == HeadmanStatsTrendQueryRequest.Mode.WEEK"
          ],
          [
            1394,
            "                            ? weekStart.plusDays(6)"
          ],
          [
            1395,
            "                            : semesterTo;"
          ],
          [
            1396,
            "            LocalDate to = requestedTo.isAfter(semesterTo) ? semesterTo : requestedTo;"
          ],
          [
            1397,
            "            if (unit == HeadmanStatsTrendQueryRequest.BucketUnit.WEEK) {"
          ],
          [
            1398,
            "                return weekBuckets(from, to);"
          ],
          [
            1399,
            "            }"
          ],
          [
            1400,
            "            List<TrendBucket> days = new ArrayList<>();"
          ],
          [
            1401,
            "            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {"
          ],
          [
            1402,
            "                days.add(new TrendBucket(date, date, date, date.toString()));"
          ],
          [
            1403,
            "            }"
          ],
          [
            1404,
            "            return List.copyOf(days);"
          ],
          [
            1405,
            "        }"
          ],
          [
            1406,
            "        return weekBuckets(semesterFrom, semesterTo);"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-statistics-teacher",
    "title": "Преподаватель · группа и предмет",
    "description": "Преподаватель выбирает семестр, вид отчёта, группу и предмет либо запрашивает доступные группы. Mobile BFF проверяет действующие назначения для пары «группа и предмет» и передаёт запрос в Attendance по gRPC. Attendance проверяет область доступа и оставляет закрытые пары нужных типов. Для группового отчёта знаменатель состоит из ячеек посещаемости всех студентов исторического состава.",
    "frames": [
      {
        "title": "Параметры отчёта преподавателя",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/TeacherApi.java",
        "language": "java",
        "lines": [
          [
            114,
            "    @Operation(summary = \"Teacher server-side attendance statistics\")"
          ],
          [
            115,
            "    @GetMapping(\"/stats\")"
          ],
          [
            116,
            "    ResponseEntity<StatsResponse> stats("
          ],
          [
            117,
            "            @RequestParam String semesterId,"
          ],
          [
            118,
            "            @RequestParam String scope,"
          ],
          [
            119,
            "            @RequestParam(required = false) String studentId,"
          ],
          [
            120,
            "            @RequestParam(required = false) String groupId,"
          ],
          [
            121,
            "            @RequestParam(required = false) String subjectId,"
          ],
          [
            122,
            "            @RequestParam(required = false, name = \"lessonType\") List<String> lessonTypes,"
          ],
          [
            123,
            "            @RequestParam(required = false, name = \"sort\") List<String> sorts,"
          ],
          [
            124,
            "            @RequestParam(required = false, name = \"filter\") List<String> filters);"
          ]
        ]
      },
      {
        "title": "Допуск к группе и предмету",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            786,
            "        TeacherStatsScope scope = parseStatsScope(scopeValue);"
          ],
          [
            787,
            "        boolean allGroups ="
          ],
          [
            788,
            "                scope == TeacherStatsScope.TEACHER_STATS_GROUPS"
          ],
          [
            789,
            "                        && selectedGroupId == null"
          ],
          [
            790,
            "                        && selectedSubjectId == null;"
          ],
          [
            791,
            "        if (studentId != null"
          ],
          [
            792,
            "                && (scope != TeacherStatsScope.TEACHER_STATS_STUDENTS"
          ],
          [
            793,
            "                        || studentId <= 0"
          ],
          [
            794,
            "                        || selectedGroupId != null && selectedGroupId <= 0"
          ],
          [
            795,
            "                        || selectedSubjectId != null"
          ],
          [
            796,
            "                                && (selectedGroupId == null || selectedSubjectId <= 0))) {"
          ],
          [
            797,
            "            throw invalidStats(\"Некорректный выбор студента, группы или предмета\");"
          ],
          [
            798,
            "        }"
          ],
          [
            799,
            "        if (studentId == null && !allGroups"
          ],
          [
            800,
            "                && (selectedGroupId == null"
          ],
          [
            801,
            "                        || selectedGroupId <= 0"
          ],
          [
            802,
            "                        || selectedSubjectId == null"
          ],
          [
            803,
            "                        || selectedSubjectId <= 0)) {"
          ],
          [
            804,
            "            throw invalidStats(\"Для выбранной статистики нужны группа и предмет\");"
          ],
          [
            805,
            "        }"
          ],
          [
            813,
            "        List<String> selectedTypes ="
          ],
          [
            814,
            "                requestedTypes.stream()"
          ],
          [
            815,
            "                        .map(value -> value.trim().toLowerCase(Locale.ROOT))"
          ],
          [
            816,
            "                        .distinct()"
          ],
          [
            817,
            "                        .toList();"
          ],
          [
            818,
            "        if (selectedTypes.stream()"
          ],
          [
            819,
            "                .anyMatch(type -> !Set.of(\"lecture\", \"practice\", \"lab\").contains(type))) {"
          ],
          [
            820,
            "            throw invalidStats(\"Неизвестный тип занятия\");"
          ],
          [
            821,
            "        }"
          ],
          [
            822,
            "        TeacherSubjectsResponse authority = academic.currentTeacherSubjects(claims.userId());"
          ],
          [
            823,
            "        Set<TeacherSubjectKey> allowedSubjects = currentTeacherSubjectKeys(authority);"
          ],
          [
            824,
            "        Set<Long> activeGroupIds = currentTeacherGroups(authority).keySet();"
          ],
          [
            825,
            "        if (activeGroupIds.isEmpty()"
          ],
          [
            826,
            "                || selectedGroupId != null && !activeGroupIds.contains(selectedGroupId)"
          ],
          [
            827,
            "                || selectedSubjectId != null"
          ],
          [
            828,
            "                        && !allowedSubjects.contains("
          ],
          [
            829,
            "                                new TeacherSubjectKey(selectedGroupId, selectedSubjectId))) {"
          ],
          [
            830,
            "            throw new MobileBffException("
          ],
          [
            831,
            "                    HttpStatus.NOT_FOUND,"
          ],
          [
            832,
            "                    ProblemCode.OUT_OF_SCOPE,"
          ],
          [
            833,
            "                    \"Статистика недоступна в текущем scope\");"
          ],
          [
            834,
            "        }"
          ]
        ]
      },
      {
        "title": "Attendance читает запрос от преподавателя",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            406,
            "    public void getTeacherStats("
          ],
          [
            407,
            "            TeacherStatsRequest request, StreamObserver<TeacherStatsResponse> responseObserver) {"
          ],
          [
            408,
            "        try {"
          ],
          [
            409,
            "            InternalJwtClaims claims = requireTeacher();"
          ],
          [
            410,
            "            TeacherStatsScope scope = request.getScope();"
          ],
          [
            411,
            "            ReportService.TeacherStatsQuery query = statsQuery(request);"
          ],
          [
            412,
            "            ReportService.TeacherStatsResult result ="
          ],
          [
            413,
            "                    reportService.getTeacherStats(query, claims.userId());"
          ],
          [
            414,
            "            TeacherStatsResponse.Builder response ="
          ],
          [
            415,
            "                    TeacherStatsResponse.newBuilder()"
          ],
          [
            416,
            "                            .setScope(scope)"
          ],
          [
            417,
            "                            .setSemesterId(result.semesterId())"
          ],
          [
            418,
            "                            .setLessonsCount(result.lessonsCount())"
          ],
          [
            419,
            "                            .setServerNow(result.serverNow().toString());"
          ],
          [
            420,
            "            if (result.periodFrom() != null) response.setPeriodFrom(result.periodFrom().toString());"
          ],
          [
            421,
            "            if (result.periodTo() != null) response.setPeriodTo(result.periodTo().toString());"
          ],
          [
            422,
            "            result.students().stream().map(this::toStudentStats).forEach(response::addStudents);"
          ],
          [
            423,
            "            result.groups().stream().map(this::toGroupStats).forEach(response::addGroups);"
          ]
        ]
      },
      {
        "title": "Отбор типов, предмета и закрытых пар",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1020,
            "            authorizeTeacherStatsLesson(lesson, teacherAuthority);"
          ],
          [
            1021,
            "            LocalDate date = parseLessonDate(lesson.getDate());"
          ],
          [
            1022,
            "            if (!allGroups && lesson.getGroupId() != query.groupId()) {"
          ],
          [
            1023,
            "                throw new AccessDeniedException("
          ],
          [
            1024,
            "                        \"Teacher stats lesson is outside the selected context\");"
          ],
          [
            1025,
            "            }"
          ],
          [
            1026,
            "            // Selected queries may carry other currently authorized subjects for"
          ],
          [
            1027,
            "            // options; only the selection contributes. Unscoped GROUPS counts all"
          ],
          [
            1028,
            "            // current group+subject pairs, including historical replacement lessons."
          ],
          [
            1029,
            "            if (!allGroups && lesson.getSubjectId() != query.subjectId()) {"
          ],
          [
            1030,
            "                continue;"
          ],
          [
            1031,
            "            }"
          ],
          [
            1032,
            "            if (!requestedTypes.isEmpty()"
          ],
          [
            1033,
            "                    && !requestedTypes.contains(normalize(lesson.getLessonType()))) {"
          ],
          [
            1034,
            "                continue;"
          ],
          [
            1035,
            "            }"
          ],
          [
            1036,
            "            if (!isCompleted(lesson) || isCancelled(lesson)) {"
          ],
          [
            1037,
            "                continue;"
          ],
          [
            1038,
            "            }"
          ],
          [
            1039,
            "            lessons.add(lesson);"
          ],
          [
            1654,
            "    private static boolean isCompleted(LessonInfo lesson) {"
          ],
          [
            1655,
            "        // Schedule transitions a lesson to CLOSED only after its concrete"
          ],
          [
            1656,
            "        // Moscow end time (+ the configured grace period). A calendar-date"
          ],
          [
            1657,
            "        // comparison would include today's unfinished lessons."
          ],
          [
            1658,
            "        return lesson != null && \"closed\".equalsIgnoreCase(lesson.getStatus());"
          ],
          [
            1659,
            "    }"
          ],
          [
            1660,
            ""
          ],
          [
            1661,
            "    private static boolean isCancelled(LessonInfo lesson) {"
          ],
          [
            1662,
            "        return \"cancelled\".equalsIgnoreCase(lesson.getStatus());"
          ],
          [
            1663,
            "    }"
          ]
        ]
      },
      {
        "title": "Форма метрик по группе",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/TeacherApiModels.java",
        "language": "java",
        "lines": [
          [
            177,
            "    @Schema(name = \"TeacherStatsMetric\")"
          ],
          [
            178,
            "    public record StatsMetric(int numerator, int denominator, double percent) {}"
          ],
          [
            189,
            "    @Schema(name = \"TeacherStatsGroup\")"
          ],
          [
            190,
            "    public record StatsGroup("
          ],
          [
            191,
            "            String groupId,"
          ],
          [
            192,
            "            String groupName,"
          ],
          [
            193,
            "            int lessonsCount,"
          ],
          [
            194,
            "            StatsMetric present,"
          ],
          [
            195,
            "            StatsMetric presentOrExcused,"
          ],
          [
            196,
            "            StatsMetric excused,"
          ],
          [
            197,
            "            StatsMetric absent) {}"
          ],
          [
            217,
            "    public record StatsResponse("
          ],
          [
            218,
            "            String scope,"
          ],
          [
            219,
            "            String semesterId,"
          ],
          [
            220,
            "            LocalDate periodFrom,"
          ],
          [
            221,
            "            LocalDate periodTo,"
          ],
          [
            222,
            "            int lessonsCount,"
          ],
          [
            223,
            "            List<StatsStudent> students,"
          ],
          [
            224,
            "            List<StatsGroup> groups,"
          ],
          [
            225,
            "            List<StatsSubjectOption> subjectOptions,"
          ]
        ]
      },
      {
        "title": "Метрики группы для преподавателя · фрагмент ответа",
        "language": "json",
        "example": [
          "{",
          "  \"scope\": \"groups\",",
          "  \"semesterId\": \"7\",",
          "  \"lessonsCount\": 3,",
          "  \"groups\": [",
          "    {",
          "      \"groupId\": \"41\",",
          "      \"groupName\": \"ДЕМО-411\",",
          "      \"lessonsCount\": 3,",
          "      \"present\": {",
          "        \"numerator\": 3,",
          "        \"denominator\": 6,",
          "        \"percent\": 50.0",
          "      },",
          "      \"presentOrExcused\": {",
          "        \"numerator\": 5,",
          "        \"denominator\": 6,",
          "        \"percent\": 83.33333333333333",
          "      },",
          "      \"excused\": {",
          "        \"numerator\": 2,",
          "        \"denominator\": 6,",
          "        \"percent\": 33.333333333333336",
          "      },",
          "      \"absent\": {",
          "        \"numerator\": 1,",
          "        \"denominator\": 6,",
          "        \"percent\": 16.666666666666668",
          "      }",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  },
  {
    "id": "attendance-statistics-teacher-student",
    "title": "Преподаватель · один студент",
    "description": "Для выбранного студента сервис проверяет историческое членство на дату каждой пары в разрешённых группах и предметах. В знаменатель входят только его закрытые пары нужных типов. Фильтры по долям применяются к готовым строкам. Порог по проценту исключает строку без знаменателя. При знаменателе 0 экран выводит прочерк.",
    "frames": [
      {
        "title": "Историческое членство проверяется по датам",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1112,
            "        var target = academicGrpcClient.selectedStudentIdentity(studentId);"
          ],
          [
            1113,
            "        // The preliminary roster is only admission for the identity read. Rebuild the projection"
          ],
          [
            1114,
            "        // under the captured generation so a concurrent move cannot mix old and new membership."
          ],
          [
            1115,
            "        roster.clear();"
          ],
          [
            1116,
            "        List<LessonInfo> memberLessons = candidates.stream()"
          ],
          [
            1117,
            "                .filter(lesson -> isDatedMember(lesson, studentId, roster))"
          ],
          [
            1118,
            "                .toList();"
          ],
          [
            1119,
            "        if (memberLessons.isEmpty()) {"
          ],
          [
            1120,
            "            throw new AccessDeniedException(\"Student is outside the dated teacher stats scope\");"
          ],
          [
            1121,
            "        }"
          ],
          [
            1122,
            ""
          ],
          [
            1123,
            "        Set<String> types = normalizedTypes(query.lessonTypes());"
          ],
          [
            1124,
            "        List<LessonInfo> completed = memberLessons.stream()"
          ],
          [
            1125,
            "                .filter(ReportService::isCompleted)"
          ],
          [
            1126,
            "                .filter(lesson -> types.isEmpty() || types.contains(normalize(lesson.getLessonType())))"
          ],
          [
            1127,
            "                .toList();"
          ]
        ]
      },
      {
        "title": "Личные отметки для выбранных пар",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1128,
            "        Set<Long> completedIds = completed.stream().map(LessonInfo::getLessonId)"
          ],
          [
            1129,
            "                .collect(Collectors.toSet());"
          ],
          [
            1130,
            "        Map<Long, AttendanceRecord> marks = completed.isEmpty()"
          ],
          [
            1131,
            "                ? Map.of()"
          ],
          [
            1132,
            "                : attendanceReadPort.findByUserId(studentId, query.semesterId()).stream()"
          ],
          [
            1133,
            "                        .filter(record -> record != null && Objects.equals(record.userId(), studentId)"
          ],
          [
            1134,
            "                                && completedIds.contains(record.lessonId()))"
          ],
          [
            1135,
            "                        .collect(Collectors.toMap(AttendanceRecord::lessonId, value -> value,"
          ],
          [
            1136,
            "                                (first, ignored) -> first));"
          ],
          [
            1137,
            "        var student = new StudentStatsAccumulator(studentId, target.displayName());"
          ],
          [
            1138,
            "        for (var lesson : completed) {"
          ],
          [
            1139,
            "            AttendanceRecord mark = marks.get(lesson.getLessonId());"
          ],
          [
            1140,
            "            student.counter.add(mark == null || mark.status() == null"
          ],
          [
            1141,
            "                    ? AttendanceStatus.ABSENT : mark.status());"
          ],
          [
            1142,
            "        }"
          ]
        ]
      },
      {
        "title": "Процентные границы применяются к результату",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1064,
            "        List<TeacherStudentStats> studentRows ="
          ],
          [
            1065,
            "                students.values().stream()"
          ],
          [
            1066,
            "                        .map(StudentStatsAccumulator::toResult)"
          ],
          [
            1067,
            "                        .filter(row -> matchesFilters(row, query.filters()))"
          ],
          [
            1068,
            "                        .sorted((left, right) -> compareStudents(left, right, query.sorts()))"
          ],
          [
            1069,
            "                        .toList();"
          ],
          [
            1070,
            "        List<TeacherGroupStats> groupRows ="
          ],
          [
            1071,
            "                groups.values().stream()"
          ],
          [
            1072,
            "                        .map(group -> group.toResult(groupNames.getOrDefault(group.groupId, \"\")))"
          ],
          [
            1073,
            "                        .filter(row -> matchesFilters(row, query.filters()))"
          ],
          [
            1074,
            "                        .sorted((left, right) -> compareGroups(left, right, query.sorts()))"
          ],
          [
            1075,
            "                        .toList();"
          ],
          [
            1701,
            "    private static boolean matchesMetric(TeacherMetric metric, TeacherStatsFilter filter) {"
          ],
          [
            1702,
            "        if (metric == null) return true;"
          ],
          [
            1703,
            "        if (metric.denominator() == 0"
          ],
          [
            1704,
            "                && (filter.minPercent() != null || filter.maxPercent() != null)) return false;"
          ],
          [
            1705,
            "        return (filter.minPercent() == null || metric.percent() >= filter.minPercent())"
          ],
          [
            1706,
            "                && (filter.maxPercent() == null || metric.percent() <= filter.maxPercent());"
          ],
          [
            1707,
            "    }"
          ],
          [
            1708,
            ""
          ],
          [
            1709,
            "    private static boolean matchesValue(int value, TeacherStatsFilter filter) {"
          ],
          [
            1710,
            "        if (!filter.column().equals(\"lessonsCount\")) return true;"
          ],
          [
            1711,
            "        return (filter.minValue() == null || value >= filter.minValue())"
          ],
          [
            1712,
            "                && (filter.maxValue() == null || value <= filter.maxValue());"
          ]
        ]
      },
      {
        "title": "Клиент показывает прочерк, если доли нет",
        "path": "frontends/mobile-core/src/features/teacher/TeacherStatsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            890,
            "function formatMetric(metric: TeacherStatsMetric | undefined): string {"
          ],
          [
            891,
            "  if (!metric || metric.denominator === 0) return '—';"
          ],
          [
            892,
            "  return `${new Intl.NumberFormat('ru', { maximumFractionDigits: 1 }).format(metric.percent)} %`;"
          ],
          [
            893,
            "}"
          ],
          [
            894,
            ""
          ],
          [
            895,
            "function metricLabel(label: string, metric: TeacherStatsMetric | undefined): string {"
          ],
          [
            896,
            "  return !metric || metric.denominator === 0"
          ],
          [
            897,
            "    ? `${label}: нет данных`"
          ],
          [
            898,
            "    : `${label}: ${formatMetric(metric)}, ${metric.numerator} из ${metric.denominator}`;"
          ],
          [
            899,
            "}"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-statistics-admin",
    "title": "Администратор · сравнение групп",
    "description": "Отчёт требует DASHBOARD_VIEW. Администратор выбирает семестр и может отобрать группы по названию. Academic передаёт в Attendance список групп и даты из выбранного семестра. Для каждой группы Attendance считает закрытые пары и ячейки исторического состава. Сортировка и пагинация применяются к готовым строкам. Дневные и недельные интервалы относятся к отдельному экрану активности.",
    "frames": [
      {
        "title": "Параметры таблицы групп",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/AdminAttendanceReportApi.java",
        "language": "java",
        "lines": [
          [
            14,
            "@RequestMapping(\"/academic/admin/attendance\")"
          ],
          [
            15,
            "public interface AdminAttendanceReportApi {"
          ],
          [
            16,
            "    @GetMapping(\"/semesters\")"
          ],
          [
            17,
            "    ResponseEntity<Semesters> semesters();"
          ],
          [
            18,
            ""
          ],
          [
            19,
            "    @GetMapping"
          ],
          [
            20,
            "    ResponseEntity<Page> read("
          ],
          [
            21,
            "            @RequestParam String semesterId,"
          ],
          [
            22,
            "            @RequestParam(defaultValue = \"\") String search,"
          ],
          [
            23,
            "            @RequestParam(defaultValue = \"GROUP_NAME\") Sort sort,"
          ],
          [
            24,
            "            @RequestParam(defaultValue = \"ASC\") Direction direction,"
          ],
          [
            25,
            "            @RequestParam(defaultValue = \"0\") int page,"
          ],
          [
            26,
            "            @RequestParam(defaultValue = \"25\") int size);"
          ]
        ]
      },
      {
        "title": "Проверка действующих прав",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/adminattendance/AdminAttendanceReportService.java",
        "language": "java",
        "lines": [
          [
            174,
            "    void requireCurrentAccess() {"
          ],
          [
            175,
            "        access.require(\"DASHBOARD_VIEW\");"
          ],
          [
            176,
            "        try {"
          ],
          [
            177,
            "            identities.checkAdminAccess("
          ],
          [
            178,
            "                    List.of(\"DASHBOARD_VIEW\"),"
          ],
          [
            179,
            "                    false,"
          ],
          [
            180,
            "                    System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3));"
          ],
          [
            181,
            "        } catch (StatusRuntimeException failure) {"
          ],
          [
            182,
            "            switch (failure.getStatus().getCode()) {"
          ],
          [
            183,
            "                case PERMISSION_DENIED, UNAUTHENTICATED -> throw AdminAccessException.denied();"
          ],
          [
            184,
            "                default -> throw unavailable();"
          ],
          [
            185,
            "            }"
          ]
        ]
      },
      {
        "title": "Семестр и поиск по названию группы",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/adminattendance/AdminAttendanceReadRepository.java",
        "language": "java",
        "lines": [
          [
            45,
            "    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)"
          ],
          [
            46,
            "    public Selection select(long semesterId, String search) {"
          ],
          [
            47,
            "        List<Semester> semesters ="
          ],
          [
            48,
            "                jdbc.query("
          ],
          [
            49,
            "                        \"SELECT id,name,date_from,date_to,is_active,is_archived FROM semesters\""
          ],
          [
            50,
            "                                + \" WHERE id=?\","
          ],
          [
            60,
            "        if (semesters.size() != 1)"
          ],
          [
            61,
            "            throw new ResponseStatusException(HttpStatus.NOT_FOUND, \"Семестр не найден\");"
          ],
          [
            62,
            "        String pattern ="
          ],
          [
            63,
            "                \"%\""
          ],
          [
            64,
            "                        + search.toLowerCase(Locale.ROOT)"
          ],
          [
            65,
            "                                .replace(\"!\", \"!!\")"
          ],
          [
            66,
            "                                .replace(\"%\", \"!%\")"
          ],
          [
            67,
            "                                .replace(\"_\", \"!_\")"
          ],
          [
            68,
            "                        + \"%\";"
          ],
          [
            69,
            "        List<Map.Entry<Long, String>> rows ="
          ],
          [
            70,
            "                jdbc.query("
          ],
          [
            71,
            "                        \"SELECT id,name FROM groups WHERE lower(name) LIKE ? ESCAPE '!' ORDER BY id\""
          ],
          [
            72,
            "                                + \" LIMIT 10001\","
          ],
          [
            73,
            "                        (rs, n) -> Map.entry(rs.getLong(\"id\"), rs.getString(\"name\")),"
          ],
          [
            74,
            "                        pattern);"
          ],
          [
            75,
            "        requireSize(rows.size());"
          ]
        ]
      },
      {
        "title": "Группы и границы выбранного семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/adminattendance/AttendanceAdminGroupStatsClient.java",
        "language": "java",
        "lines": [
          [
            77,
            "        var semester = selected.semester();"
          ],
          [
            78,
            "        AdminGroupStatsQuery query ="
          ],
          [
            79,
            "                AdminGroupStatsQuery.newBuilder()"
          ],
          [
            80,
            "                        .setSemesterId(Long.parseLong(semester.id()))"
          ],
          [
            81,
            "                        .setDateFrom(semester.dateFrom().toString())"
          ],
          [
            82,
            "                        .setDateTo(semester.dateTo().toString())"
          ],
          [
            83,
            "                        .addAllGroupIds(selected.groups().keySet().stream().sorted().toList())"
          ],
          [
            84,
            "                        .build();"
          ]
        ]
      },
      {
        "title": "Attendance считает закрытые пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1209,
            "            requireStatsLessonStatus(lesson);"
          ],
          [
            1210,
            "            parseLessonDate(lesson.getDate());"
          ],
          [
            1211,
            "            if (isCompleted(lesson) && !isCancelled(lesson)) completed.add(lesson);"
          ],
          [
            1212,
            "        }"
          ],
          [
            1213,
            "        return aggregateAttendance(completed, seededGroupIds, false).groups().values().stream()"
          ],
          [
            1214,
            "                .map("
          ],
          [
            1215,
            "                        group ->"
          ],
          [
            1216,
            "                                new GroupAttendanceTotals("
          ],
          [
            1217,
            "                                        group.groupId,"
          ],
          [
            1218,
            "                                        group.lessonsCount,"
          ],
          [
            1219,
            "                                        group.counter.presentMetric(),"
          ],
          [
            1220,
            "                                        group.counter.presentOrExcusedMetric(),"
          ],
          [
            1221,
            "                                        group.counter.excusedMetric(),"
          ],
          [
            1222,
            "                                        group.counter.absentMetric()))"
          ],
          [
            1223,
            "                .toList();"
          ]
        ]
      },
      {
        "title": "В ответе отдельно пары и ячейки",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/AdminGroupStatsReadService.java",
        "language": "java",
        "lines": [
          [
            129,
            "        for (var row : reports.aggregateAuthorizedGroups(query.getSemesterId(), groups, lessons)) {"
          ],
          [
            130,
            "            var value ="
          ],
          [
            131,
            "                    AdminGroupAttendanceTotals.newBuilder()"
          ],
          [
            132,
            "                            .setGroupId(row.groupId())"
          ],
          [
            133,
            "                            .setCountedLessons(row.lessonsCount())"
          ],
          [
            134,
            "                            .setPresent(row.present().numerator())"
          ],
          [
            135,
            "                            .setExcused(row.excused().numerator())"
          ],
          [
            136,
            "                            .setAbsent(row.absent().numerator())"
          ],
          [
            137,
            "                            .setOpportunities(row.present().denominator());"
          ],
          [
            138,
            "            if (row.present().denominator() > 0) {"
          ],
          [
            139,
            "                value.setPresentPercent(row.present().percent())"
          ],
          [
            140,
            "                        .setPresentOrExcusedPercent(row.presentOrExcused().percent());"
          ],
          [
            141,
            "            }"
          ],
          [
            142,
            "            result.addGroups(value);"
          ],
          [
            143,
            "        }"
          ]
        ]
      },
      {
        "title": "Отдельный запрос активности по периоду и шагу",
        "path": "frontends/mobile-core/src/features/admin-dashboard/admin-dashboard-client.ts",
        "language": "typescript",
        "lines": [
          [
            59,
            "  getActivity("
          ],
          [
            60,
            "    period: AdminActivityPeriod,"
          ],
          [
            61,
            "    bucket: AdminActivityBucket,"
          ],
          [
            62,
            "    signal?: AbortSignal,"
          ],
          [
            63,
            "  ): Promise<AdminDashboardActivity> {"
          ],
          [
            64,
            "    const query = new URLSearchParams({ period, bucket })"
          ],
          [
            65,
            "    return this.request<unknown>("
          ],
          [
            66,
            "      `/api/academic/dashboard/activity?${query}`,"
          ],
          [
            67,
            "      signal ? { signal } : undefined,"
          ],
          [
            68,
            "    ).then((value) => normalizeAdminDashboardActivity(value, period, bucket))"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-statistics-empty",
    "title": "Ноль процентов и отсутствие данных",
    "description": "При ненулевом знаменателе 0% означает, что среди учтённых пар нет нужных отметок. При пустом знаменателе личная статистика студента возвращает null в поле percent. Счётчик преподавателя и старосты хранит 0 при 0 из 0. Для строки административного отчёта API явно возвращает NO_DATA и null вместо долей.",
    "frames": [
      {
        "title": "Личный процент пуст при held = 0",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculator.java",
        "language": "java",
        "lines": [
          [
            247,
            "        private Metric metric(int count) {"
          ],
          [
            248,
            "            return new Metric(count, heldCount == 0 ? null : percentage(count, heldCount));"
          ],
          [
            249,
            "        }"
          ],
          [
            380,
            "    private static BigDecimal percentage(int count, int heldCount) {"
          ],
          [
            381,
            "        return BigDecimal.valueOf(count)"
          ],
          [
            382,
            "                .multiply(ONE_HUNDRED)"
          ],
          [
            383,
            "                .divide(BigDecimal.valueOf(heldCount), PERCENT_SCALE, RoundingMode.HALF_UP)"
          ],
          [
            384,
            "                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);"
          ],
          [
            385,
            "    }"
          ]
        ]
      },
      {
        "title": "Групповой счётчик возвращает 0 при 0 из 0",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1811,
            "    public record TeacherMetric(int numerator, int denominator, double percent) {"
          ],
          [
            1812,
            "        private static TeacherMetric of(int numerator, int denominator) {"
          ],
          [
            1813,
            "            return new TeacherMetric("
          ],
          [
            1814,
            "                    numerator,"
          ],
          [
            1815,
            "                    denominator,"
          ],
          [
            1816,
            "                    denominator == 0 ? 0.0 : numerator * 100.0 / denominator);"
          ],
          [
            1817,
            "        }"
          ],
          [
            1818,
            "    }"
          ]
        ]
      },
      {
        "title": "Админский REST различает AVAILABLE и NO_DATA",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/adminattendance/AdminAttendanceReportService.java",
        "language": "java",
        "lines": [
          [
            83,
            "            var selected = repository.select(id, search);"
          ],
          [
            84,
            "            List<Row> rows = new ArrayList<>();"
          ],
          [
            85,
            "            for (var value : attendance.read(selected, context.getInternalToken())) {"
          ],
          [
            86,
            "                long total = value.getOpportunities();"
          ],
          [
            87,
            "                rows.add("
          ],
          [
            88,
            "                        new Row("
          ],
          [
            89,
            "                                Long.toString(value.getGroupId()),"
          ],
          [
            90,
            "                                selected.groups().get(value.getGroupId()),"
          ],
          [
            91,
            "                                value.getCountedLessons(),"
          ],
          [
            92,
            "                                value.getPresent(),"
          ],
          [
            93,
            "                                value.getAbsent(),"
          ],
          [
            94,
            "                                value.getExcused(),"
          ],
          [
            95,
            "                                total,"
          ],
          [
            96,
            "                                total == 0 ? null : value.getPresentPercent(),"
          ],
          [
            97,
            "                                total == 0 ? null : value.getPresentOrExcusedPercent(),"
          ],
          [
            98,
            "                                total == 0 ? DataState.NO_DATA : DataState.AVAILABLE));"
          ],
          [
            99,
            "            }"
          ],
          [
            100,
            "            rows.sort(comparator(sort, direction));"
          ],
          [
            101,
            "            requireCurrentAccess();"
          ]
        ]
      },
      {
        "title": "Контракт строки отчёта",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/adminattendance/AdminAttendanceReportModels.java",
        "language": "java",
        "lines": [
          [
            75,
            "    public record Row("
          ],
          [
            76,
            "            String groupId,"
          ],
          [
            77,
            "            String groupName,"
          ],
          [
            78,
            "            long countedLessons,"
          ],
          [
            79,
            "            long presentCount,"
          ],
          [
            80,
            "            long absentCount,"
          ],
          [
            81,
            "            long excusedCount,"
          ],
          [
            82,
            "            long opportunities,"
          ],
          [
            83,
            "            @io.swagger.v3.oas.annotations.media.Schema("
          ],
          [
            84,
            "                            types = {\"number\", \"null\"},"
          ],
          [
            85,
            "                            format = \"double\")"
          ],
          [
            86,
            "                    Double presentPercent,"
          ],
          [
            87,
            "            @io.swagger.v3.oas.annotations.media.Schema("
          ],
          [
            88,
            "                            types = {\"number\", \"null\"},"
          ],
          [
            89,
            "                            format = \"double\")"
          ],
          [
            90,
            "                    Double presentOrExcusedPercent,"
          ],
          [
            91,
            "            DataState dataState) {}"
          ],
          [
            93,
            "    public record Page("
          ],
          [
            94,
            "            Semester semester,"
          ],
          [
            95,
            "            String search,"
          ],
          [
            96,
            "            Sort sort,"
          ],
          [
            97,
            "            Direction direction,"
          ],
          [
            98,
            "            int page,"
          ],
          [
            99,
            "            int size,"
          ],
          [
            100,
            "            long totalElements,"
          ],
          [
            101,
            "            long totalPages,"
          ],
          [
            102,
            "            Instant observedAt,"
          ],
          [
            103,
            "            List<Row> rows) {"
          ]
        ]
      },
      {
        "title": "Отчёт администратора · две строки ответа",
        "language": "json",
        "example": [
          "{",
          "  \"rows\": [",
          "    {",
          "      \"groupId\": \"41\",",
          "      \"groupName\": \"ДЕМО-411\",",
          "      \"countedLessons\": 3,",
          "      \"presentCount\": 3,",
          "      \"absentCount\": 1,",
          "      \"excusedCount\": 2,",
          "      \"opportunities\": 6,",
          "      \"presentPercent\": 50,",
          "      \"presentOrExcusedPercent\": 83.33333333333333,",
          "      \"dataState\": \"AVAILABLE\"",
          "    },",
          "    {",
          "      \"groupId\": \"42\",",
          "      \"groupName\": \"ДЕМО-412\",",
          "      \"countedLessons\": 0,",
          "      \"presentCount\": 0,",
          "      \"absentCount\": 0,",
          "      \"excusedCount\": 0,",
          "      \"opportunities\": 0,",
          "      \"presentPercent\": null,",
          "      \"presentOrExcusedPercent\": null,",
          "      \"dataState\": \"NO_DATA\"",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  }
]
