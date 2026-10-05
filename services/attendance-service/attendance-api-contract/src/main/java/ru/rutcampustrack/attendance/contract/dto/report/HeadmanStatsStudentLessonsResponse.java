package ru.rutcampustrack.attendance.contract.dto.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record HeadmanStatsStudentLessonsResponse(int page, int size, long totalElements, int totalPages,
                                                boolean hasPrevious, boolean hasNext, List<Lesson> items) {
    public record Lesson(long lessonId, LocalDate lessonDate, int lessonNumber, long subjectId,
                         String subjectName, String lessonType, Instant startsAt, Instant endsAt, String room,
                         String status, String source, String markRevision, boolean editable,
                         String blockedReason, ApprovedTicket approvedTicket) { }
    public record ApprovedTicket(String kind, String id, String status) { }
}
