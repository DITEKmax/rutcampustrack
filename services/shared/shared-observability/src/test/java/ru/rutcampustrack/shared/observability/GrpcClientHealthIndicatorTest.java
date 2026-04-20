package ru.rutcampustrack.shared.observability;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcClientHealthIndicatorTest {

    @Test
    void ready_returnsUp() {
        ManagedChannel ch = mock(ManagedChannel.class);
        when(ch.getState(anyBoolean())).thenReturn(ConnectivityState.READY);

        Health h = new GrpcClientHealthIndicator("academic-service", ch, 0).health();

        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails())
                .containsEntry("downstream", "academic-service")
                .containsEntry("state", "READY");
    }

    @Test
    void transientFailure_returnsDown() {
        ManagedChannel ch = mock(ManagedChannel.class);
        when(ch.getState(anyBoolean())).thenReturn(ConnectivityState.TRANSIENT_FAILURE);

        Health h = new GrpcClientHealthIndicator("academic-service", ch, 0).health();

        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsEntry("state", "TRANSIENT_FAILURE");
    }

    @Test
    void shutdown_returnsDown() {
        ManagedChannel ch = mock(ManagedChannel.class);
        when(ch.getState(anyBoolean())).thenReturn(ConnectivityState.SHUTDOWN);

        Health h = new GrpcClientHealthIndicator("academic-service", ch, 0).health();

        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void idle_withoutWait_returnsUnknown() {
        ManagedChannel ch = mock(ManagedChannel.class);
        when(ch.getState(anyBoolean())).thenReturn(ConnectivityState.IDLE);

        Health h = new GrpcClientHealthIndicator("academic-service", ch, 0).health();

        assertThat(h.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(h.getDetails()).containsEntry("state", "IDLE");
    }

    @Test
    void connecting_withoutWait_returnsUnknown() {
        ManagedChannel ch = mock(ManagedChannel.class);
        when(ch.getState(anyBoolean())).thenReturn(ConnectivityState.CONNECTING);

        Health h = new GrpcClientHealthIndicator("academic-service", ch, 0).health();

        assertThat(h.getStatus()).isEqualTo(Status.UNKNOWN);
    }
}
