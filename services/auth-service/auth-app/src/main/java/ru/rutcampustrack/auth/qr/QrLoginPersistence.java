package ru.rutcampustrack.auth.qr;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

/** One REQUIRED transaction for each command: issuer -> challenge -> canonical user/session locks. */
@Repository
public class QrLoginPersistence {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final AuthService auth;
    private final SessionLifecycleService lifecycle;
    private final QrLoginProperties properties;
    private final Clock clock = Clock.systemUTC();

    public QrLoginPersistence(JdbcTemplate jdbc, PlatformTransactionManager manager, AuthService auth,
                               SessionLifecycleService lifecycle, QrLoginProperties properties) {
        this.jdbc = jdbc; this.transaction = new TransactionTemplate(manager);
        this.auth = auth; this.lifecycle = lifecycle; this.properties = properties;
    }

    public Instant issue(UUID issuerId, byte[] issuerHash, boolean initial, UUID challengeId,
                         byte[] approvalHash, String browserLabel) {
        return tx(() -> {
            if (initial) jdbc.update("INSERT INTO qr_login_issuers(id,secret_hash,created_at) VALUES(?,?,?)",
                    issuerId, issuerHash, time(clock.instant()));
            Issuer issuer = lockIssuer(issuerId, issuerHash);
            if (issuer.current() != null) {
                jdbc.update("UPDATE qr_login_challenges SET state='EXPIRED' WHERE id=? AND issuer_id=? AND state<>'EXPIRED'",
                        issuer.current(), issuerId);
            }
            Instant now = clock.instant(), expiry = now.plusSeconds(properties.getTtlSeconds());
            jdbc.update("""
                    INSERT INTO qr_login_challenges(id,issuer_id,purpose,approval_hash,state,browser_label,created_at,expires_at)
                    VALUES(?,?,'LOGIN',?,'PENDING',?,?,?)
                    """, challengeId, issuerId, approvalHash, browserLabel, time(now), time(expiry));
            jdbc.update("UPDATE qr_login_issuers SET current_challenge_id=? WHERE id=?", challengeId, issuerId);
            return expiry;
        });
    }

    public QrLoginStatusResponse status(QrLoginProofRequest proof) {
        return tx(() -> status(browser(proof)));
    }

    /** At most one configured batch, owners locked before children exactly as in issue/decision/exchange. */
    public int cleanup(Instant cutoff) {
        return tx(() -> {
            var owners=jdbc.query("""
                    SELECT issuer.id FROM qr_login_issuers issuer
                    WHERE EXISTS (SELECT 1 FROM qr_login_challenges challenge WHERE challenge.issuer_id=issuer.id
                        AND GREATEST(challenge.expires_at,COALESCE(challenge.replay_until,challenge.expires_at))<?)
                    ORDER BY issuer.created_at,issuer.id LIMIT ? FOR UPDATE SKIP LOCKED
                    """,(rs,n)->rs.getObject(1,UUID.class),time(cutoff),properties.getCleanupBatchSize());
            int deleted=0;
            for (UUID owner:owners) {
                Issuer issuer=lockIssuer(owner,null);
                var expired=jdbc.query("""
                        SELECT id FROM qr_login_challenges WHERE issuer_id=?
                          AND GREATEST(expires_at,COALESCE(replay_until,expires_at))<?
                        ORDER BY expires_at,id LIMIT ? FOR UPDATE
                        """,(rs,n)->rs.getObject(1,UUID.class),owner,time(cutoff),properties.getCleanupBatchSize()-deleted);
                if (expired.contains(issuer.current())) jdbc.update("UPDATE qr_login_issuers SET current_challenge_id=NULL WHERE id=?",owner);
                for (UUID id:expired) deleted+=jdbc.update("DELETE FROM qr_login_challenges WHERE id=? AND issuer_id=?",id,owner);
                jdbc.update("DELETE FROM qr_login_issuers WHERE id=? AND NOT EXISTS (SELECT 1 FROM qr_login_challenges WHERE issuer_id=?)",owner,owner);
                if (deleted>=properties.getCleanupBatchSize()) break;
            }
            return deleted;
        });
    }

    public QrLoginPreviewResponse preview(QrLoginApprovalRequest proof, SessionPrincipal principal) {
        return tx(() -> {
            Row row = approval(proof);
            requireActive(auth.admit(principal));
            requireUnexpired(row);
            return new QrLoginPreviewResponse(row.id(), QrLoginPurpose.LOGIN, row.state(), row.browserLabel(),
                    row.created(), row.expires(), "Вход откроется в другом браузере. Подтверди, только если ты сам начал этот вход.");
        });
    }

    public QrLoginStatusResponse decide(QrLoginDecisionRequest request, SessionPrincipal principal) {
        return tx(() -> {
            Row row = approval(request.approval());
            SessionSnapshot source = requireActive(auth.admit(principal));
            requireUnexpired(row);
            if (row.state() != QrLoginState.PENDING) throw fail(QrLoginException.Code.ALREADY_DECIDED);
            if (request.decision() == QrLoginDecisionRequest.Decision.REJECT) {
                jdbc.update("UPDATE qr_login_challenges SET state='REJECTED' WHERE id=?", row.id());
                return response(row, QrLoginState.REJECTED);
            }
            jdbc.update("""
                    UPDATE qr_login_challenges SET state='CONFIRMED',confirmed_user_id=?,confirmed_source_sid=?,
                        confirmed_source_sv=?,confirmed_source_rv=?,confirmed_at=? WHERE id=?
                    """, source.userId(), source.sessionId(), source.sessionVersion(), source.rolesVersion(), time(clock.instant()), row.id());
            return response(row, QrLoginState.CONFIRMED);
        });
    }

    /** Session creation and LOGIN audit join this transaction; signing happens after this call commits. */
    public Issuance exchange(QrLoginProofRequest proof) {
        return tx(() -> {
            Row row = browser(proof);
            if (row.state() == QrLoginState.EXPIRED || row.state() == QrLoginState.REJECTED) throw fail(QrLoginException.Code.EXPIRED);
            if (row.state() != QrLoginState.CONFIRMED) { requireUnexpired(row); throw fail(QrLoginException.Code.NOT_READY); }
            if (row.targetSid() == null) requireUnexpired(row);
            else if (!clock.instant().isBefore(row.replayUntil())) throw fail(QrLoginException.Code.EXPIRED);
            SessionSnapshot source = snapshot(row.userId(), row.sourceSid());
            requireActive(source);
            if (source.sessionVersion() != row.sourceSv() || source.rolesVersion() != row.sourceRv())
                throw fail(QrLoginException.Code.SOURCE_DENIED);
            if (row.targetSid() != null) return replay(row);
            requireUnexpired(row); // after waiting for canonical authority locks
            Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            UUID targetSid = UUID.randomUUID(), refreshJti = UUID.randomUUID();
            SessionSnapshot target = auth.createQrSession(row.userId(), targetSid, refreshJti, issuedAt, row.browserLabel());
            requireUnexpired(row); // a delayed insert must not commit a session after the challenge deadline
            Instant accessExpiry=auth.qrAccessExpiry(target,issuedAt);
            String signingFingerprint=auth.qrSigningFingerprint();
            int receipt = jdbc.update("""
                    UPDATE qr_login_challenges SET issued_target_sid=?,issued_at=?,replay_until=?,
                        target_sv=?,target_rv=?,initial_refresh_jti_hash=?,access_expires_at=?,signing_fingerprint=?
                        WHERE id=? AND issued_target_sid IS NULL
                    """, targetSid, time(issuedAt), time(issuedAt.plusSeconds(properties.getReplaySeconds())),
                    target.sessionVersion(), target.rolesVersion(), QrLoginCrypto.refreshHash(refreshJti),time(accessExpiry),signingFingerprint,row.id());
            if (receipt != 1) throw fail(QrLoginException.Code.UNAVAILABLE);
            return new Issuance(target, refreshJti, issuedAt,accessExpiry,signingFingerprint);
        });
    }

    private Issuance replay(Row row) {
        SessionSnapshot target = snapshot(row.userId(), row.targetSid());
        UUID currentJti = jdbc.queryForObject("SELECT current_refresh_jti FROM auth_sessions WHERE sid=? AND user_id=? FOR UPDATE",
                UUID.class, row.targetSid(), row.userId());
        if (!clock.instant().isBefore(row.replayUntil())) throw fail(QrLoginException.Code.EXPIRED);
        if (target.sessionVersion() != row.targetSv() || target.rolesVersion() != row.targetRv()
                || !clock.instant().isBefore(row.accessExpiry()) || !auth.qrSigningFingerprint().equals(row.signingFingerprint())
                || currentJti == null || !QrLoginCrypto.equal(row.refreshHash(), QrLoginCrypto.refreshHash(currentJti)))
            throw fail(QrLoginException.Code.REPLAY_DENIED);
        return new Issuance(target, currentJti, row.issuedAt(),row.accessExpiry(),row.signingFingerprint());
    }

    private SessionSnapshot snapshot(long user, UUID sid) {
        SessionStatePort.SnapshotResult result = lifecycle.snapshot(new SessionLifecycleService.SnapshotRequest(user, sid, clock.instant()));
        if (result != null && result.failureCode() == SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE)
            throw fail(QrLoginException.Code.UNAVAILABLE);
        if (result == null || !result.succeeded() || result.snapshot() == null) throw fail(QrLoginException.Code.SOURCE_DENIED);
        return result.snapshot();
    }
    private SessionSnapshot requireActive(SessionSnapshot source) {
        if (!source.isLiveAt(clock.instant()) || source.activeRole() == null
                || source.activeRole().status() != RoleStatus.ACTIVE || !source.activeRole().isSelectable())
            throw fail(QrLoginException.Code.SOURCE_DENIED);
        return source;
    }

    private Row browser(QrLoginProofRequest proof) {
        purpose(proof.purpose());
        Issuer issuer = lockIssuer(proof.issuerId(), QrLoginCrypto.issuerHash(proof.issuerSecret()));
        if (!proof.challengeId().equals(issuer.current())) throw fail(QrLoginException.Code.NOT_FOUND);
        return lockRow(proof.challengeId(), issuer.id());
    }
    private Row approval(QrLoginApprovalRequest proof) {
        purpose(proof.purpose());
        var ids = jdbc.query("SELECT issuer_id FROM qr_login_challenges WHERE id=?", (rs, n) -> rs.getObject(1, UUID.class), proof.challengeId());
        if (ids.size() != 1) throw fail(QrLoginException.Code.NOT_FOUND);
        Issuer issuer = lockIssuer(ids.getFirst(), null);
        Row row = lockRow(proof.challengeId(), issuer.id());
        if (!row.id().equals(issuer.current()) || !QrLoginCrypto.equal(row.approvalHash(),
                QrLoginCrypto.approvalHash(proof.challengeId(), proof.approvalToken()))) throw fail(QrLoginException.Code.NOT_FOUND);
        return row;
    }
    private Issuer lockIssuer(UUID id, byte[] hash) {
        var issuers = jdbc.query("SELECT id,secret_hash,current_challenge_id FROM qr_login_issuers WHERE id=? FOR UPDATE",
                (rs,n) -> new Issuer(rs.getObject(1,UUID.class),rs.getBytes(2),rs.getObject(3,UUID.class)), id);
        if (issuers.size()!=1 || (hash!=null && !QrLoginCrypto.equal(issuers.getFirst().hash(), hash))) throw fail(QrLoginException.Code.NOT_FOUND);
        return issuers.getFirst();
    }
    private Row lockRow(UUID id, UUID issuer) {
        var rows = jdbc.query("SELECT * FROM qr_login_challenges WHERE id=? AND issuer_id=? AND purpose='LOGIN' FOR UPDATE",
                this::row, id, issuer);
        if (rows.size()!=1) throw fail(QrLoginException.Code.NOT_FOUND);
        return rows.getFirst();
    }
    private QrLoginStatusResponse status(Row row) {
        Instant deadline = row.targetSid() == null ? row.expires() : row.replayUntil();
        if (row.state()!=QrLoginState.EXPIRED && !clock.instant().isBefore(deadline)) {
            jdbc.update("UPDATE qr_login_challenges SET state='EXPIRED' WHERE id=?", row.id());
            return response(row, QrLoginState.EXPIRED);
        }
        return response(row, row.state());
    }
    private QrLoginStatusResponse response(Row row, QrLoginState state) {
        long remaining = Math.max(0, (row.expires().toEpochMilli()-clock.millis()+999)/1000);
        return new QrLoginStatusResponse(row.id(), state, row.expires(), remaining, properties.getPollAfterSeconds());
    }
    private void requireUnexpired(Row row) {
        if (!clock.instant().isBefore(row.expires()) || row.state()==QrLoginState.EXPIRED) throw fail(QrLoginException.Code.EXPIRED);
    }
    private static void purpose(QrLoginPurpose purpose) { if (purpose!=QrLoginPurpose.LOGIN) throw fail(QrLoginException.Code.NOT_FOUND); }
    private <T> T tx(Supplier<T> action) {
        try { return transaction.execute(status -> action.get()); }
        catch (QrLoginException | AuthSessionException denied) { throw denied; }
        catch (RuntimeException unavailable) { throw fail(QrLoginException.Code.UNAVAILABLE); }
    }
    private static QrLoginException fail(QrLoginException.Code code) { return new QrLoginException(code); }
    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp time=rs.getTimestamp(column); return time==null ? null : time.toInstant();
    }
    private Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getObject("id",UUID.class),rs.getBytes("approval_hash"),QrLoginState.valueOf(rs.getString("state")),
                rs.getString("browser_label"),instant(rs,"created_at"),instant(rs,"expires_at"),
                rs.getObject("confirmed_user_id",Long.class),rs.getObject("confirmed_source_sid",UUID.class),
                rs.getObject("confirmed_source_sv",Long.class),rs.getObject("confirmed_source_rv",Long.class),
                rs.getObject("issued_target_sid",UUID.class),instant(rs,"issued_at"),instant(rs,"replay_until"),
                rs.getObject("target_sv",Long.class),rs.getObject("target_rv",Long.class),rs.getBytes("initial_refresh_jti_hash"),
                instant(rs,"access_expires_at"),rs.getString("signing_fingerprint"));
    }
    private record Issuer(UUID id, byte[] hash, UUID current) {}
    private record Row(UUID id, byte[] approvalHash, QrLoginState state, String browserLabel, Instant created, Instant expires,
                       Long userId, UUID sourceSid, Long sourceSv, Long sourceRv, UUID targetSid, Instant issuedAt,
                       Instant replayUntil, Long targetSv, Long targetRv, byte[] refreshHash,Instant accessExpiry,String signingFingerprint) {}
    public record Issuance(SessionSnapshot snapshot, UUID refreshJti, Instant issuedAt,Instant accessExpiry,String signingFingerprint) {
        @Override public String toString() { return "QrIssuance[targetSid="+snapshot.sessionId()+", refreshJti=<redacted>, issuedAt="+issuedAt+']'; }
    }
}
