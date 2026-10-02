package ru.rutcampustrack.auth.service;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.qr.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import java.time.Instant;
import java.util.UUID;

@Service
public final class QrLoginService {
    private final QrLoginPersistence persistence;
    private final QrLoginRateLimiter limits;
    private final QrLoginProperties properties;
    private final AuthService auth;
    public QrLoginService(QrLoginPersistence persistence, QrLoginRateLimiter limits, QrLoginProperties properties, AuthService auth) {
        this.persistence=persistence; this.limits=limits; this.properties=properties; this.auth=auth;
    }
    public QrLoginIssueResponse issue(QrLoginIssueRequest request, String ip, String userAgent) {
        if (request.purpose()!=QrLoginPurpose.LOGIN || !request.isIssuerPairValid())
            throw new QrLoginException(QrLoginException.Code.NOT_FOUND);
        limits.issue(request.issuerId(),ip);
        boolean initial=request.issuerId()==null;
        UUID issuer=initial ? UUID.randomUUID() : request.issuerId();
        String browserSecret=initial ? QrLoginCrypto.secret() : request.issuerSecret();
        UUID challenge=UUID.randomUUID(); String approval=QrLoginCrypto.secret();
        Instant expiry=persistence.issue(issuer,QrLoginCrypto.issuerHash(browserSecret),initial,challenge,
                QrLoginCrypto.approvalHash(challenge,approval),browserLabel(userAgent));
        // An opaque typed payload; it is never an access-token URL or a browser exchange proof.
        String payload="{\"version\":1,\"purpose\":\"LOGIN\",\"challengeId\":\""+challenge+"\",\"approvalToken\":\""+approval+"\"}";
        return new QrLoginIssueResponse(issuer,browserSecret,challenge,QrLoginPurpose.LOGIN,payload,expiry,
                properties.getTtlSeconds(),properties.getPollAfterSeconds());
    }
    public QrLoginStatusResponse status(QrLoginProofRequest request,String ip) {
        limits.status(request.issuerId(),ip); return persistence.status(request);
    }
    public QrLoginPreviewResponse preview(QrLoginApprovalRequest request,SessionPrincipal principal) {
        limits.approve(principal.userId()); return persistence.preview(request,principal);
    }
    public QrLoginStatusResponse decide(QrLoginDecisionRequest request,SessionPrincipal principal) {
        limits.approve(principal.userId()); return persistence.decide(request,principal);
    }
    public TokenResponse exchange(QrLoginProofRequest request) {
        limits.exchange(request.issuerId());
        QrLoginPersistence.Issuance accepted=persistence.exchange(request); // transaction committed before signing
        return auth.signQrSession(accepted.snapshot(),accepted.refreshJti(),accepted.issuedAt(),accepted.accessExpiry(),accepted.signingFingerprint());
    }
    public long refreshRemainingSeconds(TokenResponse tokens) { return auth.refreshRemainingSeconds(tokens.refreshToken()); }
    private static String browserLabel(String userAgent) {
        if (userAgent==null || userAgent.isBlank()) return "Браузер";
        String clean=userAgent.replaceAll("[\\p{Cntrl}]", " ").strip();
        return clean.length()>160 ? clean.substring(0,160) : clean;
    }
}
