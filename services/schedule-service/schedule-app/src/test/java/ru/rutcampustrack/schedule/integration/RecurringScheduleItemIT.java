package ru.rutcampustrack.schedule.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.exception.RecurringProtocolConflictException;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority;
import ru.rutcampustrack.schedule.recurring.RecurringCreateResult;
import ru.rutcampustrack.schedule.recurring.RecurringScheduleItemWriter;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.HexFormat;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL checks for the recurring source contract. Academic RPCs are
 * mocked at the wrapper boundary; all local writes, replay, locks and guards
 * run against the Testcontainers PostgreSQL instance from the base harness.
 */
@AutoConfigureMockMvc
class RecurringScheduleItemIT extends AbstractScheduleIntegrationTest {

    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID = 20L;
    private static final Long SEMESTER_ID = 30L;
    private static final Long ASSIGNMENT_ID = 501L;
    private static final Long TEACHER_ID = 700L;
    private static final Long ACTOR_ID = 42L;

    private static final LocalDate SEMESTER_FROM = LocalDate.of(2026, 2, 2);
    private static final LocalDate SEMESTER_TO = LocalDate.of(2026, 2, 22);
    private static final LocalDate ASSIGNMENT_END = LocalDate.of(2026, 2, 23);

    private static final SemesterResponse ACTIVE_SEMESTER = SemesterResponse.newBuilder()
            .setId(SEMESTER_ID)
            .setName("Recurring test semester")
            .setDateFrom(SEMESTER_FROM.toString())
            .setDateTo(SEMESTER_TO.toString())
            .setFirstWeekType("odd")
            .build();

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @MockitoBean
    Clock clock;

    @Autowired
    ru.rutcampustrack.schedule.recurring.RecurringScheduleItemLifecycleWriter lifecycle;

    @Autowired
    ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence archiveFence;

    @Autowired
    ru.rutcampustrack.schedule.lesson.LessonTransferWriter transferWriter;

    @Autowired
    ru.rutcampustrack.schedule.lesson.RecurringLessonLifecycleWriter lessonLifecycle;

    @Autowired
    ru.rutcampustrack.schedule.replacement.AssignmentReplacementService replacementService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecurringScheduleItemWriter writer;

    @Autowired
    LessonRepository lessonRepository;

    @BeforeEach
    void setUp() {
        resetScheduleData();
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Moscow"));
        when(clock.instant()).thenReturn(Instant.parse("2026-02-09T09:00:00Z"));
        when(clock.withZone(any())).thenAnswer(invocation -> Clock.fixed(clock.instant(), invocation.getArgument(0)));
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                .thenAnswer(invocation -> {
                    return SemesterStateResponse.newBuilder().setId(SEMESTER_ID).setStateVersion(1).build();
                });
        when(academicGrpcClient.validateGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID)
                .setName("Recurring group")
                .setIsActive(true)
                .build());
        when(academicGrpcClient.getActiveSemester()).thenReturn(ACTIVE_SEMESTER);
        when(academicGrpcClient.isHeadman(ACTOR_ID, GROUP_ID)).thenReturn(true);
        when(academicGrpcClient.getAssignmentsByIds(List.of(ASSIGNMENT_ID)))
                .thenReturn(List.of(assignment(ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID,
                        SEMESTER_FROM, ASSIGNMENT_END)));
    }

    @Test
    void endpointPersistsSnapshotsAndPastSemesterDates() throws Exception {
        UUID key = UUID.randomUUID();
        CreateScheduleItemRequest request = request(1, 1, "A-101", ASSIGNMENT_ID,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID);

        MvcResult result = postCreate(key, request);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.get("generatedCount").asLong()).isEqualTo(3L);
        assertThat(body.get("generatedFrom").asText()).isEqualTo("2026-02-02");
        assertThat(body.get("generatedUntil").asText()).isEqualTo("2026-02-16");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM lessons
                 WHERE day_of_week = 1 AND week_type_snapshot = 'all'
                """, Long.class)).isEqualTo(3L);
        assertThat(lessonRepository.findAll()).allMatch(lesson ->
                lesson.getDate().isBefore(LocalDate.of(2026, 3, 1)));
    }

    @Test
    void endpointReplaysWithoutDuplicateAndRejectsDifferentPayload() throws Exception {
        UUID key = UUID.randomUUID();
        CreateScheduleItemRequest original = request(1, 1, "A-101", ASSIGNMENT_ID,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID);

        MvcResult firstResult = postCreate(key, original);
        JsonNode first = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        MvcResult replayResult = postCreate(key, original);
        JsonNode replay = objectMapper.readTree(replayResult.getResponse().getContentAsString());

        assertThat(first.get("id").asLong()).isEqualTo(replay.get("id").asLong());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_items", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_recurring_create_replay", Long.class)).isEqualTo(1L);
        assertThat(lessonRepository.count()).isEqualTo(3L);

        CreateScheduleItemRequest differentPayload = request(1, 1, "B-202", ASSIGNMENT_ID,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        mockMvc.perform(createBuilder(key, differentPayload))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_items", Long.class)).isEqualTo(1L);
    }

    @Test
    void activeMismatchedGroupAuthorityHasZeroLocalEffects() throws Exception {
        when(academicGrpcClient.validateGroup(GROUP_ID)).thenReturn(GroupResponse.newBuilder()
                .setId(GROUP_ID + 1)
                .setName("Wrong group")
                .setIsActive(true)
                .build());

        mockMvc.perform(createBuilder(UUID.randomUUID(), request(1, 1, "A-101", ASSIGNMENT_ID,
                        GROUP_ID, SUBJECT_ID, SEMESTER_ID)))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_items", Long.class)).isEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_assignment_fences", Long.class)).isEqualTo(0L);
    }

    @Test
    void existingV17TemplateBootstrapsFenceWithoutChangingItsProvenance() {
        long assignmentId = 9001L;
        jdbcTemplate.update("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 2, 1, TIME '08:30', TIME '10:00',
                        'all'::week_type, 'V17-ROOM', TRUE, NOW())
                """, assignmentId, GROUP_ID, SUBJECT_ID, SEMESTER_ID);

        RecurringAssignmentAuthority authority = assignmentAuthority(assignmentId, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        writer.write(request(3, 1, "A-101", assignmentId, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, authority, SEMESTER_FROM, SEMESTER_TO);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_assignment_fences WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT room FROM schedule_items WHERE assignment_id = ? AND day_of_week = 2",
                String.class, assignmentId)).isEqualTo("V17-ROOM");
    }

    @Test
    void populatedV17CancelledPhysicalAndOccurrenceBootstrapFence() {
        long assignmentId = 9002L;
        long scheduleItemId = jdbcTemplate.queryForObject("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 2, 1, TIME '08:30', TIME '10:00',
                        'all'::week_type, 'V17-ROOM', TRUE, NOW())
                RETURNING id
                """, Long.class, assignmentId, GROUP_ID, SUBJECT_ID, SEMESTER_ID);

        long[] retainedIds = new long[2];
        // These rows model data written before V18's fence guards existed.
        // Keep trigger toggles in separate auto-commit statements: PostgreSQL
        // rejects ALTER TABLE while this transaction has pending trigger
        // events. The actual V17 -> V18 migration remains covered by the
        // dedicated migration test below.
        try {
            jdbcTemplate.execute("ALTER TABLE lesson_occurrences DISABLE TRIGGER lesson_occurrences_fence_guard_trg");
            jdbcTemplate.execute("ALTER TABLE lessons DISABLE TRIGGER lessons_physical_insert_fence_guard_trg");
            retainedIds[0] = jdbcTemplate.queryForObject("""
                    INSERT INTO lesson_occurrences
                        (schedule_item_id, occurrence_date, assignment_id, group_id,
                         subject_id, semester_id, assigned_teacher_id, lesson_type,
                         generation, revision, created_at)
                    VALUES (?, DATE '2026-02-02', ?, ?, ?, ?, 700, 'lecture', 1, 1, NOW())
                    RETURNING id
                    """, Long.class, scheduleItemId, assignmentId, GROUP_ID,
                    SUBJECT_ID, SEMESTER_ID);
            retainedIds[1] = jdbcTemplate.queryForObject("""
                    INSERT INTO lessons
                        (schedule_item_id, occurrence_id, assignment_id, group_id,
                         subject_id, semester_id, assigned_teacher_id, lesson_type,
                         lesson_number, day_of_week, start_time, end_time, room_snapshot,
                         week_type_snapshot, generation, revision, date, status,
                         is_geo_blocked, created_at, closed_at)
                    VALUES (?, ?, ?, ?, ?, ?, 700, 'lecture', 1, 2,
                            TIME '08:30', TIME '10:00', 'V17-ROOM', 'all', 1, 1,
                            DATE '2026-02-02', 'cancelled'::lesson_status,
                            FALSE, NOW(), NULL)
                    RETURNING id
                    """, Long.class, scheduleItemId, retainedIds[0], assignmentId,
                    GROUP_ID, SUBJECT_ID, SEMESTER_ID);
            jdbcTemplate.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                    retainedIds[1], retainedIds[0]);
        } finally {
            jdbcTemplate.execute("ALTER TABLE lessons ENABLE TRIGGER lessons_physical_insert_fence_guard_trg");
            jdbcTemplate.execute("ALTER TABLE lesson_occurrences ENABLE TRIGGER lesson_occurrences_fence_guard_trg");
        }

        RecurringAssignmentAuthority authority = assignmentAuthority(assignmentId, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        writer.write(request(3, 1, "A-101", assignmentId, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, authority, SEMESTER_FROM, SEMESTER_TO);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status::text FROM lessons WHERE id = ?", String.class, retainedIds[1]))
                .isEqualTo("cancelled");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_lesson_id FROM lesson_occurrences WHERE id = ?", Long.class, retainedIds[0]))
                .isEqualTo(retainedIds[1]);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_assignment_fences WHERE assignment_id = ?",
                Long.class, assignmentId)).isEqualTo(1L);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE schedule_assignment_fences
                   SET cap_until_exclusive = DATE '2026-02-02'
                 WHERE assignment_id = ?
                """, assignmentId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void contradictoryV17TemplateBootstrapRollsBackFenceAtomically() {
        long assignmentId = 9003L;
        jdbcTemplate.update("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room, is_active, created_at)
                VALUES (?, ?, ?, ?, 2, 1, TIME '08:30', TIME '10:00',
                        'all'::week_type, 'CONTRADICTORY', TRUE, NOW())
                """, assignmentId, GROUP_ID + 1, SUBJECT_ID, SEMESTER_ID);

        RecurringAssignmentAuthority authority = assignmentAuthority(assignmentId, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        assertThatThrownBy(() -> writer.write(
                request(3, 1, "A-101", assignmentId, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, authority, SEMESTER_FROM, SEMESTER_TO))
                .isInstanceOf(RecurringProtocolConflictException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_assignment_fences WHERE assignment_id = ?",
                Long.class, assignmentId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_items", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Long.class)).isZero();
    }

    @Test
    void sameActorKeyAcrossAssignmentsConflictsBeforeSecondFence() {
        UUID key = UUID.randomUUID();
        RecurringAssignmentAuthority firstAuthority = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        writer.write(request(1, 1, "A-101", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                key, ACTOR_ID, firstAuthority, SEMESTER_FROM, SEMESTER_TO);

        long secondAssignmentId = ASSIGNMENT_ID + 10;
        RecurringAssignmentAuthority secondAuthority = assignmentAuthority(secondAssignmentId, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        assertThatThrownBy(() -> writer.write(
                request(2, 1, "B-202", secondAssignmentId, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                key, ACTOR_ID, secondAuthority, SEMESTER_FROM, SEMESTER_TO))
                .isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_assignment_fences", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_recurring_create_replay",
                Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_assignment_fences WHERE assignment_id = ?",
                Long.class, secondAssignmentId)).isZero();
    }

    @Test
    void remoteEarlierEndCannotShrinkRetainedFence() {
        RecurringAssignmentAuthority initial = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        writer.write(request(1, 1, "A-101", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, initial, SEMESTER_FROM, SEMESTER_TO);

        RecurringAssignmentAuthority earlierEnd = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, LocalDate.of(2026, 2, 16));
        assertThatThrownBy(() -> writer.write(
                request(2, 1, "B-202", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, earlierEnd, SEMESTER_FROM, SEMESTER_TO))
                .isInstanceOf(RecurringProtocolConflictException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id = ?",
                LocalDate.class, ASSIGNMENT_ID)).isEqualTo(ASSIGNMENT_END);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_items", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_recurring_create_replay", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void staleRemoteLaterEndDoesNotWidenLocalFence() {
        LocalDate localCap = LocalDate.of(2026, 2, 16);
        RecurringAssignmentAuthority localAuthority = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, localCap);
        writer.write(request(1, 1, "A-101", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, localAuthority, SEMESTER_FROM, SEMESTER_TO);

        RecurringAssignmentAuthority staleRemote = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        RecurringCreateResult second = writer.write(
                request(2, 1, "B-202", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, staleRemote, SEMESTER_FROM, SEMESTER_TO);

        assertThat(second.generatedUntil()).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id = ?",
                LocalDate.class, ASSIGNMENT_ID)).isEqualTo(localCap);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_items", Long.class))
                .isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Long.class))
                .isEqualTo(4L);
    }

    @Test
    void missingAssignmentAuthorityHasZeroLocalEffects() throws Exception {
        when(academicGrpcClient.getAssignmentsByIds(List.of(ASSIGNMENT_ID)))
                .thenReturn(List.of());

        mockMvc.perform(createBuilder(UUID.randomUUID(), request(1, 1, "A-101", ASSIGNMENT_ID,
                        GROUP_ID, SUBJECT_ID, SEMESTER_ID)))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_items", Long.class)).isEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_assignment_fences", Long.class)).isEqualTo(0L);
    }

    @Test
    void concurrentIdenticalWriterCallsReturnOneSeriesWithBoundedWait() throws Exception {
        CreateScheduleItemRequest request = request(1, 1, "A-101", ASSIGNMENT_ID,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        RecurringAssignmentAuthority authority = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        UUID key = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var task = (java.util.concurrent.Callable<RecurringCreateResult>) () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return writer.write(request, key, ACTOR_ID, authority, SEMESTER_FROM, SEMESTER_TO);
            };
            Future<RecurringCreateResult> left = executor.submit(task);
            Future<RecurringCreateResult> right = executor.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            RecurringCreateResult first = left.get(10, TimeUnit.SECONDS);
            RecurringCreateResult second = right.get(10, TimeUnit.SECONDS);
            assertThat(first.scheduleItemId()).isEqualTo(second.scheduleItemId());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schedule_items", Long.class)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM schedule_recurring_create_replay", Long.class)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void capNarrowingCommitIsFreshBeforeBlockedPhysicalInsert() throws Exception {
        RecurringAssignmentAuthority authority = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        RecurringCreateResult seeded = writer.write(
                request(1, 1, "A-101", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, authority, SEMESTER_FROM,
                LocalDate.of(2026, 2, 9));
        UUID closeOperation = UUID.randomUUID();
        var closeAuthority = ru.rutcampustrack.academic.grpc.PreparedAssignmentCloseResponse.newBuilder()
                .setOperationId(closeOperation.toString()).setSourceAssignmentId(ASSIGNMENT_ID).setTargetAssignmentId(502L)
                .setSourceTeacherId(TEACHER_ID).setTargetTeacherId(701L).setGroupId(GROUP_ID).setSubjectId(SUBJECT_ID)
                .setSemesterId(SEMESTER_ID).setLessonType("lecture").setSourceValidFrom(SEMESTER_FROM.toString())
                .setSourceValidUntilExclusive(ASSIGNMENT_END.toString()).setTargetValidUntilExclusive(ASSIGNMENT_END.toString())
                .setEffectiveFrom("2026-02-10").setState("PREPARED").setTargetLifecycleState("PREPARED")
                .setPayloadHash(com.google.protobuf.ByteString.copyFrom(new byte[32])).build();
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(closeOperation, ASSIGNMENT_ID)).thenReturn(closeAuthority);
        var installCap = ru.rutcampustrack.schedule.grpc.InstallAssignmentCloseCapRequest.newBuilder()
                .setOperationId(closeOperation.toString()).setSourceAssignmentId(ASSIGNMENT_ID).setTargetAssignmentId(502L)
                .setEffectiveFrom("2026-02-10").setPayloadHash(closeAuthority.getPayloadHash()).build();
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch fenceLocked = new CountDownLatch(1);
        CountDownLatch insertStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Throwable> cap = executor.submit(() -> captureFailure(() ->
                    transactions.executeWithoutResult(status -> {
                        lockFence(ASSIGNMENT_ID);
                        fenceLocked.countDown();
                        awaitBarrier(insertStarted);
                        replacementService.install(installCap);
                    })));
            Future<Throwable> insert = executor.submit(() -> captureFailure(() -> {
                awaitBarrier(fenceLocked);
                transactions.executeWithoutResult(status -> {
                    insertStarted.countDown();
                    insertOccurrence(seeded.scheduleItemId(), ASSIGNMENT_ID,
                            LocalDate.of(2026, 2, 16));
                });
            }));

            assertThat(cap.get(10, TimeUnit.SECONDS)).isNull();
            assertThat(insert.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT creation_cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id = ?",
                LocalDate.class, ASSIGNMENT_ID)).isEqualTo(LocalDate.of(2026, 2, 10));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id = ?",
                LocalDate.class, ASSIGNMENT_ID)).isEqualTo(ASSIGNMENT_END);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lesson_occurrences WHERE occurrence_date = DATE '2026-02-16'",
                Long.class)).isZero();
    }

    @Test
    void physicalInsertCommitIsFreshBeforeCapNarrowingChecksRetainedRow() throws Exception {
        RecurringAssignmentAuthority authority = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        RecurringCreateResult seeded = writer.write(
                request(1, 1, "A-101", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, authority, SEMESTER_FROM,
                LocalDate.of(2026, 2, 9));
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch insertedAndLocked = new CountDownLatch(1);
        CountDownLatch capUpdateStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Throwable> insert = executor.submit(() -> captureFailure(() ->
                    transactions.executeWithoutResult(status -> {
                        lockFence(ASSIGNMENT_ID);
                        insertOccurrence(seeded.scheduleItemId(), ASSIGNMENT_ID,
                                LocalDate.of(2026, 2, 16));
                        insertedAndLocked.countDown();
                        awaitBarrier(capUpdateStarted);
                    })));
            Future<Throwable> cap = executor.submit(() -> captureFailure(() ->
                    transactions.executeWithoutResult(status -> {
                        awaitBarrier(insertedAndLocked);
                        capUpdateStarted.countDown();
                        jdbcTemplate.update("""
                                UPDATE schedule_assignment_fences
                                   SET cap_until_exclusive = DATE '2026-02-10'
                                 WHERE assignment_id = ?
                                """, ASSIGNMENT_ID);
                    })));

            assertThat(insert.get(10, TimeUnit.SECONDS)).isNull();
            assertThat(cap.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id = ?",
                LocalDate.class, ASSIGNMENT_ID)).isEqualTo(ASSIGNMENT_END);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lesson_occurrences WHERE occurrence_date = DATE '2026-02-16'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void slotConflictRollsBackSecondFenceAndSeries() {
        CreateScheduleItemRequest firstRequest = request(1, 1, "A-101", ASSIGNMENT_ID,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        RecurringAssignmentAuthority firstAuthority = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        writer.write(firstRequest, UUID.randomUUID(), ACTOR_ID, firstAuthority,
                SEMESTER_FROM, SEMESTER_TO);

        long secondAssignmentId = ASSIGNMENT_ID + 1;
        CreateScheduleItemRequest conflictingRequest = request(1, 1, "B-202", secondAssignmentId,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        RecurringAssignmentAuthority secondAuthority = assignmentAuthority(secondAssignmentId, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);

        assertThatThrownBy(() -> writer.write(conflictingRequest, UUID.randomUUID(), ACTOR_ID,
                secondAuthority, SEMESTER_FROM, SEMESTER_TO))
                .isInstanceOf(RecurringProtocolConflictException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_items", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_assignment_fences WHERE assignment_id = ?",
                Long.class, secondAssignmentId)).isEqualTo(0L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_recurring_create_replay WHERE assignment_id = ?",
                Long.class, secondAssignmentId)).isEqualTo(0L);
    }

    @Test
    void retainedCancelledRowsBlockCapNarrowingAndDirectOutOfFenceInsert() {
        CreateScheduleItemRequest request = request(1, 1, "A-101", ASSIGNMENT_ID,
                GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        RecurringAssignmentAuthority authority = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        writer.write(request, UUID.randomUUID(), ACTOR_ID, authority, SEMESTER_FROM, SEMESTER_TO);
        jdbcTemplate.update("""
                UPDATE lessons
                   SET status = CASE WHEN id = (SELECT MIN(id) FROM lessons)
                                     THEN 'cancelled'::lesson_status
                                     ELSE 'transferred'::lesson_status
                                END
                 WHERE assignment_id = ?
                """, ASSIGNMENT_ID);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE schedule_assignment_fences
                   SET cap_until_exclusive = DATE '2026-02-16'
                 WHERE assignment_id = ?
                """, ASSIGNMENT_ID)).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type,
                     generation, revision, created_at)
                VALUES ((SELECT id FROM schedule_items LIMIT 1), DATE '2026-02-23',
                        ?, ?, ?, ?, ?, 'lecture', 1, 1, NOW())
                """, ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID, TEACHER_ID))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT cap_until_exclusive FROM schedule_assignment_fences WHERE assignment_id = ?",
                LocalDate.class, ASSIGNMENT_ID)).isEqualTo(ASSIGNMENT_END);
    }

    @Test
    void directOccurrenceInsertRejectsTemplateFromDifferentAssignment() {
        RecurringAssignmentAuthority firstAuthority = assignmentAuthority(ASSIGNMENT_ID, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        RecurringCreateResult first = writer.write(
                request(1, 1, "A-101", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, firstAuthority, SEMESTER_FROM, SEMESTER_TO);

        long secondAssignmentId = ASSIGNMENT_ID + 1;
        RecurringAssignmentAuthority secondAuthority = assignmentAuthority(secondAssignmentId, GROUP_ID,
                SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END);
        RecurringCreateResult second = writer.write(
                request(2, 1, "B-202", secondAssignmentId, GROUP_ID, SUBJECT_ID, SEMESTER_ID),
                UUID.randomUUID(), ACTOR_ID, secondAuthority, SEMESTER_FROM, SEMESTER_TO);

        assertThat(second.scheduleItemId()).isNotEqualTo(first.scheduleItemId());
        assertThatThrownBy(() -> insertOccurrence(second.scheduleItemId(), ASSIGNMENT_ID,
                SEMESTER_FROM)).isInstanceOf(DataAccessException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lesson_occurrences WHERE schedule_item_id = ? AND assignment_id = ?",
                Long.class, second.scheduleItemId(), ASSIGNMENT_ID)).isZero();
    }

    @Test
    void futureRoomUsesDurableTransferAndReplayKeepsAcceptedSnapshot() throws Exception {
        long item = createLifecycleItem();
        long past = lessonOn("2026-02-02"), started = lessonOn("2026-02-09"), future = lessonOn("2026-02-16");
        insertBinding(future, 901L);
        UUID key = UUID.randomUUID();
        var request = updateRequest("B-202", WeekType.ALL, null);
        JsonNode preview = preview(item, request, false);
        assertThat(preview.get("updatedCount").asLong()).isEqualTo(1);
        assertThat(preview.get("removedCount").asLong()).isZero();
        var apply = updateRequest("B-202", WeekType.ALL, preview.get("revision").asText());
        JsonNode first = update(item, key, apply);
        assertThat(first.path("transfers").get(0).path("state").asText()).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT room_snapshot FROM lessons WHERE id = ?", String.class, past)).isEqualTo("A-101");
        assertThat(jdbcTemplate.queryForObject("SELECT room_snapshot FROM lessons WHERE id = ?", String.class, started)).isEqualTo("A-101");
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, future)).isEqualTo("transferred");
        long current = jdbcTemplate.queryForObject("SELECT current_lesson_id FROM lesson_occurrences WHERE occurrence_date = DATE '2026-02-16'", Long.class);
        assertThat(current).isNotEqualTo(future);
        assertThat(jdbcTemplate.queryForObject("SELECT room_snapshot FROM lessons WHERE id = ?", String.class, current)).isEqualTo("B-202");
        assertThat(jdbcTemplate.queryForObject("SELECT current_lesson_id FROM lesson_homework_bindings WHERE homework_id = 901", Long.class)).isEqualTo(current);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_transfer_binding_batches", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_outbox WHERE event_type = 'lesson.transfer.requested'", Long.class)).isEqualTo(1);
        completeTransfer(first.path("transfers").get(0).path("operationId").asText());
        assertThat(update(item, key, apply)).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Long.class)).isEqualTo(4);
        mockMvc.perform(mutationHeaders(put("/schedule/items/{id}", item), key)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updateRequest("C-303", WeekType.ALL, apply.expectedRevision()))))
                .andExpect(status().isConflict());
    }

    @Test
    void parityRemovalArchivesFutureHomeworkAndRestoresAsNewPhysicalGeneration() throws Exception {
        long item = createLifecycleItem(), future = lessonOn("2026-02-16");
        insertBinding(future, 902L);
        JsonNode removal = preview(item, updateRequest("A-101", WeekType.EVEN, null), false);
        assertThat(removal.get("removedCount").asLong()).isEqualTo(1);
        update(item, UUID.randomUUID(), updateRequest("A-101", WeekType.EVEN, removal.get("revision").asText()));
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM lesson_homework_bindings WHERE homework_id = 902", String.class)).isEqualTo("ARCHIVED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_outbox WHERE event_type = 'homework.binding.archived'", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons WHERE status IN ('planned','active','closed')", Long.class)).isEqualTo(2);
        UUID inventedAuthority = UUID.randomUUID();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT set_config('rutcampustrack.template_restore_operation_id', ?, true)", String.class, inventedAuthority.toString());
            jdbcTemplate.update("""
                    INSERT INTO lesson_lifecycle_entries (occurrence_id, revision, action, lesson_id,
                        target_lesson_id, generation, actor_id, occurred_at, template_operation_id)
                    SELECT occurrence_id, 3, 'RESTORED', id, id, 2, ?, CURRENT_TIMESTAMP, ? FROM lessons WHERE id = ?
                    """, ACTOR_ID, inventedAuthority, future);
        })).isInstanceOf(DataAccessException.class).hasMessageContaining("exact template current-generation authority");
        JsonNode returning = preview(item, updateRequest("A-101", WeekType.ALL, null), false);
        assertThat(returning.get("restoredCount").asLong()).isEqualTo(1);
        update(item, UUID.randomUUID(), updateRequest("A-101", WeekType.ALL, returning.get("revision").asText()));
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, future)).isEqualTo("cancelled");
        long current = jdbcTemplate.queryForObject("SELECT current_lesson_id FROM lesson_occurrences WHERE occurrence_date = DATE '2026-02-16'", Long.class);
        assertThat(current).isNotEqualTo(future);
        assertThat(jdbcTemplate.queryForObject("SELECT generation FROM lessons WHERE id = ?", Long.class, current)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_occurrences", Long.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM lesson_homework_bindings WHERE homework_id = 902", String.class)).isEqualTo("ARCHIVED");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE current_lesson_id = ?", future, current))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void deletingAndReturningSameSeriesNeverBackfillsAbsenceAndRoomIsNotIdentity() throws Exception {
        when(academicGrpcClient.getActiveSemester()).thenReturn(ACTIVE_SEMESTER.toBuilder().setDateTo("2026-03-08").build());
        when(academicGrpcClient.getAssignmentsByIds(List.of(ASSIGNMENT_ID))).thenReturn(List.of(
                assignment(ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, LocalDate.of(2026, 3, 9))));
        long item = createLifecycleItem(), history = lessonOn("2026-02-02"), absent = lessonOn("2026-02-16");
        UUID deletionKey = UUID.randomUUID();
        String revision = preview(item, null, true).get("revision").asText();
        mockMvc.perform(mutationHeaders(delete("/schedule/items/{id}", item), deletionKey).header("If-Match", revision)).andExpect(status().isNoContent());
        mockMvc.perform(mutationHeaders(delete("/schedule/items/{id}", item), deletionKey).header("If-Match", revision)).andExpect(status().isNoContent());
        when(clock.instant()).thenReturn(Instant.parse("2026-02-17T09:00:00Z"));
        UUID restoreKey = UUID.randomUUID();
        CreateScheduleItemRequest restored = request(1, 1, "NEW-ROOM", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID);
        JsonNode first = objectMapper.readTree(postCreate(restoreKey, restored).getResponse().getContentAsString());
        assertThat(first.get("id").asLong()).isEqualTo(item);
        assertThat(objectMapper.readTree(postCreate(restoreKey, restored).getResponse().getContentAsString())).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_items", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, absent)).isEqualTo("cancelled");
        assertThat(jdbcTemplate.queryForObject("SELECT room_snapshot FROM lessons WHERE id = ?", String.class, history)).isEqualTo("A-101");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons WHERE date = DATE '2026-02-16'", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons WHERE date > DATE '2026-02-17' AND status = 'planned' AND room_snapshot = 'NEW-ROOM'", Long.class)).isEqualTo(2);
    }

    @Test
    void stalePreviewAndDeniedAuthorityHaveNoEffects() throws Exception {
        long item = createLifecycleItem();
        String revision = preview(item, updateRequest("B-202", WeekType.ALL, null), false).get("revision").asText();
        jdbcTemplate.update("UPDATE lesson_occurrences SET revision = revision + 1 WHERE occurrence_date = DATE '2026-02-16'");
        mockMvc.perform(mutationHeaders(put("/schedule/items/{id}", item), UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest("B-202", WeekType.ALL, revision)))).andExpect(status().isConflict());
        when(academicGrpcClient.isHeadman(ACTOR_ID, GROUP_ID)).thenReturn(false);
        mockMvc.perform(mutationHeaders(delete("/schedule/items/{id}", item), UUID.randomUUID()).header("If-Match", revision)).andExpect(status().isForbidden());
        when(academicGrpcClient.isHeadman(ACTOR_ID, GROUP_ID)).thenReturn(true);
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID)).thenReturn(SemesterStateResponse.newBuilder().setId(SEMESTER_ID).setArchived(true).build());
        mockMvc.perform(mutationHeaders(delete("/schedule/items/{id}", item), UUID.randomUUID()).header("If-Match", revision)).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_recurring_lifecycle_replay", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT room FROM schedule_items WHERE id = ?", String.class, item)).isEqualTo("A-101");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Long.class)).isEqualTo(3);
    }

    @Test
    void physicalConflictRollsBackTemplateAndEveryEarlierLocalEffect() throws Exception {
        LocalDate mondayDate = configureRealDateFutureSemester();
        WeekType initialParity = mondayDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()) % 2 == 0
                ? WeekType.EVEN : WeekType.ODD; // Canonical enum mapping excludes this future Monday.
        var monday = new CreateScheduleItemRequest(ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID,
                (short) 1, (short) 1, LocalTime.of(8,30), LocalTime.of(10,0), initialParity, "A-101");
        long item = objectMapper.readTree(postCreate(UUID.randomUUID(), monday).getResponse().getContentAsString()).path("id").asLong();
        postCreate(UUID.randomUUID(), request(2, 1, "TUESDAY", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID));
        transferWriter.transfer(lessonOn(mondayDate.plusDays(1).toString()), ACTOR_ID,
                new ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest(mondayDate, 1,
                        null, null, null, "1", UUID.randomUUID()));
        long lessons = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Long.class);
        long occurrences = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_occurrences", Long.class);
        long outbox = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_outbox", Long.class);
        var preview = preview(item, updateRequest("B-202", WeekType.ALL, null), false);
        assertThat(preview.path("createdCount").asLong()).isEqualTo(1);
        mockMvc.perform(mutationHeaders(put("/schedule/items/{id}", item), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        updateRequest("B-202", WeekType.ALL, preview.path("revision").asText())))).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT room FROM schedule_items WHERE id = ?", String.class, item)).isEqualTo("A-101");
        assertThat(jdbcTemplate.queryForObject("SELECT week_type::text FROM schedule_items WHERE id = ?", String.class, item)).isEqualTo(initialParity.name().toLowerCase(java.util.Locale.ROOT));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Long.class)).isEqualTo(lessons);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_occurrences", Long.class)).isEqualTo(occurrences);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_outbox", Long.class)).isEqualTo(outbox);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_recurring_lifecycle_replay", Long.class)).isZero();
    }

    @Test
    void manuallyTransferredPlannedAndCancelledExceptionsArePreserved() throws Exception {
        LocalDate mondayDate = configureRealDateFutureSemester();
        long item = createLifecycleItem(), future = lessonOn(mondayDate.toString());
        var transfer = transferWriter.transfer(future, ACTOR_ID, new ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest(
                mondayDate.plusDays(1), 2, null, null, null, "1", UUID.randomUUID()));
        long moved = Long.parseLong(transfer.targetLessonId());
        JsonNode preview = preview(item, null, true);
        assertThat(preview.path("removedCount").asLong()).isZero();
        mockMvc.perform(mutationHeaders(delete("/schedule/items/{id}", item), UUID.randomUUID()).header("If-Match", preview.path("revision").asText())).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, moved)).isEqualTo("planned");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Long.class)).isEqualTo(3);
    }

    @Test
    void templateRoomUpdateAdmitsTodaysNotStartedSlotUsingExactAuthorityTime() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-02-09T04:00:00Z"));
        long item = createLifecycleItem(), today = lessonOn("2026-02-09"), past = lessonOn("2026-02-02");
        JsonNode preview = preview(item, updateRequest("TODAY-ROOM", WeekType.ALL, null), false);
        assertThat(preview.path("updatedCount").asLong()).isEqualTo(2);
        update(item, UUID.randomUUID(), updateRequest("TODAY-ROOM", WeekType.ALL, preview.path("revision").asText()));
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, today)).isEqualTo("transferred");
        assertThat(jdbcTemplate.queryForObject("SELECT room_snapshot FROM lessons WHERE date = DATE '2026-02-09' AND status = 'planned'", String.class)).isEqualTo("TODAY-ROOM");
        assertThat(jdbcTemplate.queryForObject("SELECT room_snapshot FROM lessons WHERE id = ?", String.class, past)).isEqualTo("A-101");
    }

    @Test
    void concurrentDifferentCreateKeysCannotEditAlreadyResumedSeries() throws Exception {
        long item = createLifecycleItem();
        String revision = preview(item, null, true).path("revision").asText();
        mockMvc.perform(mutationHeaders(delete("/schedule/items/{id}", item), UUID.randomUUID()).header("If-Match", revision)).andExpect(status().isNoContent());
        var prepared = archiveFence.prepareBusinessWrite(SEMESTER_ID);
        var authority = Map.of(ASSIGNMENT_ID, assignmentAuthority(ASSIGNMENT_ID,GROUP_ID,SUBJECT_ID,SEMESTER_ID,SEMESTER_FROM,ASSIGNMENT_END));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> { awaitBarrier(start); return captureFailure(() -> lifecycle.reactivate(item,
                    request(1,1,"ROOM-ONE",ASSIGNMENT_ID,GROUP_ID,SUBJECT_ID,SEMESTER_ID),UUID.randomUUID(),ACTOR_ID,authority,SEMESTER_FROM,SEMESTER_TO,prepared)); });
            var two = executor.submit(() -> { awaitBarrier(start); return captureFailure(() -> lifecycle.reactivate(item,
                    request(1,1,"ROOM-TWO",ASSIGNMENT_ID,GROUP_ID,SUBJECT_ID,SEMESTER_ID),UUID.randomUUID(),ACTOR_ID,authority,SEMESTER_FROM,SEMESTER_TO,prepared)); });
            start.countDown();
            var results = java.util.Arrays.asList(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS));
            assertThat(results.stream().filter(java.util.Objects::isNull).count()).isEqualTo(1);
            assertThat(results.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow()).isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
        } finally { executor.shutdownNow(); }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_recurring_lifecycle_replay WHERE action = 'REACTIVATE'",Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons",Long.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_occurrences",Long.class)).isEqualTo(3);
    }

    @Test
    void concurrentLifecycleRequestsAcceptExactlyOneObservedRevision() throws Exception {
        long item = createLifecycleItem();
        String revision = preview(item, null, true).get("revision").asText();
        var prepared = archiveFence.prepareBusinessWrite(SEMESTER_ID);
        Map<Long, RecurringAssignmentAuthority> authority = Map.of(ASSIGNMENT_ID, assignmentAuthority(ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, ASSIGNMENT_END));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> { awaitBarrier(start); return captureFailure(() -> lifecycle.mutate(item, null, true, revision, UUID.randomUUID(), ACTOR_ID, authority, SEMESTER_FROM, SEMESTER_TO, prepared)); });
            var two = executor.submit(() -> { awaitBarrier(start); return captureFailure(() -> lifecycle.mutate(item, null, true, revision, UUID.randomUUID(), ACTOR_ID, authority, SEMESTER_FROM, SEMESTER_TO, prepared)); });
            start.countDown();
            List<Throwable> results = java.util.Arrays.asList(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(java.util.Objects::isNull).count()).isEqualTo(1);
            assertThat(results.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow()).isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
        } finally { executor.shutdownNow(); }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_recurring_lifecycle_replay", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_lifecycle_entries WHERE action = 'CANCELLED'", Long.class)).isEqualTo(1);
    }

    @Test
    void manuallyCancelledFutureLessonIsNeverResumedByTemplateReturn() throws Exception {
        long item = createLifecycleItem(), future = lessonOn("2026-02-16");
        lessonLifecycle.cancel(future, "Ручное исключение", ACTOR_ID);
        JsonNode preview = preview(item, null, true);
        assertThat(preview.path("removedCount").asLong()).isZero();
        mockMvc.perform(mutationHeaders(delete("/schedule/items/{id}", item), UUID.randomUUID())
                .header("If-Match", preview.path("revision").asText())).andExpect(status().isNoContent());
        postCreate(UUID.randomUUID(), request(1,1,"A-101",ASSIGNMENT_ID,GROUP_ID,SUBJECT_ID,SEMESTER_ID));
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, future)).isEqualTo("cancelled");
        assertThat(jdbcTemplate.queryForObject("SELECT current_lesson_id FROM lesson_occurrences WHERE occurrence_date = DATE '2026-02-16'", Long.class)).isEqualTo(future);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lessons", Long.class)).isEqualTo(3);
    }

    @Test
    void templateReturnAfterCommittedTeacherReplacementUsesExistingCloneAndEffectiveTeacher() throws Exception {
        long sourceItem = createLifecycleItem(), oldFuture = lessonOn("2026-02-16"), history = lessonOn("2026-02-02");
        String revision = preview(sourceItem, null, true).path("revision").asText();
        mockMvc.perform(mutationHeaders(delete("/schedule/items/{id}", sourceItem), UUID.randomUUID()).header("If-Match", revision)).andExpect(status().isNoContent());
        UUID operation = UUID.randomUUID();
        var authority = ru.rutcampustrack.academic.grpc.PreparedAssignmentCloseResponse.newBuilder()
                .setOperationId(operation.toString()).setSourceAssignmentId(ASSIGNMENT_ID).setTargetAssignmentId(502L)
                .setSourceTeacherId(TEACHER_ID).setTargetTeacherId(701L).setGroupId(GROUP_ID).setSubjectId(SUBJECT_ID)
                .setSemesterId(SEMESTER_ID).setLessonType("lecture").setSourceValidFrom(SEMESTER_FROM.toString())
                .setSourceValidUntilExclusive(ASSIGNMENT_END.toString()).setTargetValidUntilExclusive(ASSIGNMENT_END.toString())
                .setEffectiveFrom("2026-02-12").setState("PREPARED").setTargetLifecycleState("PREPARED")
                .setPayloadHash(com.google.protobuf.ByteString.copyFrom(new byte[32])).build();
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(operation, ASSIGNMENT_ID)).thenReturn(authority);
        replacementService.install(ru.rutcampustrack.schedule.grpc.InstallAssignmentCloseCapRequest.newBuilder()
                .setOperationId(operation.toString()).setSourceAssignmentId(ASSIGNMENT_ID).setTargetAssignmentId(502L)
                .setEffectiveFrom("2026-02-12").setPayloadHash(authority.getPayloadHash()).build());
        when(academicGrpcClient.getPreparedAssignmentCloseOperation(operation, ASSIGNMENT_ID)).thenReturn(authority.toBuilder()
                .setState("APPLIED").setTargetLifecycleState("ACTIVE").setSourceValidUntilExclusive("2026-02-12").build());
        replacementService.commit(ru.rutcampustrack.schedule.grpc.CommitAssignmentCloseRequest.newBuilder()
                .setOperationId(operation.toString()).setPayloadHash(authority.getPayloadHash()).build());
        long clone = jdbcTemplate.queryForObject("SELECT target_schedule_item_id FROM schedule_assignment_replacement_templates WHERE source_schedule_item_id = ?", Long.class, sourceItem);
        var source = assignment(ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID, SEMESTER_FROM, LocalDate.of(2026,2,12));
        var target = assignment(502L, GROUP_ID, SUBJECT_ID, SEMESTER_ID, LocalDate.of(2026,2,12), ASSIGNMENT_END).toBuilder().setTeacherId(701L).build();
        when(academicGrpcClient.getAssignmentsByIds(List.of(502L))).thenReturn(List.of(target));
        when(academicGrpcClient.getAssignmentsByIds(List.of(ASSIGNMENT_ID, 502L))).thenReturn(List.of(source,target));
        when(clock.instant()).thenReturn(Instant.parse("2026-02-13T09:00:00Z"));
        JsonNode returning = objectMapper.readTree(postCreate(UUID.randomUUID(), request(1,1,"A-101",502L,GROUP_ID,SUBJECT_ID,SEMESTER_ID))
                .getResponse().getContentAsString());
        assertThat(returning.path("id").asLong()).isEqualTo(clone);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule_items", Long.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_occurrences", Long.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT status::text FROM lessons WHERE id = ?", String.class, oldFuture)).isEqualTo("cancelled");
        assertThat(jdbcTemplate.queryForObject("SELECT assigned_teacher_id FROM lessons WHERE id = ?", Long.class, history)).isEqualTo(TEACHER_ID);
        assertThat(jdbcTemplate.queryForObject("SELECT assigned_teacher_id FROM lessons WHERE date = DATE '2026-02-16' AND status = 'planned'", Long.class)).isEqualTo(701L);
        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM schedule_items WHERE id = ?", Boolean.class, sourceItem)).isFalse();
    }

    private LocalDate configureRealDateFutureSemester() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        LocalDate monday = today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        LocalDate from = monday.minusDays(7), to = monday.plusDays(6);
        when(clock.instant()).thenReturn(today.atTime(12,0).atZone(ZoneId.of("Europe/Moscow")).toInstant());
        when(academicGrpcClient.getActiveSemester()).thenReturn(ACTIVE_SEMESTER.toBuilder().setDateFrom(from.toString()).setDateTo(to.toString()).build());
        when(academicGrpcClient.getAssignmentsByIds(List.of(ASSIGNMENT_ID))).thenReturn(List.of(
                assignment(ASSIGNMENT_ID,GROUP_ID,SUBJECT_ID,SEMESTER_ID,from,to.plusDays(1))));
        return monday;
    }

    private long createLifecycleItem() throws Exception {
        return objectMapper.readTree(postCreate(UUID.randomUUID(), request(1, 1, "A-101", ASSIGNMENT_ID, GROUP_ID, SUBJECT_ID, SEMESTER_ID))
                .getResponse().getContentAsString()).get("id").asLong();
    }
    private long lessonOn(String date) { return jdbcTemplate.queryForObject("SELECT id FROM lessons WHERE date = ?::date", Long.class, date); }
    private void insertBinding(long lesson, long homework) {
        jdbcTemplate.update("""
                INSERT INTO lesson_homework_bindings (occurrence_id, current_lesson_id, homework_id, actor_id,
                    request_key, payload_hash, state, revision)
                SELECT occurrence_id, id, ?, ?, ?, decode(repeat('00',32),'hex'), 'ACTIVE', 1 FROM lessons WHERE id = ?
                """, homework, ACTOR_ID, UUID.randomUUID(), lesson);
    }
    private void completeTransfer(String id) {
        var operation = jdbcTemplate.queryForMap("SELECT * FROM lesson_transfer_operations WHERE operation_id = ?::uuid", id);
        String hash = HexFormat.of().formatHex((byte[]) operation.get("operation_hash"));
        transferWriter.acknowledge(id, "ATTENDANCE", -1, "APPLIED", null, hash, ((Number) operation.get("source_lesson_id")).longValue(), ((Number) operation.get("target_lesson_id")).longValue(), false);
        transferWriter.acknowledge(id, "ACADEMIC", 0, "APPLIED", null, hash, ((Number) operation.get("source_lesson_id")).longValue(), ((Number) operation.get("target_lesson_id")).longValue(), false);
    }
    private ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest updateRequest(String room, WeekType parity, String revision) {
        return new ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest(SUBJECT_ID, (short) 1, (short) 1,
                LocalTime.of(8,30), LocalTime.of(10,0), parity, room, revision);
    }
    private JsonNode preview(long item, ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest request, boolean delete) throws Exception {
        var builder = mutationHeaders(post("/schedule/items/{id}/lifecycle-preview", item), UUID.randomUUID()).param("delete", String.valueOf(delete));
        if (request != null) builder.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request));
        return objectMapper.readTree(mockMvc.perform(builder).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode update(long item, UUID key, ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(mutationHeaders(put("/schedule/items/{id}", item), key).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private MockHttpServletRequestBuilder mutationHeaders(MockHttpServletRequestBuilder builder, UUID key) {
        return builder.header("X-User-Id", ACTOR_ID).header("X-User-Role", "STUDENT").header("X-Group-Id", GROUP_ID)
                .header("X-Is-Headman", "true").header("Idempotency-Key", key.toString());
    }

    private MvcResult postCreate(UUID key, CreateScheduleItemRequest request) throws Exception {
        return mockMvc.perform(createBuilder(key, request))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MockHttpServletRequestBuilder createBuilder(UUID key, CreateScheduleItemRequest request)
            throws Exception {
        return post("/schedule/items")
                .header("X-User-Id", ACTOR_ID)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP_ID)
                .header("X-Is-Headman", "true")
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }

    private void lockFence(long assignmentId) {
        jdbcTemplate.queryForList("""
                SELECT assignment_id
                  FROM schedule_assignment_fences
                 WHERE assignment_id = ?
                 FOR UPDATE
                """, assignmentId);
    }

    private void insertOccurrence(long scheduleItemId, long assignmentId, LocalDate date) {
        jdbcTemplate.update("""
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type,
                     generation, revision, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'lecture', 1, 1, NOW())
                """, scheduleItemId, date, assignmentId, GROUP_ID, SUBJECT_ID,
                SEMESTER_ID, TEACHER_ID);
    }

    private static Throwable captureFailure(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void awaitBarrier(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("bounded coordination barrier timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("bounded coordination barrier interrupted", interrupted);
        }
    }

    private static CreateScheduleItemRequest request(int day, int lessonNumber, String room,
                                                      long assignmentId, long groupId,
                                                      long subjectId, long semesterId) {
        return new CreateScheduleItemRequest(assignmentId, groupId, subjectId, semesterId,
                (short) day, (short) lessonNumber, LocalTime.of(8, 30),
                LocalTime.of(10, 0), WeekType.ALL, room);
    }

    private static AssignmentInfo assignment(long assignmentId, long groupId, long subjectId,
                                             long semesterId, LocalDate validFrom,
                                             LocalDate validUntilExclusive) {
        return AssignmentInfo.newBuilder()
                .setId(assignmentId)
                .setTeacherId(TEACHER_ID)
                .setSubjectId(subjectId)
                .setGroupId(groupId)
                .setSemesterId(semesterId)
                .setLessonType("lecture")
                .setValidFrom(validFrom.toString())
                .setValidUntilExclusive(validUntilExclusive.toString())
                .build();
    }

    private static RecurringAssignmentAuthority assignmentAuthority(long assignmentId, long groupId,
                                                                    long subjectId, long semesterId,
                                                                    LocalDate validFrom,
                                                                    LocalDate validUntilExclusive) {
        return new RecurringAssignmentAuthority(assignmentId, TEACHER_ID, subjectId, groupId,
                semesterId, "lecture", validFrom, validUntilExclusive);
    }
}
