package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for RabbitMQ event consumers.
 * Publishes real messages to the fanout exchange and verifies MongoDB state.
 * Proves MARK-03, MARK-04, MARK-05 requirements.
 */
class EventConsumerIntegrationTest extends AbstractAttendanceIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate.dropCollection(AttendanceDocument.class);
        Mockito.reset(scheduleGrpcClient, academicGrpcClient, semesterCacheService);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private Map<String, Object> buildEnvelope(String eventType, Map<String, Object> payload) {
        return Map.of(
                "event_type", eventType,
                "event_id", UUID.randomUUID().toString(),
                "occurred_at", Instant.now().toString(),
                "payload", payload
        );
    }

    private void publishEvent(Map<String, Object> envelope) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", envelope);
    }

    private void mockGrpcForLesson(Long lessonId, Long groupId, Long subjectId,
                                   String date, int lessonNumber, Long... studentIds) {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(lessonId).setGroupId(groupId).setSubjectId(subjectId)
                .setDate(date).setLessonNumber(lessonNumber).setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(lessonId)).thenReturn(lesson);

        GroupMembersResponse.Builder members = GroupMembersResponse.newBuilder();
        for (Long sid : studentIds) {
            members.addStudents(StudentInfo.newBuilder()
                    .setUserId(sid)
                    .setDisplayName("Student " + sid)
                    .build());
        }
        when(academicGrpcClient.getGroupMembers(groupId)).thenReturn(members.build());
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);
    }

    // -------------------------------------------------------------------------
    // Test 1 — MARK-03: lesson.closed with no existing records creates ABSENT for all
    // -------------------------------------------------------------------------

    @Test
    void lessonClosed_noExistingRecords_createsAbsentForAllStudents() {
        mockGrpcForLesson(1L, 10L, 5L, "2026-04-01", 1, 100L, 101L, 102L);

        publishEvent(buildEnvelope("lesson.closed", Map.of(
                "lesson_id", 1,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).hasSize(3);
            for (AttendanceDocument doc : docs) {
                assertThat(doc.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
                assertThat(doc.getSource()).isEqualTo(AttendanceSource.AUTO_SCHEDULER);
                assertThat(doc.getMarkedBy()).isNull();
                assertThat(doc.getGroupId()).isEqualTo(10L);
                assertThat(doc.getSubjectId()).isEqualTo(5L);
                assertThat(doc.getSemesterId()).isEqualTo(1L);
                assertThat(doc.getLessonNumber()).isEqualTo(1);
                assertThat(doc.getLessonDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Test 2 — MARK-04: lesson.closed preserves existing PRESENT checkin
    // -------------------------------------------------------------------------

    @Test
    void lessonClosed_existingCheckin_preservesCheckinStatus() {
        // Pre-seed student 100 with PRESENT status
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .markedBy(null).createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        mockGrpcForLesson(1L, 10L, 5L, "2026-04-01", 1, 100L, 101L);

        publishEvent(buildEnvelope("lesson.closed", Map.of(
                "lesson_id", 1,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).hasSize(2);
        });

        // Student 100 must still be PRESENT (not overwritten)
        AttendanceDocument doc100 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(100L)),
                AttendanceDocument.class);
        assertThat(doc100).isNotNull();
        assertThat(doc100.getStatus()).isEqualTo(AttendanceStatus.PRESENT);

        // Student 101 was not pre-seeded, must be ABSENT
        AttendanceDocument doc101 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(101L)),
                AttendanceDocument.class);
        assertThat(doc101).isNotNull();
        assertThat(doc101.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    // -------------------------------------------------------------------------
    // Test 3 — MARK-03 + MARK-04: partial checkins, only unmarked get ABSENT
    // -------------------------------------------------------------------------

    @Test
    void lessonClosed_partialCheckins_createsAbsentOnlyForUnmarked() {
        // Pre-seed student 100 with PRESENT, student 101 with EXCUSED
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(101L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.EXCUSED).source(AttendanceSource.HEADMAN)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        mockGrpcForLesson(1L, 10L, 5L, "2026-04-01", 1, 100L, 101L, 102L);

        publishEvent(buildEnvelope("lesson.closed", Map.of(
                "lesson_id", 1,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).hasSize(3);
        });

        // Student 100: PRESENT preserved
        AttendanceDocument doc100 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(100L)),
                AttendanceDocument.class);
        assertThat(doc100.getStatus()).isEqualTo(AttendanceStatus.PRESENT);

        // Student 101: EXCUSED preserved
        AttendanceDocument doc101 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(101L)),
                AttendanceDocument.class);
        assertThat(doc101.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);

        // Student 102: new ABSENT
        AttendanceDocument doc102 = mongoTemplate.findOne(
                Query.query(Criteria.where("user_id").is(102L)),
                AttendanceDocument.class);
        assertThat(doc102.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    // -------------------------------------------------------------------------
    // Test 4 — MARK-05: lesson.cancelled updates all existing docs to CANCELLED
    // -------------------------------------------------------------------------

    @Test
    void lessonCancelled_existingDocs_updatesStatusToCancelled() {
        Instant before = Instant.now();

        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(100L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.PRESENT).source(AttendanceSource.STUDENT_GEO)
                .createdAt(before).updatedAt(before)
                .build());
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(101L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.ABSENT).source(AttendanceSource.AUTO_SCHEDULER)
                .createdAt(before).updatedAt(before)
                .build());
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(1L).userId(102L).groupId(10L).subjectId(5L)
                .semesterId(1L).lessonNumber(1).lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.EXCUSED).source(AttendanceSource.HEADMAN)
                .createdAt(before).updatedAt(before)
                .build());

        publishEvent(buildEnvelope("lesson.cancelled", Map.of(
                "lesson_id", 1,
                "group_id", 10,
                "subject_id", 5,
                "date", "2026-04-01"
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).hasSize(3);
            for (AttendanceDocument doc : docs) {
                assertThat(doc.getStatus()).isEqualTo(AttendanceStatus.CANCELLED);
                assertThat(doc.getUpdatedAt()).isAfter(before);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Test 5 — MARK-05 edge case: lesson.cancelled with no docs does not error
    // -------------------------------------------------------------------------

    @Test
    void lessonCancelled_noDocs_noError() {
        publishEvent(buildEnvelope("lesson.cancelled", Map.of(
                "lesson_id", 999,
                "group_id", 10
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AttendanceDocument> docs = mongoTemplate.findAll(AttendanceDocument.class);
            assertThat(docs).isEmpty();
        });
    }

    // -------------------------------------------------------------------------
    // Test 6 — D-09: semester.archived triggers SemesterCacheService.refresh()
    // -------------------------------------------------------------------------

    @Test
    void semesterArchived_refreshesSemesterCache() {
        publishEvent(buildEnvelope("semester.archived", Map.of(
                "semester_id", 1
        )));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                verify(semesterCacheService).refresh()
        );
    }
}
