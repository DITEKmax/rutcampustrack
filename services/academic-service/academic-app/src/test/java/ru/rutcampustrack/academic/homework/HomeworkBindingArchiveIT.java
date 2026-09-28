package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.rutcampustrack.academic.contract.dto.homework.UpdateHomeworkRequest;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.event.HomeworkBindingArchivedEventConsumer;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
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
    @Autowired private Clock clock;
    @Autowired private HomeworkRepository homeworkRepository;
    @Autowired private HomeworkPublicationPersistence publicationPersistence;
    @Autowired private HomeworkBindingArchiveCoordinator archiveCoordinator;
    @Autowired private HomeworkBindingArchivedEventConsumer archiveConsumer;
    @Autowired private HomeworkNotificationJob notificationJob;
    @Autowired private HomeworkService homeworkService;
    @Autowired private RequestContext requestContext;

    private final List<UUID> eventIds = new ArrayList<>();

    private long bindingId;
    private long actorId;
    private long adminId;
    private long groupId;
    private long subjectId;
    private long semesterId;
    private int semesterYear;
    private final List<Long> previousActiveSemesterIds = new ArrayList<>();
    private UUID requestKey;
    private byte[] payloadHash;

    @BeforeEach
    void setUpFixture() {
        eventIds.clear();
        previousActiveSemesterIds.clear();
        bindingId = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 1_000_000_000_000_000L);
        requestKey = UUID.randomUUID();
        payloadHash = new byte[32];
        ThreadLocalRandom.current().nextBytes(payloadHash);
        semesterYear = ThreadLocalRandom.current().nextInt(2200, 3000);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            previousActiveSemesterIds.addAll(jdbcTemplate.queryForList(
                    "SELECT id FROM semesters WHERE is_active = true", Long.class));
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
                    "DELETE FROM academic_outbox WHERE "
                            + "payload -> 'payload' ->> 'homework_id' "
                            + "IN (SELECT id::text FROM homeworks WHERE binding_id = ?) "
                            + "OR payload #>> '{payload,homework,homework_id}' "
                            + "IN (SELECT id::text FROM homeworks WHERE binding_id = ?)",
                    bindingId, bindingId);
            jdbcTemplate.update("DELETE FROM homework_binding_archives WHERE binding_id = ?", bindingId);
            jdbcTemplate.update("DELETE FROM homeworks WHERE binding_id = ?", bindingId);
            jdbcTemplate.update("DELETE FROM subject_lesson_types WHERE subject_id = ?", subjectId);
            jdbcTemplate.update("DELETE FROM subjects WHERE id = ?", subjectId);
            jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE id = ?", semesterId);
            jdbcTemplate.update("DELETE FROM semesters WHERE id = ?", semesterId);
            for (Long previousActiveSemesterId : previousActiveSemesterIds) {
                jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?",
                        previousActiveSemesterId);
            }
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

    @Test
    void lateEditRefreshesAfterCancellationAndCannotFlushTheStaleActiveState() throws Exception {
        Homework pending = persistPending();
        publicationPersistence.activate(pending.getId(), actorId, requestKey, bindingId, payloadHash);
        CountDownLatch activeRowLoaded = new CountDownLatch(1);
        CountDownLatch cancellationCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> lateEdit;
        try {
            lateEdit = executor.submit(() -> {
                ServletRequestAttributes requestAttributes = new ServletRequestAttributes(
                        new MockHttpServletRequest());
                RequestContextHolder.setRequestAttributes(requestAttributes);
                try {
                    requestContext.setUserId(actorId);
                    requestContext.setGroupId(groupId);
                    requestContext.setRole(UserRole.STUDENT);
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        Homework staleActive = homeworkRepository.findById(pending.getId())
                                .orElseThrow();
                        assertThat(staleActive.getPublicationState())
                                .isEqualTo(HomeworkPublicationState.ACTIVE);
                        activeRowLoaded.countDown();
                        await(cancellationCommitted);
                        return homeworkService.updateHomework(pending.getId(),
                                new UpdateHomeworkRequest("late edit", "stale writer", null));
                    });
                } finally {
                    RequestContextHolder.resetRequestAttributes();
                    requestAttributes.requestCompleted();
                }
            });

            assertThat(activeRowLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            archiveConsumer.onEvent(archiveEvent(pending.getId(), actorId, requestKey));
            cancellationCommitted.countDown();

            assertThatThrownBy(() -> lateEdit.get(30, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ConflictException.class);
        } finally {
            cancellationCommitted.countDown();
            executor.shutdownNow();
        }

        assertThat(storedPublicationState()).isEqualTo("ARCHIVED");
        assertThat(storedTitle()).isEqualTo("Cancellation archive test");
    }

    @Test
    void dueReminderRefreshesBlockedActiveCandidateAfterCancellationAndPublishesNothing()
            throws Exception {
        LocalDate today = LocalDate.now(clock);
        LocalDate dueDate = today.plusDays(2);
        prepareFixtureSemesterForDueDate(today, dueDate);
        Homework pending = persistPending(dueDate);
        publicationPersistence.activate(pending.getId(), actorId, requestKey, bindingId, payloadHash);
        Map<String, Object> event = archiveEvent(pending.getId(), actorId, requestKey);

        CountDownLatch cancellationLockHeld = new CountDownLatch(1);
        CountDownLatch finishCancellation = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> cancellation = null;
        Future<?> reminder = null;
        boolean jobBlockedAfterReadingActive = false;
        try {
            cancellation = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        archiveCoordinator.lock(bindingId);
                        cancellationLockHeld.countDown();
                        await(finishCancellation);
                        archiveConsumer.onEvent(event);
                    }));
            assertThat(cancellationLockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            reminder = executor.submit(notificationJob::publishDueReminders);
            jobBlockedAfterReadingActive = awaitAdvisoryLockWaiter(10_000L);
        } finally {
            finishCancellation.countDown();
        }

        try {
            if (cancellation != null) {
                cancellation.get(30, TimeUnit.SECONDS);
            }
            if (reminder != null) {
                reminder.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(jobBlockedAfterReadingActive).isTrue();
        assertThat(storedPublicationState()).isEqualTo("ARCHIVED");
        assertThat(dueReminderSentAt()).isNull();
        assertThat(dueReminderOutboxCount(pending.getId())).isZero();
    }

    private Homework persistPending() {
        return persistPending(LocalDate.of(semesterYear, 5, 10));
    }

    private Homework persistPending(LocalDate lessonDate) {
        return publicationPersistence.persistPending(
                groupId, subjectId, semesterId, "Cancellation archive test",
                "description", null, actorId, lessonDate, 1,
                bindingId, requestKey, payloadHash);
    }

    private void prepareFixtureSemesterForDueDate(LocalDate today, LocalDate dueDate) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
            jdbcTemplate.update(
                    "UPDATE semesters SET date_from = ?, date_to = ?, is_active = true WHERE id = ?",
                    today.minusDays(1), dueDate.plusDays(3), semesterId);
        });
    }

    private boolean awaitAdvisoryLockWaiter(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            Integer waiters = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity "
                            + "WHERE pid <> pg_backend_pid() "
                            + "AND datname = current_database() "
                            + "AND wait_event_type = 'Lock' AND wait_event = 'advisory' "
                            + "AND query ILIKE '%pg_advisory_xact_lock%'",
                    Integer.class);
            if (waiters != null && waiters > 0) {
                return true;
            }
            Thread.sleep(25L);
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for cancellation test signal");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for cancellation test signal",
                    interrupted);
        }
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

    private String storedTitle() {
        return jdbcTemplate.queryForObject(
                "SELECT title FROM homeworks WHERE binding_id = ?", String.class, bindingId);
    }

    private java.time.OffsetDateTime dueReminderSentAt() {
        return jdbcTemplate.queryForObject(
                "SELECT due_reminder_sent_at FROM homeworks WHERE binding_id = ?",
                java.time.OffsetDateTime.class, bindingId);
    }

    private int dueReminderOutboxCount(long homeworkId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM academic_outbox WHERE event_type = 'homework.due_reminder' "
                        + "AND payload #>> '{payload,homework,homework_id}' = ?",
                Integer.class, Long.toString(homeworkId));
    }

    private int processedCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_consumer_processed WHERE consumer_id = ? AND event_id = ?",
                Integer.class, CONSUMER_ID, eventId);
    }
}
