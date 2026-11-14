import type { StoryOperation } from './story-source'

export const userLifecycleOperations: readonly StoryOperation[] = [
  {
    "id": "user-lifecycle-create",
    "title": "Создать пользователя",
    "description": "В разделе пользователей администратор с USERS_MANAGE выбирает начальную роль. Для STUDENT обязательны группа и Telegram ID, для TEACHER — табельный номер. Администратор с полным доступом может создать ADMIN с явно заданным профилем прав. Роль старосты выдаётся отдельным назначением в группе.",
    "frames": [
      {
        "title": "Форма показывает доступные начальные роли",
        "path": "frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue",
        "language": "typescript",
        "lines": [
          [
            2503,
            "        </summary><div class=\"admin-users-select__options\">"
          ],
          [
            2504,
            "          <button"
          ],
          [
            2505,
            "            v-for=\"role in ['STUDENT', 'TEACHER', ...(isFull ? (['ADMIN'] as const) : [])] as const\""
          ],
          [
            2506,
            "            :key=\"role\""
          ],
          [
            2507,
            "            type=\"button\""
          ],
          [
            2508,
            "            :aria-pressed=\"createRole === role\""
          ],
          [
            2509,
            "            @click=\""
          ],
          [
            2510,
            "              createRole = role;"
          ],
          [
            2511,
            "              closeDropdown($event);"
          ],
          [
            2512,
            "            \""
          ],
          [
            2513,
            "          >"
          ],
          [
            2514,
            "            {{ roleLabel(role) }}"
          ],
          [
            2515,
            "          </button>"
          ]
        ]
      },
      {
        "title": "Клиент отправляет команду создания",
        "path": "frontends/mobile-core/src/features/admin-users/admin-users-client.ts",
        "language": "typescript",
        "lines": [
          [
            448,
            "  createUser("
          ],
          [
            449,
            "    input: CreateAdminUserInput,"
          ],
          [
            450,
            "    signal?: AbortSignal,"
          ],
          [
            451,
            "    options: AdminUserReceiptOptions = {},"
          ],
          [
            452,
            "  ): Promise<AdminUser> {"
          ],
          [
            453,
            "    const payload = normalizeCreateInput(input)"
          ],
          [
            454,
            "    return this.identityRequest(AdminUsersClient.basePath, {"
          ],
          [
            455,
            "      method: 'POST',"
          ],
          [
            456,
            "      body: JSON.stringify(payload),"
          ],
          [
            457,
            "      ...(signal ? { signal } : {}),"
          ]
        ]
      },
      {
        "title": "Создание защищено правом USERS_MANAGE",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java",
        "language": "java",
        "lines": [
          [
            55,
            "    @Override"
          ],
          [
            56,
            "    @RequireRole("
          ],
          [
            57,
            "            value = {ADMIN},"
          ],
          [
            58,
            "            admin = {\"USERS_MANAGE\"})"
          ],
          [
            59,
            "    @AdminAction(\"user.create\")"
          ],
          [
            60,
            "    public ResponseEntity<EntityModel<UserCreatedResponse>> createUser(CreateUserRequest request) {"
          ],
          [
            61,
            "        return ResponseEntity.status(201).body(userService.createUser(request));"
          ],
          [
            62,
            "    }"
          ]
        ]
      },
      {
        "title": "Проверки группы и Telegram для студента",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            1333,
            "    private static void validate(CreateUserRequest request) {"
          ],
          [
            1334,
            "        if (request == null || request.role() == null)"
          ],
          [
            1335,
            "            throw new BadRequestException(\"role\", \"Роль обязательна\");"
          ],
          [
            1336,
            "        requiredName(\"lastName\", request.lastName());"
          ],
          [
            1337,
            "        requiredName(\"firstName\", request.firstName());"
          ],
          [
            1338,
            "        boolean employee = blankToNull(request.employeeNumber()) != null;"
          ],
          [
            1339,
            "        if (request.telegramId() != null && request.telegramId() <= 0)"
          ],
          [
            1340,
            "            throw new BadRequestException(\"telegramId\", \"Telegram ID должен быть положительным\");"
          ],
          [
            1341,
            "        switch (request.role()) {"
          ],
          [
            1342,
            "            case STUDENT -> {"
          ],
          [
            1343,
            "                if (request.groupId() == null || request.groupId() <= 0)"
          ],
          [
            1344,
            "                    throw new BadRequestException(\"groupId\", \"Группа обязательна для студента\");"
          ],
          [
            1345,
            "                if (request.telegramId() == null)"
          ],
          [
            1346,
            "                    throw new BadRequestException("
          ],
          [
            1347,
            "                            \"telegramId\", \"Telegram ID обязателен для студента\");"
          ],
          [
            1348,
            "                if (employee)"
          ],
          [
            1349,
            "                    throw new BadRequestException("
          ],
          [
            1350,
            "                            \"employeeNumber\", \"Табельный номер не применим к роли STUDENT\");"
          ],
          [
            1351,
            "            }"
          ]
        ]
      },
      {
        "title": "Табельный номер и административный профиль",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            1352,
            "            case TEACHER -> {"
          ],
          [
            1353,
            "                if (request.groupId() != null)"
          ],
          [
            1354,
            "                    throw new BadRequestException(\"groupId\", \"Группа не применима к роли TEACHER\");"
          ],
          [
            1355,
            "                if (!employee)"
          ],
          [
            1356,
            "                    throw new BadRequestException("
          ],
          [
            1357,
            "                            \"employeeNumber\", \"Табельный номер обязателен для преподавателя\");"
          ],
          [
            1358,
            "            }"
          ],
          [
            1359,
            "            case ADMIN -> {"
          ],
          [
            1360,
            "                if (request.groupId() != null)"
          ],
          [
            1361,
            "                    throw new BadRequestException(\"groupId\", \"Группа не применима к роли ADMIN\");"
          ],
          [
            1362,
            "                if (employee)"
          ],
          [
            1363,
            "                    throw new BadRequestException("
          ],
          [
            1364,
            "                            \"employeeNumber\", \"Табельный номер не применим к роли ADMIN\");"
          ],
          [
            1365,
            "                if (request.adminAccess() == null)"
          ],
          [
            1366,
            "                    throw new BadRequestException("
          ],
          [
            1367,
            "                            \"adminAccess\", \"Для ADMIN нужен явный профиль доступа\");"
          ],
          [
            1368,
            "            }"
          ],
          [
            1369,
            "        }"
          ],
          [
            1370,
            "        if (request.role() != UserRole.ADMIN && request.adminAccess() != null)"
          ],
          [
            1371,
            "            throw new BadRequestException(\"adminAccess\", \"Профиль доступа применим только к ADMIN\");"
          ]
        ]
      }
    ]
  },
  {
    "id": "user-lifecycle-coordinate",
    "title": "Согласовать профиль и учётную запись",
    "description": "Academic собирает команду с активным аккаунтом и выбранной ролью. Координатор сохраняет операцию, затем Auth подготавливает изменение. Academic записывает учебный профиль, после чего Auth подтверждает точный результат. Экран получает успешный ответ после подтверждения и обновления проекции. Для создания успешный ответ — HTTP 201.",
    "frames": [
      {
        "title": "В команду входят активный аккаунт и начальная роль",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            103,
            "                    var enrollment ="
          ],
          [
            104,
            "                            request.role() == UserRole.STUDENT"
          ],
          [
            105,
            "                                    ? localSnapshot(tx -> enrollment(request.groupId()))"
          ],
          [
            106,
            "                                    : null;"
          ],
          [
            107,
            "                    var grant ="
          ],
          [
            108,
            "                            AdmissionGrant.newBuilder()"
          ],
          [
            109,
            "                                    .setRole(request.role().name())"
          ],
          [
            110,
            "                                    .setStatus(\"ACTIVE\");"
          ],
          [
            111,
            "                    if (request.role() == UserRole.STUDENT) grant.setGroupId(request.groupId());"
          ],
          [
            112,
            "                    var target ="
          ],
          [
            113,
            "                            IdentityTargetCommand.newBuilder()"
          ],
          [
            114,
            "                                    .setDesiredAdminProfile("
          ],
          [
            115,
            "                                            AdmissionAdminProfile.getDefaultInstance())"
          ],
          [
            116,
            "                                    .setDesiredAccountState("
          ],
          [
            117,
            "                                            IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)"
          ],
          [
            118,
            "                                    .addDesiredGrants(grant);"
          ],
          [
            119,
            "                    var access = request.adminAccess();"
          ],
          [
            120,
            "                    if (access != null)"
          ],
          [
            121,
            "                        target.setDesiredAdminProfile("
          ],
          [
            122,
            "                                AdmissionAdminProfile.newBuilder()"
          ],
          [
            123,
            "                                        .setAssigned(true)"
          ],
          [
            124,
            "                                        .setMode(access.mode())"
          ],
          [
            125,
            "                                        .addAllPermissions("
          ],
          [
            126,
            "                                                access.permissions() == null"
          ],
          [
            127,
            "                                                        ? List.of()"
          ],
          [
            128,
            "                                                        : access.permissions().stream()"
          ],
          [
            129,
            "                                                                .distinct()"
          ],
          [
            130,
            "                                                                .sorted()"
          ],
          [
            131,
            "                                                                .toList()));"
          ]
        ]
      },
      {
        "title": "Операция сохраняется до первого обращения в Auth",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityCoordinator.java",
        "language": "java",
        "lines": [
          [
            41,
            "    /** Accepted state and both canonical hashes are durable before the first Auth request. */"
          ],
          [
            42,
            "    public Operation execute(AcceptedCommand command) {"
          ],
          [
            43,
            "        outsideTransaction();"
          ],
          [
            44,
            "        Operation operation = store.accept(command);"
          ],
          [
            45,
            "        if (operation.phase() == Phase.ACCEPTED) {"
          ],
          [
            46,
            "            outsideTransaction();"
          ],
          [
            47,
            "            var rejection = store.pendingRejection(operation);"
          ],
          [
            48,
            "            IdentityOperationResult prepared;"
          ],
          [
            49,
            "            if (rejection != null) prepared = auth.reject(rejection);"
          ],
          [
            50,
            "            else {"
          ],
          [
            51,
            "                try {"
          ],
          [
            52,
            "                    // Only this request path carries the original live actor transport context."
          ],
          [
            53,
            "                    prepared = auth.prepare(operation.command());"
          ],
          [
            54,
            "                } catch (io.grpc.StatusRuntimeException denied) {"
          ],
          [
            55,
            "                    var reason = definiteRejection(denied.getStatus().getCode());"
          ]
        ]
      },
      {
        "title": "Academic применяет подготовленную операцию",
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
        "title": "Подтверждение Auth и ожидание обновлённой проекции",
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
          ],
          [
            269,
            "            if (committed.getCommittedCatalogVersion() <= 0) throw unavailable();"
          ],
          [
            270,
            "            try {"
          ],
          [
            271,
            "                catalog.await("
          ],
          [
            272,
            "                        committed.getCommittedCatalogVersion(),"
          ],
          [
            273,
            "                        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3));"
          ],
          [
            274,
            "            } catch (RuntimeException pendingCatalog) {"
          ],
          [
            275,
            "                // Auth has committed, but local lists cannot publish an incomplete projection."
          ],
          [
            276,
            "                // Recovery repeats this idempotent commit and catch-up without releasing fences."
          ],
          [
            277,
            "                return operation;"
          ],
          [
            278,
            "            }"
          ],
          [
            279,
            "            operation = store.succeeded(operation, committed);"
          ]
        ]
      }
    ]
  },
  {
    "id": "user-lifecycle-membership",
    "title": "Начать историю членства",
    "description": "Для начального назначения STUDENT Academic проверяет активную группу, покрытие истории и единственный доступный активный семестр. При создании членство начинается с первого дня этого семестра. При архивации открытый интервал закрывается датой операции по Москве. Прошлые интервалы, отметки и домашние задания сохраняются.",
    "frames": [
      {
        "title": "Проверяется единственный доступный семестр",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            1258,
            "    private Enrollment enrollment(long group) {"
          ],
          [
            1259,
            "        fences.requireGroupReadable(group);"
          ],
          [
            1260,
            "        var semesters ="
          ],
          [
            1261,
            "                jdbc.query("
          ],
          [
            1262,
            "                        \"SELECT id,date_from,date_to FROM semesters WHERE is_active AND NOT\""
          ],
          [
            1263,
            "                                + \" is_archived AND archive_transition='NONE' AND NOT\""
          ],
          [
            1264,
            "                                + \" archive_release_pending ORDER BY id LIMIT 2\","
          ],
          [
            1265,
            "                        (rs, n) ->"
          ],
          [
            1266,
            "                                new Semester("
          ],
          [
            1267,
            "                                        rs.getLong(\"id\"),"
          ],
          [
            1268,
            "                                        rs.getObject(\"date_from\", LocalDate.class),"
          ],
          [
            1269,
            "                                        rs.getObject(\"date_to\", LocalDate.class)));"
          ],
          [
            1270,
            "        if (semesters.size() != 1"
          ],
          [
            1271,
            "                || semesters.getFirst().from() == null"
          ],
          [
            1272,
            "                || semesters.getFirst().to() == null"
          ],
          [
            1273,
            "                || semesters.getFirst().from().isAfter(semesters.getFirst().to()))"
          ],
          [
            1274,
            "            throw Status.FAILED_PRECONDITION.asRuntimeException();"
          ],
          [
            1275,
            "        var semester = semesters.getFirst();"
          ]
        ]
      },
      {
        "title": "История группы должна покрывать начало семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            1276,
            "        var groups ="
          ],
          [
            1277,
            "                jdbc.query("
          ],
          [
            1278,
            "                        \"SELECT g.academic_revision,c.coverage_from,c.writer_version FROM groups g\""
          ],
          [
            1279,
            "                            + \" JOIN group_history_coverage c ON c.group_id=g.id WHERE g.id=? AND\""
          ],
          [
            1280,
            "                            + \" g.is_active\","
          ],
          [
            1281,
            "                        (rs, n) ->"
          ],
          [
            1282,
            "                                new Coverage("
          ],
          [
            1283,
            "                                        rs.getLong(\"academic_revision\"),"
          ],
          [
            1284,
            "                                        rs.getObject(\"coverage_from\", LocalDate.class),"
          ],
          [
            1285,
            "                                        rs.getString(\"writer_version\")),"
          ],
          [
            1286,
            "                        group);"
          ],
          [
            1287,
            "        if (groups.size() != 1"
          ],
          [
            1288,
            "                || !\"managed_v1\".equals(groups.getFirst().writer())"
          ],
          [
            1289,
            "                || groups.getFirst().from() == null"
          ],
          [
            1290,
            "                || groups.getFirst().from().isAfter(semester.from()))"
          ],
          [
            1291,
            "            throw Status.FAILED_PRECONDITION.asRuntimeException();"
          ],
          [
            1292,
            "        return new Enrollment("
          ],
          [
            1293,
            "                semester.id(),"
          ],
          [
            1294,
            "                semester.from(),"
          ]
        ]
      },
      {
        "title": "Academic открывает членство с датой зачисления",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java",
        "language": "java",
        "lines": [
          [
            211,
            "            } else if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE"
          ],
          [
            212,
            "                    && desired != null"
          ],
          [
            213,
            "                    && desired.getStatus().equals(\"ACTIVE\")) {"
          ],
          [
            214,
            "                requireGroupFence(command, desired.getGroupId());"
          ],
          [
            215,
            "                long history ="
          ],
          [
            216,
            "                        openMembership("
          ],
          [
            217,
            "                                target.getUserId(),"
          ],
          [
            218,
            "                                desired.getGroupId(),"
          ],
          [
            219,
            "                                command.initialMembershipDate(),"
          ],
          [
            220,
            "                                \"initial-enrollment\","
          ],
          [
            221,
            "                                true,"
          ],
          [
            222,
            "                                command.enrollmentSemesterId());"
          ],
          [
            223,
            "                initialMemberships.add("
          ],
          [
            224,
            "                        new InitialMembership(history, command.enrollmentSemesterId()));"
          ]
        ]
      },
      {
        "title": "Архив закрывает открытое членство, сохраняя интервал",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java",
        "language": "java",
        "lines": [
          [
            276,
            "            } else if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE) {"
          ],
          [
            277,
            "                var open = openMembershipGroups(target.getUserId());"
          ],
          [
            278,
            "                if (open.size() > 1) throw conflict();"
          ],
          [
            279,
            "                if (!open.isEmpty()) {"
          ],
          [
            280,
            "                    requireGroupFence(command, open.getFirst());"
          ],
          [
            281,
            "                    closeMembership("
          ],
          [
            282,
            "                            target.getUserId(),"
          ],
          [
            283,
            "                            open.getFirst(),"
          ],
          [
            284,
            "                            command.effectiveDate(),"
          ],
          [
            285,
            "                            null,"
          ],
          [
            286,
            "                            false);"
          ],
          [
            287,
            "                }"
          ],
          [
            288,
            "                if (wasHeadman.get(target.getUserId()))"
          ],
          [
            289,
            "                    revokeGroupAssistants(old.group(), acceptedActorId);"
          ],
          [
            290,
            "                jdbc.update("
          ],
          [
            291,
            "                        \"UPDATE academic_profiles SET archived_at=now(),directory_status='ARCHIVED'\""
          ],
          [
            292,
            "                                + \" WHERE id=?\","
          ],
          [
            293,
            "                        target.getUserId());"
          ],
          [
            294,
            "                revokeUserAssistants(command, target.getUserId(), acceptedActorId);"
          ]
        ]
      }
    ]
  },
  {
    "id": "user-lifecycle-preview",
    "title": "Проверить последствия архивации",
    "description": "Для предпросмотра и архивации требуется USERS_ARCHIVE. Экран показывает число связанных групп, отметок, групп со старостой и назначений, где преподаватель единственный. Предпросмотр привязан к администратору и пользователю, действует 10 минут. Academic перечитывает своё состояние и отклоняет устаревший снимок.",
    "frames": [
      {
        "title": "Пользователь видит четыре счётчика",
        "path": "frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue",
        "language": "typescript",
        "lines": [
          [
            3024,
            "          v-if=\"archivePreview\""
          ],
          [
            3025,
            "          class=\"admin-users-data\""
          ],
          [
            3026,
            "        >"
          ],
          [
            3027,
            "          <div>"
          ],
          [
            3028,
            "            <dt>Связанные группы</dt><dd>{{ archivePreview.linkedGroupCount }}</dd>"
          ],
          [
            3029,
            "          </div><div>"
          ],
          [
            3030,
            "            <dt>Отметки посещаемости</dt><dd>{{ archivePreview.attendanceMarksCount }}</dd>"
          ],
          [
            3031,
            "          </div><div>"
          ],
          [
            3032,
            "            <dt>Группы со старостой</dt><dd>{{ archivePreview.activeHeadmanGroupCount }}</dd>"
          ],
          [
            3033,
            "          </div><div>"
          ],
          [
            3034,
            "            <dt>Назначения единственного преподавателя</dt><dd>{{ archivePreview.soleTeacherAssignmentCount }}</dd>"
          ],
          [
            3035,
            "          </div>"
          ],
          [
            3036,
            "        </dl>"
          ]
        ]
      },
      {
        "title": "Предпросмотр и защищённый архив требуют USERS_ARCHIVE",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserController.java",
        "language": "java",
        "lines": [
          [
            151,
            "    @Override"
          ],
          [
            152,
            "    @RequireRole("
          ],
          [
            153,
            "            value = {ADMIN},"
          ],
          [
            154,
            "            admin = {\"USERS_ARCHIVE\"})"
          ],
          [
            155,
            "    public ResponseEntity<ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.Preview>"
          ],
          [
            156,
            "            previewUserArchive(Long id) {"
          ],
          [
            157,
            "        return ResponseEntity.ok()"
          ],
          [
            158,
            "                .cacheControl(org.springframework.http.CacheControl.noStore())"
          ],
          [
            159,
            "                .body(archiveService.preview(id));"
          ],
          [
            160,
            "    }"
          ],
          [
            161,
            ""
          ],
          [
            162,
            "    @Override"
          ],
          [
            163,
            "    @RequireRole("
          ],
          [
            164,
            "            value = {ADMIN},"
          ],
          [
            165,
            "            admin = {\"USERS_ARCHIVE\"})"
          ],
          [
            166,
            "    @AdminAction(\"user.archive\")"
          ],
          [
            167,
            "    public ResponseEntity<Void> archiveUserProtected("
          ],
          [
            168,
            "            Long id,"
          ],
          [
            169,
            "            ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest request) {"
          ],
          [
            170,
            "        archiveService.archive(id, request);"
          ],
          [
            171,
            "        return ResponseEntity.noContent()"
          ],
          [
            172,
            "                .cacheControl(org.springframework.http.CacheControl.noStore())"
          ],
          [
            173,
            "                .build();"
          ],
          [
            174,
            "    }"
          ]
        ]
      },
      {
        "title": "Снимок учитывает данные Attendance и срок",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserArchiveService.java",
        "language": "java",
        "lines": [
          [
            94,
            "        var remote = attendance.preview(target);"
          ],
          [
            95,
            "        Instant expires = Instant.now().plus(Duration.ofMinutes(10));"
          ],
          [
            110,
            "        tx.executeWithoutResult(status -> repository.savePreview(owner, local, digest, expires));"
          ],
          [
            111,
            "        return new Preview("
          ],
          [
            112,
            "                target,"
          ],
          [
            113,
            "                local.groupCount(),"
          ],
          [
            114,
            "                remote.getAttendanceMarksCount(),"
          ],
          [
            115,
            "                local.headmanCount(),"
          ],
          [
            116,
            "                local.soleTeacherCount(),"
          ],
          [
            117,
            "                local.requiresPassword(),"
          ],
          [
            118,
            "                local.observedAt(),"
          ],
          [
            119,
            "                Instant.ofEpochMilli(remote.getObservedAtEpochMs()),"
          ],
          [
            120,
            "                remote.getSnapshotDigest(),"
          ],
          [
            121,
            "                digest,"
          ],
          [
            122,
            "                expires);"
          ],
          [
            123,
            "    }"
          ]
        ]
      },
      {
        "title": "Проверяются владелец, цель, срок и актуальность",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserArchiveRepository.java",
        "language": "java",
        "lines": [
          [
            212,
            "    public void validatePreview(long owner, long target, String digest, Local local) {"
          ],
          [
            213,
            "        List<String> fingerprints ="
          ],
          [
            214,
            "                jdbc.queryForList("
          ],
          [
            215,
            "                        \"\"\""
          ],
          [
            216,
            "                        SELECT academic_digest FROM user_archive_preview WHERE preview_digest=?"
          ],
          [
            217,
            "                          AND owner_id=? AND target_id=? AND expires_at > NOW()"
          ],
          [
            218,
            "                        \"\"\","
          ],
          [
            219,
            "                        String.class,"
          ],
          [
            220,
            "                        digest,"
          ],
          [
            221,
            "                        owner,"
          ],
          [
            222,
            "                        target);"
          ],
          [
            223,
            "        if (fingerprints.size() != 1 || !fingerprints.getFirst().equals(local.digest())) {"
          ],
          [
            224,
            "            conflict(\"archive_preview_stale\");"
          ],
          [
            225,
            "        }"
          ]
        ]
      }
    ]
  },
  {
    "id": "user-lifecycle-guards",
    "title": "Подтвердить архив с учётом полномочий",
    "description": "Администратор не может архивировать себя. Ограниченный администратор не может архивировать активного администратора. Auth защищает последнего администратора с полным доступом. Активные роли HEADMAN и TEACHER требуют подтверждения текущим паролем выполняющего администратора. Число назначений единственного преподавателя помогает оценить последствия.",
    "frames": [
      {
        "title": "Academic блокирует архивирование самого себя",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserArchiveRepository.java",
        "language": "java",
        "lines": [
          [
            261,
            "    public void checkArchiveSafeguards(long owner, long target) {"
          ],
          [
            262,
            "        if (owner == target) denied();"
          ],
          [
            263,
            "        // Auth PREPARE owns self/protected-target/last-FULL checks against current grants."
          ],
          [
            264,
            "    }"
          ]
        ]
      },
      {
        "title": "Auth защищает активного администратора от LIMITED",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java",
        "language": "java",
        "lines": [
          [
            579,
            "            if (!actorProfile.full() && admins.activeAdmin(user))"
          ],
          [
            580,
            "                throw error(Status.PERMISSION_DENIED, \"ADMIN_TARGET_PROTECTED\");"
          ],
          [
            581,
            "            if (delta.getDesiredAccountState()"
          ],
          [
            582,
            "                            == IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED"
          ],
          [
            583,
            "                    && command.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE"
          ],
          [
            584,
            "                    && command.getKind() != IdentityOperationKind.IDENTITY_OPERATION_KIND_RECONCILE)"
          ],
          [
            585,
            "                throw invalid();"
          ]
        ]
      },
      {
        "title": "Последний FULL и сам администратор защищены",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java",
        "language": "java",
        "lines": [
          [
            675,
            "        if (!create"
          ],
          [
            676,
            "                && authorizationKind == IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE"
          ],
          [
            677,
            "                && user == actor.userId())"
          ],
          [
            678,
            "            throw error(Status.PERMISSION_DENIED, \"SELF_ARCHIVE_DENIED\");"
          ],
          [
            679,
            "        boolean desiredAdmin ="
          ],
          [
            680,
            "                desired.stream()"
          ],
          [
            681,
            "                        .anyMatch("
          ],
          [
            682,
            "                                g -> \"ADMIN\".equals(g.getRole()) && \"ACTIVE\".equals(g.getStatus()));"
          ],
          [
            683,
            "        if (desiredAdmin != profile.getAssigned()) throw invalid();"
          ],
          [
            684,
            "        if (!create"
          ],
          [
            685,
            "                && oldProfile.getAssigned()"
          ],
          [
            686,
            "                && \"FULL\".equals(oldProfile.getMode())"
          ],
          [
            687,
            "                && (!desiredAdmin"
          ],
          [
            688,
            "                        || !\"FULL\".equals(profile.getMode())"
          ],
          [
            689,
            "                        || delta.getDesiredAccountState()"
          ],
          [
            690,
            "                                == IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED))"
          ],
          [
            691,
            "            admins.protectLastFull(user);"
          ]
        ]
      },
      {
        "title": "Активные HEADMAN и TEACHER требуют подтверждения",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/user/UserArchiveRepository.java",
        "language": "java",
        "lines": [
          [
            132,
            "        boolean dangerous ="
          ],
          [
            133,
            "                Boolean.TRUE.equals("
          ],
          [
            134,
            "                        jdbc.queryForObject("
          ],
          [
            135,
            "                                \"\"\""
          ],
          [
            136,
            "SELECT EXISTS(SELECT 1 FROM academic_role_eligibility WHERE user_id=?"
          ],
          [
            137,
            "    AND role IN ('TEACHER','HEADMAN') AND status='ACTIVE')"
          ],
          [
            138,
            "\"\"\","
          ],
          [
            139,
            "                                Boolean.class,"
          ],
          [
            140,
            "                                target));"
          ],
          [
            141,
            "        // Warning concerns effective assignments on the observed Moscow date."
          ]
        ]
      },
      {
        "title": "Подтверждение Auth требуется по результатам оценки",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            463,
            "                    String proof ="
          ],
          [
            464,
            "                            material.impact().requiresPassword()"
          ],
          [
            465,
            "                                    ? archiveProof.confirm("
          ],
          [
            466,
            "                                            user, id, request.previewDigest(), request.password())"
          ],
          [
            467,
            "                                    : \"\";"
          ]
        ]
      }
    ]
  },
  {
    "id": "user-lifecycle-archive",
    "title": "Отозвать роли и сессии",
    "description": "При архивации Auth переводит grants в ARCHIVED, снимает административный профиль и переводит аккаунт в ARCHIVED. Версия ролей увеличивается при подготовке. При коммите очищается выбранная роль и повышается версия сессии, если эта роль перестала быть активной. Архив отзывает все ещё действующие сессии. Обычный вход проверяет, что аккаунт ACTIVE.",
    "frames": [
      {
        "title": "Архив формирует изменения аккаунта, ролей и прав",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            468,
            "                    var target = target(material.state().auth());"
          ],
          [
            469,
            "                    var grants ="
          ],
          [
            470,
            "                            target.getDesiredGrantsList().stream()"
          ],
          [
            471,
            "                                    .map(g -> g.toBuilder().setStatus(\"ARCHIVED\").build())"
          ],
          [
            472,
            "                                    .toList();"
          ],
          [
            473,
            "                    var old = target.getDesiredAdminProfile();"
          ],
          [
            474,
            "                    target.clearDesiredGrants()"
          ],
          [
            475,
            "                            .addAllDesiredGrants(canonicalGrants(grants))"
          ],
          [
            476,
            "                            .setDesiredAccountState("
          ],
          [
            477,
            "                                    IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED)"
          ],
          [
            478,
            "                            .setDesiredAdminProfile("
          ],
          [
            479,
            "                                    AdmissionAdminProfile.newBuilder()"
          ],
          [
            480,
            "                                            .setExpectedVersion(old.getExpectedVersion()));"
          ]
        ]
      },
      {
        "title": "На подготовке увеличивается roles_version",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java",
        "language": "java",
        "lines": [
          [
            692,
            "        long rv = create ? 1 : Math.addExact(before.rv(), 1);"
          ],
          [
            693,
            "        if (!create)"
          ],
          [
            694,
            "            jdbc.update("
          ],
          [
            695,
            "                    \"UPDATE auth_identities SET\""
          ],
          [
            696,
            "                            + \" roles_version=?,fence_operation_id=?,updated_at=NOW() WHERE id=?\","
          ],
          [
            697,
            "                    rv,"
          ],
          [
            698,
            "                    operation,"
          ],
          [
            699,
            "                    user);"
          ]
        ]
      },
      {
        "title": "Коммит очищает выбранную роль и меняет session_version",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java",
        "language": "java",
        "lines": [
          [
            284,
            "                        applyGrants(target);"
          ],
          [
            285,
            "                        applyAdminProfile(target);"
          ],
          [
            286,
            "                        // A rapid regrant must never revive a selection revoked by this commit."
          ],
          [
            287,
            "                        // Active grants for other selected roles retain their sessions and SV."
          ],
          [
            288,
            "                        jdbc.update("
          ],
          [
            289,
            "                                \"\"\""
          ],
          [
            290,
            "UPDATE auth_sessions s SET active_role_grant_id=NULL,session_version=session_version+1"
          ],
          [
            291,
            " WHERE s.user_id=? AND s.revoked_at IS NULL AND s.active_role_grant_id IS NOT NULL"
          ],
          [
            292,
            " AND NOT EXISTS(SELECT 1 FROM user_role_grants g WHERE g.id=s.active_role_grant_id"
          ],
          [
            293,
            "   AND g.user_id=s.user_id AND g.status='active')"
          ],
          [
            294,
            "\"\"\","
          ],
          [
            295,
            "                                target.userId());"
          ]
        ]
      },
      {
        "title": "Архив отзывает все действующие сессии",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/authority/AuthIdentityOperationStore.java",
        "language": "java",
        "lines": [
          [
            336,
            "                        // A new authority generation never revives an old access token."
          ],
          [
            337,
            "                        // Archive additionally destroys all current session admission."
          ],
          [
            338,
            "                        if (\"ARCHIVED\".equals(target.accountState()))"
          ],
          [
            339,
            "                            jdbc.update("
          ],
          [
            340,
            "                                    \"\"\""
          ],
          [
            341,
            "UPDATE auth_sessions SET revoked_at=NOW(),revoke_reason='SECURITY_REVOKED',"
          ],
          [
            342,
            " session_version=session_version+CASE WHEN active_role_grant_id IS NULL THEN 0 ELSE 1 END,"
          ],
          [
            343,
            " active_role_grant_id=NULL"
          ],
          [
            344,
            "WHERE user_id=? AND revoked_at IS NULL"
          ],
          [
            345,
            "\"\"\","
          ],
          [
            346,
            "                                    target.userId());"
          ],
          [
            347,
            "                    }"
          ]
        ]
      },
      {
        "title": "Вход по логину и Telegram требует ACTIVE",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/repository/UserRepository.java",
        "language": "java",
        "lines": [
          [
            14,
            "    @Query("
          ],
          [
            15,
            "            value ="
          ],
          [
            16,
            "                    \"SELECT * FROM auth_identities WHERE login = :login AND account_state =\""
          ],
          [
            17,
            "                            + \" 'ACTIVE' AND fence_operation_id IS NULL\","
          ],
          [
            18,
            "            nativeQuery = true)"
          ],
          [
            19,
            "    Optional<User> findByLogin(@Param(\"login\") String login);"
          ],
          [
            20,
            ""
          ],
          [
            21,
            "    @Query("
          ],
          [
            22,
            "            value ="
          ],
          [
            23,
            "                    \"SELECT * FROM auth_identities WHERE telegram_id = :telegramId AND\""
          ],
          [
            24,
            "                            + \" account_state = 'ACTIVE' AND fence_operation_id IS NULL\","
          ],
          [
            25,
            "            nativeQuery = true)"
          ],
          [
            26,
            "    Optional<User> findByTelegramId(@Param(\"telegramId\") Long telegramId);"
          ]
        ]
      }
    ]
  },
  {
    "id": "user-lifecycle-restore",
    "title": "Восстановить аккаунт",
    "description": "Восстановление возвращает аккаунт Auth и профиль Academic в ACTIVE. Прежние grants остаются ARCHIVED, ранее отозванные сессии сохраняют отзыв, а закрытые интервалы членства остаются в истории. После успешной проверки входа без выбранной роли Auth выдаёт bootstrap-токен для ограниченных действий. Учебные разделы становятся доступны после отдельного назначения роли и её выбора для сессии.",
    "frames": [
      {
        "title": "Восстановление отправляет UUID операции",
        "path": "frontends/mobile-core/src/features/admin-users/admin-users-client.ts",
        "language": "typescript",
        "lines": [
          [
            558,
            "  async restoreUser(id: number, idempotencyId: string, signal?: AbortSignal): Promise<void> {"
          ],
          [
            559,
            "    positiveInteger(id, 'userId')"
          ],
          [
            560,
            "    operationId(idempotencyId)"
          ],
          [
            561,
            "    await this.runIdentity("
          ],
          [
            562,
            "      `${AdminUsersClient.basePath}/${id}/restore`,"
          ],
          [
            563,
            "      {"
          ],
          [
            564,
            "        method: 'POST',"
          ],
          [
            565,
            "        cache: 'no-store',"
          ],
          [
            566,
            "        body: JSON.stringify({ operationId: idempotencyId }),"
          ],
          [
            567,
            "        ...(signal ? { signal } : {}),"
          ],
          [
            568,
            "      },"
          ],
          [
            569,
            "      idempotencyId,"
          ],
          [
            570,
            "    )"
          ],
          [
            571,
            "  }"
          ]
        ]
      },
      {
        "title": "Запрос на восстановление аккаунта",
        "language": "json",
        "example": [
          "{",
          "  \"operationId\": \"c9082e06-af27-49c0-9cda-66dd3b32cd24\"",
          "}"
        ]
      },
      {
        "title": "Команда восстановления принимает только архивные grants",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            511,
            "    public void restore(long user, UserArchiveModels.RestoreRequest request) {"
          ],
          [
            512,
            "        if (request == null || request.operationId() == null)"
          ],
          [
            513,
            "            throw new BadRequestException(\"Требуется UUID операции\");"
          ],
          [
            514,
            "        var intent = intent(\"RESTORE\", user, new RestoreIntent(user));"
          ],
          [
            515,
            "        http.execute("
          ],
          [
            516,
            "                request.operationId(),"
          ],
          [
            517,
            "                intent,"
          ],
          [
            518,
            "                id -> {"
          ],
          [
            523,
            "                                var state = state(identity, true);"
          ],
          [
            524,
            "                                var target = target(state.auth());"
          ],
          [
            525,
            "                                if (target.getDesiredGrantsList().stream()"
          ],
          [
            526,
            "                                        .anyMatch(g -> !g.getStatus().equals(\"ARCHIVED\")))"
          ],
          [
            527,
            "                                    throw Status.FAILED_PRECONDITION"
          ],
          [
            528,
            "                                            .withDescription(\"archived grants required\")"
          ],
          [
            529,
            "                                            .asRuntimeException();"
          ],
          [
            536,
            "                                return accepted("
          ],
          [
            537,
            "                                        command("
          ],
          [
            538,
            "                                                id,"
          ],
          [
            539,
            "                                                IdentityOperationKind"
          ],
          [
            540,
            "                                                        .IDENTITY_OPERATION_KIND_RESTORE,"
          ],
          [
            541,
            "                                                target),"
          ],
          [
            542,
            "                                        local(intent, null, groups, null, null, \"restore\"));"
          ],
          [
            543,
            "                            });"
          ]
        ]
      },
      {
        "title": "Auth получает ACTIVE и прежние статусы grants",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            1093,
            "    private IdentityTargetCommand.Builder target(IdentityAuthoritySnapshot state) {"
          ],
          [
            1094,
            "        var profile = state.getAdminProfile();"
          ],
          [
            1095,
            "        return IdentityTargetCommand.newBuilder()"
          ],
          [
            1096,
            "                .setUserId(state.getUserId())"
          ],
          [
            1097,
            "                .setExpectedRolesVersion(state.getRolesVersion())"
          ],
          [
            1098,
            "                .setExpectedCredentialsVersion(state.getCredentialsVersion())"
          ],
          [
            1099,
            "                .setExpectedTelegramBindingVersion(state.getTelegramBindingVersion())"
          ],
          [
            1100,
            "                .setExpectedAcademicRevision(state.getAcademicRevision())"
          ],
          [
            1101,
            "                .setDesiredAccountState(IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)"
          ],
          [
            1102,
            "                .setDesiredAdminProfile("
          ],
          [
            1112,
            "                .addAllDesiredGrants("
          ],
          [
            1113,
            "                        canonicalGrants("
          ],
          [
            1114,
            "                                state.getGrantsList().stream()"
          ],
          [
            1115,
            "                                        .map("
          ],
          [
            1116,
            "                                                g -> {"
          ],
          [
            1117,
            "                                                    var grant ="
          ],
          [
            1118,
            "                                                            AdmissionGrant.newBuilder()"
          ],
          [
            1119,
            "                                                                    .setGrantId(g.getGrantId())"
          ],
          [
            1120,
            "                                                                    .setRole(g.getRole())"
          ],
          [
            1121,
            "                                                                    .setStatus(g.getStatus());"
          ],
          [
            1122,
            "                                                    if (g.hasGroupId())"
          ],
          [
            1123,
            "                                                        grant.setGroupId(g.getGroupId());"
          ],
          [
            1124,
            "                                                    return grant.build();"
          ]
        ]
      },
      {
        "title": "Academic возвращает профиль в активное состояние",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java",
        "language": "java",
        "lines": [
          [
            295,
            "            } else if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_RESTORE) {"
          ],
          [
            296,
            "                jdbc.update("
          ],
          [
            297,
            "                        \"UPDATE academic_profiles SET archived_at=NULL,directory_status='ACTIVE'\""
          ],
          [
            298,
            "                                + \" WHERE id=?\","
          ],
          [
            299,
            "                        target.getUserId());"
          ],
          [
            300,
            "                revokeUserAssistants(command, target.getUserId(), acceptedActorId);"
          ],
          [
            301,
            "            }"
          ]
        ]
      },
      {
        "title": "Политика выбирает только доступный grant",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/session/ActiveRolePolicy.java",
        "language": "java",
        "lines": [
          [
            33,
            "        RoleGrant defaultGrant ="
          ],
          [
            34,
            "                copy.stream()"
          ],
          [
            35,
            "                        .filter(RoleGrant::isSelectable)"
          ],
          [
            36,
            "                        .filter(grant -> grant.role() == AuthRole.STUDENT)"
          ],
          [
            37,
            "                        .findFirst()"
          ],
          [
            38,
            "                        .orElseGet("
          ],
          [
            39,
            "                                () ->"
          ],
          [
            40,
            "                                        copy.stream()"
          ],
          [
            41,
            "                                                .filter(RoleGrant::isSelectable)"
          ],
          [
            42,
            "                                                .filter(grant -> grant.role() == AuthRole.TEACHER)"
          ],
          [
            43,
            "                                                .findFirst()"
          ],
          [
            44,
            "                                                .orElse(null));"
          ],
          [
            45,
            "        return Evaluation.success(copy, defaultGrant);"
          ],
          [
            46,
            "    }"
          ]
        ]
      },
      {
        "title": "Без выбранной роли выдаётся ограниченный токен",
        "path": "services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/AuthService.java",
        "language": "java",
        "lines": [
          [
            784,
            "        String refresh ="
          ],
          [
            785,
            "                jwtService.generateSessionRefreshToken("
          ],
          [
            786,
            "                        snapshot, refreshJti, issuedAt, absoluteRefreshExpiry);"
          ],
          [
            787,
            "        String access ="
          ],
          [
            788,
            "                snapshot.activeRole() == null"
          ],
          [
            789,
            "                        ? jwtService.generateBootstrapToken(snapshot, issuedAt, accessExpiry)"
          ],
          [
            790,
            "                        : jwtService.generateSessionAccessToken(snapshot, issuedAt, accessExpiry);"
          ],
          [
            791,
            "        long expiresIn = Math.max(1L, accessExpiry.getEpochSecond() - issuedAt.getEpochSecond());"
          ],
          [
            792,
            "        return new TokenResponse(access, refresh, expiresIn);"
          ],
          [
            793,
            "    }"
          ]
        ]
      },
      {
        "title": "Проверка восстановления: данные остались, роли и сессия не вернулись",
        "path": "services/academic-service/academic-app/src/test/java/ru/rutcampustrack/academic/integration/UserArchiveLifecycleIT.java",
        "language": "java",
        "lines": [
          [
            482,
            "        RestoreRequest restore = new RestoreRequest(UUID.randomUUID());"
          ],
          [
            483,
            "        restore(target, restore);"
          ],
          [
            484,
            "        restore(target, restore);"
          ],
          [
            485,
            "        assertThat("
          ],
          [
            486,
            "                        jdbc.queryForList(retainedHomeworkSql, homework, completion, target)"
          ],
          [
            487,
            "                                .equals(retainedHomework))"
          ],
          [
            488,
            "                .isTrue();"
          ],
          [
            489,
            "        assertThat("
          ],
          [
            490,
            "                        identityFixture"
          ],
          [
            491,
            "                                .authJdbc()"
          ],
          [
            492,
            "                                .queryForObject("
          ],
          [
            493,
            "                                        \"SELECT COUNT(*) FROM user_role_grants WHERE user_id=? AND\""
          ],
          [
            494,
            "                                                + \" status='active'\","
          ],
          [
            495,
            "                                        Long.class,"
          ],
          [
            496,
            "                                        target))"
          ],
          [
            497,
            "                .isZero();"
          ],
          [
            498,
            "        assertThat("
          ],
          [
            499,
            "                        jdbc.queryForObject("
          ],
          [
            500,
            "                                \"SELECT group_id IS NULL AND NOT is_headman FROM\""
          ],
          [
            501,
            "                                        + \" academic_user_directory WHERE id=?\","
          ],
          [
            502,
            "                                Boolean.class,"
          ],
          [
            503,
            "                                target))"
          ],
          [
            504,
            "                .isTrue();"
          ],
          [
            508,
            "                                .queryForObject("
          ],
          [
            509,
            "                                        \"SELECT revoked_at IS NOT NULL AND\""
          ],
          [
            510,
            "                                            + \" revoke_reason='SECURITY_REVOKED' FROM auth_sessions\""
          ],
          [
            511,
            "                                            + \" WHERE sid=?\","
          ],
          [
            512,
            "                                        Boolean.class,"
          ],
          [
            513,
            "                                        session))"
          ],
          [
            514,
            "                .isTrue();"
          ],
          [
            515,
            "        requireOldSessionDenied();"
          ]
        ]
      }
    ]
  },
  {
    "id": "user-lifecycle-roles",
    "title": "Назначить роли заново",
    "description": "После восстановления экран предлагает назначить нужные роли. Это отдельная команда с USERS_MANAGE для STUDENT или TEACHER. Для STUDENT снова выбирается группа и проверяется Telegram, для TEACHER проверяется табельный номер. Повторно назначенная студенческая роль открывает новый интервал членства с текущей даты по Москве.",
    "frames": [
      {
        "title": "После восстановления экран предлагает назначить роли",
        "path": "frontends/mobile-core/src/features/admin-users/AdminUsersScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1349,
            "    view.value = 'list';"
          ],
          [
            1350,
            "    notice.value = restoring"
          ],
          [
            1351,
            "      ? 'Аккаунт восстановлен без активных прав. Назначь нужные роли заново.'"
          ],
          [
            1352,
            "      : 'Аккаунт архивирован. История сохранена.';"
          ],
          [
            1353,
            "    await refresh();"
          ]
        ]
      },
      {
        "title": "PUT сохраняет роль пользователя отдельно",
        "path": "frontends/mobile-core/src/features/admin-users/admin-users-client.ts",
        "language": "typescript",
        "lines": [
          [
            475,
            "  updateRole("
          ],
          [
            476,
            "    id: number,"
          ],
          [
            477,
            "    role: Exclude<AdminManagedRole, 'HEADMAN'>,"
          ],
          [
            478,
            "    input: UpdateAdminRoleInput,"
          ],
          [
            479,
            "    signal?: AbortSignal,"
          ],
          [
            480,
            "  ): Promise<AdminUser> {"
          ],
          [
            481,
            "    positiveInteger(id, 'userId')"
          ],
          [
            482,
            "    const payload = normalizeRoleInput(input)"
          ],
          [
            483,
            "    return this.identityRequest(`${AdminUsersClient.basePath}/${id}/roles/${role}`, {"
          ],
          [
            484,
            "      method: 'PUT',"
          ],
          [
            485,
            "      body: JSON.stringify(payload),"
          ],
          [
            486,
            "      ...(signal ? { signal } : {}),"
          ],
          [
            487,
            "    }).then(normalizeUser)"
          ],
          [
            488,
            "  }"
          ]
        ]
      },
      {
        "title": "Назначение роли преподавателя",
        "language": "json",
        "example": [
          "{",
          "  \"status\": \"ACTIVE\",",
          "  \"employeeNumber\": \"DEMO-042\"",
          "}"
        ]
      },
      {
        "title": "Сервер проверяет группу и Telegram для нового STUDENT",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            182,
            "                    var target = target(state.auth());"
          ],
          [
            183,
            "                    var grants = new ArrayList<>(target.getDesiredGrantsList());"
          ],
          [
            184,
            "                    var old = grant(grants, role);"
          ],
          [
            185,
            "                    boolean fresh = old == null || old.getStatus().equals(\"ARCHIVED\");"
          ],
          [
            186,
            "                    var changed ="
          ],
          [
            187,
            "                            old == null"
          ],
          [
            188,
            "                                    ? AdmissionGrant.newBuilder().setRole(role)"
          ],
          [
            189,
            "                                    : old.toBuilder();"
          ],
          [
            190,
            "                    var groups = new ArrayList<AcademicIdentityLocalCommand.GroupRevision>();"
          ],
          [
            191,
            "                    Enrollment enrollment = null;"
          ],
          [
            192,
            "                    var profile = state.profile();"
          ],
          [
            193,
            "                    if (role.equals(\"STUDENT\")) {"
          ],
          [
            194,
            "                        Long group = old != null && old.hasGroupId() ? old.getGroupId() : null;"
          ],
          [
            195,
            "                        if (fresh) {"
          ],
          [
            196,
            "                            Long telegram ="
          ],
          [
            197,
            "                                    request.telegramId() != null"
          ],
          [
            198,
            "                                            ? request.telegramId()"
          ],
          [
            199,
            "                                            : state.auth().hasTelegramId()"
          ],
          [
            200,
            "                                                    ? state.auth().getTelegramId()"
          ],
          [
            201,
            "                                                    : null;"
          ],
          [
            202,
            "                            if (request.groupId() == null || telegram == null)"
          ],
          [
            203,
            "                                throw new BadRequestException("
          ],
          [
            204,
            "                                        \"Для добавления STUDENT нужны группа и Telegram ID\");"
          ],
          [
            205,
            "                            enrollment = enrollment(request.groupId());"
          ],
          [
            206,
            "                            group = request.groupId();"
          ]
        ]
      },
      {
        "title": "Дата повторного назначения и открытие нового интервала",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
          [
            249,
            "                    changed.setStatus(request.status().name());"
          ],
          [
            250,
            "                    replace(grants, changed.build());"
          ],
          [
            251,
            "                    target.clearDesiredGrants().addAllDesiredGrants(canonicalGrants(grants));"
          ],
          [
            252,
            "                    var local ="
          ],
          [
            253,
            "                            local("
          ],
          [
            254,
            "                                    intent(\"ROLE\", user, new RoleIntent(role, request)),"
          ],
          [
            255,
            "                                    profile,"
          ],
          [
            256,
            "                                    groups,"
          ],
          [
            257,
            "                                    enrollment == null ? null : enrollment.semester(),"
          ],
          [
            258,
            "                                    enrollment == null"
          ],
          [
            259,
            "                                            ? null"
          ],
          [
            260,
            "                                            : old == null"
          ],
          [
            261,
            "                                                    ? enrollment.date()"
          ],
          [
            262,
            "                                                    : LocalDate.now(MOSCOW),"
          ],
          [
            263,
            "                                    \"role-status\");"
          ]
        ]
      },
      {
        "title": "Academic создаёт новый интервал после архива",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java",
        "language": "java",
        "lines": [
          [
            234,
            "                if (old == null || \"ARCHIVED\".equals(old.status())) {"
          ],
          [
            235,
            "                    requireGroupFence(command, desired.getGroupId());"
          ],
          [
            236,
            "                    boolean initial = old == null;"
          ],
          [
            237,
            "                    long history ="
          ],
          [
            238,
            "                            openMembership("
          ],
          [
            239,
            "                                    target.getUserId(),"
          ],
          [
            240,
            "                                    desired.getGroupId(),"
          ],
          [
            241,
            "                                    command.initialMembershipDate(),"
          ],
          [
            242,
            "                                    initial ? \"initial-enrollment\" : \"role-reassignment\","
          ],
          [
            243,
            "                                    initial,"
          ],
          [
            244,
            "                                    initial ? command.enrollmentSemesterId() : null);"
          ]
        ]
      }
    ]
  },
  {
    "id": "user-lifecycle-recovery",
    "title": "Проверить уже принятую операцию",
    "description": "Клиент передаёт UUID в Idempotency-Key. Для архива и восстановления он совпадает с operationId в теле. При неопределённом результате HTTP 202 содержит ссылку на ту же операцию. Academic продолжает сохранённые команды в фоне. Повтор связывается с принятым намерением, поэтому повтор запроса не создаёт ещё один профиль и не повторяет отзыв сессий.",
    "frames": [
      {
        "title": "Клиент удерживает UUID и параметры намерения",
        "path": "frontends/mobile-core/src/shared/identity-operation.ts",
        "language": "typescript",
        "lines": [
          [
            61,
            "  async run(path: string, init: RequestInit, id: string = crypto.randomUUID()): Promise<Response> {"
          ],
          [
            62,
            "    this.guard()"
          ],
          [
            63,
            "    const fingerprint = intentFingerprint(path, init)"
          ],
          [
            64,
            "    if (this.intent && this.intent.fingerprint !== fingerprint)"
          ],
          [
            65,
            "      throw new IdentityOperationError(this.intent.state)"
          ],
          [
            66,
            "    if (!this.intent) {"
          ],
          [
            67,
            "      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id))"
          ],
          [
            68,
            "        throw new Error('Некорректный номер операции.')"
          ],
          [
            69,
            "      const headers = new Headers(init.headers)"
          ],
          [
            70,
            "      headers.set('Idempotency-Key', id)"
          ],
          [
            71,
            "      // Navigation may stop observing the request, but must not discard its accepted intent."
          ],
          [
            72,
            "      const detached = { ...init }"
          ],
          [
            73,
            "      delete detached.signal"
          ],
          [
            74,
            "      this.intent = {"
          ],
          [
            75,
            "        id,"
          ],
          [
            76,
            "        path,"
          ],
          [
            77,
            "        fingerprint,"
          ],
          [
            78,
            "        init: { ...detached, headers },"
          ],
          [
            79,
            "        state: 'UNKNOWN',"
          ],
          [
            80,
            "        retryAt: 0,"
          ],
          [
            81,
            "        retainUntilAcknowledged: Boolean(init.signal),"
          ],
          [
            82,
            "        completed: false,"
          ],
          [
            83,
            "      }"
          ],
          [
            84,
            "      return (await this.execute(false)).response.clone()"
          ],
          [
            85,
            "    }"
          ],
          [
            86,
            "    return (await this.execute(true)).response.clone()"
          ]
        ]
      },
      {
        "title": "Ключ и operationId проверяются на совпадение",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityHttpOperations.java",
        "language": "java",
        "lines": [
          [
            37,
            "    public AcademicIdentityCoordinator.Operation execute("
          ],
          [
            38,
            "            UUID operation,"
          ],
          [
            39,
            "            AcademicIdentityLocalCommand.ApiIntent intent,"
          ],
          [
            40,
            "            Function<UUID, AcademicIdentityCoordinator.AcceptedCommand> build) {"
          ],
          [
            41,
            "        if (operation == null) throw Status.INVALID_ARGUMENT.asRuntimeException();"
          ],
          [
            42,
            "        String supplied = request.getHeader(\"Idempotency-Key\");"
          ],
          [
            43,
            "        Object current = request.getAttribute(AcademicIdentityHttpOperations.class.getName());"
          ],
          [
            44,
            "        if (supplied != null && !supplied.equals(operation.toString())"
          ],
          [
            45,
            "                || current instanceof UUID id && !id.equals(operation))"
          ],
          [
            46,
            "            throw Status.ALREADY_EXISTS"
          ],
          [
            47,
            "                    .withDescription(\"operation id differs from request key\")"
          ],
          [
            48,
            "                    .asRuntimeException();"
          ],
          [
            49,
            "        request.setAttribute(AcademicIdentityHttpOperations.class.getName(), operation);"
          ],
          [
            50,
            "        return execute(intent, build);"
          ],
          [
            51,
            "    }"
          ]
        ]
      },
      {
        "title": "Сначала ищется уже принятая команда",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityHttpOperations.java",
        "language": "java",
        "lines": [
          [
            58,
            "        UUID id = key();"
          ],
          [
            59,
            "        headers(id);"
          ],
          [
            60,
            "        var accepted = store.acceptedForActor(id.toString(), intent);"
          ],
          [
            61,
            "        boolean fresh = accepted == null;"
          ],
          [
            62,
            "        if (fresh) {"
          ],
          [
            63,
            "            try {"
          ],
          [
            64,
            "                accepted = build.apply(id);"
          ],
          [
            65,
            "            } catch (LocalPreflightRejection rejected) {"
          ],
          [
            66,
            "                if (store.rejectPreflight(id.toString(), intent)) throw rejected.original;"
          ],
          [
            67,
            "                // A concurrent exact positive acceptance wins; report/recover its real outcome."
          ],
          [
            68,
            "                var winner = store.acceptedForActor(id.toString(), intent);"
          ],
          [
            69,
            "                if (winner == null) throw Status.UNAVAILABLE.asRuntimeException();"
          ],
          [
            70,
            "                return complete(executeStored(winner));"
          ]
        ]
      },
      {
        "title": "HTTP 202 содержит ID и ссылку на статус",
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
      },
      {
        "title": "Фоновая задача продолжает принятые операции",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityOperationRecovery.java",
        "language": "java",
        "lines": [
          [
            33,
            "    public void recover() {"
          ],
          [
            34,
            "        for (String id : store.pending()) {"
          ],
          [
            35,
            "            try {"
          ],
          [
            36,
            "                coordinator.recover(id);"
          ],
          [
            37,
            "            } catch (RuntimeException deferred) {"
          ],
          [
            38,
            "                store.retryLater(id);"
          ],
          [
            39,
            "                LOG.warn("
          ],
          [
            40,
            "                        \"Identity operation recovery deferred; accepted state and fences retained\");"
          ],
          [
            41,
            "            }"
          ],
          [
            42,
            "        }"
          ]
        ]
      }
    ]
  }
]
