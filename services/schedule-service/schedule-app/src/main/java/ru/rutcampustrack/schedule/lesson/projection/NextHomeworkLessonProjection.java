package ru.rutcampustrack.schedule.lesson.projection;

import java.time.LocalDate;
import java.time.LocalTime;

/** Selection and occurrence revision read atomically from the current physical snapshot. */
public interface NextHomeworkLessonProjection {
    Long getLessonId();
    Long getOccurrenceId();
    Long getOccurrenceRevision();
    Long getGroupId();
    Long getSubjectId();
    Long getSemesterId();
    String getLessonType();
    LocalDate getDate();
    Short getLessonNumber();
    LocalTime getStartTime();
    LocalTime getEndTime();
}
