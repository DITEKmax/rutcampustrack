package ru.rutcampustrack.attendance.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.annotation.DirtiesContext;

import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for {@code lesson.one_off.cancelled} event consumer (AC-08, D-22).
 *
 * <p>Publishes real RabbitMQ events on the fanout exchange and verifies that {@link
 * ru.rutcampustrack.attendance.event.LessonEventService#processOneOffLessonCancelled}
 * cascade-deletes attendance docs matched by (group_id, lesson_date, lesson_number).
 *
 * <p>Idempotency is exercised by publishing the same event twice and ensuring no failure — MongoDB
 * {@code remove} returns 0 deletedCount on the second pass, without exception.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OneOffLessonCancelledConsumerIT extends AbstractAttendanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void auditIsolation(org.springframework.test.context.DynamicPropertyRegistry registry) {
        ru.rutcampustrack.attendance.integration.AuditTestIsolation.register(
                registry, "oneofflessoncancelledconsumerit", 8);
    }

    @Autowired private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanCollection() {
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
    }

    private Map<String, Object> envelope(Long groupId, LocalDate date, int lessonNumber) {
        return Map.of(
                "event_type",
                "lesson.one_off.cancelled",
                "event_id",
                UUID.randomUUID().toString(),
                "occurred_at",
                Instant.now().toString(),
                "event_version",
                1,
                "source",
                "schedule-service",
                "trace_id",
                "one-off-cancel-correlation",
                "payload",
                Map.of(
                        "group_id",
                        groupId,
                        "semester_id",
                        1L,
                        "subject_id",
                        7,
                        "date",
                        date.toString(),
                        "lesson_number",
                        lessonNumber));
    }

    private AttendanceDocument doc(Long groupId, LocalDate date, int lessonNumber, Long userId) {
        Instant now = Instant.now();
        return AttendanceDocument.builder()
                .lessonId(
                        999L) // one-off lesson id — irrelevant for this path; delete key is (group,
                // date, slot)
                .userId(userId)
                .groupId(groupId)
                .subjectId(7L)
                .semesterId(1L)
                .lessonNumber(lessonNumber)
                .lessonDate(date)
                .status(AttendanceStatus.PRESENT)
                .source(AttendanceSource.STUDENT_GEO)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void publish(Map<String, Object> env) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", env);
    }

    @Test
    void cancellation_deletesAttendanceDocs() {
        LocalDate date = LocalDate.of(2026, 5, 1);
        mongoTemplate.save(doc(1L, date, 2, 100L));
        mongoTemplate.save(doc(1L, date, 2, 101L));

        publish(envelope(1L, date, 2));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            long remaining =
                                    mongoTemplate.count(
                                            Query.query(
                                                    Criteria.where("group_id")
                                                            .is(1L)
                                                            .and("lesson_date")
                                                            .is(date)
                                                            .and("lesson_number")
                                                            .is(2)),
                                            AttendanceDocument.class);
                            assertThat(remaining).isZero();
                        });
    }

    @Test
    void cancellation_idempotent() {
        LocalDate date = LocalDate.of(2026, 5, 1);
        mongoTemplate.save(doc(1L, date, 2, 100L));

        var foreign = mongoTemplate.save(doc(2L, date, 2, 200L));
        var foreignBefore =
                mongoTemplate
                        .getCollection("attendances")
                        .find(new org.bson.Document("user_id", 200L))
                        .first();
        var event = envelope(1L, date, 2);
        publish(event);
        awaitReceiptAndAcknowledgements(event, 1);
        var receipt =
                mongoTemplate
                        .getCollection("semester_archive_effect_receipts")
                        .find(new org.bson.Document("_id", event.get("event_id") + ":ATTENDANCE"))
                        .first();
        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).hasSize(1);
        assertThat(
                        mongoTemplate
                                .getCollection("attendances")
                                .find(new org.bson.Document("user_id", 200L))
                                .first())
                .isEqualTo(foreignBefore);
        publish(event);
        awaitReceiptAndAcknowledgements(event, 2);
        assertThat(
                        mongoTemplate
                                .getCollection("semester_archive_effect_receipts")
                                .find(
                                        new org.bson.Document(
                                                "_id", event.get("event_id") + ":ATTENDANCE"))
                                .first())
                .isEqualTo(receipt);
        assertThat(mongoTemplate.findAll(AttendanceDocument.class)).hasSize(1);
        assertThat(
                        mongoTemplate
                                .getCollection("attendances")
                                .find(new org.bson.Document("user_id", 200L))
                                .first())
                .isEqualTo(foreignBefore);
        assertThat(rabbitTemplate.receive("attendance-service.events.dlq")).isNull();
    }

    private void awaitReceiptAndAcknowledgements(Map<String, Object> event, int count) {
        String id = event.get("event_id").toString();
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            assertThat(
                                            mongoTemplate
                                                    .getCollection(
                                                            "semester_archive_effect_receipts")
                                                    .countDocuments(
                                                            new org.bson.Document(
                                                                    "_id", id + ":ATTENDANCE")))
                                    .isEqualTo(1);
                            assertThat(
                                            mongoTemplate
                                                    .getCollection(
                                                            "semester_archive_effect_receipts")
                                                    .find(
                                                            new org.bson.Document(
                                                                    "_id", id + ":ATTENDANCE"))
                                                    .first()
                                                    .getString("result"))
                                    .isEqualTo("APPLIED");
                            assertThat(
                                            mongoTemplate.count(
                                                    Query.query(
                                                            Criteria.where("event_type")
                                                                    .is(
                                                                            "semester.archive.effect.ack")
                                                                    .and("payload")
                                                                    .regex(id)),
                                                    "attendance_outbox"))
                                    .isEqualTo(count);
                        });
    }

    @Test
    void cancellation_doesNotAffectOtherDocs() {
        LocalDate date = LocalDate.of(2026, 5, 1);
        LocalDate otherDate = LocalDate.of(2026, 5, 2);

        // target doc — must be deleted
        mongoTemplate.save(doc(1L, date, 2, 100L));
        // different group — must stay
        mongoTemplate.save(doc(2L, date, 2, 200L));
        // different date — must stay
        mongoTemplate.save(doc(1L, otherDate, 2, 101L));
        // different lesson_number — must stay
        mongoTemplate.save(doc(1L, date, 3, 102L));

        publish(envelope(1L, date, 2));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> {
                            List<AttendanceDocument> all =
                                    mongoTemplate.findAll(AttendanceDocument.class);
                            assertThat(all).hasSize(3);
                            assertThat(all)
                                    .noneMatch(
                                            d ->
                                                    d.getGroupId() == 1L
                                                            && date.equals(d.getLessonDate())
                                                            && d.getLessonNumber() == 2);
                        });
    }
}
