package ru.rutcampustrack.academic.constructor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
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
        interceptor.interceptCall(call(true),headers(PAIR), (call,headers)-> {
            admitted.set(ServicePrincipal.CONTEXT_KEY.get());
            assertThat(ConstructorServiceIdentityInterceptor.ACTOR.get()).isNull();
            return new ServerCall.Listener<>() {};
        });
        assertThat(admitted.get()).isEqualTo(ServicePrincipal.SCHEDULE_SERVICE);
        for(var rejected:new Object[][] {{false,PAIR},{true,"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"}}) {
            var call=call((Boolean)rejected[0]);
            var next=new AtomicBoolean();
            interceptor.interceptCall(call,headers((String)rejected[1]),(c,h)-> {next.set(true);return new ServerCall.Listener<>() {};});
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
            interceptor.interceptCall(call,headers,(c,h)-> {next.set(true);return new ServerCall.Listener<>() {};});
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
        interceptor().interceptCall(call(true),headers,(c,h)-> {
            seen.set(ConstructorServiceIdentityInterceptor.ACTOR.get()); return new ServerCall.Listener<>() {};
        });
        assertThat(seen.get()).isEqualTo(actor);
        var missing=new ConstructorServiceIdentityInterceptor("",validator);
        var called=new AtomicBoolean(); var call=call(true);
        missing.interceptCall(call,headers,(c,h)-> {called.set(true);return new ServerCall.Listener<>() {};});
        assertThat(called).isFalse();
        verify(call).close(any(),any());
    }

    private ConstructorServiceIdentityInterceptor interceptor() throws Exception {
        Path file=temporary.resolve("schedule-academic-synthetic.token"); Files.writeString(file,PAIR);
        return new ConstructorServiceIdentityInterceptor(file.toString(),validator);
    }
    private static Metadata headers(String token) {
        var headers=new Metadata(); headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY,token); return headers;
    }
    @SuppressWarnings("unchecked")
    private static ServerCall<Object,Object> call(boolean tls) {
        ServerCall<Object,Object> call=mock(ServerCall.class);
        MethodDescriptor<Object,Object> method=mock(MethodDescriptor.class);
        when(method.getFullMethodName()).thenReturn(ConstructorServiceIdentityInterceptor.METHOD);
        when(call.getMethodDescriptor()).thenReturn(method);
        var attributes=Attributes.newBuilder();
        if(tls) attributes.set(Grpc.TRANSPORT_ATTR_SSL_SESSION,mock(SSLSession.class));
        when(call.getAttributes()).thenReturn(attributes.build());
        return call;
    }
}
