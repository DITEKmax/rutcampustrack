package ru.rutcampustrack.mobilebff.contract.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Additive read models for the teacher PWA/TMA. */
public final class TeacherApiModels {
    private TeacherApiModels() {
    }

    @Schema(name = "TeacherAssignment")
    public record Assignment(
            String id,
            String teacherId,
            String groupId,
            String groupName,
            String subjectId,
            String subjectName,
            String semesterId,
            String lessonType,
            LocalDate validFrom,
            LocalDate validUntilExclusive
    ) {
    }

    @Schema(name = "TeacherDayLesson")
    public record DayLesson(
            String id,
            String assignmentId,
            String groupId,
            String groupName,
            String subjectId,
            String subjectName,
            String semesterId,
            String lessonType,
            LocalDate date,
            int lessonNumber,
            LocalTime startsAt,
            LocalTime endsAt,
            String room,
            String status,
            boolean oneOff,
            boolean moved,
            boolean cancelled
    ) {
    }

    @Schema(name = "TeacherDay")
    public record DayResponse(
            String semesterId,
            LocalDate date,
            List<DayLesson> lessons,
            Instant serverNow
    ) {
    }

    @Schema(name = "TeacherSemester")
    public record SemesterResponse(
            String id,
            String name,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
    }

    @Schema(name = "TeacherRosterEntry")
    public record RosterEntry(
            String studentId,
            String displayName,
            String status,
            String symbol,
            String source,
            boolean recordPresent,
            boolean pendingTicket,
            boolean autoAbsent,
            String ticketId,
            String excuseType,
            String excuseReason,
            String excuseComment,
            String attachmentId,
            String attachmentName,
            String attachmentContentType,
            long attachmentSize
    ) {
    }

    @Schema(name = "TeacherLesson")
    public record LessonResponse(
            DayLesson lesson,
            List<RosterEntry> roster,
            Instant serverNow
    ) {
    }

    @Schema(name = "TeacherJournalCell")
    public record JournalCell(
            String lessonId,
            String status,
            String symbol,
            String source,
            boolean recordPresent,
            boolean pendingTicket,
            boolean autoAbsent,
            String ticketId,
            String excuseType,
            String excuseReason
    ) {
    }

    @Schema(name = "TeacherJournalStudent")
    public record JournalStudent(
            String studentId,
            String displayName,
            List<JournalCell> cells
    ) {
    }

    @Schema(name = "TeacherJournal")
    public record JournalResponse(
            List<DayLesson> lessons,
            List<JournalStudent> students,
            Instant serverNow,
            int page,
            int pageSize,
            int totalLessons,
            boolean hasMore
    ) {
    }

    @Schema(name = "TeacherExcuseAttachment")
    public record ExcuseAttachment(
            String id,
            String fileName,
            String contentType,
            long size,
            Instant uploadedAt,
            Instant expiresAt
    ) {
    }

    @Schema(name = "TeacherExcuse")
    public record ExcuseResponse(
            String id,
            String kind,
            String status,
            String studentId,
            String studentName,
            String groupId,
            String groupName,
            String excuseType,
            String reason,
            String comment,
            Instant createdAt,
            String decisionBy,
            Instant decisionAt,
            String decisionComment,
            List<DayLesson> lessons,
            List<ExcuseAttachment> attachments,
            Instant serverNow
    ) {
    }
}
