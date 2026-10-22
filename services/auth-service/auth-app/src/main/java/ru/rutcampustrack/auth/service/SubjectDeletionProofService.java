package ru.rutcampustrack.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.dto.SubjectDeletionProofModels.*;
import ru.rutcampustrack.auth.event.OtpRequestedEvent;
import ru.rutcampustrack.auth.event.OtpVerifiedEvent;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Password and bot code authorize only one exact deletion, never a login or reset. */
@Service
public class SubjectDeletionProofService
        extends ru.rutcampustrack.shared.events.AbstractEventPublisher {
    public static final String PURPOSE = "subject_delete";
    private final JdbcTemplate jdbc;
    private final JwtService jwt;
    private final AuthService auth;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;
    private final BcryptConcurrencyGuard bcrypt;
    private final LoginRateLimiter limiter;
    private final OtpProperties otp;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SubjectDeletionProofService(
            JdbcTemplate jdbc,
            JwtService jwt,
            AuthService auth,
            org.springframework.security.crypto.password.PasswordEncoder passwords,
            BcryptConcurrencyGuard bcrypt,
            LoginRateLimiter limiter,
            OtpProperties otp,
            ObjectMapper mapper) {
        this(jdbc, jwt, auth, passwords, bcrypt, limiter, otp, mapper, Clock.systemUTC());
    }

    public SubjectDeletionProofService(
            JdbcTemplate jdbc,
            JwtService jwt,
            AuthService auth,
            org.springframework.security.crypto.password.PasswordEncoder passwords,
            BcryptConcurrencyGuard bcrypt,
            LoginRateLimiter limiter,
            OtpProperties otp,
            ObjectMapper mapper,
            Clock clock) {
        super("auth-service");
        this.jdbc = jdbc;
        this.jwt = jwt;
        this.auth = auth;
        this.passwords = passwords;
        this.bcrypt = bcrypt;
        this.limiter = limiter;
        this.otp = otp;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Challenge challenge(ChallengeRequest request) {
        var actor = authorize(request.internalToken(), request.binding());
        String bucket = "__subject_delete_confirmation__:" + actor.userId();
        limiter.checkBlocked("internal-subject-deletion", bucket);
        if (!bcrypt.execute(() -> passwords.matches(request.password(), actor.passwordHash()))) {
            limiter.recordFailure("internal-subject-deletion", bucket);
            throw denied();
        }
        limiter.clearFailures("internal-subject-deletion", bucket);
        Instant now = clock.instant();
        Long recent =
                jdbc.queryForObject(
                        "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=? AND"
                                + " created_at>?",
                        Long.class,
                        actor.userId(),
                        Timestamp.from(now.minusSeconds(otp.attemptsWindowSeconds())));
        Instant last =
                jdbc.query(
                        "SELECT max(created_at) FROM subject_deletion_proofs WHERE actor_id=?",
                        rs ->
                                rs.next() && rs.getTimestamp(1) != null
                                        ? rs.getTimestamp(1).toInstant()
                                        : null,
                        actor.userId());
        if (otp.codeLength() != 6
                || otp.ttlSeconds() <= 0
                || otp.maxAttempts() <= 0
                || otp.maxAttempts() > 3) throw unavailable();
        if (recent != null && recent >= otp.maxAttempts()
                || last != null && last.plusSeconds(otp.resendCooldownSeconds()).isAfter(now))
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Повторите подтверждение позже");
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String code = String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000));
        Instant expiry = now.plusSeconds(otp.ttlSeconds());
        Binding b = request.binding();
        jdbc.update(
                """
INSERT INTO subject_deletion_proofs(challenge_hash,operation_id,subject_id,semester_id,group_id,
    actor_id,session_id,session_version,roles_version,credentials_version,binding_version,
    telegram_id,preview_digest,code_hash,created_at,expires_at)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
                hash(challenge),
                b.operationId(),
                b.subjectId(),
                b.semesterId(),
                b.groupId(),
                actor.userId(),
                actor.sid(),
                actor.sv(),
                actor.rv(),
                actor.credentials(),
                actor.binding(),
                actor.telegram(),
                b.previewDigest(),
                hash(challenge + ":" + code),
                Timestamp.from(now),
                Timestamp.from(expiry));
        append(
                new OtpRequestedEvent(
                        this,
                        actor.telegram(),
                        code,
                        otp.ttlSeconds(),
                        PURPOSE,
                        challenge,
                        otp.maxAttempts()));
        return new Challenge(challenge, expiry, otp.maxAttempts());
    }

    /** Invalid codes return normally so their durable attempt increment is not rolled back. */
    @Transactional
    public Verification verify(VerifyRequest request) {
        Actor actor = authorize(request.internalToken(), request.binding());
        var rows =
                jdbc.query(
                        "SELECT * FROM subject_deletion_proofs WHERE challenge_hash=? FOR UPDATE",
                        (rs, i) ->
                                new Proof(
                                        rs.getObject("operation_id", UUID.class),
                                        rs.getLong("subject_id"),
                                        rs.getLong("semester_id"),
                                        rs.getLong("group_id"),
                                        rs.getLong("actor_id"),
                                        rs.getObject("session_id", UUID.class),
                                        rs.getLong("session_version"),
                                        rs.getLong("roles_version"),
                                        rs.getLong("credentials_version"),
                                        rs.getLong("binding_version"),
                                        rs.getLong("telegram_id"),
                                        rs.getString("preview_digest"),
                                        rs.getString("code_hash"),
                                        rs.getInt("attempts"),
                                        rs.getTimestamp("expires_at").toInstant(),
                                        rs.getObject("receipt_id", UUID.class)),
                        hash(request.challengeId()));
        if (rows.size() != 1) return new Verification(false, null);
        Proof p = rows.getFirst();
        Binding b = request.binding();
        if (!p.expires().isAfter(clock.instant())
                || p.attempts() >= otp.maxAttempts()
                || !p.operation().equals(b.operationId())
                || p.subject() != b.subjectId()
                || p.semester() != b.semesterId()
                || p.group() != b.groupId()
                || p.actor() != actor.userId()
                || !p.sid().equals(actor.sid())
                || p.sv() != actor.sv()
                || p.rv() != actor.rv()
                || p.credentials() != actor.credentials()
                || p.binding() != actor.binding()
                || p.telegram() != actor.telegram()
                || !p.digest().equals(b.previewDigest())) return new Verification(false, null);
        if (!MessageDigest.isEqual(
                p.codeHash().getBytes(StandardCharsets.US_ASCII),
                hash(request.challengeId() + ":" + request.code())
                        .getBytes(StandardCharsets.US_ASCII))) {
            // Consumed proof retries cannot exhaust or mutate the already accepted command.
            if (p.receipt() == null)
                jdbc.update(
                        "UPDATE subject_deletion_proofs SET attempts=attempts+1 WHERE"
                                + " challenge_hash=?",
                        hash(request.challengeId()));
            return new Verification(false, null);
        }
        if (p.receipt() != null) return new Verification(true, p.receipt());
        UUID receipt = UUID.randomUUID();
        jdbc.update(
                "UPDATE subject_deletion_proofs SET consumed_at=?,receipt_id=? WHERE"
                        + " challenge_hash=? AND receipt_id IS NULL",
                Timestamp.from(clock.instant()),
                receipt,
                hash(request.challengeId()));
        append(new OtpVerifiedEvent(this, actor.telegram(), PURPOSE, request.challengeId()));
        return new Verification(true, receipt);
    }

    /** Security receipt only: Academic owns subject/group eligibility and local admission. */
    @Transactional
    public ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt receipt(
            String token, Binding binding, UUID receipt) {
        return receipt(authorize(token, binding), binding, receipt);
    }

    private ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt receipt(
            Actor actor, Binding binding, UUID receipt) {
        var expiry =
                jdbc.query(
                        """
SELECT expires_at FROM subject_deletion_proofs
WHERE receipt_id=? AND consumed_at IS NOT NULL AND operation_id=? AND subject_id=? AND semester_id=? AND group_id=?
  AND preview_digest=? AND actor_id=? AND session_id=? AND session_version=? AND roles_version=?
  AND credentials_version=? AND binding_version=? AND telegram_id=? AND expires_at>?
""",
                        (rs, n) -> rs.getTimestamp(1).toInstant(),
                        receipt,
                        binding.operationId(),
                        binding.subjectId(),
                        binding.semesterId(),
                        binding.groupId(),
                        binding.previewDigest(),
                        actor.userId(),
                        actor.sid(),
                        actor.sv(),
                        actor.rv(),
                        actor.credentials(),
                        actor.binding(),
                        actor.telegram(),
                        Timestamp.from(clock.instant()));
        if (expiry.size() != 1) throw denied();
        return ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt.newBuilder()
                .setReceiptId(receipt.toString())
                .setBinding(
                        ru.rutcampustrack.authsecurity.grpc.SubjectDeletionBinding.newBuilder()
                                .setSubjectId(binding.subjectId())
                                .setSemesterId(binding.semesterId())
                                .setGroupId(binding.groupId())
                                .setOperationId(binding.operationId().toString())
                                .setPreviewDigest(binding.previewDigest()))
                .setActor(
                        ru.rutcampustrack.authsecurity.grpc.StoredSessionIdentity.newBuilder()
                                .setUserId(actor.userId())
                                .setSessionId(actor.sid().toString())
                                .setSessionVersion(actor.sv())
                                .setRolesVersion(actor.rv())
                                .setRole("HEADMAN")
                                .setStatus("ACTIVE")
                                .setGroupId(binding.groupId())
                                .setIsHeadman(true))
                .setCredentialsVersion(actor.credentials())
                .setTelegramBindingVersion(actor.binding())
                .setTelegramId(actor.telegram())
                .setExpiresAt(expiry.getFirst().toString())
                .build();
    }

    /** Fixed accepted proof continuation; never creates or accepts a synthetic user bearer. */
    @Transactional
    public ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt storedReceipt(
            Binding binding, UUID receipt) {
        validateBinding(binding);
        if (receipt == null) throw denied();
        // Read immutable proof identity first, then the canonical identity -> session locks.
        // Final exact proof check below compares current CV/BV/TG and expiry as well.
        var owners =
                jdbc.query(
                        """
SELECT actor_id,session_id,session_version,roles_version FROM subject_deletion_proofs
 WHERE receipt_id=? AND consumed_at IS NOT NULL AND operation_id=? AND subject_id=?
 AND semester_id=? AND group_id=? AND preview_digest=? AND expires_at>?
""",
                        (rs, n) ->
                                new SessionPrincipal(
                                        rs.getLong(1),
                                        rs.getObject(2, UUID.class),
                                        rs.getLong(3),
                                        rs.getLong(4),
                                        AuthRole.HEADMAN,
                                        RoleStatus.ACTIVE,
                                        binding.groupId(),
                                        true,
                                        false),
                        receipt,
                        binding.operationId(),
                        binding.subjectId(),
                        binding.semesterId(),
                        binding.groupId(),
                        binding.previewDigest(),
                        Timestamp.from(clock.instant()));
        if (owners.size() != 1) throw denied();
        return receipt(authorize(owners.getFirst(), binding), binding, receipt);
    }

    private void append(ru.rutcampustrack.auth.event.DomainEvent event) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()) throw unavailable();
        event.setOccurredAt(clock.instant().atOffset(java.time.ZoneOffset.UTC));
        fillDefaults(event);
        try {
            jdbc.update(
                    "INSERT INTO auth_outbox(event_type,payload) VALUES (?,CAST(? AS JSONB))",
                    event.getEventType(),
                    mapper.writeValueAsString(event));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw unavailable();
        }
    }

    private Actor authorize(String token, Binding b) {
        return authorize(parse(token), b);
    }

    private static void validateBinding(Binding b) {
        if (b == null
                || b.subjectId() <= 0
                || b.semesterId() <= 0
                || b.groupId() <= 0
                || b.operationId() == null
                || b.previewDigest() == null
                || !b.previewDigest().matches("[0-9a-f]{64}")) throw denied();
    }

    private Actor authorize(SessionPrincipal principal, Binding b) {
        validateBinding(b);
        if (principal.selectedRole() != AuthRole.HEADMAN
                || principal.selectedStatus() != RoleStatus.ACTIVE
                || !principal.headman()
                || principal.readOnly()
                || !Objects.equals(principal.groupId(), b.groupId())) throw denied();
        var users =
                jdbc.query(
                        "SELECT"
                            + " password_hash,credentials_version,telegram_binding_version,telegram_id"
                            + " FROM auth_identities WHERE id=? AND account_state='ACTIVE' AND"
                            + " fence_operation_id IS NULL FOR UPDATE",
                        (rs, i) ->
                                new User(
                                        rs.getString(1),
                                        rs.getLong(2),
                                        rs.getLong(3),
                                        rs.getObject(4, Long.class)),
                        principal.userId());
        if (users.size() != 1) throw denied();
        jdbc.query(
                "SELECT sid FROM auth_sessions WHERE sid=? AND user_id=? FOR UPDATE",
                (rs, i) -> rs.getObject(1),
                principal.sessionId(),
                principal.userId());
        var current = auth.admit(principal);
        if (current.activeRole() == null
                || current.activeRole().role() != AuthRole.HEADMAN
                || current.activeRole().status() != RoleStatus.ACTIVE
                || current.isReadOnly()
                || !Objects.equals(current.activeRole().groupId(), b.groupId())) throw denied();
        List<Long> headmen =
                jdbc.query(
                        "SELECT user_id FROM user_role_grants WHERE role='headman' AND"
                                + " status='active' AND group_id=? ORDER BY user_id",
                        (rs, i) -> rs.getLong(1),
                        b.groupId());
        if (headmen.size() != 1 || headmen.getFirst() != principal.userId()) throw denied();
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM user_role_grants WHERE user_id=? AND"
                                + " role='student' AND status='active' AND group_id=?)",
                        Boolean.class,
                        principal.userId(),
                        b.groupId()))) throw denied();
        User user = users.getFirst();
        if (user.telegram() == null || user.telegram() <= 0 || user.passwordHash() == null)
            throw denied();
        return new Actor(
                principal.userId(),
                principal.sessionId(),
                current.sessionVersion(),
                current.rolesVersion(),
                user.credentials(),
                user.binding(),
                user.telegram(),
                user.passwordHash());
    }

    private SessionPrincipal parse(String token) {
        try {
            Claims c = jwt.parseInternalToken(token).getPayload();
            return new SessionPrincipal(
                    Long.parseLong(c.getSubject()),
                    UUID.fromString(c.get("sid", String.class)),
                    Long.parseLong(c.get("sv", String.class)),
                    Long.parseLong(c.get("rv", String.class)),
                    AuthRole.valueOf(c.get("role", String.class)),
                    RoleStatus.valueOf(c.get("status", String.class)),
                    Long.parseLong(c.get("group_id", String.class)),
                    c.get("is_headman", Boolean.class),
                    c.get("readOnly", Boolean.class));
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Текущая сессия недействительна");
        }
    }

    private static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Подтверждение удаления недоступно");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Подтверждение удаления недоступно");
    }

    private record User(String passwordHash, long credentials, long binding, Long telegram) {
        @Override
        public String toString() {
            return "SubjectDeletionUser[redacted]";
        }
    }

    private record Actor(
            long userId,
            UUID sid,
            long sv,
            long rv,
            long credentials,
            long binding,
            long telegram,
            String passwordHash) {
        @Override
        public String toString() {
            return "SubjectDeletionActor[redacted]";
        }
    }

    private record Proof(
            UUID operation,
            long subject,
            long semester,
            long group,
            long actor,
            UUID sid,
            long sv,
            long rv,
            long credentials,
            long binding,
            long telegram,
            String digest,
            String codeHash,
            int attempts,
            Instant expires,
            UUID receipt) {
        @Override
        public String toString() {
            return "SubjectDeletionProof[redacted]";
        }
    }
}
