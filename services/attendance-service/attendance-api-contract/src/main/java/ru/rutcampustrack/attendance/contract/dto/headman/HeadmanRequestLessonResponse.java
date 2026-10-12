package ru.rutcampustrack.attendance.contract.dto.headman;

import java.time.LocalDate;
import java.time.LocalTime;

public record HeadmanRequestLessonResponse(
        Long lessonId,
        Long groupId,
        Long subjectId,
        String subjectName,
        String subjectType,
        Long semesterId,
        Integer lessonNumber,
        LocalDate date,
        LocalTime startsAt,
        LocalTime endsAt,
        String requestLessonStatus,
        String attendanceStatus,
        String attendanceSource,
        String occurrenceId,
        String occurrenceRevision) {
    public HeadmanRequestLessonResponse(
            Long lessonId,
            Long groupId,
            Long subjectId,
            String subjectName,
            String subjectType,
            Long semesterId,
            Integer lessonNumber,
            LocalDate date,
            LocalTime startsAt,
            LocalTime endsAt,
            String requestLessonStatus,
            String attendanceStatus,
            String attendanceSource) {
        this(
                lessonId,
                groupId,
                subjectId,
                subjectName,
                subjectType,
                semesterId,
                lessonNumber,
                date,
                startsAt,
                endsAt,
                requestLessonStatus,
                attendanceStatus,
                attendanceSource,
                null,
                null);
    }
}
