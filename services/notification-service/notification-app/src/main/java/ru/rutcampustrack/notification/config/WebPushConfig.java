package ru.rutcampustrack.notification.config;

import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;
import java.security.Security;

/**
 * VAPID key configuration for Web Push delivery.
 *
 * Per D-01: VAPID keys are read from environment variables only — never hardcoded.
 * Per T-27-03: Private key is never exposed via API — only public key is returned by GET /vapid-public-key.
 * Per Plan 01 NOTE: Bean name is 'webPushService' to avoid clash with our custom PushService class in Plan 03.
 *
 * BouncyCastle provider must be registered before PushService instantiation
 * to enable EC key operations required for VAPID.
 */
@Configuration
public class WebPushConfig {

    @Bean
    public PushService webPushService(
            @Value("${vapid.public-key}") String publicKeyBase64,
            @Value("${vapid.private-key}") String privateKeyBase64,
            @Value("${vapid.subject:mailto:noreply@rut.ru}") String subject
    ) throws GeneralSecurityException {
        Security.addProvider(new BouncyCastleProvider());
        return new PushService(publicKeyBase64, privateKeyBase64, subject);
    }
}
