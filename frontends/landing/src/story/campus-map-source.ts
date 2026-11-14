import type { StoryOperation } from './story-source'

export const campusMapOperations: readonly StoryOperation[] = [
  {
    "id": "campus-map-publish",
    "title": "Публикация плана этажа",
    "description": "Администратор с CAMPUS_MAP_MANAGE загружает PNG, SVG или оба файла. Сервис валидирует файлы, блокирует каталог и активный этаж, затем берёт следующий номер после наибольшего. В той же транзакции он увеличивает ревизию каталога, публикует версию и переключает указатель этажа на неё.",
    "frames": [
      {
        "title": "Администратор публикует multipart-запросом",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/api/CampusMapAdminApi.java",
        "language": "java",
        "lines": [
          [
            163,
            "    @PostMapping("
          ],
          [
            164,
            "            value = \"/floors/{floorId}/versions\","
          ],
          [
            165,
            "            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)"
          ],
          [
            166,
            "    ResponseEntity<PlanResponse> uploadVersion("
          ],
          [
            167,
            "            @PathVariable String floorId,"
          ],
          [
            168,
            "            @RequestPart(name = \"label\", required = false) String label,"
          ],
          [
            169,
            "            @RequestPart(name = \"png\", required = false) MultipartFile png,"
          ],
          [
            170,
            "            @RequestPart(name = \"svg\", required = false) MultipartFile svg);"
          ]
        ]
      },
      {
        "title": "Проверка права и HTTP 201",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminController.java",
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
            "            admin = {\"CAMPUS_MAP_MANAGE\"})"
          ],
          [
            194,
            "    public ResponseEntity<PlanResponse> uploadVersion("
          ],
          [
            195,
            "            String floorId, String label, MultipartFile png, MultipartFile svg) {"
          ],
          [
            196,
            "        return ResponseEntity.status(HttpStatus.CREATED)"
          ],
          [
            197,
            "                .body(service.uploadVersion(floorId, label, png, svg));"
          ],
          [
            198,
            "    }"
          ]
        ]
      },
      {
        "title": "Валидация и блокировка перед публикацией",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java",
        "language": "java",
        "lines": [
          [
            257,
            "    @Transactional(isolation = Isolation.READ_COMMITTED)"
          ],
          [
            258,
            "    public PlanResponse uploadVersion("
          ],
          [
            259,
            "            String floorId, String label, MultipartFile png, MultipartFile svg) {"
          ],
          [
            260,
            "        long floorKey = parsePositiveId(floorId, \"floorId\");"
          ],
          [
            261,
            "        if (isEmpty(png) && isEmpty(svg)) {"
          ],
          [
            262,
            "            throw new BadRequestException(\"file\", \"Загрузите png или svg\");"
          ],
          [
            263,
            "        }"
          ],
          [
            264,
            "        ValidatedAsset pngAsset = isEmpty(png) ? null : validate(png, CampusMapFormat.PNG);"
          ],
          [
            265,
            "        ValidatedAsset svgAsset = isEmpty(svg) ? null : validate(svg, CampusMapFormat.SVG);"
          ],
          [
            266,
            ""
          ],
          [
            267,
            "        repository.lockCatalogForWrite();"
          ],
          [
            268,
            "        CampusMapAdminRepository.FloorRow floor ="
          ],
          [
            269,
            "                repository"
          ],
          [
            270,
            "                        .findFloorForUpdate(floorKey)"
          ],
          [
            271,
            "                        .filter(CampusMapAdminRepository.FloorRow::active)"
          ],
          [
            272,
            "                        .orElseThrow(() -> new ResourceNotFoundException(\"Этаж\", \"id\", floorKey));"
          ],
          [
            273,
            "        Optional<CampusMapAdminRepository.PlanRow> current = repository.findCurrentPlan(floor.id());"
          ],
          [
            274,
            "        return publishValidatedVersion(floor, label, pngAsset, svgAsset, current);"
          ],
          [
            275,
            "    }"
          ]
        ]
      },
      {
        "title": "Следующий номер, ревизия и переключение указателя",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java",
        "language": "java",
        "lines": [
          [
            283,
            "        Map<CampusMapFormat, ValidatedAsset> assets ="
          ],
          [
            284,
            "                carryForwardMissingFormats(current, pngAsset, svgAsset);"
          ],
          [
            285,
            "        long version = repository.highestPlanVersion(floor.id()) + 1L;"
          ],
          [
            286,
            "        if (version <= 0) {"
          ],
          [
            287,
            "            throw new ConflictException(\"version\", version, \"Номер версии схемы исчерпан\");"
          ],
          [
            288,
            "        }"
          ],
          [
            289,
            ""
          ],
          [
            290,
            "        CampusMapAdminRepository.CatalogRevision revision = repository.advanceCatalogRevision();"
          ],
          [
            291,
            "        String planLabel = defaultLabel(label, floor.label());"
          ],
          [
            292,
            "        long planId = repository.insertPlan(floor.id(), version, revision.id(), planLabel);"
          ],
          [
            293,
            "        for (CampusMapFormat format : CampusMapFormat.values()) {"
          ],
          [
            294,
            "            ValidatedAsset asset = assets.get(format);"
          ],
          [
            295,
            "            if (asset == null) {"
          ],
          [
            296,
            "                repository.insertSlot("
          ],
          [
            297,
            "                        planId,"
          ],
          [
            298,
            "                        format,"
          ],
          [
            299,
            "                        CampusMapFormatState.ABSENT,"
          ],
          [
            300,
            "                        expectedMime(format),"
          ],
          [
            301,
            "                        null,"
          ],
          [
            302,
            "                        0,"
          ],
          [
            303,
            "                        null,"
          ],
          [
            304,
            "                        null,"
          ],
          [
            305,
            "                        null);"
          ],
          [
            306,
            "                continue;"
          ],
          [
            329,
            "        repository.publishPlan(planId);"
          ],
          [
            330,
            "        repository.pointFloorAt(floor.id(), planId);"
          ],
          [
            331,
            "        return toPlan("
          ],
          [
            332,
            "                repository"
          ],
          [
            333,
            "                        .findPlan(floor.id(), version)"
          ],
          [
            334,
            "                        .orElseThrow("
          ],
          [
            335,
            "                                () ->"
          ],
          [
            336,
            "                                        new IllegalStateException("
          ],
          [
            337,
            "                                                \"published map version disappeared\")),"
          ],
          [
            338,
            "                revision.revision(),"
          ],
          [
            339,
            "                repository.findSlots(planId),"
          ],
          [
            340,
            "                floor.buildingId());"
          ]
        ]
      }
    ]
  },
  {
    "id": "campus-map-versions",
    "title": "Версия плана и ревизия каталога",
    "description": "При замене только одного формата готовый второй файл из текущей версии переносится в новую. Если готового файла нет, слот остаётся absent. Смена названия или кода корпуса либо этажа увеличивает ревизию каталога без новой версии плана. В веб-панели администратор может открыть сохранённую версию по номеру и скачать её готовые форматы.",
    "frames": [
      {
        "title": "Перенос готового формата из предыдущей версии",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java",
        "language": "java",
        "lines": [
          [
            549,
            "    private Map<CampusMapFormat, ValidatedAsset> carryForwardMissingFormats("
          ],
          [
            550,
            "            Optional<CampusMapAdminRepository.PlanRow> current,"
          ],
          [
            551,
            "            ValidatedAsset png,"
          ],
          [
            552,
            "            ValidatedAsset svg) {"
          ],
          [
            553,
            "        Map<CampusMapFormat, ValidatedAsset> assets = new EnumMap<>(CampusMapFormat.class);"
          ],
          [
            554,
            "        assets.put(CampusMapFormat.PNG, png);"
          ],
          [
            555,
            "        assets.put(CampusMapFormat.SVG, svg);"
          ],
          [
            556,
            "        if (current.isEmpty()) {"
          ],
          [
            557,
            "            return assets;"
          ],
          [
            558,
            "        }"
          ],
          [
            559,
            "        for (CampusMapFormat format : CampusMapFormat.values()) {"
          ],
          [
            560,
            "            if (assets.get(format) != null) {"
          ],
          [
            561,
            "                continue;"
          ],
          [
            562,
            "            }"
          ],
          [
            563,
            "            repository.findSlots(current.get().id()).stream()"
          ],
          [
            564,
            "                    .filter("
          ],
          [
            565,
            "                            slot ->"
          ],
          [
            566,
            "                                    slot.format() == format"
          ],
          [
            567,
            "                                            && slot.state() == CampusMapFormatState.READY"
          ],
          [
            568,
            "                                            && slot.assetId() != null)"
          ],
          [
            569,
            "                    .findFirst()"
          ],
          [
            570,
            "                    .flatMap("
          ],
          [
            571,
            "                            slot ->"
          ],
          [
            572,
            "                                    repository.findAsset("
          ],
          [
            573,
            "                                            slot.assetId(), current.get().id(), format))"
          ],
          [
            584,
            "                    .ifPresent(asset -> assets.put(format, asset));"
          ],
          [
            585,
            "        }"
          ],
          [
            586,
            "        return assets;"
          ]
        ]
      },
      {
        "title": "Изменение метаданных обновляет каталог",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapAdminService.java",
        "language": "java",
        "lines": [
          [
            353,
            "        if (!building.code().equals(code) && repository.existsBuildingCode(code)) {"
          ],
          [
            354,
            "            throw new ConflictException(\"code\", code, \"Корпус с таким кодом уже существует\");"
          ],
          [
            355,
            "        }"
          ],
          [
            356,
            "        if (!building.code().equals(code) || !building.label().equals(label)) {"
          ],
          [
            357,
            "            repository.updateBuilding(id, code, label);"
          ],
          [
            358,
            "            repository.advanceCatalogRevision();"
          ],
          [
            359,
            "        }"
          ],
          [
            379,
            "        if (!floor.code().equals(code) && repository.existsFloorCode(floor.buildingId(), code)) {"
          ],
          [
            380,
            "            throw new ConflictException("
          ],
          [
            381,
            "                    \"code\", code, \"Этаж с таким кодом уже существует в корпусе\");"
          ],
          [
            382,
            "        }"
          ],
          [
            383,
            "        if (!floor.code().equals(code) || !floor.label().equals(label)) {"
          ],
          [
            384,
            "            repository.updateFloor(id, code, label);"
          ],
          [
            385,
            "            repository.advanceCatalogRevision();"
          ]
        ]
      },
      {
        "title": "Администратор запрашивает номер версии",
        "path": "frontends/web-vue/src/features/admin/MapPage.vue",
        "language": "typescript",
        "lines": [
          [
            301,
            "    if (!selected.value) return;"
          ],
          [
            302,
            "    const value = await client.getVersion(selected.value.floor.id, version.value, admin.signal);"
          ],
          [
            303,
            "    admin.assertCurrent();"
          ],
          [
            304,
            "    plan.value = value;"
          ],
          [
            305,
            "  }, 'CAMPUS_MAP_MANAGE');"
          ],
          [
            306,
            "}"
          ],
          [
            307,
            "async function download(format: MapFormat) {"
          ],
          [
            308,
            "  await admin.run(async () => {"
          ],
          [
            309,
            "    const current = plan.value;"
          ],
          [
            310,
            "    const slot = current?.[format];"
          ],
          [
            311,
            "    if (!current || !slot?.id || slot.state !== 'ready') return;"
          ],
          [
            312,
            "    const blob = await client.downloadAsset(current.floorId, current.version, format, slot.id);"
          ],
          [
            313,
            "    admin.assertCurrent();"
          ],
          [
            314,
            "    if (!blob.size || blob.type.split(';')[0] !== slot.contentType)"
          ],
          [
            315,
            "      throw new Error('Файл схемы не прошёл проверку ответа.');"
          ],
          [
            723,
            "        <section class=\"web-stack\">"
          ],
          [
            724,
            "          <h3>Версии схемы</h3><form"
          ],
          [
            725,
            "            class=\"admin-actions\""
          ],
          [
            726,
            "            @submit.prevent=\"readVersion\""
          ],
          [
            727,
            "          >"
          ],
          [
            728,
            "            <label class=\"web-field\">Номер версии<input"
          ],
          [
            729,
            "              v-model=\"version\""
          ],
          [
            730,
            "              required"
          ],
          [
            731,
            "              pattern=\"[1-9][0-9]*\""
          ],
          [
            732,
            "              inputmode=\"numeric\""
          ],
          [
            733,
            "            ></label><button"
          ],
          [
            734,
            "              class=\"web-button\""
          ],
          [
            735,
            "              :disabled=\"locked || !version\""
          ],
          [
            736,
            "            >"
          ],
          [
            737,
            "              Открыть версию"
          ],
          [
            738,
            "            </button>"
          ]
        ]
      }
    ]
  },
  {
    "id": "campus-map-manifest",
    "title": "Каталог корпусов и этажей",
    "description": "Academic собирает активные корпуса, этажи и их текущие планы. Идентификаторы, ревизия каталога и номера версий приходят строками. ETag связан с ревизией, и совпавший If-None-Match даёт 304 без тела. Общий клиент умеет условный запрос, а текущий экран при загрузке вызывает getManifest без ETag.",
    "frames": [
      {
        "title": "Каталог содержит только активные объекты и текущие планы",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java",
        "language": "java",
        "lines": [
          [
            71,
            "    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)"
          ],
          [
            72,
            "    public ManifestResult readManifest(long knownRevision, InternalJwtClaims claims) {"
          ],
          [
            73,
            "        authorize(claims);"
          ],
          [
            74,
            "        if (knownRevision < EMPTY_REVISION) {"
          ],
          [
            75,
            "            throw failure(INVALID_ARGUMENT, \"known revision is invalid\");"
          ],
          [
            76,
            "        }"
          ],
          [
            77,
            ""
          ],
          [
            78,
            "        Catalog catalog = currentCatalog();"
          ],
          [
            79,
            "        if (knownRevision > EMPTY_REVISION && knownRevision == catalog.revision()) {"
          ],
          [
            80,
            "            return new ManifestResult(true, catalog.revision(), null);"
          ],
          [
            81,
            "        }"
          ],
          [
            82,
            ""
          ],
          [
            83,
            "        List<Building> buildingRows = repository.findActiveBuildings();"
          ],
          [
            84,
            "        List<Floor> floorRows = repository.findActiveFloors();"
          ],
          [
            85,
            "        List<Plan> currentPlanRows = repository.findCurrentPlans();"
          ],
          [
            91,
            "        List<ManifestBuilding> buildings = new ArrayList<>();"
          ],
          [
            92,
            "        for (Building building : buildingRows) {"
          ],
          [
            93,
            "            List<Floor> floors = floorsByBuilding.getOrDefault(building.id(), List.of());"
          ],
          [
            94,
            "            validateFloorOrder(floors);"
          ],
          [
            95,
            "            List<ManifestFloor> manifestFloors = new ArrayList<>();"
          ],
          [
            96,
            "            for (Floor floor : floors) {"
          ],
          [
            97,
            "                Plan plan = planForFloor(floor, plansByFloor);"
          ],
          [
            98,
            "                ManifestPlan aggregate ="
          ],
          [
            99,
            "                        plan == null ? null : toPlanAggregate(building.id(), plan, true);"
          ],
          [
            100,
            "                manifestFloors.add(new ManifestFloor(floor.id(), floor.label(), aggregate));"
          ],
          [
            101,
            "            }"
          ],
          [
            102,
            "            buildings.add(new ManifestBuilding(building.id(), building.label(), manifestFloors));"
          ],
          [
            103,
            "        }"
          ],
          [
            104,
            ""
          ],
          [
            105,
            "        Manifest manifest ="
          ],
          [
            106,
            "                new Manifest("
          ],
          [
            107,
            "                        catalog.schemaVersion(),"
          ],
          [
            108,
            "                        catalog.validationPolicyVersion(),"
          ],
          [
            109,
            "                        catalog.revision(),"
          ],
          [
            110,
            "                        buildings);"
          ]
        ]
      },
      {
        "title": "ETag и условный ответ 304",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapApiController.java",
        "language": "java",
        "lines": [
          [
            29,
            "    @Override"
          ],
          [
            30,
            "    public ResponseEntity<ManifestResponse> getManifest(String ifNoneMatch) {"
          ],
          [
            31,
            "        MapAcademicClient.ManifestResult result = maps.manifest(ifNoneMatch);"
          ],
          [
            32,
            "        String etag = etag(result.revision());"
          ],
          [
            33,
            "        if (result.unchanged()) {"
          ],
          [
            34,
            "            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)"
          ],
          [
            35,
            "                    .cacheControl(CacheControl.noCache().cachePrivate())"
          ],
          [
            36,
            "                    .eTag(etag)"
          ],
          [
            37,
            "                    .build();"
          ],
          [
            38,
            "        }"
          ],
          [
            39,
            "        return ResponseEntity.ok()"
          ],
          [
            40,
            "                .cacheControl(CacheControl.noCache().cachePrivate())"
          ],
          [
            41,
            "                .eTag(etag)"
          ],
          [
            42,
            "                .body(result.manifest());"
          ],
          [
            43,
            "    }"
          ],
          [
            79,
            "    private static String etag(long revision) {"
          ],
          [
            80,
            "        return \"\\\"\" + revision + \"\\\"\";"
          ],
          [
            81,
            "    }"
          ]
        ]
      },
      {
        "title": "Клиент поддерживает ETag",
        "path": "frontends/mobile-core/src/api/map-client.ts",
        "language": "typescript",
        "lines": [
          [
            65,
            "    const response = await this.requestResponse('/api/v1/map/manifest', {"
          ],
          [
            66,
            "      ...(etag ? { headers: { 'If-None-Match': etag } } : {}),"
          ],
          [
            67,
            "      ...(signal ? { signal } : {}),"
          ],
          [
            68,
            "    })"
          ],
          [
            69,
            "    if (response.status === 304) {"
          ],
          [
            70,
            "      await response.arrayBuffer()"
          ],
          [
            71,
            "      return { data: null, etag: response.headers.get('ETag') }"
          ],
          [
            72,
            "    }"
          ],
          [
            73,
            "    return {"
          ],
          [
            74,
            "      data: await this.json<MapManifest>(response),"
          ],
          [
            75,
            "      etag: response.headers.get('ETag'),"
          ],
          [
            76,
            "    }"
          ]
        ]
      },
      {
        "title": "Экран запрашивает каталог заново",
        "path": "frontends/mobile-core/src/features/map/MapScreen.vue",
        "language": "typescript",
        "lines": [
          [
            246,
            "async function loadManifest(): Promise<void> {"
          ],
          [
            247,
            "  const requestGeneration = lifecycleGeneration;"
          ],
          [
            248,
            "  loading.value = true;"
          ],
          [
            249,
            "  error.value = null;"
          ],
          [
            250,
            "  try {"
          ],
          [
            251,
            "    const response = await props.client.getManifest();"
          ],
          [
            252,
            "    if (disposed || requestGeneration !== lifecycleGeneration) return;"
          ],
          [
            253,
            "    manifest.value = response.data;"
          ],
          [
            254,
            "    const firstBuilding = response.data?.buildings[0];"
          ],
          [
            255,
            "    selectedBuildingId.value = firstBuilding?.id ?? null;"
          ],
          [
            256,
            "    selectedFloorId.value = firstBuilding?.floors[0]?.id ?? null;"
          ]
        ]
      },
      {
        "title": "JSON каталога",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentMapModels.java",
        "language": "java",
        "lines": [
          [
            34,
            "    public record ManifestResponse("
          ],
          [
            35,
            "            int schemaVersion,"
          ],
          [
            36,
            "            int validationPolicyVersion,"
          ],
          [
            37,
            "            String revision,"
          ],
          [
            38,
            "            List<Building> buildings) {"
          ],
          [
            51,
            "    public record Building(String id, String label, List<Floor> floors) {"
          ],
          [
            62,
            "    public record Floor(String id, String label, @Schema(nullable = true) Plan plan) {"
          ]
        ]
      },
      {
        "title": "Каталог · фрагмент с этажом без плана",
        "language": "json",
        "example": [
          "{",
          "  \"schemaVersion\": 1,",
          "  \"validationPolicyVersion\": 1,",
          "  \"revision\": \"18\",",
          "  \"buildings\": [",
          "    {",
          "      \"id\": \"41\",",
          "      \"label\": \"Учебный корпус\",",
          "      \"floors\": [",
          "        {",
          "          \"id\": \"72\",",
          "          \"label\": \"Второй этаж\",",
          "          \"plan\": null",
          "        }",
          "      ]",
          "    }",
          "  ]",
          "}"
        ]
      }
    ]
  },
  {
    "id": "campus-map-asset",
    "title": "От этажа к файлу",
    "description": "После выбора этажа приложение читает его актуальный план. Этаж без публикации возвращает 204. Для готовой версии ответ содержит plan со слотами PNG и SVG. Затем запрос файла указывает корпус, этаж, версию, формат и ID ресурса. Сервер сверяет эту связь и готовность файла и возвращает байты с MIME выбранного формата.",
    "frames": [
      {
        "title": "Проверка этажа и текущей опубликованной версии",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java",
        "language": "java",
        "lines": [
          [
            116,
            "    public PlanResult readFloorPlan(String buildingId, String floorId, InternalJwtClaims claims) {"
          ],
          [
            117,
            "        authorize(claims);"
          ],
          [
            118,
            "        long buildingKey = parseCanonicalId(buildingId, \"building id\");"
          ],
          [
            119,
            "        long floorKey = parseCanonicalId(floorId, \"floor id\");"
          ],
          [
            120,
            "        Catalog catalog = currentCatalog();"
          ],
          [
            121,
            ""
          ],
          [
            122,
            "        Building building ="
          ],
          [
            123,
            "                repository"
          ],
          [
            124,
            "                        .findActiveBuilding(buildingKey)"
          ],
          [
            125,
            "                        .orElseThrow(() -> failure(NOT_FOUND, \"campus map building was not found\"));"
          ],
          [
            126,
            "        Floor floor ="
          ],
          [
            127,
            "                repository"
          ],
          [
            128,
            "                        .findActiveFloor(floorKey)"
          ],
          [
            129,
            "                        .orElseThrow(() -> failure(NOT_FOUND, \"campus map floor was not found\"));"
          ],
          [
            130,
            "        if (floor.buildingId() != building.id()) {"
          ],
          [
            131,
            "            throw failure(NOT_FOUND, \"campus map floor was not found\");"
          ],
          [
            132,
            "        }"
          ],
          [
            133,
            "        if (floor.currentVersionId() == null) {"
          ],
          [
            134,
            "            return new PlanResult(null);"
          ],
          [
            140,
            "        Plan plan ="
          ],
          [
            141,
            "                repository"
          ],
          [
            142,
            "                        .findPlanById(floor.currentVersionId(), floor.id())"
          ],
          [
            143,
            "                        .orElseThrow("
          ],
          [
            144,
            "                                () ->"
          ],
          [
            145,
            "                                        failure("
          ],
          [
            146,
            "                                                DATA_LOSS,"
          ],
          [
            147,
            "                                                \"campus map current plan graph is inconsistent\"));"
          ],
          [
            148,
            "        if (plan.publishedAt() == null) {"
          ],
          [
            149,
            "            throw failure(FAILED_PRECONDITION, \"campus map current plan is not published\");"
          ],
          [
            150,
            "        }"
          ],
          [
            151,
            "        validatePlanIdentity(plan, floor.id());"
          ],
          [
            152,
            "        return new PlanResult(toPlanAggregate(building.id(), plan, true));"
          ]
        ]
      },
      {
        "title": "204 для этажа без плана",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapApiController.java",
        "language": "java",
        "lines": [
          [
            45,
            "    @Override"
          ],
          [
            46,
            "    public ResponseEntity<PlanResponse> getFloorPlan(String buildingId, String floorId) {"
          ],
          [
            47,
            "        PlanResponse response = maps.floorPlan(buildingId, floorId);"
          ],
          [
            48,
            "        if (response.plan() == null) {"
          ],
          [
            49,
            "            return ResponseEntity.status(HttpStatus.NO_CONTENT)"
          ],
          [
            50,
            "                    .cacheControl(CacheControl.noStore())"
          ],
          [
            51,
            "                    .build();"
          ],
          [
            52,
            "        }"
          ],
          [
            53,
            "        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);"
          ],
          [
            54,
            "    }"
          ]
        ]
      },
      {
        "title": "DTO плана и два формата",
        "path": "services/mobile-bff/mobile-bff-api-contract/src/main/java/ru/rutcampustrack/mobilebff/contract/model/StudentMapModels.java",
        "language": "java",
        "lines": [
          [
            72,
            "    public record Plan("
          ],
          [
            73,
            "            String buildingId,"
          ],
          [
            74,
            "            String floorId,"
          ],
          [
            75,
            "            String version,"
          ],
          [
            76,
            "            String label,"
          ],
          [
            77,
            "            FormatSlot png,"
          ],
          [
            78,
            "            FormatSlot svg) {"
          ],
          [
            98,
            "    public record FormatSlot("
          ],
          [
            99,
            "            Format format,"
          ],
          [
            100,
            "            FormatState state,"
          ],
          [
            101,
            "            String contentType,"
          ],
          [
            102,
            "            @Schema(nullable = true) String id,"
          ],
          [
            103,
            "            long bytes,"
          ],
          [
            104,
            "            @Schema(nullable = true) String sha256,"
          ],
          [
            105,
            "            @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) Integer width,"
          ],
          [
            106,
            "            @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL) Integer height,"
          ],
          [
            107,
            "            @Schema(nullable = true) @JsonInclude(JsonInclude.Include.NON_NULL)"
          ],
          [
            108,
            "                    List<Double> viewBox) {"
          ],
          [
            149,
            "    public record PlanResponse(@Schema(nullable = true) Plan plan) {}"
          ]
        ]
      },
      {
        "title": "Текущий план · SVG готов, PNG отсутствует",
        "language": "json",
        "example": [
          "{",
          "  \"plan\": {",
          "    \"buildingId\": \"41\",",
          "    \"floorId\": \"71\",",
          "    \"version\": \"3\",",
          "    \"label\": \"Схема первого этажа\",",
          "    \"png\": {",
          "      \"format\": \"png\",",
          "      \"state\": \"absent\",",
          "      \"contentType\": \"image/png\",",
          "      \"id\": null,",
          "      \"bytes\": 0,",
          "      \"sha256\": null",
          "    },",
          "    \"svg\": {",
          "      \"format\": \"svg\",",
          "      \"state\": \"ready\",",
          "      \"contentType\": \"image/svg+xml\",",
          "      \"id\": \"903\",",
          "      \"bytes\": 4096,",
          "      \"sha256\": \"ef458de0e003f3d9f4f35a972d602aa0d898f507530a85dca6319705fb4ce30f\"",
          "    }",
          "  }",
          "}"
        ]
      },
      {
        "title": "Файл привязан к версии, формату и ID",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/map/CampusMapReadService.java",
        "language": "java",
        "lines": [
          [
            186,
            "        Building building ="
          ],
          [
            187,
            "                repository"
          ],
          [
            188,
            "                        .findActiveBuilding(buildingKey)"
          ],
          [
            189,
            "                        .orElseThrow(() -> failure(NOT_FOUND, \"campus map building was not found\"));"
          ],
          [
            190,
            "        Floor floor ="
          ],
          [
            191,
            "                repository"
          ],
          [
            192,
            "                        .findActiveFloor(floorKey)"
          ],
          [
            193,
            "                        .orElseThrow(() -> failure(NOT_FOUND, \"campus map floor was not found\"));"
          ],
          [
            194,
            "        if (floor.buildingId() != building.id()) {"
          ],
          [
            195,
            "            throw failure(NOT_FOUND, \"campus map floor was not found\");"
          ],
          [
            196,
            "        }"
          ],
          [
            201,
            "        Plan plan ="
          ],
          [
            202,
            "                repository"
          ],
          [
            203,
            "                        .findPlan(floor.id(), version)"
          ],
          [
            204,
            "                        .orElseThrow(() -> failure(NOT_FOUND, \"campus map plan was not found\"));"
          ],
          [
            205,
            "        if (plan.publishedAt() == null) {"
          ],
          [
            206,
            "            throw failure(FAILED_PRECONDITION, \"campus map plan is not published\");"
          ],
          [
            207,
            "        }"
          ],
          [
            208,
            "        validatePlanIdentity(plan, floor.id());"
          ],
          [
            213,
            "        FormatSlot slot = selectedSlot(repository.findFormatSlots(plan.id()), plan.id(), format);"
          ],
          [
            214,
            "        if (!Objects.equals(slot.assetId(), assetKey)) {"
          ],
          [
            215,
            "            throw failure(NOT_FOUND, \"campus map asset was not found\");"
          ],
          [
            216,
            "        }"
          ],
          [
            217,
            "        if (cancellation.getAsBoolean()) {"
          ],
          [
            218,
            "            return Optional.empty();"
          ],
          [
            219,
            "        }"
          ],
          [
            220,
            "        AssetMetadata metadata = validateReadySlot(slot, plan, true);"
          ],
          [
            221,
            "        if (cancellation.getAsBoolean()) {"
          ],
          [
            222,
            "            return Optional.empty();"
          ],
          [
            223,
            "        }"
          ],
          [
            224,
            ""
          ],
          [
            225,
            "        Asset asset ="
          ],
          [
            226,
            "                repository"
          ],
          [
            227,
            "                        .loadAsset(assetKey, plan.id(), format)"
          ],
          [
            228,
            "                        .orElseThrow("
          ],
          [
            229,
            "                                () ->"
          ],
          [
            230,
            "                                        failure("
          ],
          [
            231,
            "                                                DATA_LOSS,"
          ],
          [
            232,
            "                                                \"campus map asset content is unavailable\"));"
          ]
        ]
      },
      {
        "title": "HTTP возвращает содержимое файла",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/map/MapApiController.java",
        "language": "java",
        "lines": [
          [
            56,
            "    @Override"
          ],
          [
            57,
            "    public ResponseEntity<Resource> readAsset("
          ],
          [
            58,
            "            String buildingId, String floorId, String version, Format format, String assetId) {"
          ],
          [
            59,
            "        MapAcademicClient.Download download ="
          ],
          [
            60,
            "                maps.asset(buildingId, floorId, version, format, assetId);"
          ],
          [
            61,
            "        HttpHeaders headers = new HttpHeaders();"
          ],
          [
            62,
            "        headers.setContentType(MediaType.parseMediaType(download.contentType()));"
          ],
          [
            63,
            "        headers.setContentLength(download.bytes().length);"
          ],
          [
            64,
            "        headers.setContentDisposition("
          ],
          [
            65,
            "                ContentDisposition.inline()"
          ],
          [
            66,
            "                        .filename(\"campus-map.\" + format.name(), StandardCharsets.UTF_8)"
          ],
          [
            67,
            "                        .build());"
          ],
          [
            68,
            "        headers.setCacheControl(CacheControl.noStore());"
          ],
          [
            69,
            "        return new ResponseEntity<>("
          ],
          [
            70,
            "                new ByteArrayResource(download.bytes()), headers, HttpStatus.OK);"
          ],
          [
            71,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "campus-map-viewer",
    "title": "Формат и масштаб на устройстве",
    "description": "Экран скачивает готовые форматы и для просмотра предпочитает SVG. Если доступен только PNG, экран показывает его. При смене этажа предыдущий запрос прерывается, а устаревший ответ не подменяет текущий. Масштаб от 1× до 4× меняется на устройстве с сохранением центра области просмотра.",
    "frames": [
      {
        "title": "Отмена старого запроса и загрузка плана",
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
            286,
            "  loadingAsset.value = true;"
          ],
          [
            287,
            "  let planResolved = false;"
          ],
          [
            288,
            "  try {"
          ],
          [
            289,
            "    const plan = await props.client.getFloorPlan(building.id, floor.id, controller.signal);"
          ],
          [
            290,
            "    if (!isCurrentFloorRequest(requestId, floorKey)) return;"
          ],
          [
            291,
            "    currentPlan.value = plan;"
          ],
          [
            292,
            "    planResolved = true;"
          ],
          [
            293,
            "    if (!plan) return;"
          ],
          [
            294,
            ""
          ]
        ]
      },
      {
        "title": "Загрузка READY и выбор изображения",
        "path": "frontends/mobile-core/src/features/map/MapScreen.vue",
        "language": "typescript",
        "lines": [
          [
            79,
            "const previewUrl = computed(() => svgUrl.value ?? pngUrl.value);"
          ],
          [
            80,
            "const viewport = ref<HTMLElement | null>(null);"
          ],
          [
            81,
            "const zoom = ref(1);"
          ],
          [
            82,
            "const aspectRatio = ref(1);"
          ],
          [
            310,
            "    const downloads: Promise<void>[] = [];"
          ],
          [
            311,
            "    if (plan.svg.state === 'ready' && plan.svg.id) {"
          ],
          [
            312,
            "      downloads.push("
          ],
          [
            313,
            "        props.client"
          ],
          [
            314,
            "          .downloadAsset(building.id, floor.id, plan.version, 'svg', plan.svg.id, controller.signal)"
          ],
          [
            315,
            "          .then((blob) => {"
          ],
          [
            316,
            "            if (isCurrentFloorRequest(requestId, floorKey)) {"
          ],
          [
            317,
            "              svgUrl.value = URL.createObjectURL(blob);"
          ],
          [
            318,
            "              svgAssetRequest.value = requestId;"
          ],
          [
            319,
            "            }"
          ],
          [
            320,
            "          }),"
          ],
          [
            321,
            "      );"
          ],
          [
            322,
            "    }"
          ],
          [
            323,
            "    if (plan.png.state === 'ready' && plan.png.id) {"
          ],
          [
            324,
            "      downloads.push("
          ],
          [
            325,
            "        props.client"
          ],
          [
            326,
            "          .downloadAsset(building.id, floor.id, plan.version, 'png', plan.png.id, controller.signal)"
          ],
          [
            327,
            "          .then((blob) => {"
          ],
          [
            328,
            "            if (isCurrentFloorRequest(requestId, floorKey)) {"
          ],
          [
            329,
            "              pngUrl.value = URL.createObjectURL(blob);"
          ],
          [
            330,
            "              svgAssetRequest.value = requestId;"
          ],
          [
            331,
            "            }"
          ],
          [
            332,
            "          }),"
          ],
          [
            333,
            "      );"
          ],
          [
            334,
            "    }"
          ],
          [
            335,
            "    await Promise.all(downloads);"
          ]
        ]
      },
      {
        "title": "Проверка типа файла и актуальности запроса",
        "path": "frontends/mobile-core/src/api/map-client.ts",
        "language": "typescript",
        "lines": [
          [
            104,
            "    const generation = this.currentGeneration()"
          ],
          [
            105,
            "    const response = await this.requestResponse("
          ],
          [
            106,
            "      `/api/v1/map/buildings/${encodeURIComponent(buildingId)}/floors/${encodeURIComponent(floorId)}` +"
          ],
          [
            107,
            "        `/plans/${encodeURIComponent(version)}/assets/${format}/${encodeURIComponent(assetId)}`,"
          ],
          [
            108,
            "      {"
          ],
          [
            109,
            "        headers: { Accept: format === 'png' ? 'image/png' : 'image/svg+xml' },"
          ],
          [
            110,
            "        ...(signal ? { signal } : {}),"
          ],
          [
            111,
            "      },"
          ],
          [
            112,
            "    )"
          ],
          [
            113,
            "    const blob = await response.blob()"
          ],
          [
            114,
            "    this.assertCurrentGeneration(generation)"
          ],
          [
            115,
            "    const expectedType = format === 'png' ? 'image/png' : 'image/svg+xml'"
          ],
          [
            116,
            "    if (blob.size === 0 || blob.type.split(';', 1)[0]?.toLowerCase() !== expectedType) {"
          ],
          [
            117,
            "      throw new Error('Сервер вернул некорректный файл схемы.')"
          ],
          [
            118,
            "    }"
          ],
          [
            119,
            "    return blob"
          ],
          [
            120,
            "  }"
          ]
        ]
      },
      {
        "title": "Проверка выбранного этажа и сессии",
        "path": "frontends/mobile-core/src/features/map/MapScreen.vue",
        "language": "typescript",
        "lines": [
          [
            352,
            "function isCurrentFloorRequest(requestId: number, floorKey: string | null): boolean {"
          ],
          [
            353,
            "  const building = selectedBuilding.value;"
          ],
          [
            354,
            "  const floor = selectedFloor.value;"
          ],
          [
            355,
            "  return ("
          ],
          [
            356,
            "    !disposed &&"
          ],
          [
            357,
            "    props.client.isCurrentGeneration(assetGeneration) &&"
          ],
          [
            358,
            "    requestId === assetRequest &&"
          ],
          [
            359,
            "    floorKey !== null &&"
          ],
          [
            360,
            "    building !== null &&"
          ],
          [
            361,
            "    floor !== null &&"
          ],
          [
            362,
            "    floorKey === `${building.id}:${floor.id}`"
          ],
          [
            363,
            "  );"
          ],
          [
            364,
            "}"
          ]
        ]
      },
      {
        "title": "Локальный масштаб от 1× до 4×",
        "path": "frontends/mobile-core/src/features/map/MapScreen.vue",
        "language": "typescript",
        "lines": [
          [
            102,
            "function zoomBy(delta: number): void {"
          ],
          [
            103,
            "  const area = viewport.value;"
          ],
          [
            104,
            "  if (!area || !viewerReady.value) return;"
          ],
          [
            105,
            "  // Bounded local viewing scale; native scrolling clamps both pan axes."
          ],
          [
            106,
            "  const scale = Math.max(1, Math.min(4, zoom.value + delta));"
          ],
          [
            107,
            "  if (scale === zoom.value) return;"
          ],
          [
            108,
            "  stopDrag();"
          ],
          [
            109,
            "  const centerX = (area.scrollLeft + area.clientWidth / 2) / zoom.value;"
          ],
          [
            110,
            "  const centerY = (area.scrollTop + area.clientHeight / 2) / zoom.value;"
          ],
          [
            111,
            "  const revision = viewRevision;"
          ],
          [
            112,
            "  zoom.value = scale;"
          ],
          [
            113,
            "  void nextTick(() => {"
          ],
          [
            114,
            "    if (revision !== viewRevision || viewport.value !== area || zoom.value !== scale) return;"
          ],
          [
            115,
            "    area.scrollTo(centerX * scale - area.clientWidth / 2, centerY * scale - area.clientHeight / 2);"
          ],
          [
            116,
            "  });"
          ],
          [
            117,
            "}"
          ]
        ]
      }
    ]
  }
]
