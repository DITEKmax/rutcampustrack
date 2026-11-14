import type { StoryOperation } from './story-source'

export const scheduleByRoleOperations: StoryOperation[] = [
  {
    "id": "schedule-by-role-student",
    "title": "Студент · сегодня и семестр",
    "description": "«Сегодня» берёт группу из сессии и активный семестр из Academic. Mobile BFF присоединяет отметку и допуск по ID пары. Экран выделяет текущую пару, а если её нет, ближайшую запланированную из списка за сегодня. Завершённые остаются в дневном списке. Для дня без пар есть отдельное пустое состояние. Расписание всего семестра тоже ограничено активным семестром, но может вернуть 304 по ETag. У ответа «Сегодня» стоит no-store.",
    "frames": [
      {
        "title": "Расписание дня и отметки по ID пары",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java",
        "language": "java",
        "lines": [
          [
            111,
            "    public TodayResponse today() {"
          ],
          [
            112,
            "        InternalJwtClaims claims = requireStudent(true);"
          ],
          [
            113,
            "        SemesterResponse semester = academic.activeSemester();"
          ],
          [
            114,
            "        LocalDate date = LocalDate.now(clock);"
          ],
          [
            115,
            "        LessonsResponse lessons = schedule.lessons(claims.groupId(), semester.getId(), date, date);"
          ],
          [
            116,
            "        List<LessonResponse> ordered = ordered(lessons.getLessonsList());"
          ],
          [
            117,
            "        StudentAttendanceSnapshotResponse snapshot ="
          ],
          [
            118,
            "                attendance.snapshot(ordered.stream().map(LessonResponse::getId).toList());"
          ],
          [
            119,
            "        Map<Long, StudentAttendanceEntry> entryByLesson ="
          ],
          [
            120,
            "                snapshot.getEntriesList().stream()"
          ],
          [
            121,
            "                        .collect("
          ],
          [
            122,
            "                                Collectors.toMap("
          ],
          [
            123,
            "                                        StudentAttendanceEntry::getLessonId, Function.identity()));"
          ],
          [
            125,
            "        List<TodayLessonResponse> result ="
          ],
          [
            126,
            "                ordered.stream()"
          ],
          [
            127,
            "                        .map("
          ],
          [
            128,
            "                                lesson ->"
          ],
          [
            129,
            "                                        new TodayLessonResponse("
          ],
          [
            130,
            "                                                lesson(lesson, subjects.get(lesson.getSubjectId())),"
          ],
          [
            131,
            "                                                attendance(entryByLesson.get(lesson.getId())),"
          ],
          [
            132,
            "                                                eligibility(entryByLesson.get(lesson.getId())),"
          ],
          [
            133,
            "                                                request(entryByLesson.get(lesson.getId()))))"
          ],
          [
            134,
            "                        .toList();"
          ]
        ]
      },
      {
        "title": "Запрос расписания ограничен активным семестром",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java",
        "language": "java",
        "lines": [
          [
            160,
            "    public ScheduleResponse schedule(long requestedSemesterId) {"
          ],
          [
            161,
            "        InternalJwtClaims claims = requireStudent(true);"
          ],
          [
            162,
            "        SemesterResponse active = academic.activeSemester();"
          ],
          [
            163,
            "        if (active.getId() != requestedSemesterId) {"
          ],
          [
            164,
            "            throw new MobileBffException("
          ],
          [
            165,
            "                    HttpStatus.FORBIDDEN,"
          ],
          [
            166,
            "                    ProblemCode.OUT_OF_SCOPE,"
          ],
          [
            167,
            "                    \"Семестр не входит в scope студента\");"
          ],
          [
            168,
            "        }"
          ],
          [
            169,
            "        LocalDate from = LocalDate.parse(active.getDateFrom());"
          ],
          [
            170,
            "        LocalDate to = LocalDate.parse(active.getDateTo());"
          ],
          [
            171,
            "        LessonsResponse lessons = schedule.lessons(claims.groupId(), active.getId(), from, to);"
          ],
          [
            172,
            "        List<LessonResponse> ordered = ordered(lessons.getLessonsList());"
          ]
        ]
      },
      {
        "title": "Сегодня и расписание кешируются по-разному",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentApiController.java",
        "language": "java",
        "lines": [
          [
            58,
            "    @Override"
          ],
          [
            59,
            "    public ResponseEntity<TodayResponse> getToday() {"
          ],
          [
            60,
            "        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(queries.today());"
          ],
          [
            61,
            "    }"
          ],
          [
            62,
            ""
          ],
          [
            63,
            "    @Override"
          ],
          [
            64,
            "    public ResponseEntity<ScheduleResponse> getSchedule(String semesterId, String ifNoneMatch) {"
          ],
          [
            65,
            "        ScheduleResponse result = queries.schedule(Long.parseLong(semesterId));"
          ],
          [
            66,
            "        String etag = etag(result);"
          ],
          [
            67,
            "        if (etag.equals(ifNoneMatch)) {"
          ],
          [
            68,
            "            return ResponseEntity.status(304)"
          ],
          [
            69,
            "                    .cacheControl(CacheControl.noCache().cachePrivate())"
          ],
          [
            70,
            "                    .eTag(etag)"
          ],
          [
            71,
            "                    .build();"
          ],
          [
            72,
            "        }"
          ],
          [
            73,
            "        return ResponseEntity.ok()"
          ],
          [
            74,
            "                .cacheControl(CacheControl.noCache().cachePrivate())"
          ],
          [
            75,
            "                .eTag(etag)"
          ],
          [
            76,
            "                .body(result);"
          ]
        ]
      },
      {
        "title": "Текущая пара, ближайшая и пустой день",
        "path": "frontends/mobile-core/src/features/today/TodayScreen.vue",
        "language": "typescript",
        "lines": [
          [
            102,
            "const hero = computed("
          ],
          [
            103,
            "  () =>"
          ],
          [
            104,
            "    props.today?.lessons.find((lesson) => lesson.schedule.status === 'ACTIVE') ??"
          ],
          [
            105,
            "    props.today?.lessons.find((lesson) => lesson.schedule.status === 'PLANNED') ??"
          ],
          [
            106,
            "    null,"
          ],
          [
            107,
            ");"
          ],
          [
            108,
            "const hasLessons = computed(() => (props.today?.lessons.length ?? 0) > 0);"
          ],
          [
            287,
            "      <section"
          ],
          [
            288,
            "        v-else-if=\"!hasLessons\""
          ],
          [
            289,
            "        class=\"today-state\""
          ],
          [
            290,
            "        role=\"status\""
          ],
          [
            291,
            "      >"
          ],
          [
            292,
            "        <MobileIcon name=\"calendar\" /><h2>"
          ],
          [
            293,
            "          {{ offline ? 'Нет сохранённого расписания' : 'На сегодня пар нет' }}"
          ],
          [
            294,
            "        </h2>"
          ],
          [
            399,
            "        <section"
          ],
          [
            400,
            "          class=\"today-list\""
          ],
          [
            401,
            "          aria-label=\"Пары на сегодня\""
          ],
          [
            402,
            "        >"
          ],
          [
            403,
            "          <h2>Пары на сегодня</h2>"
          ],
          [
            404,
            "          <ol>"
          ],
          [
            405,
            "            <li"
          ],
          [
            406,
            "              v-for=\"lesson in today?.lessons\""
          ],
          [
            407,
            "              :key=\"lesson.schedule.id\""
          ],
          [
            408,
            "            >"
          ],
          [
            409,
            "              <StudentLessonCard"
          ],
          [
            410,
            "                :lesson=\"lesson\""
          ],
          [
            411,
            "                :current=\"isCurrent(lesson)\""
          ]
        ]
      },
      {
        "title": "Сегодня · поля ответа для пары 731",
        "language": "json",
        "example": [
          "{",
          "  \"date\": \"2026-11-16\",",
          "  \"timeZone\": \"Europe/Moscow\",",
          "  \"serverNow\": \"2026-11-16T07:10:00Z\",",
          "  \"lessons\": [",
          "    {",
          "      \"schedule\": {",
          "        \"id\": \"731\",",
          "        \"date\": \"2026-11-16\",",
          "        \"lessonNumber\": 2,",
          "        \"startsAt\": \"10:05:00\",",
          "        \"endsAt\": \"11:25:00\",",
          "        \"status\": \"ACTIVE\"",
          "      }",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  },
  {
    "id": "schedule-by-role-headman",
    "title": "Староста · дата и номер пары",
    "description": "Журнал запрашивает сохранённые пары своей группы за выбранную дату, включая отменённые и исходные записи переноса. В конструкторе редактируют шаблоны. В журнале «2-я пара» обозначает слот и повторяется по датам, а ID 731 определяет конкретное занятие 16 ноября. По этому ID журнал загружает состав и отметки.",
    "frames": [
      {
        "title": "Чтение занятий группы с явным набором статусов",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts",
        "language": "typescript",
        "lines": [
          [
            282,
            "  listLessons("
          ],
          [
            283,
            "    groupId: number,"
          ],
          [
            284,
            "    dateFrom: string,"
          ],
          [
            285,
            "    dateTo: string,"
          ],
          [
            286,
            "  ): Promise<readonly HeadmanJournalLesson[]> {"
          ],
          [
            291,
            "    return this.requestPaged("
          ],
          [
            292,
            "      (page) => {"
          ],
          [
            293,
            "        const query = new URLSearchParams({"
          ],
          [
            294,
            "          dateFrom,"
          ],
          [
            295,
            "          dateTo,"
          ],
          [
            296,
            "          page: String(page),"
          ],
          [
            297,
            "          size: '100',"
          ],
          [
            298,
            "        })"
          ],
          [
            299,
            "        for (const status of ['PLANNED', 'ACTIVE', 'CLOSED', 'CANCELLED', 'TRANSFERRED'] as const) {"
          ],
          [
            300,
            "          query.append('status', status)"
          ],
          [
            301,
            "        }"
          ],
          [
            302,
            "        return `/api/schedule/groups/${groupId}/lessons?${query.toString()}`"
          ],
          [
            303,
            "      },"
          ],
          [
            304,
            "      'lessonResponseList',"
          ],
          [
            305,
            "      normalizeLesson,"
          ],
          [
            306,
            "    )"
          ]
        ]
      },
      {
        "title": "Загрузка дня и выбор по ID",
        "path": "frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue",
        "language": "typescript",
        "lines": [
          [
            303,
            "function selectLesson(lessonId: number): void {"
          ],
          [
            304,
            "  if (selectedLessonId.value === lessonId && report.value?.lessonId === lessonId) return;"
          ],
          [
            305,
            "  selectedLessonId.value = lessonId;"
          ],
          [
            306,
            "  resumeTransferStatusForSelectedLesson();"
          ],
          [
            307,
            "  if (canViewReport()) void loadReport(lessonId);"
          ],
          [
            1690,
            "  try {"
          ],
          [
            1691,
            "    const next = await api.listLessons(props.groupId, selectedDate.value, selectedDate.value);"
          ],
          [
            1692,
            "    if (revision !== lessonsRevision) return;"
          ],
          [
            1693,
            "    lessons.value = next;"
          ],
          [
            1694,
            "    const wantsInitialLesson ="
          ],
          [
            1695,
            "      props.initialLessonId !== null && selectedDate.value === props.initialDate;"
          ],
          [
            1696,
            "    const requestedLesson = wantsInitialLesson"
          ],
          [
            1697,
            "      ? (next.find((lesson) => lesson.id === props.initialLessonId) ?? null)"
          ],
          [
            1698,
            "      : null;"
          ],
          [
            1699,
            "    const preferredLesson ="
          ],
          [
            1700,
            "      preferredLessonId === null"
          ],
          [
            1701,
            "        ? null"
          ],
          [
            1702,
            "        : (next.find((lesson) => lesson.id === preferredLessonId) ?? null);"
          ]
        ]
      },
      {
        "title": "Отдельный запрос журнала выбранной пары",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts",
        "language": "typescript",
        "lines": [
          [
            309,
            "  async getLessonAttendance(lessonId: number): Promise<HeadmanJournalReport> {"
          ],
          [
            310,
            "    assertPositiveInteger(lessonId, 'lessonId')"
          ],
          [
            311,
            "    const value = await this.request<unknown>(`/api/attendance/reports/lesson/${lessonId}`)"
          ],
          [
            312,
            "    return normalizeReport(value, lessonId)"
          ],
          [
            313,
            "  }"
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-by-role-teacher",
    "title": "Преподаватель · пары и назначения",
    "description": "На выбранную дату Mobile BFF проверяет назначение преподавателя, группу, предмет и тип пары. В примере преподаватель видит пару 731 группы 41, если она соответствует его действующему на эту дату назначению. Список назначений загружается отдельным запросом за весь выбранный семестр. На свободную дату дневной список может быть пустым, даже когда в семестре есть назначения.",
    "frames": [
      {
        "title": "День и назначения имеют разные интервалы",
        "path": "frontends/mobile-core/src/features/teacher/TeacherHomeScreen.vue",
        "language": "typescript",
        "lines": [
          [
            147,
            "function inSemester(date: string): boolean {"
          ],
          [
            148,
            "  const { semester } = props;"
          ],
          [
            149,
            "  return semester !== null && date >= semester.dateFrom && date <= semester.dateTo;"
          ],
          [
            150,
            "}"
          ],
          [
            151,
            "async function loadDay(retainProjection = false): Promise<void> {"
          ],
          [
            152,
            "  const { api, semester, selectedDate } = props;"
          ],
          [
            153,
            "  const run = ++dayRevision;"
          ],
          [
            154,
            "  if (!retainProjection) lessons.value = [];"
          ],
          [
            155,
            "  dayError.value = null;"
          ],
          [
            156,
            "  if (!api || !semester || !inSemester(selectedDate)) {"
          ],
          [
            157,
            "    dayLoading.value = false;"
          ],
          [
            158,
            "    return;"
          ],
          [
            159,
            "  }"
          ],
          [
            160,
            "  if (!retainProjection) dayLoading.value = true;"
          ],
          [
            161,
            "  try {"
          ],
          [
            162,
            "    const day = await api.day(semester.id, selectedDate);"
          ],
          [
            163,
            "    if (run === dayRevision) lessons.value = day.lessons;"
          ],
          [
            174,
            "async function loadAssignments(retainProjection = false): Promise<void> {"
          ],
          [
            175,
            "  const { api, semester } = props;"
          ],
          [
            176,
            "  const run = ++assignmentsRevision;"
          ],
          [
            177,
            "  if (!retainProjection) assignments.value = [];"
          ],
          [
            178,
            "  assignmentsError.value = null;"
          ],
          [
            179,
            "  if (!api || !semester) {"
          ],
          [
            180,
            "    assignmentsLoading.value = false;"
          ],
          [
            181,
            "    return;"
          ],
          [
            182,
            "  }"
          ],
          [
            183,
            "  if (!retainProjection) assignmentsLoading.value = true;"
          ],
          [
            184,
            "  try {"
          ],
          [
            185,
            "    const assigned = await api.assignments(semester.id, semester.dateFrom, semester.dateTo);"
          ],
          [
            186,
            "    if (run === assignmentsRevision) assignments.value = assigned;"
          ],
          [
            331,
            "    <p"
          ],
          [
            332,
            "      v-else-if=\"!timeline.length\""
          ],
          [
            333,
            "      class=\"teacher-screen__state\""
          ],
          [
            334,
            "      role=\"status\""
          ],
          [
            335,
            "    >"
          ],
          [
            336,
            "      На этот день пар нет."
          ]
        ]
      },
      {
        "title": "Подбор назначений и пар на день",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            322,
            "    public List<TeacherApiModels.Assignment> assignments("
          ],
          [
            323,
            "            long semesterId, LocalDate dateFrom, LocalDate dateTo) {"
          ],
          [
            324,
            "        InternalJwtClaims claims = requireTeacher();"
          ],
          [
            325,
            "        TeacherAssignmentsResponse response ="
          ],
          [
            326,
            "                academic.teacherAssignments(semesterId, dateFrom.toString(), dateTo.toString());"
          ],
          [
            327,
            "        return response.getAssignmentsList().stream()"
          ],
          [
            328,
            "                .filter(value -> value.getTeacherId() == claims.userId())"
          ],
          [
            329,
            "                .map(TeacherReadFacade::assignment)"
          ],
          [
            330,
            "                .toList();"
          ],
          [
            331,
            "    }"
          ],
          [
            332,
            ""
          ],
          [
            333,
            "    public TeacherApiModels.DayResponse day(long semesterId, LocalDate date) {"
          ],
          [
            334,
            "        InternalJwtClaims claims = requireTeacher();"
          ],
          [
            335,
            "        TeacherAssignmentsResponse assignments ="
          ],
          [
            336,
            "                academic.teacherAssignments(semesterId, date.toString(), date.toString());"
          ],
          [
            337,
            "        return composeDay("
          ],
          [
            338,
            "                claims.userId(),"
          ],
          [
            339,
            "                semesterId,"
          ],
          [
            340,
            "                date,"
          ],
          [
            341,
            "                assignments,"
          ],
          [
            342,
            "                groupId -> schedule.lessons(groupId, semesterId, date, date));"
          ],
          [
            343,
            "    }"
          ],
          [
            370,
            "        Map<Long, TeacherAssignment> assignmentById = new HashMap<>();"
          ],
          [
            371,
            "        Set<Long> groupIds = new HashSet<>();"
          ],
          [
            372,
            "        for (TeacherAssignment assignment : assignments.getAssignmentsList()) {"
          ],
          [
            373,
            "            if (assignment.getAssignmentId() <= 0"
          ],
          [
            374,
            "                    || assignment.getTeacherId() != teacherId"
          ],
          [
            375,
            "                    || !inRange(assignment, date)"
          ],
          [
            376,
            "                    || assignment.getGroupId() <= 0) {"
          ],
          [
            377,
            "                continue;"
          ],
          [
            378,
            "            }"
          ],
          [
            379,
            "            assignmentById.put(assignment.getAssignmentId(), assignment);"
          ],
          [
            380,
            "            groupIds.add(assignment.getGroupId());"
          ],
          [
            381,
            "        }"
          ],
          [
            382,
            ""
          ],
          [
            383,
            "        List<LessonResponse> lessons = new ArrayList<>();"
          ],
          [
            384,
            "        for (Long groupId : groupIds) {"
          ],
          [
            385,
            "            lessons.addAll(ownGroupLessons.apply(groupId).getLessonsList());"
          ],
          [
            386,
            "        }"
          ],
          [
            387,
            "        List<TeacherApiModels.DayLesson> result ="
          ],
          [
            388,
            "                lessons.stream()"
          ],
          [
            389,
            "                        .filter(lesson -> lesson.getAssignedTeacherId() == teacherId)"
          ],
          [
            390,
            "                        .filter("
          ],
          [
            391,
            "                                lesson ->"
          ],
          [
            392,
            "                                        lesson.getSemesterId() == semesterId"
          ],
          [
            393,
            "                                                && date.toString().equals(lesson.getDate()))"
          ],
          [
            394,
            "                        .filter(lesson -> assignedToTeacher(lesson, assignmentById, teacherId))"
          ],
          [
            395,
            "                        .map(lesson -> dayLesson(lesson, assignmentById))"
          ]
        ]
      },
      {
        "title": "Пара сверяется с точным назначением",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            1216,
            "    private static boolean assignedToTeacher("
          ],
          [
            1217,
            "            LessonResponse lesson, Map<Long, TeacherAssignment> byId, long teacherId) {"
          ],
          [
            1218,
            "        if (!lesson.hasAssignmentId()"
          ],
          [
            1219,
            "                || lesson.getAssignmentId() <= 0"
          ],
          [
            1220,
            "                || !lesson.hasAssignedTeacherId()) {"
          ],
          [
            1221,
            "            return false;"
          ],
          [
            1222,
            "        }"
          ],
          [
            1223,
            "        TeacherAssignment exact = byId.get(lesson.getAssignmentId());"
          ],
          [
            1224,
            "        return exact != null"
          ],
          [
            1225,
            "                && exact.getTeacherId() == teacherId"
          ],
          [
            1226,
            "                && lesson.getAssignedTeacherId() == teacherId"
          ],
          [
            1227,
            "                && exact.getGroupId() == lesson.getGroupId()"
          ],
          [
            1228,
            "                && exact.getSubjectId() == lesson.getSubjectId()"
          ],
          [
            1229,
            "                && exact.getLessonType().equalsIgnoreCase(lesson.getLessonType());"
          ],
          [
            1230,
            "    }"
          ]
        ]
      },
      {
        "title": "День преподавателя · поля ответа для той же пары",
        "language": "json",
        "example": [
          "{",
          "  \"semesterId\": \"7\",",
          "  \"date\": \"2026-11-16\",",
          "  \"serverNow\": \"2026-11-16T07:10:00Z\",",
          "  \"lessons\": [",
          "    {",
          "      \"id\": \"731\",",
          "      \"assignmentId\": \"314\",",
          "      \"groupId\": \"41\",",
          "      \"semesterId\": \"7\",",
          "      \"date\": \"2026-11-16\",",
          "      \"lessonNumber\": 2,",
          "      \"status\": \"active\"",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  },
  {
    "id": "schedule-by-role-admin",
    "title": "Администратор · чтение через API",
    "description": "REST Schedule проверяет полный административный доступ. После допуска администратор может прочитать пары выбранной группы с фильтром по датам и при необходимости по семестру. Для роли STUDENT, в том числе старосты, тот же сервис проверяет совпадение группы с сессией.",
    "frames": [
      {
        "title": "REST-проверка полномочий администратора",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/security/RoleCheckAspect.java",
        "language": "java",
        "lines": [
          [
            39,
            "    @Around("
          ],
          [
            40,
            "            \"within(@org.springframework.web.bind.annotation.RestController *) && execution(public * *(..))\")"
          ],
          [
            41,
            "    public Object checkAdminAuthority(ProceedingJoinPoint pjp) throws Throwable {"
          ],
          [
            42,
            "        if (requestContext.getRole() == UserRole.ADMIN)"
          ],
          [
            43,
            "            adminPermissionClient.requireFull(requestContext.getInternalToken());"
          ],
          [
            44,
            "        return pjp.proceed();"
          ],
          [
            45,
            "    }"
          ]
        ]
      },
      {
        "title": "Группа, даты и опциональный фильтр по семестру",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonController.java",
        "language": "java",
        "lines": [
          [
            158,
            "    @Override"
          ],
          [
            159,
            "    public ResponseEntity<PagedModel<EntityModel<LessonResponse>>> getLessons("
          ],
          [
            160,
            "            Long groupId,"
          ],
          [
            161,
            "            Long semesterId,"
          ],
          [
            162,
            "            LocalDate dateFrom,"
          ],
          [
            163,
            "            LocalDate dateTo,"
          ],
          [
            164,
            "            List<LessonStatus> status,"
          ],
          [
            165,
            "            Pageable pageable,"
          ],
          [
            166,
            "            PagedResourcesAssembler<LessonResponse> assembler) {"
          ],
          [
            167,
            "        Page<LessonWithItem> page ="
          ],
          [
            168,
            "                lessonService.getLessonsForGroup("
          ],
          [
            169,
            "                        groupId, semesterId, dateFrom, dateTo, status, pageable);"
          ]
        ]
      },
      {
        "title": "Допуск к группе и чтение сохранённых пар",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java",
        "language": "java",
        "lines": [
          [
            248,
            "    private void requireGroupReadAccess(Long targetGroupId) {"
          ],
          [
            249,
            "        UserRole role = requestContext.getRole();"
          ],
          [
            250,
            "        if (role == UserRole.ADMIN || role == UserRole.TEACHER) {"
          ],
          [
            251,
            "            return;"
          ],
          [
            252,
            "        }"
          ],
          [
            253,
            "        Long ownGroupId = requestContext.getGroupId();"
          ],
          [
            254,
            "        if (ownGroupId == null || !ownGroupId.equals(targetGroupId)) {"
          ],
          [
            255,
            "            throw new AccessDeniedException(\"Расписание принадлежит другой группе\");"
          ],
          [
            256,
            "        }"
          ],
          [
            257,
            "    }"
          ],
          [
            550,
            "    public Page<LessonWithItem> getLessonsForGroup("
          ],
          [
            551,
            "            Long groupId,"
          ],
          [
            552,
            "            Long semesterId,"
          ],
          [
            553,
            "            LocalDate from,"
          ],
          [
            554,
            "            LocalDate to,"
          ],
          [
            555,
            "            List<LessonStatus> statuses,"
          ],
          [
            556,
            "            Pageable pageable) {"
          ],
          [
            557,
            "        // M13 G9 — STUDENT видит расписание только своей группы"
          ],
          [
            558,
            "        requireGroupReadAccess(groupId);"
          ],
          [
            567,
            "        Page<Lesson> lessonPage ="
          ],
          [
            568,
            "                semesterId == null"
          ],
          [
            569,
            "                        ? lessonRepository.pageByGroupIdAndDateBetweenAndStatusIn("
          ],
          [
            570,
            "                                groupId, from, to, effectiveStatuses, pageable)"
          ],
          [
            571,
            "                        : lessonRepository.pageByGroupIdAndSemesterIdAndDateBetweenAndStatusIn("
          ],
          [
            572,
            "                                groupId, semesterId, from, to, effectiveStatuses, pageable);"
          ]
        ]
      }
    ]
  }
]
