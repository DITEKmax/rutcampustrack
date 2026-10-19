package ru.rutcampustrack.academic.grpc;

import io.grpc.ConnectivityState;
import io.netty.channel.ConnectTimeoutException;

import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/** Closed diagnostic projections; never an admission or channel lifecycle operation. */
final class SchedulePreviewFailureObservation {
    enum ChannelState {
        READY,
        IDLE,
        CONNECTING,
        TRANSIENT_FAILURE,
        SHUTDOWN,
        UNKNOWN
    }

    enum ConnectKind {
        REFUSED,
        TIMEOUT,
        NO_ROUTE,
        OTHER,
        UNKNOWN
    }

    private SchedulePreviewFailureObservation() {}

    /**
     * A FACTORY_WATCH_SNAPSHOT, not a fresh ManagedChannel sample or a transition proof. netdevh
     * 3.1.0 returns its passive cached watch map without creating a channel.
     */
    static ChannelState channelState(GrpcChannelFactory factory) {
        try {
            if (factory == null) return ChannelState.UNKNOWN;
            Map<String, ConnectivityState> states = factory.getConnectivityState();
            ConnectivityState state = states == null ? null : states.get("schedule-service");
            if (state == null) return ChannelState.UNKNOWN;
            return switch (state) {
                case READY -> ChannelState.READY;
                case IDLE -> ChannelState.IDLE;
                case CONNECTING -> ChannelState.CONNECTING;
                case TRANSIENT_FAILURE -> ChannelState.TRANSIENT_FAILURE;
                case SHUTDOWN -> ChannelState.SHUTDOWN;
            };
        } catch (RuntimeException ignored) {
            return ChannelState.UNKNOWN;
        }
    }

    static ConnectKind connectKind(Throwable failure) {
        EnumSet<ConnectKind> known = EnumSet.noneOf(ConnectKind.class);
        IdentityHashMap<Throwable, Boolean> seen = new IdentityHashMap<>();
        boolean connect = false;
        try {
            for (int depth = 0; failure != null; depth++, failure = failure.getCause()) {
                if (depth >= 8 || seen.put(failure, Boolean.TRUE) != null) {
                    return ConnectKind.UNKNOWN;
                }
                if (failure instanceof NoRouteToHostException) known.add(ConnectKind.NO_ROUTE);
                if (failure instanceof SocketTimeoutException
                        || failure instanceof ConnectTimeoutException
                        || failure instanceof TimeoutException) {
                    known.add(ConnectKind.TIMEOUT);
                }
                if (failure instanceof ConnectException) {
                    connect = true;
                    // Netty's annotated wrapper retains this original JDK cause. Do not parse
                    // its address-bearing suffix, status description, or arbitrary messages.
                    if (failure.getClass() == ConnectException.class) {
                        String message = failure.getMessage();
                        if ("Connection refused".equals(message)) known.add(ConnectKind.REFUSED);
                        if ("Connection timed out".equals(message)) known.add(ConnectKind.TIMEOUT);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return ConnectKind.UNKNOWN;
        }
        if (known.size() == 1) return known.iterator().next();
        if (!known.isEmpty()) return ConnectKind.UNKNOWN;
        return connect ? ConnectKind.OTHER : ConnectKind.UNKNOWN;
    }
}
