package ru.rutcampustrack.attendance.event;

import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Mongo proof for digest drift, semester scope and physical attachment removal. */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
@Import({AttendanceSemesterDeletionData.class, AttendanceSemesterDeletionDataIT.TestConfig.class})
class AttendanceSemesterDeletionDataIT {

    private static final long SEMESTER_ID = 40L;
    private static final long OTHER_SEMESTER_ID = 41L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-30T09:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-31T09:00:00Z");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
            return new MongoTransactionManager(factory);
        }
    }

    @Autowired private AttendanceSemesterDeletionData deletionData;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private MongoTransactionManager mongoTransactionManager;

    @BeforeEach
    void cleanCollections() {
        for (String collection : List.of("attendances", "excuse_tickets", "late_checkin_requests",
                "request_attachments", "student_late_checkin_budgets", "student_request_receipts",
                "student_checkin_receipts", "lesson_cancellation_markers", "semester_archive_fences",
                "semester_deletion_participant_receipts", "semester_deletion_tombstones",
                "attendance_outbox")) {
            mongoTemplate.dropCollection(collection);
        }
    }

    @Test
    void digestTracksOnlyOwnedDomainRows_andDeleteRemovesTheirBinaryPayloads() {
        seedSemesterRows();
        var preview = deletionData.preview(SEMESTER_ID);
        assertThat(preview.attendanceMarks()).isEqualTo(1);
        assertThat(preview.studentRequests()).isEqualTo(2);
        assertThat(preview.participantDigest()).matches("[0-9a-f]{64}");

        // Control rows and retained idempotency receipts are not deletion inputs.
        mongoTemplate.insert(new Document("_id", Long.toString(SEMESTER_ID))
                .append("semester_id", SEMESTER_ID)
                .append("barrier_state", "DELETE_PREPARING"), "semester_archive_fences");
        mongoTemplate.insert(new Document("_id", "request-replay")
                .append("request_id", "request-40"), "student_request_receipts");
        mongoTemplate.insert(new Document("_id", "participant-prepare")
                .append("status", "PENDING"), "semester_deletion_participant_receipts");
        mongoTemplate.insert(new Document("_id", "ack-outbox")
                .append("event_type", "semester.archive.participant.ack"), "attendance_outbox");
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is("marker-40-302")),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("marked_at", Date.from(UPDATED_AT)),
                "lesson_cancellation_markers");
        assertThat(deletionData.preview(SEMESTER_ID).participantDigest())
                .isEqualTo(preview.participantDigest());

        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is("mark-40")),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("status", "ABSENT")
                        .set("updated_at", Date.from(UPDATED_AT.plusSeconds(1))),
                "attendances");
        assertThatThrownBy(() -> new TransactionTemplate(mongoTransactionManager)
                .executeWithoutResult(status -> deletionData.delete(SEMESTER_ID, preview.participantDigest())))
                .isInstanceOf(AttendanceSemesterDeletionData.DigestMismatchException.class);
        assertThat(mongoTemplate.findById("mark-40", Document.class, "attendances")).isNotNull();
        assertThat(mongoTemplate.findById("request-file-40", Document.class, "request_attachments")).isNotNull();

        var sealed = deletionData.preview(SEMESTER_ID);
        new TransactionTemplate(mongoTransactionManager)
                .executeWithoutResult(status -> deletionData.delete(SEMESTER_ID, sealed.participantDigest()));

        for (String collection : List.of("attendances", "excuse_tickets", "late_checkin_requests",
                "request_attachments", "student_late_checkin_budgets")) {
            assertThat(mongoTemplate.count(Query.query(Criteria.where("semester_id").is(SEMESTER_ID)),
                    collection)).isZero();
        }
        assertThat(mongoTemplate.findById("request-file-40", Document.class, "request_attachments")).isNull();
        assertThat(mongoTemplate.findById("legacy-ticket-file-40", Document.class, "request_attachments"))
                .isNull();
        assertThat(mongoTemplate.findById("journal-file-legacy-40", Document.class, "request_attachments")).isNull();
        assertThat(mongoTemplate.findById("marker-legacy-file-40", Document.class, "request_attachments"))
                .isNull();
        assertThat(mongoTemplate.findById("unproven-legacy-orphan", Document.class, "request_attachments"))
                .isNotNull();
        assertThat(mongoTemplate.findById("mismatched-owner-legacy-file", Document.class, "request_attachments"))
                .isNotNull();
        assertThat(mongoTemplate.findById("marker-40-302", Document.class, "lesson_cancellation_markers"))
                .isNotNull();
        assertThat(mongoTemplate.findById("foreign-semester-file", Document.class, "request_attachments")).isNotNull();
        assertThat(mongoTemplate.findById("foreign-owner-legacy-file", Document.class, "request_attachments"))
                .isNotNull();
        assertThat(mongoTemplate.findById("request-replay", Document.class, "student_request_receipts"))
                .isNotNull();
        assertThat(mongoTemplate.findById("participant-prepare", Document.class,
                "semester_deletion_participant_receipts")).isNotNull();
        assertThat(mongoTemplate.findById("ack-outbox", Document.class, "attendance_outbox")).isNotNull();
        assertThat(deletionData.preview(SEMESTER_ID).attendanceMarks()).isZero();
        assertThat(deletionData.preview(SEMESTER_ID).studentRequests()).isZero();
    }

    private void seedSemesterRows() {
        ObjectId excuseTicketId = new ObjectId();
        mongoTemplate.insert(new Document("_id", "mark-40")
                .append("lesson_id", 300L)
                .append("user_id", 700L)
                .append("group_id", 90L)
                .append("semester_id", SEMESTER_ID)
                .append("status", "PRESENT")
                .append("created_at", Date.from(CREATED_AT))
                .append("updated_at", Date.from(UPDATED_AT)), "attendances");
        mongoTemplate.insert(new Document("_id", excuseTicketId)
                .append("student_id", 700L)
                .append("group_id", 90L)
                .append("semester_id", SEMESTER_ID)
                .append("lesson_ids", List.of(300L))
                .append("status", "SUBMITTED")
                .append("created_at", Date.from(CREATED_AT))
                .append("updated_at", Date.from(UPDATED_AT)), "excuse_tickets");
        mongoTemplate.insert(new Document("_id", "late-request-40")
                .append("student_id", 700L)
                .append("group_id", 90L)
                .append("lesson_id", 300L)
                .append("semester_id", SEMESTER_ID)
                .append("status", "PENDING")
                .append("created_at", Date.from(CREATED_AT))
                .append("updated_at", Date.from(UPDATED_AT)), "late_checkin_requests");
        mongoTemplate.insert(new Document("_id", "request-file-40")
                .append("request_id", excuseTicketId.toHexString())
                .append("owner_student_id", 700L)
                .append("group_id", 90L)
                .append("semester_id", SEMESTER_ID)
                .append("name", "request.pdf")
                .append("size", 3L)
                .append("sha256", "a".repeat(64))
                .append("state", "ACTIVE")
                .append("data", new Binary(new byte[]{1, 2, 3})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "legacy-ticket-file-40")
                .append("request_id", excuseTicketId.toHexString())
                .append("owner_student_id", 700L)
                .append("group_id", 90L)
                .append("name", "legacy-ticket.pdf")
                .append("size", 3L)
                .append("sha256", "e".repeat(64))
                .append("state", "ACTIVE")
                .append("data", new Binary(new byte[]{9, 8, 7})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "journal-file-legacy-40")
                .append("request_id", "700:300")
                .append("owner_student_id", 700L)
                .append("group_id", 90L)
                .append("name", "journal.pdf")
                .append("size", 3L)
                .append("sha256", "b".repeat(64))
                .append("state", "ACTIVE")
                .append("data", new Binary(new byte[]{4, 5, 6})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "marker-40-302")
                .append("lesson_id", 302L)
                .append("semester_id", SEMESTER_ID)
                .append("marked_at", Date.from(CREATED_AT)), "lesson_cancellation_markers");
        mongoTemplate.insert(new Document("_id", "marker-legacy-file-40")
                .append("request_id", "701:302")
                .append("owner_student_id", 701L)
                .append("group_id", 90L)
                .append("name", "cancelled-lesson.pdf")
                .append("size", 3L)
                .append("sha256", "f".repeat(64))
                .append("state", "ACTIVE")
                .append("data", new Binary(new byte[]{6, 5, 4})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "unproven-legacy-orphan")
                .append("request_id", "702:303")
                .append("owner_student_id", 702L)
                .append("group_id", 91L)
                .append("name", "unproven.pdf")
                .append("size", 1L)
                .append("sha256", "1".repeat(64))
                .append("state", "ACTIVE")
                .append("data", new Binary(new byte[]{3})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "mismatched-owner-legacy-file")
                .append("request_id", "703:302")
                .append("owner_student_id", 704L)
                .append("group_id", 90L)
                .append("name", "mismatched-owner.pdf")
                .append("size", 1L)
                .append("sha256", "2".repeat(64))
                .append("state", "ACTIVE")
                .append("data", new Binary(new byte[]{2})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "foreign-semester-file")
                .append("request_id", "other-request")
                .append("owner_student_id", 800L)
                .append("semester_id", OTHER_SEMESTER_ID)
                .append("sha256", "c".repeat(64))
                .append("data", new Binary(new byte[]{7})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "foreign-owner-legacy-file")
                .append("request_id", "request-40")
                .append("owner_student_id", 800L)
                .append("sha256", "d".repeat(64))
                .append("data", new Binary(new byte[]{8})), "request_attachments");
        mongoTemplate.insert(new Document("_id", "700:40")
                .append("student_id", 700L)
                .append("semester_id", SEMESTER_ID)
                .append("limit", 4)
                .append("used", 1)
                .append("updated_at", Date.from(UPDATED_AT)), "student_late_checkin_budgets");
    }
}
