import type { StoryOperation } from './story-source'

export const teacherAssignmentOperations: readonly StoryOperation[] = [
  {
    "id": "teacher-assignments-roles",
    "title": "Открыть предмет своей группы",
    "description": "Администратор создаёт учётную запись преподавателя в разделе пользователей и указывает табельный номер. Редактор назначения находится у старосты в разделе «Предметы». Староста выбирает преподавателя и сохраняет назначение для своей группы. При создании предмета первые назначения входят в initialAssignments. Academic разрешает администратору читать список назначений и статус замены.",
    "frames": [
      {
        "title": "Форма администратора требует табельный номер",
        "path": "frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1537,
            "      ...(role === 'TEACHER' && createEmployeeNumber.value.trim()"
          ],
          [
            1538,
            "        ? { employeeNumber: createEmployeeNumber.value }"
          ],
          [
            1539,
            "        : {}),"
          ],
          [
            1547,
            "    if (role === 'TEACHER' && !input.employeeNumber?.trim())"
          ],
          [
            1548,
            "      throw new Error('Укажи табельный номер преподавателя.');"
          ],
          [
            1549,
            "    return { input, message: null };"
          ]
        ]
      },
      {
        "title": "Редактор предмета добавляет преподавателя",
        "path": "frontends/mobile-core/src/features/headman-subjects/subject-save-flow.ts",
        "language": "typescript",
        "lines": [
          [
            337,
            "          if (plan.subjectId === null) {"
          ],
          [
            338,
            "            const created = await api.createSubject({"
          ],
          [
            339,
            "              name: plan.name,"
          ],
          [
            340,
            "              type: plan.lessonTypes[0]!,"
          ],
          [
            341,
            "              lessonTypes: plan.lessonTypes,"
          ],
          [
            342,
            "              initialAssignments: plan.assignments,"
          ],
          [
            343,
            "            })"
          ],
          [
            344,
            "            if (!current()) return"
          ],
          [
            345,
            "            if (!port.persist({ ...plan, createdId: created.id, step: 1, unknown: false })) {"
          ],
          [
            366,
            "            else if (assignment)"
          ],
          [
            367,
            "              await api.addTeacher(plan.subjectId, assignment.teacherId, assignment)"
          ],
          [
            368,
            "            else throw new Error('Сохранённый шаг операции повреждён.')"
          ],
          [
            369,
            "            if (!current()) return"
          ],
          [
            370,
            "            if (!port.persist({ ...plan, step: plan.step + 1, unknown: false })) {"
          ],
          [
            371,
            "              failed = true"
          ],
          [
            372,
            "              break"
          ],
          [
            373,
            "            }"
          ]
        ]
      },
      {
        "title": "Добавление доступно в студенческой роли, группа берётся из контекста",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java",
        "language": "java",
        "lines": [
          [
            88,
            "    private Long requireHeadmanGroupId() {"
          ],
          [
            89,
            "        requireHeadman();"
          ],
          [
            90,
            "        Long groupId = requestContext.getGroupId();"
          ],
          [
            91,
            "        if (groupId == null || groupId <= 0) {"
          ],
          [
            92,
            "            throw new AccessDeniedException(\"Группа старосты не определена в контексте запроса\");"
          ],
          [
            93,
            "        }"
          ],
          [
            94,
            "        return groupId;"
          ],
          [
            95,
            "    }"
          ],
          [
            96,
            ""
          ],
          [
            97,
            "    private void assertSubjectBelongsToHeadmanGroup(Subject subject, Long groupId) {"
          ],
          [
            98,
            "        if (!Objects.equals(subject.getGroupId(), groupId)) {"
          ],
          [
            99,
            "            throw new AccessDeniedException(\"Предмет не принадлежит вашей группе\");"
          ],
          [
            100,
            "        }"
          ],
          [
            292,
            "    @Transactional"
          ],
          [
            293,
            "    public Assignment addTeacher(Long subjectId, Long teacherId, AddSubjectTeacherRequest request) {"
          ],
          [
            294,
            "        Long groupId = requireHeadmanGroupId();"
          ],
          [
            295,
            "        Semester semester = assignmentAuthority.lockSemester(request.semesterId());"
          ],
          [
            296,
            "        Subject subject = lockSubject(subjectId);"
          ],
          [
            297,
            "        assertSubjectBelongsToHeadmanGroup(subject, groupId);"
          ],
          [
            298,
            "        if (!Objects.equals(subjectId, subject.getId())) {"
          ],
          [
            299,
            "            throw new ResourceNotFoundException(\"Subject\", \"id\", subjectId);"
          ],
          [
            300,
            "        }"
          ],
          [
            301,
            "        return assignmentAuthority.createWithLockedSemester("
          ],
          [
            302,
            "                teacherId,"
          ],
          [
            303,
            "                subjectId,"
          ],
          [
            304,
            "                groupId,"
          ],
          [
            305,
            "                semester,"
          ],
          [
            306,
            "                request.lessonType(),"
          ],
          [
            307,
            "                request.validFrom(),"
          ],
          [
            308,
            "                request.validUntilExclusive());"
          ],
          [
            309,
            "    }"
          ]
        ]
      },
      {
        "title": "Администратор может читать назначения и статусы замены",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentController.java",
        "language": "java",
        "lines": [
          [
            49,
            "    @Override"
          ],
          [
            50,
            "    @RequireRole({UserRole.ADMIN, UserRole.STUDENT})"
          ],
          [
            51,
            "    public ResponseEntity<PagedModel<EntityModel<AssignmentResponse>>> listAssignments("
          ],
          [
            52,
            "            Long groupId,"
          ],
          [
            53,
            "            Long semesterId,"
          ],
          [
            54,
            "            Pageable pageable,"
          ],
          [
            55,
            "            PagedResourcesAssembler<AssignmentResponse> assembler) {"
          ],
          [
            56,
            "        Page<AssignmentResponse> responsePage ="
          ],
          [
            57,
            "                assignmentService"
          ],
          [
            58,
            "                        .listAssignments(groupId, semesterId, pageable)"
          ],
          [
            59,
            "                        .map(assignmentAssembler::toResponse);"
          ],
          [
            60,
            "        return ResponseEntity.ok(assembler.toModel(responsePage, EntityModel::of));"
          ],
          [
            61,
            "    }"
          ],
          [
            79,
            "    @Override"
          ],
          [
            80,
            "    @RequireRole({UserRole.STUDENT})"
          ],
          [
            81,
            "    public ResponseEntity<AssignmentReplacementResponse> replaceAssignment("
          ],
          [
            82,
            "            Long id, ReplaceAssignmentRequest request) {"
          ],
          [
            83,
            "        return ResponseEntity.status(HttpStatus.CREATED)"
          ],
          [
            84,
            "                .body(replacementService.replace(id, request));"
          ],
          [
            85,
            "    }"
          ],
          [
            86,
            ""
          ],
          [
            87,
            "    @Override"
          ],
          [
            88,
            "    @RequireRole({UserRole.STUDENT, UserRole.ADMIN})"
          ],
          [
            89,
            "    public ResponseEntity<AssignmentReplacementResponse> getReplacementStatus(UUID operationId) {"
          ],
          [
            90,
            "        return ResponseEntity.ok(replacementService.getStatus(operationId));"
          ],
          [
            91,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "teacher-assignments-add",
    "title": "Задать тип пары и период",
    "description": "Запрос на добавление передаёт семестр, тип пары и интервал. Начало включено, конец исключён. Если конец не указан, назначение действует до конца семестра включительно. Academic проверяет предмет и группу, допустимый тип, активность преподавателя и пересечение с его назначением на тот же предмет, группу, семестр и тип.",
    "frames": [
      {
        "title": "Клиент передаёт предмет и преподавателя в URL",
        "path": "frontends/mobile-core/src/features/headman-subjects/headman-subjects-client.ts",
        "language": "typescript",
        "lines": [
          [
            291,
            "  addTeacher("
          ],
          [
            292,
            "    subjectId: number,"
          ],
          [
            293,
            "    teacherId: number,"
          ],
          [
            294,
            "    input: AddHeadmanSubjectTeacherInput,"
          ],
          [
            295,
            "  ): Promise<void> {"
          ],
          [
            296,
            "    assertPositiveInteger(subjectId, 'subjectId')"
          ],
          [
            297,
            "    assertPositiveInteger(teacherId, 'teacherId')"
          ],
          [
            298,
            "    const payload = normalizeAddInput(input)"
          ],
          [
            299,
            "    return this.request<unknown>("
          ],
          [
            300,
            "      `/api/academic/subjects/${subjectId}/teachers/${teacherId}`,"
          ],
          [
            301,
            "      {"
          ],
          [
            302,
            "        method: 'POST',"
          ],
          [
            303,
            "        body: JSON.stringify(payload),"
          ],
          [
            304,
            "      },"
          ],
          [
            305,
            "      false,"
          ],
          [
            306,
            "      201,"
          ],
          [
            307,
            "    ).then(() => undefined)"
          ],
          [
            308,
            "  }"
          ]
        ]
      },
      {
        "title": "Нормализация тела запроса",
        "path": "frontends/mobile-core/src/features/headman-subjects/headman-subjects-client.ts",
        "language": "typescript",
        "lines": [
          [
            490,
            "function normalizeAddInput(input: AddHeadmanSubjectTeacherInput): AddHeadmanSubjectTeacherInput {"
          ],
          [
            491,
            "  const result = {"
          ],
          [
            492,
            "    semesterId: positiveInteger(input.semesterId, 'semesterId'),"
          ],
          [
            493,
            "    lessonType: subjectType(input.lessonType, 'lessonType'),"
          ],
          [
            494,
            "    validFrom: requiredDate(input.validFrom, 'validFrom'),"
          ],
          [
            495,
            "    validUntilExclusive: nullableDate(input.validUntilExclusive),"
          ],
          [
            496,
            "  }"
          ],
          [
            497,
            "  if (result.validUntilExclusive !== null && result.validUntilExclusive <= result.validFrom) {"
          ],
          [
            498,
            "    throw new RangeError('validUntilExclusive must be after validFrom')"
          ],
          [
            499,
            "  }"
          ],
          [
            500,
            "  return result"
          ],
          [
            501,
            "}"
          ]
        ]
      },
      {
        "title": "Назначение на практику",
        "language": "json",
        "example": [
          "{",
          "  \"semesterId\": 3,",
          "  \"lessonType\": \"PRACTICE\",",
          "  \"validFrom\": \"2026-11-16\",",
          "  \"validUntilExclusive\": null",
          "}"
        ]
      },
      {
        "title": "Тип пары, преподаватель и конфликт интервалов",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAuthority.java",
        "language": "java",
        "lines": [
          [
            194,
            "        if (!Objects.equals(subject.getGroupId(), groupId)) {"
          ],
          [
            195,
            "            throw new AccessDeniedException(\"Предмет не принадлежит указанной группе\");"
          ],
          [
            196,
            "        }"
          ],
          [
            197,
            "        // Unresolved legacy rows keep the existing ordinary-write contract. They are never"
          ],
          [
            198,
            "        // eligible for irreversible subject deletion; new subjects always have a binding."
          ],
          [
            199,
            "        if (subject.getSemesterId() != null"
          ],
          [
            200,
            "                && !Objects.equals(subject.getSemesterId(), semester.getId())) {"
          ],
          [
            201,
            "            throw new ru.rutcampustrack.academic.exception.ConflictException("
          ],
          [
            202,
            "                    \"Предмет не привязан к этому семестру; создайте новый предмет\");"
          ],
          [
            203,
            "        }"
          ],
          [
            204,
            "        if (!lessonTypeRepository.existsBySubjectIdAndLessonType(subjectId, lessonType)) {"
          ],
          [
            205,
            "            throw new BadRequestException(\"lessonType\", \"Тип занятия не разрешён для предмета\");"
          ],
          [
            206,
            "        }"
          ],
          [
            207,
            ""
          ],
          [
            208,
            "        teacherEligibility.requireEligible(teacherId);"
          ],
          [
            209,
            ""
          ],
          [
            210,
            "        validateDates(semester, validFrom, validUntilExclusive);"
          ],
          [
            214,
            "        assignmentRepository"
          ],
          [
            215,
            "                .findOverlapping("
          ],
          [
            216,
            "                        teacherId,"
          ],
          [
            217,
            "                        subjectId,"
          ],
          [
            218,
            "                        groupId,"
          ],
          [
            219,
            "                        semester.getId(),"
          ],
          [
            220,
            "                        lessonType.name().toLowerCase(),"
          ],
          [
            221,
            "                        validFrom,"
          ],
          [
            222,
            "                        validUntilExclusive)"
          ],
          [
            223,
            "                .ifPresent("
          ],
          [
            224,
            "                        existing -> {"
          ],
          [
            225,
            "                            throw new ConflictException("
          ],
          [
            226,
            "                                    \"Назначение преподавателя пересекается с существующим\");"
          ],
          [
            227,
            "                        });"
          ]
        ]
      },
      {
        "title": "Начало внутри семестра, граница конца исключается",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentAuthority.java",
        "language": "java",
        "lines": [
          [
            250,
            "            Semester semester, LocalDate validFrom, LocalDate validUntilExclusive) {"
          ],
          [
            251,
            "        if (validFrom == null) {"
          ],
          [
            252,
            "            throw new BadRequestException(\"validFrom\", \"Дата начала обязательна\");"
          ],
          [
            253,
            "        }"
          ],
          [
            254,
            "        if (semester.getDateFrom() == null || semester.getDateTo() == null) {"
          ],
          [
            255,
            "            throw new ConflictException(\"У семестра отсутствуют границы дат\");"
          ],
          [
            256,
            "        }"
          ],
          [
            257,
            "        if (validFrom.isBefore(semester.getDateFrom()) || validFrom.isAfter(semester.getDateTo())) {"
          ],
          [
            258,
            "            throw new BadRequestException(\"validFrom\", \"Дата начала должна входить в семестр\");"
          ],
          [
            259,
            "        }"
          ],
          [
            260,
            "        LocalDate effectiveEnd = semester.getDateTo().plusDays(1);"
          ],
          [
            261,
            "        if (validUntilExclusive != null"
          ],
          [
            262,
            "                && (!validUntilExclusive.isAfter(validFrom)"
          ],
          [
            263,
            "                        || validUntilExclusive.isAfter(effectiveEnd))) {"
          ],
          [
            264,
            "            throw new BadRequestException("
          ],
          [
            265,
            "                    \"validUntilExclusive\","
          ],
          [
            266,
            "                    \"Дата окончания должна быть после начала и не позже конца семестра\");"
          ],
          [
            267,
            "        }"
          ],
          [
            268,
            "    }"
          ],
          [
            269,
            ""
          ],
          [
            270,
            "    public static LocalDate effectiveEnd(Assignment assignment, Semester semester) {"
          ],
          [
            271,
            "        return assignment.getValidUntilExclusive() != null"
          ],
          [
            272,
            "                ? assignment.getValidUntilExclusive()"
          ],
          [
            273,
            "                : semester.getDateTo().plusDays(1);"
          ],
          [
            274,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "teacher-assignments-prepare",
    "title": "Проверить дату и подтвердить замену",
    "description": "Староста выбирает другого преподавателя и дату, затем подтверждает сводку в редакторе. Запрос на замену получает UUID команды и при успехе возвращает HTTP 201. Academic требует действующее исходное назначение и непустой остаток его интервала. Предмет, группа, семестр и тип пары сохраняются. Одинаковый преподаватель, пересекающееся назначение цели или уже открытая замена дают конфликт.",
    "frames": [
      {
        "title": "Редактор показывает дату и кнопку подтверждения",
        "path": "frontends/mobile-core/src/features/headman-subjects/HeadmanSubjectsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            2255,
            "      <p"
          ],
          [
            2256,
            "        v-if=\"replacementTeacher && replacementEffectiveFrom\""
          ],
          [
            2257,
            "        class=\"headman-subjects__replacement-summary\""
          ],
          [
            2258,
            "        role=\"note\""
          ],
          [
            2259,
            "      >"
          ],
          [
            2260,
            "        {{ teacherDetail(replacementTeacher) }} примет назначение с {{ replacementEffectiveFrom }}."
          ],
          [
            2261,
            "        Перед отправкой проверь предмет и дату."
          ],
          [
            2262,
            "      </p>"
          ],
          [
            2274,
            "        @click=\"startReplacement\""
          ],
          [
            2275,
            "      >"
          ],
          [
            2276,
            "        {{ replacementBusy ? 'Отправляем…' : 'Подтвердить замену' }}"
          ],
          [
            2277,
            "      </button>"
          ]
        ]
      },
      {
        "title": "Перед отправкой сохраняются параметры и ключ",
        "path": "frontends/mobile-core/src/features/headman-subjects/HeadmanSubjectsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            813,
            "  const intent: StoredReplacement = {"
          ],
          [
            814,
            "    version: 2,"
          ],
          [
            815,
            "    actorUserId: props.actorUserId,"
          ],
          [
            816,
            "    groupId: props.groupId,"
          ],
          [
            817,
            "    assignmentId: source.assignment.id,"
          ],
          [
            818,
            "    subjectId: source.subject.id,"
          ],
          [
            819,
            "    subjectName: source.subject.name,"
          ],
          [
            820,
            "    lessonType: source.assignment.lessonType,"
          ],
          [
            821,
            "    sourceTeacherId: source.assignment.teacherId,"
          ],
          [
            822,
            "    replacementTeacherId: String(target.id),"
          ],
          [
            823,
            "    effectiveFrom: replacementEffectiveFrom.value,"
          ],
          [
            824,
            "    requestKey,"
          ],
          [
            825,
            "    operationId: null,"
          ],
          [
            826,
            "    state: null,"
          ],
          [
            827,
            "    phase: 'uncertain',"
          ],
          [
            828,
            "  };"
          ],
          [
            829,
            "  replacementError.value = null;"
          ],
          [
            830,
            "  replacementErrorRequestKey.value = null;"
          ],
          [
            831,
            "  notice.value = null;"
          ],
          [
            832,
            "  if (!persistReplacementIntent(intent)) return;"
          ],
          [
            833,
            "  await sendReplacementIntent(intent);"
          ]
        ]
      },
      {
        "title": "Команда замены и чтение статуса по ID",
        "path": "frontends/mobile-core/src/features/headman-subjects/headman-subjects-client.ts",
        "language": "typescript",
        "lines": [
          [
            239,
            "  replaceAssignment("
          ],
          [
            240,
            "    assignmentId: number,"
          ],
          [
            241,
            "    input: AssignmentReplacementInput,"
          ],
          [
            242,
            "  ): Promise<AssignmentReplacementResponse> {"
          ],
          [
            243,
            "    assertPositiveInteger(assignmentId, 'assignmentId')"
          ],
          [
            244,
            "    const payload = normalizeReplacementInput(input)"
          ],
          [
            245,
            "    return this.request<unknown>(`/api/academic/assignments/${assignmentId}/replace`, {"
          ],
          [
            246,
            "      method: 'POST',"
          ],
          [
            247,
            "      body: JSON.stringify(payload),"
          ],
          [
            248,
            "    }).then(normalizeAssignmentReplacement)"
          ],
          [
            249,
            "  }"
          ],
          [
            250,
            ""
          ],
          [
            251,
            "  getReplacementStatus(operationId: string): Promise<AssignmentReplacementResponse> {"
          ],
          [
            252,
            "    const id = uuid(operationId, 'operationId')"
          ],
          [
            253,
            "    return this.request<unknown>("
          ],
          [
            254,
            "      `/api/academic/assignments/replacements/${encodeURIComponent(id)}`,"
          ],
          [
            255,
            "    ).then(normalizeAssignmentReplacement)"
          ],
          [
            256,
            "  }"
          ]
        ]
      },
      {
        "title": "Замена с указанной даты",
        "language": "json",
        "example": [
          "{",
          "  \"replacementTeacherId\": \"501\",",
          "  \"effectiveFrom\": \"2026-11-23\",",
          "  \"requestKey\": \"b9c6d13f-3326-4333-b59d-e7555f5ffeca\"",
          "}"
        ]
      },
      {
        "title": "Исходное назначение и допустимость даты",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            182,
            "        assertOwnGroup(source.getGroupId());"
          ],
          [
            183,
            "        if (source.getLifecycleState() != AssignmentLifecycle.ACTIVE) {"
          ],
          [
            184,
            "            throw new ConflictException(\"Исходное назначение уже закрывается или закрыто\");"
          ],
          [
            185,
            "        }"
          ],
          [
            186,
            "        SubjectType lessonType = source.getLessonType();"
          ],
          [
            187,
            "        if (!lessonTypeRepository.existsBySubjectIdAndLessonType("
          ],
          [
            188,
            "                source.getSubjectId(), lessonType)) {"
          ],
          [
            189,
            "            throw new BadRequestException(\"lessonType\", \"Тип занятия не разрешён для предмета\");"
          ],
          [
            190,
            "        }"
          ],
          [
            191,
            "        LocalDate sourceEnd = AssignmentAuthority.effectiveEnd(source, semester);"
          ],
          [
            192,
            "        LocalDate effectiveFrom = request.effectiveFrom();"
          ],
          [
            193,
            "        if (effectiveFrom.isBefore(source.getValidFrom()) || effectiveFrom.isAfter(sourceEnd)) {"
          ],
          [
            194,
            "            throw new BadRequestException("
          ],
          [
            195,
            "                    \"effectiveFrom\", \"Дата замены должна входить в период назначения\");"
          ],
          [
            196,
            "        }"
          ],
          [
            197,
            "        LocalDate targetEnd = sourceEnd;"
          ],
          [
            198,
            "        if (!effectiveFrom.isBefore(targetEnd)) {"
          ],
          [
            199,
            "            throw new BadRequestException("
          ],
          [
            200,
            "                    \"effectiveFrom\", \"После даты замены должен оставаться период назначения\");"
          ],
          [
            201,
            "        }"
          ]
        ]
      },
      {
        "title": "Новый преподаватель, его интервал и открытая операция",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            202,
            "        long targetTeacherId ="
          ],
          [
            203,
            "                parsePositive(request.replacementTeacherId(), \"replacementTeacherId\");"
          ],
          [
            204,
            "        if (targetTeacherId == source.getTeacherId()) {"
          ],
          [
            205,
            "            throw new ConflictException(\"Новый преподаватель совпадает с исходным\");"
          ],
          [
            206,
            "        }"
          ],
          [
            207,
            "        teacherEligibility.requireEligible(targetTeacherId);"
          ],
          [
            208,
            "        assignmentRepository"
          ],
          [
            209,
            "                .findActiveOverlapping("
          ],
          [
            210,
            "                        targetTeacherId,"
          ],
          [
            211,
            "                        source.getSubjectId(),"
          ],
          [
            212,
            "                        source.getGroupId(),"
          ],
          [
            213,
            "                        source.getSemesterId(),"
          ],
          [
            214,
            "                        lessonType.name().toLowerCase(),"
          ],
          [
            215,
            "                        effectiveFrom,"
          ],
          [
            216,
            "                        targetEnd)"
          ],
          [
            217,
            "                .ifPresent("
          ],
          [
            218,
            "                        a -> {"
          ],
          [
            219,
            "                            throw new ConflictException("
          ],
          [
            220,
            "                                    \"Новое назначение пересекается с существующим\");"
          ],
          [
            221,
            "                        });"
          ],
          [
            222,
            "        operationRepository"
          ],
          [
            223,
            "                .findFirstBySourceAssignmentIdAndStateIn(source.getId(), OPEN_STATES)"
          ],
          [
            224,
            "                .ifPresent("
          ],
          [
            225,
            "                        open -> {"
          ],
          [
            226,
            "                            throw new ConflictException(\"Для назначения уже выполняется замена\");"
          ],
          [
            227,
            "                        });"
          ]
        ]
      }
    ]
  },
  {
    "id": "teacher-assignments-confirm",
    "title": "Согласовать замену с расписанием",
    "description": "Academic сохраняет операцию и новое назначение в PREPARED. Schedule сначала ограничивает создание пар по прежнему назначению датой замены. Затем Academic завершает прежний интервал на этой дате и включает новый. После этого Schedule применяет привязки и возвращает COMMITTED. При неопределённом результате экран хранит команду, повторяет тот же ключ или проверяет статус. Другие параметры с тем же ключом конфликтуют.",
    "frames": [
      {
        "title": "Новое назначение и операция сохраняются в PREPARED",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            239,
            "                                targetTeacherId,"
          ],
          [
            240,
            "                                source.getSubjectId(),"
          ],
          [
            241,
            "                                source.getGroupId(),"
          ],
          [
            242,
            "                                source.getSemesterId(),"
          ],
          [
            243,
            "                                lessonType,"
          ],
          [
            244,
            "                                effectiveFrom,"
          ],
          [
            245,
            "                                targetEnd,"
          ],
          [
            246,
            "                                AssignmentLifecycle.PREPARED));"
          ],
          [
            247,
            "        AssignmentReplacementOperation operation ="
          ],
          [
            248,
            "                new AssignmentReplacementOperation("
          ],
          [
            249,
            "                        UUID.randomUUID(),"
          ],
          [
            250,
            "                        actorId,"
          ],
          [
            251,
            "                        requestKey,"
          ],
          [
            252,
            "                        payloadHash,"
          ],
          [
            253,
            "                        source.getId(),"
          ],
          [
            254,
            "                        target.getId(),"
          ],
          [
            255,
            "                        effectiveFrom,"
          ],
          [
            256,
            "                        effectiveFrom,"
          ],
          [
            257,
            "                        targetEnd,"
          ],
          [
            258,
            "                        AssignmentOperationState.PREPARED);"
          ],
          [
            259,
            "        return operationRepository.saveAndFlush(operation);"
          ]
        ]
      },
      {
        "title": "Academic ждёт подтверждения Schedule на каждом шаге",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            97,
            "        if (operation.getState() == AssignmentOperationState.COMMITTED) {"
          ],
          [
            98,
            "            return status(operation.getOperationId());"
          ],
          [
            99,
            "        }"
          ],
          [
            100,
            "        if (operation.getState() == AssignmentOperationState.PREPARED) {"
          ],
          [
            101,
            "            AssignmentCloseReceipt receipt ="
          ],
          [
            102,
            "                    scheduleGrpcClient.installAssignmentCloseCap("
          ],
          [
            103,
            "                            operation.getOperationId(),"
          ],
          [
            104,
            "                            operation.getSourceAssignmentId(),"
          ],
          [
            105,
            "                            operation.getTargetAssignmentId(),"
          ],
          [
            106,
            "                            operation.getEffectiveFrom(),"
          ],
          [
            107,
            "                            payloadHash);"
          ],
          [
            108,
            "            requireReceipt(operation, receipt, \"APPLIED\");"
          ],
          [
            109,
            "            UUID operationId = operation.getOperationId();"
          ],
          [
            110,
            "            transaction.executeWithoutResult(tx -> activateAcademic(operationId, receipt));"
          ],
          [
            111,
            "            operation = operationRepository.findById(operationId).orElseThrow();"
          ],
          [
            112,
            "        }"
          ],
          [
            113,
            "        if (operation.getState() == AssignmentOperationState.APPLIED) {"
          ],
          [
            114,
            "            UUID operationId = operation.getOperationId();"
          ],
          [
            115,
            "            AssignmentCloseReceipt receipt ="
          ],
          [
            116,
            "                    scheduleGrpcClient.commitAssignmentClose(operationId, payloadHash);"
          ],
          [
            117,
            "            requireReceipt(operation, receipt, \"COMMITTED\");"
          ],
          [
            118,
            "            transaction.executeWithoutResult(tx -> markCommitted(operationId, receipt));"
          ],
          [
            119,
            "        }"
          ],
          [
            120,
            "        return status(operation.getOperationId());"
          ]
        ]
      },
      {
        "title": "Schedule удерживает дату прекращения создания по старому назначению",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            170,
            "        Map<Long, Map<String, Object>> fences = lockOrInstallFencePair(authority);"
          ],
          [
            171,
            "        LocalDate effectiveFrom = parseDate(authority.getEffectiveFrom());"
          ],
          [
            172,
            "        LocalDate sourceCreationCap ="
          ],
          [
            173,
            "                date("
          ],
          [
            174,
            "                        fences.get(authority.getSourceAssignmentId())"
          ],
          [
            175,
            "                                .get(\"creation_cap_until_exclusive\"));"
          ],
          [
            176,
            "        if (sourceCreationCap.isBefore(effectiveFrom)) {"
          ],
          [
            177,
            "            throw new ConflictException(\"Исходное назначение уже ограничено более ранней датой\");"
          ],
          [
            178,
            "        }"
          ],
          [
            179,
            "        if (sourceCreationCap.isAfter(effectiveFrom)) {"
          ],
          [
            180,
            "            int narrowed ="
          ],
          [
            181,
            "                    jdbc.update("
          ],
          [
            182,
            "                            \"\"\""
          ],
          [
            183,
            "                    UPDATE schedule_assignment_fences"
          ],
          [
            184,
            "                       SET creation_cap_until_exclusive = ?, revision = revision + 1"
          ],
          [
            185,
            "                     WHERE assignment_id = ? AND creation_cap_until_exclusive = ?"
          ],
          [
            186,
            "                    \"\"\","
          ],
          [
            187,
            "                            effectiveFrom,"
          ],
          [
            188,
            "                            authority.getSourceAssignmentId(),"
          ],
          [
            189,
            "                            sourceCreationCap);"
          ],
          [
            190,
            "            if (narrowed != 1)"
          ],
          [
            191,
            "                throw new ConflictException(\"Fence назначения изменился во время замены\");"
          ]
        ]
      },
      {
        "title": "Конец старого интервала и включение нового назначения",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            332,
            "        requireReceipt(operation, receipt, \"APPLIED\");"
          ],
          [
            333,
            "        assertReplacementTuple("
          ],
          [
            334,
            "                operation,"
          ],
          [
            335,
            "                source,"
          ],
          [
            336,
            "                target,"
          ],
          [
            337,
            "                semester,"
          ],
          [
            338,
            "                operation.getTargetValidUntil(),"
          ],
          [
            339,
            "                AssignmentLifecycle.PREPARED);"
          ],
          [
            340,
            "        teacherEligibility.requireEligible(target.getTeacherId());"
          ],
          [
            341,
            "        operation.markApplied("
          ],
          [
            342,
            "                receipt.getState(), receipt.getMovedCount(), receipt.getSkippedCount());"
          ],
          [
            343,
            "        operationRepository.saveAndFlush(operation);"
          ],
          [
            348,
            "        if (jdbc.update("
          ],
          [
            349,
            "                                \"UPDATE assignments SET valid_until_exclusive = ? WHERE id = ?\","
          ],
          [
            350,
            "                                operation.getEffectiveFrom(),"
          ],
          [
            351,
            "                                operation.getSourceAssignmentId())"
          ],
          [
            352,
            "                        != 1"
          ],
          [
            353,
            "                || jdbc.update("
          ],
          [
            354,
            "                                \"UPDATE assignments SET lifecycle_state = 'ACTIVE' WHERE id = ?\","
          ],
          [
            355,
            "                                operation.getTargetAssignmentId())"
          ],
          [
            356,
            "                        != 1) {"
          ],
          [
            357,
            "            throw new ConflictException(\"Academic не смог активировать точное назначение замены\");"
          ],
          [
            358,
            "        }"
          ]
        ]
      },
      {
        "title": "Ключ привязан к автору и содержимому",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            130,
            "    private AssignmentReplacementOperation prepare("
          ],
          [
            131,
            "            long actorId,"
          ],
          [
            132,
            "            UUID requestKey,"
          ],
          [
            133,
            "            byte[] payloadHash,"
          ],
          [
            134,
            "            Long sourceAssignmentId,"
          ],
          [
            135,
            "            ReplaceAssignmentRequest request) {"
          ],
          [
            136,
            "        AssignmentReplacementOperation existing ="
          ],
          [
            137,
            "                operationRepository.findByActorIdAndRequestKey(actorId, requestKey).orElse(null);"
          ],
          [
            138,
            "        if (existing != null) {"
          ],
          [
            139,
            "            if (existing.getOperationKind() != AssignmentOperationKind.REPLACE"
          ],
          [
            140,
            "                    || !MessageDigest.isEqual(existing.getPayloadHash(), payloadHash)) {"
          ],
          [
            141,
            "                throw new ConflictException(\"Idempotency-Key уже использован для другой замены\");"
          ],
          [
            142,
            "            }"
          ],
          [
            143,
            "            return existing;"
          ],
          [
            144,
            "        }"
          ]
        ]
      },
      {
        "title": "Экран различает ожидание и завершённую замену",
        "path": "frontends/mobile-core/src/features/headman-subjects/HeadmanSubjectsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            882,
            "    const response = await api.getReplacementStatus(intent.operationId);"
          ],
          [
            883,
            "    if (revision !== replacementRevision || disposed) return;"
          ],
          [
            884,
            "    await acceptReplacementResponse(intent, response, revision);"
          ],
          [
            885,
            "  } catch (cause) {"
          ],
          [
            912,
            "  const next: StoredReplacement = {"
          ],
          [
            913,
            "    ...intent,"
          ],
          [
            914,
            "    operationId: response.operationId,"
          ],
          [
            915,
            "    state: response.state,"
          ],
          [
            916,
            "    phase: response.state === 'COMMITTED' ? 'committed' : 'pending',"
          ],
          [
            917,
            "  };"
          ],
          [
            918,
            "  if (!persistReplacementIntent(next)) upsertReplacementIntent(next);"
          ],
          [
            919,
            "  if (response.state === 'COMMITTED') {"
          ],
          [
            920,
            "    const outcome = await load();"
          ],
          [
            921,
            "    if (revision !== replacementRevision || disposed) return;"
          ],
          [
            922,
            "    if (outcome !== 'loaded' && !replacementError.value) {"
          ],
          [
            923,
            "      replacementError.value ="
          ],
          [
            924,
            "        'Сервер подтвердил замену, но предметы не удалось обновить. Повтори загрузку списка.';"
          ],
          [
            925,
            "      replacementErrorRequestKey.value = intent.requestKey;"
          ]
        ]
      }
    ]
  },
  {
    "id": "teacher-assignments-series",
    "title": "Переключить шаблоны серии",
    "description": "Schedule создаёт или находит соответствующий шаблон нового назначения. При коммите прежние активные шаблоны выключаются, а их преемники включаются. Для существующих пар Schedule выбирает даты от даты замены включительно до конца нового интервала исключительно. В серии меняются подходящие записи со статусом planned. Активные, завершённые и отменённые пары сохраняют прежние данные.",
    "frames": [
      {
        "title": "Привязка шаблона к назначению и интервал дат",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            524,
            "        LocalDate effectiveFrom = parseDate(authority.getEffectiveFrom());"
          ],
          [
            525,
            "        for (Map<String, Object> sourceItem : sourceItems) {"
          ],
          [
            526,
            "            long sourceItemId = number(sourceItem.get(\"id\"));"
          ],
          [
            527,
            "            long targetItemId ="
          ],
          [
            528,
            "                    targetItem(operationId, sourceItem, authority.getTargetAssignmentId());"
          ],
          [
            529,
            "            List<Map<String, Object>> occurrences ="
          ],
          [
            530,
            "                    jdbc.queryForList("
          ],
          [
            531,
            "                            \"\"\""
          ],
          [
            532,
            "                    SELECT id AS occurrence_id, occurrence_date, current_lesson_id, revision"
          ],
          [
            533,
            "                      FROM lesson_occurrences"
          ],
          [
            534,
            "                     WHERE schedule_item_id = ? AND assignment_id = ? AND occurrence_date >= ?"
          ],
          [
            535,
            "                       AND occurrence_date < ?"
          ],
          [
            536,
            "                     ORDER BY id"
          ],
          [
            537,
            "                     FOR UPDATE"
          ],
          [
            538,
            "                    \"\"\","
          ],
          [
            539,
            "                            sourceItemId,"
          ],
          [
            540,
            "                            authority.getSourceAssignmentId(),"
          ],
          [
            541,
            "                            effectiveFrom,"
          ],
          [
            542,
            "                            parseDate(authority.getTargetValidUntilExclusive()));"
          ]
        ]
      },
      {
        "title": "Для серии проверяется planned, другие состояния сохраняются",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            544,
            "            for (Map<String, Object> occurrence : occurrences) {"
          ],
          [
            545,
            "                long occurrenceId = number(occurrence.get(\"occurrence_id\"));"
          ],
          [
            546,
            "                LocalDate date = date(occurrence.get(\"occurrence_date\"));"
          ],
          [
            547,
            "                Long lessonId = nullableNumber(occurrence.get(\"current_lesson_id\"));"
          ],
          [
            548,
            "                long occurrenceRevision = number(occurrence.get(\"revision\"));"
          ],
          [
            549,
            "                Map<String, Object> lesson = lessonId == null ? null : lessons.get(lessonId);"
          ],
          [
            550,
            "                if (lessonId != null && lesson == null) {"
          ],
          [
            551,
            "                    throw new ConflictException(\"Текущая физическая пара исчезла во время замены\");"
          ],
          [
            552,
            "                }"
          ],
          [
            553,
            "                String lessonStatus = lesson == null ? \"\" : String.valueOf(lesson.get(\"status\"));"
          ],
          [
            554,
            "                boolean eligible = lesson != null && \"planned\".equalsIgnoreCase(lessonStatus);"
          ],
          [
            555,
            "                String result = eligible ? \"MOVED\" : skipResult(lessonStatus);"
          ],
          [
            757,
            "    private static String skipResult(String lessonStatus) {"
          ],
          [
            758,
            "        if (\"cancelled\".equalsIgnoreCase(lessonStatus)) return \"SKIPPED_CANCELLED\";"
          ],
          [
            759,
            "        if (\"active\".equalsIgnoreCase(lessonStatus)) return \"SKIPPED_ACTIVE\";"
          ],
          [
            760,
            "        if (\"closed\".equalsIgnoreCase(lessonStatus)) return \"SKIPPED_CLOSED\";"
          ],
          [
            761,
            "        if (lessonStatus == null || lessonStatus.isBlank()) return \"SKIPPED_NO_CURRENT_LESSON\";"
          ],
          [
            762,
            "        return \"SKIPPED_ALREADY_STARTED\";"
          ],
          [
            763,
            "    }"
          ]
        ]
      },
      {
        "title": "Активность переключается между шаблонами",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            462,
            "            int[] counts = rebindFutureOccurrences(operationId, authority);"
          ],
          [
            463,
            "            int[] oneOffCounts = rebindOneOffOccurrences(operationId, authority);"
          ],
          [
            464,
            "            counts[0] += oneOffCounts[0];"
          ],
          [
            465,
            "            counts[1] += oneOffCounts[1];"
          ],
          [
            466,
            "            jdbc.update("
          ],
          [
            467,
            "                    \"\"\""
          ],
          [
            468,
            "                UPDATE schedule_items item"
          ],
          [
            469,
            "                   SET is_active = FALSE"
          ],
          [
            470,
            "                 WHERE item.id IN (SELECT source_schedule_item_id"
          ],
          [
            471,
            "                                     FROM schedule_assignment_replacement_templates"
          ],
          [
            472,
            "                                    WHERE operation_id = ? AND source_was_active)"
          ],
          [
            473,
            "                \"\"\","
          ],
          [
            474,
            "                    operationId);"
          ],
          [
            475,
            "            jdbc.update("
          ],
          [
            476,
            "                    \"\"\""
          ],
          [
            477,
            "                UPDATE schedule_items item"
          ],
          [
            478,
            "                   SET is_active = TRUE"
          ],
          [
            479,
            "                 WHERE item.id IN (SELECT target_schedule_item_id"
          ],
          [
            480,
            "                                     FROM schedule_assignment_replacement_templates"
          ],
          [
            481,
            "                                    WHERE operation_id = ? AND source_was_active)"
          ],
          [
            482,
            "                \"\"\","
          ],
          [
            483,
            "                    operationId);"
          ]
        ]
      }
    ]
  },
  {
    "id": "teacher-assignments-pairs",
    "title": "Сохранить ID пары и её историю",
    "description": "Schedule меняет назначение, преподавателя и ревизии существующих occurrence и lesson. ID физической пары, ID occurrence и связанные отметки сохраняются. Разовые пары обрабатываются отдельно. У них должны одновременно выполняться planned и проверка, что начало ещё впереди. Журнал изменения хранит связь между прежними и новыми значениями.",
    "frames": [
      {
        "title": "Существующие occurrence и lesson обновляются по ID и ревизии",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            574,
            "                            UPDATE lesson_occurrences"
          ],
          [
            575,
            "                               SET schedule_item_id = ?, assignment_id = ?, assigned_teacher_id = ?,"
          ],
          [
            576,
            "                                   revision = revision + 1"
          ],
          [
            577,
            "                             WHERE id = ? AND schedule_item_id = ? AND assignment_id = ?"
          ],
          [
            578,
            "                               AND occurrence_date = ? AND revision = ?"
          ],
          [
            579,
            "                            \"\"\","
          ],
          [
            591,
            "                            UPDATE lessons"
          ],
          [
            592,
            "                               SET schedule_item_id = ?, assignment_id = ?, assigned_teacher_id = ?,"
          ],
          [
            593,
            "                                   revision = revision + 1"
          ],
          [
            594,
            "                             WHERE id = ? AND occurrence_id = ? AND schedule_item_id = ?"
          ],
          [
            595,
            "                               AND assignment_id = ? AND assigned_teacher_id = ?"
          ],
          [
            596,
            "                               AND status::text = 'planned' AND date = ? AND revision = ?"
          ],
          [
            597,
            "                            \"\"\","
          ],
          [
            608,
            "                    if (occurrenceUpdated != 1 || lessonUpdated != 1) {"
          ],
          [
            609,
            "                        throw new ConflictException(\"Плановая пара изменилась во время замены\");"
          ],
          [
            610,
            "                    }"
          ],
          [
            611,
            "                    ScheduleActionRecordedEvent.publish("
          ],
          [
            612,
            "                            jdbc,"
          ],
          [
            613,
            "                            events,"
          ],
          [
            614,
            "                            \"LESSON_EDITED\","
          ],
          [
            615,
            "                            lessonId,"
          ]
        ]
      },
      {
        "title": "Разовая пара должна оставаться плановой и ещё не начаться",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            673,
            "        int skipped = 0;"
          ],
          [
            674,
            "        for (Map<String, Object> occurrence : occurrences) {"
          ],
          [
            675,
            "            long occurrenceId = number(occurrence.get(\"occurrence_id\"));"
          ],
          [
            676,
            "            Long lessonId = nullableNumber(occurrence.get(\"current_lesson_id\"));"
          ],
          [
            677,
            "            Map<String, Object> lesson = lessonId == null ? null : lessons.get(lessonId);"
          ],
          [
            678,
            "            if (lessonId != null && lesson == null)"
          ],
          [
            679,
            "                throw new ConflictException(\"Текущая разовая пара исчезла во время замены\");"
          ],
          [
            680,
            "            if (lesson == null"
          ],
          [
            681,
            "                    || !\"planned\".equals(lesson.get(\"status\"))"
          ],
          [
            682,
            "                    || !Boolean.TRUE.equals(lesson.get(\"upcoming\"))) {"
          ],
          [
            683,
            "                skipped++;"
          ],
          [
            684,
            "                continue;"
          ],
          [
            685,
            "            }"
          ],
          [
            686,
            "            long occurrenceRevision = number(occurrence.get(\"revision\"));"
          ],
          [
            687,
            "            long lessonRevision = number(lesson.get(\"revision\"));"
          ]
        ]
      },
      {
        "title": "Реестр фиксирует прежнее и новое назначение для тех же ID",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentReplacementService.java",
        "language": "java",
        "lines": [
          [
            691,
            "                INSERT INTO schedule_assignment_binding_ledger"
          ],
          [
            692,
            "                    (operation_id, semester_id, occurrence_id, lesson_id, occurrence_date,"
          ],
          [
            693,
            "                     expected_occurrence_revision, after_occurrence_revision,"
          ],
          [
            694,
            "                     expected_lesson_revision, after_lesson_revision,"
          ],
          [
            695,
            "                     before_assignment_id, before_teacher_id, after_assignment_id, after_teacher_id)"
          ],
          [
            696,
            "                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
          ],
          [
            697,
            "                \"\"\","
          ],
          [
            698,
            "                    operationId,"
          ],
          [
            699,
            "                    authority.getSemesterId(),"
          ],
          [
            700,
            "                    occurrenceId,"
          ],
          [
            701,
            "                    lessonId,"
          ],
          [
            702,
            "                    occurrence.get(\"occurrence_date\"),"
          ],
          [
            703,
            "                    occurrenceRevision,"
          ],
          [
            704,
            "                    occurrenceRevision + 1,"
          ],
          [
            705,
            "                    lessonRevision,"
          ],
          [
            706,
            "                    lessonRevision + 1,"
          ],
          [
            707,
            "                    authority.getSourceAssignmentId(),"
          ],
          [
            708,
            "                    authority.getSourceTeacherId(),"
          ],
          [
            709,
            "                    authority.getTargetAssignmentId(),"
          ],
          [
            710,
            "                    authority.getTargetTeacherId());"
          ]
        ]
      }
    ]
  },
  {
    "id": "teacher-assignments-journal",
    "title": "Проверить доступ к журналу",
    "description": "Academic на московское сегодня проверяет активный grant TEACHER и действующее назначение в активном семестре. Журнал открывается по текущей паре «группа + предмет», затем выбирает занятия по семестру, датам и типам. Поэтому после даты замены новый преподаватель может читать прошлые пары этой группы и предмета. Прежний сохраняет доступ, если у него остаётся другое действующее назначение для тех же группы и предмета. Без такого назначения доступ к журналу закрывается.",
    "frames": [
      {
        "title": "Сервер проверяет активного преподавателя и семестр",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            544,
            "        if (grantRepository"
          ],
          [
            545,
            "                .findByUserIdAndRoleAndStatus(request.getTeacherId(), \"teacher\", \"active\")"
          ],
          [
            546,
            "                .isEmpty()) {"
          ],
          [
            547,
            "            responseObserver.onError("
          ],
          [
            548,
            "                    Status.PERMISSION_DENIED"
          ],
          [
            549,
            "                            .withDescription(\"teacher has no active TEACHER grant\")"
          ],
          [
            550,
            "                            .asRuntimeException());"
          ],
          [
            551,
            "            return;"
          ],
          [
            552,
            "        }"
          ],
          [
            553,
            "        Optional<ru.rutcampustrack.academic.entity.Semester> semester ="
          ],
          [
            554,
            "                semesterRepository.findById(request.getSemesterId());"
          ],
          [
            555,
            "        if (semester.isEmpty() || !semester.get().isActive()) {"
          ],
          [
            556,
            "            responseObserver.onNext(TeacherSubjectsResponse.newBuilder().build());"
          ],
          [
            557,
            "            responseObserver.onCompleted();"
          ],
          [
            558,
            "            return;"
          ],
          [
            559,
            "        }"
          ],
          [
            560,
            "        ru.rutcampustrack.academic.entity.Semester activeSemester = semester.get();"
          ],
          [
            561,
            "        LocalDate today = LocalDate.now(MOSCOW);"
          ],
          [
            562,
            "        if (today.isBefore(activeSemester.getDateFrom())"
          ],
          [
            563,
            "                || today.isAfter(activeSemester.getDateTo())) {"
          ],
          [
            564,
            "            responseObserver.onNext(TeacherSubjectsResponse.newBuilder().build());"
          ],
          [
            565,
            "            responseObserver.onCompleted();"
          ],
          [
            566,
            "            return;"
          ]
        ]
      },
      {
        "title": "Текущий интервал вычисляется по дате",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            569,
            "        List<TeacherSubjectInfo> subjectInfos ="
          ],
          [
            570,
            "                assignmentRepository"
          ],
          [
            571,
            "                        .findByTeacherIdAndSemesterId("
          ],
          [
            572,
            "                                request.getTeacherId(), request.getSemesterId())"
          ],
          [
            573,
            "                        .stream()"
          ],
          [
            574,
            "                        .filter(a -> a.getLifecycleState() == AssignmentLifecycle.ACTIVE)"
          ],
          [
            575,
            "                        .filter(a -> !a.getValidFrom().isAfter(today))"
          ],
          [
            576,
            "                        .filter("
          ],
          [
            577,
            "                                a ->"
          ],
          [
            578,
            "                                        today.isBefore("
          ],
          [
            579,
            "                                                a.getValidUntilExclusive() != null"
          ],
          [
            580,
            "                                                        ? a.getValidUntilExclusive()"
          ],
          [
            581,
            "                                                        : activeSemester.getDateTo().plusDays(1)))"
          ]
        ]
      },
      {
        "title": "Журнал проверяет текущую группу и предмет",
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
            508,
            "                                lesson ->"
          ],
          [
            509,
            "                                        lesson.getSemesterId() == semesterId"
          ],
          [
            510,
            "                                                && lesson.getGroupId() == groupId"
          ],
          [
            511,
            "                                                && lesson.getSubjectId() == subjectId"
          ],
          [
            512,
            "                                                && selectedTypes.stream()"
          ],
          [
            513,
            "                                                        .anyMatch("
          ],
          [
            514,
            "                                                                type ->"
          ],
          [
            515,
            "                                                                        type.equalsIgnoreCase("
          ],
          [
            516,
            "                                                                                lesson"
          ],
          [
            517,
            "                                                                                        .getLessonType())))"
          ],
          [
            518,
            "                        .filter(lesson -> inRange(lesson, range))"
          ],
          [
            519,
            "                        .filter(lesson -> !pastOnly || completedHistoryLesson(lesson, teachingNow))"
          ]
        ]
      },
      {
        "title": "Attendance проверяет ту же пару при чтении занятия",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            2096,
            "    public void authorizeTeacherLesson(LessonResponse lesson, Long teacherId) {"
          ],
          [
            2097,
            "        if (lesson == null"
          ],
          [
            2098,
            "                || lesson.getId() <= 0"
          ],
          [
            2099,
            "                || lesson.getSemesterId() <= 0"
          ],
          [
            2100,
            "                || lesson.getLessonType().isBlank()"
          ],
          [
            2101,
            "                || teacherId == null"
          ],
          [
            2102,
            "                || teacherId <= 0) {"
          ],
          [
            2103,
            "            throw new AccessDeniedException(\"Teacher cannot read this lesson\");"
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
  },
  {
    "id": "teacher-assignments-snapshot",
    "title": "Открыть своё расписание на день",
    "description": "Дневное расписание преподавателя проверяет ID назначения и assignedTeacherId снимка каждой пары. Дополнительно совпадают группа, предмет и тип. Поэтому доступ к истории по текущему предмету не переносит старые пары в личное расписание нового преподавателя.",
    "frames": [
      {
        "title": "Для дневного расписания проверяется точная привязка пары",
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
        "title": "Academic выдаёт интервал и идентификатор назначения",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            606,
            "                                    return TeacherSubjectInfo.newBuilder()"
          ],
          [
            607,
            "                                            .setSubjectId(a.getSubjectId())"
          ],
          [
            608,
            "                                            .setSubjectName(subject.getName())"
          ],
          [
            609,
            "                                            .setSubjectType("
          ],
          [
            610,
            "                                                    a.getLessonType()"
          ],
          [
            611,
            "                                                            .name()"
          ],
          [
            612,
            "                                                            .toLowerCase(Locale.ROOT))"
          ],
          [
            613,
            "                                            .setGroupId(a.getGroupId())"
          ],
          [
            614,
            "                                            .setGroupName(group.getName())"
          ],
          [
            615,
            "                                            .setAssignmentId(a.getId())"
          ],
          [
            616,
            "                                            .setSemesterId(a.getSemesterId())"
          ],
          [
            617,
            "                                            .setLessonType("
          ],
          [
            618,
            "                                                    a.getLessonType()"
          ],
          [
            619,
            "                                                            .name()"
          ],
          [
            620,
            "                                                            .toLowerCase(Locale.ROOT))"
          ],
          [
            621,
            "                                            .setValidFrom(a.getValidFrom().toString())"
          ],
          [
            622,
            "                                            .setValidUntilExclusive(effectiveEnd)"
          ],
          [
            623,
            "                                            .build();"
          ]
        ]
      }
    ]
  },
  {
    "id": "teacher-assignments-close",
    "title": "Закрыть назначение без замены",
    "description": "Убрать преподавателя можно отдельной командой DELETE с датой окончания и ключом. Academic запускает CLOSE_UNASSIGN. Schedule убирает привязку к преподавателю у подходящих пар, сохраняя их ID. Для передачи занятий конкретному преподавателю используется команда замены.",
    "frames": [
      {
        "title": "Снятие передаёт ID назначения и исключающую дату",
        "path": "frontends/mobile-core/src/features/headman-subjects/headman-subjects-client.ts",
        "language": "typescript",
        "lines": [
          [
            310,
            "  removeTeacher("
          ],
          [
            311,
            "    subjectId: number,"
          ],
          [
            312,
            "    teacherId: number,"
          ],
          [
            313,
            "    assignmentId: number,"
          ],
          [
            314,
            "    validUntilExclusive: string,"
          ],
          [
            315,
            "    requestKey: string,"
          ],
          [
            316,
            "  ): Promise<void> {"
          ],
          [
            317,
            "    const query = new URLSearchParams({"
          ],
          [
            318,
            "      assignmentId: String(positiveInteger(assignmentId, 'assignmentId')),"
          ],
          [
            319,
            "      validUntilExclusive: requiredDate(validUntilExclusive, 'validUntilExclusive'),"
          ],
          [
            320,
            "    })"
          ],
          [
            321,
            "    return this.request<void>("
          ],
          [
            322,
            "      `/api/academic/subjects/${positiveInteger(subjectId, 'subjectId')}/teachers/${positiveInteger(teacherId, 'teacherId')}?${query}`,"
          ],
          [
            323,
            "      {"
          ],
          [
            324,
            "        method: 'DELETE',"
          ],
          [
            325,
            "        headers: { 'Idempotency-Key': uuid(requestKey, 'requestKey') },"
          ],
          [
            326,
            "      },"
          ],
          [
            327,
            "      false,"
          ],
          [
            328,
            "      204,"
          ],
          [
            329,
            "    )"
          ]
        ]
      },
      {
        "title": "Контроллер завершает снятие ответом 204",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectController.java",
        "language": "java",
        "lines": [
          [
            101,
            "    @Override"
          ],
          [
            102,
            "    @RequireRole({UserRole.STUDENT})"
          ],
          [
            103,
            "    public ResponseEntity<Void> removeTeacher("
          ],
          [
            104,
            "            Long id,"
          ],
          [
            105,
            "            Long teacherId,"
          ],
          [
            106,
            "            Long assignmentId,"
          ],
          [
            107,
            "            LocalDate validUntilExclusive,"
          ],
          [
            108,
            "            java.util.UUID requestKey) {"
          ],
          [
            109,
            "        subjectService.removeTeacher(id, teacherId, assignmentId, validUntilExclusive, requestKey);"
          ],
          [
            110,
            "        return ResponseEntity.noContent().build();"
          ],
          [
            111,
            "    }"
          ]
        ]
      },
      {
        "title": "Снятие имеет собственное намерение и проверку повтора",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assignment/AssignmentClosureService.java",
        "language": "java",
        "lines": [
          [
            82,
            "    public void close(Long id, Long subjectId, Long teacherId, LocalDate cutoff, UUID requestKey) {"
          ],
          [
            83,
            "        long actor = actor();"
          ],
          [
            84,
            "        if (id == null || id <= 0 || cutoff == null || requestKey == null) {"
          ],
          [
            85,
            "            throw new BadRequestException("
          ],
          [
            86,
            "                    \"assignment/date/key\", \"Назначение, дата и ключ обязательны\");"
          ],
          [
            87,
            "        }"
          ],
          [
            88,
            "        byte[] hash = hash(\"CLOSE_UNASSIGN|\" + id + \"|\" + cutoff);"
          ],
          [
            89,
            "        AssignmentReplacementOperation operation ="
          ],
          [
            90,
            "                transaction.execute("
          ],
          [
            91,
            "                        tx -> {"
          ],
          [
            92,
            "                            Assignment before = requiredAssignment(id);"
          ],
          [
            93,
            "                            requireTuple(before, subjectId, teacherId);"
          ],
          [
            94,
            "                            AssignmentReplacementOperation existing ="
          ],
          [
            95,
            "                                    acceptedClosure(actor, requestKey, id, hash);"
          ],
          [
            96,
            "                            if (existing != null) {"
          ],
          [
            97,
            "                                if (existing.getOperationKind()"
          ],
          [
            98,
            "                                                != AssignmentOperationKind.CLOSE_UNASSIGN"
          ],
          [
            99,
            "                                        || !MessageDigest.isEqual("
          ],
          [
            100,
            "                                                hash, existing.getPayloadHash())) {"
          ],
          [
            101,
            "                                    throw new ru.rutcampustrack.academic.exception"
          ],
          [
            102,
            "                                            .AssignmentIdempotencyConflictException();"
          ],
          [
            103,
            "                                }"
          ],
          [
            104,
            "                                lockSemester(before.getSemesterId());"
          ],
          [
            105,
            "                                return existing;"
          ]
        ]
      },
      {
        "title": "Schedule снимает привязку, сохраняя ID",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/replacement/AssignmentUnassignmentWriter.java",
        "language": "java",
        "lines": [
          [
            234,
            "            if (!Boolean.TRUE.equals(eligible)) {"
          ],
          [
            235,
            "                held++;"
          ],
          [
            236,
            "                continue;"
          ],
          [
            237,
            "            }"
          ],
          [
            238,
            "            long revision = number(occurrence.get(\"revision\"));"
          ],
          [
            239,
            "            Long lessonRevision = lesson == null ? null : number(lesson.get(\"revision\"));"
          ],
          [
            240,
            "            Long afterAssignment = close ? null : target(authority);"
          ],
          [
            241,
            "            Long afterTeacher = close ? null : authority.getTargetTeacherId();"
          ],
          [
            262,
            "            if (jdbc.update("
          ],
          [
            263,
            "                            \"UPDATE lesson_occurrences SET assignment_id=?,assigned_teacher_id=?,revision=revision+1 WHERE id=? AND revision=?\","
          ],
          [
            264,
            "                            afterAssignment,"
          ],
          [
            265,
            "                            afterTeacher,"
          ],
          [
            266,
            "                            occurrence.get(\"id\"),"
          ],
          [
            267,
            "                            revision)"
          ],
          [
            268,
            "                    != 1) conflict();"
          ],
          [
            269,
            "            if (lessonId != null"
          ],
          [
            270,
            "                    && jdbc.update("
          ],
          [
            271,
            "                                    \"UPDATE lessons SET assignment_id=?,assigned_teacher_id=?,revision=revision+1 WHERE id=? AND revision=?\","
          ],
          [
            272,
            "                                    afterAssignment,"
          ],
          [
            273,
            "                                    afterTeacher,"
          ],
          [
            274,
            "                                    lessonId,"
          ],
          [
            275,
            "                                    lessonRevision)"
          ],
          [
            276,
            "                            != 1) conflict();"
          ]
        ]
      }
    ]
  }
]
