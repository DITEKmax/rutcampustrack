package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinService;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LegacyLateDecisionPersistenceIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        AuditTestIsolation.register(registry, "legacylatedecision", 11);
    }

    @Autowired private LateCheckinService service;
    @Autowired private LateCheckinRepository requests;

    @org.junit.jupiter.api.BeforeEach
    void clearOwnedFixtures() {
        for (String collection :
                List.of(
                        "late_checkin_requests",
                        "attendances",
                        "lesson_actions",
                        "attendance_outbox",
                        "student_checkin_pairs",
                        "semester_archive_fences"))
            mongoTemplate.getCollection(collection).deleteMany(new Document());
    }

    @Test
    void staleApprovedDecisionPreservesEveryPersistedFieldAndIntent() {
        Instant now = Instant.parse("2026-04-01T08:00:00Z");
        var request =
                requests.save(
                        LateCheckinRequest.builder()
                                .studentId(100L)
                                .groupId(10L)
                                .lessonId(419L)
                                .semesterId(30L)
                                .subjectId(7L)
                                .status(LateCheckinRequestStatus.APPROVED)
                                .decisionBy(777L)
                                .decisionAt(now)
                                .createdAt(now.minusSeconds(60))
                                .updatedAt(now)
                                .build());
        attendanceRepository.save(
                AttendanceDocument.builder()
                        .lessonId(419L)
                        .userId(100L)
                        .groupId(10L)
                        .subjectId(7L)
                        .semesterId(30L)
                        .status(AttendanceStatus.PRESENT)
                        .source(AttendanceSource.LATE_CHECKIN)
                        .markedBy(777L)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
        var before = new java.util.LinkedHashMap<String, List<Document>>();
        for (String collection :
                List.of(
                        "late_checkin_requests",
                        "attendances",
                        "lesson_actions",
                        "attendance_outbox",
                        "student_checkin_pairs",
                        "semester_archive_fences")) before.put(collection, rows(collection));
        service.applyDecision(request.getId(), 777L, false);
        before.forEach(
                (collection, original) ->
                        assertThat(rows(collection))
                                .as(collection)
                                .containsExactlyElementsOf(original));
    }

    @Test
    void rejectionCommitsOneDecisionIntentAndPreservesAttendanceThenStaleDeliveryChangesNothing() {
        var now = Instant.parse("2026-04-01T08:00:00Z");
        attendanceRepository.save(
                AttendanceDocument.builder()
                        .lessonId(418L)
                        .userId(100L)
                        .groupId(10L)
                        .subjectId(7L)
                        .semesterId(30L)
                        .lessonDate(LocalDate.of(2026, 4, 1))
                        .lessonNumber(2)
                        .status(AttendanceStatus.ABSENT)
                        .source(AttendanceSource.HEADMAN)
                        .markedBy(777L)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
        var request =
                requests.save(
                        LateCheckinRequest.builder()
                                .studentId(100L)
                                .groupId(10L)
                                .lessonId(418L)
                                .semesterId(30L)
                                .subjectId(7L)
                                .lessonDate(LocalDate.of(2026, 4, 1))
                                .lessonNumber(2)
                                .status(LateCheckinRequestStatus.PENDING)
                                .createdAt(now)
                                .updatedAt(now)
                                .build());
        when(academicGrpcClient.isHeadman(777L, 10L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.HeadmanCheckResponse.newBuilder()
                                .setIsHeadman(true)
                                .build());
        when(academicGrpcClient.getSubjectsByIds(List.of(7L)))
                .thenReturn(java.util.Map.of(7L, "Math"));
        when(scheduleGrpcClient.requireAttendanceMutationReady(418L, 10L))
                .thenReturn(
                        LessonResponse.newBuilder()
                                .setId(418L)
                                .setGroupId(10L)
                                .setSubjectId(7L)
                                .setSemesterId(30L)
                                .setDate("2026-04-01")
                                .setLessonNumber(2)
                                .setStatus("closed")
                                .build());
        var marks = rows("attendances");
        service.applyDecision(request.getId(), 777L, false);
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(LateCheckinRequestStatus.REJECTED);
        assertThat(rows("attendances")).containsExactlyElementsOf(marks);
        assertThat(
                        mongoTemplate
                                .getCollection("attendance_outbox")
                                .countDocuments(new Document("event_type", "late_checkin.decided")))
                .isEqualTo(1);
        var snapshots = new java.util.LinkedHashMap<String, List<Document>>();
        for (String collection :
                List.of(
                        "late_checkin_requests",
                        "attendances",
                        "lesson_actions",
                        "attendance_outbox",
                        "student_checkin_pairs",
                        "semester_archive_fences")) snapshots.put(collection, rows(collection));
        service.applyDecision(request.getId(), 777L, true);
        snapshots.forEach(
                (collection, before) ->
                        assertThat(rows(collection))
                                .as(collection)
                                .containsExactlyElementsOf(before));
    }

    private List<Document> rows(String collection) {
        return mongoTemplate.getCollection(collection).find().into(new ArrayList<>());
    }
}
