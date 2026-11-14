import type { StoryOperation } from './story-source'

export const notificationLifecycleOperations: readonly StoryOperation[] = [
  {
    "id": "notification-lifecycle-audience",
    "title": "Найти получателей по событию",
    "description": "Для lesson.started Academic проверяет состав группы на московскую дату события и действующего старосту. Участник получает представление STUDENT, староста также может получить HEADMAN. У excuse.requested аудитория включает автора заявки, действующего старосту группы и её помощников с MANAGE_EXCUSES. Лента автора сохраняется, внешний Web Push по этой подаче ему не ставится в очередь.",
    "frames": [
      {
        "title": "Начало пары содержит группу, предмет и занятие",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonStartedEvent.java",
        "language": "java",
        "lines": [
          [
            18,
            "    public record Payload("
          ],
          [
            19,
            "            @JsonProperty(\"lesson_id\") Long lessonId,"
          ],
          [
            20,
            "            @JsonProperty(\"group_id\") Long groupId,"
          ],
          [
            21,
            "            @JsonProperty(\"subject_id\") Long subjectId,"
          ],
          [
            22,
            "            @JsonProperty(\"lesson_number\") Short lessonNumber,"
          ],
          [
            23,
            "            @JsonProperty(\"start_time\") String startTime,"
          ],
          [
            24,
            "            @JsonProperty(\"end_time\") String endTime,"
          ],
          [
            25,
            "            @JsonProperty(\"room\") String room) {}"
          ],
          [
            36,
            "        super("
          ],
          [
            37,
            "                source,"
          ],
          [
            38,
            "                \"lesson.started\","
          ],
          [
            39,
            "                new Payload("
          ],
          [
            40,
            "                        lessonId,"
          ],
          [
            41,
            "                        groupId,"
          ],
          [
            42,
            "                        subjectId,"
          ],
          [
            43,
            "                        lessonNumber,"
          ],
          [
            44,
            "                        startTime.toString(),"
          ],
          [
            45,
            "                        endTime.toString(),"
          ],
          [
            46,
            "                        room));"
          ]
        ]
      },
      {
        "title": "Состав на дату события и текущий староста",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceResolver.java",
        "language": "java",
        "lines": [
          [
            113,
            "        LocalDate occurredDay;"
          ],
          [
            114,
            "        try {"
          ],
          [
            115,
            "            if (!UUID.fromString(request.getRequestId()).toString().equals(request.getRequestId()))"
          ],
          [
            116,
            "                throw new IllegalArgumentException();"
          ],
          [
            117,
            "            occurredDay ="
          ],
          [
            118,
            "                    OffsetDateTime.parse(request.getOccurredAt())"
          ],
          [
            119,
            "                            .atZoneSameInstant(MOSCOW)"
          ],
          [
            120,
            "                            .toLocalDate();"
          ],
          [
            153,
            "        Map<Long, SortedSet<String>> recipients = new TreeMap<>();"
          ],
          [
            154,
            "        long affected = request.hasAffectedUserId() ? request.getAffectedUserId() : -1;"
          ],
          [
            155,
            "        if (policy.students()) {"
          ],
          [
            156,
            "            for (long user : membership.readMemberUserIds(request.getGroupId(), occurredDay))"
          ],
          [
            157,
            "                addIfActive(recipients, user, \"STUDENT\");"
          ],
          [
            158,
            "        }"
          ],
          [
            159,
            "        if (policy.headman() && activeGroup) {"
          ],
          [
            160,
            "            var current = users.findActiveHeadmanIdsByGrantGroupId(request.getGroupId());"
          ],
          [
            161,
            "            if (current.size() > 1)"
          ],
          [
            162,
            "                throw Status.FAILED_PRECONDITION"
          ],
          [
            163,
            "                        .withDescription(\"headman authority inconsistent\")"
          ],
          [
            164,
            "                        .asRuntimeException();"
          ],
          [
            165,
            "            for (long user : current)"
          ],
          [
            166,
            "                if (user != affected"
          ],
          [
            167,
            "                        && Objects.equals("
          ],
          [
            168,
            "                                request.getGroupId(),"
          ],
          [
            169,
            "                                users.findActiveStudentGrantGroupId(user).orElse(null)))"
          ],
          [
            170,
            "                    addIfActive(recipients, user, \"HEADMAN\");"
          ]
        ]
      },
      {
        "title": "Политика начала пары и новой заявки",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceResolver.java",
        "language": "java",
        "lines": [
          [
            307,
            "    private static Policy policy(String type) {"
          ],
          [
            308,
            "        return switch (type) {"
          ],
          [
            309,
            "            case \"lesson.started\", \"lesson.reminder\", \"lesson.blocked\" ->"
          ],
          [
            310,
            "                    new Policy(true, true, true, null, false, false, false, null, false);"
          ],
          [
            323,
            "            case \"excuse.requested\", \"late_checkin.requested\" ->"
          ],
          [
            324,
            "                    new Policy("
          ],
          [
            325,
            "                            true, false, true, \"MANAGE_EXCUSES\", false, true, false, null, false);"
          ],
          [
            368,
            "    private record Policy("
          ],
          [
            369,
            "            boolean group,"
          ],
          [
            370,
            "            boolean students,"
          ],
          [
            371,
            "            boolean headman,"
          ],
          [
            372,
            "            String helperPermission,"
          ],
          [
            373,
            "            boolean teacher,"
          ],
          [
            374,
            "            boolean personal,"
          ],
          [
            375,
            "            boolean personalAdmin,"
          ],
          [
            376,
            "            String adminPermission,"
          ],
          [
            377,
            "            boolean multipleGroups) {}"
          ]
        ]
      },
      {
        "title": "Помощник проверяется по праву, автор получает личную запись",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceResolver.java",
        "language": "java",
        "lines": [
          [
            172,
            "        if (policy.helperPermission() != null && activeGroup) {"
          ],
          [
            173,
            "            for (var assistant : assistants.findByGroupIdAndIsActiveTrue(request.getGroupId())) {"
          ],
          [
            174,
            "                long user = assistant.getStudentId();"
          ],
          [
            175,
            "                if (user != affected"
          ],
          [
            176,
            "                        && helperAuthority.allowsRecipient("
          ],
          [
            177,
            "                                user, request.getGroupId(), policy.helperPermission()))"
          ],
          [
            178,
            "                    addIfActive(recipients, user, \"STUDENT\");"
          ],
          [
            179,
            "            }"
          ],
          [
            180,
            "        }"
          ],
          [
            181,
            "        if (policy.personal()) {"
          ],
          [
            182,
            "            if (policy.personalAdmin()) {"
          ],
          [
            183,
            "                addIfActive(recipients, affected, \"ADMIN\");"
          ],
          [
            184,
            "            } else addIfActive(recipients, affected, \"STUDENT\");"
          ],
          [
            185,
            "        }"
          ]
        ]
      },
      {
        "title": "Внешнее оповещение о подаче пропускает автора заявки",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java",
        "language": "java",
        "lines": [
          [
            115,
            "                    Map<Long, Set<String>> recipients = audience.snapshot(envelope);"
          ],
          [
            116,
            "                    history.persistResolved(envelope, recipients);"
          ],
          [
            117,
            "                    if (!webPushDeliveryService.shouldPush(eventType)) return;"
          ],
          [
            128,
            "                    recipients.forEach("
          ],
          [
            129,
            "                            (id, roles) -> {"
          ],
          [
            130,
            "                                // Submission history is private; only privileged recipients get an"
          ],
          [
            131,
            "                                // external request task."
          ],
          [
            132,
            "                                if ((eventType.equals(\"excuse.requested\")"
          ],
          [
            133,
            "                                                || eventType.equals(\"late_checkin.requested\"))"
          ],
          [
            134,
            "                                        && id.equals(owner)) return;"
          ],
          [
            135,
            "                                if (id.equals(actor)"
          ],
          [
            136,
            "                                        && !Set.of("
          ],
          [
            137,
            "                                                        \"semester.operation.completed\","
          ],
          [
            138,
            "                                                        \"lesson.transferred\","
          ],
          [
            139,
            "                                                        \"lesson.updated\")"
          ],
          [
            140,
            "                                                .contains(eventType)) return;"
          ],
          [
            141,
            "                                if (preferences.isEnabledForUser(id, eventType, payload))"
          ],
          [
            142,
            "                                    admitted.put(id, roles);"
          ],
          [
            143,
            "                            });"
          ],
          [
            144,
            "                    intents.enqueue(envelope, admitted);"
          ]
        ]
      }
    ]
  },
  {
    "id": "notification-lifecycle-permissions",
    "title": "Проверить преподавателя и администратора",
    "description": "При отмене пары преподаватель может получить уведомление, если контекст занятия подтверждает его назначение по группе, предмету, семестру, типу и дате. Для semester.activated Academic проверяет у администратора действующий grant ADMIN и право SEMESTERS_MANAGE в Auth. Набор ролей зависит от события.",
    "frames": [
      {
        "title": "Политики отмены пары и включения семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceResolver.java",
        "language": "java",
        "lines": [
          [
            311,
            "            case \"lesson.cancelled\","
          ],
          [
            312,
            "                    \"lesson.one_off.cancelled\","
          ],
          [
            313,
            "                    \"lesson.one_off.created\","
          ],
          [
            314,
            "                    \"homework.published\","
          ],
          [
            315,
            "                    \"homework.updated\","
          ],
          [
            316,
            "                    \"homework.withdrawn\" ->"
          ],
          [
            317,
            "                    new Policy(true, true, true, null, true, false, false, null, false);"
          ],
          [
            340,
            "            case \"semester.activated\" ->"
          ],
          [
            341,
            "                    new Policy("
          ],
          [
            342,
            "                            false,"
          ],
          [
            343,
            "                            false,"
          ],
          [
            344,
            "                            false,"
          ],
          [
            345,
            "                            null,"
          ],
          [
            346,
            "                            false,"
          ],
          [
            347,
            "                            false,"
          ],
          [
            348,
            "                            false,"
          ],
          [
            349,
            "                            \"SEMESTERS_MANAGE\","
          ],
          [
            350,
            "                            false);"
          ]
        ]
      },
      {
        "title": "Преподаватель определяется по контексту занятия",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceResolver.java",
        "language": "java",
        "lines": [
          [
            196,
            "                if (scope.getGroupId() <= 0"
          ],
          [
            197,
            "                        || scope.getSubjectId() <= 0"
          ],
          [
            198,
            "                        || scope.getSemesterId() <= 0"
          ],
          [
            199,
            "                        || scope.hasAssignmentId() && scope.getAssignmentId() <= 0)"
          ],
          [
            200,
            "                    throw invalid(\"exact lesson IDs required\");"
          ],
          [
            201,
            "                if (!policy.multipleGroups() && scope.getGroupId() != request.getGroupId())"
          ],
          [
            202,
            "                    throw invalid(\"lesson group differs from event\");"
          ],
          [
            203,
            "                groups.findById(scope.getGroupId())"
          ],
          [
            204,
            "                        .orElseThrow(() -> invalid(\"unknown lesson group\"));"
          ],
          [
            205,
            "                var subject ="
          ],
          [
            206,
            "                        subjects.findById(scope.getSubjectId())"
          ],
          [
            207,
            "                                .orElseThrow(() -> invalid(\"unknown lesson subject\"));"
          ],
          [
            208,
            "                if (!Objects.equals(subject.getGroupId(), scope.getGroupId()))"
          ],
          [
            209,
            "                    throw invalid(\"lesson subject belongs to another group\");"
          ],
          [
            210,
            "                for (long teacher :"
          ],
          [
            211,
            "                        teacherAuthority.findTeacherIds("
          ],
          [
            212,
            "                                scope.getGroupId(),"
          ],
          [
            213,
            "                                scope.getSubjectId(),"
          ],
          [
            214,
            "                                scope.getSemesterId(),"
          ],
          [
            215,
            "                                type,"
          ],
          [
            216,
            "                                day,"
          ],
          [
            217,
            "                                scope.hasAssignmentId() ? scope.getAssignmentId() : null))"
          ],
          [
            218,
            "                    addIfActive(recipients, teacher, \"TEACHER\");"
          ]
        ]
      },
      {
        "title": "Auth проверяет активные grants и право администратора",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceAuthority.java",
        "language": "java",
        "lines": [
          [
            77,
            "            var roles = new TreeSet<String>();"
          ],
          [
            78,
            "            for (String role : local.candidates().get(user)) {"
          ],
          [
            79,
            "                Long authGroup = activeGrants.get(role);"
          ],
          [
            80,
            "                if (!active || authGroup == null) continue;"
          ],
          [
            81,
            "                if (!role.equals(\"ADMIN\")"
          ],
          [
            82,
            "                        && !Objects.equals(authGroup, profile.roleGroups().get(role)))"
          ],
          [
            83,
            "                    throw unavailable();"
          ],
          [
            84,
            "                if (role.equals(\"ADMIN\")"
          ],
          [
            85,
            "                        && local.adminPermission() != null"
          ],
          [
            86,
            "                        && !AdminAccessService.profile(user, identity.getAdminProfile())"
          ],
          [
            87,
            "                                .allows(local.adminPermission())) continue;"
          ],
          [
            88,
            "                roles.add(role);"
          ],
          [
            89,
            "            }"
          ],
          [
            90,
            "            if (!roles.isEmpty())"
          ],
          [
            91,
            "                result.addRecipients("
          ],
          [
            92,
            "                        NotificationAudienceRecipient.newBuilder()"
          ],
          [
            93,
            "                                .setUserId(user)"
          ],
          [
            94,
            "                                .addAllRoles(roles));"
          ]
        ]
      }
    ]
  },
  {
    "id": "notification-lifecycle-history",
    "title": "Записать событие в ленту",
    "description": "Notification фиксирует получателей и роли для события. Для каждого пользователя хранится одна запись на eventId, внутри неё отдельные представления ролей. Повторная доставка сохраняет уже записанное состояние прочтения. Запись в ленте выполняется до проверки Web Push. Отключённая доставка, отсутствие подписки или Telegram-привязки не убирают запись.",
    "frames": [
      {
        "title": "Снимок получателей сохраняется при первом чтении события",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationAudienceService.java",
        "language": "java",
        "lines": [
          [
            40,
            "        Document stored = mongo.findById(request.getRequestId(), Document.class, COLLECTION);"
          ],
          [
            41,
            "        if (stored == null) {"
          ],
          [
            42,
            "            Map<Long, Set<String>> admitted = filter(envelope, client.resolve(envelope));"
          ],
          [
            43,
            "            Document roles = new Document();"
          ],
          [
            44,
            "            admitted.forEach((id, values) -> roles.put(Long.toString(id), new ArrayList<>(values)));"
          ],
          [
            45,
            "            stored ="
          ],
          [
            46,
            "                    mongo.findAndModify("
          ],
          [
            47,
            "                            Query.query(Criteria.where(\"_id\").is(request.getRequestId())),"
          ],
          [
            48,
            "                            new Update()"
          ],
          [
            49,
            "                                    .setOnInsert(\"event_type\", request.getEventType())"
          ],
          [
            50,
            "                                    .setOnInsert(\"context_hash\", contextHash)"
          ],
          [
            51,
            "                                    .setOnInsert(\"roles\", roles)"
          ],
          [
            52,
            "                                    .setOnInsert(\"created_at\", Date.from(Instant.now())),"
          ],
          [
            53,
            "                            FindAndModifyOptions.options().upsert(true).returnNew(true),"
          ],
          [
            54,
            "                            Document.class,"
          ],
          [
            55,
            "                            COLLECTION);"
          ],
          [
            56,
            "        }"
          ],
          [
            57,
            "        if (stored == null"
          ],
          [
            58,
            "                || !request.getEventType().equals(stored.getString(\"event_type\"))"
          ],
          [
            59,
            "                || !contextHash.equals(stored.getString(\"context_hash\")))"
          ],
          [
            60,
            "            throw new IllegalStateException(\"Audience snapshot scope is invalid\");"
          ]
        ]
      },
      {
        "title": "История сохраняется с контекстом получателя и его ролями",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationHistoryEventProcessor.java",
        "language": "java",
        "lines": [
          [
            121,
            "    public void persistResolved(Map<String, Object> envelope, Map<Long, Set<String>> recipients) {"
          ],
          [
            122,
            "        if (roleWriter == null)"
          ],
          [
            123,
            "            throw new IllegalStateException(\"Role history writer is unavailable\");"
          ],
          [
            124,
            "        String eventType = requireString(envelope.get(\"event_type\"), \"event_type\");"
          ],
          [
            125,
            "        Map<String, Object> payload = NotificationAudienceClient.payload(envelope);"
          ],
          [
            126,
            "        NotificationType type = mapType(eventType, payload).orElseThrow();"
          ],
          [
            127,
            "        String eventId = UUID.fromString(envelope.get(\"event_id\").toString()).toString();"
          ],
          [
            128,
            "        String traceId = envelope.get(\"trace_id\") instanceof String value ? value : null;"
          ],
          [
            129,
            "        Instant sentAt = Instant.now();"
          ],
          [
            130,
            "        recipients.forEach("
          ],
          [
            131,
            "                (userId, roles) ->"
          ],
          [
            132,
            "                        roleWriter.persist("
          ],
          [
            133,
            "                                eventId,"
          ],
          [
            134,
            "                                userId,"
          ],
          [
            135,
            "                                type,"
          ],
          [
            136,
            "                                NotificationPayloadSnapshot.forRecipient("
          ],
          [
            137,
            "                                        eventType, payload, userId),"
          ],
          [
            138,
            "                                traceId,"
          ],
          [
            139,
            "                                roles,"
          ],
          [
            140,
            "                                sentAt));"
          ],
          [
            141,
            "        invalidateUnreadAfterCommit(recipients.keySet());"
          ]
        ]
      },
      {
        "title": "Одна запись на событие и пользователя, отдельные roleViews",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationRoleHistoryWriter.java",
        "language": "java",
        "lines": [
          [
            36,
            "        Map<String, NotificationHistoryDocument.RoleView> views = new LinkedHashMap<>();"
          ],
          [
            37,
            "        roles.forEach(role -> views.put(role, view(sentAt)));"
          ],
          [
            38,
            "        Query identity ="
          ],
          [
            39,
            "                Query.query(Criteria.where(\"event_id\").is(eventId).and(\"user_id\").is(userId));"
          ],
          [
            40,
            "        mongo.upsert("
          ],
          [
            41,
            "                identity,"
          ],
          [
            42,
            "                new Update()"
          ],
          [
            43,
            "                        .setOnInsert(\"eventId\", eventId)"
          ],
          [
            44,
            "                        .setOnInsert(\"userId\", userId)"
          ],
          [
            45,
            "                        .setOnInsert(\"type\", type)"
          ],
          [
            46,
            "                        .setOnInsert(\"payload\", payload)"
          ],
          [
            47,
            "                        .setOnInsert(\"sentAt\", sentAt)"
          ],
          [
            48,
            "                        .setOnInsert(\"traceId\", traceId)"
          ],
          [
            49,
            "                        .setOnInsert(\"roleViews\", views),"
          ],
          [
            50,
            "                NotificationHistoryDocument.class);"
          ],
          [
            55,
            "        for (String role : roles) {"
          ],
          [
            56,
            "            Query missing ="
          ],
          [
            57,
            "                    Query.query("
          ],
          [
            58,
            "                            Criteria.where(\"event_id\")"
          ],
          [
            59,
            "                                    .is(eventId)"
          ],
          [
            60,
            "                                    .and(\"user_id\")"
          ],
          [
            61,
            "                                    .is(userId)"
          ],
          [
            62,
            "                                    .and(\"role_views.\" + role)"
          ],
          [
            63,
            "                                    .exists(false));"
          ],
          [
            64,
            "            mongo.updateFirst("
          ],
          [
            65,
            "                    missing,"
          ],
          [
            66,
            "                    new Update().set(\"role_views.\" + role, view(stored.getSentAt())),"
          ],
          [
            67,
            "                    NotificationHistoryDocument.class);"
          ],
          [
            68,
            "        }"
          ],
          [
            69,
            "    }"
          ],
          [
            70,
            ""
          ],
          [
            71,
            "    private static NotificationHistoryDocument.RoleView view(Instant sentAt) {"
          ],
          [
            72,
            "        return NotificationHistoryDocument.RoleView.builder().orderAt(sentAt).revision(0).build();"
          ]
        ]
      },
      {
        "title": "Проверка Web Push начинается после записи ленты",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java",
        "language": "java",
        "lines": [
          [
            115,
            "                    Map<Long, Set<String>> recipients = audience.snapshot(envelope);"
          ],
          [
            116,
            "                    history.persistResolved(envelope, recipients);"
          ],
          [
            117,
            "                    if (!webPushDeliveryService.shouldPush(eventType)) return;"
          ]
        ]
      }
    ]
  },
  {
    "id": "notification-lifecycle-push",
    "title": "Доставить Web Push в PWA",
    "description": "Браузер должен поддерживать Web Push, а пользователь разрешить уведомления и оформить подписку. Notification отбирает события для Web Push, затем диспетчер находит подписки получателя и заново проверяет настройки и текущую аудиторию. Текущие права могут сузить список получателей.",
    "frames": [
      {
        "title": "PWA проверяет поддержку, разрешение и подписку",
        "path": "frontends/pwa-vue/src/features/push/browser-push.ts",
        "language": "typescript",
        "lines": [
          [
            5,
            "  const supported = (): boolean =>"
          ],
          [
            6,
            "    enabled &&"
          ],
          [
            7,
            "    window.isSecureContext &&"
          ],
          [
            8,
            "    'Notification' in window &&"
          ],
          [
            9,
            "    'PushManager' in window &&"
          ],
          [
            10,
            "    'serviceWorker' in navigator &&"
          ],
          [
            11,
            "    'locks' in navigator"
          ],
          [
            12,
            "  async function registration(): Promise<ServiceWorkerRegistration> {"
          ],
          [
            13,
            "    const result = await navigator.serviceWorker.getRegistration(new URL(base, location.href).href)"
          ],
          [
            14,
            "    if (!result?.active)"
          ],
          [
            15,
            "      throw new Error('Приложение ещё не готово к Web Push. Обнови страницу и повтори.')"
          ],
          [
            16,
            "    return result"
          ],
          [
            41,
            "  return {"
          ],
          [
            42,
            "    supported,"
          ],
          [
            43,
            "    permission: () => Notification.permission,"
          ],
          [
            44,
            "    requestPermission: () => Notification.requestPermission(),"
          ],
          [
            45,
            "    installationRequired: () =>"
          ],
          [
            46,
            "      /iphone|ipad|ipod/i.test(navigator.userAgent) &&"
          ],
          [
            47,
            "      !window.matchMedia('(display-mode: standalone)').matches &&"
          ],
          [
            48,
            "      !(navigator as Navigator & { standalone?: boolean }).standalone,"
          ],
          [
            49,
            "    subscription: async () => (await registration()).pushManager.getSubscription(),"
          ],
          [
            50,
            "    createSubscription: async (key) =>"
          ],
          [
            51,
            "      (await registration()).pushManager.subscribe({"
          ],
          [
            52,
            "        userVisibleOnly: true,"
          ],
          [
            53,
            "        applicationServerKey: key,"
          ],
          [
            54,
            "      }),"
          ]
        ]
      },
      {
        "title": "События, допущенные к Web Push",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java",
        "language": "java",
        "lines": [
          [
            52,
            "    private static final Set<String> PUSH_EVENT_TYPES ="
          ],
          [
            53,
            "            Set.of("
          ],
          [
            54,
            "                    \"lesson.started\","
          ],
          [
            55,
            "                    \"lesson.reminder\","
          ],
          [
            56,
            "                    \"lesson.blocked\","
          ],
          [
            57,
            "                    \"lesson.cancelled\","
          ],
          [
            58,
            "                    \"lesson.one_off.created\","
          ],
          [
            59,
            "                    \"lesson.one_off.cancelled\","
          ],
          [
            60,
            "                    \"homework.published\","
          ],
          [
            61,
            "                    \"homework.updated\","
          ],
          [
            62,
            "                    \"group.renamed\","
          ],
          [
            63,
            "                    \"group.archived\","
          ],
          [
            64,
            "                    // Студент-таргетированные: доходят только подписчику user_id из payload."
          ],
          [
            65,
            "                    \"excuse.decided\","
          ],
          [
            66,
            "                    \"late_checkin.decided\","
          ],
          [
            67,
            "                    \"attendance.marked\","
          ],
          [
            68,
            "                    \"homework.weekly_digest\","
          ],
          [
            69,
            "                    \"homework.due_reminder\");"
          ],
          [
            157,
            "    public boolean shouldPush(String eventType) {"
          ],
          [
            158,
            "        return PUSH_EVENT_TYPES.contains(eventType)"
          ],
          [
            159,
            "                || HEADMAN_ONLY_EVENT_TYPES.contains(eventType)"
          ],
          [
            160,
            "                || Set.of("
          ],
          [
            161,
            "                                \"lesson.transferred\","
          ],
          [
            162,
            "                                \"lesson.restored\","
          ],
          [
            163,
            "                                \"lesson.updated\","
          ],
          [
            164,
            "                                \"homework.withdrawn\","
          ],
          [
            165,
            "                                \"group.membership.changed\","
          ],
          [
            166,
            "                                \"headman.access.changed\","
          ],
          [
            167,
            "                                \"assistant.access.changed\","
          ],
          [
            168,
            "                                \"admin.access.changed\","
          ],
          [
            169,
            "                                \"semester.activated\","
          ],
          [
            170,
            "                                \"semester.operation.completed\","
          ],
          [
            171,
            "                                \"group.promotion.completed\")"
          ],
          [
            172,
            "                        .contains(eventType);"
          ],
          [
            173,
            "    }"
          ]
        ]
      },
      {
        "title": "Подписка, настройки и текущая аудитория проверяются при доставке",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/NotificationPushIntentDispatcher.java",
        "language": "java",
        "lines": [
          [
            69,
            "            var candidates = subscriptions.findAllByUserIdIn(original.keySet());"
          ],
          [
            70,
            "            if (candidates.isEmpty())"
          ],
          [
            71,
            "                metrics.dispatch(WebPushOutcomeMetrics.DispatchOutcome.SKIPPED_NO_SUBSCRIPTIONS);"
          ],
          [
            72,
            "            for (PushSubscriptionDocument sub : candidates) {"
          ],
          [
            73,
            "                if (sub.getUserId() == null"
          ],
          [
            74,
            "                        || sub.getId() == null"
          ],
          [
            75,
            "                        || !original.containsKey(sub.getUserId()))"
          ],
          [
            76,
            "                    throw new IllegalStateException(\"Delivery subscription recipient is invalid\");"
          ],
          [
            77,
            "                String key = sub.getUserId() + \":\" + sub.getId();"
          ],
          [
            78,
            "                if (processed.contains(key)) continue;"
          ],
          [
            79,
            "                if (!preferences.isEnabledForUser(sub.getUserId(), eventType, payload)) {"
          ],
          [
            80,
            "                    metrics.dispatch(WebPushOutcomeMetrics.DispatchOutcome.SKIPPED_PREFERENCES);"
          ],
          [
            81,
            "                    continue;"
          ],
          [
            82,
            "                }"
          ],
          [
            83,
            "                Map<Long, Set<String>> current = audience.current(envelope, original);"
          ],
          [
            84,
            "                if (!current.containsKey(sub.getUserId())) {"
          ],
          [
            85,
            "                    metrics.dispatch(WebPushOutcomeMetrics.DispatchOutcome.SKIPPED_AUDIENCE);"
          ],
          [
            86,
            "                    continue;"
          ],
          [
            113,
            "                if (!store.startProvider(job, key)) return;"
          ],
          [
            114,
            "                providerAttempt = true;"
          ],
          [
            115,
            "                int status ="
          ],
          [
            116,
            "                        delivery.sendToSubscription("
          ],
          [
            117,
            "                                sub, eventType, catalog.present(document, role));"
          ]
        ]
      },
      {
        "title": "Текущий доступ пересекается со снимком получателей",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationAudienceService.java",
        "language": "java",
        "lines": [
          [
            78,
            "    public Map<Long, Set<String>> current("
          ],
          [
            79,
            "            Map<String, Object> envelope, Map<Long, Set<String>> original) {"
          ],
          [
            80,
            "        return intersect(original, filter(envelope, client.resolve(envelope)));"
          ],
          [
            81,
            "    }"
          ],
          [
            82,
            ""
          ],
          [
            83,
            "    static Map<Long, Set<String>> intersect("
          ],
          [
            84,
            "            Map<Long, Set<String>> original, Map<Long, Set<String>> live) {"
          ],
          [
            85,
            "        Map<Long, Set<String>> result = new LinkedHashMap<>();"
          ],
          [
            86,
            "        original.forEach("
          ],
          [
            87,
            "                (id, roles) -> {"
          ],
          [
            88,
            "                    Set<String> retained = new LinkedHashSet<>(roles);"
          ],
          [
            89,
            "                    retained.retainAll(live.getOrDefault(id, Set.of()));"
          ],
          [
            90,
            "                    if (!retained.isEmpty()) result.put(id, Set.copyOf(retained));"
          ],
          [
            91,
            "                });"
          ],
          [
            92,
            "        return Collections.unmodifiableMap(result);"
          ]
        ]
      }
    ]
  },
  {
    "id": "notification-lifecycle-settings",
    "title": "Настроить типы и каналы",
    "description": "В настройках доставки пользователь может связать переключатели PWA и Telegram или разделить их. Настройки сохраняются на пользователя, по типу уведомления. Для Web Push сервис проверяет appPush и общую паузу. До перехода на V2 он использует прежние категории. Если настройки менялись в другом клиенте, несовпадение ревизии возвращает конфликт.",
    "frames": [
      {
        "title": "Экран учитывает поддержку Web Push и привязку Telegram",
        "path": "frontends/mobile-core/src/features/notifications/NotificationSettings.vue",
        "language": "typescript",
        "lines": [
          [
            62,
            "const unavailable = computed(() =>"
          ],
          [
            63,
            "  value.value.splitChannels"
          ],
          [
            64,
            "    ? { appPush: props.appPushAvailable === false, telegram: !props.preferences.telegramLinked }"
          ],
          [
            65,
            "    : {"
          ],
          [
            66,
            "        appPush: props.appPushAvailable === false && !props.preferences.telegramLinked,"
          ],
          [
            67,
            "        telegram: false,"
          ],
          [
            68,
            "      },"
          ],
          [
            69,
            ");"
          ],
          [
            70,
            "const groups = computed(() =>"
          ],
          [
            71,
            "  compatible.value"
          ],
          [
            72,
            "    ? notificationCatalogGroupsForRole(props.catalog, props.signedRole).map((group) => ({"
          ],
          [
            73,
            "        ...group,"
          ],
          [
            74,
            "        types: group.types.map((type) => ({ ...type, preferences: value.value.types[type.id]! })),"
          ],
          [
            75,
            "      }))"
          ],
          [
            76,
            "    : [],"
          ],
          [
            116,
            "function setDelivery("
          ],
          [
            117,
            "  id: NotificationTypeId,"
          ],
          [
            118,
            "  channel: NotificationDeliveryChannel,"
          ],
          [
            119,
            "  enabled: boolean,"
          ],
          [
            120,
            "): void {"
          ],
          [
            121,
            "  if (editingBlocked.value || unavailable.value[channel] || choiceOpen.value) return;"
          ],
          [
            122,
            "  emit('edit', (latest) => changeNotificationDelivery(latest, id, channel, enabled));"
          ],
          [
            123,
            "}"
          ]
        ]
      },
      {
        "title": "Связанный режим меняет оба канала для выбранного типа",
        "path": "frontends/mobile-core/src/features/notifications/notification-preferences-draft.ts",
        "language": "typescript",
        "lines": [
          [
            53,
            "export function changeNotificationDelivery("
          ],
          [
            54,
            "  value: NotificationPreferencesV2,"
          ],
          [
            55,
            "  id: NotificationTypeId,"
          ],
          [
            56,
            "  channel: NotificationDeliveryChannel,"
          ],
          [
            57,
            "  enabled: boolean,"
          ],
          [
            58,
            "): NotificationPreferencesV2 {"
          ],
          [
            59,
            "  const next = copyNotificationPreferences(value)"
          ],
          [
            60,
            "  const entry = next.types[id]"
          ],
          [
            61,
            "  if (!entry) throw new Error('Тип уведомления отсутствует в подтверждённом каталоге')"
          ],
          [
            62,
            "  entry[channel] = enabled"
          ],
          [
            63,
            "  if (!next.splitChannels) {"
          ],
          [
            64,
            "    entry.appPush = enabled"
          ],
          [
            65,
            "    entry.telegram = enabled"
          ],
          [
            66,
            "  }"
          ],
          [
            67,
            "  return next"
          ],
          [
            68,
            "}"
          ]
        ]
      },
      {
        "title": "Клиент отправляет текущую ревизию и матрицу типов",
        "path": "frontends/mobile-core/src/features/notifications/notifications-v2-client.ts",
        "language": "typescript",
        "lines": [
          [
            157,
            "    async updatePreferences(value: NotificationPreferencesV2Update) {"
          ],
          [
            158,
            "      const types = parseTypes(value.types)"
          ],
          [
            159,
            "      if ("
          ],
          [
            160,
            "        typeof value.splitChannels !== 'boolean' ||"
          ],
          [
            161,
            "        (!value.splitChannels &&"
          ],
          [
            162,
            "          Object.values(types).some((entry) => entry.appPush !== entry.telegram))"
          ],
          [
            163,
            "      )"
          ],
          [
            164,
            "        invalid()"
          ],
          [
            165,
            "      const body: NotificationPreferencesV2Update = {"
          ],
          [
            166,
            "        expectedRevision: boundedString(value.expectedRevision, 256),"
          ],
          [
            167,
            "        splitChannels: value.splitChannels,"
          ],
          [
            168,
            "        types,"
          ],
          [
            169,
            "      }"
          ],
          [
            170,
            "      if (Object.prototype.hasOwnProperty.call(value, 'mutedUntil'))"
          ],
          [
            171,
            "        body.mutedUntil = nullableInstant(value.mutedUntil)"
          ],
          [
            172,
            "      return parseNotificationPreferencesV2("
          ],
          [
            173,
            "        await request('/preferences/v2', { method: 'PUT', body: JSON.stringify(body) }),"
          ],
          [
            174,
            "      )"
          ]
        ]
      },
      {
        "title": "Раздельные каналы · часть ответа",
        "language": "json",
        "example": [
          "{",
          "  \"splitChannels\": true,",
          "  \"types\": {",
          "    \"LESSON_STARTED\": {",
          "      \"appPush\": true,",
          "      \"telegram\": false,",
          "      \"colorId\": \"violet\"",
          "    }",
          "  }",
          "}"
        ]
      },
      {
        "title": "Ревизия и пауза проверяются перед сохранением",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/preferences/NotificationPreferencesV2Service.java",
        "language": "java",
        "lines": [
          [
            48,
            "    public NotificationPreferencesV2Dto update("
          ],
          [
            49,
            "            long userId, NotificationPreferencesV2Dto.Update request) {"
          ],
          [
            50,
            "        validate(request);"
          ],
          [
            51,
            "        Long telegramId = binding.telegramId(userId);"
          ],
          [
            52,
            "        Document before = ensure(userId, telegramId);"
          ],
          [
            53,
            "        NotificationPreferencesV2Dto current = snapshot(before, telegramId != null);"
          ],
          [
            54,
            "        if (!current.revision().equals(request.expectedRevision()))"
          ],
          [
            55,
            "            throw new NotificationPreferencesConflict(current);"
          ],
          [
            56,
            "        Update update ="
          ],
          [
            57,
            "                new Update()"
          ],
          [
            58,
            "                        .set(\"v2.types\", encode(request.types()))"
          ],
          [
            59,
            "                        .set(\"v2.splitChannels\", request.splitChannels())"
          ],
          [
            60,
            "                        .set(\"v2.revision\", UUID.randomUUID().toString());"
          ],
          [
            61,
            "        if (request.hasMutedUntil()) {"
          ],
          [
            62,
            "            if (request.mutedUntil() == null) update.unset(\"mutedUntil\");"
          ],
          [
            63,
            "            else update.set(\"mutedUntil\", Date.from(request.mutedUntil()));"
          ],
          [
            64,
            "        }"
          ],
          [
            65,
            "        Document saved = store.patchV2(userId, current.revision(), update);"
          ],
          [
            66,
            "        if (saved == null)"
          ],
          [
            67,
            "            throw new NotificationPreferencesConflict("
          ],
          [
            68,
            "                    snapshot(store.durableUser(userId), telegramId != null));"
          ],
          [
            69,
            "        if (signals != null) signals.changed(userId);"
          ],
          [
            70,
            "        return snapshot(saved, telegramId != null);"
          ]
        ]
      },
      {
        "title": "Web Push учитывает общую паузу и флаг типа",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/preferences/NotificationPreferencesV2Service.java",
        "language": "java",
        "lines": [
          [
            148,
            "    public boolean appEnabled(long userId, String type) {"
          ],
          [
            149,
            "        if (!NotificationCatalog.typeIds().contains(type))"
          ],
          [
            150,
            "            throw new IllegalArgumentException(\"Unsupported notification type\");"
          ],
          [
            151,
            "        Document user = store.durableUser(userId);"
          ],
          [
            152,
            "        if (active(NotificationPreferencesStore.instant(user.get(\"mutedUntil\"))) != null)"
          ],
          [
            153,
            "            return false;"
          ],
          [
            154,
            "        Document profile = user.get(\"v2\", Document.class);"
          ],
          [
            155,
            "        return profile == null"
          ],
          [
            156,
            "                ? NotificationPreferencesStore.durableCategories(user.get(\"categories\"))"
          ],
          [
            157,
            "                        .get("
          ],
          [
            158,
            "                                NotificationCatalog.meanings().stream()"
          ],
          [
            159,
            "                                        .filter(meaning -> meaning.type().name().equals(type))"
          ],
          [
            160,
            "                                        .findFirst()"
          ],
          [
            161,
            "                                        .orElseThrow()"
          ],
          [
            162,
            "                                        .category())"
          ],
          [
            163,
            "                : types(profile).get(type).appPush();"
          ],
          [
            164,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "notification-lifecycle-telegram",
    "title": "Отправить сообщение в Telegram",
    "description": "Бот проверяет актуальную аудиторию события и пропускает аккаунты без Telegram-привязки. Перед отправкой очередь проверяет настройки точного типа, привязку и право получателя. Команды бота для всех уведомлений или категории обновляют флаги типов. В раздельном режиме они меняют только Telegram. Доставку блокируют как общая пауза аккаунта, так и пауза Telegram.",
    "frames": [
      {
        "title": "Рабочий обработчик событий использует сохранённую аудиторию",
        "path": "services/notification-bot/bot/consumers/event_dispatcher.py",
        "language": "python",
        "lines": [
          [
            457,
            "        event_type = event.get(\"event_type\")"
          ],
          [
            458,
            "        from bot.notifications.audience import handle_business"
          ],
          [
            459,
            "        from bot.services.notification_prefs import BUSINESS_EVENTS"
          ],
          [
            460,
            ""
          ],
          [
            461,
            "        if event_type in BUSINESS_EVENTS:"
          ],
          [
            462,
            "            async with self._send_queue.staged() as batch:"
          ],
          [
            463,
            "                await handle_business("
          ],
          [
            464,
            "                    event,"
          ],
          [
            465,
            "                    bot=self._bot,"
          ],
          [
            466,
            "                    academic=self._academic_client,"
          ],
          [
            467,
            "                    queue=self._send_queue,"
          ],
          [
            468,
            "                    reminders=self._redis_client,"
          ],
          [
            469,
            "                    config=self._config,"
          ],
          [
            470,
            "                    state=self._delivery_state,"
          ],
          [
            471,
            "                    attendance=self._attendance_client,"
          ],
          [
            472,
            "                    tracker=self._request_tracker,"
          ],
          [
            473,
            "                )"
          ],
          [
            474,
            "            return batch"
          ]
        ]
      },
      {
        "title": "Проверяются роли и Telegram-привязка",
        "path": "services/notification-bot/bot/notifications/audience.py",
        "language": "python",
        "lines": [
          [
            360,
            "    for user_id, saved_roles in saved.items():"
          ],
          [
            361,
            "        roles = saved_roles & current.get(user_id, frozenset())"
          ],
          [
            362,
            "        if not roles or user_id in delivered or await state.completed(event_id, user_id):"
          ],
          [
            363,
            "            continue"
          ],
          [
            375,
            "        try:"
          ],
          [
            376,
            "            user = await academic.get_user_by_id(user_id)"
          ],
          [
            377,
            "            if user.id != user_id:"
          ],
          [
            378,
            "                raise ValueError"
          ],
          [
            379,
            "            chat_id = user.telegram_id"
          ],
          [
            380,
            "            if chat_id == 0:"
          ],
          [
            381,
            "                continue  # Explicit unbound account, never an unknown authority."
          ],
          [
            382,
            "            if not positive(chat_id):"
          ],
          [
            383,
            "                raise ValueError"
          ],
          [
            384,
            "        except Exception as error:"
          ],
          [
            385,
            "            raise NotificationPreferencesUnavailable(\"Recipient binding unavailable\") from error"
          ],
          [
            386,
            ""
          ],
          [
            387,
            "        live_roles = [roles]"
          ],
          [
            388,
            ""
          ],
          [
            389,
            "        async def authorized(uid=user_id, retained=roles, role_state=live_roles):"
          ],
          [
            390,
            "            live = await resolve_all(academic, requests)"
          ],
          [
            391,
            "            role_state[0] = retained & live.get(uid, frozenset())"
          ],
          [
            392,
            "            if not role_state[0]:"
          ],
          [
            393,
            "                return False"
          ],
          [
            394,
            "            if uid in delivered or await state.completed(event_id, uid):"
          ],
          [
            395,
            "                return False"
          ]
        ]
      },
      {
        "title": "Очередь перепроверяет настройки и допуск",
        "path": "services/notification-bot/bot/services/send_queue.py",
        "language": "python",
        "lines": [
          [
            173,
            "        try:"
          ],
          [
            174,
            "            enabled = await self._prefs_client.is_enabled("
          ],
          [
            175,
            "                task.chat_id, task.category, user_id=task.user_id, notification_type=task.notification_type"
          ],
          [
            176,
            "            )"
          ],
          [
            177,
            "            if not enabled:"
          ],
          [
            178,
            "                return False"
          ],
          [
            179,
            "            return await task.audience_check() if task.audience_check is not None else True"
          ],
          [
            180,
            "        except NotificationPreferencesUnavailable:"
          ],
          [
            181,
            "            raise"
          ],
          [
            182,
            "        except Exception as error:"
          ],
          [
            183,
            "            raise NotificationPreferencesUnavailable(\"Recipient preferences are unavailable\") from error"
          ]
        ]
      },
      {
        "title": "Сервер проверяет привязку и тип уведомления",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/preferences/BotNotificationPreferencesService.java",
        "language": "java",
        "lines": [
          [
            67,
            "        if (type != null && v2 == null)"
          ],
          [
            68,
            "            throw NotificationPreferencesStore.unavailable("
          ],
          [
            69,
            "                    \"Exact type authority is unavailable\", null);"
          ],
          [
            70,
            "        binding.validate(userId, telegramId);"
          ],
          [
            71,
            "        if (v2 != null) v2.ensure(userId, telegramId);"
          ],
          [
            72,
            "        var result ="
          ],
          [
            73,
            "                snapshot("
          ],
          [
            74,
            "                        userId,"
          ],
          [
            75,
            "                        telegramId,"
          ],
          [
            76,
            "                        store.telegram(userId, telegramId),"
          ],
          [
            77,
            "                        store.user(userId),"
          ],
          [
            78,
            "                        category);"
          ],
          [
            79,
            "        if (type == null) return result;"
          ],
          [
            80,
            "        return new BotNotificationPreferencesDto("
          ],
          [
            81,
            "                result.userId(),"
          ],
          [
            82,
            "                result.telegramId(),"
          ],
          [
            83,
            "                result.globalEnabled(),"
          ],
          [
            84,
            "                result.categories(),"
          ],
          [
            85,
            "                result.mutedUntil(),"
          ],
          [
            86,
            "                result.canonicalCategories(),"
          ],
          [
            87,
            "                result.canonicalMutedUntil(),"
          ],
          [
            88,
            "                v2.telegramEnabled(userId, telegramId, type));"
          ]
        ]
      },
      {
        "title": "Глобальная и категорийная команды обновляют флаги типов",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/preferences/BotNotificationPreferencesService.java",
        "language": "java",
        "lines": [
          [
            141,
            "        Document updated = store.patchTelegram(userId, telegramId, patch, compareField, expected);"
          ],
          [
            142,
            "        if (v2 != null) {"
          ],
          [
            143,
            "            switch (request.operation()) {"
          ],
          [
            144,
            "                case TOGGLE_GLOBAL, SET_GLOBAL ->"
          ],
          [
            145,
            "                        v2.applyBotFlags("
          ],
          [
            146,
            "                                userId,"
          ],
          [
            147,
            "                                telegramId,"
          ],
          [
            148,
            "                                null,"
          ],
          [
            149,
            "                                NotificationPreferencesStore.globalEnabled(updated));"
          ],
          [
            150,
            "                case TOGGLE_CATEGORY, SET_CATEGORY ->"
          ],
          [
            151,
            "                        v2.applyBotFlags("
          ],
          [
            152,
            "                                userId,"
          ],
          [
            153,
            "                                telegramId,"
          ],
          [
            154,
            "                                request.category(),"
          ],
          [
            155,
            "                                NotificationPreferencesStore.durableCategories("
          ],
          [
            156,
            "                                                updated.get(\"categories\"))"
          ],
          [
            157,
            "                                        .get(request.category()));"
          ]
        ]
      },
      {
        "title": "Паузы и изменение каналов при командах бота",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/preferences/NotificationPreferencesV2Service.java",
        "language": "java",
        "lines": [
          [
            166,
            "    public boolean telegramEnabled(long userId, long telegramId, String type) {"
          ],
          [
            167,
            "        if (!NotificationCatalog.typeIds().contains(type))"
          ],
          [
            168,
            "            throw new ResponseStatusException("
          ],
          [
            169,
            "                    HttpStatus.BAD_REQUEST, \"Unsupported notification type\");"
          ],
          [
            170,
            "        Document user = ensure(userId, telegramId);"
          ],
          [
            171,
            "        Document facet = store.telegram(userId, telegramId);"
          ],
          [
            172,
            "        return types(user.get(\"v2\", Document.class)).get(type).telegram()"
          ],
          [
            173,
            "                && active(NotificationPreferencesStore.instant(user.get(\"mutedUntil\"))) == null"
          ],
          [
            174,
            "                && active(NotificationPreferencesStore.instant(facet.get(\"mutedUntil\"))) == null;"
          ],
          [
            178,
            "    void applyBotFlags(long userId, long telegramId, String category, boolean enabled) {"
          ],
          [
            179,
            "        Document user = ensure(userId, telegramId);"
          ],
          [
            180,
            "        Document profile = user.get(\"v2\", Document.class);"
          ],
          [
            181,
            "        boolean split = profile.getBoolean(\"splitChannels\");"
          ],
          [
            182,
            "        Map<String, NotificationPreferencesV2Dto.TypePreference> changed ="
          ],
          [
            183,
            "                new LinkedHashMap<>(types(profile));"
          ],
          [
            184,
            "        for (var meaning : NotificationCatalog.meanings()) {"
          ],
          [
            185,
            "            if (category != null && !meaning.category().equals(category)) continue;"
          ],
          [
            186,
            "            var old = changed.get(meaning.type().name());"
          ],
          [
            187,
            "            changed.put("
          ],
          [
            188,
            "                    meaning.type().name(),"
          ],
          [
            189,
            "                    new NotificationPreferencesV2Dto.TypePreference("
          ],
          [
            190,
            "                            split ? old.appPush() : enabled, enabled, old.colorId()));"
          ],
          [
            191,
            "        }"
          ]
        ]
      },
      {
        "title": "После отправки записывается подтверждение",
        "path": "services/notification-bot/bot/notifications/audience.py",
        "language": "python",
        "lines": [
          [
            474,
            "            result = await bot.send_message("
          ],
          [
            475,
            "                chat_id=chat,"
          ],
          [
            476,
            "                **message_options(display, config=config, facts=facts, role=role, fallback_markup=fallback),"
          ],
          [
            477,
            "            )"
          ],
          [
            478,
            "            # Persist receipt before best-effort auxiliary state."
          ],
          [
            479,
            "            await state.complete(event_id, uid)"
          ],
          [
            480,
            "            if kind in {\"lesson.started\", \"lesson.reminder\"}:"
          ],
          [
            481,
            "                await reminders.add_message_id(lesson_id, uid, result.message_id)"
          ]
        ]
      }
    ]
  },
  {
    "id": "notification-lifecycle-inbox",
    "title": "Прочитать ленту текущей роли",
    "description": "V2 выбирает документы по ID пользователя и выбранной роли. Группа влияет на исходную аудиторию события. Сохранённая лента проверяет владельца и представление роли. Для каждой роли отдельно хранятся время прочтения, порядок и ревизия. Одна запись может быть прочитана в STUDENT и оставаться непрочитанной в HEADMAN. Прочтение сообщения в Telegram не меняет статус карточки V2.",
    "frames": [
      {
        "title": "Контекст роли определяет выборку, порядок и следующую страницу",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationInboxService.java",
        "language": "java",
        "lines": [
          [
            48,
            "    public NotificationInboxDto.Page list("
          ],
          [
            49,
            "            RequestContext context, int limit, String cursor, boolean unreadOnly) {"
          ],
          [
            50,
            "        String role = context.requireSelectedRole();"
          ],
          [
            51,
            "        if (limit < 1 || limit > 100) throw bad(\"Notification limit must be between 1 and 100\");"
          ],
          [
            52,
            "        adoptLegacy(context.getUserId());"
          ],
          [
            53,
            "        Criteria criteria = scope(context.getUserId(), role, unreadOnly);"
          ],
          [
            54,
            "        if (cursor != null) {"
          ],
          [
            55,
            "            Cursor previous = decode(cursor, context, unreadOnly);"
          ],
          [
            74,
            "        Query query ="
          ],
          [
            75,
            "                Query.query(criteria)"
          ],
          [
            76,
            "                        .with("
          ],
          [
            77,
            "                                Sort.by("
          ],
          [
            78,
            "                                        Sort.Order.desc(path(role, \"order_at\")),"
          ],
          [
            79,
            "                                        Sort.Order.desc(\"_id\")))"
          ],
          [
            80,
            "                        .limit(limit + 1);"
          ],
          [
            81,
            "        List<NotificationHistoryDocument> documents ="
          ],
          [
            82,
            "                mongo.find(query, NotificationHistoryDocument.class);"
          ],
          [
            83,
            "        boolean hasMore = documents.size() > limit;"
          ],
          [
            84,
            "        if (hasMore) documents = documents.subList(0, limit);"
          ],
          [
            85,
            "        String next = hasMore ? encode(documents.getLast(), context, unreadOnly) : null;"
          ],
          [
            86,
            "        Map<Long, String> names = subjectNames(documents);"
          ],
          [
            87,
            "        return new NotificationInboxDto.Page("
          ],
          [
            88,
            "                role, documents.stream().map(doc -> catalog.presentInbox(doc, role, names)).toList(), next);"
          ]
        ]
      },
      {
        "title": "Запрос ограничен пользователем и представлением роли",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationInboxService.java",
        "language": "java",
        "lines": [
          [
            259,
            "    private static Criteria scope(long user, String role, boolean unreadOnly) {"
          ],
          [
            260,
            "        Criteria result = Criteria.where(\"user_id\").is(user).and(\"role_views.\" + role).exists(true);"
          ],
          [
            261,
            "        if (unreadOnly) result.and(path(role, \"read_at\")).is(null);"
          ],
          [
            262,
            "        return result;"
          ],
          [
            263,
            "    }"
          ],
          [
            264,
            ""
          ],
          [
            265,
            "    private static String path(String role, String field) {"
          ],
          [
            266,
            "        return \"role_views.\" + role + \".\" + field;"
          ],
          [
            267,
            "    }"
          ]
        ]
      },
      {
        "title": "Карточка возвращает ревизию и состояние выбранной роли",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationCatalog.java",
        "language": "java",
        "lines": [
          [
            655,
            "        return new NotificationInboxDto.Item("
          ],
          [
            656,
            "                doc.getId(),"
          ],
          [
            657,
            "                doc.getType().name(),"
          ],
          [
            658,
            "                heading,"
          ],
          [
            659,
            "                subjectName,"
          ],
          [
            660,
            "                doc.getSentAt(),"
          ],
          [
            661,
            "                view.getOrderAt(),"
          ],
          [
            662,
            "                view.getReadAt(),"
          ],
          [
            663,
            "                Long.toString(view.getRevision()),"
          ],
          [
            664,
            "                List.copyOf(details),"
          ],
          [
            665,
            "                action(doc.getType(), payload, role));"
          ],
          [
            666,
            "    }"
          ],
          [
            749,
            "        return lesson == null"
          ],
          [
            750,
            "                ? null"
          ],
          [
            751,
            "                : new NotificationInboxDto.Action("
          ],
          [
            752,
            "                        \"lesson\", role, lesson, date, null, null, null, null, null);"
          ],
          [
            753,
            "    }"
          ]
        ]
      },
      {
        "title": "Страница ленты STUDENT",
        "language": "json",
        "example": [
          "{",
          "  \"role\": \"STUDENT\",",
          "  \"items\": [",
          "    {",
          "      \"id\": \"67df2a9801b5ab402a7912cf\",",
          "      \"type\": \"LESSON_STARTED\",",
          "      \"heading\": \"Занятие началось\",",
          "      \"summary\": \"Основы программирования\",",
          "      \"sentAt\": \"2026-11-19T07:05:00Z\",",
          "      \"orderAt\": \"2026-11-19T07:05:00Z\",",
          "      \"readAt\": null,",
          "      \"revision\": \"0\",",
          "      \"details\": [",
          "        {",
          "          \"label\": \"Предмет\",",
          "          \"value\": \"Основы программирования\"",
          "        },",
          "        {",
          "          \"label\": \"Номер занятия\",",
          "          \"value\": \"2\"",
          "        },",
          "        {",
          "          \"label\": \"Начало\",",
          "          \"value\": \"10:05\"",
          "        },",
          "        {",
          "          \"label\": \"Окончание\",",
          "          \"value\": \"11:25\"",
          "        },",
          "        {",
          "          \"label\": \"Аудитория\",",
          "          \"value\": \"А-405\"",
          "        }",
          "      ],",
          "      \"action\": {",
          "        \"kind\": \"lesson\",",
          "        \"role\": \"STUDENT\",",
          "        \"lessonId\": \"731\"",
          "      }",
          "    }",
          "  ],",
          "  \"nextCursor\": null",
          "}"
        ]
      }
    ]
  },
  {
    "id": "notification-lifecycle-read",
    "title": "Изменить состояние одной карточки",
    "description": "При раскрытии непрочитанной карточки или переходе к доступному действию клиент отправляет read: true. Команда «Отметить непрочитанным» отправляет false. Сервер меняет только выбранное представление роли по ожидаемой ревизии. Точный повтор последней команды возвращает её результат. Устаревшая ревизия другой команды даёт конфликт.",
    "frames": [
      {
        "title": "Раскрытие отмечает карточку, меню снимает отметку",
        "path": "frontends/mobile-core/src/features/notifications/NotificationInboxCard.vue",
        "language": "typescript",
        "lines": [
          [
            49,
            "function expand(): void {"
          ],
          [
            50,
            "  expanded.value = !expanded.value;"
          ],
          [
            51,
            "  if ("
          ],
          [
            52,
            "    expanded.value &&"
          ],
          [
            53,
            "    unread.value &&"
          ],
          [
            54,
            "    !commandsDisabled.value &&"
          ],
          [
            55,
            "    readRequestedRevision.value !== props.item.revision"
          ],
          [
            56,
            "  ) {"
          ],
          [
            57,
            "    readRequestedRevision.value = props.item.revision;"
          ],
          [
            58,
            "    emit('read-state', props.item, true);"
          ],
          [
            59,
            "  }"
          ],
          [
            60,
            "}"
          ],
          [
            85,
            "function markUnread(): void {"
          ],
          [
            86,
            "  if (unread.value || commandsDisabled.value) return;"
          ],
          [
            87,
            "  menuOpen.value = false;"
          ],
          [
            88,
            "  emit('read-state', props.item, false);"
          ],
          [
            89,
            "  mainAction.value?.focus();"
          ]
        ]
      },
      {
        "title": "PUT отправляет флаг и ожидаемую ревизию, возвращает карточку",
        "path": "frontends/mobile-core/src/features/notifications/notifications-v2-client.ts",
        "language": "typescript",
        "lines": [
          [
            137,
            "    async setReadState(item, read) {"
          ],
          [
            138,
            "      const body = requiredRecord("
          ],
          [
            139,
            "        await request(`/inbox/${encodeURIComponent(safeId(item.id))}/read-state`, {"
          ],
          [
            140,
            "          method: 'PUT',"
          ],
          [
            141,
            "          body: JSON.stringify({ read, expectedRevision: boundedString(item.revision, 256) }),"
          ],
          [
            142,
            "        }),"
          ],
          [
            143,
            "      )"
          ],
          [
            144,
            "      assertRole(body.role)"
          ],
          [
            145,
            "      const saved = parseNotificationItem(body.item)"
          ],
          [
            146,
            "      if (saved.id !== item.id || (saved.readAt !== null) !== read) invalid()"
          ],
          [
            147,
            "      return { role, item: saved, unreadCount: count(body.unreadCount) }"
          ],
          [
            148,
            "    },"
          ]
        ]
      },
      {
        "title": "Отметить одну карточку прочитанной",
        "language": "json",
        "example": [
          "{",
          "  \"read\": true,",
          "  \"expectedRevision\": \"0\"",
          "}"
        ]
      },
      {
        "title": "Владелец и роль проверяются перед сравнением ревизии",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationInboxService.java",
        "language": "java",
        "lines": [
          [
            131,
            "        adoptLegacy(context.getUserId());"
          ],
          [
            132,
            "        Criteria own ="
          ],
          [
            133,
            "                new Criteria()"
          ],
          [
            134,
            "                        .andOperator("
          ],
          [
            135,
            "                                scope(context.getUserId(), role, false),"
          ],
          [
            136,
            "                                Criteria.where(\"_id\").is(id));"
          ],
          [
            137,
            "        NotificationHistoryDocument before ="
          ],
          [
            138,
            "                mongo.findOne(Query.query(own), NotificationHistoryDocument.class);"
          ],
          [
            139,
            "        if (before == null)"
          ],
          [
            140,
            "            throw new ResponseStatusException(HttpStatus.NOT_FOUND, \"Notification is unavailable\");"
          ],
          [
            141,
            "        var view = before.getRoleViews().get(role);"
          ],
          [
            142,
            "        if (view.getRevision() != expected) {"
          ],
          [
            143,
            "            if (Objects.equals(view.getLastExpected(), expected)"
          ],
          [
            144,
            "                    && Objects.equals(view.getLastRead(), request.read())) {"
          ],
          [
            145,
            "                return result(before, context, role, enrich);"
          ],
          [
            146,
            "            }"
          ],
          [
            147,
            "            throw conflict();"
          ],
          [
            148,
            "        }"
          ],
          [
            149,
            "        if ((view.getReadAt() != null) == request.read()) return result(before, context, role, enrich);"
          ]
        ]
      },
      {
        "title": "Изменение read_at и CAS текущей роли",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationInboxService.java",
        "language": "java",
        "lines": [
          [
            150,
            "        Instant now = Instant.now(clock);"
          ],
          [
            151,
            "        Update update ="
          ],
          [
            152,
            "                new Update()"
          ],
          [
            153,
            "                        .inc(path(role, \"revision\"), 1)"
          ],
          [
            154,
            "                        .set(path(role, \"last_expected\"), expected)"
          ],
          [
            155,
            "                        .set(path(role, \"last_read\"), request.read());"
          ],
          [
            156,
            "        if (request.read()) update.set(path(role, \"read_at\"), now);"
          ],
          [
            157,
            "        else update.unset(path(role, \"read_at\")).set(path(role, \"order_at\"), now);"
          ],
          [
            158,
            "        Criteria cas ="
          ],
          [
            159,
            "                new Criteria()"
          ],
          [
            160,
            "                        .andOperator(own, Criteria.where(path(role, \"revision\")).is(expected));"
          ],
          [
            161,
            "        NotificationHistoryDocument saved ="
          ],
          [
            162,
            "                mongo.findAndModify("
          ],
          [
            163,
            "                        Query.query(cas),"
          ],
          [
            164,
            "                        update,"
          ],
          [
            165,
            "                        FindAndModifyOptions.options().returnNew(true),"
          ],
          [
            166,
            "                        NotificationHistoryDocument.class);"
          ]
        ]
      },
      {
        "title": "Клиент применяет подтверждённую карточку и счётчик",
        "path": "frontends/mobile-core/src/features/notifications/NotificationsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            367,
            "    const ack = await mutations.run(() =>"
          ],
          [
            368,
            "      current(run) ? props.api.setReadState(item, read) : Promise.resolve(null),"
          ],
          [
            369,
            "    );"
          ],
          [
            370,
            "    if (!ack || !current(run)) return false;"
          ],
          [
            371,
            "    listRequest += 1;"
          ],
          [
            372,
            "    loading.value = false;"
          ],
          [
            373,
            "    loadingMore.value = false;"
          ],
          [
            374,
            "    props.unreadOwner.acknowledge(ack.unreadCount);"
          ],
          [
            375,
            "    items.value = items.value"
          ],
          [
            376,
            "      .map((row) => (row.id === ack.item.id ? ack.item : row))"
          ],
          [
            377,
            "      .filter((row) => !unreadOnly.value || row.readAt === null)"
          ],
          [
            378,
            "      .sort("
          ],
          [
            379,
            "        (left, right) =>"
          ],
          [
            380,
            "          right.orderAt.localeCompare(left.orderAt) || right.id.localeCompare(left.id),"
          ],
          [
            381,
            "      );"
          ]
        ]
      }
    ]
  },
  {
    "id": "notification-lifecycle-all",
    "title": "Отметить всё прочитанным",
    "description": "«Отметить всё прочитанным» отправляет POST без тела. Mongo обновляет все непрочитанные записи пользователя для выбранной роли, включая записи за пределами загруженной страницы. Уже прочитанные строки остаются прежними. Ответ содержит актуальный счётчик, а экран перечитывает ленту.",
    "frames": [
      {
        "title": "Кнопка доступна при наличии непрочитанного",
        "path": "frontends/mobile-core/src/features/notifications/NotificationInbox.vue",
        "language": "typescript",
        "lines": [
          [
            78,
            "      <button"
          ],
          [
            79,
            "        v-if=\"canMarkAllRead\""
          ],
          [
            80,
            "        type=\"button\""
          ],
          [
            81,
            "        class=\"notification-inbox__button notification-inbox__mark-all\""
          ],
          [
            82,
            "        :aria-label=\"markAllPending ? 'Отмечаем всё прочитанным' : 'Отметить всё прочитанным'\""
          ],
          [
            83,
            "        title=\"Отметить всё прочитанным\""
          ],
          [
            84,
            "        :aria-busy=\"markAllPending\""
          ],
          [
            85,
            "        :disabled=\"markAllPending || offline || loading || pendingIds.size > 0\""
          ],
          [
            86,
            "        @click=\"emit('mark-all-read')\""
          ],
          [
            87,
            "      >"
          ]
        ]
      },
      {
        "title": "POST отправляется без тела и возвращает счётчик",
        "path": "frontends/mobile-core/src/features/notifications/notifications-v2-client.ts",
        "language": "typescript",
        "lines": [
          [
            149,
            "    async markAllRead() {"
          ],
          [
            150,
            "      const body = requiredRecord(await request('/inbox/mark-all-read', { method: 'POST' }))"
          ],
          [
            151,
            "      assertRole(body.role)"
          ],
          [
            152,
            "      return { role, unreadCount: count(body.unreadCount) }"
          ],
          [
            153,
            "    },"
          ]
        ]
      },
      {
        "title": "Mongo обновляет только непрочитанные представления роли",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationInboxService.java",
        "language": "java",
        "lines": [
          [
            190,
            "    public long markAllRead(RequestContext context) {"
          ],
          [
            191,
            "        String role = context.requireSelectedRole();"
          ],
          [
            192,
            "        adoptLegacy(context.getUserId());"
          ],
          [
            193,
            "        var updateResult ="
          ],
          [
            194,
            "                mongo.updateMulti("
          ],
          [
            195,
            "                        Query.query(scope(context.getUserId(), role, true)),"
          ],
          [
            196,
            "                        new Update()"
          ],
          [
            197,
            "                                .set(path(role, \"read_at\"), Instant.now(clock))"
          ],
          [
            198,
            "                                .inc(path(role, \"revision\"), 1)"
          ],
          [
            199,
            "                                .unset(path(role, \"last_expected\"))"
          ],
          [
            200,
            "                                .unset(path(role, \"last_read\")),"
          ],
          [
            201,
            "                        NotificationHistoryDocument.class);"
          ],
          [
            202,
            "        if (signals != null && updateResult.getModifiedCount() > 0)"
          ],
          [
            203,
            "            signals.changed(context.getUserId());"
          ],
          [
            204,
            "        return count(context).count();"
          ],
          [
            205,
            "    }"
          ]
        ]
      },
      {
        "title": "Ответ после прочтения всех карточек",
        "language": "json",
        "example": [
          "{",
          "  \"role\": \"STUDENT\",",
          "  \"unreadCount\": 0",
          "}"
        ]
      },
      {
        "title": "Экран подтверждает счётчик и перечитывает ленту",
        "path": "frontends/mobile-core/src/features/notifications/NotificationsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            416,
            "async function markAllRead(): Promise<void> {"
          ],
          [
            417,
            "  if (props.offline || permissionDenied.value || markAllPending.value || !unread.value.count)"
          ],
          [
            418,
            "    return;"
          ],
          [
            419,
            "  const run = epoch;"
          ],
          [
            420,
            "  markAllPending.value = true;"
          ],
          [
            421,
            "  try {"
          ],
          [
            422,
            "    const ack = await mutations.run(() =>"
          ],
          [
            423,
            "      current(run) ? props.api.markAllRead() : Promise.resolve(null),"
          ],
          [
            424,
            "    );"
          ],
          [
            425,
            "    if (!ack || !current(run)) return;"
          ],
          [
            426,
            "    listRequest += 1;"
          ],
          [
            427,
            "    loading.value = false;"
          ],
          [
            428,
            "    loadingMore.value = false;"
          ],
          [
            429,
            "    props.unreadOwner.acknowledge(ack.unreadCount);"
          ],
          [
            430,
            "    await loadInbox();"
          ]
        ]
      }
    ]
  }
]
