import type { StoryOperation } from './story-source'

export const scheduleConstructorOperations: readonly StoryOperation[] = [
  {
    "id": "schedule-constructor-weeks",
    "title": "Переключить учебную неделю",
    "description": "В конструкторе староста переключает недели «1» и «2». Сервер считает чётность по номеру недели ISO. Чётная ISO-неделя — «1» (ODD в API), нечётная — «2» (EVEN). Номер недели с начала семестра вычисляется отдельно. Если в форме выбраны обе недели, шаблон получает ALL и показывается на обеих вкладках.",
    "frames": [
      {
        "title": "Вкладки используют варианты с сервера",
        "path": "frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue",
        "language": "typescript",
        "lines": [
          [
            280,
            "const parityOptions = computed("
          ],
          [
            281,
            "  () => weekPolicy.value?.weekTypes.filter((option) => option.parityNumber !== null) ?? [],"
          ],
          [
            282,
            ");"
          ],
          [
            283,
            "const weekType = computed<ConstructorWeek>({"
          ],
          [
            284,
            "  get: () => (selectedWeekTypes.value.length === 1 ? selectedWeekTypes.value[0]! : 'ALL'),"
          ],
          [
            285,
            "  set: (value) => {"
          ],
          [
            286,
            "    selectedWeekTypes.value = value === 'ALL' ? ['ODD', 'EVEN'] : [value];"
          ],
          [
            287,
            "  },"
          ],
          [
            2355,
            "        <div"
          ],
          [
            2356,
            "          class=\"headman-schedule__weeks\""
          ],
          [
            2357,
            "          aria-label=\"Учебная неделя\""
          ],
          [
            2358,
            "        >"
          ],
          [
            2359,
            "          <button"
          ],
          [
            2360,
            "            v-for=\"option in parityOptions\""
          ],
          [
            2361,
            "            :key=\"option.weekType\""
          ],
          [
            2362,
            "            type=\"button\""
          ],
          [
            2363,
            "            :aria-pressed=\"constructorWeek === option.weekType\""
          ],
          [
            2364,
            "            @click=\"constructorWeek = option.weekType\""
          ],
          [
            2365,
            "          >"
          ],
          [
            2366,
            "            {{ option.label }}"
          ],
          [
            2367,
            "          </button>"
          ],
          [
            2368,
            "        </div>"
          ]
        ]
      },
      {
        "title": "Еженедельный шаблон виден на обеих вкладках",
        "path": "frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue",
        "language": "typescript",
        "lines": [
          [
            933,
            "const visibleDayItems = computed(() =>"
          ],
          [
            934,
            "  dayItems.value.filter("
          ],
          [
            935,
            "    (item) => item.weekType === 'ALL' || item.weekType === constructorWeek.value,"
          ],
          [
            936,
            "  ),"
          ],
          [
            937,
            ");"
          ]
        ]
      },
      {
        "title": "ISO-неделя и номер недели семестра",
        "path": "services/shared/shared-web-api/src/main/java/ru/rutcampustrack/shared/web/time/AcademicWeekCalendar.java",
        "language": "java",
        "lines": [
          [
            12,
            "    public enum Parity {"
          ],
          [
            13,
            "        ISO_EVEN(1, \"1\"),"
          ],
          [
            14,
            "        ISO_ODD(2, \"2\");"
          ],
          [
            33,
            "    public static int isoWeek(LocalDate date) {"
          ],
          [
            34,
            "        return date.get(WeekFields.ISO.weekOfWeekBasedYear());"
          ],
          [
            35,
            "    }"
          ],
          [
            36,
            ""
          ],
          [
            37,
            "    public static Parity parity(LocalDate date) {"
          ],
          [
            38,
            "        return isoWeek(date) % 2 == 0 ? Parity.ISO_EVEN : Parity.ISO_ODD;"
          ],
          [
            39,
            "    }"
          ],
          [
            40,
            ""
          ],
          [
            41,
            "    public static LocalDate monday(LocalDate date) {"
          ],
          [
            42,
            "        return date.with(DayOfWeek.MONDAY);"
          ],
          [
            43,
            "    }"
          ],
          [
            44,
            ""
          ],
          [
            45,
            "    /** The Monday-containing first partial semester week is week one. */"
          ],
          [
            46,
            "    public static int weekOfSemester(LocalDate semesterStart, LocalDate date) {"
          ],
          [
            47,
            "        long days = ChronoUnit.DAYS.between(monday(semesterStart), monday(date));"
          ],
          [
            48,
            "        return (int) (days / 7L) + 1;"
          ]
        ]
      },
      {
        "title": "Варианты 1 и 2 отображаются как ODD и EVEN в API",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/week/AcademicWeekPolicy.java",
        "language": "java",
        "lines": [
          [
            13,
            "    private static final WeekTypeOption ALL ="
          ],
          [
            14,
            "            new WeekTypeOption(WeekType.ALL, null, \"Каждую неделю\");"
          ],
          [
            15,
            "    private static final WeekTypeOption ISO_EVEN ="
          ],
          [
            16,
            "            new WeekTypeOption("
          ],
          [
            17,
            "                    WeekType.ODD,"
          ],
          [
            18,
            "                    AcademicWeekCalendar.Parity.ISO_EVEN.number(),"
          ],
          [
            19,
            "                    AcademicWeekCalendar.Parity.ISO_EVEN.label());"
          ],
          [
            20,
            "    private static final WeekTypeOption ISO_ODD ="
          ],
          [
            21,
            "            new WeekTypeOption("
          ],
          [
            22,
            "                    WeekType.EVEN,"
          ],
          [
            23,
            "                    AcademicWeekCalendar.Parity.ISO_ODD.number(),"
          ],
          [
            24,
            "                    AcademicWeekCalendar.Parity.ISO_ODD.label());"
          ],
          [
            25,
            "    private static final List<WeekTypeOption> CATALOG = List.of(ALL, ISO_EVEN, ISO_ODD);"
          ],
          [
            47,
            "    private static WeekTypeOption option(LocalDate date) {"
          ],
          [
            48,
            "        return switch (AcademicWeekCalendar.parity(date)) {"
          ],
          [
            49,
            "            case ISO_EVEN -> ISO_EVEN;"
          ],
          [
            50,
            "            case ISO_ODD -> ISO_ODD;"
          ],
          [
            51,
            "        };"
          ],
          [
            52,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-constructor-create",
    "title": "Собрать шаблон пары",
    "description": "Форма передаёт назначение преподавателя, группу, предмет, семестр, день, номер и время пары, неделю и при наличии аудиторию. POST /api/schedule/items получает UUID в Idempotency-Key. Schedule проверяет полномочия, активные группу и семестр, согласованность назначения и параметры слота. Время сверяется с номером пары.",
    "frames": [
      {
        "title": "Поля формы и выбор хотя бы одной недели",
        "path": "frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1102,
            "  return {"
          ],
          [
            1103,
            "    assignmentId: selected.id,"
          ],
          [
            1104,
            "    groupId: props.groupId,"
          ],
          [
            1105,
            "    subjectId: selected.subjectId,"
          ],
          [
            1106,
            "    semesterId: currentSemester.id,"
          ],
          [
            1107,
            "    dayOfWeek: editorDay.value,"
          ],
          [
            1108,
            "    lessonNumber: slot.lessonNumber,"
          ],
          [
            1109,
            "    startTime: `${startTime.value}:00`,"
          ],
          [
            1110,
            "    endTime: `${endTime.value}:00`,"
          ],
          [
            1111,
            "    weekType: weekType.value,"
          ],
          [
            1112,
            "    ...(room.value.trim() ? { room: room.value.trim() } : {}),"
          ],
          [
            1113,
            "  };"
          ],
          [
            1127,
            "  if (!selectedWeekTypes.value.length) {"
          ],
          [
            1128,
            "    formError.value = 'Выбери хотя бы одну неделю.';"
          ],
          [
            1129,
            "    return;"
          ]
        ]
      },
      {
        "title": "POST /api/schedule/items с ключом команды",
        "path": "frontends/mobile-core/src/features/schedule/headman-schedule-client.ts",
        "language": "typescript",
        "lines": [
          [
            339,
            "  async createScheduleItem("
          ],
          [
            340,
            "    input: HeadmanScheduleCreateInput,"
          ],
          [
            341,
            "    idempotencyKey: string,"
          ],
          [
            342,
            "  ): Promise<HeadmanRecurringCreateReceipt> {"
          ],
          [
            343,
            "    validateOneOffKey(idempotencyKey)"
          ],
          [
            344,
            "    const item = scheduleItem("
          ],
          [
            345,
            "      await this.request<unknown>('/api/schedule/items', {"
          ],
          [
            346,
            "        method: 'POST',"
          ],
          [
            347,
            "        headers: { 'Idempotency-Key': idempotencyKey },"
          ],
          [
            348,
            "        body: JSON.stringify(input),"
          ],
          [
            349,
            "      }),"
          ],
          [
            350,
            "    )"
          ]
        ]
      },
      {
        "title": "Запрос на шаблон первой недели",
        "language": "json",
        "example": [
          "{",
          "  \"assignmentId\": 501,",
          "  \"groupId\": 42,",
          "  \"subjectId\": 18,",
          "  \"semesterId\": 3,",
          "  \"dayOfWeek\": 1,",
          "  \"lessonNumber\": 2,",
          "  \"startTime\": \"10:05:00\",",
          "  \"endTime\": \"11:25:00\",",
          "  \"weekType\": \"ODD\",",
          "  \"room\": \"А-405\"",
          "}"
        ]
      },
      {
        "title": "Доступ старосты сверяется с Academic",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinator.java",
        "language": "java",
        "lines": [
          [
            253,
            "    private void requireHeadmanForGroup(Long targetGroupId) {"
          ],
          [
            254,
            "        UserRole role = requestContext.getRole();"
          ],
          [
            255,
            "        if (role == UserRole.ADMIN) return;"
          ],
          [
            256,
            "        if (!requestContext.isHeadman()) {"
          ],
          [
            257,
            "            throw new AccessDeniedException(\"Only headman or admin can perform this action\");"
          ],
          [
            258,
            "        }"
          ],
          [
            259,
            "        Long actor = requestContext.getUserId();"
          ],
          [
            260,
            "        if (actor == null || !academicGrpcClient.isHeadman(actor, targetGroupId)) {"
          ],
          [
            261,
            "            throw new AccessDeniedException(\"You are not headman of group \" + targetGroupId);"
          ],
          [
            262,
            "        }"
          ],
          [
            263,
            "    }"
          ]
        ]
      },
      {
        "title": "Создание проверяет группу, семестр и назначение",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinator.java",
        "language": "java",
        "lines": [
          [
            75,
            "        requireHeadmanForGroup(request.groupId());"
          ],
          [
            84,
            "        GroupResponse group = academicGrpcClient.validateGroup(request.groupId());"
          ],
          [
            85,
            "        if (group == null || group.getId() <= 0 || group.getId() != request.groupId()) {"
          ],
          [
            86,
            "            throw new RecurringProtocolConflictException("
          ],
          [
            87,
            "                    \"group authority response is inconsistent\");"
          ],
          [
            88,
            "        }"
          ],
          [
            89,
            "        if (!group.getIsActive()) {"
          ],
          [
            90,
            "            throw new RecurringProtocolConflictException(\"group is inactive\");"
          ],
          [
            91,
            "        }"
          ],
          [
            92,
            "        if (request.subjectId() == null || request.semesterId() == null) {"
          ],
          [
            93,
            "            throw new RecurringProtocolConflictException(\"subjectId and semesterId are required\");"
          ],
          [
            94,
            "        }"
          ],
          [
            95,
            "        SemesterResponse semester = academicGrpcClient.getActiveSemester();"
          ],
          [
            96,
            "        if (semester == null || semester.getId() <= 0 || semester.getId() != request.semesterId()) {"
          ],
          [
            97,
            "            throw new RecurringProtocolConflictException(\"semester is not active\");"
          ],
          [
            98,
            "        }"
          ],
          [
            99,
            "        List<AssignmentInfo> assignments ="
          ],
          [
            100,
            "                academicGrpcClient.getAssignmentsByIds(List.of(request.assignmentId()));"
          ],
          [
            101,
            "        if (assignments == null"
          ],
          [
            102,
            "                || assignments.size() != 1"
          ],
          [
            103,
            "                || assignments.get(0) == null"
          ],
          [
            104,
            "                || assignments.get(0).getId() != request.assignmentId()) {"
          ],
          [
            105,
            "            throw new RecurringProtocolConflictException("
          ],
          [
            106,
            "                    \"assignment authority response is incomplete\");"
          ],
          [
            107,
            "        }"
          ],
          [
            108,
            "        AssignmentInfo raw = assignments.get(0);"
          ],
          [
            109,
            "        RecurringAssignmentAuthority authority = toAuthority(raw);"
          ],
          [
            110,
            "        assertRequestMatches(request, authority);"
          ]
        ]
      },
      {
        "title": "Канонические времена и проверка номера слота",
        "path": "services/schedule-service/schedule-api-contract/src/main/java/ru/rutcampustrack/schedule/contract/enums/LessonSlot.java",
        "language": "java",
        "lines": [
          [
            7,
            "public enum LessonSlot {"
          ],
          [
            8,
            "    FIRST(1, \"08:30\", \"09:50\"),"
          ],
          [
            9,
            "    SECOND(2, \"10:05\", \"11:25\"),"
          ],
          [
            10,
            "    THIRD(3, \"11:40\", \"13:00\"),"
          ],
          [
            11,
            "    FOURTH(4, \"13:45\", \"15:05\"),"
          ],
          [
            12,
            "    FIFTH(5, \"15:20\", \"16:40\"),"
          ],
          [
            13,
            "    SIXTH(6, \"16:55\", \"18:15\"),"
          ],
          [
            14,
            "    SEVENTH(7, \"18:30\", \"19:50\"),"
          ],
          [
            15,
            "    EIGHTH(8, \"20:00\", \"21:20\");"
          ],
          [
            45,
            "    public static LessonSlot forNumber(Number number) {"
          ],
          [
            46,
            "        if (number == null"
          ],
          [
            47,
            "                || number.intValue() < 1"
          ],
          [
            48,
            "                || number.intValue() > ORDERED.size()"
          ],
          [
            49,
            "                || number.doubleValue() != number.intValue()) {"
          ],
          [
            50,
            "            throw new ValidationException(\"Выбери номер пары от 1 до 8\");"
          ],
          [
            51,
            "        }"
          ],
          [
            52,
            "        return ORDERED.get(number.intValue() - 1);"
          ],
          [
            53,
            "    }"
          ],
          [
            54,
            ""
          ],
          [
            55,
            "    /** Optional legacy fields are accepted only as an exact pair matching this slot. */"
          ],
          [
            56,
            "    public void validateTimes(LocalTime start, LocalTime end) {"
          ],
          [
            57,
            "        if (start == null && end == null) return;"
          ],
          [
            58,
            "        if (start == null || end == null) {"
          ],
          [
            59,
            "            throw new ValidationException(\"Время начала и окончания пары нужно передать вместе\");"
          ],
          [
            60,
            "        }"
          ],
          [
            61,
            "        if (!startTime.equals(start) || !endTime.equals(end)) {"
          ],
          [
            62,
            "            throw new ValidationException("
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-constructor-materialize",
    "title": "Создать пары на даты семестра",
    "description": "При сохранении ScheduleItemService передаёт команду в RecurringScheduleItemCoordinator. Для нового шаблона Writer создаёт пары сразу в транзакции: проходит даты от начала до конца семестра включительно, фильтрует по дню недели и чётности. На каждую дату создаёт occurrence и запись пары. Преподаватель и назначение указываются, когда они действуют на эту дату.",
    "frames": [
      {
        "title": "Текущий путь от ScheduleItemService к Writer",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemService.java",
        "language": "java",
        "lines": [
          [
            114,
            "    public ScheduleItem createScheduleItem(CreateScheduleItemRequest request, UUID idempotencyKey) {"
          ],
          [
            115,
            "        if (writeAdmission != null) writeAdmission.requireEnabled();"
          ],
          [
            116,
            "        if (recurringCoordinator == null) {"
          ],
          [
            117,
            "            requireHeadmanForGroup(request.groupId());"
          ],
          [
            118,
            "            throw new RecurringLifecycleNotReadyException(\"recurring coordinator unavailable\");"
          ],
          [
            119,
            "        }"
          ],
          [
            120,
            "        return recurringCoordinator.create(request, idempotencyKey);"
          ],
          [
            121,
            "    }"
          ]
        ]
      },
      {
        "title": "Координатор передаёт даты семестра в Writer",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemCoordinator.java",
        "language": "java",
        "lines": [
          [
            128,
            "            var prepared = archiveFence.prepareBusinessWrite(request.semesterId());"
          ],
          [
            129,
            "            long actor = requireActor();"
          ],
          [
            130,
            "            // Resolve an accepted key before inspecting the current occupants of its old slot."
          ],
          [
            131,
            "            result = writer.findAcceptedCreate(actor, requestKey, RecurringScheduleItemWriter.payloadHash(request));"
          ],
          [
            132,
            "            if (result == null) {"
          ],
          [
            133,
            "                Long inactive = writer.inactiveTemplateId(request);"
          ],
          [
            134,
            "                result = inactive == null"
          ],
          [
            135,
            "                        ? writer.writePrepared(request, requestKey, actor, authority,"
          ],
          [
            136,
            "                                semesterStart, semesterEnd, prepared)"
          ],
          [
            137,
            "                        : lifecycle.reactivate(inactive, request, requestKey, actor, authorities(inactive),"
          ],
          [
            138,
            "                                semesterStart, semesterEnd, prepared);"
          ],
          [
            139,
            "            }"
          ]
        ]
      },
      {
        "title": "День недели и чётность в границах семестра",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringDateCalculator.java",
        "language": "java",
        "lines": [
          [
            21,
            "        LocalDate from = semesterStart;"
          ],
          [
            22,
            "        LocalDate exclusiveEnd = semesterEnd.plusDays(1);"
          ],
          [
            23,
            "        if (!from.isBefore(exclusiveEnd)) return List.of();"
          ],
          [
            24,
            ""
          ],
          [
            25,
            "        List<LocalDate> dates = new ArrayList<>();"
          ],
          [
            26,
            "        for (LocalDate date = from; date.isBefore(exclusiveEnd); date = date.plusDays(1)) {"
          ],
          [
            27,
            "            if (date.getDayOfWeek().getValue() != request.dayOfWeek()) continue;"
          ],
          [
            28,
            "            if (request.weekType() == WeekType.ALL) {"
          ],
          [
            29,
            "                dates.add(date);"
          ],
          [
            30,
            "                continue;"
          ],
          [
            31,
            "            }"
          ],
          [
            32,
            "            if (AcademicWeekPolicy.weekType(date) == request.weekType()) dates.add(date);"
          ],
          [
            33,
            "        }"
          ],
          [
            34,
            "        return List.copyOf(dates);"
          ]
        ]
      },
      {
        "title": "Occurrence связывается с созданной парой",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemWriter.java",
        "language": "java",
        "lines": [
          [
            166,
            "            UUID materialization = semesterMaterialization.begin("
          ],
          [
            167,
            "                    scheduleItemId, actorId, requestKey, authority, semesterStart, semesterEnd);"
          ],
          [
            168,
            "            for (LocalDate date : dates) {"
          ],
          [
            169,
            "                long occurrenceId = insertOccurrence(scheduleItemId, date, authority, createdAt);"
          ],
          [
            170,
            "                long lessonId ="
          ],
          [
            171,
            "                        insertPhysicalLesson("
          ],
          [
            172,
            "                                scheduleItemId,"
          ],
          [
            173,
            "                                occurrenceId,"
          ],
          [
            174,
            "                                date,"
          ],
          [
            175,
            "                                request,"
          ],
          [
            176,
            "                                authority,"
          ],
          [
            177,
            "                                LessonStatus.PLANNED,"
          ],
          [
            178,
            "                                createdAt,"
          ],
          [
            179,
            "                                null);"
          ],
          [
            180,
            "                jdbc.update("
          ],
          [
            181,
            "                        \"UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?\","
          ],
          [
            182,
            "                        lessonId,"
          ],
          [
            183,
            "                        occurrenceId);"
          ]
        ]
      },
      {
        "title": "Обе записи создаёт канонический материализатор",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemWriter.java",
        "language": "java",
        "lines": [
          [
            732,
            "    long insertOccurrence("
          ],
          [
            733,
            "            long scheduleItemId,"
          ],
          [
            734,
            "            LocalDate date,"
          ],
          [
            735,
            "            RecurringAssignmentAuthority authority,"
          ],
          [
            736,
            "            OffsetDateTime createdAt) {"
          ],
          [
            737,
            "        if (semesterMaterialization != null && semesterMaterialization.admitted())"
          ],
          [
            738,
            "            return semesterMaterialization.insertOccurrence(scheduleItemId, date, authority, createdAt);"
          ],
          [
            764,
            "    long insertPhysicalLesson("
          ],
          [
            765,
            "            long scheduleItemId,"
          ],
          [
            766,
            "            long occurrenceId,"
          ],
          [
            767,
            "            LocalDate date,"
          ],
          [
            768,
            "            CreateScheduleItemRequest request,"
          ],
          [
            769,
            "            RecurringAssignmentAuthority authority,"
          ],
          [
            770,
            "            LessonStatus status,"
          ],
          [
            771,
            "            OffsetDateTime createdAt,"
          ],
          [
            772,
            "            Long closedAtEpochMillis) {"
          ],
          [
            773,
            "        if (semesterMaterialization != null && semesterMaterialization.admitted())"
          ],
          [
            774,
            "            return semesterMaterialization.insertPhysicalLesson(occurrenceId, createdAt);"
          ]
        ]
      },
      {
        "title": "Назначение на конкретную дату и запись occurrence",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/constructor/SemesterMaterializationWriter.java",
        "language": "java",
        "lines": [
          [
            94,
            "        var item = jdbc.queryForMap(\"SELECT *,week_type::text AS week_type FROM schedule_items WHERE id=?\", itemId);"
          ],
          [
            95,
            "        var fence = jdbc.queryForMap("
          ],
          [
            96,
            "                \"SELECT valid_from,creation_cap_until_exclusive,assigned_teacher_id FROM schedule_assignment_fences WHERE assignment_id=?\","
          ],
          [
            97,
            "                authority.assignmentId());"
          ],
          [
            98,
            "        boolean assigned = !date.isBefore(date(fence.get(\"valid_from\")))"
          ],
          [
            99,
            "                && date.isBefore(date(fence.get(\"creation_cap_until_exclusive\")));"
          ],
          [
            100,
            "        Long assignment = assigned ? authority.assignmentId() : null;"
          ],
          [
            101,
            "        Long teacher = assigned ? ((Number) fence.get(\"assigned_teacher_id\")).longValue() : null;"
          ],
          [
            102,
            "        long occurrence = nextId(\"lesson_occurrences\"), lesson = nextId(\"lessons\");"
          ],
          [
            120,
            "        jdbc.update("
          ],
          [
            121,
            "                \"\"\""
          ],
          [
            122,
            "                INSERT INTO lesson_occurrences"
          ],
          [
            123,
            "                    (id,schedule_item_id,occurrence_date,assignment_id,group_id,subject_id,semester_id,"
          ],
          [
            124,
            "                     assigned_teacher_id,lesson_type,generation,revision,created_at)"
          ],
          [
            125,
            "                OVERRIDING SYSTEM VALUE VALUES (?,?,?,?,?,?,?,?,?,1,1,?)"
          ],
          [
            126,
            "                \"\"\","
          ],
          [
            127,
            "                occurrence, itemId, date, assignment, authority.groupId(), authority.subjectId(),"
          ],
          [
            128,
            "                authority.semesterId(), teacher, authority.lessonType(), at);"
          ],
          [
            129,
            "        return occurrence;"
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-constructor-historical",
    "title": "Обработать уже прошедшие даты",
    "description": "При создании расписания посреди семестра Schedule использует московское время. Будущая пара получает planned, начавшаяся до момента закрытия — active, а начиная с конца пары плюс пять минут — closed. Для созданной задним числом закрытой пары не публикуются обычные сигналы начала, закрытия и напоминания. Данные для обработки посещаемости передаются в Attendance отдельными сохранёнными пакетами.",
    "frames": [
      {
        "title": "Начальное состояние зависит от даты и времени",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/constructor/SemesterMaterializationWriter.java",
        "language": "java",
        "lines": [
          [
            137,
            "        LocalDate day = date(origin.get(\"occurrence_date\"));"
          ],
          [
            138,
            "        LocalTime start = time(origin.get(\"start_time\")), end = time(origin.get(\"end_time\"));"
          ],
          [
            139,
            "        LocalDateTime now = at.atZoneSameInstant(MOSCOW).toLocalDateTime();"
          ],
          [
            140,
            "        String status = !day.atTime(end).plusMinutes(5).isAfter(now) ? \"closed\""
          ],
          [
            141,
            "                : !day.atTime(start).isAfter(now) ? \"active\" : \"planned\";"
          ],
          [
            142,
            "        jdbc.update("
          ],
          [
            143,
            "                \"\"\""
          ],
          [
            144,
            "                INSERT INTO lessons"
          ],
          [
            145,
            "                    (id,schedule_item_id,occurrence_id,assignment_id,group_id,subject_id,semester_id,"
          ],
          [
            146,
            "                     assigned_teacher_id,lesson_type,lesson_number,day_of_week,start_time,end_time,"
          ],
          [
            147,
            "                    room_snapshot,week_type_snapshot,generation,revision,date,status,is_geo_blocked,"
          ],
          [
            148,
            "                     created_at,closed_at)"
          ],
          [
            149,
            "                OVERRIDING SYSTEM VALUE VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,1,?,?::lesson_status,FALSE,?,?)"
          ],
          [
            150,
            "                \"\"\","
          ],
          [
            151,
            "                origin.get(\"lesson_id\"), origin.get(\"schedule_item_id\"), occurrence,"
          ],
          [
            152,
            "                origin.get(\"initial_assignment_id\"), origin.get(\"group_id\"), origin.get(\"subject_id\"),"
          ],
          [
            153,
            "                origin.get(\"semester_id\"), origin.get(\"initial_teacher_id\"), origin.get(\"lesson_type\"),"
          ],
          [
            154,
            "                origin.get(\"lesson_number\"), day.getDayOfWeek().getValue(), start, end,"
          ],
          [
            155,
            "                origin.get(\"room_snapshot\"), origin.get(\"week_type_snapshot\"), day, status, at,"
          ]
        ]
      },
      {
        "title": "Сигнал начала для active и выборка closed",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/constructor/SemesterMaterializationWriter.java",
        "language": "java",
        "lines": [
          [
            157,
            "        long lesson = ((Number) origin.get(\"lesson_id\")).longValue();"
          ],
          [
            158,
            "        if (\"active\".equals(status)) {"
          ],
          [
            159,
            "            events.publishEvent(new LessonStartedEvent("
          ],
          [
            160,
            "                    this, lesson, ((Number) origin.get(\"group_id\")).longValue(),"
          ],
          [
            161,
            "                    ((Number) origin.get(\"subject_id\")).longValue(),"
          ],
          [
            162,
            "                    ((Number) origin.get(\"lesson_number\")).shortValue(), start, end,"
          ],
          [
            163,
            "                    (String) origin.get(\"room_snapshot\")));"
          ],
          [
            164,
            "        }"
          ],
          [
            165,
            "        // Historical CLOSED has no shared started/closed/reminder publication."
          ],
          [
            166,
            "        return lesson;"
          ],
          [
            167,
            "    }"
          ],
          [
            168,
            ""
          ],
          [
            169,
            "    public void finish(UUID operation) {"
          ],
          [
            170,
            "        requireTransaction();"
          ],
          [
            171,
            "        var scope = jdbc.queryForMap(\"SELECT * FROM constructor_semester_materializations WHERE operation_id=?\", operation);"
          ],
          [
            172,
            "        var historical = jdbc.queryForList("
          ],
          [
            173,
            "                \"\"\""
          ],
          [
            174,
            "                SELECT origin.lesson_id,origin.occurrence_id,origin.snapshot_hash,lesson.revision"
          ],
          [
            175,
            "                  FROM constructor_generation_origins origin JOIN lessons lesson ON lesson.id=origin.lesson_id"
          ],
          [
            176,
            "                 WHERE origin.operation_id=? AND lesson.status='closed' ORDER BY origin.lesson_id"
          ],
          [
            177,
            "                \"\"\", operation);"
          ]
        ]
      },
      {
        "title": "Работа по закрытым парам сохраняется пакетами",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/constructor/SemesterMaterializationWriter.java",
        "language": "java",
        "lines": [
          [
            185,
            "            for (var row : historical.subList(batch * BATCH_SIZE, Math.min(historical.size(), (batch + 1) * BATCH_SIZE))) {"
          ],
          [
            186,
            "                command.addLessons(PastLessonIdentity.newBuilder()"
          ],
          [
            187,
            "                        .setLessonId(((Number) row.get(\"lesson_id\")).longValue())"
          ],
          [
            188,
            "                        .setOccurrenceId(((Number) row.get(\"occurrence_id\")).longValue())"
          ],
          [
            189,
            "                        .setExpectedRevision(((Number) row.get(\"revision\")).longValue())"
          ],
          [
            190,
            "                        .setSnapshotHash(ByteString.copyFrom((byte[]) row.get(\"snapshot_hash\"))));"
          ],
          [
            191,
            "            }"
          ],
          [
            192,
            "            byte[] hash = ConstructorHashes.sha256(command.build().toByteArray());"
          ],
          [
            193,
            "            command.setBatchHash(ByteString.copyFrom(hash));"
          ],
          [
            194,
            "            jdbc.update("
          ],
          [
            195,
            "                    \"INSERT INTO constructor_materialization_work(operation_id,batch_index,command,batch_hash,lesson_count,lesson_ids) VALUES(?,?,?,?,?,?::bigint[])\","
          ],
          [
            196,
            "                    operation, batch, command.build().toByteArray(), hash, command.getLessonsCount(),"
          ],
          [
            197,
            "                    array(command.getLessonsList().stream().map(PastLessonIdentity::getLessonId).toList()));"
          ],
          [
            198,
            "        }"
          ],
          [
            199,
            "        jdbc.update(\"UPDATE constructor_semester_materializations SET state=? WHERE operation_id=?\","
          ],
          [
            200,
            "                batches == 0 ? \"COMPLETED\" : \"PENDING\", operation);"
          ]
        ]
      },
      {
        "title": "Фоновая задача передаёт сохранённый пакет",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/constructor/SemesterMaterializationRecoveryJob.java",
        "language": "java",
        "lines": [
          [
            49,
            "                        || command.getBatchIndex()!=((Number)claimed.get(\"batch_index\")).intValue()"
          ],
          [
            50,
            "                        || command.getGroupId()!=((Number)claimed.get(\"accepted_group\")).longValue()"
          ],
          [
            51,
            "                        || command.getSemesterId()!=((Number)claimed.get(\"accepted_semester\")).longValue()"
          ],
          [
            52,
            "                        || command.getBatchCount()<=command.getBatchIndex() || command.getLessonsCount()<1 || command.getLessonsCount()>128"
          ],
          [
            53,
            "                        || !MessageDigest.isEqual(ConstructorHashes.sha256(command.toBuilder().clearBatchHash().build().toByteArray()),command.getBatchHash().toByteArray())"
          ],
          [
            54,
            "                        || !MessageDigest.isEqual(command.getBatchHash().toByteArray(),(byte[])claimed.get(\"batch_hash\")))"
          ],
          [
            55,
            "                    throw new IllegalStateException(\"Invalid retained materialization identity\");"
          ],
          [
            56,
            "                var admittedIds=jdbc.queryForList(\"SELECT unnest(lesson_ids) FROM constructor_materialization_work WHERE operation_id=? AND batch_index=?\","
          ],
          [
            57,
            "                        Long.class,claimed.get(\"operation_id\"),claimed.get(\"batch_index\"));"
          ],
          [
            58,
            "                if (!admittedIds.equals(command.getLessonsList().stream().map(l->l.getLessonId()).toList()))"
          ],
          [
            59,
            "                    throw new IllegalStateException(\"Invalid retained materialization lesson set\");"
          ],
          [
            60,
            "                var receipt=clients.materialize(command);"
          ],
          [
            61,
            "                int unavailable=validateReceipt(command,receipt);"
          ],
          [
            62,
            "                transactions.executeWithoutResult(status->settle(claimed,unavailable));"
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-constructor-replay",
    "title": "Повторить запрос без второй серии",
    "description": "Ключ создания принадлежит автору и связан с содержимым команды. Writer повторно проверяет сохранённый результат после получения блокировки: тот же запрос возвращает прежнюю серию, другой запрос с тем же ключом конфликтует. В БД уникальны дата occurrence внутри шаблона и занятый физический слот группы для planned, active и closed.",
    "frames": [
      {
        "title": "Запись выполняется в транзакции",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemWriter.java",
        "language": "java",
        "lines": [
          [
            82,
            "    @Transactional(isolation = Isolation.READ_COMMITTED)"
          ],
          [
            83,
            "    public RecurringCreateResult writePrepared("
          ],
          [
            84,
            "            CreateScheduleItemRequest request,"
          ],
          [
            85,
            "            UUID requestKey,"
          ],
          [
            86,
            "            long actorId,"
          ],
          [
            87,
            "            RecurringAssignmentAuthority authority,"
          ],
          [
            88,
            "            LocalDate semesterStart,"
          ],
          [
            89,
            "            LocalDate semesterEnd,"
          ],
          [
            90,
            "            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {"
          ],
          [
            91,
            "        return writeInternal("
          ],
          [
            92,
            "                request, requestKey, actorId, authority, semesterStart, semesterEnd, prepared);"
          ],
          [
            93,
            "    }"
          ]
        ]
      },
      {
        "title": "Проверка повтора до и после блокировки",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemWriter.java",
        "language": "java",
        "lines": [
          [
            107,
            "        validateInputs(request, authority, semesterStart, semesterEnd);"
          ],
          [
            108,
            "        byte[] payloadHash = payloadHash(request);"
          ],
          [
            109,
            "        RecurringCreateResult existingReplay = replayIfPresent(actorId, requestKey, payloadHash);"
          ],
          [
            110,
            "        if (existingReplay != null) {"
          ],
          [
            111,
            "            return existingReplay;"
          ],
          [
            112,
            "        }"
          ],
          [
            113,
            "        request = request.withCanonicalTimes();"
          ],
          [
            114,
            "        if (prepared == null) archiveWriteFence.lockForBusinessWrite(authority.semesterId());"
          ],
          [
            115,
            "        else archiveWriteFence.lockPreparedBusinessWrite(prepared);"
          ],
          [
            116,
            "        LocalDate fenceCap;"
          ],
          [
            117,
            "        try {"
          ],
          [
            118,
            "            fenceCap = lockOrInstallFence(authority);"
          ],
          [
            119,
            "        } catch (DataIntegrityViolationException ex) {"
          ],
          [
            120,
            "            throw new RecurringProtocolConflictException(\"assignment fence authority is invalid\");"
          ],
          [
            121,
            "        }"
          ],
          [
            122,
            ""
          ],
          [
            123,
            "        // A concurrent identical request can have committed while this"
          ],
          [
            124,
            "        // transaction waited on the assignment fence. Re-read the replay"
          ],
          [
            125,
            "        // ledger after that wait before inserting another origin."
          ],
          [
            126,
            "        existingReplay = replayIfPresent(actorId, requestKey, payloadHash);"
          ],
          [
            127,
            "        if (existingReplay != null) {"
          ],
          [
            128,
            "            return existingReplay;"
          ],
          [
            129,
            "        }"
          ]
        ]
      },
      {
        "title": "Изменённое содержимое с тем же ключом конфликтует",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemWriter.java",
        "language": "java",
        "lines": [
          [
            407,
            "                       generated_from, generated_until, payload_hash"
          ],
          [
            408,
            "                  FROM schedule_recurring_create_replay"
          ],
          [
            409,
            "                 WHERE actor_id = ? AND request_key = ?"
          ],
          [
            410,
            "                \"\"\" + (lock ? \" FOR UPDATE\" : \"\"),"
          ],
          [
            411,
            "                        actorId,"
          ],
          [
            412,
            "                        requestKey);"
          ],
          [
            413,
            "        if (replayRows.isEmpty()) {"
          ],
          [
            414,
            "            return null;"
          ],
          [
            415,
            "        }"
          ],
          [
            416,
            "        Map<String, Object> replay = replayRows.get(0);"
          ],
          [
            417,
            "        byte[] storedHash = (byte[]) replay.get(\"payload_hash\");"
          ],
          [
            418,
            "        if (!MessageDigest.isEqual(storedHash, payloadHash)) {"
          ],
          [
            419,
            "            throw new ConflictException("
          ],
          [
            420,
            "                    \"Idempotency-Key was already used for a different recurring request\");"
          ],
          [
            421,
            "        }"
          ],
          [
            422,
            "        return resultFrom(replay);"
          ],
          [
            423,
            "    }"
          ]
        ]
      },
      {
        "title": "Ограничения на occurrence и физический слот",
        "path": "services/schedule-service/schedule-app/src/main/resources/db/migration/V17__student_occurrence_homework_binding.sql",
        "language": "sql",
        "lines": [
          [
            78,
            "CREATE UNIQUE INDEX uq_lesson_occurrences_recurring_origin"
          ],
          [
            79,
            "    ON lesson_occurrences (schedule_item_id, occurrence_date)"
          ],
          [
            80,
            "    WHERE schedule_item_id IS NOT NULL;"
          ],
          [
            139,
            "CREATE UNIQUE INDEX uq_lessons_included_physical_slot"
          ],
          [
            140,
            "    ON lessons (group_id, date, lesson_number)"
          ],
          [
            141,
            "    WHERE status IN ('planned', 'active', 'closed');"
          ],
          [
            142,
            ""
          ],
          [
            143,
            "CREATE UNIQUE INDEX uq_lessons_included_occurrence"
          ],
          [
            144,
            "    ON lessons (occurrence_id)"
          ],
          [
            145,
            "    WHERE status IN ('planned', 'active', 'closed');"
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-constructor-preview",
    "title": "Рассчитать изменение и сверить версию",
    "description": "При сохранении правки экран сначала запрашивает предварительный расчёт. Ответ содержит revision и числа изменяемых, снимаемых, восстанавливаемых и добавляемых пар. Клиент сохраняет ключ и передаёт revision как expectedRevision в PUT. Schedule блокирует серию и заново рассчитывает план. Если серия изменилась, требуется новый расчёт. Предмет серии остаётся прежним, а день, номер, чётность и аудиторию можно менять в пределах проверок.",
    "frames": [
      {
        "title": "Предварительный расчёт и проверка ответа",
        "path": "frontends/mobile-core/src/features/schedule/headman-schedule-client.ts",
        "language": "typescript",
        "lines": [
          [
            387,
            "  async previewScheduleItem("
          ],
          [
            388,
            "    id: number,"
          ],
          [
            389,
            "    input: Omit<HeadmanScheduleUpdateInput, 'expectedRevision'> | null,"
          ],
          [
            390,
            "  ): Promise<HeadmanLifecyclePreview> {"
          ],
          [
            391,
            "    assertNumericId(id, 'id')"
          ],
          [
            392,
            "    const value = await this.request<HeadmanLifecyclePreview>("
          ],
          [
            393,
            "      `/api/schedule/items/${id}/lifecycle-preview?delete=${input === null}`,"
          ],
          [
            394,
            "      {"
          ],
          [
            395,
            "        method: 'POST',"
          ],
          [
            396,
            "        ...(input === null ? {} : { body: JSON.stringify(input) }),"
          ],
          [
            397,
            "      },"
          ],
          [
            398,
            "    )"
          ],
          [
            399,
            "    if ("
          ],
          [
            400,
            "      !/^[0-9a-f]{64}$/.test(value.revision) ||"
          ],
          [
            401,
            "      [value.updatedCount, value.removedCount, value.restoredCount, value.createdCount].some("
          ],
          [
            402,
            "        (count) => !Number.isSafeInteger(count) || count < 0,"
          ],
          [
            403,
            "      )"
          ],
          [
            404,
            "    ) {"
          ],
          [
            405,
            "      throw new Error('Сервер не вернул корректный предварительный расчёт.')"
          ],
          [
            406,
            "    }"
          ]
        ]
      },
      {
        "title": "Клиент сохраняет ключ и ревизию расчёта",
        "path": "frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1497,
            "    if (!intent) {"
          ],
          [
            1498,
            "      if (!preview || typeof crypto === 'undefined' || typeof crypto.randomUUID !== 'function')"
          ],
          [
            1499,
            "        throw new Error('Для сохранения нужен UUID запроса. Обнови браузер.');"
          ],
          [
            1500,
            "      intent = {"
          ],
          [
            1501,
            "        owner,"
          ],
          [
            1502,
            "        target: Object.freeze({ ...target }),"
          ],
          [
            1503,
            "        key: crypto.randomUUID(),"
          ],
          [
            1504,
            "        action: preview.action,"
          ],
          [
            1505,
            "        input: preview.input"
          ],
          [
            1506,
            "          ? Object.freeze({ ...preview.input, expectedRevision: preview.result.revision })"
          ],
          [
            1507,
            "          : null,"
          ],
          [
            1508,
            "        preview: Object.freeze({ ...preview.result }),"
          ],
          [
            1509,
            "        rejected: false,"
          ],
          [
            1510,
            "        transferIds: [],"
          ],
          [
            1511,
            "      };"
          ],
          [
            1512,
            "    }"
          ],
          [
            1513,
            "    persistRecurringIntent(sessionStorage, intent);"
          ],
          [
            1514,
            "    recurringIntent.value = intent;"
          ]
        ]
      },
      {
        "title": "PUT /api/schedule/items/{id}",
        "path": "frontends/mobile-core/src/features/schedule/headman-schedule-client.ts",
        "language": "typescript",
        "lines": [
          [
            426,
            "  updateScheduleItem("
          ],
          [
            427,
            "    id: number,"
          ],
          [
            428,
            "    input: Readonly<HeadmanScheduleUpdateInput>,"
          ],
          [
            429,
            "    key: string,"
          ],
          [
            430,
            "  ): Promise<HeadmanScheduleItem> {"
          ],
          [
            431,
            "    assertNumericId(id, 'id')"
          ],
          [
            432,
            "    validateOneOffKey(key)"
          ],
          [
            433,
            "    return this.request<unknown>(`/api/schedule/items/${id}`, {"
          ],
          [
            434,
            "      method: 'PUT',"
          ],
          [
            435,
            "      headers: { 'Idempotency-Key': key },"
          ],
          [
            436,
            "      body: JSON.stringify(input),"
          ],
          [
            437,
            "    }).then((value) => {"
          ],
          [
            438,
            "      // An omitted list cannot prove that all related transfers are known."
          ],
          [
            439,
            "      if (!isRecord(value) || !Array.isArray(value.transfers))"
          ],
          [
            440,
            "        throw new Error('Сервер не подтвердил список переносов серии.')"
          ],
          [
            441,
            "      return scheduleItem(value)"
          ]
        ]
      },
      {
        "title": "Изменение на вторую пару среды",
        "language": "json",
        "example": [
          "{",
          "  \"subjectId\": 18,",
          "  \"dayOfWeek\": 3,",
          "  \"lessonNumber\": 2,",
          "  \"startTime\": \"10:05:00\",",
          "  \"endTime\": \"11:25:00\",",
          "  \"weekType\": \"ODD\",",
          "  \"room\": \"А-410\",",
          "  \"expectedRevision\": \"43a6b75fcb848f235ece5d3516e52a9845ba89fda138c7b26bbbf18e5913c402\"",
          "}"
        ]
      },
      {
        "title": "Серия блокируется, расчёт проверяется заново",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            154,
            "        String action = delete ? \"DELETE\" : \"UPDATE\";"
          ],
          [
            155,
            "        byte[] hash = hash(action + \"|\" + itemId + \"|\" + json(request) + \"|\" + expectedRevision);"
          ],
          [
            156,
            "        try {"
          ],
          [
            157,
            "            requireKey(key, actor);"
          ],
          [
            158,
            "            archiveFence.lockPreparedBusinessWrite(prepared);"
          ],
          [
            159,
            "            lockKey(actor, key);"
          ],
          [
            160,
            "            ScheduleItemResponse replay = replay(actor, key, hash);"
          ],
          [
            161,
            "            if (replay != null) return replay;"
          ],
          [
            162,
            "            List<Map<String, Object>> items = lockSeries(itemId, authorities);"
          ],
          [
            163,
            "            Map<String, Object> requested = item(items, itemId);"
          ],
          [
            164,
            "            validateUpdate(requested, request, delete);"
          ],
          [
            165,
            "            if (!delete && !Boolean.TRUE.equals(requested.get(\"is_active\"))) {"
          ],
          [
            166,
            "                throw new ConflictException("
          ],
          [
            167,
            "                        \"Удалённый слот нужно вернуть через добавление прежней серии\");"
          ],
          [
            168,
            "            }"
          ],
          [
            169,
            "            List<Map<String, Object>> rows = readOccurrences(items, true);"
          ],
          [
            170,
            "            Plan plan = plan(items, rows, request, delete, false, authorities, from, to);"
          ],
          [
            171,
            "            if (!previewRevision(items, rows, request, plan).equals(expectedRevision)) {"
          ],
          [
            172,
            "                throw new ConflictException("
          ],
          [
            173,
            "                        \"Серия изменилась после предварительного расчёта; обнови расчёт\");"
          ],
          [
            174,
            "            }"
          ],
          [
            175,
            "            UUID operation = UUID.randomUUID();"
          ],
          [
            176,
            "            OffsetDateTime now = OffsetDateTime.now(clock);"
          ],
          [
            177,
            "            checkTemplateCollision(items, request, delete);"
          ]
        ]
      },
      {
        "title": "Предмет сохраняется, новый слот проверяется",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            989,
            "    private void validateUpdate("
          ],
          [
            990,
            "            Map<String, Object> current, UpdateScheduleItemRequest request, boolean delete) {"
          ],
          [
            991,
            "        if (delete) return;"
          ],
          [
            992,
            "        if (request == null"
          ],
          [
            993,
            "                || request.subjectId() == null"
          ],
          [
            994,
            "                || request.dayOfWeek() == null"
          ],
          [
            995,
            "                || request.lessonNumber() == null"
          ],
          [
            996,
            "                || request.startTime() == null"
          ],
          [
            997,
            "                || request.endTime() == null"
          ],
          [
            998,
            "                || request.weekType() == null"
          ],
          [
            999,
            "                || number(current.get(\"subject_id\")) != request.subjectId()"
          ],
          [
            1000,
            "                || request.room() != null && request.room().length() > 64) {"
          ],
          [
            1001,
            "            throw new ConflictException(\"Предмет серии нельзя изменить; проверь параметры нового слота\");"
          ],
          [
            1002,
            "        }"
          ],
          [
            1003,
            "        if (placementDiffers(current, request)) {"
          ],
          [
            1004,
            "            if (request.dayOfWeek() < 1 || request.dayOfWeek() > 7) {"
          ],
          [
            1005,
            "                throw new ConflictException(\"Выбери день недели для нового слота\");"
          ],
          [
            1006,
            "            }"
          ],
          [
            1007,
            "            LessonSlot.forNumber(request.lessonNumber()).validateTimes(request.startTime(), request.endTime());"
          ],
          [
            1008,
            "        }"
          ],
          [
            1009,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-constructor-future",
    "title": "Сопоставить будущие пары по неделям",
    "description": "При смене дня или номера будущая пара получает новый слот в той же учебной неделе. Начавшиеся, проведённые, отменённые вручную и отдельно перенесённые пары сохраняют свои существующие слоты. Schedule отклоняет правку, если подходящий слот уже прошёл или занят, перенос ещё не завершён либо будущая пара с действующей привязкой ДЗ осталась без нового слота. При смене чётности допустимые будущие пары могут сниматься или добавляться.",
    "frames": [
      {
        "title": "Прошедшие, начатые и ручные исключения сохраняются",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            479,
            "        Map<LocalDate, NewDate> targets = new TreeMap<>();"
          ],
          [
            480,
            "        for (LocalDate day : desired.values().stream().flatMap(Set::stream).distinct().sorted().toList()) {"
          ],
          [
            481,
            "            if (!day.atTime(request.startTime()).isAfter(now)) continue;"
          ],
          [
            482,
            "            var target = targetForDate(items, desired, day, true, reactivate);"
          ],
          [
            483,
            "            if (target != null) targets.put(weekOf(day), new NewDate(target, day,"
          ],
          [
            484,
            "                    authorities.get(number(target.get(\"assignment_id\"))), createRequest(target, request)));"
          ],
          [
            485,
            "        }"
          ],
          [
            486,
            "        Set<LocalDate> reservedWeeks = new HashSet<>();"
          ],
          [
            487,
            "        Set<LocalDate> retainedDates = new HashSet<>();"
          ],
          [
            488,
            "        Map<LocalDate, Map<String, Object>> future = new TreeMap<>();"
          ],
          [
            489,
            "        Map<LocalDate, Map<String, Object>> cancelled = new HashMap<>();"
          ],
          [
            490,
            "        for (var row : rows) {"
          ],
          [
            491,
            "            LocalDate day = date(row.get(\"occurrence_date\"));"
          ],
          [
            492,
            "            LocalDate week = weekOf(day);"
          ],
          [
            493,
            "            String status = String.valueOf(row.get(\"status\"));"
          ],
          [
            494,
            "            if (row.get(\"manual_source_date\") != null) {"
          ],
          [
            495,
            "                reservedWeeks.add(weekOf(date(row.get(\"manual_source_date\"))));"
          ],
          [
            496,
            "                retainedDates.add(day);"
          ],
          [
            497,
            "            } else if (\"cancelled\".equals(status) && Boolean.TRUE.equals(row.get(\"template_cancelled\"))) {"
          ],
          [
            498,
            "                cancelled.put(day, row);"
          ],
          [
            499,
            "            } else if (!\"planned\".equals(status) || !day.atTime(time(row.get(\"start_time\"))).isAfter(now)) {"
          ],
          [
            500,
            "                // A conducted, started or intentionally cancelled lesson still occupies its natural week."
          ],
          [
            501,
            "                reservedWeeks.add(week);"
          ],
          [
            502,
            "                retainedDates.add(day);"
          ],
          [
            503,
            "            } else if (future.putIfAbsent(week, row) != null) {"
          ],
          [
            504,
            "                throw new ConflictException(\"На одной учебной неделе несколько пар этой серии\");"
          ],
          [
            505,
            "            }"
          ]
        ]
      },
      {
        "title": "Конфликт при незавершённом переносе или отсутствии слота",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            507,
            "        for (var entry : future.entrySet()) {"
          ],
          [
            508,
            "            var row = entry.getValue();"
          ],
          [
            509,
            "            LocalDate source = date(row.get(\"occurrence_date\"));"
          ],
          [
            510,
            "            var target = targets.remove(entry.getKey());"
          ],
          [
            511,
            "            if (Boolean.TRUE.equals(row.get(\"pending_transfer\"))) {"
          ],
          [
            512,
            "                throw new ConflictException(\"Предыдущий перенос пары ещё не завершён\");"
          ],
          [
            513,
            "            }"
          ],
          [
            514,
            "            if (target == null) {"
          ],
          [
            515,
            "                if (plan.structural && (request.weekType() == WeekType.ALL"
          ],
          [
            516,
            "                        || request.weekType() == AcademicWeekPolicy.weekType(source))) {"
          ],
          [
            517,
            "                    throw new ConflictException(\"Для будущей пары нет нового слота в той же учебной неделе: \""
          ],
          [
            518,
            "                            + \"время уже прошло или дата выходит за границы семестра\");"
          ],
          [
            519,
            "                }"
          ],
          [
            520,
            "                if (plan.structural && Boolean.TRUE.equals(row.get(\"live_homework\"))) {"
          ],
          [
            521,
            "                    throw new ConflictException(\"У будущей пары с домашним заданием нет нового слота в этой неделе\");"
          ],
          [
            522,
            "                }"
          ],
          [
            523,
            "                plan.removed.add(row);"
          ],
          [
            524,
            "                continue;"
          ],
          [
            525,
            "            }"
          ],
          [
            526,
            "            if (reservedWeeks.contains(entry.getKey()) || retainedDates.contains(target.date)"
          ],
          [
            527,
            "                    || cancelled.containsKey(target.date) && !source.equals(target.date)) {"
          ],
          [
            528,
            "                throw new ConflictException(\"Новый слот пересекается с сохранённой парой этой серии\");"
          ],
          [
            529,
            "            }"
          ]
        ]
      },
      {
        "title": "Проверка занятого слота группы",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            597,
            "    private void checkPhysicalCollision(NewDate target, long source) {"
          ],
          [
            598,
            "        if (Boolean.TRUE.equals(jdbc.queryForObject("
          ],
          [
            599,
            "                \"\"\""
          ],
          [
            600,
            "                SELECT EXISTS (SELECT 1 FROM lessons WHERE group_id = ? AND date = ? AND lesson_number = ?"
          ],
          [
            601,
            "                    AND status IN ('planned', 'active', 'closed') AND id <> ?)"
          ],
          [
            602,
            "                \"\"\", Boolean.class, target.request.groupId(), target.date, target.request.lessonNumber(), source))) {"
          ],
          [
            603,
            "            throw new ConflictException(\"Новый слот уже занят\");"
          ],
          [
            604,
            "        }"
          ],
          [
            605,
            "    }"
          ]
        ]
      },
      {
        "title": "Аудитория также может изменить будущую пару",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            530,
            "            boolean moving = !source.equals(target.date)"
          ],
          [
            531,
            "                    || number(row.get(\"lesson_number\")) != request.lessonNumber()"
          ],
          [
            532,
            "                    || !time(row.get(\"start_time\")).equals(request.startTime())"
          ],
          [
            533,
            "                    || !time(row.get(\"end_time\")).equals(request.endTime());"
          ],
          [
            534,
            "            if (moving) {"
          ],
          [
            535,
            "                long originId = number(row.get(\"schedule_item_id\"));"
          ],
          [
            536,
            "                var origin = item(items, originId);"
          ],
          [
            537,
            "                boolean admitted = originId == number(target.item.get(\"id\"))"
          ],
          [
            546,
            "                if (!plan.structural || !admitted"
          ],
          [
            547,
            "                        || !retainsDatedAssignment(row, target.date)) {"
          ],
          [
            548,
            "                    throw new ConflictException(\"Новый слот не соответствует действующему назначению пары\");"
          ],
          [
            549,
            "                }"
          ],
          [
            550,
            "                checkPhysicalCollision(target, number(row.get(\"current_lesson_id\")));"
          ],
          [
            551,
            "                plan.updated.add(new ChangedDate(row, target.date, request.lessonNumber(),"
          ],
          [
            552,
            "                        request.startTime(), request.endTime(), request.room()));"
          ],
          [
            553,
            "            } else if (!Objects.equals(row.get(\"room_snapshot\"), request.room())) {"
          ],
          [
            554,
            "                plan.updated.add(new ChangedDate(row, source, request.lessonNumber(),"
          ],
          [
            555,
            "                        request.startTime(), request.endTime(), request.room()));"
          ],
          [
            556,
            "            }"
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-constructor-apply",
    "title": "Дождаться переноса связанных данных",
    "description": "Schedule сохраняет шаблон, принимает отдельные переносы будущих пар и возвращает их в transfers. Даже смена аудитории может запустить этот путь. Новый экземпляр сохраняет occurrence, привязки ДЗ переезжают к новой записи пары, а Attendance и Academic применяют свои части операции. Экран проверяет состояния переносов и перечитывает шаблон. PENDING или ERROR означает, что сохранение слота ещё не подтвердило перенос связанных данных.",
    "frames": [
      {
        "title": "Шаблон и результат применения плана сохраняются",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            178,
            "            for (Map<String, Object> current : items) {"
          ],
          [
            179,
            "                jdbc.update("
          ],
          [
            180,
            "                        \"\"\""
          ],
          [
            181,
            "                        UPDATE schedule_items"
          ],
          [
            182,
            "                           SET day_of_week = ?, lesson_number = ?, start_time = ?, end_time = ?,"
          ],
          [
            183,
            "                               placement_changed_at = CASE WHEN ? THEN ? ELSE placement_changed_at END,"
          ],
          [
            184,
            "                               week_type = CAST(? AS week_type), room = ?, lifecycle_revision = lifecycle_revision + 1,"
          ],
          [
            185,
            "                               is_active = CASE WHEN ? THEN FALSE ELSE is_active END,"
          ],
          [
            186,
            "                               deactivated_at = CASE WHEN ? THEN COALESCE(deactivated_at, ?) ELSE deactivated_at END"
          ],
          [
            187,
            "                         WHERE id = ? AND lifecycle_revision = ?"
          ],
          [
            210,
            "            List<TransferLessonResponse> accepted = apply(plan, operation, actor, prepared, now);"
          ],
          [
            211,
            "            creation.finishLifecycleMaterialization(materialization);"
          ],
          [
            212,
            "            ScheduleItemResponse response = response(itemId);"
          ],
          [
            213,
            "            response.setTransfers(accepted);"
          ],
          [
            214,
            "            response.setLifecycleResult("
          ],
          [
            215,
            "                    counts("
          ],
          [
            216,
            "                            revision("
          ],
          [
            217,
            "                                    readItems(seriesItemIds(itemId)),"
          ],
          [
            218,
            "                                    readOccurrences(items, false)),"
          ],
          [
            219,
            "                            plan));"
          ],
          [
            220,
            "            saveReplay(operation, actor, key, hash, itemId, action, response, now);"
          ],
          [
            221,
            "            return response;"
          ],
          [
            222,
            "        } catch (DataIntegrityViolationException conflict) {"
          ],
          [
            223,
            "            throw new ConflictException("
          ],
          [
            224,
            "                    \"Изменение серии конфликтует с занятым слотом или текущей историей\");"
          ],
          [
            225,
            "        }"
          ]
        ]
      },
      {
        "title": "Для изменившихся пар принимаются переносы",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/recurring/RecurringScheduleItemLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            628,
            "        List<TransferLessonResponse> accepted = new ArrayList<>();"
          ],
          [
            629,
            "        for (Map<String, Object> row : plan.removed) cancel(row, operation, actor, now, plan.structural);"
          ],
          [
            630,
            "        for (ChangedDate change : plan.updated) {"
          ],
          [
            631,
            "            Map<String, Object> row = change.source;"
          ],
          [
            632,
            "            UUID key ="
          ],
          [
            633,
            "                    UUID.nameUUIDFromBytes("
          ],
          [
            634,
            "                            (operation + \":\" + row.get(\"id\")).getBytes(StandardCharsets.UTF_8));"
          ],
          [
            635,
            "            accepted.add("
          ],
          [
            636,
            "                    transfers.transferForTemplate("
          ],
          [
            637,
            "                            number(row.get(\"current_lesson_id\")),"
          ],
          [
            638,
            "                            actor,"
          ],
          [
            639,
            "                            new TransferLessonRequest("
          ],
          [
            640,
            "                                    change.date,"
          ],
          [
            641,
            "                                    (int) change.number,"
          ],
          [
            642,
            "                                    change.start,"
          ],
          [
            643,
            "                                    change.end,"
          ],
          [
            644,
            "                                    change.room,"
          ],
          [
            645,
            "                                    String.valueOf(row.get(\"revision\")),"
          ],
          [
            646,
            "                                    key),"
          ],
          [
            647,
            "                            operation,"
          ],
          [
            648,
            "                            prepared));"
          ]
        ]
      },
      {
        "title": "Привязки ДЗ переходят на новую запись пары",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonTransferWriter.java",
        "language": "java",
        "lines": [
          [
            398,
            "            moveBindings(occurrenceId, sourceLessonId, targetLessonId, bindings);"
          ],
          [
            399,
            "            jdbc.update("
          ],
          [
            400,
            "                    \"\"\""
          ],
          [
            401,
            "                    INSERT INTO lesson_lifecycle_entries"
          ],
          [
            402,
            "                        (occurrence_id, revision, action, lesson_id, target_lesson_id,"
          ],
          [
            403,
            "                         generation, reason, actor_id, occurred_at, transfer_operation_id, template_operation_id)"
          ],
          [
            404,
            "                    VALUES (?, ?, 'TRANSFERRED', ?, ?, ?, NULL, ?, ?, ?, ?)"
          ],
          [
            405,
            "                    \"\"\","
          ],
          [
            406,
            "                    occurrenceId,"
          ],
          [
            407,
            "                    expectedRevision + 1,"
          ],
          [
            408,
            "                    sourceLessonId,"
          ],
          [
            409,
            "                    targetLessonId,"
          ],
          [
            410,
            "                    generation + 1,"
          ],
          [
            1016,
            "            List<Map<String, Object>> bindings) {"
          ],
          [
            1017,
            "        for (Map<String, Object> binding : bindings) {"
          ],
          [
            1018,
            "            int changed ="
          ],
          [
            1019,
            "                    jdbc.update("
          ],
          [
            1020,
            "                            \"\"\""
          ],
          [
            1021,
            "                    UPDATE lesson_homework_bindings"
          ],
          [
            1022,
            "                       SET current_lesson_id = ?, revision = revision + 1, updated_at = NOW()"
          ],
          [
            1023,
            "                     WHERE binding_id = ? AND occurrence_id = ? AND current_lesson_id = ?"
          ],
          [
            1024,
            "                       AND revision = ? AND state IN ('PENDING', 'ACTIVE')"
          ],
          [
            1025,
            "                    \"\"\","
          ],
          [
            1026,
            "                            targetLessonId,"
          ],
          [
            1027,
            "                            number(binding.get(\"binding_id\")),"
          ],
          [
            1028,
            "                            occurrenceId,"
          ],
          [
            1029,
            "                            sourceLessonId,"
          ],
          [
            1030,
            "                            number(binding.get(\"revision\")));"
          ],
          [
            1031,
            "            if (changed != 1)"
          ]
        ]
      },
      {
        "title": "Завершение зависит от Attendance и Academic",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonTransferWriter.java",
        "language": "java",
        "lines": [
          [
            749,
            "        Integer attendanceApplied ="
          ],
          [
            750,
            "                jdbc.queryForObject("
          ],
          [
            751,
            "                        \"\"\""
          ],
          [
            752,
            "                SELECT COUNT(*) FROM lesson_transfer_participant_receipts"
          ],
          [
            753,
            "                 WHERE operation_id = ? AND participant = 'ATTENDANCE'"
          ],
          [
            754,
            "                   AND batch_index = -1 AND result = 'APPLIED'"
          ],
          [
            755,
            "                \"\"\","
          ],
          [
            756,
            "                        Integer.class,"
          ],
          [
            757,
            "                        operation);"
          ],
          [
            758,
            "        Integer academicApplied ="
          ],
          [
            759,
            "                jdbc.queryForObject("
          ],
          [
            760,
            "                        \"\"\""
          ],
          [
            761,
            "                SELECT COUNT(*) FROM lesson_transfer_participant_receipts"
          ],
          [
            762,
            "                 WHERE operation_id = ? AND participant = 'ACADEMIC' AND result = 'APPLIED'"
          ],
          [
            763,
            "                \"\"\","
          ],
          [
            764,
            "                        Integer.class,"
          ],
          [
            765,
            "                        operation);"
          ],
          [
            766,
            "        if (attendanceApplied != null"
          ],
          [
            767,
            "                && attendanceApplied == 1"
          ],
          [
            768,
            "                && academicApplied != null"
          ],
          [
            769,
            "                && academicApplied == number(op.get(\"batch_count\"))) {"
          ],
          [
            770,
            "            int completed ="
          ],
          [
            771,
            "                    jdbc.update("
          ],
          [
            772,
            "                            \"UPDATE lesson_transfer_operations SET state = 'COMPLETED', updated_at = NOW() WHERE operation_id = ? AND state = 'PENDING'\","
          ],
          [
            773,
            "                            operation);"
          ]
        ]
      },
      {
        "title": "Клиент проверяет переносы и перечитывает шаблон",
        "path": "frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1535,
            "      const accepted = await api.updateScheduleItem(target.id, intent.input!, intent.key);"
          ],
          [
            1536,
            "      if (!isCurrent()) return;"
          ],
          [
            1537,
            "      intent = {"
          ],
          [
            1538,
            "        ...intent,"
          ],
          [
            1539,
            "        transferIds: acceptedRecurringTransfers(intent, accepted),"
          ],
          [
            1540,
            "      };"
          ],
          [
            1541,
            "      persistRecurringIntent(sessionStorage, intent);"
          ],
          [
            1542,
            "      recurringIntent.value = intent;"
          ],
          [
            1543,
            "      acknowledged = true;"
          ],
          [
            1544,
            "      const transfers = await Promise.all("
          ],
          [
            1545,
            "        intent.transferIds.map((id) => api.getRecurringTransfer(id)),"
          ],
          [
            1546,
            "      );"
          ],
          [
            1547,
            "      if (!isCurrent()) return;"
          ],
          [
            1548,
            "      if (transfers.some((transfer) => transfer.state !== 'COMPLETED')) {"
          ],
          [
            1549,
            "        formError.value = transfers.some((transfer) => transfer.state === 'ERROR')"
          ],
          [
            1550,
            "          ? 'Слот сохранён, перенос связанных данных завершился с ошибкой. Повтори проверку исходного запроса.'"
          ],
          [
            1551,
            "          : 'Слот сохранён, перенос связанных данных ещё выполняется (PENDING). Повтори проверку исходного запроса.';"
          ],
          [
            1552,
            "        return;"
          ],
          [
            1553,
            "      }"
          ],
          [
            1554,
            "      const readback = await api.getScheduleItem(target.id, true);"
          ],
          [
            1555,
            "      if (!isCurrent()) return;"
          ],
          [
            1556,
            "      currentPlacement = recurringReadback(intent, readback);"
          ]
        ]
      }
    ]
  },
  {
    "id": "schedule-constructor-delete",
    "title": "Проверить, можно ли удалить серию",
    "description": "Полное удаление имеет отдельный расчёт и сохраняется отдельной операцией. Если Schedule, Attendance или Academic находят защищённую историю, серия остаётся. Допущенная команда фиксируется до взаимодействия с участниками. DELETE возвращает 204 после завершения или 202 со статусом операции, который экран может запросить повторно.",
    "frames": [
      {
        "title": "Клиент показывает результат проверки удаления",
        "path": "frontends/mobile-core/src/features/schedule/HeadmanScheduleScreen.vue",
        "language": "typescript",
        "lines": [
          [
            2595,
            "      <p"
          ],
          [
            2596,
            "        v-if=\"removalPreview\""
          ],
          [
            2597,
            "        role=\"status\""
          ],
          [
            2598,
            "      >"
          ],
          [
            2599,
            "        <template v-if=\"canDeleteUnusedSeries(removalPreview)\">"
          ],
          [
            2600,
            "          Пар к удалению: {{ removalPreview.removableCount }}."
          ],
          [
            2601,
            "        </template>"
          ],
          [
            2602,
            "        <template v-else>"
          ],
          [
            2603,
            "          Полное удаление недоступно: {{ seriesDeletionProtection(removalPreview) }}."
          ],
          [
            2604,
            "        </template>"
          ]
        ]
      },
      {
        "title": "Ревизия и защищённая история проверяются до принятия",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/constructor/ConstructorLifecycleCoordinator.java",
        "language": "java",
        "lines": [
          [
            102,
            "        var preview = inspect(item);"
          ],
          [
            103,
            "        store.assertNotLegacyLifecycleReplay(user, key);"
          ],
          [
            104,
            "        if (!revision.equals(preview.revision()))"
          ],
          [
            105,
            "            throw ConstructorLifecycleException.conflict(\"PREVIEW_STALE\");"
          ],
          [
            106,
            "        if (preview.protectedCount() > 0)"
          ],
          [
            107,
            "            throw ConstructorLifecycleException.conflict(\"CONSTRUCTOR_HISTORY_PROTECTED\");"
          ],
          [
            108,
            "        writeAdmission.requireEnabled();"
          ],
          [
            109,
            "        UUID operation = UUID.randomUUID();"
          ],
          [
            110,
            "        var attendance = accepted(preview.attendance(), operation, user, requestHash);"
          ],
          [
            111,
            "        var academicCommand = accepted(preview.academic(), operation, user, requestHash);"
          ],
          [
            112,
            "        // This separate proxied local transaction MUST COMMIT before the first"
          ],
          [
            113,
            "        // participant PREPARE. Recovery uses this immutable source authority."
          ],
          [
            114,
            "        var accepted ="
          ],
          [
            115,
            "                store.accept("
          ],
          [
            116,
            "                        preview.local(),"
          ],
          [
            117,
            "                        operation,"
          ],
          [
            118,
            "                        user,"
          ],
          [
            119,
            "                        key,"
          ],
          [
            120,
            "                        requestHash,"
          ],
          [
            121,
            "                        revision,"
          ],
          [
            122,
            "                        attendance,"
          ],
          [
            123,
            "                        academicCommand,"
          ],
          [
            124,
            "                        preview.prepared());"
          ],
          [
            125,
            "        if (accepted.itemId() != item"
          ],
          [
            126,
            "                || !MessageDigest.isEqual(accepted.requestHash(), requestHash))"
          ],
          [
            127,
            "            throw ConstructorLifecycleException.conflict(\"IDEMPOTENCY_PAYLOAD_MISMATCH\");"
          ],
          [
            128,
            "        return publicResult(resume(accepted.id(), 1500, actor.getInternalToken()));"
          ]
        ]
      },
      {
        "title": "Ответ DELETE и чтение статуса операции",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/item/ScheduleItemController.java",
        "language": "java",
        "lines": [
          [
            106,
            "    @Override"
          ],
          [
            107,
            "    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})"
          ],
          [
            108,
            "    public ResponseEntity<?> deleteScheduleItem("
          ],
          [
            109,
            "            Long id, UUID idempotencyKey, String expectedRevision) {"
          ],
          [
            110,
            "        var operation ="
          ],
          [
            111,
            "                scheduleItemService.deleteScheduleItem(id, idempotencyKey, expectedRevision);"
          ],
          [
            112,
            "        return operation.completed()"
          ],
          [
            113,
            "                ? ResponseEntity.noContent().build()"
          ],
          [
            114,
            "                : ResponseEntity.accepted()"
          ],
          [
            115,
            "                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)"
          ],
          [
            116,
            "                        .body(operation);"
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
            "    @Override"
          ],
          [
            120,
            "    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})"
          ],
          [
            121,
            "    public ResponseEntity<ConstructorOperationResponse> getConstructorOperation(UUID operationId) {"
          ],
          [
            122,
            "        return ResponseEntity.ok(scheduleItemService.constructorOperation(operationId));"
          ],
          [
            123,
            "    }"
          ]
        ]
      }
    ]
  }
]
