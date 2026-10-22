package ru.rutcampustrack.schedule.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.grpc.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;

import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSession;

class BotReadScheduleIdentityInterceptorTest {
    private static final String BFF = token((byte) 1);
    private static final String ATTENDANCE = token((byte) 2);
    private static final String REPORT = token((byte) 4);
    private final BotReadScheduleIdentityInterceptor directed =
            new BotReadScheduleIdentityInterceptor(BFF, ATTENDANCE, REPORT);
    private final GrpcAuthInterceptor legacy = new GrpcAuthInterceptor();

    @Test
    void exactDirectedReadsWorkWithoutGlobalSecretOnlyOnAuthenticatedTls() {
        ReflectionTestUtils.setField(legacy, "expectedSecret", "legacy-secret");
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, BFF, true, false)).isTrue();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, REPORT, true, false)).isTrue();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.LESSON, REPORT, true, false))
                .isFalse();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, REPORT, false, false)).isFalse();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.LESSON, ATTENDANCE, true, false))
                .isTrue();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, ATTENDANCE, true, false))
                .isFalse();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.LESSON, BFF, true, false)).isFalse();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, BFF, false, false)).isFalse();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, token((byte) 3), true, false))
                .isFalse();
    }

    @Test
    void newTokensCannotCallOtherReadsOrMutationsEvenWhenLegacySecretIsUnconfigured() {
        for (String method :
                java.util.List.of(
                        "GetActiveLesson",
                        "GetLessonsByIds",
                        "ResolveLesson",
                        "ReserveHomeworkBinding",
                        "InstallAssignmentCloseCap",
                        "SetSemesterArchiveBarrier")) {
            assertThat(
                            invoke(
                                    ScheduleGrpcServiceGrpc.SERVICE_NAME + "/" + method,
                                    REPORT,
                                    true,
                                    false))
                    .as(method)
                    .isFalse();
            assertThat(
                            invoke(
                                    ScheduleGrpcServiceGrpc.SERVICE_NAME + "/" + method,
                                    BFF,
                                    true,
                                    false))
                    .as(method)
                    .isFalse();
            assertThat(
                            invoke(
                                    ScheduleGrpcServiceGrpc.SERVICE_NAME + "/" + method,
                                    ATTENDANCE,
                                    true,
                                    false))
                    .as(method)
                    .isFalse();
        }
    }

    @Test
    void existingNonBotConsumersKeepTheirGlobalSecretAdmission() {
        ReflectionTestUtils.setField(legacy, "expectedSecret", "legacy-secret");
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, null, false, true)).isTrue();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.LESSON, null, false, true)).isTrue();
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, null, true, false)).isFalse();
        assertThat(
                        invoke(
                                ScheduleGrpcServiceGrpc.SERVICE_NAME + "/GetActiveLesson",
                                null,
                                false,
                                true))
                .isTrue();
    }

    @Test
    void duplicateOrMissingReportCredentialsCannotInheritLegacyAdmission() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new BotReadScheduleIdentityInterceptor(BFF, ATTENDANCE, BFF))
                .isInstanceOf(IllegalArgumentException.class);
        ReflectionTestUtils.setField(legacy, "expectedSecret", "legacy-secret");
        assertThat(invoke(BotReadScheduleIdentityInterceptor.DAY, "unknown", true, true)).isFalse();
    }

    private boolean invoke(String method, String token, boolean tls, boolean oldSecret) {
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
        if (oldSecret)
            headers.put(
                    Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                    "legacy-secret");
        AtomicBoolean reached = new AtomicBoolean();
        directed.interceptCall(
                call,
                headers,
                (nextCall, metadata) ->
                        legacy.interceptCall(
                                nextCall,
                                metadata,
                                (acceptedCall, acceptedHeaders) -> {
                                    reached.set(true);
                                    return new ServerCall.Listener<>() {};
                                }));
        return reached.get();
    }

    private static String token(byte fill) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
