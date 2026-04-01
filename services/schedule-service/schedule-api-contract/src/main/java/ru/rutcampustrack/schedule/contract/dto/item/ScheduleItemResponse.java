package ru.rutcampustrack.schedule.contract.dto.item;

import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Response DTO for schedule template items with HATEOAS links.
 * Extends RepresentationModel for HATEOAS Level 3 compliance.
 * No Lombok — contract modules use plain Java classes with explicit constructors and getters.
 */
public class ScheduleItemResponse extends RepresentationModel<ScheduleItemResponse> {

    private Long id;
    private Long groupId;
    private Long subjectId;
    private Long teacherId;
    private Long semesterId;
    private Short dayOfWeek;
    private Short lessonNumber;
    private LocalTime startTime;
    private LocalTime endTime;
    private WeekType weekType;
    private String room;
    private boolean active;
    private OffsetDateTime createdAt;

    public ScheduleItemResponse() {}

    public ScheduleItemResponse(Long id, Long groupId, Long subjectId, Long teacherId,
                                 Long semesterId, Short dayOfWeek, Short lessonNumber,
                                 LocalTime startTime, LocalTime endTime, WeekType weekType,
                                 String room, boolean active, OffsetDateTime createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.teacherId = teacherId;
        this.semesterId = semesterId;
        this.dayOfWeek = dayOfWeek;
        this.lessonNumber = lessonNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.weekType = weekType;
        this.room = room;
        this.active = active;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public Long getSemesterId() {
        return semesterId;
    }

    public Short getDayOfWeek() {
        return dayOfWeek;
    }

    public Short getLessonNumber() {
        return lessonNumber;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public WeekType getWeekType() {
        return weekType;
    }

    public String getRoom() {
        return room;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
