package ru.rutcampustrack.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * M13 G18: STOMP heartbeat config — guarantees that {@link WebSocketConfig} provides a
 * TaskScheduler. Without this scheduler Spring silently disables heartbeat (default 0/0) — idle
 * WebSocket connections behind nginx may stall as half-open. Regression guard.
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
    void stompHeartbeatScheduler_isInitializedDaemonThread() throws Exception {
        config =
                new WebSocketConfig(null, null, null, null) {
                    @Override
                    public ThreadPoolTaskScheduler stompHeartbeatScheduler() {
                        if (scheduler == null) scheduler = super.stompHeartbeatScheduler();
                        return scheduler;
                    }
                };
        var inbound = new org.springframework.messaging.support.ExecutorSubscribableChannel();
        var outbound = new org.springframework.messaging.support.ExecutorSubscribableChannel();
        var registry =
                new org.springframework.messaging.simp.config.MessageBrokerRegistry(
                        inbound, outbound) {
                    org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler broker() {
                        return getSimpleBroker(
                                new org.springframework.messaging.support
                                        .ExecutorSubscribableChannel());
                    }
                };
        config.configureMessageBroker(registry);
        var broker = registry.broker();
        assertThat(broker.getHeartbeatValue()).containsExactly(10_000L, 10_000L);
        assertThat(broker.getTaskScheduler()).isSameAs(scheduler);
        java.util.concurrent.CompletableFuture<Boolean> daemon =
                new java.util.concurrent.CompletableFuture<>();
        scheduler.execute(() -> daemon.complete(Thread.currentThread().isDaemon()));
        assertThat(daemon.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
}
