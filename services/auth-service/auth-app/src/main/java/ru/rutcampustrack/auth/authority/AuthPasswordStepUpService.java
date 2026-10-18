package ru.rutcampustrack.auth.authority;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.SemesterDeletionConfirmationService;
import ru.rutcampustrack.authsecurity.grpc.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/** Immutable password proof for one exact operation, guarded by current Auth generations. */
@Service
public class AuthPasswordStepUpService {
    private final JdbcTemplate jdbc;
    private final AuthInternalActorReader actors;
    private final AuthAdminJdbcAuthority admins;
    private final SemesterDeletionConfirmationService confirmation;

    public AuthPasswordStepUpService(
            JdbcTemplate jdbc,
            AuthInternalActorReader actors,
            SemesterDeletionConfirmationService confirmation) {
        this.jdbc = jdbc;
        this.actors = actors;
        this.admins = new AuthAdminJdbcAuthority(jdbc);
        this.confirmation = confirmation;
    }

    @Transactional
    public PasswordStepUpReceipt confirm(PasswordStepUpRequest request) {
        if (!request.hasBinding()
                || !request.getUnknownFields().asMap().isEmpty()
                || request.getPassword().isBlank()
                || request.getPassword().length() > 256) throw invalid();
        var b = request.getBinding();
        validate(b);
        SessionPrincipal actor = actors.read();
        lock(actor, b);
        var old =
                jdbc.queryForList(
                        "SELECT receipt_id FROM auth_password_step_up_receipts WHERE operation_id=?"
                                + " AND purpose=? AND actor_id=? AND session_id=?",
                        UUID.class,
                        uuid(b.getOperationId()),
                        purpose(b),
                        actor.userId(),
                        actor.sessionId());
        if (!old.isEmpty()) return receipt(b, old.getFirst(), actor);
        String token = AuthActorMetadataInterceptor.ACTOR_TOKEN.get();
        switch (b.getPurpose()) {
            case PASSWORD_STEP_UP_PURPOSE_USER_ARCHIVE ->
                    confirmation.confirmUserArchive(
                            new ConfirmUserArchiveRequest(
                                    token,
                                    request.getPassword(),
                                    ConfirmUserArchiveRequest.Purpose.USER_ARCHIVE,
                                    b.getTargetId(),
                                    uuid(b.getOperationId()),
                                    b.getPreviewDigest()));
            case PASSWORD_STEP_UP_PURPOSE_SEMESTER_DELETE ->
                    confirmation.confirm(
                            new ConfirmSemesterDeletionRequest(
                                    token,
                                    request.getPassword(),
                                    b.getTargetId(),
                                    uuid(b.getOperationId()),
                                    b.getPreviewDigest()));
            case PASSWORD_STEP_UP_PURPOSE_MAP_FLOOR_DELETE,
                    PASSWORD_STEP_UP_PURPOSE_MAP_BUILDING_DELETE ->
                    confirmation.confirmMapDeletion(
                            new ConfirmMapDeletionRequest(
                                    token,
                                    request.getPassword(),
                                    b.getPurpose()
                                                    == PasswordStepUpPurpose
                                                            .PASSWORD_STEP_UP_PURPOSE_MAP_FLOOR_DELETE
                                            ? ConfirmMapDeletionRequest.TargetType.FLOOR
                                            : ConfirmMapDeletionRequest.TargetType.BUILDING,
                                    b.getTargetId(),
                                    uuid(b.getOperationId()),
                                    b.getPreviewDigest()));
            default -> throw invalid();
        }
        var generations =
                jdbc.queryForMap(
                        "SELECT credentials_version,telegram_binding_version FROM auth_identities"
                                + " WHERE id=?",
                        actor.userId());
        var profile = admins.current(AuthInternalActorReader.claims(actor));
        UUID receipt = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
INSERT INTO auth_password_step_up_receipts(receipt_id,purpose,target_id,operation_id,preview_digest,
 actor_id,session_id,session_version,roles_version,credentials_version,binding_version,admin_profile_version,created_at,expires_at)
VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
                receipt,
                purpose(b),
                b.getTargetId(),
                uuid(b.getOperationId()),
                b.getPreviewDigest(),
                actor.userId(),
                actor.sessionId(),
                actor.sessionVersion(),
                actor.rolesVersion(),
                generations.get("credentials_version"),
                generations.get("telegram_binding_version"),
                profile.version(),
                Timestamp.from(now),
                Timestamp.from(now.plusSeconds(60)));
        return receipt(b, receipt, actor);
    }

    @Transactional
    public PasswordStepUpReceipt check(PasswordStepUpReceiptRequest request) {
        if (!request.hasBinding() || !request.getUnknownFields().asMap().isEmpty()) throw invalid();
        validate(request.getBinding());
        SessionPrincipal actor = actors.read();
        lock(actor, request.getBinding());
        return receipt(request.getBinding(), uuid(request.getReceiptId()), actor);
    }

    private void lock(SessionPrincipal actor, PasswordStepUpBinding binding) {
        admins.lockGlobal();
        admins.lockUsers(
                actor.userId(),
                binding.getPurpose() == PasswordStepUpPurpose.PASSWORD_STEP_UP_PURPOSE_USER_ARCHIVE
                        ? binding.getTargetId()
                        : null);
        var profile = admins.current(AuthInternalActorReader.claims(actor));
        var required =
                switch (binding.getPurpose()) {
                    case PASSWORD_STEP_UP_PURPOSE_USER_ARCHIVE -> new String[] {"USERS_ARCHIVE"};
                    case PASSWORD_STEP_UP_PURPOSE_SEMESTER_DELETE ->
                            new String[] {"SEMESTERS_MANAGE", "DATA_DELETE"};
                    default -> new String[] {"CAMPUS_MAP_MANAGE", "DATA_DELETE"};
                };
        for (String p : required)
            if (!profile.allows(p)) throw Status.PERMISSION_DENIED.asRuntimeException();
    }

    private PasswordStepUpReceipt receipt(
            PasswordStepUpBinding b, UUID receipt, SessionPrincipal actor) {
        var rows =
                jdbc.query(
                        """
SELECT r.credentials_version,r.binding_version,r.expires_at
FROM auth_password_step_up_receipts r JOIN auth_identities u ON u.id=r.actor_id
JOIN admin_access_profiles p ON p.user_id=u.id
WHERE r.receipt_id=? AND r.purpose=? AND r.target_id=? AND r.operation_id=? AND r.preview_digest=?
 AND r.actor_id=? AND r.session_id=? AND r.session_version=? AND r.roles_version=?
 AND r.credentials_version=u.credentials_version AND r.binding_version=u.telegram_binding_version
 AND r.admin_profile_version=p.version AND r.expires_at>NOW()
""",
                        (rs, n) ->
                                PasswordStepUpReceipt.newBuilder()
                                        .setReceiptId(receipt.toString())
                                        .setBinding(b)
                                        .setActor(
                                                StoredSessionIdentity.newBuilder()
                                                        .setUserId(actor.userId())
                                                        .setSessionId(actor.sessionId().toString())
                                                        .setSessionVersion(actor.sessionVersion())
                                                        .setRolesVersion(actor.rolesVersion())
                                                        .setRole("ADMIN")
                                                        .setStatus("ACTIVE"))
                                        .setCredentialsVersion(rs.getLong(1))
                                        .setTelegramBindingVersion(rs.getLong(2))
                                        .setExpiresAt(rs.getTimestamp(3).toInstant().toString())
                                        .build(),
                        receipt,
                        purpose(b),
                        b.getTargetId(),
                        uuid(b.getOperationId()),
                        b.getPreviewDigest(),
                        actor.userId(),
                        actor.sessionId(),
                        actor.sessionVersion(),
                        actor.rolesVersion());
        if (rows.size() != 1)
            throw Status.FAILED_PRECONDITION
                    .withDescription("Step-up receipt stale")
                    .asRuntimeException();
        return rows.getFirst();
    }

    private static void validate(PasswordStepUpBinding b) {
        if (!b.getUnknownFields().asMap().isEmpty()
                || b.getTargetId() <= 0
                || !b.getPreviewDigest().matches("[0-9a-f]{64}")
                || b.getPurpose() == PasswordStepUpPurpose.UNRECOGNIZED
                || b.getPurpose() == PasswordStepUpPurpose.PASSWORD_STEP_UP_PURPOSE_UNSPECIFIED)
            throw invalid();
        uuid(b.getOperationId());
    }

    private static String purpose(PasswordStepUpBinding b) {
        return b.getPurpose().name().substring("PASSWORD_STEP_UP_PURPOSE_".length());
    }

    private static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw invalid();
            return id;
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    private static io.grpc.StatusRuntimeException invalid() {
        return Status.INVALID_ARGUMENT
                .withDescription("Invalid step-up binding")
                .asRuntimeException();
    }
}
