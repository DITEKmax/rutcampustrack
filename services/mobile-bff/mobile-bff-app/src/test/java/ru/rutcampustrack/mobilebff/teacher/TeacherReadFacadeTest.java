package ru.rutcampustrack.mobilebff.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.rutcampustrack.academic.grpc.SubjectInfo;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsResponse;
import ru.rutcampustrack.academic.grpc.TeacherSubjectInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.mobilebff.error.MobileBffException;
import ru.rutcampustrack.mobilebff.grpc.MobileAcademicClient;
import ru.rutcampustrack.mobilebff.grpc.MobileAttendanceClient;
import ru.rutcampustrack.mobilebff.grpc.MobileScheduleClient;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.teacher.grpc.TeacherAssignment;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalCell;
import ru.rutcampustrack.teacher.grpc.TeacherJournalResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalStudent;
import ru.rutcampustrack.teacher.grpc.TeacherLessonSummary;

/** The server filters a selected journal period and types before pagination. */
@ExtendWith(MockitoExtension.class)
class TeacherReadFacadeTest {
    @Mock private MobileRequestContext requestContext;
    @Mock private MobileAcademicClient academic;
    @Mock private MobileScheduleClient schedule;
    @Mock private MobileAttendanceClient attendance;

    @Test
    void journalFiltersSelectedTypesAndPeriodBeforePaginationForActiveGroup() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, "УИТ-311"));
        when(academic.teacherAssignmentsForSemester(9L))
                .thenReturn(
                        TeacherAssignmentsResponse.newBuilder()
                                .addAssignments(
                                        TeacherAssignment.newBuilder()
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
                                .setSemesterDateFrom("2026-09-01")
                                .setSemesterDateTo("2026-09-30")
                                .build());

        LessonResponse sep1 =
                lesson(101L, "2026-09-01", 1, "09:00:00").toBuilder()
                        .setAssignedTeacherId(72L)
                        .build();
        LessonResponse sep8 =
                lesson(108L, "2026-09-08", 2, "10:40:00").toBuilder()
                        .setAssignedTeacherId(72L)
                        .build();
        LessonResponse sep7Lab =
                lesson(107L, "2026-09-07", 3, "12:20:00").toBuilder().setLessonType("lab").build();
        LessonResponse sep6OtherType =
                lesson(106L, "2026-09-06", 4, "14:00:00").toBuilder()
                        .setLessonType("seminar")
                        .build();
        when(schedule.lessons(33L, 9L, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 8)))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(sep8)
                                .addLessons(sep6OtherType)
                                .addLessons(sep1)
                                .addLessons(sep7Lab)
                                .build());
        when(attendance.teacherJournal(List.of(107L))).thenReturn(journalResponse(107L));

        TeacherReadFacade facade =
                new TeacherReadFacade(requestContext, academic, schedule, attendance);
        var result =
                facade.journal(
                        9L,
                        33L,
                        22L,
                        List.of("lecture", "lab"),
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 8),
                        0,
                        1);

        assertThat(result.totalLessons()).isEqualTo(2);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.lessons())
                .extracting(lesson -> lesson.date())
                .containsExactly(LocalDate.of(2026, 9, 7));
        assertThat(result.students())
                .singleElement()
                .satisfies(student -> assertThat(student.cells()).hasSize(1));
        verify(schedule).lessons(33L, 9L, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 8));
        verify(attendance).teacherJournal(List.of(107L));
    }

    @Test
    void dayIncludesOnlyLessonsWithTheTeachersExactAssignment() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        when(academic.teacherAssignments(9L, "2026-09-10", "2026-09-10"))
                .thenReturn(
                        TeacherAssignmentsResponse.newBuilder()
                                .addAssignments(assignment(0L, 71L))
                                .addAssignments(assignment(12L, 71L))
                                .addAssignments(assignment(13L, 72L))
                                .build());

        LessonResponse ownLesson = lesson(101L, "2026-09-10", 1, "09:00:00");
        LessonResponse otherTeacherLesson =
                lesson(102L, "2026-09-10", 2, "10:40:00").toBuilder()
                        .setAssignmentId(13L)
                        .setAssignedTeacherId(72L)
                        .build();
        LessonResponse wrongAssignmentId =
                lesson(103L, "2026-09-10", 3, "12:20:00").toBuilder().setAssignmentId(13L).build();
        LessonResponse missingAssignmentId =
                lesson(104L, "2026-09-10", 4, "14:00:00").toBuilder().setAssignmentId(0L).build();
        LessonResponse teacherMismatch =
                lesson(105L, "2026-09-10", 5, "15:40:00").toBuilder()
                        .setAssignedTeacherId(72L)
                        .build();
        LessonResponse contextMismatch =
                lesson(106L, "2026-09-10", 6, "17:20:00").toBuilder().setSubjectId(23L).build();
        when(schedule.lessons(33L, 9L, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10)))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(otherTeacherLesson)
                                .addLessons(wrongAssignmentId)
                                .addLessons(missingAssignmentId)
                                .addLessons(teacherMismatch)
                                .addLessons(contextMismatch)
                                .addLessons(ownLesson)
                                .build());

        TeacherReadFacade facade =
                new TeacherReadFacade(requestContext, academic, schedule, attendance);
        var result = facade.day(9L, LocalDate.of(2026, 9, 10));

        assertThat(result.lessons()).extracting(lesson -> lesson.id()).containsExactly("101");
    }

    @Test
    void statsAndExportPreserveSortColumnsDirectionsAndPriorityForSuffixAndPrefixForms() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, "УИТ-311"));
        when(academic.teacherAssignmentsForSemester(9L))
                .thenReturn(
                        TeacherAssignmentsResponse.newBuilder()
                                .setSemesterDateFrom(today.minusDays(1).toString())
                                .setSemesterDateTo(today.plusDays(1).toString())
                                .addAssignments(
                                        assignment(12L, 71L).toBuilder()
                                                .setValidFrom(today.minusDays(1).toString())
                                                .setValidUntilExclusive(
                                                        today.plusDays(2).toString()))
                                .build());
        when(schedule.lessons(33L, 9L, today.minusDays(1), today.plusDays(1)))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(attendance.teacherStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        ru.rutcampustrack.teacher.grpc.TeacherStatsResponse.newBuilder()
                                .setSemesterId(9L)
                                .setServerNow(java.time.Instant.now().toString())
                                .build());
        when(attendance.exportTeacherStats(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("pdf")))
                .thenReturn(
                        ru.rutcampustrack.teacher.grpc.TeacherStatsExportResponse.newBuilder()
                                .setFileName("stats.pdf")
                                .setContentType("application/pdf")
                                .setContent(
                                        com.google.protobuf.ByteString.copyFromUtf8(
                                                "synthetic-pdf"))
                                .build());
        var facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);
        var sorts = List.of("present:asc", "absent:desc", "excused", "-presentOrExcused");
        facade.stats(9L, "students", 33L, 22L, List.of("lecture"), sorts, List.of());
        facade.exportStats(9L, "students", 33L, 22L, List.of("lecture"), sorts, List.of(), "pdf");
        var stats =
                org.mockito.ArgumentCaptor.forClass(
                        ru.rutcampustrack.teacher.grpc.TeacherStatsRequest.class);
        var exported =
                org.mockito.ArgumentCaptor.forClass(
                        ru.rutcampustrack.teacher.grpc.TeacherStatsRequest.class);
        verify(attendance).teacherStats(stats.capture());
        verify(attendance)
                .exportTeacherStats(exported.capture(), org.mockito.ArgumentMatchers.eq("pdf"));
        assertThat(stats.getValue().getSortsList())
                .extracting(ru.rutcampustrack.teacher.grpc.TeacherStatsSort::getColumn)
                .containsExactly("present", "absent", "excused", "presentOrExcused");
        assertThat(stats.getValue().getSortsList())
                .extracting(ru.rutcampustrack.teacher.grpc.TeacherStatsSort::getDescending)
                .containsExactly(false, true, false, true);
        assertThat(exported.getValue()).isEqualTo(stats.getValue());
    }

    @Test
    void completedJournalFiltersBeforeTotalAndOffsetPreservingGroupHistory() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, "УИТ-311"));
        when(academic.teacherAssignmentsForSemester(9L))
                .thenReturn(
                        TeacherAssignmentsResponse.newBuilder()
                                .setSemesterDateFrom("2026-09-01")
                                .setSemesterDateTo("2026-09-30")
                                .build());
        LessonResponse closed =
                lesson(101L, "2026-09-10", 1, "09:00").toBuilder()
                        .setAssignedTeacherId(88L)
                        .setStatus("CLOSED")
                        .build();
        when(schedule.lessons(33L, 9L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(
                                        closed.toBuilder()
                                                .setId(102L)
                                                .setDate("2026-09-01")
                                                .setStatus("PLANNED"))
                                .addLessons(closed.toBuilder().setId(103L).setStatus("CANCELLED"))
                                .addLessons(closed)
                                .addLessons(closed.toBuilder().setId(104L))
                                .build());
        when(attendance.teacherJournal(List.of(104L), true)).thenReturn(journalResponse(104L));
        var result =
                new TeacherReadFacade(requestContext, academic, schedule, attendance)
                        .journal(9L, 33L, 22L, List.of("lecture"), null, null, 1, 1, true);
        assertThat(result.totalLessons()).isEqualTo(2);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.lessons()).extracting(value -> value.id()).containsExactly("104");
    }

    private static TeacherAssignment assignment(long assignmentId, long teacherId) {
        return TeacherAssignment.newBuilder()
                .setAssignmentId(assignmentId)
                .setTeacherId(teacherId)
                .setGroupId(33L)
                .setGroupName("УИТ-311")
                .setSubjectId(22L)
                .setSubjectName("Математика")
                .setSemesterId(9L)
                .setLessonType("lecture")
                .setValidFrom("2026-09-01")
                .setValidUntilExclusive("2026-10-01")
                .build();
    }

    @Test
    void catalogUsesCurrentAuthorityForHistoricalScheduledSubjectsWithoutAttendanceDependency() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, "УИТ-311"));
        when(academic.teacherAssignmentsForSemester(9L))
                .thenReturn(
                        TeacherAssignmentsResponse.newBuilder()
                                .setSemesterDateFrom("2026-09-01")
                                .setSemesterDateTo("2026-09-30")
                                .addAssignments(assignment(99L, 71L).toBuilder().setGroupId(44L))
                                .build());
        LessonResponse historical =
                lesson(101L, "2026-09-01", 1, "09:00:00").toBuilder()
                        .setSubjectId(23L)
                        .setAssignedTeacherId(72L)
                        .clearAssignmentId()
                        .setLessonType("LAB")
                        .build();
        LessonResponse lecture =
                historical.toBuilder().setId(102L).setLessonType("lecture").build();
        when(schedule.lessons(33L, 9L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(historical)
                                .addLessons(lecture)
                                .addLessons(historical)
                                .build());
        when(academic.teacherCatalogSubjects(List.of(23L)))
                .thenReturn(
                        SubjectsByIdsResponse.newBuilder()
                                .addSubjects(
                                        SubjectInfo.newBuilder()
                                                .setSubjectId(23L)
                                                .setSubjectName("Физика"))
                                .build());
        var facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);

        var result = facade.catalog(9L);

        assertThat(result.groups())
                .singleElement()
                .satisfies(
                        group -> {
                            assertThat(group.id()).isEqualTo("33");
                            assertThat(group.name()).isEqualTo("УИТ-311");
                        });
        assertThat(result.subjectOptions())
                .singleElement()
                .satisfies(
                        option -> {
                            assertThat(option.groupId()).isEqualTo("33");
                            assertThat(option.subjectId()).isEqualTo("23");
                            assertThat(option.subjectName()).isEqualTo("Физика");
                            assertThat(option.lessonTypes()).containsExactly("lab", "lecture");
                        });
        assertThat(result.taughtSubjectOptions())
                .singleElement()
                .satisfies(
                        option -> {
                            assertThat(option.subjectId()).isEqualTo("22");
                            assertThat(option.lessonTypes()).containsExactly("lecture");
                        });
        verify(academic).currentTeacherSubjects(71L);
        verify(academic).teacherCatalogSubjects(List.of(23L));
        verify(schedule, never())
                .lessons(
                        org.mockito.ArgumentMatchers.eq(44L),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(attendance);

        clearInvocations(academic, schedule);
        when(academic.currentTeacherSubjects(71L))
                .thenReturn(TeacherSubjectsResponse.getDefaultInstance());
        var empty = facade.catalog(9L);
        assertThat(empty.groups()).isEmpty();
        assertThat(empty.subjectOptions()).isEmpty();
        verifyNoInteractions(schedule, attendance);
        verify(academic, never()).teacherCatalogSubjects(org.mockito.ArgumentMatchers.any());

        clearInvocations(academic, schedule);
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "STUDENT",
                                "ACTIVE",
                                33L,
                                false,
                                false));
        assertThatThrownBy(() -> facade.catalog(9L))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(academic, schedule, attendance);
    }

    @Test
    void catalogRejectsMalformedAuthorityAndIncompleteOrContradictoryScheduleMetadata() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        var facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);
        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, ""));
        assertThatThrownBy(() -> facade.catalog(9L))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error ->
                                assertThat(error.status())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(schedule, attendance);

        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, "УИТ-311"));
        when(academic.teacherAssignmentsForSemester(9L))
                .thenReturn(
                        TeacherAssignmentsResponse.newBuilder()
                                .setSemesterDateFrom("2026-09-01")
                                .setSemesterDateTo("2026-09-30")
                                .build());
        LessonResponse lesson = lesson(101L, "2026-09-01", 1, "09:00:00");
        when(schedule.lessons(33L, 9L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(LessonsResponse.newBuilder().addLessons(lesson).build());
        when(academic.teacherCatalogSubjects(List.of(22L)))
                .thenReturn(SubjectsByIdsResponse.getDefaultInstance());
        assertThatThrownBy(() -> facade.catalog(9L))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error ->
                                assertThat(error.status())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        when(schedule.lessons(33L, 9L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson)
                                .addLessons(lesson.toBuilder().setSubjectId(23L))
                                .build());
        assertThatThrownBy(() -> facade.catalog(9L))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error ->
                                assertThat(error.status())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(attendance);
    }

    private static TeacherSubjectsResponse currentGroup(long groupId, String groupName) {
        return TeacherSubjectsResponse.newBuilder()
                .addSubjects(
                        TeacherSubjectInfo.newBuilder()
                                .setAssignmentId(14L)
                                .setSemesterId(10L)
                                .setSubjectId(22L)
                                .setSubjectName("Математика")
                                .setGroupId(groupId)
                                .setGroupName(groupName)
                                .setLessonType("lecture"))
                .build();
    }

    @Test
    void searchNormalizesTokensAndPublishesOnlyCurrentMinimalMetadata() {
        searchContext();
        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, "УИТ-311"));
        when(academic.teacherSearchGroup(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                                .setId(33L)
                                .setName("УИТ-311")
                                .build());
        var student =
                ru.rutcampustrack.academic.grpc.StudentInfo.newBuilder()
                        .setUserId(501L)
                        .setDisplayName("Фёдор Иванов")
                        .setTelegramId(987L)
                        .setIsHeadman(true)
                        .build();
        when(academic.teacherSearchMembers(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupMembersResponse.newBuilder()
                                .addStudents(student)
                                .addStudents(student)
                                .addStudents(
                                        student.toBuilder()
                                                .setUserId(502L)
                                                .setDisplayName("Иван Петров"))
                                .build());
        var result =
                new TeacherReadFacade(requestContext, academic, schedule, attendance)
                        .search(9L, "  ИВАНОВ\tфед ");
        assertThat(result.groups()).isEmpty();
        assertThat(result.students())
                .singleElement()
                .satisfies(
                        value -> {
                            assertThat(value.id()).isEqualTo("501");
                            assertThat(value.groupId()).isEqualTo("33");
                            assertThat(value.groupName()).isEqualTo("УИТ-311");
                        });
        assertThat(result.truncated()).isFalse();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);
        assertThat(json.get("students").get(0).size()).isEqualTo(4);
        assertThat(json.toString()).doesNotContain("telegram", "isHeadman", "987");
        verifyNoInteractions(schedule, attendance);
    }

    @Test
    void searchBlankAndInvalidQueriesNeverLoadRostersOrMetrics() {
        searchContext();
        var facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);
        assertThat(facade.search(9L, " \t ").students()).isEmpty();
        assertThat(facade.search(9L, "").truncated()).isFalse();
        for (String q : List.of("я", "x".repeat(129))) {
            assertThatThrownBy(() -> facade.search(9L, q))
                    .isInstanceOfSatisfying(
                            MobileBffException.class,
                            error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(academic, never()).teacherSearchMembers(org.mockito.ArgumentMatchers.anyLong());
        verify(academic, never()).currentTeacherSubjects(org.mockito.ArgumentMatchers.anyLong());
        verifyNoInteractions(schedule, attendance);
    }

    @Test
    void searchCapsBothListsWithStableOrderingAndDedup() {
        searchContext();
        var authority = TeacherSubjectsResponse.newBuilder();
        for (long id = 21; id >= 1; id--) {
            String name = "Группа " + id;
            authority.addAllSubjects(currentGroup(id, name).getSubjectsList());
            when(academic.teacherSearchGroup(id))
                    .thenReturn(
                            ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                                    .setId(id)
                                    .setName(name)
                                    .build());
            var roster = ru.rutcampustrack.academic.grpc.GroupMembersResponse.newBuilder();
            for (long studentId = 3; studentId >= 1; studentId--)
                roster.addStudents(
                        ru.rutcampustrack.academic.grpc.StudentInfo.newBuilder()
                                .setUserId(id * 10 + studentId)
                                .setDisplayName("Группа студент " + id + " " + studentId));
            when(academic.teacherSearchMembers(id)).thenReturn(roster.build());
        }
        when(academic.currentTeacherSubjects(71L)).thenReturn(authority.build());
        var result =
                new TeacherReadFacade(requestContext, academic, schedule, attendance)
                        .search(9L, "группа");
        assertThat(result.groups()).hasSize(20);
        assertThat(result.students()).hasSize(50);
        assertThat(result.groups().get(0).id()).isEqualTo("1");
        assertThat(result.groups().get(1).id()).isEqualTo("10");
        assertThat(result.truncated()).isTrue();
        verifyNoInteractions(schedule, attendance);
    }

    @Test
    void searchDiscardsRevokedAuthorityAndRejectsWrongGroupAndHistoricalRoster() {
        searchContext();
        var facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);
        when(academic.currentTeacherSubjects(71L))
                .thenReturn(
                        currentGroup(33L, "УИТ-311"), TeacherSubjectsResponse.getDefaultInstance());
        when(academic.teacherSearchGroup(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                                .setId(33L)
                                .setName("УИТ-311")
                                .build());
        when(academic.teacherSearchMembers(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupMembersResponse.newBuilder()
                                .addStudents(
                                        ru.rutcampustrack.academic.grpc.StudentInfo.newBuilder()
                                                .setUserId(501L)
                                                .setDisplayName("Иван Иванов"))
                                .build());
        assertThatThrownBy(() -> facade.search(9L, "иван"))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND));
        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, "УИТ-311"));
        when(academic.teacherSearchGroup(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                                .setId(44L)
                                .setName("УИТ-311")
                                .build());
        assertThatThrownBy(() -> facade.search(9L, "иван"))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error ->
                                assertThat(error.status())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        when(academic.teacherSearchGroup(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                                .setId(33L)
                                .setName("УИТ-311")
                                .build());
        when(academic.teacherSearchMembers(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupMembersResponse.newBuilder()
                                .setAsOfDate("2026-04-01")
                                .build());
        assertThatThrownBy(() -> facade.search(9L, "иван"))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error ->
                                assertThat(error.status())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(schedule, attendance);
    }

    @Test
    void statsBothScopesRejectIncompleteSelectionBeforeDependencies() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        var facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);
        for (String scope : List.of("students", "groups")) {
            assertThatThrownBy(
                            () ->
                                    facade.stats(
                                            9L,
                                            scope,
                                            null,
                                            22L,
                                            List.of("lecture"),
                                            List.of(),
                                            List.of()))
                    .isInstanceOf(MobileBffException.class);
            assertThatThrownBy(
                            () ->
                                    facade.stats(
                                            9L, scope, 33L, 22L, List.of(), List.of(), List.of()))
                    .isInstanceOf(MobileBffException.class);
            for (List<String> invalidTypes :
                    List.of(List.of("garbage"), List.of("lecture", "garbage"))) {
                assertThatThrownBy(
                                () ->
                                        facade.stats(
                                                9L,
                                                scope,
                                                33L,
                                                22L,
                                                invalidTypes,
                                                List.of(),
                                                List.of()))
                        .isInstanceOfSatisfying(
                                MobileBffException.class,
                                error ->
                                        assertThat(error.status())
                                                .isEqualTo(HttpStatus.BAD_REQUEST));
                assertThatThrownBy(
                                () ->
                                        facade.exportStats(
                                                9L,
                                                scope,
                                                33L,
                                                22L,
                                                invalidTypes,
                                                List.of(),
                                                List.of(),
                                                "pdf"))
                        .isInstanceOfSatisfying(
                                MobileBffException.class,
                                error ->
                                        assertThat(error.status())
                                                .isEqualTo(HttpStatus.BAD_REQUEST));
            }
            assertThatThrownBy(
                            () ->
                                    facade.exportStats(
                                            9L,
                                            scope,
                                            33L,
                                            null,
                                            List.of("lecture"),
                                            List.of(),
                                            List.of(),
                                            "pdf"))
                    .isInstanceOf(MobileBffException.class);
        }
        verifyNoInteractions(academic, schedule, attendance);
    }

    @Test
    void searchRejectsMalformedRosterDependencyFailureAndWrongRoleWithoutPartialNames() {
        searchContext();
        when(academic.currentTeacherSubjects(71L)).thenReturn(currentGroup(33L, "УИТ-311"));
        when(academic.teacherSearchGroup(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupResponse.newBuilder()
                                .setId(33L)
                                .setName("УИТ-311")
                                .build());
        var facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);
        when(academic.teacherSearchMembers(33L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.GroupMembersResponse.newBuilder()
                                .addStudents(
                                        ru.rutcampustrack.academic.grpc.StudentInfo.newBuilder()
                                                .setUserId(501L)
                                                .setDisplayName("Иван Иванов"))
                                .addStudents(
                                        ru.rutcampustrack.academic.grpc.StudentInfo.newBuilder()
                                                .setDisplayName("Иван Петров"))
                                .build());
        assertThatThrownBy(() -> facade.search(9L, "иван"))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error ->
                                assertThat(error.status())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        when(academic.teacherSearchMembers(33L))
                .thenThrow(
                        new MobileBffException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                ru.rutcampustrack.mobilebff.contract.model.StudentApiModels
                                        .ProblemCode.DEPENDENCY_UNAVAILABLE,
                                "Dependency unavailable"));
        assertThatThrownBy(() -> facade.search(9L, "иван"))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error ->
                                assertThat(error.status())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        clearInvocations(academic);
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "STUDENT",
                                "ACTIVE",
                                33L,
                                false,
                                false));
        assertThatThrownBy(() -> facade.search(9L, "иван"))
                .isInstanceOfSatisfying(
                        MobileBffException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(academic, schedule, attendance);
    }

    @Test
    void groupStatsAndExportUseOnlySelectedGroupHistoricalSubjectAndTypes() {
        searchContext();
        when(academic.currentTeacherSubjects(71L))
                .thenReturn(
                        currentGroup(33L, "УИТ-311").toBuilder()
                                .addAllSubjects(currentGroup(44L, "УИТ-312").getSubjectsList())
                                .build());
        when(schedule.lessons(33L, 9L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(
                                        lesson(101L, "2026-09-02", 1, "09:00").toBuilder()
                                                .setSubjectId(23L)
                                                .setAssignedTeacherId(72L))
                                .addLessons(lesson(102L, "2026-09-02", 2, "10:40"))
                                .addLessons(
                                        lesson(103L, "2026-09-02", 3, "12:20").toBuilder()
                                                .setSubjectId(23L)
                                                .setLessonType("lab"))
                                .build());
        when(attendance.teacherStats(org.mockito.ArgumentMatchers.any()))
                .thenReturn(
                        ru.rutcampustrack.teacher.grpc.TeacherStatsResponse.newBuilder()
                                .setSemesterId(9L)
                                .build());
        when(attendance.exportTeacherStats(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("pdf")))
                .thenReturn(
                        ru.rutcampustrack.teacher.grpc.TeacherStatsExportResponse.newBuilder()
                                .setFileName("stats.pdf")
                                .setContentType("application/pdf")
                                .setContent(
                                        com.google.protobuf.ByteString.copyFromUtf8(
                                                "synthetic-pdf"))
                                .build());
        var facade = new TeacherReadFacade(requestContext, academic, schedule, attendance);
        facade.stats(9L, "groups", 33L, 23L, List.of("LECTURE"), List.of(), List.of());
        facade.exportStats(9L, "groups", 33L, 23L, List.of("lecture"), List.of(), List.of(), "pdf");
        var stats =
                org.mockito.ArgumentCaptor.forClass(
                        ru.rutcampustrack.teacher.grpc.TeacherStatsRequest.class);
        var exported =
                org.mockito.ArgumentCaptor.forClass(
                        ru.rutcampustrack.teacher.grpc.TeacherStatsRequest.class);
        verify(attendance).teacherStats(stats.capture());
        verify(attendance)
                .exportTeacherStats(exported.capture(), org.mockito.ArgumentMatchers.eq("pdf"));
        // Full selected-group batch preserves historical subject metadata independently of the
        // metric types.
        assertThat(stats.getValue().getLessonIdsList()).containsExactly(101L, 102L, 103L);
        assertThat(stats.getValue().getLessonTypesList()).containsExactly("lecture");
        assertThat(stats.getValue().getGroupId()).isEqualTo(33L);
        assertThat(stats.getValue().getSubjectId()).isEqualTo(23L);
        assertThat(exported.getValue()).isEqualTo(stats.getValue());
        verify(schedule, never())
                .lessons(
                        org.mockito.ArgumentMatchers.eq(44L),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private void searchContext() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        when(academic.teacherAssignmentsForSemester(9L))
                .thenReturn(
                        TeacherAssignmentsResponse.newBuilder()
                                .setSemesterDateFrom("2026-09-01")
                                .setSemesterDateTo("2026-09-30")
                                .build());
    }

    @Test
    void authorizedHistoricalLessonWithAbsentBindingDoesNotPublishFakeZeroId() {
        when(requestContext.claims())
                .thenReturn(
                        new InternalJwtClaims(
                                71L,
                                UUID.randomUUID(),
                                1L,
                                1L,
                                "TEACHER",
                                "ACTIVE",
                                null,
                                false,
                                false));
        when(attendance.teacherLesson(101L))
                .thenReturn(
                        ru.rutcampustrack.teacher.grpc.TeacherLessonResponse.newBuilder()
                                .setLesson(
                                        summary(101L, "2026-09-07", 3, "12:20:00").toBuilder()
                                                .clearAssignmentId()
                                                .clearAssignedTeacherId())
                                .setServerNow("2026-09-09T09:00:00Z")
                                .build());
        var result =
                new TeacherReadFacade(requestContext, academic, schedule, attendance).lesson(101L);
        assertThat(result.lesson().assignmentId()).isNull();
        com.fasterxml.jackson.databind.JsonNode json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules()
                        .valueToTree(result);
        assertThat(json.get("lesson").get("assignmentId").isNull()).isTrue();
        verify(attendance).teacherLesson(101L);
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

    private static TeacherJournalResponse journalResponse(long... lessonIds) {
        TeacherJournalResponse.Builder response =
                TeacherJournalResponse.newBuilder().setServerNow("2026-09-09T09:00:00Z");
        TeacherJournalStudent.Builder student =
                TeacherJournalStudent.newBuilder().setStudentId(501L).setDisplayName("Иван Иванов");
        for (long lessonId : lessonIds) {
            response.addLessons(summary(lessonId, "2026-09-07", 3, "12:20:00"));
            student.addCells(
                    TeacherJournalCell.newBuilder()
                            .setLessonId(lessonId)
                            .setStatus("present")
                            .setSymbol("+")
                            .setRecordPresent(true));
        }
        response.addStudents(student);
        return response.build();
    }

    private static TeacherLessonSummary summary(
            long id, String date, int number, String startTime) {
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
