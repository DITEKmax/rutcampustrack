package ru.rutcampustrack.academic.grpc;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** Explicit Academic registration for the narrow service identity boundary. */
@Configuration(proxyBeanMethods = false)
public class AssignmentCloseServiceAuthConfiguration {

    @GrpcGlobalServerInterceptor
    public AssignmentCloseServiceIdentityInterceptor assignmentCloseServiceIdentityInterceptor(
            @Value("${grpc.service-identity.schedule-to-academic-token:}") String token) {
        return new AssignmentCloseServiceIdentityInterceptor(token);
    }
}
