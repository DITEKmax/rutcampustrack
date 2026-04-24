package ru.rutcampustrack.academic.contract.dto.homework;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for a homework assignment with HATEOAS links.
 * The {@code completed} flag is per-student, derived from HomeworkCompletion records.
 *
 * <p>Phase 61 / D-01: {@code lessonDate} + {@code lessonNumber} определяют пару, к которой
 * привязано задание. Привязка фиксируется при create и не редактируется (см. D-04/D-05).
 */
@Schema(description = "Домашнее задание (HATEOAS Level 3 с _links; completed — per-student)")
public class HomeworkResponse extends RepresentationModel<HomeworkResponse> {

    private Long id;
    private String title;
    private String description;
    private String link;
    private Long subjectId;
    private Long groupId;
    private Long semesterId;
    private Long publishedBy;
    /** Per-student completion flag populated from HomeworkCompletion table. */
    private boolean completed;
    private OffsetDateTime createdAt;
    private LocalDate lessonDate;
    private Integer lessonNumber;

    public HomeworkResponse() {}

    public HomeworkResponse(Long id, String title, String description, String link,
                            Long subjectId, Long groupId, Long semesterId,
                            Long publishedBy, boolean completed, OffsetDateTime createdAt,
                            LocalDate lessonDate, Integer lessonNumber) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.link = link;
        this.subjectId = subjectId;
        this.groupId = groupId;
        this.semesterId = semesterId;
        this.publishedBy = publishedBy;
        this.completed = completed;
        this.createdAt = createdAt;
        this.lessonDate = lessonDate;
        this.lessonNumber = lessonNumber;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getSemesterId() { return semesterId; }
    public void setSemesterId(Long semesterId) { this.semesterId = semesterId; }

    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long publishedBy) { this.publishedBy = publishedBy; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDate getLessonDate() { return lessonDate; }
    public void setLessonDate(LocalDate lessonDate) { this.lessonDate = lessonDate; }

    public Integer getLessonNumber() { return lessonNumber; }
    public void setLessonNumber(Integer lessonNumber) { this.lessonNumber = lessonNumber; }
}
