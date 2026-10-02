package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkSnapshot;
import ru.rutcampustrack.academic.homework.HomeworkCreateIntent;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Objects;

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
    @Column(name = "lesson_number")
    private Integer lessonNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_mode", nullable = false)
    private HomeworkBindingMode bindingMode = HomeworkBindingMode.LESSON;

    @Column(name = "revision", nullable = false)
    private long revision = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "create_intent", columnDefinition = "jsonb")
    private HomeworkCreateIntent createIntent;

    @Column(name = "create_intent_kind", length = 32)
    private String createIntentKind;

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
        this.createIntent = new HomeworkCreateIntent(groupId, subjectId, semesterId,
                title, description, link, HomeworkBindingMode.LESSON, lessonDate, lessonNumber);
        this.createIntentKind = "ORIGINAL_CREATE";
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

    public Homework(Long groupId, Long subjectId, Long semesterId,
                    String title, String description, String link, Long publishedBy,
                    LocalDate lessonDate, Integer lessonNumber, Long bindingId, Long actorId,
                    UUID requestKey, byte[] payloadHash, HomeworkBindingMode mode) {
        this(groupId, subjectId, semesterId, title, description, link, publishedBy,
                lessonDate, lessonNumber, bindingId, actorId, requestKey, payloadHash);
        this.bindingMode = mode;
        this.createIntent = new HomeworkCreateIntent(groupId, subjectId, semesterId,
                title, description, link, mode, lessonDate, lessonNumber);
    }

    public byte[] getPayloadHash() {
        return payloadHash == null ? null : payloadHash.clone();
    }

    public HomeworkSnapshot snapshot() {
        return new HomeworkSnapshot(title, description, link, bindingMode, lessonDate, lessonNumber);
    }

    /** Captures only the request previously accepted by legacy sameRequest, never guesses original content. */
    public void captureLegacyAcceptedReplay() {
        if (createIntent != null) return;
        createIntent = new HomeworkCreateIntent(groupId, subjectId, semesterId, title,
                description, link, bindingMode, lessonDate, lessonNumber);
        createIntentKind = "LEGACY_ACCEPTED_REPLAY";
    }

    public void replaceContent(String title, String description, String link, OffsetDateTime changedAt) {
        if (publicationState != HomeworkPublicationState.ACTIVE) {
            throw new IllegalStateException("only active homework can be edited");
        }
        this.title = title;
        this.description = description;
        this.link = link;
        this.updatedAt = changedAt;
        revision++;
    }

    /** The accepted edit changes content/placement once without touching publication identity. */
    public void applyEdit(HomeworkSnapshot accepted, OffsetDateTime changedAt) {
        if (publicationState != HomeworkPublicationState.ACTIVE) throw new IllegalStateException("archived homework is read-only");
        if (accepted.bindingMode() == null || accepted.lessonDate() == null
                || (accepted.bindingMode() == HomeworkBindingMode.DATE && accepted.lessonNumber() != null)
                || (accepted.bindingMode() == HomeworkBindingMode.LESSON && (accepted.lessonNumber() == null
                    || accepted.lessonNumber() < 1 || accepted.lessonNumber() > 8))) {
            throw new IllegalArgumentException("invalid accepted homework placement");
        }
        if (!Objects.equals(lessonDate, accepted.lessonDate()) || !Objects.equals(lessonNumber, accepted.lessonNumber())
                || bindingMode != accepted.bindingMode()) dueReminderSentAt = null;
        title = accepted.title(); description = accepted.description(); link = accepted.link();
        bindingMode = accepted.bindingMode(); lessonDate = accepted.lessonDate(); lessonNumber = accepted.lessonNumber();
        updatedAt = changedAt; revision++;
    }

    /** Detached response of a previously committed command; never saved or attached. */
    public Homework recordedResult(HomeworkSnapshot result, long acceptedRevision) {
        Homework copy = new Homework(groupId, subjectId, semesterId, result.title(),
                result.description(), result.link(), publishedBy, result.lessonDate(),
                result.lessonNumber(), bindingId, actorId, requestKey, payloadHash);
        copy.id = id;
        copy.bindingMode = result.bindingMode();
        copy.revision = acceptedRevision;
        copy.publicationState = publicationState;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        copy.createIntent = createIntent;
        copy.createIntentKind = createIntentKind;
        return copy;
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
        revision++;
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
