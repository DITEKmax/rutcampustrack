package ru.rutcampustrack.academic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Durable terminal identity for a Schedule homework binding. */
@Entity
@Table(name = "homework_binding_archives")
@Getter
@NoArgsConstructor
public class HomeworkBindingArchiveMarker {

    @Id
    @Column(name = "binding_id", nullable = false, updatable = false)
    private Long bindingId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(name = "request_key", nullable = false, updatable = false)
    private UUID requestKey;

    @Column(name = "homework_id")
    private Long homeworkId;

    @Column(name = "semester_id")
    private Long semesterId;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "archived_at", nullable = false, updatable = false)
    private OffsetDateTime archivedAt;

    public HomeworkBindingArchiveMarker(long bindingId, long actorId,
                                        UUID requestKey, Long homeworkId) {
        this(bindingId, actorId, requestKey, homeworkId, null, null);
    }

    public HomeworkBindingArchiveMarker(long bindingId, long actorId,
                                        UUID requestKey, Long homeworkId,
                                        Long semesterId, UUID sourceEventId) {
        this.bindingId = bindingId;
        this.actorId = actorId;
        this.requestKey = requestKey;
        this.homeworkId = homeworkId;
        this.semesterId = semesterId;
        this.sourceEventId = sourceEventId;
    }

    public boolean hasIdentity(long expectedActorId, UUID expectedRequestKey) {
        return actorId == expectedActorId && requestKey.equals(expectedRequestKey);
    }

    public void associateHomework(long expectedHomeworkId) {
        if (expectedHomeworkId <= 0) {
            throw new IllegalArgumentException("homeworkId must be positive");
        }
        if (homeworkId != null && homeworkId != expectedHomeworkId) {
            throw new IllegalStateException("homework archive marker points to different content");
        }
        homeworkId = expectedHomeworkId;
    }

    public void associateOrigin(long expectedSemesterId, UUID expectedSourceEventId) {
        if (expectedSemesterId <= 0) {
            throw new IllegalArgumentException("semesterId must be positive");
        }
        if (semesterId != null && semesterId != expectedSemesterId) {
            throw new IllegalStateException("homework archive marker belongs to another semester");
        }
        semesterId = expectedSemesterId;
        if (sourceEventId == null && expectedSourceEventId != null) {
            sourceEventId = expectedSourceEventId;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (archivedAt == null) {
            archivedAt = OffsetDateTime.now();
        }
    }
}
