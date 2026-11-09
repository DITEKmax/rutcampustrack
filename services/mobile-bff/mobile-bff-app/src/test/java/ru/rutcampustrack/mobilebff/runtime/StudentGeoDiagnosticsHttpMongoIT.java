package ru.rutcampustrack.mobilebff.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.client.MongoClients;

import io.grpc.*;
import io.grpc.netty.NettyServerBuilder;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.attendance.audit.*;
import ru.rutcampustrack.attendance.checkin.*;
import ru.rutcampustrack.attendance.contract.enums.*;
import ru.rutcampustrack.attendance.event.*;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.geofence.GeofenceService;
import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.attendance.latecheckin.*;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.*;
import ru.rutcampustrack.attendance.studentrequest.*;
import ru.rutcampustrack.mobilebff.error.MobileProblemHandler;
import ru.rutcampustrack.mobilebff.grpc.*;
import ru.rutcampustrack.mobilebff.security.*;
import ru.rutcampustrack.mobilebff.student.*;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.outbox.mongo.MongoOutboxStorage;
import ru.rutcampustrack.shared.security.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Signed HTTP JSON -> production BFF -> TCP gRPC -> real transaction/audit Mongo. Only the upstream
 * Academic/Schedule authority snapshots are substituted.
 */
class StudentGeoDiagnosticsHttpMongoIT {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-06T07:15:00Z"), ZoneId.of("Europe/Moscow"));
    private static final String KEY = "geo-http-key-00001";
    private static final String GEO =
            "\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.788328,\"longitude\":37.606637}";

    @Test
    void diagnosticsReplayAndConflictsPreserveTransactionalHistoryAcrossRealBoundaries()
            throws Exception {
        try (var container = new MongoDBContainer("mongo:7.0")) {
            String uri = System.getenv("RCT_GEO_MONGO_URI");
            if (uri == null || uri.isBlank()) {
                container.start();
                uri = container.getReplicaSetUrl();
            }
            URI target = URI.create(uri);
            if (!"mongodb".equals(target.getScheme())
                    || target.getUserInfo() != null
                    || !List.of("127.0.0.1", "localhost", "[::1]").contains(target.getHost()))
                throw new IllegalArgumentException("Geo verification requires loopback Mongo");
            try (var client = MongoClients.create(uri)) {
                run(
                        new MongoTemplate(
                                client,
                                "rct_geo_1109_http_"
                                        + UUID.randomUUID().toString().replace("-", "")));
            }
        }
    }

    private void run(MongoTemplate mongo) throws Exception {
        var repositories = new MongoRepositoryFactory(mongo);
        var marks = repositories.getRepository(AttendanceRepository.class);
        var receipts = repositories.getRepository(StudentCheckinReceiptRepository.class);
        var late = repositories.getRepository(LateCheckinRepository.class);
        var excuses = repositories.getRepository(ExcuseRepository.class);
        var pairs = new PairWriteCoordinator(mongo);
        var manager = new MongoTransactionManager(mongo.getMongoDatabaseFactory());
        var tx = new TransactionTemplate(manager);
        var academic = mock(AcademicGrpcClient.class);
        var schedule = mock(ScheduleGrpcClient.class);
        when(academic.getCampusGeofence())
                .thenReturn(
                        GeofenceResponse.newBuilder()
                                .setLat(55.788328)
                                .setLng(37.606637)
                                .setRadiusM(200)
                                .build());
        when(academic.getUserDisplayName(anyLong())).thenReturn("Тестовый студент");
        when(academic.getSubjectDetailsByIds(any()))
                .thenReturn(
                        Map.of(
                                20L,
                                new AcademicGrpcClient.SubjectDetails(
                                        "Тестовый предмет", "lecture")));
        when(academic.getGroupMembers(eq(10L), any(LocalDate.class), eq(30L)))
                .thenAnswer(
                        call ->
                                GroupMembersResponse.newBuilder()
                                        .setSemesterId(30)
                                        .setAsOfDate(call.<LocalDate>getArgument(1).toString())
                                        .addStudents(
                                                StudentInfo.newBuilder()
                                                        .setUserId(100)
                                                        .setDisplayName("Тестовый студент"))
                                        .build());
        when(academic.isHeadman(any(), any()))
                .thenReturn(HeadmanCheckResponse.newBuilder().setIsHeadman(true).build());
        when(schedule.getLessonById(anyLong())).thenAnswer(call -> lesson(call.getArgument(0)));
        var jsonBuilder =
                Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        new CheckinJsonConfiguration().strictCheckinDiagnostics().customize(jsonBuilder);
        ObjectMapper json = jsonBuilder.build();
        var outbox = new MongoOutboxStorage(mongo, "attendance_outbox");
        var actions = new LessonActionService(mongo, CLOCK, outbox, json);
        actions.setPairs(pairs);
        var attendanceEvents = new AttendanceEventPublisher(outbox, json);
        attendanceEvents.setActions(actions);
        var lateEvents = new LateCheckinEventPublisher(outbox, json);
        lateEvents.setRequestActions(new RequestActionRecorder(actions));
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var checkins =
                new StudentCheckinService(
                        marks,
                        repositories.getRepository(CheckinPairStateRepository.class),
                        receipts,
                        late,
                        excuses,
                        pairs,
                        new GeofenceService(academic),
                        attendanceEvents,
                        lateEvents,
                        new BusinessMetrics(meters),
                        tx,
                        CLOCK);
        checkins.setRequestActions(new RequestActionRecorder(actions));
        var actor = mock(RequestContext.class);
        checkins.setRejectedActions(
                new RejectedAttendanceActionService(
                        actions, schedule, academic, manager, CLOCK, actor));
        var upstream =
                new AttendanceStudentGrpcServiceImpl(
                        checkins,
                        mock(StudentAttendanceSnapshotService.class),
                        schedule,
                        academic,
                        mock(SemesterCacheService.class));
        var signer = new InternalJwtTestFactory();
        var keys = mock(PublicKeyProvider.class);
        when(keys.getPublicKey()).thenReturn(signer.publicKey());
        var properties = new InternalJwtProperties(null, 60, 0, false, null, null, null);
        var validator = new InternalJwtValidator(keys, properties);
        var dropNextResponse = new AtomicBoolean();
        ServerInterceptor dropAfterCommit =
                new ServerInterceptor() {
                    @Override
                    public <Q, R> ServerCall.Listener<Q> interceptCall(
                            ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
                        boolean drop = dropNextResponse.getAndSet(false);
                        return next.startCall(
                                new ForwardingServerCall.SimpleForwardingServerCall<Q, R>(call) {
                                    @Override
                                    public void sendMessage(R message) {
                                        if (!drop) super.sendMessage(message);
                                    }

                                    @Override
                                    public void close(Status status, Metadata trailers) {
                                        super.close(
                                                drop && status.isOk()
                                                        ? Status.UNAVAILABLE.withDescription(
                                                                "Synthetic response loss after"
                                                                        + " commit")
                                                        : status,
                                                trailers);
                                    }
                                },
                                headers);
                    }
                };
        var grpc =
                NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                        .addService(upstream)
                        .intercept(dropAfterCommit)
                        .intercept(new StudentGrpcIdentityInterceptor(validator))
                        .build()
                        .start();
        var channel =
                ManagedChannelBuilder.forAddress("127.0.0.1", grpc.getPort())
                        .usePlaintext()
                        .build();
        try {
            var context = new MobileRequestContext();
            var attendance = new MobileAttendanceClient(new MobileGrpcAuth(context, ""));
            ReflectionTestUtils.setField(
                    attendance, "stub", AttendanceStudentGrpcServiceGrpc.newBlockingStub(channel));
            var mvc =
                    MockMvcBuilders.standaloneSetup(
                                    new StudentApiController(
                                            mock(StudentQueryService.class),
                                            new StudentCheckinFacade(attendance, context),
                                            mock(StudentRequestFacade.class),
                                            json))
                            .setControllerAdvice(new MobileProblemHandler(CLOCK))
                            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                            .addFilters(
                                    new MobileIdentityFilter(
                                            validator, properties, context, json, CLOCK))
                            .build();
            String token =
                    signer.validToken(
                            100L,
                            UUID.randomUUID(),
                            1L,
                            1L,
                            "STUDENT",
                            "ACTIVE",
                            10L,
                            false,
                            false);
            String browser =
                    "{"
                            + GEO
                            + ",\"diagnostics\":{\"source\":\"BROWSER\",\"accuracyMeters\":20,"
                            + "\"fixTimestamp\":\"2026-09-06T07:14:59Z\",\"receivedAt\":\"2026-09-06T07:15:00Z\","
                            + "\"sampleCount\":2,\"acquisitionDurationMs\":800,\"completionReason\":\"QUALITY_REACHED\"}}";
            // The mutation commits; its response is lost. Retrying the exact command replays one
            // effect.
            dropNextResponse.set(true);
            send(mvc, token, 1, KEY, browser, 503);
            String ack = send(mvc, token, 1, KEY, browser, 200);
            assertThat(ack).contains("\"outcome\":\"PRESENT\"");
            assertThat(marks.count()).isEqualTo(1);
            assertThat(receipts.count()).isEqualTo(1);
            assertThat(
                            mongo.getCollection("lesson_actions")
                                    .countDocuments(new Document("payload.action", "GEO_ATTEMPT")))
                    .isEqualTo(1);
            send(
                    mvc,
                    token,
                    1,
                    KEY,
                    browser.replace("\"accuracyMeters\":20", "\"accuracyMeters\":21"),
                    409);
            when(schedule.getLessonById(1L))
                    .thenReturn(lesson(1).toBuilder().setStatus("cancelled").build());
            assertThat(send(mvc, token, 1, KEY, browser, 200)).isEqualTo(ack);

            when(actor.getUserId()).thenReturn(200L);
            when(actor.getGroupId()).thenReturn(10L);
            when(actor.isHeadman()).thenReturn(true);
            var reads = new LessonActionReadService(mongo, schedule, academic, actor);
            var audit =
                    reads.read(1L, null, 100).items().stream()
                            .filter(
                                    row ->
                                            row.action().equals("GEO_ATTEMPT")
                                                    && row.outcome().equals("APPLIED"))
                            .findFirst()
                            .orElseThrow()
                            .geo();
            assertThat(audit.accuracyMeters()).isEqualTo(20);
            assertThat(audit.source()).isEqualTo("BROWSER");
            assertThat(audit.fixTimestamp()).isEqualTo(Instant.parse("2026-09-06T07:14:59Z"));
            assertThat(audit.receivedAt()).isEqualTo(CLOCK.instant());
            assertThat(audit.sampleCount()).isEqualTo(2);
            assertThat(audit.acquisitionDurationMs()).isEqualTo(800);
            assertThat(audit.completionReason()).isEqualTo("QUALITY_REACHED");
            assertThat(audit.latitude())
                    .isNull(); // Successful fixes retain the existing redaction.

            String tma =
                    "{\"geo\":{\"kind\":\"UNAVAILABLE\",\"reason\":\"TIMEOUT\"},"
                        + "\"diagnostics\":{\"source\":\"TMA\",\"accuracyMeters\":null,\"fixTimestamp\":null,"
                        + "\"receivedAt\":\"2026-09-06T07:15:00Z\",\"sampleCount\":0,\"completionReason\":\"TIMEOUT\"}}";
            send(mvc, token, 2, KEY, tma, 200);
            var unavailable =
                    reads.read(2L, null, 100).items().stream()
                            .filter(row -> row.action().equals("GEO_ATTEMPT"))
                            .findFirst()
                            .orElseThrow()
                            .geo();
            assertThat(unavailable.source()).isEqualTo("TMA");
            assertThat(unavailable.unavailableReason()).isEqualTo("GEO_UNAVAILABLE_REASON_TIMEOUT");
            assertThat(unavailable.fixTimestamp()).isNull();
            assertThat(unavailable.accuracyMeters()).isNull();
            assertThat(unavailable.verdict()).isEqualTo("UNAVAILABLE");

            // Legacy bytes and an existing persisted legacy receipt still replay without schedule
            // eligibility.
            var legacy = new StudentCheckinModels.Coordinates(55.788328, 37.606637);
            assertThat(legacy.canonicalValue())
                    .isEqualTo(
                            "coordinates:"
                                    + Double.toHexString(55.788328)
                                    + ':'
                                    + Double.toHexString(37.606637));
            var legacyHash =
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    ("3\n" + legacy.canonicalValue())
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8)));
            receipts.save(
                    StudentCheckinReceiptDocument.builder()
                            .studentId(100L)
                            .lessonId(3L)
                            .idempotencyKey(KEY)
                            .payloadHash(legacyHash)
                            .outcome("PRESENT")
                            .attendanceStatus("PRESENT")
                            .attendanceSource("STUDENT_GEO")
                            .markedAt(CLOCK.instant())
                            .serverNow(CLOCK.instant())
                            .createdAt(CLOCK.instant())
                            .build());
            send(mvc, token, 3, KEY, "{" + GEO + "}", 200);
            send(mvc, token, 3, KEY, browser, 409);
            send(mvc, token, 4, KEY, "{" + GEO + "}", 409); // Previous-day window.
            String foreign =
                    signer.validToken(
                            100L,
                            UUID.randomUUID(),
                            1L,
                            1L,
                            "STUDENT",
                            "ACTIVE",
                            99L,
                            false,
                            false);
            send(mvc, foreign, 5, KEY, browser, 403);
            for (String invalid :
                    List.of(
                            "\"accuracyMeters\":-1",
                            "\"accuracyMeters\":10000001",
                            "\"sampleCount\":65",
                            "\"sampleCount\":1.5",
                            "\"sampleCount\":null",
                            "\"acquisitionDurationMs\":\"10\"",
                            "\"accuracyMeters\":\"20\"",
                            "\"fixTimestamp\":123",
                            "\"receivedAt\":123",
                            "\"source\":0",
                            "\"completionReason\":0",
                            "\"acquisitionDurationMs\":60001",
                            "\"fixTimestamp\":\"not-instant\"",
                            "\"source\":\"UNKNOWN\"")) {
                send(
                        mvc,
                        token,
                        5,
                        KEY,
                        "{" + GEO + ",\"diagnostics\":{\"source\":\"BROWSER\"," + invalid + "}}",
                        400);
            }
            send(mvc, token, 5, KEY, "{" + GEO + ",\"diagnostics\":{}}", 400);
            Metadata metadata = new Metadata();
            metadata.put(
                    Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token);
            var direct =
                    AttendanceStudentGrpcServiceGrpc.newBlockingStub(channel)
                            .withInterceptors(
                                    io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(
                                            metadata));
            var validDiagnostic =
                    GeoDiagnostics.newBuilder()
                            .setSource(GeoDiagnosticSource.GEO_DIAGNOSTIC_SOURCE_BROWSER);
            for (GeoDiagnostics invalid :
                    List.of(
                            GeoDiagnostics.getDefaultInstance(),
                            validDiagnostic.clone().setAccuracyMeters(Double.NaN).build(),
                            validDiagnostic
                                    .clone()
                                    .setAccuracyMeters(Double.POSITIVE_INFINITY)
                                    .build(),
                            validDiagnostic.clone().setSampleCount(-1).build(),
                            validDiagnostic.clone().setAcquisitionDurationMs(60001).build(),
                            validDiagnostic.clone().setFixTimestamp("invalid").build(),
                            validDiagnostic
                                    .clone()
                                    .setCompletionReason(
                                            GeoCompletionReason.GEO_COMPLETION_REASON_UNSPECIFIED)
                                    .build())) {
                assertThatThrownBy(
                                () ->
                                        direct.checkin(
                                                StudentCheckinCommand.newBuilder()
                                                        .setLessonId(5)
                                                        .setIdempotencyKey(KEY)
                                                        .setCoordinates(
                                                                Coordinates.newBuilder()
                                                                        .setLatitude(55.788328)
                                                                        .setLongitude(37.606637))
                                                        .setDiagnostics(invalid)
                                                        .build()))
                        .isInstanceOfSatisfying(
                                StatusRuntimeException.class,
                                failure ->
                                        assertThat(failure.getStatus().getCode())
                                                .isEqualTo(Status.Code.INVALID_ARGUMENT));
            }

            long beforeMarks = marks.count(),
                    beforeRequests = late.count(),
                    beforeReceipts = receipts.count();
            mongo.save(
                    SemesterArchiveFenceDocument.builder()
                            .id("30")
                            .semesterId(30L)
                            .stateVersion(1L)
                            .operationId("11111111-1111-4111-8111-111111111111")
                            .barrierState("ARCHIVE_PREPARING")
                            .writeFence(0L)
                            .updatedAt(CLOCK.instant())
                            .build());
            assertThat(send(mvc, token, 5, KEY, browser, 409))
                    .contains("\"code\":\"CHECKIN_NOT_ELIGIBLE\"");
            assertThat(marks.count()).isEqualTo(beforeMarks);
            assertThat(late.count()).isEqualTo(beforeRequests);
            assertThat(receipts.count()).isEqualTo(beforeReceipts);
            assertThat(
                            meters.get("attendance.checkin")
                                    .tag("status", "geo_accepted")
                                    .counter()
                                    .count())
                    .isEqualTo(1);
            assertThat(
                            meters.get("attendance.checkin")
                                    .tag("status", "geo_unavailable")
                                    .counter()
                                    .count())
                    .isEqualTo(1);
            var conflict =
                    reads.read(5L, null, 100).items().stream()
                            .filter(row -> "ATTENDANCE_WRITE_CONFLICT".equals(row.errorCode()))
                            .findFirst()
                            .orElseThrow();
            assertThat(conflict.geo().verdict()).isNull();
            assertThat(conflict.geo().accuracyMeters()).isEqualTo(20);

            // B05: yesterday's unresolved request remains OPEN and counted; foreign group excluded.
            late.save(
                    LateCheckinRequest.builder()
                            .id("old-pending")
                            .studentId(100L)
                            .groupId(10L)
                            .semesterId(30L)
                            .lessonId(44L)
                            .subjectId(20L)
                            .subjectName("Тестовый предмет")
                            .subjectType("lecture")
                            .lessonNumber(1)
                            .lessonDate(LocalDate.of(2026, 9, 5))
                            .startsAt(LocalTime.of(10, 0))
                            .endsAt(LocalTime.of(11, 0))
                            .createdAt(CLOCK.instant().minusSeconds(86400))
                            .updatedAt(CLOCK.instant())
                            .studentName("Тестовый студент")
                            .build());
            late.save(
                    LateCheckinRequest.builder()
                            .id("foreign-pending")
                            .studentId(999L)
                            .groupId(99L)
                            .semesterId(30L)
                            .lessonId(45L)
                            .lessonDate(LocalDate.of(2026, 9, 5))
                            .createdAt(CLOCK.instant())
                            .build());
            var requestAuthority = mock(StudentRequestService.class);
            var headman =
                    new HeadmanRequestService(
                            excuses,
                            late,
                            marks,
                            repositories.getRepository(RequestAttachmentRepository.class),
                            requestAuthority,
                            schedule,
                            mongo,
                            CLOCK);
            var page =
                    headman.list(
                            new StudentRequestModels.Identity(200L, UserRole.STUDENT, 10L, true),
                            "OPEN",
                            0,
                            20,
                            "LATE_CHECKIN",
                            null,
                            null,
                            null);
            assertThat(page.totalElements()).isEqualTo(2);
            assertThat(page.content())
                    .extracting(row -> row.id())
                    .contains("old-pending")
                    .doesNotContain("foreign-pending");
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            grpc.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static LessonResponse lesson(long id) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(10)
                .setSubjectId(20)
                .setSemesterId(30)
                .setLessonNumber(2)
                .setDate(id == 4 ? "2026-09-05" : "2026-09-06")
                .setStartTime("10:00")
                .setEndTime("11:00")
                .setStatus("active")
                .build();
    }

    private static String send(
            MockMvc mvc, String token, long lesson, String key, String body, int expected)
            throws Exception {
        return mvc.perform(
                        post("/api/v1/student/lessons/" + lesson + "/checkin")
                                .header("X-Internal-Token", token)
                                .header("Idempotency-Key", key)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().is(expected))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
