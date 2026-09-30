package ru.rutcampustrack.attendance.event;

/** Non-retryable, scope-specific rejection persisted as an ERROR proof for Schedule. */
public class SemesterArchiveEffectRejectedException extends RuntimeException {

    private final String blockingReason;

    public SemesterArchiveEffectRejectedException(String blockingReason, String message) {
        super(message);
        this.blockingReason = blockingReason;
    }

    public String blockingReason() {
        return blockingReason;
    }
}
