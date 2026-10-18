package ru.rutcampustrack.attendance.grpc;

import io.grpc.*;

import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;

import org.springframework.beans.factory.annotation.Value;

import ru.rutcampustrack.authauthority.grpc.AuthIdentityGrpcServiceGrpc;
import ru.rutcampustrack.botreads.grpc.AcademicOwnBotReadServiceGrpc;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;

/** IMP-09: Attaches shared secret to outgoing gRPC calls. */
@GrpcGlobalClientInterceptor
public class GrpcSecretClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> SECRET_KEY =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${grpc.auth.secret:}")
    private String secret;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        boolean botRead =
                method.getFullMethodName()
                                .equals(
                                        AuthIdentityGrpcServiceGrpc
                                                .getCheckAttendanceBotOwnAuthorityMethod()
                                                .getFullMethodName())
                        || method.getFullMethodName()
                                .equals(
                                        AcademicOwnBotReadServiceGrpc
                                                .getResolveAttendanceOwnBotReadScopeMethod()
                                                .getFullMethodName())
                        || method.getFullMethodName()
                                        .equals(
                                                ScheduleGrpcServiceGrpc.getGetLessonByIdMethod()
                                                        .getFullMethodName())
                                && callOptions.getCredentials()
                                        instanceof ServiceTokenCallCredentials;
        return new ForwardingClientCall.SimpleForwardingClientCall<>(
                next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (botRead) {
                    headers.discardAll(SECRET_KEY);
                } else if (secret != null && !secret.isBlank()) {
                    headers.put(SECRET_KEY, secret);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
