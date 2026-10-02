package ru.rutcampustrack.auth.qr;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.Duration;

/** Reuses Auth scheduling/ShedLock; only ephemeral QR metadata, never sessions or account audit. */
@Component
@Profile("!test")
public class QrLoginCleanupJob {
    private final QrLoginPersistence persistence;
    private final QrLoginProperties properties;
    public QrLoginCleanupJob(QrLoginPersistence persistence,QrLoginProperties properties) { this.persistence=persistence;this.properties=properties; }
    @Scheduled(fixedDelayString="${auth.qr-login.cleanup-delay-millis:3600000}")
    @SchedulerLock(name="auth-qr-login-cleanup",lockAtMostFor="PT5M")
    public void tick() {
        if (properties.isCleanupEnabled()) persistence.cleanup(Instant.now().minus(Duration.ofHours(24)));
    }
}
