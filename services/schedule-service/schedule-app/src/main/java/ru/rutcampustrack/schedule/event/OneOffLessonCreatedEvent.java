package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Domain event published when a one-off lesson is created by a headman. Matches
 * event-schemas/lesson.one_off.created.json contract.
 *
 * <p>Published by OneOffLessonService.createOneOffLesson() after successful save. Consumed by: -
 * notification-bot (push to all group students, D-18) - notification-web (STOMP push to open
 * PWA/web-panel tabs) - attendance-service (read-model update for lesson generation)
 */
public class OneOffLessonCreatedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("one_off_lesson_id") Long oneOffLessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("date") String date,
            @JsonProperty("lesson_number") Integer lessonNumber,
            @JsonProperty("classroom") String classroom,
            @JsonProperty("lesson_id") Long lessonId,
            @JsonProperty("semester_id") Long semesterId,
            @JsonProperty("lesson_type") String lessonType,
            @JsonProperty("lesson_date") String lessonDate,
            @JsonProperty("assignment_id") Long assignmentId,
            @JsonProperty("assigned_teacher_id") Long assignedTeacherId,
            @JsonProperty("actor_user_id") Long actorUserId,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime) {}

    public OneOffLessonCreatedEvent(
            Object source,
            Long oneOffLessonId,
            Long groupId,
            Long subjectId,
            LocalDate date,
            Integer lessonNumber,
            String classroom) {
        this(
                source,
                oneOffLessonId,
                groupId,
                subjectId,
                date,
                lessonNumber,
                classroom,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public OneOffLessonCreatedEvent(
            Object source,
            Long oneOffLessonId,
            Long groupId,
            Long subjectId,
            LocalDate date,
            Integer lessonNumber,
            String classroom,
            Long lessonId,
            Long semesterId,
            String lessonType,
            Long assignmentId,
            Long assignedTeacherId,
            Long actorUserId,
            LocalTime startTime,
            LocalTime endTime) {
        super(
                source,
                "lesson.one_off.created",
                new Payload(
                        oneOffLessonId,
                        groupId,
                        subjectId,
                        date.toString(),
                        lessonNumber,
                        classroom,
                        lessonId,
                        semesterId,
                        lessonType,
                        date.toString(),
                        assignmentId,
                        assignedTeacherId,
                        actorUserId,
                        startTime == null ? null : startTime.toString(),
                        endTime == null ? null : endTime.toString()));
    }
}
