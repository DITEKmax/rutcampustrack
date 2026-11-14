import type { StoryOperation } from './story-source'

export const studentTransferHistoryOperations: StoryOperation[] = [
  {
    "id": "student-transfer-history-command",
    "title": "Команда перевода",
    "description": "Администратор с USERS_MANAGE выбирает новую группу и причину. POST /api/academic/users/{id}/transfer получает Idempotency-Key из клиента. В запросе нет даты вступления. Academic берёт дату по Москве. Перевод доступен для активного студента, группы должны отличаться, обе должны быть активны и иметь историю на эту дату.",
    "frames": [
      {
        "title": "Клиент отправляет группу и причину",
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
          ]
        ]
      },
      {
        "title": "Операция получает ключ и сохраняет намерение",
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
          ]
        ]
      },
      {
        "title": "Сервер требует USERS_MANAGE",
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
        "title": "Активный студент и границы истории групп",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
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
          ]
        ]
      },
      {
        "title": "POST · перевод в группу 42",
        "language": "json",
        "example": [
          "{",
          "  \"newGroupId\": 42,",
          "  \"reason\": \"По заявлению студента\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "student-transfer-history-memberships",
    "title": "Одна дата для двух интервалов",
    "description": "В примере 17 ноября закрывается членство в группе 41 и с той же даты открывается членство в группе 42. Команда меняет группу в студенческом допуске. Полномочия старосты при наличии приостанавливаются. Academic отзывает назначения студента помощником, а при переводе действующего старосты также отзывает помощников прежней группы.",
    "frames": [
      {
        "title": "Новая группа в допуске и московская дата",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicUserIdentityOperations.java",
        "language": "java",
        "lines": [
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
          ],
          [
            419,
            "                    return accepted("
          ],
          [
            420,
            "                            command("
          ],
          [
            421,
            "                                    id,"
          ],
          [
            422,
            "                                    IdentityOperationKind.IDENTITY_OPERATION_KIND_TRANSFER,"
          ],
          [
            423,
            "                                    target),"
          ],
          [
            424,
            "                            local);"
          ],
          [
            425,
            "                });"
          ]
        ]
      },
      {
        "title": "Закрытие и открытие на одну дату, отзыв делегирования",
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
        "title": "Границы сохраняются в истории членства",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/identityoperation/AcademicIdentityDomainWriter.java",
        "language": "java",
        "lines": [
          [
            469,
            "                jdbc.queryForList("
          ],
          [
            470,
            "                        \"SELECT id FROM student_group_history WHERE user_id=? AND left_at IS NULL\""
          ],
          [
            471,
            "                                + \" ORDER BY id FOR UPDATE\","
          ],
          [
            472,
            "                        Long.class,"
          ],
          [
            473,
            "                        user);"
          ],
          [
            474,
            "        if (rows.size() != 1"
          ],
          [
            475,
            "                || jdbc.update("
          ],
          [
            476,
            "                                \"UPDATE student_group_history SET left_at=?,reason=CASE WHEN ? THEN\""
          ],
          [
            477,
            "                                        + \" ? ELSE reason END WHERE id=? AND group_id=? AND\""
          ],
          [
            478,
            "                                        + \" joined_at<=?\","
          ],
          [
            479,
            "                                date,"
          ],
          [
            480,
            "                                replaceReason,"
          ],
          [
            481,
            "                                reason,"
          ],
          [
            482,
            "                                rows.getFirst(),"
          ],
          [
            483,
            "                                group,"
          ],
          [
            484,
            "                                date)"
          ],
          [
            520,
            "        Long id ="
          ],
          [
            521,
            "                jdbc.queryForObject("
          ],
          [
            522,
            "                        \"INSERT INTO\""
          ],
          [
            523,
            "                            + \" student_group_history(user_id,group_id,joined_at,reason,created_at)\""
          ],
          [
            524,
            "                            + \" VALUES(?,?,?,?,now()) RETURNING id\","
          ],
          [
            525,
            "                        Long.class,"
          ],
          [
            526,
            "                        user,"
          ],
          [
            527,
            "                        group,"
          ],
          [
            528,
            "                        date,"
          ],
          [
            529,
            "                        reason);"
          ]
        ]
      }
    ]
  },
  {
    "id": "student-transfer-history-journal",
    "title": "Состав на дату пары",
    "description": "Пара 731 осталась у группы 41. Журнал просит состав этой группы на 16 ноября и показывает студента вместе с его сохранённой отметкой, если она есть. Для пары прежней группы 17 ноября студент уже вне состава, а для пары новой группы в этот день он уже в составе. Исторический интервал включает день вступления и заканчивается перед днём выхода.",
    "frames": [
      {
        "title": "Attendance запрашивает состав на дату занятия",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            514,
            "    private GroupMembersResponse membersForLesson(LessonResponse lesson) {"
          ],
          [
            515,
            "        if (lesson.getSemesterId() <= 0) {"
          ],
          [
            516,
            "            throw new AcademicServiceUnavailableException("
          ],
          [
            517,
            "                    \"Schedule returned a lesson without a positive semester\");"
          ],
          [
            518,
            "        }"
          ],
          [
            519,
            "        LocalDate lessonDate = parseLessonDate(lesson.getDate());"
          ],
          [
            520,
            "        GroupMembersResponse response ="
          ],
          [
            521,
            "                academicGrpcClient.getGroupMembers("
          ],
          [
            522,
            "                        lesson.getGroupId(), lessonDate, lesson.getSemesterId());"
          ],
          [
            523,
            "        validateHistoricalRoster(response, lessonDate, lesson.getSemesterId());"
          ],
          [
            524,
            "        return response;"
          ],
          [
            527,
            "    private static void validateHistoricalRoster("
          ],
          [
            528,
            "            GroupMembersResponse members, LocalDate lessonDate, long semesterId) {"
          ],
          [
            529,
            "        if (members == null"
          ],
          [
            530,
            "                || !members.hasAsOfDate()"
          ],
          [
            531,
            "                || !members.hasSemesterId()"
          ],
          [
            532,
            "                || !lessonDate.toString().equals(members.getAsOfDate())"
          ],
          [
            533,
            "                || members.getSemesterId() != semesterId) {"
          ],
          [
            534,
            "            throw new AcademicServiceUnavailableException("
          ],
          [
            535,
            "                    \"Academic returned a missing or mismatched historical roster echo\");"
          ]
        ]
      },
      {
        "title": "Вступление включено, дата выхода исключена",
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
          ]
        ]
      },
      {
        "title": "Поля запроса состава по gRPC",
        "path": "proto/academic.proto",
        "language": "proto",
        "lines": [
          [
            189,
            "message GroupMembersRequest {"
          ],
          [
            190,
            "  int64 group_id = 1;"
          ],
          [
            191,
            "  optional string as_of_date = 2;  // ISO-8601 date for historical membership"
          ],
          [
            192,
            "  optional int64 semester_id = 3;  // semester containing as_of_date"
          ],
          [
            193,
            "}"
          ]
        ]
      },
      {
        "title": "Состав для пары 731 · поля сообщения gRPC в JSON",
        "language": "json",
        "example": [
          "{",
          "  \"groupId\": \"41\",",
          "  \"asOfDate\": \"2026-11-16\",",
          "  \"semesterId\": \"7\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "student-transfer-history-ranking",
    "title": "Своя история и рейтинг",
    "description": "Личная история в выбранном семестре собирается по интервалам членства, поэтому может включать пары обеих групп. Для текущего рейтинга сервис выбирает группу из действующего допуска и сверяет её с историей. Если выбранный семестр уже закончился и у студента был перевод, рейтинг скрывается.",
    "frames": [
      {
        "title": "Членство ограничивается границами семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java",
        "language": "java",
        "lines": [
          [
            156,
            "        validateSemester(semesterId, semester);"
          ],
          [
            157,
            "        LocalDate dateUntilExclusive = exclusiveEnd(semester.dateTo());"
          ],
          [
            158,
            "        LocalDate serverDate = LocalDate.now(educationalClock.withZone(MOSCOW));"
          ],
          [
            159,
            ""
          ],
          [
            160,
            "        List<StudentProjectionQuery.GroupHistorySnapshot> historyRows ="
          ],
          [
            161,
            "                query.findGroupHistory(authorization.userId());"
          ],
          [
            162,
            "        HistoryTimeline history = HistoryTimeline.validate(historyRows, authorization.userId());"
          ],
          [
            163,
            "        List<StudentProjectionScope.MembershipSegment> clippedSegments ="
          ],
          [
            164,
            "                history.clip(semester.dateFrom(), dateUntilExclusive).stream()"
          ],
          [
            165,
            "                        .filter("
          ],
          [
            166,
            "                                segment ->"
          ],
          [
            167,
            "                                        allowedGroupId == null"
          ],
          [
            168,
            "                                                || segment.groupId() == allowedGroupId)"
          ],
          [
            169,
            "                        .toList();"
          ],
          [
            170,
            "        if (clippedSegments.isEmpty()) {"
          ],
          [
            171,
            "            throw StudentProjectionException.unresolved("
          ],
          [
            172,
            "                    \"Student has no valid membership in the semester\");"
          ],
          [
            173,
            "        }"
          ],
          [
            174,
            ""
          ],
          [
            175,
            "        SubjectResolution subjects ="
          ],
          [
            176,
            "                resolveSubjects("
          ],
          [
            177,
            "                        semesterId, semester.dateFrom(), dateUntilExclusive, clippedSegments);"
          ]
        ]
      },
      {
        "title": "Рейтинг скрывается для прошедшего семестра после перевода",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java",
        "language": "java",
        "lines": [
          [
            431,
            "    private RankDecision resolveRank("
          ],
          [
            432,
            "            Authorization authorization,"
          ],
          [
            433,
            "            HistoryTimeline history,"
          ],
          [
            434,
            "            long semesterId,"
          ],
          [
            435,
            "            LocalDate semesterFrom,"
          ],
          [
            436,
            "            LocalDate semesterUntilExclusive,"
          ],
          [
            437,
            "            LocalDate semesterTo,"
          ],
          [
            438,
            "            LocalDate serverDate,"
          ],
          [
            439,
            "            Long allowedGroupId) {"
          ],
          [
            440,
            "        boolean past = serverDate.isAfter(semesterTo);"
          ],
          [
            441,
            "        if (past && history.everTransferred()) {"
          ],
          [
            442,
            "            // This branch intentionally returns before any roster query."
          ],
          [
            443,
            "            return RankDecision.hidden();"
          ],
          [
            444,
            "        }"
          ]
        ]
      },
      {
        "title": "Текущая группа сверяется с историей",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java",
        "language": "java",
        "lines": [
          [
            449,
            "        Long grantGroupId = authorization.studentGrant().groupId();"
          ],
          [
            450,
            "        if (grantGroupId == null || grantGroupId <= 0) {"
          ],
          [
            451,
            "            throw StudentProjectionException.inconsistent("
          ],
          [
            452,
            "                    \"Active student grant has no authoritative group\");"
          ],
          [
            460,
            "        Optional<Long> currentGroup = history.currentGroupAt(serverDate);"
          ],
          [
            461,
            "        if (currentGroup.isEmpty() || !grantGroupId.equals(currentGroup.get())) {"
          ],
          [
            462,
            "            throw StudentProjectionException.inconsistent("
          ],
          [
            463,
            "                    \"Current student grant does not match authoritative history\");"
          ],
          [
            464,
            "        }"
          ],
          [
            479,
            "        } else {"
          ],
          [
            480,
            "            rankGroupId = grantGroupId;"
          ],
          [
            481,
            "        }"
          ],
          [
            482,
            ""
          ],
          [
            483,
            "        if (!history.hasMembership(rankGroupId, semesterFrom, semesterUntilExclusive)) {"
          ],
          [
            484,
            "            return RankDecision.visible(null, false, List.of());"
          ],
          [
            485,
            "        }"
          ],
          [
            486,
            ""
          ],
          [
            487,
            "        List<Long> roster ="
          ],
          [
            488,
            "                query.findActiveStudentIds("
          ],
          [
            489,
            "                        rankGroupId, semesterId, semesterFrom, semesterUntilExclusive);"
          ]
        ]
      }
    ]
  }
]
