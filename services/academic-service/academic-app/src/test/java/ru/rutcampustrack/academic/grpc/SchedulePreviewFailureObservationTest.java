package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.grpc.ConnectivityState;
import io.grpc.Status;
import io.netty.channel.ConnectTimeoutException;

import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

class SchedulePreviewFailureObservationTest {
    @Test
    void observesOnlyThePassiveFactoryWatchSnapshotWithoutCreatingOrConnectingAChannel() {
        for (ConnectivityState state : ConnectivityState.values()) {
            GrpcChannelFactory factory = mock(GrpcChannelFactory.class);
            when(factory.getConnectivityState()).thenReturn(Map.of("schedule-service", state));
            assertThat(SchedulePreviewFailureObservation.channelState(factory).name())
                    .isEqualTo(state.name());
            verify(factory).getConnectivityState();
            verifyNoMoreInteractions(factory);
        }
        // A delayed watch callback may leave the same cached value at both observations.
        GrpcChannelFactory factory = mock(GrpcChannelFactory.class);
        Map<String, ConnectivityState> cached = new HashMap<>();
        cached.put("schedule-service", ConnectivityState.IDLE);
        when(factory.getConnectivityState()).thenReturn(cached);
        assertThat(SchedulePreviewFailureObservation.channelState(factory).name())
                .isEqualTo("IDLE");
        assertThat(SchedulePreviewFailureObservation.channelState(factory).name())
                .isEqualTo("IDLE");
    }

    @Test
    void missingForeignOrFailingWatchObservationsStayUnknown() {
        assertThat(SchedulePreviewFailureObservation.channelState(null).name())
                .isEqualTo("UNKNOWN");
        GrpcChannelFactory factory = mock(GrpcChannelFactory.class);
        when(factory.getConnectivityState())
                .thenReturn(null, Map.of(), Map.of("other", ConnectivityState.READY));
        for (int i = 0; i < 3; i++) {
            assertThat(SchedulePreviewFailureObservation.channelState(factory).name())
                    .isEqualTo("UNKNOWN");
        }
        when(factory.getConnectivityState()).thenThrow(new IllegalStateException("PRIVATE_WATCH"));
        assertThat(SchedulePreviewFailureObservation.channelState(factory).name())
                .isEqualTo("UNKNOWN");
    }

    @Test
    void projectsNestedTypedConnectCausesWithoutPublishingDescriptionsOrAddresses() {
        ConnectException refused = new ConnectException("Connection refused");
        ConnectException annotated =
                new ConnectException("Connection refused: PRIVATE_ADDRESS:9092");
        annotated.initCause(refused);
        assertKind(
                Status.UNAVAILABLE
                        .withDescription("PRIVATE_STATUS")
                        .withCause(annotated)
                        .asRuntimeException(),
                "REFUSED");
        assertKind(new NoRouteToHostException("PRIVATE_ROUTE"), "NO_ROUTE");
        assertKind(new SocketTimeoutException("PRIVATE_TIMEOUT"), "TIMEOUT");
        assertKind(new ConnectTimeoutException("PRIVATE_NETTY_TIMEOUT"), "TIMEOUT");
        assertKind(new TimeoutException("PRIVATE_TIMEOUT"), "TIMEOUT");
        assertKind(new ConnectException("Connection timed out"), "TIMEOUT");
        assertKind(new ConnectException("PRIVATE_OTHER"), "OTHER");
        assertKind(new ConnectException(), "OTHER");
        assertKind(null, "UNKNOWN");
    }

    @Test
    void messageLookalikesAndUnknownExceptionClassesCannotInventAConnectCause() {
        for (String message :
                new String[] {"Connection refused", "Connection timed out", "PRIVATE_TOKEN"}) {
            assertKind(new IllegalStateException(message), "UNKNOWN");
        }
        for (String message :
                new String[] {
                    "Connection refused: PRIVATE_ADDRESS",
                    "private Connection refused",
                    "Connection refused\nPRIVATE_TOKEN",
                    "Connection Refused"
                }) {
            assertKind(new ConnectException(message), "OTHER");
        }
        assertKind(new ConnectException("Connection refused") {}, "OTHER");
    }

    @Test
    void ambiguousCyclicOverlongAndThrowingCauseChainsStayUnknown() {
        NoRouteToHostException conflicting = new NoRouteToHostException("PRIVATE_ROUTE");
        conflicting.initCause(new ConnectException("Connection refused"));
        assertKind(conflicting, "UNKNOWN");
        Throwable a = new RuntimeException("PRIVATE_A"), b = new RuntimeException("PRIVATE_B");
        a.initCause(b);
        b.initCause(a);
        assertKind(a, "UNKNOWN");
        Throwable chain = new ConnectException("Connection refused");
        for (int i = 0; i < 7; i++) chain = new RuntimeException("PRIVATE_WRAPPER", chain);
        assertKind(chain, "REFUSED");
        assertKind(new RuntimeException("PRIVATE_OVERFLOW", chain), "UNKNOWN");
        assertKind(
                new RuntimeException() {
                    @Override
                    public synchronized Throwable getCause() {
                        throw new IllegalStateException("PRIVATE_GETTER");
                    }
                },
                "UNKNOWN");
    }

    private static void assertKind(Throwable error, String expected) {
        String projected = SchedulePreviewFailureObservation.connectKind(error).name();
        assertThat(projected)
                .isEqualTo(expected)
                .doesNotContain("PRIVATE", "9092", "ADDRESS", "TOKEN");
    }
}
