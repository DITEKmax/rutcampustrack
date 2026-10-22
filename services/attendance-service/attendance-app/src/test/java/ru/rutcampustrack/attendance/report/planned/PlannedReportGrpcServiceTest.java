package ru.rutcampustrack.attendance.report.planned;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;

import io.grpc.*;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Jwts;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.attendance.report.PlannedReportRenderer;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.shared.security.*;
import ru.rutcampustrack.shared.security.grpc.*;

import java.time.Instant;
import java.util.*;

import javax.net.ssl.SSLSession;

class PlannedReportGrpcServiceTest {
    private static final InternalJwtTestFactory KEYS = new InternalJwtTestFactory();
    private final PublicKeyProvider publicKeys = mock(PublicKeyProvider.class);
    private final InternalJwtValidator validator;
    private final PlannedReportRenderer renderer = mock(PlannedReportRenderer.class);
    private final PlannedReportGrpcService service;

    PlannedReportGrpcServiceTest() {
        when(publicKeys.getPublicKey()).thenReturn(KEYS.publicKey());
        validator =
                spy(
                        new InternalJwtValidator(
                                publicKeys,
                                new InternalJwtProperties(null, 60, 0, false, null, null, null)));
        service = new PlannedReportGrpcService(validator, renderer);
    }

    @Test
    void adminStatsMethodRequiresExactReportCapabilityAndDiscardsAfterExpiry() {
        var plan = ReportPlanFixtures.adminPlan();
        String cap =
                capability(plan, Instant.now().plusSeconds(45), ReportPlanFixtures.adminActor());
        var result =
                ru.rutcampustrack.attendance.grpc.AdminGroupStatsSnapshot.newBuilder()
                        .setSemesterId(9)
                        .build();
        when(renderer.readAdminGroupStats(any())).thenReturn(result);
        var accepted = invokeAdmin(plan, cap);
        assertThat(accepted.result).isEqualTo(result);
        assertThat(accepted.completed).isTrue();
        verify(validator, times(2))
                .validateAcademicReport(
                        cap,
                        plan.getReportKind(),
                        AdmittedReportPlan.digest(plan),
                        plan.getJobId(),
                        plan.getLeaseId());
        assertThatThrownBy(() -> validator.validate(cap)).isInstanceOf(InternalJwtException.class);
        var changed = plan.toBuilder().setLeaseId(UUID.randomUUID().toString()).build();
        assertThat(Status.fromThrowable(invokeAdmin(changed, cap).error).getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(
                        Status.fromThrowable(
                                        invokeAdmin(
                                                        ReportPlanFixtures.plan(
                                                                "HEADMAN_STATS", "html"),
                                                        cap)
                                                .error)
                                .getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        doReturn(ReportPlanFixtures.adminActor())
                .doThrow(new InternalJwtException("expired during read"))
                .when(validator)
                .validateAcademicReport(
                        eq(cap), anyString(), anyString(), anyString(), anyString());
        var expired = invokeAdmin(plan, cap);
        assertThat(expired.result).isNull();
        assertThat(expired.completed).isFalse();
        assertThat(Status.fromThrowable(expired.error).getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    private AdminRecording invokeAdmin(ReportRenderPlan plan, String cap) {
        var observer = new AdminRecording();
        Context.current()
                .withValue(PlannedReportIdentityInterceptor.CAPABILITY, cap)
                .withValue(ServicePrincipal.CONTEXT_KEY, ServicePrincipal.ACADEMIC_SERVICE)
                .run(
                        () ->
                                service.readAdminGroupStats(
                                        RenderReportRequest.newBuilder().setPlan(plan).build(),
                                        observer));
        return observer;
    }

    private static final class AdminRecording
            implements StreamObserver<ru.rutcampustrack.attendance.grpc.AdminGroupStatsSnapshot> {
        ru.rutcampustrack.attendance.grpc.AdminGroupStatsSnapshot result;
        Throwable error;
        boolean completed;

        public void onNext(ru.rutcampustrack.attendance.grpc.AdminGroupStatsSnapshot value) {
            result = value;
        }

        public void onError(Throwable value) {
            error = value;
        }

        public void onCompleted() {
            completed = true;
        }
    }

    @Test
    void signedCapabilityBindsWholeMaterialAndStoredSessionBeforeAndAfterRendering() {
        var plan = ReportPlanFixtures.plan("HEADMAN_STATS", "html");
        when(renderer.render(any()))
                .thenReturn(
                        RenderReportResponse.newBuilder()
                                .setBody(ByteString.copyFromUtf8("rendered"))
                                .setFilename("report.html")
                                .setContentType("text/html")
                                .build());
        String cap = capability(plan, Instant.now().plusSeconds(45));
        var observer = invoke(plan, cap, true);
        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.result.getBody().toStringUtf8()).isEqualTo("rendered");
        verify(validator, times(2))
                .validateAcademicReport(
                        cap,
                        plan.getReportKind(),
                        AdmittedReportPlan.digest(plan),
                        plan.getJobId(),
                        plan.getLeaseId());
        assertThatThrownBy(() -> validator.validate(cap)).isInstanceOf(InternalJwtException.class);
    }

    @Test
    void alteredSnapshotsLeaseActorUnknownKindAndExpiredAuthorityDenyWithoutRendering() {
        var original = ReportPlanFixtures.plan("HEADMAN_STATS", "html");
        String cap = capability(original, Instant.now().plusSeconds(45));
        for (var altered :
                List.of(
                        original.toBuilder()
                                .setGroups(
                                        0,
                                        original.getGroups(0).toBuilder().setName("Other snapshot"))
                                .build(),
                        original.toBuilder().setLeaseId(UUID.randomUUID().toString()).build(),
                        original.toBuilder()
                                .setActor(original.getActor().toBuilder().setRolesVersion(6))
                                .build(),
                        original.toBuilder().setReportKind("UNKNOWN").build())) {
            assertThat(Status.fromThrowable(invoke(altered, cap, true).error).getCode())
                    .isEqualTo(Status.Code.UNAUTHENTICATED);
        }
        assertThat(
                        Status.fromThrowable(
                                        invoke(
                                                        original,
                                                        capability(
                                                                original,
                                                                Instant.now().minusSeconds(1)),
                                                        true)
                                                .error)
                                .getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        String ordinary =
                KEYS.validToken(
                        1,
                        ReportPlanFixtures.SESSION,
                        2,
                        5,
                        "STUDENT",
                        "ACTIVE",
                        33L,
                        false,
                        false);
        assertThat(Status.fromThrowable(invoke(original, ordinary, true).error).getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        var actorMismatch =
                original.toBuilder()
                        .setActor(original.getActor().toBuilder().setRolesVersion(6))
                        .build();
        assertThat(
                        Status.fromThrowable(
                                        invoke(
                                                        actorMismatch,
                                                        capability(
                                                                actorMismatch,
                                                                Instant.now().plusSeconds(45)),
                                                        true)
                                                .error)
                                .getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        verifyNoInteractions(renderer);
    }

    @Test
    void resultIsDiscardedWhenAdmissionExpiresDuringRenderingAndMissingPrincipalIsDenied() {
        var plan = ReportPlanFixtures.plan("HEADMAN_STATS", "html");
        String cap = capability(plan, Instant.now().plusSeconds(45));
        doReturn(ReportPlanFixtures.actor(false, false))
                .doThrow(new InternalJwtException("expired during rendering"))
                .when(validator)
                .validateAcademicReport(
                        eq(cap), anyString(), anyString(), anyString(), anyString());
        when(renderer.render(any()))
                .thenReturn(
                        RenderReportResponse.newBuilder()
                                .setBody(ByteString.copyFromUtf8("must discard"))
                                .build());
        var observer = invoke(plan, cap, true);
        assertThat(observer.result).isNull();
        assertThat(observer.completed).isFalse();
        assertThat(Status.fromThrowable(observer.error).getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(Status.fromThrowable(invoke(plan, cap, false).error).getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        verify(renderer).render(any());
    }

    @Test
    void dedicatedServiceTokenRequiresTlsAndOneCapabilityAndCannotEnterOtherRpcs() {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var interceptor = new PlannedReportIdentityInterceptor(token);
        String method = ReportRenderingServiceGrpc.getRenderReportMethod().getFullMethodName();
        assertThat(intercept(interceptor, method, token, true, 1)).isTrue();
        assertThat(intercept(interceptor, method, token, false, 1)).isFalse();
        assertThat(intercept(interceptor, method, token, true, 0)).isFalse();
        assertThat(intercept(interceptor, method, token, true, 2)).isFalse();
        assertThat(intercept(interceptor, method, null, true, 1)).isFalse();
        assertThat(intercept(interceptor, method, "unknown", true, 1)).isFalse();
        String adminMethod =
                ReportRenderingServiceGrpc.getReadAdminGroupStatsMethod().getFullMethodName();
        assertThat(intercept(interceptor, adminMethod, token, true, 1)).isTrue();
        assertThat(intercept(interceptor, adminMethod, token, false, 1)).isFalse();
        assertThat(intercept(interceptor, adminMethod, token, true, 0)).isFalse();
        assertThat(intercept(interceptor, adminMethod, token, true, 2)).isFalse();
        assertThat(intercept(interceptor, "attendance.Attendance/Mark", token, true, 1)).isFalse();
        assertThat(intercept(interceptor, "attendance.Attendance/Mark", null, false, 0)).isTrue();
    }

    private Recording invoke(ReportRenderPlan plan, String cap, boolean principal) {
        var observer = new Recording();
        Context context =
                Context.current().withValue(PlannedReportIdentityInterceptor.CAPABILITY, cap);
        if (principal)
            context =
                    context.withValue(
                            ServicePrincipal.CONTEXT_KEY, ServicePrincipal.ACADEMIC_SERVICE);
        context.run(
                () ->
                        service.renderReport(
                                RenderReportRequest.newBuilder().setPlan(plan).build(), observer));
        return observer;
    }

    private static String capability(ReportRenderPlan plan, Instant expiry) {
        return capability(plan, expiry, ReportPlanFixtures.actor(false, false));
    }

    private static String capability(
            ReportRenderPlan plan, Instant expiry, InternalJwtClaims actor) {
        return Jwts.builder()
                .header()
                .keyId(InternalJwtTestFactory.KEY_ID)
                .and()
                .subject(Long.toString(actor.userId()))
                .issuer(InternalJwtTestFactory.ISSUER)
                .audience()
                .add(InternalJwtTestFactory.AUDIENCE)
                .and()
                .claim("token_use", "academic_report")
                .claim("sid", actor.sessionId().toString())
                .claim("sv", Long.toString(actor.sessionVersion()))
                .claim("rv", Long.toString(actor.rolesVersion()))
                .claim("role", actor.role())
                .claim("status", actor.status())
                .claim("group_id", actor.groupId() == null ? null : Long.toString(actor.groupId()))
                .claim("is_headman", actor.isHeadman())
                .claim("readOnly", actor.readOnly())
                .claim("report_kind", plan.getReportKind())
                .claim("report_hash", AdmittedReportPlan.digest(plan))
                .claim("report_job", plan.getJobId())
                .claim("report_lease", plan.getLeaseId())
                .claim("report_not_after", expiry.getEpochSecond())
                .issuedAt(Date.from(Instant.now().minusSeconds(2)))
                .expiration(Date.from(expiry))
                .signWith(KEYS.keyPair().getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static boolean intercept(
            PlannedReportIdentityInterceptor interceptor,
            String method,
            String token,
            boolean tls,
            int caps) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getFullMethodName()).thenReturn(method);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        when(call.getAttributes())
                .thenReturn(
                        tls
                                ? Attributes.newBuilder()
                                        .set(
                                                Grpc.TRANSPORT_ATTR_SSL_SESSION,
                                                mock(SSLSession.class))
                                        .build()
                                : Attributes.EMPTY);
        Metadata headers = new Metadata();
        if (token != null) headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token);
        for (int i = 0; i < caps; i++)
            headers.put(PlannedReportIdentityInterceptor.CAPABILITY_HEADER, "synthetic-capability");
        var reached = new java.util.concurrent.atomic.AtomicBoolean();
        interceptor.interceptCall(
                call,
                headers,
                (accepted, metadata) -> {
                    reached.set(true);
                    return new ServerCall.Listener<>() {};
                });
        return reached.get();
    }

    private static final class Recording implements StreamObserver<RenderReportResponse> {
        private RenderReportResponse result;
        private Throwable error;
        private boolean completed;

        public void onNext(RenderReportResponse value) {
            result = value;
        }

        public void onError(Throwable failure) {
            error = failure;
        }

        public void onCompleted() {
            completed = true;
        }
    }
}
