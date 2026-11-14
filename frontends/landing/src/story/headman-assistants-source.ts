import type { StoryOperation } from './story-source'

export const headmanAssistantsOperations: readonly StoryOperation[] = [
  {
    "id": "headman-assistants-grant",
    "title": "Права в своей группе",
    "description": "Староста выбирает студента из своей группы и набор действий. Есть пять прав. Они открывают отметки посещаемости, обработку заявок, управление ДЗ, отмену занятий и просмотр статистики. Перед назначением Academic блокирует группу, подтверждает действующего старосту и проверяет активное членство студента.",
    "frames": [
      {
        "title": "Пять прав помощника",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantService.java",
        "language": "java",
        "lines": [
          [
            321,
            "    private static String permissionLabel(AssistantPermission permission) {"
          ],
          [
            322,
            "        return switch (permission) {"
          ],
          [
            323,
            "            case MARK_ATTENDANCE -> \"Отмечать посещаемость\";"
          ],
          [
            324,
            "            case MANAGE_EXCUSES -> \"Обрабатывать уважительные причины\";"
          ],
          [
            325,
            "            case MANAGE_HOMEWORK -> \"Управлять домашними заданиями\";"
          ],
          [
            326,
            "            case CANCEL_LESSONS -> \"Отменять занятия\";"
          ],
          [
            327,
            "            case VIEW_STATS -> \"Просматривать статистику\";"
          ]
        ]
      },
      {
        "title": "Блокировка группы и проверка старосты",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantService.java",
        "language": "java",
        "lines": [
          [
            88,
            "    private Long lockAndRequireCurrentHeadman(Long requestedGroupId) {"
          ],
          [
            89,
            "        Long groupId = requireCurrentHeadmanRead(requestedGroupId);"
          ],
          [
            90,
            "        Group group ="
          ],
          [
            91,
            "                groupRepository"
          ],
          [
            92,
            "                        .findByIdForUpdate(groupId)"
          ],
          [
            93,
            "                        .orElseThrow(() -> new AccessDeniedException(\"Учебная группа не найдена\"));"
          ],
          [
            94,
            "        Long actorId = requestContext.getUserId();"
          ],
          [
            95,
            "        boolean currentHeadman ="
          ],
          [
            96,
            "                actorId != null"
          ],
          [
            97,
            "                        && grantRepository"
          ],
          [
            98,
            "                                .findByUserIdAndRoleAndStatus(actorId, \"headman\", \"active\")"
          ],
          [
            99,
            "                                .stream()"
          ],
          [
            100,
            "                                .anyMatch(grant -> groupId.equals(grant.getGroupId()));"
          ],
          [
            101,
            "        if (!group.isActive() || !currentHeadman) {"
          ],
          [
            102,
            "            throw new AccessDeniedException("
          ],
          [
            103,
            "                    \"Текущая сессия больше не является старостой этой группы\");"
          ],
          [
            104,
            "        }"
          ],
          [
            105,
            "        return group.getId();"
          ],
          [
            106,
            "    }"
          ],
          [
            107,
            ""
          ],
          [
            108,
            "    private void requireActiveStudentGrant(Long studentId, Long groupId) {"
          ],
          [
            109,
            "        Long grantedGroupId = userRepository.findActiveStudentGrantGroupId(studentId).orElse(null);"
          ],
          [
            110,
            "        if (grantedGroupId == null || !grantedGroupId.equals(groupId)) {"
          ],
          [
            111,
            "            throw new AccessDeniedException(\"Студент не имеет активного права в этой группе\");"
          ],
          [
            112,
            "        }"
          ]
        ]
      },
      {
        "title": "Назначение проверяет активное право студента",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantService.java",
        "language": "java",
        "lines": [
          [
            115,
            "    @Transactional"
          ],
          [
            116,
            "    public HeadmanAssistant assignAssistant(AssignAssistantRequest request) {"
          ],
          [
            117,
            "        // M13 G9 — request.groupId должен совпадать с группой headman'а"
          ],
          [
            118,
            "        Long groupId = lockAndRequireCurrentHeadman(request.groupId());"
          ],
          [
            119,
            ""
          ],
          [
            120,
            "        // The durable active STUDENT grant is the membership authority. The"
          ],
          [
            121,
            "        // legacy users.group_id field and the client groupId are not enough."
          ],
          [
            122,
            "        userRepository"
          ],
          [
            123,
            "                .findById(request.studentId())"
          ],
          [
            124,
            "                .orElseThrow("
          ],
          [
            125,
            "                        () -> new ResourceNotFoundException(\"User\", \"id\", request.studentId()));"
          ],
          [
            126,
            "        requireActiveStudentGrant(request.studentId(), groupId);"
          ],
          [
            127,
            ""
          ],
          [
            128,
            "        // Check no existing active assistant"
          ],
          [
            129,
            "        assistantRepository"
          ],
          [
            130,
            "                .findByGroupIdAndStudentIdAndIsActiveTrue(groupId, request.studentId())"
          ],
          [
            137,
            "        // Convert permissions to lowercase string array (Pitfall 4)"
          ],
          [
            138,
            "        String[] permissionsArray ="
          ],
          [
            139,
            "                request.permissions().stream()"
          ],
          [
            140,
            "                        .map(p -> p.name().toLowerCase())"
          ],
          [
            141,
            "                        .toArray(String[]::new);"
          ],
          [
            142,
            ""
          ],
          [
            143,
            "        HeadmanAssistant assistant ="
          ],
          [
            144,
            "                new HeadmanAssistant("
          ],
          [
            145,
            "                        groupId, request.studentId(), permissionsArray, requestContext.getUserId());"
          ],
          [
            146,
            "        HeadmanAssistant saved = assistantRepository.save(assistant);"
          ]
        ]
      }
    ]
  },
  {
    "id": "headman-assistants-change",
    "title": "Обновление по снимку",
    "description": "Мобильный экран запоминает набор до и после изменения. При подтверждении он отправляет оба набора. Academic под блокировкой сравнивает ожидаемые и актуальные права как множества. Если другой запрос уже изменил права, сервер возвращает 409 и предлагает перечитать список.",
    "frames": [
      {
        "title": "Ожидаемые права берутся из подтверждённого снимка",
        "path": "frontends/mobile-core/src/features/headman-group/HeadmanGroupScreen.vue",
        "language": "typescript",
        "lines": [
          [
            719,
            "  const result = confirmationFlow.confirm(confirmationContext());"
          ],
          [
            720,
            "  if (result.kind === 'invalid' || !baselineMatchesCurrent()) {"
          ],
          [
            721,
            "    closeDialog();"
          ],
          [
            722,
            "    error.value = 'Права изменились. Проверь список и подтверди изменения заново.';"
          ],
          [
            723,
            "    return;"
          ],
          [
            724,
            "  }"
          ],
          [
            725,
            "  if (result.kind === 'next') {"
          ],
          [
            726,
            "    confirmation.value = result.state;"
          ],
          [
            727,
            "    return;"
          ],
          [
            728,
            "  }"
          ],
          [
            729,
            "  const confirmed = result.snapshot;"
          ],
          [
            730,
            "  const api = props.api,"
          ],
          [
            731,
            "    groupId = props.groupId,"
          ],
          [
            732,
            "    owner = ownerRevision;"
          ],
          [
            733,
            "  const pending: PermissionIntent = {"
          ],
          [
            734,
            "    memberId: member.value.id,"
          ],
          [
            735,
            "    assistantId: confirmed.assistantId,"
          ],
          [
            736,
            "    permissions: [...confirmed.after],"
          ],
          [
            737,
            "    before: [...confirmed.before],"
          ],
          [
            738,
            "  };"
          ],
          [
            745,
            "  try {"
          ],
          [
            746,
            "    if (pending.assistantId !== null)"
          ],
          [
            747,
            "      await api.updatePermissions(pending.assistantId, pending.permissions, pending.before);"
          ],
          [
            748,
            "    else await api.assignAssistant(groupId, pending.memberId, pending.permissions);"
          ],
          [
            749,
            "    if (owner !== ownerRevision || disposed) return;"
          ],
          [
            750,
            "    if (!props.offline) await load();"
          ]
        ]
      },
      {
        "title": "PATCH передаёт новые и ожидаемые права",
        "path": "frontends/mobile-core/src/features/headman-group/headman-group-client.ts",
        "language": "typescript",
        "lines": [
          [
            345,
            "    permissions: readonly HeadmanAssistantPermission[],"
          ],
          [
            346,
            "    expectedPermissions?: readonly HeadmanAssistantPermission[],"
          ],
          [
            347,
            "  ): Promise<HeadmanAssistant> {"
          ],
          [
            348,
            "    assertPositiveInteger(assistantId, 'assistantId')"
          ],
          [
            349,
            "    const normalized = normalizePermissions(permissions)"
          ],
          [
            350,
            "    if (normalized.length === 0 && expectedPermissions === undefined)"
          ],
          [
            351,
            "      throw new RangeError('permissions must not be empty without an expected snapshot')"
          ],
          [
            352,
            "    return normalizeAssistant("
          ],
          [
            353,
            "      await this.request<unknown>(`/api/academic/assistants/${assistantId}/permissions`, {"
          ],
          [
            354,
            "        method: 'PATCH',"
          ],
          [
            355,
            "        body: JSON.stringify({"
          ],
          [
            356,
            "          permissions: normalized,"
          ],
          [
            357,
            "          ...(expectedPermissions === undefined"
          ],
          [
            358,
            "            ? {}"
          ],
          [
            359,
            "            : { expectedPermissions: normalizePermissions(expectedPermissions) }),"
          ],
          [
            360,
            "        }),"
          ],
          [
            361,
            "      }),"
          ],
          [
            362,
            "    )"
          ]
        ]
      },
      {
        "title": "Устаревший снимок даёт конфликт",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantService.java",
        "language": "java",
        "lines": [
          [
            220,
            "    public HeadmanAssistant updatePermissions(Long id, UpdateAssistantPermissionsRequest request) {"
          ],
          [
            221,
            "        // M13 G9 — assistant должен принадлежать группе headman'а"
          ],
          [
            222,
            "        Long groupId ="
          ],
          [
            223,
            "                assistantRepository"
          ],
          [
            224,
            "                        .findGroupIdById(id)"
          ],
          [
            225,
            "                        .orElseThrow("
          ],
          [
            226,
            "                                () -> new ResourceNotFoundException(\"HeadmanAssistant\", \"id\", id));"
          ],
          [
            227,
            "        lockAndRequireCurrentHeadman(groupId);"
          ],
          [
            228,
            "        HeadmanAssistant assistant ="
          ],
          [
            229,
            "                assistantRepository"
          ],
          [
            230,
            "                        .findByIdForUpdate(id)"
          ],
          [
            231,
            "                        .orElseThrow("
          ],
          [
            232,
            "                                () -> new ResourceNotFoundException(\"HeadmanAssistant\", \"id\", id));"
          ],
          [
            233,
            "        if (!groupId.equals(assistant.getGroupId())) {"
          ],
          [
            234,
            "            throw new AccessDeniedException(\"Помощник принадлежит другой группе\");"
          ],
          [
            235,
            "        }"
          ],
          [
            236,
            "        if (!assistant.isActive()) {"
          ],
          [
            237,
            "            throw new ConflictException(\"Помощник уже отозван\");"
          ],
          [
            240,
            "        if (request.expectedPermissions() != null) {"
          ],
          [
            241,
            "            var current ="
          ],
          [
            242,
            "                    Arrays.stream(assistant.getPermissions())"
          ],
          [
            243,
            "                            .map(value -> value.toUpperCase(Locale.ROOT))"
          ],
          [
            244,
            "                            .collect(Collectors.toSet());"
          ],
          [
            245,
            "            var expected ="
          ],
          [
            246,
            "                    request.expectedPermissions().stream()"
          ],
          [
            247,
            "                            .map(Enum::name)"
          ],
          [
            248,
            "                            .collect(Collectors.toSet());"
          ],
          [
            249,
            "            if (!current.equals(expected)) {"
          ],
          [
            250,
            "                throw new ConflictException("
          ],
          [
            251,
            "                        \"Права помощника изменились. Обнови список и подтверди изменения заново.\","
          ],
          [
            252,
            "                        Map.of(\"code\", \"ASSISTANT_PERMISSIONS_CONFLICT\"));"
          ],
          [
            253,
            "            }"
          ]
        ]
      }
    ]
  },
  {
    "id": "headman-assistants-revoke",
    "title": "Снятие последнего права",
    "description": "Пустой набор с ожидаемыми правами отзывает помощника. HTTP 200 возвращает active=false и прежний набор в сохранённой записи. Событие assistant.access.changed передаёт пустой after_permissions. Так запись хранит прежние права, а актуальный допуск больше их не использует.",
    "frames": [
      {
        "title": "Пустой набор требует снимка и отзывает запись",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantService.java",
        "language": "java",
        "lines": [
          [
            255,
            "        if (request.permissions().isEmpty()) {"
          ],
          [
            256,
            "            if (request.expectedPermissions() == null)"
          ],
          [
            257,
            "                throw new BadRequestException("
          ],
          [
            258,
            "                        \"permissions\","
          ],
          [
            259,
            "                        \"Для отзыва последнего права нужен снимок expectedPermissions\");"
          ],
          [
            260,
            "            revokeLocked(assistant, groupId);"
          ],
          [
            261,
            "            return assistant;"
          ],
          [
            262,
            "        }"
          ],
          [
            306,
            "    /** Shared revoke effects; caller holds the group and assistant locks in its transaction. */"
          ],
          [
            307,
            "    private void revokeLocked(HeadmanAssistant assistant, Long groupId) {"
          ],
          [
            308,
            "        assistant.setActive(false);"
          ],
          [
            309,
            "        assistant.setRevokedAt(OffsetDateTime.now());"
          ],
          [
            310,
            "        assistantRepository.save(assistant);"
          ],
          [
            311,
            "        if (personalNotifications != null)"
          ],
          [
            312,
            "            personalNotifications.assistant("
          ],
          [
            313,
            "                    assistant.getStudentId(),"
          ],
          [
            314,
            "                    groupId,"
          ],
          [
            315,
            "                    true,"
          ],
          [
            316,
            "                    false,"
          ],
          [
            317,
            "                    assistant.getPermissions(),"
          ],
          [
            318,
            "                    new String[0]);"
          ],
          [
            319,
            "    }"
          ]
        ]
      },
      {
        "title": "Ответ PATCH содержит сохранённую запись",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantController.java",
        "language": "java",
        "lines": [
          [
            68,
            "            Long id, UpdateAssistantPermissionsRequest request) {"
          ],
          [
            69,
            "        HeadmanAssistant assistant = assistantService.updatePermissions(id, request);"
          ],
          [
            70,
            "        return ResponseEntity.ok(assistantAssembler.toModel(assistant));"
          ],
          [
            71,
            "    }"
          ]
        ]
      },
      {
        "title": "PATCH · снять право обработки заявок",
        "language": "json",
        "example": [
          "{",
          "  \"permissions\": [],",
          "  \"expectedPermissions\": [",
          "    \"MANAGE_EXCUSES\"",
          "  ]",
          "}"
        ]
      },
      {
        "title": "Ответ читает прежние права и новый флаг",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantAssembler.java",
        "language": "java",
        "lines": [
          [
            31,
            "        List<AssistantPermission> permissions ="
          ],
          [
            32,
            "                Arrays.stream(assistant.getPermissions())"
          ],
          [
            33,
            "                        .map(s -> AssistantPermission.valueOf(s.toUpperCase()))"
          ],
          [
            34,
            "                        .toList();"
          ],
          [
            35,
            ""
          ],
          [
            36,
            "        AssistantResponse response = new AssistantResponse();"
          ],
          [
            37,
            "        response.setId(assistant.getId());"
          ],
          [
            38,
            "        response.setStudentId(assistant.getStudentId());"
          ],
          [
            42,
            "        response.setGroupId(assistant.getGroupId());"
          ],
          [
            43,
            "        response.setPermissions(permissions);"
          ],
          [
            44,
            "        response.setActive(assistant.isActive());"
          ],
          [
            45,
            "        response.setAssignedAt(assistant.getAssignedAt());"
          ],
          [
            46,
            "        response.setRevokedAt(assistant.getRevokedAt());"
          ]
        ]
      },
      {
        "title": "Отозванный помощник · фрагмент ответа",
        "language": "json",
        "example": [
          "{",
          "  \"id\": 17,",
          "  \"studentId\": 110,",
          "  \"groupId\": 41,",
          "  \"permissions\": [",
          "    \"MANAGE_EXCUSES\"",
          "  ],",
          "  \"active\": false,",
          "  \"revokedAt\": \"2026-11-13T09:00:00+03:00\"",
          "}"
        ]
      },
      {
        "title": "Событие описывает эффективный набор после отзыва",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/AcademicPersonalNotificationEvent.java",
        "language": "java",
        "lines": [
          [
            44,
            "        var oldPermissions = permissions(beforePermissions);"
          ],
          [
            45,
            "        var newPermissions = permissions(afterPermissions);"
          ],
          [
            46,
            "        if (before == after && oldPermissions.equals(newPermissions))"
          ],
          [
            47,
            "            throw new IllegalArgumentException(\"assistant fact requires a transition\");"
          ],
          [
            48,
            "        var payload = base(user, actor);"
          ],
          [
            49,
            "        payload.put(\"group_id\", group);"
          ],
          [
            50,
            "        payload.put(\"before_active\", before);"
          ],
          [
            51,
            "        payload.put(\"after_active\", after);"
          ],
          [
            52,
            "        payload.put(\"before_permissions\", oldPermissions);"
          ],
          [
            53,
            "        payload.put(\"after_permissions\", newPermissions);"
          ],
          [
            54,
            "        payload.put(\"change\", !after ? \"REVOKED\" : !before ? \"GRANTED\" : \"PERMISSIONS_CHANGED\");"
          ],
          [
            55,
            "        return new AcademicPersonalNotificationEvent(\"assistant.access.changed\", payload);"
          ]
        ]
      }
    ]
  },
  {
    "id": "headman-assistants-refresh",
    "title": "Доступ во время работы",
    "description": "Сервис проверяет сессию, группу и активную запись помощника для каждого защищённого действия. После отзыва действие может вернуть 403. PWA и Mini App перечитывают права после такого отказа или при возвращении приложения на передний план. Сам отзыв меняет доступные действия, а завершение сессии относится к отдельной проверке Auth.",
    "frames": [
      {
        "title": "Сессия и совпадение группы проверяются заново",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantPermissionReadService.java",
        "language": "java",
        "lines": [
          [
            48,
            "    public boolean allows(InternalJwtClaims actor, long groupId, String rawPermission) {"
          ],
          [
            49,
            "        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();"
          ],
          [
            50,
            "        String permission ="
          ],
          [
            51,
            "                rawPermission == null ? \"\" : rawPermission.trim().toUpperCase(Locale.ROOT);"
          ],
          [
            52,
            "        if (actor == null"
          ],
          [
            53,
            "                || actor.userId() <= 0"
          ],
          [
            54,
            "                || groupId <= 0"
          ],
          [
            55,
            "                || actor.groupId() == null"
          ],
          [
            56,
            "                || actor.groupId() != groupId"
          ],
          [
            57,
            "                || actor.readOnly()"
          ],
          [
            58,
            "                || !\"ACTIVE\".equalsIgnoreCase(actor.status())"
          ],
          [
            59,
            "                || !(\"HEADMAN\".equalsIgnoreCase(actor.role())"
          ],
          [
            60,
            "                        || \"STUDENT\".equalsIgnoreCase(actor.role()))"
          ],
          [
            61,
            "                || !PERMISSIONS.contains(permission)) return false;"
          ],
          [
            62,
            "        if (!admit(actor)) return denyUnlessPending(actor, groupId);"
          ],
          [
            63,
            "        var before = snapshot(actor, groupId, permission);"
          ],
          [
            64,
            "        if (!admit(actor)) return denyUnlessPending(actor, groupId);"
          ],
          [
            65,
            "        var after = snapshot(actor, groupId, permission);"
          ],
          [
            66,
            "        if (!java.util.Objects.equals(before, after)) throw unavailable();"
          ],
          [
            67,
            "        return before != null && before.allowed();"
          ]
        ]
      },
      {
        "title": "Допуск учитывает только действующего помощника",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/assistant/AssistantPermissionReadService.java",
        "language": "java",
        "lines": [
          [
            104,
            "  EXISTS(SELECT 1 FROM academic_role_eligibility e WHERE e.user_id=p.id"
          ],
          [
            105,
            "    AND e.role='STUDENT' AND e.status='ACTIVE' AND e.group_id=g.id) AS student,"
          ],
          [
            106,
            "  EXISTS(SELECT 1 FROM academic_role_eligibility e WHERE e.user_id=p.id"
          ],
          [
            107,
            "    AND e.role='HEADMAN' AND e.status='ACTIVE' AND e.group_id=g.id) AS headman,"
          ],
          [
            108,
            "  EXISTS(SELECT 1 FROM headman_assistants h WHERE h.student_id=p.id"
          ],
          [
            109,
            "    AND h.group_id=g.id AND h.is_active"
          ],
          [
            110,
            "    AND EXISTS(SELECT 1 FROM unnest(h.permissions) permission"
          ],
          [
            111,
            "      WHERE lower(trim(permission))=?)) AS helper"
          ],
          [
            120,
            "                                        boolean student = rs.getBoolean(\"student\");"
          ],
          [
            121,
            "                                        boolean headman = rs.getBoolean(\"headman\");"
          ],
          [
            122,
            "                                        boolean helper = rs.getBoolean(\"helper\");"
          ],
          [
            123,
            "                                        boolean allowed ="
          ],
          [
            124,
            "                                                rs.getBoolean(\"visible\")"
          ],
          [
            125,
            "                                                        && student"
          ],
          [
            126,
            "                                                        && (\"HEADMAN\".equalsIgnoreCase(actor.role())"
          ],
          [
            127,
            "                                                                ? headman"
          ],
          [
            128,
            "                                                                : helper);"
          ]
        ]
      },
      {
        "title": "PWA перечитывает разрешения после 403 и при возврате",
        "path": "frontends/pwa-vue/src/App.vue",
        "language": "typescript",
        "lines": [
          [
            2147,
            "  if (isAssistantCapabilityForbiddenError(error)) {"
          ],
          [
            2148,
            "    const generation = auth.currentGeneration();"
          ],
          [
            2149,
            "    void assistantPermissionRefresher.refresh('forbidden').catch((cause: unknown) => {"
          ],
          [
            2150,
            "      if (auth.isCurrent(generation) && isConfirmedOnlineAuthDenial(cause))"
          ],
          [
            2151,
            "        void handleOnlineAuthDenial();"
          ],
          [
            2152,
            "    });"
          ],
          [
            2153,
            "  }"
          ],
          [
            2181,
            "function onAssistantForeground(): void {"
          ],
          [
            2182,
            "  if (typeof document === 'undefined' || document.visibilityState !== 'visible') return;"
          ],
          [
            2183,
            "  const generation = auth.currentGeneration();"
          ],
          [
            2184,
            "  void assistantPermissionRefresher.refresh('foreground').catch((cause: unknown) => {"
          ],
          [
            2185,
            "    if (auth.isCurrent(generation) && isConfirmedOnlineAuthDenial(cause))"
          ],
          [
            2186,
            "      void handleOnlineAuthDenial();"
          ],
          [
            2187,
            "  });"
          ],
          [
            2188,
            "}"
          ]
        ]
      },
      {
        "title": "Обновлённый набор применяется в текущем контексте",
        "path": "frontends/pwa-vue/src/App.vue",
        "language": "typescript",
        "lines": [
          [
            669,
            "    const groupId = current.group?.id ? Number(current.group.id) : null;"
          ],
          [
            670,
            "    if (groupId === null || !Number.isSafeInteger(groupId) || groupId <= 0) return [];"
          ],
          [
            671,
            "    const permissions = await auth.createHeadmanGroupApi(currentFetcher()).listMyPermissions();"
          ],
          [
            672,
            "    if (!auth.isCurrent(generation) || currentAssistantOwnerKey() !== expectedOwnerKey) {"
          ],
          [
            673,
            "      throw new StaleSessionGenerationError();"
          ],
          [
            674,
            "    }"
          ],
          [
            675,
            "    return permissions;"
          ],
          [
            676,
            "  },"
          ],
          [
            677,
            "  invalidate: () => {"
          ],
          [
            678,
            "    assistantPermissionsVerified.value = false;"
          ],
          [
            679,
            "  },"
          ],
          [
            680,
            "  apply: (permissions) => {"
          ],
          [
            681,
            "    assistantPermissionOptions.value = permissions;"
          ],
          [
            682,
            "    assistantPermissionsVerified.value = true;"
          ],
          [
            683,
            "  },"
          ]
        ]
      },
      {
        "title": "Mini App также обновляет разрешения",
        "path": "frontends/tma-vue/src/App.vue",
        "language": "typescript",
        "lines": [
          [
            1435,
            "  } else if (isAssistantCapabilityForbiddenError(cause)) {"
          ],
          [
            1436,
            "    void assistantPermissionRefresher.refresh('forbidden').catch((refreshError: unknown) => {"
          ],
          [
            1437,
            "      if (isAuthDenied(refreshError)) {"
          ],
          [
            1438,
            "        invalidateOwnerSynchronously();"
          ],
          [
            1439,
            "        offline.value = false;"
          ],
          [
            1440,
            "      }"
          ],
          [
            1467,
            ""
          ],
          [
            1468,
            "function onAssistantForeground(): void {"
          ],
          [
            1469,
            "  if (typeof document === 'undefined' || document.visibilityState !== 'visible') return;"
          ],
          [
            1470,
            "  void assistantPermissionRefresher.refresh('foreground').catch((cause: unknown) => {"
          ],
          [
            1471,
            "    if (isAuthDenied(cause)) {"
          ],
          [
            1472,
            "      invalidateOwnerSynchronously();"
          ],
          [
            1473,
            "      offline.value = false;"
          ],
          [
            1474,
            "    }"
          ],
          [
            1475,
            "  });"
          ]
        ]
      }
    ]
  }
]
