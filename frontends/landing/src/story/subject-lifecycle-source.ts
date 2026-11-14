import type { StoryOperation } from './story-source'

export const subjectLifecycleOperations: readonly StoryOperation[] = [
  {
    "id": "subject-lifecycle-create",
    "title": "Создать предмет для своей группы",
    "description": "В «Предметах» староста задаёт название и выбирает типы занятий из лекций, практик и лабораторных. Редактор отправляет первый выбранный тип в type, полный список в lessonTypes. Academic берёт группу из контекста старосты. Без начальных назначений предмет относится к активному семестру. Если назначения есть, они задают один общий семестр и записываются вместе с предметом.",
    "frames": [
      {
        "title": "Форма собирает название, типы и начальные назначения",
        "path": "frontends/mobile-core/src/features/headman-subjects/HeadmanSubjectsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1368,
            "async function saveSubject(): Promise<void> {"
          ],
          [
            1369,
            "  const semester = editSemester.value,"
          ],
          [
            1370,
            "    actor = props.actorUserId,"
          ],
          [
            1371,
            "    group = props.groupId;"
          ],
          [
            1372,
            "  if (!editorWritable.value || !semester || !actor || group === null || !editorDirty.value) return;"
          ],
          [
            1373,
            "  const name = subjectName.value.trim(),"
          ],
          [
            1374,
            "    types = [...selectedTypes.value];"
          ],
          [
            1375,
            "  if (!name || !types.length) {"
          ],
          [
            1376,
            "    error.value = 'Укажи название и хотя бы один тип занятия.';"
          ],
          [
            1377,
            "    return;"
          ],
          [
            1378,
            "  }"
          ],
          [
            1379,
            "  const assignments = types.flatMap((lessonType) =>"
          ],
          [
            1380,
            "    selectedTeachers[lessonType]"
          ],
          [
            1381,
            "      .filter((teacher) => teacher.assignmentId === undefined)"
          ],
          [
            1382,
            "      .map((teacher) => ({"
          ],
          [
            1383,
            "        teacherId: teacher.id,"
          ],
          [
            1384,
            "        semesterId: semester.id,"
          ],
          [
            1385,
            "        lessonType,"
          ],
          [
            1386,
            "        validFrom: draftValidFrom[lessonType]?.[teacher.id] ?? '',"
          ],
          [
            1387,
            "        validUntilExclusive: draftValidUntil[lessonType]?.[teacher.id] ?? '',"
          ],
          [
            1388,
            "      })),"
          ],
          [
            1389,
            "  );"
          ]
        ]
      },
      {
        "title": "Клиент отправляет POST /api/academic/subjects",
        "path": "frontends/mobile-core/src/features/headman-subjects/headman-subjects-client.ts",
        "language": "typescript",
        "lines": [
          [
            258,
            "  createSubject(input: CreateHeadmanSubjectInput): Promise<HeadmanSubject> {"
          ],
          [
            259,
            "    const payload = normalizeCreateInput(input)"
          ],
          [
            260,
            "    return this.request<unknown>("
          ],
          [
            261,
            "      '/api/academic/subjects',"
          ],
          [
            262,
            "      {"
          ],
          [
            263,
            "        method: 'POST',"
          ],
          [
            264,
            "        body: JSON.stringify(payload),"
          ],
          [
            265,
            "      },"
          ],
          [
            266,
            "      false,"
          ],
          [
            267,
            "      201,"
          ]
        ]
      },
      {
        "title": "Первый тип и полный список переходят в запрос",
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
          ]
        ]
      },
      {
        "title": "Создание предмета с лекциями и практикой",
        "language": "json",
        "example": [
          "{",
          "  \"name\": \"Основы программирования\",",
          "  \"type\": \"LECTURE\",",
          "  \"lessonTypes\": [",
          "    \"LECTURE\",",
          "    \"PRACTICE\"",
          "  ],",
          "  \"initialAssignments\": []",
          "}"
        ]
      },
      {
        "title": "Academic выбирает единственный семестр",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java",
        "language": "java",
        "lines": [
          [
            115,
            "    public Subject createSubject(CreateSubjectRequest request) {"
          ],
          [
            116,
            "        Long groupId = requireHeadmanGroupId();"
          ],
          [
            117,
            "        if (request.teacherIds() != null && !request.teacherIds().isEmpty()) {"
          ],
          [
            118,
            "            throw new BadRequestException("
          ],
          [
            119,
            "                    \"teacherIds\", \"Используйте initialAssignments с полными данными назначения\");"
          ],
          [
            120,
            "        }"
          ],
          [
            121,
            "        List<SubjectType> lessonTypes = canonicalLessonTypes(request.type(), request.lessonTypes());"
          ],
          [
            122,
            "        List<InitialAssignmentRequest> initialAssignments ="
          ],
          [
            123,
            "                request.initialAssignments() == null ? List.of() : request.initialAssignments();"
          ],
          [
            127,
            "        List<Long> semesterIds ="
          ],
          [
            128,
            "                initialAssignments.stream()"
          ],
          [
            129,
            "                        .map(InitialAssignmentRequest::semesterId)"
          ],
          [
            130,
            "                        .distinct()"
          ],
          [
            131,
            "                        .sorted()"
          ],
          [
            132,
            "                        .toList();"
          ],
          [
            133,
            "        if (semesterIds.size() > 1) {"
          ],
          [
            134,
            "            throw new ConflictException(\"Предмет должен принадлежать одному семестру\");"
          ],
          [
            135,
            "        }"
          ],
          [
            136,
            "        if (semesterIds.isEmpty()) {"
          ],
          [
            137,
            "            semesterIds = List.of(requireActiveSemester().getId());"
          ],
          [
            138,
            "        }"
          ],
          [
            139,
            "        java.util.Map<Long, Semester> lockedSemesters = new java.util.LinkedHashMap<>();"
          ],
          [
            140,
            "        for (Long semesterId : semesterIds) {"
          ],
          [
            141,
            "            lockedSemesters.put(semesterId, assignmentAuthority.lockSemester(semesterId));"
          ],
          [
            142,
            "        }"
          ]
        ]
      },
      {
        "title": "Предмет, типы и назначения сохраняются вместе",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java",
        "language": "java",
        "lines": [
          [
            144,
            "        Subject subject = new Subject();"
          ],
          [
            145,
            "        subject.setName(request.name());"
          ],
          [
            146,
            "        subject.setType(request.type());"
          ],
          [
            147,
            "        subject.setGroupId(groupId);"
          ],
          [
            148,
            "        subject.setSemesterId(semesterIds.getFirst());"
          ],
          [
            149,
            "        Subject saved = subjectRepository.save(subject);"
          ],
          [
            150,
            "        lessonTypeRepository.saveAll("
          ],
          [
            151,
            "                lessonTypes.stream()"
          ],
          [
            152,
            "                        .map(type -> new SubjectLessonType(saved.getId(), type))"
          ],
          [
            153,
            "                        .toList());"
          ],
          [
            154,
            ""
          ],
          [
            155,
            "        assignmentAuthority.createInitialAssignmentsForNewSubject("
          ],
          [
            156,
            "                saved.getId(), groupId, lockedSemesters, initialAssignments);"
          ],
          [
            157,
            "        return saved;"
          ],
          [
            158,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "subject-lifecycle-edit",
    "title": "Изменить название и типы занятий",
    "description": "Староста своей группы может поменять название и набор типов. Academic принимает от одного до трёх уникальных типов и проверяет, что основной тип входит в список. Для типа с любым сохранённым назначением действует защита истории, поэтому снять такой тип через редактирование предмета нельзя.",
    "frames": [
      {
        "title": "Редактор переключает выбранные типы",
        "path": "frontends/mobile-core/src/features/headman-subjects/HeadmanSubjectsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1166,
            "function toggleType(type: HeadmanSubjectType): void {"
          ],
          [
            1167,
            "  if (editorWritable.value)"
          ],
          [
            1168,
            "    selectedTypes.value = selectedTypes.value.includes(type)"
          ],
          [
            1169,
            "      ? selectedTypes.value.filter((item) => item !== type)"
          ],
          [
            1170,
            "      : [...selectedTypes.value, type];"
          ],
          [
            1171,
            "}"
          ]
        ]
      },
      {
        "title": "Сначала сохраняются название и типы предмета",
        "path": "frontends/mobile-core/src/features/headman-subjects/subject-save-flow.ts",
        "language": "typescript",
        "lines": [
          [
            349,
            "          } else {"
          ],
          [
            350,
            "            const removal = subjectEditRemoval(plan),"
          ],
          [
            351,
            "              assignment = subjectEditAssignment(plan)"
          ],
          [
            352,
            "            if (plan.step === 0)"
          ],
          [
            353,
            "              await api.updateSubject(plan.subjectId, {"
          ],
          [
            354,
            "                name: plan.name,"
          ],
          [
            355,
            "                type: plan.lessonTypes[0]!,"
          ],
          [
            356,
            "                lessonTypes: plan.lessonTypes,"
          ],
          [
            357,
            "              })"
          ]
        ]
      },
      {
        "title": "Основной тип обязателен и входит в набор",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java",
        "language": "java",
        "lines": [
          [
            160,
            "    private static List<SubjectType> canonicalLessonTypes("
          ],
          [
            161,
            "            SubjectType scalarType, List<SubjectType> requested) {"
          ],
          [
            162,
            "        if (scalarType == null) {"
          ],
          [
            163,
            "            throw new BadRequestException(\"type\", \"Тип предмета обязателен\");"
          ],
          [
            164,
            "        }"
          ],
          [
            165,
            "        if (requested == null) {"
          ],
          [
            166,
            "            return List.of(scalarType);"
          ],
          [
            167,
            "        }"
          ],
          [
            168,
            "        if (requested.isEmpty()"
          ],
          [
            169,
            "                || requested.size() > 3"
          ],
          [
            170,
            "                || requested.stream().anyMatch(Objects::isNull)) {"
          ],
          [
            171,
            "            throw new BadRequestException("
          ],
          [
            172,
            "                    \"lessonTypes\", \"Предмет должен иметь от 1 до 3 типов занятий\");"
          ],
          [
            173,
            "        }"
          ],
          [
            174,
            "        Set<SubjectType> distinct = new LinkedHashSet<>(requested);"
          ],
          [
            175,
            "        if (distinct.size() != requested.size() || !distinct.contains(scalarType)) {"
          ],
          [
            176,
            "            throw new BadRequestException("
          ],
          [
            177,
            "                    \"lessonTypes\", \"Тип предмета должен входить в список уникальных типов занятий\");"
          ],
          [
            178,
            "        }"
          ],
          [
            179,
            "        return List.copyOf(distinct);"
          ],
          [
            180,
            "    }"
          ]
        ]
      },
      {
        "title": "Сервер проверяет свою группу и историю назначений",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java",
        "language": "java",
        "lines": [
          [
            238,
            "    @Transactional"
          ],
          [
            239,
            "    public Subject updateSubject(Long id, UpdateSubjectRequest request) {"
          ],
          [
            240,
            "        Long groupId = requireHeadmanGroupId();"
          ],
          [
            241,
            "        Subject subject = lockSubject(id);"
          ],
          [
            242,
            "        assertSubjectBelongsToHeadmanGroup(subject, groupId);"
          ],
          [
            243,
            "        List<SubjectType> requestedTypes ="
          ],
          [
            244,
            "                canonicalLessonTypes(request.type(), request.lessonTypes());"
          ],
          [
            245,
            "        List<SubjectLessonType> existing = lessonTypeRepository.findBySubjectId(id);"
          ],
          [
            246,
            "        for (SubjectLessonType row : existing) {"
          ],
          [
            247,
            "            if (!requestedTypes.contains(row.getLessonType())"
          ],
          [
            248,
            "                    && assignmentRepository.existsBySubjectIdAndLessonType("
          ],
          [
            249,
            "                            id, row.getLessonType())) {"
          ],
          [
            250,
            "                throw new ConflictException(\"Нельзя удалить тип занятия с историей назначений\");"
          ],
          [
            251,
            "            }"
          ]
        ]
      },
      {
        "title": "Сохраняются изменения типов, названия и основного типа",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subject/SubjectService.java",
        "language": "java",
        "lines": [
          [
            257,
            "        lessonTypeRepository.deleteAll("
          ],
          [
            258,
            "                existing.stream()"
          ],
          [
            259,
            "                        .filter(row -> !requestedTypes.contains(row.getLessonType()))"
          ],
          [
            260,
            "                        .toList());"
          ],
          [
            261,
            "        lessonTypeRepository.saveAll("
          ],
          [
            262,
            "                requestedTypes.stream()"
          ],
          [
            263,
            "                        .filter(type -> !existingTypes.contains(type))"
          ],
          [
            264,
            "                        .map(type -> new SubjectLessonType(id, type))"
          ],
          [
            265,
            "                        .toList());"
          ],
          [
            266,
            "        subject.setName(request.name());"
          ],
          [
            267,
            "        subject.setType(request.type());"
          ],
          [
            268,
            "        return subjectRepository.save(subject);"
          ],
          [
            269,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "subject-lifecycle-preview",
    "title": "Проверить последствия удаления",
    "description": "Полное удаление открывается в веб-панели старосты. Предпросмотр показывает предмет, группу, семестр, объём затронутых данных и число заявок на занятия нескольких предметов. Academic проверяет действующего старосту этой группы и доступный для записи активный семестр. Снимок связывает будущую команду с тем набором данных, который показан пользователю.",
    "frames": [
      {
        "title": "Web открывает отдельный экран удаления",
        "path": "frontends/web-vue/src/features/headman/SubjectsPage.vue",
        "language": "typescript",
        "lines": [
          [
            427,
            "    <SubjectDeletion"
          ],
          [
            428,
            "      v-if=\"deleting\""
          ],
          [
            429,
            "      :key=\"deleting\""
          ],
          [
            430,
            "      :feature=\"feature\""
          ],
          [
            431,
            "      :subject-id=\"deleting\""
          ],
          [
            432,
            "      :semester-id=\"semesterId\""
          ],
          [
            433,
            "      @completed=\""
          ],
          [
            434,
            "        deleting = 0;"
          ],
          [
            435,
            "        load();"
          ],
          [
            436,
            "      \""
          ],
          [
            437,
            "      @close=\"deleting = 0\""
          ],
          [
            438,
            "    />"
          ]
        ]
      },
      {
        "title": "Клиент сверяет группу и семестр предпросмотра",
        "path": "frontends/web-vue/src/features/headman/SubjectDeletion.vue",
        "language": "typescript",
        "lines": [
          [
            57,
            "    const value = await api.preview(props.subjectId);"
          ],
          [
            58,
            "    props.feature.assertCurrent();"
          ],
          [
            59,
            "    if ("
          ],
          [
            60,
            "      value.groupId !== String(props.feature.groupId) ||"
          ],
          [
            61,
            "      value.semesterId !== String(props.semesterId)"
          ],
          [
            62,
            "    )"
          ],
          [
            63,
            "      throw new Error('Удаление относится к другому семестру или группе.');"
          ],
          [
            64,
            "    preview.value = value;"
          ]
        ]
      },
      {
        "title": "Предмет должен относиться к доступному семестру",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionAcademicData.java",
        "language": "java",
        "lines": [
          [
            48,
            "        if (rows.isEmpty())"
          ],
          [
            49,
            "            throw new ResponseStatusException(HttpStatus.NOT_FOUND, \"Предмет не найден\");"
          ],
          [
            50,
            "        Target target = rows.getFirst();"
          ],
          [
            51,
            "        actor(target.group());"
          ],
          [
            52,
            "        if (target.semester() == null) throw conflict();"
          ],
          [
            53,
            "        writable(target.semester());"
          ],
          [
            54,
            "        return target;"
          ],
          [
            55,
            "    }"
          ],
          [
            93,
            "    public void writable(long semester) {"
          ],
          [
            94,
            "        if (!Boolean.TRUE.equals("
          ],
          [
            95,
            "                jdbc.queryForObject("
          ],
          [
            96,
            "                        \"\"\""
          ],
          [
            97,
            "SELECT EXISTS(SELECT 1 FROM semesters WHERE id=? AND is_active AND NOT is_archived"
          ],
          [
            98,
            "    AND archive_transition='NONE' AND NOT archive_release_pending)"
          ],
          [
            99,
            "\"\"\","
          ],
          [
            100,
            "                        Boolean.class,"
          ],
          [
            101,
            "                        semester))) throw conflict();"
          ],
          [
            102,
            "    }"
          ]
        ]
      },
      {
        "title": "Academic сверяет активную роль, группу и право записи",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionAcademicData.java",
        "language": "java",
        "lines": [
          [
            58,
            "    public InternalJwtClaims actor(Long group) {"
          ],
          [
            59,
            "        var claims = context.getClaims();"
          ],
          [
            60,
            "        if (claims == null"
          ],
          [
            61,
            "                || !\"HEADMAN\".equals(claims.role())"
          ],
          [
            62,
            "                || !\"ACTIVE\".equals(claims.status())"
          ],
          [
            63,
            "                || !claims.isHeadman()"
          ],
          [
            64,
            "                || claims.readOnly()"
          ],
          [
            65,
            "                || context.getAdmittedAdminProfile() == null"
          ],
          [
            66,
            "                || claims.sessionId() == null"
          ],
          [
            67,
            "                || claims.sessionVersion() <= 0"
          ],
          [
            68,
            "                || claims.rolesVersion() <= 0"
          ],
          [
            69,
            "                || claims.groupId() == null"
          ],
          [
            70,
            "                || group != null && !Objects.equals(group, claims.groupId())) throw denied();"
          ],
          [
            71,
            "        localActor(claims.userId(), claims.groupId());"
          ],
          [
            72,
            "        return claims;"
          ]
        ]
      },
      {
        "title": "Ответ предпросмотра содержит объём данных и смешанные заявки",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/subject/SubjectDeletionModels.java",
        "language": "java",
        "lines": [
          [
            20,
            "    public record Preview("
          ],
          [
            21,
            "            String subjectId,"
          ],
          [
            22,
            "            String semesterId,"
          ],
          [
            23,
            "            String groupId,"
          ],
          [
            24,
            "            String subjectName,"
          ],
          [
            25,
            "            String previewDigest,"
          ],
          [
            26,
            "            long academicRows,"
          ],
          [
            27,
            "            long scheduleRows,"
          ],
          [
            28,
            "            long attendanceRows,"
          ],
          [
            29,
            "            long mixedTickets) {}"
          ]
        ]
      }
    ]
  },
  {
    "id": "subject-lifecycle-auth",
    "title": "Подтвердить намерение",
    "description": "Для этой операции Auth проверяет текущий пароль старосты. Затем запрашивается отправка кода в привязанный Telegram. Academic заново сверяет снимок. Код подтверждает только эту операцию и её снимок. При изменившихся данных нужен новый предпросмотр.",
    "frames": [
      {
        "title": "Academic сначала сверяет снимок",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionService.java",
        "language": "java",
        "lines": [
          [
            42,
            "    public Challenge challenge(long subject, ChallengeRequest request) {"
          ],
          [
            43,
            "        var target = data.target(subject);"
          ],
          [
            44,
            "        var snapshot = snapshot(target);"
          ],
          [
            45,
            "        if (!snapshot.digest().equals(request.previewDigest()))"
          ],
          [
            46,
            "            throw SubjectDeletionAcademicData.conflict();"
          ],
          [
            47,
            "        if (commands.get(request.operationId()) != null)"
          ],
          [
            48,
            "            throw SubjectDeletionAcademicData.conflict();"
          ],
          [
            49,
            "        return auth.challenge(target, request);"
          ],
          [
            50,
            "    }"
          ]
        ]
      },
      {
        "title": "Auth проверяет пароль перед выдачей кода",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/SubjectDeletionProofService.java",
        "language": "java",
        "lines": [
          [
            91,
            "        if (!bcrypt.execute(() -> passwords.matches(request.password(), actor.passwordHash()))) {"
          ],
          [
            92,
            "            limiter.recordFailure(\"internal-subject-deletion\", bucket);"
          ],
          [
            93,
            "            throw denied();"
          ],
          [
            94,
            "        }"
          ]
        ]
      },
      {
        "title": "Auth ставит отправку кода в очередь",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/SubjectDeletionProofService.java",
        "language": "java",
        "lines": [
          [
            149,
            "        append("
          ],
          [
            150,
            "                new OtpRequestedEvent("
          ],
          [
            151,
            "                        this,"
          ],
          [
            152,
            "                        actor.telegram(),"
          ],
          [
            153,
            "                        code,"
          ],
          [
            154,
            "                        otp.ttlSeconds(),"
          ],
          [
            155,
            "                        PURPOSE,"
          ],
          [
            156,
            "                        challenge,"
          ],
          [
            157,
            "                        otp.maxAttempts()));"
          ],
          [
            158,
            "        return new Challenge(challenge, expiry, otp.maxAttempts());"
          ]
        ]
      },
      {
        "title": "Подтверждение привязано к снимку и операции",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionService.java",
        "language": "java",
        "lines": [
          [
            61,
            "        try {"
          ],
          [
            62,
            "            var target = data.target(subject);"
          ],
          [
            63,
            "            var snapshot = snapshot(target);"
          ],
          [
            64,
            "            if (!snapshot.digest().equals(request.previewDigest()))"
          ],
          [
            65,
            "                throw SubjectDeletionAcademicData.conflict();"
          ],
          [
            66,
            "            UUID proof = auth.verify(target, request);"
          ],
          [
            67,
            "            commands.accept("
          ],
          [
            68,
            "                    request.operationId(),"
          ],
          [
            69,
            "                    key,"
          ],
          [
            70,
            "                    auth.check(target, request.operationId(), snapshot.digest(), proof),"
          ],
          [
            71,
            "                    confirmationHash(key, request),"
          ],
          [
            72,
            "                    snapshot);"
          ]
        ]
      }
    ]
  },
  {
    "id": "subject-lifecycle-replay",
    "title": "Проверить ход принятой операции",
    "description": "Web сохраняет ID операции и ключ перед подтверждением. При неизвестном результате экран проверяет исходную операцию по ID. Сервер сверяет ключ и параметры принятой команды. Подтверждение возвращает 200 для завершённой операции или 202 для остальных состояний. Итог определяет поле phase, включая отмену CANCELLED.",
    "frames": [
      {
        "title": "Перед отправкой сохраняются только ID и ключ операции",
        "path": "frontends/web-vue/src/features/headman/SubjectDeletion.vue",
        "language": "typescript",
        "lines": [
          [
            101,
            "  error.value = null;"
          ],
          [
            102,
            "  try {"
          ],
          [
            103,
            "    sessionStorage.setItem("
          ],
          [
            104,
            "      storageKey,"
          ],
          [
            105,
            "      JSON.stringify({ operationId: operationId.value, key: key.value }),"
          ],
          [
            106,
            "    );"
          ],
          [
            107,
            "    sent.value = true;"
          ],
          [
            116,
            "  } catch (cause) {"
          ],
          [
            117,
            "    if (cause instanceof SubjectDeletionError && [400, 401, 403, 422].includes(cause.status)) {"
          ],
          [
            118,
            "      sent.value = false;"
          ],
          [
            119,
            "      sessionStorage.removeItem(storageKey);"
          ],
          [
            120,
            "      error.value = message(cause);"
          ],
          [
            121,
            "    } else"
          ],
          [
            122,
            "      error.value = `${message(cause)}. Проверяй исходную операцию; удаление повторно не отправляется.`;"
          ],
          [
            123,
            "  } finally {"
          ],
          [
            124,
            "    code.value = '';"
          ],
          [
            125,
            "    challenge.value = null;"
          ],
          [
            126,
            "    busy.value = false;"
          ],
          [
            127,
            "  }"
          ]
        ]
      },
      {
        "title": "Повторное открытие проверяет прежнюю операцию",
        "path": "frontends/web-vue/src/features/headman/SubjectDeletion.vue",
        "language": "typescript",
        "lines": [
          [
            39,
            "    const raw = sessionStorage.getItem(storageKey);"
          ],
          [
            40,
            "    if (raw) {"
          ],
          [
            41,
            "      const value: unknown = JSON.parse(raw);"
          ],
          [
            42,
            "      if ("
          ],
          [
            43,
            "        !value ||"
          ],
          [
            44,
            "        typeof value !== 'object' ||"
          ],
          [
            45,
            "        !('operationId' in value) ||"
          ],
          [
            46,
            "        typeof value.operationId !== 'string' ||"
          ],
          [
            47,
            "        !('key' in value) ||"
          ],
          [
            48,
            "        typeof value.key !== 'string'"
          ],
          [
            49,
            "      )"
          ],
          [
            50,
            "        throw new Error('Не удалось прочитать исходную операцию удаления.');"
          ],
          [
            51,
            "      operationId.value = value.operationId;"
          ],
          [
            52,
            "      key.value = value.key;"
          ],
          [
            53,
            "      sent.value = true;"
          ],
          [
            54,
            "      accept(await api.status(operationId.value));"
          ],
          [
            55,
            "      return;"
          ],
          [
            129,
            "async function check() {"
          ],
          [
            130,
            "  if (busy.value) return;"
          ],
          [
            131,
            "  busy.value = true;"
          ],
          [
            132,
            "  try {"
          ],
          [
            133,
            "    accept(await api.status(operationId.value));"
          ],
          [
            134,
            "    error.value = null;"
          ]
        ]
      },
      {
        "title": "Тот же ключ проверяет принятую операцию",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionService.java",
        "language": "java",
        "lines": [
          [
            52,
            "    public Operation confirm(long subject, UUID key, ConfirmRequest request) {"
          ],
          [
            53,
            "        var existing = commands.get(request.operationId());"
          ],
          [
            54,
            "        UUID keyed = commands.byKey(key);"
          ],
          [
            55,
            "        if (existing != null || keyed != null) {"
          ],
          [
            56,
            "            if (existing == null || !existing.id().equals(keyed))"
          ],
          [
            57,
            "                throw SubjectDeletionAcademicData.conflict();"
          ],
          [
            58,
            "            sameRequest(existing, subject, key, request);"
          ],
          [
            59,
            "            return status(existing.id());"
          ],
          [
            60,
            "        }"
          ],
          [
            104,
            "    private void sameRequest("
          ],
          [
            105,
            "            SubjectDeletionOperation operation, long subject, UUID key, ConfirmRequest request) {"
          ],
          [
            106,
            "        var actor = data.actor(operation.group());"
          ],
          [
            107,
            "        if (operation.subject() != subject"
          ],
          [
            108,
            "                || !operation.key().equals(key)"
          ],
          [
            109,
            "                || actor.userId() != operation.actor()"
          ],
          [
            110,
            "                || !actor.sessionId().equals(operation.sid())"
          ],
          [
            111,
            "                || !request.previewDigest().equals(operation.digest())"
          ],
          [
            112,
            "                || !operation.confirmationHash().equals(confirmationHash(key, request)))"
          ],
          [
            113,
            "            throw SubjectDeletionAcademicData.conflict();"
          ],
          [
            114,
            "    }"
          ]
        ]
      },
      {
        "title": "Ключ идемпотентности и запрос состояния",
        "path": "frontends/web-vue/src/features/headman/subject-deletion-client.ts",
        "language": "typescript",
        "lines": [
          [
            139,
            "  async confirm(id: number, key: string, input: SubjectDeletionConfirmation) {"
          ],
          [
            140,
            "    return this.operation("
          ],
          [
            141,
            "      await this.request(`/api/academic/subjects/${id}/deletion`, {"
          ],
          [
            142,
            "        method: 'POST',"
          ],
          [
            143,
            "        headers: { 'Idempotency-Key': key },"
          ],
          [
            144,
            "        body: JSON.stringify(input),"
          ],
          [
            145,
            "      }),"
          ],
          [
            146,
            "      input.operationId,"
          ],
          [
            147,
            "    )"
          ],
          [
            148,
            "  }"
          ],
          [
            149,
            "  async status(operationId: string) {"
          ],
          [
            150,
            "    return this.operation("
          ],
          [
            151,
            "      await this.request("
          ],
          [
            152,
            "        `/api/academic/subject-deletion-operations/${encodeURIComponent(operationId)}`,"
          ],
          [
            153,
            "      ),"
          ],
          [
            154,
            "      operationId,"
          ],
          [
            155,
            "    )"
          ],
          [
            156,
            "  }"
          ]
        ]
      },
      {
        "title": "Подтверждение и чтение состояния",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionController.java",
        "language": "java",
        "lines": [
          [
            31,
            "    @Override"
          ],
          [
            32,
            "    public ResponseEntity<Operation> confirm(long id, UUID key, ConfirmRequest request) {"
          ],
          [
            33,
            "        var result = service.confirm(id, key, request);"
          ],
          [
            34,
            "        return ResponseEntity.status(result.phase() == Phase.COMPLETED ? 200 : 202)"
          ],
          [
            35,
            "                .cacheControl(CacheControl.noStore())"
          ],
          [
            36,
            "                .body(result);"
          ],
          [
            37,
            "    }"
          ],
          [
            38,
            ""
          ],
          [
            39,
            "    @Override"
          ],
          [
            40,
            "    public ResponseEntity<Operation> status(UUID operationId) {"
          ],
          [
            41,
            "        return ResponseEntity.ok()"
          ],
          [
            42,
            "                .cacheControl(CacheControl.noStore())"
          ],
          [
            43,
            "                .body(service.status(operationId));"
          ],
          [
            44,
            "    }"
          ]
        ]
      },
      {
        "title": "HTTP 202 · участник временно недоступен",
        "language": "json",
        "example": [
          "{",
          "  \"operationId\": \"51ab1058-47cc-42df-b2b7-52476db54294\",",
          "  \"subjectId\": \"18\",",
          "  \"semesterId\": \"3\",",
          "  \"phase\": \"COMMITTING\",",
          "  \"retryable\": true,",
          "  \"reason\": \"DEPENDENCY_UNAVAILABLE\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "subject-lifecycle-commit",
    "title": "Закрепить снимок у участников",
    "description": "Schedule и Attendance сначала принимают и закрепляют снимок. Academic повторно проверяет подтверждение Auth перед переходом в COMMITTING. До этой стадии устаревшее подтверждение или истечение подготовки могут привести к отмене и снятию блокировок. После начала необратимого этапа координатор продолжает принятую операцию по сохранённым состояниям участников.",
    "frames": [
      {
        "title": "Подготовка может завершиться освобождением блокировок",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionCoordinator.java",
        "language": "java",
        "lines": [
          [
            43,
            "    public synchronized void advance(UUID id) {"
          ],
          [
            44,
            "        SubjectDeletionOperation operation = commands.get(id);"
          ],
          [
            45,
            "        if (operation == null) return;"
          ],
          [
            46,
            "        try {"
          ],
          [
            47,
            "            if ((operation.phase() == Phase.PREPARING || operation.phase() == Phase.SEALED)"
          ],
          [
            48,
            "                    && operation.created().plusSeconds(300).isBefore(clock.instant())) {"
          ],
          [
            49,
            "                commands.release(id, \"PREPARE_EXPIRED\");"
          ],
          [
            50,
            "                operation = commands.get(id);"
          ],
          [
            51,
            "            }"
          ],
          [
            52,
            "            if (operation.phase() == Phase.RELEASING) {"
          ],
          [
            53,
            "                release(operation);"
          ],
          [
            54,
            "                return;"
          ],
          [
            55,
            "            }"
          ]
        ]
      },
      {
        "title": "Schedule и Attendance закрепляют снимки",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionCoordinator.java",
        "language": "java",
        "lines": [
          [
            56,
            "            if (operation.phase() == Phase.PREPARING || operation.phase() == Phase.SEALED) {"
          ],
          [
            57,
            "                if (!operation.scheduleState().equals(\"SEALED\")) {"
          ],
          [
            58,
            "                    participants.schedule(operation.command(true, SubjectDeletionAction.PREPARE));"
          ],
          [
            59,
            "                    commands.participant(id, true, \"PREPARED\");"
          ],
          [
            60,
            "                    participants.schedule(operation.command(true, SubjectDeletionAction.SEAL));"
          ],
          [
            61,
            "                    commands.participant(id, true, \"SEALED\");"
          ],
          [
            62,
            "                }"
          ],
          [
            63,
            "                if (!operation.attendanceState().equals(\"SEALED\")) {"
          ],
          [
            64,
            "                    participants.attendance("
          ],
          [
            65,
            "                            operation.command(false, SubjectDeletionAction.PREPARE));"
          ],
          [
            66,
            "                    commands.participant(id, false, \"PREPARED\");"
          ],
          [
            67,
            "                    participants.attendance(operation.command(false, SubjectDeletionAction.SEAL));"
          ],
          [
            68,
            "                    commands.participant(id, false, \"SEALED\");"
          ],
          [
            69,
            "                }"
          ]
        ]
      },
      {
        "title": "Перед необратимым этапом проверяется подтверждение",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionCoordinator.java",
        "language": "java",
        "lines": [
          [
            70,
            "                // Auth admission finishes before local locks. Its expiry/exact tuple is checked"
          ],
          [
            71,
            "                // again in the local transition; this is not a distributed transaction and does"
          ],
          [
            72,
            "                // not promise to cancel a write already admitted when a later revocation occurs."
          ],
          [
            73,
            "                var admitted = auth.checkStored(operation);"
          ],
          [
            74,
            "                if (!commands.beginCommit(id, admitted)) {"
          ],
          [
            75,
            "                    commands.release(id, \"PROOF_OR_SCOPE_STALE\");"
          ],
          [
            76,
            "                    return;"
          ],
          [
            77,
            "                }"
          ],
          [
            78,
            "                operation = commands.get(id);"
          ],
          [
            79,
            "            }"
          ],
          [
            80,
            "            if (operation.phase() == Phase.COMMITTING) {"
          ],
          [
            81,
            "                if (!operation.scheduleState().equals(\"DELETED\")) {"
          ],
          [
            82,
            "                    participants.schedule(operation.command(true, SubjectDeletionAction.COMMIT));"
          ],
          [
            83,
            "                    commands.participant(id, true, \"DELETED\");"
          ],
          [
            84,
            "                }"
          ],
          [
            85,
            "                if (!operation.attendanceState().equals(\"DELETED\")) {"
          ],
          [
            86,
            "                    participants.attendance(operation.command(false, SubjectDeletionAction.COMMIT));"
          ],
          [
            87,
            "                    commands.participant(id, false, \"DELETED\");"
          ],
          [
            88,
            "                }"
          ],
          [
            89,
            "                commands.finish(id);"
          ],
          [
            90,
            "            }"
          ]
        ]
      }
    ]
  },
  {
    "id": "subject-lifecycle-schedule",
    "title": "Убрать пары, шаблоны и домашние задания",
    "description": "Schedule удаляет записи пар, их occurrence, шаблоны и разовые занятия предмета. Связанные привязки ДЗ архивируются и освобождаются от ID задания. После подтверждений Schedule и Attendance сервис Academic удаляет сами задания, отметки их выполнения, назначения преподавателей, типы занятий, пороги и запись предмета.",
    "frames": [
      {
        "title": "Schedule архивирует привязки и удаляет пары и серии",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/subjectdeletion/ScheduleSubjectDeletionParticipant.java",
        "language": "java",
        "lines": [
          [
            148,
            "            jdbc.update("
          ],
          [
            149,
            "                    \"\"\""
          ],
          [
            150,
            "UPDATE lesson_homework_bindings SET state='ARCHIVED',homework_id=NULL,revision=revision+1,updated_at=NOW()"
          ],
          [
            151,
            "WHERE subject_id=? AND (state<>'ARCHIVED' OR homework_id IS NOT NULL)"
          ],
          [
            152,
            "\"\"\","
          ],
          [
            153,
            "                    command.getSubjectId());"
          ],
          [
            154,
            "            jdbc.update("
          ],
          [
            155,
            "                    \"UPDATE schedule_one_off_lessons SET physical_lesson_id=NULL WHERE subject_id=?\""
          ],
          [
            156,
            "                            + \" AND physical_lesson_id IS NOT NULL\","
          ],
          [
            157,
            "                    command.getSubjectId());"
          ],
          [
            158,
            "            jdbc.update("
          ],
          [
            159,
            "                    \"DELETE FROM lesson_lifecycle_entries entry USING lesson_occurrences occurrence\""
          ],
          [
            160,
            "                        + \" WHERE entry.occurrence_id=occurrence.id AND occurrence.subject_id=?\","
          ],
          [
            161,
            "                    command.getSubjectId());"
          ],
          [
            162,
            "            jdbc.update(\"DELETE FROM lessons WHERE subject_id=?\", command.getSubjectId());"
          ],
          [
            163,
            "            jdbc.update("
          ],
          [
            164,
            "                    \"DELETE FROM lesson_occurrences WHERE subject_id=?\", command.getSubjectId());"
          ],
          [
            165,
            "            jdbc.update(\"DELETE FROM schedule_items WHERE subject_id=?\", command.getSubjectId());"
          ],
          [
            166,
            "            jdbc.update("
          ],
          [
            167,
            "                    \"DELETE FROM schedule_one_off_lessons WHERE subject_id=?\","
          ],
          [
            168,
            "                    command.getSubjectId());"
          ]
        ]
      },
      {
        "title": "Academic ждёт удаления у обоих участников",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionCommands.java",
        "language": "java",
        "lines": [
          [
            276,
            "    @Transactional"
          ],
          [
            277,
            "    public void finish(UUID operation) {"
          ],
          [
            278,
            "        SubjectDeletionOperation before = get(operation);"
          ],
          [
            279,
            "        if (before == null || before.phase() == Phase.COMPLETED) return;"
          ],
          [
            280,
            "        lockTarget(before.subject(), before.semester());"
          ],
          [
            281,
            "        var current = lockOperation(operation);"
          ],
          [
            282,
            "        if (current.phase() != Phase.COMMITTING"
          ],
          [
            283,
            "                || !current.scheduleState().equals(\"DELETED\")"
          ],
          [
            284,
            "                || !current.attendanceState().equals(\"DELETED\")) return;"
          ],
          [
            285,
            "        if (!data.snapshot(target(current)).digest().equals(current.academicDigest()))"
          ],
          [
            286,
            "            throw SubjectDeletionAcademicData.conflict();"
          ]
        ]
      },
      {
        "title": "Academic удаляет ДЗ, выполнение и предмет",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/subjectdeletion/SubjectDeletionCommands.java",
        "language": "java",
        "lines": [
          [
            304,
            "        jdbc.update("
          ],
          [
            305,
            "                \"DELETE FROM homework_completions c USING homeworks h WHERE c.homework_id=h.id AND\""
          ],
          [
            306,
            "                        + \" h.subject_id=?\","
          ],
          [
            307,
            "                current.subject());"
          ],
          [
            308,
            "        jdbc.update(\"DELETE FROM homeworks WHERE subject_id=?\", current.subject());"
          ],
          [
            309,
            "        jdbc.update(\"DELETE FROM teacher_subject_groups WHERE subject_id=?\", current.subject());"
          ],
          [
            310,
            "        jdbc.update(\"DELETE FROM assignments WHERE subject_id=?\", current.subject());"
          ],
          [
            311,
            "        jdbc.update(\"DELETE FROM subject_lesson_types WHERE subject_id=?\", current.subject());"
          ],
          [
            312,
            "        jdbc.update(\"DELETE FROM attendance_thresholds WHERE subject_id=?\", current.subject());"
          ],
          [
            313,
            "        if (jdbc.update("
          ],
          [
            314,
            "                        \"DELETE FROM subjects WHERE id=? AND semester_id=? AND group_id=?\","
          ],
          [
            315,
            "                        current.subject(),"
          ],
          [
            316,
            "                        current.semester(),"
          ],
          [
            317,
            "                        current.group())"
          ],
          [
            318,
            "                != 1) throw SubjectDeletionAcademicData.conflict();"
          ]
        ]
      }
    ]
  },
  {
    "id": "subject-lifecycle-attendance",
    "title": "Сохранить части заявок по другим предметам",
    "description": "Attendance удаляет отметки и заявки на позднюю отметку по затронутым парам. Если уважительная заявка охватывает только удаляемые пары, вместе с ней удаляются прикреплённые к ней документы. Для заявки на несколько предметов остаются вложения, исходная история и действующая часть по другим парам. Лимит поздних отметок сохраняется.",
    "frames": [
      {
        "title": "К удалению относятся отметки и поздние заявки этих пар",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/subjectdeletion/AttendanceSubjectDeletionParticipant.java",
        "language": "java",
        "lines": [
          [
            232,
            "    private Snapshot snapshot(SubjectDeletionCommand c, Set<Long> ids) {"
          ],
          [
            233,
            "        List<Row> rows = new ArrayList<>();"
          ],
          [
            234,
            "        for (String collection : List.of(\"attendances\", \"late_checkin_requests\")) {"
          ],
          [
            235,
            "            for (Document row :"
          ],
          [
            236,
            "                    mongo.find("
          ],
          [
            237,
            "                            Query.query("
          ],
          [
            238,
            "                                    new Criteria()"
          ],
          [
            239,
            "                                            .orOperator("
          ],
          [
            240,
            "                                                    Criteria.where(\"lesson_id\").in(ids),"
          ],
          [
            241,
            "                                                    Criteria.where(\"subject_id\")"
          ],
          [
            242,
            "                                                            .is(c.getSubjectId()))),"
          ],
          [
            243,
            "                            Document.class,"
          ],
          [
            244,
            "                            collection)) {"
          ],
          [
            245,
            "                scope(row, c);"
          ],
          [
            246,
            "                if (!ids.contains(number(row.get(\"lesson_id\")))) throw conflict();"
          ],
          [
            247,
            "                rows.add(new Row(collection, row));"
          ],
          [
            248,
            "            }"
          ]
        ]
      },
      {
        "title": "Заявка по другим предметам сохраняется с новой областью действия",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/subjectdeletion/AttendanceSubjectDeletionParticipant.java",
        "language": "java",
        "lines": [
          [
            179,
            "            for (Row row : current.entries()) {"
          ],
          [
            180,
            "                if (row.collection().equals(\"excuse_tickets\")) {"
          ],
          [
            181,
            "                    ExcuseTicket ticket ="
          ],
          [
            182,
            "                            mongo.getConverter().read(ExcuseTicket.class, row.document());"
          ],
          [
            183,
            "                    if (ExcuseSubjectCoverage.originals(ticket).stream()"
          ],
          [
            184,
            "                            .anyMatch(id -> !ids.contains(id))) {"
          ],
          [
            185,
            "                        Map<String, Object> before = RequestActionRecorder.snapshot(ticket);"
          ],
          [
            186,
            "                        if (!ExcuseSubjectCoverage.subtract("
          ],
          [
            187,
            "                                ticket,"
          ],
          [
            188,
            "                                ids,"
          ],
          [
            189,
            "                                command.getSubjectId(),"
          ],
          [
            190,
            "                                command.getOperationId(),"
          ],
          [
            191,
            "                                command.getActorId(),"
          ],
          [
            192,
            "                                clock.instant())) throw conflict();"
          ],
          [
            193,
            "                        mongo.save(ticket);"
          ],
          [
            194,
            "                        actions.subjectDeletionCoverageChanged("
          ],
          [
            195,
            "                                command.getOperationId(),"
          ],
          [
            196,
            "                                command.getActorId(),"
          ],
          [
            197,
            "                                ticket,"
          ],
          [
            198,
            "                                before,"
          ],
          [
            199,
            "                                clock.instant());"
          ],
          [
            200,
            "                        continue;"
          ],
          [
            201,
            "                    }"
          ],
          [
            202,
            "                }"
          ],
          [
            203,
            "                mongo.remove("
          ],
          [
            204,
            "                        Query.query(Criteria.where(\"_id\").is(row.document().get(\"_id\"))),"
          ],
          [
            205,
            "                        row.collection());"
          ]
        ]
      },
      {
        "title": "Вложения отбираются по удаляемой заявке или точной паре",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/subjectdeletion/AttendanceSubjectDeletionParticipant.java",
        "language": "java",
        "lines": [
          [
            274,
            "            boolean remaining ="
          ],
          [
            275,
            "                    ExcuseSubjectCoverage.originals(ticket).stream()"
          ],
          [
            276,
            "                            .anyMatch(id -> !ids.contains(id));"
          ],
          [
            277,
            "            if (remaining) {"
          ],
          [
            278,
            "                // Validate the real prospective subtraction during preview/SEAL as well."
          ],
          [
            279,
            "                ExcuseSubjectCoverage.subtract("
          ],
          [
            280,
            "                        ticket,"
          ],
          [
            281,
            "                        ids,"
          ],
          [
            282,
            "                        c.getSubjectId(),"
          ],
          [
            283,
            "                        c.getOperationId(),"
          ],
          [
            284,
            "                        c.getActorId(),"
          ],
          [
            285,
            "                        clock.instant());"
          ],
          [
            286,
            "                mixed++;"
          ],
          [
            287,
            "            } else attachmentOwners.put(ticket.getId(), ticket.getStudentId());"
          ],
          [
            288,
            "            rows.add(new Row(\"excuse_tickets\", document));"
          ],
          [
            303,
            "            String request = attachment.getString(\"request_id\");"
          ],
          [
            304,
            "            Long owner = number(attachment.get(\"owner_student_id\"));"
          ],
          [
            305,
            "            if (request != null"
          ],
          [
            306,
            "                    && attachmentOwners.containsKey(request)"
          ],
          [
            307,
            "                    && !Objects.equals(attachmentOwners.get(request), owner)) throw conflict();"
          ],
          [
            308,
            "            boolean selected ="
          ],
          [
            309,
            "                    request != null"
          ],
          [
            310,
            "                            && attachmentOwners.containsKey(request)"
          ],
          [
            311,
            "                            && Objects.equals(attachmentOwners.get(request), owner);"
          ],
          [
            312,
            "            if (request != null && request.matches(\"[1-9][0-9]*:[1-9][0-9]*\")) {"
          ],
          [
            313,
            "                String[] parts = request.split(\":\");"
          ],
          [
            314,
            "                try {"
          ],
          [
            315,
            "                    selected ="
          ],
          [
            316,
            "                            ids.contains(Long.parseLong(parts[1]))"
          ],
          [
            317,
            "                                    && Objects.equals(owner, Long.parseLong(parts[0]));"
          ],
          [
            318,
            "                } catch (NumberFormatException invalid) {"
          ],
          [
            319,
            "                    throw conflict();"
          ],
          [
            320,
            "                }"
          ],
          [
            321,
            "            }"
          ],
          [
            322,
            "            if (!selected) continue;"
          ],
          [
            323,
            "            scope(attachment, c);"
          ]
        ]
      },
      {
        "title": "Исходные снимки и решение остаются в истории",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/subjectdeletion/ExcuseSubjectCoverage.java",
        "language": "java",
        "lines": [
          [
            67,
            "        var originalSelection = ticket.getDecisionSelection();"
          ],
          [
            68,
            "        history.add("
          ],
          [
            69,
            "                new SubjectCoverageAdjustmentDocument("
          ],
          [
            70,
            "                        operation,"
          ],
          [
            71,
            "                        subject,"
          ],
          [
            72,
            "                        actor,"
          ],
          [
            73,
            "                        now,"
          ],
          [
            74,
            "                        java.util.stream.Stream.concat("
          ],
          [
            75,
            "                                        before.stream(),"
          ],
          [
            76,
            "                                        snapshots.stream()"
          ],
          [
            77,
            "                                                .map(StudentLessonSnapshotDocument::getLessonId))"
          ],
          [
            78,
            "                                .filter(target::contains)"
          ],
          [
            79,
            "                                .distinct()"
          ],
          [
            80,
            "                                .sorted()"
          ],
          [
            81,
            "                                .toList(),"
          ],
          [
            82,
            "                        remaining,"
          ],
          [
            83,
            "                        List.copyOf(snapshots),"
          ],
          [
            84,
            "                        before,"
          ],
          [
            85,
            "                        originalSelection));"
          ],
          [
            86,
            "        ticket.setSubjectCoverageAdjustments(List.copyOf(history));"
          ],
          [
            87,
            "        ticket.setEffectiveOriginalLessonIds(remaining);"
          ],
          [
            88,
            "        ticket.setEffectiveLessonSnapshots("
          ],
          [
            89,
            "                snapshots.stream().filter(s -> !target.contains(s.getLessonId())).toList());"
          ]
        ]
      }
    ]
  }
]
