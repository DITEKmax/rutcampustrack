package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;

import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.grpc.AdminGroupStatsQuery;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Real Mongo records and the canonical calculation; Schedule/Academic authority seams are explicit.
 */
class AdminGroupStatsDataIT extends AbstractAttendanceIntegrationTest {
    @Autowired private ReportService reports;
    private AdminGroupStatsReadService service;

    @BeforeEach
    void fixture() {
        reset(scheduleGrpcClient, academicGrpcClient);
        mongoTemplate.remove(new Query(), "attendances");
        service = new AdminGroupStatsReadService(scheduleGrpcClient, reports);
    }

    @Test
    void weightedHistoricalOpportunitiesExcludeCancelledAndKeepZeroDistinctFromNoData() {
        when(scheduleGrpcClient.getLessonsByGroup(eq(10L), eq(1L), anyString(), anyString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(1, 10, 100, "2026-04-01", "closed"))
                                .addLessons(lesson(2, 10, 200, "2026-04-02", "closed"))
                                .addLessons(lesson(3, 10, 100, "2026-04-03", "cancelled"))
                                .addLessons(lesson(4, 10, 200, "2026-04-04", "active"))
                                .addLessons(lesson(5, 10, 200, "2026-04-05", "planned"))
                                .build());
        when(scheduleGrpcClient.getLessonsByGroup(eq(20L), eq(1L), anyString(), anyString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(6, 20, 300, "2026-04-02", "closed"))
                                .build());
        when(scheduleGrpcClient.getLessonsByGroup(eq(30L), eq(1L), anyString(), anyString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        roster(10, "2026-04-01", 101);
        roster(10, "2026-04-02", 101, 102, 103);
        roster(20, "2026-04-02", 201);
        mark(1, 101, AttendanceStatus.PRESENT);
        mark(2, 101, AttendanceStatus.PRESENT);
        mark(2, 102, AttendanceStatus.FREE_ATTENDANCE);
        mark(
                2,
                999,
                AttendanceStatus.PRESENT); // A stored nonmember must not enter the denominator.
        mark(3, 101, AttendanceStatus.PRESENT);
        mark(4, 101, AttendanceStatus.PRESENT);
        var result = service.read(query());
        var first =
                result.getGroupsList().stream()
                        .filter(row -> row.getGroupId() == 10)
                        .findFirst()
                        .orElseThrow();
        assertThat(first.getCountedLessons()).isEqualTo(2);
        assertThat(first.getOpportunities()).isEqualTo(4);
        assertThat(first.getPresent()).isEqualTo(2);
        assertThat(first.getExcused()).isEqualTo(1);
        assertThat(first.getAbsent()).isEqualTo(1);
        assertThat(first.getPresentPercent()).isEqualTo(50.0);
        assertThat(first.getPresentOrExcusedPercent()).isEqualTo(75.0);
        var zero =
                result.getGroupsList().stream()
                        .filter(row -> row.getGroupId() == 20)
                        .findFirst()
                        .orElseThrow();
        assertThat(zero.getOpportunities()).isEqualTo(1);
        assertThat(zero.hasPresentPercent()).isTrue();
        assertThat(zero.getPresentPercent()).isZero();
        var empty =
                result.getGroupsList().stream()
                        .filter(row -> row.getGroupId() == 30)
                        .findFirst()
                        .orElseThrow();
        assertThat(empty.getCountedLessons()).isZero();
        assertThat(empty.getOpportunities()).isZero();
        assertThat(empty.hasPresentPercent()).isFalse();
        assertThat(empty.hasPresentOrExcusedPercent()).isFalse();
    }

    @Test
    void unknownStoredStatusCannotBecomeAnAbsentMarkOrChangeTheStoredDocument() {
        when(scheduleGrpcClient.getLessonsByGroup(eq(10L), eq(1L), anyString(), anyString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(1, 10, 100, "2026-04-01", "closed"))
                                .build());
        when(scheduleGrpcClient.getLessonsByGroup(eq(20L), eq(1L), anyString(), anyString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        when(scheduleGrpcClient.getLessonsByGroup(eq(30L), eq(1L), anyString(), anyString()))
                .thenReturn(LessonsResponse.getDefaultInstance());
        roster(10, "2026-04-01", 101);
        mark(1, 101, AttendanceStatus.PRESENT);
        var collection = mongoTemplate.getCollection("attendances");
        var key = new org.bson.Document("lesson_id", 1L).append("user_id", 101L);
        collection.updateOne(
                key,
                new org.bson.Document(
                        "$set", new org.bson.Document("status", "future-status-marker")));
        var before = collection.find(key).first();
        assertThatThrownBy(() -> service.read(query()))
                .satisfies(
                        error -> {
                            Throwable cause = error;
                            boolean conflict = false;
                            for (int depth = 0; cause != null && depth < 8; depth++) {
                                if (cause
                                        instanceof
                                        ru.rutcampustrack.attendance.exception.ConflictException) {
                                    conflict = true;
                                    break;
                                }
                                cause = cause.getCause();
                            }
                            assertThat(conflict).isTrue();
                        });
        assertThat(java.util.Objects.equals(before, collection.find(key).first())).isTrue();
    }

    @Test
    void incompleteOrUnknownScheduleAuthorityCannotBecomeAnEmptyReport() {
        when(scheduleGrpcClient.getLessonsByGroup(eq(10L), eq(1L), anyString(), anyString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(1, 999, 100, "2026-04-01", "closed"))
                                .build());
        assertThatThrownBy(() -> service.read(query()))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        when(scheduleGrpcClient.getLessonsByGroup(eq(10L), eq(1L), anyString(), anyString()))
                .thenReturn(
                        LessonsResponse.newBuilder()
                                .addLessons(lesson(1, 10, 100, "2026-04-01", "future-status"))
                                .build());
        assertThatThrownBy(() -> service.read(query()))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
    }

    private static AdminGroupStatsQuery query() {
        return AdminGroupStatsQuery.newBuilder()
                .setSemesterId(1)
                .setDateFrom("2026-04-01")
                .setDateTo("2026-04-30")
                .addAllGroupIds(List.of(10L, 20L, 30L))
                .build();
    }

    private static LessonResponse lesson(
            long id, long group, long subject, String date, String status) {
        return LessonResponse.newBuilder()
                .setId(id)
                .setGroupId(group)
                .setSubjectId(subject)
                .setSemesterId(1)
                .setDate(date)
                .setLessonNumber(1)
                .setStartTime("08:30")
                .setEndTime("09:50")
                .setLessonType("lecture")
                .setStatus(status)
                .setAssignmentId(1000 + subject)
                .setAssignedTeacherId(2000 + subject)
                .build();
    }

    private void roster(long group, String date, long... members) {
        var response = GroupMembersResponse.newBuilder().setAsOfDate(date).setSemesterId(1);
        for (long id : members)
            response.addStudents(
                    StudentInfo.newBuilder()
                            .setUserId(id)
                            .setDisplayName("private-student-marker"));
        when(academicGrpcClient.getGroupMembers(group, LocalDate.parse(date), 1L))
                .thenReturn(response.build());
    }

    private void mark(long lesson, long user, AttendanceStatus status) {
        mongoTemplate
                .getCollection("attendances")
                .insertOne(
                        new org.bson.Document("lesson_id", lesson)
                                .append("user_id", user)
                                .append("group_id", 10L)
                                .append("subject_id", lesson == 2 || lesson == 4 ? 200L : 100L)
                                .append("semester_id", 1L)
                                .append("lesson_number", 1)
                                .append(
                                        "lesson_date",
                                        mongoTemplate
                                                .getConverter()
                                                .convertToMongoType(
                                                        LocalDate.of(2026, 4, (int) lesson)))
                                .append(
                                        "status",
                                        mongoTemplate.getConverter().convertToMongoType(status))
                                .append(
                                        "source",
                                        mongoTemplate
                                                .getConverter()
                                                .convertToMongoType(AttendanceSource.HEADMAN)));
    }
}
