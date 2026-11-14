import type { StoryOperation } from './story-source'

export const campusMapStatisticsOperations: readonly StoryOperation[] = [
  {
    "id": "campus-map-statistics-open",
    "title": "Что считается открытием",
    "description": "На мобильном экране успешная загрузка текущего изображения запускает запись открытия. Это работает для SVG и PNG. Повторное событие загрузки того же просмотра подавляется в клиенте. UUID уходит в Idempotency-Key запроса POST без тела. При принятии сервер возвращает 204. Ошибка подсчёта не блокирует просмотр схемы.",
    "frames": [
      {
        "title": "Проверка текущего изображения при загрузке",
        "path": "frontends/mobile-core/src/features/map/MapScreen.vue",
        "language": "typescript",
        "lines": [
          [
            170,
            "function previewLoaded(event: Event): void {"
          ],
          [
            171,
            "  const image = event.currentTarget;"
          ],
          [
            172,
            "  if ("
          ],
          [
            173,
            "    !(image instanceof HTMLImageElement) ||"
          ],
          [
            174,
            "    image.src !== previewUrl.value ||"
          ],
          [
            175,
            "    !image.naturalWidth ||"
          ],
          [
            176,
            "    !image.naturalHeight"
          ],
          [
            177,
            "  )"
          ],
          [
            178,
            "    return;"
          ],
          [
            179,
            "  // Asset geometry determines the viewport, with bounded tall/wide proportions."
          ],
          [
            180,
            "  aspectRatio.value = Math.max(0.5, Math.min(2, image.naturalWidth / image.naturalHeight));"
          ],
          [
            181,
            "  imageReady.value = true;"
          ],
          [
            182,
            "  handleSvgLoaded();"
          ],
          [
            183,
            "}"
          ],
          [
            582,
            "                <img"
          ],
          [
            583,
            "                  :key=\"previewUrl\""
          ],
          [
            584,
            "                  :src=\"previewUrl\""
          ],
          [
            585,
            "                  alt=\"Схема выбранного этажа\""
          ],
          [
            586,
            "                  :draggable=\"false\""
          ],
          [
            587,
            "                  @load=\"previewLoaded\""
          ],
          [
            588,
            "                />"
          ]
        ]
      },
      {
        "title": "Одно намерение для выбранного просмотра",
        "path": "frontends/mobile-core/src/features/map/MapScreen.vue",
        "language": "typescript",
        "lines": [
          [
            295,
            "    const currentOpenKey = `${building.id}:${floor.id}:${plan.version}`;"
          ],
          [
            296,
            "    const currentGeneration = props.client.currentGeneration();"
          ],
          [
            297,
            "    if ("
          ],
          [
            298,
            "      openIntentKey !== currentOpenKey ||"
          ],
          [
            299,
            "      openIntentGeneration !== currentGeneration ||"
          ],
          [
            300,
            "      !openIntentId"
          ],
          [
            301,
            "    ) {"
          ],
          [
            302,
            "      openIntentKey = currentOpenKey;"
          ],
          [
            303,
            "      openIntentGeneration = currentGeneration;"
          ],
          [
            304,
            "      openIntentId = globalThis.crypto?.randomUUID?.() ?? null;"
          ],
          [
            305,
            "      openRecorded = false;"
          ],
          [
            306,
            "      openRecording = false;"
          ],
          [
            307,
            "    }"
          ],
          [
            373,
            "  if ("
          ],
          [
            374,
            "    disposed ||"
          ],
          [
            375,
            "    requestId === 0 ||"
          ],
          [
            376,
            "    requestId !== assetRequest ||"
          ],
          [
            377,
            "    !intentId ||"
          ],
          [
            378,
            "    !intentKey ||"
          ],
          [
            379,
            "    !building ||"
          ],
          [
            380,
            "    !floor ||"
          ],
          [
            381,
            "    openRecorded ||"
          ],
          [
            382,
            "    openRecording ||"
          ],
          [
            383,
            "    !props.client.isCurrentGeneration(generation)"
          ],
          [
            384,
            "  )"
          ],
          [
            385,
            "    return;"
          ],
          [
            386,
            ""
          ],
          [
            387,
            "  openRecording = true;"
          ],
          [
            388,
            "  void props.client"
          ],
          [
            389,
            "    .recordFloorOpen(building.id, floor.id, intentId, requestController?.signal)"
          ],
          [
            390,
            "    .then(() => {"
          ],
          [
            391,
            "      if ("
          ],
          [
            392,
            "        requestId === assetRequest &&"
          ],
          [
            393,
            "        intentKey === openIntentKey &&"
          ],
          [
            394,
            "        intentId === openIntentId &&"
          ],
          [
            395,
            "        props.client.isCurrentGeneration(generation)"
          ],
          [
            396,
            "      ) {"
          ],
          [
            397,
            "        openRecorded = true;"
          ],
          [
            398,
            "      }"
          ],
          [
            399,
            "    })"
          ]
        ]
      },
      {
        "title": "Клиент передаёт UUID в заголовке",
        "path": "frontends/mobile-core/src/api/map-client.ts",
        "language": "typescript",
        "lines": [
          [
            122,
            "  async recordFloorOpen("
          ],
          [
            123,
            "    buildingId: string,"
          ],
          [
            124,
            "    floorId: string,"
          ],
          [
            125,
            "    intentId: string,"
          ],
          [
            126,
            "    signal?: AbortSignal,"
          ],
          [
            127,
            "  ): Promise<void> {"
          ],
          [
            128,
            "    const response = await this.requestResponse("
          ],
          [
            129,
            "      `/api/v1/map/buildings/${encodeURIComponent(buildingId)}/floors/${encodeURIComponent(floorId)}/opens`,"
          ],
          [
            130,
            "      {"
          ],
          [
            131,
            "        method: 'POST',"
          ],
          [
            132,
            "        headers: {"
          ],
          [
            133,
            "          'Idempotency-Key': intentId,"
          ],
          [
            134,
            "        },"
          ],
          [
            135,
            "        ...(signal ? { signal } : {}),"
          ],
          [
            136,
            "      },"
          ],
          [
            137,
            "    )"
          ],
          [
            138,
            "    await response.arrayBuffer()"
          ],
          [
            139,
            "  }"
          ]
        ]
      },
      {
        "title": "Контракт ответа без тела",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/api/MapApi.java",
        "language": "java",
        "lines": [
          [
            71,
            "    @Operation(summary = \"Record one explicit floor-open intent\")"
          ],
          [
            72,
            "    @ApiResponses({"
          ],
          [
            73,
            "        @ApiResponse(responseCode = \"204\", description = \"Open intent accepted or already counted\"),"
          ],
          [
            74,
            "        @ApiResponse(responseCode = \"400\", description = \"Invalid idempotency key\"),"
          ],
          [
            75,
            "        @ApiResponse(responseCode = \"401\", description = \"Invalid or expired session\"),"
          ],
          [
            76,
            "        @ApiResponse(responseCode = \"403\", description = \"Floor is outside the current role scope\"),"
          ],
          [
            77,
            "        @ApiResponse(responseCode = \"404\", description = \"Building or floor not found\"),"
          ],
          [
            78,
            "        @ApiResponse("
          ],
          [
            79,
            "                responseCode = \"409\","
          ],
          [
            80,
            "                description = \"Intent is already bound to another floor\"),"
          ],
          [
            81,
            "        @ApiResponse(responseCode = \"503\", description = \"Map usage is unavailable\")"
          ],
          [
            82,
            "    })"
          ],
          [
            83,
            "    @PostMapping(\"/buildings/{buildingId}/floors/{floorId}/opens\")"
          ],
          [
            84,
            "    ResponseEntity<Void> recordFloorOpen("
          ],
          [
            85,
            "            @PathVariable String buildingId,"
          ],
          [
            86,
            "            @PathVariable String floorId,"
          ],
          [
            87,
            "            @RequestHeader(name = \"Idempotency-Key\") @NotNull UUID intentId);"
          ]
        ]
      }
    ]
  },
  {
    "id": "campus-map-statistics-retry",
    "title": "Повтор и новый просмотр",
    "description": "Сервис проверяет, что этаж доступен и имеет опубликованный план. Пока запись намерения сохранена, тот же владелец и UUID на том же этаже дают повтор без прибавки. Тот же UUID на другом опубликованном этаже даёт 409. Новое намерение, например после перезагрузки или выбора другого этажа, получает другой UUID и может добавить ещё одно открытие даже в те же сутки.",
    "frames": [
      {
        "title": "Открытие разрешено для этажа с опубликованным планом",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java",
        "language": "java",
        "lines": [
          [
            41,
            "    @Transactional(isolation = Isolation.READ_COMMITTED)"
          ],
          [
            42,
            "    public void recordFloorOpen("
          ],
          [
            43,
            "            String buildingId, String floorId, UUID intentId, InternalJwtClaims claims) {"
          ],
          [
            44,
            "        if (intentId == null) {"
          ],
          [
            45,
            "            throw CampusMapReadException.of(INVALID_ARGUMENT, \"map open intent is required\");"
          ],
          [
            46,
            "        }"
          ],
          [
            47,
            ""
          ],
          [
            48,
            "        CampusMapReadModels.PlanResult plan ="
          ],
          [
            49,
            "                mapReadService.readFloorPlan(buildingId, floorId, claims);"
          ],
          [
            50,
            "        if (!plan.hasPlan()) {"
          ],
          [
            51,
            "            throw CampusMapReadException.of("
          ],
          [
            52,
            "                    FAILED_PRECONDITION, \"campus map floor has no published plan\");"
          ],
          [
            53,
            "        }"
          ],
          [
            54,
            ""
          ],
          [
            55,
            "        long buildingKey = parseCanonicalId(buildingId, \"building id\");"
          ],
          [
            56,
            "        long floorKey = parseCanonicalId(floorId, \"floor id\");"
          ],
          [
            57,
            "        if (!repository.lockFloorForOpen(floorKey)) {"
          ],
          [
            58,
            "            throw CampusMapReadException.of("
          ],
          [
            59,
            "                    CampusMapReadException.Code.NOT_FOUND, \"campus map floor was not found\");"
          ],
          [
            60,
            "        }"
          ]
        ]
      },
      {
        "title": "Совпавшее намерение и конфликт этажа",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java",
        "language": "java",
        "lines": [
          [
            64,
            "        boolean newIntent ="
          ],
          [
            65,
            "                repository.insertOpenIntent(ownerHmac, intentId, buildingKey, floorKey, payloadHash)"
          ],
          [
            66,
            "                        == 1;"
          ],
          [
            67,
            "        CampusMapUsageRepository.OpenIntentRow intent ="
          ],
          [
            68,
            "                repository"
          ],
          [
            69,
            "                        .findOpenIntentForUpdate(ownerHmac, intentId)"
          ],
          [
            70,
            "                        .orElseThrow("
          ],
          [
            71,
            "                                () ->"
          ],
          [
            72,
            "                                        CampusMapReadException.of("
          ],
          [
            73,
            "                                                CampusMapReadException.Code.INTERNAL,"
          ],
          [
            74,
            "                                                \"campus map open intent was not retained\"));"
          ],
          [
            75,
            "        if (intent.buildingId() != buildingKey"
          ],
          [
            76,
            "                || intent.floorId() != floorKey"
          ],
          [
            77,
            "                || !Arrays.equals(intent.payloadHash(), payloadHash)) {"
          ],
          [
            78,
            "            throw CampusMapReadException.of("
          ],
          [
            79,
            "                    FAILED_PRECONDITION, \"map open intent is already bound to another floor\");"
          ],
          [
            80,
            "        }"
          ],
          [
            81,
            ""
          ],
          [
            82,
            "        repository.insertDemandDedupe("
          ],
          [
            83,
            "                ownerHmac, floorKey, intent.acceptedUtcDay(), intentId, intent.acceptedAt());"
          ],
          [
            84,
            "        if (newIntent) events.publishEvent(new DashboardInvalidatedEvent(this));"
          ]
        ]
      },
      {
        "title": "Уникальность открытия по владельцу и UUID",
        "path": "services/academic-service/academic-app/src/main/resources/db/migration/V30__campus_map_distinct_openings.sql",
        "language": "sql",
        "lines": [
          [
            1,
            "-- Count every distinct logical floor opening.  V26's owner/floor/day key"
          ],
          [
            2,
            "-- suppressed a second opening by the same actor; retry idempotency remains"
          ],
          [
            3,
            "-- protected by the owner/intent key and the immutable open-intent row."
          ],
          [
            4,
            "ALTER TABLE campus_map_floor_demand_dedupe"
          ],
          [
            5,
            "    DROP CONSTRAINT campus_map_floor_demand_dedupe_owner_floor_day_uq;"
          ]
        ]
      },
      {
        "title": "Смена этажа сбрасывает намерение",
        "path": "frontends/mobile-core/src/features/map/MapScreen.vue",
        "language": "typescript",
        "lines": [
          [
            266,
            "async function loadCurrentFloorPlan(): Promise<void> {"
          ],
          [
            267,
            "  const floor = selectedFloor.value;"
          ],
          [
            268,
            "  const building = selectedBuilding.value;"
          ],
          [
            269,
            "  const requestId = ++assetRequest;"
          ],
          [
            270,
            "  assetGeneration = props.client.currentGeneration();"
          ],
          [
            271,
            "  requestController?.abort();"
          ],
          [
            272,
            "  const controller = new AbortController();"
          ],
          [
            273,
            "  requestController = controller;"
          ],
          [
            274,
            "  const floorKey = floor && building ? `${building.id}:${floor.id}` : null;"
          ],
          [
            275,
            "  currentPlan.value = null;"
          ],
          [
            276,
            "  currentPlanFloorKey.value = floorKey;"
          ],
          [
            277,
            "  revokeAsset('svg');"
          ],
          [
            278,
            "  revokeAsset('png');"
          ],
          [
            279,
            "  invalidateOpenIntent();"
          ],
          [
            410,
            "function invalidateOpenIntent(): void {"
          ],
          [
            411,
            "  openIntentKey = null;"
          ],
          [
            412,
            "  openIntentId = null;"
          ],
          [
            413,
            "  openIntentGeneration = undefined;"
          ],
          [
            414,
            "  openRecorded = false;"
          ],
          [
            415,
            "  openRecording = false;"
          ],
          [
            416,
            "}"
          ]
        ]
      }
    ]
  },
  {
    "id": "campus-map-statistics-aggregate",
    "title": "Счётчики по этажу и UTC",
    "description": "Новое принятое намерение добавляет одну строку дедупликации. Только успешная вставка увеличивает суточный и почасовой счётчики. Ключи суток и часа используют UTC. После публикации обоих планов откроем первый этаж дважды разными UUID и второй этаж с новым UUID. У этажей будет 2 и 1 открытие. Повтор прежнего запроса не добавит четвёртое.",
    "frames": [
      {
        "title": "Конфликт UUID не создаёт вторую запись",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageRepository.java",
        "language": "java",
        "lines": [
          [
            18,
            "    private static final String INSERT_INTENT_SQL ="
          ],
          [
            19,
            "            \"\"\""
          ],
          [
            20,
            "            INSERT INTO campus_map_open_intent"
          ],
          [
            21,
            "                (owner_hmac, intent_id, building_id, floor_id, payload_hash,"
          ],
          [
            22,
            "                 accepted_utc_day, accepted_at, expires_at)"
          ],
          [
            23,
            "            VALUES (?, ?, ?, ?, ?, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date,"
          ],
          [
            24,
            "                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '48 hours')"
          ],
          [
            25,
            "            ON CONFLICT (owner_hmac, intent_id) DO NOTHING"
          ],
          [
            26,
            "            \"\"\";"
          ],
          [
            37,
            "    private static final String INSERT_DEDUPE_SQL ="
          ],
          [
            38,
            "            \"\"\""
          ],
          [
            39,
            "            INSERT INTO campus_map_floor_demand_dedupe"
          ],
          [
            40,
            "                (owner_hmac, floor_id, utc_day, intent_id, accepted_at)"
          ],
          [
            41,
            "            VALUES (?, ?, ?, ?, ?)"
          ],
          [
            42,
            "            ON CONFLICT (owner_hmac, intent_id) DO NOTHING"
          ],
          [
            43,
            "            \"\"\";"
          ]
        ]
      },
      {
        "title": "Новая запись прибавляет единицу к этажу и дате",
        "path": "services/academic-service/academic-app/src/main/resources/db/migration/V26__campus_map.sql",
        "language": "sql",
        "lines": [
          [
            400,
            "    -- This trigger runs only for an actual INSERT. A conflicting retry does"
          ],
          [
            401,
            "    -- not create a dedupe row and therefore cannot inflate the aggregate."
          ],
          [
            402,
            "    INSERT INTO campus_map_floor_daily_demand (floor_id, utc_day, open_count)"
          ],
          [
            403,
            "    VALUES (NEW.floor_id, NEW.utc_day, 1)"
          ],
          [
            404,
            "    ON CONFLICT (floor_id, utc_day)"
          ],
          [
            405,
            "    DO UPDATE SET open_count = campus_map_floor_daily_demand.open_count + 1,"
          ],
          [
            406,
            "                  updated_at = NOW();"
          ],
          [
            407,
            "    RETURN NEW;"
          ],
          [
            408,
            "END"
          ],
          [
            409,
            "$$;"
          ],
          [
            410,
            ""
          ],
          [
            411,
            "CREATE TRIGGER campus_map_floor_demand_dedupe_count_trg"
          ],
          [
            412,
            "    AFTER INSERT ON campus_map_floor_demand_dedupe"
          ],
          [
            413,
            "    FOR EACH ROW"
          ],
          [
            414,
            "    EXECUTE FUNCTION increment_campus_map_floor_demand();"
          ]
        ]
      },
      {
        "title": "Почасовой счётчик использует ту же вставку",
        "path": "services/academic-service/academic-app/src/main/resources/db/migration/V52__admin_dashboard_activity.sql",
        "language": "sql",
        "lines": [
          [
            19,
            "CREATE FUNCTION increment_campus_map_hourly_activity() RETURNS TRIGGER LANGUAGE plpgsql AS $$"
          ],
          [
            20,
            "BEGIN"
          ],
          [
            21,
            "    -- The existing guarded dedupe INSERT is the single acceptance authority."
          ],
          [
            22,
            "    -- UTC hours align with Europe/Moscow hours. Retries never fire this trigger."
          ],
          [
            23,
            "    INSERT INTO campus_map_floor_hourly_activity(floor_id, hour_start, open_count)"
          ],
          [
            24,
            "    VALUES (NEW.floor_id, date_trunc('hour', NEW.accepted_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC', 1)"
          ],
          [
            25,
            "    ON CONFLICT (floor_id, hour_start)"
          ],
          [
            26,
            "    DO UPDATE SET open_count = campus_map_floor_hourly_activity.open_count + 1;"
          ],
          [
            27,
            "    RETURN NEW;"
          ],
          [
            28,
            "END $$;"
          ],
          [
            29,
            "CREATE TRIGGER campus_map_hourly_activity_count"
          ],
          [
            30,
            "    AFTER INSERT ON campus_map_floor_demand_dedupe"
          ],
          [
            31,
            "    FOR EACH ROW EXECUTE FUNCTION increment_campus_map_hourly_activity();"
          ]
        ]
      }
    ]
  },
  {
    "id": "campus-map-statistics-admin",
    "title": "Кто видит статистику карты",
    "description": "Раздел карты доступен назначенному администратору с CAMPUS_MAP_MANAGE. API возвращает активные корпуса и этажи и счётчик за всё время для каждого этажа. Экран складывает счётчики этажей в выбранном корпусе или по всем корпусам. Сумма отражает открытия, включая разные просмотры одного пользователя.",
    "frames": [
      {
        "title": "Право чтения списка корпусов",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminController.java",
        "language": "java",
        "lines": [
          [
            53,
            "    @Override"
          ],
          [
            54,
            "    @RequireRole("
          ],
          [
            55,
            "            value = {ADMIN},"
          ],
          [
            56,
            "            admin = {\"CAMPUS_MAP_MANAGE\"})"
          ],
          [
            57,
            "    public ResponseEntity<List<BuildingResponse>> listBuildings() {"
          ],
          [
            58,
            "        return ResponseEntity.ok(service.listBuildings());"
          ],
          [
            59,
            "    }"
          ]
        ]
      },
      {
        "title": "Счётчики суммируются по этажам за всё время",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageRepository.java",
        "language": "java",
        "lines": [
          [
            45,
            "    private static final String FLOOR_COUNTS_SQL ="
          ],
          [
            46,
            "            \"\"\""
          ],
          [
            47,
            "            SELECT floor_id, COALESCE(SUM(open_count), 0) AS open_count"
          ],
          [
            48,
            "            FROM campus_map_floor_daily_demand"
          ],
          [
            49,
            "            GROUP BY floor_id"
          ],
          [
            50,
            "            ORDER BY floor_id"
          ],
          [
            51,
            "            \"\"\";"
          ],
          [
            110,
            "    public Map<Long, Long> findAllTimeFloorCounts() {"
          ],
          [
            111,
            "        List<FloorCount> rows ="
          ],
          [
            112,
            "                jdbcTemplate.query("
          ],
          [
            113,
            "                        FLOOR_COUNTS_SQL,"
          ],
          [
            114,
            "                        (rs, rowNum) ->"
          ],
          [
            115,
            "                                new FloorCount(rs.getLong(\"floor_id\"), rs.getLong(\"open_count\")));"
          ],
          [
            116,
            "        Map<Long, Long> result = new LinkedHashMap<>();"
          ],
          [
            117,
            "        for (FloorCount row : rows) {"
          ],
          [
            118,
            "            result.put(row.floorId(), row.openCount());"
          ],
          [
            119,
            "        }"
          ],
          [
            120,
            "        return result;"
          ]
        ]
      },
      {
        "title": "Этаж получает счётчик и период all_time",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java",
        "language": "java",
        "lines": [
          [
            100,
            "    @Transactional(readOnly = true)"
          ],
          [
            101,
            "    public List<BuildingResponse> listBuildings() {"
          ],
          [
            102,
            "        List<CampusMapAdminRepository.FloorRow> floors = repository.findActiveFloors(null);"
          ],
          [
            103,
            "        Map<Long, PlanResponse> plans = currentPlans(floors);"
          ],
          [
            104,
            "        Map<Long, Long> openCounts = usage == null ? Map.of() : usage.allTimeFloorCounts();"
          ],
          [
            105,
            "        return repository.findActiveBuildings().stream()"
          ],
          [
            106,
            "                .map("
          ],
          [
            107,
            "                        building ->"
          ],
          [
            108,
            "                                new BuildingResponse("
          ],
          [
            109,
            "                                        decimal(building.id()),"
          ],
          [
            110,
            "                                        building.code(),"
          ],
          [
            111,
            "                                        building.label(),"
          ],
          [
            112,
            "                                        floors.stream()"
          ],
          [
            113,
            "                                                .filter("
          ],
          [
            114,
            "                                                        floor ->"
          ],
          [
            115,
            "                                                                floor.buildingId() == building.id())"
          ],
          [
            116,
            "                                                .map("
          ],
          [
            117,
            "                                                        floor ->"
          ],
          [
            118,
            "                                                                toFloor("
          ],
          [
            119,
            "                                                                        floor,"
          ],
          [
            120,
            "                                                                        plans.get(floor.id()),"
          ],
          [
            121,
            "                                                                        openCounts.getOrDefault("
          ],
          [
            495,
            "    private static FloorResponse toFloor("
          ],
          [
            496,
            "            CampusMapAdminRepository.FloorRow floor, PlanResponse plan, long openCount) {"
          ],
          [
            497,
            "        return new FloorResponse("
          ],
          [
            498,
            "                decimal(floor.id()),"
          ],
          [
            499,
            "                decimal(floor.buildingId()),"
          ],
          [
            500,
            "                floor.code(),"
          ],
          [
            501,
            "                floor.label(),"
          ],
          [
            502,
            "                plan,"
          ],
          [
            503,
            "                openCount,"
          ],
          [
            504,
            "                \"all_time\");"
          ]
        ]
      },
      {
        "title": "Поля счётчика этажа",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/map/CampusMapAdminModels.java",
        "language": "java",
        "lines": [
          [
            188,
            "            String id,"
          ],
          [
            189,
            "            String buildingId,"
          ],
          [
            190,
            "            String code,"
          ],
          [
            191,
            "            String label,"
          ],
          [
            192,
            "            PlanResponse currentPlan,"
          ],
          [
            193,
            "            @Schema("
          ],
          [
            194,
            "                            description ="
          ],
          [
            195,
            "                                    \"Number of distinct floor openings in the labelled period\","
          ],
          [
            196,
            "                            example = \"42\")"
          ],
          [
            197,
            "                    long openCount,"
          ],
          [
            198,
            "            @Schema(description = \"Period used for openCount\", example = \"all_time\")"
          ],
          [
            199,
            "                    String openCountPeriod) {"
          ]
        ]
      },
      {
        "title": "Открытия двух этажей · фрагмент списка корпусов",
        "language": "json",
        "example": [
          "[",
          "  {",
          "    \"id\": \"41\",",
          "    \"code\": \"1\",",
          "    \"label\": \"Учебный корпус\",",
          "    \"floors\": [",
          "      {",
          "        \"id\": \"71\",",
          "        \"buildingId\": \"41\",",
          "        \"code\": \"1\",",
          "        \"label\": \"Первый этаж\",",
          "        \"openCount\": 2,",
          "        \"openCountPeriod\": \"all_time\"",
          "      },",
          "      {",
          "        \"id\": \"72\",",
          "        \"buildingId\": \"41\",",
          "        \"code\": \"2\",",
          "        \"label\": \"Второй этаж\",",
          "        \"openCount\": 1,",
          "        \"openCountPeriod\": \"all_time\"",
          "      }",
          "    ]",
          "  }",
          "]"
        ]
      },
      {
        "title": "Фильтр корпуса и сумма показанных этажей",
        "path": "frontends/mobile-core/src/features/admin-map/AdminMapStatisticsScreen.vue",
        "language": "typescript",
        "lines": [
          [
            22,
            "const inventory = ref<readonly AdminMapBuildingResponse[]>([]);"
          ],
          [
            23,
            "const buildingId = ref('all');"
          ],
          [
            24,
            "const buildingOptions = computed(() => ["
          ],
          [
            25,
            "  { value: 'all', label: 'Все корпуса' },"
          ],
          [
            26,
            "  ...inventory.value.map((building) => ({ value: building.id, label: `Корпус ${building.code}` })),"
          ],
          [
            27,
            "]);"
          ],
          [
            28,
            "const loading = ref(true);"
          ],
          [
            29,
            "const error = ref(false);"
          ],
          [
            30,
            "const canView = computed("
          ],
          [
            31,
            "  () =>"
          ],
          [
            32,
            "    props.authority?.assigned === true &&"
          ],
          [
            33,
            "    (props.authority.mode === 'FULL' || props.authority.mode === 'LIMITED') &&"
          ],
          [
            34,
            "    props.authority.permissions.includes('CAMPUS_MAP_MANAGE'),"
          ],
          [
            35,
            ");"
          ],
          [
            36,
            "const allRows = computed(() =>"
          ],
          [
            37,
            "  inventory.value.flatMap((building) => building.floors.map((floor) => ({ building, floor }))),"
          ],
          [
            38,
            ");"
          ],
          [
            39,
            "const rows = computed(() =>"
          ],
          [
            40,
            "  allRows.value.filter((row) => buildingId.value === 'all' || row.building.id === buildingId.value),"
          ],
          [
            41,
            ");"
          ],
          [
            42,
            "const total = computed(() => rows.value.reduce((sum, row) => sum + row.floor.openCount, 0));"
          ],
          [
            43,
            "const maximum = computed(() => Math.max(1, ...rows.value.map((row) => row.floor.openCount)));"
          ],
          [
            96,
            "  try {"
          ],
          [
            97,
            "    const result = await client.listBuildings(signal);"
          ],
          [
            98,
            "    if (current()) inventory.value = result;"
          ]
        ]
      }
    ]
  },
  {
    "id": "campus-map-statistics-dashboard",
    "title": "Обновление обзора и периоды",
    "description": "Новое намерение публикует dashboard.invalidated. Административный обзор с DASHBOARD_VIEW отдельно читает агрегат mapOpens. Его график строит дни и недели по Москве. Если для интервала нет полной истории, API сообщает INSUFFICIENT_HISTORY. Показатель «За всё время» в разделе карты складывается по этажам независимо от этого графика.",
    "frames": [
      {
        "title": "Новое намерение публикует сигнал",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapUsageService.java",
        "language": "java",
        "lines": [
          [
            82,
            "        repository.insertDemandDedupe("
          ],
          [
            83,
            "                ownerHmac, floorKey, intent.acceptedUtcDay(), intentId, intent.acceptedAt());"
          ],
          [
            84,
            "        if (newIntent) events.publishEvent(new DashboardInvalidatedEvent(this));"
          ],
          [
            85,
            "    }"
          ]
        ]
      },
      {
        "title": "Активность доступна с правом DASHBOARD_VIEW",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardController.java",
        "language": "java",
        "lines": [
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
          ],
          [
            43,
            "    }"
          ]
        ]
      },
      {
        "title": "Дни и недели обзора по Москве",
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
          ],
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
        "title": "Окно с неполной историей не становится нулём",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardActivityReadRepository.java",
        "language": "java",
        "lines": [
          [
            40,
            "        MapCount summary ="
          ],
          [
            41,
            "                window.summary().from() == null"
          ],
          [
            42,
            "                        ? new MapCount("
          ],
          [
            43,
            "                                State.AVAILABLE,"
          ],
          [
            44,
            "                                total(window.observedAt()),"
          ],
          [
            45,
            "                                new Coverage(\"RETAINED_DOMAIN_HISTORY\", null))"
          ],
          [
            46,
            "                        : count(window.summary(), capturedFrom, rawFrom);"
          ],
          [
            47,
            "        MapCount previous ="
          ],
          [
            48,
            "                window.previous() == null ? null : count(window.previous(), capturedFrom, rawFrom);"
          ],
          [
            49,
            "        List<MapCount> series = new ArrayList<>();"
          ],
          [
            50,
            "        for (Range range : window.series()) series.add(count(range, capturedFrom, rawFrom));"
          ],
          [
            54,
            "    private MapCount count(Range range, Instant capturedFrom, Instant rawFrom) {"
          ],
          [
            55,
            "        if (!range.from().isBefore(rawFrom)) {"
          ],
          [
            56,
            "            return new MapCount("
          ],
          [
            57,
            "                    State.AVAILABLE,"
          ],
          [
            58,
            "                    rawCount(range.from(), range.to()),"
          ],
          [
            59,
            "                    new Coverage(\"RAW_RETENTION\", rawFrom));"
          ],
          [
            60,
            "        }"
          ],
          [
            61,
            "        Coverage coverage = new Coverage(\"BOUNDED_CAPTURE\", capturedFrom);"
          ],
          [
            62,
            "        if (range.from().isBefore(capturedFrom))"
          ],
          [
            63,
            "            return new MapCount(State.INSUFFICIENT_HISTORY, null, coverage);"
          ],
          [
            64,
            "        // Completed hours are durable. The current partial hour is read at its"
          ]
        ]
      },
      {
        "title": "Обзор публикует показатель mapOpens",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/dashboard/DashboardActivityService.java",
        "language": "java",
        "lines": [
          [
            108,
            "        result.put("
          ],
          [
            109,
            "                \"mapOpens\","
          ],
          [
            110,
            "                metric("
          ],
          [
            111,
            "                        map == null ? null : map.count(),"
          ],
          [
            112,
            "                        null,"
          ],
          [
            113,
            "                        map == null ? RETAINED : map.coverage(),"
          ],
          [
            114,
            "                        map == null ? State.UNAVAILABLE : map.state(),"
          ],
          [
            115,
            "                        previousRange,"
          ],
          [
            116,
            "                        previousMap == null ? null : previousMap.count(),"
          ],
          [
            117,
            "                        null,"
          ],
          [
            118,
            "                        previousMap == null ? State.UNAVAILABLE : previousMap.state()));"
          ],
          [
            181,
            "        return new Metric("
          ],
          [
            182,
            "                state,"
          ],
          [
            183,
            "                value,"
          ],
          [
            184,
            "                denominator == null ? null : value,"
          ],
          [
            185,
            "                denominator,"
          ],
          [
            186,
            "                percent,"
          ],
          [
            187,
            "                coverage,"
          ],
          [
            188,
            "                state == State.AVAILABLE"
          ],
          [
            189,
            "                        ? null"
          ],
          [
            190,
            "                        : state == State.INSUFFICIENT_HISTORY"
          ],
          [
            191,
            "                                ? \"INSUFFICIENT_HISTORY\""
          ],
          [
            192,
            "                                : \"DEPENDENCY_UNAVAILABLE\","
          ]
        ]
      },
      {
        "title": "Контракт метрики обзора",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/dashboard/DashboardActivityResponse.java",
        "language": "java",
        "lines": [
          [
            26,
            "    @JsonInclude(JsonInclude.Include.ALWAYS)"
          ],
          [
            27,
            "    public record Range(Instant from, Instant to) {}"
          ],
          [
            28,
            ""
          ],
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
        "title": "Три открытия · фрагмент ответа обзора",
        "language": "json",
        "example": [
          "{",
          "  \"period\": \"all_time\",",
          "  \"bucket\": \"day\",",
          "  \"summary\": {",
          "    \"metrics\": {",
          "      \"mapOpens\": {",
          "        \"state\": \"AVAILABLE\",",
          "        \"value\": 3,",
          "        \"numerator\": null,",
          "        \"denominator\": null,",
          "        \"percent\": null,",
          "        \"coverage\": {",
          "          \"kind\": \"RETAINED_DOMAIN_HISTORY\",",
          "          \"from\": null",
          "        },",
          "        \"reason\": null,",
          "        \"comparison\": null",
          "      }",
          "    }",
          "  }",
          "}"
        ]
      }
    ]
  }
]
