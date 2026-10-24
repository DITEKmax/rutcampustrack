package ru.rutcampustrack.auth.authority;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exact method/caller admission. Neither a user JWT nor the legacy gRPC secret suffices. */
@Component
public final class AuthDirectedIdentityInterceptor implements ServerInterceptor {
    public static final ServicePrincipal AUTH = new ServicePrincipal("auth-service");
    public static final ServicePrincipal BOT = new ServicePrincipal("notification-bot");
    public static final ServicePrincipal BFF = new ServicePrincipal("mobile-bff");
    public static final ServicePrincipal ACADEMIC = ServicePrincipal.ACADEMIC_SERVICE;
    public static final ServicePrincipal ATTENDANCE = new ServicePrincipal("attendance-service");
    private static final String BOT_SERVICE =
            "rutcampustrack.authauthority.AuthIdentityGrpcService/";
    private static final String DATA_SERVICE =
            "rutcampustrack.authauthority.AuthDataAuthorityService/";
    private static final String OP_SERVICE =
            "rutcampustrack.authoperations.AuthIdentityOperationService/";
    private static final String READER_SERVICE =
            "rutcampustrack.authoperations.AuthCredentialReaderInvalidationService/";
    private static final String CAP_SERVICE =
            "rutcampustrack.authsecurity.AuthSecurityCapabilityService/";
    private final ServiceIdentityServerInterceptor delegate;

    public AuthDirectedIdentityInterceptor(
            @Value("${grpc.service-identity.bot-to-auth-token:}") String bot,
            @Value("${grpc.service-identity.mobile-bff-to-auth-token:}") String bff,
            @Value("${grpc.service-identity.academic-to-auth-token:}") String academic,
            @Value("${grpc.service-identity.attendance-to-auth-token:}") String attendance) {
        List<DirectedServiceCredential> credentials = new ArrayList<>();
        add(credentials, BOT, bot);
        add(credentials, BFF, bff);
        add(credentials, ACADEMIC, academic);
        add(credentials, ATTENDANCE, attendance);
        delegate =
                new ServiceIdentityServerInterceptor(
                        AUTH,
                        Map.ofEntries(
                                Map.entry(BOT_SERVICE + "GetOwnBotMenuScope", BOT),
                                Map.entry(BOT_SERVICE + "GetOwnBotProfile", BOT),
                                Map.entry(BOT_SERVICE + "CheckBotOwnAuthority", BFF),
                                Map.entry(BOT_SERVICE + "CheckAcademicBotOwnAuthority", ACADEMIC),
                                Map.entry(
                                        BOT_SERVICE + "CheckAttendanceBotOwnAuthority", ATTENDANCE),
                                Map.entry(DATA_SERVICE + "ReadIdentityAuthority", ACADEMIC),
                                Map.entry(DATA_SERVICE + "CheckAdminAccess", ACADEMIC),
                                Map.entry(DATA_SERVICE + "ReadInitialCredentials", ACADEMIC),
                                Map.entry(DATA_SERVICE + "GetIdentityCatalogWatermark", ACADEMIC),
                                Map.entry(DATA_SERVICE + "ReadIdentityCatalogChanges", ACADEMIC),
                                Map.entry(OP_SERVICE + "PrepareIdentityOperation", ACADEMIC),
                                Map.entry(OP_SERVICE + "CommitIdentityOperation", ACADEMIC),
                                Map.entry(OP_SERVICE + "GetIdentityOperation", ACADEMIC),
                                Map.entry(OP_SERVICE + "FailIdentityOperation", ACADEMIC),
                                Map.entry(
                                        OP_SERVICE + "RejectUnpreparedIdentityOperation", ACADEMIC),
                                Map.entry(
                                        READER_SERVICE + "PrepareCredentialReaderInvalidation",
                                        ACADEMIC),
                                Map.entry(
                                        READER_SERVICE + "GetCredentialReaderInvalidation",
                                        ACADEMIC),
                                Map.entry(
                                        READER_SERVICE + "CommitCredentialReaderInvalidation",
                                        ACADEMIC),
                                Map.entry(
                                        READER_SERVICE + "FailCredentialReaderInvalidation",
                                        ACADEMIC),
                                Map.entry(
                                        READER_SERVICE
                                                + "RejectUnpreparedCredentialReaderInvalidation",
                                        ACADEMIC),
                                Map.entry(CAP_SERVICE + "AdmitStoredSession", ACADEMIC),
                                Map.entry(CAP_SERVICE + "AdmitSessionIdentity", ACADEMIC),
                                Map.entry(CAP_SERVICE + "IssueReportCapability", ACADEMIC),
                                Map.entry(CAP_SERVICE + "RedeemReportCapability", ACADEMIC),
                                Map.entry(CAP_SERVICE + "ChallengeSubjectDeletion", ACADEMIC),
                                Map.entry(CAP_SERVICE + "VerifySubjectDeletion", ACADEMIC),
                                Map.entry(CAP_SERVICE + "CheckSubjectDeletionReceipt", ACADEMIC),
                                Map.entry(
                                        CAP_SERVICE + "CheckStoredSubjectDeletionReceipt",
                                        ACADEMIC),
                                Map.entry(CAP_SERVICE + "ApplyGroupDeletion", ACADEMIC),
                                Map.entry(CAP_SERVICE + "ConfirmPasswordStepUp", ACADEMIC),
                                Map.entry(CAP_SERVICE + "CheckPasswordStepUpReceipt", ACADEMIC)),
                        credentials);
    }

    private static void add(
            List<DirectedServiceCredential> credentials, ServicePrincipal caller, String token) {
        DirectedServiceCredential.tryCreate(caller, AUTH, token).ifPresent(credentials::add);
    }

    static void require(ServicePrincipal expected) {
        if (!expected.equals(ServicePrincipal.CONTEXT_KEY.get())) {
            throw Status.UNAUTHENTICATED
                    .withDescription("verified Auth caller required")
                    .asRuntimeException();
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
