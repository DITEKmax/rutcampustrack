package ru.rutcampustrack.shared.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class PublicKeyHealthIndicatorTest {

    @Test
    void ready_returnsUp() {
        Health h = new PublicKeyHealthIndicator(() -> true).health();

        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails())
                .containsEntry("keyId", "internal-jwt")
                .containsEntry("ready", true);
    }

    @Test
    void notReady_returnsDown() {
        Health h = new PublicKeyHealthIndicator(() -> false).health();

        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("ready", false);
    }

    @Test
    void probeException_returnsDownWithError() {
        Health h = new PublicKeyHealthIndicator(() -> {
            throw new IllegalStateException("key fetch failed");
        }).health();

        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsKey("error");
    }

    @Test
    void customKeyId_inDetails() {
        Health h = new PublicKeyHealthIndicator(() -> true, "auth-rsa-2026").health();

        assertThat(h.getDetails()).containsEntry("keyId", "auth-rsa-2026");
    }
}
