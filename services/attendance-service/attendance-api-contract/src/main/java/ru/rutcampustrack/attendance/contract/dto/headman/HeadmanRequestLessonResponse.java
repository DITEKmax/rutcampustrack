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
        String attendanceSource
) {
}
