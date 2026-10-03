package ru.rutcampustrack.schedule.contract.dto.item;

/** Read-only estimate; revision freezes the observed series, not its authorization. */
public record ScheduleItemLifecyclePreviewResponse(
        String revision, long updatedCount, long removedCount, long restoredCount, long createdCount) { }
