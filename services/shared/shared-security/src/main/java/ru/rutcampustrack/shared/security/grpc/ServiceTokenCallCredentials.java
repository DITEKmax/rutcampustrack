package ru.rutcampustrack.shared.security.grpc;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.SecurityLevel;
import io.grpc.Status;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Target- and method-scoped gRPC credentials for one directed service token.
 * The token is released only after gRPC reports an authenticated confidential
 * transport and the requested method and authority match the local policy.
 */
public final class ServiceTokenCallCredentials extends CallCredentials {

    private static final Status TLS_REQUIRED =
            Status.UNAUTHENTICATED.withDescription("Service identity requires authenticated TLS");
    private static final Status SCOPE_DENIED =
            Status.PERMISSION_DENIED.withDescription("Service credential is outside its configured scope");

    private final DirectedServiceCredential credential;
    private final String targetAuthority;
    private final Set<String> protectedMethods;

    public ServiceTokenCallCredentials(
            DirectedServiceCredential credential,
            String targetAuthority,
            String... protectedMethods) {
        if (credential == null || targetAuthority == null || targetAuthority.isBlank()
                || protectedMethods == null || protectedMethods.length == 0
                || Arrays.stream(protectedMethods).anyMatch(method -> method == null || method.isBlank())) {
            throw new IllegalArgumentException("Service credential scope is required");
        }
        this.credential = credential;
        this.targetAuthority = targetAuthority;
        this.protectedMethods = Set.copyOf(new HashSet<>(Arrays.asList(protectedMethods)));
    }

    public static ServiceTokenCallCredentials forMethod(
            DirectedServiceCredential credential,
            String targetAuthority,
            String protectedMethod) {
        return new ServiceTokenCallCredentials(credential, targetAuthority, protectedMethod);
    }

    boolean appliesTo(String methodName) {
        return methodName != null && protectedMethods.contains(methodName);
    }

    @Override
    public void applyRequestMetadata(
            RequestInfo requestInfo,
            Executor appExecutor,
            MetadataApplier applier) {
        if (requestInfo == null || appExecutor == null || applier == null) {
            if (applier != null) {
                applier.fail(SCOPE_DENIED);
            }
            return;
        }
        Runnable apply = () -> {
            MethodDescriptor<?, ?> method = requestInfo.getMethodDescriptor();
            if (method == null || !protectedMethods.contains(method.getFullMethodName())
                    || !targetAuthority.equals(requestInfo.getAuthority())) {
                applier.fail(SCOPE_DENIED);
                return;
            }
            if (requestInfo.getSecurityLevel() != SecurityLevel.PRIVACY_AND_INTEGRITY) {
                applier.fail(TLS_REQUIRED);
                return;
            }

            Metadata metadata = new Metadata();
            metadata.discardAll(DirectedServiceCredential.TOKEN_METADATA_KEY);
            metadata.put(DirectedServiceCredential.TOKEN_METADATA_KEY, credential.wireToken());
            applier.apply(metadata);
        };
        try {
            appExecutor.execute(apply);
        } catch (RuntimeException rejected) {
            applier.fail(Status.UNAVAILABLE.withDescription("Service credential execution unavailable"));
        }
    }
}
