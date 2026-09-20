package ru.rutcampustrack.mobilebff.runtime;

import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.attendance.checkin.AttendanceRepository;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.geofence.GeofenceService;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.AttendanceStudentGrpcServiceGrpc;
import ru.rutcampustrack.attendance.grpc.AttendanceStudentGrpcServiceImpl;
import ru.rutcampustrack.attendance.grpc.Coordinates;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.grpc.StudentCheckinCommand;
import ru.rutcampustrack.attendance.grpc.StudentGrpcIdentityInterceptor;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinEventPublisher;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.student.CheckinPairStateRepository;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.attendance.student.StudentAttendanceSnapshotService;
import ru.rutcampustrack.attendance.student.StudentCheckinModels;
import ru.rutcampustrack.attendance.student.StudentCheckinReceiptDocument;
import ru.rutcampustrack.attendance.student.StudentCheckinReceiptRepository;
import ru.rutcampustrack.attendance.student.StudentCheckinService;
import ru.rutcampustrack.mobilebff.MobileBffApplication;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = MobileBffApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class StudentHttpGrpcAuthIT {

    private static final long STUDENT_ID = 100L;
    private static final long GROUP_ID = 10L;
    private static final long LESSON_ID = 77L;
    private static final String KEY = "runtime-key-00001";
    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final UUID SESSION_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final InternalJwtTestFactory JWT = new InternalJwtTestFactory();
    private static final StudentCheckinReceiptRepository RECEIPTS = mock(StudentCheckinReceiptRepository.class);
    private static final AtomicInteger GRPC_CALLS = new AtomicInteger();
    private static Server grpcServer;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        startGrpcServer();
        registry.add("grpc.client.attendance-service.address",
                () -> "static://127.0.0.1:" + grpcServer.getPort());
        registry.add("grpc.client.attendance-service.negotiation-type", () -> "plaintext");
        registry.add("grpc.server.port", () -> 0);
        registry.add("rutcampustrack.security.internal-jwt.clock-skew-seconds", () -> "0");
    }

    @LocalServerPort
    private int httpPort;

    @Autowired
    private TestRestTemplate http;

    @MockitoBean
    private PublicKeyProvider bffPublicKeyProvider;

    @BeforeEach
    void setUp() {
        GRPC_CALLS.set(0);
        clearInvocations(RECEIPTS);
        when(bffPublicKeyProvider.getPublicKey()).thenReturn(JWT.publicKey());
    }

    @AfterAll
    static void stopGrpcServer() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
            grpcServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void signedStudentJwtTraversesRealHttpAndGrpcAndSpoofedHeadersAreIgnored() {
        HttpHeaders headers = headers(validToken(STUDENT_ID, "STUDENT", "ACTIVE", GROUP_ID, false));
        headers.set("X-User-Id", "999999");
        headers.set("X-User-Role", "ADMIN");
        var response = http.exchange(url(), HttpMethod.POST, request(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"outcome\":\"PRESENT\"")
                .contains("\"lessonId\":\"77\"")
                .contains("\"source\":\"STUDENT_GEO\"");
        assertThat(GRPC_CALLS).hasValue(1);
        verify(RECEIPTS).findByStudentIdAndLessonIdAndIdempotencyKey(STUDENT_ID, LESSON_ID, KEY);
    }

    @ParameterizedTest(name = "BFF rejects {0} JWT before gRPC")
    @MethodSource("invalidTokens")
    void invalidSignedJwtIsRejectedAtHttpBoundary(String caseName, String token) {
        var response = http.exchange(url(), HttpMethod.POST, request(headers(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("\"code\":\"INVALID_SESSION\"")
                .doesNotContain("signature", "issuer", "audience", "expired");
        assertThat(GRPC_CALLS).hasValue(0);
    }

    @ParameterizedTest(name = "attendance gRPC rejects {0} JWT")
    @MethodSource("invalidTokens")
    void invalidSignedJwtIsRejectedAgainAtGrpcBoundary(String caseName, String token) {
        var channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcServer.getPort())
                .usePlaintext().build();
        try {
            Metadata metadata = new Metadata();
            metadata.put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token);
            var stub = AttendanceStudentGrpcServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

            assertThatThrownBy(() -> stub.checkin(command()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            error -> assertThat(error.getStatus().getCode())
                                    .isEqualTo(Status.Code.UNAUTHENTICATED));
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void legacyIdentityHeadersCannotReplaceTheSignedToken() {
        HttpHeaders headers = headers(null);
        headers.set("X-User-Id", Long.toString(STUDENT_ID));
        headers.set("X-User-Role", "STUDENT");
        headers.set("X-User-Group-Id", Long.toString(GROUP_ID));

        var response = http.exchange(url(), HttpMethod.POST, request(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(GRPC_CALLS).hasValue(0);
    }

    @Test
    void readOnlyStudentJwtIsRejectedBeforeCheckinGrpc() {
        HttpHeaders headers = headers(validToken(STUDENT_ID, "STUDENT", "EXPELLED", GROUP_ID, true));
        var response = http.exchange(url(), HttpMethod.POST, request(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).contains("\"code\":\"ROLE_READ_ONLY\"");
        assertThat(GRPC_CALLS).hasValue(0);
        verify(RECEIPTS, never()).findByStudentIdAndLessonIdAndIdempotencyKey(
                STUDENT_ID, LESSON_ID, KEY);
    }

    @Test
    void terminalWrongRoleReadOnlyJwtUsesCheckinFacadeBoundary() {
        HttpHeaders headers = headers(validToken(STUDENT_ID, "ADMIN", "EXPELLED", GROUP_ID, true));
        var response = http.exchange(url(), HttpMethod.POST, request(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .contains("\"status\":403")
                .contains("\"code\":\"WRONG_ROLE\"")
                .contains("\"type\":\"urn:rct:problem:wrong-role\"");
        assertThat(GRPC_CALLS).hasValue(0);
        verifyNoInteractions(RECEIPTS);
    }

    @Test
    void activeStudentCanCheckinThroughMatrixDecoratedRoute() {
        HttpHeaders headers = headers(validToken(STUDENT_ID, "STUDENT", "ACTIVE", GROUP_ID, false));
        String path = "/api/v1/student;v=1/lessons/77;v=2/checkin;v=3";
        var response = http.exchange(
                "http://127.0.0.1:" + httpPort + path, HttpMethod.POST,
                request(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"outcome\":\"PRESENT\"")
                .contains("\"lessonId\":\"77\"")
                .contains("\"source\":\"STUDENT_GEO\"");
        assertThat(GRPC_CALLS).hasValue(1);
        verify(RECEIPTS).findByStudentIdAndLessonIdAndIdempotencyKey(STUDENT_ID, LESSON_ID, KEY);
    }

    @ParameterizedTest(name = "read-only check-in mutation wins over {0}")
    @MethodSource("readOnlyCheckinMutationInputs")
    void readOnlyCheckinMutationIsRejectedBeforeMvcValidation(
            String caseName, String path, String idempotencyKey, String body) {
        HttpHeaders headers = headers(validToken(STUDENT_ID, "STUDENT", "EXPELLED", GROUP_ID, true));
        if (idempotencyKey == null) {
            headers.remove("Idempotency-Key");
        } else {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        var response = http.exchange(
                "http://127.0.0.1:" + httpPort + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .contains("\"status\":403")
                .contains("\"code\":\"ROLE_READ_ONLY\"")
                .contains("\"type\":\"urn:rct:problem:role-read-only\"")
                .contains("\"instance\":\"" + path + "\"");
        assertThat(GRPC_CALLS).hasValue(0);
        verifyNoInteractions(RECEIPTS);
    }

    private static Stream<Arguments> readOnlyCheckinMutationInputs() {
        return Stream.of(
                Arguments.of("malformed path", "/api/v1/student/lessons/not-a-number/checkin", KEY,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"),
                Arguments.of("overflow path", "/api/v1/student/lessons/9223372036854775808/checkin", KEY,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"),
                Arguments.of("missing idempotency header", "/api/v1/student/lessons/77/checkin", null,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"),
                Arguments.of("invalid idempotency header", "/api/v1/student/lessons/77/checkin", "short-key",
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"),
                Arguments.of("malformed body", "/api/v1/student/lessons/77/checkin", KEY, "{\"geo\":"),
                Arguments.of("bean-invalid body", "/api/v1/student/lessons/77/checkin", KEY,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":null,\"longitude\":37.61}}"),
                Arguments.of("matrix on student prefix", "/api/v1/student;v=1/lessons/77/checkin", KEY,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"),
                Arguments.of("matrix on lesson variable", "/api/v1/student/lessons/77;v=2/checkin", KEY,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"),
                Arguments.of("matrix on check-in suffix", "/api/v1/student/lessons/77/checkin;v=3", KEY,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"),
                Arguments.of("matrix prefix with malformed body", "/api/v1/student;v=1/lessons/77/checkin", KEY,
                        "{\"geo\":"),
                Arguments.of("matrix suffix with bean-invalid body", "/api/v1/student/lessons/77/checkin;v=3", KEY,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":null,\"longitude\":37.61}}"),
                Arguments.of("matrix variable with missing idempotency header", "/api/v1/student/lessons/77;v=2/checkin", null,
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"),
                Arguments.of("matrix suffix with invalid idempotency header", "/api/v1/student/lessons/77/checkin;v=3", "short-key",
                        "{\"geo\":{\"kind\":\"COORDINATES\",\"latitude\":55.75,\"longitude\":37.61}}"));
    }

    @ParameterizedTest(name = "BFF rejects signed token with {0}")
    @MethodSource("invalidStudentScopes")
    void roleAndGroupScopeAreRejectedAtBffBoundary(String caseName, String token, String code) {
        var response = http.exchange(url(), HttpMethod.POST, request(headers(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"" + code + "\"");
        assertThat(GRPC_CALLS).hasValue(0);
    }

    private static Stream<Arguments> invalidTokens() {
        return Stream.of(
                Arguments.of("invalid signature", invalidSignatureToken()),
                Arguments.of("expired", expiredToken()),
                Arguments.of("wrong issuer", wrongIssuerToken()),
                Arguments.of("wrong audience", wrongAudienceToken())
        );
    }

    private static Stream<Arguments> invalidStudentScopes() {
        return Stream.of(
                Arguments.of("wrong role", validToken(STUDENT_ID, "ADMIN", "ACTIVE", GROUP_ID, false), "WRONG_ROLE"),
                Arguments.of("missing group", validToken(STUDENT_ID, "STUDENT", "ACTIVE", null, false), "OUT_OF_SCOPE")
        );
    }

    private static String validToken(long userId, String role, String status,
                                     Long groupId, boolean readOnly) {
        return JWT.validToken(userId, SESSION_ID, 1L, 1L, role, status, groupId,
                "HEADMAN".equals(role), readOnly);
    }

    private static String invalidSignatureToken() {
        InternalJwtTestFactory wrongSigner = new InternalJwtTestFactory();
        return JWT.buildToken(STUDENT_ID, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", GROUP_ID,
                false, false, now().minusSeconds(1), now().plusSeconds(60),
                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE, "internal",
                wrongSigner.keyPair());
    }

    private static String expiredToken() {
        Instant now = now();
        return JWT.buildToken(STUDENT_ID, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", GROUP_ID,
                false, false, now.minusSeconds(600), now.minusSeconds(300),
                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE, "internal", JWT.keyPair());
    }

    private static String wrongIssuerToken() {
        return JWT.buildToken(STUDENT_ID, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", GROUP_ID,
                false, false, now().minusSeconds(1), now().plusSeconds(60),
                "evil-issuer", InternalJwtTestFactory.AUDIENCE, "internal", JWT.keyPair());
    }

    private static String wrongAudienceToken() {
        return JWT.buildToken(STUDENT_ID, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", GROUP_ID,
                false, false, now().minusSeconds(1), now().plusSeconds(60),
                InternalJwtTestFactory.ISSUER, "other-audience", "internal", JWT.keyPair());
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String url() {
        return "http://127.0.0.1:" + httpPort + "/api/v1/student/lessons/" + LESSON_ID + "/checkin";
    }

    private static HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", KEY);
        if (token != null) headers.set(TOKEN_HEADER, token);
        return headers;
    }

    private static HttpEntity<String> request(HttpHeaders headers) {
        return new HttpEntity<>("""
                {"geo":{"kind":"COORDINATES","latitude":55.75,"longitude":37.61}}
                """, headers);
    }

    private static StudentCheckinCommand command() {
        return StudentCheckinCommand.newBuilder()
                .setLessonId(LESSON_ID)
                .setIdempotencyKey(KEY)
                .setCoordinates(Coordinates.newBuilder().setLatitude(55.75).setLongitude(37.61))
                .build();
    }

    private static synchronized void startGrpcServer() {
        if (grpcServer != null) return;
        try {
            PublicKeyProvider serverKeyProvider = mock(PublicKeyProvider.class);
            when(serverKeyProvider.getPublicKey()).thenReturn(JWT.publicKey());
            InternalJwtProperties properties = new InternalJwtProperties(
                    "http://unused", 60, 0, false,
                    InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE, TOKEN_HEADER);
            InternalJwtValidator validator = new InternalJwtValidator(serverKeyProvider, properties);

            StudentCheckinReceiptDocument receipt = StudentCheckinReceiptDocument.builder()
                    .id("receipt-1")
                    .studentId(STUDENT_ID)
                    .lessonId(LESSON_ID)
                    .idempotencyKey(KEY)
                    .payloadHash(payloadHash())
                    .outcome(StudentCheckinModels.Outcome.PRESENT.name())
                    .attendanceStatus("PRESENT")
                    .attendanceSource("STUDENT_GEO")
                    .markedAt(Instant.parse("2026-09-06T07:00:00Z"))
                    .serverNow(Instant.parse("2026-09-06T07:00:00Z"))
                    .createdAt(Instant.parse("2026-09-06T07:00:00Z"))
                    .build();
            when(RECEIPTS.findByStudentIdAndLessonIdAndIdempotencyKey(STUDENT_ID, LESSON_ID, KEY))
                    .thenReturn(Optional.of(receipt));

            StudentCheckinService checkins = new StudentCheckinService(
                    mock(AttendanceRepository.class),
                    mock(CheckinPairStateRepository.class),
                    RECEIPTS,
                    mock(LateCheckinRepository.class),
                    mock(PairWriteCoordinator.class),
                    mock(GeofenceService.class),
                    mock(AttendanceEventPublisher.class),
                    mock(LateCheckinEventPublisher.class),
                    mock(BusinessMetrics.class),
                    mock(org.springframework.transaction.support.TransactionTemplate.class),
                    Clock.fixed(Instant.parse("2026-09-06T07:00:00Z"), ZoneOffset.UTC));
            AttendanceStudentGrpcServiceImpl service = new AttendanceStudentGrpcServiceImpl(
                    checkins,
                    mock(StudentAttendanceSnapshotService.class),
                    mock(ScheduleGrpcClient.class),
                    mock(AcademicGrpcClient.class),
                    mock(SemesterCacheService.class));
            ServerInterceptor counter = new ServerInterceptor() {
                @Override
                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                        ServerCall<ReqT, RespT> call,
                        Metadata metadata,
                        ServerCallHandler<ReqT, RespT> next
                ) {
                    GRPC_CALLS.incrementAndGet();
                    return next.startCall(call, metadata);
                }
            };
            grpcServer = ServerBuilder.forPort(0)
                    .addService(ServerInterceptors.intercept(
                            service, counter, new StudentGrpcIdentityInterceptor(validator)))
                    .build()
                    .start();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static String payloadHash() throws Exception {
        String canonical = LESSON_ID + "\n"
                + new StudentCheckinModels.Coordinates(55.75, 37.61).canonicalValue();
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
