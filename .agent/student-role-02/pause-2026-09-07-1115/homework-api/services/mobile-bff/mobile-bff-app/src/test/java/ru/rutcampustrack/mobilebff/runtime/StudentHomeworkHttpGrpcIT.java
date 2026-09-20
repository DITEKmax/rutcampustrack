package ru.rutcampustrack.mobilebff.runtime;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.Empty;
import ru.rutcampustrack.academic.grpc.HomeworkInfo;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekRequest;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SetHomeworkCompletionRequest;
import ru.rutcampustrack.academic.grpc.SetHomeworkCompletionResponse;
import ru.rutcampustrack.mobilebff.MobileBffApplication;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runtime boundary proof for the new student homework HTTP operations.
 *
 * <p>The BFF runs on a random servlet port and talks to a task-owned local
 * Academic gRPC server.  The server validates the forwarded signed token and
 * records only claims and a digest, so the test proves identity forwarding
 * without exposing a token in logs or evidence.</p>
 */
@SpringBootTest(
        classes = MobileBffApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class StudentHomeworkHttpGrpcIT {

    private static final long STUDENT_ID = 701L;
    private static final long GROUP_ID = 41L;
    private static final long SEMESTER_ID = 9L;
    private static final long HOMEWORK_ID = 9001L;
    private static final long OLD_HOMEWORK_ID = 9002L;
    private static final long SUBJECT_ID = 501L;
    private static final Instant SERVER_NOW = Instant.parse("2026-03-05T10:00:00Z");
    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final InternalJwtTestFactory JWT = new InternalJwtTestFactory();
    private static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<InternalJwtClaims> CLAIMS =
            Context.key("student-homework-http-grpc-test-claims");
    private static final AtomicReference<HomeworksForWeekRequest> LAST_HOMEWORK_REQUEST = new AtomicReference<>();
    private static final AtomicReference<SetHomeworkCompletionRequest> LAST_COMPLETION_REQUEST =
            new AtomicReference<>();
    private static final AtomicReference<InternalJwtClaims> LAST_HOMEWORK_CLAIMS = new AtomicReference<>();
    private static final AtomicReference<InternalJwtClaims> LAST_COMPLETION_CLAIMS = new AtomicReference<>();
    private static final AtomicReference<String> LAST_TOKEN_DIGEST = new AtomicReference<>();
    private static final AtomicReference<Status> FORCED_HOMEWORK_STATUS = new AtomicReference<>();
    private static final AtomicReference<Status> FORCED_COMPLETION_STATUS = new AtomicReference<>();
    private static final AtomicReference<Status> FORCED_ACTIVE_SEMESTER_STATUS = new AtomicReference<>();
    private static final AtomicInteger RPC_CALLS = new AtomicInteger();
    private static Server grpcServer;

    @LocalServerPort
    private int httpPort;

    @Autowired
    private TestRestTemplate http;

    @MockitoBean
    private PublicKeyProvider bffPublicKeyProvider;

    @MockitoBean
    private Clock bffClock;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        startGrpcServer();
        registry.add("grpc.client.academic-service.address",
                () -> "static://127.0.0.1:" + grpcServer.getPort());
        registry.add("grpc.client.academic-service.negotiation-type", () -> "plaintext");
        registry.add("grpc.auth.secret", () -> "");
        registry.add("rutcampustrack.security.internal-jwt.clock-skew-seconds", () -> "0");
    }

    @BeforeEach
    void resetRuntimeSeam() {
        LAST_HOMEWORK_REQUEST.set(null);
        LAST_COMPLETION_REQUEST.set(null);
        LAST_HOMEWORK_CLAIMS.set(null);
        LAST_COMPLETION_CLAIMS.set(null);
        LAST_TOKEN_DIGEST.set(null);
        FORCED_HOMEWORK_STATUS.set(null);
        FORCED_COMPLETION_STATUS.set(null);
        FORCED_ACTIVE_SEMESTER_STATUS.set(null);
        RPC_CALLS.set(0);
        when(bffPublicKeyProvider.getPublicKey()).thenReturn(JWT.publicKey());
        when(bffClock.instant()).thenReturn(SERVER_NOW);
        when(bffClock.withZone(any(ZoneId.class)))
                .thenAnswer(invocation -> Clock.fixed(SERVER_NOW, invocation.getArgument(0)));
    }

    @AfterAll
    static void stopGrpcServer() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdownNow();
            grpcServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void signedStudentJwtTraversesGetAndPutAndSpoofedHeadersAreIgnored() {
        String token = JWT.validToken(STUDENT_ID, "STUDENT", GROUP_ID, false);

        ResponseEntity<String> feed = http.exchange(
                url("/api/v1/student/homework?from=2026-03-01&to=2026-03-31"),
                HttpMethod.GET,
                new HttpEntity<>(headers(token, true)),
                String.class);

        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(feed.getHeaders().getCacheControl()).contains("no-store");
        assertThat(feed.getBody()).contains("\"id\":\"9001\"")
                .contains("\"completed\":false")
                .contains("\"lessonDate\":\"2026-03-02\"")
                .contains("\"id\":\"9002\"")
                .contains("\"completedAt\":\"2026-03-05T08:00:00Z\"")
                .contains("\"serverNow\":\"2026-03-05T10:00:00Z\"");
        assertThat(LAST_HOMEWORK_REQUEST).hasValueSatisfying(request -> {
            assertThat(request.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(request.getSemesterId()).isEqualTo(SEMESTER_ID);
            assertThat(request.getStudentId()).isEqualTo(STUDENT_ID);
            assertThat(request.getDateFrom()).isEqualTo("2026-03-01");
            assertThat(request.getDateTo()).isEqualTo("2026-03-31");
            assertThat(request.getIncludeCompletedToday()).isTrue();
            assertThat(request.getCompletedTodayFrom()).isEqualTo("2026-03-04T21:00:00Z");
            assertThat(request.getCompletedTodayTo()).isEqualTo("2026-03-05T21:00:00Z");
        });
        assertThat(LAST_HOMEWORK_CLAIMS).hasValue(new InternalJwtClaims(STUDENT_ID, "STUDENT", GROUP_ID, false));
        assertThat(LAST_TOKEN_DIGEST).hasValue(tokenDigest(token));

        ResponseEntity<String> completion = http.exchange(
                url("/api/v1/student/homework/9001/completion"),
                HttpMethod.PUT,
                new HttpEntity<>("{\"completed\":true}", headers(token, true)),
                String.class);

        assertThat(completion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completion.getHeaders().getCacheControl()).contains("no-store");
        assertThat(completion.getBody()).contains("\"id\":\"9001\"")
                .contains("\"completed\":true");
        assertThat(LAST_COMPLETION_REQUEST).hasValueSatisfying(request -> {
            assertThat(request.getHomeworkId()).isEqualTo(HOMEWORK_ID);
            assertThat(request.getSemesterId()).isEqualTo(SEMESTER_ID);
            assertThat(request.getCompleted()).isTrue();
        });
        assertThat(LAST_COMPLETION_CLAIMS)
                .hasValue(new InternalJwtClaims(STUDENT_ID, "STUDENT", GROUP_ID, false));

        ResponseEntity<String> uncompleted = http.exchange(
                url("/api/v1/student/homework/9001/completion"),
                HttpMethod.PUT,
                new HttpEntity<>("{\"completed\":false}", headers(token, false)),
                String.class);
        assertThat(uncompleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uncompleted.getHeaders().getCacheControl()).contains("no-store");
        assertThat(uncompleted.getBody()).contains("\"id\":\"9001\"")
                .contains("\"completed\":false");
    }

    @Test
    void historicalRangeDoesNotRequestTodayCompletionUnion() {
        String token = JWT.validToken(STUDENT_ID, "STUDENT", GROUP_ID, false);

        ResponseEntity<String> feed = http.exchange(
                url("/api/v1/student/homework?from=2026-02-01&to=2026-02-28"),
                HttpMethod.GET,
                new HttpEntity<>(headers(token, false)),
                String.class);

        assertThat(feed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(feed.getBody()).doesNotContain("\"id\":\"9002\"");
        assertThat(LAST_HOMEWORK_REQUEST).hasValueSatisfying(request -> {
            assertThat(request.getIncludeCompletedToday()).isFalse();
            assertThat(request.getCompletedTodayFrom()).isEmpty();
            assertThat(request.getCompletedTodayTo()).isEmpty();
        });
    }

    @Test
    void missingOrTamperedTokenIsRejectedBeforeAcademicGrpc() {
        ResponseEntity<String> missing = http.exchange(
                url("/api/v1/student/homework"), HttpMethod.GET,
                new HttpEntity<>(headers(null, false)), String.class);
        assertProblem(missing, HttpStatus.UNAUTHORIZED, "INVALID_SESSION");
        assertThat(RPC_CALLS).hasValue(0);

        ResponseEntity<String> tampered = http.exchange(
                url("/api/v1/student/homework"), HttpMethod.GET,
                new HttpEntity<>(headers(JWT.validToken(STUDENT_ID, "STUDENT", GROUP_ID, false) + ".tampered", false)),
                String.class);
        assertProblem(tampered, HttpStatus.UNAUTHORIZED, "INVALID_SESSION");
        assertThat(RPC_CALLS).hasValue(0);
    }

    @Test
    void wrongRoleIsRejectedAndDoesNotReachAcademicGrpc() {
        String token = JWT.validToken(STUDENT_ID, "ADMIN", GROUP_ID, false);

        ResponseEntity<String> response = http.exchange(
                url("/api/v1/student/homework"), HttpMethod.GET,
                new HttpEntity<>(headers(token, false)), String.class);

        assertProblem(response, HttpStatus.FORBIDDEN, "WRONG_ROLE");
        assertThat(RPC_CALLS).hasValue(0);
    }

    @Test
    void invalidHomeworkInputsUseTypedProblemDetails() {
        String token = JWT.validToken(STUDENT_ID, "STUDENT", GROUP_ID, false);

        ResponseEntity<String> malformedId = http.exchange(
                url("/api/v1/student/homework/not-a-number/completion"), HttpMethod.PUT,
                new HttpEntity<>("{\"completed\":true}", headers(token, true)), String.class);
        assertProblem(malformedId, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");

        ResponseEntity<String> overflowingId = http.exchange(
                url("/api/v1/student/homework/9223372036854775808/completion"), HttpMethod.PUT,
                new HttpEntity<>("{\"completed\":true}", headers(token, true)), String.class);
        assertProblem(overflowingId, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");

        for (String body : new String[]{"{\"completed\":1}", "{\"completed\":\"true\"}",
                "{\"completed\":null}"}) {
            ResponseEntity<String> malformedBoolean = http.exchange(
                    url("/api/v1/student/homework/9001/completion"), HttpMethod.PUT,
                    new HttpEntity<>(body, headers(token, true)), String.class);
            assertProblem(malformedBoolean, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }

        ResponseEntity<String> missingCompleted = http.exchange(
                url("/api/v1/student/homework/9001/completion"), HttpMethod.PUT,
                new HttpEntity<>("{}", headers(token, true)), String.class);
        assertProblem(missingCompleted, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");

        ResponseEntity<String> malformedDate = http.exchange(
                url("/api/v1/student/homework?from=2026-3-1"), HttpMethod.GET,
                new HttpEntity<>(headers(token, false)), String.class);
        assertProblem(malformedDate, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertThat(LAST_COMPLETION_REQUEST).hasValue(null);
    }

    @Test
    void academicStatusMappingsRemainTypedAtTheHttpBoundary() {
        String token = JWT.validToken(STUDENT_ID, "STUDENT", GROUP_ID, false);

        FORCED_COMPLETION_STATUS.set(Status.NOT_FOUND);
        ResponseEntity<String> notFound = http.exchange(
                url("/api/v1/student/homework/9001/completion"), HttpMethod.PUT,
                new HttpEntity<>("{\"completed\":true}", headers(token, false)), String.class);
        assertProblem(notFound, HttpStatus.NOT_FOUND, "HOMEWORK_NOT_FOUND");

        FORCED_HOMEWORK_STATUS.set(Status.PERMISSION_DENIED);
        ResponseEntity<String> forbidden = http.exchange(
                url("/api/v1/student/homework?from=2026-03-01&to=2026-03-31"), HttpMethod.GET,
                new HttpEntity<>(headers(token, false)), String.class);
        assertProblem(forbidden, HttpStatus.FORBIDDEN, "OUT_OF_SCOPE");

        FORCED_COMPLETION_STATUS.set(Status.UNAVAILABLE);
        ResponseEntity<String> unavailable = http.exchange(
                url("/api/v1/student/homework/9001/completion"), HttpMethod.PUT,
                new HttpEntity<>("{\"completed\":true}", headers(token, false)), String.class);
        assertProblem(unavailable, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void missingActiveSemesterIsDependencyFailureBeforeHomeworkRpc() {
        String token = JWT.validToken(STUDENT_ID, "STUDENT", GROUP_ID, false);

        FORCED_ACTIVE_SEMESTER_STATUS.set(Status.NOT_FOUND);
        ResponseEntity<String> feed = http.exchange(
                url("/api/v1/student/homework?from=2026-03-01&to=2026-03-31"),
                HttpMethod.GET,
                new HttpEntity<>(headers(token, false)), String.class);
        assertProblem(feed, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
        assertThat(LAST_HOMEWORK_REQUEST).hasValue(null);

        FORCED_ACTIVE_SEMESTER_STATUS.set(Status.NOT_FOUND);
        ResponseEntity<String> completion = http.exchange(
                url("/api/v1/student/homework/9001/completion"),
                HttpMethod.PUT,
                new HttpEntity<>("{\"completed\":true}", headers(token, false)), String.class);
        assertProblem(completion, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
        assertThat(LAST_COMPLETION_REQUEST).hasValue(null);
    }

    private void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getBody()).contains("\"status\":" + status.value())
                .contains("\"code\":\"" + code + "\"");
    }

    private HttpHeaders headers(String token, boolean spoofIdentity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set(TOKEN_HEADER, token);
        }
        if (spoofIdentity) {
            headers.set("X-User-Id", "999999");
            headers.set("X-User-Role", "ADMIN");
            headers.set("X-User-Group-Id", "999999");
        }
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + httpPort + path;
    }

    private static String tokenDigest(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new AssertionError("Cannot hash test token", error);
        }
    }

    private static synchronized void startGrpcServer() {
        if (grpcServer != null) {
            return;
        }
        try {
            PublicKeyProvider serverKeyProvider = mock(PublicKeyProvider.class);
            when(serverKeyProvider.getPublicKey()).thenReturn(JWT.publicKey());
            InternalJwtProperties properties = new InternalJwtProperties(
                    "http://unused", 60, 0, false,
                    InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE, TOKEN_HEADER);
            InternalJwtValidator validator = new InternalJwtValidator(serverKeyProvider, properties);

            grpcServer = ServerBuilder.forPort(0)
                    .addService(new AcademicHomeworkFake())
                    .intercept(jwtInterceptor(validator))
                    .build()
                    .start();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static ServerInterceptor jwtInterceptor(InternalJwtValidator validator) {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                String token = headers.get(INTERNAL_TOKEN);
                try {
                    InternalJwtClaims claims = validator.validate(token);
                    LAST_TOKEN_DIGEST.set(tokenDigest(token));
                    Context context = Context.current().withValue(CLAIMS, claims);
                    return Contexts.interceptCall(context, call, headers, next);
                } catch (InternalJwtException error) {
                    call.close(Status.UNAUTHENTICATED.withDescription("Invalid internal identity"), new Metadata());
                    return new ServerCall.Listener<>() {
                    };
                }
            }
        };
    }

    private static final class AcademicHomeworkFake extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {
        @Override
        public void getActiveSemester(Empty request, StreamObserver<SemesterResponse> responseObserver) {
            RPC_CALLS.incrementAndGet();
            Status forced = FORCED_ACTIVE_SEMESTER_STATUS.get();
            if (forced != null) {
                responseObserver.onError(forced.asRuntimeException());
                return;
            }
            responseObserver.onNext(SemesterResponse.newBuilder()
                    .setId(SEMESTER_ID)
                    .setName("Spring 2026")
                    .setDateFrom("2026-02-01")
                    .setDateTo("2026-06-30")
                    .setFirstWeekType("odd")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getHomeworksForWeek(HomeworksForWeekRequest request,
                                        StreamObserver<HomeworksForWeekResponse> responseObserver) {
            RPC_CALLS.incrementAndGet();
            LAST_HOMEWORK_REQUEST.set(request);
            LAST_HOMEWORK_CLAIMS.set(CLAIMS.get());
            Status forced = FORCED_HOMEWORK_STATUS.get();
            if (forced != null) {
                responseObserver.onError(forced.asRuntimeException());
                return;
            }
            HomeworksForWeekResponse.Builder response = HomeworksForWeekResponse.newBuilder()
                    .addHomeworks(HomeworkInfo.newBuilder()
                            .setHomeworkId(HOMEWORK_ID)
                            .setSubjectId(SUBJECT_ID)
                            .setSubjectName("Алгоритмы")
                            .setTitle("Повторить графы")
                            .setDescription("Конкурентная проверка")
                            .setLessonDate("2026-03-02")
                            .setLessonNumber(1)
                            .setCompleted(false)
                            .build());
            if (request.getIncludeCompletedToday()) {
                response.addHomeworks(HomeworkInfo.newBuilder()
                        .setHomeworkId(OLD_HOMEWORK_ID)
                        .setSubjectId(SUBJECT_ID)
                        .setSubjectName("Алгоритмы")
                        .setTitle("Старое задание")
                        .setDescription("Завершено сегодня")
                        .setLessonDate("2026-02-20")
                        .setLessonNumber(2)
                        .setCompleted(true)
                        .setCompletedAt("2026-03-05T08:00:00Z")
                        .build());
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        }

        @Override
        public void setHomeworkCompletion(SetHomeworkCompletionRequest request,
                                           StreamObserver<SetHomeworkCompletionResponse> responseObserver) {
            RPC_CALLS.incrementAndGet();
            LAST_COMPLETION_REQUEST.set(request);
            LAST_COMPLETION_CLAIMS.set(CLAIMS.get());
            Status forced = FORCED_COMPLETION_STATUS.get();
            if (forced != null) {
                responseObserver.onError(forced.asRuntimeException());
                return;
            }
            SetHomeworkCompletionResponse.Builder response = SetHomeworkCompletionResponse.newBuilder()
                    .setHomeworkId(request.getHomeworkId())
                    .setCompleted(request.getCompleted());
            if (request.getCompleted()) {
                response.setCompletedAt("2026-03-05T08:00:00Z");
            }
            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        }
    }
}
