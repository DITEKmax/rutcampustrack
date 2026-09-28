package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.event.HomeworkBindingArchivedEventConsumer;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HomeworkBindingArchiveIT extends AbstractAcademicIntegrationTest {

    private static final String CONSUMER_ID = "academic-homework-archive";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private HomeworkPublicationPersistence publicationPersistence;
    @Autowired private HomeworkBindingArchivedEventConsumer archiveConsumer;

    private final List<UUID> eventIds = new ArrayList<>();

    private long bindingId;
    private long actorId;
    private long adminId;
    private long groupId;
    private long subjectId;
    private long semesterId;
    private int semesterYear;
    private UUID requestKey;
    private byte[] payloadHash;

    @BeforeEach
    void setUpFixture() {
        eventIds.clear();
        bindingId = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 1_000_000_000_000_000L);
        requestKey = UUID.randomUUID();
        payloadHash = new byte[32];
        ThreadLocalRandom.current().nextBytes(payloadHash);
        semesterYear = ThreadLocalRandom.current().nextInt(2200, 3000);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            actorId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE login = 'student'", Long.class);
            adminId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE login = 'admin'", Long.class);
            groupId = jdbcTemplate.queryForObject(
                    "SELECT id FROM groups WHERE name = 'ИВТ-211'", Long.class);
            subjectId = jdbcTemplate.queryForObject(
                    "INSERT INTO subjects (name, type, group_id) VALUES (?, 'lecture', ?) RETURNING id",
                    Long.class, "archive-it-subject-" + UUID.randomUUID(), groupId);
            jdbcTemplate.update(
                    "INSERT INTO subject_lesson_types (subject_id, lesson_type) VALUES (?, 'lecture')",
                    subjectId);
            semesterId = jdbcTemplate.queryForObject(
                    "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                            + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                    Long.class, "archive-it-semester-" + UUID.randomUUID(),
                    LocalDate.of(semesterYear, 1, 1), LocalDate.of(semesterYear, 12, 31));
        });
    }

    @AfterEach
    void cleanFixture() {
        if (groupId == 0) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (UUID eventId : eventIds) {
                jdbcTemplate.update(
                        "DELETE FROM event_consumer_processed WHERE consumer_id = ? AND event_id = ?",
                        CONSUMER_ID, eventId);
            }
            jdbcTemplate.update(
                    "DELETE FROM academic_outbox WHERE payload -> 'payload' ->> 'homework_id' "
                            + "IN (SELECT id::text FROM homeworks WHERE binding_id = ?)",
                    bindingId);
            jdbcTemplate.update("DELETE FROM homework_binding_archives WHERE binding_id = ?", bindingId);
            jdbcTemplate.update("DELETE FROM homeworks WHERE binding_id = ?", bindingId);
            jdbcTemplate.update("DELETE FROM subject_lesson_types WHERE subject_id = ?", subjectId);
            jdbcTemplate.update("DELETE FROM subjects WHERE id = ?", subjectId);
            jdbcTemplate.update("DELETE FROM semesters WHERE id = ?", semesterId);
        });
    }

    @Test
    void eventBeforeContentStoresArchivedHistoryAndMakesActivationMonotonic() {
        Map<String, Object> event = archiveEvent(null, actorId, requestKey);

        archiveConsumer.onEvent(event);
        assertThat(homeworkCount()).isZero();
        assertThat(markerCount()).isEqualTo(1);

        Homework lateContent = persistPending();
        assertThat(lateContent.getPublicationState().name()).isEqualTo("ARCHIVED");
        assertThat(markerHomeworkId()).isEqualTo(lateContent.getId());

        Homework activationRetry = publicationPersistence.activate(
                lateContent.getId(), actorId, requestKey, bindingId, payloadHash);
        assertThat(activationRetry.getPublicationState().name()).isEqualTo("ARCHIVED");

        archiveConsumer.onEvent(event);
        assertThat(storedPublicationState()).isEqualTo("ARCHIVED");
        assertThat(processedCount(eventIds.getFirst())).isEqualTo(1);
    }

    @Test
    void cancellationArchivesExistingContentAndIdentityMismatchRollsBackClaim() {
        Homework pending = persistPending();
        Map<String, Object> event = archiveEvent(pending.getId(), actorId, requestKey);
        archiveConsumer.onEvent(event);

        assertThat(storedPublicationState()).isEqualTo("ARCHIVED");
        assertThat(markerHomeworkId()).isEqualTo(pending.getId());

        UUID conflictingEventId = UUID.randomUUID();
        eventIds.add(conflictingEventId);
        Map<String, Object> wrongIdentity = archiveEvent(
                pending.getId(), actorId, UUID.randomUUID(), conflictingEventId);
        assertThatThrownBy(() -> archiveConsumer.onEvent(wrongIdentity))
                .isInstanceOf(ConflictException.class);

        assertThat(storedPublicationState()).isEqualTo("ARCHIVED");
        assertThat(processedCount(conflictingEventId)).isZero();
        assertThat(markerCount()).isEqualTo(1);
    }

    @Test
    void directArchiveAndCancellationShareTheOriginalBindingIdentity() {
        Homework pending = persistPending();

        Homework archived = publicationPersistence.archive(
                pending.getId(), adminId, requestKey, bindingId);
        assertThat(archived.getPublicationState().name()).isEqualTo("ARCHIVED");
        assertThat(markerActorId()).isEqualTo(actorId);

        archiveConsumer.onEvent(archiveEvent(null, actorId, requestKey));
        assertThat(storedPublicationState()).isEqualTo("ARCHIVED");
        assertThat(markerCount()).isEqualTo(1);
    }

    @Test
    void unknownEventsAreIgnoredAndInvalidTargetVersionsAreNotClaimed() {
        UUID unknownId = UUID.randomUUID();
        eventIds.add(unknownId);
        archiveConsumer.onEvent(Map.of(
                "event_type", "lesson.cancelled",
                "event_id", unknownId.toString()));

        Map<String, Object> unsupportedVersion = archiveEvent(null, actorId, requestKey);
        unsupportedVersion.put("event_version", 2);
        UUID unsupportedId = UUID.fromString((String) unsupportedVersion.get("event_id"));
        assertThatThrownBy(() -> archiveConsumer.onEvent(unsupportedVersion))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(processedCount(unknownId)).isZero();
        assertThat(processedCount(unsupportedId)).isZero();
        assertThat(markerCount()).isZero();
    }

    @Test
    void concurrentActivationAndCancellationLeavePostgresInTerminalState() throws Exception {
        Homework pending = persistPending();
        Map<String, Object> event = archiveEvent(pending.getId(), actorId, requestKey);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> activation = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return publicationPersistence.activate(
                        pending.getId(), actorId, requestKey, bindingId, payloadHash);
            });
            Future<?> cancellation = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                archiveConsumer.onEvent(event);
                return null;
            });

            activation.get(30, TimeUnit.SECONDS);
            cancellation.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(storedPublicationState()).isEqualTo("ARCHIVED");
        assertThat(markerCount()).isEqualTo(1);
        assertThat(processedCount(eventIds.getFirst())).isEqualTo(1);
    }

    private Homework persistPending() {
        return publicationPersistence.persistPending(
                groupId, subjectId, semesterId, "Cancellation archive test",
                "description", null, actorId, LocalDate.of(semesterYear, 5, 10), 1,
                bindingId, requestKey, payloadHash);
    }

    private Map<String, Object> archiveEvent(Long homeworkId, long eventActorId, UUID eventRequestKey) {
        return archiveEvent(homeworkId, eventActorId, eventRequestKey, UUID.randomUUID());
    }

    private Map<String, Object> archiveEvent(Long homeworkId, long eventActorId,
                                             UUID eventRequestKey, UUID eventId) {
        eventIds.add(eventId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("binding_id", bindingId);
        payload.put("actor_id", eventActorId);
        payload.put("request_key", eventRequestKey.toString());
        payload.put("occurrence_id", bindingId + 1);
        payload.put("lesson_id", bindingId + 2);
        payload.put("homework_id", homeworkId);
        payload.put("binding_revision", 2L);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "homework.binding.archived");
        envelope.put("event_id", eventId.toString());
        envelope.put("occurred_at", "2026-09-29T09:00:00Z");
        envelope.put("event_version", 1);
        envelope.put("trace_id", UUID.randomUUID().toString());
        envelope.put("source", "schedule-service");
        envelope.put("payload", payload);
        return envelope;
    }

    private int homeworkCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homeworks WHERE binding_id = ?", Integer.class, bindingId);
    }

    private int markerCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homework_binding_archives WHERE binding_id = ?",
                Integer.class, bindingId);
    }

    private Long markerHomeworkId() {
        return jdbcTemplate.queryForObject(
                "SELECT homework_id FROM homework_binding_archives WHERE binding_id = ?",
                Long.class, bindingId);
    }

    private Long markerActorId() {
        return jdbcTemplate.queryForObject(
                "SELECT actor_id FROM homework_binding_archives WHERE binding_id = ?",
                Long.class, bindingId);
    }

    private String storedPublicationState() {
        return jdbcTemplate.queryForObject(
                "SELECT publication_state FROM homeworks WHERE binding_id = ?",
                String.class, bindingId);
    }

    private int processedCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_consumer_processed WHERE consumer_id = ? AND event_id = ?",
                Integer.class, CONSUMER_ID, eventId);
    }
}
