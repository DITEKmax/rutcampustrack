package ru.rutcampustrack.attendance.shared.port;

/**
 * Pair-scoped lifecycle for journal attachments.
 *
 * <p>The attendance document stores only attachment metadata while the bytes
 * live in the request-attachment collection.  All writers use this port so a
 * status transition cannot leave a journal blob behind or make an expired
 * blob look downloadable.  Request-owned attachments use their request id
 * and are therefore outside this lifecycle.</p>
 */
public interface JournalAttachmentPort {

    /** Remove every journal attachment bound to the student/lesson pair. */
    void delete(long semesterId, long studentId, long lessonId, long groupId);

    /**
     * Return whether the exact metadata id still has live journal bytes for
     * the pair.  A false result is fail-closed and does not mutate storage.
     */
    boolean isAvailable(long lessonId, long studentId, String attachmentId);
}
