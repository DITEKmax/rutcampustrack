package ru.rutcampustrack.mobilebff.teacher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAssignment;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalCell;
import ru.rutcampustrack.teacher.grpc.TeacherJournalResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalStudent;
import ru.rutcampustrack.teacher.grpc.TeacherLessonSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Semester journal discovery must load concrete lessons across dates in one server scope. */
@ExtendWith(MockitoExtension.class)
class TeacherReadFacadeTest {
    @Mock
    private MobileRequestContext requestContext;
    @Mock
    private MobileAcademicClient academic;
    @Mock
    private MobileScheduleClient schedule;
    @Mock
    private MobileAttendanceClient attendance;

    @Test
    void journalDiscoversConcreteLessonsOnTwoDatesForOneContext() {
        when(requestContext.claims()).thenReturn(new InternalJwtClaims(
                71L, UUID.fromString("33333333-3333-4333-8333-333333333333"),
                1L, 1L, "TEACHER", "ACTIVE", null, false, false));
        when(academic.teacherAssignmentsForSemester(9L)).thenReturn(TeacherAssignmentsResponse.newBuilder()
                .addAssignments(TeacherAssignment.newBuilder()
                        .setAssignmentId(12L)
                        .setTeacherId(71L)
                        .setGroupId(33L)
                        .setGroupName("УИТ-311")
                        .setSubjectId(22L)
                        .setSubjectName("Математика")
                        .setSemesterId(9L)
                        .setLessonType("lecture")
                        .setValidFrom("2026-09-01")
                        .setValidUntilExclusive("2026-10-01")
                        .build())
                .build());

        LessonResponse sep1 = lesson(101L, "2026-09-01", 1, "09:00:00");
        LessonResponse sep8 = lesson(108L, "2026-09-08", 2, "10:40:00");
        when(schedule.lessons(33L, 9L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(LessonsResponse.newBuilder().addLessons(sep8).addLessons(sep1).build());
        when(attendance.teacherJournal(List.of(101L, 108L))).thenReturn(journalResponse());

        TeacherReadFacade facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);
        var result = facade.journal(9L, 33L, 22L, "lecture", 0, 100);

        assertThat(result.totalLessons()).isEqualTo(2);
        assertThat(result.lessons()).extracting(lesson -> lesson.date())
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8));
        assertThat(result.students()).singleElement().satisfies(student ->
                assertThat(student.cells()).hasSize(2));
        verify(schedule).lessons(33L, 9L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        verify(attendance).teacherJournal(List.of(101L, 108L));
    }

    private static LessonResponse lesson(long id, String date, int number, String startTime) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setScheduleItemId(id + 1000)
                .setGroupId(33L)
                .setSubjectId(22L)
                .setDate(date)
                .setLessonNumber(number)
                .setStartTime(startTime)
                .setEndTime("10:30:00")
                .setStatus("PLANNED")
                .setRoom("301")
                .setAssignmentId(12L)
                .setSemesterId(9L)
                .setAssignedTeacherId(71L)
                .setLessonType("lecture")
                .setGeneration(1)
                .setRevision(1)
                .build();
    }

    private static TeacherJournalResponse journalResponse() {
        TeacherJournalCell sep1 = TeacherJournalCell.newBuilder()
                .setLessonId(101L).setStatus("present").setSymbol("+")
                .setRecordPresent(true).build();
        TeacherJournalCell sep8 = TeacherJournalCell.newBuilder()
                .setLessonId(108L).setStatus("present").setSymbol("+")
                .setRecordPresent(true).build();
        return TeacherJournalResponse.newBuilder()
                .addLessons(summary(101L, "2026-09-01", 1, "09:00:00"))
                .addLessons(summary(108L, "2026-09-08", 2, "10:40:00"))
                .addStudents(TeacherJournalStudent.newBuilder()
                        .setStudentId(501L)
                        .setDisplayName("Иван Иванов")
                        .addCells(sep1)
                        .addCells(sep8)
                        .build())
                .setServerNow("2026-09-09T09:00:00Z")
                .build();
    }

    private static TeacherLessonSummary summary(long id, String date, int number, String startTime) {
        return TeacherLessonSummary.newBuilder()
                .setLessonId(id)
                .setScheduleItemId(id + 1000)
                .setGroupId(33L)
                .setGroupName("УИТ-311")
                .setSubjectId(22L)
                .setSubjectName("Математика")
                .setSemesterId(9L)
                .setAssignmentId(12L)
                .setAssignedTeacherId(71L)
                .setLessonType("lecture")
                .setLessonDate(date)
                .setLessonNumber(number)
                .setStartTime(startTime)
                .setEndTime("10:30:00")
                .setRoom("301")
                .setStatus("PLANNED")
                .setGeneration(1)
                .setRevision(1)
                .build();
    }
}
