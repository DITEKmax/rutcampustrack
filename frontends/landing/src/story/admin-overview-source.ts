import type { StoryOperation } from './story-source'

export const adminOverviewOperations: readonly StoryOperation[] = [
  {
    "id": "admin-overview-scale",
    "title": "Текущие числа и прирост",
    "description": "Администратор с DASHBOARD_VIEW видит число активных групп и уникальных пользователей с активными правами STUDENT и TEACHER. Прирост сравнивает эти числа с состоянием на конец вчерашнего дня по Москве. Известный ноль означает отсутствие изменения. Если прежнее состояние нельзя восстановить надёжно, API отдаёт UNKNOWN_HISTORY и пустые значения.",
    "frames": [
      {
        "title": "Панель и активность требуют DASHBOARD_VIEW",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardController.java",
        "language": "java",
        "lines": [
          [
            29,
            "    @Override"
          ],
          [
            30,
            "    @RequireRole("
          ],
          [
            31,
            "            value = {ADMIN},"
          ],
          [
            32,
            "            admin = {\"DASHBOARD_VIEW\"})"
          ],
          [
            33,
            "    public ResponseEntity<EntityModel<DashboardStatsResponse>> getStats() {"
          ],
          [
            34,
            "        return ResponseEntity.ok(dashboardService.getStats());"
          ],
          [
            35,
            "    }"
          ],
          [
            36,
            ""
          ],
          [
            37,
            "    @Override"
          ],
          [
            38,
            "    @RequireRole("
          ],
          [
            39,
            "            value = {ADMIN},"
          ],
          [
            40,
            "            admin = {\"DASHBOARD_VIEW\"})"
          ],
          [
            41,
            "    public ResponseEntity<DashboardActivityResponse> getActivity(String period, String bucket) {"
          ],
          [
            42,
            "        return ResponseEntity.ok(activityService.read(period, bucket));"
          ]
        ]
      },
      {
        "title": "Уникальные активные студенты, преподаватели и группы",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardScaleCounts.java",
        "language": "java",
        "lines": [
          [
            9,
            "/** The same canonical aggregate supplies current cards and prospective observations. */"
          ],
          [
            10,
            "record DashboardScaleCounts(long students, long teachers, long groups) {"
          ],
          [
            11,
            "    static DashboardScaleCounts read("
          ],
          [
            12,
            "            UserRoleGrantRepository grants, GroupRegistryReadRepository groups) {"
          ],
          [
            13,
            "        String active = RoleGrantStatus.ACTIVE.name().toLowerCase(Locale.ROOT);"
          ],
          [
            14,
            "        return new DashboardScaleCounts("
          ],
          [
            15,
            "                grants.countDistinctUsersByRoleAndStatus("
          ],
          [
            16,
            "                        UserRole.STUDENT.name().toLowerCase(Locale.ROOT), active),"
          ],
          [
            17,
            "                grants.countDistinctUsersByRoleAndStatus("
          ],
          [
            18,
            "                        UserRole.TEACHER.name().toLowerCase(Locale.ROOT), active),"
          ],
          [
            19,
            "                groups.countAll(null).activeCount());"
          ],
          [
            20,
            "    }"
          ],
          [
            21,
            "}"
          ]
        ]
      },
      {
        "title": "Московская дата и закрытая граница",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardScaleObservationService.java",
        "language": "java",
        "lines": [
          [
            115,
            "                    DashboardScaleCounts current = DashboardScaleCounts.read(grants, groups);"
          ],
          [
            116,
            "                    Instant capturedAt = clock.instant();"
          ],
          [
            117,
            "                    LocalDate today = capturedAt.atZone(MOSCOW).toLocalDate();"
          ],
          [
            118,
            "                    LocalDate baselineDate = today.minusDays(1);"
          ],
          [
            119,
            "                    LocalDate latestDay ="
          ],
          [
            120,
            "                            jdbc.queryForObject("
          ],
          [
            121,
            "                                    \"SELECT max(snapshot_day) FROM dashboard_scale_observations\""
          ],
          [
            122,
            "                                        + \" WHERE kind='SNAPSHOT'\","
          ],
          [
            123,
            "                                    LocalDate.class);"
          ],
          [
            124,
            "                    if (latestDay != null && latestDay.isAfter(today)) {"
          ],
          [
            125,
            "                        // A calendar regression cannot rewrite a previously closed baseline."
          ],
          [
            126,
            "                        return new Snapshot("
          ],
          [
            127,
            "                                current, DashboardDailyGrowthResponse.unknown(baselineDate));"
          ],
          [
            128,
            "                    }"
          ],
          [
            151,
            "                    var cutoff = today.atStartOfDay(MOSCOW).toOffsetDateTime();"
          ],
          [
            152,
            "                    boolean uncertain ="
          ],
          [
            153,
            "                            Boolean.TRUE.equals("
          ],
          [
            154,
            "                                    jdbc.queryForObject("
          ],
          [
            155,
            "                                            \"\"\""
          ],
          [
            156,
            "                                            SELECT EXISTS(SELECT 1 FROM dashboard_scale_observations"
          ],
          [
            157,
            "                                              WHERE kind='CHANGE' AND observed_at < ?"
          ],
          [
            158,
            "                                                AND (settled_at IS NULL OR settled_at >= ?))"
          ],
          [
            159,
            "                                            \"\"\","
          ],
          [
            160,
            "                                            Boolean.class,"
          ],
          [
            161,
            "                                            cutoff,"
          ],
          [
            162,
            "                                            cutoff));"
          ]
        ]
      },
      {
        "title": "Нет достоверной базы или известная разница",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardScaleObservationService.java",
        "language": "java",
        "lines": [
          [
            163,
            "                    var baseline ="
          ],
          [
            164,
            "                            jdbc.query("
          ],
          [
            165,
            "                                    \"\"\""
          ],
          [
            166,
            "                                    SELECT total_students,total_teachers,total_groups"
          ],
          [
            167,
            "                                      FROM dashboard_scale_observations"
          ],
          [
            168,
            "                                     WHERE kind='SNAPSHOT' AND snapshot_day < ?"
          ],
          [
            169,
            "                                     ORDER BY snapshot_day DESC LIMIT 1"
          ],
          [
            170,
            "                                    \"\"\","
          ],
          [
            171,
            "                                    (row, index) ->"
          ],
          [
            172,
            "                                            new DashboardScaleCounts("
          ],
          [
            173,
            "                                                    row.getLong(\"total_students\"),"
          ],
          [
            174,
            "                                                    row.getLong(\"total_teachers\"),"
          ],
          [
            175,
            "                                                    row.getLong(\"total_groups\")),"
          ],
          [
            176,
            "                                    today);"
          ],
          [
            177,
            "                    if (uncertain || baseline.isEmpty()) {"
          ],
          [
            178,
            "                        return new Snapshot("
          ],
          [
            179,
            "                                current, DashboardDailyGrowthResponse.unknown(baselineDate));"
          ],
          [
            180,
            "                    }"
          ],
          [
            181,
            "                    // With no intervening changes, an earlier observed state carries forward"
          ],
          [
            182,
            "                    // through quiet days. A straddling/pending marker above prevents guessing."
          ],
          [
            183,
            "                    DashboardScaleCounts previous = baseline.getFirst();"
          ],
          [
            184,
            "                    return new Snapshot("
          ],
          [
            185,
            "                            current,"
          ],
          [
            186,
            "                            new DashboardDailyGrowthResponse("
          ],
          [
            187,
            "                                    Status.AVAILABLE,"
          ],
          [
            188,
            "                                    baselineDate,"
          ],
          [
            189,
            "                                    current.students() - previous.students(),"
          ],
          [
            190,
            "                                    current.teachers() - previous.teachers(),"
          ],
          [
            191,
            "                                    current.groups() - previous.groups()));"
          ]
        ]
      },
      {
        "title": "Неизвестные дельты остаются null",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/dashboard/DashboardDailyGrowthResponse.java",
        "language": "java",
        "lines": [
          [
            24,
            "    public enum Status {"
          ],
          [
            25,
            "        AVAILABLE,"
          ],
          [
            26,
            "        UNKNOWN_HISTORY"
          ],
          [
            27,
            "    }"
          ],
          [
            28,
            ""
          ],
          [
            29,
            "    public DashboardDailyGrowthResponse {"
          ],
          [
            30,
            "        Objects.requireNonNull(status, \"status\");"
          ],
          [
            31,
            "        Objects.requireNonNull(baselineDate, \"baselineDate\");"
          ],
          [
            32,
            "        boolean allKnown = studentsDelta != null && teachersDelta != null && groupsDelta != null;"
          ],
          [
            33,
            "        boolean allUnknown = studentsDelta == null && teachersDelta == null && groupsDelta == null;"
          ],
          [
            34,
            "        if (status == Status.AVAILABLE ? !allKnown : !allUnknown) {"
          ],
          [
            35,
            "            throw new IllegalArgumentException("
          ],
          [
            36,
            "                    \"Daily growth values must match their history status\");"
          ],
          [
            37,
            "        }"
          ],
          [
            40,
            "    public static DashboardDailyGrowthResponse unknown(LocalDate baselineDate) {"
          ],
          [
            41,
            "        return new DashboardDailyGrowthResponse("
          ],
          [
            42,
            "                Status.UNKNOWN_HISTORY, baselineDate, null, null, null);"
          ],
          [
            43,
            "    }"
          ]
        ]
      },
      {
        "title": "База сравнения неизвестна · фрагмент ответа",
        "language": "json",
        "example": [
          "{",
          "  \"dailyGrowth\": {",
          "    \"status\": \"UNKNOWN_HISTORY\",",
          "    \"baselineDate\": \"2026-11-12\",",
          "    \"studentsDelta\": null,",
          "    \"teachersDelta\": null,",
          "    \"groupsDelta\": null",
          "  }",
          "}"
        ]
      }
    ]
  },
  {
    "id": "admin-overview-window",
    "title": "Сегодня, вчера и график",
    "description": "Режим «Сегодня» сравнивает текущий московский день со вчерашним к тому же времени. Режим «За всё время» показывает накопленный итог без сравнения. График содержит 14 дневных или 12 недельных интервалов. Последний интервал заканчивается на моменте наблюдения.",
    "frames": [
      {
        "title": "Московская полночь и сетка интервалов",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/ActivityWindow.java",
        "language": "java",
        "lines": [
          [
            28,
            "        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);"
          ],
          [
            29,
            "        ZonedDateTime local = now.atZone(MOSCOW);"
          ],
          [
            30,
            "        Instant midnight = local.toLocalDate().atStartOfDay(MOSCOW).toInstant();"
          ],
          [
            31,
            "        boolean weekly = \"week\".equals(bucket);"
          ],
          [
            32,
            "        ZonedDateTime first = local.toLocalDate().atStartOfDay(MOSCOW);"
          ],
          [
            33,
            "        first ="
          ],
          [
            34,
            "                weekly"
          ],
          [
            35,
            "                        ? first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))"
          ],
          [
            36,
            "                                .minusWeeks(11)"
          ],
          [
            37,
            "                        : first.minusDays(13);"
          ],
          [
            38,
            "        List<Range> series = new ArrayList<>();"
          ],
          [
            39,
            "        for (int i = 0; i < (weekly ? 12 : 14); i++) {"
          ],
          [
            40,
            "            ZonedDateTime next = weekly ? first.plusWeeks(1) : first.plusDays(1);"
          ],
          [
            41,
            "            series.add("
          ],
          [
            42,
            "                    new Range("
          ],
          [
            43,
            "                            first.toInstant(),"
          ],
          [
            44,
            "                            next.toInstant().isAfter(now) ? now : next.toInstant()));"
          ],
          [
            45,
            "            first = next;"
          ],
          [
            46,
            "        }"
          ]
        ]
      },
      {
        "title": "Период сравнения обрезан до того же времени",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/ActivityWindow.java",
        "language": "java",
        "lines": [
          [
            47,
            "        boolean today = \"today\".equals(period);"
          ],
          [
            48,
            "        return new ActivityWindow("
          ],
          [
            49,
            "                now,"
          ],
          [
            50,
            "                period,"
          ],
          [
            51,
            "                bucket,"
          ],
          [
            52,
            "                new Range(today ? midnight : null, now),"
          ],
          [
            53,
            "                today"
          ],
          [
            54,
            "                        ? new Range("
          ],
          [
            55,
            "                                midnight.minus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS))"
          ],
          [
            56,
            "                        : null,"
          ],
          [
            57,
            "                List.copyOf(series));"
          ]
        ]
      },
      {
        "title": "Сервис строит сводку и точки",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardActivityService.java",
        "language": "java",
        "lines": [
          [
            48,
            "        Section summary ="
          ],
          [
            49,
            "                section("
          ],
          [
            50,
            "                        window.summary(),"
          ],
          [
            51,
            "                        activity == null ? null : activity.getSummary(),"
          ],
          [
            52,
            "                        map == null ? null : map.summary(),"
          ],
          [
            53,
            "                        window.previous(),"
          ],
          [
            54,
            "                        activity == null ? null : activity.getPrevious(),"
          ],
          [
            55,
            "                        map == null ? null : map.previous());"
          ],
          [
            56,
            "        List<Section> series = new ArrayList<>();"
          ],
          [
            57,
            "        for (int i = 0; i < window.series().size(); i++) {"
          ],
          [
            58,
            "            series.add("
          ],
          [
            59,
            "                    section("
          ],
          [
            60,
            "                            window.series().get(i),"
          ],
          [
            61,
            "                            activity == null ? null : activity.getSeries(i).getCounts(),"
          ],
          [
            62,
            "                            map == null ? null : map.series().get(i),"
          ],
          [
            63,
            "                            null,"
          ],
          [
            64,
            "                            null,"
          ]
        ]
      },
      {
        "title": "График использует даты и состояние ответа",
        "path": "frontends/mobile-core/src/features/admin-dashboard/admin-dashboard-activity-view.ts",
        "language": "typescript",
        "lines": [
          [
            76,
            "export function adminActivityChartPoints("
          ],
          [
            77,
            "  activity: AdminDashboardActivity,"
          ],
          [
            78,
            "  code: AdminActivityMetricCode,"
          ],
          [
            79,
            "): readonly AdminActivityChartPoint[] {"
          ],
          [
            80,
            "  return activity.series.map((item, index) => {"
          ],
          [
            81,
            "    const metric = item.metrics[code]"
          ],
          [
            82,
            "    // Bounds are UTC instants; a completed exclusive midnight belongs to the preceding Moscow day."
          ],
          [
            83,
            "    const from = adminActivityMoscowDate(item.range.from)"
          ],
          [
            84,
            "    const to = adminActivityMoscowDate("
          ],
          [
            85,
            "      new Date(Math.max(Date.parse(item.range.from), Date.parse(item.range.to) - 1)).toISOString(),"
          ],
          [
            86,
            "    )"
          ],
          [
            87,
            "    const value ="
          ],
          [
            88,
            "      metric.state === 'AVAILABLE' ? (code === 'geoMarks' ? metric.percent : metric.value) : null"
          ],
          [
            89,
            "    return {"
          ],
          [
            90,
            "      id: item.range.from + ':' + index,"
          ],
          [
            91,
            "      dateFrom: from,"
          ],
          [
            92,
            "      dateTo: to,"
          ],
          [
            93,
            "      value,"
          ],
          [
            94,
            "      detail: adminActivityExplanation(metric),"
          ],
          [
            95,
            "    }"
          ],
          [
            96,
            "  })"
          ]
        ]
      }
    ]
  },
  {
    "id": "admin-overview-activity",
    "title": "Что считается отметкой",
    "description": "Активность считает записи аудита о переходе в PRESENT. Изменение уже имеющегося PRESENT и отклонённая попытка в счётчик не входят. Источник STUDENT_GEO выделяет геоотметки внутри этих переходов. Доля геоотметок считается от всех таких переходов за выбранное окно.",
    "frames": [
      {
        "title": "Mongo читает переходы в PRESENT",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/adminactivity/AdminActivityReadRepository.java",
        "language": "java",
        "lines": [
          [
            22,
            "    public AdminActivitySnapshot read(AdminActivityWindow window) {"
          ],
          [
            23,
            "        Document positives ="
          ],
          [
            24,
            "                aggregate("
          ],
          [
            25,
            "                        \"lesson_actions\","
          ],
          [
            26,
            "                        List.of("
          ],
          [
            27,
            "                                new Document("
          ],
          [
            28,
            "                                        \"$match\","
          ],
          [
            29,
            "                                        new Document(\"source\", \"attendance-service\")"
          ],
          [
            30,
            "                                                .append(\"payload.action\", \"ATTENDANCE_SET\")"
          ],
          [
            31,
            "                                                .append("
          ],
          [
            32,
            "                                                        \"payload.outcome\","
          ],
          [
            33,
            "                                                        new Document(\"$ne\", \"REJECTED\"))"
          ],
          [
            34,
            "                                                .append(\"payload.after.status\", \"PRESENT\")"
          ],
          [
            35,
            "                                                .append("
          ],
          [
            36,
            "                                                        \"payload.before.status\","
          ],
          [
            37,
            "                                                        new Document(\"$ne\", \"PRESENT\"))"
          ],
          [
            38,
            "                                                .append("
          ],
          [
            39,
            "                                                        \"occurred_at\","
          ],
          [
            40,
            "                                                        new Document("
          ],
          [
            41,
            "                                                                \"$lt\","
          ],
          [
            42,
            "                                                                Date.from(window.observedAt())))),"
          ],
          [
            43,
            "                                facets(window, \"occurred_at\", true)));"
          ]
        ]
      },
      {
        "title": "Внутри переходов выделяется STUDENT_GEO",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/adminactivity/AdminActivityReadRepository.java",
        "language": "java",
        "lines": [
          [
            144,
            "    private static Document countGroup(Object id, boolean geo) {"
          ],
          [
            145,
            "        Document group = new Document(\"_id\", id).append(\"value\", new Document(\"$sum\", 1));"
          ],
          [
            146,
            "        if (geo)"
          ],
          [
            147,
            "            group.append("
          ],
          [
            148,
            "                    \"geo\","
          ],
          [
            149,
            "                    new Document("
          ],
          [
            150,
            "                            \"$sum\","
          ],
          [
            151,
            "                            new Document("
          ],
          [
            152,
            "                                    \"$cond\","
          ],
          [
            153,
            "                                    List.of("
          ],
          [
            154,
            "                                            new Document("
          ],
          [
            155,
            "                                                    \"$eq\","
          ],
          [
            156,
            "                                                    List.of("
          ],
          [
            157,
            "                                                            \"$payload.after.source\","
          ],
          [
            158,
            "                                                            \"STUDENT_GEO\")),"
          ],
          [
            159,
            "                                            1,"
          ],
          [
            160,
            "                                            0))));"
          ],
          [
            161,
            "        return group;"
          ],
          [
            162,
            "    }"
          ]
        ]
      },
      {
        "title": "Из переходов получаются счётчик и доля",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardActivityService.java",
        "language": "java",
        "lines": [
          [
            86,
            "        result.put("
          ],
          [
            87,
            "                \"positiveMarks\","
          ],
          [
            88,
            "                metric("
          ],
          [
            89,
            "                        now == null ? null : now.getPositiveMarks(),"
          ],
          [
            90,
            "                        null,"
          ],
          [
            91,
            "                        RETAINED,"
          ],
          [
            92,
            "                        now == null ? State.UNAVAILABLE : State.AVAILABLE,"
          ],
          [
            93,
            "                        previousRange,"
          ],
          [
            94,
            "                        previous == null ? null : previous.getPositiveMarks(),"
          ],
          [
            95,
            "                        null,"
          ],
          [
            96,
            "                        previous == null ? State.UNAVAILABLE : State.AVAILABLE));"
          ],
          [
            97,
            "        result.put("
          ],
          [
            98,
            "                \"geoMarks\","
          ],
          [
            99,
            "                metric("
          ],
          [
            100,
            "                        now == null ? null : now.getGeoMarks(),"
          ],
          [
            101,
            "                        now == null ? null : now.getPositiveMarks(),"
          ],
          [
            102,
            "                        RETAINED,"
          ],
          [
            103,
            "                        now == null ? State.UNAVAILABLE : State.AVAILABLE,"
          ],
          [
            104,
            "                        previousRange,"
          ],
          [
            105,
            "                        previous == null ? null : previous.getGeoMarks(),"
          ],
          [
            106,
            "                        previous == null ? null : previous.getPositiveMarks(),"
          ],
          [
            107,
            "                        previous == null ? State.UNAVAILABLE : State.AVAILABLE));"
          ]
        ]
      }
    ]
  },
  {
    "id": "admin-overview-comparison",
    "title": "Количество и процентные пункты",
    "description": "Если сегодня 20 положительных отметок против 10 вчера, прибавка равна 10. Геоотметок 6 из 20 против 4 из 10. Доля снизилась с 40% до 30%, то есть на 10 процентных пунктов. Карточка количества использует абсолютную разницу, а карточка доли использует percentPointDelta. Без знаменателя доля остаётся пустой.",
    "frames": [
      {
        "title": "Контракт метрики и сравнения",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/dashboard/DashboardActivityResponse.java",
        "language": "java",
        "lines": [
          [
            29,
            "    @JsonInclude(JsonInclude.Include.ALWAYS)"
          ],
          [
            30,
            "    public record Section(Range range, Map<String, Metric> metrics) {}"
          ],
          [
            31,
            ""
          ],
          [
            32,
            "    @JsonInclude(JsonInclude.Include.ALWAYS)"
          ],
          [
            33,
            "    public record Coverage(String kind, Instant from) {}"
          ],
          [
            34,
            ""
          ],
          [
            35,
            "    @JsonInclude(JsonInclude.Include.ALWAYS)"
          ],
          [
            36,
            "    public record Comparison("
          ],
          [
            37,
            "            State state,"
          ],
          [
            38,
            "            Range range,"
          ],
          [
            39,
            "            Long previousValue,"
          ],
          [
            40,
            "            Long absoluteDelta,"
          ],
          [
            41,
            "            BigDecimal relativeDeltaPercent,"
          ],
          [
            42,
            "            BigDecimal previousPercent,"
          ],
          [
            43,
            "            BigDecimal percentPointDelta) {}"
          ],
          [
            44,
            ""
          ],
          [
            45,
            "    @JsonInclude(JsonInclude.Include.ALWAYS)"
          ],
          [
            46,
            "    public record Metric("
          ],
          [
            47,
            "            State state,"
          ],
          [
            48,
            "            Long value,"
          ],
          [
            49,
            "            Long numerator,"
          ],
          [
            50,
            "            Long denominator,"
          ],
          [
            51,
            "            BigDecimal percent,"
          ],
          [
            52,
            "            Coverage coverage,"
          ],
          [
            53,
            "            String reason,"
          ],
          [
            54,
            "            Comparison comparison) {}"
          ]
        ]
      },
      {
        "title": "Абсолютная разница и разница долей",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardActivityService.java",
        "language": "java",
        "lines": [
          [
            153,
            "        BigDecimal percent = denominator == null ? null : percent(value, denominator);"
          ],
          [
            154,
            "        Comparison comparison = null;"
          ],
          [
            155,
            "        if (previousRange != null) {"
          ],
          [
            156,
            "            State comparisonState = state == State.AVAILABLE ? previousState : state;"
          ],
          [
            157,
            "            boolean available = comparisonState == State.AVAILABLE;"
          ],
          [
            158,
            "            BigDecimal previousPercent ="
          ],
          [
            159,
            "                    available && previousDenominator != null"
          ],
          [
            160,
            "                            ? percent(previous, previousDenominator)"
          ],
          [
            161,
            "                            : null;"
          ],
          [
            162,
            "            comparison ="
          ],
          [
            163,
            "                    new Comparison("
          ],
          [
            164,
            "                            comparisonState,"
          ],
          [
            165,
            "                            previousRange,"
          ],
          [
            166,
            "                            available ? previous : null,"
          ],
          [
            167,
            "                            available ? value - previous : null,"
          ],
          [
            168,
            "                            available && previous != 0"
          ],
          [
            169,
            "                                    ? BigDecimal.valueOf(value - previous)"
          ],
          [
            170,
            "                                            .multiply(BigDecimal.valueOf(100))"
          ],
          [
            171,
            "                                            .divide("
          ],
          [
            172,
            "                                                    BigDecimal.valueOf(previous),"
          ],
          [
            173,
            "                                                    2,"
          ],
          [
            174,
            "                                                    RoundingMode.HALF_UP)"
          ],
          [
            175,
            "                                    : null,"
          ],
          [
            176,
            "                            previousPercent,"
          ],
          [
            177,
            "                            available && percent != null && previousPercent != null"
          ],
          [
            178,
            "                                    ? pointDelta(value, denominator, previous, previousDenominator)"
          ],
          [
            179,
            "                                    : null);"
          ],
          [
            196,
            "    private static BigDecimal percent(Long numerator, long denominator) {"
          ],
          [
            197,
            "        return numerator == null || denominator == 0"
          ],
          [
            198,
            "                ? null"
          ],
          [
            199,
            "                : BigDecimal.valueOf(numerator)"
          ],
          [
            200,
            "                        .multiply(BigDecimal.valueOf(100))"
          ],
          [
            201,
            "                        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);"
          ],
          [
            202,
            "    }"
          ],
          [
            203,
            ""
          ],
          [
            204,
            "    private static BigDecimal pointDelta("
          ],
          [
            205,
            "            long value, long denominator, long previous, long previousDenominator) {"
          ],
          [
            206,
            "        BigDecimal currentDenominator = BigDecimal.valueOf(denominator);"
          ],
          [
            207,
            "        BigDecimal oldDenominator = BigDecimal.valueOf(previousDenominator);"
          ],
          [
            208,
            "        return BigDecimal.valueOf(value)"
          ],
          [
            209,
            "                .multiply(oldDenominator)"
          ],
          [
            210,
            "                .subtract(BigDecimal.valueOf(previous).multiply(currentDenominator))"
          ],
          [
            211,
            "                .multiply(BigDecimal.valueOf(100))"
          ],
          [
            212,
            "                .divide(currentDenominator.multiply(oldDenominator), 2, RoundingMode.HALF_UP);"
          ]
        ]
      },
      {
        "title": "20 отметок и доля 30% · фрагмент ответа",
        "language": "json",
        "example": [
          "{",
          "  \"period\": \"today\",",
          "  \"bucket\": \"day\",",
          "  \"summary\": {",
          "    \"range\": {",
          "      \"from\": \"2026-11-12T21:00:00Z\",",
          "      \"to\": \"2026-11-13T06:00:00Z\"",
          "    },",
          "    \"metrics\": {",
          "      \"positiveMarks\": {",
          "        \"state\": \"AVAILABLE\",",
          "        \"value\": 20,",
          "        \"numerator\": null,",
          "        \"denominator\": null,",
          "        \"percent\": null,",
          "        \"coverage\": {",
          "          \"kind\": \"RETAINED_DOMAIN_HISTORY\",",
          "          \"from\": null",
          "        },",
          "        \"reason\": null,",
          "        \"comparison\": {",
          "          \"state\": \"AVAILABLE\",",
          "          \"range\": {",
          "            \"from\": \"2026-11-11T21:00:00Z\",",
          "            \"to\": \"2026-11-12T06:00:00Z\"",
          "          },",
          "          \"previousValue\": 10,",
          "          \"absoluteDelta\": 10,",
          "          \"relativeDeltaPercent\": 100,",
          "          \"previousPercent\": null,",
          "          \"percentPointDelta\": null",
          "        }",
          "      },",
          "      \"geoMarks\": {",
          "        \"state\": \"AVAILABLE\",",
          "        \"value\": 6,",
          "        \"numerator\": 6,",
          "        \"denominator\": 20,",
          "        \"percent\": 30,",
          "        \"coverage\": {",
          "          \"kind\": \"RETAINED_DOMAIN_HISTORY\",",
          "          \"from\": null",
          "        },",
          "        \"reason\": null,",
          "        \"comparison\": {",
          "          \"state\": \"AVAILABLE\",",
          "          \"range\": {",
          "            \"from\": \"2026-11-11T21:00:00Z\",",
          "            \"to\": \"2026-11-12T06:00:00Z\"",
          "          },",
          "          \"previousValue\": 4,",
          "          \"absoluteDelta\": 2,",
          "          \"relativeDeltaPercent\": 50,",
          "          \"previousPercent\": 40,",
          "          \"percentPointDelta\": -10",
          "        }",
          "      }",
          "    }",
          "  }",
          "}"
        ]
      },
      {
        "title": "Клиент выбирает абсолютную разницу или пункты",
        "path": "frontends/mobile-core/src/features/admin-dashboard/admin-dashboard-activity-view.ts",
        "language": "typescript",
        "lines": [
          [
            39,
            "export function adminActivityValue("
          ],
          [
            40,
            "  metric: AdminActivityMetric,"
          ],
          [
            41,
            "  code: AdminActivityMetricCode,"
          ],
          [
            42,
            "): string {"
          ],
          [
            43,
            "  if (metric.state !== 'AVAILABLE') return '—'"
          ],
          [
            44,
            "  if (code === 'geoMarks')"
          ],
          [
            45,
            "    return metric.percent === null ? '—' : decimal.format(metric.percent) + '%'"
          ],
          [
            46,
            "  return metric.value === null ? '—' : integer.format(metric.value)"
          ],
          [
            54,
            "export function adminActivityDelta("
          ],
          [
            55,
            "  metric: AdminActivityMetric,"
          ],
          [
            56,
            "  code: AdminActivityMetricCode,"
          ],
          [
            57,
            "): { text: string; tone: 'positive' | 'negative' | 'neutral'; description: string } {"
          ],
          [
            58,
            "  const previous = metric.comparison"
          ],
          [
            59,
            "  const measured ="
          ],
          [
            60,
            "    metric.state === 'AVAILABLE' &&"
          ],
          [
            61,
            "    (code === 'geoMarks' ? metric.percent !== null : metric.value !== null)"
          ],
          [
            62,
            "  const delta ="
          ],
          [
            63,
            "    measured && previous?.state === 'AVAILABLE'"
          ],
          [
            64,
            "      ? code === 'geoMarks'"
          ],
          [
            65,
            "        ? previous.percentPointDelta"
          ],
          [
            66,
            "        : previous.absoluteDelta"
          ],
          [
            67,
            "      : null"
          ],
          [
            68,
            "  if (delta === null) return { text: '—', tone: 'neutral', description: 'Сравнение недоступно' }"
          ],
          [
            69,
            "  const text = `${delta > 0 ? '+' : delta < 0 ? '−' : ''}${decimal.format(Math.abs(delta))}${code === 'geoMarks' ? '%' : ''}`"
          ],
          [
            70,
            "  return {"
          ],
          [
            71,
            "    text,"
          ],
          [
            72,
            "    tone: delta > 0 ? 'positive' : delta < 0 ? 'negative' : 'neutral',"
          ],
          [
            73,
            "    description: `${text}, относительно вчера к этому времени`,"
          ],
          [
            74,
            "  }"
          ]
        ]
      }
    ]
  }
]
