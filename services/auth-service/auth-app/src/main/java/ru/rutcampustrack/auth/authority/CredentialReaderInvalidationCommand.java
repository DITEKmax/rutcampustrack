package ru.rutcampustrack.auth.authority;

import io.grpc.Status;

import ru.rutcampustrack.authoperations.grpc.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Fixed transport and immutable business-intent rules; never contains domain SQL. */
final class CredentialReaderInvalidationCommand {
    static final int MAX_BYTES = 1024 * 1024;

    private CredentialReaderInvalidationCommand() {}

    static byte[] validate(PrepareCredentialReaderInvalidationRequest request) {
        uuid(request.getOperationId());
        if (!request.getSupersedesFailedOperationId().isEmpty())
            uuid(request.getSupersedesFailedOperationId());
        byte[] canonical = request.toBuilder().clearCommandHash().build().toByteArray();
        if (canonical.length > MAX_BYTES)
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("INVALIDATION_COMMAND_TOO_LARGE")
                    .asRuntimeException();
        if (request.getAcademicCommandHash().size() != 32
                || request.getCommandHash().size() != 32
                || !request.getUnknownFields().asMap().isEmpty()
                || request.getGroupsCount() == 0
                || !MessageDigest.isEqual(sha(canonical), request.getCommandHash().toByteArray()))
            throw invalid();
        boolean cycle =
                switch (request.getAction()) {
                    case CREDENTIAL_READER_INVALIDATION_ACTION_GROUP_DEACTIVATE,
                            CREDENTIAL_READER_INVALIDATION_ACTION_GROUP_RESTORE ->
                            false;
                    case CREDENTIAL_READER_INVALIDATION_ACTION_MANUAL_CYCLE_ARCHIVE,
                            CREDENTIAL_READER_INVALIDATION_ACTION_AUTOMATIC_CYCLE_ARCHIVE ->
                            true;
                    default -> throw invalid();
                };
        if (cycle
                ? request.getCycleSemesterId() <= 0
                        || request.getCycleYear() < 2000
                        || request.getCycleYear() > 9999
                : request.getCycleSemesterId() != 0
                        || request.getCycleYear() != 0
                        || request.getGroupsCount() != 1) throw invalid();
        long previous = 0;
        Set<Long> headmen = new HashSet<>();
        for (var g : request.getGroupsList()) {
            if (g.getGroupId() <= previous
                    || g.getExpectedGroupRevision() <= 0
                    || !g.getUnknownFields().asMap().isEmpty()) throw invalid();
            previous = g.getGroupId();
            if (g.getHeadmanUserId() == 0) {
                if (g.getExpectedHeadmanRolesVersion() != 0
                        || g.getExpectedHeadmanAcademicRevision() != 0) throw invalid();
            } else if (g.getHeadmanUserId() < 0
                    || g.getExpectedHeadmanRolesVersion() <= 0
                    || g.getExpectedHeadmanAcademicRevision() <= 0
                    || !headmen.add(g.getHeadmanUserId())) throw invalid();
        }
        return canonical;
    }

    static byte[] intent(PrepareCredentialReaderInvalidationRequest request) {
        var normalized =
                request.toBuilder()
                        .clearOperationId()
                        .clearCommandHash()
                        .clearSupersedesFailedOperationId()
                        .clearGroups();
        for (var group : request.getGroupsList())
            normalized.addGroups(
                    group.toBuilder()
                            .clearExpectedHeadmanRolesVersion()
                            .clearExpectedHeadmanAcademicRevision());
        return normalized.build().toByteArray();
    }

    static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw invalid();
            return id;
        } catch (IllegalArgumentException invalid) {
            throw invalid();
        }
    }

    static byte[] sha(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static io.grpc.StatusRuntimeException invalid() {
        return Status.INVALID_ARGUMENT
                .withDescription("INVALID_INVALIDATION_COMMAND")
                .asRuntimeException();
    }

    static io.grpc.StatusRuntimeException conflict() {
        return Status.FAILED_PRECONDITION
                .withDescription("INVALIDATION_CONFLICT")
                .asRuntimeException();
    }
}
