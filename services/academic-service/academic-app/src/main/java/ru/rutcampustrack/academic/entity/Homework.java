package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "homeworks")
@Getter
@NoArgsConstructor
public class Homework {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "semester_id", nullable = false)
    private Long semesterId;

    /** Phase 61 / D-01: календарная дата пары, к которой привязано ДЗ. */
    @Column(name = "lesson_date", nullable = false)
    private LocalDate lessonDate;

    /** Phase 61 / D-01: номер пары в дне (1..8). */
    @Column(name = "lesson_number", nullable = false)
    private Integer lessonNumber;

    @Setter
    @Column(nullable = false, length = 500)
    private String title;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String description;

    @Setter
    @Column(length = 1000)
    private String link;

    @Column(name = "published_by", nullable = false)
    private Long publishedBy;

    /** Immutable binding identity returned by schedule-service. */
    @Column(name = "binding_id", nullable = false, updatable = false)
    private Long bindingId;

    /** Actor identity used by the Schedule binding authority. */
    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    /** Idempotency key shared by Academic and Schedule for this command. */
    @Column(name = "request_key", nullable = false, updatable = false)
    private UUID requestKey;

    /** Server-computed canonical command hash. */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "payload_hash", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_state", nullable = false)
    private HomeworkPublicationState publicationState = HomeworkPublicationState.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Setter
    @Column(name = "due_reminder_sent_at")
    private OffsetDateTime dueReminderSentAt;

    public Homework(Long groupId, Long subjectId, Long semesterId,
                    String title, String description, String link, Long publishedBy,
                    LocalDate lessonDate, Integer lessonNumber) {
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.semesterId = semesterId;
        this.title = title;
        this.description = description;
        this.link = link;
        this.publishedBy = publishedBy;
        this.lessonDate = lessonDate;
        this.lessonNumber = lessonNumber;
        // Kept for source compatibility with old fixture-only constructors.
        // The live creation path must use the binding-aware constructor below.
        this.actorId = publishedBy;
    }

    public Homework(Long groupId, Long subjectId, Long semesterId,
                    String title, String description, String link, Long publishedBy,
                    LocalDate lessonDate, Integer lessonNumber,
                    Long bindingId, Long actorId, UUID requestKey, byte[] payloadHash) {
        this(groupId, subjectId, semesterId, title, description, link, publishedBy,
                lessonDate, lessonNumber);
        this.bindingId = bindingId;
        this.actorId = actorId;
        this.requestKey = requestKey;
        this.payloadHash = payloadHash == null ? null : payloadHash.clone();
    }

    public byte[] getPayloadHash() {
        return payloadHash == null ? null : payloadHash.clone();
    }

    public void activatePublication() {
        if (publicationState == HomeworkPublicationState.ARCHIVED) {
            throw new IllegalStateException("archived homework cannot be published again");
        }
        if (publicationState == HomeworkPublicationState.ACTIVE) {
            return;
        }
        publicationState = HomeworkPublicationState.ACTIVE;
    }

    public void archivePublication() {
        publicationState = HomeworkPublicationState.ARCHIVED;
    }

    /** Moves only the lesson slot while preserving publication and content identity. */
    public void transferLessonSlot(LocalDate targetDate, Integer targetLessonNumber) {
        if (publicationState == HomeworkPublicationState.ARCHIVED) {
            throw new IllegalStateException("archived homework cannot follow a lesson transfer");
        }
        if (targetDate == null || targetLessonNumber == null
                || targetLessonNumber < 1 || targetLessonNumber > 8) {
            throw new IllegalArgumentException("target lesson slot is invalid");
        }
        if (targetDate.equals(lessonDate) && targetLessonNumber.equals(lessonNumber)) return;
        lessonDate = targetDate;
        lessonNumber = targetLessonNumber;
        dueReminderSentAt = null;
        updatedAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }
}
