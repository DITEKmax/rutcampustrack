package ru.rutcampustrack.auth.event;

import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

public class AuthOutboxPublisherJob extends OutboxPublisherJob {
    public AuthOutboxPublisherJob(OutboxStorage storage, OutboxEventSender sender,
                                  MeterRegistry meterRegistry) {
        super(storage, sender, meterRegistry);
    }

    @Override
    @Scheduled(fixedDelayString = "${rutcampustrack.auth-outbox.publisher.fixed-delay-ms:5000}")
    @SchedulerLock(name = "auth-outbox-publisher", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1S")
    @Transactional(timeout = 600)
    public void tick() {
        publishBatch();
    }
}
