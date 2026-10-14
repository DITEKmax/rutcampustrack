package ru.rutcampustrack.mobilebff.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private static final String REQUEST_ID = "777777777777777777777777";
    private static final String REQUEST_PATH = "/api/v1/student/requests";
    private static final String PLAN_PATH = "/api/v1/map/buildings/1/floors/2/plan";
    private static final AtomicReference<StudentRequestDetail> REQUEST = new AtomicReference<>();
    private static final AtomicReference<StudentRequestOptions> REQUEST_OPTIONS =
            new AtomicReference<>();
    private static final AtomicReference<CampusMapPlanResponse> PLAN = new AtomicReference<>();
    private static Server server;

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
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
        REQUEST.set(knownRequest());
        REQUEST_OPTIONS.set(knownRequestOptions());
        PLAN.set(knownPlan());
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

    @Test
    void knownRequestsPreserveSummaryDetailAndOptionalAbsence() {
        var list = get(REQUEST_PATH);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody())
                .contains("\"kind\":\"EXCUSE\"", "\"status\":\"PENDING\"", "\"origin\":\"MANUAL\"");
        var detail = get(REQUEST_PATH + "/" + REQUEST_ID);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody())
                .contains(
                        "\"reason\":\"ILLNESS\"",
                        "\"comment\":null",
                        "\"decision\":null",
                        "\"decisionSelection\":null",
                        "\"state\":\"ACTIVE\"",
                        "\"state\":\"EXPIRED\"");
        var options = get(REQUEST_PATH + "/options");
        assertThat(options.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(options.getBody())
                .contains("\"code\":\"ILLNESS\"", "\"kind\":\"EXCUSE\"", "\"origin\":\"MANUAL\"");
        REQUEST.set(
                REQUEST.get().toBuilder()
                        .clearReason()
                        .clearAttachments()
                        .setSummary(
                                REQUEST.get().getSummary().toBuilder()
                                        .setKind(
                                                StudentRequestKind
                                                        .STUDENT_REQUEST_KIND_LATE_CHECKIN))
                        .build());
        var withoutReason = get(REQUEST_PATH + "/" + REQUEST_ID);
        assertThat(withoutReason.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withoutReason.getBody())
                .contains("\"reason\":null", "\"kind\":\"LATE_CHECKIN\"");
        REQUEST.set(
                knownRequest().toBuilder()
                        .setReason(StudentExcuseReason.STUDENT_EXCUSE_REASON_OTHER)
                        .build());
        var withReason = get(REQUEST_PATH + "/" + REQUEST_ID);
        assertThat(withReason.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withReason.getBody()).contains("\"reason\":\"OTHER\"");
        assertForwardedIdentity();
    }

    @ParameterizedTest
    @CsvSource({
        "kind,0",
        "kind,8675309",
        "status,0",
        "origin,0",
        "origin,8675309",
        "reason,0",
        "reason,8675309",
        "attachment,0"
    })
    void unknownRequestEnumIsTypedDependencyFailure(String boundary, int code) {
        assertUnknownRequestEnum(boundary, code);
    }

    @Test
    void requestUnknownStatusAndAttachmentStateAreTypedFailures() {
        assertAll(
                () -> assertUnknownRequestEnum("status", 8675309),
                () -> assertUnknownRequestEnum("attachment", 8675309));
    }

    private void assertUnknownRequestEnum(String boundary, int code) {
        var source = knownRequest().toBuilder();
        var summary = source.getSummary().toBuilder();
        String path = REQUEST_PATH;
        switch (boundary) {
            case "kind" -> summary.setKindValue(code);
            case "status" -> summary.setStatusValue(code);
            case "origin" -> summary.setOriginValue(code);
            case "reason" -> {
                source.setReasonValue(code);
                path += "/" + REQUEST_ID;
            }
            case "attachment" -> {
                source.setAttachments(0, source.getAttachments(0).toBuilder().setStateValue(code));
                path += "/" + REQUEST_ID;
            }
            default -> throw new IllegalArgumentException("Unexpected test boundary");
        }
        StudentRequestDetail upstream = source.setSummary(summary).build();
        REQUEST.set(upstream);
        assertDependencyFailure(get(path));
        assertThat(REQUEST.get()).isEqualTo(upstream);
        REQUEST.set(knownRequest());
        assertThat(get(path).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertForwardedIdentity();
    }

    @ParameterizedTest
    @ValueSource(strings = {"reason", "pending-kind", "pending-origin"})
    void unknownRequestOptionEnumIsTypedDependencyFailure(String boundary) {
        var source = REQUEST_OPTIONS.get().toBuilder();
        if (boundary.equals("reason")) {
            source.setReasons(0, source.getReasons(0).toBuilder().setCodeValue(8675309));
        } else {
            var lesson = source.getLessons(0).toBuilder();
            var pending = lesson.getPendingRequests(0).toBuilder();
            if (boundary.equals("pending-kind")) pending.setKindValue(8675309);
            else pending.setOriginValue(8675309);
            source.setLessons(0, lesson.setPendingRequests(0, pending));
        }
        StudentRequestOptions upstream = source.build();
        REQUEST_OPTIONS.set(upstream);
        assertDependencyFailure(get(REQUEST_PATH + "/options"));
        assertThat(REQUEST_OPTIONS.get()).isEqualTo(upstream);
        REQUEST_OPTIONS.set(knownRequestOptions());
        assertThat(get(REQUEST_PATH + "/options").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertForwardedIdentity();
    }

    @Test
    void knownMapSlotsAndAbsentPlanKeepTheirWireMeaning() throws Exception {
        var response = get(PLAN_PATH);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains(
                        "\"format\":\"png\"",
                        "\"state\":\"ready\"",
                        "\"format\":\"svg\"",
                        "\"state\":\"absent\"");
        var absentSlot = json.readTree(response.getBody()).path("plan").path("svg");
        assertThat(absentSlot.path("id").isNull()).isTrue();
        assertThat(absentSlot.path("sha256").isNull()).isTrue();
        assertThat(absentSlot.has("width")).isFalse();
        assertThat(absentSlot.has("height")).isFalse();
        assertThat(absentSlot.has("viewBox")).isFalse();
        PLAN.set(
                CampusMapPlanResponse.newBuilder()
                        .setNoPlan(ru.rutcampustrack.academic.grpc.Empty.getDefaultInstance())
                        .build());
        var absent = get(PLAN_PATH);
        assertThat(absent.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(absent.getBody()).isNull();
        assertForwardedIdentity();
    }

    @ParameterizedTest
    @ValueSource(strings = {"format", "state"})
    void unknownMapEnumIsTypedDependencyFailure(String boundary) {
        var source = PLAN.get().getPlan().toBuilder();
        var slot = source.getPng().toBuilder();
        if (boundary.equals("format")) slot.setFormatValue(8675309);
        else slot.setStateValue(8675309);
        CampusMapPlanResponse upstream =
                PLAN.get().toBuilder().setPlan(source.setPng(slot)).build();
        PLAN.set(upstream);
        assertDependencyFailure(get(PLAN_PATH));
        assertThat(PLAN.get()).isEqualTo(upstream);
        assertForwardedIdentity();
    }

    private void assertDependencyFailure(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).contains("\"code\":\"DEPENDENCY_UNAVAILABLE\"");
        assertThat(response.getBody())
                .doesNotContain("8675309", "UNRECOGNIZED", "UNSPECIFIED", "\"state\":\"ACTIVE\"");
    }

    private ResponseEntity<String> today() {
        return get("/api/v1/student/today");
    }

    private ResponseEntity<String> get(String path) {
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
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
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

    private static StudentRequestDetail knownRequest() {
        var active =
                StudentRequestAttachment.newBuilder()
                        .setId("888888888888888888888888")
                        .setName("document.pdf")
                        .setContentType("application/pdf")
                        .setSizeBytes(32)
                        .setSha256("a".repeat(64))
                        .setState(
                                StudentRequestAttachmentState
                                        .STUDENT_REQUEST_ATTACHMENT_STATE_ACTIVE)
                        .setUploadedAt("2026-03-05T09:00:00Z")
                        .setExpiresAt("2026-04-05T09:00:00Z")
                        .build();
        return StudentRequestDetail.newBuilder()
                .setSummary(
                        StudentRequestSummary.newBuilder()
                                .setId(REQUEST_ID)
                                .setKind(StudentRequestKind.STUDENT_REQUEST_KIND_EXCUSE)
                                .setStatus(StudentRequestStatus.STUDENT_REQUEST_STATUS_PENDING)
                                .setOrigin(StudentRequestOrigin.STUDENT_REQUEST_ORIGIN_MANUAL)
                                .setCreatedAt("2026-03-05T09:00:00Z")
                                .setUpdatedAt("2026-03-05T09:00:00Z"))
                .setReason(StudentExcuseReason.STUDENT_EXCUSE_REASON_ILLNESS)
                .addAttachments(active)
                .addAttachments(
                        active.toBuilder()
                                .setId("999999999999999999999999")
                                .setState(
                                        StudentRequestAttachmentState
                                                .STUDENT_REQUEST_ATTACHMENT_STATE_EXPIRED)
                                .setExpiredAt("2026-04-05T09:00:00Z"))
                .build();
    }

    private static StudentRequestOptions knownRequestOptions() {
        var summary = knownRequest().getSummary();
        return StudentRequestOptions.newBuilder()
                .addReasons(
                        StudentRequestReasonOption.newBuilder()
                                .setCode(StudentExcuseReason.STUDENT_EXCUSE_REASON_ILLNESS)
                                .setLabel("Болезнь"))
                .addLessons(
                        StudentRequestLessonOption.newBuilder()
                                .setLesson(
                                        StudentRequestLesson.newBuilder()
                                                .setId(1001)
                                                .setLessonNumber(1))
                                .addPendingRequests(
                                        StudentRequestPendingRef.newBuilder()
                                                .setId(REQUEST_ID)
                                                .setKind(summary.getKind())
                                                .setOrigin(summary.getOrigin())))
                .build();
    }

    private static CampusMapPlanResponse knownPlan() {
        return CampusMapPlanResponse.newBuilder()
                .setPlan(
                        CampusMapPlan.newBuilder()
                                .setBuildingId("1")
                                .setFloorId("2")
                                .setVersion(3)
                                .setLabel("Этаж 2")
                                .setPng(
                                        CampusMapFormatSlot.newBuilder()
                                                .setFormat(CampusMapFormat.CAMPUS_MAP_FORMAT_PNG)
                                                .setState(
                                                        CampusMapFormatState
                                                                .CAMPUS_MAP_FORMAT_STATE_READY)
                                                .setContentType("image/png")
                                                .setAssetId("31")
                                                .setBytes(32)
                                                .setSha256("b".repeat(64))
                                                .setWidth(100)
                                                .setHeight(200))
                                .setSvg(
                                        CampusMapFormatSlot.newBuilder()
                                                .setFormat(CampusMapFormat.CAMPUS_MAP_FORMAT_SVG)
                                                .setState(
                                                        CampusMapFormatState
                                                                .CAMPUS_MAP_FORMAT_STATE_ABSENT)
                                                .setContentType("image/svg+xml")))
                .build();
    }

    private static final class AcademicProducer
            extends AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase {
        @Override
        public void getCampusFloorPlan(
                CampusMapFloorRequest request, StreamObserver<CampusMapPlanResponse> response) {
            response.onNext(PLAN.get());
            response.onCompleted();
        }

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
        public void listStudentRequests(
                StudentRequestListQuery request, StreamObserver<StudentRequestPage> response) {
            response.onNext(
                    StudentRequestPage.newBuilder()
                            .addContent(REQUEST.get().getSummary())
                            .setPage(0)
                            .setSize(20)
                            .setTotalElements(1)
                            .setTotalPages(1)
                            .build());
            response.onCompleted();
        }

        @Override
        public void getStudentRequest(
                StudentRequestId request, StreamObserver<StudentRequestDetail> response) {
            if (!REQUEST_ID.equals(request.getRequestId())) {
                response.onError(Status.NOT_FOUND.asRuntimeException());
                return;
            }
            response.onNext(REQUEST.get());
            response.onCompleted();
        }

        @Override
        public void getStudentRequestOptions(
                StudentRequestOptionsQuery request,
                StreamObserver<StudentRequestOptions> response) {
            response.onNext(REQUEST_OPTIONS.get());
            response.onCompleted();
        }

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
