package ru.rutcampustrack.schedule.contract.dto.item;

import java.util.List;

/** Read-only estimate; revision freezes the observed series, not its authorization. */
public record ScheduleItemLifecyclePreviewResponse(
        String revision,
        long updatedCount,
        long removedCount,
        long restoredCount,
        long createdCount,
        String operationKind,
        boolean deletable,
        long protectedCount,
        long removableCount,
        List<ConstructorProtectedReason> protectedReasons) {
    public ScheduleItemLifecyclePreviewResponse {
        protectedReasons = protectedReasons == null ? List.of() : List.copyOf(protectedReasons);
    }

    public ScheduleItemLifecyclePreviewResponse(
            String revision,
            long updatedCount,
            long removedCount,
            long restoredCount,
            long createdCount) {
        this(
                revision,
                updatedCount,
                removedCount,
                restoredCount,
                createdCount,
                null,
                false,
                0,
                0,
                List.of());
    }
}
