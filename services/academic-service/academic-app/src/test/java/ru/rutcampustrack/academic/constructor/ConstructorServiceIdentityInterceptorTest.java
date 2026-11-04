package ru.rutcampustrack.academic.constructor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;
import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;
import io.grpc.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.academic.grpc.GrpcAuthInterceptor;
import ru.rutcampustrack.constructor.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

class ConstructorServiceIdentityInterceptorTest {
    private static final String PAIR = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final Metadata.Key<String> JWT=Metadata.Key.of("x-internal-token",Metadata.ASCII_STRING_MARSHALLER);
    @TempDir Path temporary;
    private final InternalJwtValidator validator=mock(InternalJwtValidator.class);

    @Test
    void recoveryWithoutJwtRequiresExactPairAndAuthenticatedTls() throws Exception {
        var interceptor=interceptor();
        var admitted=new AtomicReference<ServicePrincipal>();
        chain(interceptor,call(true),headers(PAIR), (call,headers)-> {
            admitted.set(ServicePrincipal.CONTEXT_KEY.get());
            assertThat(ConstructorServiceIdentityInterceptor.ACTOR.get()).isNull();
            assertThat(ConstructorServiceIdentityInterceptor.admitted(ConstructorServiceIdentityInterceptor.METHOD)).isTrue();
            return new ServerCall.Listener<>() {};
        });
        assertThat(admitted.get()).isEqualTo(ServicePrincipal.SCHEDULE_SERVICE);
        for(var rejected:new Object[][] {{false,PAIR},{true,"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"},{true,null}}) {
            var call=call((Boolean)rejected[0]);
            var next=new AtomicBoolean();
            var rejectedHeaders=headers((String)rejected[1]);
            rejectedHeaders.put(Metadata.Key.of("x-grpc-secret",Metadata.ASCII_STRING_MARSHALLER),"legacy-synthetic");
            chain(interceptor,call,rejectedHeaders,(c,h)-> {next.set(true);return new ServerCall.Listener<>() {};});
            assertThat(next).isFalse();
            var status=ArgumentCaptor.forClass(Status.class);
            verify(call).close(status.capture(),any());
            assertThat(status.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        }
        verifyNoInteractions(validator);
    }

    @Test
    void invalidOrDuplicatedProvidedJwtCannotDowngradeToDelegatedRecovery() throws Exception {
        var interceptor=interceptor();
        when(validator.validate("expired")).thenThrow(new InternalJwtException("expired synthetic actor"));
        for(boolean duplicate:new boolean[]{false,true}) {
            var headers=headers(PAIR); headers.put(JWT,"expired"); if(duplicate) headers.put(JWT,"expired");
            var call=call(true); var next=new AtomicBoolean();
            chain(interceptor,call,headers,(c,h)-> {next.set(true);return new ServerCall.Listener<>() {};});
            assertThat(next).isFalse();
            var status=ArgumentCaptor.forClass(Status.class);
            verify(call).close(status.capture(),any());
            assertThat(status.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        }
    }

    @Test
    void signedActorIsBoundAfterPairValidationAndBlankConfigurationHasNoFallback() throws Exception {
        var actor=new InternalJwtClaims(1,UUID.randomUUID(),1,1,"ADMIN","ACTIVE",null,false,false);
        when(validator.validate("signed")).thenReturn(actor);
        var headers=headers(PAIR); headers.put(JWT,"signed");
        var seen=new AtomicReference<InternalJwtClaims>();
        chain(interceptor(),call(true),headers,(c,h)-> {
            seen.set(ConstructorServiceIdentityInterceptor.ACTOR.get()); return new ServerCall.Listener<>() {};
        });
        assertThat(seen.get()).isEqualTo(actor);
        var missing=new ConstructorServiceIdentityInterceptor("",validator);
        var called=new AtomicBoolean(); var call=call(true);
        chain(missing,call,headers,(c,h)-> {called.set(true);return new ServerCall.Listener<>() {};});
        assertThat(called).isFalse();
        verify(call).close(any(),any());
    }

    @Test
    void composedChainStillRequiresSignedPreviewActorAtParticipant() throws Exception {
        var scope=ConstructorDeletionScope.newBuilder().setGroupId(1).setSubjectId(1).setSemesterId(1).addScheduleItemIds(1).build();
        var command=ConstructorDeletionCommand.newBuilder().setOperationId(UUID.randomUUID().toString())
                .setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW).setOriginalActorId(1)
                .setScope(scope).setScopeHash(ByteString.copyFrom(ConstructorHashes.sha256(scope.toByteArray())))
                .setRequestHash(ByteString.copyFrom(new byte[32])).build();
        var jdbc=mock(JdbcTemplate.class);
        var service=new ConstructorAcademicGrpcService(new ConstructorAcademicParticipant(jdbc));
        var failure=new AtomicReference<Throwable>();
        chain(interceptor(),call(true),headers(PAIR),(c,h)-> {
            service.applyDeletion(command,new StreamObserver<ConstructorParticipantReceipt>() {
                @Override public void onNext(ConstructorParticipantReceipt value) { fail("unsigned preview admitted"); }
                @Override public void onError(Throwable error) { failure.set(error); }
                @Override public void onCompleted() { fail("unsigned preview completed"); }
            });
            return new ServerCall.Listener<>() {};
        });
        assertThat(Status.fromThrowable(failure.get()).getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        verifyNoInteractions(jdbc);
    }

    @Test
    void verifiedConstructorContextDoesNotBypassUnrelatedLegacyMethod() throws Exception {
        var interceptor=interceptor();
        var method="rutcampustrack.academic.AcademicGrpcService/GetGroupMembers";
        for(boolean legacySecret:new boolean[]{false,true}) {
            var metadata=headers(PAIR);
            if(legacySecret) metadata.put(Metadata.Key.of("x-grpc-secret",Metadata.ASCII_STRING_MARSHALLER),"legacy-synthetic");
            var allowed=new AtomicBoolean();
            Context.current().withValue(ServicePrincipal.CONTEXT_KEY,ServicePrincipal.SCHEDULE_SERVICE)
                    .withValue(ConstructorServiceIdentityInterceptor.VERIFIED_SCHEDULE,true).run(() ->
                        chain(interceptor,call(method,true),metadata,(c,h)-> {
                            assertThat(ConstructorServiceIdentityInterceptor.admitted(method)).isFalse();
                            allowed.set(true); return new ServerCall.Listener<>() {};
                        }));
            assertThat(allowed.get()).isEqualTo(legacySecret);
        }
    }

    private static ServerCall.Listener<Object> chain(ConstructorServiceIdentityInterceptor constructor,
            ServerCall<Object,Object> call,Metadata metadata,ServerCallHandler<Object,Object> next) {
        var legacy=new GrpcAuthInterceptor();
        ReflectionTestUtils.setField(legacy,"expectedSecret","legacy-synthetic");
        // Deliberately reversed input: actual Spring ordering must put pair admission first.
        var interceptors=new java.util.ArrayList<ServerInterceptor>(java.util.List.of(legacy,constructor));
        AnnotationAwareOrderComparator.sort(interceptors);
        var handler=next;
        for(int index=interceptors.size()-1;index>=0;index--) {
            var interceptor=interceptors.get(index);
            var downstream=handler;
            handler=(c,h)->interceptor.interceptCall(c,h,downstream);
        }
        return handler.startCall(call,metadata);
    }

    private ConstructorServiceIdentityInterceptor interceptor() throws Exception {
        Path file=temporary.resolve("schedule-academic-synthetic.token"); Files.writeString(file,PAIR);
        return new ConstructorServiceIdentityInterceptor(file.toString(),validator);
    }
    private static Metadata headers(String token) {
        var headers=new Metadata(); if(token!=null) headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY,token); return headers;
    }
    @SuppressWarnings("unchecked")
    private static ServerCall<Object,Object> call(boolean tls) {
        return call(ConstructorServiceIdentityInterceptor.METHOD,tls);
    }
    @SuppressWarnings("unchecked")
    private static ServerCall<Object,Object> call(String methodName,boolean tls) {
        ServerCall<Object,Object> call=mock(ServerCall.class);
        MethodDescriptor<Object,Object> method=mock(MethodDescriptor.class);
        when(method.getFullMethodName()).thenReturn(methodName);
        when(call.getMethodDescriptor()).thenReturn(method);
        var attributes=Attributes.newBuilder();
        if(tls) attributes.set(Grpc.TRANSPORT_ATTR_SSL_SESSION,mock(SSLSession.class));
        when(call.getAttributes()).thenReturn(attributes.build());
        return call;
    }
}
