package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.stub.StreamObserver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.attendance.contract.dto.report.LessonAttendanceResponse;
import ru.rutcampustrack.attendance.contract.dto.report.StudentAttendanceEntry;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.attendance.studentrequest.RequestAttachmentRepository;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseRequest;
import ru.rutcampustrack.teacher.grpc.TeacherExcuseResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalRequest;
import ru.rutcampustrack.teacher.grpc.TeacherJournalResponse;
import ru.rutcampustrack.teacher.grpc.TeacherLessonRequest;
import ru.rutcampustrack.teacher.grpc.TeacherLessonResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsRequest;
import ru.rutcampustrack.teacher.grpc.TeacherStatsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherStatsScope;

/** A mixed student ticket is projected only over the teacher's active groups. */
public class TeacherAttendanceReadGrpcServiceTest {

    /** Shared test identity fixture; production context keys stay package-private. */
    public static Context teacherContext(long teacherId) {
        InternalJwtClaims claims =
                new InternalJwtClaims(
                        teacherId,
                        UUID.randomUUID(),
                        1L,
                        1L,
                        "TEACHER",
                        "ACTIVE",
                        null,
                        false,
                        false);
        return Context.current().withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims);
    }

    @Test
    void teacherStatsMapsScheduleDependencyFailureToUnavailable() {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ExcuseRepository excuses = mock(ExcuseRepository.class);
        RequestAttachmentRepository attachments = mock(RequestAttachmentRepository.class);
        when(report.getTeacherStats(any(), eq(71L)))
                .thenThrow(new ScheduleServiceUnavailableException("schedule unavailable"));

        TeacherAttendanceReadGrpcService service =
                new TeacherAttendanceReadGrpcService(
                        report,
                        schedule,
                        academic,
                        excuses,
                        attachments,
                        Clock.fixed(Instant.parse("2025-12-02T08:00:00Z"), ZoneOffset.UTC));
        RecordingObserver<TeacherStatsResponse> observer = new RecordingObserver<>();
        InternalJwtClaims claims =
                new InternalJwtClaims(
                        71L, UUID.randomUUID(), 1L, 1L, "TEACHER", "ACTIVE", null, false, false);

        Context.current()
                .withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims)
                .run(
                        () ->
                                service.getTeacherStats(
                                        TeacherStatsRequest.newBuilder()
                                                .setSemesterId(9L)
                                                .setScope(TeacherStatsScope.TEACHER_STATS_GROUPS)
                                                .addLessonIds(101L)
                                                .build(),
                                        observer));

        assertThat(observer.value).isNull();
        assertThat(observer.error).isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThat(io.grpc.Status.fromThrowable(observer.error).getCode())
                .isEqualTo(io.grpc.Status.Code.UNAVAILABLE);
    }

    @Test
    void excuseDetailOmitsForeignLessonWithoutLeakingItsCount() {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ExcuseRepository excuses = mock(ExcuseRepository.class);
        RequestAttachmentRepository attachments = mock(RequestAttachmentRepository.class);

        LessonResponse own = lesson(101L, 12L, 71L);
        LessonResponse foreign = lesson(202L, 99L, 88L);
        when(schedule.getLessonById(101L)).thenReturn(own);
        when(schedule.getLessonById(202L)).thenReturn(foreign);
        doNothing().when(report).authorizeTeacherLesson(own, 71L);
        doThrow(new AccessDeniedException("foreign lesson"))
                .when(report)
                .authorizeTeacherLesson(foreign, 71L);
        when(academic.getGroup(33L))
                .thenReturn(GroupResponse.newBuilder().setId(33L).setName("УИТ-311").build());
        when(academic.getSubjectDetailsByIds(List.of(22L)))
                .thenReturn(
                        Map.of(
                                22L,
                                new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));

        ExcuseTicket ticket =
                ExcuseTicket.builder()
                        .id("ticket-1")
                        .studentId(501L)
                        .studentName("Иван Петров")
                        .groupId(33L)
                        .lessonIds(List.of(101L, 202L))
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .excuseType(ExcuseType.ILLNESS)
                        .comment("Болел")
                        .decisionBy(91L)
                        .createdAt(Instant.parse("2025-12-01T08:00:00Z"))
                        .build();
        when(excuses.findById("ticket-1")).thenReturn(Optional.of(ticket));

        TeacherAttendanceReadGrpcService service =
                new TeacherAttendanceReadGrpcService(
                        report,
                        schedule,
                        academic,
                        excuses,
                        attachments,
                        Clock.fixed(Instant.parse("2025-12-02T08:00:00Z"), ZoneOffset.UTC));
        RecordingObserver<TeacherExcuseResponse> observer = new RecordingObserver<>();
        InternalJwtClaims claims =
                new InternalJwtClaims(
                        71L, UUID.randomUUID(), 1L, 1L, "TEACHER", "ACTIVE", null, false, false);

        Deadline parentDeadline = Deadline.after(2, TimeUnit.SECONDS);
        when(academic.getAuditUserDisplayName(eq(91L), any()))
                .thenAnswer(
                        invocation -> {
                            Deadline lookupDeadline = invocation.getArgument(1);
                            assertThat(
                                            lookupDeadline.isBefore(parentDeadline)
                                                    || lookupDeadline.equals(parentDeadline))
                                    .isTrue();
                            assertThat(lookupDeadline.timeRemaining(TimeUnit.MILLISECONDS))
                                    .isLessThanOrEqualTo(3000L);
                            return "Анна Иванова";
                        });
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        Context.CancellableContext context =
                Context.current()
                        .withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims)
                        .withDeadline(parentDeadline, scheduler);
        try {
            context.run(
                    () ->
                            service.getTeacherExcuse(
                                    TeacherExcuseRequest.newBuilder()
                                            .setRequestId("ticket-1")
                                            .build(),
                                    observer));

            assertThat(observer.error).isNull();
            assertThat(observer.value.getDecisionBy()).isEqualTo(91L);
            assertThat(observer.value.getDecisionByName()).isEqualTo("Анна Иванова");
            org.mockito.Mockito.doReturn(null)
                    .when(academic)
                    .getAuditUserDisplayName(eq(91L), any());
            RecordingObserver<TeacherExcuseResponse> missingName = new RecordingObserver<>();
            context.run(
                    () ->
                            service.getTeacherExcuse(
                                    TeacherExcuseRequest.newBuilder()
                                            .setRequestId("ticket-1")
                                            .build(),
                                    missingName));
            assertThat(missingName.error).isNull();
            assertThat(missingName.value.getDecisionBy()).isEqualTo(91L);
            assertThat(missingName.value.getDecisionByName()).isEmpty();
            assertThat(missingName.value.getLessonsList())
                    .isEqualTo(observer.value.getLessonsList());
        } finally {
            context.cancel(null);
            scheduler.shutdownNow();
        }

        assertThat(observer.error).isNull();
        assertThat(observer.value.getLessonsList())
                .singleElement()
                .satisfies(value -> assertThat(value.getLessonId()).isEqualTo(101L));
    }

    @Test
    void excuseDetailDoesNotResolveDecisionNameOutsideTeacherScope() {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ExcuseRepository excuses = mock(ExcuseRepository.class);
        RequestAttachmentRepository attachments = mock(RequestAttachmentRepository.class);
        LessonResponse foreign = lesson(202L, 99L, 88L);
        when(schedule.getLessonById(202L)).thenReturn(foreign);
        doThrow(new AccessDeniedException("foreign lesson"))
                .when(report)
                .authorizeTeacherLesson(foreign, 71L);
        when(excuses.findById("ticket-foreign"))
                .thenReturn(
                        Optional.of(
                                ExcuseTicket.builder()
                                        .id("ticket-foreign")
                                        .studentId(501L)
                                        .groupId(33L)
                                        .lessonIds(List.of(202L))
                                        .decisionBy(91L)
                                        .build()));
        TeacherAttendanceReadGrpcService service =
                new TeacherAttendanceReadGrpcService(
                        report,
                        schedule,
                        academic,
                        excuses,
                        attachments,
                        Clock.fixed(Instant.parse("2025-12-02T08:00:00Z"), ZoneOffset.UTC));
        RecordingObserver<TeacherExcuseResponse> observer = new RecordingObserver<>();

        teacherContext(71L)
                .run(
                        () ->
                                service.getTeacherExcuse(
                                        TeacherExcuseRequest.newBuilder()
                                                .setRequestId("ticket-foreign")
                                                .build(),
                                        observer));

        assertThat(observer.value).isNull();
        assertThat(io.grpc.Status.fromThrowable(observer.error).getCode())
                .isEqualTo(io.grpc.Status.Code.NOT_FOUND);
        verify(academic, never()).getAuditUserDisplayName(any(), any());
    }

    @Test
    void journalReadsMixedLessonTypesButRejectsMixedContextsAndKeepsAutomaticAbsenceMark() {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ExcuseRepository excuses = mock(ExcuseRepository.class);
        RequestAttachmentRepository attachments = mock(RequestAttachmentRepository.class);

        LessonResponse own = lesson(101L, 12L, 71L);
        LessonResponse practice =
                LessonResponse.newBuilder(own)
                        .setId(202L)
                        .setDate("2025-12-02")
                        .setLessonNumber(2)
                        .setLessonType("practice")
                        .build();
        LessonResponse foreignContext =
                LessonResponse.newBuilder(own).setId(303L).setGroupId(44L).build();
        when(schedule.getLessonById(101L)).thenReturn(own);
        when(schedule.getLessonById(202L)).thenReturn(practice);
        when(schedule.getLessonById(303L)).thenReturn(foreignContext);
        when(academic.getGroup(33L))
                .thenReturn(GroupResponse.newBuilder().setId(33L).setName("УИТ-311").build());
        when(academic.getSubjectDetailsByIds(List.of(22L)))
                .thenReturn(
                        Map.of(
                                22L,
                                new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));
        when(report.getTeacherLessonAttendance(101L, 71L)).thenReturn(attendance(101L));
        when(report.getTeacherLessonAttendance(202L, 71L)).thenReturn(attendance(202L));
        when(report.getTeacherLessonAttendance(303L, 71L)).thenReturn(attendance(303L));
        when(excuses.findByLessonIdsInAndStatusIn(
                        List.of(101L),
                        List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED)))
                .thenReturn(
                        List.of(
                                ExcuseTicket.builder()
                                        .id("ticket-automatic")
                                        .studentId(501L)
                                        .lessonIds(List.of(101L))
                                        .status(ExcuseTicketStatus.SUBMITTED)
                                        .build(),
                                ExcuseTicket.builder()
                                        .id("ticket-present")
                                        .studentId(502L)
                                        .lessonIds(List.of(101L))
                                        .status(ExcuseTicketStatus.SUBMITTED)
                                        .build()));

        TeacherAttendanceReadGrpcService service =
                new TeacherAttendanceReadGrpcService(
                        report,
                        schedule,
                        academic,
                        excuses,
                        attachments,
                        Clock.fixed(Instant.parse("2025-12-02T08:00:00Z"), ZoneOffset.UTC));
        InternalJwtClaims claims =
                new InternalJwtClaims(
                        71L, UUID.randomUUID(), 1L, 1L, "TEACHER", "ACTIVE", null, false, false);

        RecordingObserver<TeacherLessonResponse> lessonObserver = new RecordingObserver<>();
        Context.current()
                .withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims)
                .run(
                        () ->
                                service.getTeacherLesson(
                                        TeacherLessonRequest.newBuilder().setLessonId(101L).build(),
                                        lessonObserver));

        assertThat(lessonObserver.error).isNull();
        assertThat(lessonObserver.value.getRosterList()).hasSize(2);
        assertThat(lessonObserver.value.getRosterList())
                .filteredOn(entry -> entry.getStudentId() == 501L)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.getStatus()).isEqualTo("absent");
                            assertThat(entry.getSymbol()).isEqualTo("н");
                            assertThat(entry.getSource()).isEqualTo("auto_scheduler");
                            assertThat(entry.getRecordPresent()).isTrue();
                            assertThat(entry.getAutoAbsent()).isTrue();
                            assertThat(entry.getPendingTicket()).isTrue();
                        });
        assertThat(lessonObserver.value.getRosterList())
                .filteredOn(entry -> entry.getStudentId() == 502L)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.getStatus()).isEqualTo("present");
                            assertThat(entry.getSymbol()).isEqualTo("+");
                            assertThat(entry.getSource()).isEqualTo("student_geo");
                            assertThat(entry.getRecordPresent()).isTrue();
                            assertThat(entry.getAutoAbsent()).isFalse();
                            assertThat(entry.getPendingTicket()).isTrue();
                        });

        RecordingObserver<TeacherJournalResponse> mixedTypesObserver = new RecordingObserver<>();
        Context.current()
                .withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims)
                .run(
                        () ->
                                service.getTeacherJournal(
                                        TeacherJournalRequest.newBuilder()
                                                .addLessonIds(101L)
                                                .addLessonIds(202L)
                                                .build(),
                                        mixedTypesObserver));

        assertThat(mixedTypesObserver.error).isNull();
        assertThat(mixedTypesObserver.value.getLessonsList())
                .extracting(lesson -> lesson.getLessonId())
                .containsExactly(101L, 202L);
        assertThat(mixedTypesObserver.value.getStudentsList())
                .filteredOn(student -> student.getStudentId() == 501L)
                .singleElement()
                .satisfies(
                        student ->
                                assertThat(student.getCellsList())
                                        .extracting(cell -> cell.getLessonId())
                                        .containsExactly(101L, 202L));
        verify(report, times(2)).getTeacherLessonAttendance(101L, 71L);
        verify(report).getTeacherLessonAttendance(202L, 71L);

        RecordingObserver<TeacherJournalResponse> journalObserver = new RecordingObserver<>();
        Context.current()
                .withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims)
                .run(
                        () ->
                                service.getTeacherJournal(
                                        TeacherJournalRequest.newBuilder()
                                                .addLessonIds(101L)
                                                .addLessonIds(303L)
                                                .build(),
                                        journalObserver));

        assertThat(journalObserver.error).isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThat(io.grpc.Status.fromThrowable(journalObserver.error).getCode())
                .isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void completedJournalProjectsMissingHistoricalMarkWithoutInventingRecordsOrTransferAbsences() {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ExcuseRepository excuses = mock(ExcuseRepository.class);
        when(schedule.getLessonById(101L)).thenReturn(lesson(101L, 12L, 71L));
        when(schedule.getLessonById(102L)).thenReturn(lesson(102L, 12L, 71L));
        when(academic.getGroup(33L))
                .thenReturn(GroupResponse.newBuilder().setName("УИТ-311").build());
        when(academic.getSubjectDetailsByIds(List.of(22L)))
                .thenReturn(
                        Map.of(
                                22L,
                                new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));
        StudentAttendanceEntry missing =
                new StudentAttendanceEntry(
                        501L,
                        "Исторический студент",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null);
        when(report.getTeacherLessonAttendance(101L, 71L))
                .thenReturn(
                        new LessonAttendanceResponse(
                                101L,
                                33L,
                                22L,
                                "2025-12-01",
                                9L,
                                "CLOSED",
                                false,
                                List.of(missing)));
        when(report.getTeacherLessonAttendance(102L, 71L))
                .thenReturn(
                        new LessonAttendanceResponse(
                                102L, 33L, 22L, "2025-12-01", 9L, "CLOSED", false, List.of()));
        var service =
                new TeacherAttendanceReadGrpcService(
                        report,
                        schedule,
                        academic,
                        excuses,
                        mock(RequestAttachmentRepository.class),
                        Clock.fixed(Instant.parse("2025-12-02T08:00:00Z"), ZoneOffset.UTC));
        var observer = new RecordingObserver<TeacherJournalResponse>();
        teacherContext(71L)
                .run(
                        () ->
                                service.getTeacherJournal(
                                        TeacherJournalRequest.newBuilder()
                                                .addLessonIds(101L)
                                                .addLessonIds(102L)
                                                .setPastOnly(true)
                                                .build(),
                                        observer));
        assertThat(observer.error).isNull();
        assertThat(observer.value.getStudentsList())
                .singleElement()
                .satisfies(
                        student -> {
                            assertThat(student.getCellsList())
                                    .singleElement()
                                    .satisfies(
                                            cell -> {
                                                assertThat(cell.getLessonId()).isEqualTo(101L);
                                                assertThat(cell.getStatus()).isEqualTo("absent");
                                                assertThat(cell.getSymbol()).isEqualTo("н");
                                                assertThat(cell.getRecordPresent()).isFalse();
                                                assertThat(cell.getAutoAbsent()).isFalse();
                                                assertThat(cell.getSource()).isEmpty();
                                            });
                        });
        var legacy = new RecordingObserver<TeacherJournalResponse>();
        teacherContext(71L)
                .run(
                        () ->
                                service.getTeacherJournal(
                                        TeacherJournalRequest.newBuilder()
                                                .addLessonIds(101L)
                                                .build(),
                                        legacy));
        assertThat(legacy.value.getStudents(0).getCells(0).getStatus()).isEmpty();
        when(schedule.getLessonById(101L))
                .thenReturn(lesson(101L, 12L, 71L).toBuilder().setStatus("planned").build());
        var planned = new RecordingObserver<TeacherJournalResponse>();
        teacherContext(71L)
                .run(
                        () ->
                                service.getTeacherJournal(
                                        TeacherJournalRequest.newBuilder()
                                                .addLessonIds(101L)
                                                .setPastOnly(true)
                                                .build(),
                                        planned));
        assertThat(io.grpc.Status.fromThrowable(planned.error).getCode())
                .isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
        // CANCELLED belongs to completed history only once its physical Moscow end has elapsed.
        LessonResponse cancelled =
                lesson(101L, 12L, 71L).toBuilder()
                        .setDate("2025-12-02")
                        .setStatus("cancelled")
                        .setEndTime("11:00")
                        .build();
        when(schedule.getLessonById(101L)).thenReturn(cancelled);
        when(report.getTeacherLessonAttendance(101L, 71L))
                .thenReturn(
                        new LessonAttendanceResponse(
                                101L,
                                33L,
                                22L,
                                "2025-12-02",
                                9L,
                                "CANCELLED",
                                false,
                                List.of(missing)));
        var ended = new RecordingObserver<TeacherJournalResponse>();
        teacherContext(71L)
                .run(
                        () ->
                                service.getTeacherJournal(
                                        TeacherJournalRequest.newBuilder()
                                                .addLessonIds(101L)
                                                .setPastOnly(true)
                                                .build(),
                                        ended));
        assertThat(ended.error).isNull();
        assertThat(ended.value.getLessonsList())
                .singleElement()
                .satisfies(
                        lesson -> {
                            assertThat(lesson.getCancelled()).isTrue();
                            assertThat(lesson.getStatus()).isEqualTo("cancelled");
                        });
        assertThat(ended.value.getStudents(0).getCellsList())
                .singleElement()
                .satisfies(
                        cell -> {
                            assertThat(cell.getStatus()).isEmpty();
                            assertThat(cell.getSymbol()).isEmpty();
                            assertThat(cell.getRecordPresent()).isFalse();
                            assertThat(cell.getAutoAbsent()).isFalse();
                            assertThat(cell.getPendingTicket()).isFalse();
                            assertThat(cell.getTicketId()).isEmpty();
                        });
        for (String status : List.of("cancelled", "active", "planned", "unknown")) {
            when(schedule.getLessonById(101L))
                    .thenReturn(
                            cancelled.toBuilder()
                                    .setStatus(status)
                                    .setEndTime("11:00:00.000000001")
                                    .build());
            var future = new RecordingObserver<TeacherJournalResponse>();
            teacherContext(71L)
                    .run(
                            () ->
                                    service.getTeacherJournal(
                                            TeacherJournalRequest.newBuilder()
                                                    .addLessonIds(101L)
                                                    .setPastOnly(true)
                                                    .build(),
                                            future));
            assertThat(io.grpc.Status.fromThrowable(future.error).getCode())
                    .isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
        }
        when(schedule.getLessonById(101L)).thenReturn(cancelled.toBuilder().setEndTime("").build());
        var invalidTime = new RecordingObserver<TeacherJournalResponse>();
        teacherContext(71L)
                .run(
                        () ->
                                service.getTeacherJournal(
                                        TeacherJournalRequest.newBuilder()
                                                .addLessonIds(101L)
                                                .setPastOnly(true)
                                                .build(),
                                        invalidTime));
        assertThat(io.grpc.Status.fromThrowable(invalidTime.error).getCode())
                .isEqualTo(io.grpc.Status.Code.UNAVAILABLE);
    }

    private static LessonAttendanceResponse attendance(long lessonId) {
        StudentAttendanceEntry entry =
                new StudentAttendanceEntry(
                        501L,
                        "Иван Петров",
                        "absent",
                        "н",
                        AttendanceSource.AUTO_SCHEDULER.name().toLowerCase(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null);
        StudentAttendanceEntry present =
                new StudentAttendanceEntry(
                        502L,
                        "Пётр Иванов",
                        "present",
                        "+",
                        "student_geo",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null);
        return new LessonAttendanceResponse(
                lessonId, 33L, 22L, "2025-12-01", 9L, "CLOSED", false, List.of(entry, present));
    }

    private static LessonResponse lesson(long lessonId, long assignmentId, long teacherId) {
        return LessonResponse.newBuilder()
                .setId(lessonId)
                .setGroupId(33L)
                .setSubjectId(22L)
                .setDate("2025-12-01")
                .setLessonNumber(1)
                .setStartTime("09:00")
                .setEndTime("10:30")
                .setStatus("closed")
                .setAssignmentId(assignmentId)
                .setSemesterId(9L)
                .setAssignedTeacherId(teacherId)
                .setLessonType("lecture")
                .build();
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {}
    }
}
