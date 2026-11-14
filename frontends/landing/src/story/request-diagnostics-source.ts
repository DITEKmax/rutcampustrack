import type { StoryOperation } from './story-source'

export const requestDiagnosticsOperations: StoryOperation[] = [
  {
    "id": "request-diagnostics-trace",
    "title": "Одна отметка в логах и трассе",
    "description": "Студент сообщает X-Request-Id из ответа на отметку. Gateway создаёт его для конкретного HTTP-запроса. Поддержка находит по нему связанные записи и затем ищет traceId, который объединяет интервалы выполнения в распределённой трассе. У отдельных интервалов свой spanId. В примере лога достаточно трёх полей. Фильтр экспорта оставляет разрешённые протокольные атрибуты и шаблон серверного маршрута, убирает необработанное содержимое исключений и URL клиента.",
    "frames": [
      {
        "title": "Gateway создаёт и возвращает ID запроса",
        "path": "services/api-gateway/src/main/java/ru/rutcampustrack/gateway/config/RequestCorrelationHttpHandlerDecorator.java",
        "language": "java",
        "lines": [
          [
            18,
            "        return (request, response) -> {"
          ],
          [
            19,
            "            String requestId = RequestCorrelation.newId();"
          ],
          [
            20,
            "            var canonical ="
          ],
          [
            21,
            "                    request.mutate()"
          ],
          [
            22,
            "                            .headers("
          ],
          [
            23,
            "                                    headers -> {"
          ],
          [
            24,
            "                                        // Public diagnostic headers never supply credentials or"
          ],
          [
            25,
            "                                        // arbitrary baggage."
          ],
          [
            26,
            "                                        headers.remove(\"baggage\");"
          ],
          [
            27,
            "                                        headers.set(RequestCorrelation.HEADER, requestId);"
          ],
          [
            28,
            "                                        headers.set(RequestCorrelation.BAGGAGE_FIELD, requestId);"
          ],
          [
            29,
            "                                    })"
          ],
          [
            30,
            "                            .build();"
          ],
          [
            31,
            "            response.getHeaders().set(RequestCorrelation.HEADER, requestId);"
          ],
          [
            32,
            "            response.beforeCommit("
          ],
          [
            33,
            "                    () -> {"
          ],
          [
            34,
            "                        response.getHeaders().set(RequestCorrelation.HEADER, requestId);"
          ],
          [
            35,
            "                        return Mono.empty();"
          ],
          [
            36,
            "                    });"
          ],
          [
            37,
            "            return delegate.handle(canonical, response);"
          ]
        ]
      },
      {
        "title": "Интеграционная проверка связывает HTTP, MDC и traceparent",
        "path": "services/api-gateway/src/test/java/ru/rutcampustrack/gateway/observability/GatewayBffTracePropagationIT.java",
        "language": "java",
        "lines": [
          [
            406,
            "            assertThat(seen).hasSize(4);"
          ],
          [
            407,
            "            assertThat(seen).extracting(Seen::requestId).doesNotHaveDuplicates();"
          ],
          [
            408,
            "            assertThat(seen).extracting(Seen::traceId).doesNotHaveDuplicates();"
          ],
          [
            409,
            "            for (Seen call : seen) {"
          ],
          [
            410,
            "                assertThat(RequestCorrelation.isCanonical(call.requestId())).isTrue();"
          ],
          [
            411,
            "                assertThat(call.mdcTrace()).isEqualTo(call.traceId());"
          ],
          [
            412,
            "                assertThat(call.mdcRequest()).isEqualTo(call.requestId());"
          ],
          [
            413,
            "                assertThat(call.durableRequestId()).isEqualTo(call.requestId());"
          ],
          [
            414,
            "                assertThat(call.durableTraceparent())"
          ],
          [
            415,
            "                        .startsWith(\"00-\" + call.traceId() + \"-\" + call.spanId() + \"-\");"
          ]
        ]
      },
      {
        "title": "Разрешённые поля в экспортируемом span",
        "path": "services/shared/shared-observability/src/main/java/ru/rutcampustrack/shared/observability/SafeTraceSpanFilter.java",
        "language": "java",
        "lines": [
          [
            45,
            "    public FinishedSpan map(FinishedSpan span) {"
          ],
          [
            46,
            "        SpanData original = OtelFinishedSpan.toOtel(span);"
          ],
          [
            47,
            "        Attributes safeAttributes = safeAttributes(original.getAttributes(), original.getKind());"
          ],
          [
            48,
            "        List<EventData> safeEvents ="
          ],
          [
            49,
            "                original.getEvents().stream()"
          ],
          [
            50,
            "                        .map("
          ],
          [
            51,
            "                                event ->"
          ],
          [
            52,
            "                                        EventData.create("
          ],
          [
            53,
            "                                                event.getEpochNanos(),"
          ],
          [
            54,
            "                                                \"exception\".equals(event.getName())"
          ],
          [
            55,
            "                                                        ? \"exception\""
          ],
          [
            56,
            "                                                        : \"protocol.event\","
          ],
          [
            57,
            "                                                safeExceptionType(event.getAttributes())))"
          ],
          [
            58,
            "                        .toList();"
          ],
          [
            127,
            "                    boolean allowed ="
          ],
          [
            128,
            "                            switch (name) {"
          ],
          [
            129,
            "                                case \"http.method\", \"http.request.method\" ->"
          ],
          [
            130,
            "                                        METHODS.contains(value);"
          ],
          [
            131,
            "                                case \"http.status_code\", \"http.response.status_code\" ->"
          ],
          [
            132,
            "                                        value.matches(\"[1-5][0-9]{2}\");"
          ],
          [
            133,
            "                                case \"requestId\", \"messaging.message.id\" ->"
          ],
          [
            134,
            "                                        RequestCorrelation.isCanonical(value);"
          ],
          [
            135,
            "                                // Only server conventions guarantee registered route templates."
          ],
          [
            136,
            "                                // Client uri may be a literal capability path passed to WebClient."
          ],
          [
            137,
            "                                case \"uri\", \"http.route\" ->"
          ],
          [
            138,
            "                                        kind == SpanKind.SERVER"
          ],
          [
            139,
            "                                                && value.length() <= 256"
          ],
          [
            140,
            "                                                && value.startsWith(\"/\")"
          ],
          [
            141,
            "                                                && !value.contains(\"?\")"
          ],
          [
            142,
            "                                                && !value.contains(\"#\")"
          ],
          [
            143,
            "                                                && !value.contains(\"://\");"
          ],
          [
            144,
            "                                default ->"
          ],
          [
            145,
            "                                        PROTOCOL_KEYS.contains(name)"
          ],
          [
            146,
            "                                                && PROTOCOL_VALUE.matcher(value).matches();"
          ],
          [
            147,
            "                            };"
          ],
          [
            148,
            "                    if (allowed) {"
          ],
          [
            149,
            "                        put(safe, key, rawValue);"
          ],
          [
            150,
            "                    }"
          ],
          [
            151,
            "                });"
          ],
          [
            152,
            "        return safe.build();"
          ]
        ]
      },
      {
        "title": "Условный фрагмент корреляции в JSON-логе",
        "language": "json",
        "example": [
          "{",
          "  \"requestId\": \"11111111-1111-4111-8111-111111111111\",",
          "  \"traceId\": \"7c7d383384ac428099354995bc1f6545\",",
          "  \"spanId\": \"d2df87445a17409c\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "request-diagnostics-accepted",
    "title": "HTTP 202 и потерянный ответ",
    "description": "При удалении группы HTTP 202 с фазой COMMITTING означает, что операция принята и продолжается. Если ответ потерялся, клиент разрешает исходную попытку по тому же UUID отдельным запросом resolve. Academic проверяет владельца и группу. Принятая операция продолжается. Если исходный запрос не успел сохраниться, resolve фиксирует CANCELLED с причиной NOT_ACCEPTED, чтобы запоздалая попытка не запустила удаление.",
    "frames": [
      {
        "title": "Подтверждение и отдельный resolve",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupdeletion/GroupDeletionController.java",
        "language": "java",
        "lines": [
          [
            24,
            "    @Override"
          ],
          [
            25,
            "    public ResponseEntity<Operation> confirm(long id, UUID key, ConfirmRequest request) {"
          ],
          [
            26,
            "        var result = service.confirm(id, key, request);"
          ],
          [
            27,
            "        return ResponseEntity.status(result.phase() == Phase.COMPLETED ? 200 : 202)"
          ],
          [
            28,
            "                .cacheControl(CacheControl.noStore())"
          ],
          [
            29,
            "                .body(result);"
          ],
          [
            30,
            "    }"
          ],
          [
            31,
            ""
          ],
          [
            32,
            "    @Override"
          ],
          [
            33,
            "    public ResponseEntity<Operation> resolve(long id, UUID operationId) {"
          ],
          [
            34,
            "        return ResponseEntity.ok()"
          ],
          [
            35,
            "                .cacheControl(CacheControl.noStore())"
          ],
          [
            36,
            "                .body(service.resolve(id, operationId));"
          ],
          [
            37,
            "    }"
          ]
        ]
      },
      {
        "title": "Ответ описывает фазу сохранённой операции",
        "path": "services/academic-service/academic-api-contract/src/main/java/ru/rutcampustrack/academic/contract/dto/group/GroupDeletionModels.java",
        "language": "java",
        "lines": [
          [
            39,
            "    @Schema(name = \"AdminGroupDeletionOperation\")"
          ],
          [
            40,
            "    public record Operation("
          ],
          [
            41,
            "            UUID operationId,"
          ],
          [
            42,
            "            String groupId,"
          ],
          [
            43,
            "            Phase phase,"
          ],
          [
            44,
            "            boolean retryable,"
          ],
          [
            45,
            "            Map<String, Long> counts,"
          ],
          [
            46,
            "            String failure) {}"
          ]
        ]
      },
      {
        "title": "HTTP 202 · поля операции в работе",
        "language": "json",
        "example": [
          "{",
          "  \"operationId\": \"22222222-2222-4222-8222-222222222222\",",
          "  \"groupId\": \"41\",",
          "  \"phase\": \"COMMITTING\",",
          "  \"retryable\": true",
          "}"
        ]
      },
      {
        "title": "Resolve проверяет исходную группу и владельца",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupdeletion/GroupDeletionService.java",
        "language": "java",
        "lines": [
          [
            73,
            "    public Operation resolve(long group, UUID id) {"
          ],
          [
            74,
            "        authorize();"
          ],
          [
            75,
            "        if (group <= 0) throw GroupDeletionStore.error(400, \"invalid_group_id\");"
          ],
          [
            76,
            "        var operation = store.cancelUnaccepted(id, group);"
          ],
          [
            77,
            "        // No receipt body, ownership or existence detail is disclosed across either scope."
          ],
          [
            78,
            "        if (operation.actor() != context.getClaims().userId() || operation.group() != group)"
          ],
          [
            79,
            "            throw GroupDeletionStore.error(404, \"group_deletion_operation_not_found\");"
          ],
          [
            80,
            "        return acceptedResponse(id);"
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
            "    public Operation status(UUID id) {"
          ],
          [
            84,
            "        authorize();"
          ],
          [
            85,
            "        return acceptedResponse(id);"
          ],
          [
            86,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "request-diagnostics-phases",
    "title": "Продолжение по сохранённой фазе",
    "description": "Academic координирует Auth, Schedule и Attendance. PREPARING запрашивает подготовку и фиксацию условий у участников. В COMMITTING координатор ждёт ответы всех трёх и завершает локальную операцию после синхронизации каталога. Если подготовка устарела или Auth отклонил коммит, операция переходит в RELEASING и снимает ранее поставленные ограничения. Фоновая задача перечитывает сохранённые незавершённые операции и продолжает их с текущей фазы.",
    "frames": [
      {
        "title": "Планировщик подбирает сохранённые операции",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupdeletion/GroupDeletionCoordinator.java",
        "language": "java",
        "lines": [
          [
            32,
            "    @Scheduled(fixedDelayString = \"${group-deletion.retry-delay-ms:2000}\")"
          ],
          [
            33,
            "    @SchedulerLock("
          ],
          [
            34,
            "            name = \"academic-group-deletion-retry\","
          ],
          [
            35,
            "            lockAtMostFor = \"PT5M\","
          ],
          [
            36,
            "            lockAtLeastFor = \"PT1S\")"
          ],
          [
            37,
            "    public void resume() {"
          ],
          [
            38,
            "        for (UUID id : store.pending()) advance(id);"
          ],
          [
            39,
            "    }"
          ],
          [
            40,
            ""
          ],
          [
            41,
            "    public synchronized void advance(UUID id) {"
          ],
          [
            42,
            "        var operation = store.get(id);"
          ],
          [
            43,
            "        if (operation == null"
          ],
          [
            44,
            "                || operation.phase() == Phase.COMPLETED"
          ],
          [
            45,
            "                || operation.phase() == Phase.CANCELLED) return;"
          ],
          [
            46,
            "        try {"
          ],
          [
            47,
            "            var manifest = GroupDeletionManifest.parseFrom(operation.receipts());"
          ]
        ]
      },
      {
        "title": "Подготовка, применение и снятие ограничений",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupdeletion/GroupDeletionCoordinator.java",
        "language": "java",
        "lines": [
          [
            48,
            "            if (operation.phase() == Phase.RELEASING) {"
          ],
          [
            49,
            "                apply(operation, manifest, GroupDeletionAction.RELEASE);"
          ],
          [
            50,
            "                store.cancel(id);"
          ],
          [
            51,
            "                return;"
          ],
          [
            52,
            "            }"
          ],
          [
            53,
            "            if (operation.phase() == Phase.PREPARING) {"
          ],
          [
            54,
            "                apply(operation, manifest, GroupDeletionAction.PREPARE);"
          ],
          [
            55,
            "                apply(operation, manifest, GroupDeletionAction.SEAL);"
          ],
          [
            56,
            "                store.beginCommit(id);"
          ],
          [
            57,
            "                operation = store.get(id);"
          ],
          [
            58,
            "            }"
          ],
          [
            59,
            "            if (operation.phase() == Phase.COMMITTING) {"
          ],
          [
            60,
            "                if (!apply(operation, manifest, GroupDeletionAction.COMMIT)) {"
          ],
          [
            61,
            "                    store.rejectCommit(id);"
          ],
          [
            62,
            "                    return;"
          ],
          [
            63,
            "                }"
          ],
          [
            64,
            "                // Auth has published its grant removal. Catch up its existing restricted feed"
          ],
          [
            65,
            "                // before local completion makes registry/dashboard readers available again."
          ],
          [
            66,
            "                catalog.poll();"
          ],
          [
            67,
            "                store.finish(id);"
          ],
          [
            68,
            "            }"
          ],
          [
            69,
            "        } catch (org.springframework.web.server.ResponseStatusException stale) {"
          ],
          [
            70,
            "            if (stale.getStatusCode().value() == 409 && operation.phase() == Phase.PREPARING)"
          ],
          [
            71,
            "                store.release(id, \"STALE_PREVIEW\");"
          ],
          [
            72,
            "            else store.failure(id);"
          ],
          [
            73,
            "        } catch (Exception unavailable) {"
          ],
          [
            74,
            "            store.failure(id);"
          ],
          [
            75,
            "        }"
          ]
        ]
      },
      {
        "title": "Три участника по gRPC",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupdeletion/GroupDeletionParticipants.java",
        "language": "java",
        "lines": [
          [
            15,
            "@Component"
          ],
          [
            16,
            "public class GroupDeletionParticipants {"
          ],
          [
            17,
            "    @GrpcClient(\"auth-service\")"
          ],
          [
            18,
            "    private AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub auth;"
          ],
          [
            19,
            ""
          ],
          [
            20,
            "    @GrpcClient(\"schedule-service\")"
          ],
          [
            21,
            "    private GroupDeletionParticipantGrpc.GroupDeletionParticipantBlockingStub schedule;"
          ],
          [
            22,
            ""
          ],
          [
            23,
            "    @GrpcClient(\"attendance-service\")"
          ],
          [
            24,
            "    private GroupDeletionParticipantGrpc.GroupDeletionParticipantBlockingStub attendance;"
          ]
        ]
      },
      {
        "title": "Каждый участник возвращает подтверждение",
        "path": "services/academic-service/academic-app/src/main/java/ru/rutcampustrack/academic/groupdeletion/GroupDeletionCoordinator.java",
        "language": "java",
        "lines": [
          [
            78,
            "    private boolean apply("
          ],
          [
            79,
            "            GroupDeletionStore.Saved operation,"
          ],
          [
            80,
            "            GroupDeletionManifest manifest,"
          ],
          [
            81,
            "            GroupDeletionAction action) {"
          ],
          [
            82,
            "        if (manifest.getParticipantsCount() != 3)"
          ],
          [
            83,
            "            throw new IllegalStateException(\"Invalid saved participant manifest\");"
          ],
          [
            84,
            "        for (int i = 0; i < 3; i++) {"
          ],
          [
            85,
            "            var saved = manifest.getParticipants(i);"
          ],
          [
            86,
            "            var command ="
          ],
          [
            87,
            "                    GroupDeletionCommand.newBuilder()"
          ],
          [
            88,
            "                            .setOperationId(operation.id().toString())"
          ],
          [
            103,
            "                            .build();"
          ],
          [
            104,
            "            var receipt = participants.apply(i, command);"
          ],
          [
            105,
            "            if (receipt.getCommitRejected()) return false;"
          ],
          [
            106,
            "        }"
          ],
          [
            107,
            "        return true;"
          ],
          [
            108,
            "    }"
          ]
        ]
      }
    ]
  },
  {
    "id": "request-diagnostics-repeats",
    "title": "Квитанция, ревизия и event_id",
    "description": "При повторе геоотметки сервер сначала ищет квитанцию по студенту, паре и ключу. Совпавший запрос получает сохранённый итог до повторной проверки окна. Другие данные с тем же ключом дают конфликт. Для ручной отметки сервер под блокировкой сравнивает статус и ревизию с текущими. При HTTP 409 клиент перечитывает журнал и просит подтвердить действие заново. Для повторной доставки сообщения потребитель проверяет собственную пару consumerId и event_id.",
    "frames": [
      {
        "title": "Квитанция читается до динамического допуска",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java",
        "language": "java",
        "lines": [
          [
            189,
            "    private Ack checkinAttempt(Identity identity, Lesson lesson, String idempotencyKey, Geo geo) {"
          ],
          [
            190,
            "        Ack replay = replay(identity, lesson.id(), idempotencyKey, geo);"
          ],
          [
            191,
            "        if (replay != null) return replay;"
          ],
          [
            192,
            "        validateDynamicEligibility(identity, lesson);"
          ],
          [
            193,
            "        Instant eligibilityAt = clock.instant();"
          ],
          [
            194,
            "        validateWindow(lesson, eligibilityAt, clock);"
          ],
          [
            195,
            "        Instant acceptedAt = eligibilityAt.truncatedTo(ChronoUnit.MILLIS);"
          ],
          [
            275,
            "    public Ack replay(Identity identity, long lessonId, String idempotencyKey, Geo geo) {"
          ],
          [
            276,
            "        validateIdentityAndPayload(identity, lessonId, idempotencyKey, geo);"
          ],
          [
            277,
            "        String payloadHash = sha256(lessonId + \"\\n\" + geo.canonicalValue());"
          ],
          [
            278,
            "        return receiptRepository"
          ],
          [
            279,
            "                .findByStudentIdAndLessonIdAndIdempotencyKey("
          ],
          [
            280,
            "                        identity.userId(), lessonId, idempotencyKey)"
          ],
          [
            281,
            "                .map("
          ],
          [
            282,
            "                        receipt -> {"
          ],
          [
            283,
            "                            if (!receipt.getPayloadHash().equals(payloadHash)) {"
          ],
          [
            284,
            "                                throw new StudentCheckinException("
          ],
          [
            285,
            "                                        Code.IDEMPOTENCY_PAYLOAD_MISMATCH,"
          ],
          [
            286,
            "                                        \"Idempotency-Key уже использован с другим запросом\");"
          ],
          [
            287,
            "                            }"
          ],
          [
            288,
            "                            return fromReceipt(receipt);"
          ],
          [
            289,
            "                        })"
          ],
          [
            290,
            "                .orElse(null);"
          ]
        ]
      },
      {
        "title": "Ревизия сравнивается после блокировки пары и студента",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/marking/MarkingService.java",
        "language": "java",
        "lines": [
          [
            192,
            "        Instant now = clock.instant();"
          ],
          [
            193,
            "        guardedPairLock(lesson, userId, \"ATTENDANCE_SET\", now);"
          ],
          [
            194,
            "        Query filter = pairFilter(lessonId, userId);"
          ],
          [
            195,
            "        AttendanceDocument existing = mongoTemplate.findOne(filter, AttendanceDocument.class);"
          ],
          [
            196,
            "        requirePersistedScope(existing, lesson);"
          ],
          [
            197,
            "        guardedExpectedMark("
          ],
          [
            198,
            "                lesson,"
          ],
          [
            199,
            "                userId,"
          ],
          [
            200,
            "                \"ATTENDANCE_SET\","
          ],
          [
            201,
            "                existing,"
          ],
          [
            202,
            "                request.expectedStatus(),"
          ],
          [
            203,
            "                request.expectedRevision());"
          ],
          [
            392,
            "    private static void requireExpectedMark("
          ],
          [
            393,
            "            AttendanceDocument current, String status, String revision) {"
          ],
          [
            394,
            "        if (status == null) return;"
          ],
          [
            395,
            "        if (\"EMPTY\".equals(status) && current == null) return;"
          ],
          [
            396,
            "        if (current == null"
          ],
          [
            397,
            "                || !status.equals(current.getStatus().name())"
          ],
          [
            398,
            "                || !revision.equals("
          ],
          [
            399,
            "                        ru.rutcampustrack.attendance.checkin.AttendanceRevisions.effective("
          ],
          [
            400,
            "                                current))) {"
          ],
          [
            401,
            "            throw new ConflictException("
          ],
          [
            402,
            "                    \"Отметка изменилась. Обнови журнал и подтверди действие заново\");"
          ],
          [
            403,
            "        }"
          ],
          [
            404,
            "    }"
          ]
        ]
      },
      {
        "title": "HTTP 409 обновляет данные перед новым подтверждением",
        "path": "frontends/mobile-core/src/features/headman-journal/headman-manual-marks.ts",
        "language": "typescript",
        "lines": [
          [
            399,
            "        if (cause instanceof HeadmanJournalApiError && cause.response.status === 409) {"
          ],
          [
            400,
            "          staleRows.value = new Set(staleRows.value).add(userId)"
          ],
          [
            401,
            "          const refreshed = await options.refresh()"
          ],
          [
            402,
            "          if (current())"
          ],
          [
            403,
            "            setIssue(userId, {"
          ],
          [
            404,
            "              kind: 'conflict',"
          ],
          [
            405,
            "              message: refreshed"
          ],
          [
            406,
            "                ? conflictReviewMessage"
          ],
          [
            407,
            "                : 'Отметка изменилась. Обнови состав и проверь текущий статус.',"
          ],
          [
            408,
            "            })"
          ],
          [
            409,
            "          return"
          ]
        ]
      },
      {
        "title": "Потребитель регистрирует собственный event_id",
        "path": "services/shared/shared-events/src/main/java/ru/rutcampustrack/shared/events/IdempotencyGuard.java",
        "language": "java",
        "lines": [
          [
            56,
            "    public boolean tryClaim(String consumerId, Map<String, Object> envelope) {"
          ],
          [
            57,
            "        UUID eventId = extractEventId(envelope);"
          ],
          [
            58,
            "        if (eventId == null) {"
          ],
          [
            59,
            "            log.error("
          ],
          [
            60,
            "                    \"Idempotency: rejecting event with missing or invalid event_id (fail-closed)\");"
          ],
          [
            61,
            "            throw new IllegalStateException("
          ],
          [
            62,
            "                    \"Event with missing or invalid event_id rejected (fail-closed)\");"
          ],
          [
            63,
            "        }"
          ],
          [
            64,
            "        boolean claimed = store.tryClaim(consumerId, eventId);"
          ],
          [
            65,
            "        if (!claimed) {"
          ],
          [
            66,
            "            log.info("
          ],
          [
            67,
            "                    \"Idempotency: duplicate event skipped (consumer={}, event_id={})\","
          ],
          [
            68,
            "                    consumerId,"
          ],
          [
            69,
            "                    eventId);"
          ],
          [
            70,
            "        }"
          ],
          [
            71,
            "        return claimed;"
          ]
        ]
      }
    ]
  },
  {
    "id": "request-diagnostics-time",
    "title": "Московская дата и UTC",
    "description": "Пару относят к учебной дате по Москве. Событие в 23:30Z 16 ноября означает 02:30 17 ноября по учебному времени. Окно геоотметки строится от местных времени начала и конца пары с запасом пять минут с каждой стороны, обе границы включены. Очистка записей outbox и дедупликации использует отдельные часы UTC и собственные сроки хранения.",
    "frames": [
      {
        "title": "Учебные часы Attendance работают по Москве",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/config/ClockConfig.java",
        "language": "java",
        "lines": [
          [
            26,
            ""
          ],
          [
            27,
            "    @Bean"
          ],
          [
            28,
            "    public Clock clock() {"
          ],
          [
            29,
            "        return Clock.system(ZoneId.of(\"Europe/Moscow\"));"
          ],
          [
            30,
            "    }"
          ],
          [
            31,
            ""
          ],
          [
            32,
            "    @Bean"
          ],
          [
            33,
            "    public EducationalTime educationalTime(Clock realClock) {"
          ],
          [
            34,
            "        return new EducationalTime(realClock);"
          ],
          [
            35,
            "    }"
          ]
        ]
      },
      {
        "title": "Тип учебного времени передаёт часы явно",
        "path": "services/shared/shared-web-api/src/main/java/ru/rutcampustrack/shared/web/time/EducationalTime.java",
        "language": "java",
        "lines": [
          [
            6,
            "/** Explicit educational date/window time; never an unqualified Clock injection candidate. */"
          ],
          [
            7,
            "public record EducationalTime(Clock clock) {"
          ],
          [
            8,
            "    public EducationalTime {"
          ],
          [
            9,
            "        Objects.requireNonNull(clock, \"clock\");"
          ],
          [
            10,
            "    }"
          ],
          [
            11,
            "}"
          ]
        ]
      },
      {
        "title": "Границы сравниваются как абсолютные моменты",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentCheckinService.java",
        "language": "java",
        "lines": [
          [
            70,
            "    static final Duration CHECKIN_BUFFER = Duration.ofMinutes(5);"
          ],
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
          ]
        ]
      },
      {
        "title": "Очистка использует UTC",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/event/OutboxConfig.java",
        "language": "java",
        "lines": [
          [
            69,
            "        public OutboxCleanupJob outboxCleanupJob("
          ],
          [
            70,
            "                OutboxStorage storage,"
          ],
          [
            71,
            "                @Value(\"${rutcampustrack.outbox.retention-days:7}\") int retentionDays) {"
          ],
          [
            72,
            "            return new OutboxCleanupJob(storage, Clock.systemUTC(), retentionDays);"
          ],
          [
            73,
            "        }"
          ],
          [
            74,
            ""
          ],
          [
            75,
            "        /** M13 G8 — daily cleanup для event_consumer_processed (retention 7d). */"
          ],
          [
            76,
            "        @Bean"
          ],
          [
            77,
            "        public IdempotencyCleanupJob idempotencyCleanupJob("
          ],
          [
            78,
            "                IdempotencyStore store,"
          ],
          [
            79,
            "                @Value(\"${rutcampustrack.idempotency.retention-days:7}\") int retentionDays) {"
          ],
          [
            80,
            "            return new IdempotencyCleanupJob(store, Clock.systemUTC(), retentionDays);"
          ]
        ]
      }
    ]
  },
  {
    "id": "request-diagnostics-states",
    "title": "Пусто, ноль, ошибка и запрет",
    "description": "«На сегодня пар нет» означает, что сервер вернул пустой список. Ноль процентов возможен при ненулевом числе учтённых пар, когда отметок нужного типа нет. При пустом знаменателе журнал оставляет оценку порога нейтральной. Ошибка загрузки выводится отдельно и предлагает повторить. Если запросить расписание чужого для студента семестра, сервер вернёт 403 OUT_OF_SCOPE.",
    "frames": [
      {
        "title": "Ошибка и пустой день — отдельные состояния",
        "path": "frontends/mobile-core/src/features/today/TodayScreen.vue",
        "language": "typescript",
        "lines": [
          [
            280,
            "        severity=\"error\""
          ],
          [
            281,
            "        title=\"Не удалось получить данные\""
          ],
          [
            282,
            "        :message=\"error\""
          ],
          [
            283,
            "        action-label=\"Повторить\""
          ],
          [
            284,
            "        :action-disabled=\"offline\""
          ],
          [
            285,
            "        @action=\"emit('retry')\""
          ],
          [
            286,
            "      />"
          ],
          [
            287,
            "      <section"
          ],
          [
            288,
            "        v-else-if=\"!hasLessons\""
          ],
          [
            289,
            "        class=\"today-state\""
          ],
          [
            290,
            "        role=\"status\""
          ],
          [
            291,
            "      >"
          ],
          [
            292,
            "        <MobileIcon name=\"calendar\" /><h2>"
          ],
          [
            293,
            "          {{ offline ? 'Нет сохранённого расписания' : 'На сегодня пар нет' }}"
          ],
          [
            294,
            "        </h2>"
          ],
          [
            295,
            "        <p>"
          ],
          [
            296,
            "          {{"
          ],
          [
            297,
            "            offline"
          ],
          [
            298,
            "              ? 'Подключись к интернету, чтобы загрузить сегодняшние пары.'"
          ],
          [
            299,
            "              : 'Когда появится следующая пара, ты увидишь её здесь.'"
          ],
          [
            300,
            "          }}"
          ]
        ]
      },
      {
        "title": "Порог считает нулевой процент при наличии знаменателя",
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
        "title": "Ограничение расписания активным семестром",
        "path": "services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/student/StudentQueryService.java",
        "language": "java",
        "lines": [
          [
            160,
            "    public ScheduleResponse schedule(long requestedSemesterId) {"
          ],
          [
            161,
            "        InternalJwtClaims claims = requireStudent(true);"
          ],
          [
            162,
            "        SemesterResponse active = academic.activeSemester();"
          ],
          [
            163,
            "        if (active.getId() != requestedSemesterId) {"
          ],
          [
            164,
            "            throw new MobileBffException("
          ],
          [
            165,
            "                    HttpStatus.FORBIDDEN,"
          ],
          [
            166,
            "                    ProblemCode.OUT_OF_SCOPE,"
          ],
          [
            167,
            "                    \"Семестр не входит в scope студента\");"
          ],
          [
            168,
            "        }"
          ],
          [
            169,
            "        LocalDate from = LocalDate.parse(active.getDateFrom());"
          ],
          [
            170,
            "        LocalDate to = LocalDate.parse(active.getDateTo());"
          ],
          [
            171,
            "        LessonsResponse lessons = schedule.lessons(claims.groupId(), active.getId(), from, to);"
          ],
          [
            172,
            "        List<LessonResponse> ordered = ordered(lessons.getLessonsList());"
          ]
        ]
      },
      {
        "title": "HTTP 403 · поля ошибки доступа к семестру",
        "language": "json",
        "example": [
          "{",
          "  \"status\": 403,",
          "  \"type\": \"urn:rct:problem:out-of-scope\",",
          "  \"detail\": \"Семестр не входит в scope студента\",",
          "  \"code\": \"OUT_OF_SCOPE\"",
          "}"
        ]
      }
    ]
  },
  {
    "id": "request-diagnostics-pending",
    "title": "Заявка и геоотметка",
    "description": "В журнале ожидание решения дополняет отметку. Например, «н» остаётся, рядом видно, что заявка ещё не решена. При частичном одобрении заявки на несколько пар староста исключает отдельные занятия и подтверждает оставшиеся. Повтор чтения координат идёт на устройстве до запроса отметки. Attendance отдельно решает, создать ли заявку или использовать существующую. При блокировке гео на паре клиент получает GEO_BLOCKED, а сервер отклоняет новую геоотметку.",
    "frames": [
      {
        "title": "Ожидание решения поверх статуса ячейки",
        "path": "frontends/mobile-core/src/features/teacher/TeacherJournalTable.vue",
        "language": "typescript",
        "lines": [
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
          ]
        ]
      },
      {
        "title": "Частичное решение проверяет текущий состав и исключения",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/studentrequest/ExcuseDecisionSelections.java",
        "language": "java",
        "lines": [
          [
            123,
            "    static List<Long> included("
          ],
          [
            124,
            "            ExcuseTicket ticket, Map<Long, LessonResponse> lessons, Intent intent) {"
          ],
          [
            125,
            "        String current = revision(ticket, lessons);"
          ],
          [
            126,
            "        if (current == null || !Objects.equals(current, intent.expectedRevision())) {"
          ],
          [
            127,
            "            throw new ConflictException(\"Состав или состояние пар изменились; обнови заявку\");"
          ],
          [
            128,
            "        }"
          ],
          [
            129,
            "        HashSet<String> currentOccurrences = new HashSet<>();"
          ],
          [
            130,
            "        lessons.values()"
          ],
          [
            131,
            "                .forEach(lesson -> currentOccurrences.add(Long.toString(lesson.getOccurrenceId())));"
          ],
          [
            132,
            "        if (!currentOccurrences.containsAll(intent.excludedOccurrenceIds()))"
          ],
          [
            133,
            "            throw new BadRequestException(\"Исключение не принадлежит заявке\");"
          ],
          [
            134,
            "        List<Long> included ="
          ],
          [
            135,
            "                ticket.getLessonIds().stream()"
          ],
          [
            136,
            "                        .distinct()"
          ],
          [
            137,
            "                        .sorted()"
          ],
          [
            138,
            "                        .filter("
          ],
          [
            139,
            "                                id ->"
          ],
          [
            140,
            "                                        !intent.excludedOccurrenceIds()"
          ],
          [
            141,
            "                                                .contains("
          ],
          [
            142,
            "                                                        Long.toString("
          ],
          [
            143,
            "                                                                lessons.get(id).getOccurrenceId())))"
          ],
          [
            144,
            "                        .toList();"
          ],
          [
            145,
            "        if (included.isEmpty()) throw new BadRequestException(\"Нужно оставить хотя бы одну пару\");"
          ],
          [
            146,
            "        return included;"
          ]
        ]
      },
      {
        "title": "Клиент уточняет координаты до обращения к серверу",
        "path": "frontends/mobile-core/src/device-access/geolocation.ts",
        "language": "typescript",
        "lines": [
          [
            173,
            "  const budget = Math.max(0, Math.min(GEO_BUDGET_MS, options.budgetMs ?? GEO_BUDGET_MS))"
          ],
          [
            174,
            "  let latest: GeoSample | null = null"
          ],
          [
            175,
            "  let count = 0"
          ],
          [
            176,
            "  let reason: GeoResult['reason'] = 'POSITION_UNAVAILABLE'"
          ],
          [
            177,
            "  try {"
          ],
          [
            178,
            "    for (let attempt = 0; attempt < GEO_MAX_ATTEMPTS; attempt++) {"
          ],
          [
            179,
            "      if (options.signal?.aborted) throw new DOMException('Geolocation cancelled', 'AbortError')"
          ],
          [
            180,
            "      const remaining = budget - elapsed()"
          ],
          [
            181,
            "      if (remaining <= 0) {"
          ],
          [
            182,
            "        reason = 'BUDGET_EXPIRED'"
          ],
          [
            183,
            "        break"
          ],
          [
            184,
            "      }"
          ],
          [
            185,
            "      try {"
          ],
          [
            186,
            "        options.progress?.(attempt === 0 ? 'determining' : 'refining')"
          ],
          [
            187,
            "      } catch {"
          ],
          [
            188,
            "        /* Presentation only. */"
          ],
          [
            189,
            "      }"
          ],
          [
            190,
            "      const reading = await readGeolocationOnce(source, {"
          ],
          [
            191,
            "        ...(options.signal ? { signal: options.signal } : {}),"
          ],
          [
            192,
            "        budgetMs: remaining,"
          ],
          [
            193,
            "        afterTimestamp: latest?.timestamp ?? null,"
          ],
          [
            194,
            "      })"
          ]
        ]
      },
      {
        "title": "Attendance сообщает причину блокировки",
        "path": "services/attendance-service/attendance-app/src/main/java/ru/rutcampustrack/attendance/student/StudentAttendanceSnapshotService.java",
        "language": "java",
        "lines": [
          [
            196,
            "        if (attendance != null"
          ],
          [
            197,
            "                && attendance.getStatus() == AttendanceStatus.ABSENT"
          ],
          [
            198,
            "                && attendance.getSource() == AttendanceSource.HEADMAN) {"
          ],
          [
            199,
            "            return disabled(EligibilityReason.HEADMAN_ABSENT_REQUIRES_APPEAL, null);"
          ],
          [
            200,
            "        }"
          ],
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
          ]
        ]
      }
    ]
  }
]
