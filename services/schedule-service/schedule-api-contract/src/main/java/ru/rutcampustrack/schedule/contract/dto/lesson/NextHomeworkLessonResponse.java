package ru.rutcampustrack.schedule.contract.dto.lesson;

import java.time.LocalDate;
import java.time.LocalTime;

/** Current physical identity shared by recurring and one-off homework choices. */
public record NextHomeworkLessonResponse(
        Long lessonId, Long occurrenceId, Long occurrenceRevision,
        Long groupId, Long subjectId, Long semesterId, String lessonType,
        LocalDate date, Short lessonNumber, LocalTime startTime, LocalTime endTime) {}
