package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.grpc.PreparedAssignmentCloseResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.exception.RecurringProtocolConflictException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.AssignmentCloseReceipt;
import ru.rutcampustrack.schedule.grpc.CommitAssignmentCloseRequest;
import ru.rutcampustrack.schedule.grpc.InstallAssignmentCloseCapRequest;
import ru.rutcampustrack.schedule.lesson.RecurringLessonLifecycleWriter;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.oneoff.OneOffLessonWriter;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;
import ru.rutcampustrack.schedule.recurring.RecurringScheduleItemWriter;
import ru.rutcampustrack.schedule.replacement.AssignmentReplacementService;

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

    private static final UUID OPERATION_ID =
            UUID.fromString("4e03f227-3818-4148-b59b-7f45eb4fd243");
    private static final byte[] PAYLOAD_HASH = new byte[32];

    @MockitoBean AcademicGrpcClient academicGrpcClient;

    @Autowired JdbcTemplate jdbc;

    @Autowired ObjectMapper objectMapper;

    @Autowired AssignmentReplacementService replacementService;

    @Autowired RecurringScheduleItemWriter recurringWriter;

    @Autowired RecurringLessonLifecycleWriter lifecycleWriter;

    @Autowired LessonRepository lessonRepository;

    @Autowired OneOffLessonWriter oneOffWriter;

    @Autowired PlatformTransactionManager transactionManager;

    @Autowired ru.rutcampustrack.schedule.lesson.LessonTransferWriter transferWriter;

    @BeforeEach
    void resetData() {
        resetScheduleData();
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .build());
    }

    @Test
    void rebindsOnlyFuturePlannedAndKeepsPhysicalHistoryAndCoveredDates() throws Exception {
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .build());
        long sourceTemplateId = insertSourceTemplate();
        // New generation uses the current slot catalog; physical historical snapshots remain
        // unchanged.
        jdbc.update(
                "UPDATE schedule_items SET end_time = TIME '09:50' WHERE id = ?", sourceTemplateId);
        SeededOccurrence pastPlanned =
                seedOccurrence(sourceTemplateId, LocalDate.of(2026, 9, 29), "planned");
        SeededOccurrence movedPlanned =
                seedOccurrence(sourceTemplateId, LocalDate.of(2026, 10, 6), "planned");
        SeededOccurrence active =
                seedOccurrence(sourceTemplateId, LocalDate.of(2026, 10, 13), "active");
        SeededOccurrence cancelled =
                seedOccurrence(sourceTemplateId, LocalDate.of(2026, 10, 20), "cancelled");
        SeededOccurrence transferred =
                seedOccurrence(sourceTemplateId, LocalDate.of(2026, 10, 27), "transferred");
        SeededOccurrence noCurrentLesson =
                seedOccurrence(sourceTemplateId, LocalDate.of(2026, 11, 3), null);
        long homeworkBindingId = insertHomeworkBinding(movedPlanned);
        installAuthority(PAYLOAD_HASH);

        AssignmentCloseReceipt applied = replacementService.install(installRequest(PAYLOAD_HASH));

        assertThat(applied.getState()).isEqualTo("APPLIED");
        assertThat(applied.getMovedCount()).isZero();
        assertThat(applied.getSkippedCount()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_assignment_rebind_ledger WHERE operation_id = ?
                """,
                                Long.class,
                                OPERATION_ID))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_assignment_replacement_templates WHERE operation_id = ?
                """,
                                Long.class,
                                OPERATION_ID))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT creation_cap_until_exclusive FROM schedule_assignment_fences
                 WHERE assignment_id = ?
                """,
                                LocalDate.class,
                                SOURCE_ASSIGNMENT_ID))
                .isEqualTo(EFFECTIVE_FROM);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT cap_until_exclusive FROM schedule_assignment_fences
                 WHERE assignment_id = ?
                """,
                                LocalDate.class,
                                SOURCE_ASSIGNMENT_ID))
                .isEqualTo(ASSIGNMENT_END);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type,
                     generation, revision, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture', 1, 1, NOW())
                """,
                                        sourceTemplateId,
                                        EFFECTIVE_FROM,
                                        SOURCE_ASSIGNMENT_ID,
                                        GROUP_ID,
                                        SUBJECT_ID,
                                        SEMESTER_ID,
                                        SOURCE_TEACHER_ID))
                .isInstanceOf(DataAccessException.class);

        assertOccurrenceUnchanged(
                pastPlanned,
                sourceTemplateId,
                SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID,
                "planned",
                1L);
        assertOccurrenceUnchanged(
                active, sourceTemplateId, SOURCE_ASSIGNMENT_ID, SOURCE_TEACHER_ID, "active", 1L);
        assertOccurrenceUnchanged(
                cancelled,
                sourceTemplateId,
                SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID,
                "cancelled",
                1L);
        assertOccurrenceUnchanged(
                transferred,
                sourceTemplateId,
                SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID,
                "transferred",
                1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT current_lesson_id FROM lesson_occurrences WHERE id = ?
                """,
                                Long.class,
                                noCurrentLesson.occurrenceId()))
                .isNull();
        assertOccurrenceUnchanged(
                movedPlanned,
                sourceTemplateId,
                SOURCE_ASSIGNMENT_ID,
                SOURCE_TEACHER_ID,
                "planned",
                1L);
        assertPendingTargetWritesBlocked();

        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_lifecycle_entries
                 WHERE occurrence_id IN (?, ?, ?, ?, ?)
                """,
                                Long.class,
                                pastPlanned.occurrenceId(),
                                movedPlanned.occurrenceId(),
                                active.occurrenceId(),
                                cancelled.occurrenceId(),
                                transferred.occurrenceId()))
                .isEqualTo(5L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_homework_bindings
                 WHERE binding_id = ? AND occurrence_id = ? AND current_lesson_id = ?
                   AND homework_id = 991 AND state = 'ACTIVE'
                """,
                                Long.class,
                                homeworkBindingId,
                                movedPlanned.occurrenceId(),
                                movedPlanned.lessonId()))
                .isEqualTo(1L);

        activateAuthority(PAYLOAD_HASH);
        AssignmentCloseReceipt committed = replacementService.commit(commitRequest(PAYLOAD_HASH));
        assertThat(committed.getState()).isEqualTo("COMMITTED");
        assertThat(committed.getMovedCount()).isEqualTo(1L);
        assertThat(committed.getSkippedCount()).isEqualTo(4L);
        assertOccurrenceMoved(movedPlanned);
        var actionRows =
                jdbc.queryForList(
                        """
                SELECT payload FROM schedule_outbox WHERE event_type = 'schedule.action-recorded'
                 AND payload::jsonb -> 'payload' ->> 'action' = 'LESSON_EDITED'
                """,
                        String.class);
        assertThat(actionRows).hasSize(1);
        assertThat(
                        ru.rutcampustrack.schedule.events.EventSchemaValidator.validate(
                                "schedule.action-recorded.json", actionRows.getFirst()))
                .isEmpty();
        var action = objectMapper.readTree(actionRows.getFirst()).path("payload");
        assertThat(action.path("lesson_id").asLong()).isEqualTo(movedPlanned.lessonId());
        assertThat(action.path("actor").isNull()).isTrue();
        assertThat(action.path("before").path("assignment_id").asLong())
                .isEqualTo(SOURCE_ASSIGNMENT_ID);
        assertThat(action.path("after").path("assignment_id").asLong())
                .isEqualTo(TARGET_ASSIGNMENT_ID);
        assertThat(replacementService.commit(commitRequest(PAYLOAD_HASH))).isEqualTo(committed);
        assertThat(
                        jdbc.queryForList(
                                """
                SELECT payload FROM schedule_outbox WHERE event_type = 'schedule.action-recorded'
                 AND payload::jsonb -> 'payload' ->> 'action' = 'LESSON_EDITED'
                """,
                                String.class))
                .isEqualTo(actionRows);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_assignment_replacement_templates
                 WHERE operation_id = ?
                """,
                                Long.class,
                                OPERATION_ID))
                .isEqualTo(3L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_items
                 WHERE group_id = ? AND day_of_week = 2 AND lesson_number = 1
                   AND week_type = 'all'::week_type AND semester_id = ? AND is_active
                """,
                                Long.class,
                                GROUP_ID,
                                SEMESTER_ID))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_items
                 WHERE assignment_id = ? AND day_of_week = 2 AND lesson_number = 1
                   AND is_active
                """,
                                Long.class,
                                TARGET_ASSIGNMENT_ID))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_items
                 WHERE group_id = ? AND day_of_week = 3 AND lesson_number = 2
                   AND week_type = 'all'::week_type AND semester_id = ? AND is_active
                """,
                                Long.class,
                                GROUP_ID,
                                SEMESTER_ID))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_items item
                JOIN schedule_assignment_replacement_templates mapping
                  ON mapping.target_schedule_item_id = item.id
                 WHERE mapping.operation_id = ? AND NOT mapping.source_was_active
                   AND item.is_active
                """,
                                Long.class,
                                OPERATION_ID))
                .isZero();

        RecurringAssignmentAuthority targetAuthority =
                new RecurringAssignmentAuthority(
                        TARGET_ASSIGNMENT_ID,
                        TARGET_TEACHER_ID,
                        SUBJECT_ID,
                        GROUP_ID,
                        SEMESTER_ID,
                        "lecture",
                        EFFECTIVE_FROM,
                        ASSIGNMENT_END);
        var targetGeneration =
                recurringWriter.write(
                        request(TARGET_ASSIGNMENT_ID),
                        UUID.randomUUID(),
                        ACTOR_ID,
                        targetAuthority,
                        LocalDate.of(2026, 10, 6),
                        GENERATION_END);
        assertThat(targetGeneration.generatedCount()).isEqualTo(1L);
        assertThat(
                        jdbc.queryForList(
                                """
                SELECT occurrence_date FROM lesson_occurrences
                 WHERE assignment_id = ? ORDER BY occurrence_date
                """,
                                LocalDate.class,
                                TARGET_ASSIGNMENT_ID))
                .containsExactly(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 11, 10));

        long inactiveHistoricalCloneId =
                jdbc.queryForObject(
                        """
                SELECT mapping.target_schedule_item_id
                  FROM schedule_assignment_replacement_templates mapping
                  JOIN schedule_items source_item ON source_item.id = mapping.source_schedule_item_id
                 WHERE mapping.operation_id = ? AND NOT mapping.source_was_active
                   AND source_item.day_of_week = 4 AND source_item.lesson_number = 3
                """,
                        Long.class,
                        OPERATION_ID);
        CreateScheduleItemRequest inactiveSlotRequest =
                new CreateScheduleItemRequest(
                        TARGET_ASSIGNMENT_ID,
                        GROUP_ID,
                        SUBJECT_ID,
                        SEMESTER_ID,
                        (short) 4,
                        (short) 3,
                        LocalTime.of(11, 40),
                        LocalTime.of(13, 0),
                        WeekType.ALL,
                        "R-303");
        var createdForInactiveHistory =
                recurringWriter.write(
                        inactiveSlotRequest,
                        UUID.randomUUID(),
                        ACTOR_ID,
                        targetAuthority,
                        EFFECTIVE_FROM,
                        GENERATION_END);
        assertThat(createdForInactiveHistory.generatedCount()).isPositive();
        assertThat(createdForInactiveHistory.scheduleItemId())
                .isNotEqualTo(inactiveHistoricalCloneId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM schedule_items WHERE id = ?",
                                Boolean.class,
                                createdForInactiveHistory.scheduleItemId()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM schedule_items WHERE id = ?",
                                Boolean.class,
                                inactiveHistoricalCloneId))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_items
                 WHERE group_id = ? AND day_of_week = 4 AND lesson_number = 3
                   AND week_type = 'all'::week_type AND semester_id = ? AND is_active
                """,
                                Long.class,
                                GROUP_ID,
                                SEMESTER_ID))
                .isEqualTo(1L);

        long deactivatedTargetTemplateId = targetGeneration.scheduleItemId();
        jdbc.update(
                "UPDATE schedule_items SET is_active = FALSE WHERE id = ?",
                deactivatedTargetTemplateId);
        var createAfterTargetTemplateRemoval =
                recurringWriter.write(
                        request(TARGET_ASSIGNMENT_ID),
                        UUID.randomUUID(),
                        ACTOR_ID,
                        targetAuthority,
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2026, 12, 30));
        assertThat(createAfterTargetTemplateRemoval.generatedCount()).isPositive();
        assertThat(createAfterTargetTemplateRemoval.scheduleItemId())
                .isNotEqualTo(deactivatedTargetTemplateId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM schedule_items WHERE id = ?",
                                Boolean.class,
                                createAfterTargetTemplateRemoval.scheduleItemId()))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM schedule_items WHERE id = ?",
                                Boolean.class,
                                deactivatedTargetTemplateId))
                .isFalse();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
                UPDATE schedule_assignment_rebind_ledger
                   SET result = 'SKIPPED_ACTIVE'
                 WHERE operation_id = ? AND result = 'MOVED'
                """,
                                        OPERATION_ID))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void cancelledOccurrenceRestoresThroughExactReplacementAndConflictRollsBack() throws Exception {
        long sourceTemplateId = insertSourceTemplate();
        LocalDate occurrenceDate = LocalDate.of(2026, 10, 6);
        SeededOccurrence source = seedOccurrence(sourceTemplateId, occurrenceDate, "planned");
        long bindingId = insertHomeworkBinding(source);
        UUID bindingRequestKey =
                jdbc.queryForObject(
                        """
                SELECT request_key FROM lesson_homework_bindings WHERE binding_id = ?
                """,
                        UUID.class,
                        bindingId);

        lifecycleWriter.cancel(source.lessonId(), "Teacher replaced", ACTOR_ID);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_homework_bindings
                 WHERE binding_id = ? AND state = 'ARCHIVED' AND revision = 2
                """,
                                Long.class,
                                bindingId))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_lifecycle_entries
                 WHERE occurrence_id = ? AND revision = 2 AND action = 'CANCELLED'
                """,
                                Long.class,
                                source.occurrenceId()))
                .isEqualTo(1L);

        long conflictingTemplateId = insertConflictingTemplate();
        SeededOccurrence conflict =
                seedOccurrence(
                        conflictingTemplateId,
                        occurrenceDate,
                        "planned",
                        OTHER_ASSIGNMENT_ID,
                        OTHER_TEACHER_ID);
        installAuthority(PAYLOAD_HASH);
        replacementService.install(installRequest(PAYLOAD_HASH));
        activateAuthority(PAYLOAD_HASH);
        AssignmentCloseReceipt committed = replacementService.commit(commitRequest(PAYLOAD_HASH));
        assertThat(committed.getState()).isEqualTo("COMMITTED");
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT result FROM schedule_assignment_rebind_ledger
                 WHERE operation_id = ? AND occurrence_id = ?
                """,
                                String.class,
                                OPERATION_ID,
                                source.occurrenceId()))
                .isEqualTo("SKIPPED_CANCELLED");

        assertThatThrownBy(() -> lifecycleWriter.restore(source.lessonId(), ACTOR_ID))
                .isInstanceOf(ConflictException.class);
        assertThat(outboxStorage.findPending(1000))
                .noneMatch(row -> "lesson.restored".equals(row.eventType()));
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_restore_authorities WHERE occurrence_id = ?
                """,
                                Long.class,
                                source.occurrenceId()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT current_lesson_id FROM lesson_occurrences WHERE id = ?
                """,
                                Long.class,
                                source.occurrenceId()))
                .isEqualTo(source.lessonId());
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT revision FROM lesson_occurrences WHERE id = ?
                """,
                                Long.class,
                                source.occurrenceId()))
                .isEqualTo(2L);

        lifecycleWriter.cancel(conflict.lessonId(), "Free the slot", ACTOR_ID);
        long restoredLessonId = lifecycleWriter.restore(source.lessonId(), ACTOR_ID);
        assertThat(restoredLessonId).isNotEqualTo(source.lessonId());
        var restoredEvents =
                outboxStorage.findPending(1000).stream()
                        .filter(row -> "lesson.restored".equals(row.eventType()))
                        .toList();
        assertThat(restoredEvents).hasSize(1);
        var restoredFact =
                new ObjectMapper().readTree(restoredEvents.getFirst().payload()).path("payload");
        assertThat(restoredFact.path("lesson_id").asLong()).isEqualTo(restoredLessonId);
        assertThat(restoredFact.path("source_lesson_id").asLong()).isEqualTo(source.lessonId());
        assertThat(restoredFact.path("assignment_id").asLong()).isEqualTo(TARGET_ASSIGNMENT_ID);
        assertThat(restoredFact.path("assigned_teacher_id").asLong()).isEqualTo(TARGET_TEACHER_ID);
        assertThat(restoredFact.path("lesson_date").asText()).isEqualTo(occurrenceDate.toString());
        assertThat(restoredFact.path("lesson_type").asText()).isEqualTo("lecture");
        assertThat(restoredFact.path("actor_user_id").asLong()).isEqualTo(ACTOR_ID);
        Long targetTemplateId =
                jdbc.queryForObject(
                        """
                SELECT target_schedule_item_id FROM schedule_assignment_replacement_templates
                 WHERE operation_id = ? AND source_schedule_item_id = ?
                """,
                        Long.class,
                        OPERATION_ID,
                        sourceTemplateId);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_occurrences occurrence
                JOIN lessons lesson ON lesson.id = occurrence.current_lesson_id
                 WHERE occurrence.id = ? AND occurrence.current_lesson_id = ?
                   AND occurrence.schedule_item_id = ? AND occurrence.assignment_id = ?
                   AND occurrence.assigned_teacher_id = ? AND occurrence.generation = 2
                   AND occurrence.revision = 3 AND lesson.id = ?
                   AND lesson.schedule_item_id = ? AND lesson.assignment_id = ?
                   AND lesson.assigned_teacher_id = ? AND lesson.generation = 2
                   AND lesson.revision = 1 AND lesson.status::text = 'planned'
                """,
                                Long.class,
                                source.occurrenceId(),
                                restoredLessonId,
                                targetTemplateId,
                                TARGET_ASSIGNMENT_ID,
                                TARGET_TEACHER_ID,
                                restoredLessonId,
                                targetTemplateId,
                                TARGET_ASSIGNMENT_ID,
                                TARGET_TEACHER_ID))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_lifecycle_entries
                 WHERE occurrence_id = ? AND revision = 3 AND action = 'RESTORED'
                   AND lesson_id = ? AND target_lesson_id = ? AND generation = 2
                   AND restore_operation_id IS NOT NULL
                """,
                                Long.class,
                                source.occurrenceId(),
                                source.lessonId(),
                                restoredLessonId))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_homework_bindings
                 WHERE binding_id = ? AND occurrence_id = ? AND current_lesson_id = ?
                   AND request_key = ? AND state = 'ARCHIVED' AND revision = 2
                """,
                                Long.class,
                                bindingId,
                                source.occurrenceId(),
                                source.lessonId(),
                                bindingRequestKey))
                .isEqualTo(1L);

        assertThatThrownBy(() -> lifecycleWriter.restore(source.lessonId(), ACTOR_ID))
                .isInstanceOf(ConflictException.class);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_restore_authorities WHERE occurrence_id = ?
                """,
                                Long.class,
                                source.occurrenceId()))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lessons WHERE occurrence_id = ?
                """,
                                Long.class,
                                source.occurrenceId()))
                .isEqualTo(2L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT current_lesson_id FROM lesson_occurrences WHERE id = ?
                """,
                                Long.class,
                                source.occurrenceId()))
                .isEqualTo(restoredLessonId);
        var listedGenerations =
                lessonRepository
                        .pageByGroupIdAndDateBetweenAndStatusIn(
                                GROUP_ID,
                                occurrenceDate,
                                occurrenceDate,
                                List.of("planned", "cancelled"),
                                PageRequest.of(0, 20))
                        .getContent()
                        .stream()
                        .filter(lesson -> lesson.getOccurrenceId().equals(source.occurrenceId()))
                        .map(lesson -> lesson.getId())
                        .toList();
        assertThat(listedGenerations).containsExactly(restoredLessonId);
        assertThat(
                        outboxStorage.findPending(1000).stream()
                                .filter(row -> "lesson.restored".equals(row.eventType()))
                                .map(row -> row.payload())
                                .toList())
                .containsExactly(restoredEvents.getFirst().payload());
    }

    @Test
    void exactReplayReturnsDurableReceiptAndAnotherHashCannotReuseOperation() {
        insertSourceTemplate();
        installAuthority(PAYLOAD_HASH);

        AssignmentCloseReceipt first = replacementService.install(installRequest(PAYLOAD_HASH));
        AssignmentCloseReceipt replay = replacementService.install(installRequest(PAYLOAD_HASH));
        assertThat(replay).isEqualTo(first);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_assignment_replacement_operations
                 WHERE operation_id = ?
                """,
                                Long.class,
                                OPERATION_ID))
                .isEqualTo(1L);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM schedule_assignment_replacement_templates
                 WHERE operation_id = ?
                """,
                                Long.class,
                                OPERATION_ID))
                .isEqualTo(0L);

        byte[] otherHash = new byte[32];
        java.util.Arrays.fill(otherHash, (byte) 0x6A);
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(
                        OPERATION_ID, SOURCE_ASSIGNMENT_ID))
                .thenReturn(authority(otherHash, "PREPARED", ASSIGNMENT_END, "PREPARED"));
        assertThatThrownBy(() -> replacementService.install(installRequest(otherHash)))
                .isInstanceOf(ConflictException.class);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT payload_hash FROM schedule_assignment_replacement_operations
                 WHERE operation_id = ?
                """,
                                byte[].class,
                                OPERATION_ID))
                .containsExactly(PAYLOAD_HASH);

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
            Future<AssignmentCloseReceipt> firstInstall =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                await(start);
                                return replacementService.install(installRequest(PAYLOAD_HASH));
                            });
            Future<AssignmentCloseReceipt> secondInstall =
                    pool.submit(
                            () -> {
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
            Future<AssignmentCloseReceipt> firstCommit =
                    pool.submit(
                            () -> {
                                commitReady.countDown();
                                await(commitStart);
                                return replacementService.commit(commitRequest(PAYLOAD_HASH));
                            });
            Future<AssignmentCloseReceipt> secondCommit =
                    pool.submit(
                            () -> {
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

            assertThat(
                            jdbc.queryForObject(
                                    """
                    SELECT COUNT(*) FROM schedule_assignment_replacement_operations
                     WHERE operation_id = ? AND state = 'COMMITTED'
                    """,
                                    Long.class,
                                    OPERATION_ID))
                    .isEqualTo(1L);
            assertThat(
                            jdbc.queryForObject(
                                    """
                    SELECT COUNT(*) FROM schedule_items
                     WHERE group_id = ? AND day_of_week = 2 AND lesson_number = 1
                       AND week_type = 'all'::week_type AND semester_id = ? AND is_active
                    """,
                                    Long.class,
                                    GROUP_ID,
                                    SEMESTER_ID))
                    .isEqualTo(1L);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private void installAuthority(byte[] hash) {
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(
                        OPERATION_ID, SOURCE_ASSIGNMENT_ID))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return authority(hash, "PREPARED", ASSIGNMENT_END, "PREPARED");
                        });
    }

    @Test
    void closureRetainsFutureCancelledAndHomeworkAndAdoptionUsesExactNullProvenance() {
        LocalDate cutoff = LocalDate.now(java.time.ZoneId.of("Europe/Moscow")).plusDays(2);
        long item = insertSourceTemplate();
        var planned = seedOccurrence(item, cutoff.plusDays(1), "planned");
        var cancelled = seedOccurrence(item, cutoff.plusDays(2), "planned");
        var held = seedOccurrence(item, cutoff.plusDays(3), "active");
        var past =
                seedOccurrence(
                        item,
                        LocalDate.now(java.time.ZoneId.of("Europe/Moscow")).minusDays(1),
                        "planned");
        long binding = insertHomeworkBinding(planned);
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .build());
        lifecycleWriter.cancel(cancelled.lessonId(), "fixture cancellation", ACTOR_ID);
        UUID close = UUID.randomUUID();
        configureLifecycle(close, "CLOSE_UNASSIGN", cutoff, "PREPARED");
        var install = lifecycleRequest(close, "CLOSE_UNASSIGN", cutoff);
        assertThat(replacementService.install(install).getState()).isEqualTo("APPLIED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM schedule_items WHERE id=?",
                                Boolean.class,
                                item))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT lifecycle_revision FROM schedule_items WHERE id=?",
                                Long.class,
                                item))
                .isEqualTo(2L);
        configureLifecycle(close, "CLOSE_UNASSIGN", cutoff, "APPLIED");
        var commit =
                CommitAssignmentCloseRequest.newBuilder()
                        .setOperationId(close.toString())
                        .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                        .build();
        var receipt = replacementService.commit(commit);
        assertThat(receipt.getState()).isEqualTo("COMMITTED");
        assertThat(receipt.getMovedCount()).isEqualTo(2);
        assertThat(replacementService.commit(commit)).isEqualTo(receipt);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM lessons WHERE id IN (?,?) AND assignment_id IS NULL AND assigned_teacher_id IS NULL",
                                Long.class,
                                planned.lessonId(),
                                cancelled.lessonId()))
                .isEqualTo(2L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status::text FROM lessons WHERE id=?",
                                String.class,
                                cancelled.lessonId()))
                .isEqualTo("cancelled");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM lesson_homework_bindings WHERE binding_id=? AND homework_id=991 AND state='ACTIVE' AND revision=1",
                                Long.class,
                                binding))
                .isEqualTo(1L);
        assertOccurrenceUnchanged(held, item, SOURCE_ASSIGNMENT_ID, SOURCE_TEACHER_ID, "active", 1);
        assertOccurrenceUnchanged(
                past, item, SOURCE_ASSIGNMENT_ID, SOURCE_TEACHER_ID, "planned", 1);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE lessons SET assignment_id=?,assigned_teacher_id=? WHERE id=?",
                                        TARGET_ASSIGNMENT_ID,
                                        TARGET_TEACHER_ID,
                                        planned.lessonId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "DELETE FROM schedule_assignment_binding_ledger WHERE operation_id=?",
                                        close))
                .isInstanceOf(DataAccessException.class);
        // Restore before reassignment keeps the NULL current binding and exact physical id.
        assertThat(lifecycleWriter.restore(cancelled.lessonId(), ACTOR_ID))
                .isEqualTo(cancelled.lessonId());
        lifecycleWriter.cancel(cancelled.lessonId(), "cancel again", ACTOR_ID);
        UUID adopt = UUID.randomUUID();
        LocalDate targetFrom = cutoff.plusDays(2);
        configureLifecycle(adopt, "ADOPT_UNASSIGNED", targetFrom, "PREPARED");
        replacementService.install(lifecycleRequest(adopt, "ADOPT_UNASSIGNED", targetFrom));
        configureLifecycle(adopt, "ADOPT_UNASSIGNED", targetFrom, "APPLIED");
        var adopted =
                replacementService.commit(
                        CommitAssignmentCloseRequest.newBuilder()
                                .setOperationId(adopt.toString())
                                .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                                .build());
        assertThat(adopted.getMovedCount()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT assignment_id FROM lessons WHERE id=?",
                                Long.class,
                                cancelled.lessonId()))
                .isEqualTo(TARGET_ASSIGNMENT_ID);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT assignment_id FROM lessons WHERE id=?",
                                Long.class,
                                planned.lessonId()))
                .isNull();
        assertThat(lifecycleWriter.restore(cancelled.lessonId(), ACTOR_ID))
                .isEqualTo(cancelled.lessonId());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM schedule_assignment_binding_ledger WHERE occurrence_id=?",
                                Long.class,
                                cancelled.occurrenceId()))
                .isEqualTo(2L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM lessons WHERE occurrence_id=?",
                                Long.class,
                                cancelled.occurrenceId()))
                .isEqualTo(1L);
        var transferred =
                transferWriter.transfer(
                        planned.lessonId(),
                        ACTOR_ID,
                        new ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest(
                                cutoff.plusDays(1),
                                5,
                                LocalTime.of(15, 20),
                                LocalTime.of(16, 40),
                                "R-404",
                                "2",
                                UUID.randomUUID()));
        assertThat(transferred).isNotNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM lessons lesson JOIN lesson_occurrences occurrence ON occurrence.current_lesson_id=lesson.id WHERE occurrence.id=? AND lesson.assignment_id IS NULL AND lesson.assigned_teacher_id IS NULL AND lesson.lesson_number=5 AND lesson.generation=2",
                                Long.class,
                                planned.occurrenceId()))
                .isEqualTo(1L);
    }

    @Test
    void adoptedOriginReplacesAndRestoresWithOrWithoutNewActiveTemplate() {
        for (boolean newTemplate : List.of(false, true)) {
            resetScheduleData();
            LocalDate cutoff = LocalDate.now(java.time.ZoneId.of("Europe/Moscow")).plusDays(2);
            long origin = insertSourceTemplate();
            var planned = seedOccurrence(origin, cutoff.plusDays(1), "planned");
            var cancelled = seedOccurrence(origin, cutoff.plusDays(2), "planned");
            var held = seedOccurrence(origin, cutoff.plusDays(3), "active");
            long homework = insertHomeworkBinding(planned);
            when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                    .thenReturn(
                            ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                    .setId(SEMESTER_ID)
                                    .build());
            lifecycleWriter.cancel(cancelled.lessonId(), "saved cancellation", ACTOR_ID);
            UUID close = UUID.randomUUID(), adopt = UUID.randomUUID(), replace = UUID.randomUUID();
            configureLifecycle(close, "CLOSE_UNASSIGN", cutoff, "PREPARED");
            replacementService.install(lifecycleRequest(close, "CLOSE_UNASSIGN", cutoff));
            configureLifecycle(close, "CLOSE_UNASSIGN", cutoff, "APPLIED");
            replacementService.commit(
                    CommitAssignmentCloseRequest.newBuilder()
                            .setOperationId(close.toString())
                            .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                            .build());
            configureLifecycle(adopt, "ADOPT_UNASSIGNED", cutoff, "PREPARED");
            replacementService.install(lifecycleRequest(adopt, "ADOPT_UNASSIGNED", cutoff));
            configureLifecycle(adopt, "ADOPT_UNASSIGNED", cutoff, "APPLIED");
            replacementService.commit(
                    CommitAssignmentCloseRequest.newBuilder()
                            .setOperationId(adopt.toString())
                            .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                            .build());
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT schedule_item_id FROM lessons WHERE id=?",
                                    Long.class,
                                    planned.lessonId()))
                    .isEqualTo(origin);
            Long activeB = null;
            if (newTemplate)
                activeB =
                        jdbc.queryForObject(
                                """
                    INSERT INTO schedule_items (assignment_id,group_id,subject_id,semester_id,day_of_week,lesson_number,start_time,end_time,week_type,room,is_active)
                    SELECT ?,group_id,subject_id,semester_id,day_of_week,lesson_number,start_time,end_time,week_type,room,true FROM schedule_items WHERE id=? RETURNING id
                    """,
                                Long.class,
                                TARGET_ASSIGNMENT_ID,
                                origin);
            var replacementAuthority =
                    PreparedAssignmentCloseResponse.newBuilder()
                            .setOperationId(replace.toString())
                            .setSourceAssignmentId(TARGET_ASSIGNMENT_ID)
                            .setTargetAssignmentId(504L)
                            .setSourceTeacherId(TARGET_TEACHER_ID)
                            .setTargetTeacherId(703L)
                            .setGroupId(GROUP_ID)
                            .setSubjectId(SUBJECT_ID)
                            .setSemesterId(SEMESTER_ID)
                            .setLessonType("lecture")
                            .setSourceValidFrom(cutoff.toString())
                            .setSourceValidUntilExclusive(ASSIGNMENT_END.toString())
                            .setTargetValidUntilExclusive(ASSIGNMENT_END.toString())
                            .setEffectiveFrom(cutoff.toString())
                            .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                            .setState("PREPARED")
                            .setTargetLifecycleState("PREPARED");
            when(academicGrpcClient.getPreparedAssignmentCloseOperation(
                            replace, TARGET_ASSIGNMENT_ID))
                    .thenAnswer(
                            call -> {
                                assertThat(
                                                TransactionSynchronizationManager
                                                        .isActualTransactionActive())
                                        .isFalse();
                                return replacementAuthority.build();
                            });
            replacementService.install(
                    InstallAssignmentCloseCapRequest.newBuilder()
                            .setOperationId(replace.toString())
                            .setSourceAssignmentId(TARGET_ASSIGNMENT_ID)
                            .setTargetAssignmentId(504L)
                            .setEffectiveFrom(cutoff.toString())
                            .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                            .build());
            replacementAuthority
                    .setState("APPLIED")
                    .setTargetLifecycleState("ACTIVE")
                    .setSourceValidUntilExclusive(cutoff.toString());
            var commit =
                    CommitAssignmentCloseRequest.newBuilder()
                            .setOperationId(replace.toString())
                            .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                            .build();
            var result = replacementService.commit(commit);
            assertThat(result.getMovedCount()).isEqualTo(1);
            assertThat(result.getSkippedCount()).isEqualTo(1);
            assertThat(replacementService.commit(commit)).isEqualTo(result);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM lessons WHERE id=? AND occurrence_id=? AND assignment_id=504 AND assigned_teacher_id=703 AND generation=1 AND revision=4",
                                    Long.class,
                                    planned.lessonId(),
                                    planned.occurrenceId()))
                    .isEqualTo(1L);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM lesson_homework_bindings WHERE binding_id=? AND current_lesson_id=? AND homework_id=991 AND revision=1 AND state='ACTIVE'",
                                    Long.class,
                                    homework,
                                    planned.lessonId()))
                    .isEqualTo(1L);
            assertOccurrenceUnchanged(
                    held, origin, SOURCE_ASSIGNMENT_ID, SOURCE_TEACHER_ID, "active", 1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM schedule_items WHERE assignment_id=504 AND is_active",
                                    Long.class))
                    .isEqualTo(newTemplate ? 1L : 0L);
            if (activeB != null)
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT is_active FROM schedule_items WHERE id=?",
                                        Boolean.class,
                                        activeB))
                        .isFalse();
            long restored = lifecycleWriter.restore(cancelled.lessonId(), ACTOR_ID);
            assertThat(restored).isNotEqualTo(cancelled.lessonId());
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM lessons WHERE id=? AND occurrence_id=? AND assignment_id=504 AND assigned_teacher_id=703 AND generation=2 AND status::text='planned'",
                                    Long.class,
                                    restored,
                                    cancelled.occurrenceId()))
                    .isEqualTo(1L);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM lessons WHERE id=? AND schedule_item_id=? AND assignment_id=? AND status::text='cancelled'",
                                    Long.class,
                                    cancelled.lessonId(),
                                    origin,
                                    TARGET_ASSIGNMENT_ID))
                    .isEqualTo(1L);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM schedule_assignment_rebind_ledger WHERE operation_id=?",
                                    Long.class,
                                    replace))
                    .isEqualTo(2L);
        }
    }

    @Test
    void terminalClosureClampsCreationCapAndDiscriminatorRejectsForgedAbsentSource() {
        long item = insertSourceTemplate();
        LocalDate cutoff = SOURCE_FROM.minusDays(1);
        UUID close = UUID.randomUUID();
        configureLifecycle(close, "CLOSE_UNASSIGN", cutoff, "PREPARED");
        assertThat(
                        replacementService
                                .install(lifecycleRequest(close, "CLOSE_UNASSIGN", cutoff))
                                .getState())
                .isEqualTo("APPLIED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT creation_cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id=?",
                                LocalDate.class,
                                SOURCE_ASSIGNMENT_ID))
                .isEqualTo(SOURCE_FROM);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id=?",
                                LocalDate.class,
                                SOURCE_ASSIGNMENT_ID))
                .isEqualTo(ASSIGNMENT_END);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM schedule_assignment_template_close_ledger WHERE operation_id=? AND schedule_item_id=?",
                                Long.class,
                                close,
                                item))
                .isEqualTo(1L);
        var exactSourceAuthority =
                academicGrpcClient.getPreparedAssignmentLifecycle(close, SOURCE_ASSIGNMENT_ID);
        when(academicGrpcClient.getPreparedAssignmentLifecycle(close, null))
                .thenReturn(exactSourceAuthority);
        assertThatThrownBy(
                        () ->
                                replacementService.install(
                                        lifecycleRequest(close, "CLOSE_UNASSIGN", cutoff)
                                                .toBuilder()
                                                .clearSourceAssignmentId()
                                                .build()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void oneOffCloseAdoptThenNextDayReplacementPreservesHistoryAndReplay() throws Exception {
        LocalDate today = oneOffWriter.today();
        LocalDate until = today.plusDays(30);
        var past = createOneOff(today.minusDays(1), (short) 1, today, until);
        var active = createOneOff(today, (short) 2, today, until);
        var future = createOneOff(today.plusDays(2), (short) 3, today, until);
        var cancelled = createOneOff(today.plusDays(3), (short) 4, today, until);
        jdbc.update("UPDATE lessons SET status='closed' WHERE id=?", past.physicalLessonId());
        jdbc.update("UPDATE lessons SET status='active' WHERE id=?", active.physicalLessonId());
        oneOffWriter.cancelPhysical(
                cancelled.physicalLessonId(), "retained cancellation", ACTOR_ID);
        long homework =
                insertHomeworkBinding(
                        new SeededOccurrence(future.occurrenceId(), future.physicalLessonId()));
        String pastBefore = oneOffSnapshot(past);
        String activeBefore = oneOffSnapshot(active);

        UUID close = UUID.randomUUID(), adopt = UUID.randomUUID(), replace = UUID.randomUUID();
        configureOneOffLifecycle(close, "CLOSE_UNASSIGN", today, until, "PREPARED");
        replacementService.install(lifecycleRequest(close, "CLOSE_UNASSIGN", today));
        configureOneOffLifecycle(close, "CLOSE_UNASSIGN", today, until, "APPLIED");
        var closeCommand = oneOffCommit(close);
        assertThat(replacementService.commit(closeCommand).getMovedCount()).isEqualTo(2);
        assertThat(replacementService.commit(closeCommand).getState()).isEqualTo("COMMITTED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT assignment_id FROM lessons WHERE id=?",
                                Long.class,
                                future.physicalLessonId()))
                .isNull();
        configureOneOffLifecycle(adopt, "ADOPT_UNASSIGNED", today, until, "PREPARED");
        replacementService.install(lifecycleRequest(adopt, "ADOPT_UNASSIGNED", today));
        configureOneOffLifecycle(adopt, "ADOPT_UNASSIGNED", today, until, "APPLIED");
        assertThat(replacementService.commit(oneOffCommit(adopt)).getMovedCount()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT assignment_id FROM lessons WHERE id=?",
                                Long.class,
                                future.physicalLessonId()))
                .isEqualTo(TARGET_ASSIGNMENT_ID);
        String cancelledBefore = oneOffSnapshot(cancelled);
        String futureBefore = oneOffSnapshot(future);
        String homeworkBefore =
                jdbc.queryForObject(
                        "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings binding WHERE binding_id=?",
                        String.class,
                        homework);
        var authority =
                oneOffReplacementAuthority(
                        replace,
                        TARGET_ASSIGNMENT_ID,
                        TARGET_TEACHER_ID,
                        OTHER_ASSIGNMENT_ID,
                        OTHER_TEACHER_ID,
                        today,
                        today.plusDays(1),
                        until);
        installOneOffReplacement(authority);
        authority
                .setState("APPLIED")
                .setTargetLifecycleState("ACTIVE")
                .setSourceValidUntilExclusive(today.plusDays(1).toString());
        var command = oneOffCommit(replace);
        var receipt = replacementService.commit(command);
        assertThat(receipt.getState()).isEqualTo("COMMITTED");
        assertThat(receipt.getMovedCount()).as("one-off replacement moved count").isEqualTo(1);
        assertThat(receipt.getSkippedCount()).isEqualTo(1);
        var expected = objectMapper.readTree(futureBefore);
        for (String key : List.of("occurrence", "lesson")) {
            var row = (ObjectNode) expected.get(key);
            row.put("assignment_id", OTHER_ASSIGNMENT_ID);
            row.put("assigned_teacher_id", OTHER_TEACHER_ID);
            row.put("revision", row.get("revision").asLong() + 1);
        }
        var expectedSnapshot = objectMapper.readTree(expected.toString());
        assertThat(objectMapper.readTree(oneOffSnapshot(future))).isEqualTo(expectedSnapshot);
        assertThat(oneOffSnapshot(past)).isEqualTo(pastBefore);
        assertThat(oneOffSnapshot(active)).isEqualTo(activeBefore);
        assertThat(oneOffSnapshot(cancelled)).isEqualTo(cancelledBefore);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings binding WHERE binding_id=?",
                                String.class,
                                homework))
                .isEqualTo(homeworkBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM schedule_items", Long.class)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM schedule_assignment_binding_ledger WHERE operation_id=?",
                                Long.class,
                                replace))
                .isEqualTo(1);
        var actions =
                jdbc.queryForList(
                        "SELECT payload FROM schedule_outbox WHERE event_type='schedule.action-recorded' AND payload::jsonb->'payload'->>'action'='LESSON_EDITED'",
                        String.class);
        assertThat(actions).hasSize(1);
        assertThat(replacementService.commit(command)).isEqualTo(receipt);
        assertThat(
                        jdbc.queryForList(
                                "SELECT payload FROM schedule_outbox WHERE event_type='schedule.action-recorded' AND payload::jsonb->'payload'->>'action'='LESSON_EDITED'",
                                String.class))
                .isEqualTo(actions);
        assertThat(objectMapper.readTree(oneOffSnapshot(future))).isEqualTo(expectedSnapshot);
    }

    @Test
    void oneOffReplacementRejectsUnknownStaleForeignAndUnconsumedBindingReceipts() {
        LocalDate today = oneOffWriter.today();
        LocalDate until = today.plusDays(30);
        var future = createOneOff(today.plusDays(2), (short) 3, today, until);
        UUID operation = UUID.randomUUID();
        var authority =
                oneOffReplacementAuthority(
                        operation,
                        SOURCE_ASSIGNMENT_ID,
                        SOURCE_TEACHER_ID,
                        TARGET_ASSIGNMENT_ID,
                        TARGET_TEACHER_ID,
                        today.minusDays(2),
                        today.plusDays(1),
                        until);
        installOneOffReplacement(authority);
        String before = oneOffSnapshot(future);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(
                status -> {
                    UUID missingFence = UUID.randomUUID();
                    jdbc.update(
                            """
                INSERT INTO schedule_assignment_replacement_operations
                    (operation_id,payload_hash,source_assignment_id,target_assignment_id,source_teacher_id,target_teacher_id,
                     group_id,subject_id,semester_id,lesson_type,source_valid_from,valid_until_exclusive,effective_from,state)
                SELECT ?,payload_hash,source_assignment_id,?,source_teacher_id,?,group_id,subject_id,semester_id,
                    lesson_type,source_valid_from,valid_until_exclusive,effective_from,'APPLIED'
                  FROM schedule_assignment_replacement_operations WHERE operation_id=?
                """,
                            missingFence,
                            OTHER_ASSIGNMENT_ID,
                            OTHER_TEACHER_ID,
                            operation);
                    jdbc.queryForObject(
                            "SELECT set_config('rutcampustrack.assignment_binding_operation_id', ?, true)",
                            String.class,
                            missingFence.toString());
                    assertThatThrownBy(
                                    () ->
                                            insertOneOffBinding(
                                                    missingFence,
                                                    future,
                                                    0,
                                                    OTHER_ASSIGNMENT_ID,
                                                    OTHER_TEACHER_ID))
                            .isInstanceOf(DataAccessException.class)
                            .hasStackTraceContaining("exact source and target fences");
                    status.setRollbackOnly();
                });
        for (int fault : List.of(0, 1, 2, 3)) {
            UUID selected = fault == 0 ? UUID.randomUUID() : operation;
            transaction.executeWithoutResult(
                    status -> {
                        jdbc.queryForObject(
                                "SELECT set_config('rutcampustrack.assignment_binding_operation_id', ?, true)",
                                String.class,
                                selected.toString());
                        assertThatThrownBy(
                                        () ->
                                                insertOneOffBinding(
                                                        selected,
                                                        future,
                                                        fault == 1 ? 1 : 0,
                                                        fault == 2
                                                                ? OTHER_ASSIGNMENT_ID
                                                                : TARGET_ASSIGNMENT_ID,
                                                        fault == 3
                                                                ? OTHER_TEACHER_ID
                                                                : TARGET_TEACHER_ID))
                                .isInstanceOf(DataAccessException.class);
                        status.setRollbackOnly();
                    });
            assertThat(oneOffSnapshot(future)).isEqualTo(before);
        }
        transaction.executeWithoutResult(
                status -> {
                    jdbc.queryForObject(
                            "SELECT set_config('rutcampustrack.assignment_binding_operation_id', ?, true)",
                            String.class,
                            operation.toString());
                    insertOneOffBinding(
                            operation, future, 0, TARGET_ASSIGNMENT_ID, TARGET_TEACHER_ID);
                    // Even an admitted receipt cannot change any field beyond binding/revision.
                    assertThatThrownBy(
                                    () ->
                                            jdbc.update(
                                                    "UPDATE lesson_occurrences SET assignment_id=?,assigned_teacher_id=?,revision=revision+1,generation=generation+1 WHERE id=?",
                                                    TARGET_ASSIGNMENT_ID,
                                                    TARGET_TEACHER_ID,
                                                    future.occurrenceId()))
                            .isInstanceOf(DataAccessException.class);
                    status.setRollbackOnly();
                });
        // A valid receipt left unconsumed must roll back at the deferred completion guard.
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            jdbc.queryForObject(
                                                    "SELECT set_config('rutcampustrack.assignment_binding_operation_id', ?, true)",
                                                    String.class,
                                                    operation.toString());
                                            insertOneOffBinding(
                                                    operation,
                                                    future,
                                                    0,
                                                    TARGET_ASSIGNMENT_ID,
                                                    TARGET_TEACHER_ID);
                                            jdbc.update(
                                                    "UPDATE lesson_occurrences SET assignment_id=?,assigned_teacher_id=?,revision=revision+1 WHERE id=?",
                                                    TARGET_ASSIGNMENT_ID,
                                                    TARGET_TEACHER_ID,
                                                    future.occurrenceId());
                                        }))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("assignment binding receipt must be consumed");
        assertThat(oneOffSnapshot(future)).isEqualTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM schedule_assignment_binding_ledger WHERE operation_id=?",
                                Long.class,
                                operation))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT state FROM schedule_assignment_replacement_operations WHERE operation_id=?",
                                String.class,
                                operation))
                .isEqualTo("APPLIED");
        authority
                .setState("APPLIED")
                .setTargetLifecycleState("ACTIVE")
                .setSourceValidUntilExclusive(today.plusDays(1).toString());
        assertThat(replacementService.commit(oneOffCommit(operation)).getMovedCount()).isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE schedule_assignment_binding_ledger SET after_teacher_id=? WHERE operation_id=?",
                                        OTHER_TEACHER_ID,
                                        operation))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE lessons SET assignment_id=?,assigned_teacher_id=?,revision=revision+1 WHERE id=?",
                                        SOURCE_ASSIGNMENT_ID,
                                        SOURCE_TEACHER_ID,
                                        future.physicalLessonId()))
                .isInstanceOf(DataAccessException.class);
    }

    private OneOffLessonWriter.Creation createOneOff(
            LocalDate date, short number, LocalDate today, LocalDate until) {
        return oneOffWriter.create(
                new CreateOneOffLessonRequest(
                        GROUP_ID,
                        SUBJECT_ID,
                        SOURCE_ASSIGNMENT_ID,
                        date,
                        number,
                        null,
                        null,
                        "one-off replacement"),
                UUID.randomUUID(),
                ACTOR_ID,
                new RecurringAssignmentAuthority(
                        SOURCE_ASSIGNMENT_ID,
                        SOURCE_TEACHER_ID,
                        SUBJECT_ID,
                        GROUP_ID,
                        SEMESTER_ID,
                        "lecture",
                        today.minusDays(2),
                        until));
    }

    private String oneOffSnapshot(OneOffLessonWriter.Creation row) {
        return jdbc.queryForObject(
                "SELECT jsonb_build_object('origin',to_jsonb(origin),'occurrence',to_jsonb(occurrence),'lesson',to_jsonb(lesson))::text FROM schedule_one_off_lessons origin JOIN lesson_occurrences occurrence ON occurrence.one_off_lesson_id=origin.id JOIN lessons lesson ON lesson.id=occurrence.current_lesson_id WHERE occurrence.id=?",
                String.class,
                row.occurrenceId());
    }

    private void configureOneOffLifecycle(
            UUID id, String kind, LocalDate today, LocalDate until, String state) {
        boolean close = "CLOSE_UNASSIGN".equals(kind);
        var authority =
                PreparedAssignmentCloseResponse.newBuilder()
                        .setOperationId(id.toString())
                        .setOperationKind(kind)
                        .setGroupId(GROUP_ID)
                        .setSubjectId(SUBJECT_ID)
                        .setSemesterId(SEMESTER_ID)
                        .setLessonType("lecture")
                        .setSourceValidFrom((close ? today.minusDays(2) : today).toString())
                        .setSourceValidUntilExclusive(until.toString())
                        .setTargetValidUntilExclusive(until.toString())
                        .setRequestedCutoff(today.toString())
                        .setEffectiveFrom(today.toString())
                        .setState(state)
                        .setTargetLifecycleState("PREPARED".equals(state) ? "PREPARED" : "ACTIVE")
                        .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH));
        if (close)
            authority
                    .setSourceAssignmentId(SOURCE_ASSIGNMENT_ID)
                    .setSourceTeacherId(SOURCE_TEACHER_ID);
        else
            authority
                    .setTargetAssignmentId(TARGET_ASSIGNMENT_ID)
                    .setTargetTeacherId(TARGET_TEACHER_ID);
        when(academicGrpcClient.getPreparedAssignmentLifecycle(
                        id, close ? SOURCE_ASSIGNMENT_ID : null))
                .thenReturn(authority.build());
    }

    private PreparedAssignmentCloseResponse.Builder oneOffReplacementAuthority(
            UUID id,
            long sourceAssignment,
            long sourceTeacher,
            long targetAssignment,
            long targetTeacher,
            LocalDate sourceFrom,
            LocalDate effective,
            LocalDate until) {
        return PreparedAssignmentCloseResponse.newBuilder()
                .setOperationId(id.toString())
                .setOperationKind("REPLACE")
                .setSourceAssignmentId(sourceAssignment)
                .setSourceTeacherId(sourceTeacher)
                .setTargetAssignmentId(targetAssignment)
                .setTargetTeacherId(targetTeacher)
                .setGroupId(GROUP_ID)
                .setSubjectId(SUBJECT_ID)
                .setSemesterId(SEMESTER_ID)
                .setLessonType("lecture")
                .setSourceValidFrom(sourceFrom.toString())
                .setSourceValidUntilExclusive(until.toString())
                .setTargetValidUntilExclusive(until.toString())
                .setEffectiveFrom(effective.toString())
                .setState("PREPARED")
                .setTargetLifecycleState("PREPARED")
                .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH));
    }

    private void installOneOffReplacement(PreparedAssignmentCloseResponse.Builder authority) {
        UUID id = UUID.fromString(authority.getOperationId());
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(
                        id, authority.getSourceAssignmentId()))
                .thenAnswer(call -> authority.build());
        replacementService.install(
                InstallAssignmentCloseCapRequest.newBuilder()
                        .setOperationId(id.toString())
                        .setSourceAssignmentId(authority.getSourceAssignmentId())
                        .setTargetAssignmentId(authority.getTargetAssignmentId())
                        .setEffectiveFrom(authority.getEffectiveFrom())
                        .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                        .build());
    }

    private static CommitAssignmentCloseRequest oneOffCommit(UUID id) {
        return CommitAssignmentCloseRequest.newBuilder()
                .setOperationId(id.toString())
                .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                .build();
    }

    private void insertOneOffBinding(
            UUID id, OneOffLessonWriter.Creation row, int stale, long assignment, long teacher) {
        jdbc.update(
                """
            INSERT INTO schedule_assignment_binding_ledger(operation_id,semester_id,occurrence_id,lesson_id,occurrence_date,
                expected_occurrence_revision,after_occurrence_revision,expected_lesson_revision,after_lesson_revision,
                before_assignment_id,before_teacher_id,after_assignment_id,after_teacher_id)
            SELECT ?,occurrence.semester_id,occurrence.id,lesson.id,occurrence.occurrence_date,
                occurrence.revision+?,occurrence.revision+?+1,lesson.revision,lesson.revision+1,
                occurrence.assignment_id,occurrence.assigned_teacher_id,?,?
            FROM lesson_occurrences occurrence JOIN lessons lesson ON lesson.id=occurrence.current_lesson_id WHERE occurrence.id=?
            """,
                id,
                stale,
                stale,
                assignment,
                teacher,
                row.occurrenceId());
    }

    private void configureLifecycle(UUID id, String kind, LocalDate cutoff, String state) {
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .build());
        boolean close = "CLOSE_UNASSIGN".equals(kind);
        when(academicGrpcClient.getPreparedAssignmentLifecycle(
                        id, close ? SOURCE_ASSIGNMENT_ID : null))
                .thenAnswer(
                        call -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            var result =
                                    PreparedAssignmentCloseResponse.newBuilder()
                                            .setOperationId(id.toString())
                                            .setOperationKind(kind)
                                            .setGroupId(GROUP_ID)
                                            .setSubjectId(SUBJECT_ID)
                                            .setSemesterId(SEMESTER_ID)
                                            .setLessonType("lecture")
                                            .setSourceValidFrom(
                                                    (close ? SOURCE_FROM : cutoff).toString())
                                            .setSourceValidUntilExclusive(ASSIGNMENT_END.toString())
                                            .setTargetValidUntilExclusive(ASSIGNMENT_END.toString())
                                            .setRequestedCutoff(cutoff.toString())
                                            .setEffectiveFrom(
                                                    (close && cutoff.isBefore(SOURCE_FROM)
                                                                    ? SOURCE_FROM
                                                                    : cutoff)
                                                            .toString())
                                            .setState(state)
                                            .setTargetLifecycleState(
                                                    "PREPARED".equals(state)
                                                            ? "PREPARED"
                                                            : "ACTIVE")
                                            .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH));
                            if (close)
                                result.setSourceAssignmentId(SOURCE_ASSIGNMENT_ID)
                                        .setSourceTeacherId(SOURCE_TEACHER_ID);
                            else
                                result.setTargetAssignmentId(TARGET_ASSIGNMENT_ID)
                                        .setTargetTeacherId(TARGET_TEACHER_ID);
                            return result.build();
                        });
    }

    private static InstallAssignmentCloseCapRequest lifecycleRequest(
            UUID id, String kind, LocalDate cutoff) {
        var request =
                InstallAssignmentCloseCapRequest.newBuilder()
                        .setOperationId(id.toString())
                        .setOperationKind(kind)
                        .setPayloadHash(ByteString.copyFrom(PAYLOAD_HASH))
                        .setEffectiveFrom(
                                ("CLOSE_UNASSIGN".equals(kind) && cutoff.isBefore(SOURCE_FROM)
                                                ? SOURCE_FROM
                                                : cutoff)
                                        .toString());
        if ("CLOSE_UNASSIGN".equals(kind)) request.setSourceAssignmentId(SOURCE_ASSIGNMENT_ID);
        else request.setTargetAssignmentId(TARGET_ASSIGNMENT_ID);
        return request.build();
    }

    private void activateAuthority(byte[] hash) {
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(
                        OPERATION_ID, SOURCE_ASSIGNMENT_ID))
                .thenAnswer(
                        invocation -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            return authority(hash, "APPLIED", EFFECTIVE_FROM, "ACTIVE");
                        });
    }

    private long insertSourceTemplate() {
        jdbc.update(
                """
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, ?, 'lecture', ?, ?, ?)
                """,
                SOURCE_ASSIGNMENT_ID,
                GROUP_ID,
                SUBJECT_ID,
                SEMESTER_ID,
                SOURCE_TEACHER_ID,
                SOURCE_FROM,
                ASSIGNMENT_END,
                ASSIGNMENT_END);
        jdbc.update(
                """
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, ?, 'lecture', ?, ?, ?)
                """,
                OTHER_ASSIGNMENT_ID,
                GROUP_ID,
                SUBJECT_ID,
                SEMESTER_ID,
                OTHER_TEACHER_ID,
                SOURCE_FROM,
                ASSIGNMENT_END,
                ASSIGNMENT_END);
        Long id =
                jdbc.queryForObject(
                        """
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 2, 1, TIME '08:30', TIME '10:00',
                        'all'::week_type, 'R-101', TRUE, NOW())
                RETURNING id
                """,
                        Long.class,
                        SOURCE_ASSIGNMENT_ID,
                        GROUP_ID,
                        SUBJECT_ID,
                        SEMESTER_ID);
        jdbc.update(
                """
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 3, 2, TIME '10:15', TIME '11:45',
                        'all'::week_type, 'R-202', FALSE, NOW())
                """,
                SOURCE_ASSIGNMENT_ID,
                GROUP_ID,
                SUBJECT_ID,
                SEMESTER_ID);
        jdbc.update(
                """
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 3, 2, TIME '10:15', TIME '11:45',
                        'all'::week_type, 'R-202', TRUE, NOW())
                """,
                OTHER_ASSIGNMENT_ID,
                GROUP_ID,
                SUBJECT_ID,
                SEMESTER_ID);
        jdbc.update(
                """
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 4, 3, TIME '12:00', TIME '13:00',
                        'all'::week_type, 'R-303', FALSE, NOW())
                """,
                SOURCE_ASSIGNMENT_ID,
                GROUP_ID,
                SUBJECT_ID,
                SEMESTER_ID);
        return id;
    }

    private long insertConflictingTemplate() {
        return jdbc.queryForObject(
                """
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 2, 1, TIME '08:30', TIME '10:00',
                        'all'::week_type, 'R-101', FALSE, NOW())
                RETURNING id
                """,
                Long.class,
                OTHER_ASSIGNMENT_ID,
                GROUP_ID,
                SUBJECT_ID,
                SEMESTER_ID);
    }

    private void assertPendingTargetWritesBlocked() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        """
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 4, 3, TIME '12:00', TIME '13:00',
                        'all'::week_type, 'R-303', FALSE, NOW())
                """,
                                        TARGET_ASSIGNMENT_ID,
                                        GROUP_ID,
                                        SUBJECT_ID,
                                        SEMESTER_ID))
                .isInstanceOf(DataAccessException.class);
        RecurringAssignmentAuthority targetAuthority =
                new RecurringAssignmentAuthority(
                        TARGET_ASSIGNMENT_ID,
                        TARGET_TEACHER_ID,
                        SUBJECT_ID,
                        GROUP_ID,
                        SEMESTER_ID,
                        "lecture",
                        EFFECTIVE_FROM,
                        ASSIGNMENT_END);
        assertThatThrownBy(
                        () ->
                                recurringWriter.write(
                                        request(TARGET_ASSIGNMENT_ID),
                                        UUID.randomUUID(),
                                        ACTOR_ID,
                                        targetAuthority,
                                        LocalDate.of(2026, 10, 1),
                                        GENERATION_END))
                .isInstanceOf(RecurringProtocolConflictException.class);
    }

    private SeededOccurrence seedOccurrence(long templateId, LocalDate date, String status) {
        return seedOccurrence(templateId, date, status, SOURCE_ASSIGNMENT_ID, SOURCE_TEACHER_ID);
    }

    private SeededOccurrence seedOccurrence(
            long templateId, LocalDate date, String status, long assignmentId, long teacherId) {
        Long occurrenceId =
                jdbc.queryForObject(
                        """
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type,
                     generation, revision, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture', 1, 1, NOW())
                RETURNING id
                """,
                        Long.class,
                        templateId,
                        date,
                        assignmentId,
                        GROUP_ID,
                        SUBJECT_ID,
                        SEMESTER_ID,
                        teacherId);
        if (status == null) return new SeededOccurrence(occurrenceId, null);
        Long lessonId =
                jdbc.queryForObject(
                        """
                INSERT INTO lessons
                    (schedule_item_id, occurrence_id, assignment_id, group_id, subject_id,
                     semester_id, assigned_teacher_id, lesson_type, lesson_number,
                     day_of_week, start_time, end_time, room_snapshot, week_type_snapshot,
                     generation, revision, date, status, is_geo_blocked, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture', 1, 2,
                        TIME '08:30', TIME '10:00', 'R-101', 'all', 1, 1, ?,
                        ?::lesson_status, FALSE, NOW())
                RETURNING id
                """,
                        Long.class,
                        templateId,
                        occurrenceId,
                        assignmentId,
                        GROUP_ID,
                        SUBJECT_ID,
                        SEMESTER_ID,
                        teacherId,
                        date,
                        status);
        jdbc.update(
                "UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                lessonId,
                occurrenceId);
        jdbc.update(
                """
                INSERT INTO lesson_lifecycle_entries
                    (occurrence_id, revision, action, lesson_id, generation, actor_id, occurred_at)
                VALUES (?, 1, 'CREATED', ?, 1, ?, NOW())
                """,
                occurrenceId,
                lessonId,
                ACTOR_ID);
        return new SeededOccurrence(occurrenceId, lessonId);
    }

    private long insertHomeworkBinding(SeededOccurrence occurrence) {
        return jdbc.queryForObject(
                """
                INSERT INTO lesson_homework_bindings
                    (occurrence_id, current_lesson_id, homework_id, actor_id, request_key,
                     payload_hash, state, revision, created_at, updated_at)
                VALUES (?, ?, 991, ?, ?, ?, 'ACTIVE', 1, NOW(), NOW())
                RETURNING binding_id
                """,
                Long.class,
                occurrence.occurrenceId(),
                occurrence.lessonId(),
                ACTOR_ID,
                UUID.randomUUID(),
                PAYLOAD_HASH);
    }

    private void assertOccurrenceUnchanged(
            SeededOccurrence occurrence,
            long templateId,
            long assignmentId,
            long teacherId,
            String status,
            long revision) {
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_occurrences occurrence
                JOIN lessons lesson ON lesson.id = occurrence.current_lesson_id
                 WHERE occurrence.id = ? AND occurrence.schedule_item_id = ?
                   AND occurrence.assignment_id = ? AND occurrence.assigned_teacher_id = ?
                   AND occurrence.current_lesson_id = ? AND lesson.id = ?
                   AND lesson.assignment_id = ? AND lesson.assigned_teacher_id = ?
                   AND lesson.status::text = ? AND occurrence.revision = ? AND lesson.revision = ?
                """,
                                Long.class,
                                occurrence.occurrenceId(),
                                templateId,
                                assignmentId,
                                teacherId,
                                occurrence.lessonId(),
                                occurrence.lessonId(),
                                assignmentId,
                                teacherId,
                                status,
                                revision,
                                revision))
                .isEqualTo(1L);
    }

    private void assertOccurrenceMoved(SeededOccurrence occurrence) {
        Long targetTemplateId =
                jdbc.queryForObject(
                        """
                SELECT target_schedule_item_id FROM schedule_assignment_rebind_ledger
                 WHERE operation_id = ? AND occurrence_id = ? AND result = 'MOVED'
                """,
                        Long.class,
                        OPERATION_ID,
                        occurrence.occurrenceId());
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT COUNT(*) FROM lesson_occurrences occurrence
                JOIN lessons lesson ON lesson.id = occurrence.current_lesson_id
                 WHERE occurrence.id = ? AND occurrence.current_lesson_id = ?
                   AND occurrence.schedule_item_id = ? AND occurrence.assignment_id = ?
                   AND occurrence.assigned_teacher_id = ? AND occurrence.revision = 2
                   AND lesson.id = ? AND lesson.schedule_item_id = ? AND lesson.assignment_id = ?
                   AND lesson.assigned_teacher_id = ? AND lesson.status::text = 'planned'
                   AND lesson.revision = 2
                """,
                                Long.class,
                                occurrence.occurrenceId(),
                                occurrence.lessonId(),
                                targetTemplateId,
                                TARGET_ASSIGNMENT_ID,
                                TARGET_TEACHER_ID,
                                occurrence.lessonId(),
                                targetTemplateId,
                                TARGET_ASSIGNMENT_ID,
                                TARGET_TEACHER_ID))
                .isEqualTo(1L);
    }

    private static PreparedAssignmentCloseResponse authority(
            byte[] hash, String state, LocalDate sourceEnd, String targetState) {
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
        return new CreateScheduleItemRequest(
                assignmentId,
                GROUP_ID,
                SUBJECT_ID,
                SEMESTER_ID,
                (short) 2,
                (short) 1,
                LocalTime.of(8, 30),
                LocalTime.of(9, 50),
                WeekType.ALL,
                "R-101");
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

    private record SeededOccurrence(long occurrenceId, Long lessonId) {}
}
