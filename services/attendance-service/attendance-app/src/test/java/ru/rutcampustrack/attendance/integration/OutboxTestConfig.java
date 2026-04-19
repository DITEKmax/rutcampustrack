package ru.rutcampustrack.attendance.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

/**
 * Test-side аналог {@code OutboxConfig.Publisher} (M02 Группа 5).
 *
 * <p>Прод-Publisher guarded {@code @Profile("!test")} — шедулер не стартует
 * в тестах. Этот config даёт {@link OutboxPublisherJob} bean (без
 * {@code @EnableScheduling}) — тесты дёргают {@code publishBatch()} вручную
 * чтобы выпихнуть pending rows в Rabbit.
 */
@Configuration
@Profile("test")
public class OutboxTestConfig {

    @Bean
    public OutboxPublisherJob outboxPublisherJob(OutboxStorage storage,
                                                 OutboxEventSender sender) {
        return new OutboxPublisherJob(storage, sender);
    }
}
