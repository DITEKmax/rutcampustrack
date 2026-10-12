package ru.rutcampustrack.mobilebff.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.mobilebff.MobileBffApplication;
import ru.rutcampustrack.schedule.grpc.*;
import ru.rutcampustrack.shared.security.*;

/** Real signed HTTP and forwarded JWT over task-owned gRPC producers; not domain DB acceptance. */
@SpringBootTest(
        classes = MobileBffApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentEnumProjectionHttpGrpcIT {
    private static final InternalJwtTestFactory JWT = new InternalJwtTestFactory();
    private static final Context.Key<InternalJwtClaims> CLAIMS =
            Context.key("enum-projection-claims");
    private static final AtomicReference<String> LESSON_STATUS = new AtomicReference<>("active");
    private static final AtomicInteger ELIGIBILITY = new AtomicInteger(1);
    private static final AtomicReference<InternalJwtClaims> FORWARDED = new AtomicReference<>();
    private static Server server;

    @Autowired TestRestTemplate http;
    @MockitoBean PublicKeyProvider publicKeyProvider;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        startServer();
        for (String service :
                java.util.List.of("academic-service", "schedule-service", "attendance-service")) {
            registry.add(
                    "grpc.client." + service + ".address",
                    () -> "static://127.0.0.1:" + server.getPort());
            registry.add("grpc.client." + service + ".negotiation-type", () -> "plaintext");
        }
        registry.add("grpc.server.port", () -> 0);
        registry.add("grpc.auth.secret", () -> "");
    }

    @BeforeEach
    void reset() {
        LESSON_STATUS.set("active");
        ELIGIBILITY.set(1);
        FORWARDED.set(null);
        when(publicKeyProvider.getPublicKey()).thenReturn(JWT.publicKey());
    }

    @AfterAll
    static void stop() throws InterruptedException {
        server.shutdownNow();
        server.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @ResourceLock("default-locale")
    void knownScheduleStatusRemainsCanonicalUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var response = today();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody())
                    .contains("\"status\":\"ACTIVE\"", "\"reason\":\"ELIGIBLE\"");
            assertForwardedIdentity();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"schedule-status", "eligibility-number"})
    void unknownUpstreamEnumIsTypedDependencyFailure(String boundary) {
        if (boundary.equals("schedule-status")) LESSON_STATUS.set("future_status");
        else ELIGIBILITY.set(8675309);
        var response = today();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).contains("\"code\":\"DEPENDENCY_UNAVAILABLE\"");
        assertThat(response.getBody()).doesNotContain("future_status", "8675309", "UNRECOGNIZED");
        assertForwardedIdentity();
    }

    private org.springframework.http.ResponseEntity<String> today() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(
                "X-Internal-Token",
                JWT.validToken(
                        701L,
                        UUID.fromString("77777777-7777-4777-8777-777777777777"),
                        1L,
                        1L,
                        "STUDENT",
                        "ACTIVE",
                        41L,
                        false,
                        false));
        headers.set("X-User-Role", "ADMIN");
        return http.exchange(
                "/api/v1/student/today", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void assertForwardedIdentity() {
        assertThat(FORWARDED.get()).isNotNull();
        assertThat(FORWARDED.get().userId()).isEqualTo(701L);
        assertThat(FORWARDED.get().groupId()).isEqualTo(41L);
        assertThat(FORWARDED.get().role()).isEqualTo("STUDENT");
    }

    private static synchronized void startServer() {
        if (server != null) return;
        try {
            PublicKeyProvider key = mock(PublicKeyProvider.class);
            when(key.getPublicKey()).thenReturn(JWT.publicKey());
            InternalJwtValidator validator =
                    new InternalJwtValidator(
                            key,
                            new InternalJwtProperties(
                                    "http://unused",
                                    60,
                                    0,
                                    false,
                                    InternalJwtTestFactory.ISSUER,
                                    InternalJwtTestFactory.AUDIENCE,
                                    "X-Internal-Token"));
            server =
                    ServerBuilder.forPort(0)
                            .addService(new AcademicProducer())
                            .addService(new ScheduleProducer())
                            .addService(new AttendanceProducer())
                            .intercept(
                                    new ServerInterceptor() {
                                        @Override
                                        public <Q, R> ServerCall.Listener<Q> interceptCall(
                                                ServerCall<Q, R> call,
                                                Metadata headers,
                                                ServerCallHandler<Q, R> next) {
                                            try {
                                                var claims =
                                                        validator.validate(
                                                                headers.get(
                                                                        Metadata.Key.of(
                                                                                "x-internal-token",
                                                                                Metadata
                                                                                        .ASCII_STRING_MARSHALLER)));
                                                FORWARDED.set(claims);
                                                return Contexts.interceptCall(
                                                        Context.current().withValue(CLAIMS, claims),
                                                        call,
                                                        headers,
                                                        next);
                                            } catch (InternalJwtException error) {
                                                call.close(Status.UNAUTHENTICATED, new Metadata());
                                                return new ServerCall.Listener<>() {};
                                            }
                                        }
                                    })
                            .build()
                            .start();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static final class AcademicProducer
            extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {
        @Override
        public void getActiveSemester(
                ru.rutcampustrack.academic.grpc.Empty request,
                StreamObserver<SemesterResponse> response) {
            response.onNext(
                    SemesterResponse.newBuilder()
                            .setId(9)
                            .setName("Spring 2026")
                            .setDateFrom("2026-02-01")
                            .setDateTo("2026-06-30")
                            .build());
            response.onCompleted();
        }

        @Override
        public void getSubjectsByIds(
                SubjectsByIdsRequest request, StreamObserver<SubjectsByIdsResponse> response) {
            response.onNext(
                    SubjectsByIdsResponse.newBuilder()
                            .addSubjects(
                                    SubjectInfo.newBuilder()
                                            .setSubjectId(501)
                                            .setSubjectName("Алгоритмы"))
                            .build());
            response.onCompleted();
        }
    }

    private static final class ScheduleProducer
            extends ScheduleGrpcServiceGrpc.ScheduleGrpcServiceImplBase {
        @Override
        public void getLessonsByGroup(
                LessonsByGroupRequest request, StreamObserver<LessonsResponse> response) {
            if (request.getGroupId() != CLAIMS.get().groupId()) {
                response.onError(Status.PERMISSION_DENIED.asRuntimeException());
                return;
            }
            response.onNext(
                    LessonsResponse.newBuilder()
                            .addLessons(
                                    LessonResponse.newBuilder()
                                            .setId(1001)
                                            .setGroupId(41)
                                            .setSubjectId(501)
                                            .setDate(request.getDateFrom())
                                            .setLessonNumber(1)
                                            .setStartTime("08:30")
                                            .setEndTime("09:50")
                                            .setStatus(LESSON_STATUS.get())
                                            .setLessonType("practice")
                                            .setRoomChangeState("UNCHANGED"))
                            .build());
            response.onCompleted();
        }
    }

    private static final class AttendanceProducer
            extends AttendanceStudentGrpcServiceGrpc.AttendanceStudentGrpcServiceImplBase {
        @Override
        public void getStudentAttendanceSnapshot(
                StudentAttendanceSnapshotRequest request,
                StreamObserver<StudentAttendanceSnapshotResponse> response) {
            response.onNext(
                    StudentAttendanceSnapshotResponse.newBuilder()
                            .setServerNow("2026-03-05T10:00:00Z")
                            .addEntries(
                                    StudentAttendanceEntry.newBuilder()
                                            .setLessonId(1001)
                                            .setEligibility(
                                                    StudentCheckinEligibility.newBuilder()
                                                            .setAllowed(true)
                                                            .setReasonValue(ELIGIBILITY.get())))
                            .build());
            response.onCompleted();
        }
    }
}
