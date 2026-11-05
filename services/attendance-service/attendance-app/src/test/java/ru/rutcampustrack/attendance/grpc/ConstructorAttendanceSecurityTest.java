package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.rutcampustrack.attendance.constructor.*;
import ru.rutcampustrack.constructor.grpc.*;
import ru.rutcampustrack.shared.security.*;
import ru.rutcampustrack.shared.security.grpc.*;

class ConstructorAttendanceSecurityTest {
    @TempDir Path directory;
    private static final String TOKEN = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    @Test void onlyDedicatedPairOverTlsCanInvokeEitherMutationAndMissingFileFailsClosed() throws Exception {
        Path credential = directory.resolve("schedule-attendance.token"); Files.writeString(credential, TOKEN);
        var interceptor = new ConstructorAttendanceIdentityInterceptor(credential.toString());
        for (String method : List.of(ConstructorAttendanceParticipantGrpc.getApplyDeletionMethod().getFullMethodName(), ConstructorAttendanceParticipantGrpc.getMaterializePastMethod().getFullMethodName())) {
            var accepted = new AtomicBoolean();
            interceptor.interceptCall(call(method, true), headers(TOKEN), (c,h) -> {
                assertThat(ServicePrincipal.CONTEXT_KEY.get()).isEqualTo(ServicePrincipal.SCHEDULE_SERVICE);
                accepted.set(true); return new ServerCall.Listener<>() {};
            });
            assertThat(accepted).isTrue();
            var duplicate = headers(TOKEN); duplicate.put(DirectedServiceCredential.TOKEN_METADATA_KEY, TOKEN);
            for (Metadata denied : List.of(new Metadata(), headers("wrong"), duplicate)) {
                accepted.set(false);
                interceptor.interceptCall(call(method, true), denied, (c,h) -> { accepted.set(true); return new ServerCall.Listener<>() {}; });
                assertThat(accepted).isFalse();
            }
            accepted.set(false);
            interceptor.interceptCall(call(method, false), headers(TOKEN), (c,h) -> { accepted.set(true); return new ServerCall.Listener<>() {}; });
            assertThat(accepted).isFalse();
            new ConstructorAttendanceIdentityInterceptor(directory.resolve("absent").toString()).interceptCall(call(method,true), headers(TOKEN), (c,h) -> { accepted.set(true); return new ServerCall.Listener<>() {}; });
            assertThat(accepted).isFalse();
        }
    }

    @Test void previewRequiresSignedActorButDelegatedRecoveryDoesNotRequireExpiredUserJwt() {
        var deletion = mock(ConstructorDeletionParticipant.class);
        var materializer = mock(ConstructorPastMaterializer.class);
        var validator = mock(InternalJwtValidator.class);
        var service = new ConstructorAttendanceGrpcService(deletion, materializer, validator);
        var scope = ConstructorDeletionScope.newBuilder().setGroupId(7).build();
        var command = ConstructorDeletionCommand.newBuilder().setOriginalActorId(3).setScope(scope).setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW).build();
        StreamObserver<ConstructorParticipantReceipt> observer = mock(StreamObserver.class);
        Context source = Context.current().withValue(ServicePrincipal.CONTEXT_KEY, ServicePrincipal.SCHEDULE_SERVICE);
        source.run(() -> service.applyDeletion(command, observer));
        verify(observer).onError(argThat(e -> Status.fromThrowable(e).getCode() == Status.Code.UNAUTHENTICATED));
        verifyNoInteractions(deletion);
        reset(observer);
        when(deletion.apply(any())).thenReturn(ConstructorParticipantReceipt.getDefaultInstance());
        source.run(() -> service.applyDeletion(command.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE).build(), observer));
        verify(observer).onCompleted();
        verifyNoInteractions(validator);
        reset(observer, deletion);
        when(validator.validate("bad")).thenThrow(new InternalJwtException("invalid"));
        source.withValue(ConstructorAttendanceIdentityInterceptor.ACTOR_TOKEN,"bad").run(() -> service.applyDeletion(command.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE).build(), observer));
        verify(observer).onError(argThat(e -> Status.fromThrowable(e).getCode() == Status.Code.UNAUTHENTICATED));
        verifyNoInteractions(deletion);
    }

    private static Metadata headers(String token) { var h = new Metadata(); h.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token); return h; }
    @SuppressWarnings("unchecked") private static ServerCall<Object,Object> call(String method, boolean tls) {
        ServerCall<Object,Object> call = mock(ServerCall.class);
        MethodDescriptor<Object,Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getFullMethodName()).thenReturn(method);
        when(descriptor.getServiceName()).thenReturn(ConstructorAttendanceParticipantGrpc.SERVICE_NAME);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        when(call.getAttributes()).thenReturn(tls ? Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION,mock(SSLSession.class)).build() : Attributes.EMPTY);
        return call;
    }
}
