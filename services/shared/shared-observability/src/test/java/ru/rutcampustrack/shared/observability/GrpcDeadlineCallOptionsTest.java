package ru.rutcampustrack.shared.observability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.CallOptions;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class GrpcDeadlineCallOptionsTest {
    @Test
    void ignoredImmutableDeadlineReturnFailsActualCallOracle() {
        CallOptions actual = CallOptions.DEFAULT;
        actual.withDeadlineAfter(3, TimeUnit.SECONDS);
        assertThatThrownBy(() -> GrpcDeadlineArchRules.assertCallHasDeadline(actual))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("no deadline");
    }

    @Test
    void deadlineReturnedByHelperDelegationPassesActualCallOracle() {
        GrpcDeadlineArchRules.assertCallHasDeadline(deadlined(CallOptions.DEFAULT));
    }

    private static CallOptions deadlined(CallOptions options) {
        return options.withDeadlineAfter(3, TimeUnit.SECONDS);
    }
}
