import type { StoryOperation } from './story-source'

export const lessonRequestAccessOperations: readonly StoryOperation[] = [
  {
    "id": "lesson-request-access-lesson",
    "title": "Пара в расписании и отмена",
    "description": "Рассмотрим пару 731 группы 41. Студент видит расписание своей группы. Чтение расписания в Schedule и чтение журнала преподавателя проверяются отдельно. Отмена пары требует действующего старосту этой группы, помощника с CANCEL_LESSONS или администратора с FULL на REST-границе Schedule.",
    "frames": [
      {
        "title": "Чтение расписания своей группы",
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
          ]
        ]
      },
      {
        "title": "Отмена проверяет право на группу пары",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java",
        "language": "java",
        "lines": [
          [
            225,
            "    private void requireHeadmanForGroup(Long targetGroupId, String assistantPermission) {"
          ],
          [
            226,
            "        UserRole role = requestContext.getRole();"
          ],
          [
            227,
            "        if (role == UserRole.ADMIN) return;"
          ],
          [
            228,
            "        if (!requestContext.isHeadman()) {"
          ],
          [
            229,
            "            if (assistantPermission == null"
          ],
          [
            230,
            "                    || !java.util.Objects.equals(requestContext.getGroupId(), targetGroupId)"
          ],
          [
            231,
            "                    || !academicGrpcClient.hasAssistantPermission("
          ],
          [
            232,
            "                            targetGroupId, assistantPermission)) {"
          ],
          [
            233,
            "                throw new AccessDeniedException("
          ],
          [
            234,
            "                        \"Only headman or authorized assistant can perform this action\");"
          ],
          [
            235,
            "            }"
          ],
          [
            236,
            "            return;"
          ],
          [
            237,
            "        }"
          ],
          [
            238,
            "        boolean confirmed = academicGrpcClient.isHeadman(requestContext.getUserId(), targetGroupId);"
          ],
          [
            239,
            "        if (!confirmed) {"
          ],
          [
            240,
            "            throw new AccessDeniedException(\"You are not headman of group \" + targetGroupId);"
          ],
          [
            241,
            "        }"
          ],
          [
            242,
            "    }"
          ],
          [
            280,
            "        requireHeadmanForGroup("
          ],
          [
            281,
            "                lesson.getGroupId() != null ? lesson.getGroupId() : item.getGroupId(),"
          ],
          [
            282,
            "                assistantPermission);"
          ],
          [
            283,
            "        return new LessonWithItem(lesson, item);"
          ],
          [
            295,
            "    public LessonWithItem cancelLesson(Long lessonId, CancelLessonRequest request) {"
          ],
          [
            296,
            "        LessonWithItem lwi = findLessonAndValidateGroup(lessonId, \"CANCEL_LESSONS\");"
          ],
          [
            297,
            "        Lesson lesson = lwi.lesson();"
          ],
          [
            298,
            "        ScheduleItem item = lwi.scheduleItem();"
          ],
          [
            299,
            "        if (lesson.getStatus() == LessonStatus.CANCELLED) {"
          ],
          [
            300,
            "            throw new InvalidLessonStateException(\"Lesson is already cancelled\");"
          ]
        ]
      },
      {
        "title": "Администратор проходит проверку FULL",
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
      }
    ]
  },
  {
    "id": "lesson-request-access-student",
    "title": "Студент · своя заявка",
    "description": "Студент открывает свою заявку к паре 731 через Mobile BFF. Attendance берёт личность из подписанного контекста и сверяет её с владельцем заявки. Проверка применяется к уважительной причине и к заявке на позднюю отметку. ID заявки сам по себе доступа не даёт.",
    "frames": [
      {
        "title": "Мобильный запрос карточки студента",
        "path": "frontends/mobile-core/src/api/student-client.ts",
        "language": "typescript",
        "lines": [
          [
            314,
            "  getRequest(id: string): Promise<StudentRequestDetail> {"
          ],
          [
            315,
            "    return this.request(`/api/v1/student/requests/${encodeURIComponent(id)}`)"
          ],
          [
            316,
            "  }"
          ]
        ]
      },
      {
        "title": "Подписанный контекст передаётся в Attendance",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            282,
            "    public void getStudentRequest("
          ],
          [
            283,
            "            StudentRequestId request, StreamObserver<StudentRequestDetail> observer) {"
          ],
          [
            284,
            "        try {"
          ],
          [
            285,
            "            StudentRequestModels.Identity identity = requestIdentity(requireClaims());"
          ],
          [
            286,
            "            validateObjectId(request.getRequestId(), \"request_id\");"
          ],
          [
            287,
            "            observer.onNext("
          ],
          [
            288,
            "                    StudentRequestGrpcMapper.detail("
          ],
          [
            289,
            "                            requireRequestService().get(identity, request.getRequestId())));"
          ],
          [
            290,
            "            observer.onCompleted();"
          ],
          [
            291,
            "        } catch (RuntimeException error) {"
          ],
          [
            292,
            "            observer.onError(StudentRequestGrpcErrors.toStatus(error));"
          ],
          [
            293,
            "        }"
          ]
        ]
      },
      {
        "title": "Владелец проверяется для обоих типов",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java",
        "language": "java",
        "lines": [
          [
            430,
            "    public RequestDetail get(Identity identity, String requestId) {"
          ],
          [
            431,
            "        requireStudentIdentity(identity);"
          ],
          [
            432,
            "        ExcuseTicket excuse = excuseRepository.findById(requestId).orElse(null);"
          ],
          [
            433,
            "        if (excuse != null) {"
          ],
          [
            434,
            "            requireOwner(identity, excuse.getStudentId());"
          ],
          [
            435,
            "            return toDetail(excuse);"
          ],
          [
            436,
            "        }"
          ],
          [
            437,
            "        LateCheckinRequest late = lateCheckinRepository.findById(requestId).orElse(null);"
          ],
          [
            438,
            "        if (late != null) {"
          ],
          [
            439,
            "            requireOwner(identity, late.getStudentId());"
          ],
          [
            440,
            "            return toDetail(late);"
          ],
          [
            441,
            "        }"
          ],
          [
            442,
            "        throw new ResourceNotFoundException(\"StudentRequest\", \"id\", requestId);"
          ],
          [
            443,
            "    }"
          ],
          [
            3045,
            "    private void requireOwner(Identity identity, Long ownerStudentId) {"
          ],
          [
            3046,
            "        if (!Objects.equals(ownerStudentId, identity.userId())) {"
          ],
          [
            3047,
            "            throw new AccessDeniedException(\"Доступ к заявке запрещён\");"
          ],
          [
            3048,
            "        }"
          ],
          [
            3049,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "lesson-request-access-decision",
    "title": "Староста · решение по заявке",
    "description": "Карточку и решение открывают действующий староста той же группы или её помощник с MANAGE_EXCUSES. Сервер проверяет право заново при решении. Для отказа нужна непустая причина. В этом примере староста отклоняет заявку по паре 731.",
    "frames": [
      {
        "title": "Одно право для чтения и решения",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/HeadmanRequestService.java",
        "language": "java",
        "lines": [
          [
            138,
            "    public HeadmanRequestDetailResponse get(Identity identity, String requestId) {"
          ],
          [
            139,
            "        Loaded loaded = load(requestId);"
          ],
          [
            140,
            "        requireHeadmanAuthority(identity, loaded.groupId());"
          ],
          [
            141,
            "        return detail(identity, loaded);"
          ],
          [
            142,
            "    }"
          ],
          [
            143,
            ""
          ],
          [
            144,
            "    public HeadmanRequestDetailResponse decide("
          ],
          [
            145,
            "            Identity identity, String requestId, HeadmanRequestDecisionRequest command) {"
          ],
          [
            146,
            "        if (command == null || command.decision() == null) {"
          ],
          [
            147,
            "            throw new BadRequestException(\"Решение обязательно\");"
          ],
          [
            148,
            "        }"
          ],
          [
            149,
            "        String reason = normalize(command.reason());"
          ],
          [
            150,
            "        if (command.decision() == HeadmanRequestDecision.REJECTED && reason == null) {"
          ],
          [
            151,
            "            throw new BadRequestException(\"Для отклонения нужно указать причину\");"
          ],
          [
            152,
            "        }"
          ],
          [
            153,
            ""
          ],
          [
            154,
            "        Loaded loaded = load(requestId);"
          ],
          [
            155,
            "        requireHeadmanAuthority(identity, loaded.groupId());"
          ],
          [
            156,
            "        boolean approved = command.decision() == HeadmanRequestDecision.APPROVED;"
          ]
        ]
      },
      {
        "title": "Группа и полномочия проверяются на сервере",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/StudentRequestService.java",
        "language": "java",
        "lines": [
          [
            2945,
            "    private long requireDecisionAuthority(Identity identity, Long resourceGroupId) {"
          ],
          [
            2946,
            "        long caller = requireAuthenticatedScope(identity);"
          ],
          [
            2947,
            "        if (resourceGroupId == null || !Objects.equals(resourceGroupId, identity.groupId())) {"
          ],
          [
            2948,
            "            throw new AccessDeniedException(\"Нельзя принимать решение по чужой группе\");"
          ],
          [
            2949,
            "        }"
          ],
          [
            2950,
            "        if (identity.headman()) {"
          ],
          [
            2951,
            "            var authority = academicGrpcClient.isHeadman(caller, resourceGroupId);"
          ],
          [
            2952,
            "            if (authority == null || !authority.getIsHeadman()) {"
          ],
          [
            2953,
            "                throw new AccessDeniedException(\"Староста не подтверждён для группы заявки\");"
          ],
          [
            2954,
            "            }"
          ],
          [
            2955,
            "        } else if (!academicGrpcClient.hasAssistantPermission(resourceGroupId, \"MANAGE_EXCUSES\")) {"
          ],
          [
            2956,
            "            throw new AccessDeniedException(\"Отсутствует право MANAGE_EXCUSES\");"
          ],
          [
            2957,
            "        }"
          ],
          [
            2958,
            "        return caller;"
          ],
          [
            2959,
            "    }"
          ],
          [
            2960,
            ""
          ],
          [
            2961,
            "    /** Shared authorization gate for headman read adapters as well as decisions. */"
          ],
          [
            2962,
            "    public long requireHeadmanAuthority(Identity identity, Long resourceGroupId) {"
          ],
          [
            2963,
            "        return requireDecisionAuthority(identity, resourceGroupId);"
          ],
          [
            2964,
            "    }"
          ]
        ]
      },
      {
        "title": "POST отправляет решение и причину",
        "path": "frontends/mobile-core/src/features/headman-requests/headman-requests-client.ts",
        "language": "typescript",
        "lines": [
          [
            166,
            "    assertId(requestId)"
          ],
          [
            167,
            "    if (decision !== 'APPROVED' && decision !== 'REJECTED')"
          ],
          [
            168,
            "      throw new TypeError('Unsupported request decision')"
          ],
          [
            169,
            "    const normalizedReason = reason?.trim() || null"
          ],
          [
            170,
            "    if (decision === 'REJECTED' && !normalizedReason)"
          ],
          [
            171,
            "      throw new TypeError('A rejection reason is required')"
          ],
          [
            172,
            "    return this.request<unknown>("
          ],
          [
            173,
            "      `/api/attendance/requests/${encodeURIComponent(requestId)}/decision`,"
          ],
          [
            174,
            "      {"
          ],
          [
            175,
            "        method: 'POST',"
          ],
          [
            176,
            "        body: JSON.stringify({"
          ],
          [
            177,
            "          decision,"
          ],
          [
            178,
            "          ...(normalizedReason ? { reason: normalizedReason } : {}),"
          ],
          [
            179,
            "          ...selection,"
          ],
          [
            180,
            "        }),"
          ],
          [
            181,
            "      },"
          ],
          [
            182,
            "    ).then(normalizeDetail)"
          ]
        ]
      },
      {
        "title": "Поля команды решения",
        "path": "services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/headman/HeadmanRequestDecisionRequest.java",
        "language": "java",
        "lines": [
          [
            8,
            "public record HeadmanRequestDecisionRequest("
          ],
          [
            9,
            "        @NotNull HeadmanRequestDecision decision,"
          ],
          [
            10,
            "        @Size(max = 1000) String reason,"
          ],
          [
            11,
            "        String expectedRevision,"
          ],
          [
            12,
            "        List<String> excludedOccurrenceIds,"
          ],
          [
            13,
            "        String requestKey) {"
          ],
          [
            14,
            "    public HeadmanRequestDecisionRequest(HeadmanRequestDecision decision, String reason) {"
          ],
          [
            15,
            "        this(decision, reason, null, null, null);"
          ],
          [
            16,
            "    }"
          ]
        ]
      },
      {
        "title": "POST · отклонение заявки",
        "language": "json",
        "example": [
          "{",
          "  \"decision\": \"REJECTED\",",
          "  \"reason\": \"Не хватает подтверждения\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "lesson-request-access-teacher",
    "title": "Преподаватель · разрешённые пары заявки",
    "description": "Преподаватель открывает уважительную заявку через свой маршрут чтения. Attendance сверяет текущую связку «группа и предмет» для каждой пары заявки. Если доступна только пара 731, карточка вернёт её в списке занятий. Если доступных пар нет, сервис возвращает 404. Решение остаётся за старостой или уполномоченным помощником.",
    "frames": [
      {
        "title": "Карточка по маршруту преподавателя",
        "path": "frontends/mobile-core/src/features/teacher/teacher-client.ts",
        "language": "typescript",
        "lines": [
          [
            614,
            "  excuse("
          ],
          [
            615,
            "    requestId: string,"
          ],
          [
            616,
            "    options?: Pick<RequestInit, 'cache' | 'signal'>,"
          ],
          [
            617,
            "  ): Promise<TeacherExcuseResponse> {"
          ],
          [
            618,
            "    assertText(requestId, 'requestId')"
          ],
          [
            619,
            "    return this.request<unknown>(`/api/v1/teacher/excuses/${encodeURIComponent(requestId)}`, options).then("
          ],
          [
            620,
            "      normalizeExcuse,"
          ],
          [
            621,
            "    )"
          ],
          [
            622,
            "  }"
          ]
        ]
      },
      {
        "title": "Ответ строится из доступных занятий",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            558,
            "    public void getTeacherExcuse("
          ],
          [
            559,
            "            TeacherExcuseRequest request, StreamObserver<TeacherExcuseResponse> responseObserver) {"
          ],
          [
            560,
            "        try {"
          ],
          [
            561,
            "            InternalJwtClaims claims = requireTeacher();"
          ],
          [
            562,
            "            String requestId = required(request.getRequestId(), \"request_id\");"
          ],
          [
            563,
            "            ExcuseTicket ticket ="
          ],
          [
            564,
            "                    excuseRepository"
          ],
          [
            565,
            "                            .findById(requestId)"
          ],
          [
            566,
            "                            .orElseThrow("
          ],
          [
            567,
            "                                    () ->"
          ],
          [
            568,
            "                                            Status.NOT_FOUND"
          ],
          [
            569,
            "                                                    .withDescription(\"request not found\")"
          ],
          [
            570,
            "                                                    .asRuntimeException());"
          ],
          [
            571,
            "            List<LessonResponse> lessons = authorizedTicketLessons(ticket, claims.userId());"
          ],
          [
            572,
            "            if (lessons.isEmpty()) {"
          ],
          [
            573,
            "                throw Status.NOT_FOUND.withDescription(\"request not found\").asRuntimeException();"
          ],
          [
            574,
            "            }"
          ],
          [
            575,
            "            TeacherExcuseResponse.Builder result ="
          ],
          [
            576,
            "                    TeacherExcuseResponse.newBuilder()"
          ],
          [
            577,
            "                            .setRequestId(ticket.getId())"
          ],
          [
            578,
            "                            .setRequestKind(\"EXCUSE\")"
          ],
          [
            617,
            "            for (LessonResponse lesson : lessons) {"
          ],
          [
            618,
            "                result.addLessons(toSummary(lesson));"
          ],
          [
            619,
            "            }"
          ]
        ]
      },
      {
        "title": "Каждая пара заявки проверяется отдельно",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            867,
            "    private List<LessonResponse> authorizedTicketLessons(ExcuseTicket ticket, long teacherId) {"
          ],
          [
            868,
            "        List<Long> lessonIds = ticket.getLessonIds() == null ? List.of() : ticket.getLessonIds();"
          ],
          [
            869,
            "        List<LessonResponse> result = new ArrayList<>();"
          ],
          [
            870,
            "        for (Long lessonId : lessonIds.stream().filter(Objects::nonNull).distinct().toList()) {"
          ],
          [
            871,
            "            try {"
          ],
          [
            872,
            "                LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);"
          ],
          [
            873,
            "                reportService.authorizeTeacherLesson(lesson, teacherId);"
          ],
          [
            874,
            "                result.add(lesson);"
          ],
          [
            875,
            "            } catch (AccessDeniedException"
          ],
          [
            876,
            "                    | ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException"
          ],
          [
            877,
            "                            ignored) {"
          ],
          [
            878,
            "                // Project a mixed ticket only over the teacher's current active groups."
          ],
          [
            879,
            "            }"
          ],
          [
            880,
            "        }"
          ],
          [
            881,
            "        return result;"
          ]
        ]
      },
      {
        "title": "Пара сверяется с текущим правом на группу и предмет",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            2105,
            "        try {"
          ],
          [
            2106,
            "            LocalDate.parse(lesson.getDate());"
          ],
          [
            2107,
            "        } catch (RuntimeException invalid) {"
          ],
          [
            2108,
            "            throw new AccessDeniedException(\"Teacher lesson date unavailable\");"
          ],
          [
            2109,
            "        }"
          ],
          [
            2110,
            "        authorizeTeacherSubject(lesson.getGroupId(), lesson.getSubjectId(), teacherId);"
          ],
          [
            2111,
            "    }"
          ],
          [
            2112,
            ""
          ],
          [
            2113,
            "    private void authorizeTeacherSubject(long groupId, long subjectId, long teacherId) {"
          ],
          [
            2114,
            "        if (groupId <= 0"
          ],
          [
            2115,
            "                || subjectId <= 0"
          ],
          [
            2116,
            "                || teacherId <= 0"
          ],
          [
            2117,
            "                || !currentTeacherSubjectKeys("
          ],
          [
            2118,
            "                                academicGrpcClient.getCurrentTeacherSubjects(teacherId))"
          ],
          [
            2119,
            "                        .contains(new TeacherStatsSubjectKey(groupId, subjectId))) {"
          ],
          [
            2120,
            "            throw new AccessDeniedException(\"Teacher is not active in this group and subject\");"
          ],
          [
            2121,
            "        }"
          ],
          [
            2122,
            "    }"
          ]
        ]
      }
    ]
  }
]
