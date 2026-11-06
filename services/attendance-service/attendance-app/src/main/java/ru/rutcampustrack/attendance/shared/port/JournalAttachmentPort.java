package ru.rutcampustrack.attendance.shared.port;

import java.time.Instant;
import java.util.Map;

/**
 * Pair-scoped lifecycle for journal attachments.
 *
 * <p>The attendance document stores only attachment metadata while the bytes live in the
 * request-attachment collection. All writers use this port so a status transition cannot leave a
 * journal blob behind or make an expired blob look downloadable. Request-owned attachments use
 * their request id and are therefore outside this lifecycle.
 */
public interface JournalAttachmentPort {

    /** Remove every journal attachment bound to the student/lesson pair. */
    void delete(long semesterId, long lessonId, long studentId, long groupId);

    /**
     * Return whether the exact metadata id still has live journal bytes for the pair. A false
     * result is fail-closed and does not mutate storage.
     */
    boolean isAvailable(long lessonId, long studentId, String attachmentId);

    /** Actual expiry of each matching manual descriptor, keyed by roster student. */
    Map<Long, Instant> manualAttachmentExpiries(
            long lessonId, long groupId, long semesterId, Map<Long, ManualReference> references,
            ManualVerification verification);

    /** Metadata alone is only an intermediate check; public roster and download verify bytes. */
    enum ManualVerification {
        METADATA_ONLY,
        VERIFY_SIGNATURE
    }

    record ManualReference(String id, String name, String contentType, Long size) {}
}
