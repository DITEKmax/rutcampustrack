package ru.rutcampustrack.attendance.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A mixed student ticket is projected only over the teacher's concrete lessons. */
class TeacherAttendanceReadGrpcServiceTest {

    @Test
    void teacherStatsMapsScheduleDependencyFailureToUnavailable() {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ExcuseRepository excuses = mock(ExcuseRepository.class);
        RequestAttachmentRepository attachments = mock(RequestAttachmentRepository.class);
        when(report.getTeacherStats(any(), eq(71L)))
                .thenThrow(new ScheduleServiceUnavailableException("schedule unavailable"));

        TeacherAttendanceReadGrpcService service = new TeacherAttendanceReadGrpcService(
                report, schedule, academic, excuses, attachments,
                Clock.fixed(Instant.parse("2025-12-02T08:00:00Z"), ZoneOffset.UTC));
        RecordingObserver<TeacherStatsResponse> observer = new RecordingObserver<>();
        InternalJwtClaims claims = new InternalJwtClaims(
                71L, UUID.randomUUID(), 1L, 1L, "TEACHER", "ACTIVE", null, false, false);

        Context.current().withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims).run(() ->
                service.getTeacherStats(TeacherStatsRequest.newBuilder()
                        .setSemesterId(9L)
                        .setScope(TeacherStatsScope.TEACHER_STATS_GROUPS)
                        .addLessonIds(101L)
                        .build(), observer));

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
        doNothing().when(report).authorizeTeacherOwnLesson(own, 71L);
        doThrow(new AccessDeniedException("foreign lesson"))
                .when(report).authorizeTeacherOwnLesson(foreign, 71L);
        when(academic.getGroup(33L)).thenReturn(GroupResponse.newBuilder().setId(33L).setName("УИТ-311").build());
        when(academic.getSubjectDetailsByIds(List.of(22L)))
                .thenReturn(Map.of(22L, new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));

        ExcuseTicket ticket = ExcuseTicket.builder()
                .id("ticket-1")
                .studentId(501L)
                .studentName("Иван Петров")
                .groupId(33L)
                .lessonIds(List.of(101L, 202L))
                .status(ExcuseTicketStatus.SUBMITTED)
                .excuseType(ExcuseType.ILLNESS)
                .comment("Болел")
                .createdAt(Instant.parse("2025-12-01T08:00:00Z"))
                .build();
        when(excuses.findById("ticket-1")).thenReturn(Optional.of(ticket));

        TeacherAttendanceReadGrpcService service = new TeacherAttendanceReadGrpcService(
                report, schedule, academic, excuses, attachments,
                Clock.fixed(Instant.parse("2025-12-02T08:00:00Z"), ZoneOffset.UTC));
        RecordingObserver<TeacherExcuseResponse> observer = new RecordingObserver<>();
        InternalJwtClaims claims = new InternalJwtClaims(
                71L, UUID.randomUUID(), 1L, 1L, "TEACHER", "ACTIVE", null, false, false);

        Context.current().withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims).run(() ->
                service.getTeacherExcuse(TeacherExcuseRequest.newBuilder()
                        .setRequestId("ticket-1").build(), observer));

        assertThat(observer.error).isNull();
        assertThat(observer.value.getLessonsList()).singleElement()
                .satisfies(value -> assertThat(value.getLessonId()).isEqualTo(101L));
    }

    @Test
    void journalReadsMixedLessonTypesButRejectsMixedContextsAndKeepsAutomaticAbsenceMark() {
        ReportService report = mock(ReportService.class);
        ScheduleGrpcClient schedule = mock(ScheduleGrpcClient.class);
        AcademicGrpcClient academic = mock(AcademicGrpcClient.class);
        ExcuseRepository excuses = mock(ExcuseRepository.class);
        RequestAttachmentRepository attachments = mock(RequestAttachmentRepository.class);

        LessonResponse own = lesson(101L, 12L, 71L);
        LessonResponse practice = LessonResponse.newBuilder(own)
                .setId(202L)
                .setDate("2025-12-02")
                .setLessonNumber(2)
                .setLessonType("practice")
                .build();
        LessonResponse foreignContext = LessonResponse.newBuilder(own)
                .setId(303L)
                .setGroupId(44L)
                .build();
        when(schedule.getLessonById(101L)).thenReturn(own);
        when(schedule.getLessonById(202L)).thenReturn(practice);
        when(schedule.getLessonById(303L)).thenReturn(foreignContext);
        when(academic.getGroup(33L)).thenReturn(GroupResponse.newBuilder()
                .setId(33L).setName("УИТ-311").build());
        when(academic.getSubjectDetailsByIds(List.of(22L)))
                .thenReturn(Map.of(22L, new AcademicGrpcClient.SubjectDetails("Математика", "lecture")));
        when(report.getTeacherLessonAttendance(101L, 71L))
                .thenReturn(attendance(101L));
        when(report.getTeacherLessonAttendance(202L, 71L))
                .thenReturn(attendance(202L));
        when(report.getTeacherLessonAttendance(303L, 71L))
                .thenReturn(attendance(303L));
        when(excuses.findByLessonIdsInAndStatusIn(
                List.of(101L), List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED)))
                .thenReturn(List.of(ExcuseTicket.builder()
                        .id("ticket-automatic")
                        .studentId(501L)
                        .lessonIds(List.of(101L))
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .build(), ExcuseTicket.builder()
                        .id("ticket-present")
                        .studentId(502L)
                        .lessonIds(List.of(101L))
                        .status(ExcuseTicketStatus.SUBMITTED)
                        .build()));

        TeacherAttendanceReadGrpcService service = new TeacherAttendanceReadGrpcService(
                report, schedule, academic, excuses, attachments,
                Clock.fixed(Instant.parse("2025-12-02T08:00:00Z"), ZoneOffset.UTC));
        InternalJwtClaims claims = new InternalJwtClaims(
                71L, UUID.randomUUID(), 1L, 1L, "TEACHER", "ACTIVE", null, false, false);

        RecordingObserver<TeacherLessonResponse> lessonObserver = new RecordingObserver<>();
        Context.current().withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims).run(() ->
                service.getTeacherLesson(TeacherLessonRequest.newBuilder()
                        .setLessonId(101L).build(), lessonObserver));

        assertThat(lessonObserver.error).isNull();
        assertThat(lessonObserver.value.getRosterList()).hasSize(2);
        assertThat(lessonObserver.value.getRosterList())
                .filteredOn(entry -> entry.getStudentId() == 501L)
                .singleElement()
                .satisfies(entry -> {
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
                .satisfies(entry -> {
                    assertThat(entry.getStatus()).isEqualTo("present");
                    assertThat(entry.getSymbol()).isEqualTo("+");
                    assertThat(entry.getSource()).isEqualTo("student_geo");
                    assertThat(entry.getRecordPresent()).isTrue();
                    assertThat(entry.getAutoAbsent()).isFalse();
                    assertThat(entry.getPendingTicket()).isTrue();
                });

        RecordingObserver<TeacherJournalResponse> mixedTypesObserver = new RecordingObserver<>();
        Context.current().withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims).run(() ->
                service.getTeacherJournal(TeacherJournalRequest.newBuilder()
                        .addLessonIds(101L).addLessonIds(202L).build(), mixedTypesObserver));

        assertThat(mixedTypesObserver.error).isNull();
        assertThat(mixedTypesObserver.value.getLessonsList())
                .extracting(lesson -> lesson.getLessonId())
                .containsExactly(101L, 202L);
        assertThat(mixedTypesObserver.value.getStudentsList())
                .filteredOn(student -> student.getStudentId() == 501L)
                .singleElement()
                .satisfies(student -> assertThat(student.getCellsList())
                        .extracting(cell -> cell.getLessonId())
                        .containsExactly(101L, 202L));
        verify(report, times(2)).getTeacherLessonAttendance(101L, 71L);
        verify(report).getTeacherLessonAttendance(202L, 71L);

        RecordingObserver<TeacherJournalResponse> journalObserver = new RecordingObserver<>();
        Context.current().withValue(TeacherAttendanceGrpcIdentity.CLAIMS, claims).run(() ->
                service.getTeacherJournal(TeacherJournalRequest.newBuilder()
                        .addLessonIds(101L).addLessonIds(303L).build(), journalObserver));

        assertThat(journalObserver.error).isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThat(io.grpc.Status.fromThrowable(journalObserver.error).getCode())
                .isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
    }

    private static LessonAttendanceResponse attendance(long lessonId) {
        StudentAttendanceEntry entry = new StudentAttendanceEntry(
                501L, "Иван Петров", "absent", "н",
                AttendanceSource.AUTO_SCHEDULER.name().toLowerCase(),
                null, null, null, null, null, null, null, false, null);
        StudentAttendanceEntry present = new StudentAttendanceEntry(
                502L, "Пётр Иванов", "present", "+", "student_geo",
                null, null, null, null, null, null, null, false, null);
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

        @Override public void onNext(T value) { this.value = value; }
        @Override public void onError(Throwable error) { this.error = error; }
        @Override public void onCompleted() { }
    }
}
