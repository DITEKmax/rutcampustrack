package ru.rutcampustrack.academic.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxPublisherJob;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

/**
 * Test-side аналог {@code OutboxConfig.Publisher} (M02 Группа 5).
 *
 * <p>Продакшн-{@code OutboxConfig.Publisher} guarded {@code @Profile("!test")}
 * — в тестах он не стартует (чтобы {@code @Scheduled} tick не пулял events
 * в фоне). Но интеграционные event-тесты хотят дёргать
 * {@link OutboxPublisherJob#publishBatch()} вручную для контролируемой
 * проверки. Этот config даёт им {@link OutboxPublisherJob} bean БЕЗ
 * {@code @EnableScheduling} / {@code @EnableSchedulerLock} — scheduler
 * не активируется, но bean доступен для {@code @Autowired}.
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
