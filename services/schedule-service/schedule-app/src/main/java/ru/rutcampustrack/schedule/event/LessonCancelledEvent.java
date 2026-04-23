package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Domain event published when a lesson is cancelled by a headman/admin.
 * Matches event-schemas/lesson.cancelled.json contract.
 *
 * <p>Published by LessonService.cancelLesson() and LessonService.massCancelLessons().
 *
 * <p>M09 G5 (02 P2-11/5, NEW-118) — расширен до **full snapshot**.
 * Раньше payload содержал только ключи (lesson_id, group_id, subject_id,
 * date, cancel_reason), и downstream consumer'ам (attendance, notification-web)
 * приходилось делать second-look gRPC к schedule для start_time/end_time/
 * lesson_number. Теперь всё в одном сообщении — без read-amplification и
 * с полным audit trail (cancelled_by, cancelled_at).
 */
public class LessonCancelledEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("lesson_id") Long lessonId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            @JsonProperty("date") String date,
            @JsonProperty("start_time") String startTime,
            @JsonProperty("end_time") String endTime,
            @JsonProperty("lesson_number") Integer lessonNumber,
            @JsonProperty("cancel_reason") String cancelReason,
            @JsonProperty("cancelled_by") Long cancelledBy,
            @JsonProperty("cancelled_at") String cancelledAt
    ) {}

    public LessonCancelledEvent(Object source,
                                Long lessonId, Long groupId, Long subjectId,
                                LocalDate date, LocalTime startTime, LocalTime endTime,
                                Integer lessonNumber, String cancelReason,
                                Long cancelledBy, OffsetDateTime cancelledAt) {
        super(source, "lesson.cancelled",
                new Payload(
                        lessonId, groupId, subjectId,
                        date.toString(),
                        startTime != null ? startTime.toString() : null,
                        endTime != null ? endTime.toString() : null,
                        lessonNumber,
                        cancelReason,
                        cancelledBy,
                        cancelledAt != null ? cancelledAt.toString() : null));
    }
}
