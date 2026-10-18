package ru.rutcampustrack.academic.groupavailability;

import ru.rutcampustrack.academic.identityoperation.AcademicIdentityLocalCommand.ApiIntent;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

/** Distinguishes exact HTTP requests from the immutable domain command reused by reconciliation. */
public final class GroupAvailabilityIntent {
    private GroupAvailabilityIntent() {}

    public static ApiIntent direct(
            GroupAvailabilityCommand.Action action, long scope, String digest) {
        return new ApiIntent("GROUP_" + action.name(), scope, digest);
    }

    public static ApiIntent reconcile(UUID failed) {
        return new ApiIntent(
                "GROUP_RECONCILE",
                0,
                HexFormat.of()
                        .formatHex(
                                GroupAvailabilityStore.hash(
                                                ("GROUP_RECONCILE:" + failed)
                                                        .getBytes(StandardCharsets.UTF_8))
                                        .toByteArray()));
    }
}
