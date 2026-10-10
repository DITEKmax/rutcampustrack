package ru.rutcampustrack.notification.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;

@Component @Slf4j
public class NotificationProjectionSignals {
    private final SimpMessagingTemplate messaging;
    public NotificationProjectionSignals(SimpMessagingTemplate messaging) { this.messaging = messaging; }
    public void changed(long userId) {
        Runnable publish = () -> {
            try { messaging.convertAndSend("/topic/user/" + userId, Map.of("type", "notification.history.changed")); }
            catch (RuntimeException error) { log.debug("Notification projection invalidation unavailable"); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else publish.run();
    }
}
