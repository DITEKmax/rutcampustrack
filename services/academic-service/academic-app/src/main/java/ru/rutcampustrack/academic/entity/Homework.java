package ru.rutcampustrack.academic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    @Setter
    @Column(name = "lesson_id")
    private Long lessonId;

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

    public Homework(Long groupId, Long subjectId, Long semesterId,
                    String title, String description, String link, Long publishedBy) {
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.semesterId = semesterId;
        this.title = title;
        this.description = description;
        this.link = link;
        this.publishedBy = publishedBy;
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
