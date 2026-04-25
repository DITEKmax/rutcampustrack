package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M13 G18: STOMP heartbeat config — guarantees that {@link WebSocketConfig}
 * provides a TaskScheduler. Without this scheduler Spring silently disables
 * heartbeat (default 0/0) — idle WebSocket connections behind nginx may
 * stall as half-open. Regression guard.
 */
class WebSocketConfigTest {

    private WebSocketConfig config;
    private ThreadPoolTaskScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void stompHeartbeatScheduler_isInitializedDaemonThread() {
        config = new WebSocketConfig(null, null);
        scheduler = config.stompHeartbeatScheduler();

        assertThat(scheduler).isNotNull();
        // initialize() вызвана внутри bean-factory метода → scheduler usable;
        // getScheduledExecutor() кидает IllegalStateException если scheduler не initialized.
        assertThat(scheduler.getScheduledExecutor()).isNotNull();
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("stomp-heartbeat-");
    }
}
