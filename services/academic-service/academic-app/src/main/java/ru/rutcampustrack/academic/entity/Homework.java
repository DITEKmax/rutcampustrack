package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

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
