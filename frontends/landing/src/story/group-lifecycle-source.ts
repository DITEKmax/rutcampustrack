import type { StoryOperation } from './story-source'

export const groupLifecycleOperations: readonly StoryOperation[] = [
  {
    "id": "group-lifecycle-create",
    "title": "Создать группу в реестре",
    "description": "Администратор с правом GROUPS_MANAGE задаёт буквенный и цифровой коды и срок обучения. POST /api/academic/groups/registry проверяет программу, курс, срок и дубли. Для начала истории нужен один активный семестр. Группа создаётся с активным флагом и видна в реестре как «Черновик», пока у неё нет старосты.",
    "frames": [
      {
        "title": "Форма передаёт обе части кода и срок",
        "path": "frontends/mobile-core/src/features/admin-groups/AdminGroupsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            148,
            "const createInput = computed(() => {"
          ],
          [
            149,
            "  try {"
          ],
          [
            150,
            "    return normalizeAdminGroupCreate({"
          ],
          [
            151,
            "      alphabeticCode: alphabeticCode.value,"
          ],
          [
            152,
            "      numericCode: numericCode.value,"
          ],
          [
            153,
            "      trainingDurationYears: Number(trainingDurationYears.value),"
          ],
          [
            154,
            "    });"
          ],
          [
            155,
            "  } catch {"
          ],
          [
            156,
            "    return null;"
          ],
          [
            157,
            "  }"
          ],
          [
            158,
            "});"
          ],
          [
            1179,
            "  const input = createInput.value;"
          ],
          [
            1180,
            "  if (!input) {"
          ],
          [
            1181,
            "    error.value = 'Проверь буквенный код, трёхзначный цифровой код и срок обучения.';"
          ],
          [
            1182,
            "    return;"
          ],
          [
            1183,
            "  }"
          ],
          [
            1184,
            "  saving.value = true;"
          ],
          [
            1185,
            "  error.value = null;"
          ],
          [
            1186,
            "  notice.value = null;"
          ],
          [
            1187,
            "  try {"
          ],
          [
            1188,
            "    const created = await client.createGroup(input);"
          ],
          [
            1189,
            "    if (!current()) return;"
          ],
          [
            1194,
            "    creationReceipt.value = { id: created.id, name: created.name };"
          ],
          [
            1195,
            "    creationReadFailed.value = false;"
          ],
          [
            1196,
            "    status.value = 'DRAFT';"
          ],
          [
            1197,
            "    page.value = 0;"
          ]
        ]
      },
      {
        "title": "Право управления группами и HTTP 201",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java",
        "language": "java",
        "lines": [
          [
            70,
            "    @Override"
          ],
          [
            71,
            "    @RequireRole("
          ],
          [
            72,
            "            value = {ADMIN},"
          ],
          [
            73,
            "            admin = {\"GROUPS_MANAGE\"})"
          ],
          [
            74,
            "    public ResponseEntity<AdminGroupResponse> createAdminGroup(CreateAdminGroupRequest request) {"
          ],
          [
            75,
            "        Group group = groupService.createAdminGroup(request);"
          ],
          [
            76,
            "        return ResponseEntity.status(201).body(groupAssembler.toCreatedRegistryResponse(group));"
          ],
          [
            77,
            "    }"
          ]
        ]
      },
      {
        "title": "Запрос на создание группы",
        "language": "json",
        "example": [
          "{",
          "  \"alphabeticCode\": \"ИВТ\",",
          "  \"numericCode\": \"311\",",
          "  \"trainingDurationYears\": 4",
          "}"
        ]
      },
      {
        "title": "Курс не может превышать срок обучения",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupCodeRules.java",
        "language": "java",
        "lines": [
          [
            62,
            "    public static CanonicalCode fromParts("
          ],
          [
            63,
            "            String alphabeticCode, String numericCode, Integer trainingDurationYears) {"
          ],
          [
            64,
            "        String prefix = normalizeAlphabeticCode(alphabeticCode);"
          ],
          [
            65,
            "        String numeric = normalizeNumericCode(numericCode);"
          ],
          [
            66,
            "        if (trainingDurationYears == null"
          ],
          [
            67,
            "                || trainingDurationYears < 1"
          ],
          [
            68,
            "                || trainingDurationYears > MAX_COURSE) {"
          ],
          [
            69,
            "            throw new InvalidCodeException("
          ],
          [
            70,
            "                    \"trainingDurationYears\", \"Срок обучения должен быть от 1 до 5 лет\");"
          ],
          [
            71,
            "        }"
          ],
          [
            72,
            "        int course = Character.digit(numeric.charAt(0), 10);"
          ],
          [
            73,
            "        if (course < 1 || course > MAX_COURSE || course > trainingDurationYears) {"
          ],
          [
            74,
            "            throw new InvalidCodeException("
          ],
          [
            75,
            "                    \"numericCode\","
          ],
          [
            76,
            "                    \"Первая цифра цифрового кода должна быть от 1 до 5 и не больше срока обучения\");"
          ],
          [
            77,
            "        }"
          ],
          [
            78,
            "        int typeDigit = Character.digit(numeric.charAt(1), 10);"
          ],
          [
            79,
            "        ProgramType type = ProgramType.fromDigit(typeDigit);"
          ],
          [
            80,
            "        return new CanonicalCode(prefix, numeric, course, type, trainingDurationYears);"
          ],
          [
            81,
            "    }"
          ]
        ]
      },
      {
        "title": "Активный семестр, уникальность и начало истории",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java",
        "language": "java",
        "lines": [
          [
            137,
            "    private Group createGroupCanonical(GroupCodeRules.CanonicalCode code, String duplicateField) {"
          ],
          [
            138,
            "        Semester activeSemester = requireSingleActiveSemester();"
          ],
          [
            139,
            "        if (groupRepository.existsByAlphabeticCodeAndNumericCodeAndIsActiveTrue("
          ],
          [
            140,
            "                        code.alphabeticCode(), code.numericCode())"
          ],
          [
            141,
            "                || groupRepository.existsByName(code.name())) {"
          ],
          [
            142,
            "            Object value = \"name\".equals(duplicateField) ? code.name() : code.numericCode();"
          ],
          [
            143,
            "            throw new ConflictException("
          ],
          [
            144,
            "                    duplicateField, value, \"Группа с таким кодом уже существует\");"
          ],
          [
            145,
            "        }"
          ],
          [
            146,
            "        Group group = new Group();"
          ],
          [
            147,
            "        GroupCodeRules.apply(group, code);"
          ],
          [
            148,
            "        group.setActive(true);"
          ],
          [
            149,
            "        group.setCreatedAt(OffsetDateTime.now());"
          ],
          [
            150,
            "        Group saved = groupRepository.save(group);"
          ],
          [
            151,
            "        // The marker is written in the same transaction as the genuinely new"
          ],
          [
            152,
            "        // group.  Existing/legacy groups are never certified by this path."
          ],
          [
            153,
            "        coverageRepository.save("
          ],
          [
            154,
            "                new GroupHistoryCoverage("
          ],
          [
            155,
            "                        saved.getId(),"
          ],
          [
            156,
            "                        activeSemester.getDateFrom(),"
          ],
          [
            157,
            "                        HistoricalMembershipService.WRITER_VERSION,"
          ],
          [
            158,
            "                        OffsetDateTime.now()));"
          ],
          [
            159,
            "        eventPublisher.publishEvent(new DashboardInvalidatedEvent(this));"
          ],
          [
            160,
            "        return saved;"
          ]
        ]
      },
      {
        "title": "Статус реестра зависит от старосты",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupAssembler.java",
        "language": "java",
        "lines": [
          [
            53,
            "        AdminGroupStatus status ="
          ],
          [
            54,
            "                !row.active()"
          ],
          [
            55,
            "                        ? AdminGroupStatus.ARCHIVED"
          ],
          [
            56,
            "                        : row.headmanFio() == null"
          ],
          [
            57,
            "                                ? AdminGroupStatus.DRAFT"
          ],
          [
            58,
            "                                : AdminGroupStatus.ACTIVE;"
          ],
          [
            59,
            "        String draftReason = status == AdminGroupStatus.DRAFT ? \"Староста не назначен\" : null;"
          ]
        ]
      }
    ]
  },
  {
    "id": "group-lifecycle-headman",
    "title": "Назначить или заменить старосту",
    "description": "Администратор выбирает студента группы. PUT /api/academic/groups/{id}/headman передаёт ожидаемого старосту из прочитанного состава. Academic проверяет, что кандидат остаётся активным студентом группы и староста не сменился. Операция передаёт Auth новый активный grant HEADMAN для кандидата и приостанавливает прежний grant. Повторный выбор действующего старосты может подтвердиться без изменений.",
    "frames": [
      {
        "title": "Форма отправляет ожидаемого старосту",
        "path": "frontends/mobile-core/src/features/admin-groups/AdminGroupsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            664,
            "    const result = await props.client.assignHeadman("
          ],
          [
            665,
            "      currentGroup.id,"
          ],
          [
            666,
            "      {"
          ],
          [
            667,
            "        studentId: currentStudentId,"
          ],
          [
            668,
            "        expectedHeadmanId: currentRoster.currentHeadmanId,"
          ],
          [
            669,
            "      },"
          ],
          [
            670,
            "      controller.signal,"
          ],
          [
            671,
            "    );"
          ],
          [
            677,
            "    acknowledgeGroupOperation(props.client);"
          ],
          [
            678,
            "    headmanSaving.value = false;"
          ],
          [
            679,
            "    closeHeadman(false);"
          ],
          [
            680,
            "    notice.value = result.changed"
          ],
          [
            681,
            "      ? `Староста группы изменён: ${result.headmanFio}.`"
          ],
          [
            682,
            "      : 'Староста уже назначен, изменений нет.';"
          ],
          [
            683,
            "    await reconciliation;"
          ]
        ]
      },
      {
        "title": "Контракт PUT и expectedHeadmanId",
        "path": "frontends/mobile-core/src/features/admin-groups/admin-groups-client.ts",
        "language": "typescript",
        "lines": [
          [
            536,
            "  assignHeadman("
          ],
          [
            537,
            "    groupId: number,"
          ],
          [
            538,
            "    input: AssignHeadmanInput,"
          ],
          [
            539,
            "    signal?: AbortSignal,"
          ],
          [
            540,
            "  ): Promise<HeadmanAssignment> {"
          ],
          [
            541,
            "    positiveInteger(groupId, 'groupId')"
          ],
          [
            542,
            "    const studentId = positiveInteger(input.studentId, 'studentId')"
          ],
          [
            543,
            "    if (input.expectedHeadmanId !== null)"
          ],
          [
            544,
            "      positiveInteger(input.expectedHeadmanId, 'expectedHeadmanId')"
          ],
          [
            550,
            "    return this.identityRequest(`/api/academic/groups/${groupId}/headman`, {"
          ],
          [
            551,
            "      method: 'PUT',"
          ],
          [
            552,
            "      body: JSON.stringify({ studentId, expectedHeadmanId: input.expectedHeadmanId }),"
          ],
          [
            553,
            "      ...(signal ? { signal } : {}),"
          ],
          [
            554,
            "    }).then(normalizeHeadmanAssignment)"
          ],
          [
            555,
            "  }"
          ]
        ]
      },
      {
        "title": "Назначение требует GROUPS_MANAGE",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupHeadmanAssignmentService.java",
        "language": "java",
        "lines": [
          [
            120,
            "    @Transactional("
          ],
          [
            121,
            "            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)"
          ],
          [
            122,
            "    public HeadmanAssignmentResponse assign(Long groupId, AssignHeadmanRequest request) {"
          ],
          [
            123,
            "        adminAccess.require(\"GROUPS_MANAGE\");"
          ],
          [
            124,
            "        if (request == null || request.studentId() == null)"
          ],
          [
            125,
            "            throw new BadRequestException(\"Запрос назначения старосты обязателен\");"
          ],
          [
            126,
            "        return identityOperations.headman("
          ],
          [
            127,
            "                groupId, request.studentId(), request.expectedHeadmanId(), true, null);"
          ],
          [
            128,
            "    }"
          ]
        ]
      },
      {
        "title": "Группа кандидата и текущий староста сверяются",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            735,
            "                                                if (student == null"
          ],
          [
            736,
            "                                                        || !student.getStatus().equals(\"ACTIVE\")"
          ],
          [
            737,
            "                                                        || !student.hasGroupId())"
          ],
          [
            738,
            "                                                    throw new BadRequestException("
          ],
          [
            739,
            "                                                            \"Старостой может быть только активный\""
          ],
          [
            740,
            "                                                                    + \" студент\");"
          ],
          [
            741,
            "                                                long group = student.getGroupId();"
          ],
          [
            742,
            "                                                if (requestedGroup != null"
          ],
          [
            743,
            "                                                        && requestedGroup != group)"
          ],
          [
            744,
            "                                                    throw new ru.rutcampustrack.academic.exception"
          ],
          [
            745,
            "                                                            .ConflictException("
          ],
          [
            746,
            "                                                            \"studentId\","
          ],
          [
            747,
            "                                                            candidate,"
          ],
          [
            748,
            "                                                            \"Студент должен состоять в выбранной\""
          ],
          [
            749,
            "                                                                    + \" группе\");"
          ],
          [
            756,
            "                                                var revision = activeGroup(group);"
          ],
          [
            757,
            "                                                Long current = currentHeadman(group);"
          ],
          [
            758,
            "                                                if (enforceCas"
          ],
          [
            759,
            "                                                        && !Objects.equals(expected, current))"
          ],
          [
            760,
            "                                                    throw Status.ABORTED"
          ],
          [
            761,
            "                                                            .withDescription(\"headman changed\")"
          ],
          [
            762,
            "                                                            .asRuntimeException();"
          ],
          [
            763,
            "                                                return new HeadmanDiscovery("
          ],
          [
            764,
            "                                                        group, current, revision);"
          ]
        ]
      },
      {
        "title": "Новый HEADMAN активируется, прежний приостанавливается",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            790,
            "                                        boolean noopCandidate ="
          ],
          [
            791,
            "                                                Objects.equals(discovery.current(), candidate);"
          ],
          [
            792,
            "                                        for (long user : ids) {"
          ],
          [
            793,
            "                                            var state = state(snapshots.get(user));"
          ],
          [
            794,
            "                                            var target = target(state.auth());"
          ],
          [
            795,
            "                                            var unchangedGrants ="
          ],
          [
            796,
            "                                                    List.copyOf(target.getDesiredGrantsList());"
          ],
          [
            797,
            "                                            var grants ="
          ],
          [
            798,
            "                                                    new ArrayList<>(target.getDesiredGrantsList());"
          ],
          [
            799,
            "                                            if (user == candidate) {"
          ],
          [
            800,
            "                                                var student = grant(grants, \"STUDENT\");"
          ],
          [
            801,
            "                                                if (student == null"
          ],
          [
            802,
            "                                                        || !student.getStatus().equals(\"ACTIVE\")"
          ],
          [
            803,
            "                                                        || !student.hasGroupId()"
          ],
          [
            804,
            "                                                        || student.getGroupId()"
          ],
          [
            805,
            "                                                                != discovery.group())"
          ],
          [
            806,
            "                                                    throw Status.ABORTED.asRuntimeException();"
          ],
          [
            807,
            "                                                var old = grant(grants, \"HEADMAN\");"
          ],
          [
            808,
            "                                                replace("
          ],
          [
            809,
            "                                                        grants,"
          ],
          [
            810,
            "                                                        (old == null"
          ],
          [
            811,
            "                                                                        ? AdmissionGrant"
          ],
          [
            812,
            "                                                                                .newBuilder()"
          ],
          [
            813,
            "                                                                                .setRole(\"HEADMAN\")"
          ],
          [
            814,
            "                                                                        : old.toBuilder())"
          ],
          [
            815,
            "                                                                .setStatus(\"ACTIVE\")"
          ],
          [
            816,
            "                                                                .setGroupId(discovery.group())"
          ],
          [
            817,
            "                                                                .build());"
          ],
          [
            832,
            "                                            } else suspendHeadman(grants);"
          ],
          [
            833,
            "                                            target.clearDesiredGrants()"
          ],
          [
            834,
            "                                                    .addAllDesiredGrants(canonicalGrants(grants));"
          ],
          [
            835,
            "                                            if (user == candidate)"
          ],
          [
            836,
            "                                                noopCandidate &="
          ],
          [
            837,
            "                                                        candidateProfile.equals(state.profile())"
          ],
          [
            838,
            "                                                                && !target.hasTelegramChange()"
          ],
          [
            839,
            "                                                                && target.getDesiredGrantsList()"
          ],
          [
            840,
            "                                                                        .equals(unchangedGrants);"
          ],
          [
            841,
            "                                            auth.addTargets(target);"
          ],
          [
            842,
            "                                        }"
          ],
          [
            843,
            "                                        auth.setHeadmanNoopCandidate(noopCandidate);"
          ]
        ]
      }
    ]
  },
  {
    "id": "group-lifecycle-transfer",
    "title": "Перевести студента в другую группу",
    "description": "В карточке пользователя администратор с USERS_MANAGE указывает новую группу и причину. POST /api/academic/users/{id}/transfer доступен для активного STUDENT. Обе группы должны быть активны и иметь историю состава на эту дату. В Auth его grant получает новую группу, прежние полномочия старосты приостанавливаются. Если переводится староста, Academic также снимает права помощников его прежней группы. В новой группе роль старосты оформляется отдельным назначением.",
    "frames": [
      {
        "title": "Экран требует новую группу и причину",
        "path": "frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1812,
            "async function transferStudent(): Promise<void> {"
          ],
          [
            1813,
            "  const user = selectedUser.value;"
          ],
          [
            1814,
            "  if (!user || saving.value || unknownMutation.value || !canTransfer.value || !canManage.value)"
          ],
          [
            1815,
            "    return;"
          ],
          [
            1816,
            "  let newGroupId: number;"
          ],
          [
            1817,
            "  try {"
          ],
          [
            1818,
            "    newGroupId = parsePositive(transferGroupId.value, 'newGroupId');"
          ],
          [
            1819,
            "    if (!transferReason.value.trim()) throw new Error('Укажи причину перевода.');"
          ],
          [
            1820,
            "  } catch (cause) {"
          ],
          [
            1821,
            "    error.value = cause instanceof Error ? cause.message : 'Проверь данные перевода.';"
          ],
          [
            1822,
            "    return;"
          ],
          [
            1823,
            "  }"
          ],
          [
            1824,
            "  const { revision, controller } = beginMutation();"
          ],
          [
            1829,
            "    const updated = await props.client.transferStudent("
          ],
          [
            1830,
            "      user.id,"
          ],
          [
            1831,
            "      {"
          ],
          [
            1832,
            "        newGroupId,"
          ],
          [
            1833,
            "        reason: transferReason.value,"
          ],
          [
            1834,
            "      },"
          ],
          [
            1835,
            "      controller.signal,"
          ],
          [
            1836,
            "    );"
          ],
          [
            1837,
            "    if (!isCurrentMutation(revision, controller)) return;"
          ],
          [
            1838,
            "    acknowledgeIdentityOperation();"
          ],
          [
            1839,
            "    selectedUser.value = updated;"
          ],
          [
            1840,
            "    transferGroupId.value = '';"
          ],
          [
            1841,
            "    transferReason.value = '';"
          ],
          [
            1842,
            "    view.value = 'detail';"
          ],
          [
            1843,
            "    await selectUser(updated);"
          ],
          [
            1844,
            "    if (!isCurrentMutation(revision, controller)) return;"
          ],
          [
            1845,
            "    notice.value = 'Студент переведён в другую группу.';"
          ]
        ]
      },
      {
        "title": "POST передаёт newGroupId и reason",
        "path": "frontends/mobile-core/src/features/admin-users/admin-users-client.ts",
        "language": "typescript",
        "lines": [
          [
            490,
            "  transferStudent("
          ],
          [
            491,
            "    id: number,"
          ],
          [
            492,
            "    input: TransferAdminStudentInput,"
          ],
          [
            493,
            "    signal?: AbortSignal,"
          ],
          [
            494,
            "  ): Promise<AdminUser> {"
          ],
          [
            495,
            "    positiveInteger(id, 'userId')"
          ],
          [
            496,
            "    const newGroupId = positiveInteger(input.newGroupId, 'newGroupId')"
          ],
          [
            497,
            "    const reason = requiredText(input.reason, 'reason')"
          ],
          [
            498,
            "    return this.identityRequest(`${AdminUsersClient.basePath}/${id}/transfer`, {"
          ],
          [
            499,
            "      method: 'POST',"
          ],
          [
            500,
            "      body: JSON.stringify({ newGroupId, reason }),"
          ],
          [
            501,
            "      ...(signal ? { signal } : {}),"
          ],
          [
            502,
            "    }).then(normalizeUser)"
          ],
          [
            503,
            "  }"
          ]
        ]
      },
      {
        "title": "Запрос на перевод студента",
        "language": "json",
        "example": [
          "{",
          "  \"newGroupId\": 42,",
          "  \"reason\": \"Перевод в другую учебную группу\"",
          "}"
        ]
      },
      {
        "title": "Перевод требует USERS_MANAGE",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java",
        "language": "java",
        "lines": [
          [
            190,
            "    @Override"
          ],
          [
            191,
            "    @RequireRole("
          ],
          [
            192,
            "            value = {ADMIN},"
          ],
          [
            193,
            "            admin = {\"USERS_MANAGE\"})"
          ],
          [
            194,
            "    @AdminAction(\"user.transfer\")"
          ],
          [
            195,
            "    public ResponseEntity<EntityModel<UserResponse>> transferStudent("
          ],
          [
            196,
            "            Long id, TransferStudentRequest request) {"
          ],
          [
            197,
            "        User user = userService.transferStudent(id, request);"
          ],
          [
            198,
            "        return ResponseEntity.ok(userAssembler.toModel(user));"
          ],
          [
            199,
            "    }"
          ]
        ]
      },
      {
        "title": "Смена группы STUDENT и приостановка HEADMAN",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            381,
            "                    var target = target(state.auth());"
          ],
          [
            382,
            "                    var grants = new ArrayList<>(target.getDesiredGrantsList());"
          ],
          [
            383,
            "                    var student = grant(grants, \"STUDENT\");"
          ],
          [
            384,
            "                    if (student == null"
          ],
          [
            385,
            "                            || !student.getStatus().equals(\"ACTIVE\")"
          ],
          [
            386,
            "                            || !student.hasGroupId())"
          ],
          [
            387,
            "                        throw new BadRequestException("
          ],
          [
            388,
            "                                \"Перевод доступен только для активного STUDENT\");"
          ],
          [
            389,
            "                    long source = student.getGroupId(), destination = request.newGroupId();"
          ],
          [
            390,
            "                    if (source == destination)"
          ],
          [
            391,
            "                        throw new BadRequestException(\"Новая группа должна отличаться от текущей\");"
          ],
          [
            392,
            "                    var groups ="
          ],
          [
            393,
            "                            List.of("
          ],
          [
            394,
            "                                            group(source, LocalDate.now(MOSCOW)),"
          ],
          [
            395,
            "                                            group(destination, LocalDate.now(MOSCOW)))"
          ],
          [
            396,
            "                                    .stream()"
          ],
          [
            397,
            "                                    .sorted("
          ],
          [
            398,
            "                                            java.util.Comparator.comparingLong("
          ],
          [
            399,
            "                                                    AcademicIdentityLocalCommand.GroupRevision"
          ],
          [
            400,
            "                                                            ::groupId))"
          ],
          [
            401,
            "                                    .toList();"
          ],
          [
            402,
            "                    replace(grants, student.toBuilder().setGroupId(destination).build());"
          ],
          [
            403,
            "                    suspendHeadman(grants);"
          ],
          [
            404,
            "                    target.clearDesiredGrants().addAllDesiredGrants(canonicalGrants(grants));"
          ],
          [
            405,
            "                    var local ="
          ],
          [
            406,
            "                            new AcademicIdentityLocalCommand("
          ],
          [
            407,
            "                                    intent,"
          ],
          [
            408,
            "                                    null,"
          ],
          [
            409,
            "                                    LocalDate.now(MOSCOW),"
          ],
          [
            410,
            "                                    null,"
          ],
          [
            411,
            "                                    source,"
          ],
          [
            412,
            "                                    destination,"
          ]
        ]
      },
      {
        "title": "Для перевода обе группы должны иметь историю на дату",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            1176,
            "    private AcademicIdentityLocalCommand.GroupRevision group(long id, LocalDate date) {"
          ],
          [
            1177,
            "        fences.requireGroupReadable(id);"
          ],
          [
            1178,
            "        var rows ="
          ],
          [
            1179,
            "                jdbc.query("
          ],
          [
            1180,
            "                        \"SELECT g.academic_revision,c.coverage_from,c.writer_version FROM groups g\""
          ],
          [
            1181,
            "                            + \" JOIN group_history_coverage c ON c.group_id=g.id WHERE g.id=? AND\""
          ],
          [
            1182,
            "                            + \" g.is_active\","
          ],
          [
            1183,
            "                        (rs, n) ->"
          ],
          [
            1184,
            "                                new Coverage("
          ],
          [
            1185,
            "                                        rs.getLong(\"academic_revision\"),"
          ],
          [
            1186,
            "                                        rs.getObject(\"coverage_from\", LocalDate.class),"
          ],
          [
            1187,
            "                                        rs.getString(\"writer_version\")),"
          ],
          [
            1188,
            "                        id);"
          ],
          [
            1189,
            "        if (rows.size() != 1"
          ],
          [
            1190,
            "                || !\"managed_v1\".equals(rows.getFirst().writer())"
          ],
          [
            1191,
            "                || rows.getFirst().from() == null"
          ],
          [
            1192,
            "                || rows.getFirst().from().isAfter(date))"
          ],
          [
            1193,
            "            throw Status.FAILED_PRECONDITION"
          ],
          [
            1194,
            "                    .withDescription(\"managed group history required\")"
          ],
          [
            1195,
            "                    .asRuntimeException();"
          ],
          [
            1196,
            "        return new AcademicIdentityLocalCommand.GroupRevision(id, rows.getFirst().revision());"
          ],
          [
            1197,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "group-lifecycle-history",
    "title": "Записать членство на дату перевода",
    "description": "Academic закрывает прежнее членство и открывает новое одной датой по Москве. День вступления включён, день выхода исключён. Журнал запрашивает состав на дату пары и семестр, поэтому отметки в прежней группе остаются в своей истории. Сервер отдельно отзывает полномочия помощника у переведённого студента.",
    "frames": [
      {
        "title": "Оба интервала меняются вместе с отзывом полномочий",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java",
        "language": "java",
        "lines": [
          [
            186,
            "                requireGroupFence(command, command.sourceGroupId());"
          ],
          [
            187,
            "                requireGroupFence(command, command.destinationGroupId());"
          ],
          [
            188,
            "                closeMembership("
          ],
          [
            189,
            "                        target.getUserId(),"
          ],
          [
            190,
            "                        command.sourceGroupId(),"
          ],
          [
            191,
            "                        command.effectiveDate(),"
          ],
          [
            192,
            "                        command.reason(),"
          ],
          [
            193,
            "                        true);"
          ],
          [
            194,
            "                openMembership("
          ],
          [
            195,
            "                        target.getUserId(),"
          ],
          [
            196,
            "                        command.destinationGroupId(),"
          ],
          [
            197,
            "                        command.effectiveDate(),"
          ],
          [
            198,
            "                        command.reason(),"
          ],
          [
            199,
            "                        false,"
          ],
          [
            200,
            "                        null);"
          ],
          [
            201,
            "                revokeUserAssistants(command, target.getUserId(), acceptedActorId);"
          ],
          [
            202,
            "                if (wasHeadman.get(target.getUserId()))"
          ],
          [
            203,
            "                    revokeGroupAssistants(command.sourceGroupId(), acceptedActorId);"
          ],
          [
            204,
            "                events.publishEvent("
          ],
          [
            205,
            "                        AcademicPersonalNotificationEvent.membership("
          ],
          [
            206,
            "                                target.getUserId(),"
          ],
          [
            207,
            "                                command.sourceGroupId(),"
          ],
          [
            208,
            "                                command.destinationGroupId(),"
          ],
          [
            209,
            "                                command.effectiveDate(),"
          ],
          [
            210,
            "                                acceptedActorId));"
          ]
        ]
      },
      {
        "title": "День вступления включён, день выхода исключён",
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
        "title": "Исторический состав читается по дате и семестру",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            443,
            "    public void getGroupMembers("
          ],
          [
            444,
            "            GroupMembersRequest request, StreamObserver<GroupMembersResponse> responseObserver) {"
          ],
          [
            445,
            "        boolean hasDate = request.hasAsOfDate();"
          ],
          [
            446,
            "        boolean hasSemester = request.hasSemesterId();"
          ],
          [
            447,
            "        if (hasDate != hasSemester) {"
          ],
          [
            448,
            "            throw HistoricalMembershipException.invalid("
          ],
          [
            449,
            "                    \"as_of_date and semester_id must be supplied together\");"
          ],
          [
            450,
            "        }"
          ],
          [
            474,
            "        HistoricalMembershipService.RosterSnapshot snapshot ="
          ],
          [
            475,
            "                academicReadService.fetchHistoricalGroupMembers("
          ],
          [
            476,
            "                        request.getGroupId(), asOfDate, request.getSemesterId());"
          ],
          [
            477,
            "        List<StudentInfo> studentInfos ="
          ],
          [
            478,
            "                snapshot.students().stream().map(AcademicGrpcServiceImpl::toStudentInfo).toList();"
          ],
          [
            479,
            ""
          ],
          [
            480,
            "        GroupMembersResponse response ="
          ],
          [
            481,
            "                GroupMembersResponse.newBuilder()"
          ],
          [
            482,
            "                        .addAllStudents(studentInfos)"
          ]
        ]
      }
    ]
  },
  {
    "id": "group-lifecycle-confirm",
    "title": "Дождаться подтверждения операции",
    "description": "Назначение и перевод проходят через общий координатор Academic и Auth. После подготовки Auth Academic применяет локальное изменение, затем подтверждает его в Auth. Пока результат неизвестен, сервер может вернуть HTTP 202 с идентификатором операции. Клиент продолжает проверку той же команды и обновляет экран по подтверждённому результату.",
    "frames": [
      {
        "title": "Academic применяет локальные изменения после подготовки Auth",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityCoordinator.java",
        "language": "java",
        "lines": [
          [
            208,
            "    private Operation advance(Operation operation) {"
          ],
          [
            209,
            "        if (operation.phase() == Phase.AUTH_PREPARED) {"
          ],
          [
            210,
            "            // A thrown/unknown outcome is not a rollback proof. Leave the operation fenced;"
          ],
          [
            211,
            "            // the next recovery reads its durable phase before doing anything else."
          ],
          [
            212,
            "            try {"
          ],
          [
            213,
            "                operation = store.apply(operation);"
          ],
          [
            214,
            "            } catch (io.grpc.StatusRuntimeException rejected) {"
          ],
          [
            215,
            "                if (rejected.getStatus().getCode() != Status.Code.ABORTED) throw rejected;"
          ],
          [
            216,
            "                // The store's transactional proxy has finished a definite business rollback."
          ],
          [
            217,
            "                // An unknown SQL/commit failure never enters this branch."
          ],
          [
            218,
            "                operation = store.rejectedBeforeCommit(operation);"
          ],
          [
            219,
            "            }"
          ]
        ]
      },
      {
        "title": "Auth подтверждает сохранённый результат",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityCoordinator.java",
        "language": "java",
        "lines": [
          [
            242,
            "            outsideTransaction();"
          ],
          [
            243,
            "            var committed ="
          ],
          [
            244,
            "                    auth.commit("
          ],
          [
            245,
            "                            CommitIdentityOperationRequest.newBuilder()"
          ],
          [
            246,
            "                                    .setOperationId(operation.id())"
          ],
          [
            247,
            "                                    .setCommandHash(operation.command().getCommandHash())"
          ],
          [
            248,
            "                                    .setExpectedOperationRevision("
          ],
          [
            249,
            "                                            operation.prepared().getOperationRevision())"
          ],
          [
            250,
            "                                    .addAllReceipts(operation.receipts())"
          ],
          [
            251,
            "                                    .build());"
          ],
          [
            262,
            "            validate("
          ],
          [
            263,
            "                    committed,"
          ],
          [
            264,
            "                    operation,"
          ],
          [
            265,
            "                    IdentityOperationPhase.IDENTITY_OPERATION_PHASE_COMMITTED);"
          ],
          [
            266,
            "            if (!committed.getTargetsList().equals(operation.prepared().getTargetsList())) {"
          ],
          [
            267,
            "                throw unavailable();"
          ],
          [
            268,
            "            }"
          ]
        ]
      },
      {
        "title": "202 возвращает идентификатор и ссылку на состояние",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityOperationHttpAdvice.java",
        "language": "java",
        "lines": [
          [
            11,
            "    @ExceptionHandler(AcademicIdentityPendingException.class)"
          ],
          [
            12,
            "    public ResponseEntity<IdentityOperationHttpModels.Operation> pending("
          ],
          [
            13,
            "            AcademicIdentityPendingException pending) {"
          ],
          [
            14,
            "        var operation = pending.operation();"
          ],
          [
            15,
            "        return ResponseEntity.accepted()"
          ],
          [
            16,
            "                .cacheControl(org.springframework.http.CacheControl.noStore())"
          ],
          [
            17,
            "                .header(\"X-Operation-Id\", operation.operationId().toString())"
          ],
          [
            18,
            "                .header("
          ],
          [
            19,
            "                        \"Location\","
          ],
          [
            20,
            "                        AcademicIdentityHttpOperations.location(operation.operationId()))"
          ],
          [
            21,
            "                .header(\"Retry-After\", Integer.toString(operation.retryAfterSeconds()))"
          ],
          [
            22,
            "                .body(operation);"
          ],
          [
            23,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "group-lifecycle-archive",
    "title": "Архивировать группу вручную",
    "description": "Команда «Архивировать» отправляет PUT /api/academic/groups/{id} с active: false. GroupAvailabilityService сохраняет намерение. Auth обновляет версию прав затронутого старосты для переоценки доступа к чтению учётных данных. После локального изменения координатор ждёт подтверждения Auth. Архивная запись получает дату, неактивный флаг и суффикс «выпуск YYYY».",
    "frames": [
      {
        "title": "PUT отключает активную группу",
        "path": "frontends/mobile-core/src/features/admin-groups/admin-groups-client.ts",
        "language": "typescript",
        "lines": [
          [
            461,
            "  async archiveGroup(groupId: number, name: string, signal?: AbortSignal): Promise<void> {"
          ],
          [
            462,
            "    positiveInteger(groupId, 'groupId')"
          ],
          [
            463,
            "    const groupName = requiredText(name, 'name')"
          ],
          [
            464,
            "    await this.groupMutation(`/api/academic/groups/${groupId}`, {"
          ],
          [
            465,
            "      method: 'PUT',"
          ],
          [
            466,
            "      body: JSON.stringify({ name: groupName, active: false }),"
          ],
          [
            467,
            "      ...(signal ? { signal } : {}),"
          ],
          [
            468,
            "    })"
          ],
          [
            469,
            "  }"
          ]
        ]
      },
      {
        "title": "Academic принимает команду DEACTIVATE",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupavailability/GroupAvailabilityService.java",
        "language": "java",
        "lines": [
          [
            87,
            "    public Group updateGroup(long group, UpdateGroupRequest request) {"
          ],
          [
            88,
            "        if (request == null) throw Status.INVALID_ARGUMENT.asRuntimeException();"
          ],
          [
            89,
            "        if (request.active())"
          ],
          [
            90,
            "            return tx.execute(status -> groups.getObject().applyOrdinaryUpdate(group, request));"
          ],
          [
            91,
            "        String digest = digest(request);"
          ],
          [
            92,
            "        var operation ="
          ],
          [
            93,
            "                execute("
          ],
          [
            94,
            "                        http.beginOperation(),"
          ],
          [
            95,
            "                        GroupAvailabilityCommand.Action.DEACTIVATE,"
          ],
          [
            96,
            "                        group,"
          ],
          [
            97,
            "                        digest,"
          ],
          [
            98,
            "                        () ->"
          ],
          [
            99,
            "                                tx.execute("
          ],
          [
            100,
            "                                        status -> {"
          ],
          [
            101,
            "                                            groups.getObject()"
          ],
          [
            102,
            "                                                    .validateAvailabilityUpdate(group, request);"
          ],
          [
            103,
            "                                            return new GroupAvailabilityCommand("
          ],
          [
            104,
            "                                                    GroupAvailabilityCommand.Action.DEACTIVATE,"
          ],
          [
            105,
            "                                                    group,"
          ],
          [
            106,
            "                                                    digest,"
          ],
          [
            107,
            "                                                    request,"
          ],
          [
            108,
            "                                                    LocalDate.now(clock).getYear(),"
          ],
          [
            109,
            "                                                    null,"
          ],
          [
            110,
            "                                                    List.of(revision(group)));"
          ],
          [
            111,
            "                                        }));"
          ],
          [
            112,
            "        finish(operation);"
          ],
          [
            113,
            "        return groups.getObject().findGroupById(group);"
          ]
        ]
      },
      {
        "title": "Academic применяет команду после инвалидации в Auth",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupavailability/GroupAvailabilityCoordinator.java",
        "language": "java",
        "lines": [
          [
            108,
            "    private GroupAvailabilityStore.Operation adopt("
          ],
          [
            109,
            "            GroupAvailabilityStore.Operation operation, CredentialReaderInvalidationResult result) {"
          ],
          [
            110,
            "        validate(operation, result);"
          ],
          [
            111,
            "        return switch (result.getPhase()) {"
          ],
          [
            112,
            "            case CREDENTIAL_READER_INVALIDATION_PHASE_INVALIDATED ->"
          ],
          [
            113,
            "                    store.invalidated(operation, result);"
          ],
          [
            114,
            "            case CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE ->"
          ],
          [
            115,
            "                    store.failed(operation, result);"
          ],
          [
            116,
            "            case CREDENTIAL_READER_INVALIDATION_PHASE_REJECTED -> store.rejected(operation, result);"
          ],
          [
            117,
            "            default -> throw AuthCredentialReaderInvalidationClient.unavailable();"
          ],
          [
            118,
            "        };"
          ],
          [
            119,
            "    }"
          ],
          [
            120,
            ""
          ],
          [
            121,
            "    private GroupAvailabilityStore.Operation advance(GroupAvailabilityStore.Operation operation) {"
          ],
          [
            122,
            "        if (operation.phase() == GroupAvailabilityStore.Phase.AUTH_INVALIDATED) {"
          ],
          [
            123,
            "            if (!operation.localRejected()) {"
          ],
          [
            124,
            "                try {"
          ],
          [
            125,
            "                    operation = store.apply(operation);"
          ]
        ]
      },
      {
        "title": "Версия прав затронутого старосты обновляется",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthCredentialReaderInvalidationStore.java",
        "language": "java",
        "lines": [
          [
            169,
            "                    for (var group : request.getGroupsList())"
          ],
          [
            170,
            "                        if (group.getHeadmanUserId() > 0)"
          ],
          [
            171,
            "                            if (jdbc.update("
          ],
          [
            172,
            "                                            \"UPDATE auth_identities SET\""
          ],
          [
            173,
            "                                                + \" roles_version=roles_version+1 WHERE id=? AND\""
          ],
          [
            174,
            "                                                + \" roles_version=? AND fence_operation_id IS NULL\","
          ],
          [
            175,
            "                                            group.getHeadmanUserId(),"
          ],
          [
            176,
            "                                            group.getExpectedHeadmanRolesVersion())"
          ],
          [
            177,
            "                                    != 1) throw conflict();"
          ],
          [
            178,
            "                    return result(find(id));"
          ]
        ]
      },
      {
        "title": "Auth подтверждает результат, координатор завершает операцию",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupavailability/GroupAvailabilityCoordinator.java",
        "language": "java",
        "lines": [
          [
            149,
            "        if (operation.phase() == GroupAvailabilityStore.Phase.LOCAL_COMMITTED) {"
          ],
          [
            150,
            "            validateReceipts(operation);"
          ],
          [
            151,
            "            var request ="
          ],
          [
            152,
            "                    operation.receipts().toBuilder()"
          ],
          [
            153,
            "                            .setOperationId(operation.id().toString())"
          ],
          [
            154,
            "                            .setCommandHash(operation.auth().getCommandHash())"
          ],
          [
            155,
            "                            .setExpectedOperationRevision(operation.result().getOperationRevision())"
          ],
          [
            156,
            "                            .build();"
          ],
          [
            157,
            "            var result = auth.commit(request);"
          ],
          [
            158,
            "            validate(operation, result);"
          ],
          [
            159,
            "            if (result.getPhase()"
          ],
          [
            160,
            "                    == CredentialReaderInvalidationPhase"
          ],
          [
            161,
            "                            .CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE)"
          ],
          [
            162,
            "                return store.failed(operation, result);"
          ],
          [
            163,
            "            if (result.getPhase()"
          ],
          [
            164,
            "                            != CredentialReaderInvalidationPhase"
          ],
          [
            165,
            "                                    .CREDENTIAL_READER_INVALIDATION_PHASE_COMMITTED"
          ],
          [
            170,
            "            catalog.await("
          ],
          [
            171,
            "                    result.getCommittedCatalogVersion(),"
          ],
          [
            172,
            "                    System.nanoTime() + TimeUnit.SECONDS.toNanos(3));"
          ],
          [
            173,
            "            return store.succeeded(operation, result);"
          ],
          [
            174,
            "        }"
          ]
        ]
      },
      {
        "title": "Архивация сохраняет запись и отмечает дату",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupArchivalService.java",
        "language": "java",
        "lines": [
          [
            73,
            "    private void archive(Group group, int graduationYear, Long actorUserId, Long cycleSemesterId) {"
          ],
          [
            74,
            "        if (!group.isActive()) {"
          ],
          [
            75,
            "            throw new IllegalStateException(\"Group already archived: \" + group.getId());"
          ],
          [
            76,
            "        }"
          ],
          [
            77,
            "        group.setName(buildArchivedName(group.getName(), graduationYear));"
          ],
          [
            78,
            "        group.setActive(false);"
          ],
          [
            79,
            "        group.setArchivedAt(OffsetDateTime.now(clock));"
          ],
          [
            80,
            "        publisher.publishEvent("
          ],
          [
            81,
            "                cycleSemesterId == null"
          ],
          [
            82,
            "                        ? new GroupArchivedEvent(this, group.getId(), group.getName(), actorUserId)"
          ],
          [
            83,
            "                        : GroupArchivedEvent.automaticCycle("
          ],
          [
            84,
            "                                this, group.getId(), group.getName(), cycleSemesterId));"
          ],
          [
            85,
            "        publisher.publishEvent(new DashboardInvalidatedEvent(this));"
          ],
          [
            86,
            "    }"
          ],
          [
            87,
            ""
          ],
          [
            88,
            "    /** Построить архивное имя группы: {@code \"<active> (выпуск YYYY)\"}. */"
          ],
          [
            89,
            "    public String buildArchivedName(String active, int year) {"
          ],
          [
            90,
            "        return active + \" (выпуск \" + year + \")\";"
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
    "id": "group-lifecycle-cycle",
    "title": "Перевести курс или завершить выпуск",
    "description": "Перевод на следующий курс обрабатывает группы в цикле завершённого весеннего семестра. Администратор может запустить его после предпросмотра. Фоновая проверка по умолчанию идёт в 03:00 по Москве. Автоматический цикл допускается с 18 августа года окончания семестра. Если текущий курс достиг срока обучения, группа попадает в архив. Итог фиксируется по группе и циклу.",
    "frames": [
      {
        "title": "Автоматическая проверка по московскому времени",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionJob.java",
        "language": "java",
        "lines": [
          [
            19,
            "    @Scheduled("
          ],
          [
            20,
            "            cron = \"${rutcampustrack.academic.group-promotion.cron:0 0 3 * * *}\","
          ],
          [
            21,
            "            zone = \"Europe/Moscow\")"
          ],
          [
            22,
            "    @SchedulerLock("
          ],
          [
            23,
            "            name = \"academic-group-promotion-job\","
          ],
          [
            24,
            "            lockAtMostFor = \"PT10M\","
          ],
          [
            25,
            "            lockAtLeastFor = \"PT30S\")"
          ],
          [
            26,
            "    public void checkDueSpringCycle() {"
          ],
          [
            27,
            "        promotionService.executeAutomaticallyIfDue().ifPresent(this::logResult);"
          ],
          [
            28,
            "    }"
          ]
        ]
      },
      {
        "title": "План опирается на завершённый весенний семестр",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java",
        "language": "java",
        "lines": [
          [
            139,
            "        LocalDate today = LocalDate.now(clock);"
          ],
          [
            140,
            "        Optional<Semester> candidate ="
          ],
          [
            141,
            "                semesterRepository.findLatestCompletedSpringPromotionCycle(today);"
          ],
          [
            142,
            "        if (candidate.isEmpty() || today.isBefore(automaticDate(candidate.get()))) {"
          ],
          [
            143,
            "            return Optional.empty();"
          ],
          [
            144,
            "        }"
          ],
          [
            145,
            ""
          ],
          [
            146,
            "        Semester cycle = requireCurrentCycleForUpdate(candidate.get().getId());"
          ],
          [
            147,
            "        if (today.isBefore(automaticDate(cycle))) {"
          ],
          [
            148,
            "            return Optional.empty();"
          ],
          [
            149,
            "        }"
          ],
          [
            150,
            ""
          ],
          [
            151,
            "        List<Group> active = groupRepository.findAllActiveForPromotionUpdate();"
          ],
          [
            152,
            "        List<GroupPromotionCycleRecord> processed ="
          ],
          [
            153,
            "                cycleRecordRepository.findAllByCycleSemesterIdOrderByGroupId(cycle.getId());"
          ],
          [
            154,
            "        availability().requireAvailableScope(0);"
          ],
          [
            155,
            "        PromotionSummary plan = buildSummary(cycle, active, processed, null, true);"
          ],
          [
            156,
            "        return Optional.of(plan);"
          ],
          [
            177,
            "    private static LocalDate cycleDateTo(Semester cycle) {"
          ],
          [
            178,
            "        return cycle.getPromotionCycleDateTo() == null"
          ],
          [
            179,
            "                ? cycle.getDateTo()"
          ],
          [
            180,
            "                : cycle.getPromotionCycleDateTo();"
          ],
          [
            181,
            "    }"
          ],
          [
            182,
            ""
          ],
          [
            183,
            "    private LocalDate automaticDate(Semester cycle) {"
          ],
          [
            184,
            "        return LocalDate.of(cycleDateTo(cycle).getYear(), Month.AUGUST, 18);"
          ],
          [
            185,
            "    }"
          ]
        ]
      },
      {
        "title": "Следующий курс существует до окончания срока обучения",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupCodeRules.java",
        "language": "java",
        "lines": [
          [
            31,
            "        public CanonicalCode next() {"
          ],
          [
            32,
            "            if (currentCourse >= trainingDurationYears) {"
          ],
          [
            33,
            "                return null;"
          ],
          [
            34,
            "            }"
          ],
          [
            35,
            "            String nextNumeric ="
          ],
          [
            36,
            "                    (currentCourse + 1)"
          ],
          [
            37,
            "                            + Integer.toString(programType.getDigit())"
          ],
          [
            38,
            "                            + numericCode.charAt(2);"
          ],
          [
            39,
            "            return fromParts(alphabeticCode, nextNumeric, trainingDurationYears);"
          ],
          [
            40,
            "        }"
          ]
        ]
      },
      {
        "title": "План распределяет группы между выпуском и переводом",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java",
        "language": "java",
        "lines": [
          [
            384,
            "        Map<String, Long> newNameOwners = new HashMap<>();"
          ],
          [
            385,
            "        for (Group group : eligible) {"
          ],
          [
            386,
            "            GroupCodeRules.CanonicalCode current ="
          ],
          [
            387,
            "                    GroupCodeRules.fromName(group.getName(), group.getTrainingDurationYears());"
          ],
          [
            388,
            "            GroupCodeRules.CanonicalCode next;"
          ],
          [
            389,
            "            try {"
          ],
          [
            390,
            "                next = current.next();"
          ],
          [
            391,
            "            } catch (GroupCodeRules.InvalidCodeException e) {"
          ],
          [
            392,
            "                return conflict("
          ],
          [
            393,
            "                        prefix,"
          ],
          [
            394,
            "                        \"unrepresentable_next_course\","
          ],
          [
            395,
            "                        \"Следующий курс группы «\""
          ],
          [
            396,
            "                                + group.getName()"
          ],
          [
            397,
            "                                + \"» нельзя записать в трёхзначный код. Перевод этого префикса\""
          ],
          [
            398,
            "                                + \" невозможен.\","
          ],
          [
            399,
            "                        eligible,"
          ],
          [
            400,
            "                        List.of(group.getId()));"
          ],
          [
            401,
            "            }"
          ],
          [
            402,
            "            long students = studentCount(studentCounts, group.getId());"
          ],
          [
            403,
            "            if (next == null) {"
          ],
          [
            404,
            "                prefixArchive.add("
          ],
          [
            405,
            "                        new PromotionPreviewItem("
          ],
          [
            406,
            "                                group.getId(), group.getName(), null, Action.ARCHIVE, students));"
          ],
          [
            407,
            "                continue;"
          ],
          [
            419,
            "            prefixPromote.add("
          ],
          [
            420,
            "                    new PromotionPreviewItem("
          ],
          [
            421,
            "                            group.getId(), group.getName(), next.name(), Action.PROMOTE, students));"
          ],
          [
            422,
            "        }"
          ]
        ]
      },
      {
        "title": "Архивная запись и результат цикла сохраняются",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupPromotionService.java",
        "language": "java",
        "lines": [
          [
            532,
            "        for (PromotionPreviewItem item : plan.getToArchive()) {"
          ],
          [
            533,
            "            Group group = byId.get(item.getId());"
          ],
          [
            534,
            "            GroupCodeRules.apply("
          ],
          [
            535,
            "                    group,"
          ],
          [
            536,
            "                    GroupCodeRules.fromName(group.getName(), group.getTrainingDurationYears()));"
          ],
          [
            537,
            "            if (automatic) {"
          ],
          [
            538,
            "                archivalService.archiveAutomaticallyForCycle("
          ],
          [
            539,
            "                        group, cycleDateTo(cycle).getYear(), cycle.getId());"
          ],
          [
            540,
            "            } else {"
          ],
          [
            541,
            "                archivalService.archive(group, cycleDateTo(cycle).getYear());"
          ],
          [
            542,
            "            }"
          ],
          [
            543,
            "            groupRepository.flush();"
          ],
          [
            544,
            "            records.add("
          ],
          [
            545,
            "                    new GroupPromotionCycleRecord("
          ],
          [
            546,
            "                            cycle.getId(),"
          ],
          [
            547,
            "                            group.getId(),"
          ],
          [
            548,
            "                            Action.ARCHIVE,"
          ],
          [
            549,
            "                            item.getFrom(),"
          ],
          [
            550,
            "                            group.getName(),"
          ],
          [
            551,
            "                            item.getStudentCount(),"
          ],
          [
            552,
            "                            processedAt));"
          ]
        ]
      }
    ]
  },
  {
    "id": "group-lifecycle-effects",
    "title": "Сохранить расписание и посещаемость",
    "description": "Архивация сохраняет расписание, отметки и историю членства. Schedule проверяет активность группы при создании занятий из шаблона и разовых пар. Attendance читает данные конкретной пары и состав на её дату в пределах проверенных прав.",
    "frames": [
      {
        "title": "Schedule отклоняет создание для неактивной группы",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/AcademicGrpcClient.java",
        "language": "java",
        "lines": [
          [
            60,
            "    public GroupResponse validateGroup(Long groupId) {"
          ],
          [
            61,
            "        try {"
          ],
          [
            62,
            "            GroupResponse response ="
          ],
          [
            63,
            "                    stub.withDeadlineAfter(3, TimeUnit.SECONDS)"
          ],
          [
            64,
            "                            .getGroup(GroupRequest.newBuilder().setGroupId(groupId).build());"
          ],
          [
            65,
            "            if (!response.getIsActive()) {"
          ],
          [
            66,
            "                throw new ResourceNotFoundException(\"Group\", \"id\", groupId);"
          ],
          [
            67,
            "            }"
          ],
          [
            68,
            "            return response;"
          ],
          [
            69,
            "        } catch (ResourceNotFoundException e) {"
          ],
          [
            70,
            "            throw e;"
          ],
          [
            71,
            "        } catch (StatusRuntimeException e) {"
          ],
          [
            72,
            "            throw new AcademicServiceUnavailableException("
          ],
          [
            73,
            "                    \"Academic Service unavailable: \" + e.getStatus());"
          ]
        ]
      },
      {
        "title": "Academic возвращает фактический статус группы",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            425,
            "    /** GRPC-01: Get group by ID. */"
          ],
          [
            426,
            "    @Override"
          ],
          [
            427,
            "    public void getGroup(GroupRequest request, StreamObserver<GroupResponse> responseObserver) {"
          ],
          [
            428,
            "        Group group = academicReadService.fetchGroup(request.getGroupId());"
          ],
          [
            429,
            ""
          ],
          [
            430,
            "        GroupResponse response ="
          ],
          [
            431,
            "                GroupResponse.newBuilder()"
          ],
          [
            432,
            "                        .setId(group.getId())"
          ],
          [
            433,
            "                        .setName(group.getName())"
          ],
          [
            434,
            "                        .setIsActive(group.isActive())"
          ],
          [
            435,
            "                        .build();"
          ]
        ]
      },
      {
        "title": "Журнал читает историческую пару и проверяет доступ",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            292,
            "        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);"
          ],
          [
            293,
            "        if (lesson == null) {"
          ],
          [
            294,
            "            throw new BadRequestException(\"Пара недоступна\");"
          ],
          [
            295,
            "        }"
          ],
          [
            296,
            "        if (lesson.getId() != lessonId) {"
          ],
          [
            297,
            "            throw new ScheduleServiceUnavailableException(\"Schedule returned a mismatched lesson\");"
          ],
          [
            298,
            "        }"
          ],
          [
            299,
            "        authorizeTeacherLesson(lesson, teacherId);"
          ],
          [
            300,
            "        LessonAttendanceData roster = buildLessonAttendance(lesson, selection);"
          ],
          [
            301,
            "        return new TeacherLessonRoster(lesson, roster.attendance(), roster.manualAttachmentExpiries());"
          ],
          [
            302,
            "    }"
          ],
          [
            325,
            "    private LessonAttendanceData buildLessonAttendance("
          ],
          [
            326,
            "            LessonResponse lesson, TeacherAttachmentSelection teacherAttachments) {"
          ],
          [
            327,
            "        Long lessonId = lesson.getId();"
          ],
          [
            328,
            "        boolean teacherBoundary = teacherAttachments != null;"
          ],
          [
            329,
            ""
          ],
          [
            330,
            "        JournalLessonPolicy.Timing timing = JournalLessonPolicy.requireTiming(lesson);"
          ],
          [
            331,
            "        GroupMembersResponse members = membersForLesson(lesson);"
          ]
        ]
      },
      {
        "title": "Запись отметки сверяет состав на дату пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java",
        "language": "java",
        "lines": [
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
          ],
          [
            548,
            "        return response;"
          ],
          [
            549,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "group-lifecycle-restore",
    "title": "Вернуть группу из архива",
    "description": "Восстановление возвращает группе прежнее имя и активный флаг. Academic проверяет формат и свободный код, затем выполняет восстановление через GroupAvailabilityService. Удаление группы проходит отдельным процессом с предпросмотром и подтверждением.",
    "frames": [
      {
        "title": "Восстановление защищено правом GROUPS_MANAGE",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupController.java",
        "language": "java",
        "lines": [
          [
            118,
            "    @Override"
          ],
          [
            119,
            "    @RequireRole("
          ],
          [
            120,
            "            value = {ADMIN},"
          ],
          [
            121,
            "            admin = {\"GROUPS_MANAGE\"})"
          ],
          [
            122,
            "    public ResponseEntity<Void> restoreGroup(Long id) {"
          ],
          [
            123,
            "        groupService.restoreGroup(id);"
          ],
          [
            124,
            "        return ResponseEntity.noContent().build();"
          ],
          [
            125,
            "    }"
          ]
        ]
      },
      {
        "title": "Проверяются прежнее имя и свободный код",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java",
        "language": "java",
        "lines": [
          [
            366,
            "    private GroupArchivalService.RestorableName validateRestore(Group group, Long id) {"
          ],
          [
            367,
            "        GroupArchivalService.RestorableName restoredName = archivalService.restorableName(group);"
          ],
          [
            368,
            "        try {"
          ],
          [
            369,
            "            GroupCodeRules.fromName(restoredName.name(), group.getTrainingDurationYears());"
          ],
          [
            370,
            "        } catch (GroupCodeRules.InvalidCodeException error) {"
          ],
          [
            371,
            "            throw new ConflictException(error.field(), id, error.getMessage());"
          ],
          [
            372,
            "        } catch (UnknownProgramTypeException error) {"
          ],
          [
            373,
            "            throw new ConflictException(\"name\", id, \"Неизвестный тип программы архивной группы\");"
          ],
          [
            374,
            "        }"
          ],
          [
            375,
            "        if (groupRepository.existsByName(restoredName.name())) {"
          ],
          [
            376,
            "            throw new ConflictException("
          ],
          [
            377,
            "                    \"name\", restoredName.name(), \"Группа с таким названием уже существует\");"
          ],
          [
            378,
            "        }"
          ],
          [
            379,
            "        if (restoredName.alphabeticCode() != null"
          ],
          [
            380,
            "                && groupRepository.existsByAlphabeticCodeAndNumericCodeAndIsActiveTrue("
          ],
          [
            381,
            "                        restoredName.alphabeticCode(), restoredName.numericCode())) {"
          ],
          [
            382,
            "            throw new ConflictException("
          ],
          [
            383,
            "                    \"numericCode\","
          ],
          [
            384,
            "                    restoredName.numericCode(),"
          ],
          [
            385,
            "                    \"Группа с таким кодом уже существует\");"
          ],
          [
            386,
            "        }"
          ]
        ]
      },
      {
        "title": "Переиспользуется сохранённая операция восстановления",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupavailability/GroupAvailabilityService.java",
        "language": "java",
        "lines": [
          [
            116,
            "    public void restoreGroup(long group) {"
          ],
          [
            117,
            "        String digest = digest(new RestoreIntent(group));"
          ],
          [
            118,
            "        UUID id = http.beginOperation();"
          ],
          [
            119,
            "        var intent ="
          ],
          [
            120,
            "                GroupAvailabilityIntent.direct("
          ],
          [
            121,
            "                        GroupAvailabilityCommand.Action.RESTORE, group, digest);"
          ],
          [
            122,
            "        http.checkGroupAttempt(id, intent);"
          ],
          [
            123,
            "        var replay = store.findForActor(id, intent);"
          ],
          [
            124,
            "        if (replay != null) {"
          ],
          [
            125,
            "            finish(run(replay));"
          ],
          [
            126,
            "            return;"
          ],
          [
            127,
            "        }"
          ],
          [
            128,
            "        var failed = store.failedIntent(GroupAvailabilityCommand.Action.RESTORE, group, digest);"
          ],
          [
            129,
            "        if (failed != null) {"
          ],
          [
            130,
            "            finish(run(reconcileAccepted(id, failed, intent)));"
          ],
          [
            131,
            "            return;"
          ]
        ]
      },
      {
        "title": "Удаление идёт через отдельный протокол",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/group/GroupService.java",
        "language": "java",
        "lines": [
          [
            478,
            "    /** Final deletion requires the exact preview and current ADMIN password protocol. */"
          ],
          [
            479,
            "    public void deleteGroup(Long id) {"
          ],
          [
            480,
            "        throw new ConflictException("
          ],
          [
            481,
            "                \"deletion\", id, \"Используй подтверждённое окончательное удаление группы\");"
          ],
          [
            482,
            "    }"
          ],
          [
            483,
            ""
          ]
        ]
      }
    ]
  }
]
