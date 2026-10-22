package ru.rutcampustrack.auth.authority;

import static ru.rutcampustrack.auth.authority.IdentityOperationFailure.error;

import com.google.protobuf.ByteString;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.authoperations.grpc.*;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

/** Immutable negative reservation; caller must hold the positive operation lock for insert. */
final class AuthIdentityRejectionStore {
    private final JdbcTemplate jdbc;

    AuthIdentityRejectionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    IdentityOperationResult find(UUID id, ByteString hash, byte[] canonical) {
        var rows =
                jdbc.query(
                        "SELECT command_hash,canonical_command,kind,reason FROM"
                                + " auth_identity_operation_rejections WHERE operation_id=?",
                        (rs, n) -> {
                            if (!MessageDigest.isEqual(rs.getBytes(1), hash.toByteArray())
                                    || (canonical != null
                                            && !Arrays.equals(rs.getBytes(2), canonical)))
                                throw error(Status.ALREADY_EXISTS, "OPERATION_ID_CONFLICT");
                            return IdentityOperationResult.newBuilder()
                                    .setOperationId(id.toString())
                                    .setCommandHash(hash)
                                    .setKind(
                                            IdentityOperationKind.valueOf(
                                                    "IDENTITY_OPERATION_KIND_" + rs.getString(3)))
                                    .setPhase(
                                            IdentityOperationPhase
                                                    .IDENTITY_OPERATION_PHASE_REJECTED)
                                    .setOperationRevision(1)
                                    .setErrorCode("PREPARE_REJECTED_" + rs.getString(4))
                                    .build();
                        },
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    void requireActor(UUID id, IdentityOperationActor actor) {
        Boolean matches =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM auth_identity_operation_rejections WHERE"
                                + " operation_id=? AND actor_id=? AND actor_session_id=? AND"
                                + " actor_session_version=? AND actor_roles_version=?)",
                        Boolean.class,
                        id,
                        actor.getUserId(),
                        UUID.fromString(actor.getSessionId()),
                        actor.getSessionVersion(),
                        actor.getRolesVersion());
        if (!Boolean.TRUE.equals(matches))
            throw error(Status.ALREADY_EXISTS, "OPERATION_ID_CONFLICT");
    }

    IdentityOperationResult insert(
            PrepareIdentityOperationRequest command,
            byte[] canonical,
            IdentityOperationActor actor,
            IdentityOperationRejectionReason reason) {
        UUID id = UUID.fromString(command.getOperationId());
        jdbc.update(
                "INSERT INTO"
                    + " auth_identity_operation_rejections(operation_id,command_hash,canonical_command,kind,actor_id,actor_session_id,actor_session_version,actor_roles_version,reason)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                id,
                command.getCommandHash().toByteArray(),
                canonical,
                command.getKind().name().substring("IDENTITY_OPERATION_KIND_".length()),
                actor.getUserId(),
                UUID.fromString(actor.getSessionId()),
                actor.getSessionVersion(),
                actor.getRolesVersion(),
                reason.name().substring("IDENTITY_OPERATION_REJECTION_REASON_".length()));
        return find(id, command.getCommandHash(), canonical);
    }
}
