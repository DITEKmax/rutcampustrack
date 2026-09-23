package ru.rutcampustrack.schedule.integration;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.academic.grpc.PreparedAssignmentCloseResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;
import ru.rutcampustrack.schedule.grpc.CommitAssignmentCloseRequest;
import ru.rutcampustrack.schedule.grpc.InstallAssignmentCloseCapRequest;
import ru.rutcampustrack.schedule.exception.RecurringProtocolConflictException;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;
import ru.rutcampustrack.schedule.recurring.RecurringScheduleItemWriter;
import ru.rutcampustrack.schedule.replacement.AssignmentReplacementService;
import ru.rutcampustrack.schedule.exception.ConflictException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** PostgreSQL-backed tests for the durable replacement ledger and fence guards. */
class AssignmentReplacementServiceIT extends AbstractScheduleIntegrationTest {

    private static final long SOURCE_ASSIGNMENT_ID = 501L;
    private static final long TARGET_ASSIGNMENT_ID = 502L;
    private static final long SOURCE_TEACHER_ID = 700L;
    private static final long TARGET_TEACHER_ID = 701L;
    private static final long OTHER_ASSIGNMENT_ID = 503L;
    private static final long OTHER_TEACHER_ID = 702L;
    private static final long GROUP_ID = 10L;
    private static final long SUBJECT_ID = 20L;
    private static final long SEMESTER_ID = 30L;
    private static final LocalDate SOURCE_FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate EFFECTIVE_FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate ASSIGNMENT_END = LocalDate.of(2026, 12, 31);
    private static final LocalDate GENERATION_END = LocalDate.of(2026, 11, 10);
    private static final long ACTOR_ID = 42L;

    private static final UUID OPERATION_ID = UUID.fromString("4e03f227-3818-4148-b59b-7f45eb4fd243");
    private static final byte[] PAYLOAD_HASH = new byte[32];

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AssignmentReplacementService replacementService;

    @Autowired
    RecurringScheduleItemWriter recurringWriter;

    @BeforeEach
    void resetData() {
        resetScheduleData();
    }

    @Test
    void rebindsOnlyFuturePlannedAndKeepsPhysicalHistoryAndCoveredDates() {
        long sourceTemplateId = insertSourceTemplate();
        SeededOccurrence pastPlanned = seedOccurrence(sourceTemplateId,
                LocalDate.of(2026, 9, 29), "planned");
        SeededOccurrence movedPlanned = seedOccurrence(sourceTemplateId,
                LocalDate.of(2026, 10, 6), "planned");
        SeededOccurrence active = seedOccurrence(sourceTemplateId,
                LocalDate.of(2026, 10, 13), "active");
        SeededOccurrence cancelled = seedOccurrence(sourceTemplateId,
                LocalDate.of(2026, 10, 20), "cancelled");
        SeededOccurrence transferred = seedOccurrence(sourceTemplateId,
                LocalDate.of(2026, 10, 27), "transferred");
        SeededOccurrence noCurrentLesson = seedOccurrence(sourceTemplateId,
                LocalDate.of(2026, 11, 3), null);
        long homeworkBindingId = insertHomeworkBinding(movedPlanned);
        installAuthority(PAYLOAD_HASH);

        AssignmentCloseReceipt applied = replacementService.install(installRequest(PAYLOAD_HASH));

        assertThat(applied.getState()).isEqualTo("APPLIED");
        assertThat(applied.getMovedCount()).isZero();
        assertThat(applied.getSkippedCount()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_assignment_rebind_ledger WHERE operation_id = ?
                """, Long.class, OPERATION_ID)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_assignment_replacement_templates WHERE operation_id = ?
                """, Long.class, OPERATION_ID)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT creation_cap_until_exclusive FROM schedule_assignment_fences
                 WHERE assignment_id = ?
                """, LocalDate.class, SOURCE_ASSIGNMENT_ID)).isEqualTo(EFFECTIVE_FROM);
        assertThat(jdbc.queryForObject("""
                SELECT cap_until_exclusive FROM schedule_assignment_fences
                 WHERE assignment_id = ?
                """, LocalDate.class, SOURCE_ASSIGNMENT_ID)).isEqualTo(ASSIGNMENT_END);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type,
                     generation, revision, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture', 1, 1, NOW())
                """, sourceTemplateId, EFFECTIVE_FROM, SOURCE_ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SOURCE_TEACHER_ID))
                .isInstanceOf(DataAccessException.class);

        assertOccurrenceUnchanged(pastPlanned, sourceTemplateId, SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID, "planned", 1L);
        assertOccurrenceUnchanged(active, sourceTemplateId, SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID, "active", 1L);
        assertOccurrenceUnchanged(cancelled, sourceTemplateId, SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID, "cancelled", 1L);
        assertOccurrenceUnchanged(transferred, sourceTemplateId, SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID, "transferred", 1L);
        assertThat(jdbc.queryForObject("""
                SELECT current_lesson_id FROM lesson_occurrences WHERE id = ?
                """, Long.class, noCurrentLesson.occurrenceId())).isNull();
        assertOccurrenceUnchanged(movedPlanned, sourceTemplateId, SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID, "planned", 1L);
        assertPendingTargetWritesBlocked();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_lifecycle_entries
                 WHERE occurrence_id IN (?, ?, ?, ?, ?)
                """, Long.class, pastPlanned.occurrenceId(), movedPlanned.occurrenceId(),
                active.occurrenceId(), cancelled.occurrenceId(), transferred.occurrenceId()))
                .isEqualTo(5L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_homework_bindings
                 WHERE binding_id = ? AND occurrence_id = ? AND current_lesson_id = ?
                   AND homework_id = 991 AND state = 'ACTIVE'
                """, Long.class, homeworkBindingId, movedPlanned.occurrenceId(),
                movedPlanned.lessonId())).isEqualTo(1L);

        activateAuthority(PAYLOAD_HASH);
        AssignmentCloseReceipt committed = replacementService.commit(commitRequest(PAYLOAD_HASH));
        assertThat(committed.getState()).isEqualTo("COMMITTED");
        assertThat(committed.getMovedCount()).isEqualTo(1L);
        assertThat(committed.getSkippedCount()).isEqualTo(4L);
        assertOccurrenceMoved(movedPlanned);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_assignment_replacement_templates
                 WHERE operation_id = ?
                """, Long.class, OPERATION_ID)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_items
                 WHERE group_id = ? AND day_of_week = 2 AND lesson_number = 1
                   AND week_type = 'all'::week_type AND semester_id = ? AND is_active
                """, Long.class, GROUP_ID, SEMESTER_ID)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_items
                 WHERE assignment_id = ? AND day_of_week = 2 AND lesson_number = 1
                   AND is_active
                """, Long.class, TARGET_ASSIGNMENT_ID)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_items
                 WHERE group_id = ? AND day_of_week = 3 AND lesson_number = 2
                   AND week_type = 'all'::week_type AND semester_id = ? AND is_active
                """, Long.class, GROUP_ID, SEMESTER_ID)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_items item
                JOIN schedule_assignment_replacement_templates mapping
                  ON mapping.target_schedule_item_id = item.id
                 WHERE mapping.operation_id = ? AND NOT mapping.source_was_active
                   AND item.is_active
                """, Long.class, OPERATION_ID)).isZero();

        RecurringAssignmentAuthority targetAuthority = new RecurringAssignmentAuthority(
                TARGET_ASSIGNMENT_ID, TARGET_TEACHER_ID, SUBJECT_ID, GROUP_ID, SEMESTER_ID,
                "lecture", EFFECTIVE_FROM, ASSIGNMENT_END);
        var targetGeneration = recurringWriter.write(
                request(TARGET_ASSIGNMENT_ID), UUID.randomUUID(), ACTOR_ID,
                targetAuthority, LocalDate.of(2026, 10, 6), GENERATION_END);
        assertThat(targetGeneration.generatedCount()).isEqualTo(1L);
        assertThat(jdbc.queryForList("""
                SELECT occurrence_date FROM lesson_occurrences
                 WHERE assignment_id = ? ORDER BY occurrence_date
                """, LocalDate.class, TARGET_ASSIGNMENT_ID)).containsExactly(
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 11, 10));

        long inactiveHistoricalCloneId = jdbc.queryForObject("""
                SELECT mapping.target_schedule_item_id
                  FROM schedule_assignment_replacement_templates mapping
                  JOIN schedule_items source_item ON source_item.id = mapping.source_schedule_item_id
                 WHERE mapping.operation_id = ? AND NOT mapping.source_was_active
                   AND source_item.day_of_week = 4 AND source_item.lesson_number = 3
                """, Long.class, OPERATION_ID);
        CreateScheduleItemRequest inactiveSlotRequest = new CreateScheduleItemRequest(
                TARGET_ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID,
                (short) 4, (short) 3, LocalTime.of(12, 0), LocalTime.of(13, 0),
                WeekType.ALL, "R-303");
        var createdForInactiveHistory = recurringWriter.write(inactiveSlotRequest,
                UUID.randomUUID(), ACTOR_ID, targetAuthority,
                EFFECTIVE_FROM, GENERATION_END);
        assertThat(createdForInactiveHistory.generatedCount()).isPositive();
        assertThat(createdForInactiveHistory.scheduleItemId()).isNotEqualTo(inactiveHistoricalCloneId);
        assertThat(jdbc.queryForObject("SELECT is_active FROM schedule_items WHERE id = ?",
                Boolean.class, createdForInactiveHistory.scheduleItemId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_active FROM schedule_items WHERE id = ?",
                Boolean.class, inactiveHistoricalCloneId)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_items
                 WHERE group_id = ? AND day_of_week = 4 AND lesson_number = 3
                   AND week_type = 'all'::week_type AND semester_id = ? AND is_active
                """, Long.class, GROUP_ID, SEMESTER_ID)).isEqualTo(1L);

        long deactivatedTargetTemplateId = targetGeneration.scheduleItemId();
        jdbc.update("UPDATE schedule_items SET is_active = FALSE WHERE id = ?",
                deactivatedTargetTemplateId);
        var createAfterTargetTemplateRemoval = recurringWriter.write(
                request(TARGET_ASSIGNMENT_ID), UUID.randomUUID(), ACTOR_ID, targetAuthority,
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 30));
        assertThat(createAfterTargetTemplateRemoval.generatedCount()).isPositive();
        assertThat(createAfterTargetTemplateRemoval.scheduleItemId())
                .isNotEqualTo(deactivatedTargetTemplateId);
        assertThat(jdbc.queryForObject("SELECT is_active FROM schedule_items WHERE id = ?",
                Boolean.class, createAfterTargetTemplateRemoval.scheduleItemId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_active FROM schedule_items WHERE id = ?",
                Boolean.class, deactivatedTargetTemplateId)).isFalse();
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE schedule_assignment_rebind_ledger
                   SET result = 'SKIPPED_ACTIVE'
                 WHERE operation_id = ? AND result = 'MOVED'
                """, OPERATION_ID)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void exactReplayReturnsDurableReceiptAndAnotherHashCannotReuseOperation() {
        insertSourceTemplate();
        installAuthority(PAYLOAD_HASH);

        AssignmentCloseReceipt first = replacementService.install(installRequest(PAYLOAD_HASH));
        AssignmentCloseReceipt replay = replacementService.install(installRequest(PAYLOAD_HASH));
        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_assignment_replacement_operations
                 WHERE operation_id = ?
                """, Long.class, OPERATION_ID)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM schedule_assignment_replacement_templates
                 WHERE operation_id = ?
                """, Long.class, OPERATION_ID)).isEqualTo(0L);

        byte[] otherHash = new byte[32];
        java.util.Arrays.fill(otherHash, (byte) 0x6A);
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(OPERATION_ID,
                SOURCE_ASSIGNMENT_ID)).thenReturn(authority(otherHash, "PREPARED",
                ASSIGNMENT_END, "PREPARED"));
        assertThatThrownBy(() -> replacementService.install(installRequest(otherHash)))
                .isInstanceOf(ConflictException.class);
        assertThat(jdbc.queryForObject("""
                SELECT payload_hash FROM schedule_assignment_replacement_operations
                 WHERE operation_id = ?
                """, byte[].class, OPERATION_ID)).containsExactly(PAYLOAD_HASH);

        activateAuthority(PAYLOAD_HASH);
        assertThat(replacementService.commit(commitRequest(PAYLOAD_HASH)).getState())
                .isEqualTo("COMMITTED");
    }

    @Test
    void concurrentExactInstallsAndCommitsReplayOneOperationWithoutSlotConflict() throws Exception {
        insertSourceTemplate();
        installAuthority(PAYLOAD_HASH);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<AssignmentCloseReceipt> firstInstall = pool.submit(() -> {
                ready.countDown();
                await(start);
                return replacementService.install(installRequest(PAYLOAD_HASH));
            });
            Future<AssignmentCloseReceipt> secondInstall = pool.submit(() -> {
                ready.countDown();
                await(start);
                return replacementService.install(installRequest(PAYLOAD_HASH));
            });
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(firstInstall.get(10, java.util.concurrent.TimeUnit.SECONDS).getState())
                    .isEqualTo("APPLIED");
            assertThat(secondInstall.get(10, java.util.concurrent.TimeUnit.SECONDS).getState())
                    .isEqualTo("APPLIED");

            activateAuthority(PAYLOAD_HASH);
            CountDownLatch commitReady = new CountDownLatch(2);
            CountDownLatch commitStart = new CountDownLatch(1);
            Future<AssignmentCloseReceipt> firstCommit = pool.submit(() -> {
                commitReady.countDown();
                await(commitStart);
                return replacementService.commit(commitRequest(PAYLOAD_HASH));
            });
            Future<AssignmentCloseReceipt> secondCommit = pool.submit(() -> {
                commitReady.countDown();
                await(commitStart);
                return replacementService.commit(commitRequest(PAYLOAD_HASH));
            });
            assertThat(commitReady.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            commitStart.countDown();
            assertThat(firstCommit.get(10, java.util.concurrent.TimeUnit.SECONDS).getState())
                    .isEqualTo("COMMITTED");
            assertThat(secondCommit.get(10, java.util.concurrent.TimeUnit.SECONDS).getState())
                    .isEqualTo("COMMITTED");

            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM schedule_assignment_replacement_operations
                     WHERE operation_id = ? AND state = 'COMMITTED'
                    """, Long.class, OPERATION_ID)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM schedule_items
                     WHERE group_id = ? AND day_of_week = 2 AND lesson_number = 1
                       AND week_type = 'all'::week_type AND semester_id = ? AND is_active
                    """, Long.class, GROUP_ID, SEMESTER_ID)).isEqualTo(1L);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private void installAuthority(byte[] hash) {
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(OPERATION_ID,
                SOURCE_ASSIGNMENT_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return authority(hash, "PREPARED", ASSIGNMENT_END, "PREPARED");
        });
    }

    private void activateAuthority(byte[] hash) {
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(OPERATION_ID,
                SOURCE_ASSIGNMENT_ID)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return authority(hash, "APPLIED", EFFECTIVE_FROM, "ACTIVE");
        });
    }

    private long insertSourceTemplate() {
        jdbc.update("""
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, ?, 'lecture', ?, ?, ?)
                """, SOURCE_ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID,
                SOURCE_TEACHER_ID, SOURCE_FROM, ASSIGNMENT_END, ASSIGNMENT_END);
        jdbc.update("""
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, ?, 'lecture', ?, ?, ?)
                """, OTHER_ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID,
                OTHER_TEACHER_ID, SOURCE_FROM, ASSIGNMENT_END, ASSIGNMENT_END);
        Long id = jdbc.queryForObject("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 2, 1, TIME '08:30', TIME '10:00',
                        'all'::week_type, 'R-101', TRUE, NOW())
                RETURNING id
                """, Long.class, SOURCE_ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        jdbc.update("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 3, 2, TIME '10:15', TIME '11:45',
                        'all'::week_type, 'R-202', FALSE, NOW())
                """, SOURCE_ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        jdbc.update("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 3, 2, TIME '10:15', TIME '11:45',
                        'all'::week_type, 'R-202', TRUE, NOW())
                """, OTHER_ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        jdbc.update("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 4, 3, TIME '12:00', TIME '13:00',
                        'all'::week_type, 'R-303', FALSE, NOW())
                """, SOURCE_ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        return id;
    }

    private void assertPendingTargetWritesBlocked() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 4, 3, TIME '12:00', TIME '13:00',
                        'all'::week_type, 'R-303', FALSE, NOW())
                """, TARGET_ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID))
                .isInstanceOf(DataAccessException.class);
        RecurringAssignmentAuthority targetAuthority = new RecurringAssignmentAuthority(
                TARGET_ASSIGNMENT_ID, TARGET_TEACHER_ID, SUBJECT_ID, GROUP_ID, SEMESTER_ID,
                "lecture", EFFECTIVE_FROM, ASSIGNMENT_END);
        assertThatThrownBy(() -> recurringWriter.write(request(TARGET_ASSIGNMENT_ID),
                UUID.randomUUID(), ACTOR_ID, targetAuthority,
                LocalDate.of(2026, 10, 1), GENERATION_END))
                .isInstanceOf(RecurringProtocolConflictException.class);
    }

    private SeededOccurrence seedOccurrence(long templateId, LocalDate date, String status) {
        Long occurrenceId = jdbc.queryForObject("""
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type,
                     generation, revision, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture', 1, 1, NOW())
                RETURNING id
                """, Long.class, templateId, date, SOURCE_ASSIGNMENT_ID,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID, SOURCE_TEACHER_ID);
        if (status == null) return new SeededOccurrence(occurrenceId, null);
        Long lessonId = jdbc.queryForObject("""
                INSERT INTO lessons
                    (schedule_item_id, occurrence_id, assignment_id, group_id, subject_id,
                     semester_id, assigned_teacher_id, lesson_type, lesson_number,
                     day_of_week, start_time, end_time, room_snapshot, week_type_snapshot,
                     generation, revision, date, status, is_geo_blocked, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture', 1, 2,
                        TIME '08:30', TIME '10:00', 'R-101', 'all', 1, 1, ?,
                        ?::lesson_status, FALSE, NOW())
                RETURNING id
                """, Long.class, templateId, occurrenceId, SOURCE_ASSIGNMENT_ID,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID, SOURCE_TEACHER_ID, date, status);
        jdbc.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                lessonId, occurrenceId);
        jdbc.update("""
                INSERT INTO lesson_lifecycle_entries
                    (occurrence_id, revision, action, lesson_id, generation, actor_id, occurred_at)
                VALUES (?, 1, 'CREATED', ?, 1, ?, NOW())
                """, occurrenceId, lessonId, ACTOR_ID);
        return new SeededOccurrence(occurrenceId, lessonId);
    }

    private long insertHomeworkBinding(SeededOccurrence occurrence) {
        return jdbc.queryForObject("""
                INSERT INTO lesson_homework_bindings
                    (occurrence_id, current_lesson_id, homework_id, actor_id, request_key,
                     payload_hash, state, revision, created_at, updated_at)
                VALUES (?, ?, 991, ?, ?, ?, 'ACTIVE', 1, NOW(), NOW())
                RETURNING binding_id
                """, Long.class, occurrence.occurrenceId(), occurrence.lessonId(), ACTOR_ID,
                UUID.randomUUID(), PAYLOAD_HASH);
    }

    private void assertOccurrenceUnchanged(SeededOccurrence occurrence,
                                          long templateId,
                                          long assignmentId,
                                          long teacherId,
                                          String status,
                                          long revision) {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrences occurrence
                JOIN lessons lesson ON lesson.id = occurrence.current_lesson_id
                 WHERE occurrence.id = ? AND occurrence.schedule_item_id = ?
                   AND occurrence.assignment_id = ? AND occurrence.assigned_teacher_id = ?
                   AND occurrence.current_lesson_id = ? AND lesson.id = ?
                   AND lesson.assignment_id = ? AND lesson.assigned_teacher_id = ?
                   AND lesson.status::text = ? AND occurrence.revision = ? AND lesson.revision = ?
                """, Long.class, occurrence.occurrenceId(), templateId, assignmentId, teacherId,
                occurrence.lessonId(), occurrence.lessonId(), assignmentId, teacherId, status,
                revision, revision)).isEqualTo(1L);
    }

    private void assertOccurrenceMoved(SeededOccurrence occurrence) {
        Long targetTemplateId = jdbc.queryForObject("""
                SELECT target_schedule_item_id FROM schedule_assignment_rebind_ledger
                 WHERE operation_id = ? AND occurrence_id = ? AND result = 'MOVED'
                """, Long.class, OPERATION_ID, occurrence.occurrenceId());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_occurrences occurrence
                JOIN lessons lesson ON lesson.id = occurrence.current_lesson_id
                 WHERE occurrence.id = ? AND occurrence.current_lesson_id = ?
                   AND occurrence.schedule_item_id = ? AND occurrence.assignment_id = ?
                   AND occurrence.assigned_teacher_id = ? AND occurrence.revision = 2
                   AND lesson.id = ? AND lesson.schedule_item_id = ? AND lesson.assignment_id = ?
                   AND lesson.assigned_teacher_id = ? AND lesson.status::text = 'planned'
                   AND lesson.revision = 2
                """, Long.class, occurrence.occurrenceId(), occurrence.lessonId(), targetTemplateId,
                TARGET_ASSIGNMENT_ID, TARGET_TEACHER_ID, occurrence.lessonId(), targetTemplateId,
                TARGET_ASSIGNMENT_ID, TARGET_TEACHER_ID)).isEqualTo(1L);
    }

    private static PreparedAssignmentCloseResponse authority(byte[] hash, String state,
                                                              LocalDate sourceEnd, String targetState) {
        return PreparedAssignmentCloseResponse.newBuilder()
                .setOperationId(OPERATION_ID.toString())
                .setSourceAssignmentId(SOURCE_ASSIGNMENT_ID)
                .setTargetAssignmentId(TARGET_ASSIGNMENT_ID)
                .setSourceTeacherId(SOURCE_TEACHER_ID)
                .setTargetTeacherId(TARGET_TEACHER_ID)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID)
                .setSemesterId(SEMESTER_ID)
                .setLessonType("lecture")
                .setSourceValidFrom(SOURCE_FROM.toString())
                .setSourceValidUntilExclusive(sourceEnd.toString())
                .setTargetValidUntilExclusive(ASSIGNMENT_END.toString())
                .setEffectiveFrom(EFFECTIVE_FROM.toString())
                .setTargetLifecycleState(targetState)
                .setState(state)
                .setPayloadHash(ByteString.copyFrom(hash))
                .build();
    }

    private static InstallAssignmentCloseCapRequest installRequest(byte[] hash) {
        return InstallAssignmentCloseCapRequest.newBuilder()
                .setOperationId(OPERATION_ID.toString())
                .setSourceAssignmentId(SOURCE_ASSIGNMENT_ID)
                .setTargetAssignmentId(TARGET_ASSIGNMENT_ID)
                .setEffectiveFrom(EFFECTIVE_FROM.toString())
                .setPayloadHash(ByteString.copyFrom(hash))
                .build();
    }

    private static CommitAssignmentCloseRequest commitRequest(byte[] hash) {
        return CommitAssignmentCloseRequest.newBuilder()
                .setOperationId(OPERATION_ID.toString())
                .setPayloadHash(ByteString.copyFrom(hash))
                .build();
    }

    private static CreateScheduleItemRequest request(long assignmentId) {
        return new CreateScheduleItemRequest(assignmentId, GROUP_ID, SUBJECT_ID, SEMESTER_ID,
                (short) 2, (short) 1, LocalTime.of(8, 30), LocalTime.of(10, 0),
                WeekType.ALL, "R-101");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("bounded replacement race barrier timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("replacement race interrupted", interrupted);
        }
    }

    private record SeededOccurrence(long occurrenceId, Long lessonId) { }
}
