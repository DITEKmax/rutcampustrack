import type { StoryOperation } from './story-source'

export const semesterLifecycleOperations: readonly StoryOperation[] = [
  {
    "id": "semester-lifecycle-create",
    "title": "Создать семестр",
    "description": "Администратор с правом SEMESTERS_MANAGE задаёт тип, учебный год и диапазон дат. POST /api/academic/semesters сохраняет неактивный семестр. Academic строит название из типа и года, проверяет обе даты и пересечения. Начало может быть в прошлом, если последний день семестра ещё не прошёл. Обе границы входят в диапазон.",
    "frames": [
      {
        "title": "Форма отправляет тип, год и даты",
        "path": "frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue",
        "language": "typescript",
        "lines": [
          [
            467,
            "  const selectedType = semesterType.value;"
          ],
          [
            468,
            "  const selectedYear = Number(academicYear.value);"
          ],
          [
            469,
            "  const selectedDateFrom = dateFrom.value;"
          ],
          [
            470,
            "  const selectedDateTo = dateTo.value;"
          ],
          [
            471,
            "  if ("
          ],
          [
            472,
            "    !selectedDateFrom ||"
          ],
          [
            473,
            "    !selectedDateTo ||"
          ],
          [
            474,
            "    selectedType === null ||"
          ],
          [
            475,
            "    !Number.isSafeInteger(selectedYear) ||"
          ],
          [
            476,
            "    selectedYear < 1 ||"
          ],
          [
            477,
            "    selectedYear > 9998"
          ],
          [
            478,
            "  ) {"
          ],
          [
            479,
            "    error.value = 'Укажи тип семестра, учебный год, дату начала и дату окончания.';"
          ],
          [
            480,
            "    return;"
          ],
          [
            481,
            "  }"
          ],
          [
            482,
            ""
          ],
          [
            483,
            "  const payload = {"
          ],
          [
            484,
            "    name: generatedSemesterName(selectedType, selectedYear),"
          ],
          [
            485,
            "    dateFrom: selectedDateFrom,"
          ],
          [
            486,
            "    dateTo: selectedDateTo,"
          ],
          [
            487,
            "    semesterType: selectedType,"
          ],
          [
            488,
            "    academicYear: selectedYear,"
          ],
          [
            489,
            "  };"
          ],
          [
            490,
            "  if (payload.dateTo < payload.dateFrom) {"
          ],
          [
            491,
            "    dateError.value = 'Дата окончания не может быть раньше даты начала.';"
          ],
          [
            492,
            "    return;"
          ]
        ]
      },
      {
        "title": "POST и право управления семестрами",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterController.java",
        "language": "java",
        "lines": [
          [
            59,
            "    @Override"
          ],
          [
            60,
            "    @RequireRole("
          ],
          [
            61,
            "            value = {ADMIN},"
          ],
          [
            62,
            "            admin = {\"SEMESTERS_MANAGE\"})"
          ],
          [
            63,
            "    public ResponseEntity<EntityModel<SemesterResponse>> createSemester("
          ],
          [
            64,
            "            CreateSemesterRequest request) {"
          ],
          [
            65,
            "        Semester semester = semesterService.createSemester(request);"
          ],
          [
            66,
            "        return ResponseEntity.status(201).body(semesterAssembler.toModel(semester));"
          ],
          [
            67,
            "    }"
          ]
        ]
      },
      {
        "title": "Запрос на создание весеннего семестра",
        "language": "json",
        "example": [
          "{",
          "  \"name\": \"Весенний 2026/2027\",",
          "  \"dateFrom\": \"2027-02-08\",",
          "  \"dateTo\": \"2027-06-30\",",
          "  \"semesterType\": \"SPRING\",",
          "  \"academicYear\": 2026",
          "}"
        ]
      },
      {
        "title": "Название генерируется, запись создаётся неактивной",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            117,
            "    @Transactional"
          ],
          [
            118,
            "    public Semester createSemester(CreateSemesterRequest request) {"
          ],
          [
            119,
            "        validateAcademicYear(request.semesterType(), request.academicYear());"
          ],
          [
            120,
            "        validateDates(request.dateFrom(), request.dateTo(), true);"
          ],
          [
            121,
            "        checkOverlapOrThrow(request.dateFrom(), request.dateTo(), null);"
          ],
          [
            122,
            ""
          ],
          [
            123,
            "        Semester semester = new Semester();"
          ],
          [
            124,
            "        semester.setName(generatedName(request.semesterType(), request.academicYear()));"
          ],
          [
            125,
            "        semester.setDateFrom(request.dateFrom());"
          ],
          [
            126,
            "        semester.setDateTo(request.dateTo());"
          ],
          [
            127,
            "        semester.setSemesterType(request.semesterType());"
          ],
          [
            128,
            "        semester.setAcademicYear(request.academicYear());"
          ],
          [
            129,
            "        semester.setActive(false);"
          ],
          [
            130,
            "        semester.setCreatedAt(OffsetDateTime.now());"
          ],
          [
            131,
            "        return semesterRepository.save(semester);"
          ],
          [
            132,
            "    }"
          ],
          [
            233,
            "    private String generatedName(SemesterType type, Integer academicYear) {"
          ],
          [
            234,
            "        String season = type == SemesterType.AUTUMN ? \"Осенний\" : \"Весенний\";"
          ],
          [
            235,
            "        return season + \" \" + academicYear + \"/\" + (academicYear + 1);"
          ],
          [
            236,
            "    }"
          ]
        ]
      },
      {
        "title": "Проверка дат по московскому календарю",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            260,
            "    private void validateDates(LocalDate from, LocalDate to, boolean requireFutureStart) {"
          ],
          [
            261,
            "        if (from == null) {"
          ],
          [
            262,
            "            throw new BadRequestException(\"dateFrom\", \"Дата начала обязательна\");"
          ],
          [
            263,
            "        }"
          ],
          [
            264,
            "        if (to == null) {"
          ],
          [
            265,
            "            throw new BadRequestException(\"dateTo\", \"Дата окончания обязательна\");"
          ],
          [
            266,
            "        }"
          ],
          [
            267,
            "        if (requireFutureStart"
          ],
          [
            268,
            "                && to.isBefore(LocalDate.ofInstant(educationalClock.instant(), MOSCOW))) {"
          ],
          [
            269,
            "            throw new BadRequestException(\"dateTo\", \"Нельзя создать завершённый семестр\");"
          ],
          [
            270,
            "        }"
          ],
          [
            271,
            "        if (to.isBefore(from)) {"
          ],
          [
            272,
            "            throw new BadRequestException(\"dateTo\", \"Дата окончания раньше даты начала\");"
          ],
          [
            273,
            "        }"
          ],
          [
            274,
            "    }"
          ]
        ]
      },
      {
        "title": "Пересечения проверяются с обеими границами",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SemesterRepository.java",
        "language": "java",
        "lines": [
          [
            66,
            "    @Query("
          ],
          [
            67,
            "            value ="
          ],
          [
            68,
            "                    \"\"\""
          ],
          [
            69,
            "            SELECT * FROM semesters"
          ],
          [
            70,
            "            WHERE daterange(date_from, date_to, '[]') && daterange(:from, :to, '[]')"
          ],
          [
            71,
            "              AND (:excludeId IS NULL OR id <> :excludeId)"
          ],
          [
            72,
            "            ORDER BY date_from"
          ],
          [
            73,
            "            LIMIT 1"
          ],
          [
            74,
            "            \"\"\","
          ],
          [
            75,
            "            nativeQuery = true)"
          ]
        ]
      }
    ]
  },
  {
    "id": "semester-lifecycle-activate",
    "title": "Активировать сейчас или запланировать",
    "description": "Кнопка активации отправляет PATCH /api/academic/semesters/{id}/activate. Один запрос либо включает семестр, либо сохраняет activationScheduled, когда дата начала ещё впереди. Текущий семестр при планировании продолжает действовать. На дате окончания включение ещё допустимо. Позднее ручной запрос получает конфликт.",
    "frames": [
      {
        "title": "PATCH для активации",
        "path": "frontends/mobile-core/src/features/admin-semester/admin-semester-client.ts",
        "language": "typescript",
        "lines": [
          [
            216,
            "  activateSemester(id: number): Promise<AdminSemester> {"
          ],
          [
            217,
            "    assertPositiveInteger(id, 'semesterId')"
          ],
          [
            218,
            "    return this.request<unknown>(`${AdminSemesterClient.basePath}/${id}/activate`, {"
          ],
          [
            219,
            "      method: 'PATCH',"
          ],
          [
            220,
            "    }).then(normalizeSemester)"
          ],
          [
            221,
            "  }"
          ]
        ]
      },
      {
        "title": "Экран различает активацию и планирование",
        "path": "frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue",
        "language": "typescript",
        "lines": [
          [
            571,
            "    const result = await props.client.activateSemester(semester.id);"
          ],
          [
            572,
            "    if ("
          ],
          [
            573,
            "      disposed ||"
          ],
          [
            574,
            "      authority !== props.authority ||"
          ],
          [
            575,
            "      scope !== authorityRevision ||"
          ],
          [
            576,
            "      client !== props.client"
          ],
          [
            577,
            "    )"
          ],
          [
            578,
            "      return;"
          ],
          [
            579,
            "    activationConfirmation.value = null;"
          ],
          [
            580,
            "    notice.value = result.active"
          ],
          [
            581,
            "      ? `Активирован семестр «${semester.name}».`"
          ],
          [
            582,
            "      : result.activationScheduled"
          ],
          [
            583,
            "        ? `Активация семестра «${semester.name}» запланирована на ${formatDate(result.dateFrom)}.`"
          ],
          [
            584,
            "        : `Запрос принят, но активация не подтверждена. Проверь состояние семестра.`;"
          ],
          [
            585,
            "    await refresh();"
          ]
        ]
      },
      {
        "title": "Право администратора для команды",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterController.java",
        "language": "java",
        "lines": [
          [
            153,
            "    @Override"
          ],
          [
            154,
            "    @RequireRole("
          ],
          [
            155,
            "            value = {ADMIN},"
          ],
          [
            156,
            "            admin = {\"SEMESTERS_MANAGE\"})"
          ],
          [
            157,
            "    public ResponseEntity<EntityModel<SemesterResponse>> activateSemester(Long id) {"
          ],
          [
            158,
            "        Semester semester = semesterService.activateSemester(id);"
          ],
          [
            159,
            "        return ResponseEntity.ok(semesterAssembler.toModel(semester));"
          ],
          [
            160,
            "    }"
          ]
        ]
      },
      {
        "title": "Архивный или переходный семестр защищён от активации",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            334,
            "        if (isWriteBlocked(target)) {"
          ],
          [
            335,
            "            throw new ConflictException("
          ],
          [
            336,
            "                    \"status\","
          ],
          [
            337,
            "                    id,"
          ],
          [
            338,
            "                    \"Нельзя активировать архивируемый или архивный семестр до восстановления\");"
          ],
          [
            339,
            "        }"
          ],
          [
            340,
            ""
          ],
          [
            341,
            "        validateAcademicYear(target.getSemesterType(), target.getAcademicYear());"
          ],
          [
            342,
            "        validateDates(target.getDateFrom(), target.getDateTo(), false);"
          ],
          [
            343,
            "        checkOverlapOrThrow(target.getDateFrom(), target.getDateTo(), id);"
          ],
          [
            736,
            "    private static boolean isWriteBlocked(Semester semester) {"
          ],
          [
            737,
            "        return semester.isArchived()"
          ],
          [
            738,
            "                || semester.isReleasePending()"
          ],
          [
            739,
            "                || semester.getArchiveTransition() != SemesterTransition.NONE"
          ],
          [
            740,
            "                || semester.getDeletionPhase() != null"
          ],
          [
            741,
            "                || semester.getTransitionOperationId() != null;"
          ],
          [
            742,
            "    }"
          ]
        ]
      },
      {
        "title": "Последний день включён, будущая активация сохраняется",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            344,
            "        if (today.isAfter(target.getDateTo())) {"
          ],
          [
            345,
            "            if (scheduledOnly) {"
          ],
          [
            346,
            "                target.setActivationScheduled(false);"
          ],
          [
            347,
            "                incrementStateVersion(target);"
          ],
          [
            348,
            "                invalidateDashboard();"
          ],
          [
            349,
            "                return semesterRepository.saveAndFlush(target);"
          ],
          [
            350,
            "            }"
          ],
          [
            351,
            "            throw new ConflictException(\"dateTo\", id, \"Нельзя активировать завершённый семестр\");"
          ],
          [
            352,
            "        }"
          ],
          [
            353,
            "        if (today.isBefore(target.getDateFrom())) {"
          ],
          [
            354,
            "            if (target.isActive()) {"
          ],
          [
            355,
            "                throw new ConflictException("
          ],
          [
            356,
            "                        \"dateFrom\","
          ],
          [
            357,
            "                        id,"
          ],
          [
            358,
            "                        \"Текущий семестр имеет будущую дату начала; сначала исправь даты\");"
          ],
          [
            359,
            "            }"
          ],
          [
            360,
            "            if (!scheduledOnly && !target.isActivationScheduled()) {"
          ],
          [
            361,
            "                target.setActivationScheduled(true);"
          ],
          [
            362,
            "                incrementStateVersion(target);"
          ],
          [
            363,
            "                invalidateDashboard();"
          ],
          [
            364,
            "                return semesterRepository.saveAndFlush(target);"
          ],
          [
            365,
            "            }"
          ],
          [
            366,
            "            return target;"
          ],
          [
            367,
            "        }"
          ]
        ]
      }
    ]
  },
  {
    "id": "semester-lifecycle-queue",
    "title": "Выполнить план по московской дате",
    "description": "Фоновая задача с задержкой 30 секунд по умолчанию выбирает сохранённые команды с dateFrom не позже московского сегодня. Она снова проверяет состояние под общей блокировкой переходов. Если к моменту обработки дата окончания уже прошла, флаг планирования снимается. Эта задача обрабатывает только запланированные включения.",
    "frames": [
      {
        "title": "Выбор запланированных включений",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SemesterRepository.java",
        "language": "java",
        "lines": [
          [
            19,
            "    @Query("
          ],
          [
            20,
            "            value ="
          ],
          [
            21,
            "                    \"\"\""
          ],
          [
            22,
            "            SELECT id FROM semesters"
          ],
          [
            23,
            "             WHERE activation_scheduled = TRUE AND date_from <= :today"
          ],
          [
            24,
            "             ORDER BY date_from, id LIMIT 100"
          ],
          [
            25,
            "            \"\"\","
          ],
          [
            26,
            "            nativeQuery = true)"
          ],
          [
            27,
            "    List<Long> findDueScheduledActivationIds(@Param(\"today\") LocalDate today);"
          ]
        ]
      },
      {
        "title": "Периодическая проверка по московскому календарю",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterActivationJob.java",
        "language": "java",
        "lines": [
          [
            47,
            "    @Scheduled(fixedDelayString = \"${semester.activation.delay-ms:30000}\")"
          ],
          [
            48,
            "    @SchedulerLock("
          ],
          [
            49,
            "            name = \"academic-semester-activation-job\","
          ],
          [
            50,
            "            lockAtMostFor = \"PT10M\","
          ],
          [
            51,
            "            lockAtLeastFor = \"PT1S\")"
          ],
          [
            52,
            "    public void activateDueSemesters() {"
          ],
          [
            53,
            "        LocalDate today = LocalDate.ofInstant(educationalClock.instant(), MOSCOW);"
          ],
          [
            54,
            "        for (Long id : repository.findDueScheduledActivationIds(today)) {"
          ],
          [
            55,
            "            try {"
          ],
          [
            56,
            "                service.activateScheduledSemester(id);"
          ],
          [
            57,
            "            } catch (ConflictException | BadRequestException | ResourceNotFoundException deferred) {"
          ],
          [
            58,
            "                log.warn("
          ],
          [
            59,
            "                        \"Scheduled semester activation deferred semester={} reason={}\","
          ],
          [
            60,
            "                        id,"
          ],
          [
            61,
            "                        deferred.getClass().getSimpleName());"
          ],
          [
            62,
            "            }"
          ],
          [
            63,
            "        }"
          ],
          [
            64,
            "    }"
          ]
        ]
      },
      {
        "title": "План повторно проверяется под блокировкой",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            300,
            "    /** Re-check durable intent after acquiring the same lock as all manual transitions. */"
          ],
          [
            301,
            "    @CacheEvict(value = \"active_semester\", allEntries = true)"
          ],
          [
            302,
            "    @Transactional"
          ],
          [
            303,
            "    public void activateScheduledSemester(Long id) {"
          ],
          [
            304,
            "        lockSemesterStateTransition();"
          ],
          [
            305,
            "        activateLocked(id, true);"
          ],
          [
            306,
            "    }"
          ],
          [
            307,
            ""
          ],
          [
            308,
            "    private Semester activateLocked(Long id, boolean scheduledOnly) {"
          ],
          [
            309,
            "        Semester target = findSemesterForUpdate(id);"
          ],
          [
            310,
            "        requireNoPendingSubjectDeletion(id, true);"
          ],
          [
            311,
            "        if (scheduledOnly && !target.isActivationScheduled()) {"
          ],
          [
            312,
            "            return target;"
          ],
          [
            313,
            "        }"
          ],
          [
            314,
            "        LocalDate today = LocalDate.ofInstant(educationalClock.instant(), MOSCOW);"
          ],
          [
            344,
            "        if (today.isAfter(target.getDateTo())) {"
          ],
          [
            345,
            "            if (scheduledOnly) {"
          ],
          [
            346,
            "                target.setActivationScheduled(false);"
          ],
          [
            347,
            "                incrementStateVersion(target);"
          ],
          [
            348,
            "                invalidateDashboard();"
          ],
          [
            349,
            "                return semesterRepository.saveAndFlush(target);"
          ],
          [
            350,
            "            }"
          ]
        ]
      }
    ]
  },
  {
    "id": "semester-lifecycle-switch",
    "title": "Переключить активный семестр",
    "description": "Academic выключает прежний семестр и включает новый в одной транзакции. У прежнего меняются active и версия состояния. Повтор для уже активного семестра сохраняет выбор. Событие semester.archived запускает обновление кеша активного семестра в Attendance. Прежний семестр остаётся в списке неактивных.",
    "frames": [
      {
        "title": "Блокировка строки и снятие признака active",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/SemesterRepository.java",
        "language": "java",
        "lines": [
          [
            47,
            "    @Lock(LockModeType.PESSIMISTIC_WRITE)"
          ],
          [
            48,
            "    @Query(\"select s from Semester s where s.id = :id\")"
          ],
          [
            49,
            "    Optional<Semester> findByIdForUpdate(@Param(\"id\") Long id);"
          ],
          [
            50,
            ""
          ],
          [
            51,
            "    @Modifying"
          ],
          [
            52,
            "    @Query("
          ],
          [
            53,
            "            \"UPDATE Semester s SET s.isActive = false, s.stateVersion = s.stateVersion + 1 \""
          ],
          [
            54,
            "                    + \"WHERE s.isActive = true\")"
          ],
          [
            55,
            "    int deactivateAllActive();"
          ]
        ]
      },
      {
        "title": "Повтор не переключает семестр ещё раз",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            369,
            "        // Replaying activation for the current semester is a successful no-op."
          ],
          [
            370,
            "        Optional<Semester> previouslyActive = semesterRepository.findByIsActiveTrue();"
          ],
          [
            371,
            "        if (previouslyActive.map(active -> active.getId().equals(id)).orElse(false)) {"
          ],
          [
            372,
            "            if (target.isActivationScheduled()) {"
          ],
          [
            373,
            "                target.setActivationScheduled(false);"
          ],
          [
            374,
            "                incrementStateVersion(target);"
          ],
          [
            375,
            "                invalidateDashboard();"
          ],
          [
            376,
            "                return semesterRepository.saveAndFlush(target);"
          ],
          [
            377,
            "            }"
          ],
          [
            378,
            "            return target;"
          ],
          [
            379,
            "        }"
          ]
        ]
      },
      {
        "title": "Прежний отключается, новый включается",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            381,
            "        // Deactivate before activation to satisfy the database's one-active constraint."
          ],
          [
            382,
            "        semesterRepository.deactivateAllActive();"
          ],
          [
            383,
            ""
          ],
          [
            384,
            "        // Bulk UPDATE bypasses the persistence context, so re-read after clearing it."
          ],
          [
            385,
            "        entityManager.flush();"
          ],
          [
            386,
            "        entityManager.clear();"
          ],
          [
            387,
            ""
          ],
          [
            388,
            "        // The outbox listener stores this only if the same transaction commits."
          ],
          [
            389,
            "        previouslyActive.ifPresent("
          ],
          [
            390,
            "                deactivated ->"
          ],
          [
            391,
            "                        eventPublisher.publishEvent("
          ],
          [
            392,
            "                                new SemesterArchivedEvent(this, deactivated.getId())));"
          ],
          [
            393,
            ""
          ],
          [
            394,
            "        // Re-read the locked target because clear() detached it."
          ],
          [
            395,
            "        Semester semester = findSemesterById(id);"
          ],
          [
            396,
            "        semester.setActive(true);"
          ],
          [
            397,
            "        semester.setActivationScheduled(false);"
          ],
          [
            398,
            "        incrementStateVersion(semester);"
          ],
          [
            399,
            "        invalidateDashboard();"
          ],
          [
            400,
            "        Semester saved = semesterRepository.saveAndFlush(semester);"
          ],
          [
            401,
            "        eventPublisher.publishEvent("
          ],
          [
            402,
            "                new AdministrativeNotificationSemesterActivatedEvent("
          ],
          [
            403,
            "                        this, saved, scheduledOnly ? null : activationActor()));"
          ]
        ]
      },
      {
        "title": "Attendance обновляет представление активного семестра",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/EventConsumer.java",
        "language": "java",
        "lines": [
          [
            576,
            "    private void handleSemesterArchived(Map<String, Object> envelope) {"
          ],
          [
            577,
            "        semesterCacheService.refresh();"
          ],
          [
            578,
            "        log.info(\"semester.archived: refreshed semester cache\");"
          ],
          [
            579,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "semester-lifecycle-archive",
    "title": "Начать архивирование",
    "description": "Экран отправляет POST /archive с Idempotency-Key. Academic снимает active и отложенную активацию, выставляет ARCHIVING и сохраняет версию операции. Сначала Schedule подтверждает готовность, затем Academic закрывает свой барьер, затем Attendance подтверждает финальный барьер. Пока хотя бы один участник не готов, операция остаётся открытой. Экран читает её по ID, а сервер продолжает сохранённую операцию.",
    "frames": [
      {
        "title": "Команды архива и восстановления с ключом",
        "path": "frontends/mobile-core/src/features/admin-semester/admin-semester-client.ts",
        "language": "typescript",
        "lines": [
          [
            327,
            "  private changeArchiveState("
          ],
          [
            328,
            "    id: number,"
          ],
          [
            329,
            "    action: AdminSemesterArchiveAction,"
          ],
          [
            330,
            "    idempotencyKey: string,"
          ],
          [
            331,
            "    signal?: AbortSignal,"
          ],
          [
            332,
            "  ): Promise<AdminSemesterArchiveOperation> {"
          ],
          [
            333,
            "    assertPositiveInteger(id, 'semesterId')"
          ],
          [
            334,
            "    assertUuid(idempotencyKey, 'idempotencyKey')"
          ],
          [
            335,
            "    return this.request<unknown>(`${AdminSemesterClient.basePath}/${id}/${action.toLowerCase()}`, {"
          ],
          [
            336,
            "      method: 'POST',"
          ],
          [
            337,
            "      body: null,"
          ],
          [
            338,
            "      headers: { 'Idempotency-Key': idempotencyKey },"
          ],
          [
            339,
            "      ...(signal ? { signal } : {}),"
          ],
          [
            340,
            "    }).then((value) => {"
          ],
          [
            341,
            "      const operation = normalizeArchiveOperation(value)"
          ],
          [
            342,
            "      if (operation.semesterId !== id || operation.action !== action) {"
          ],
          [
            343,
            "        throw new Error('Сервер вернул операцию для другого семестра или действия.')"
          ],
          [
            344,
            "      }"
          ],
          [
            345,
            "      return operation"
          ]
        ]
      },
      {
        "title": "Семестр переходит в ARCHIVING",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            447,
            "        boolean wasActive = semester.isActive();"
          ],
          [
            448,
            "        semester.setActive(false);"
          ],
          [
            449,
            "        semester.setActivationScheduled(false);"
          ],
          [
            450,
            "        semester.setArchiveTransition(SemesterTransition.ARCHIVING);"
          ],
          [
            451,
            "        incrementStateVersion(semester);"
          ],
          [
            452,
            "        invalidateDashboard();"
          ],
          [
            453,
            "        Semester transitioning = semesterRepository.saveAndFlush(semester);"
          ],
          [
            454,
            ""
          ],
          [
            455,
            "        // This legacy event refreshes downstream active-semester caches on deactivation."
          ],
          [
            456,
            "        // It is emitted once at the real active -> inactive transition, not on retries."
          ],
          [
            457,
            "        if (wasActive) {"
          ],
          [
            458,
            "            eventPublisher.publishEvent(new SemesterArchivedEvent(this, semester.getId()));"
          ],
          [
            459,
            "        }"
          ],
          [
            460,
            "        return transitioning;"
          ]
        ]
      },
      {
        "title": "Операция, версия и команда участнику сохраняются вместе",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterArchiveCommandTransaction.java",
        "language": "java",
        "lines": [
          [
            121,
            "        Semester semester ="
          ],
          [
            122,
            "                action == SemesterArchiveAction.ARCHIVE"
          ],
          [
            123,
            "                        ? semesterService.beginArchiveTransition(semesterId)"
          ],
          [
            124,
            "                        : semesterService.beginRestoreTransition(semesterId);"
          ],
          [
            125,
            ""
          ],
          [
            126,
            "        SemesterArchiveOperation operation ="
          ],
          [
            127,
            "                new SemesterArchiveOperation("
          ],
          [
            128,
            "                        UUID.randomUUID(), idempotencyKey, actorId, semesterId, action);"
          ],
          [
            129,
            "        copyAuthoritySnapshot(operation, semester);"
          ],
          [
            130,
            "        academicBarrier.install("
          ],
          [
            131,
            "                operation.getOperationId(), semesterId, operation.getStateVersion(), action);"
          ],
          [
            132,
            "        operation.setAcademic("
          ],
          [
            133,
            "                action == SemesterArchiveAction.ARCHIVE"
          ],
          [
            134,
            "                        ? SemesterArchiveParticipantStatus.PENDING"
          ],
          [
            135,
            "                        : SemesterArchiveParticipantStatus.PREPARED_RESTORE);"
          ],
          [
            136,
            "        operation.setSchedule(SemesterArchiveParticipantStatus.PENDING);"
          ],
          [
            137,
            "        operation.setAttendance(SemesterArchiveParticipantStatus.PENDING);"
          ],
          [
            138,
            "        SemesterArchiveOperation saved = operationRepository.saveAndFlush(operation);"
          ],
          [
            139,
            "        publishParticipantCommand(saved, initialCommand(action));"
          ],
          [
            140,
            "        return saved;"
          ]
        ]
      },
      {
        "title": "Готовность Schedule, Academic и Attendance",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterArchiveCoordinator.java",
        "language": "java",
        "lines": [
          [
            383,
            "        if (operation.getSchedule() == SemesterArchiveParticipantStatus.READY"
          ],
          [
            384,
            "                && operation.getAcademic() != SemesterArchiveParticipantStatus.READY) {"
          ],
          [
            385,
            "            String reason ="
          ],
          [
            386,
            "                    academicBarrier.sealArchive("
          ],
          [
            387,
            "                            operationId, operation.getSemesterId(), operation.getStateVersion());"
          ],
          [
            388,
            "            commands.recordParticipant("
          ],
          [
            389,
            "                    operationId,"
          ],
          [
            390,
            "                    SemesterArchiveCommandTransaction.Participant.ACADEMIC,"
          ],
          [
            391,
            "                    reason == null"
          ],
          [
            392,
            "                            ? SemesterArchiveParticipantStatus.READY"
          ],
          [
            393,
            "                            : SemesterArchiveParticipantStatus.PENDING,"
          ],
          [
            394,
            "                    reason);"
          ],
          [
            395,
            "        }"
          ],
          [
            396,
            ""
          ],
          [
            397,
            "        operation = commands.find(operationId);"
          ],
          [
            398,
            "        if (operation.getSchedule() == SemesterArchiveParticipantStatus.READY"
          ],
          [
            399,
            "                && operation.getAcademic() == SemesterArchiveParticipantStatus.READY"
          ],
          [
            400,
            "                && operation.getAttendance() != SemesterArchiveParticipantStatus.READY) {"
          ],
          [
            401,
            "            commands.enqueueParticipantCommand("
          ],
          [
            402,
            "                    operationId, SemesterArchiveParticipantCommand.SEAL_ARCHIVE);"
          ],
          [
            403,
            "        }"
          ],
          [
            404,
            "        operation = commands.find(operationId);"
          ],
          [
            405,
            "        if (operation.getSchedule() == SemesterArchiveParticipantStatus.READY"
          ],
          [
            406,
            "                && operation.getAcademic() == SemesterArchiveParticipantStatus.READY"
          ],
          [
            407,
            "                && operation.getAttendance() == SemesterArchiveParticipantStatus.READY) {"
          ],
          [
            408,
            "            operation = commands.completeArchive(operationId);"
          ],
          [
            409,
            "        }"
          ]
        ]
      },
      {
        "title": "HTTP 200, 202 или 503 по состоянию операции",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterController.java",
        "language": "java",
        "lines": [
          [
            192,
            "    private static ResponseEntity<SemesterArchiveOperationResponse> operationResponse("
          ],
          [
            193,
            "            SemesterArchiveOperationResponse response) {"
          ],
          [
            194,
            "        return switch (response.operationState()) {"
          ],
          [
            195,
            "            case COMPLETED -> ResponseEntity.ok(response);"
          ],
          [
            196,
            "            case PENDING -> ResponseEntity.accepted().body(response);"
          ],
          [
            197,
            "            case ERROR -> ResponseEntity.status(503).body(response);"
          ],
          [
            198,
            "        };"
          ]
        ]
      },
      {
        "title": "HTTP 202 · архивирование выполняется",
        "language": "json",
        "example": [
          "{",
          "  \"operationId\": \"30bc3c7d-cb43-452a-9eaa-3352d75859c3\",",
          "  \"semesterId\": 3,",
          "  \"action\": \"ARCHIVE\",",
          "  \"operationState\": \"PENDING\",",
          "  \"retryable\": true,",
          "  \"stateVersion\": 4,",
          "  \"active\": false,",
          "  \"archived\": false,",
          "  \"transition\": \"ARCHIVING\",",
          "  \"releasePending\": false,",
          "  \"academic\": \"PENDING\",",
          "  \"schedule\": \"PENDING\",",
          "  \"attendance\": \"PENDING\",",
          "  \"blockingReason\": \"Ожидается подтверждение принятой публикации домашнего задания в расписании\"",
          "}"
        ]
      },
      {
        "title": "Сохранённая операция проверяется по ID",
        "path": "frontends/mobile-core/src/features/admin-semester/AdminSemesterScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1320,
            "  let operation = initial;"
          ],
          [
            1321,
            "  for (let attempt = 0; attempt < 10; attempt += 1) {"
          ],
          [
            1322,
            "    if (!(await waitForArchivePoll(controller.signal, 1_200))) return;"
          ],
          [
            1323,
            "    if ("
          ],
          [
            1324,
            "      !isCurrentArchiveScope(scope, controller) ||"
          ],
          [
            1325,
            "      archiveCommand.value?.operationId !== operation.operationId"
          ],
          [
            1326,
            "    )"
          ],
          [
            1327,
            "      return;"
          ],
          [
            1328,
            "    try {"
          ],
          [
            1329,
            "      operation = await props.client.getArchiveOperation(operation.operationId, controller.signal);"
          ],
          [
            1330,
            "    } catch (cause) {"
          ],
          [
            1331,
            "      if (!isCurrentArchiveScope(scope, controller)) return;"
          ],
          [
            1332,
            "      if (cause instanceof StaleSessionGenerationError) {"
          ],
          [
            1333,
            "        resetArchiveScope();"
          ],
          [
            1334,
            "        return;"
          ],
          [
            1335,
            "      }"
          ],
          [
            1336,
            "      archiveCommand.value = { ...command, operationId: operation.operationId, phase: 'PENDING' };"
          ],
          [
            1337,
            "      error.value = 'Не удалось проверить ход операции. Продолжи проверку сохранённой операции.';"
          ]
        ]
      },
      {
        "title": "Фоновое продолжение сохранённых операций",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterArchiveCoordinator.java",
        "language": "java",
        "lines": [
          [
            56,
            "    @Scheduled(fixedDelayString = \"${semester.archive.retry-delay-ms:3000}\")"
          ],
          [
            57,
            "    @SchedulerLock("
          ],
          [
            58,
            "            name = \"academic-semester-archive-retry\","
          ],
          [
            59,
            "            lockAtMostFor = \"PT5M\","
          ],
          [
            60,
            "            lockAtLeastFor = \"PT3S\")"
          ],
          [
            61,
            "    public void retryPendingOperations() {"
          ],
          [
            62,
            "        for (UUID operationId : commands.retryableOperationIds()) {"
          ],
          [
            63,
            "            advance(operationId);"
          ],
          [
            64,
            "        }"
          ],
          [
            65,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "semester-lifecycle-effects",
    "title": "Закрыть записи и сохранить историю",
    "description": "В архиве остаются расписание, отметки, заявки и задания. Schedule исключает заблокированные семестры из запуска и закрытия пар. Attendance блокирует обычную запись, но на подготовительной стадии может завершить уже принятые эффекты Schedule. Academic защищает запись ДЗ. Незавершённый перенос или ожидающая публикация либо правка ДЗ могут задержать подтверждение архива.",
    "frames": [
      {
        "title": "Заблокированные семестры исключаются из переходов пар",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonStatusTransitionJob.java",
        "language": "java",
        "lines": [
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
          ]
        ]
      },
      {
        "title": "Schedule ждёт уже принятые переносы и изменения ДЗ",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleSemesterArchiveBarrierTransaction.java",
        "language": "java",
        "lines": [
          [
            404,
            "        Long unfinishedTransfers ="
          ],
          [
            405,
            "                jdbc.queryForObject("
          ],
          [
            406,
            "                        \"\"\""
          ],
          [
            407,
            "                SELECT count(*) FROM lesson_transfer_operations transfer"
          ],
          [
            408,
            "                JOIN lesson_occurrences occurrence ON occurrence.id = transfer.occurrence_id"
          ],
          [
            409,
            "                 WHERE occurrence.semester_id = ? AND transfer.state <> 'COMPLETED'"
          ],
          [
            410,
            "                \"\"\","
          ],
          [
            411,
            "                        Long.class,"
          ],
          [
            412,
            "                        semesterId);"
          ],
          [
            413,
            "        if (unfinishedTransfers != null && unfinishedTransfers > 0) {"
          ],
          [
            414,
            "            return new BlockingStatus("
          ],
          [
            415,
            "                    \"Ожидается завершение или reconciliation переноса пары в расписании\", null);"
          ],
          [
            416,
            "        }"
          ],
          [
            417,
            "        PendingBinding pendingBinding = firstPendingBinding(semesterId);"
          ],
          [
            418,
            "        if (pendingBinding != null) {"
          ],
          [
            419,
            "            return new BlockingStatus("
          ],
          [
            420,
            "                    \"Ожидается подтверждение принятой публикации домашнего задания в расписании\","
          ],
          [
            421,
            "                    pendingBinding);"
          ],
          [
            422,
            "        }"
          ],
          [
            423,
            "        if (Boolean.TRUE.equals("
          ],
          [
            424,
            "                jdbc.queryForObject("
          ],
          [
            425,
            "                        \"\"\""
          ],
          [
            426,
            "                SELECT EXISTS (SELECT 1 FROM lesson_homework_bindings"
          ],
          [
            427,
            "                    WHERE semester_id = ? AND pending_edit_operation_id IS NOT NULL)"
          ],
          [
            428,
            "                \"\"\","
          ],
          [
            429,
            "                        Boolean.class,"
          ],
          [
            430,
            "                        semesterId))) {"
          ],
          [
            431,
            "            return new BlockingStatus("
          ],
          [
            432,
            "                    \"Ожидается подтверждение принятого изменения домашнего задания\", null);"
          ],
          [
            433,
            "        }"
          ],
          [
            434,
            "        return new BlockingStatus(effectLedger.firstUnprovenEffectReason(semesterId), null);"
          ]
        ]
      },
      {
        "title": "Attendance разделяет обычную запись и принятые эффекты",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/SemesterArchiveFence.java",
        "language": "java",
        "lines": [
          [
            61,
            "    public SemesterArchiveFenceDocument lockWritable(long semesterId, Instant now) {"
          ],
          [
            62,
            "        SemesterArchiveFenceDocument fence = lock(semesterId, now);"
          ],
          [
            63,
            "        if (!isWritable(fence)) {"
          ],
          [
            64,
            "            throw new ConflictException("
          ],
          [
            65,
            "                    \"Изменения посещаемости недоступны: семестр архивируется или архивирован\");"
          ],
          [
            66,
            "        }"
          ],
          [
            67,
            "        return fence;"
          ],
          [
            68,
            "    }"
          ],
          [
            74,
            "    public SemesterArchiveFenceDocument lockAcceptedScheduleEffect(long semesterId, Instant now) {"
          ],
          [
            75,
            "        SemesterArchiveFenceDocument fence = lock(semesterId, now);"
          ],
          [
            76,
            "        String state = fence == null ? null : fence.getBarrierState();"
          ],
          [
            77,
            "        if (!\"OPEN\".equals(state)"
          ],
          [
            78,
            "                && !\"RELEASED\".equals(state)"
          ],
          [
            79,
            "                && !\"ARCHIVE_PREPARING\".equals(state)"
          ],
          [
            80,
            "                && !\"DELETE_PREPARING\".equals(state)) {"
          ],
          [
            81,
            "            String reason ="
          ],
          [
            82,
            "                    \"DELETE_SEALED\".equals(state) || \"DELETE_COMMITTED\".equals(state)"
          ],
          [
            83,
            "                            ? \"SEMESTER_DELETE_SEALED\""
          ],
          [
            84,
            "                            : \"SEMESTER_ARCHIVE_SEALED\";"
          ],
          [
            85,
            "            throw new SemesterArchiveEffectRejectedException("
          ],
          [
            86,
            "                    reason, \"Schedule effect arrived after the semester barrier closed: \" + reason);"
          ],
          [
            87,
            "        }"
          ]
        ]
      },
      {
        "title": "Блокировка записей Academic",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/AcademicSemesterArchiveBarrierTransaction.java",
        "language": "java",
        "lines": [
          [
            330,
            "    /** Ordinary write entrypoint: acquire this lock before binding/row locks, then fail closed. */"
          ],
          [
            331,
            "    public void lockOrdinaryWrite(long semesterId) {"
          ],
          [
            332,
            "        requireTransaction();"
          ],
          [
            333,
            "        lockSemester(semesterId);"
          ],
          [
            334,
            "        Map<String, Object> state = authorityState(semesterId);"
          ],
          [
            335,
            "        Map<String, Object> barrier = barrier(semesterId);"
          ],
          [
            336,
            "        if (authorityBlocks(state)"
          ],
          [
            337,
            "                || barrier != null && isFenced(String.valueOf(barrier.get(\"participant_state\")))) {"
          ],
          [
            338,
            "            throw new ConflictException("
          ],
          [
            339,
            "                    \"Семестр временно заблокирован переходом архивации или восстановления\");"
          ],
          [
            340,
            "        }"
          ],
          [
            341,
            "    }"
          ]
        ]
      },
      {
        "title": "После подтверждений меняется состояние семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            468,
            "    public Semester completeArchiveTransition(Long id, long expectedStateVersion) {"
          ],
          [
            469,
            "        lockSemesterStateTransition();"
          ],
          [
            470,
            "        Semester semester = findSemesterForUpdate(id);"
          ],
          [
            471,
            ""
          ],
          [
            472,
            "        if (semester.isArchived() && semester.getArchiveTransition() == SemesterTransition.NONE) {"
          ],
          [
            473,
            "            requireReplayVersion(semester, id, expectedStateVersion);"
          ],
          [
            474,
            "            return semester;"
          ],
          [
            475,
            "        }"
          ],
          [
            476,
            "        requireTransitionVersion(semester, id, expectedStateVersion, SemesterTransition.ARCHIVING);"
          ],
          [
            477,
            "        semester.setArchived(true);"
          ],
          [
            478,
            "        semester.setArchiveTransition(SemesterTransition.NONE);"
          ],
          [
            479,
            "        invalidateDashboard();"
          ],
          [
            480,
            "        return semesterRepository.saveAndFlush(semester);"
          ]
        ]
      }
    ]
  },
  {
    "id": "semester-lifecycle-restore",
    "title": "Восстановить и затем активировать",
    "description": "Если последний день архивного семестра уже прошёл по Москве, администратор сначала расширяет диапазон. Архивные даты можно расширять с сохранением прежних дат, без пересечения с другим семестром. Восстановление возвращает семестр в неактивное состояние. Архивная блокировка снимается после подтверждений участников. Затем администратор отдельно нажимает «Сделать текущим».",
    "frames": [
      {
        "title": "Условие восстановления по последнему дню",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            510,
            "        if (semester.getDateTo()"
          ],
          [
            511,
            "                .isBefore(LocalDate.ofInstant(educationalClock.instant(), MOSCOW))) {"
          ],
          [
            512,
            "            throw new ConflictException("
          ],
          [
            513,
            "                    \"dateTo\","
          ],
          [
            514,
            "                    id,"
          ],
          [
            515,
            "                    \"Сначала расширь даты истёкшего архивного семестра, затем восстанови его\");"
          ],
          [
            516,
            "        }"
          ],
          [
            517,
            "        semester.setArchiveTransition(SemesterTransition.RESTORING);"
          ],
          [
            518,
            "        incrementStateVersion(semester);"
          ],
          [
            519,
            "        return semesterRepository.saveAndFlush(semester);"
          ]
        ]
      },
      {
        "title": "Правила расширения архивных дат",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            173,
            "        boolean datesChanged ="
          ],
          [
            174,
            "                !request.dateFrom().equals(semester.getDateFrom())"
          ],
          [
            175,
            "                        || !request.dateTo().equals(semester.getDateTo());"
          ],
          [
            176,
            "        if (archivedMetadata) {"
          ],
          [
            177,
            "            if (request.dateFrom().isAfter(semester.getDateFrom())"
          ],
          [
            178,
            "                    || request.dateTo().isBefore(semester.getDateTo())) {"
          ],
          [
            179,
            "                throw new ConflictException("
          ],
          [
            180,
            "                        \"dates\","
          ],
          [
            181,
            "                        id,"
          ],
          [
            182,
            "                        \"Архивный диапазон можно только расширить с сохранением всех прежних дат\");"
          ],
          [
            183,
            "            }"
          ],
          [
            184,
            "            if ((semester.getSemesterType() != null"
          ],
          [
            185,
            "                            && request.semesterType() != semester.getSemesterType())"
          ],
          [
            186,
            "                    || (semester.getAcademicYear() != null"
          ],
          [
            187,
            "                            && !semester.getAcademicYear().equals(request.academicYear()))) {"
          ],
          [
            188,
            "                throw new ConflictException("
          ],
          [
            189,
            "                        \"semesterType\","
          ],
          [
            190,
            "                        id,"
          ],
          [
            191,
            "                        \"Расширение архивного диапазона не меняет тип и учебный год семестра\");"
          ],
          [
            192,
            "            }"
          ],
          [
            193,
            "        } else if (datesChanged && assignmentRepository.existsBySemesterId(id)) {"
          ],
          [
            194,
            "            throw new ConflictException("
          ],
          [
            195,
            "                    \"dates\", id, \"Нельзя изменить даты семестра, на который ссылаются назначения\");"
          ],
          [
            196,
            "        }"
          ],
          [
            197,
            "        checkOverlapOrThrow(request.dateFrom(), request.dateTo(), id);"
          ]
        ]
      },
      {
        "title": "Восстановление снимает архив и оставляет releasePending",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            665,
            "        requireTransitionVersion(semester, id, expectedStateVersion, SemesterTransition.RESTORING);"
          ],
          [
            666,
            "        if (semester.isActive()) {"
          ],
          [
            667,
            "            throw new ConflictException("
          ],
          [
            668,
            "                    \"status\", id, \"Восстановление не может активировать семестр\");"
          ],
          [
            669,
            "        }"
          ],
          [
            670,
            ""
          ],
          [
            671,
            "        semester.setArchived(false);"
          ],
          [
            672,
            "        semester.setArchiveTransition(SemesterTransition.NONE);"
          ],
          [
            673,
            "        semester.setReleasePending(true);"
          ],
          [
            674,
            "        invalidateDashboard();"
          ],
          [
            675,
            "        return semesterRepository.saveAndFlush(semester);"
          ]
        ]
      },
      {
        "title": "Снятие блокировки после трёх подтверждений",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterArchiveCoordinator.java",
        "language": "java",
        "lines": [
          [
            669,
            "            if (operation.getSchedule() == SemesterArchiveParticipantStatus.RELEASED"
          ],
          [
            670,
            "                    && operation.getAcademic() == SemesterArchiveParticipantStatus.RELEASED"
          ],
          [
            671,
            "                    && operation.getAttendance() == SemesterArchiveParticipantStatus.RELEASED) {"
          ],
          [
            672,
            "                operation = commands.completeRestoreRelease(operationId);"
          ],
          [
            673,
            "            }"
          ]
        ]
      },
      {
        "title": "Центральный флаг снимается после проверки версии",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterService.java",
        "language": "java",
        "lines": [
          [
            684,
            "    public Semester completeRestoreRelease(Long id, long expectedStateVersion) {"
          ],
          [
            685,
            "        lockSemesterStateTransition();"
          ],
          [
            686,
            "        Semester semester = findSemesterForUpdate(id);"
          ],
          [
            687,
            "        if (semester.isActive()"
          ],
          [
            688,
            "                || semester.isArchived()"
          ],
          [
            689,
            "                || semester.getArchiveTransition() != SemesterTransition.NONE) {"
          ],
          [
            690,
            "            throw new ConflictException("
          ],
          [
            691,
            "                    \"status\","
          ],
          [
            692,
            "                    id,"
          ],
          [
            693,
            "                    \"Центральное состояние восстановления изменилось до подтверждения release\");"
          ],
          [
            694,
            "        }"
          ],
          [
            695,
            "        if (semester.getStateVersion() != expectedStateVersion) {"
          ],
          [
            696,
            "            throw staleTransitionVersion(id);"
          ],
          [
            697,
            "        }"
          ],
          [
            698,
            "        if (semester.isReleasePending()) {"
          ],
          [
            699,
            "            semester.setReleasePending(false);"
          ],
          [
            700,
            "            return semesterRepository.saveAndFlush(semester);"
          ],
          [
            701,
            "        }"
          ],
          [
            702,
            "        return semester;"
          ]
        ]
      }
    ]
  },
  {
    "id": "semester-lifecycle-delete",
    "title": "Отличить архив от удаления",
    "description": "Удаление семестра — отдельная операция с предпросмотром, правами SEMESTERS_MANAGE и DATA_DELETE, а также подтверждением в Auth. После принятия необратимого этапа Schedule удаляет записи расписания, Attendance — отметки и заявки, затем Academic удаляет задания и запись семестра. Восстановление относится к сохранённому архиву.",
    "frames": [
      {
        "title": "Предпросмотр требует обоих административных прав",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterDeletionService.java",
        "language": "java",
        "lines": [
          [
            47,
            "    public SemesterDeletionPreviewResponse preview(long semesterId) {"
          ],
          [
            48,
            "        adminAccess.require(\"SEMESTERS_MANAGE\", \"DATA_DELETE\");"
          ],
          [
            49,
            "        return previews.preview(semesterId).response();"
          ],
          [
            50,
            "    }"
          ],
          [
            51,
            ""
          ],
          [
            52,
            "    public SemesterDeletionOperationResponse confirm("
          ],
          [
            53,
            "            long semesterId, DeleteSemesterRequest request, UUID idempotencyKey) {"
          ],
          [
            54,
            "        adminAccess.require(\"SEMESTERS_MANAGE\", \"DATA_DELETE\");"
          ],
          [
            55,
            "        Long actorId = requestContext.getUserId();"
          ],
          [
            56,
            "        if (actorId == null || actorId <= 0) {"
          ],
          [
            57,
            "            throw new AccessDeniedException(\"Для удаления требуется аутентифицированный ADMIN\");"
          ]
        ]
      },
      {
        "title": "Необратимая стадия и подтверждения участников",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/SemesterArchiveCoordinator.java",
        "language": "java",
        "lines": [
          [
            282,
            "        operation = commands.find(operationId);"
          ],
          [
            283,
            "        if (operation.getDeletePhase() == SemesterDeletionPhase.DELETING"
          ],
          [
            284,
            "                && operation.isIrreversibleIntent()) {"
          ],
          [
            285,
            "            if (operation.getSchedule() != SemesterArchiveParticipantStatus.DELETED) {"
          ],
          [
            286,
            "                var response ="
          ],
          [
            287,
            "                        scheduleClient.setSemesterDeletionBarrier("
          ],
          [
            288,
            "                                operationId,"
          ],
          [
            289,
            "                                operation.getSemesterId(),"
          ],
          [
            290,
            "                                operation.getStateVersion(),"
          ],
          [
            291,
            "                                ru.rutcampustrack.academic.contract.enums"
          ],
          [
            292,
            "                                        .SemesterArchiveParticipantCommand.COMMIT_DELETE,"
          ],
          [
            293,
            "                                operation.getScheduleParticipantDigest());"
          ],
          [
            294,
            "                requireSameOperation(operation, response);"
          ],
          [
            307,
            "            operation = commands.find(operationId);"
          ],
          [
            308,
            "            if (operation.getAttendance() != SemesterArchiveParticipantStatus.DELETED) {"
          ],
          [
            309,
            "                commands.enqueueParticipantCommand("
          ],
          [
            310,
            "                        operationId,"
          ],
          [
            311,
            "                        ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand"
          ],
          [
            312,
            "                                .COMMIT_DELETE);"
          ],
          [
            313,
            "            }"
          ],
          [
            314,
            "            operation = commands.find(operationId);"
          ],
          [
            315,
            "            if (operation.getSchedule() == SemesterArchiveParticipantStatus.DELETED"
          ],
          [
            316,
            "                    && operation.getAttendance() == SemesterArchiveParticipantStatus.DELETED) {"
          ],
          [
            317,
            "                commands.completeDelete(operationId);"
          ],
          [
            318,
            "            }"
          ]
        ]
      },
      {
        "title": "Schedule удаляет пары и шаблоны",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/grpc/ScheduleSemesterArchiveBarrierTransaction.java",
        "language": "java",
        "lines": [
          [
            337,
            "        jdbc.update("
          ],
          [
            338,
            "                \"DELETE FROM schedule_assignment_binding_ledger WHERE semester_id = ?\", semesterId);"
          ],
          [
            339,
            "        jdbc.update("
          ],
          [
            340,
            "                \"DELETE FROM schedule_assignment_template_close_ledger WHERE semester_id = ?\","
          ],
          [
            341,
            "                semesterId);"
          ],
          [
            342,
            "        jdbc.update(\"DELETE FROM lesson_room_change_ledger WHERE semester_id = ?\", semesterId);"
          ],
          [
            343,
            "        jdbc.update(\"DELETE FROM lessons WHERE semester_id = ?\", semesterId);"
          ],
          [
            344,
            "        jdbc.update(\"DELETE FROM lesson_occurrences WHERE semester_id = ?\", semesterId);"
          ],
          [
            345,
            "        jdbc.update(\"DELETE FROM schedule_items WHERE semester_id = ?\", semesterId);"
          ],
          [
            346,
            "        jdbc.update(\"DELETE FROM schedule_one_off_lessons WHERE semester_id = ?\", semesterId);"
          ]
        ]
      },
      {
        "title": "Attendance удаляет отметки и заявки в транзакции",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/AttendanceSemesterDeletionData.java",
        "language": "java",
        "lines": [
          [
            97,
            "    public Snapshot delete(long semesterId, String expectedDigest) {"
          ],
          [
            98,
            "        requireSemesterId(semesterId);"
          ],
          [
            99,
            "        if (!TransactionSynchronizationManager.isActualTransactionActive()) {"
          ],
          [
            100,
            "            throw new IllegalStateException("
          ],
          [
            101,
            "                    \"Attendance semester deletion requires an active Mongo transaction\");"
          ],
          [
            102,
            "        }"
          ],
          [
            108,
            "        DomainRows rows = loadRows(semesterId);"
          ],
          [
            109,
            "        Snapshot current = snapshot(rows);"
          ],
          [
            110,
            "        if (!MessageDigest.isEqual("
          ],
          [
            111,
            "                current.participantDigest().getBytes(StandardCharsets.US_ASCII),"
          ],
          [
            112,
            "                expectedDigest.getBytes(StandardCharsets.US_ASCII))) {"
          ],
          [
            113,
            "            throw new DigestMismatchException(current);"
          ],
          [
            114,
            "        }"
          ],
          [
            115,
            ""
          ],
          [
            116,
            "        removeRows(rows.rows(ATTENDANCES), ATTENDANCES);"
          ],
          [
            117,
            "        removeRows(rows.rows(EXCUSE_TICKETS), EXCUSE_TICKETS);"
          ],
          [
            118,
            "        removeRows(rows.rows(LATE_CHECKIN_REQUESTS), LATE_CHECKIN_REQUESTS);"
          ],
          [
            119,
            "        removeRows(rows.rows(REQUEST_ATTACHMENTS), REQUEST_ATTACHMENTS);"
          ],
          [
            120,
            "        removeRows(rows.rows(LATE_CHECKIN_BUDGETS), LATE_CHECKIN_BUDGETS);"
          ]
        ]
      },
      {
        "title": "Academic удаляет задания и запись семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/semester/AcademicSemesterArchiveBarrierTransaction.java",
        "language": "java",
        "lines": [
          [
            313,
            "                \"\"\","
          ],
          [
            314,
            "                semesterId);"
          ],
          [
            315,
            "        jdbc.update(\"DELETE FROM homeworks WHERE semester_id = ?\", semesterId);"
          ],
          [
            316,
            "        jdbc.update(\"DELETE FROM assignments WHERE semester_id = ?\", semesterId);"
          ],
          [
            317,
            "        // New immutable semester-local subjects belong to this already-authorized final cascade."
          ],
          [
            318,
            "        // Nullable legacy identities and every other semester remain untouched. Existing FKs"
          ],
          [
            319,
            "        // cascade only their own threshold/teacher-group rows after the scope check above."
          ],
          [
            320,
            "        jdbc.update("
          ],
          [
            321,
            "                \"DELETE FROM subject_lesson_types t USING subjects s WHERE t.subject_id=s.id AND\""
          ],
          [
            322,
            "                        + \" s.semester_id=?\","
          ],
          [
            323,
            "                semesterId);"
          ],
          [
            324,
            "        jdbc.update(\"DELETE FROM subjects WHERE semester_id = ?\", semesterId);"
          ],
          [
            325,
            "        updateState(operationId, semesterId, stateVersion, \"DELETED\", null);"
          ],
          [
            326,
            "        int deleted = jdbc.update(\"DELETE FROM semesters WHERE id = ?\", semesterId);"
          ],
          [
            327,
            "        requireOne(deleted, \"Academic semester disappeared before final deletion\");"
          ]
        ]
      }
    ]
  }
]
