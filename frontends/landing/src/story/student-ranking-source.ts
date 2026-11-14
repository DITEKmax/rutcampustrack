import type { StoryOperation } from './story-source'

export const studentRankingOperations: readonly StoryOperation[] = [
  {
    "id": "student-ranking-identity",
    "title": "Сессия и семестр",
    "description": "PWA и Mini App открывают рейтинг в контексте роли STUDENT и активного семестра. Academic сверяет подписанную сессию, версию полномочий и группу до чтения состава, а затем перепроверяет допуск. В общем ответе статистики есть только краткое собственное место. Строки других студентов доступны отдельным запросом после проверки области доступа.",
    "frames": [
      {
        "title": "Mobile BFF принимает активный семестр",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java",
        "language": "java",
        "lines": [
          [
            205,
            "    public StudentStatisticsRankingResponse statisticsRanking("
          ],
          [
            206,
            "            long requestedSemesterId, Integer page, int size) {"
          ],
          [
            207,
            "        requireActiveSemester(requestedSemesterId);"
          ],
          [
            208,
            "        StudentAttendanceRankingResponse response ="
          ],
          [
            209,
            "                attendance.ranking(requestedSemesterId, page, size);"
          ],
          [
            357,
            "    private InternalJwtClaims requireStudent(boolean groupRequired) {"
          ],
          [
            358,
            "        InternalJwtClaims claims = requestContext.claims();"
          ],
          [
            359,
            "        if (!\"STUDENT\".equalsIgnoreCase(claims.domainRole())) {"
          ],
          [
            360,
            "            throw new MobileBffException("
          ],
          [
            361,
            "                    HttpStatus.FORBIDDEN,"
          ],
          [
            362,
            "                    ProblemCode.WRONG_ROLE,"
          ],
          [
            363,
            "                    \"Мобильный student API доступен роли STUDENT\");"
          ],
          [
            364,
            "        }"
          ],
          [
            365,
            "        if (claims.userId() <= 0"
          ],
          [
            366,
            "                || groupRequired && (claims.groupId() == null || claims.groupId() <= 0)) {"
          ],
          [
            367,
            "            throw new MobileBffException("
          ],
          [
            368,
            "                    HttpStatus.FORBIDDEN,"
          ],
          [
            369,
            "                    ProblemCode.OUT_OF_SCOPE,"
          ],
          [
            370,
            "                    \"Не хватает student/group scope\");"
          ],
          [
            371,
            "        }"
          ],
          [
            372,
            "        return claims;"
          ],
          [
            375,
            "    private SemesterResponse requireActiveSemester(long requestedSemesterId) {"
          ],
          [
            376,
            "        requireStudent(false);"
          ],
          [
            377,
            "        SemesterResponse active = academic.activeSemester();"
          ],
          [
            378,
            "        if (active.getId() != requestedSemesterId) {"
          ],
          [
            379,
            "            throw new MobileBffException("
          ],
          [
            380,
            "                    HttpStatus.FORBIDDEN,"
          ],
          [
            381,
            "                    ProblemCode.OUT_OF_SCOPE,"
          ],
          [
            382,
            "                    \"Семестр не входит в scope студента\");"
          ],
          [
            383,
            "        }"
          ],
          [
            384,
            "        return active;"
          ],
          [
            385,
            "    }"
          ]
        ]
      },
      {
        "title": "Attendance берёт пользователя из подписанного контекста",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/AttendanceStudentGrpcServiceImpl.java",
        "language": "java",
        "lines": [
          [
            142,
            "    public void getStudentAttendanceRanking("
          ],
          [
            143,
            "            StudentAttendanceRankingRequest request,"
          ],
          [
            144,
            "            StreamObserver<StudentAttendanceRankingResponse> observer) {"
          ],
          [
            145,
            "        try {"
          ],
          [
            146,
            "            if (projectionService == null) {"
          ],
          [
            147,
            "                throw new AcademicServiceUnavailableException("
          ],
          [
            148,
            "                        \"Student attendance projection is not configured\");"
          ],
          [
            149,
            "            }"
          ],
          [
            150,
            "            InternalJwtClaims claims = requireClaims();"
          ],
          [
            151,
            "            StudentAttendanceProjectionService.RankingPage ranking ="
          ],
          [
            152,
            "                    projectionService.ranking("
          ],
          [
            153,
            "                            claims,"
          ],
          [
            154,
            "                            request.getSemesterId(),"
          ],
          [
            155,
            "                            request.hasPage() ? request.getPage() : null,"
          ],
          [
            156,
            "                            request.getSize());"
          ],
          [
            157,
            "            observer.onNext(toProto(ranking));"
          ],
          [
            158,
            "            observer.onCompleted();"
          ]
        ]
      },
      {
        "title": "Допуск проверяется до и после чтения",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java",
        "language": "java",
        "lines": [
          [
            95,
            "    private StudentProjectionScope resolveInternal(long semesterId, InternalJwtClaims claims) {"
          ],
          [
            96,
            "        Authorization admitted = authorize(claims);"
          ],
          [
            97,
            "        StudentProjectionScope scope ="
          ],
          [
            98,
            "                query.readSnapshot("
          ],
          [
            99,
            "                        admitted.authority(), () -> resolveInternal(semesterId, admitted, null));"
          ],
          [
            100,
            "        query.verifyCurrent(admitted.authority(), scope);"
          ],
          [
            101,
            "        Authorization current = authorize(claims);"
          ],
          [
            102,
            "        if (!admitted.equals(current)) {"
          ],
          [
            103,
            "            throw StudentProjectionException.invalidSession(\"Signed student authority changed\");"
          ],
          [
            104,
            "        }"
          ],
          [
            105,
            "        return scope;"
          ]
        ]
      },
      {
        "title": "Подписанная роль и актуальность полномочий",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java",
        "language": "java",
        "lines": [
          [
            231,
            "    private Authorization authorize(InternalJwtClaims claims) {"
          ],
          [
            232,
            "        if (claims == null"
          ],
          [
            233,
            "                || claims.userId() <= 0"
          ],
          [
            234,
            "                || claims.sessionId() == null"
          ],
          [
            235,
            "                || claims.sessionVersion() <= 0"
          ],
          [
            236,
            "                || claims.rolesVersion() <= 0) {"
          ],
          [
            237,
            "            throw StudentProjectionException.invalidSession(\"Signed student identity is required\");"
          ],
          [
            238,
            "        }"
          ],
          [
            239,
            "        if (!STUDENT_ROLE.equals(normalize(claims.domainRole()))) {"
          ],
          [
            240,
            "            throw StudentProjectionException.wrongRole("
          ],
          [
            241,
            "                    \"Student projection requires a signed STUDENT identity\");"
          ],
          [
            242,
            "        }"
          ],
          [
            255,
            "        if (authority.userId() != claims.userId()) {"
          ],
          [
            256,
            "            throw StudentProjectionException.inconsistent("
          ],
          [
            257,
            "                    \"Authority user identity is inconsistent\");"
          ],
          [
            258,
            "        }"
          ],
          [
            259,
            "        if (authority.rolesVersion() <= 0 || authority.rolesVersion() != claims.rolesVersion()) {"
          ],
          [
            260,
            "            throw StudentProjectionException.invalidSession(\"Signed student authority is stale\");"
          ],
          [
            261,
            "        }"
          ],
          [
            262,
            ""
          ],
          [
            263,
            "        StudentProjectionQuery.RoleGrantSnapshot studentGrant = studentGrant(authority);"
          ],
          [
            264,
            "        String accountStatus = normalize(studentGrant.status());"
          ],
          [
            265,
            "        if (!accountStatus.equals(normalize(claims.status()))) {"
          ],
          [
            266,
            "            throw StudentProjectionException.invalidSession(\"Signed student status is stale\");"
          ],
          [
            267,
            "        }"
          ],
          [
            279,
            "        boolean terminal = TERMINAL_STATUSES.contains(accountStatus);"
          ],
          [
            280,
            "        if (!session.sessionId().equals(claims.sessionId())"
          ],
          [
            281,
            "                || session.userId() != claims.userId()"
          ],
          [
            282,
            "                || !session.isLiveAt(clock.instant())"
          ],
          [
            283,
            "                || session.sessionVersion() != claims.sessionVersion()"
          ],
          [
            284,
            "                || !Objects.equals(session.activeRoleGrantId(), studentGrant.id())) {"
          ],
          [
            285,
            "            throw StudentProjectionException.invalidSession(\"Signed student session is stale\");"
          ],
          [
            286,
            "        }"
          ],
          [
            287,
            "        if (!Objects.equals(claims.groupId(), studentGrant.groupId()) || claims.isHeadman()) {"
          ],
          [
            288,
            "            throw StudentProjectionException.invalidSession(\"Signed student identity is stale\");"
          ],
          [
            289,
            "        }"
          ]
        ]
      }
    ]
  },
  {
    "id": "student-ranking-cohort",
    "title": "Кто входит в состав",
    "description": "Academic берёт группу из действующего права STUDENT и сверяет её с историей членства на московскую дату. В список входят активные студенты этой группы, чьё членство пересекает семестр. Личная история ограничена собственными интервалами. Для завершившегося по датам семестра после перевода рейтинг скрыт. У терминального аккаунта, например архивного, места нет.",
    "frames": [
      {
        "title": "Московская дата и границы членства",
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
          ]
        ]
      },
      {
        "title": "Текущая группа и условия исторического расчёта",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java",
        "language": "java",
        "lines": [
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
          ],
          [
            445,
            "        if (authorization.terminalReadOnly()) {"
          ],
          [
            446,
            "            return RankDecision.visible(null, false, List.of());"
          ],
          [
            447,
            "        }"
          ],
          [
            455,
            "        // No foreign current-group or cohort data is needed for this admitted historical target."
          ],
          [
            456,
            "        if (allowedGroupId != null && !allowedGroupId.equals(grantGroupId)) {"
          ],
          [
            457,
            "            return RankDecision.hidden();"
          ],
          [
            458,
            "        }"
          ],
          [
            459,
            ""
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
            465,
            ""
          ],
          [
            466,
            "        long rankGroupId;"
          ],
          [
            467,
            "        if (past) {"
          ],
          [
            468,
            "            rankGroupId ="
          ],
          [
            469,
            "                    history.soleHistoricalGroup()"
          ],
          [
            470,
            "                            .orElseThrow("
          ],
          [
            471,
            "                                    () ->"
          ],
          [
            472,
            "                                            StudentProjectionException.unresolved("
          ],
          [
            473,
            "                                                    \"Untransferred past scope has no historical\""
          ],
          [
            474,
            "                                                            + \" group\"));"
          ],
          [
            475,
            "            if (rankGroupId != grantGroupId) {"
          ],
          [
            476,
            "                throw StudentProjectionException.inconsistent("
          ],
          [
            477,
            "                        \"Current student grant disagrees with history\");"
          ],
          [
            478,
            "            }"
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
      },
      {
        "title": "Архивный аккаунт относится к терминальным",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java",
        "language": "java",
        "lines": [
          [
            42,
            "    private static final String ACTIVE_STATUS = \"ACTIVE\";"
          ],
          [
            43,
            "    private static final Set<String> TERMINAL_STATUSES ="
          ],
          [
            44,
            "            Set.of(\"EXPELLED\", \"GRADUATED\", \"ARCHIVED\");"
          ],
          [
            291,
            "        if (\"SUSPENDED\".equals(accountStatus)) {"
          ],
          [
            292,
            "            throw StudentProjectionException.outOfScope("
          ],
          [
            293,
            "                    \"Suspended students have no projection scope\");"
          ],
          [
            294,
            "        }"
          ],
          [
            295,
            "        if (claims.readOnly() != terminal) {"
          ],
          [
            296,
            "            throw StudentProjectionException.invalidSession(\"Signed student identity is stale\");"
          ],
          [
            297,
            "        }"
          ],
          [
            298,
            "        if (ACTIVE_STATUS.equals(accountStatus)"
          ],
          [
            299,
            "                && (studentGrant.groupId() == null || studentGrant.groupId() <= 0)) {"
          ],
          [
            300,
            "            throw StudentProjectionException.inconsistent("
          ],
          [
            301,
            "                    \"Active student grant has no authoritative group\");"
          ],
          [
            302,
            "        }"
          ],
          [
            303,
            "        return new Authorization("
          ],
          [
            304,
            "                claims.userId(), studentGrant, terminal && claims.readOnly(), authority);"
          ]
        ]
      },
      {
        "title": "Состав из активных прав и истории группы",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/JdbcStudentProjectionQueryAdapter.java",
        "language": "java",
        "lines": [
          [
            71,
            "    private static final String ROSTER_SQL ="
          ],
          [
            72,
            "            \"\"\""
          ],
          [
            73,
            "            SELECT DISTINCT g.user_id"
          ],
          [
            74,
            "            FROM academic_role_eligibility g"
          ],
          [
            75,
            "            JOIN academic_profiles p ON p.id = g.user_id"
          ],
          [
            76,
            "            JOIN student_group_history h"
          ],
          [
            77,
            "              ON h.user_id = g.user_id"
          ],
          [
            78,
            "             AND h.group_id = g.group_id"
          ],
          [
            79,
            "            WHERE g.role = 'STUDENT'"
          ],
          [
            80,
            "              AND g.status = 'ACTIVE'"
          ],
          [
            81,
            "              AND g.group_id = ?"
          ],
          [
            82,
            "              AND h.joined_at < ?"
          ],
          [
            83,
            "              AND (h.left_at IS NULL OR h.left_at > h.joined_at)"
          ],
          [
            84,
            "              AND (h.left_at IS NULL OR h.left_at > ?)"
          ],
          [
            85,
            "            ORDER BY g.user_id"
          ],
          [
            86,
            "            \"\"\";"
          ],
          [
            383,
            "        fences.requireGroupReadable(groupId);"
          ],
          [
            384,
            "        return jdbcTemplate.query("
          ],
          [
            385,
            "                ROSTER_SQL,"
          ],
          [
            386,
            "                ps -> {"
          ],
          [
            387,
            "                    ps.setLong(1, groupId);"
          ],
          [
            388,
            "                    ps.setObject(2, dateUntilExclusive);"
          ],
          [
            389,
            "                    ps.setObject(3, dateFrom);"
          ],
          [
            390,
            "                },"
          ],
          [
            391,
            "                (rs, rowNum) -> rs.getLong(\"user_id\"));"
          ]
        ]
      }
    ]
  },
  {
    "id": "student-ranking-held",
    "title": "Общий знаменатель группы",
    "description": "Для рейтинга Attendance берёт закрытые пары всей группы за семестр. Запланированные, текущие, отменённые и исходные пары после переноса в знаменатель не входят. У каждого участника числитель состоит из PRESENT. Для самого запрашивающего студента отметки берутся только с разрешённых ему пар. Поэтому у поздно вступившего студента процент в рейтинге может отличаться от личной сводки.",
    "frames": [
      {
        "title": "Предметы группы берутся за весь семестр",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/studentprojection/StudentProjectionScopeService.java",
        "language": "java",
        "lines": [
          [
            207,
            "    private List<StudentProjectionScope.Subject> resolveRankSubjects("
          ],
          [
            208,
            "            long semesterId,"
          ],
          [
            209,
            "            LocalDate semesterFrom,"
          ],
          [
            210,
            "            LocalDate semesterUntilExclusive,"
          ],
          [
            211,
            "            RankDecision rank) {"
          ],
          [
            212,
            "        if (!rank.eligible()) {"
          ],
          [
            213,
            "            return List.of();"
          ],
          [
            214,
            "        }"
          ],
          [
            215,
            "        if (rank.groupId() == null || rank.groupId() <= 0) {"
          ],
          [
            216,
            "            throw StudentProjectionException.inconsistent("
          ],
          [
            217,
            "                    \"Eligible rank scope has no authoritative group\");"
          ],
          [
            218,
            "        }"
          ],
          [
            219,
            ""
          ],
          [
            220,
            "        // Ranking uses the whole semester cohort, not only the student's own"
          ],
          [
            221,
            "        // membership intervals. A subject whose assignment ended before a"
          ],
          [
            222,
            "        // late joiner arrived still contributes the group's semester lessons."
          ],
          [
            223,
            "        StudentProjectionScope.MembershipSegment semesterGroup ="
          ],
          [
            224,
            "                new StudentProjectionScope.MembershipSegment("
          ],
          [
            225,
            "                        rank.groupId(), semesterFrom, semesterUntilExclusive, List.of());"
          ],
          [
            226,
            "        return resolveSubjects("
          ],
          [
            227,
            "                        semesterId, semesterFrom, semesterUntilExclusive, List.of(semesterGroup))"
          ],
          [
            228,
            "                .subjects();"
          ]
        ]
      },
      {
        "title": "Границы пар для рейтинга",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            964,
            "    private List<Lesson> canonicalRankLessons("
          ],
          [
            965,
            "            StudentProjectionScopeResponse scope,"
          ],
          [
            966,
            "            Map<Long, AcademicSubjectInfo> rankSubjectsById,"
          ],
          [
            967,
            "            long semesterId,"
          ],
          [
            968,
            "            long rankGroup) {"
          ],
          [
            969,
            "        List<Long> rankSubjectIds = rankSubjectsById.keySet().stream().sorted().toList();"
          ],
          [
            970,
            "        if (rankSubjectIds.isEmpty()) return List.of();"
          ],
          [
            971,
            "        LocalDate from = parseRequiredDate(scope.getDateFrom(), \"date_from\");"
          ],
          [
            972,
            "        LocalDate to = parseRequiredDate(scope.getDateTo(), \"date_to\");"
          ],
          [
            973,
            "        StudentProjectionMembershipSegment canonicalSegment ="
          ],
          [
            974,
            "                StudentProjectionMembershipSegment.newBuilder()"
          ],
          [
            975,
            "                        .setGroupId(rankGroup)"
          ],
          [
            976,
            "                        .setDateFrom(from.toString())"
          ],
          [
            977,
            "                        .setDateUntilExclusive(to.plusDays(1).toString())"
          ],
          [
            978,
            "                        .addAllSubjectIds(rankSubjectIds)"
          ],
          [
            979,
            "                        .build();"
          ],
          [
            980,
            "        return loadLessons(List.of(canonicalSegment), semesterId, rankSubjectsById, null, Set.of())"
          ],
          [
            981,
            "                .stream()"
          ],
          [
            982,
            "                .filter(lesson -> lesson.groupId() == rankGroup)"
          ],
          [
            983,
            "                .toList();"
          ],
          [
            984,
            "    }"
          ]
        ]
      },
      {
        "title": "Исходный экземпляр переноса исключается",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            399,
            "        if (\"transferred\".equalsIgnoreCase(source.getStatus())) return null;"
          ],
          [
            400,
            "        LocalDate date = parseRequiredDate(source.getDate(), \"lesson.date\");"
          ],
          [
            401,
            "        LocalDate from = parseRequiredDate(segment.getDateFrom(), \"membership.date_from\");"
          ],
          [
            402,
            "        LocalDate untilExclusive ="
          ],
          [
            403,
            "                parseRequiredDate("
          ],
          [
            404,
            "                        segment.getDateUntilExclusive(), \"membership.date_until_exclusive\");"
          ],
          [
            405,
            "        if (date.isBefore(from) || !date.isBefore(untilExclusive)) {"
          ],
          [
            406,
            "            return null;"
          ],
          [
            407,
            "        }"
          ]
        ]
      },
      {
        "title": "Общий набор пар и ограничение своих отметок",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            892,
            "        long rankGroup = scope.getRankGroupId();"
          ],
          [
            893,
            "        Map<Long, AcademicSubjectInfo> rankSubjects = rankSubjectMap(scope, semesterId, rankGroup);"
          ],
          [
            894,
            "        List<Lesson> rankLessons ="
          ],
          [
            895,
            "                capturedGroupLessons == null"
          ],
          [
            896,
            "                        ? canonicalRankLessons(scope, rankSubjects, semesterId, rankGroup)"
          ],
          [
            897,
            "                        : capturedGroupLessons;"
          ],
          [
            898,
            "        Set<Long> ownAuthorizedLessonIds ="
          ],
          [
            899,
            "                ownLessons.stream()"
          ],
          [
            900,
            "                        .filter(lesson -> lesson.groupId() == rankGroup)"
          ],
          [
            901,
            "                        .map(Lesson::lessonId)"
          ],
          [
            902,
            "                        .collect(Collectors.toUnmodifiableSet());"
          ],
          [
            903,
            "        Map<Long, List<AttendanceRecord>> records ="
          ],
          [
            904,
            "                attendanceReadPort.findByUserIds(roster, semesterId);"
          ],
          [
            913,
            "                                    List<AttendanceRecord> participantRecords ="
          ],
          [
            914,
            "                                            records.getOrDefault(userId, List.of());"
          ],
          [
            915,
            "                                    if (userId == ownId) {"
          ],
          [
            916,
            "                                        // The rank denominator is the canonical semester cohort"
          ],
          [
            917,
            "                                        // for every roster member.  A late joiner contributes"
          ],
          [
            918,
            "                                        // marks only from their authorized membership history."
          ],
          [
            919,
            "                                        participantRecords ="
          ],
          [
            920,
            "                                                participantRecords.stream()"
          ],
          [
            921,
            "                                                        .filter("
          ],
          [
            922,
            "                                                                record ->"
          ],
          [
            923,
            "                                                                        ownAuthorizedLessonIds"
          ],
          [
            924,
            "                                                                                .contains("
          ],
          [
            925,
            "                                                                                        record"
          ],
          [
            926,
            "                                                                                                .lessonId()))"
          ],
          [
            927,
            "                                                        .toList();"
          ],
          [
            928,
            "                                    }"
          ],
          [
            929,
            "                                    Map<Long, AttendanceRecord> marks ="
          ],
          [
            930,
            "                                            markIndex("
          ],
          [
            931,
            "                                                    participantRecords,"
          ],
          [
            932,
            "                                                    rankLessons,"
          ],
          [
            933,
            "                                                    userId,"
          ],
          [
            934,
            "                                                    semesterId);"
          ]
        ]
      },
      {
        "title": "В рейтинг передаются held и presentCount",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            935,
            "                                    List<Lesson> markedLessons ="
          ],
          [
            936,
            "                                            rankLessons.stream()"
          ],
          [
            937,
            "                                                    .map("
          ],
          [
            938,
            "                                                            lesson ->"
          ],
          [
            939,
            "                                                                    lesson.withMark("
          ],
          [
            940,
            "                                                                            marks.get("
          ],
          [
            941,
            "                                                                                    lesson"
          ],
          [
            942,
            "                                                                                            .lessonId())))"
          ],
          [
            943,
            "                                                    .toList();"
          ],
          [
            944,
            "                                    AttendanceMetricCalculator.Metrics metrics ="
          ],
          [
            945,
            "                                            metrics(markedLessons).metrics();"
          ],
          [
            946,
            "                                    return OwnRankCalculator.Participant.withCounts("
          ],
          [
            947,
            "                                            userId, metrics.heldCount(), metrics.presentCount());"
          ],
          [
            948,
            "                                })"
          ],
          [
            949,
            "                        .toList();"
          ],
          [
            950,
            "        List<OwnRankCalculator.RankedParticipant> ranked = OwnRankCalculator.rankAll(participants);"
          ],
          [
            951,
            "        OwnRankCalculator.Rank result = OwnRankCalculator.summarize(ownId, ranked);"
          ],
          [
            952,
            "        return new RankedCohort("
          ],
          [
            953,
            "                new Rank(result.position(), result.participantCount(), result.available()), ranked);"
          ]
        ]
      },
      {
        "title": "Что попадает в held и PRESENT",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/AttendanceMetricCalculator.java",
        "language": "java",
        "lines": [
          [
            297,
            "            AttendanceStatus status = occurrence.attendanceStatus();"
          ],
          [
            298,
            "            switch (state) {"
          ],
          [
            299,
            "                case PLANNED, ACTIVE -> {"
          ],
          [
            300,
            "                    // Check-in can legitimately persist a mark before the"
          ],
          [
            301,
            "                    // schedule transition reaches CLOSED. It stays outside H."
          ],
          [
            302,
            "                    rejectIncludedStatus(id, status);"
          ],
          [
            303,
            "                    plannedCount = addOne(plannedCount, \"planned count\");"
          ],
          [
            304,
            "                }"
          ],
          [
            305,
            "                case CLOSED -> {"
          ],
          [
            306,
            "                    plannedCount = addOne(plannedCount, \"planned count\");"
          ],
          [
            307,
            "                    heldCount = addOne(heldCount, \"held count\");"
          ],
          [
            308,
            "                    if (status == null) {"
          ],
          [
            309,
            "                        absentCount = addOne(absentCount, \"absent count\");"
          ],
          [
            310,
            "                        missingClosedCount = addOne(missingClosedCount, \"missing closed count\");"
          ],
          [
            311,
            "                    } else {"
          ],
          [
            312,
            "                        switch (status) {"
          ],
          [
            313,
            "                            case PRESENT -> presentCount = addOne(presentCount, \"present count\");"
          ],
          [
            314,
            "                            case ABSENT -> absentCount = addOne(absentCount, \"absent count\");"
          ],
          [
            315,
            "                            case EXCUSED -> excusedCount = addOne(excusedCount, \"excused count\");"
          ],
          [
            316,
            "                            case FREE_ATTENDANCE ->"
          ],
          [
            317,
            "                                    throw StudentProjectionException.unsupportedAttendanceStatus("
          ],
          [
            318,
            "                                            id, status);"
          ],
          [
            327,
            "                case CANCELLED, TRANSFERRED_OUT -> {"
          ],
          [
            328,
            "                    // Historical excluded rows may retain an invalidated mark."
          ],
          [
            329,
            "                    // Lifecycle decides exclusion, but FREE_ATTENDANCE remains"
          ],
          [
            330,
            "                    // an explicit unsupported-data failure."
          ],
          [
            331,
            "                    rejectUnsupportedStatusOnly(id, status);"
          ],
          [
            332,
            "                }"
          ]
        ]
      }
    ]
  },
  {
    "id": "student-ranking-ties",
    "title": "Почему места идут 1, 2, 2, 4",
    "description": "При четырёх закрытых парах доли 4/4, 3/4, 3/4 и 0/4 дают места 1, 2, 2 и 4. Сервер сравнивает дроби перекрёстным умножением. ID задаёт порядок строк с одинаковой долей. Округление происходит для показа. Например, экран показывает 50% и для 100/201, и для 101/201, хотя вторая доля выше.",
    "frames": [
      {
        "title": "Сравнение долей без округления",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculator.java",
        "language": "java",
        "lines": [
          [
            145,
            "    private static boolean hasRatio(AttendanceMetricCalculator.Metrics metrics) {"
          ],
          [
            146,
            "        return metrics != null && metrics.heldCount() > 0;"
          ],
          [
            147,
            "    }"
          ],
          [
            148,
            ""
          ],
          [
            149,
            "    /** Positive means left has the higher exact present/held fraction. */"
          ],
          [
            150,
            "    private static int compareRatio("
          ],
          [
            151,
            "            AttendanceMetricCalculator.Metrics left, AttendanceMetricCalculator.Metrics right) {"
          ],
          [
            152,
            "        BigInteger leftCross ="
          ],
          [
            153,
            "                BigInteger.valueOf(left.presentCount())"
          ],
          [
            154,
            "                        .multiply(BigInteger.valueOf(right.heldCount()));"
          ],
          [
            155,
            "        BigInteger rightCross ="
          ],
          [
            156,
            "                BigInteger.valueOf(right.presentCount())"
          ],
          [
            157,
            "                        .multiply(BigInteger.valueOf(left.heldCount()));"
          ],
          [
            158,
            "        return leftCross.compareTo(rightCross);"
          ],
          [
            159,
            "    }"
          ]
        ]
      },
      {
        "title": "Сортировка, равное место и пропуск позиции",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculator.java",
        "language": "java",
        "lines": [
          [
            92,
            "        participants.sort("
          ],
          [
            93,
            "                (left, right) -> {"
          ],
          [
            94,
            "                    boolean leftAvailable = hasRatio(left.metrics());"
          ],
          [
            95,
            "                    boolean rightAvailable = hasRatio(right.metrics());"
          ],
          [
            96,
            "                    if (leftAvailable != rightAvailable) {"
          ],
          [
            97,
            "                        return leftAvailable ? -1 : 1;"
          ],
          [
            98,
            "                    }"
          ],
          [
            99,
            "                    if (leftAvailable) {"
          ],
          [
            100,
            "                        int byRatio = compareRatio(right.metrics(), left.metrics());"
          ],
          [
            101,
            "                        if (byRatio != 0) return byRatio;"
          ],
          [
            102,
            "                    }"
          ],
          [
            103,
            "                    return Long.compare(left.participantId(), right.participantId());"
          ],
          [
            104,
            "                });"
          ],
          [
            105,
            ""
          ],
          [
            106,
            "        List<RankedParticipant> result = new ArrayList<>(participants.size());"
          ],
          [
            107,
            "        int rankPosition = 0;"
          ],
          [
            108,
            "        int availableCount = 0;"
          ],
          [
            109,
            "        Participant previousRanked = null;"
          ],
          [
            110,
            "        for (Participant participant : participants) {"
          ],
          [
            111,
            "            AttendanceMetricCalculator.Metrics metrics = participant.metrics();"
          ],
          [
            112,
            "            if (!hasRatio(metrics)) {"
          ],
          [
            113,
            "                result.add(new RankedParticipant(participant.participantId(), null, null));"
          ],
          [
            114,
            "                continue;"
          ],
          [
            115,
            "            }"
          ],
          [
            116,
            "            availableCount++;"
          ],
          [
            117,
            "            if (previousRanked == null || compareRatio(previousRanked.metrics(), metrics) != 0) {"
          ],
          [
            118,
            "                rankPosition = availableCount;"
          ],
          [
            119,
            "            }"
          ],
          [
            120,
            "            result.add("
          ],
          [
            121,
            "                    new RankedParticipant("
          ],
          [
            122,
            "                            participant.participantId(), rankPosition, metrics.presentPercent()));"
          ],
          [
            123,
            "            previousRanked = participant;"
          ],
          [
            124,
            "        }"
          ]
        ]
      },
      {
        "title": "На экране процент округляется до целого",
        "path": "frontends/mobile-core/src/features/statistics/statistics-view-model.ts",
        "language": "typescript",
        "lines": [
          [
            98,
            "export function displayPercent(percent: number | null): string {"
          ],
          [
            99,
            "  return percent === null || !Number.isFinite(percent) ? '—' : `${Math.round(percent)}%`"
          ],
          [
            100,
            "}"
          ]
        ]
      },
      {
        "title": "Контракт списка и строки",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentApiModels.java",
        "language": "java",
        "lines": [
          [
            590,
            "    @Schema("
          ],
          [
            591,
            "            name = \"StudentStatisticsRankingRow\","
          ],
          [
            592,
            "            requiredProperties = {\"id\", \"name\", \"position\", \"percentage\", \"isSelf\"})"
          ],
          [
            593,
            "    public record StudentStatisticsRankingRow("
          ],
          [
            594,
            "            String id, String name, Integer position, Double percentage, boolean isSelf) {}"
          ],
          [
            595,
            ""
          ],
          [
            596,
            "    @Schema("
          ],
          [
            597,
            "            name = \"StudentStatisticsRanking\","
          ],
          [
            598,
            "            requiredProperties = {\"available\", \"page\", \"size\", \"total\", \"ownPosition\", \"rows\"})"
          ],
          [
            599,
            "    public record StudentStatisticsRankingResponse("
          ],
          [
            600,
            "            boolean available,"
          ],
          [
            601,
            "            int page,"
          ],
          [
            602,
            "            int size,"
          ],
          [
            603,
            "            int total,"
          ],
          [
            604,
            "            @Schema(nullable = true) Integer ownPosition,"
          ],
          [
            605,
            "            List<StudentStatisticsRankingRow> rows) {}"
          ]
        ]
      },
      {
        "title": "Страница с равными местами · ответ",
        "language": "json",
        "example": [
          "{",
          "  \"available\": true,",
          "  \"page\": 0,",
          "  \"size\": 20,",
          "  \"total\": 4,",
          "  \"ownPosition\": 2,",
          "  \"rows\": [",
          "    {",
          "      \"id\": \"110\",",
          "      \"name\": \"Участник А\",",
          "      \"position\": 1,",
          "      \"percentage\": 100,",
          "      \"isSelf\": false",
          "    },",
          "    {",
          "      \"id\": \"111\",",
          "      \"name\": \"Участник Б\",",
          "      \"position\": 2,",
          "      \"percentage\": 75,",
          "      \"isSelf\": false",
          "    },",
          "    {",
          "      \"id\": \"112\",",
          "      \"name\": \"Участник В\",",
          "      \"position\": 2,",
          "      \"percentage\": 75,",
          "      \"isSelf\": true",
          "    },",
          "    {",
          "      \"id\": \"113\",",
          "      \"name\": \"Участник Г\",",
          "      \"position\": 4,",
          "      \"percentage\": 0,",
          "      \"isSelf\": false",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  },
  {
    "id": "student-ranking-empty",
    "title": "Своё место и отсутствие данных",
    "description": "При 0/4 студент получает 0% и место. Если закрытых пар нет, дробь не определена. В примере из четырёх человек краткая сводка может сохранить participantCount = 4, но вернёт пустую позицию. Отдельный список требует доступного собственного места. Если его нет или состав скрыт, ответ списка содержит total = 0 и пустые строки.",
    "frames": [
      {
        "title": "Сводка знает собственное место и размер состава",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/OwnRankCalculator.java",
        "language": "java",
        "lines": [
          [
            129,
            "    public static Rank summarize(Long ownParticipantId, List<RankedParticipant> rankedRoster) {"
          ],
          [
            130,
            "        validateOwnId(ownParticipantId);"
          ],
          [
            131,
            "        if (rankedRoster == null) {"
          ],
          [
            132,
            "            throw StudentProjectionException.invalidRoster(\"ranked roster is null\");"
          ],
          [
            133,
            "        }"
          ],
          [
            134,
            "        RankedParticipant own ="
          ],
          [
            135,
            "                rankedRoster.stream()"
          ],
          [
            136,
            "                        .filter(participant -> participant.participantId() == ownParticipantId)"
          ],
          [
            137,
            "                        .findFirst()"
          ],
          [
            138,
            "                        .orElse(null);"
          ],
          [
            139,
            "        if (own == null || own.position() == null) {"
          ],
          [
            140,
            "            return Rank.unavailable(rankedRoster.size());"
          ],
          [
            141,
            "        }"
          ],
          [
            142,
            "        return new Rank(own.position(), rankedRoster.size(), true);"
          ],
          [
            143,
            "    }"
          ],
          [
            144,
            ""
          ],
          [
            145,
            "    private static boolean hasRatio(AttendanceMetricCalculator.Metrics metrics) {"
          ],
          [
            146,
            "        return metrics != null && metrics.heldCount() > 0;"
          ],
          [
            147,
            "    }"
          ]
        ]
      },
      {
        "title": "Mobile BFF передаёт только краткую сводку",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java",
        "language": "java",
        "lines": [
          [
            631,
            "    private static StudentStatisticsResponse statisticsResponse("
          ],
          [
            632,
            "            StudentAttendanceProjectionResponse response, SemesterResponse semester) {"
          ],
          [
            633,
            "        return new StudentStatisticsResponse("
          ],
          [
            634,
            "                metricSet(response.getMetrics()),"
          ],
          [
            635,
            "                rank(response.getOwnRank()),"
          ],
          [
            779,
            "    private static StudentAttendanceOwnRank rank("
          ],
          [
            780,
            "            ru.rutcampustrack.attendance.grpc.StudentAttendanceOwnRank source) {"
          ],
          [
            781,
            "        return new StudentAttendanceOwnRank("
          ],
          [
            782,
            "                source.hasPosition() ? source.getPosition() : null,"
          ],
          [
            783,
            "                source.getParticipantCount(),"
          ],
          [
            784,
            "                source.getAvailable());"
          ],
          [
            785,
            "    }"
          ]
        ]
      },
      {
        "title": "Собственное место · фрагмент статистики",
        "language": "json",
        "example": [
          "{",
          "  \"ownRank\": {",
          "    \"position\": 2,",
          "    \"participantCount\": 4,",
          "    \"available\": true",
          "  }",
          "}"
        ]
      },
      {
        "title": "До первой закрытой пары · фрагмент статистики",
        "language": "json",
        "example": [
          "{",
          "  \"ownRank\": {",
          "    \"position\": null,",
          "    \"participantCount\": 4,",
          "    \"available\": false",
          "  }",
          "}"
        ]
      },
      {
        "title": "Отдельному списку нужно доступное своё место",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            231,
            "        StudentProjectionScopeResponse scope ="
          ],
          [
            232,
            "                academicGrpcClient.resolveStudentProjectionScope(semesterId);"
          ],
          [
            233,
            "        validateScope(scope, claims, semesterId, null);"
          ],
          [
            234,
            "        if (!rankingVisible(scope)) {"
          ],
          [
            235,
            "            return RankingPage.unavailable(requestedPage == null ? 0 : requestedPage, pageSize);"
          ],
          [
            236,
            "        }"
          ],
          [
            237,
            ""
          ],
          [
            238,
            "        Map<Long, AcademicSubjectInfo> subjects = subjectMap(scope);"
          ],
          [
            239,
            "        List<Lesson> ownLessons = loadLessons(scope, semesterId, subjects, null, Set.of());"
          ],
          [
            240,
            "        RankedCohort cohort ="
          ],
          [
            241,
            "                rankedCohort(scope, claims.userId(), ownLessons, subjects, semesterId);"
          ],
          [
            242,
            "        if (!cohort.ownRank().available()) {"
          ],
          [
            243,
            "            return RankingPage.unavailable(requestedPage == null ? 0 : requestedPage, pageSize);"
          ],
          [
            244,
            "        }"
          ],
          [
            245,
            ""
          ],
          [
            1473,
            "        private static RankingPage unavailable(int page, int size) {"
          ],
          [
            1474,
            "            return new RankingPage(false, page, size, 0, null, List.of());"
          ],
          [
            1475,
            "        }"
          ]
        ]
      },
      {
        "title": "До первой закрытой пары · ответ списка",
        "language": "json",
        "example": [
          "{",
          "  \"available\": false,",
          "  \"page\": 0,",
          "  \"size\": 20,",
          "  \"total\": 0,",
          "  \"ownPosition\": null,",
          "  \"rows\": []",
          "}"
        ]
      }
    ]
  },
  {
    "id": "student-ranking-headman",
    "title": "Что может увидеть староста",
    "description": "В карточке студента староста или помощник с VIEW_STATS может увидеть краткое место в своей группе. Academic проверяет членство выбранного студента в периоде. Для бывшего участника допускается чтение истории в своей группе, а состав чужой текущей группы скрыт. Ответ карточки возвращает место, число участников, доступность и видимость. Именного списка рейтинга в этом ответе нет.",
    "frames": [
      {
        "title": "Право чтения своей активной группы",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/HeadmanStatsScopeReadService.java",
        "language": "java",
        "lines": [
          [
            111,
            "    private LocalRead readLocal(InternalJwtClaims actor, HeadmanStatsScopeRequest request) {"
          ],
          [
            112,
            "        long groupId = request.getGroupId();"
          ],
          [
            113,
            "        fences.requireUserReadable(actor.userId());"
          ],
          [
            114,
            "        fences.requireGroupReadable(groupId);"
          ],
          [
            115,
            "        if (!authority.allows(actor, groupId, \"VIEW_STATS\")) {"
          ],
          [
            116,
            "            throw Status.PERMISSION_DENIED"
          ],
          [
            117,
            "                    .withDescription(\"current own-group VIEW_STATS required\")"
          ],
          [
            118,
            "                    .asRuntimeException();"
          ],
          [
            119,
            "        }"
          ],
          [
            120,
            "        var group ="
          ],
          [
            121,
            "                groups.findById(groupId)"
          ],
          [
            122,
            "                        .filter(value -> value.isActive())"
          ],
          [
            123,
            "                        .orElseThrow("
          ],
          [
            124,
            "                                () ->"
          ],
          [
            125,
            "                                        Status.PERMISSION_DENIED"
          ],
          [
            126,
            "                                                .withDescription(\"current group unavailable\")"
          ],
          [
            127,
            "                                                .asRuntimeException());"
          ]
        ]
      },
      {
        "title": "Членство выбранного студента на датах семестра",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/grpc/HeadmanStatsScopeReadService.java",
        "language": "java",
        "lines": [
          [
            200,
            "                    if (row.getGroupId() == groupId"
          ],
          [
            201,
            "                            && !row.getJoinedAt().isAfter(selected.getDateTo())"
          ],
          [
            202,
            "                            && (row.getLeftAt() == null"
          ],
          [
            203,
            "                                    || row.getLeftAt().isAfter(row.getJoinedAt())"
          ],
          [
            204,
            "                                            && row.getLeftAt().isAfter(selected.getDateFrom())))"
          ],
          [
            205,
            "                        historical = true;"
          ],
          [
            206,
            "                    openSeen = row.getLeftAt() == null;"
          ],
          [
            207,
            "                    previousEnd = row.getLeftAt();"
          ],
          [
            208,
            "                }"
          ],
          [
            209,
            "            }"
          ],
          [
            210,
            "            if (selected != null ? !historical : !current) {"
          ],
          [
            211,
            "                throw Status.NOT_FOUND"
          ],
          [
            212,
            "                        .withDescription(\"student outside selected group scope\")"
          ],
          [
            213,
            "                        .asRuntimeException();"
          ],
          [
            214,
            "            }"
          ]
        ]
      },
      {
        "title": "Чужой текущий состав скрывается",
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
            453,
            "        }"
          ],
          [
            454,
            ""
          ],
          [
            455,
            "        // No foreign current-group or cohort data is needed for this admitted historical target."
          ],
          [
            456,
            "        if (allowedGroupId != null && !allowedGroupId.equals(grantGroupId)) {"
          ],
          [
            457,
            "            return RankDecision.hidden();"
          ],
          [
            458,
            "        }"
          ],
          [
            459,
            ""
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
          ]
        ]
      },
      {
        "title": "Проверка области краткого рейтинга старосты",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            828,
            "    /** Internal rank calculation for a target already admitted by live own-group authority. */"
          ],
          [
            829,
            "    public Rank rankForHeadman("
          ],
          [
            830,
            "            StudentProjectionScopeResponse scope, long studentId, long semesterId, long groupId) {"
          ],
          [
            831,
            "        if (scope == null"
          ],
          [
            832,
            "                || scope.getStudentId() != studentId"
          ],
          [
            833,
            "                || scope.getSemesterId() != semesterId"
          ],
          [
            834,
            "                || scope.hasRankGroupId() && scope.getRankGroupId() != groupId"
          ],
          [
            835,
            "                || scope.getOwnMembershipSegmentsList().stream()"
          ],
          [
            836,
            "                        .anyMatch(segment -> segment.getGroupId() != groupId)"
          ],
          [
            837,
            "                || scope.getSubjectsList().stream()"
          ],
          [
            838,
            "                        .anyMatch(subject -> subject.getGroupId() != groupId)"
          ],
          [
            839,
            "                || scope.getRankSubjectsList().stream()"
          ],
          [
            840,
            "                        .anyMatch(subject -> subject.getGroupId() != groupId)) {"
          ],
          [
            841,
            "            throw StudentProjectionException.invalidRoster(\"Headman target rank scope mismatch\");"
          ],
          [
            842,
            "        }"
          ],
          [
            843,
            "        if (scope.getRankVisibility()"
          ],
          [
            844,
            "                == StudentProjectionRankVisibility.STUDENT_PROJECTION_RANK_VISIBILITY_HIDDEN) {"
          ],
          [
            845,
            "            if (scope.getRankEligible()"
          ],
          [
            846,
            "                    || scope.hasRankGroupId()"
          ],
          [
            847,
            "                    || scope.getActiveRosterUserIdsCount() != 0"
          ],
          [
            848,
            "                    || scope.getRankSubjectsCount() != 0) {"
          ],
          [
            849,
            "                throw StudentProjectionException.invalidRoster("
          ],
          [
            850,
            "                        \"Hidden target rank contains a cohort\");"
          ],
          [
            851,
            "            }"
          ],
          [
            852,
            "            return new Rank(null, 0, false);"
          ]
        ]
      },
      {
        "title": "Ответ карточки и контроль неизменности области",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/HeadmanStatsService.java",
        "language": "java",
        "lines": [
          [
            995,
            "        var fresh ="
          ],
          [
            996,
            "                requireScope(capture.context.groupId(), capture.context.semesterId(), studentId);"
          ],
          [
            997,
            "        if (!fresh.hasStudentRankScope() || !fresh.getStudentRankScope().equals(rankScope)) {"
          ],
          [
            998,
            "            throw new AcademicServiceUnavailableException(\"Target rank authority changed\");"
          ],
          [
            999,
            "        }"
          ],
          [
            1000,
            "        return new HeadmanStatsStudentDetailResponse.Rank("
          ],
          [
            1001,
            "                rank.position(),"
          ],
          [
            1002,
            "                rank.participantCount(),"
          ],
          [
            1003,
            "                rank.available(),"
          ],
          [
            1004,
            "                rankScope.getRankVisibility()"
          ],
          [
            1005,
            "                                == ru.rutcampustrack.academic.grpc.StudentProjectionRankVisibility"
          ],
          [
            1006,
            "                                        .STUDENT_PROJECTION_RANK_VISIBILITY_HIDDEN"
          ],
          [
            1007,
            "                        ? HeadmanStatsStudentDetailResponse.Visibility.HIDDEN"
          ],
          [
            1008,
            "                        : HeadmanStatsStudentDetailResponse.Visibility.VISIBLE);"
          ]
        ]
      },
      {
        "title": "Карточка различает скрытое место и пустые данные",
        "path": "frontends/mobile-core/src/features/headman-stats/HeadmanStatsDetailPanel.vue",
        "language": "typescript",
        "lines": [
          [
            221,
            "        <h3>Рейтинг группы</h3>"
          ],
          [
            222,
            "        <p v-if=\"!detail.rank\">"
          ],
          [
            223,
            "          Сервер не передал рейтинг студента."
          ],
          [
            224,
            "        </p>"
          ],
          [
            225,
            "        <p v-else-if=\"detail.rank.visibility === 'HIDDEN'\">"
          ],
          [
            226,
            "          Рейтинг скрыт для этой истории."
          ],
          [
            227,
            "        </p>"
          ],
          [
            228,
            "        <p v-else-if=\"detail.rank.available\">"
          ],
          [
            229,
            "          {{ detail.rank.position }} из {{ detail.rank.participantCount }}"
          ],
          [
            230,
            "        </p>"
          ],
          [
            231,
            "        <p v-else>"
          ],
          [
            232,
            "          Нет данных для рейтинга."
          ],
          [
            233,
            "        </p>"
          ]
        ]
      }
    ]
  },
  {
    "id": "student-ranking-pages",
    "title": "Как раскрывается список",
    "description": "Приложение запрашивает список при раскрытии карточки и наличии текущей группы и сети. Без номера страницы сервер начинает со страницы с собственным местом. Клиент запрашивает по 20 строк и дочитывает остальные страницы и проверяет, что собственное место и общее число строк не изменились. API отдаёт имена только из разрешённого состава. Интерфейс сокращает имя визуально, сохраняя полное имя для чтения экранным диктором.",
    "frames": [
      {
        "title": "Имена фильтруются по разрешённым ID",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            290,
            "    private Map<Long, String> rosterNames(long groupId, List<Long> authorizedRoster) {"
          ],
          [
            291,
            "        GroupMembersResponse response = academicGrpcClient.getGroupMembers(groupId);"
          ],
          [
            292,
            "        if (response == null) {"
          ],
          [
            293,
            "            throw StudentProjectionException.invalidRoster(\"Academic returned no group roster\");"
          ],
          [
            294,
            "        }"
          ],
          [
            295,
            "        Set<Long> authorizedIds = new HashSet<>(authorizedRoster);"
          ],
          [
            296,
            "        Map<Long, String> names = new LinkedHashMap<>();"
          ],
          [
            297,
            "        for (StudentInfo student : response.getStudentsList()) {"
          ],
          [
            298,
            "            if (student == null || !authorizedIds.contains(student.getUserId())) {"
          ],
          [
            299,
            "                continue;"
          ],
          [
            300,
            "            }"
          ],
          [
            301,
            "            String name = student.getDisplayName() == null ? \"\" : student.getDisplayName().trim();"
          ],
          [
            302,
            "            if (student.getUserId() <= 0"
          ],
          [
            303,
            "                    || name.isBlank()"
          ],
          [
            304,
            "                    || names.putIfAbsent(student.getUserId(), name) != null) {"
          ],
          [
            305,
            "                throw StudentProjectionException.invalidRoster("
          ],
          [
            306,
            "                        \"Academic returned an invalid cohort identity\");"
          ],
          [
            307,
            "            }"
          ],
          [
            308,
            "        }"
          ],
          [
            309,
            "        if (!names.keySet().containsAll(authorizedIds)) {"
          ],
          [
            310,
            "            throw StudentProjectionException.invalidRoster("
          ],
          [
            311,
            "                    \"Academic omitted a current cohort identity\");"
          ],
          [
            312,
            "        }"
          ],
          [
            313,
            "        return Map.copyOf(names);"
          ]
        ]
      },
      {
        "title": "Отдельный запрос списка рейтинга",
        "path": "frontends/mobile-core/src/api/student-client.ts",
        "language": "typescript",
        "lines": [
          [
            183,
            "  async getStatisticsRanking("
          ],
          [
            184,
            "    semesterId: string,"
          ],
          [
            185,
            "    page: number | null = null,"
          ],
          [
            186,
            "    size = 20,"
          ],
          [
            187,
            "    signal?: AbortSignal,"
          ],
          [
            188,
            "  ): Promise<StudentStatisticsRankingResponse> {"
          ],
          [
            189,
            "    if ("
          ],
          [
            190,
            "      (page !== null && (!Number.isSafeInteger(page) || page < 0)) ||"
          ],
          [
            191,
            "      !Number.isSafeInteger(size) ||"
          ],
          [
            192,
            "      size < 1 ||"
          ],
          [
            193,
            "      size > 100"
          ],
          [
            194,
            "    )"
          ],
          [
            195,
            "      throw new RangeError('Некорректная страница рейтинга')"
          ],
          [
            196,
            "    const params = new URLSearchParams({ semesterId, size: String(size) })"
          ],
          [
            197,
            "    // Omission asks the server for the page containing the current student."
          ],
          [
            198,
            "    if (page !== null) params.set('page', String(page))"
          ],
          [
            199,
            "    return decodeStatisticsRanking("
          ],
          [
            200,
            "      await this.request<unknown>("
          ],
          [
            201,
            "        `/api/v1/student/statistics/ranking?${params.toString()}`,"
          ],
          [
            202,
            "        signal ? { signal } : undefined,"
          ],
          [
            203,
            "      ),"
          ],
          [
            204,
            "    )"
          ],
          [
            205,
            "  }"
          ]
        ]
      },
      {
        "title": "Первая страница по позиции своей строки",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/studentprojection/StudentAttendanceProjectionService.java",
        "language": "java",
        "lines": [
          [
            268,
            "        int ownIndex = -1;"
          ],
          [
            269,
            "        for (int index = 0; index < rows.size(); index++) {"
          ],
          [
            270,
            "            if (rows.get(index).studentId() == claims.userId()) {"
          ],
          [
            271,
            "                ownIndex = index;"
          ],
          [
            272,
            "                break;"
          ],
          [
            273,
            "            }"
          ],
          [
            274,
            "        }"
          ],
          [
            275,
            "        if (ownIndex < 0) {"
          ],
          [
            276,
            "            throw StudentProjectionException.invalidRoster("
          ],
          [
            277,
            "                    \"the current student is missing from the ranking\");"
          ],
          [
            278,
            "        }"
          ],
          [
            279,
            "        int page = requestedPage == null ? ownIndex / pageSize : requestedPage;"
          ],
          [
            280,
            "        long offset = (long) page * pageSize;"
          ],
          [
            281,
            "        List<RankingRow> pageRows ="
          ],
          [
            282,
            "                offset >= rows.size()"
          ],
          [
            283,
            "                        ? List.of()"
          ],
          [
            284,
            "                        : rows.subList("
          ],
          [
            285,
            "                                (int) offset, (int) Math.min(rows.size(), offset + pageSize));"
          ],
          [
            286,
            "        return new RankingPage("
          ],
          [
            287,
            "                true, page, pageSize, rows.size(), cohort.ownRank().position(), pageRows);"
          ],
          [
            288,
            "    }"
          ]
        ]
      },
      {
        "title": "Запрос после раскрытия и смена контекста",
        "path": "frontends/mobile-core/src/features/statistics/use-statistics.ts",
        "language": "typescript",
        "lines": [
          [
            290,
            "  const rankingVisible = ref(false)"
          ],
          [
            291,
            "  const rankingPage = ref<number | null>(null)"
          ],
          [
            292,
            "  watch("
          ],
          [
            293,
            "    () => studentFeatureScopeIdentity(scope.value),"
          ],
          [
            294,
            "    () => {"
          ],
          [
            295,
            "      selectedSubjectId.value = null"
          ],
          [
            296,
            "      selectedTypes.value = null"
          ],
          [
            297,
            "      range.value = 'weeks'"
          ],
          [
            298,
            "      rankingVisible.value = false"
          ],
          [
            299,
            "      rankingPage.value = null"
          ],
          [
            300,
            "    },"
          ],
          [
            301,
            "    { flush: 'sync' },"
          ],
          [
            302,
            "  )"
          ],
          [
            336,
            "  const rankingQueryKey = computed("
          ],
          [
            337,
            "    () => [...statisticsQueryKey(scope.value), 'ranking', rankingPage.value] as const,"
          ],
          [
            338,
            "  )"
          ],
          [
            339,
            "  const rankingQuery = useQuery<StudentStatisticsRankingResponse>({"
          ],
          [
            340,
            "    queryKey: rankingQueryKey,"
          ],
          [
            341,
            "    enabled: computed("
          ],
          [
            342,
            "      () =>"
          ],
          [
            343,
            "        rankingVisible.value && scopeReady.value && Boolean(scope.value.groupId) && !offline.value,"
          ],
          [
            344,
            "    ),"
          ],
          [
            345,
            "    queryFn: async ({ signal, queryKey: key }) => {"
          ],
          [
            346,
            "      const requestScope = scope.value"
          ],
          [
            347,
            "      if (!isStatisticsScopeReady(requestScope) || !requestScope.groupId)"
          ],
          [
            348,
            "        throw new StatisticsScopeError()"
          ],
          [
            349,
            "      const requestedPage = (key as typeof rankingQueryKey.value)[12]"
          ],
          [
            350,
            "      const assertCurrent = (): void => {"
          ],
          [
            351,
            "        if ("
          ],
          [
            352,
            "          signal.aborted ||"
          ],
          [
            353,
            "          offline.value ||"
          ],
          [
            354,
            "          JSON.stringify(rankingQueryKey.value) !== JSON.stringify(key) ||"
          ],
          [
            355,
            "          studentFeatureScopeIdentity(scope.value) !== studentFeatureScopeIdentity(requestScope)"
          ]
        ]
      },
      {
        "title": "Клиент дочитывает страницы и сверяет состав",
        "path": "frontends/mobile-core/src/features/statistics/use-statistics.ts",
        "language": "typescript",
        "lines": [
          [
            359,
            "      const loadPage = async (page: number | null): Promise<StudentStatisticsRankingResponse> => {"
          ],
          [
            360,
            "        const response = await api.getStatisticsRanking(requestScope.semesterId!, page, 20, signal)"
          ],
          [
            361,
            "        assertCurrent()"
          ],
          [
            362,
            "        if (response.rows.some((row) => row.isSelf !== (row.id === requestScope.userId)))"
          ],
          [
            363,
            "          throw new TypeError('Некорректный текущий участник рейтинга')"
          ],
          [
            364,
            "        return response"
          ],
          [
            365,
            "      }"
          ],
          [
            366,
            "      const first = await loadPage(requestedPage)"
          ],
          [
            367,
            "      if (!first.available || !first.total) return first"
          ],
          [
            368,
            "      const pages = new Map([[first.page, first.rows]])"
          ],
          [
            369,
            "      // Keep the server's bounded transport pages; the expanded card shows the whole group."
          ],
          [
            370,
            "      for (let page = 0; page < Math.ceil(first.total / first.size); page += 1) {"
          ],
          [
            371,
            "        if (pages.has(page)) continue"
          ],
          [
            372,
            "        const response = await loadPage(page)"
          ],
          [
            373,
            "        if ("
          ],
          [
            374,
            "          !response.available ||"
          ],
          [
            375,
            "          response.page !== page ||"
          ],
          [
            376,
            "          response.size !== first.size ||"
          ],
          [
            377,
            "          response.total !== first.total ||"
          ],
          [
            378,
            "          response.ownPosition !== first.ownPosition"
          ],
          [
            379,
            "        )"
          ],
          [
            380,
            "          throw new TypeError('Рейтинг группы изменился. Попробуй ещё раз.')"
          ],
          [
            381,
            "        pages.set(page, response.rows)"
          ],
          [
            382,
            "      }"
          ],
          [
            383,
            "      const rows = [...pages].sort(([left], [right]) => left - right).flatMap(([, rows]) => rows)"
          ],
          [
            384,
            "      if (rows.length !== first.total || new Set(rows.map((row) => row.id)).size !== rows.length)"
          ],
          [
            385,
            "        throw new TypeError('Рейтинг группы изменился. Попробуй ещё раз.')"
          ],
          [
            386,
            "      return { ...first, rows }"
          ]
        ]
      },
      {
        "title": "Собственная строка и имя в доступном списке",
        "path": "frontends/mobile-core/src/features/statistics/StatisticsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            198,
            "          <template v-else-if=\"rankingState.status === 'ready'\">"
          ],
          [
            199,
            "            <ol"
          ],
          [
            200,
            "              class=\"statistics-ranking__rows\""
          ],
          [
            201,
            "              tabindex=\"0\""
          ],
          [
            202,
            "              aria-label=\"Рейтинг группы\""
          ],
          [
            203,
            "            >"
          ],
          [
            204,
            "              <li"
          ],
          [
            205,
            "                v-for=\"row in rankingState.data.rows\""
          ],
          [
            206,
            "                :key=\"row.id\""
          ],
          [
            207,
            "                class=\"statistics-ranking__row\""
          ],
          [
            208,
            "                :data-self=\"row.isSelf\""
          ],
          [
            209,
            "              >"
          ],
          [
            210,
            "                <span>{{ row.position ?? '—' }}</span>"
          ],
          [
            211,
            "                <span class=\"statistics-ranking__name\">"
          ],
          [
            212,
            "                  <span aria-hidden=\"true\">{{ statisticsRankingName(row.name) }}</span>"
          ],
          [
            213,
            "                  <span class=\"statistics-visually-hidden\">{{ row.name }}</span>"
          ],
          [
            214,
            "                </span>"
          ],
          [
            215,
            "                <span>{{ displayPercent(row.percentage) }}</span>"
          ],
          [
            216,
            "              </li>"
          ],
          [
            217,
            "            </ol>"
          ]
        ]
      }
    ]
  }
]
