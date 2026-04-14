package ru.rutcampustrack.schedule.contract.dto.oneoff;

import org.springframework.hateoas.RepresentationModel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for one-off lessons with HATEOAS links (Phase 60-03, D-04).
 * Extends RepresentationModel for HATEOAS Level 3 compliance.
 * No Lombok — contract modules use plain Java classes with explicit constructors and getters.
 */
public class OneOffLessonResponse extends RepresentationModel<OneOffLessonResponse> {

    private Long id;
    private Long groupId;
    private Long subjectId;
    private Long semesterId;
    private LocalDate date;
    private Short lessonNumber;
    private String classroom;
    private Long createdBy;
    private OffsetDateTime createdAt;

    public OneOffLessonResponse() {}

    public OneOffLessonResponse(Long id, Long groupId, Long subjectId, Long semesterId,
                                LocalDate date, Short lessonNumber, String classroom,
                                Long createdBy, OffsetDateTime createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.semesterId = semesterId;
        this.date = date;
        this.lessonNumber = lessonNumber;
        this.classroom = classroom;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getSubjectId() { return subjectId; }
    public Long getSemesterId() { return semesterId; }
    public LocalDate getDate() { return date; }
    public Short getLessonNumber() { return lessonNumber; }
    public String getClassroom() { return classroom; }
    public Long getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
