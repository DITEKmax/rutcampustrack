import type { StoryOperation } from './story-source'

export const homeworkLifecycleOperations: readonly StoryOperation[] = [
  {
    "id": "homework-lifecycle-assign",
    "title": "Выдать задание своей группе",
    "description": "В разделе «ДЗ» староста выбирает дату и нажимает «Выдать ДЗ». Academic разрешает выдачу действующему старосте группы или её активному студенту с назначением помощника и правом manage_homework. Предмет должен принадлежать этой группе, а дата входить в указанный семестр и не быть прошедшей.",
    "frames": [
      {
        "title": "Отправка формы и кнопка выдачи",
        "path": "frontends/mobile-core/src/features/homework/HeadmanHomeworkScreen.vue",
        "language": "typescript",
        "lines": [
          [
            950,
            "  try {"
          ],
          [
            951,
            "    if (item) {"
          ],
          [
            952,
            "      await api.updateHomework(item.id, editIntent.value!);"
          ],
          [
            953,
            "      if (disposed || owner !== ownerRevision) return;"
          ],
          [
            954,
            "    } else {"
          ],
          [
            955,
            "      const result = await api.createHomework("
          ],
          [
            956,
            "        createIntent.value!.input,"
          ],
          [
            957,
            "        createIntent.value!.receipt,"
          ],
          [
            958,
            "      );"
          ],
          [
            1288,
            "        <button"
          ],
          [
            1289,
            "          v-if=\"semester\""
          ],
          [
            1290,
            "          class=\"headman-homework__primary headman-homework__create\""
          ],
          [
            1291,
            "          type=\"button\""
          ],
          [
            1292,
            "          aria-label=\"Выдать ДЗ\""
          ],
          [
            1293,
            "          :disabled=\"!writeAllowed\""
          ],
          [
            1294,
            "          @click=\"openCreate\""
          ],
          [
            1295,
            "        >"
          ],
          [
            1296,
            "          <span aria-hidden=\"true\">＋</span>Выдать ДЗ"
          ]
        ]
      },
      {
        "title": "Староста и помощник проверяются на сервере",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java",
        "language": "java",
        "lines": [
          [
            244,
            "        boolean currentHeadman ="
          ],
          [
            245,
            "                grantRepository.findByUserIdAndRoleAndStatus(actorId, \"headman\", \"active\").stream()"
          ],
          [
            246,
            "                        .anyMatch(grant -> groupId.equals(grant.getGroupId()));"
          ],
          [
            247,
            "        if (currentHeadman) {"
          ],
          [
            248,
            "            return \"HEADMAN\";"
          ],
          [
            249,
            "        }"
          ],
          [
            250,
            ""
          ],
          [
            251,
            "        boolean currentStudent ="
          ],
          [
            252,
            "                grantRepository.findByUserIdAndRoleAndStatus(actorId, \"student\", \"active\").stream()"
          ],
          [
            253,
            "                        .anyMatch(grant -> groupId.equals(grant.getGroupId()));"
          ],
          [
            254,
            "        if (!currentStudent) {"
          ],
          [
            255,
            "            throw new AccessDeniedException(\"Не является старостой или помощником\");"
          ],
          [
            256,
            "        }"
          ],
          [
            257,
            ""
          ],
          [
            258,
            "        var assistant ="
          ],
          [
            259,
            "                assistantRepository"
          ],
          [
            260,
            "                        .findByGroupIdAndStudentIdAndIsActiveTrue(groupId, actorId)"
          ],
          [
            265,
            "        boolean hasPermission ="
          ],
          [
            266,
            "                assistant.getPermissions() != null"
          ],
          [
            267,
            "                        && Arrays.stream(assistant.getPermissions())"
          ],
          [
            268,
            "                                .filter(Objects::nonNull)"
          ],
          [
            269,
            "                                .anyMatch("
          ],
          [
            270,
            "                                        permission ->"
          ],
          [
            271,
            "                                                \"manage_homework\""
          ],
          [
            272,
            "                                                        .equalsIgnoreCase(permission.trim()));"
          ],
          [
            273,
            "        if (!hasPermission) {"
          ],
          [
            274,
            "            throw new AccessDeniedException(\"Отсутствует право MANAGE_HOMEWORK\");"
          ],
          [
            275,
            "        }"
          ],
          [
            276,
            "        return \"ASSISTANT\";"
          ]
        ]
      },
      {
        "title": "Своя группа, предмет и даты семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java",
        "language": "java",
        "lines": [
          [
            294,
            "    public Homework createHomework(CreateHomeworkRequest request) {"
          ],
          [
            295,
            "        // D-06: роль-гard (HEADMAN / assistant c manage_homework)"
          ],
          [
            296,
            "        String actorRole = requireHeadmanOrManageHomework();"
          ],
          [
            297,
            ""
          ],
          [
            298,
            "        if (!Objects.equals(request.groupId(), requestContext.getGroupId())) {"
          ],
          [
            299,
            "            throw new AccessDeniedException(\"ДЗ можно создать только для своей группы\");"
          ],
          [
            300,
            "        }"
          ],
          [
            301,
            "        if (!request.isSelectedOccurrenceValid())"
          ],
          [
            302,
            "            throw new BadRequestException(\"selectedOccurrenceId\", \"Некорректная выбранная пара\");"
          ],
          [
            303,
            "        if (!request.isPlacementValid())"
          ],
          [
            304,
            "            throw new BadRequestException(\"bindingMode\", \"Недопустимая привязка ДЗ\");"
          ],
          [
            336,
            "        if (scopeValidator != null)"
          ],
          [
            337,
            "            scopeValidator.validate("
          ],
          [
            338,
            "                    request.groupId(),"
          ],
          [
            339,
            "                    request.subjectId(),"
          ],
          [
            340,
            "                    request.semesterId(),"
          ],
          [
            341,
            "                    request.lessonDate());"
          ],
          [
            342,
            "        if (request.bindingMode() == HomeworkBindingMode.DATE)"
          ],
          [
            343,
            "            return createDateHomework(request, actorId, requestKey, actorRole);"
          ]
        ]
      },
      {
        "title": "Проверка предмета и границ семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkScopeValidator.java",
        "language": "java",
        "lines": [
          [
            36,
            "    public void validate(long group, long subject, long semester, LocalDate date) {"
          ],
          [
            37,
            "        if (date == null"
          ],
          [
            38,
            "                || date.isBefore(LocalDate.now(clock.withZone(ZoneId.of(\"Europe/Moscow\"))))) {"
          ],
          [
            39,
            "            throw new BadRequestException(\"lessonDate\", \"Дата ДЗ должна быть не в прошлом\");"
          ],
          [
            40,
            "        }"
          ],
          [
            41,
            "        Boolean subjectMatches ="
          ],
          [
            42,
            "                jdbc.queryForObject("
          ],
          [
            43,
            "                        \"SELECT EXISTS (SELECT 1 FROM subjects WHERE id = ? AND group_id = ?)\","
          ],
          [
            44,
            "                        Boolean.class,"
          ],
          [
            45,
            "                        subject,"
          ],
          [
            46,
            "                        group);"
          ],
          [
            47,
            "        if (!Boolean.TRUE.equals(subjectMatches))"
          ],
          [
            48,
            "            throw new BadRequestException(\"subjectId\", \"Предмет не принадлежит группе\");"
          ],
          [
            49,
            "        List<Boolean> semesterMatches ="
          ],
          [
            50,
            "                jdbc.query("
          ],
          [
            51,
            "                        \"SELECT ?::date BETWEEN date_from AND date_to FROM semesters WHERE id = ?\","
          ],
          [
            52,
            "                        (rs, index) -> rs.getBoolean(1),"
          ],
          [
            53,
            "                        date,"
          ],
          [
            54,
            "                        semester);"
          ],
          [
            55,
            "        if (semesterMatches.isEmpty() || !semesterMatches.getFirst()) {"
          ],
          [
            56,
            "            throw new BadRequestException("
          ],
          [
            57,
            "                    \"lessonDate\", \"Дата ДЗ должна находиться в указанном семестре\");"
          ],
          [
            58,
            "        }"
          ]
        ]
      }
    ]
  },
  {
    "id": "homework-lifecycle-bind",
    "title": "Привязать к паре или дате",
    "description": "Для LESSON сервер находит пару по группе, дате и номеру. Сверяет предмет, выбранное занятие и его ревизию, если они переданы. Schedule резервирует привязку по occurrenceId и версии пары. DATE использует календарный день без номера пары и резервируется отдельным вызовом.",
    "frames": [
      {
        "title": "POST /api/academic/homeworks",
        "path": "frontends/mobile-core/src/features/homework/headman-homework-client.ts",
        "language": "typescript",
        "lines": [
          [
            264,
            "    const response = await this.response('/api/academic/homeworks', {"
          ],
          [
            265,
            "      method: 'POST',"
          ],
          [
            266,
            "      body: JSON.stringify({"
          ],
          [
            267,
            "        title: input.title.trim(),"
          ],
          [
            268,
            "        description: optionalText(input.description),"
          ],
          [
            269,
            "        link: optionalText(input.link),"
          ],
          [
            270,
            "        subjectId: input.subjectId,"
          ],
          [
            271,
            "        groupId: input.groupId,"
          ],
          [
            272,
            "        semesterId: input.semesterId,"
          ],
          [
            273,
            "        lessonDate: input.lessonDate,"
          ],
          [
            274,
            "        lessonNumber: input.lessonNumber,"
          ],
          [
            275,
            "        bindingMode: input.bindingMode,"
          ],
          [
            276,
            "        ...placementExtras(input),"
          ],
          [
            277,
            "        requestKey: input.requestKey,"
          ],
          [
            278,
            "      }),"
          ],
          [
            279,
            "    })"
          ],
          [
            525,
            "function placementExtras(input: {"
          ],
          [
            526,
            "  lessonType?: HeadmanSubjectType | null"
          ],
          [
            527,
            "  selectedOccurrenceId?: string"
          ],
          [
            528,
            "  selectedOccurrenceRevision?: string"
          ],
          [
            529,
            "}): Record<string, unknown> {"
          ],
          [
            530,
            "  return {"
          ],
          [
            531,
            "    ...(input.lessonType === undefined ? {} : { lessonType: input.lessonType }),"
          ],
          [
            532,
            "    ...(input.selectedOccurrenceId === undefined"
          ],
          [
            533,
            "      ? {}"
          ],
          [
            534,
            "      : {"
          ],
          [
            535,
            "          selectedOccurrenceId: input.selectedOccurrenceId,"
          ],
          [
            536,
            "          selectedOccurrenceRevision: input.selectedOccurrenceRevision,"
          ],
          [
            537,
            "        }),"
          ],
          [
            538,
            "  }"
          ],
          [
            539,
            "}"
          ]
        ]
      },
      {
        "title": "Запрос на выдачу ДЗ к паре",
        "language": "json",
        "example": [
          "{",
          "  \"title\": \"Задачи по массивам\",",
          "  \"description\": \"Решить задачи 1–3\",",
          "  \"link\": null,",
          "  \"subjectId\": 18,",
          "  \"groupId\": 42,",
          "  \"semesterId\": 3,",
          "  \"lessonDate\": \"2026-11-19\",",
          "  \"lessonNumber\": 2,",
          "  \"bindingMode\": \"LESSON\",",
          "  \"lessonType\": \"PRACTICE\",",
          "  \"selectedOccurrenceId\": \"731\",",
          "  \"selectedOccurrenceRevision\": \"4\",",
          "  \"requestKey\": \"924c1c87-9987-4df0-b6a3-33d66e1ee971\"",
          "}"
        ]
      },
      {
        "title": "Поиск пары и резерв по ревизии",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java",
        "language": "java",
        "lines": [
          [
            345,
            "        // D-04: пара существует + предмет совпадает"
          ],
          [
            346,
            "        LessonResponse lesson ="
          ],
          [
            347,
            "                scheduleGrpcClient"
          ],
          [
            348,
            "                        .resolveLesson("
          ],
          [
            349,
            "                                request.groupId(), request.lessonDate(), request.lessonNumber())"
          ],
          [
            350,
            "                        .orElseThrow("
          ],
          [
            351,
            "                                () ->"
          ],
          [
            352,
            "                                        new BadRequestException("
          ],
          [
            353,
            "                                                \"lessonNumber\","
          ],
          [
            354,
            "                                                \"На эту пару нельзя назначить ДЗ — пары нет в расписании\"));"
          ],
          [
            355,
            "        validateLessonIdentity(request, lesson);"
          ],
          [
            356,
            "        HomeworkPlacementIdentity.selected("
          ],
          [
            357,
            "                lesson, request.selectedOccurrenceId(), request.selectedOccurrenceRevision());"
          ],
          [
            358,
            "        var lessonType = HomeworkPlacementIdentity.lessonType(lesson);"
          ],
          [
            359,
            "        HomeworkPlacementIdentity.requireType(request.lessonType(), lessonType);"
          ],
          [
            361,
            "        byte[] payloadHash = homeworkPayloadHash(request, actorId, lesson);"
          ],
          [
            362,
            "        // A missing client key still gets a replayable identity. Explicit"
          ],
          [
            363,
            "        // keys remain available when the author intentionally wants two"
          ],
          [
            364,
            "        // identical content payloads on the same lesson."
          ],
          [
            365,
            "        HomeworkBindingResponse reservation ="
          ],
          [
            366,
            "                scheduleGrpcClient.reserveHomeworkBinding("
          ],
          [
            367,
            "                        lesson.getOccurrenceId(), requestKey, lesson.getRevision(), payloadHash);"
          ],
          [
            368,
            "        validateBindingResponse(reservation, request, lesson, true);"
          ]
        ]
      },
      {
        "title": "Отдельный резерв для привязки к дате",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java",
        "language": "java",
        "lines": [
          [
            494,
            "    private Homework createDateHomework("
          ],
          [
            495,
            "            CreateHomeworkRequest request, long actor, UUID key, String actorRole) {"
          ],
          [
            496,
            "        byte[] hash = datePayloadHash(request, actor);"
          ],
          [
            497,
            "        HomeworkBindingResponse reservation ="
          ],
          [
            498,
            "                scheduleGrpcClient.reserveDateHomeworkBinding("
          ],
          [
            499,
            "                        request.groupId(),"
          ],
          [
            500,
            "                        request.subjectId(),"
          ],
          [
            501,
            "                        request.semesterId(),"
          ],
          [
            502,
            "                        request.lessonDate(),"
          ],
          [
            503,
            "                        key,"
          ],
          [
            504,
            "                        hash);"
          ],
          [
            505,
            "        validateDateBinding(reservation, request);"
          ],
          [
            506,
            "        Homework saved;"
          ],
          [
            507,
            "        if (reservation.getState() == HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE) {"
          ]
        ]
      }
    ]
  },
  {
    "id": "homework-lifecycle-publish",
    "title": "Опубликовать после подтверждения",
    "description": "Academic сохраняет текст со статусом PENDING и передаёт ID задания в Schedule. Schedule подтверждает привязку, после чего Academic переводит задание в ACTIVE. Если ответ Schedule недоступен после записи, HTTP 202 сообщает о незавершённой публикации. Повтор с тем же ключом продолжает её с прежними ID.",
    "frames": [
      {
        "title": "Academic сохраняет PENDING и запрашивает подтверждение",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java",
        "language": "java",
        "lines": [
          [
            431,
            "            saved ="
          ],
          [
            432,
            "                    publicationPersistence.persistPending("
          ],
          [
            433,
            "                            request.groupId(),"
          ],
          [
            434,
            "                            request.subjectId(),"
          ],
          [
            435,
            "                            request.semesterId(),"
          ],
          [
            436,
            "                            request.title(),"
          ],
          [
            437,
            "                            request.description(),"
          ],
          [
            438,
            "                            request.link(),"
          ],
          [
            439,
            "                            actorId,"
          ],
          [
            440,
            "                            request.lessonDate(),"
          ],
          [
            441,
            "                            request.lessonNumber(),"
          ],
          [
            442,
            "                            reservation.getBindingId(),"
          ],
          [
            443,
            "                            requestKey,"
          ],
          [
            444,
            "                            payloadHash,"
          ],
          [
            445,
            "                            HomeworkBindingMode.LESSON,"
          ],
          [
            455,
            "        HomeworkBindingResponse confirmation;"
          ],
          [
            456,
            "        try {"
          ],
          [
            457,
            "            confirmation ="
          ],
          [
            458,
            "                    scheduleGrpcClient.confirmHomeworkBinding("
          ],
          [
            459,
            "                            reservation.getBindingId(), saved.getId(), requestKey);"
          ],
          [
            460,
            "        } catch (ScheduleServiceUnavailableException e) {"
          ],
          [
            461,
            "            // PENDING content is already committed. The caller receives the"
          ],
          [
            462,
            "            // typed 202 payload and can retry the same key to replay Reserve"
          ],
          [
            463,
            "            // and finish Confirm; a generic 500 would lose that recovery path."
          ],
          [
            464,
            "            throw new HomeworkPublicationPendingException("
          ],
          [
            465,
            "                    saved.getId(), reservation.getBindingId(), requestKey);"
          ],
          [
            466,
            "        }"
          ],
          [
            467,
            "        validateBindingResponse(confirmation, request, lesson, false);"
          ],
          [
            468,
            "        if (confirmation.getState() != HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE"
          ],
          [
            469,
            "                || !confirmation.hasHomeworkId()"
          ],
          [
            470,
            "                || confirmation.getHomeworkId() != saved.getId()) {"
          ],
          [
            471,
            "            throw new ConflictException(\"schedule did not activate the reserved homework binding\");"
          ],
          [
            472,
            "        }"
          ]
        ]
      },
      {
        "title": "Schedule связывает резерв с ID задания",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/homework/HomeworkBindingService.java",
        "language": "java",
        "lines": [
          [
            213,
            "        if (\"ACTIVE\".equals(binding.state())) {"
          ],
          [
            214,
            "            if (Objects.equals(binding.homeworkId(), homeworkId)) {"
          ],
          [
            215,
            "                return toResponse(binding, current);"
          ],
          [
            216,
            "            }"
          ],
          [
            217,
            "            throw new ConflictException(\"active homework binding cannot be replaced\");"
          ],
          [
            218,
            "        }"
          ],
          [
            219,
            "        if (!\"PENDING\".equals(binding.state()) || binding.homeworkId() != null) {"
          ],
          [
            220,
            "            throw new ConflictException(\"homework binding is not confirmable\");"
          ],
          [
            221,
            "        }"
          ],
          [
            226,
            "        int updated ="
          ],
          [
            227,
            "                jdbcTemplate.update("
          ],
          [
            228,
            "                        \"\"\""
          ],
          [
            229,
            "                UPDATE lesson_homework_bindings"
          ],
          [
            230,
            "                   SET homework_id = ?, state = 'ACTIVE', revision = revision + 1,"
          ],
          [
            231,
            "                       updated_at = NOW()"
          ],
          [
            232,
            "                 WHERE binding_id = ?"
          ],
          [
            233,
            "                   AND revision = ?"
          ],
          [
            234,
            "                   AND state = 'PENDING'"
          ],
          [
            235,
            "                   AND homework_id IS NULL"
          ],
          [
            236,
            "                \"\"\","
          ],
          [
            237,
            "                        homeworkId,"
          ],
          [
            238,
            "                        bindingId,"
          ],
          [
            239,
            "                        binding.revision());"
          ],
          [
            240,
            "        if (updated != 1) {"
          ],
          [
            241,
            "            throw new ConflictException(\"homework binding revision changed before confirmation\");"
          ]
        ]
      },
      {
        "title": "Academic активирует задание и формирует событие",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkPublicationPersistence.java",
        "language": "java",
        "lines": [
          [
            324,
            "        if (homework.getPublicationState() == HomeworkPublicationState.ARCHIVED) {"
          ],
          [
            325,
            "            throw new ConflictException(\"archived homework cannot be activated\");"
          ],
          [
            326,
            "        }"
          ],
          [
            327,
            "        if (homework.getPublicationState() == HomeworkPublicationState.PENDING) {"
          ],
          [
            328,
            "            HomeworkPlacementIdentity.accept(homework, acceptedBinding);"
          ],
          [
            329,
            "            homework.activatePublication();"
          ],
          [
            330,
            "            homework = homeworkRepository.save(homework);"
          ],
          [
            331,
            "            homeworkRepository.flush();"
          ],
          [
            332,
            "            eventPublisher.publishEvent("
          ],
          [
            333,
            "                    new HomeworkPublishedEvent(this, homework, homework.getActorId()));"
          ]
        ]
      },
      {
        "title": "HTTP 202 и типизированный ответ",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/exception/GlobalExceptionHandler.java",
        "language": "java",
        "lines": [
          [
            185,
            "    @ExceptionHandler(HomeworkPublicationPendingException.class)"
          ],
          [
            186,
            "    public ResponseEntity<HomeworkPublicationPendingResponse> handleHomeworkPublicationPending("
          ],
          [
            187,
            "            HomeworkPublicationPendingException ex) {"
          ],
          [
            188,
            "        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ex.getResponse());"
          ],
          [
            189,
            "    }"
          ]
        ]
      },
      {
        "title": "HTTP 202 · публикация ожидает подтверждения",
        "language": "json",
        "example": [
          "{",
          "  \"homeworkId\": \"284\",",
          "  \"bindingId\": \"619\",",
          "  \"requestKey\": \"924c1c87-9987-4df0-b6a3-33d66e1ee971\",",
          "  \"state\": \"PENDING\"",
          "}"
        ]
      },
      {
        "title": "Форма сохраняет подтверждение для повтора",
        "path": "frontends/mobile-core/src/features/homework/HeadmanHomeworkScreen.vue",
        "language": "typescript",
        "lines": [
          [
            955,
            "      const result = await api.createHomework("
          ],
          [
            956,
            "        createIntent.value!.input,"
          ],
          [
            957,
            "        createIntent.value!.receipt,"
          ],
          [
            958,
            "      );"
          ],
          [
            959,
            "      if (disposed || owner !== ownerRevision) return;"
          ],
          [
            960,
            "      if (result.state === 'PENDING') {"
          ],
          [
            961,
            "        createEverUncertain.value = true;"
          ],
          [
            962,
            "        createIntent.value = withAssistantHomeworkPublicationReceipt("
          ],
          [
            963,
            "          createIntent.value!,"
          ],
          [
            964,
            "          result.receipt,"
          ],
          [
            965,
            "        );"
          ],
          [
            966,
            "        notice.value = 'Публикация выполняется. Повтор проверит результат без нового задания.';"
          ],
          [
            967,
            "        return;"
          ],
          [
            968,
            "      }"
          ],
          [
            969,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "homework-lifecycle-edit",
    "title": "Изменить задание с проверкой версии",
    "description": "Клиент создаёт requestKey и передаёт в PUT expectedRevision из открытой карточки. Academic заново проверяет права, группу и состояние, затем сверяет версию под блокировкой. Принятый повтор читается из истории. Устаревшая версия даёт конфликт. Для смены места привязки Academic фиксирует операцию, а затем применяет подтверждение от Schedule.",
    "frames": [
      {
        "title": "Форма фиксирует ревизию и ключ команды",
        "path": "frontends/mobile-core/src/features/homework/HeadmanHomeworkScreen.vue",
        "language": "typescript",
        "lines": [
          [
            910,
            "  if (item && item.revision === null) return;"
          ],
          [
            911,
            "  const values = { title: title.value, description: description.value, link: link.value };"
          ],
          [
            912,
            "  if (item) {"
          ],
          [
            913,
            "    if (!editIntent.value)"
          ],
          [
            914,
            "      editIntent.value = Object.freeze({"
          ],
          [
            915,
            "        ...values,"
          ],
          [
            916,
            "        requestKey: crypto.randomUUID(),"
          ],
          [
            917,
            "        expectedRevision: item.revision!,"
          ],
          [
            918,
            "        ...(originalPlacementUnchanged.value"
          ],
          [
            919,
            "          ? {}"
          ],
          [
            920,
            "          : {"
          ],
          [
            921,
            "              bindingMode: context.bindingMode,"
          ],
          [
            922,
            "              lessonDate: context.lessonDate,"
          ],
          [
            923,
            "              lessonNumber: context.lessonNumber,"
          ],
          [
            924,
            "              lessonType: context.lessonType,"
          ],
          [
            925,
            "              ...(context.selectedOccurrenceId === undefined"
          ],
          [
            926,
            "                ? {}"
          ],
          [
            927,
            "                : {"
          ],
          [
            928,
            "                    selectedOccurrenceId: context.selectedOccurrenceId,"
          ]
        ]
      },
      {
        "title": "PUT /api/academic/homeworks/{id}",
        "path": "frontends/mobile-core/src/features/homework/headman-homework-client.ts",
        "language": "typescript",
        "lines": [
          [
            336,
            "      await this.request<unknown>(`/api/academic/homeworks/${id}`, {"
          ],
          [
            337,
            "        method: 'PUT',"
          ],
          [
            338,
            "        body: JSON.stringify({"
          ],
          [
            339,
            "          title: input.title.trim(),"
          ],
          [
            340,
            "          description: optionalText(input.description),"
          ],
          [
            341,
            "          link: optionalText(input.link),"
          ],
          [
            342,
            "          requestKey: input.requestKey,"
          ],
          [
            343,
            "          expectedRevision: input.expectedRevision,"
          ],
          [
            344,
            "          ...(input.bindingMode === undefined"
          ],
          [
            345,
            "            ? {}"
          ],
          [
            346,
            "            : {"
          ],
          [
            347,
            "                bindingMode: input.bindingMode,"
          ],
          [
            348,
            "                lessonDate: input.lessonDate,"
          ],
          [
            349,
            "                lessonNumber: input.lessonNumber,"
          ],
          [
            350,
            "              }),"
          ],
          [
            351,
            "          ...placementExtras(input),"
          ],
          [
            352,
            "        }),"
          ]
        ]
      },
      {
        "title": "Актуальные права и состояние после блокировки",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkEditPersistence.java",
        "language": "java",
        "lines": [
          [
            411,
            "    private AuthorizedHomework authorizedHomework(long id) {"
          ],
          [
            412,
            "        HomeworkManagementAuthorization.require(context, grants, assistants);"
          ],
          [
            413,
            "        Homework current ="
          ],
          [
            414,
            "                homeworks"
          ],
          [
            415,
            "                        .findById(id)"
          ],
          [
            416,
            "                        .orElseThrow(() -> new ResourceNotFoundException(\"Homework\", \"id\", id));"
          ],
          [
            417,
            "        if (!Objects.equals(current.getGroupId(), context.getGroupId()))"
          ],
          [
            418,
            "            throw new AccessDeniedException(\"ДЗ принадлежит другой группе\");"
          ],
          [
            419,
            "        locks.lockAndRefresh(current);"
          ],
          [
            420,
            "        String actorRole = HomeworkManagementAuthorization.require(context, grants, assistants);"
          ],
          [
            421,
            "        if (current.getPublicationState() != HomeworkPublicationState.ACTIVE"
          ],
          [
            422,
            "                || isExpired(current)) {"
          ],
          [
            423,
            "            throw new ConflictException("
          ],
          [
            424,
            "                    \"Архивное или ожидающее публикации ДЗ недоступно для изменения\");"
          ],
          [
            425,
            "        }"
          ],
          [
            426,
            "        return new AuthorizedHomework(current, actorRole);"
          ]
        ]
      },
      {
        "title": "Повтор, проверка версии и сохранение изменения",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkEditPersistence.java",
        "language": "java",
        "lines": [
          [
            134,
            "        AuthorizedHomework authorized = authorizedHomework(id);"
          ],
          [
            135,
            "        Homework current = authorized.homework();"
          ],
          [
            136,
            "        byte[] hash = history.commandHash(request);"
          ],
          [
            137,
            "        Homework replay = history.replay(current, context.getUserId(), request.requestKey(), hash);"
          ],
          [
            138,
            "        if (replay != null) return replay;"
          ],
          [
            139,
            "        rejectOtherPending(current.getId(), null);"
          ],
          [
            140,
            "        if (current.getRevision() != request.expectedRevision()) throw stale();"
          ],
          [
            156,
            "        boolean changed = !before.equals(after);"
          ],
          [
            157,
            "        current.captureLegacyAcceptedReplay();"
          ],
          [
            158,
            "        OffsetDateTime now = OffsetDateTime.now(clock);"
          ],
          [
            159,
            "        if (changed) current.applyEdit(after, now);"
          ],
          [
            160,
            "        history.record("
          ],
          [
            161,
            "                current, context.getUserId(), request.requestKey(), hash, before, now, changed);"
          ],
          [
            162,
            "        homeworks.saveAndFlush(current);"
          ],
          [
            163,
            "        if (changed) publish(current, context.getUserId());"
          ]
        ]
      },
      {
        "title": "Смена привязки через Schedule",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkEditCoordinator.java",
        "language": "java",
        "lines": [
          [
            132,
            "        if (!placementChanged && request.selectedOccurrenceId() == null)"
          ],
          [
            133,
            "            return persistence.content(id, request, type);"
          ],
          [
            134,
            "        var binding = schedule.getHomeworkBinding(current.getBindingId());"
          ],
          [
            135,
            "        return moveAndFinish("
          ],
          [
            136,
            "                persistence.prepare(id, request, desired, binding, occurrence, lessonRevision));"
          ],
          [
            137,
            "    }"
          ],
          [
            138,
            ""
          ],
          [
            139,
            "    private Homework moveAndFinish(HomeworkEditOperation operation) {"
          ],
          [
            140,
            "        HomeworkEditReceipt accepted;"
          ],
          [
            141,
            "        try {"
          ],
          [
            142,
            "            accepted = schedule.moveHomeworkBinding(operation.move());"
          ],
          [
            143,
            "        } catch (ScheduleServiceUnavailableException uncertain) {"
          ],
          [
            144,
            "            throw uncertain;"
          ],
          [
            157,
            "        Homework result = persistence.finalizeReceipt(operation, accepted);"
          ],
          [
            158,
            "        if (\"NOT_ACCEPTED\".equals(accepted.getState()))"
          ],
          [
            159,
            "            throw new ConflictException(\"Изменение не было принято; используй новый requestKey\");"
          ],
          [
            160,
            "        acknowledge(operation);"
          ],
          [
            161,
            "        requireApplied(operation);"
          ],
          [
            162,
            "        return result;"
          ]
        ]
      }
    ]
  },
  {
    "id": "homework-lifecycle-student",
    "title": "Прочитать и отметить выполнение",
    "description": "В PWA и Telegram Mini App студенческий экран запрашивает GET /api/v1/student/homework. Mobile BFF выбирает текущую группу и диапазон внутри активного семестра. Academic читает опубликованные задания и добавляет состояние выполнения. Отметку «Выполнено» студент сохраняет для себя. Для архивных заданий отметка выполнения недоступна.",
    "frames": [
      {
        "title": "Клиент читает ДЗ и сохраняет выполнение",
        "path": "frontends/mobile-core/src/api/student-client.ts",
        "language": "typescript",
        "lines": [
          [
            208,
            "    const params = new URLSearchParams()"
          ],
          [
            209,
            "    if (from) params.set('from', from)"
          ],
          [
            210,
            "    if (to) params.set('to', to)"
          ],
          [
            211,
            "    const query = params.toString()"
          ],
          [
            212,
            "    return this.request(`/api/v1/student/homework${query ? `?${query}` : ''}`)"
          ],
          [
            213,
            "  }"
          ],
          [
            214,
            ""
          ],
          [
            215,
            "  setHomeworkCompletion("
          ],
          [
            216,
            "    id: string,"
          ],
          [
            217,
            "    command: StudentHomeworkCompletionCommand,"
          ],
          [
            218,
            "  ): Promise<StudentHomeworkCompletion> {"
          ],
          [
            219,
            "    return this.request(`/api/v1/student/homework/${encodeURIComponent(id)}/completion`, {"
          ],
          [
            220,
            "      method: 'PUT',"
          ],
          [
            221,
            "      body: JSON.stringify(command),"
          ],
          [
            222,
            "    })"
          ],
          [
            223,
            "  }"
          ],
          [
            224,
            ""
          ]
        ]
      },
      {
        "title": "Mobile BFF берёт активный семестр и текущую группу",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java",
        "language": "java",
        "lines": [
          [
            258,
            "    public HomeworkResponse homework(String fromRaw, String toRaw) {"
          ],
          [
            259,
            "        InternalJwtClaims claims = requireStudent(true);"
          ],
          [
            260,
            "        SemesterResponse active = academic.activeSemesterForHomework();"
          ],
          [
            261,
            "        LocalDate activeFrom = LocalDate.parse(active.getDateFrom());"
          ],
          [
            262,
            "        LocalDate activeTo = LocalDate.parse(active.getDateTo());"
          ],
          [
            263,
            "        Instant serverNow = clock.instant();"
          ],
          [
            264,
            "        LocalDate today = LocalDate.ofInstant(serverNow, MOSCOW);"
          ],
          [
            265,
            ""
          ],
          [
            266,
            "        LocalDate from ="
          ],
          [
            267,
            "                fromRaw == null"
          ],
          [
            268,
            "                        ? clamp(today, activeFrom, activeTo)"
          ],
          [
            269,
            "                        : parseHomeworkDate(fromRaw, \"from\");"
          ],
          [
            270,
            "        LocalDate to = toRaw == null ? activeTo : parseHomeworkDate(toRaw, \"to\");"
          ],
          [
            271,
            "        if (from.isBefore(activeFrom) || to.isAfter(activeTo) || from.isAfter(to)) {"
          ],
          [
            272,
            "            throw new MobileBffException("
          ],
          [
            273,
            "                    HttpStatus.BAD_REQUEST,"
          ],
          [
            274,
            "                    ProblemCode.INVALID_REQUEST,"
          ],
          [
            275,
            "                    \"Диапазон ДЗ должен входить в активный семестр\");"
          ],
          [
            276,
            "        }"
          ],
          [
            283,
            "            response ="
          ],
          [
            284,
            "                    academic.homeworks("
          ],
          [
            285,
            "                            claims.groupId(),"
          ],
          [
            286,
            "                            active.getId(),"
          ],
          [
            287,
            "                            claims.userId(),"
          ],
          [
            288,
            "                            from.toString(),"
          ],
          [
            289,
            "                            to.toString(),"
          ],
          [
            290,
            "                            true,"
          ],
          [
            291,
            "                            completedTodayFrom.toInstant().toString(),"
          ],
          [
            292,
            "                            completedTodayTo.toInstant().toString());"
          ]
        ]
      },
      {
        "title": "Academic выбирает опубликованные задания",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/AcademicGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            1742,
            "        Map<Long, Homework> homeworksById = new LinkedHashMap<>();"
          ],
          [
            1743,
            "        homeworkRepository"
          ],
          [
            1744,
            "                .findByGroupIdAndSemesterIdAndPublicationStateAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc("
          ],
          [
            1745,
            "                        request.getGroupId(),"
          ],
          [
            1746,
            "                        request.getSemesterId(),"
          ],
          [
            1747,
            "                        HomeworkPublicationState.ACTIVE,"
          ],
          [
            1748,
            "                        from,"
          ],
          [
            1749,
            "                        to)"
          ],
          [
            1750,
            "                .forEach(homework -> homeworksById.put(homework.getId(), homework));"
          ]
        ]
      },
      {
        "title": "Выполнение хранится по заданию и студенту",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkStudentService.java",
        "language": "java",
        "lines": [
          [
            98,
            "        if (!studentGroupId.equals(homework.getGroupId())) {"
          ],
          [
            99,
            "            throw new AccessDeniedException(\"ДЗ принадлежит другой группе\");"
          ],
          [
            100,
            "        }"
          ],
          [
            101,
            "        if (!active.getId().equals(homework.getSemesterId())) {"
          ],
          [
            102,
            "            throw new AccessDeniedException(\"ДЗ принадлежит неактивному семестру\");"
          ],
          [
            103,
            "        }"
          ],
          [
            104,
            "        if (homework.getPublicationState() != HomeworkPublicationState.ACTIVE"
          ],
          [
            105,
            "                || lifecycle.archived(homework)) {"
          ],
          [
            106,
            "            throw new ResourceNotFoundException(\"Homework\", \"id\", homeworkId);"
          ],
          [
            107,
            "        }"
          ],
          [
            108,
            ""
          ],
          [
            109,
            "        if (completed) {"
          ],
          [
            110,
            "            completionRepository.insertIfAbsent(homework.getId(), studentId);"
          ],
          [
            111,
            "            HomeworkCompletion completion ="
          ],
          [
            112,
            "                    completionRepository"
          ],
          [
            113,
            "                            .findByHomeworkIdAndStudentId(homework.getId(), studentId)"
          ],
          [
            118,
            "            return new CompletionState(true, completion.getCompletedAt());"
          ],
          [
            119,
            "        } else {"
          ],
          [
            120,
            "            completionRepository.deleteByHomeworkIdAndStudentId(homework.getId(), studentId);"
          ],
          [
            121,
            "            return new CompletionState(false, null);"
          ],
          [
            122,
            "        }"
          ]
        ]
      },
      {
        "title": "Архивное задание нельзя отметить выполненным",
        "path": "frontends/mobile-core/src/features/homework/HomeworkScreen.vue",
        "language": "typescript",
        "lines": [
          [
            281,
            "function completionLabel(item: StudentHomeworkItem): string {"
          ],
          [
            282,
            "  if (itemPending(item.id)) return 'Сохраняем…';"
          ],
          [
            283,
            "  if (item.archived) return item.completed ? 'Выполнено · архив' : 'Архив · только чтение';"
          ],
          [
            284,
            "  if (item.completed) return 'Выполнено';"
          ],
          [
            285,
            "  if (isReadOnly.value) return 'Только просмотр';"
          ],
          [
            286,
            "  return 'Проведи, чтобы выполнить';"
          ],
          [
            287,
            "}"
          ],
          [
            288,
            ""
          ],
          [
            289,
            "function requestCompletion(item: StudentHomeworkItem, desired: boolean): void {"
          ],
          [
            290,
            "  if (isReadOnly.value || item.archived || itemPending(item.id)) return;"
          ],
          [
            291,
            "  rememberCompletionFocus(item, desired);"
          ],
          [
            292,
            "  emit('complete', item, desired);"
          ],
          [
            293,
            "}"
          ]
        ]
      }
    ]
  },
  {
    "id": "homework-lifecycle-notify",
    "title": "Получить событие о публикации",
    "description": "Публикация создаёт homework.published, а сохранённое изменение создаёт homework.updated. Academic записывает событие в outbox до коммита. Notification определяет аудиторию по данным группы и сохраняет ленту. Web Push зависит от настроек и подписки, а Telegram-бот ставит сообщение в очередь только для привязанных аккаунтов с последующей проверкой доставки. Открытый экран ДЗ перечитывает список при сигнале обновления.",
    "frames": [
      {
        "title": "Событие записывается в outbox в транзакции",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/DomainEventListener.java",
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
            "        // M04 QA3 — auto-fill envelope (trace_id из MDC, event_version из @EventVersion,"
          ],
          [
            46,
            "        // occurred_at = now, source = \"academic-service\") перед сериализацией."
          ],
          [
            47,
            "        fillDefaults(event);"
          ],
          [
            48,
            "        String payload;"
          ],
          [
            49,
            "        try {"
          ],
          [
            50,
            "            payload = objectMapper.writeValueAsString(event);"
          ],
          [
            58,
            "        }"
          ],
          [
            59,
            "        outboxStorage.save(event.getEventType(), payload);"
          ],
          [
            60,
            "        log.debug(\"Outbox saved: type={}, id={}\", event.getEventType(), event.getEventId());"
          ]
        ]
      },
      {
        "title": "Аудитория включает активных участников группы",
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
          ]
        ]
      },
      {
        "title": "Лента сохраняется, push проверяет настройки",
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
            119,
            "                    Long actor ="
          ],
          [
            120,
            "                            payload.get(\"actor_user_id\") == null"
          ],
          [
            121,
            "                                    ? null"
          ],
          [
            122,
            "                                    : NotificationAudienceClient.positive("
          ],
          [
            123,
            "                                            payload.get(\"actor_user_id\"));"
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
          ],
          [
            145,
            "                });"
          ],
          [
            146,
            "    }"
          ]
        ]
      },
      {
        "title": "Очередь сообщений Telegram для привязанных аккаунтов",
        "path": "services/notification-bot/bot/notifications/homework.py",
        "language": "python",
        "lines": [
          [
            73,
            "    options = message_options(event, config=kwargs.get(\"config\"), facts={\"subject_name\": subject_name})"
          ],
          [
            74,
            "    members = await academic_client.get_current_group_members(group_id)"
          ],
          [
            75,
            "    for student in members:"
          ],
          [
            76,
            "        if not student.telegram_id:"
          ],
          [
            77,
            "            continue"
          ],
          [
            78,
            ""
          ],
          [
            79,
            "        await send_queue.put("
          ],
          [
            80,
            "            SendTask("
          ],
          [
            81,
            "                coroutine_factory=lambda s=student: bot.send_message(chat_id=s.telegram_id, **options),"
          ],
          [
            82,
            "                user_id=student.user_id,"
          ],
          [
            83,
            "                chat_id=student.telegram_id,"
          ],
          [
            84,
            "                category=\"homework\","
          ],
          [
            85,
            "                notification_type=type_for_event(event),"
          ],
          [
            86,
            "                audience_check=group_audience(academic_client, group_id, student.user_id, student.telegram_id),"
          ],
          [
            87,
            "            )"
          ]
        ]
      },
      {
        "title": "Открытый экран запрашивает свежий список",
        "path": "frontends/mobile-core/src/shared/components/StudentFeatureOwner.vue",
        "language": "typescript",
        "lines": [
          [
            2312,
            "useProjectionRefresh("
          ],
          [
            2313,
            "  async () => {"
          ],
          [
            2314,
            "    // Reads preserve the mounted form, request draft, and pending/unknown command owners."
          ],
          [
            2315,
            "    const id = route.value.id;"
          ],
          [
            2316,
            "    if (id === 'today') await today.refresh();"
          ],
          [
            2317,
            "    else if (id === 'homework') await homework.query.refetch();"
          ],
          [
            2318,
            "    else if (id.startsWith('more/statistics')) await statistics.query.refetch();"
          ],
          [
            2319,
            "  },"
          ],
          [
            2320,
            "  {"
          ],
          [
            2321,
            "    revision: () => props.realtimeRevision,"
          ],
          [
            2322,
            "    blocked: projectionRefreshBlocked,"
          ],
          [
            2323,
            "  },"
          ],
          [
            2324,
            ");"
          ]
        ]
      }
    ]
  },
  {
    "id": "homework-lifecycle-transfer",
    "title": "Сохранить задание при переносе пары",
    "description": "Schedule меняет текущую пару в привязке LESSON и увеличивает её ревизию. Academic получает пакет lesson.transfer.requested и применяет его в транзакции. У задания остаётся прежний ID, меняются дата, номер пары и ревизия привязки. Для ACTIVE публикуется homework.updated, если изменились дата или номер. Задание DATE привязано к календарному дню.",
    "frames": [
      {
        "title": "Schedule меняет текущую пару в привязке",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonTransferWriter.java",
        "language": "java",
        "lines": [
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
        "title": "Academic обрабатывает пакет переноса",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/event/LessonTransferEventConsumer.java",
        "language": "java",
        "lines": [
          [
            46,
            "    @RabbitListener(queues = RabbitConfig.LESSON_TRANSFER_EVENTS_QUEUE)"
          ],
          [
            47,
            "    @EventIdempotent(consumer = CONSUMER_ID)"
          ],
          [
            48,
            "    @Transactional"
          ],
          [
            49,
            "    public void onEvent(Map<String, Object> envelope) {"
          ],
          [
            50,
            "        if (envelope == null || !EVENT_TYPE.equals(envelope.get(\"event_type\"))) return;"
          ],
          [
            51,
            "        LessonTransferBatch batch = parseTransferBatch(envelope);"
          ],
          [
            52,
            "        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) return;"
          ],
          [
            53,
            "        withTraceContext(envelope, () -> transferCoordinator.applyBatch(batch));"
          ],
          [
            54,
            "    }"
          ]
        ]
      },
      {
        "title": "Прежний ID задания, новый слот и событие",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkBindingTransferCoordinator.java",
        "language": "java",
        "lines": [
          [
            375,
            "            var actionBefore = HomeworkActionRecordedEvent.snapshot(homework);"
          ],
          [
            376,
            "            boolean slotChanged ="
          ],
          [
            377,
            "                    !batch.targetDate().equals(homework.getLessonDate())"
          ],
          [
            378,
            "                            || batch.targetLessonNumber() != homework.getLessonNumber();"
          ],
          [
            379,
            "            homework.transferLessonSlot(batch.targetDate(), batch.targetLessonNumber());"
          ],
          [
            380,
            "            homework.acceptPlacementIdentity("
          ],
          [
            381,
            "                    batch.occurrenceId(), batch.targetLessonId(), binding.revision() + 1);"
          ],
          [
            382,
            "            homeworkRepository.save(homework);"
          ],
          [
            383,
            "            homeworkRepository.flush();"
          ],
          [
            384,
            "            upsertMarker(batch, binding, homework.getId(), \"APPLIED\");"
          ],
          [
            385,
            "            if (homework.getPublicationState() == HomeworkPublicationState.ACTIVE) {"
          ],
          [
            394,
            "            if (slotChanged && homework.getPublicationState() == HomeworkPublicationState.ACTIVE) {"
          ],
          [
            395,
            "                editHistory.append("
          ],
          [
            396,
            "                        homework,"
          ],
          [
            397,
            "                        batch.actorId(),"
          ],
          [
            398,
            "                        before,"
          ],
          [
            399,
            "                        java.time.OffsetDateTime.now(),"
          ],
          [
            400,
            "                        \"LESSON_TRANSFERRED\");"
          ],
          [
            401,
            "                eventPublisher.publishEvent("
          ],
          [
            402,
            "                        new HomeworkUpdatedEvent(this, homework, batch.actorId()));"
          ],
          [
            403,
            "            }"
          ],
          [
            404,
            "            resultState = \"MOVED\";"
          ]
        ]
      }
    ]
  },
  {
    "id": "homework-lifecycle-archive",
    "title": "Открыть архив заданий",
    "description": "«Открыть архив» запрашивает задания выбранной группы и семестра с archived=true. В выборку попадают задания со статусом ARCHIVED и задания DATE с датой раньше сегодняшней по Москве. Для DATE чтение уже учитывает срок, даже если фоновая архивация ещё не прошла. При отмене пары Schedule архивирует её привязки LESSON. Само окончание пары не переводит LESSON в архив.",
    "frames": [
      {
        "title": "Староста открывает архив",
        "path": "frontends/mobile-core/src/features/homework/HeadmanHomeworkScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1208,
            "        <button"
          ],
          [
            1209,
            "          class=\"headman-homework__secondary\""
          ],
          [
            1210,
            "          type=\"button\""
          ],
          [
            1211,
            "          :aria-pressed=\"readIntent === 'archive'\""
          ],
          [
            1212,
            "          @click=\"selectReadIntent('archive')\""
          ],
          [
            1213,
            "        >"
          ],
          [
            1214,
            "          Открыть архив"
          ],
          [
            1215,
            "        </button>"
          ]
        ]
      },
      {
        "title": "Клиент запрашивает archived=true",
        "path": "frontends/mobile-core/src/features/homework/headman-homework-client.ts",
        "language": "typescript",
        "lines": [
          [
            127,
            "  listHomeworks("
          ],
          [
            128,
            "    groupId: number,"
          ],
          [
            129,
            "    semesterId: number,"
          ],
          [
            130,
            "    archived = false,"
          ],
          [
            131,
            "  ): Promise<readonly HeadmanManagedHomework[]> {"
          ],
          [
            132,
            "    assertPositiveInteger(groupId, 'groupId')"
          ],
          [
            133,
            "    assertPositiveInteger(semesterId, 'semesterId')"
          ],
          [
            134,
            "    return this.requestPaged("
          ],
          [
            135,
            "      (page) =>"
          ],
          [
            136,
            "        `/api/academic/homeworks?groupId=${groupId}&semesterId=${semesterId}&page=${page}&size=100${archived ? '&archived=true' : ''}`,"
          ],
          [
            137,
            "      (value) =>"
          ],
          [
            138,
            "        embeddedItems(value, ['homeworkResponseList']).map(normalizeHomework).filter(isValue),"
          ],
          [
            139,
            "      true,"
          ],
          [
            140,
            "    )"
          ],
          [
            141,
            "  }"
          ]
        ]
      },
      {
        "title": "Academic выбирает архив и прошедшие DATE",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java",
        "language": "java",
        "lines": [
          [
            878,
            "    private void requireArchiveRead(Long groupId) {"
          ],
          [
            879,
            "        if (!Objects.equals(groupId, requestContext.getGroupId()))"
          ],
          [
            880,
            "            throw new AccessDeniedException(\"ДЗ принадлежит другой группе\");"
          ],
          [
            881,
            "        requireHeadmanOrManageHomework();"
          ],
          [
            882,
            "    }"
          ],
          [
            883,
            ""
          ],
          [
            884,
            "    @Transactional(readOnly = true)"
          ],
          [
            885,
            "    public Page<Homework> listHomeworks("
          ],
          [
            886,
            "            Long groupId, Long semesterId, Pageable pageable, boolean archived) {"
          ],
          [
            887,
            "        if (archived) {"
          ],
          [
            888,
            "            requireArchiveRead(groupId);"
          ],
          [
            889,
            "            return homeworkRepository.findArchive("
          ],
          [
            890,
            "                    groupId,"
          ],
          [
            891,
            "                    semesterId,"
          ],
          [
            892,
            "                    LocalDate.now("
          ],
          [
            893,
            "                            instructionalClock.withZone(java.time.ZoneId.of(\"Europe/Moscow\"))),"
          ],
          [
            894,
            "                    org.springframework.data.domain.PageRequest.of("
          ],
          [
            895,
            "                            pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100)));"
          ]
        ]
      },
      {
        "title": "Условие архивной выборки",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/repository/HomeworkRepository.java",
        "language": "java",
        "lines": [
          [
            14,
            "    @org.springframework.data.jpa.repository.Query("
          ],
          [
            15,
            "            \"\"\""
          ],
          [
            16,
            "        SELECT h FROM Homework h WHERE h.groupId = :groupId AND h.semesterId = :semesterId"
          ],
          [
            17,
            "        AND (h.publicationState = ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState.ARCHIVED"
          ],
          [
            18,
            "          OR (h.publicationState = ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState.ACTIVE"
          ],
          [
            19,
            "              AND h.bindingMode = ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode.DATE AND h.lessonDate < :today))"
          ],
          [
            20,
            "        ORDER BY h.lessonDate DESC, h.id DESC"
          ],
          [
            21,
            "        \"\"\")"
          ],
          [
            22,
            "    Page<Homework> findArchive(Long groupId, Long semesterId, LocalDate today, Pageable pageable);"
          ]
        ]
      },
      {
        "title": "Schedule сохраняет архив для просроченных DATE",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/homework/HomeworkDateDeadlineJob.java",
        "language": "java",
        "lines": [
          [
            18,
            "    @Scheduled(fixedDelay = 30_000)"
          ],
          [
            19,
            "    @SchedulerLock(name = \"homework-date-deadline\", lockAtMostFor = \"PT2M\", lockAtLeastFor = \"PT5S\")"
          ],
          [
            20,
            "    public void archiveDueDates() {"
          ],
          [
            21,
            "        placement.archiveExpiredDates();"
          ],
          [
            22,
            "    }"
          ]
        ]
      },
      {
        "title": "Schedule архивирует привязки отменённой пары",
        "path": "services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/RecurringLessonLifecycleWriter.java",
        "language": "java",
        "lines": [
          [
            112,
            "                    reason,"
          ],
          [
            113,
            "                    actorId,"
          ],
          [
            114,
            "                    now,"
          ],
          [
            115,
            "                    null);"
          ],
          [
            116,
            "            archiveOccurrenceBindings("
          ],
          [
            117,
            "                    current.occurrence().id(), lessonId, current.occurrence().semesterId());"
          ],
          [
            118,
            "            ScheduleActionRecordedEvent.publish("
          ],
          [
            119,
            "                    jdbc, eventPublisher, \"LESSON_CANCELLED\", lessonId, actorId, actionBefore, now);"
          ],
          [
            534,
            "        for (BindingSnapshot binding : bindings) {"
          ],
          [
            535,
            "            int updated ="
          ],
          [
            536,
            "                    jdbc.update("
          ],
          [
            537,
            "                            \"\"\""
          ],
          [
            538,
            "                    UPDATE lesson_homework_bindings"
          ],
          [
            539,
            "                       SET state = 'ARCHIVED', revision = revision + 1, updated_at = NOW()"
          ],
          [
            540,
            "                     WHERE binding_id = ? AND occurrence_id = ? AND current_lesson_id = ?"
          ],
          [
            541,
            "                       AND revision = ? AND state IN ('PENDING', 'ACTIVE')"
          ],
          [
            542,
            "                    \"\"\","
          ]
        ]
      }
    ]
  },
  {
    "id": "homework-lifecycle-withdraw",
    "title": "Снять задание с публикации",
    "description": "«Удалить задание» отправляет DELETE. Schedule архивирует привязку, Academic сохраняет карточку в архиве. homework.withdrawn возникает при ручном снятии ещё активного и не истёкшего задания. Закрытие семестра меняет доступность операций и студенческую выдачу активного семестра. Сохранённые задания староста может найти по дате и семестру.",
    "frames": [
      {
        "title": "После удаления клиент перечитывает архивную карточку",
        "path": "frontends/mobile-core/src/features/homework/HeadmanHomeworkScreen.vue",
        "language": "typescript",
        "lines": [
          [
            634,
            "  if (typeof window === 'undefined' || !window.confirm(`Удалить задание «${item.title}»?`)) return;"
          ],
          [
            635,
            "  const current = () =>"
          ],
          [
            636,
            "    !disposed &&"
          ],
          [
            637,
            "    owner === ownerRevision &&"
          ],
          [
            638,
            "    api === props.api &&"
          ],
          [
            639,
            "    item.groupId === props.groupId &&"
          ],
          [
            640,
            "    detail.value?.id === item.id &&"
          ],
          [
            641,
            "    item.semesterId === semester.value?.id &&"
          ],
          [
            642,
            "    writeAllowed.value;"
          ],
          [
            643,
            "  if (!current()) return;"
          ],
          [
            644,
            "  detailRevision += 1;"
          ],
          [
            645,
            "  busy.value = true;"
          ],
          [
            646,
            "  error.value = null;"
          ],
          [
            647,
            "  notice.value = null;"
          ],
          [
            648,
            "  try {"
          ],
          [
            649,
            "    await api.deleteHomework(item.id);"
          ],
          [
            650,
            "    if (!current()) return;"
          ],
          [
            651,
            "    // Display the authoritative archive only after the DELETE acknowledgement."
          ],
          [
            652,
            "    const archived = await api.getHomework(item.id, true);"
          ],
          [
            653,
            "    if (!current()) return;"
          ],
          [
            654,
            "    detail.value = archived;"
          ]
        ]
      },
      {
        "title": "Сначала архив привязки, затем запись Academic",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkService.java",
        "language": "java",
        "lines": [
          [
            1057,
            "        homework = publicationPersistence.prepareManualArchive(homework.getId());"
          ],
          [
            1058,
            "        if (homework.getPublicationState() == HomeworkPublicationState.ARCHIVED) return;"
          ],
          [
            1059,
            "        Long actorId = requestContext.getUserId();"
          ],
          [
            1060,
            "        HomeworkBindingResponse archived ="
          ],
          [
            1061,
            "                scheduleGrpcClient.archiveHomeworkBinding("
          ],
          [
            1062,
            "                        homework.getBindingId(), homework.getId(), homework.getRequestKey());"
          ],
          [
            1063,
            "        if (archived.getBindingId() != homework.getBindingId()"
          ],
          [
            1064,
            "                || !archived.hasHomeworkId()"
          ],
          [
            1065,
            "                || archived.getHomeworkId() != homework.getId()"
          ],
          [
            1066,
            "                || archived.getState() != HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED) {"
          ],
          [
            1067,
            "            throw new ConflictException(\"schedule did not durably archive the homework binding\");"
          ],
          [
            1068,
            "        }"
          ],
          [
            1069,
            "        publicationPersistence.archive("
          ],
          [
            1070,
            "                homework.getId(),"
          ],
          [
            1071,
            "                actorId,"
          ],
          [
            1072,
            "                homework.getRequestKey(),"
          ],
          [
            1073,
            "                homework.getBindingId(),"
          ],
          [
            1074,
            "                archived);"
          ],
          [
            1075,
            "    }"
          ]
        ]
      },
      {
        "title": "Карточка сохраняется, событие зависит от причины",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/homework/HomeworkBindingArchiveCoordinator.java",
        "language": "java",
        "lines": [
          [
            277,
            "        if (homework.getPublicationState() != HomeworkPublicationState.ARCHIVED) {"
          ],
          [
            278,
            "            var before ="
          ],
          [
            279,
            "                    actualBefore == null"
          ],
          [
            280,
            "                            ? HomeworkActionRecordedEvent.snapshot(homework)"
          ],
          [
            281,
            "                            : actualBefore;"
          ],
          [
            282,
            "            boolean manualPublished ="
          ],
          [
            283,
            "                    homework.getPublicationState() == HomeworkPublicationState.ACTIVE"
          ],
          [
            284,
            "                            && actualActorId != null"
          ],
          [
            285,
            "                            && actualActorId > 0"
          ],
          [
            286,
            "                            && actorRole != null"
          ],
          [
            287,
            "                            && !new HomeworkLifecycle(notificationClock).archived(homework);"
          ],
          [
            288,
            "            homework.archivePublication();"
          ],
          [
            289,
            "            homeworkRepository.save(homework);"
          ],
          [
            290,
            "            if (manualPublished)"
          ],
          [
            291,
            "                events.publishEvent("
          ],
          [
            292,
            "                        new ru.rutcampustrack.academic.event.HomeworkWithdrawnEvent("
          ],
          [
            293,
            "                                this, homework, actualActorId));"
          ]
        ]
      },
      {
        "title": "Граница записей при архивации семестра",
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
        "title": "Предыдущие задания доступны через календарь",
        "path": "frontends/mobile-core/src/features/homework/HeadmanHomeworkScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1222,
            "        <StudentWarningBlock"
          ],
          [
            1223,
            "          v-if=\"!semester\""
          ],
          [
            1224,
            "          title=\"Нет активного семестра\""
          ],
          [
            1225,
            "          message=\"Выдать новое задание можно в активном семестре. Предыдущие задания доступны по выбранной дате в календаре.\""
          ],
          [
            1226,
            "        />"
          ],
          [
            1227,
            "        <section"
          ],
          [
            1228,
            "          v-if=\"readIntent !== null && semesterOptions.length > 0\""
          ],
          [
            1229,
            "          class=\"headman-homework__calendar\""
          ]
        ]
      }
    ]
  }
]
