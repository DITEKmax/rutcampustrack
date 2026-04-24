package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.RepresentationModel;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Response DTO for lesson view — combines fields from both Lesson and ScheduleItem (VIEW-02, D-16).
 * Extends RepresentationModel for HATEOAS Level 3 compliance.
 * No Lombok — contract modules use plain Java classes with explicit constructors and getters.
 * <p>
 * D-16: teacherId removed. Teacher access to journals is resolved via JOIN
 * ScheduleItem × TeacherSubjectGroup, not by slot-level teacher assignment.
 */
@Schema(description = "Пара (конкретное занятие на дату) — объединение полей Lesson и ScheduleItem с HATEOAS-ссылками")
public class LessonResponse extends RepresentationModel<LessonResponse> {

    private Long id;
    private Long scheduleItemId;
    private Long groupId;
    private Long subjectId;
    private LocalDate date;
    private LessonStatus status;
    private Short dayOfWeek;
    private Short lessonNumber;
    private LocalTime startTime;
    private LocalTime endTime;
    private WeekType weekType;
    private String room;
    private boolean geoBlocked;
    private boolean blockedByHeadman;
    private Long blockedByUserId;
    private OffsetDateTime blockedAt;
    private String cancelReason;
    private OffsetDateTime createdAt;

    public LessonResponse() {}

    public LessonResponse(Long id, Long scheduleItemId, Long groupId, Long subjectId,
                           LocalDate date, LessonStatus status,
                           Short dayOfWeek, Short lessonNumber, LocalTime startTime,
                           LocalTime endTime, WeekType weekType, String room,
                           boolean geoBlocked, String cancelReason, OffsetDateTime createdAt) {
        this(id, scheduleItemId, groupId, subjectId, date, status,
                dayOfWeek, lessonNumber, startTime, endTime, weekType, room,
                geoBlocked, false, null, null, cancelReason, createdAt);
    }

    public LessonResponse(Long id, Long scheduleItemId, Long groupId, Long subjectId,
                           LocalDate date, LessonStatus status,
                           Short dayOfWeek, Short lessonNumber, LocalTime startTime,
                           LocalTime endTime, WeekType weekType, String room,
                           boolean geoBlocked, boolean blockedByHeadman,
                           Long blockedByUserId, OffsetDateTime blockedAt,
                           String cancelReason, OffsetDateTime createdAt) {
        this.id = id;
        this.scheduleItemId = scheduleItemId;
        this.groupId = groupId;
        this.subjectId = subjectId;
        this.date = date;
        this.status = status;
        this.dayOfWeek = dayOfWeek;
        this.lessonNumber = lessonNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.weekType = weekType;
        this.room = room;
        this.geoBlocked = geoBlocked;
        this.blockedByHeadman = blockedByHeadman;
        this.blockedByUserId = blockedByUserId;
        this.blockedAt = blockedAt;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getScheduleItemId() {
        return scheduleItemId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public LocalDate getDate() {
        return date;
    }

    public LessonStatus getStatus() {
        return status;
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

    public boolean isGeoBlocked() {
        return geoBlocked;
    }

    public boolean isBlockedByHeadman() {
        return blockedByHeadman;
    }

    public Long getBlockedByUserId() {
        return blockedByUserId;
    }

    public OffsetDateTime getBlockedAt() {
        return blockedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
