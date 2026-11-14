import type { StoryOperation } from './story-source'

export const lessonLifecycleOperations: readonly StoryOperation[] = [
  {
    "id": "lesson-lifecycle-schedule",
    "title": "Пара появляется в расписании",
    "description": "Schedule создаёт для даты физическую пару со своим ID и ссылкой на экземпляр в расписании. При материализации шаблона статус определяется по московскому времени. Будущая пара получает PLANNED, начавшаяся до границы закрытия получает ACTIVE. Разовая пара создаётся в PLANNED и затем проходит обычные переходы.",
    "frames": [
      {
        "title": "Начальный статус пары из шаблона",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/constructor/SemesterMaterializationWriter.java",
        "language": "java",
        "lines": [
          [
            132,
            "    public long insertPhysicalLesson(long occurrence, OffsetDateTime at) {"
          ],
          [
            133,
            "        requireTransaction();"
          ],
          [
            134,
            "        var origin = jdbc.queryForMap("
          ],
          [
            135,
            "                \"SELECT *,week_type_snapshot::text AS week_type_snapshot FROM constructor_generation_origins WHERE occurrence_id=? AND operation_id=?\","
          ],
          [
            136,
            "                occurrence, activeOperation());"
          ],
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
          ],
          [
            156,
            "                \"closed\".equals(status) ? at : null);"
          ]
        ]
      },
      {
        "title": "Разовая пара и ссылка на её экземпляр",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/oneoff/OneOffLessonWriter.java",
        "language": "java",
        "lines": [
          [
            449,
            "            long lessonId ="
          ],
          [
            450,
            "                    jdbc.queryForObject("
          ],
          [
            451,
            "                            \"\"\""
          ],
          [
            452,
            "                    INSERT INTO lessons"
          ],
          [
            453,
            "                        (one_off_lesson_id, occurrence_id, assignment_id, group_id, subject_id, semester_id,"
          ],
          [
            454,
            "                         assigned_teacher_id, lesson_type, lesson_number, day_of_week, start_time, end_time,"
          ],
          [
            455,
            "                         room_snapshot, week_type_snapshot, generation, revision, date, status,"
          ],
          [
            456,
            "                         is_geo_blocked, created_at)"
          ],
          [
            457,
            "                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'all', 1, 1, ?, 'planned', FALSE, ?)"
          ],
          [
            458,
            "                    RETURNING id"
          ],
          [
            459,
            "                    \"\"\","
          ],
          [
            469,
            "                            request.lessonNumber(),"
          ],
          [
            470,
            "                            request.date().getDayOfWeek().getValue(),"
          ],
          [
            471,
            "                            request.startTime(),"
          ],
          [
            472,
            "                            request.endTime(),"
          ],
          [
            473,
            "                            request.classroom(),"
          ],
          [
            474,
            "                            request.date(),"
          ],
          [
            475,
            "                            now);"
          ],
          [
            476,
            "            jdbc.update("
          ],
          [
            477,
            "                    \"UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?\","
          ],
          [
            478,
            "                    lessonId,"
          ],
          [
            479,
            "                    occurrenceId);"
          ]
        ]
      }
    ]
  },
  {
    "id": "lesson-lifecycle-start",
    "title": "Автоматическое начало",
    "description": "Задача Schedule ждёт минуту после завершения предыдущего прохода. Когда по Москве наступает время начала, она выбирает PLANNED и ставит ACTIVE. ShedLock ограничивает параллельные запуски. Задача пропускает незавершённые переносы, семестры с блокировкой записи и шаблоны с блокировкой удаления.",
    "frames": [
      {
        "title": "Минута между проходами, транзакция и блокировки",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java",
        "language": "java",
        "lines": [
          [
            48,
            "    private static final ZoneId MOSCOW = ZoneId.of(\"Europe/Moscow\");"
          ],
          [
            74,
            "    @Scheduled(fixedDelay = 60_000)"
          ],
          [
            75,
            "    @SchedulerLock("
          ],
          [
            76,
            "            name = \"lesson-status-transition\","
          ],
          [
            77,
            "            lockAtMostFor = \"PT2M\","
          ],
          [
            78,
            "            lockAtLeastFor = \"PT10S\")"
          ],
          [
            79,
            "    @Transactional"
          ],
          [
            80,
            "    public void runTransitions() {"
          ],
          [
            81,
            "        String nowMoscow = LocalDateTime.ofInstant(clock.instant(), MOSCOW).toString();"
          ],
          [
            82,
            ""
          ],
          [
            83,
            "        // Phase 1: planned -> active"
          ],
          [
            84,
            "        List<Lesson> activationCandidates = lessonRepository.findPlannedDueForActivation(nowMoscow);"
          ],
          [
            85,
            "        List<Lesson> closingCandidates = lessonRepository.findActiveDueForClosure(nowMoscow);"
          ],
          [
            86,
            "        java.util.ArrayList<Lesson> candidates = new java.util.ArrayList<>(activationCandidates);"
          ],
          [
            87,
            "        candidates.addAll(closingCandidates);"
          ],
          [
            88,
            "        Set<Long> writableSemesters ="
          ],
          [
            89,
            "                archiveWriteFence.lockWritableSemesters("
          ],
          [
            90,
            "                        candidates.stream()"
          ],
          [
            91,
            "                                .map(Lesson::getSemesterId)"
          ],
          [
            92,
            "                                .filter(java.util.Objects::nonNull)"
          ],
          [
            93,
            "                                .toList());"
          ],
          [
            97,
            "        Set<Long> fencedItems ="
          ],
          [
            98,
            "                constructorDeletions.fencedScheduleItemsInSemesters(writableSemesters);"
          ],
          [
            99,
            "        List<Lesson> toActivate ="
          ],
          [
            100,
            "                activationCandidates.stream()"
          ],
          [
            101,
            "                        .filter("
          ],
          [
            102,
            "                                lesson ->"
          ],
          [
            103,
            "                                        lesson.getSemesterId() != null"
          ],
          [
            104,
            "                                                && writableSemesters.contains("
          ],
          [
            105,
            "                                                        lesson.getSemesterId()))"
          ],
          [
            106,
            "                        .filter("
          ],
          [
            107,
            "                                lesson ->"
          ],
          [
            108,
            "                                        lesson.getScheduleItemId() == null"
          ],
          [
            109,
            "                                                || !fencedItems.contains("
          ],
          [
            110,
            "                                                        lesson.getScheduleItemId()))"
          ],
          [
            111,
            "                        .toList();"
          ]
        ]
      },
      {
        "title": "Условия выборки до перехода в ACTIVE",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java",
        "language": "java",
        "lines": [
          [
            366,
            "        SELECT l.* FROM lessons l"
          ],
          [
            367,
            "        WHERE l.status::text = 'planned'"
          ],
          [
            368,
            "          AND NOT EXISTS (SELECT 1 FROM lesson_transfer_operations transfer"
          ],
          [
            369,
            "                           WHERE transfer.occurrence_id = l.occurrence_id"
          ],
          [
            370,
            "                             AND transfer.state <> 'COMPLETED')"
          ],
          [
            371,
            "          AND (l.date + l.start_time) <= CAST(:now AS timestamp)"
          ],
          [
            372,
            "        ORDER BY l.date, l.start_time"
          ],
          [
            373,
            "        \"\"\","
          ],
          [
            374,
            "            nativeQuery = true)"
          ],
          [
            375,
            "    List<Lesson> findPlannedDueForActivation(@Param(\"now\") String now);"
          ]
        ]
      },
      {
        "title": "Смена статуса и событие lesson.started",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java",
        "language": "java",
        "lines": [
          [
            112,
            "        for (Lesson lesson : toActivate) {"
          ],
          [
            113,
            "            lesson.setStatus(LessonStatus.ACTIVE);"
          ],
          [
            114,
            "            ScheduleItem item ="
          ],
          [
            124,
            "            eventPublisher.publishEvent("
          ],
          [
            125,
            "                    new LessonStartedEvent("
          ],
          [
            126,
            "                            this,"
          ],
          [
            127,
            "                            lesson.getId(),"
          ],
          [
            128,
            "                            snapshotGroup(lesson, item),"
          ],
          [
            129,
            "                            snapshotSubject(lesson, item),"
          ],
          [
            130,
            "                            snapshotLessonNumber(lesson, item),"
          ],
          [
            131,
            "                            snapshotStart(lesson, item),"
          ],
          [
            132,
            "                            snapshotEnd(lesson, item),"
          ],
          [
            133,
            "                            snapshotRoom(lesson, item)));"
          ],
          [
            134,
            "        }"
          ],
          [
            135,
            "        lessonRepository.saveAll(toActivate);"
          ]
        ]
      },
      {
        "title": "Поля события и формат времени",
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
        "title": "Данные lesson.started",
        "language": "json",
        "example": [
          "{",
          "  \"lesson_id\": 731,",
          "  \"group_id\": 41,",
          "  \"subject_id\": 19,",
          "  \"lesson_number\": 2,",
          "  \"start_time\": \"10:40\",",
          "  \"end_time\": \"12:10\",",
          "  \"room\": \"А-405\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "lesson-lifecycle-outbox",
    "title": "Из транзакции в уведомления",
    "description": "Schedule сохраняет статус и событие в одной транзакции. Outbox отправляет событие в RabbitMQ после коммита, поэтому время доставки зависит и от обработки очереди. Для lesson.started получателей определяют по составу группы на дату события и действующему старосте. Notification записывает событие в ленту, а Web Push и Telegram зависят от настроек и доступности канала.",
    "frames": [
      {
        "title": "Outbox пополняется перед коммитом",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/DomainEventListener.java",
        "language": "java",
        "lines": [
          [
            43,
            "    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)"
          ],
          [
            44,
            "    public void onDomainEvent(DomainEvent event) {"
          ],
          [
            45,
            "        // M04 QA3 — auto-fill envelope (trace_id/event_version/occurred_at/source)."
          ],
          [
            46,
            "        fillDefaults(event);"
          ],
          [
            47,
            "        String payload;"
          ],
          [
            48,
            "        try {"
          ],
          [
            49,
            "            payload = objectMapper.writeValueAsString(event);"
          ],
          [
            50,
            "        } catch (JsonProcessingException e) {"
          ],
          [
            57,
            "        }"
          ],
          [
            58,
            "        outboxStorage.save(event.getEventType(), payload);"
          ],
          [
            59,
            "        semesterArchiveEffectLedger.record(event, payload);"
          ],
          [
            60,
            "        log.debug(\"Outbox saved: type={}, id={}\", event.getEventType(), event.getEventId());"
          ]
        ]
      },
      {
        "title": "Сбой RabbitMQ оставляет запись ожидающей",
        "path": "services/shared/shared-outbox/src/main/java/ru/rutcampustrack/shared/outbox/OutboxPublisherJob.java",
        "language": "java",
        "lines": [
          [
            116,
            "            try {"
          ],
          [
            117,
            "                sender.send(record.eventType(), record.payload());"
          ],
          [
            118,
            "                incrementAttempt(\"confirmed\");"
          ],
          [
            119,
            "                confirmed.add(record);"
          ],
          [
            120,
            "            } catch (Exception e) {"
          ],
          [
            121,
            "                log.error(\"Outbox publish failed: id={}, category=transport\", record.id());"
          ],
          [
            122,
            "                incrementAttempt(\"failed\");"
          ],
          [
            123,
            "                incrementFailed(record.eventType());"
          ],
          [
            124,
            "                throw new IllegalStateException("
          ],
          [
            125,
            "                        \"Outbox publish failed; original event remains pending\");"
          ],
          [
            126,
            "            }"
          ]
        ]
      },
      {
        "title": "Состав группы и действующий староста",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceResolver.java",
        "language": "java",
        "lines": [
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
          ],
          [
            171,
            "        }"
          ],
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
          ]
        ]
      },
      {
        "title": "Лента раньше проверки возможности Web Push",
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
            118,
            "                    Map<Long, Set<String>> admitted = new LinkedHashMap<>();"
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
      },
      {
        "title": "Начало и напоминание поддерживают Web Push",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java",
        "language": "java",
        "lines": [
          [
            51,
            "    /** Events that should create Web Push notifications. */"
          ],
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
            156,
            "    /** Returns true if this event type should trigger a Web Push notification. */"
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
          ]
        ]
      },
      {
        "title": "Telegram сохраняет ID отправленного сообщения",
        "path": "services/notification-bot/bot/notifications/audience.py",
        "language": "python",
        "lines": [
          [
            373,
            "        if kind == \"lesson.reminder\" and await reminders.is_student_marked(lesson_id, user_id):"
          ],
          [
            374,
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
    "id": "lesson-lifecycle-window",
    "title": "Когда можно отметиться",
    "description": "Для пары с 10:40 до 12:10 Attendance проверяет окно с 10:35 по 12:15 по Москве включительно. Сервер отдельно проверяет группу, отмену и блокировку геоотметки. Староста отмечает себя через журнал. Экран «Сегодня» выделяет начавшуюся пару, а для кнопки учитывает доступность отметки. Снимок для интерфейса считает уже закрытую пару недоступной.",
    "frames": [
      {
        "title": "Пять минут и проверка допуска",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java",
        "language": "java",
        "lines": [
          [
            70,
            "    static final Duration CHECKIN_BUFFER = Duration.ofMinutes(5);"
          ],
          [
            604,
            "    private static void validateDynamicEligibility(Identity identity, Lesson lesson) {"
          ],
          [
            605,
            "        if (identity.headman()) {"
          ],
          [
            606,
            "            throw new StudentCheckinException("
          ],
          [
            607,
            "                    Code.CHECKIN_NOT_ELIGIBLE, \"Староста отмечает себя через журнал посещаемости\");"
          ],
          [
            608,
            "        }"
          ],
          [
            609,
            "        if (lesson == null || lesson.id() <= 0 || lesson.semesterId() <= 0) {"
          ],
          [
            610,
            "            throw new StudentCheckinException(Code.LESSON_NOT_FOUND, \"Пара не найдена\");"
          ],
          [
            611,
            "        }"
          ],
          [
            612,
            "        if (identity.groupId() != lesson.groupId()) {"
          ],
          [
            613,
            "            throw new StudentCheckinException(Code.OUT_OF_SCOPE, \"Пара принадлежит другой группе\");"
          ],
          [
            614,
            "        }"
          ],
          [
            615,
            "        if (\"cancelled\".equalsIgnoreCase(lesson.status())) {"
          ],
          [
            616,
            "            throw new StudentCheckinException(Code.CHECKIN_NOT_ELIGIBLE, \"Пара отменена\");"
          ],
          [
            617,
            "        }"
          ],
          [
            618,
            "        if (lesson.geoBlocked()) {"
          ],
          [
            619,
            "            throw new StudentCheckinException("
          ],
          [
            620,
            "                    Code.CHECKIN_NOT_ELIGIBLE, \"Геоотметка заблокирована\");"
          ],
          [
            621,
            "        }"
          ]
        ]
      },
      {
        "title": "Обе временные границы включены",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java",
        "language": "java",
        "lines": [
          [
            624,
            "    static void validateWindow(Lesson lesson, Instant now, Clock clock) {"
          ],
          [
            625,
            "        Instant opensAt ="
          ],
          [
            626,
            "                lesson.date()"
          ],
          [
            627,
            "                        .atTime(lesson.startsAt())"
          ],
          [
            628,
            "                        .atZone(clock.getZone())"
          ],
          [
            629,
            "                        .toInstant()"
          ],
          [
            630,
            "                        .minus(CHECKIN_BUFFER);"
          ],
          [
            631,
            "        Instant closesAt ="
          ],
          [
            632,
            "                lesson.date()"
          ],
          [
            633,
            "                        .atTime(lesson.endsAt())"
          ],
          [
            634,
            "                        .atZone(clock.getZone())"
          ],
          [
            635,
            "                        .toInstant()"
          ],
          [
            636,
            "                        .plus(CHECKIN_BUFFER);"
          ],
          [
            637,
            "        if (now.isBefore(opensAt) || now.isAfter(closesAt)) {"
          ],
          [
            638,
            "            throw new StudentCheckinException("
          ],
          [
            639,
            "                    Code.CHECKIN_NOT_ELIGIBLE, \"Вне временного окна отметки\");"
          ],
          [
            640,
            "        }"
          ],
          [
            641,
            "    }"
          ]
        ]
      },
      {
        "title": "Снимок доступности для экрана",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentAttendanceSnapshotService.java",
        "language": "java",
        "lines": [
          [
            201,
            "        if (\"closed\".equalsIgnoreCase(lesson.getStatus())) {"
          ],
          [
            202,
            "            return disabled(EligibilityReason.WINDOW_CLOSED, null);"
          ],
          [
            203,
            "        }"
          ],
          [
            204,
            "        if (lesson.getIsGeoBlocked() || lesson.getIsBlockedByHeadman()) {"
          ],
          [
            205,
            "            return disabled(EligibilityReason.GEO_BLOCKED, null);"
          ],
          [
            206,
            "        }"
          ],
          [
            207,
            "        LocalDate date = LocalDate.parse(lesson.getDate());"
          ],
          [
            208,
            "        LocalTime startsAt = LocalTime.parse(lesson.getStartTime());"
          ],
          [
            209,
            "        LocalTime endsAt = LocalTime.parse(lesson.getEndTime());"
          ],
          [
            210,
            "        Instant opensAt = date.atTime(startsAt).atZone(clock.getZone()).toInstant().minus(BUFFER);"
          ],
          [
            211,
            "        Instant closesAt = date.atTime(endsAt).atZone(clock.getZone()).toInstant().plus(BUFFER);"
          ],
          [
            212,
            "        if (now.isBefore(opensAt)) return disabled(EligibilityReason.TOO_EARLY, null);"
          ],
          [
            213,
            "        if (now.isAfter(closesAt)) return disabled(EligibilityReason.WINDOW_CLOSED, null);"
          ],
          [
            214,
            "        if (retryAt != null && now.isBefore(retryAt)) {"
          ],
          [
            215,
            "            return disabled(EligibilityReason.COOLDOWN, retryAt);"
          ],
          [
            216,
            "        }"
          ],
          [
            217,
            "        return new Eligibility(true, EligibilityReason.ELIGIBLE, null);"
          ]
        ]
      },
      {
        "title": "Активная пара и доступность кнопки",
        "path": "frontends/mobile-core/src/features/today/TodayScreen.vue",
        "language": "typescript",
        "lines": [
          [
            126,
            "function isCurrent(lesson: TodayLesson): boolean {"
          ],
          [
            127,
            "  return lesson.schedule.status === 'ACTIVE' && !isConfirmed(lesson);"
          ],
          [
            128,
            "}"
          ],
          [
            129,
            "function canCheckin(lesson: TodayLesson): boolean {"
          ],
          [
            130,
            "  if (props.offline || props.readOnly || busy.value || isConfirmed(lesson)) return false;"
          ],
          [
            131,
            "  const pending = isPendingCheckin(lesson);"
          ],
          [
            132,
            "  if (pending && lesson.request?.origin !== 'AUTO_GEO_FAILURE') return false;"
          ],
          [
            133,
            "  if (option(lesson)?.pendingRequests?.some((request) => request.origin !== 'AUTO_GEO_FAILURE'))"
          ],
          [
            134,
            "    return false;"
          ],
          [
            135,
            "  return ("
          ],
          [
            136,
            "    lesson.checkinEligibility.allowed ||"
          ],
          [
            137,
            "    (lesson.checkinEligibility.reason === 'COOLDOWN' &&"
          ],
          [
            138,
            "      lesson.checkinEligibility.retryAt !== null &&"
          ],
          [
            139,
            "      countdown(lesson) === 0)"
          ],
          [
            140,
            "  );"
          ]
        ]
      }
    ]
  },
  {
    "id": "lesson-lifecycle-reminders",
    "title": "Два момента для напоминаний",
    "description": "Отдельная задача проверяет ACTIVE с минутной паузой между проходами. Серединное напоминание возможно с середины пары до пяти минут перед концом. С этого порога до конца возможно финальное. Для пары 10:40–12:10 это интервалы с 11:25 до 12:05 и с 12:05 до 12:10, правая граница исключена. Каждая фаза записывает свой маркер в транзакции с outbox. После истечения интервала задача пропускает фазу.",
    "frames": [
      {
        "title": "Отдельная задача и блокировка семестра",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonReminderJob.java",
        "language": "java",
        "lines": [
          [
            61,
            "    @Scheduled(fixedDelay = 60_000)"
          ],
          [
            62,
            "    @SchedulerLock(name = \"lesson-reminder-job\", lockAtMostFor = \"PT2M\", lockAtLeastFor = \"PT10S\")"
          ],
          [
            63,
            "    @Transactional"
          ],
          [
            64,
            "    public void runReminders() {"
          ],
          [
            65,
            "        String nowMoscow = LocalDateTime.ofInstant(clock.instant(), MOSCOW).toString();"
          ],
          [
            66,
            "        List<Lesson> midpointCandidates ="
          ],
          [
            67,
            "                lessonRepository.findActiveDueForMidpointReminder(nowMoscow);"
          ],
          [
            68,
            "        List<Lesson> nearEndCandidates ="
          ],
          [
            69,
            "                lessonRepository.findActiveDueForNearEndReminder(nowMoscow);"
          ],
          [
            75,
            "        Set<Long> writableSemesters ="
          ],
          [
            76,
            "                archiveWriteFence.lockWritableSemesters("
          ],
          [
            77,
            "                        candidates.stream()"
          ],
          [
            78,
            "                                .map(Lesson::getSemesterId)"
          ],
          [
            79,
            "                                .filter(java.util.Objects::nonNull)"
          ],
          [
            80,
            "                                .toList());"
          ]
        ]
      },
      {
        "title": "Фаза midpoint доступна до последних пяти минут",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java",
        "language": "java",
        "lines": [
          [
            404,
            "        SELECT l.* FROM lessons l"
          ],
          [
            405,
            "        WHERE l.status::text = 'active'"
          ],
          [
            406,
            "          AND NOT EXISTS (SELECT 1 FROM lesson_transfer_operations transfer"
          ],
          [
            407,
            "                           WHERE transfer.occurrence_id = l.occurrence_id"
          ],
          [
            408,
            "                             AND transfer.state <> 'COMPLETED')"
          ],
          [
            409,
            "          AND l.reminder_midpoint_sent_at IS NULL"
          ],
          [
            410,
            "          AND (l.date + l.start_time + (l.end_time - l.start_time) / 2)"
          ],
          [
            411,
            "                  <= CAST(:now AS timestamp)"
          ],
          [
            412,
            "          AND (l.date + l.end_time - INTERVAL '5 minutes') > CAST(:now AS timestamp)"
          ],
          [
            413,
            "        ORDER BY l.date, l.start_time"
          ],
          [
            414,
            "        \"\"\","
          ],
          [
            415,
            "            nativeQuery = true)"
          ],
          [
            416,
            "    List<Lesson> findActiveDueForMidpointReminder(@Param(\"now\") String now);"
          ]
        ]
      },
      {
        "title": "Фаза near_end доступна до окончания пары",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java",
        "language": "java",
        "lines": [
          [
            421,
            "        SELECT l.* FROM lessons l"
          ],
          [
            422,
            "        WHERE l.status::text = 'active'"
          ],
          [
            423,
            "          AND NOT EXISTS (SELECT 1 FROM lesson_transfer_operations transfer"
          ],
          [
            424,
            "                           WHERE transfer.occurrence_id = l.occurrence_id"
          ],
          [
            425,
            "                             AND transfer.state <> 'COMPLETED')"
          ],
          [
            426,
            "          AND l.reminder_near_end_sent_at IS NULL"
          ],
          [
            427,
            "          AND (l.date + l.end_time - INTERVAL '5 minutes') <= CAST(:now AS timestamp)"
          ],
          [
            428,
            "          AND (l.date + l.end_time) > CAST(:now AS timestamp)"
          ],
          [
            429,
            "        ORDER BY l.date, l.end_time"
          ],
          [
            430,
            "        \"\"\","
          ],
          [
            431,
            "            nativeQuery = true)"
          ],
          [
            432,
            "    List<Lesson> findActiveDueForNearEndReminder(@Param(\"now\") String now);"
          ]
        ]
      },
      {
        "title": "Событие и отдельный маркер каждой фазы",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonReminderJob.java",
        "language": "java",
        "lines": [
          [
            97,
            "        if (midpointDue.isEmpty() && nearEndDue.isEmpty()) return;"
          ],
          [
            98,
            "        OffsetDateTime nowOffset = OffsetDateTime.ofInstant(clock.instant(), MOSCOW);"
          ],
          [
            99,
            "        for (Lesson lesson : midpointDue) {"
          ],
          [
            100,
            "            publishReminder(lesson, \"midpoint\");"
          ],
          [
            101,
            "            lesson.setReminderMidpointSentAt(nowOffset);"
          ],
          [
            102,
            "        }"
          ],
          [
            103,
            "        for (Lesson lesson : nearEndDue) {"
          ],
          [
            104,
            "            publishReminder(lesson, \"near_end\");"
          ],
          [
            105,
            "            lesson.setReminderNearEndSentAt(nowOffset);"
          ],
          [
            106,
            "        }"
          ],
          [
            107,
            "        lessonRepository.saveAll(midpointDue);"
          ],
          [
            108,
            "        lessonRepository.saveAll(nearEndDue);"
          ]
        ]
      },
      {
        "title": "Фаза входит в данные события",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonReminderEvent.java",
        "language": "java",
        "lines": [
          [
            21,
            "    public record Payload("
          ],
          [
            22,
            "            @JsonProperty(\"lesson_id\") Long lessonId,"
          ],
          [
            23,
            "            @JsonProperty(\"group_id\") Long groupId,"
          ],
          [
            24,
            "            @JsonProperty(\"subject_id\") Long subjectId,"
          ],
          [
            25,
            "            @JsonProperty(\"lesson_number\") Short lessonNumber,"
          ],
          [
            26,
            "            @JsonProperty(\"start_time\") String startTime,"
          ],
          [
            27,
            "            @JsonProperty(\"end_time\") String endTime,"
          ],
          [
            28,
            "            @JsonProperty(\"room\") String room,"
          ],
          [
            29,
            "            @JsonProperty(\"phase\") String phase) {}"
          ],
          [
            41,
            "        super("
          ],
          [
            42,
            "                source,"
          ],
          [
            43,
            "                \"lesson.reminder\","
          ],
          [
            44,
            "                new Payload("
          ],
          [
            45,
            "                        lessonId,"
          ],
          [
            46,
            "                        groupId,"
          ],
          [
            47,
            "                        subjectId,"
          ],
          [
            48,
            "                        lessonNumber,"
          ],
          [
            49,
            "                        startTime.toString(),"
          ],
          [
            50,
            "                        endTime.toString(),"
          ],
          [
            51,
            "                        room,"
          ],
          [
            52,
            "                        phase));"
          ]
        ]
      },
      {
        "title": "Данные lesson.reminder",
        "language": "json",
        "example": [
          "{",
          "  \"lesson_id\": 731,",
          "  \"group_id\": 41,",
          "  \"subject_id\": 19,",
          "  \"lesson_number\": 2,",
          "  \"start_time\": \"10:40\",",
          "  \"end_time\": \"12:10\",",
          "  \"room\": \"А-405\",",
          "  \"phase\": \"near_end\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "lesson-lifecycle-marked",
    "title": "После отметки напоминаний меньше",
    "description": "При обработке attendance.marked Notification обновляет состояние отметки. Из аудитории следующего lesson.reminder исключаются пользователи, чья отметка уже учтена, в том числе при формировании ленты. Бот ведёт своё состояние и повторно проверяет его перед отправкой. Правило относится к обработанным событиям, поэтому порядок поступления отметки и напоминания имеет значение.",
    "frames": [
      {
        "title": "Состояние обновляется до расчёта получателей",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/event/EventConsumer.java",
        "language": "java",
        "lines": [
          [
            103,
            "        withTraceContext("
          ],
          [
            104,
            "                envelope,"
          ],
          [
            105,
            "                () -> {"
          ],
          [
            106,
            "                    Map<String, Object> payload = NotificationAudienceClient.payload(envelope);"
          ],
          [
            107,
            "                    updateReminderState(eventType, payload);"
          ],
          [
            108,
            "                    if (NotificationCatalog.eventType(eventType, payload).isEmpty()) return;"
          ],
          [
            194,
            "    private void updateReminderState(String eventType, Map<String, Object> payload) {"
          ],
          [
            195,
            "        if (reminderState == null) return;"
          ],
          [
            196,
            "        if (\"attendance.marked\".equals(eventType)) reminderState.recordMarked(payload);"
          ],
          [
            197,
            "        else if (Set.of(\"lesson.closed\", \"lesson.cancelled\", \"lesson.deleted\", \"lesson.transferred\")"
          ],
          [
            198,
            "                .contains(eventType)) {"
          ],
          [
            199,
            "            if (payload.get(\"source\") instanceof Map<?, ?> source"
          ],
          [
            200,
            "                    && source.get(\"lesson_id\") != null)"
          ],
          [
            201,
            "                reminderState.deleteLessonState(Map.of(\"lesson_id\", source.get(\"lesson_id\")));"
          ],
          [
            202,
            "            else reminderState.deleteLessonState(payload);"
          ]
        ]
      },
      {
        "title": "В выборке остаются те, кто ещё не отмечен",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/history/NotificationAudienceService.java",
        "language": "java",
        "lines": [
          [
            110,
            "        if (\"lesson.reminder\".equals(eventType)) {"
          ],
          [
            111,
            "            Set<Long> unmarked ="
          ],
          [
            112,
            "                    reminderState.getUnmarkedUserIds("
          ],
          [
            113,
            "                            NotificationAudienceClient.positive(payload.get(\"lesson_id\")),"
          ],
          [
            114,
            "                            result.keySet());"
          ],
          [
            115,
            "            result.keySet().retainAll(unmarked);"
          ],
          [
            116,
            "        }"
          ],
          [
            117,
            "        return Collections.unmodifiableMap(result);"
          ]
        ]
      },
      {
        "title": "Бот учитывает отметку и перепроверяет перед доставкой",
        "path": "services/notification-bot/bot/notifications/audience.py",
        "language": "python",
        "lines": [
          [
            357,
            "    lesson_id = payload.get(\"lesson_id\")"
          ],
          [
            358,
            "    if kind == \"attendance.marked\" and payload.get(\"status\") in {\"present\", \"absent\", \"excused\", \"free_attendance\"}:"
          ],
          [
            359,
            "        await reminders.mark_student_marked(lesson_id, payload[\"user_id\"], payload[\"status\"])"
          ],
          [
            373,
            "        if kind == \"lesson.reminder\" and await reminders.is_student_marked(lesson_id, user_id):"
          ],
          [
            374,
            "            continue"
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
          ],
          [
            396,
            "            if kind == \"lesson.reminder\" and await reminders.is_student_marked(lesson_id, uid):"
          ],
          [
            397,
            "                return False"
          ]
        ]
      }
    ]
  },
  {
    "id": "lesson-lifecycle-close",
    "title": "Закрытие и пропущенный проход",
    "description": "Время закрытия наступает через пять минут после конца пары. Если Schedule пропустил начало и конец, ближайший доступный проход может сначала активировать сохранённую PLANNED, а затем закрыть её в той же транзакции. Иначе устроено создание прошлых пар из шаблона. Историческая пара сразу получает CLOSED без общих событий старта, напоминания и закрытия. Для её посещаемости Schedule сохраняет отдельную работу пакетами.",
    "frames": [
      {
        "title": "Отбор ACTIVE после конца и пяти минут",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/repository/LessonRepository.java",
        "language": "java",
        "lines": [
          [
            381,
            "        SELECT l.* FROM lessons l"
          ],
          [
            382,
            "        WHERE l.status::text = 'active'"
          ],
          [
            383,
            "          AND NOT EXISTS (SELECT 1 FROM lesson_transfer_operations transfer"
          ],
          [
            384,
            "                           WHERE transfer.occurrence_id = l.occurrence_id"
          ],
          [
            385,
            "                             AND transfer.state <> 'COMPLETED')"
          ],
          [
            386,
            "          AND (l.date + l.end_time + INTERVAL '5 minutes') <= CAST(:now AS timestamp)"
          ],
          [
            387,
            "        ORDER BY l.date, l.end_time"
          ],
          [
            388,
            "        \"\"\","
          ],
          [
            389,
            "            nativeQuery = true)"
          ],
          [
            390,
            "    List<Lesson> findActiveDueForClosure(@Param(\"now\") String now);"
          ]
        ]
      },
      {
        "title": "Повторная выборка после активации",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java",
        "language": "java",
        "lines": [
          [
            135,
            "        lessonRepository.saveAll(toActivate);"
          ],
          [
            136,
            ""
          ],
          [
            137,
            "        // Phase 2: active -> closed (including lessons just activated in phase 1 that are also past"
          ],
          [
            138,
            "        // end_time+5min)"
          ],
          [
            139,
            "        List<Lesson> toClose ="
          ],
          [
            140,
            "                lessonRepository.findActiveDueForClosure(nowMoscow).stream()"
          ],
          [
            141,
            "                        .filter("
          ],
          [
            142,
            "                                lesson ->"
          ],
          [
            143,
            "                                        lesson.getSemesterId() != null"
          ],
          [
            144,
            "                                                && writableSemesters.contains("
          ],
          [
            145,
            "                                                        lesson.getSemesterId()))"
          ],
          [
            146,
            "                        .filter("
          ],
          [
            147,
            "                                lesson ->"
          ],
          [
            148,
            "                                        lesson.getScheduleItemId() == null"
          ],
          [
            149,
            "                                                || !fencedItems.contains("
          ],
          [
            150,
            "                                                        lesson.getScheduleItemId()))"
          ],
          [
            151,
            "                        .toList();"
          ],
          [
            152,
            "        for (Lesson lesson : toClose) {"
          ],
          [
            153,
            "            lesson.setStatus(LessonStatus.CLOSED);"
          ],
          [
            154,
            "            lesson.setClosedAt(OffsetDateTime.ofInstant(clock.instant(), MOSCOW));"
          ],
          [
            165,
            "            eventPublisher.publishEvent("
          ],
          [
            166,
            "                    new LessonClosedEvent("
          ],
          [
            167,
            "                            this,"
          ],
          [
            168,
            "                            lesson.getId(),"
          ],
          [
            169,
            "                            snapshotGroup(lesson, item),"
          ],
          [
            170,
            "                            snapshotSubject(lesson, item),"
          ],
          [
            171,
            "                            lesson.getSemesterId()));"
          ],
          [
            172,
            "        }"
          ],
          [
            173,
            "        lessonRepository.saveAll(toClose);"
          ]
        ]
      },
      {
        "title": "Данные закрытия включают семестр",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/event/LessonClosedEvent.java",
        "language": "java",
        "lines": [
          [
            13,
            "    public record Payload("
          ],
          [
            14,
            "            @JsonProperty(\"lesson_id\") Long lessonId,"
          ],
          [
            15,
            "            @JsonProperty(\"group_id\") Long groupId,"
          ],
          [
            16,
            "            @JsonProperty(\"subject_id\") Long subjectId,"
          ],
          [
            17,
            "            @JsonProperty(\"semester_id\") Long semesterId) {}"
          ],
          [
            18,
            ""
          ],
          [
            19,
            "    public LessonClosedEvent("
          ],
          [
            20,
            "            Object source, Long lessonId, Long groupId, Long subjectId, Long semesterId) {"
          ],
          [
            21,
            "        super(source, \"lesson.closed\", new Payload(lessonId, groupId, subjectId, semesterId));"
          ],
          [
            22,
            "    }"
          ]
        ]
      },
      {
        "title": "Данные lesson.closed",
        "language": "json",
        "example": [
          "{",
          "  \"lesson_id\": 731,",
          "  \"group_id\": 41,",
          "  \"subject_id\": 19,",
          "  \"semester_id\": 7",
          "}"
        ]
      },
      {
        "title": "Событие при материализации сразу в ACTIVE",
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
          ]
        ]
      },
      {
        "title": "Исторические пары идут в отдельные пакеты",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/constructor/SemesterMaterializationWriter.java",
        "language": "java",
        "lines": [
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
          ],
          [
            178,
            "        int batches = (historical.size() + BATCH_SIZE - 1) / BATCH_SIZE;"
          ],
          [
            179,
            "        for (int batch = 0; batch < batches; batch++) {"
          ],
          [
            180,
            "            var command = PastLessonMaterializationCommand.newBuilder()"
          ],
          [
            181,
            "                    .setOperationId(operation.toString())"
          ],
          [
            182,
            "                    .setGroupId(((Number) scope.get(\"group_id\")).longValue())"
          ],
          [
            183,
            "                    .setSemesterId(((Number) scope.get(\"semester_id\")).longValue())"
          ],
          [
            184,
            "                    .setBatchIndex(batch).setBatchCount(batches);"
          ],
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
        "title": "Attendance применяет закрытие для исторической пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/constructor/ConstructorPastMaterializer.java",
        "language": "java",
        "lines": [
          [
            61,
            "            for (PastLessonIdentity target : c.getLessonsList()) {"
          ],
          [
            62,
            "                long lessonId = target.getLessonId();"
          ],
          [
            63,
            "                if (pairs.isSubjectDeleted(lessonId, c.getSemesterId(), c.getGroupId())) {"
          ],
          [
            64,
            "                    result.put(lessonId, \"TOMBSTONED\"); continue;"
          ],
          [
            65,
            "                }"
          ],
          [
            66,
            "                if (terminal(result.get(lessonId))) continue;"
          ],
          [
            67,
            "                var snapshot = prepared.get(lessonId);"
          ],
          [
            68,
            "                if (snapshot == null) { result.put(lessonId, unavailable.get(lessonId)); continue; }"
          ],
          [
            69,
            "                long before = mongo.count(Query.query(Criteria.where(\"lesson_id\").is(lessonId)), \"attendances\");"
          ],
          [
            70,
            "                lessons.applyLessonClosed(snapshot);"
          ],
          [
            71,
            "                long after = mongo.count(Query.query(Criteria.where(\"lesson_id\").is(lessonId)), \"attendances\");"
          ],
          [
            72,
            "                result.put(lessonId, snapshot.members().getStudentsCount() == 0 ? \"NO_ELIGIBLE_MEMBERS\" : after > before ? \"APPLIED\" : \"UNCHANGED\");"
          ]
        ]
      }
    ]
  },
  {
    "id": "lesson-lifecycle-journal",
    "title": "Что меняется в журнале",
    "description": "Attendance сверяет пару и читает состав группы на дату пары и семестр. Для отсутствующей записи создаётся ABSENT с источником AUTO_SCHEDULER, существующая отметка сохраняется. При чтении закрытой пары отсутствие может быть выведено ещё до записи. Уведомление о закрытии адресуется действующему старосте и допущенным помощникам с правом MARK_ATTENDANCE. Бот отдельно удаляет сохранённые напоминания для участников группы.",
    "frames": [
      {
        "title": "Семестр и пара из события закрытия",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java",
        "language": "java",
        "lines": [
          [
            248,
            "            stage = LessonClosedStage.PAYLOAD;"
          ],
          [
            249,
            "            Map<String, Object> payload = extractRequiredPayload(envelope);"
          ],
          [
            250,
            "            Long lessonId = extractPositiveInteger(payload, \"lesson_id\");"
          ],
          [
            251,
            "            Long groupId = extractPositiveInteger(payload, \"group_id\");"
          ],
          [
            252,
            "            Long semesterId = extractPositiveInteger(payload, \"semester_id\");"
          ],
          [
            253,
            "            stage = LessonClosedStage.PREPARE;"
          ],
          [
            254,
            "            LessonEventService.LessonClosedSnapshot effectSnapshot ="
          ],
          [
            255,
            "                    lessonEventService.prepareLessonClosed(lessonId, groupId, semesterId);"
          ],
          [
            256,
            "            stage = LessonClosedStage.TRANSACTION;"
          ],
          [
            257,
            "            transactionTemplate.executeWithoutResult("
          ],
          [
            265,
            "                        semesterArchiveEffectService.apply("
          ],
          [
            266,
            "                                envelope,"
          ],
          [
            267,
            "                                () -> lessonEventService.preflightLessonClosed(effectSnapshot),"
          ],
          [
            268,
            "                                () -> lessonEventService.applyLessonClosed(effectSnapshot));"
          ],
          [
            269,
            "                    });"
          ]
        ]
      },
      {
        "title": "Состав на дату пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/LessonEventService.java",
        "language": "java",
        "lines": [
          [
            112,
            "        if (pairWriteCoordinator.isSubjectDeleted(lessonId, eventSemesterId, groupId))"
          ],
          [
            113,
            "            return new LessonClosedSnapshot(lessonId, groupId, eventSemesterId, null, null, null);"
          ],
          [
            114,
            "        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);"
          ],
          [
            115,
            "        LocalDate lessonDate = validateLessonSnapshot(lesson, lessonId, groupId, eventSemesterId);"
          ],
          [
            116,
            "        GroupMembersResponse members = null;"
          ],
          [
            117,
            "        if (!\"transferred\".equalsIgnoreCase(lesson.getStatus())"
          ],
          [
            118,
            "                && !\"cancelled\".equalsIgnoreCase(lesson.getStatus())) {"
          ],
          [
            119,
            "            members ="
          ],
          [
            120,
            "                    academicGrpcClient.getGroupMembers("
          ],
          [
            121,
            "                            lesson.getGroupId(), lessonDate, lesson.getSemesterId());"
          ],
          [
            122,
            "            validateRosterEcho(members, lessonDate, lesson.getSemesterId());"
          ],
          [
            123,
            "        }"
          ],
          [
            124,
            "        return new LessonClosedSnapshot("
          ],
          [
            125,
            "                lessonId, groupId, eventSemesterId, lesson, lessonDate, members);"
          ],
          [
            178,
            "        if (\"cancelled\".equalsIgnoreCase(lesson.getStatus())) {"
          ],
          [
            179,
            "            ensureCancellationMarker(lessonId, eventSemesterId);"
          ],
          [
            180,
            "            applyCancellation(lessonId, eventSemesterId);"
          ],
          [
            181,
            "            return;"
          ],
          [
            182,
            "        }"
          ],
          [
            183,
            ""
          ],
          [
            184,
            "        // A cancellation may have won the race while the historical roster was"
          ],
          [
            185,
            "        // being resolved.  Do not start materialization after its marker."
          ],
          [
            186,
            "        if (isCancellationMarked(lessonId)) {"
          ],
          [
            187,
            "            applyCancellation(lessonId, eventSemesterId);"
          ],
          [
            188,
            "            return;"
          ],
          [
            189,
            "        }"
          ],
          [
            190,
            ""
          ],
          [
            191,
            "        if (members.getStudentsList().isEmpty()) {"
          ],
          [
            192,
            "            log.info("
          ],
          [
            193,
            "                    \"lesson.closed: no historical students in group {} for lesson {}, skipping\","
          ],
          [
            194,
            "                    lesson.getGroupId(),"
          ],
          [
            195,
            "                    lessonId);"
          ],
          [
            196,
            "            return;"
          ],
          [
            197,
            "        }"
          ],
          [
            198,
            ""
          ],
          [
            199,
            "        materializeAbsent(snapshot);"
          ]
        ]
      },
      {
        "title": "Автоотсутствие только для новой строки",
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
        "title": "Расчёт статуса при чтении",
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
      },
      {
        "title": "Аудитория закрытия ограничена правами",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/notification/NotificationAudienceResolver.java",
        "language": "java",
        "lines": [
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
          ],
          [
            171,
            "        }"
          ],
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
            320,
            "            case \"lesson.closed\" ->"
          ],
          [
            321,
            "                    new Policy("
          ],
          [
            322,
            "                            true, false, true, \"MARK_ATTENDANCE\", false, false, false, null, false);"
          ]
        ]
      },
      {
        "title": "Закрытие может дать Web Push",
        "path": "services/notification-service/notification-app/src/main/java/ru/rutcampustrack/notification/push/WebPushDeliveryService.java",
        "language": "java",
        "lines": [
          [
            72,
            "    private static final Set<String> HEADMAN_ONLY_EVENT_TYPES ="
          ],
          [
            73,
            "            Set.of(\"excuse.requested\", \"late_checkin.requested\", \"lesson.closed\");"
          ],
          [
            156,
            "    /** Returns true if this event type should trigger a Web Push notification. */"
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
          ]
        ]
      },
      {
        "title": "Telegram удаляет сохранённые напоминания",
        "path": "services/notification-bot/bot/notifications/audience.py",
        "language": "python",
        "lines": [
          [
            508,
            "    # Preserve reminder lifecycle independently of whether a business notification is eligible."
          ],
          [
            509,
            "    if kind == \"lesson.closed\" or kind == \"attendance.marked\":"
          ],
          [
            510,
            "        await cleanup_reminders(event, bot=bot, academic=academic, reminders=reminders)"
          ],
          [
            622,
            "    else:"
          ],
          [
            623,
            "        # Roster is used only to retire existing reminder artifacts, never to send facts."
          ],
          [
            624,
            "        members = await academic.get_current_group_members(payload[\"group_id\"])"
          ],
          [
            625,
            "        user_ids = [member.user_id for member in members if positive(member.user_id)]"
          ],
          [
            626,
            "    for user_id in user_ids:"
          ],
          [
            627,
            "        user = await academic.get_user_by_id(user_id)"
          ],
          [
            628,
            "        if user.id != user_id or not positive(user.telegram_id):"
          ],
          [
            629,
            "            continue"
          ],
          [
            630,
            "        for message_id in await reminders.get_message_ids(lesson_id, user_id):"
          ],
          [
            631,
            "            try:"
          ],
          [
            632,
            "                await bot.delete_message(chat_id=user.telegram_id, message_id=message_id)"
          ],
          [
            633,
            "            except TelegramBadRequest:"
          ],
          [
            634,
            "                pass"
          ],
          [
            635,
            "        await reminders.delete_key(lesson_id, user_id)"
          ],
          [
            636,
            "        if kind == \"lesson.closed\":"
          ],
          [
            637,
            "            await reminders.delete_marked_key(lesson_id, user_id)"
          ]
        ]
      }
    ]
  }
]
