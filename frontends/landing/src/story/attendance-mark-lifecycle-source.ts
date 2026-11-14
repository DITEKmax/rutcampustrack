import type { StoryOperation } from './story-source'

export const attendanceMarkLifecycleOperations: readonly StoryOperation[] = [
  {
    "id": "attendance-mark-lifecycle-checkin",
    "title": "Отметка студента",
    "description": "На паре № 2 первый студент успешно проходит геопроверку. Attendance сохраняет PRESENT с источником STUDENT_GEO. Если позиция недоступна или вне зоны, сервис создаёт или переиспользует ожидающую заявку и возвращает PENDING_CONFIRMATION с пустым полем attendance. Отказ в допуске обрабатывается отдельно. Уже выставленная старостой отметка ABSENT требует заявки для пересмотра.",
    "frames": [
      {
        "title": "Геопроверка выбирает отметку или заявку",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java",
        "language": "java",
        "lines": [
          [
            383,
            "        ru.rutcampustrack.attendance.geofence.GeofenceService.Decision geoDecision ="
          ],
          [
            384,
            "                geo instanceof Coordinates coordinates"
          ],
          [
            385,
            "                        ? geofenceService.evaluate(coordinates.latitude(), coordinates.longitude())"
          ],
          [
            386,
            "                        : null;"
          ],
          [
            387,
            "        boolean geoAccepted = geoDecision != null && geoDecision.accepted();"
          ],
          [
            396,
            "        Ack ack ="
          ],
          [
            397,
            "                geoAccepted"
          ],
          [
            398,
            "                        ? markPresent(identity, lesson, current, acceptedAt)"
          ],
          [
            399,
            "                        : createOrReuseRequest(identity, lesson, acceptedAt, retryAt);"
          ],
          [
            400,
            "        receiptRepository.save(toReceipt(identity, lesson, idempotencyKey, payloadHash, ack));"
          ]
        ]
      },
      {
        "title": "Успешная запись и источник STUDENT_GEO",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java",
        "language": "java",
        "lines": [
          [
            452,
            "        attendance.setLessonId(lesson.id());"
          ],
          [
            453,
            "        attendance.setUserId(identity.userId());"
          ],
          [
            454,
            "        attendance.setGroupId(identity.groupId());"
          ],
          [
            455,
            "        attendance.setSubjectId(lesson.subjectId());"
          ],
          [
            456,
            "        attendance.setSemesterId(lesson.semesterId());"
          ],
          [
            457,
            "        attendance.setLessonNumber(lesson.lessonNumber());"
          ],
          [
            458,
            "        attendance.setLessonDate(lesson.date());"
          ],
          [
            459,
            "        attendance.setStatus(AttendanceStatus.PRESENT);"
          ],
          [
            460,
            "        attendance.setSource(AttendanceSource.STUDENT_GEO);"
          ],
          [
            461,
            "        attendance.setMarkedBy(null);"
          ],
          [
            474,
            "        attendance.setUpdatedAt(now);"
          ],
          [
            475,
            "        attendance.setMarkRevision(java.util.UUID.randomUUID().toString());"
          ],
          [
            476,
            "        AttendanceDocument saved = attendanceRepository.save(attendance);"
          ],
          [
            477,
            "        attendanceEventPublisher.recordChange("
          ],
          [
            478,
            "                \"ATTENDANCE_SET\", beforeMark, saved, identity.userId(), \"STUDENT\", null);"
          ],
          [
            505,
            "        attendanceEventPublisher.publishMarked(saved);"
          ],
          [
            506,
            "        businessMetrics.incrementAfterCommit(businessMetrics.checkinCounter(\"present\"));"
          ],
          [
            507,
            "        return presentAck(saved, lesson.id(), now);"
          ],
          [
            508,
            "    }"
          ]
        ]
      },
      {
        "title": "Ожидающая заявка и два результата",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java",
        "language": "java",
        "lines": [
          [
            533,
            "                            .status(LateCheckinRequestStatus.PENDING)"
          ],
          [
            534,
            "                            .origin(LateCheckinRequestOrigin.AUTO_GEO_FAILURE)"
          ],
          [
            535,
            "                            .createdAt(now)"
          ],
          [
            536,
            "                            .updatedAt(now)"
          ],
          [
            537,
            "                            .build();"
          ],
          [
            538,
            "            request = lateCheckinRepository.save(request);"
          ],
          [
            539,
            "            lateCheckinEventPublisher.publishRequested("
          ],
          [
            540,
            "                    request, lesson.date(), lesson.lessonNumber(), lesson.subjectId(), null);"
          ],
          [
            541,
            "            businessMetrics.incrementAfterCommit(businessMetrics.lateCheckinCreatedCounter());"
          ],
          [
            542,
            "        }"
          ],
          [
            543,
            "        Request projection = new Request(request.getId(), request.getStatus(), null);"
          ],
          [
            544,
            "        return new Ack(Outcome.PENDING_CONFIRMATION, lesson.id(), null, projection, retryAt, now);"
          ],
          [
            545,
            "    }"
          ],
          [
            553,
            "    private static Ack presentAck(AttendanceDocument document, long lessonId, Instant serverNow) {"
          ],
          [
            554,
            "        Attendance attendance ="
          ],
          [
            555,
            "                new Attendance(document.getStatus(), document.getSource(), document.getUpdatedAt());"
          ],
          [
            556,
            "        return new Ack(Outcome.PRESENT, lessonId, attendance, null, null, serverNow);"
          ],
          [
            557,
            "    }"
          ]
        ]
      },
      {
        "title": "Контракт ответа Mobile BFF",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java",
        "language": "java",
        "lines": [
          [
            269,
            "    public record AttendanceProjection("
          ],
          [
            270,
            "            AttendanceStatus status,"
          ],
          [
            271,
            "            AttendanceSource source,"
          ],
          [
            272,
            "            @Schema(nullable = true) Instant markedAt) {}"
          ],
          [
            803,
            "    public record CheckinAck("
          ],
          [
            804,
            "            CheckinOutcome outcome,"
          ],
          [
            805,
            "            String lessonId,"
          ],
          [
            806,
            "            @Schema(nullable = true) AttendanceProjection attendance,"
          ],
          [
            807,
            "            @Schema(nullable = true) AutomaticRequestProjection request,"
          ],
          [
            808,
            "            @Schema(nullable = true) Instant retryAt,"
          ],
          [
            809,
            "            Instant serverNow,"
          ],
          [
            810,
            "            @JsonProperty(\"_links\") Map<String, Link> links) {}"
          ]
        ]
      },
      {
        "title": "Ответ геоотметки · фрагмент",
        "language": "json",
        "example": [
          "{",
          "  \"outcome\": \"PRESENT\",",
          "  \"lessonId\": \"731\",",
          "  \"attendance\": {",
          "    \"status\": \"PRESENT\",",
          "    \"source\": \"STUDENT_GEO\",",
          "    \"markedAt\": \"2026-09-14T07:47:00Z\"",
          "  },",
          "  \"request\": null,",
          "  \"retryAt\": null,",
          "  \"serverNow\": \"2026-09-14T07:47:00Z\"",
          "}"
        ]
      },
      {
        "title": "Ручное отсутствие требует отдельного пересмотра",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java",
        "language": "java",
        "lines": [
          [
            317,
            "        AttendanceDocument current ="
          ],
          [
            318,
            "                attendanceRepository"
          ],
          [
            319,
            "                        .findByLessonIdAndUserId(lesson.id(), identity.userId())"
          ],
          [
            320,
            "                        .orElse(null);"
          ],
          [
            321,
            "        if (isManualHeadmanAbsence(current)) {"
          ],
          [
            322,
            "            throw new StudentCheckinException("
          ],
          [
            323,
            "                    Code.MANUAL_ABSENCE_REQUIRES_APPEAL,"
          ],
          [
            324,
            "                    \"Ручная отметка «н» оспаривается отдельной заявкой\");"
          ],
          [
            325,
            "        }"
          ],
          [
            326,
            "        if (current != null && current.getStatus() == AttendanceStatus.PRESENT) {"
          ],
          [
            327,
            "            return presentAck(current, lesson.id(), acceptedAt);"
          ],
          [
            547,
            "    private static boolean isManualHeadmanAbsence(AttendanceDocument current) {"
          ],
          [
            548,
            "        return current != null"
          ],
          [
            549,
            "                && current.getStatus() == AttendanceStatus.ABSENT"
          ],
          [
            550,
            "                && current.getSource() == AttendanceSource.HEADMAN;"
          ],
          [
            551,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-mark-lifecycle-storage",
    "title": "Одна запись на пару и студента",
    "description": "У каждого студента своя запись на физической паре. Уникальный индекс MongoDB связывает lesson_id и user_id. В записи хранятся текущий статус, источник, автор ручной отметки, время изменения и ревизия. После закрытия пары Attendance добавляет второму студенту ABSENT с источником AUTO_SCHEDULER, если записи ещё нет. Уже сохранённая геоотметка первого студента остаётся.",
    "frames": [
      {
        "title": "Уникальная пара ключей в attendances",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceDocument.java",
        "language": "java",
        "lines": [
          [
            22,
            "@Document(collection = \"attendances\")"
          ],
          [
            23,
            "@CompoundIndexes({"
          ],
          [
            24,
            "    @CompoundIndex(name = \"uniq_lesson_user\", def = \"{'lesson_id': 1, 'user_id': 1}\", unique = true)"
          ],
          [
            25,
            "})"
          ],
          [
            26,
            "public class AttendanceDocument {"
          ],
          [
            27,
            ""
          ],
          [
            28,
            "    @Id private String id;"
          ],
          [
            29,
            ""
          ],
          [
            30,
            "    @Field(\"lesson_id\")"
          ],
          [
            31,
            "    private Long lessonId;"
          ],
          [
            32,
            ""
          ],
          [
            33,
            "    @Field(\"user_id\")"
          ],
          [
            34,
            "    private Long userId;"
          ],
          [
            51,
            "    @Field(\"status\")"
          ],
          [
            52,
            "    private AttendanceStatus status;"
          ],
          [
            53,
            ""
          ],
          [
            54,
            "    @Field(\"source\")"
          ],
          [
            55,
            "    private AttendanceSource source;"
          ],
          [
            56,
            ""
          ],
          [
            57,
            "    @Field(\"marked_by\")"
          ],
          [
            58,
            "    private Long markedBy;"
          ],
          [
            81,
            "    @Field(\"created_at\")"
          ],
          [
            82,
            "    private Instant createdAt;"
          ],
          [
            83,
            ""
          ],
          [
            84,
            "    @Field(\"updated_at\")"
          ],
          [
            85,
            "    private Instant updatedAt;"
          ],
          [
            86,
            ""
          ],
          [
            87,
            "    @Field(\"excuse_request_id\")"
          ],
          [
            88,
            "    private String excuseRequestId;"
          ],
          [
            89,
            ""
          ],
          [
            90,
            "    @Field(\"mark_revision\")"
          ],
          [
            91,
            "    private String markRevision;"
          ]
        ]
      },
      {
        "title": "Автоотсутствие записывается только при вставке",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java",
        "language": "java",
        "lines": [
          [
            276,
            "        for (StudentInfo student : members.getStudentsList()) {"
          ],
          [
            277,
            "            Query filter ="
          ],
          [
            278,
            "                    Query.query("
          ],
          [
            279,
            "                            Criteria.where(\"lesson_id\")"
          ],
          [
            280,
            "                                    .is(lessonId)"
          ],
          [
            281,
            "                                    .and(\"user_id\")"
          ],
          [
            282,
            "                                    .is(student.getUserId()));"
          ],
          [
            283,
            ""
          ],
          [
            284,
            "            Update insert ="
          ],
          [
            285,
            "                    new Update()"
          ],
          [
            286,
            "                            .setOnInsert(\"lesson_id\", lessonId)"
          ],
          [
            287,
            "                            .setOnInsert(\"user_id\", student.getUserId())"
          ],
          [
            288,
            "                            .setOnInsert(\"group_id\", lesson.getGroupId())"
          ],
          [
            289,
            "                            .setOnInsert(\"subject_id\", lesson.getSubjectId())"
          ],
          [
            290,
            "                            .setOnInsert(\"semester_id\", lesson.getSemesterId())"
          ],
          [
            291,
            "                            .setOnInsert(\"lesson_number\", lesson.getLessonNumber())"
          ],
          [
            292,
            "                            .setOnInsert(\"lesson_date\", lessonDate)"
          ],
          [
            293,
            "                            .setOnInsert(\"status\", AttendanceStatus.ABSENT)"
          ],
          [
            294,
            "                            .setOnInsert(\"source\", AttendanceSource.AUTO_SCHEDULER)"
          ],
          [
            300,
            "            bulkOps.upsert(filter, insert);"
          ],
          [
            301,
            "        }"
          ],
          [
            302,
            ""
          ],
          [
            303,
            "        bulkOps.execute();"
          ]
        ]
      },
      {
        "title": "Ручное изменение использует ту же пару ключей",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java",
        "language": "java",
        "lines": [
          [
            192,
            "        Instant now = clock.instant();"
          ],
          [
            193,
            "        guardedPairLock(lesson, userId, \"ATTENDANCE_SET\", now);"
          ],
          [
            194,
            "        Query filter = pairFilter(lessonId, userId);"
          ],
          [
            195,
            "        AttendanceDocument existing = mongoTemplate.findOne(filter, AttendanceDocument.class);"
          ],
          [
            196,
            "        requirePersistedScope(existing, lesson);"
          ],
          [
            197,
            "        guardedExpectedMark("
          ],
          [
            198,
            "                lesson,"
          ],
          [
            199,
            "                userId,"
          ],
          [
            200,
            "                \"ATTENDANCE_SET\","
          ],
          [
            201,
            "                existing,"
          ],
          [
            202,
            "                request.expectedStatus(),"
          ],
          [
            203,
            "                request.expectedRevision());"
          ],
          [
            578,
            "    private static Query pairFilter(Long lessonId, Long userId) {"
          ],
          [
            579,
            "        return Query.query(Criteria.where(\"lesson_id\").is(lessonId).and(\"user_id\").is(userId));"
          ],
          [
            580,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-mark-lifecycle-access",
    "title": "Кто может менять журнал",
    "description": "Клиент старосты отправляет PUT в Attendance напрямую через Gateway. Attendance сверяет группу пары и состав на её дату через Schedule и Academic. Право действующего старосты подтверждается заново. Помощнику нужно MARK_ATTENDANCE для обычной отметки и MANAGE_EXCUSES для установки, изменения или снятия EXCUSED. Преподаватель получает журнал для доступной группы и предмета через отдельный маршрут чтения Mobile BFF.",
    "frames": [
      {
        "title": "Клиент журнала отправляет PUT",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts",
        "language": "typescript",
        "lines": [
          [
            483,
            "    assertPositiveInteger(lessonId, 'lessonId')"
          ],
          [
            484,
            "    assertPositiveInteger(userId, 'userId')"
          ],
          [
            485,
            "    const payload = { ...markPayload(command), ...(condition ?? {}) }"
          ],
          [
            486,
            "    const headers = new Headers()"
          ],
          [
            493,
            "    } else {"
          ],
          [
            494,
            "      headers.set('Content-Type', 'application/json')"
          ],
          [
            495,
            "      body = JSON.stringify(payload)"
          ],
          [
            496,
            "    }"
          ],
          [
            497,
            "    return this.request<unknown>(`/api/attendance/lessons/${lessonId}/students/${userId}`, {"
          ],
          [
            498,
            "      method: 'PUT',"
          ],
          [
            499,
            "      headers,"
          ],
          [
            500,
            "      body,"
          ],
          [
            501,
            "    }).then((value) => normalizeMarkAck(value, lessonId, userId))"
          ],
          [
            1092,
            "function markPayload(command: HeadmanJournalMarkCommand): Record<string, string> {"
          ],
          [
            1093,
            "  if (command.status === 'PRESENT' || command.status === 'ABSENT') return { status: command.status }"
          ]
        ]
      },
      {
        "title": "Gateway направляет запрос в Attendance",
        "path": "services/api-gateway/src/main/resources/application.yml",
        "language": "config",
        "lines": [
          [
            387,
            "            - id: attendance-service"
          ],
          [
            388,
            "              uri: ${ATTENDANCE_SERVICE_URL:http://attendance-service:9093}"
          ],
          [
            389,
            "              predicates:"
          ],
          [
            390,
            "                - Path=/api/attendance/**"
          ],
          [
            391,
            "              filters:"
          ],
          [
            392,
            "                - StripPrefix=1"
          ]
        ]
      },
      {
        "title": "Вход в Attendance и проверка группы пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingController.java",
        "language": "java",
        "lines": [
          [
            40,
            "    @Override"
          ],
          [
            41,
            "    @RequireRole(UserRole.STUDENT)"
          ],
          [
            42,
            "    public ResponseEntity<EntityModel<MarkResponse>> mark("
          ],
          [
            43,
            "            Long lessonId, Long userId, MarkRequest request) {"
          ],
          [
            44,
            "        AttendanceDocument doc ="
          ],
          [
            45,
            "                MarkingWriteRetry.execute("
          ],
          [
            46,
            "                        () -> markingService.markAttendance(lessonId, userId, request));"
          ]
        ]
      },
      {
        "title": "Группа и исторический состав",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java",
        "language": "java",
        "lines": [
          [
            181,
            "        LessonResponse lesson = requireWritableLesson(lessonId, userId, \"ATTENDANCE_SET\");"
          ],
          [
            182,
            "        LessonResponse readyLesson ="
          ],
          [
            183,
            "                scheduleGrpcClient.requireAttendanceMutationReady(lessonId, lesson.getGroupId());"
          ],
          [
            184,
            "        requireSameLessonScope(lesson, readyLesson);"
          ],
          [
            185,
            "        lesson = readyLesson;"
          ],
          [
            186,
            "        requireStudentInRoster(membersForLesson(lesson), userId);"
          ],
          [
            187,
            "        boolean hasMarkPermission = hasAttendancePermission(lesson.getGroupId(), \"MARK_ATTENDANCE\");"
          ],
          [
            188,
            "        boolean hasManagePermission ="
          ],
          [
            189,
            "                hasAttendancePermission(lesson.getGroupId(), \"MANAGE_EXCUSES\");"
          ],
          [
            429,
            "    private LessonResponse requireOwnLesson(Long lessonId) {"
          ],
          [
            430,
            "        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);"
          ],
          [
            431,
            "        if (lesson == null || !Objects.equals(requestContext.getGroupId(), lesson.getGroupId())) {"
          ],
          [
            432,
            "            throw new AccessDeniedException(\"Нельзя изменять студентов чужой группы\");"
          ],
          [
            433,
            "        }"
          ],
          [
            434,
            "        return lesson;"
          ],
          [
            538,
            "    private GroupMembersResponse membersForLesson(LessonResponse lesson) {"
          ],
          [
            539,
            "        if (lesson.getSemesterId() <= 0) {"
          ],
          [
            540,
            "            throw new AcademicServiceUnavailableException("
          ],
          [
            541,
            "                    \"Schedule returned a lesson without a positive semester\");"
          ],
          [
            542,
            "        }"
          ],
          [
            543,
            "        LocalDate lessonDate = LocalDate.parse(lesson.getDate());"
          ],
          [
            544,
            "        GroupMembersResponse response ="
          ],
          [
            545,
            "                academicGrpcClient.getGroupMembers("
          ],
          [
            546,
            "                        lesson.getGroupId(), lessonDate, lesson.getSemesterId());"
          ],
          [
            547,
            "        validateHistoricalRoster(response, lessonDate, lesson.getSemesterId());"
          ]
        ]
      },
      {
        "title": "Право старосты и отдельные права помощника",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java",
        "language": "java",
        "lines": [
          [
            204,
            "        boolean requiresManagePermission ="
          ],
          [
            205,
            "                request.status() == AttendanceStatus.EXCUSED"
          ],
          [
            206,
            "                        || (existing != null && existing.getStatus() == AttendanceStatus.EXCUSED);"
          ],
          [
            207,
            "        requirePrecheckedAttendancePermission("
          ],
          [
            208,
            "                requiresManagePermission, hasMarkPermission, hasManagePermission);"
          ],
          [
            515,
            "    private boolean hasAttendancePermission(Long targetGroupId, String permission) {"
          ],
          [
            516,
            "        if (targetGroupId == null || !Objects.equals(targetGroupId, requestContext.getGroupId())) {"
          ],
          [
            517,
            "            throw new AccessDeniedException(\"Нельзя изменять студентов чужой группы\");"
          ],
          [
            518,
            "        }"
          ],
          [
            519,
            "        if (requestContext.isHeadman()) {"
          ],
          [
            520,
            "            return academicGrpcClient"
          ],
          [
            521,
            "                    .isHeadman(requestContext.getUserId(), targetGroupId)"
          ],
          [
            522,
            "                    .getIsHeadman();"
          ],
          [
            523,
            "        }"
          ],
          [
            524,
            "        return academicGrpcClient.hasAssistantPermission(targetGroupId, permission);"
          ],
          [
            525,
            "    }"
          ],
          [
            526,
            ""
          ],
          [
            527,
            "    private void requirePrecheckedAttendancePermission("
          ],
          [
            528,
            "            boolean requiresManagePermission,"
          ],
          [
            529,
            "            boolean hasMarkPermission,"
          ],
          [
            530,
            "            boolean hasManagePermission) {"
          ],
          [
            531,
            "        if (requiresManagePermission ? !hasManagePermission : !hasMarkPermission) {"
          ],
          [
            532,
            "            throw new AccessDeniedException("
          ],
          [
            533,
            "                    \"Отсутствует право \""
          ],
          [
            534,
            "                            + (requiresManagePermission ? \"MANAGE_EXCUSES\" : \"MARK_ATTENDANCE\"));"
          ],
          [
            535,
            "        }"
          ]
        ]
      },
      {
        "title": "Журнал преподавателя читает данные по разрешённому предмету",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            490,
            "        if (!currentTeacherSubjectKeys(claims)"
          ],
          [
            491,
            "                .contains(new TeacherSubjectKey(groupId, subjectId))) {"
          ],
          [
            492,
            "            throw new MobileBffException("
          ],
          [
            493,
            "                    HttpStatus.NOT_FOUND,"
          ],
          [
            494,
            "                    ProblemCode.OUT_OF_SCOPE,"
          ],
          [
            495,
            "                    \"Журнал недоступен в текущем scope\");"
          ],
          [
            496,
            "        }"
          ],
          [
            497,
            "        DateRange range ="
          ],
          [
            498,
            "                selectedPeriod("
          ],
          [
            499,
            "                        semesterRange(academic.teacherAssignmentsForSemester(semesterId)),"
          ],
          [
            500,
            "                        dateFrom,"
          ],
          [
            501,
            "                        dateTo);"
          ],
          [
            502,
            "        LessonsResponse scheduled = schedule.lessons(groupId, semesterId, range.from(), range.to());"
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
          ],
          [
            552,
            "        return new TeacherApiModels.JournalResponse("
          ],
          [
            553,
            "                response.getLessonsList().stream().map(TeacherReadFacade::dayLesson).toList(),"
          ],
          [
            554,
            "                response.getStudentsList().stream().map(TeacherReadFacade::journalStudent).toList(),"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-mark-lifecycle-revision",
    "title": "Правка и конфликт версий",
    "description": "Староста исправляет ABSENT второго студента на PRESENT. После начала пары ручная правка возможна и в CLOSED, если семестр допускает запись. Отменённая пара отклоняется, состояние переноса проверяется отдельно. Клиент передаёт прочитанные статус и ревизию. Attendance сверяет их после блокировки записи. При HTTP 409 клиент перечитывает строку и ждёт нового подтверждения. После успешной правки он также читает свежую ревизию.",
    "frames": [
      {
        "title": "Ручная правка после начала пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/journal/JournalLessonPolicy.java",
        "language": "java",
        "lines": [
          [
            60,
            "    public static void requireStarted(Timing timing, Clock clock) {"
          ],
          [
            61,
            "        if (\"CANCELLED\".equals(timing.status())) {"
          ],
          [
            62,
            "            throw new BadRequestException(\"Пара отменена\");"
          ],
          [
            63,
            "        }"
          ],
          [
            64,
            "        if (clock == null || clock.instant().isBefore(timing.startsAt())) {"
          ],
          [
            65,
            "            throw new BadRequestException(\"Нельзя изменять посещаемость до начала пары\");"
          ],
          [
            66,
            "        }"
          ],
          [
            67,
            "    }"
          ],
          [
            68,
            ""
          ],
          [
            69,
            "    private static String normalizedStatus(String status) {"
          ],
          [
            70,
            "        if (status == null || status.isBlank()) {"
          ],
          [
            71,
            "            throw new BadRequestException(\"Состояние пары недоступно\");"
          ],
          [
            72,
            "        }"
          ],
          [
            73,
            "        return switch (status.toUpperCase(Locale.ROOT)) {"
          ],
          [
            74,
            "            case \"STARTED\", \"ACTIVE\" -> \"ACTIVE\";"
          ],
          [
            75,
            "            case \"PLANNED\" -> \"PLANNED\";"
          ],
          [
            76,
            "            case \"CLOSED\" -> \"CLOSED\";"
          ],
          [
            77,
            "            case \"CANCELLED\" -> \"CANCELLED\";"
          ],
          [
            78,
            "            default -> throw new BadRequestException(\"Неизвестное состояние пары\");"
          ],
          [
            79,
            "        };"
          ]
        ]
      },
      {
        "title": "Проверка переноса перед изменением",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/ScheduleGrpcClient.java",
        "language": "java",
        "lines": [
          [
            58,
            "    public LessonResponse getLessonById(Long lessonId) {"
          ],
          [
            59,
            "        try {"
          ],
          [
            60,
            "            LessonResponse response ="
          ],
          [
            61,
            "                    stub.withDeadlineAfter(3, TimeUnit.SECONDS)"
          ],
          [
            62,
            "                            .getLessonById("
          ],
          [
            63,
            "                                    LessonByIdRequest.newBuilder().setLessonId(lessonId).build());"
          ],
          [
            64,
            "            requireTransferReadable(response);"
          ],
          [
            65,
            "            return response;"
          ],
          [
            206,
            "    public LessonResponse requireAttendanceMutationReady(long lessonId, long groupId) {"
          ],
          [
            207,
            "        LessonResponse lesson = getLessonById(lessonId);"
          ],
          [
            208,
            "        if (lesson.getId() != lessonId || lesson.getGroupId() != groupId) {"
          ],
          [
            209,
            "            throw new ConflictException(\"Урок изменился; обнови данные и повтори действие\");"
          ],
          [
            210,
            "        }"
          ],
          [
            211,
            "        if (\"transferred\".equalsIgnoreCase(lesson.getStatus())) {"
          ],
          [
            212,
            "            throw new ConflictException(\"Урок перенесён; обнови данные и повтори действие\");"
          ],
          [
            213,
            "        }"
          ],
          [
            214,
            "        return lesson;"
          ],
          [
            215,
            "    }"
          ],
          [
            216,
            ""
          ],
          [
            217,
            "    private void requireTransferReadable(LessonResponse response) {"
          ],
          [
            218,
            "        if (response == null) {"
          ],
          [
            219,
            "            throw new ScheduleServiceUnavailableException(\"Schedule returned no lesson snapshot\");"
          ],
          [
            220,
            "        }"
          ],
          [
            221,
            "        String state = response.getTransferState();"
          ],
          [
            222,
            "        if (state == null || state.isBlank() || \"COMPLETED\".equals(state)) {"
          ],
          [
            223,
            "            return;"
          ],
          [
            224,
            "        }"
          ]
        ]
      },
      {
        "title": "Клиент берёт условия из открытой строки",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-manual-marks.ts",
        "language": "typescript",
        "lines": [
          [
            84,
            "export function markCondition("
          ],
          [
            85,
            "  entry: HeadmanJournalRosterEntry,"
          ],
          [
            86,
            "): HeadmanJournalMarkCondition | null {"
          ],
          [
            87,
            "  if (entry.status === 'EMPTY') return { expectedStatus: 'EMPTY', expectedRevision: 'EMPTY' }"
          ],
          [
            88,
            "  if ("
          ],
          [
            89,
            "    (entry.status === 'PRESENT' || entry.status === 'ABSENT' || entry.status === 'EXCUSED') &&"
          ],
          [
            90,
            "    entry.markRevision"
          ],
          [
            91,
            "  ) {"
          ],
          [
            92,
            "    return { expectedStatus: entry.status, expectedRevision: entry.markRevision }"
          ],
          [
            93,
            "  }"
          ],
          [
            94,
            "  return null"
          ],
          [
            95,
            "}"
          ]
        ]
      },
      {
        "title": "PUT · исправление отметки студента 110",
        "language": "json",
        "example": [
          "{",
          "  \"status\": \"PRESENT\",",
          "  \"expectedStatus\": \"ABSENT\",",
          "  \"expectedRevision\": \"45d8ce00-7b01-4eed-bd37-897b2725b452\"",
          "}"
        ]
      },
      {
        "title": "Сверка статуса и ревизии",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java",
        "language": "java",
        "lines": [
          [
            379,
            "    private static void validateExpectedMark(String status, String revision, boolean allowEmpty) {"
          ],
          [
            380,
            "        if ((status == null) != (revision == null))"
          ],
          [
            381,
            "            throw new BadRequestException("
          ],
          [
            382,
            "                    \"expectedStatus и expectedRevision должны передаваться вместе\");"
          ],
          [
            383,
            "        if (status == null) return;"
          ],
          [
            384,
            "        if (allowEmpty && \"EMPTY\".equals(status) && \"EMPTY\".equals(revision)) return;"
          ],
          [
            385,
            "        if (!Set.of(\"PRESENT\", \"EXCUSED\", \"ABSENT\").contains(status)"
          ],
          [
            386,
            "                || !revision.matches(\"[A-Za-z0-9_-]{1,128}\")"
          ],
          [
            387,
            "                || \"EMPTY\".equals(revision)) {"
          ],
          [
            388,
            "            throw new BadRequestException(\"Некорректная ожидаемая отметка\");"
          ],
          [
            389,
            "        }"
          ],
          [
            390,
            "    }"
          ],
          [
            391,
            ""
          ],
          [
            392,
            "    private static void requireExpectedMark("
          ],
          [
            393,
            "            AttendanceDocument current, String status, String revision) {"
          ],
          [
            394,
            "        if (status == null) return;"
          ],
          [
            395,
            "        if (\"EMPTY\".equals(status) && current == null) return;"
          ],
          [
            396,
            "        if (current == null"
          ],
          [
            397,
            "                || !status.equals(current.getStatus().name())"
          ],
          [
            398,
            "                || !revision.equals("
          ],
          [
            399,
            "                        ru.rutcampustrack.attendance.checkin.AttendanceRevisions.effective("
          ],
          [
            400,
            "                                current))) {"
          ],
          [
            401,
            "            throw new ConflictException("
          ],
          [
            402,
            "                    \"Отметка изменилась. Обнови журнал и подтверди действие заново\");"
          ],
          [
            403,
            "        }"
          ]
        ]
      },
      {
        "title": "Изменение получает новую ревизию",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/checkin/AttendanceRevisions.java",
        "language": "java",
        "lines": [
          [
            111,
            "            state.putAll((Map<String, Object>) insert);"
          ],
          [
            112,
            "        if (operations.get(\"$set\") instanceof Map<?, ?> set)"
          ],
          [
            113,
            "            state.putAll((Map<String, Object>) set);"
          ],
          [
            114,
            "        if (operations.get(\"$unset\") instanceof Map<?, ?> unset)"
          ],
          [
            115,
            "            unset.keySet().forEach(key -> state.remove(key.toString()));"
          ],
          [
            116,
            "        if (before != null"
          ],
          [
            117,
            "                && semantic(before).equals(encode(Arrays.stream(keys).map(state::get).toList())))"
          ],
          [
            118,
            "            return false;"
          ],
          [
            119,
            "        update.set(\"mark_revision\", UUID.randomUUID().toString());"
          ],
          [
            120,
            "        return true;"
          ],
          [
            121,
            "    }"
          ]
        ]
      },
      {
        "title": "Перечитать при конфликте и после записи",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-manual-marks.ts",
        "language": "typescript",
        "lines": [
          [
            399,
            "        if (cause instanceof HeadmanJournalApiError && cause.response.status === 409) {"
          ],
          [
            400,
            "          staleRows.value = new Set(staleRows.value).add(userId)"
          ],
          [
            401,
            "          const refreshed = await options.refresh()"
          ],
          [
            402,
            "          if (current())"
          ],
          [
            403,
            "            setIssue(userId, {"
          ],
          [
            404,
            "              kind: 'conflict',"
          ],
          [
            405,
            "              message: refreshed"
          ],
          [
            406,
            "                ? conflictReviewMessage"
          ],
          [
            407,
            "                : 'Отметка изменилась. Обнови состав и проверь текущий статус.',"
          ],
          [
            408,
            "            })"
          ],
          [
            409,
            "          return"
          ],
          [
            410,
            "        }"
          ],
          [
            418,
            "      if (!current()) return"
          ],
          [
            419,
            "      staleRows.value = new Set(staleRows.value).add(userId)"
          ],
          [
            420,
            "      project(entry, command, timestamp)"
          ],
          [
            421,
            "      if (fromForm) {"
          ],
          [
            422,
            "        draft.value = null"
          ],
          [
            423,
            "        restoreFocus(target, owner.generation, true)"
          ],
          [
            424,
            "      }"
          ],
          [
            425,
            "      // A successful write has no fresh revision. The only safe retry is this read."
          ],
          [
            426,
            "      const refreshed = await options.refresh()"
          ],
          [
            427,
            "      if (current() && !refreshed)"
          ],
          [
            428,
            "        setIssue(userId, {"
          ],
          [
            429,
            "          kind: 'accepted',"
          ],
          [
            430,
            "          message: 'Отметка сохранена. Обнови состав, чтобы снова менять эту строку.',"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-mark-lifecycle-decisions",
    "title": "Решение заявки и снятие отметки",
    "description": "При одобрении поздней отметки сохраняется PRESENT с источником LATE_CHECKIN, если ещё нет PRESENT. Одобренная уважительная причина сохраняет EXCUSED с источником HEADMAN_EXCUSE. При ручном снятии запись удаляется, а в аудите остаётся ATTENDANCE_CLEARED. Если в закрытой паре записи больше нет, отчёт может вывести ABSENT по правилу чтения.",
    "frames": [
      {
        "title": "Подтверждение поздней отметки",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java",
        "language": "java",
        "lines": [
          [
            2875,
            "        document.setStatus(AttendanceStatus.PRESENT);"
          ],
          [
            2876,
            "        document.setSource(AttendanceSource.LATE_CHECKIN);"
          ],
          [
            2877,
            "        document.setMarkedBy(markedBy);"
          ],
          [
            2878,
            "        document.setExcuseRequestId(null);"
          ],
          [
            2890,
            "        document.setUpdatedAt(now);"
          ],
          [
            2891,
            "        if (ru.rutcampustrack.attendance.checkin.AttendanceRevisions.sameMeaning("
          ],
          [
            2892,
            "                beforeMark, document)) return;"
          ],
          [
            2893,
            "        document.setMarkRevision(java.util.UUID.randomUUID().toString());"
          ],
          [
            2894,
            "        attendanceRepository.save(document);"
          ],
          [
            2895,
            "        if (actions != null)"
          ],
          [
            2896,
            "            actions.mark("
          ],
          [
            2897,
            "                    \"ATTENDANCE_SET\","
          ]
        ]
      },
      {
        "title": "Уважительная причина сохраняет уже выставленный PRESENT",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java",
        "language": "java",
        "lines": [
          [
            2452,
            "                                            if (attendance != null"
          ],
          [
            2453,
            "                                                    && attendance.getStatus()"
          ],
          [
            2454,
            "                                                            == AttendanceStatus.PRESENT) {"
          ],
          [
            2455,
            "                                                continue;"
          ],
          [
            2456,
            "                                            }"
          ],
          [
            2823,
            "        document.setStatus(AttendanceStatus.EXCUSED);"
          ],
          [
            2824,
            "        document.setSource(AttendanceSource.HEADMAN_EXCUSE);"
          ],
          [
            2825,
            "        document.setExcuseRequestId(ticket.getId());"
          ],
          [
            2826,
            "        document.setExcuseReason(reasonText(ticket));"
          ],
          [
            2827,
            "        document.setExcuseType(ticket.getExcuseType());"
          ],
          [
            2828,
            "        document.setExcuseComment(ticket.getComment());"
          ],
          [
            2829,
            "        document.setMarkedBy(actor);"
          ]
        ]
      },
      {
        "title": "Ручное снятие сохраняет историю",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java",
        "language": "java",
        "lines": [
          [
            313,
            "        requirePersistedScope(existing, lesson);"
          ],
          [
            314,
            "        requirePrecheckedAttendancePermission("
          ],
          [
            315,
            "                existing != null && existing.getStatus() == AttendanceStatus.EXCUSED,"
          ],
          [
            316,
            "                hasMarkPermission,"
          ],
          [
            317,
            "                hasManagePermission);"
          ],
          [
            318,
            "        guardedExpectedMark("
          ],
          [
            319,
            "                lesson, userId, \"ATTENDANCE_CLEARED\", existing, expectedStatus, expectedRevision);"
          ],
          [
            320,
            "        if (existing == null) return;"
          ],
          [
            321,
            "        mongoTemplate.remove(pairFilter(lessonId, userId), AttendanceDocument.class);"
          ],
          [
            322,
            "        eventPublisher.recordChange("
          ],
          [
            323,
            "                \"ATTENDANCE_CLEARED\","
          ],
          [
            324,
            "                existing,"
          ],
          [
            325,
            "                null,"
          ],
          [
            326,
            "                requestContext.getUserId(),"
          ],
          [
            327,
            "                requestContext.isHeadman() ? \"HEADMAN\" : \"ASSISTANT\","
          ],
          [
            328,
            "                null);"
          ]
        ]
      },
      {
        "title": "Эффективный статус при чтении",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/EffectiveAttendance.java",
        "language": "java",
        "lines": [
          [
            9,
            "    public static EffectiveAttendance resolve("
          ],
          [
            10,
            "            String lessonStatus,"
          ],
          [
            11,
            "            boolean hasStoredMark,"
          ],
          [
            12,
            "            AttendanceStatus storedStatus,"
          ],
          [
            13,
            "            AttendanceSource storedSource) {"
          ],
          [
            14,
            "        if (hasStoredMark) {"
          ],
          [
            15,
            "            return new EffectiveAttendance(storedStatus, storedSource);"
          ],
          [
            16,
            "        }"
          ],
          [
            17,
            "        if (\"closed\".equalsIgnoreCase(lessonStatus)) {"
          ],
          [
            18,
            "            return new EffectiveAttendance("
          ],
          [
            19,
            "                    AttendanceStatus.ABSENT, AttendanceSource.AUTO_SCHEDULER);"
          ],
          [
            20,
            "        }"
          ],
          [
            21,
            "        return new EffectiveAttendance(null, null);"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-mark-lifecycle-audit",
    "title": "Что видно в истории пары",
    "description": "Для правки второго студента Attendance сохраняет прежнее ABSENT и новое PRESENT, время и автора. Источник ручной отметки в записи HEADMAN. Если действовал помощник, роль автора в аудите ASSISTANT. История читается страницами для своей группы. Поля причины и вложения скрываются без MANAGE_EXCUSES, геоданные возвращаются по отдельному условию. Журнал может показывать и отклонённые попытки.",
    "frames": [
      {
        "title": "Отдельная коллекция истории",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/audit/LessonActionDocument.java",
        "language": "java",
        "lines": [
          [
            13,
            "@Document(\"lesson_actions\")"
          ],
          [
            14,
            "@CompoundIndex(name = \"lesson_action_page\", def = \"{'lesson_id':1,'occurred_at':-1,'_id':-1}\")"
          ],
          [
            15,
            "public class LessonActionDocument {"
          ],
          [
            16,
            "    @Id private String id;"
          ],
          [
            17,
            ""
          ],
          [
            18,
            "    @Field(\"lesson_id\")"
          ],
          [
            19,
            "    private Long lessonId;"
          ],
          [
            20,
            ""
          ],
          [
            21,
            "    @Field(\"group_id\")"
          ],
          [
            22,
            "    private Long groupId;"
          ],
          [
            23,
            ""
          ],
          [
            24,
            "    @Field(\"semester_id\")"
          ],
          [
            25,
            "    private Long semesterId;"
          ],
          [
            26,
            ""
          ],
          [
            27,
            "    @Field(\"occurred_at\")"
          ],
          [
            28,
            "    private Instant occurredAt;"
          ],
          [
            29,
            ""
          ],
          [
            30,
            "    private String source;"
          ],
          [
            31,
            "    private Map<String, Object> payload;"
          ],
          [
            32,
            "}"
          ]
        ]
      },
      {
        "title": "Актёр, прежнее и новое состояние, время",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/audit/LessonActionService.java",
        "language": "java",
        "lines": [
          [
            39,
            "    public void mark("
          ],
          [
            40,
            "            String action,"
          ],
          [
            41,
            "            AttendanceDocument before,"
          ],
          [
            42,
            "            AttendanceDocument after,"
          ],
          [
            43,
            "            Long actorId,"
          ],
          [
            44,
            "            String actorRole,"
          ],
          [
            45,
            "            Map<String, Object> ticket) {"
          ],
          [
            46,
            "        if (before == null && after == null || AttendanceRevisions.sameMeaning(before, after))"
          ],
          [
            47,
            "            return;"
          ],
          [
            48,
            "        AttendanceDocument scope = after == null ? before : after;"
          ],
          [
            49,
            "        Map<String, Object> beforeSnapshot = snapshot(before);"
          ],
          [
            60,
            "        append("
          ],
          [
            61,
            "                UUID.randomUUID().toString(),"
          ],
          [
            62,
            "                clock.instant(),"
          ],
          [
            63,
            "                \"attendance-service\","
          ],
          [
            64,
            "                payload("
          ],
          [
            65,
            "                        scope.getGroupId(),"
          ],
          [
            66,
            "                        scope.getSemesterId(),"
          ],
          [
            67,
            "                        scope.getLessonId(),"
          ],
          [
            68,
            "                        action,"
          ],
          [
            69,
            "                        person(actorId, actorRole, null),"
          ],
          [
            70,
            "                        person(scope.getUserId(), \"STUDENT\", null),"
          ],
          [
            71,
            "                        beforeSnapshot,"
          ],
          [
            72,
            "                        snapshot(after),"
          ],
          [
            73,
            "                        null,"
          ],
          [
            74,
            "                        ticket,"
          ],
          [
            75,
            "                        null,"
          ],
          [
            76,
            "                        AttendanceRevisions.effective(after)));"
          ],
          [
            227,
            "    public static Map<String, Object> snapshot(AttendanceDocument d) {"
          ],
          [
            228,
            "        if (d == null) return null;"
          ],
          [
            229,
            "        Map<String, Object> p = new LinkedHashMap<>();"
          ],
          [
            230,
            "        p.put(\"status\", d.getStatus() == null ? null : d.getStatus().name());"
          ],
          [
            231,
            "        p.put(\"source\", d.getSource() == null ? null : d.getSource().name());"
          ],
          [
            232,
            "        p.put(\"marked_by\", d.getMarkedBy());"
          ],
          [
            233,
            "        p.put(\"mark_revision\", AttendanceRevisions.effective(d));"
          ],
          [
            234,
            "        p.put(\"excuse_reason\", d.getExcuseReason());"
          ],
          [
            235,
            "        p.put(\"excuse_type\", d.getExcuseType() == null ? null : d.getExcuseType().name());"
          ],
          [
            236,
            "        p.put(\"excuse_comment\", d.getExcuseComment());"
          ]
        ]
      },
      {
        "title": "Источник отметки и роль автора записываются отдельно",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java",
        "language": "java",
        "lines": [
          [
            231,
            "        Update update ="
          ],
          [
            232,
            "                new Update()"
          ],
          [
            233,
            "                        .unset(\"excuse_request_id\")"
          ],
          [
            234,
            "                        .set(\"status\", request.status())"
          ],
          [
            235,
            "                        .set(\"source\", AttendanceSource.HEADMAN)"
          ],
          [
            236,
            "                        .set(\"marked_by\", requestContext.getUserId())"
          ],
          [
            237,
            "                        .set(\"updated_at\", now)"
          ],
          [
            238,
            "                        .set(\"group_id\", lesson.getGroupId())"
          ],
          [
            239,
            "                        .set(\"semester_id\", lesson.getSemesterId())"
          ],
          [
            240,
            "                        .setOnInsert(\"lesson_id\", lessonId)"
          ],
          [
            241,
            "                        .setOnInsert(\"user_id\", userId)"
          ],
          [
            281,
            "        eventPublisher.recordChange("
          ],
          [
            282,
            "                \"ATTENDANCE_SET\","
          ],
          [
            283,
            "                existing,"
          ],
          [
            284,
            "                doc,"
          ],
          [
            285,
            "                requestContext.getUserId(),"
          ],
          [
            286,
            "                requestContext.isHeadman() ? \"HEADMAN\" : \"ASSISTANT\","
          ],
          [
            287,
            "                null);"
          ],
          [
            288,
            "        eventPublisher.publishMarked(doc, subjectName);"
          ],
          [
            289,
            "        return doc;"
          ]
        ]
      },
      {
        "title": "Маршрут и курсор истории",
        "path": "services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/api/LessonActionsApi.java",
        "language": "java",
        "lines": [
          [
            9,
            "@RequestMapping(\"/attendance/reports\")"
          ],
          [
            10,
            "public interface LessonActionsApi {"
          ],
          [
            11,
            "    @GetMapping(\"/lesson/{lessonId}/actions\")"
          ],
          [
            12,
            "    LessonActionsResponse actions("
          ],
          [
            13,
            "            @PathVariable Long lessonId,"
          ],
          [
            14,
            "            @RequestParam(required = false) String cursor,"
          ],
          [
            15,
            "            @RequestParam(defaultValue = \"50\") int limit);"
          ],
          [
            16,
            "}"
          ]
        ]
      },
      {
        "title": "История ограничена группой, семестром и правами",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/audit/LessonActionReadService.java",
        "language": "java",
        "lines": [
          [
            50,
            "        var lesson = schedule.getLessonById(lessonId);"
          ],
          [
            51,
            "        if (actor.getUserId() == null"
          ],
          [
            52,
            "                || lesson == null"
          ],
          [
            53,
            "                || !Objects.equals(actor.getGroupId(), lesson.getGroupId())) {"
          ],
          [
            54,
            "            throw new AccessDeniedException(\"История доступна только для своей группы\");"
          ],
          [
            55,
            "        }"
          ],
          [
            56,
            "        boolean headman ="
          ],
          [
            57,
            "                actor.isHeadman()"
          ],
          [
            58,
            "                        && academic.isHeadman(actor.getUserId(), actor.getGroupId()).getIsHeadman();"
          ],
          [
            59,
            "        boolean excuses ="
          ],
          [
            60,
            "                headman || academic.hasAssistantPermission(actor.getGroupId(), \"MANAGE_EXCUSES\");"
          ],
          [
            61,
            "        boolean homework ="
          ],
          [
            62,
            "                headman || academic.hasAssistantPermission(actor.getGroupId(), \"MANAGE_HOMEWORK\");"
          ],
          [
            63,
            "        boolean mark ="
          ],
          [
            64,
            "                headman || academic.hasAssistantPermission(actor.getGroupId(), \"MARK_ATTENDANCE\");"
          ],
          [
            65,
            "        if (!mark && !excuses && !homework)"
          ],
          [
            66,
            "            throw new AccessDeniedException(\"Отсутствует право читать действия по паре\");"
          ],
          [
            67,
            "        Criteria scope ="
          ],
          [
            68,
            "                Criteria.where(\"lesson_id\")"
          ],
          [
            69,
            "                        .is(lessonId)"
          ],
          [
            70,
            "                        .and(\"group_id\")"
          ],
          [
            71,
            "                        .is(actor.getGroupId())"
          ],
          [
            72,
            "                        .and(\"semester_id\")"
          ],
          [
            73,
            "                        .is(lesson.getSemesterId());"
          ],
          [
            74,
            "        Query query = Query.query(scope);"
          ],
          [
            86,
            "        query.with(Sort.by(Sort.Order.desc(\"occurred_at\"), Sort.Order.desc(\"_id\")))"
          ],
          [
            87,
            "                .limit(limit + 1);"
          ],
          [
            88,
            "        List<LessonActionDocument> rows = mongo.find(query, LessonActionDocument.class);"
          ],
          [
            89,
            "        List<LessonActionDocument> page = rows.stream().limit(limit).toList();"
          ],
          [
            90,
            "        String next = rows.size() <= limit ? null : encode(lessonId, page.get(page.size() - 1));"
          ],
          [
            91,
            "        // Only this authorized page is enriched; one shared deadline bounds all distinct-ID"
          ],
          [
            92,
            "        // lookups."
          ],
          [
            93,
            "        Deadline namesDeadline = Deadline.after(3, TimeUnit.SECONDS);"
          ],
          [
            94,
            "        Map<Long, String> names = new HashMap<>();"
          ],
          [
            95,
            "        for (LessonActionDocument row : page) {"
          ],
          [
            96,
            "            cacheName(row.getPayload().get(\"actor\"), names, namesDeadline);"
          ],
          [
            97,
            "            cacheName(row.getPayload().get(\"student\"), names, namesDeadline);"
          ],
          [
            98,
            "        }"
          ],
          [
            99,
            "        return new LessonActionsResponse("
          ],
          [
            100,
            "                page.stream().map(row -> entry(row, excuses, homework, mark, names)).toList(),"
          ],
          [
            101,
            "                next);"
          ]
        ]
      },
      {
        "title": "Детали причины и результат действия",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/audit/LessonActionReadService.java",
        "language": "java",
        "lines": [
          [
            123,
            "        Map<String, Object> p = row.getPayload();"
          ],
          [
            124,
            "        String action = (String) p.get(\"action\");"
          ],
          [
            125,
            "        boolean hideHomework = action.startsWith(\"HOMEWORK_\") && !homework;"
          ],
          [
            126,
            "        Map<String, Object> before ="
          ],
          [
            127,
            "                hideHomework ? null : redact((Map<String, Object>) p.get(\"before\"), excuses);"
          ],
          [
            128,
            "        Map<String, Object> after ="
          ],
          [
            129,
            "                hideHomework ? null : redact((Map<String, Object>) p.get(\"after\"), excuses);"
          ],
          [
            130,
            "        Map<String, Object> geo = (Map<String, Object>) p.get(\"geo\");"
          ],
          [
            131,
            "        boolean coordinatesVisible ="
          ],
          [
            132,
            "                mark"
          ],
          [
            133,
            "                        && \"GEO_ATTEMPT\".equals(action)"
          ],
          [
            134,
            "                        && geo != null"
          ],
          [
            135,
            "                        && (\"REJECTED\".equals(p.get(\"outcome\"))"
          ],
          [
            136,
            "                                || \"OUTSIDE_RADIUS\".equals(geo.get(\"verdict\")));"
          ],
          [
            161,
            "                excuses ? (Map<String, Object>) p.get(\"ticket\") : null,"
          ],
          [
            162,
            "                homework ? (Map<String, Object>) p.get(\"homework\") : null,"
          ],
          [
            163,
            "                row.getSource(),"
          ],
          [
            164,
            "                \"REJECTED\".equals(p.get(\"outcome\")) ? \"REJECTED\" : \"APPLIED\","
          ],
          [
            165,
            "                \"REJECTED\".equals(p.get(\"outcome\")) ? (String) p.get(\"error_code\") : null);"
          ],
          [
            166,
            "    }"
          ],
          [
            167,
            ""
          ],
          [
            168,
            "    static Map<String, Object> redact(Map<String, Object> source, boolean excuses) {"
          ],
          [
            169,
            "        if (source == null) return null;"
          ],
          [
            170,
            "        Map<String, Object> result = new LinkedHashMap<>();"
          ],
          [
            171,
            "        source.forEach("
          ],
          [
            172,
            "                (key, value) -> {"
          ],
          [
            173,
            "                    String normalized = key.toLowerCase(java.util.Locale.ROOT);"
          ],
          [
            174,
            "                    if (excuses"
          ],
          [
            175,
            "                            || !(normalized.contains(\"excuse\")"
          ],
          [
            176,
            "                                    || normalized.contains(\"attachment\")"
          ],
          [
            177,
            "                                    || normalized.contains(\"file\")"
          ],
          [
            178,
            "                                    || normalized.contains(\"comment\")"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-mark-lifecycle-events",
    "title": "Запись и сигнал обновления",
    "description": "При сохранении правки в outbox попадает attendance.marked. В поле marked_by передаётся источник отметки. Для обновления данных история добавляет отдельное событие dashboard.invalidated в той же транзакции. Отклонённая попытка остаётся в аудите без этого сигнала. Клиент журнала старосты перечитывает сохранённую строку после ответа PUT. PWA использует отдельный сигнал для обновления панели администратора.",
    "frames": [
      {
        "title": "Событие об отметке записывается в outbox",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/AttendanceEventPublisher.java",
        "language": "java",
        "lines": [
          [
            87,
            "    public void publishMarked(AttendanceDocument doc, String subjectName) {"
          ],
          [
            88,
            "        Map<String, Object> payload = new LinkedHashMap<>();"
          ],
          [
            89,
            "        payload.put(\"lesson_id\", doc.getLessonId());"
          ],
          [
            90,
            "        payload.put(\"user_id\", doc.getUserId());"
          ],
          [
            91,
            "        payload.put(\"group_id\", doc.getGroupId());"
          ],
          [
            92,
            "        payload.put(\"status\", doc.getStatus().name().toLowerCase());"
          ],
          [
            93,
            "        payload.put(\"marked_by\", doc.getSource().name().toLowerCase());"
          ],
          [
            94,
            "        if (doc.getLessonNumber() != null) {"
          ],
          [
            95,
            "            payload.put(\"lesson_number\", doc.getLessonNumber());"
          ],
          [
            96,
            "        }"
          ],
          [
            97,
            "        if (doc.getLessonDate() != null) {"
          ],
          [
            98,
            "            payload.put(\"lesson_date\", doc.getLessonDate().toString());"
          ],
          [
            99,
            "        }"
          ],
          [
            100,
            "        if (doc.getSubjectId() != null) {"
          ],
          [
            101,
            "            payload.put(\"subject_id\", doc.getSubjectId());"
          ],
          [
            102,
            "        }"
          ],
          [
            103,
            "        if (subjectName != null && !subjectName.isBlank()) {"
          ],
          [
            104,
            "            payload.put(\"subject_name\", subjectName);"
          ],
          [
            105,
            "        }"
          ],
          [
            107,
            "        Map<String, Object> envelope = EventEnvelope.build(EVENT_TYPE, payload);"
          ],
          [
            108,
            ""
          ],
          [
            109,
            "        String json;"
          ],
          [
            110,
            "        try {"
          ],
          [
            111,
            "            json = objectMapper.writeValueAsString(envelope);"
          ],
          [
            112,
            "        } catch (JsonProcessingException e) {"
          ],
          [
            113,
            "            throw new IllegalStateException(\"Failed to serialize attendance.marked\", e);"
          ],
          [
            114,
            "        }"
          ],
          [
            115,
            "        outboxStorage.save(EVENT_TYPE, json);"
          ]
        ]
      },
      {
        "title": "Данные события attendance.marked",
        "language": "json",
        "example": [
          "{",
          "  \"lesson_id\": 731,",
          "  \"user_id\": 110,",
          "  \"group_id\": 41,",
          "  \"status\": \"present\",",
          "  \"marked_by\": \"headman\",",
          "  \"lesson_number\": 2,",
          "  \"lesson_date\": \"2026-09-14\",",
          "  \"subject_id\": 19",
          "}"
        ]
      },
      {
        "title": "Принятое изменение добавляет сигнал обновления",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/audit/LessonActionService.java",
        "language": "java",
        "lines": [
          [
            166,
            "        LessonActionDocument row = new LessonActionDocument();"
          ],
          [
            167,
            "        row.setId(id);"
          ],
          [
            168,
            "        row.setOccurredAt(at.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));"
          ],
          [
            169,
            "        row.setSource(source);"
          ],
          [
            170,
            "        row.setPayload(payload);"
          ],
          [
            171,
            "        row.setGroupId(((Number) payload.get(\"group_id\")).longValue());"
          ],
          [
            172,
            "        row.setSemesterId(((Number) payload.get(\"semester_id\")).longValue());"
          ],
          [
            173,
            "        Object lessonId = payload.get(\"lesson_id\");"
          ],
          [
            174,
            "        row.setLessonId(lessonId == null ? null : ((Number) lessonId).longValue());"
          ],
          [
            175,
            "        mongo.insert(row);"
          ],
          [
            176,
            "        // Rejected attempts are retained audit evidence and never change dashboard aggregates."
          ],
          [
            177,
            "        if (\"REJECTED\".equals(payload.get(\"outcome\"))) return;"
          ],
          [
            178,
            "        // Projection and signal share the same Mongo transaction: REST is readable before delivery."
          ],
          [
            179,
            "        try {"
          ],
          [
            180,
            "            outbox.save("
          ],
          [
            181,
            "                    \"dashboard.invalidated\","
          ],
          [
            182,
            "                    mapper.writeValueAsString("
          ],
          [
            183,
            "                            EventEnvelope.build(\"dashboard.invalidated\", Map.of())));"
          ],
          [
            184,
            "        } catch (JsonProcessingException failure) {"
          ],
          [
            185,
            "            throw new IllegalStateException(\"Failed to serialize dashboard invalidation\", failure);"
          ],
          [
            186,
            "        }"
          ]
        ]
      },
      {
        "title": "Панель администратора получает сигнал обновления",
        "path": "frontends/pwa-vue/src/App.vue",
        "language": "typescript",
        "lines": [
          [
            952,
            "        adminDashboard: dashboardAccess,"
          ],
          [
            953,
            "      },"
          ],
          [
            954,
            "      fetcher: currentFetcher(),"
          ],
          [
            955,
            "      onAdminDashboardChanged: () => {"
          ],
          [
            956,
            "        if (adminDashboardRealtimeAccess.value && adminAuthorityOwner.isCurrent(authorityRevision))"
          ],
          [
            957,
            "          adminDashboardRevision.value += 1;"
          ],
          [
            958,
            "      },"
          ],
          [
            2486,
            "      :client=\"adminDashboardApi\""
          ],
          [
            2487,
            "      :invalidation-revision=\"adminDashboardRevision\""
          ]
        ]
      },
      {
        "title": "Журнал перечитывает сохранённые данные",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-manual-marks.ts",
        "language": "typescript",
        "lines": [
          [
            418,
            "      if (!current()) return"
          ],
          [
            419,
            "      staleRows.value = new Set(staleRows.value).add(userId)"
          ],
          [
            420,
            "      project(entry, command, timestamp)"
          ],
          [
            421,
            "      if (fromForm) {"
          ],
          [
            422,
            "        draft.value = null"
          ],
          [
            423,
            "        restoreFocus(target, owner.generation, true)"
          ],
          [
            424,
            "      }"
          ],
          [
            425,
            "      // A successful write has no fresh revision. The only safe retry is this read."
          ],
          [
            426,
            "      const refreshed = await options.refresh()"
          ],
          [
            427,
            "      if (current() && !refreshed)"
          ],
          [
            428,
            "        setIssue(userId, {"
          ],
          [
            429,
            "          kind: 'accepted',"
          ],
          [
            430,
            "          message: 'Отметка сохранена. Обнови состав, чтобы снова менять эту строку.',"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-mark-lifecycle-statistics",
    "title": "Как меняются доли в статистике",
    "description": "Для сводки старосты сервис читает завершённые пары, исторический состав и текущие отметки. Пустая ячейка учитывается как ABSENT, отмена исключается. В примере фильтр оставил одну закрытую пару с двумя студентами. После правки доля PRESENT меняется с 1 из 2 на 2 из 2. Экран получает рассчитанные доли при запросе статистики.",
    "frames": [
      {
        "title": "В выборку метрик входят закрытые пары",
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
        "title": "Текущая отметка по ключу пары и студента",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            1193,
            "        List<Long> lessonIds ="
          ],
          [
            1194,
            "                selectedLessons.stream().map(value -> (long) value.lesson().getId()).toList();"
          ],
          [
            1195,
            "        List<AttendanceRecord> records ="
          ],
          [
            1196,
            "                attendanceReadPort.findByGroupAndDateRange(groupId, semesterFrom, semesterTo);"
          ],
          [
            1197,
            "        Map<AttendanceKey, AttendanceRecord> attendanceByCell ="
          ],
          [
            1198,
            "                validateAttendanceRecords(records, groupId, lessonsById);"
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
        "title": "Числитель и знаменатель по статусам",
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
        "title": "Поля сводки в REST-ответе",
        "path": "services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/HeadmanStatsResponse.java",
        "language": "java",
        "lines": [
          [
            9,
            "public record HeadmanStatsResponse("
          ],
          [
            10,
            "        Context context,"
          ],
          [
            11,
            "        Metrics summary,"
          ],
          [
            12,
            "        SummaryTickets summaryTickets,"
          ],
          [
            13,
            "        int filteredStudents,"
          ],
          [
            14,
            "        List<StudentRow> rows,"
          ],
          [
            15,
            "        List<SubjectOption> subjects,"
          ],
          [
            110,
            "    public record Metrics(Metric present, Metric presentOrExcused, Metric excused, Metric absent) {"
          ],
          [
            111,
            "        public Metrics {"
          ],
          [
            112,
            "            present = Objects.requireNonNull(present, \"present\");"
          ],
          [
            113,
            "            presentOrExcused = Objects.requireNonNull(presentOrExcused, \"presentOrExcused\");"
          ],
          [
            114,
            "            excused = Objects.requireNonNull(excused, \"excused\");"
          ],
          [
            115,
            "            absent = Objects.requireNonNull(absent, \"absent\");"
          ],
          [
            116,
            "        }"
          ],
          [
            117,
            "    }"
          ],
          [
            118,
            ""
          ],
          [
            119,
            "    public record Metric(int numerator, int denominator, double percent) {"
          ],
          [
            120,
            "        public Metric {"
          ],
          [
            121,
            "            if (numerator < 0"
          ],
          [
            122,
            "                    || denominator < 0"
          ],
          [
            123,
            "                    || numerator > denominator"
          ],
          [
            124,
            "                    || !Double.isFinite(percent)"
          ],
          [
            125,
            "                    || percent < 0"
          ]
        ]
      },
      {
        "title": "Экран запрашивает сводку с фильтром",
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
        "title": "Ответ статистики после правки · фрагмент",
        "language": "json",
        "example": [
          "{",
          "  \"summary\": {",
          "    \"present\": {",
          "      \"numerator\": 2,",
          "      \"denominator\": 2,",
          "      \"percent\": 100",
          "    },",
          "    \"presentOrExcused\": {",
          "      \"numerator\": 2,",
          "      \"denominator\": 2,",
          "      \"percent\": 100",
          "    },",
          "    \"excused\": {",
          "      \"numerator\": 0,",
          "      \"denominator\": 2,",
          "      \"percent\": 0",
          "    },",
          "    \"absent\": {",
          "      \"numerator\": 0,",
          "      \"denominator\": 2,",
          "      \"percent\": 0",
          "    }",
          "  }",
          "}"
        ]
      }
    ]
  }
]
