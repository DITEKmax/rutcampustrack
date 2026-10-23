package ru.rutcampustrack.auth.device;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DevicePreferencesCleanupJob {
    private final DevicePreferencesService service;
    private final int batch;

    public DevicePreferencesCleanupJob(
            DevicePreferencesService service,
            @Value("${auth.device-preferences.cleanup-batch-size:500}") int batch) {
        if (batch < 1 || batch > 5000)
            throw new IllegalArgumentException("Invalid device cleanup batch");
        this.service = service;
        this.batch = batch;
    }

    @Scheduled(fixedDelayString = "${auth.device-preferences.cleanup-delay-millis:3600000}")
    @SchedulerLock(name = "auth-device-preferences-cleanup", lockAtMostFor = "PT5M")
    public void tick() {
        service.cleanup(batch);
    }
}
