package ru.rutcampustrack.mobilebff.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAssignment;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

class DirectedBotTeacherDayTest {
    @Test
    void sameDayCompositionUsesExactOwnAssignmentsWithoutHttpActorAndEducationalClock() {
        var context = mock(MobileRequestContext.class);
        var academic = mock(MobileAcademicClient.class);
        var schedule = mock(MobileScheduleClient.class);
        var attendance = mock(MobileAttendanceClient.class);
        Instant educationalNow = Instant.parse("2026-10-17T21:15:00Z");
        var facade =
                new TeacherReadFacade(
                        context,
                        academic,
                        schedule,
                        attendance,
                        Clock.fixed(educationalNow, ZoneOffset.UTC));
        LocalDate day = educationalNow.atZone(java.time.ZoneId.of("Europe/Moscow")).toLocalDate();
        var assignment =
                TeacherAssignment.newBuilder()
                        .setAssignmentId(12)
                        .setTeacherId(71)
                        .setGroupId(33)
                        .setGroupName("УИТ-311")
                        .setSubjectId(22)
                        .setSubjectName("Математика")
                        .setSemesterId(9)
                        .setLessonType("lecture")
                        .setValidFrom("2026-09-01")
                        .setValidUntilExclusive("2026-12-01")
                        .build();
        var snapshot =
                TeacherAssignmentsResponse.newBuilder()
                        .addAssignments(assignment)
                        .addAssignments(assignment.toBuilder().setAssignmentId(13).setTeacherId(72))
                        .build();
        var own =
                LessonResponse.newBuilder()
                        .setId(101)
                        .setAssignmentId(12)
                        .setAssignedTeacherId(71)
                        .setGroupId(33)
                        .setSubjectId(22)
                        .setSemesterId(9)
                        .setLessonType("lecture")
                        .setDate(day.toString())
                        .setStartTime("00:00")
                        .setEndTime("00:30")
                        .setStatus("active")
                        .setLessonNumber(1)
                        .build();
        var lessons =
                LessonsResponse.newBuilder()
                        .addLessons(own)
                        .addLessons(
                                own.toBuilder()
                                        .setId(102)
                                        .setAssignedTeacherId(72)
                                        .setAssignmentId(13))
                        .addLessons(own.toBuilder().setId(103).setGroupId(99))
                        .addLessons(own.toBuilder().setId(104).setDate("2026-10-17"))
                        .addLessons(own.toBuilder().setId(105).setAssignmentId(0))
                        .build();
        var result =
                facade.directedBotDay(
                        71,
                        9,
                        day,
                        snapshot,
                        groupId -> {
                            assertThat(groupId).isEqualTo(33);
                            return lessons;
                        });
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(result.serverNow()).isEqualTo(educationalNow);
        assertThat(result.lessons()).extracting(lesson -> lesson.id()).containsExactly("101");
        assertThat(result.lessons().getFirst().groupName()).isEqualTo("УИТ-311");
        verifyNoInteractions(context, academic, schedule, attendance);
        assertThat(
                        facade.directedBotDay(
                                        71,
                                        9,
                                        day,
                                        TeacherAssignmentsResponse.getDefaultInstance(),
                                        ignored -> {
                                            throw new AssertionError(
                                                    "no groups without assignments");
                                        })
                                .lessons())
                .isEqualTo(List.of());
    }
}
