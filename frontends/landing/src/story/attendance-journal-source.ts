import type { StoryOperation } from './story-source'

export const attendanceJournalOperations: readonly StoryOperation[] = [
  {
    "id": "attendance-journal-headman",
    "title": "Староста · список одной пары",
    "description": "Староста открывает нужную дату. PWA и Mini App загружают пары своей группы из Schedule и выбирают конкретный ID. Затем Attendance возвращает список этой пары. Состав сверяется на дату пары и семестр. Отметки накладываются на список по ID студента, поэтому даже участник без записи остаётся в списке.",
    "frames": [
      {
        "title": "Дата выбирает список, ID открывает пару",
        "path": "frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue",
        "language": "typescript",
        "lines": [
          [
            1691,
            "    const next = await api.listLessons(props.groupId, selectedDate.value, selectedDate.value);"
          ],
          [
            1692,
            "    if (revision !== lessonsRevision) return;"
          ],
          [
            1693,
            "    lessons.value = next;"
          ],
          [
            1694,
            "    const wantsInitialLesson ="
          ],
          [
            1695,
            "      props.initialLessonId !== null && selectedDate.value === props.initialDate;"
          ],
          [
            1696,
            "    const requestedLesson = wantsInitialLesson"
          ],
          [
            1697,
            "      ? (next.find((lesson) => lesson.id === props.initialLessonId) ?? null)"
          ],
          [
            1698,
            "      : null;"
          ],
          [
            1699,
            "    const preferredLesson ="
          ],
          [
            1700,
            "      preferredLessonId === null"
          ],
          [
            1701,
            "        ? null"
          ],
          [
            1702,
            "        : (next.find((lesson) => lesson.id === preferredLessonId) ?? null);"
          ],
          [
            1712,
            "    const first = requestedLesson ?? preferredLesson ?? next[0];"
          ],
          [
            1713,
            "    if (first && canViewReport()) {"
          ],
          [
            1714,
            "      selectedLessonId.value = first.id;"
          ],
          [
            1715,
            "      resumeTransferStatusForSelectedLesson();"
          ],
          [
            1716,
            "      await loadReport(first.id);"
          ],
          [
            1717,
            "    } else if (first) {"
          ],
          [
            1718,
            "      selectedLessonId.value = first.id;"
          ],
          [
            1719,
            "      resumeTransferStatusForSelectedLesson();"
          ],
          [
            1720,
            "      report.value = null;"
          ],
          [
            1721,
            "    }"
          ]
        ]
      },
      {
        "title": "Schedule для пар, Attendance для состава",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts",
        "language": "typescript",
        "lines": [
          [
            282,
            "  listLessons("
          ],
          [
            283,
            "    groupId: number,"
          ],
          [
            284,
            "    dateFrom: string,"
          ],
          [
            285,
            "    dateTo: string,"
          ],
          [
            286,
            "  ): Promise<readonly HeadmanJournalLesson[]> {"
          ],
          [
            287,
            "    assertPositiveInteger(groupId, 'groupId')"
          ],
          [
            288,
            "    assertDate(dateFrom, 'dateFrom')"
          ],
          [
            289,
            "    assertDate(dateTo, 'dateTo')"
          ],
          [
            290,
            "    if (dateFrom > dateTo) throw new RangeError('dateFrom must be before or equal to dateTo')"
          ],
          [
            291,
            "    return this.requestPaged("
          ],
          [
            292,
            "      (page) => {"
          ],
          [
            293,
            "        const query = new URLSearchParams({"
          ],
          [
            294,
            "          dateFrom,"
          ],
          [
            295,
            "          dateTo,"
          ],
          [
            296,
            "          page: String(page),"
          ],
          [
            297,
            "          size: '100',"
          ],
          [
            298,
            "        })"
          ],
          [
            299,
            "        for (const status of ['PLANNED', 'ACTIVE', 'CLOSED', 'CANCELLED', 'TRANSFERRED'] as const) {"
          ],
          [
            300,
            "          query.append('status', status)"
          ],
          [
            301,
            "        }"
          ],
          [
            302,
            "        return `/api/schedule/groups/${groupId}/lessons?${query.toString()}`"
          ],
          [
            303,
            "      },"
          ],
          [
            304,
            "      'lessonResponseList',"
          ],
          [
            305,
            "      normalizeLesson,"
          ],
          [
            306,
            "    )"
          ],
          [
            307,
            "  }"
          ],
          [
            308,
            ""
          ],
          [
            309,
            "  async getLessonAttendance(lessonId: number): Promise<HeadmanJournalReport> {"
          ],
          [
            310,
            "    assertPositiveInteger(lessonId, 'lessonId')"
          ],
          [
            311,
            "    const value = await this.request<unknown>(`/api/attendance/reports/lesson/${lessonId}`)"
          ],
          [
            312,
            "    return normalizeReport(value, lessonId)"
          ],
          [
            313,
            "  }"
          ]
        ]
      },
      {
        "title": "Список группы на дату и семестр пары",
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
            525,
            "    }"
          ]
        ]
      },
      {
        "title": "Для каждого участника читается текущая отметка",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            381,
            "        Map<Long, AttendanceRecord> recordsByUserId = lessonAttendanceRecords(lessonId);"
          ],
          [
            382,
            "        Map<Long, Instant> attachmentExpiries ="
          ],
          [
            383,
            "                teacherManualAttachmentExpiries(lesson, members, recordsByUserId, teacherAttachments);"
          ],
          [
            384,
            ""
          ],
          [
            385,
            "        List<StudentAttendanceEntry> entries ="
          ],
          [
            386,
            "                members.getStudentsList().stream()"
          ],
          [
            387,
            "                        .map("
          ],
          [
            388,
            "                                student -> {"
          ],
          [
            389,
            "                                    Long uid = student.getUserId();"
          ],
          [
            390,
            "                                    AttendanceRecord rec = recordsByUserId.get(uid);"
          ],
          [
            391,
            "                                    AttendanceStatus status = rec == null ? null : rec.status();"
          ],
          [
            392,
            "                                    String source ="
          ],
          [
            393,
            "                                            (rec != null && rec.source() != null)"
          ],
          [
            394,
            "                                                    ? rec.source().name().toLowerCase()"
          ],
          [
            395,
            "                                                    : null;"
          ],
          [
            396,
            "                                    boolean excuse ="
          ],
          [
            397,
            "                                            rec != null"
          ],
          [
            398,
            "                                                    && rec.status() == AttendanceStatus.EXCUSED"
          ],
          [
            399,
            "                                                    && (!headmanActor || canManage);"
          ],
          [
            440,
            "                                    return new StudentAttendanceEntry("
          ],
          [
            441,
            "                                            uid,"
          ],
          [
            442,
            "                                            student.getDisplayName(),"
          ],
          [
            443,
            "                                            status == null ? null : status.name().toLowerCase(),"
          ],
          [
            444,
            "                                            status == null ? null : statusSymbol(status),"
          ],
          [
            445,
            "                                            source,"
          ]
        ]
      },
      {
        "title": "Форма ответа для выбранной пары",
        "path": "services/attendance-service/attendance-api-contract/src/main/java/ru/rutcampustrack/attendance/contract/dto/report/LessonAttendanceResponse.java",
        "language": "java",
        "lines": [
          [
            16,
            "    private final Long lessonId;"
          ],
          [
            17,
            "    private final Long groupId;"
          ],
          [
            18,
            "    private final Long subjectId;"
          ],
          [
            19,
            "    private final String lessonDate;"
          ],
          [
            20,
            "    private final Long semesterId;"
          ],
          [
            21,
            ""
          ],
          [
            22,
            "    /** Uppercase schedule state: PLANNED, ACTIVE, CLOSED, or CANCELLED. */"
          ],
          [
            23,
            "    private final String lessonStatus;"
          ],
          [
            24,
            ""
          ],
          [
            25,
            "    /** Server-side editability derived from lesson state and server clock. */"
          ],
          [
            26,
            "    private final boolean editable;"
          ],
          [
            27,
            ""
          ],
          [
            28,
            "    private final List<StudentAttendanceEntry> entries;"
          ]
        ]
      },
      {
        "title": "Список пары 731 · фрагмент ответа",
        "language": "json",
        "example": [
          "{",
          "  \"lessonId\": 731,",
          "  \"groupId\": 41,",
          "  \"subjectId\": 19,",
          "  \"lessonDate\": \"2026-09-14\",",
          "  \"semesterId\": 7,",
          "  \"lessonStatus\": \"ACTIVE\",",
          "  \"editable\": true,",
          "  \"entries\": [",
          "    {",
          "      \"userId\": 110,",
          "      \"displayName\": \"Участник А\",",
          "      \"status\": \"present\",",
          "      \"symbol\": \"+\",",
          "      \"source\": \"student_geo\"",
          "    },",
          "    {",
          "      \"userId\": 111,",
          "      \"displayName\": \"Участник Б\",",
          "      \"status\": null,",
          "      \"symbol\": null,",
          "      \"source\": null",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-permissions",
    "title": "Права и пустая отметка",
    "description": "Для чтения списка своей группы помощнику достаточно VIEW_STATS или MARK_ATTENDANCE. Управление обычной отметкой и уважительной причиной проверяется отдельно. В списке старосты null в поле status становится EMPTY. Отдельный ABSENT означает уже записанное отсутствие. Разрешение редактирования зависит от начала пары, её состояния, семестра и прав.",
    "frames": [
      {
        "title": "Проверка чтения в мобильном журнале",
        "path": "frontends/mobile-core/src/features/headman-journal/HeadmanJournalScreen.vue",
        "language": "typescript",
        "lines": [
          [
            303,
            "function selectLesson(lessonId: number): void {"
          ],
          [
            304,
            "  if (selectedLessonId.value === lessonId && report.value?.lessonId === lessonId) return;"
          ],
          [
            305,
            "  selectedLessonId.value = lessonId;"
          ],
          [
            306,
            "  resumeTransferStatusForSelectedLesson();"
          ],
          [
            307,
            "  if (canViewReport()) void loadReport(lessonId);"
          ],
          [
            308,
            "  else report.value = null;"
          ],
          [
            309,
            "}"
          ],
          [
            310,
            ""
          ],
          [
            311,
            "function hasAssistantPermission(permission: HeadmanAssistantPermission): boolean {"
          ],
          [
            312,
            "  return props.assistantPermissions === null || props.assistantPermissions.includes(permission);"
          ],
          [
            313,
            "}"
          ],
          [
            314,
            ""
          ],
          [
            315,
            "function canViewReport(): boolean {"
          ],
          [
            316,
            "  return hasAssistantPermission('MARK_ATTENDANCE') || hasAssistantPermission('VIEW_STATS');"
          ],
          [
            317,
            "}"
          ]
        ]
      },
      {
        "title": "Сервер проверяет чтение по роли и группе",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            2074,
            "    private void authorizeHeadmanOrTeacher(LessonResponse lesson) {"
          ],
          [
            2075,
            "        Long groupId = lesson.getGroupId();"
          ],
          [
            2076,
            "        Long subjectId = lesson.getSubjectId();"
          ],
          [
            2077,
            "        UserRole role = requestContext.getRole();"
          ],
          [
            2078,
            "        if (role == UserRole.STUDENT) {"
          ],
          [
            2079,
            "            if (!Objects.equals(requestContext.getGroupId(), groupId)) {"
          ],
          [
            2080,
            "                throw new AccessDeniedException(\"Cannot view data for another group\");"
          ],
          [
            2081,
            "            }"
          ],
          [
            2082,
            "            if (!academicGrpcClient.hasAssistantPermission(groupId, \"VIEW_STATS\")"
          ],
          [
            2083,
            "                    && !academicGrpcClient.hasAssistantPermission(groupId, \"MARK_ATTENDANCE\")) {"
          ],
          [
            2084,
            "                throw new AccessDeniedException(\"Отсутствует право VIEW_STATS\");"
          ],
          [
            2085,
            "            }"
          ],
          [
            2086,
            "            return;"
          ],
          [
            2087,
            "        }"
          ],
          [
            2088,
            "        if (role != UserRole.TEACHER) {"
          ],
          [
            2089,
            "            throw new AccessDeniedException(\"Access denied\");"
          ],
          [
            2090,
            "        }"
          ],
          [
            2091,
            ""
          ],
          [
            2092,
            "        authorizeTeacherLesson(lesson, requestContext.getUserId());"
          ],
          [
            2093,
            "    }"
          ]
        ]
      },
      {
        "title": "Клиент различает пустую строку и отсутствие",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-journal-client.ts",
        "language": "typescript",
        "lines": [
          [
            906,
            "function normalizeEntry(value: unknown, reportEditable: boolean): HeadmanJournalRosterEntry | null {"
          ],
          [
            907,
            "  if (!isRecord(value)) return null"
          ],
          [
            908,
            "  const userId = positiveInteger(value.userId)"
          ],
          [
            909,
            "  if (userId === null) return null"
          ],
          [
            910,
            "  const rawStatus ="
          ],
          [
            911,
            "    typeof value.status === 'string'"
          ],
          [
            912,
            "      ? value.status.trim().toUpperCase()"
          ],
          [
            913,
            "      : value.status === null"
          ],
          [
            914,
            "        ? ''"
          ],
          [
            915,
            "        : 'UNSUPPORTED'"
          ],
          [
            916,
            "  const status: HeadmanJournalAttendanceStatus ="
          ],
          [
            917,
            "    rawStatus === 'PRESENT'"
          ],
          [
            918,
            "      ? 'PRESENT'"
          ],
          [
            919,
            "      : rawStatus === 'ABSENT'"
          ],
          [
            920,
            "        ? 'ABSENT'"
          ],
          [
            921,
            "        : rawStatus === 'EXCUSED'"
          ],
          [
            922,
            "          ? 'EXCUSED'"
          ],
          [
            923,
            "          : rawStatus === 'CANCELLED'"
          ],
          [
            924,
            "            ? 'CANCELLED'"
          ],
          [
            925,
            "            : rawStatus === ''"
          ],
          [
            926,
            "              ? 'EMPTY'"
          ],
          [
            927,
            "              : 'UNSUPPORTED'"
          ],
          [
            928,
            "  return {"
          ],
          [
            929,
            "    userId,"
          ],
          [
            930,
            "    displayName: stringValue(value.displayName) ?? `Студент #${userId}`,"
          ],
          [
            931,
            "    status,"
          ],
          [
            932,
            "    symbol: stringValue(value.symbol),"
          ],
          [
            933,
            "    markRevision: stringValue(value.markRevision),"
          ],
          [
            934,
            "    approvedTicket: normalizeApprovedTicket(value.approvedTicket),"
          ]
        ]
      },
      {
        "title": "Состояние пары, семестра и возможность редактирования",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            330,
            "        JournalLessonPolicy.Timing timing = JournalLessonPolicy.requireTiming(lesson);"
          ],
          [
            331,
            "        GroupMembersResponse members = membersForLesson(lesson);"
          ],
          [
            332,
            "        String lessonStatus = timing.status();"
          ],
          [
            333,
            "        java.time.Instant now = clock.instant();"
          ],
          [
            334,
            "        boolean editable = !\"CANCELLED\".equals(lessonStatus) && timing.hasStarted(now);"
          ],
          [
            335,
            "        String blockedReason = editBlockedReason(timing, now);"
          ],
          [
            336,
            "        boolean headmanActor = !teacherBoundary && requestContext.getRole() == UserRole.STUDENT;"
          ],
          [
            337,
            "        var scope ="
          ],
          [
            338,
            "                headmanActor"
          ],
          [
            339,
            "                        ? academicGrpcClient.getHeadmanJournalSemesterState("
          ],
          [
            340,
            "                                lesson.getGroupId(), lesson.getSemesterId())"
          ],
          [
            341,
            "                        : null;"
          ],
          [
            342,
            "        if (headmanActor && (scope == null || scope.getId() != lesson.getSemesterId())) {"
          ],
          [
            343,
            "            throw new AcademicServiceUnavailableException("
          ],
          [
            344,
            "                    \"Academic returned mismatched journal semester\");"
          ],
          [
            345,
            "        }"
          ],
          [
            346,
            "        boolean archived = headmanActor && (scope.getArchived() || scope.getWriteBlocked());"
          ],
          [
            347,
            "        String semesterBlocked ="
          ],
          [
            348,
            "                !headmanActor"
          ],
          [
            349,
            "                        ? null"
          ],
          [
            350,
            "                        : scope.getArchived()"
          ],
          [
            351,
            "                                ? \"Семестр архивирован\""
          ],
          [
            352,
            "                                : scope.getWriteBlocked()"
          ],
          [
            353,
            "                                        ? \"Изменения в семестре заблокированы\""
          ],
          [
            354,
            "                                        : null;"
          ],
          [
            355,
            "        boolean canMark ="
          ],
          [
            356,
            "                !headmanActor"
          ],
          [
            357,
            "                        || academicGrpcClient.hasAssistantPermission("
          ],
          [
            358,
            "                                lesson.getGroupId(), \"MARK_ATTENDANCE\");"
          ],
          [
            359,
            "        boolean canManage ="
          ],
          [
            360,
            "                !headmanActor"
          ],
          [
            361,
            "                        || academicGrpcClient.hasAssistantPermission("
          ],
          [
            362,
            "                                lesson.getGroupId(), \"MANAGE_EXCUSES\");"
          ],
          [
            470,
            "                lesson.getSemesterId() > 0 ? lesson.getSemesterId() : null,"
          ],
          [
            471,
            "                lessonStatus,"
          ],
          [
            472,
            "                headmanActor"
          ],
          [
            473,
            "                        ? !archived"
          ],
          [
            474,
            "                                && (canMark || canManage)"
          ],
          [
            475,
            "                                && editable"
          ],
          [
            476,
            "                                && !\"PENDING\".equalsIgnoreCase(lesson.getTransferState())"
          ],
          [
            477,
            "                        : editable,"
          ],
          [
            478,
            "                entries);"
          ]
        ]
      },
      {
        "title": "Кнопки старосты учитывают права и тип отметки",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-manual-marks.ts",
        "language": "typescript",
        "lines": [
          [
            254,
            "  function canWrite(entry: HeadmanJournalRosterEntry, choice: ManualChoice): boolean {"
          ],
          [
            255,
            "    const permissions = options.scope().permissions"
          ],
          [
            256,
            "    const allowed = (permission: HeadmanAssistantPermission) =>"
          ],
          [
            257,
            "      permissions === null || permissions.includes(permission)"
          ],
          [
            258,
            "    return ("
          ],
          [
            259,
            "      reportOwned.value &&"
          ],
          [
            260,
            "      rowsById.value.get(entry.userId) === entry &&"
          ],
          [
            261,
            "      entry.editable &&"
          ],
          [
            262,
            "      markCondition(entry) !== null &&"
          ],
          [
            263,
            "      allowed(entry.status === 'EXCUSED' ? 'MANAGE_EXCUSES' : 'MARK_ATTENDANCE') &&"
          ],
          [
            264,
            "      (choice !== 'EXCUSED' || allowed('MANAGE_EXCUSES')) &&"
          ],
          [
            265,
            "      !staleRows.value.has(entry.userId)"
          ],
          [
            266,
            "    )"
          ],
          [
            267,
            "  }"
          ],
          [
            268,
            ""
          ],
          [
            269,
            "  function canChoose(entry: HeadmanJournalRosterEntry, choice: ManualChoice): boolean {"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-teacher",
    "title": "Преподаватель · выбор журнала",
    "description": "У преподавателя это журнал для чтения. Контекст задают семестр, группа и предмет. Внутри журнала можно переключить предмет и выбрать от одного до трёх типов пар. Если даты переданы, нужны обе границы внутри семестра. Mobile BFF проверяет действующее право на группу и предмет. Для истории он оставляет CLOSED, а отменённые пары показывает после их планового окончания.",
    "frames": [
      {
        "title": "Навигация передаёт семестр, группу, предмет и даты",
        "path": "frontends/mobile-core/src/shared/components/TeacherFeatureOwner.vue",
        "language": "typescript",
        "lines": [
          [
            650,
            "function openAttendanceJournal(query: TeacherJournalQuery): void {"
          ],
          [
            651,
            "  attendanceQuery.value = query;"
          ],
          [
            652,
            "  journalEntry.value = 'history';"
          ],
          [
            653,
            "  journalQuery.value = {"
          ],
          [
            654,
            "    semesterId: query.semesterId,"
          ],
          [
            655,
            "    groupId: query.groupId,"
          ],
          [
            656,
            "    subjectId: query.subjectId,"
          ],
          [
            657,
            "    ...(query.dateFrom !== undefined ? { dateFrom: query.dateFrom } : {}),"
          ],
          [
            658,
            "    ...(query.dateTo !== undefined ? { dateTo: query.dateTo } : {}),"
          ],
          [
            659,
            "    lessonTypes: [],"
          ],
          [
            660,
            "    pastOnly: true,"
          ],
          [
            661,
            "  };"
          ],
          [
            662,
            "  lessonId.value = null;"
          ],
          [
            663,
            "  navigation.push(nestedRoute('teacher-attendance', 'teacher-attendance/journal', 'detail'));"
          ],
          [
            664,
            "}"
          ]
        ]
      },
      {
        "title": "Предмет и типы можно выбрать в журнале",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue",
        "language": "typescript",
        "lines": [
          [
            505,
            "    <HeadmanStatsSelect"
          ],
          [
            506,
            "      v-if=\"subjects.length\""
          ],
          [
            507,
            "      label=\"Предмет\""
          ],
          [
            508,
            "      :model-value=\"String(effectiveQuery?.subjectId ?? '')\""
          ],
          [
            509,
            "      :options=\""
          ],
          [
            510,
            "        subjects.map((subject) => ({"
          ],
          [
            511,
            "          value: String(subject.subjectId),"
          ],
          [
            512,
            "          label: subject.subjectName,"
          ],
          [
            513,
            "        }))"
          ],
          [
            514,
            "      \""
          ],
          [
            515,
            "      @update:model-value=\"changeSubject\""
          ],
          [
            516,
            "    />"
          ],
          [
            527,
            "      <div"
          ],
          [
            528,
            "        class=\"teacher-journal__types\""
          ],
          [
            529,
            "        role=\"group\""
          ],
          [
            530,
            "        aria-label=\"Типы занятий\""
          ],
          [
            531,
            "      >"
          ],
          [
            532,
            "        <button"
          ],
          [
            533,
            "          v-for=\"type in availableTypes\""
          ],
          [
            534,
            "          :key=\"type\""
          ],
          [
            535,
            "          type=\"button\""
          ],
          [
            536,
            "          :aria-pressed=\"selectedTypes.includes(type)\""
          ],
          [
            537,
            "          :disabled=\"selectedTypes.length >= 3 && !selectedTypes.includes(type)\""
          ],
          [
            538,
            "          @click=\"toggleType(type)\""
          ],
          [
            539,
            "        >"
          ],
          [
            540,
            "          {{ teacherLessonTypeLabel(type) }}"
          ],
          [
            541,
            "        </button>"
          ]
        ]
      },
      {
        "title": "Запрос преподавателя с фильтрами",
        "path": "frontends/mobile-core/src/features/teacher/teacher-client.ts",
        "language": "typescript",
        "lines": [
          [
            433,
            "    const period = journalPeriod(query.dateFrom, query.dateTo)"
          ],
          [
            434,
            "    const page = query.page ?? 0"
          ],
          [
            435,
            "    const pageSize = query.pageSize ?? 100"
          ],
          [
            436,
            "    assertNonNegativeInteger(page, 'page')"
          ],
          [
            437,
            "    assertPageSize(pageSize)"
          ],
          [
            438,
            "    const params = new URLSearchParams({"
          ],
          [
            439,
            "      semesterId: String(query.semesterId),"
          ],
          [
            440,
            "      groupId: String(query.groupId),"
          ],
          [
            441,
            "      subjectId: String(query.subjectId),"
          ],
          [
            442,
            "      page: String(page),"
          ],
          [
            443,
            "      pageSize: String(pageSize),"
          ],
          [
            444,
            "    })"
          ],
          [
            445,
            "    if (query.includeMetrics !== undefined)"
          ],
          [
            446,
            "      params.set('includeMetrics', String(query.includeMetrics))"
          ],
          [
            447,
            "    if (query.metricsStudentId !== undefined)"
          ],
          [
            448,
            "      params.set('metricsStudentId', String(query.metricsStudentId))"
          ],
          [
            449,
            "    for (const lessonType of lessonTypes) params.append('lessonType', lessonType)"
          ],
          [
            450,
            "    if (query.pastOnly !== undefined) {"
          ],
          [
            451,
            "      if (typeof query.pastOnly !== 'boolean') throw new RangeError('pastOnly must be boolean')"
          ],
          [
            452,
            "      params.set('pastOnly', String(query.pastOnly))"
          ],
          [
            453,
            "    }"
          ],
          [
            454,
            "    if (period) {"
          ],
          [
            455,
            "      params.set('dateFrom', period.dateFrom)"
          ],
          [
            456,
            "      params.set('dateTo', period.dateTo)"
          ],
          [
            457,
            "    }"
          ],
          [
            458,
            "    return this.request<unknown>(`/api/v1/teacher/journal?${params.toString()}`).then("
          ],
          [
            459,
            "      normalizeJournal,"
          ]
        ]
      },
      {
        "title": "Авторизация и отбор реальных пар",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            488,
            "        List<String> selectedTypes = journalTypes(lessonTypes);"
          ],
          [
            489,
            ""
          ],
          [
            490,
            "        if (!currentTeacherSubjectKeys(claims)"
          ],
          [
            491,
            "                .contains(new TeacherSubjectKey(groupId, subjectId))) {"
          ],
          [
            492,
            "            throw new MobileBffException("
          ],
          [
            493,
            "                    HttpStatus.NOT_FOUND,"
          ],
          [
            494,
            "                    ProblemCode.OUT_OF_SCOPE,"
          ],
          [
            495,
            "                    \"Журнал недоступен в текущем scope\");"
          ],
          [
            496,
            "        }"
          ],
          [
            497,
            "        DateRange range ="
          ],
          [
            498,
            "                selectedPeriod("
          ],
          [
            499,
            "                        semesterRange(academic.teacherAssignmentsForSemester(semesterId)),"
          ],
          [
            500,
            "                        dateFrom,"
          ],
          [
            501,
            "                        dateTo);"
          ],
          [
            502,
            "        LessonsResponse scheduled = schedule.lessons(groupId, semesterId, range.from(), range.to());"
          ],
          [
            503,
            "        Instant teachingNow = educationalClock.instant();"
          ],
          [
            504,
            "        List<LessonResponse> concrete ="
          ],
          [
            505,
            "                scheduled.getLessonsList().stream()"
          ],
          [
            506,
            "                        .filter(lesson -> lesson.getId() > 0)"
          ],
          [
            507,
            "                        .filter("
          ],
          [
            508,
            "                                lesson ->"
          ],
          [
            509,
            "                                        lesson.getSemesterId() == semesterId"
          ],
          [
            510,
            "                                                && lesson.getGroupId() == groupId"
          ],
          [
            511,
            "                                                && lesson.getSubjectId() == subjectId"
          ],
          [
            512,
            "                                                && selectedTypes.stream()"
          ],
          [
            513,
            "                                                        .anyMatch("
          ],
          [
            514,
            "                                                                type ->"
          ],
          [
            515,
            "                                                                        type.equalsIgnoreCase("
          ],
          [
            516,
            "                                                                                lesson"
          ],
          [
            517,
            "                                                                                        .getLessonType())))"
          ],
          [
            518,
            "                        .filter(lesson -> inRange(lesson, range))"
          ],
          [
            519,
            "                        .filter(lesson -> !pastOnly || completedHistoryLesson(lesson, teachingNow))"
          ]
        ]
      },
      {
        "title": "Обе даты внутри семестра, история после окончания",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            581,
            "    private static boolean completedHistoryLesson(LessonResponse lesson, Instant now) {"
          ],
          [
            582,
            "        if (\"closed\".equalsIgnoreCase(lesson.getStatus())) return true;"
          ],
          [
            583,
            "        if (!\"cancelled\".equalsIgnoreCase(lesson.getStatus())) return false;"
          ],
          [
            584,
            "        try {"
          ],
          [
            585,
            "            Instant ended ="
          ],
          [
            586,
            "                    LocalDate.parse(lesson.getDate())"
          ],
          [
            587,
            "                            .atTime(LocalTime.parse(lesson.getEndTime()))"
          ],
          [
            588,
            "                            .atZone(ZoneId.of(\"Europe/Moscow\"))"
          ],
          [
            589,
            "                            .toInstant();"
          ],
          [
            590,
            "            return !ended.isAfter(now);"
          ],
          [
            1000,
            "    private static DateRange selectedPeriod(DateRange semester, LocalDate from, LocalDate to) {"
          ],
          [
            1001,
            "        if ((from == null) != (to == null)) {"
          ],
          [
            1002,
            "            throw invalidJournal(\"Укажи обе границы периода\");"
          ],
          [
            1003,
            "        }"
          ],
          [
            1004,
            "        if (from == null) return semester;"
          ],
          [
            1005,
            "        if (to.isBefore(from) || from.isBefore(semester.from()) || to.isAfter(semester.to())) {"
          ],
          [
            1006,
            "            throw invalidJournal(\"Период должен находиться внутри семестра\");"
          ],
          [
            1007,
            "        }"
          ],
          [
            1008,
            "        return new DateRange(from, to);"
          ],
          [
            1009,
            "    }"
          ],
          [
            1010,
            ""
          ],
          [
            1011,
            "    private static List<String> journalTypes(List<String> values) {"
          ],
          [
            1012,
            "        if (values == null"
          ],
          [
            1013,
            "                || values.isEmpty()"
          ],
          [
            1014,
            "                || values.size() > 3"
          ],
          [
            1015,
            "                || values.stream()"
          ],
          [
            1016,
            "                        .anyMatch("
          ],
          [
            1017,
            "                                value -> value == null || value.isBlank() || value.length() > 64)) {"
          ],
          [
            1018,
            "            throw invalidJournal(\"Выбери от одного до трёх типов занятий\");"
          ],
          [
            1019,
            "        }"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-matrix",
    "title": "Как строится таблица",
    "description": "Каждая колонка — физическая пара со своим ID. Порядок задают дата, время начала, номер пары и ID. Attendance читает состав отдельно для каждой даты и объединяет студентов по ID. Ячейки привязаны к ID пары, поэтому переход студента в другую группу не превращает отсутствие членства в отсутствие на занятии. Нажатие на заголовок выделяет строку или колонку.",
    "frames": [
      {
        "title": "Состав преподавателя читается для каждой пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            285,
            "        Instant teachingNow = educationalClock.instant();"
          ],
          [
            286,
            "        List<LessonRoster> rosters = new ArrayList<>();"
          ],
          [
            287,
            "        for (Long lessonId : lessonIds) {"
          ],
          [
            288,
            "            LessonAttendanceResponse attendance ="
          ],
          [
            289,
            "                    reportService.getTeacherLessonAttendance(lessonId, teacherId);"
          ],
          [
            290,
            "            LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);"
          ],
          [
            291,
            "            if (pastOnly && !completedHistoryLesson(lesson, teachingNow)) {"
          ],
          [
            292,
            "                throw Status.INVALID_ARGUMENT"
          ],
          [
            293,
            "                        .withDescription(\"past_only requires completed history lessons\")"
          ],
          [
            294,
            "                        .asRuntimeException();"
          ],
          [
            295,
            "            }"
          ],
          [
            296,
            "            Map<Long, TicketMarker> markers = ticketMarkers(lessonId);"
          ],
          [
            297,
            "            List<TeacherRosterEntry> entries ="
          ],
          [
            317,
            "            rosters.add(new LessonRoster(lesson, entries));"
          ],
          [
            318,
            "        }"
          ],
          [
            319,
            "        JournalScope scope = null;"
          ],
          [
            320,
            "        for (LessonRoster roster : rosters) {"
          ],
          [
            321,
            "            JournalScope lessonScope = JournalScope.from(roster.lesson());"
          ],
          [
            322,
            "            if (scope == null) {"
          ],
          [
            323,
            "                scope = lessonScope;"
          ],
          [
            324,
            "            } else if (!scope.equals(lessonScope)) {"
          ],
          [
            325,
            "                throw Status.INVALID_ARGUMENT"
          ],
          [
            326,
            "                        .withDescription(\"lesson_ids must share group, subject, and semester\")"
          ],
          [
            327,
            "                        .asRuntimeException();"
          ],
          [
            328,
            "            }"
          ]
        ]
      },
      {
        "title": "Объединение строк и связь ячеек с ID пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            335,
            "        Map<Long, StudentJournalAccumulator> students = new HashMap<>();"
          ],
          [
            336,
            "        List<TeacherLessonSummary> lessons = new ArrayList<>();"
          ],
          [
            337,
            "        for (LessonRoster roster : rosters) {"
          ],
          [
            338,
            "            lessons.add(toSummary(roster.lesson()));"
          ],
          [
            339,
            "            for (TeacherRosterEntry entry : roster.entries()) {"
          ],
          [
            340,
            "                StudentJournalAccumulator student ="
          ],
          [
            341,
            "                        students.computeIfAbsent("
          ],
          [
            342,
            "                                entry.getStudentId(),"
          ],
          [
            343,
            "                                id ->"
          ],
          [
            344,
            "                                        new StudentJournalAccumulator("
          ],
          [
            345,
            "                                                entry.getStudentId(), entry.getDisplayName()));"
          ],
          [
            346,
            "                student.cells()"
          ],
          [
            347,
            "                        .put("
          ],
          [
            348,
            "                                roster.lesson().getId(),"
          ],
          [
            349,
            "                                TeacherJournalCell.newBuilder()"
          ],
          [
            350,
            "                                        .setLessonId(roster.lesson().getId())"
          ],
          [
            351,
            "                                        .setStatus(entry.getStatus())"
          ],
          [
            352,
            "                                        .setSymbol(entry.getSymbol())"
          ],
          [
            353,
            "                                        .setSource(entry.getSource())"
          ],
          [
            354,
            "                                        .setRecordPresent(entry.getRecordPresent())"
          ],
          [
            355,
            "                                        .setPendingTicket(entry.getPendingTicket())"
          ],
          [
            356,
            "                                        .setAutoAbsent(entry.getAutoAbsent())"
          ],
          [
            357,
            "                                        .setTicketId(entry.getTicketId())"
          ],
          [
            358,
            "                                        .setExcuseType(entry.getExcuseType())"
          ],
          [
            359,
            "                                        .setExcuseReason(entry.getExcuseReason())"
          ],
          [
            360,
            "                                        .build());"
          ]
        ]
      },
      {
        "title": "Страницы объединяются по ID, колонки сортируются",
        "path": "frontends/mobile-core/src/features/teacher/teacher-journal-model.ts",
        "language": "typescript",
        "lines": [
          [
            56,
            "export function mergeJournalPage("
          ],
          [
            57,
            "  previous: TeacherJournalResponse | null,"
          ],
          [
            58,
            "  next: TeacherJournalResponse,"
          ],
          [
            59,
            "): TeacherJournalResponse {"
          ],
          [
            60,
            "  const lessons = new Map<number, TeacherDayLesson>()"
          ],
          [
            61,
            "  const students = new Map<number, TeacherJournalStudent>()"
          ],
          [
            62,
            "  for (const lesson of [...(previous?.lessons ?? []), ...next.lessons])"
          ],
          [
            63,
            "    lessons.set(lesson.id, lesson)"
          ],
          [
            64,
            "  for (const student of [...(previous?.students ?? []), ...next.students]) {"
          ],
          [
            65,
            "    const old = students.get(student.studentId)"
          ],
          [
            66,
            "    const cells = new Map<number, TeacherJournalCell>()"
          ],
          [
            67,
            "    for (const cell of [...(old?.cells ?? []), ...student.cells]) cells.set(cell.lessonId, cell)"
          ],
          [
            68,
            "    students.set(student.studentId, { ...student, cells: [...cells.values()] })"
          ],
          [
            69,
            "  }"
          ],
          [
            70,
            "  return {"
          ],
          [
            71,
            "    ...next,"
          ],
          [
            72,
            "    metrics: previous ? (previous.metrics ?? null) : (next.metrics ?? null),"
          ],
          [
            73,
            "    lessons: [...lessons.values()].sort("
          ],
          [
            74,
            "      (a, b) =>"
          ],
          [
            75,
            "        a.date.localeCompare(b.date) ||"
          ],
          [
            76,
            "        (a.startsAt ?? '').localeCompare(b.startsAt ?? '') ||"
          ],
          [
            77,
            "        a.lessonNumber - b.lessonNumber ||"
          ],
          [
            78,
            "        a.id - b.id,"
          ],
          [
            79,
            "    ),"
          ],
          [
            80,
            "    students: [...students.values()].sort((a, b) =>"
          ],
          [
            81,
            "      a.displayName.localeCompare(b.displayName, 'ru'),"
          ],
          [
            82,
            "    ),"
          ],
          [
            83,
            "  }"
          ]
        ]
      },
      {
        "title": "Клик переключает выделение строки или колонки",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalTable.vue",
        "language": "typescript",
        "lines": [
          [
            24,
            "const rows = computed(() =>"
          ],
          [
            25,
            "  props.journal.students.map((student) => ({"
          ],
          [
            26,
            "    ...student,"
          ],
          [
            27,
            "    cellsById: new Map(student.cells.map((cell) => [cell.lessonId, cell])),"
          ],
          [
            28,
            "    metrics: props.journal.metrics?.students.find((row) => row.studentId === student.studentId),"
          ],
          [
            29,
            "  })),"
          ],
          [
            30,
            ");"
          ],
          [
            53,
            "function selectStudent(id: number): void {"
          ],
          [
            54,
            "  selectedStudentId.value = selectedStudentId.value === id ? null : id;"
          ],
          [
            55,
            "}"
          ],
          [
            56,
            "function selectLesson(id: number): void {"
          ],
          [
            57,
            "  selectedLessonId.value = selectedLessonId.value === id ? null : id;"
          ],
          [
            58,
            "}"
          ],
          [
            203,
            "            <th"
          ],
          [
            204,
            "              v-for=\"(lesson, index) in lessons\""
          ],
          [
            205,
            "              :key=\"lesson.id\""
          ],
          [
            206,
            "              :data-day-start=\"dayStart(index)\""
          ],
          [
            207,
            "              scope=\"col\""
          ],
          [
            208,
            "              :data-selected-column=\"selectedLessonId === lesson.id\""
          ],
          [
            209,
            "              :data-cancelled=\"lesson.cancelled\""
          ],
          [
            210,
            "              :title=\"lesson.cancellationReason ?? undefined\""
          ],
          [
            211,
            "            >"
          ],
          [
            212,
            "              <button"
          ],
          [
            213,
            "                class=\"teacher-journal__lesson-heading teacher-journal__date-heading\""
          ],
          [
            214,
            "                type=\"button\""
          ],
          [
            215,
            "                :aria-label=\"`Выделить ${lesson.lessonNumber}-ю пару, ${date(lesson.date)}, ${teacherLessonTypeLabel(lesson.lessonType)}`\""
          ],
          [
            216,
            "                :aria-pressed=\"selectedLessonId === lesson.id\""
          ],
          [
            217,
            "                @click=\"selectLesson(lesson.id)\""
          ],
          [
            218,
            "              >"
          ],
          [
            219,
            "                <time :datetime=\"lesson.date\">{{ date(lesson.date) }}</time>"
          ],
          [
            278,
            "          <tr"
          ],
          [
            279,
            "            v-for=\"student in rows\""
          ],
          [
            280,
            "            :key=\"student.studentId\""
          ],
          [
            281,
            "            :data-selected-row=\"selectedStudentId === student.studentId\""
          ],
          [
            282,
            "          >"
          ],
          [
            283,
            "            <th"
          ],
          [
            284,
            "              scope=\"row\""
          ],
          [
            285,
            "              class=\"teacher-journal__name\""
          ],
          [
            286,
            "            >"
          ],
          [
            287,
            "              <button"
          ],
          [
            288,
            "                type=\"button\""
          ],
          [
            289,
            "                class=\"teacher-journal__student-heading\""
          ],
          [
            290,
            "                :aria-label=\"student.displayName\""
          ],
          [
            291,
            "                :title=\"student.displayName\""
          ],
          [
            292,
            "                :aria-pressed=\"selectedStudentId === student.studentId\""
          ],
          [
            293,
            "                @click=\"selectStudent(student.studentId)\""
          ],
          [
            294,
            "              >"
          ],
          [
            295,
            "                {{ compactName(student.displayName) }}"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-cells",
    "title": "Отсутствие, заявка и прочерк",
    "description": "В примере участник Б состоял в группе на паре 731, но отметки нет. В закрытой паре преподаватель видит Н с recordPresent: false. Ожидающая уважительная заявка остаётся отдельной пометкой. На дате пары 733 участник Б уже вне состава, поэтому его ячейки нет и в таблице будет прочерк. Отменённая колонка тоже помечается отдельно.",
    "frames": [
      {
        "title": "Закрытая ячейка без записи получает отсутствие",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            296,
            "            Map<Long, TicketMarker> markers = ticketMarkers(lessonId);"
          ],
          [
            297,
            "            List<TeacherRosterEntry> entries ="
          ],
          [
            298,
            "                    attendance.getEntries().stream()"
          ],
          [
            299,
            "                            .map("
          ],
          [
            300,
            "                                    entry -> {"
          ],
          [
            301,
            "                                        TeacherRosterEntry value ="
          ],
          [
            302,
            "                                                toRosterEntry("
          ],
          [
            303,
            "                                                        entry, markers.get(entry.getUserId()));"
          ],
          [
            304,
            "                                        return pastOnly"
          ],
          [
            305,
            "                                                        && \"closed\""
          ],
          [
            306,
            "                                                                .equalsIgnoreCase("
          ],
          [
            307,
            "                                                                        lesson.getStatus())"
          ],
          [
            308,
            "                                                        && !value.getRecordPresent()"
          ],
          [
            309,
            "                                                ? value.toBuilder()"
          ],
          [
            310,
            "                                                        .setStatus(\"absent\")"
          ],
          [
            311,
            "                                                        .setSymbol(\"н\")"
          ],
          [
            312,
            "                                                        .setAutoAbsent(false)"
          ],
          [
            313,
            "                                                        .build()"
          ],
          [
            314,
            "                                                : value;"
          ],
          [
            315,
            "                                    })"
          ],
          [
            316,
            "                            .toList();"
          ]
        ]
      },
      {
        "title": "Заявка не заменяет статус отметки",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            817,
            "    private Map<Long, TicketMarker> ticketMarkers(long lessonId) {"
          ],
          [
            818,
            "        Map<Long, TicketMarker> result = new HashMap<>();"
          ],
          [
            819,
            "        for (ExcuseTicket ticket :"
          ],
          [
            820,
            "                excuseRepository.findByLessonIdsInAndStatusIn("
          ],
          [
            821,
            "                        List.of(lessonId), VISIBLE_TICKET_STATUSES)) {"
          ],
          [
            822,
            "            if (ticket == null"
          ],
          [
            823,
            "                    || ticket.getStudentId() == null"
          ],
          [
            824,
            "                    || ticket.getLessonIds() == null"
          ],
          [
            825,
            "                    || !ticket.getLessonIds().contains(lessonId)) {"
          ],
          [
            826,
            "                continue;"
          ],
          [
            827,
            "            }"
          ],
          [
            828,
            "            boolean pending = ticket.getStatus() == ExcuseTicketStatus.SUBMITTED;"
          ],
          [
            829,
            "            TicketMarker previous = result.get(ticket.getStudentId());"
          ],
          [
            830,
            "            if (previous == null || (pending && !previous.pending())) {"
          ],
          [
            831,
            "                result.put(ticket.getStudentId(), new TicketMarker(ticket.getId(), pending));"
          ],
          [
            832,
            "            }"
          ],
          [
            833,
            "        }"
          ],
          [
            834,
            "        return result;"
          ],
          [
            837,
            "    private TeacherRosterEntry toRosterEntry(StudentAttendanceEntry entry, TicketMarker marker) {"
          ],
          [
            838,
            "        boolean noTicketExcuse = marker == null && \"excused\".equalsIgnoreCase(entry.getStatus());"
          ],
          [
            839,
            "        boolean automaticAbsent ="
          ],
          [
            840,
            "                entry.getStatus() != null"
          ],
          [
            841,
            "                        && \"absent\".equalsIgnoreCase(entry.getStatus())"
          ],
          [
            842,
            "                        && \"auto_scheduler\".equalsIgnoreCase(entry.getSource());"
          ],
          [
            843,
            "        TeacherRosterEntry.Builder result ="
          ],
          [
            844,
            "                TeacherRosterEntry.newBuilder()"
          ],
          [
            845,
            "                        .setStudentId(value(entry.getUserId()))"
          ],
          [
            846,
            "                        .setDisplayName(value(entry.getDisplayName()))"
          ],
          [
            847,
            "                        .setStatus(value(entry.getStatus()))"
          ],
          [
            848,
            "                        .setSymbol(value(entry.getSymbol()))"
          ],
          [
            849,
            "                        // Preserve a machine-readable distinction for a manual EXCUSED"
          ],
          [
            850,
            "                        // record that has no persisted student ticket.  The UI must not"
          ],
          [
            851,
            "                        // invent a request card for this case."
          ],
          [
            852,
            "                        .setSource(noTicketExcuse ? \"NO_TICKET\" : value(entry.getSource()))"
          ],
          [
            853,
            "                        .setRecordPresent(entry.getStatus() != null)"
          ],
          [
            854,
            "                        .setPendingTicket(marker != null && marker.pending())"
          ],
          [
            855,
            "                        .setAutoAbsent(automaticAbsent)"
          ],
          [
            856,
            "                        .setTicketId(marker == null ? \"\" : value(marker.ticketId()))"
          ]
        ]
      },
      {
        "title": "Форма строки и ячейки преподавателя",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/TeacherApiModels.java",
        "language": "java",
        "lines": [
          [
            85,
            "    public record JournalCell("
          ],
          [
            86,
            "            String lessonId,"
          ],
          [
            87,
            "            String status,"
          ],
          [
            88,
            "            String symbol,"
          ],
          [
            89,
            "            String source,"
          ],
          [
            90,
            "            boolean recordPresent,"
          ],
          [
            91,
            "            boolean pendingTicket,"
          ],
          [
            92,
            "            boolean autoAbsent,"
          ],
          [
            93,
            "            String ticketId,"
          ],
          [
            94,
            "            String excuseType,"
          ],
          [
            95,
            "            String excuseReason) {}"
          ],
          [
            96,
            ""
          ],
          [
            97,
            "    @Schema(name = \"TeacherJournalStudent\")"
          ],
          [
            98,
            "    public record JournalStudent(String studentId, String displayName, List<JournalCell> cells) {}"
          ],
          [
            99,
            ""
          ],
          [
            100,
            "    @Schema(name = \"TeacherJournal\")"
          ],
          [
            101,
            "    public record JournalResponse("
          ],
          [
            102,
            "            List<DayLesson> lessons,"
          ],
          [
            103,
            "            List<JournalStudent> students,"
          ],
          [
            104,
            "            Instant serverNow,"
          ],
          [
            105,
            "            int page,"
          ],
          [
            106,
            "            int pageSize,"
          ],
          [
            107,
            "            int totalLessons,"
          ],
          [
            108,
            "            boolean hasMore,"
          ],
          [
            109,
            "            @Schema(nullable = true) JournalMetrics metrics) {"
          ]
        ]
      },
      {
        "title": "Таблица преподавателя · фрагмент ответа",
        "language": "json",
        "example": [
          "{",
          "  \"lessons\": [",
          "    {",
          "      \"id\": \"731\",",
          "      \"date\": \"2026-09-14\",",
          "      \"lessonNumber\": 2,",
          "      \"status\": \"closed\",",
          "      \"cancelled\": false",
          "    },",
          "    {",
          "      \"id\": \"733\",",
          "      \"date\": \"2026-09-16\",",
          "      \"lessonNumber\": 2,",
          "      \"status\": \"closed\",",
          "      \"cancelled\": false",
          "    }",
          "  ],",
          "  \"students\": [",
          "    {",
          "      \"studentId\": \"110\",",
          "      \"displayName\": \"Участник А\",",
          "      \"cells\": [",
          "        {",
          "          \"lessonId\": \"731\",",
          "          \"status\": \"present\",",
          "          \"symbol\": \"+\",",
          "          \"source\": \"student_geo\",",
          "          \"recordPresent\": true",
          "        },",
          "        {",
          "          \"lessonId\": \"733\",",
          "          \"status\": \"excused\",",
          "          \"symbol\": \"у\",",
          "          \"source\": \"NO_TICKET\",",
          "          \"recordPresent\": true",
          "        }",
          "      ]",
          "    },",
          "    {",
          "      \"studentId\": \"111\",",
          "      \"displayName\": \"Участник Б\",",
          "      \"cells\": [",
          "        {",
          "          \"lessonId\": \"731\",",
          "          \"status\": \"absent\",",
          "          \"symbol\": \"н\",",
          "          \"source\": null,",
          "          \"recordPresent\": false,",
          "          \"pendingTicket\": true,",
          "          \"autoAbsent\": false",
          "        }",
          "      ]",
          "    }",
          "  ]",
          "}"
        ]
      },
      {
        "title": "Пустая ячейка и отсутствие — разные случаи",
        "path": "frontends/mobile-core/src/features/teacher/teacher-journal-model.ts",
        "language": "typescript",
        "lines": [
          [
            31,
            "export function journalMark(cell: TeacherJournalCell | null | undefined): JournalMark | null {"
          ],
          [
            32,
            "  if (!cell) return null // No historical membership cell must never turn into an absence."
          ],
          [
            33,
            "  const status = cell.status?.toLowerCase()"
          ],
          [
            34,
            "  if (status === 'present' || cell.symbol === '+' || cell.symbol === 'б') return 'present'"
          ],
          [
            35,
            "  if (status === 'excused' || cell.symbol === 'у') return 'excused'"
          ],
          [
            36,
            "  if (status === 'absent' || cell.symbol === 'н' || !cell.recordPresent) return 'absent'"
          ],
          [
            37,
            "  return null"
          ],
          [
            38,
            "}"
          ]
        ]
      },
      {
        "title": "Подпись ячейки сохраняет пометку о заявке",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalTable.vue",
        "language": "typescript",
        "lines": [
          [
            133,
            "      ? 'Присутствовал'"
          ],
          [
            134,
            "      : mark === 'excused'"
          ],
          [
            135,
            "        ? 'Уважительная причина'"
          ],
          [
            136,
            "        : mark === 'absent'"
          ],
          [
            137,
            "          ? 'Отсутствовал'"
          ],
          [
            138,
            "          : cell"
          ],
          [
            139,
            "            ? 'Нет данных'"
          ],
          [
            140,
            "            : 'Нет строки списка на эту дату';"
          ],
          [
            141,
            "  return cell?.pendingTicket ? `${value}; заявление ожидает решения` : value;"
          ],
          [
            142,
            "}"
          ],
          [
            143,
            "function description(cell: TeacherJournalCell | undefined): string | undefined {"
          ],
          [
            144,
            "  return cell?.autoAbsent && journalMark(cell) === 'absent'"
          ],
          [
            145,
            "    ? 'Отсутствие отмечено автоматически при закрытии пары'"
          ],
          [
            146,
            "    : undefined;"
          ],
          [
            147,
            "}"
          ],
          [
            148,
            "function pendingAbsence(cell: TeacherJournalCell | undefined): boolean {"
          ],
          [
            149,
            "  return Boolean(cell?.pendingTicket && journalMark(cell) === 'absent');"
          ],
          [
            150,
            "}"
          ],
          [
            303,
            "              <span"
          ],
          [
            304,
            "                v-if=\"lesson.cancelled\""
          ],
          [
            305,
            "                class=\"teacher-journal__mark teacher-journal__mark--cancelled\""
          ],
          [
            306,
            "                role=\"img\""
          ],
          [
            307,
            "                aria-label=\"Отменена\""
          ],
          [
            308,
            "              ><span"
          ],
          [
            309,
            "                class=\"teacher-journal__cell-circle\""
          ],
          [
            310,
            "                aria-hidden=\"true\""
          ],
          [
            311,
            "              >—</span></span><button"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-pages",
    "title": "Страницы и общий итог",
    "description": "Клиент дочитывает все страницы по 100 пар. Только первая страница запрашивает метрики. Mobile BFF передаёт Attendance ID пар текущей страницы отдельно от полного набора после фильтров. Счётчики считают завершённые неотменённые пары по историческому составу. Пустая отметка участника входит в отсутствие, а пустое место вне состава не влияет на знаменатель.",
    "frames": [
      {
        "title": "Метрики запрашиваются с первой страницей",
        "path": "frontends/mobile-core/src/features/teacher/teacher-journal-model.ts",
        "language": "typescript",
        "lines": [
          [
            86,
            "export async function loadJournalPages("
          ],
          [
            87,
            "  api: Pick<TeacherApi, 'journal'>,"
          ],
          [
            88,
            "  query: TeacherJournalQuery,"
          ],
          [
            89,
            "  isCurrent: () => boolean,"
          ],
          [
            90,
            "): Promise<TeacherJournalResponse | null> {"
          ],
          [
            91,
            "  if (teacherJournalTypes(query).length === 0 || !isCurrent()) return null"
          ],
          [
            92,
            "  let merged: TeacherJournalResponse | null = null"
          ],
          [
            93,
            "  for (let page = 0; ; page += 1) {"
          ],
          [
            94,
            "    const next = await api.journal({"
          ],
          [
            95,
            "      ...query,"
          ],
          [
            96,
            "      page,"
          ],
          [
            97,
            "      pageSize: 100,"
          ],
          [
            98,
            "      includeMetrics: page === 0,"
          ],
          [
            99,
            "      metricsStudentId: undefined,"
          ],
          [
            100,
            "    })"
          ],
          [
            101,
            "    if (!isCurrent()) return null"
          ],
          [
            102,
            "    if ("
          ],
          [
            103,
            "      next.page !== page ||"
          ],
          [
            104,
            "      (next.hasMore &&"
          ],
          [
            105,
            "        (next.lessons.length === 0 || (page + 1) * next.pageSize >= next.totalLessons))"
          ],
          [
            106,
            "    )"
          ],
          [
            107,
            "      throw new Error('Сервер вернул несогласованные страницы журнала.')"
          ],
          [
            108,
            "    merged = mergeJournalPage(merged, next)"
          ],
          [
            109,
            "    if (!next.hasMore) {"
          ]
        ]
      },
      {
        "title": "ID страницы отдельно от ID периода",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java",
        "language": "java",
        "lines": [
          [
            529,
            "        if (includeMetrics && concrete.size() > 5_000) {"
          ],
          [
            530,
            "            throw invalidStats(\"Слишком много занятий для статистики журнала\");"
          ],
          [
            531,
            "        }"
          ],
          [
            532,
            "        long offset = (long) page * pageSize;"
          ],
          [
            533,
            "        int start = offset >= concrete.size() ? concrete.size() : (int) offset;"
          ],
          [
            534,
            "        int end = Math.min(concrete.size(), start + pageSize);"
          ],
          [
            535,
            "        List<LessonResponse> pageLessons = concrete.subList(start, end);"
          ],
          [
            536,
            "        if (pageLessons.isEmpty() && !includeMetrics) {"
          ],
          [
            537,
            "            return new TeacherApiModels.JournalResponse("
          ],
          [
            538,
            "                    List.of(), List.of(), Instant.now(), page, pageSize, concrete.size(), false);"
          ],
          [
            539,
            "        }"
          ],
          [
            540,
            "        List<Long> ids = pageLessons.stream().map(LessonResponse::getId).toList();"
          ],
          [
            541,
            "        TeacherJournalResponse response ="
          ],
          [
            542,
            "                includeMetrics"
          ],
          [
            543,
            "                        ? attendance.teacherJournal("
          ],
          [
            544,
            "                                ids,"
          ],
          [
            545,
            "                                pastOnly,"
          ],
          [
            546,
            "                                true,"
          ],
          [
            547,
            "                                concrete.stream().map(LessonResponse::getId).toList(),"
          ],
          [
            548,
            "                                metricsStudentId)"
          ],
          [
            549,
            "                        : pastOnly"
          ],
          [
            550,
            "                                ? attendance.teacherJournal(ids, true)"
          ],
          [
            551,
            "                                : attendance.teacherJournal(ids);"
          ]
        ]
      },
      {
        "title": "Весь набор передаётся в расчёт Attendance",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            225,
            "            if (request.getIncludeMetrics()) {"
          ],
          [
            226,
            "                if (!request.getAggregateLessonIdsList().containsAll(request.getLessonIdsList())) {"
          ],
          [
            227,
            "                    throw new BadRequestException(\"Display lessons are outside metrics context\");"
          ],
          [
            228,
            "                }"
          ],
          [
            229,
            "                var value ="
          ],
          [
            230,
            "                        reportService.getTeacherJournalMetrics("
          ],
          [
            231,
            "                                request.getAggregateLessonIdsList(),"
          ],
          [
            232,
            "                                claims.userId(),"
          ],
          [
            233,
            "                                request.hasMetricsStudentId()"
          ],
          [
            234,
            "                                        ? request.getMetricsStudentId()"
          ],
          [
            235,
            "                                        : null);"
          ],
          [
            236,
            "                TeacherJournalMetrics.Builder metrics ="
          ],
          [
            237,
            "                        TeacherJournalMetrics.newBuilder()"
          ],
          [
            238,
            "                                .addAllStudents("
          ],
          [
            239,
            "                                        value.students().stream()"
          ],
          [
            240,
            "                                                .map(this::toStudentStats)"
          ],
          [
            241,
            "                                                .toList())"
          ],
          [
            242,
            "                                .setServerNow(value.serverNow().toString());"
          ]
        ]
      },
      {
        "title": "Метрики только для закрытых пар, которые уже начались",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1277,
            "        List<LessonInfo> ordered = lessonIds.stream().map(byId::get).toList();"
          ],
          [
            1278,
            "        Map<Long, StatsCounter> series = new java.util.LinkedHashMap<>();"
          ],
          [
            1279,
            "        java.time.Instant now = clock.instant();"
          ],
          [
            1280,
            "        AttendanceAggregation aggregation ="
          ],
          [
            1281,
            "                aggregateAttendance("
          ],
          [
            1282,
            "                        ordered,"
          ],
          [
            1283,
            "                        Set.of(),"
          ],
          [
            1284,
            "                        true,"
          ],
          [
            1285,
            "                        selectedStudentId,"
          ],
          [
            1286,
            "                        series,"
          ],
          [
            1287,
            "                        lesson -> journalMetricsEligible(lesson, now));"
          ],
          [
            1288,
            "        if (selectedStudentId != null && !aggregation.students().containsKey(selectedStudentId)) {"
          ],
          [
            1289,
            "            throw new AccessDeniedException(\"Student is outside the dated journal roster\");"
          ],
          [
            1312,
            "    private static boolean journalMetricsEligible(LessonInfo lesson, java.time.Instant now) {"
          ],
          [
            1313,
            "        if (!isCompleted(lesson) || isCancelled(lesson)) return false;"
          ],
          [
            1314,
            "        try {"
          ],
          [
            1315,
            "            java.time.LocalDateTime startsAt = java.time.LocalDateTime.parse(lesson.getStartsAt());"
          ],
          [
            1316,
            "            if (!startsAt.toLocalDate().equals(parseLessonDate(lesson.getDate())))"
          ],
          [
            1317,
            "                throw new IllegalArgumentException(\"Inconsistent physical lesson timing\");"
          ],
          [
            1318,
            "            // Schedule owns CLOSED after physical end+grace; a future start is"
          ],
          [
            1319,
            "            // still ineligible even if its stored status is inconsistent."
          ],
          [
            1320,
            "            return !startsAt.atZone(JournalLessonPolicy.MOSCOW).toInstant().isAfter(now);"
          ],
          [
            1321,
            "        } catch (RuntimeException error) {"
          ],
          [
            1322,
            "            throw new ScheduleServiceUnavailableException(\"Journal lesson timing is unavailable\");"
          ],
          [
            1323,
            "        }"
          ]
        ]
      },
      {
        "title": "Пустая отметка учитывается внутри исторического состава",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1374,
            "            GroupMembersResponse roster ="
          ],
          [
            1375,
            "                    rosters.computeIfAbsent("
          ],
          [
            1376,
            "                            lesson.getGroupId()"
          ],
          [
            1377,
            "                                    + \":\""
          ],
          [
            1378,
            "                                    + lesson.getSemesterId()"
          ],
          [
            1379,
            "                                    + \":\""
          ],
          [
            1380,
            "                                    + lesson.getDate(),"
          ],
          [
            1381,
            "                            ignored -> historicalMembersFor(lesson));"
          ],
          [
            1390,
            "            for (StudentInfo member : roster.getStudentsList()) {"
          ],
          [
            1391,
            "                if (member.getUserId() <= 0) {"
          ],
          [
            1392,
            "                    throw new AcademicServiceUnavailableException("
          ],
          [
            1393,
            "                            \"Academic returned an invalid student identity\");"
          ],
          [
            1394,
            "                }"
          ],
          [
            1404,
            "                if (!counted) continue;"
          ],
          [
            1405,
            "                AttendanceRecord record = records.get(member.getUserId());"
          ],
          [
            1406,
            "                // Closed lessons normally have an AUTO_SCHEDULER ABSENT record."
          ],
          [
            1407,
            "                // Treat a missing historical mark as ABSENT so the denominator is"
          ],
          [
            1408,
            "                // the passed lesson roster, never the number of stored documents."
          ],
          [
            1409,
            "                AttendanceStatus status ="
          ],
          [
            1410,
            "                        record == null || record.status() == null"
          ],
          [
            1411,
            "                                ? AttendanceStatus.ABSENT"
          ],
          [
            1412,
            "                                : record.status();"
          ],
          [
            1413,
            "                if (student != null) student.counter.add(status);"
          ],
          [
            1414,
            "                group.counter.add(status);"
          ],
          [
            1415,
            "                if (lessonCounter != null"
          ],
          [
            1416,
            "                        && (selectedStudentId == null || selectedStudentId == member.getUserId())) {"
          ],
          [
            1417,
            "                    lessonCounter.add(status);"
          ],
          [
            1418,
            "                }"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-chart",
    "title": "График группы и студента",
    "description": "График берёт серверные метрики по ID каждой закрытой пары. В режиме группы первая пара даёт одно присутствие из двух. Выбор студента отправляет отдельный запрос с metricsStudentId. Тогда значения серии относятся к выбранному студенту. На паре 731 у участника А 1 из 1, у группы 1 из 2.",
    "frames": [
      {
        "title": "Выбор студента переключает запрос серии",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalScreen.vue",
        "language": "typescript",
        "lines": [
          [
            405,
            "function changeStudent(value: string): void {"
          ],
          [
            406,
            "  const selected = value === 'group' ? null : Number(value);"
          ],
          [
            407,
            "  if ("
          ],
          [
            408,
            "    selected !== null &&"
          ],
          [
            409,
            "    !completedJournal.value?.students.some((row) => row.studentId === selected)"
          ],
          [
            410,
            "  )"
          ],
          [
            411,
            "    return;"
          ],
          [
            412,
            "  studentId.value = selected;"
          ],
          [
            413,
            "  void loadChartMetrics();"
          ],
          [
            431,
            "  chartLoading.value = true;"
          ],
          [
            432,
            "  try {"
          ],
          [
            433,
            "    const response = await api.journal({"
          ],
          [
            434,
            "      ...query,"
          ],
          [
            435,
            "      page: 0,"
          ],
          [
            436,
            "      pageSize: 1,"
          ],
          [
            437,
            "      includeMetrics: true,"
          ],
          [
            438,
            "      metricsStudentId: selected ?? undefined,"
          ],
          [
            439,
            "    });"
          ]
        ]
      },
      {
        "title": "Серия и четыре показателя по ID пары",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/grpc/TeacherAttendanceReadGrpcService.java",
        "language": "java",
        "lines": [
          [
            243,
            "                if (value.selectedStudentId() != null)"
          ],
          [
            244,
            "                    metrics.setSelectedStudentId(value.selectedStudentId());"
          ],
          [
            245,
            "                for (var point : value.series())"
          ],
          [
            246,
            "                    metrics.addSeries("
          ],
          [
            247,
            "                            TeacherJournalLessonMetrics.newBuilder()"
          ],
          [
            248,
            "                                    .setLessonId(point.lessonId())"
          ],
          [
            249,
            "                                    .setPresent(toMetric(point.present()))"
          ],
          [
            250,
            "                                    .setPresentOrExcused(toMetric(point.presentOrExcused()))"
          ],
          [
            251,
            "                                    .setExcused(toMetric(point.excused()))"
          ],
          [
            252,
            "                                    .setAbsent(toMetric(point.absent())));"
          ],
          [
            253,
            "                response = response.toBuilder().setMetrics(metrics).build();"
          ]
        ]
      },
      {
        "title": "График может считать только одного участника",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/report/ReportService.java",
        "language": "java",
        "lines": [
          [
            1409,
            "                AttendanceStatus status ="
          ],
          [
            1410,
            "                        record == null || record.status() == null"
          ],
          [
            1411,
            "                                ? AttendanceStatus.ABSENT"
          ],
          [
            1412,
            "                                : record.status();"
          ],
          [
            1413,
            "                if (student != null) student.counter.add(status);"
          ],
          [
            1414,
            "                group.counter.add(status);"
          ],
          [
            1415,
            "                if (lessonCounter != null"
          ],
          [
            1416,
            "                        && (selectedStudentId == null || selectedStudentId == member.getUserId())) {"
          ],
          [
            1417,
            "                    lessonCounter.add(status);"
          ],
          [
            1418,
            "                }"
          ]
        ]
      },
      {
        "title": "Контракт серии в Mobile BFF",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/TeacherApiModels.java",
        "language": "java",
        "lines": [
          [
            123,
            "    public record JournalMetrics("
          ],
          [
            124,
            "            List<StatsStudent> students,"
          ],
          [
            125,
            "            List<JournalLessonMetrics> series,"
          ],
          [
            126,
            "            @Schema(nullable = true) String selectedStudentId,"
          ],
          [
            127,
            "            Instant serverNow) {}"
          ],
          [
            128,
            ""
          ],
          [
            129,
            "    @Schema(name = \"TeacherJournalLessonMetrics\")"
          ],
          [
            130,
            "    public record JournalLessonMetrics("
          ],
          [
            131,
            "            String lessonId,"
          ],
          [
            132,
            "            StatsMetric present,"
          ],
          [
            133,
            "            StatsMetric presentOrExcused,"
          ],
          [
            134,
            "            StatsMetric excused,"
          ],
          [
            135,
            "            StatsMetric absent) {}"
          ],
          [
            177,
            "    @Schema(name = \"TeacherStatsMetric\")"
          ],
          [
            178,
            "    public record StatsMetric(int numerator, int denominator, double percent) {}"
          ],
          [
            179,
            ""
          ],
          [
            180,
            "    @Schema(name = \"TeacherStatsStudent\")"
          ],
          [
            181,
            "    public record StatsStudent("
          ],
          [
            182,
            "            String studentId,"
          ],
          [
            183,
            "            String displayName,"
          ],
          [
            184,
            "            StatsMetric present,"
          ],
          [
            185,
            "            StatsMetric presentOrExcused,"
          ],
          [
            186,
            "            StatsMetric excused,"
          ],
          [
            187,
            "            StatsMetric absent) {}"
          ]
        ]
      },
      {
        "title": "Итоги строки и точка графика группы · фрагмент ответа",
        "language": "json",
        "example": [
          "{",
          "  \"metrics\": {",
          "    \"selectedStudentId\": null,",
          "    \"students\": [",
          "      {",
          "        \"studentId\": \"110\",",
          "        \"displayName\": \"Участник А\",",
          "        \"present\": {",
          "          \"numerator\": 1,",
          "          \"denominator\": 2,",
          "          \"percent\": 50.0",
          "        },",
          "        \"presentOrExcused\": {",
          "          \"numerator\": 2,",
          "          \"denominator\": 2,",
          "          \"percent\": 100.0",
          "        },",
          "        \"excused\": {",
          "          \"numerator\": 1,",
          "          \"denominator\": 2,",
          "          \"percent\": 50.0",
          "        },",
          "        \"absent\": {",
          "          \"numerator\": 0,",
          "          \"denominator\": 2,",
          "          \"percent\": 0.0",
          "        }",
          "      }",
          "    ],",
          "    \"series\": [",
          "      {",
          "        \"lessonId\": \"731\",",
          "        \"present\": {",
          "          \"numerator\": 1,",
          "          \"denominator\": 2,",
          "          \"percent\": 50.0",
          "        },",
          "        \"presentOrExcused\": {",
          "          \"numerator\": 1,",
          "          \"denominator\": 2,",
          "          \"percent\": 50.0",
          "        },",
          "        \"excused\": {",
          "          \"numerator\": 0,",
          "          \"denominator\": 2,",
          "          \"percent\": 0.0",
          "        },",
          "        \"absent\": {",
          "          \"numerator\": 1,",
          "          \"denominator\": 2,",
          "          \"percent\": 50.0",
          "        }",
          "      }",
          "    ]",
          "  }",
          "}"
        ]
      },
      {
        "title": "График связывает точки с закрытыми парами",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalChart.vue",
        "language": "typescript",
        "lines": [
          [
            74,
            "const bars = computed(() =>"
          ],
          [
            75,
            "  props.journal.lessons"
          ],
          [
            76,
            "    .filter("
          ],
          [
            77,
            "      (lesson) =>"
          ],
          [
            78,
            "        lesson.status === 'CLOSED' &&"
          ],
          [
            79,
            "        !lesson.cancelled &&"
          ],
          [
            80,
            "        lesson.date >= props.from &&"
          ],
          [
            81,
            "        lesson.date <= props.to,"
          ],
          [
            82,
            "    )"
          ],
          [
            83,
            "    .map((lesson) => {"
          ],
          [
            84,
            "      const counts ="
          ],
          [
            85,
            "        props.metrics?.selectedStudentId === props.studentId"
          ],
          [
            86,
            "          ? props.metrics.series.find((point) => point.lessonId === lesson.id)"
          ],
          [
            87,
            "          : undefined;"
          ],
          [
            88,
            "      return { lesson, counts };"
          ],
          [
            89,
            "    })"
          ],
          [
            90,
            "    .filter("
          ],
          [
            91,
            "      (bar): bar is typeof bar & { counts: NonNullable<typeof bar.counts> } => bar.counts != null,"
          ],
          [
            92,
            "    ),"
          ],
          [
            93,
            ");"
          ],
          [
            94,
            "const selectedBar = computed(() =>"
          ],
          [
            95,
            "  bars.value.find((bar) => bar.lesson.id === selectedLessonId.value),"
          ],
          [
            96,
            ");"
          ]
        ]
      }
    ]
  },
  {
    "id": "attendance-journal-threshold",
    "title": "Порог подсвечивает долю",
    "description": "У каждого из четырёх итоговых столбцов свой порог. Он принимает число от 0 до 100 с запятой или точкой. Если серверная доля равна порогу или выше, условие выполнено. Для участника А присутствие 50% проходит порог 50,0%. Пустой, неверный порог или знаменатель 0 оставляют ячейку без подсветки. Порог действует только на оформление и не меняет запрос, выборку или долю.",
    "frames": [
      {
        "title": "Четыре независимых поля порога",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalTable.vue",
        "language": "typescript",
        "lines": [
          [
            63,
            "const metricColumns = ["
          ],
          [
            64,
            "  { key: 'present', label: 'Присутствия', symbols: ['present'] },"
          ],
          [
            65,
            "  {"
          ],
          [
            66,
            "    key: 'presentOrExcused',"
          ],
          [
            67,
            "    label: 'Присутствия и уважительные причины',"
          ],
          [
            68,
            "    symbols: ['present', 'excused'],"
          ],
          [
            69,
            "  },"
          ],
          [
            70,
            "  { key: 'excused', label: 'Уважительные причины', symbols: ['excused'] },"
          ],
          [
            71,
            "  { key: 'absent', label: 'Отсутствия', symbols: ['absent'] },"
          ],
          [
            72,
            "] as const;"
          ],
          [
            73,
            "type MetricKey = (typeof metricColumns)[number]['key'];"
          ],
          [
            74,
            "function emptyThresholds(): Record<MetricKey, string> {"
          ],
          [
            75,
            "  return { present: '', presentOrExcused: '', excused: '', absent: '' };"
          ],
          [
            76,
            "}"
          ],
          [
            77,
            "const thresholdInputs = ref(emptyThresholds());"
          ],
          [
            78,
            "const thresholds = computed("
          ],
          [
            79,
            "  () =>"
          ],
          [
            80,
            "    Object.fromEntries("
          ],
          [
            81,
            "      metricColumns.map(({ key }) => [key, journalThreshold(thresholdInputs.value[key])]),"
          ],
          [
            82,
            "    ) as Record<MetricKey, ReturnType<typeof journalThreshold>>,"
          ],
          [
            83,
            ");"
          ]
        ]
      },
      {
        "title": "Десятичное число и допустимые границы",
        "path": "frontends/mobile-core/src/features/teacher/teacher-journal-threshold.ts",
        "language": "typescript",
        "lines": [
          [
            7,
            "/** Local display preference only; never changes the authoritative metric or query. */"
          ],
          [
            8,
            "export function journalThreshold(input: string): JournalThreshold {"
          ],
          [
            9,
            "  const text = input.trim()"
          ],
          [
            10,
            "  if (!text) return { state: 'empty', value: null }"
          ],
          [
            11,
            "  if (!/^(?:\\d+(?:[.,]\\d*)?|[.,]\\d+)$/.test(text)) return { state: 'invalid', value: null }"
          ],
          [
            12,
            "  const value = Number(text.replace(',', '.'))"
          ],
          [
            13,
            "  return Number.isFinite(value) && value >= 0 && value <= 100"
          ],
          [
            14,
            "    ? { state: 'valid', value }"
          ],
          [
            15,
            "    : { state: 'invalid', value: null }"
          ],
          [
            16,
            "}"
          ]
        ]
      },
      {
        "title": "Порог включительно и нейтральная ячейка без данных",
        "path": "frontends/mobile-core/src/features/teacher/teacher-journal-threshold.ts",
        "language": "typescript",
        "lines": [
          [
            18,
            "export function journalThresholdResult("
          ],
          [
            19,
            "  metric: TeacherStatsMetric | null | undefined,"
          ],
          [
            20,
            "  threshold: JournalThreshold,"
          ],
          [
            21,
            "): 'meets' | 'below' | null {"
          ],
          [
            22,
            "  if ("
          ],
          [
            23,
            "    threshold.state !== 'valid' ||"
          ],
          [
            24,
            "    !metric ||"
          ],
          [
            25,
            "    !Number.isFinite(metric.denominator) ||"
          ],
          [
            26,
            "    metric.denominator <= 0 ||"
          ],
          [
            27,
            "    !Number.isFinite(metric.percent) ||"
          ],
          [
            28,
            "    metric.percent < 0 ||"
          ],
          [
            29,
            "    metric.percent > 100"
          ],
          [
            30,
            "  )"
          ],
          [
            31,
            "    return null"
          ],
          [
            32,
            "  return metric.percent >= threshold.value ? 'meets' : 'below'"
          ],
          [
            33,
            "}"
          ]
        ]
      },
      {
        "title": "Отображение процента и прочерк при нуле",
        "path": "frontends/mobile-core/src/features/teacher/teacher-journal-model.ts",
        "language": "typescript",
        "lines": [
          [
            126,
            "/** Formatting only: percentages and their common denominator belong to Attendance. */"
          ],
          [
            127,
            "export function journalMetricPercent("
          ],
          [
            128,
            "  metric: import('./teacher-client').TeacherStatsMetric | null | undefined,"
          ],
          [
            129,
            "): string {"
          ],
          [
            130,
            "  return metric && metric.denominator > 0"
          ],
          [
            131,
            "    ? `${new Intl.NumberFormat('ru', { maximumFractionDigits: 1 }).format(metric.percent)} %`"
          ],
          [
            132,
            "    : '—'"
          ],
          [
            133,
            "}"
          ]
        ]
      },
      {
        "title": "Результат влияет только на оформление и подпись",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalTable.vue",
        "language": "typescript",
        "lines": [
          [
            339,
            "            <td"
          ],
          [
            340,
            "              v-for=\"metric in metricColumns\""
          ],
          [
            341,
            "              :key=\"metric.key\""
          ],
          [
            342,
            "              class=\"teacher-journal__percent\""
          ],
          [
            343,
            "              :data-threshold=\""
          ],
          [
            344,
            "                journalThresholdResult(student.metrics?.[metric.key], thresholds[metric.key])"
          ],
          [
            345,
            "              \""
          ],
          [
            346,
            "              :aria-label=\"metricLabel(metric.label, student.metrics?.[metric.key], metric.key)\""
          ],
          [
            347,
            "            >"
          ],
          [
            348,
            "              {{ journalMetricPercent(student.metrics?.[metric.key]) }}"
          ],
          [
            349,
            "            </td>"
          ],
          [
            365,
            "            <td"
          ],
          [
            366,
            "              v-for=\"metric in metricColumns\""
          ],
          [
            367,
            "              :key=\"metric.key\""
          ],
          [
            368,
            "              class=\"teacher-journal__threshold\""
          ],
          [
            369,
            "            >"
          ],
          [
            370,
            "              <label"
          ],
          [
            371,
            "                class=\"teacher-journal__sr\""
          ],
          [
            372,
            "                :for=\"`${tableId}-${metric.key}-input`\""
          ],
          [
            373,
            "              >{{ `Порог: ${metric.label}, проценты` }}</label>"
          ],
          [
            374,
            "              <input"
          ],
          [
            375,
            "                :id=\"`${tableId}-${metric.key}-input`\""
          ],
          [
            376,
            "                type=\"text\""
          ],
          [
            377,
            "                inputmode=\"decimal\""
          ],
          [
            378,
            "                autocomplete=\"off\""
          ],
          [
            379,
            "                class=\"teacher-journal__threshold-input\""
          ],
          [
            380,
            "                :value=\"thresholdInputs[metric.key]\""
          ],
          [
            381,
            "                :aria-label=\"`Порог: ${metric.label}, проценты`\""
          ],
          [
            382,
            "                :aria-invalid=\"thresholds[metric.key].state === 'invalid'\""
          ],
          [
            383,
            "                :aria-describedby=\"`${tableId}-${metric.key}-hint`\""
          ],
          [
            384,
            "                placeholder=\"—\""
          ],
          [
            385,
            "                @input=\"updateThreshold(metric.key, $event)\""
          ]
        ]
      }
    ]
  }
]
