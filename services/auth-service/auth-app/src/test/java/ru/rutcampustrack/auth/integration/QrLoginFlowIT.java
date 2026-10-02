package ru.rutcampustrack.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.qr.QrLoginCrypto;
import ru.rutcampustrack.auth.qr.QrLoginPersistence;
import ru.rutcampustrack.auth.qr.QrLoginProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real HTTP security/controller + canonical committed session/receipt/audit transactions + PostgreSQL/Redis. */
@TestPropertySource(properties={"auth.qr-login.ttl-seconds=5","auth.qr-login.cleanup-enabled=false"})
@Timeout(60)
class QrLoginFlowIT extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH="$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired StringRedisTemplate redis;
    @Autowired QrLoginPersistence persistence;
    @Autowired QrLoginProperties properties;
    private long user;
    private Session source;

    @BeforeEach void ownUser() throws Exception {
        String login="qr-"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        user=jdbc.queryForObject("""
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,initial_password,password_changed)
                VALUES(?,?,'Qr','Boundary','student'::user_role,'active'::account_status,false,'password',false) RETURNING id
                """,Long.class,login,PASSWORD_HASH);
        jdbc.update("INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at) VALUES(?,'student','active',NOW(),NOW()),(?,'teacher','active',NOW(),NOW())",user,user);
        ResponseEntity<TokenResponse> loginResponse=http.postForEntity("/auth/login",new LoginRequest(login,"password"),TokenResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        source=session(loginResponse.getBody());
    }

    @Test void browserBoundSingleWinnerExchangeAndLostResponseReplaySurviveQrExpiry() throws Exception {
        Challenge challenge=issue(null);
        assertThat(challenge.issue().ttl()).isEqualTo(5);
        assertThat(challenge.issue().pollAfterSeconds()).isEqualTo(1);
        assertThat(challenge.issue().qrPayload().contains(challenge.proof().issuerSecret())).isFalse();
        assertThat(challenge.issue().qrPayload().contains("issuerId")).isFalse();
        assertThat(challenge.issue().qrPayload().contains("accessToken")).isFalse();
        assertThat(post("/auth/qr/exchange",challenge.proof(),null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/auth/qr/preview",challenge.approval(),null).getStatusCode().is4xxClientError()).isTrue();
        assertThat(post("/auth/qr/decision",decision(challenge,QrLoginDecisionRequest.Decision.CONFIRM),null).getStatusCode().is4xxClientError()).isTrue();
        var preview=post("/auth/qr/preview",challenge.approval(),source);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(preview.getBody()).path("warning").asText()).contains("другом браузере");
        assertThat(qrSessions()).isZero();
        assertThat(json.readTree(post("/auth/qr/status",challenge.proof(),null).getBody()).path("status").asText()).isEqualTo("pending");
        QrLoginProofRequest wrongBrowser=new QrLoginProofRequest(QrLoginPurpose.LOGIN,challenge.proof().issuerId(),QrLoginCrypto.secret(),challenge.proof().challengeId());
        assertThat(post("/auth/qr/status",wrongBrowser,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        QrLoginProofRequest approvalOnly=new QrLoginProofRequest(QrLoginPurpose.LOGIN,challenge.proof().issuerId(),challenge.approval().approvalToken(),challenge.proof().challengeId());
        assertThat(post("/auth/qr/exchange",approvalOnly,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/auth/qr/status",Map.of("purpose","DEVICE_BINDING","issuerId",challenge.proof().issuerId(),
                "issuerSecret",challenge.proof().issuerSecret(),"challengeId",challenge.proof().challengeId()),null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        confirm(challenge);
        var executor=Executors.newFixedThreadPool(2);
        ResponseEntity<String> first,second;
        try {
            var start=new CyclicBarrier(2);
            Future<ResponseEntity<String>> a=executor.submit(()->{start.await(10,TimeUnit.SECONDS);return post("/auth/qr/exchange",challenge.proof(),null);});
            Future<ResponseEntity<String>> b=executor.submit(()->{start.await(10,TimeUnit.SECONDS);return post("/auth/qr/exchange",challenge.proof(),null);});
            first=a.get(15,TimeUnit.SECONDS); second=b.get(15,TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse original=json.readValue(first.getBody(),TokenResponse.class);
        TokenResponse duplicate=json.readValue(second.getBody(),TokenResponse.class);
        assertSamePair(original,duplicate);
        assertThat(qrSessions()).isEqualTo(1);
        assertThat(qrLogins()).isEqualTo(1);
        String cookie=first.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie!=null && cookie.contains("HttpOnly") && cookie.contains("Secure")
                && cookie.contains("SameSite=Strict") && cookie.contains("Path=/api/auth")).isTrue();
        Session target=session(original);
        var current=get("/auth/session",target);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(current.getBody()).path("activeRole").asText()).isEqualTo("STUDENT");
        assertThat(json.readTree(current.getBody()).path("roles").findValuesAsText("role")).contains("STUDENT","TEACHER");
        assertThat(jdbc.queryForObject("SELECT password_changed FROM users WHERE id=?",Boolean.class,user)).isFalse();
        assertThat(post("/auth/qr/status",challenge.proof(),null).getBody().contains("accessToken")).isFalse();
        awaitExpiry(challenge);
        var lostReplyRecovery=post("/auth/qr/exchange",challenge.proof(),null);
        assertThat(lostReplyRecovery.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertSamePair(original,json.readValue(lostReplyRecovery.getBody(),TokenResponse.class));
        assertThat(qrSessions()).isEqualTo(1);
        assertThat(qrLogins()).isEqualTo(1);
        var headers=new HttpHeaders(); headers.add(HttpHeaders.COOKIE,"rct_refresh="+original.refreshToken());
        assertThat(http.exchange("/auth/refresh",HttpMethod.POST,new HttpEntity<>(null,headers),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/auth/qr/exchange",challenge.proof(),null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(qrSessions()).isEqualTo(1);
    }

    @Test void rejectedExpiredAndSupersededChallengesCannotIssueAndRedisLimitsAreAuthoritative() throws Exception {
        Challenge old=issue(null), replacement=issue(old);
        assertThat(post("/auth/qr/status",old.proof(),null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/auth/qr/preview",old.approval(),source).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/auth/qr/decision",decision(replacement,QrLoginDecisionRequest.Decision.REJECT),source).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(post("/auth/qr/status",replacement.proof(),null).getBody()).path("status").asText()).isEqualTo("rejected");
        assertThat(post("/auth/qr/exchange",replacement.proof(),null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(post("/auth/qr/decision",decision(replacement,QrLoginDecisionRequest.Decision.CONFIRM),source).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Challenge expired=issue(null);
        awaitExpiry(expired);
        assertThat(json.readTree(post("/auth/qr/status",expired.proof(),null).getBody()).path("status").asText()).isEqualTo("expired");
        assertThat(post("/auth/qr/exchange",expired.proof(),null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(post("/auth/qr/decision",decision(expired,QrLoginDecisionRequest.Decision.CONFIRM),source).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(http.getForEntity("/auth/qr/status",String.class).getStatusCode().is4xxClientError()).isTrue();
        assertThat(qrSessions()).isZero();
        String key="qr_login:limit:status:issuer:"+expired.proof().issuerId();
        redis.opsForValue().set(key,Integer.toString(properties.getStatusesPerIssuerMinute()),java.time.Duration.ofSeconds(60));
        var limited=post("/auth/qr/status",expired.proof(),null);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertNoStore(limited);
        // A wrong Redis type reproduces a real Lua/backend failure; it must not authorize polling or exchange.
        redis.delete(key); redis.opsForList().leftPush(key,"owned-wrong-type"); redis.expire(key,java.time.Duration.ofSeconds(60));
        assertThat(post("/auth/qr/status",expired.proof(),null).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        redis.delete(key);
        var spoofed=new HttpHeaders(); spoofed.setContentType(MediaType.APPLICATION_JSON); spoofed.set("X-Forwarded-For","203.0.113.123");
        var invalid=http.exchange("/auth/qr/status",HttpMethod.POST,new HttpEntity<>(Map.of("issuerSecret","sensitive-invalid-value"),spoofed),String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().contains("sensitive-invalid-value")).isFalse();
        String forwardedKey="qr_login:limit:status:ip:"+HexFormat.of().formatHex(QrLoginCrypto.hash("qr:ip:203.0.113.123"));
        redis.delete(forwardedKey);
        assertThat(http.exchange("/auth/qr/status",HttpMethod.POST,new HttpEntity<>(expired.proof(),spoofed),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(redis.hasKey(forwardedKey)).isFalse(); // even loopback is untrusted by default
        List<String> previousTrusted=properties.getTrustedProxyAddresses();
        try {
            properties.setTrustedProxyAddresses(List.of("127.0.0.1","::1")); // explicit test proxy peers
            assertThat(http.exchange("/auth/qr/status",HttpMethod.POST,new HttpEntity<>(expired.proof(),spoofed),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(redis.opsForValue().get(forwardedKey)).isEqualTo("1");
            spoofed.set("X-Forwarded-For","203.0.113.123, 198.51.100.7");
            assertThat(http.exchange("/auth/qr/status",HttpMethod.POST,new HttpEntity<>(expired.proof(),spoofed),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(redis.opsForValue().get(forwardedKey)).isEqualTo("1"); // multiple/nonliteral forwarding falls back to peer
        } finally { properties.setTrustedProxyAddresses(previousTrusted); redis.delete(forwardedKey); }
        assertThat(qrSessions()).isZero();
    }

    @Test void sourceRevocationAndSourceOrTargetAuthorityDriftFenceExchangeAndRecovery() throws Exception {
        String neutralLogin="qr-neutral-"+UUID.randomUUID().toString().substring(0,12);
        jdbc.update("""
                INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,initial_password,password_changed)
                VALUES(?,?,'Qr','Neutral','student'::user_role,'active'::account_status,false,'password',false)
                """,neutralLogin,PASSWORD_HASH);
        var neutralLoginResponse=http.postForEntity("/auth/login",new LoginRequest(neutralLogin,"password"),TokenResponse.class);
        assertThat(neutralLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Session bootstrap=session(neutralLoginResponse.getBody());
        Challenge bootstrapChallenge=issue(null);
        assertThat(post("/auth/qr/preview",bootstrapChallenge.approval(),bootstrap).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/auth/qr/decision",decision(bootstrapChallenge,QrLoginDecisionRequest.Decision.CONFIRM),bootstrap).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json.readTree(post("/auth/qr/status",bootstrapChallenge.proof(),null).getBody()).path("status").asText()).isEqualTo("pending");
        Challenge revoked=issue(null); confirm(revoked);
        assertThat(post("/auth/logout",null,source).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/auth/qr/exchange",revoked.proof(),null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(qrSessions()).isZero();
        source=loginOwnUser();
        Challenge roleChanged=issue(null); confirm(roleChanged);
        var role=postRole("TEACHER",source);
        assertThat(role.getStatusCode()).isEqualTo(HttpStatus.OK);
        source=new Session(json.readTree(role.getBody()).path("accessToken").asText(),source.refreshToken(),source.sid());
        assertThat(post("/auth/qr/exchange",roleChanged.proof(),null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Challenge targetRevoked=issue(null); confirm(targetRevoked);
        var accepted=post("/auth/qr/exchange",targetRevoked.proof(),null);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        Session target=session(json.readValue(accepted.getBody(),TokenResponse.class));
        assertThat(post("/auth/logout",null,target).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/auth/qr/exchange",targetRevoked.proof(),null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Challenge accountRevoked=issue(null); confirm(accountRevoked);
        jdbc.update("UPDATE user_role_grants SET status='archived' WHERE user_id=?",user);
        assertThat(post("/auth/qr/exchange",accountRevoked.proof(),null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/auth/qr/preview",accountRevoked.approval(),source).getStatusCode().is4xxClientError()).isTrue();
        assertThat(qrSessions()).isEqualTo(1);
        assertThat(qrLogins()).isEqualTo(1);
    }

    @Test void receiptFailureRollsBackSessionAuditAndCleanupOnlyDeletesOwnedExpiredQrMetadata() throws Exception {
        Challenge challenge=issue(null); confirm(challenge);
        jdbc.execute("CREATE FUNCTION qr_it_fail_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"
                +challenge.proof().challengeId()+"'::uuid AND NEW.issued_target_sid IS NOT NULL THEN RAISE EXCEPTION 'owned receipt fault'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER qr_it_receipt_fault BEFORE UPDATE ON qr_login_challenges FOR EACH ROW EXECUTE FUNCTION qr_it_fail_receipt()");
        try {
            assertThat(post("/auth/qr/exchange",challenge.proof(),null).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(qrSessions()).isZero(); assertThat(qrLogins()).isZero();
            assertThat(jdbc.queryForObject("SELECT issued_target_sid IS NULL FROM qr_login_challenges WHERE id=?",Boolean.class,challenge.proof().challengeId())).isTrue();
        } finally {
            jdbc.execute("DROP TRIGGER qr_it_receipt_fault ON qr_login_challenges"); jdbc.execute("DROP FUNCTION qr_it_fail_receipt()");
        }
        var recovered=post("/auth/qr/exchange",challenge.proof(),null);
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(qrSessions()).isEqualTo(1); assertThat(qrLogins()).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("UPDATE qr_login_challenges SET issued_at=issued_at-INTERVAL '1 second' WHERE id=?",challenge.proof().challengeId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        long allSessions=jdbc.queryForObject("SELECT count(*) FROM auth_sessions",Long.class);
        long allAudit=jdbc.queryForObject("SELECT count(*) FROM account_security_events",Long.class);
        UUID oldIssuer=UUID.randomUUID(),oldChallenge=UUID.randomUUID();
        Instant expiredAt=Instant.now().minusSeconds(25*3600);
        jdbc.update("INSERT INTO qr_login_issuers(id,secret_hash,created_at) VALUES(?,?,?)",oldIssuer,QrLoginCrypto.hash("synthetic-expired-issuer"),Timestamp.from(expiredAt.minusSeconds(120)));
        jdbc.update("""
                INSERT INTO qr_login_challenges(id,issuer_id,purpose,approval_hash,state,browser_label,created_at,expires_at)
                VALUES(?,?,'LOGIN',?,'EXPIRED','synthetic-expired',?,?)
                """,oldChallenge,oldIssuer,QrLoginCrypto.hash("synthetic-expired-approval"),Timestamp.from(expiredAt.minusSeconds(120)),Timestamp.from(expiredAt));
        jdbc.update("UPDATE qr_login_issuers SET current_challenge_id=? WHERE id=?",oldChallenge,oldIssuer);
        assertThat(persistence.cleanup(Instant.now().minusSeconds(24*3600))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM qr_login_issuers WHERE id=?",Long.class,oldIssuer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions",Long.class)).isEqualTo(allSessions);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_security_events",Long.class)).isEqualTo(allAudit);
        assertThat(post("/auth/qr/exchange",challenge.proof(),null).getStatusCode()).isEqualTo(HttpStatus.OK);
        Challenge boundedReplay=issue(null); confirm(boundedReplay);
        int previousReplay=properties.getReplaySeconds();
        try {
            properties.setReplaySeconds(1);
            assertThat(post("/auth/qr/exchange",boundedReplay.proof(),null).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally { properties.setReplaySeconds(previousReplay); }
        Instant replayUntil=jdbc.queryForObject("SELECT replay_until FROM qr_login_challenges WHERE id=?",Timestamp.class,boundedReplay.proof().challengeId()).toInstant();
        long waitMillis=replayUntil.toEpochMilli()-System.currentTimeMillis()+150;
        if(waitMillis>0)Thread.sleep(waitMillis);
        assertThat(post("/auth/qr/exchange",boundedReplay.proof(),null).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(qrSessions()).isEqualTo(2); assertThat(qrLogins()).isEqualTo(2);
    }

    private Challenge issue(Challenge previous) throws Exception {
        QrLoginIssueRequest request=previous==null ? new QrLoginIssueRequest(QrLoginPurpose.LOGIN,null,null)
                : new QrLoginIssueRequest(QrLoginPurpose.LOGIN,previous.proof().issuerId(),previous.proof().issuerSecret());
        var response=post("/auth/qr/challenges",request,null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNoStore(response);
        QrLoginIssueResponse issue=json.readValue(response.getBody(),QrLoginIssueResponse.class);
        JsonNode payload=json.readTree(issue.qrPayload());
        var approval=new QrLoginApprovalRequest(QrLoginPurpose.LOGIN,issue.challengeId(),payload.path("approvalToken").asText());
        var proof=new QrLoginProofRequest(QrLoginPurpose.LOGIN,issue.issuerId(),issue.issuerSecret(),issue.challengeId());
        return new Challenge(issue,approval,proof);
    }
    private void confirm(Challenge challenge) {
        assertThat(post("/auth/qr/decision",decision(challenge,QrLoginDecisionRequest.Decision.CONFIRM),source).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    private QrLoginDecisionRequest decision(Challenge c,QrLoginDecisionRequest.Decision choice) {
        return new QrLoginDecisionRequest(QrLoginPurpose.LOGIN,c.approval().challengeId(),c.approval().approvalToken(),choice);
    }
    private ResponseEntity<String> post(String path,Object body,Session session) {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        if(session!=null)headers.setBearerAuth(session.accessToken());
        return http.exchange(path,HttpMethod.POST,new HttpEntity<>(body,headers),String.class);
    }
    private ResponseEntity<String> get(String path,Session session) {
        var headers=new HttpHeaders();headers.setBearerAuth(session.accessToken());
        return http.exchange(path,HttpMethod.GET,new HttpEntity<>(null,headers),String.class);
    }
    private ResponseEntity<String> postRole(String role,Session session) throws Exception {
        var headers=new HttpHeaders();headers.setBearerAuth(session.accessToken());headers.setContentType(MediaType.APPLICATION_JSON);
        String version=json.readTree(get("/auth/session",session).getBody()).path("sessionVersion").asText();
        return http.exchange("/auth/session/active-role",HttpMethod.PUT,new HttpEntity<>(Map.of("role",role,"expectedSessionVersion",version),headers),String.class);
    }
    private Session loginOwnUser() throws Exception {
        String login=jdbc.queryForObject("SELECT login FROM users WHERE id=?",String.class,user);
        var result=http.postForEntity("/auth/login",new LoginRequest(login,"password"),TokenResponse.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);return session(result.getBody());
    }
    private Session session(TokenResponse tokens) throws Exception {
        JsonNode claims=json.readTree(Base64.getUrlDecoder().decode(tokens.accessToken().split("\\.")[1]));
        return new Session(tokens.accessToken(),tokens.refreshToken(),UUID.fromString(claims.path("sid").asText()));
    }
    private long qrSessions(){return jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=? AND auth_method='QR'",Long.class,user);}
    private long qrLogins(){return jdbc.queryForObject("SELECT count(*) FROM account_security_events WHERE user_id=? AND auth_method='QR' AND event_type='LOGIN'",Long.class,user);}
    private void awaitExpiry(Challenge challenge) throws InterruptedException {
        long millis=challenge.issue().expiresAt().toEpochMilli()-System.currentTimeMillis()+150;
        if(millis>0)Thread.sleep(Math.min(millis,6000));
        assertThat(Instant.now().isAfter(challenge.issue().expiresAt())).isTrue();
    }
    private static void assertSamePair(TokenResponse a,TokenResponse b){
        assertThat(Objects.equals(a.accessToken(),b.accessToken()) && Objects.equals(a.refreshToken(),b.refreshToken()) && a.expiresIn()==b.expiresIn()).isTrue();
    }
    private static void assertNoStore(ResponseEntity<?> response) {
        String cacheControl=response.getHeaders().getCacheControl();
        assertThat(cacheControl).isNotNull();
        assertThat(Arrays.stream(cacheControl.split(",")).map(String::trim).toList())
                .isNotEmpty().allMatch("no-store"::equals);
    }
    private record Challenge(QrLoginIssueResponse issue,QrLoginApprovalRequest approval,QrLoginProofRequest proof) {}
    private record Session(String accessToken,String refreshToken,UUID sid){@Override public String toString(){return "Session[sid="+sid+", tokens=<redacted>]";}}
}
