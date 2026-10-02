package ru.rutcampustrack.schedule.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.academic.grpc.SemesterTransition;
import ru.rutcampustrack.schedule.exception.AccessDeniedException;
import ru.rutcampustrack.schedule.homework.HomeworkBindingService;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * One real PostgreSQL protocol cycle for the V17 homework binding contract.
 *
 * <p>The claims installed in the gRPC context model the output of the signed
 * metadata interceptor; the test deliberately does not install an actor
 * header. This keeps the check focused on the durable Schedule transaction
 * and its group authority boundary.</p>
 */
class HomeworkBindingServiceIT extends AbstractScheduleIntegrationTest {

    private static final AtomicLong FIXTURE_SEQUENCE =
            new AtomicLong(System.currentTimeMillis() * 100L);
    private static final long ACTOR_ID = 880001L;
    private static final long AUTHORIZED_GROUP_ID = 881001L;
    private static final long FOREIGN_GROUP_ID = 881002L;
    private static final long SUBJECT_ID = 882001L;
    private static final long SEMESTER_ID = 883001L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HomeworkBindingService homeworkBindingService;

    @Autowired
    private ScheduleSemesterArchiveBarrierService archiveBarrierService;

    @Autowired
    private ScheduleSemesterDeletionSnapshotReader deletionSnapshots;

    @MockitoBean
    private AcademicGrpcClient academicGrpcClient;

    @Autowired
    private ru.rutcampustrack.schedule.homework.HomeworkPlacementService placement;
    @Autowired
    private ru.rutcampustrack.schedule.lesson.LessonTransferWriter transfers;
    @Autowired
    private ScheduleSemesterArchiveWriteFence writeFence;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean
    private Clock clock;
    private final AtomicReference<Instant> instant = new AtomicReference<>();

    @BeforeEach
    void fixedClock() {
        instant.set(Instant.parse("2026-10-02T12:00:00Z"));
        when(clock.withZone(any(ZoneId.class))).thenAnswer(invocation -> Clock.fixed(instant.get(), invocation.getArgument(0)));
    }

    private final List<Long> scheduleItemIds = new ArrayList<>();
    private final List<Long> occurrenceIds = new ArrayList<>();
    private final List<Long> lessonIds = new ArrayList<>();
    private final List<Long> archiveBarrierSemesterIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        archiveBarrierSemesterIds.forEach(id -> jdbcTemplate.update(
                "DELETE FROM schedule_semester_archive_barriers WHERE semester_id = ?", id));
        // V17 deliberately retains binding and physical lesson history. A
        // DELETE is rejected by the database trigger, so archive the rows and
        // leave their immutable FK chain intact for the next test process.
        occurrenceIds.forEach(id -> jdbcTemplate.update("""
                UPDATE lesson_homework_bindings
                   SET state = 'ARCHIVED', revision = revision + 1, updated_at = NOW()
                 WHERE occurrence_id = ? AND state <> 'ARCHIVED'
                """, id));
        drainOutbox();
        scheduleItemIds.clear();
        occurrenceIds.clear();
        lessonIds.clear();
        archiveBarrierSemesterIds.clear();
    }

    @Test
    void reserveConfirmReplay_isDurableAndRejectsForeignGroup() {
        Fixture authorized = insertFixture(AUTHORIZED_GROUP_ID);
        Fixture foreign = insertFixture(FOREIGN_GROUP_ID);
        AtomicReference<SemesterStateResponse> authority = new AtomicReference<>(
                SemesterStateResponse.newBuilder().setId(SEMESTER_ID).setActive(true).build());
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                .thenAnswer(invocation -> authority.get());
        InternalJwtClaims signedHeadman = new InternalJwtClaims(
                ACTOR_ID, UUID.randomUUID(), 1L, 1L, "HEADMAN", "ACTIVE",
                AUTHORIZED_GROUP_ID, true, false);
        byte[] payloadHash = new byte[32];
        payloadHash[0] = 7;
        UUID requestKey = UUID.randomUUID();

        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, signedHeadman).run(() -> {
            ReserveHomeworkBindingRequest reserve = ReserveHomeworkBindingRequest.newBuilder()
                    .setOccurrenceId(authorized.occurrenceId())
                    .setRequestKey(requestKey.toString())
                    .setExpectedRevision(1L)
                    .setPayloadHash(ByteString.copyFrom(payloadHash))
                    .build();

            HomeworkBindingResponse pending = homeworkBindingService.reserve(reserve);
            assertThat(pending.getState())
                    .isEqualTo(HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING);
            assertThat(pending.getBindingId()).isPositive();
            assertThat(pending.getCurrentLesson().getLessonId()).isEqualTo(authorized.lessonId());
            assertThat(pending.getGroupId()).isEqualTo(AUTHORIZED_GROUP_ID);
            assertThat(pending.getSubjectId()).isEqualTo(SUBJECT_ID);
            assertThat(pending.getSemesterId()).isEqualTo(SEMESTER_ID);

            String persistedState = jdbcTemplate.queryForObject(
                    "SELECT state FROM lesson_homework_bindings WHERE binding_id = ?",
                    String.class, pending.getBindingId());
            assertThat(persistedState).isEqualTo("PENDING");

            UUID archiveOperation = UUID.randomUUID();
            long archiveVersion = 90L;
            jdbcTemplate.update("""
                    INSERT INTO schedule_semester_archive_barriers
                        (semester_id, operation_id, state_version, participant_state)
                    VALUES (?, ?, ?, 'PENDING')
                    """, SEMESTER_ID, archiveOperation, archiveVersion);
            archiveBarrierSemesterIds.add(SEMESTER_ID);
            authority.set(SemesterStateResponse.newBuilder().setId(SEMESTER_ID)
                    .setStateVersion(archiveVersion).setTransition(SemesterTransition.ARCHIVING)
                    .setWriteBlocked(true).build());
            assertThat(authority.get().getTransitionOperationId()).isEmpty();
            assertThat(homeworkBindingService.reserve(reserve).getBindingId()).isEqualTo(pending.getBindingId());
            ReserveHomeworkBindingRequest newWrite = reserve.toBuilder()
                    .setRequestKey(UUID.randomUUID().toString()).build();
            assertThatThrownBy(() -> homeworkBindingService.reserve(newWrite))
                    .isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);

            ConfirmHomeworkBindingRequest confirm = ConfirmHomeworkBindingRequest.newBuilder()
                    .setBindingId(pending.getBindingId())
                    .setHomeworkId(990001L)
                    .setRequestKey(requestKey.toString())
                    .build();
            HomeworkBindingResponse active = homeworkBindingService.confirm(confirm);
            assertThat(active.getState())
                    .isEqualTo(HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE);
            assertThat(active.getHomeworkId()).isEqualTo(990001L);

            HomeworkBindingResponse confirmReplay = homeworkBindingService.confirm(confirm);
            assertThat(confirmReplay.getState())
                    .isEqualTo(HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE);
            assertThat(confirmReplay.getBindingId()).isEqualTo(pending.getBindingId());
            assertThat(confirmReplay.getHomeworkId()).isEqualTo(990001L);

            jdbcTemplate.update("DELETE FROM schedule_semester_archive_barriers WHERE semester_id = ?", SEMESTER_ID);
            authority.set(SemesterStateResponse.newBuilder().setId(SEMESTER_ID).setActive(true).build());
            HomeworkBindingResponse reserveReplay = homeworkBindingService.reserve(reserve);
            assertThat(reserveReplay.getState())
                    .isEqualTo(HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE);
            assertThat(reserveReplay.getBindingId()).isEqualTo(pending.getBindingId());

            ArchiveHomeworkBindingRequest archive = ArchiveHomeworkBindingRequest.newBuilder()
                    .setBindingId(pending.getBindingId())
                    .setHomeworkId(990001L)
                    .setRequestKey(requestKey.toString())
                    .build();
            InternalJwtClaims signedAssistant = new InternalJwtClaims(
                    ACTOR_ID + 1, UUID.randomUUID(), 1L, 1L, "STUDENT", "ACTIVE",
                    AUTHORIZED_GROUP_ID, false, false);
            AtomicReference<HomeworkBindingResponse> archivedRef = new AtomicReference<>();
            Context.current()
                    .withValue(HomeworkBindingActorContext.CLAIMS, signedAssistant)
                    .run(() -> archivedRef.set(homeworkBindingService.archive(archive)));
            HomeworkBindingResponse archived = archivedRef.get();
            assertThat(archived.getState())
                    .isEqualTo(HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED);
            AtomicReference<HomeworkBindingResponse> replayRef = new AtomicReference<>();
            Context.current()
                    .withValue(HomeworkBindingActorContext.CLAIMS, signedAssistant)
                    .run(() -> replayRef.set(homeworkBindingService.archive(archive)));
            assertThat(replayRef.get().getState())
                    .isEqualTo(HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED);

            ReserveHomeworkBindingRequest foreignRequest = ReserveHomeworkBindingRequest.newBuilder()
                    .setOccurrenceId(foreign.occurrenceId())
                    .setRequestKey(UUID.randomUUID().toString())
                    .setExpectedRevision(1L)
                    .setPayloadHash(ByteString.copyFrom(new byte[32]))
                    .build();
            assertThatThrownBy(() -> homeworkBindingService.reserve(foreignRequest))
                    .isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void archiveCancellationCommitsBindingLedgerAndOutbox_andReplayReturnsOriginalEvent() {
        long semesterId = FIXTURE_SEQUENCE.incrementAndGet();
        Fixture fixture = insertFixture(AUTHORIZED_GROUP_ID, semesterId);
        long barrierVersion = 91L;
        UUID operationId = UUID.randomUUID();
        UUID requestKey = UUID.randomUUID();
        byte[] payloadHash = new byte[32];
        payloadHash[0] = 19;
        InternalJwtClaims signedHeadman = new InternalJwtClaims(
                ACTOR_ID, UUID.randomUUID(), 1L, 1L, "HEADMAN", "ACTIVE",
                AUTHORIZED_GROUP_ID, true, false);
        ReserveHomeworkBindingRequest reserve = ReserveHomeworkBindingRequest.newBuilder()
                .setOccurrenceId(fixture.occurrenceId())
                .setRequestKey(requestKey.toString())
                .setExpectedRevision(1L)
                .setPayloadHash(ByteString.copyFrom(payloadHash))
                .build();
        AtomicReference<HomeworkBindingResponse> reservation = new AtomicReference<>();
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, signedHeadman)
                .run(() -> reservation.set(homeworkBindingService.reserve(reserve)));
        HomeworkBindingResponse pending = reservation.get();
        assertThat(pending.getState())
                .isEqualTo(HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING);

        jdbcTemplate.update("""
                INSERT INTO schedule_semester_archive_barriers
                    (semester_id, operation_id, state_version, participant_state)
                VALUES (?, ?, ?, 'PENDING')
                """, semesterId, operationId, barrierVersion);
        archiveBarrierSemesterIds.add(semesterId);
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semesterId))
                .thenReturn(SemesterStateResponse.newBuilder()
                        .setId(semesterId)
                        .setStateVersion(barrierVersion)
                        .setTransition(SemesterTransition.ARCHIVING)
                        .build());

        SetSemesterArchiveBarrierRequest cancel = SetSemesterArchiveBarrierRequest.newBuilder()
                .setOperationId(operationId.toString())
                .setSemesterId(semesterId)
                .setStateVersion(barrierVersion)
                .setCommand(SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_RECONCILE_HOMEWORK_BINDING)
                .setBindingResolution(
                        SemesterArchiveHomeworkBindingResolution.SEMESTER_ARCHIVE_HOMEWORK_BINDING_CANCEL_UNPUBLISHED)
                .setBinding(SemesterArchiveHomeworkBindingIdentity.newBuilder()
                        .setBindingId(pending.getBindingId())
                        .setOccurrenceId(pending.getOccurrenceId())
                        .setActorId(ACTOR_ID)
                        .setRequestKey(requestKey.toString())
                        .setPayloadHash(ByteString.copyFrom(payloadHash))
                        .setRevision(pending.getRevision()))
                .build();

        SetSemesterArchiveBarrierResponse first = archiveBarrierService.set(cancel);
        assertThat(first.getTerminalEventId()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT state FROM lesson_homework_bindings WHERE binding_id = ?
                """, String.class, pending.getBindingId())).isEqualTo("ARCHIVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM schedule_semester_archive_effect_ledger
                 WHERE event_id = ? AND target = 'ACADEMIC'
                   AND event_type = 'homework.binding.archived' AND semester_id = ? AND state = 'PENDING'
                """, Long.class, UUID.fromString(first.getTerminalEventId()), semesterId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM schedule_outbox
                 WHERE event_type = 'homework.binding.archived'
                   AND payload ->> 'event_id' = ?
                """, Long.class, first.getTerminalEventId())).isEqualTo(1L);

        SetSemesterArchiveBarrierResponse replay = archiveBarrierService.set(cancel);
        assertThat(replay.getTerminalEventId()).isEqualTo(first.getTerminalEventId());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM schedule_semester_archive_effect_ledger
                 WHERE event_id = ? AND target = 'ACADEMIC'
                   AND event_type = 'homework.binding.archived'
                """, Long.class, UUID.fromString(first.getTerminalEventId()))).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM schedule_outbox
                 WHERE event_type = 'homework.binding.archived'
                   AND payload ->> 'event_id' = ?
                """, Long.class, first.getTerminalEventId())).isEqualTo(1L);
    }

    @Test
    void deleteCommitIsAtomicReplayableAndLeavesReplayTombstone() {
        long semesterId = FIXTURE_SEQUENCE.incrementAndGet();
        Fixture fixture = insertFixture(AUTHORIZED_GROUP_ID, semesterId);
        long stateVersion = 92L;
        UUID operationId = UUID.randomUUID();
        UUID bindingRequestKey = UUID.randomUUID();
        byte[] payloadHash = new byte[32];
        payloadHash[0] = 23;
        jdbcTemplate.update("""
                INSERT INTO lesson_homework_bindings
                    (occurrence_id, current_lesson_id, actor_id, request_key, payload_hash,
                     state, revision)
                VALUES (?, ?, ?, ?, ?, 'ARCHIVED', 2)
                """, fixture.occurrenceId(), fixture.lessonId(), ACTOR_ID,
                bindingRequestKey, payloadHash);

        archiveBarrierSemesterIds.add(semesterId);
        ScheduleSemesterDeletionSnapshotReader.Snapshot before = deletionSnapshots.read(semesterId);
        AtomicReference<SemesterStateResponse> authority = new AtomicReference<>(
                deletionAuthority(semesterId, stateVersion, operationId, "PREPARING").toBuilder().setArchived(true).build());
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semesterId))
                .thenAnswer(invocation -> authority.get());

        SetSemesterArchiveBarrierResponse prepared = archiveBarrierService.set(deleteBarrierRequest(
                operationId, semesterId, stateVersion,
                SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_PREPARE_DELETE,
                before.participantDigest()));
        assertThat(prepared.getState())
                .isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_READY);
        assertThat(prepared.getParticipantDigest()).isEqualTo(before.participantDigest());
        assertThat(prepared.getScheduleTemplatesCount()).isEqualTo(1L);
        assertThat(prepared.getLessonsCount()).isEqualTo(1L);

        SetSemesterArchiveBarrierResponse sealed = archiveBarrierService.set(deleteBarrierRequest(
                operationId, semesterId, stateVersion,
                SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_SEAL_DELETE,
                before.participantDigest()));
        assertThat(sealed.getState())
                .isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_READY);

        authority.set(deletionAuthority(semesterId, stateVersion, operationId, "DELETING").toBuilder().setArchived(true).build());
        SetSemesterArchiveBarrierRequest commit = deleteBarrierRequest(operationId, semesterId, stateVersion,
                SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_COMMIT_DELETE,
                before.participantDigest());
        SetSemesterArchiveBarrierResponse deleted = archiveBarrierService.set(commit);
        assertThat(deleted.getState())
                .isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_DELETED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM schedule_items WHERE semester_id = ?", Long.class, semesterId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lesson_occurrences WHERE semester_id = ?", Long.class, semesterId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lessons WHERE semester_id = ?", Long.class, semesterId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM lesson_homework_bindings
                 WHERE request_key = ? AND state = 'ARCHIVED' AND homework_id IS NULL
                """, Long.class, bindingRequestKey)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT participant_state FROM schedule_semester_archive_barriers
                 WHERE semester_id = ?
                """, String.class, semesterId)).isEqualTo("DELETED");

        SetSemesterArchiveBarrierResponse replay = archiveBarrierService.set(commit);
        assertThat(replay.getState()).isEqualTo(deleted.getState());
        assertThat(replay.getParticipantDigest()).isEqualTo(deleted.getParticipantDigest());
        assertThat(replay.getLessonsCount()).isEqualTo(deleted.getLessonsCount());

        long lateAssignmentId = FIXTURE_SEQUENCE.incrementAndGet();
        LocalDate from = LocalDate.of(2091, 1, 1);
        jdbcTemplate.update("""
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, 884001, 'lecture', ?, ?, ?)
                """, lateAssignmentId, AUTHORIZED_GROUP_ID, SUBJECT_ID, semesterId,
                from, from.plusDays(30), from.plusDays(30));
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type)
                VALUES (?, ?, ?, ?, 4, 8, '14:00'::time, '15:00'::time, 'all')
                """, lateAssignmentId, AUTHORIZED_GROUP_ID, SUBJECT_ID, semesterId))
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> {
                    Throwable cause = error;
                    while (cause.getCause() != null) cause = cause.getCause();
                    assertThat(cause).isInstanceOf(java.sql.SQLException.class);
                    assertThat(((java.sql.SQLException) cause).getSQLState()).isEqualTo("55000");
                    assertThat(cause.getMessage()).contains("Schedule semester " + semesterId
                            + " is fenced by final deletion");
                });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM schedule_items WHERE semester_id = ?", Long.class, semesterId)).isZero();
    }

    @Test
    void deleteReleaseBeforePreparePersistsExactReplayReceiptAndKeepsDomain() {
        long semesterId = FIXTURE_SEQUENCE.incrementAndGet();
        Fixture fixture = insertFixture(AUTHORIZED_GROUP_ID, semesterId);
        long version = 93L;
        UUID operationId = UUID.randomUUID();
        archiveBarrierSemesterIds.add(semesterId);
        var snapshot = deletionSnapshots.read(semesterId);
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semesterId)).thenReturn(
                deletionAuthority(semesterId, version, operationId, "RELEASING").toBuilder()
                        .setArchived(true).build());
        var release = deleteBarrierRequest(operationId, semesterId, version,
                SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_RELEASE_DELETE,
                snapshot.participantDigest());
        var receipt = archiveBarrierService.set(release);
        assertThat(receipt.getState()).isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_RELEASED);
        assertThat(receipt.getParticipantDigest()).isEqualTo(snapshot.participantDigest());
        assertThat(receipt.getLessonsCount()).isEqualTo(1L);
        assertThat(archiveBarrierService.set(release)).isEqualTo(receipt);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lessons WHERE id = ?",
                Long.class, fixture.lessonId())).isEqualTo(1L);
        assertThatThrownBy(() -> archiveBarrierService.set(release.toBuilder()
                .setExpectedParticipantDigest("a".repeat(64)).build()))
                .isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
    }

    @Test
    void datePlacementReceiptGatesTransferAndKeepsCreateIntentAcrossManualMove() {
        long semester = FIXTURE_SEQUENCE.incrementAndGet();
        Fixture target = insertFixture(AUTHORIZED_GROUP_ID, semester);
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semester)).thenReturn(
                SemesterStateResponse.newBuilder().setId(semester).setActive(true).build());
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> {
            ReserveHomeworkBindingRequest create = dateCreate(semester, "2090-01-01");
            HomeworkBindingResponse active = createDate(create);
            String targetDate = jdbcTemplate.queryForObject("SELECT date::TEXT FROM lessons WHERE id = ?", String.class, target.lessonId());
            HomeworkEditIdentity identity = editIdentity(active, semester);
            MoveHomeworkBindingRequest move = MoveHomeworkBindingRequest.newBuilder().setIdentity(identity)
                    .setBindingMode("LESSON").setDate(targetDate).setLessonNumber(1)
                    .setTargetOccurrenceId(target.occurrenceId()).setExpectedLessonRevision(1)
                    .setExpectedBindingRevision(active.getRevision()).build();
            HomeworkEditReceipt receipt = placement.move(move);
            assertThat(receipt.getState()).isEqualTo("APPLIED_AWAITING_ACK");
            assertThat(receipt.getAcceptedBinding().getOccurrenceId()).isEqualTo(target.occurrenceId());
            assertThat(placement.move(move)).isEqualTo(receipt);
            assertThat(placement.continuation(identity)).isEqualTo(receipt);
            assertThat(homeworkBindingService.reserve(create).getBindingId()).isEqualTo(active.getBindingId());
            LocalDate next = LocalDate.parse(targetDate).plusDays(1);
            if (next.getDayOfWeek().getValue() == 7) next = next.plusDays(1);
            var transfer = new ru.rutcampustrack.schedule.contract.dto.lesson.TransferLessonRequest(
                    next, 2, null, null, null, "1", UUID.randomUUID());
            assertThatThrownBy(() -> transfers.transfer(target.lessonId(), ACTOR_ID, transfer))
                    .isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_transfer_operations WHERE occurrence_id = ?",
                    Long.class, target.occurrenceId())).isZero();
            assertThatThrownBy(() -> placement.acknowledge(identity.toBuilder().setActorId(ACTOR_ID + 1).build()))
                    .isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
            assertThat(placement.acknowledge(identity).getState()).isEqualTo("ACKNOWLEDGED");
            assertThat(placement.acknowledge(identity).getState()).isEqualTo("ACKNOWLEDGED");
            assertThat(transfers.transfer(target.lessonId(), ACTOR_ID, transfer).state()).isEqualTo("PENDING");
            assertThat(placement.get(active.getBindingId()).getDate()).isEqualTo(next.toString());
            assertThat(placement.continuation(identity).getAcceptedBinding().getDate()).isEqualTo(targetDate);
        });
    }

    @Test
    void abortTombstoneFencesDelayedMoveAndAppliedResultWinsAbort() {
        long semester = FIXTURE_SEQUENCE.incrementAndGet();
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semester)).thenReturn(
                SemesterStateResponse.newBuilder().setId(semester).setActive(true).build());
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> {
            HomeworkBindingResponse active = createDate(dateCreate(semester, "2090-01-01"));
            HomeworkEditIdentity cancelled = editIdentity(active, semester);
            assertThatThrownBy(() -> placement.continuation(cancelled))
                    .isInstanceOf(ru.rutcampustrack.schedule.exception.ResourceNotFoundException.class);
            assertThat(placement.abortUnaccepted(cancelled).getState()).isEqualTo("NOT_ACCEPTED");
            var move = dateMove(active, cancelled, "2090-01-02");
            assertThatThrownBy(() -> placement.move(move)).isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
            assertThat(placement.get(active.getBindingId()).getDate()).isEqualTo("2090-01-01");
            HomeworkEditIdentity admitted = editIdentity(active, semester);
            HomeworkEditReceipt accepted = placement.move(dateMove(active, admitted, "2090-01-02"));
            assertThat(placement.abortUnaccepted(admitted)).isEqualTo(accepted);
            assertThatThrownBy(() -> placement.continuation(admitted.toBuilder().setCommandHash(ByteString.copyFrom(new byte[32])).build()))
                    .isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE homework_placement_operations SET target_date = '2090-01-03' WHERE operation_id = ?",
                    UUID.fromString(admitted.getOperationId()))).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE lesson_homework_bindings SET pending_edit_operation_id = NULL WHERE binding_id = ?",
                    active.getBindingId())).isInstanceOf(DataAccessException.class);
            placement.acknowledge(admitted);
        });
    }

    @Test
    void datePendingPublicationAndAdmittedEditDrainArchiveWithoutOccurrence() {
        long semester = FIXTURE_SEQUENCE.incrementAndGet();
        AtomicReference<SemesterStateResponse> authority = new AtomicReference<>(SemesterStateResponse.newBuilder().setId(semester).setActive(true).build());
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semester)).thenAnswer(invocation -> authority.get());
        archiveBarrierSemesterIds.add(semester);
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> {
            var create = dateCreate(semester, "2090-01-01");
            var pending = homeworkBindingService.reserve(create);
            UUID archive = UUID.randomUUID();
            authority.set(SemesterStateResponse.newBuilder().setId(semester).setStateVersion(94).setTransition(SemesterTransition.ARCHIVING).setWriteBlocked(true).build());
            var prepare = SetSemesterArchiveBarrierRequest.newBuilder().setOperationId(archive.toString())
                    .setSemesterId(semester).setStateVersion(94).setCommand(SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_PREPARE_ARCHIVE).build();
            var blocked = archiveBarrierService.set(prepare);
            assertThat(blocked.getState()).isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_PENDING);
            assertThat(blocked.getPendingBinding().getBindingId()).isEqualTo(pending.getBindingId());
            assertThat(blocked.getPendingBinding().getOccurrenceId()).isZero();
            var cancel = prepare.toBuilder().setCommand(SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_RECONCILE_HOMEWORK_BINDING)
                    .setBinding(blocked.getPendingBinding()).setBindingResolution(SemesterArchiveHomeworkBindingResolution.SEMESTER_ARCHIVE_HOMEWORK_BINDING_CANCEL_UNPUBLISHED).build();
            assertThat(archiveBarrierService.set(cancel).getTerminalEventId()).isNotBlank();
            assertThat(archiveBarrierService.set(cancel).getTerminalEventId()).isNotBlank();
            assertThat(archiveBarrierService.set(prepare).getState()).isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_PENDING);
            // Cancellation effect remains pending until Academic acknowledges its durable outbox effect.
        });
        long editSemester = FIXTURE_SEQUENCE.incrementAndGet();
        AtomicReference<SemesterStateResponse> editAuthority = new AtomicReference<>(SemesterStateResponse.newBuilder().setId(editSemester).setActive(true).build());
        when(academicGrpcClient.getSemesterArchiveAuthorityState(editSemester)).thenAnswer(invocation -> editAuthority.get());
        archiveBarrierSemesterIds.add(editSemester);
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> {
            var active = createDate(dateCreate(editSemester, "2090-01-01"));
            var identity = editIdentity(active, editSemester);
            placement.move(dateMove(active, identity, "2090-01-02"));
            var beforeAck = deletionSnapshots.read(editSemester).participantDigest();
            UUID archive = UUID.randomUUID();
            editAuthority.set(SemesterStateResponse.newBuilder().setId(editSemester).setStateVersion(95).setTransition(SemesterTransition.ARCHIVING).setWriteBlocked(true).build());
            var prepare = SetSemesterArchiveBarrierRequest.newBuilder().setOperationId(archive.toString())
                    .setSemesterId(editSemester).setStateVersion(95).setCommand(SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_PREPARE_ARCHIVE).build();
            assertThat(archiveBarrierService.set(prepare).getState()).isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_PENDING);
            assertThat(placement.continuation(identity).getState()).isEqualTo("APPLIED_AWAITING_ACK");
            assertThat(placement.acknowledge(identity).getState()).isEqualTo("ACKNOWLEDGED");
            assertThat(deletionSnapshots.read(editSemester).participantDigest()).isEqualTo(beforeAck);
            assertThat(archiveBarrierService.set(prepare).getState()).isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_READY);
        });
    }

    @Test
    void dateCutoffIsNextMidnightInMoscowAndTerminalReplayIsDenied() {
        long semester = FIXTURE_SEQUENCE.incrementAndGet();
        AtomicReference<SemesterStateResponse> authority = new AtomicReference<>(SemesterStateResponse.newBuilder().setId(semester).setActive(true).build());
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semester)).thenAnswer(invocation -> authority.get());
        archiveBarrierSemesterIds.add(semester);
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> {
            var create = dateCreate(semester, "2026-10-02");
            var active = createDate(create);
            instant.set(Instant.parse("2026-10-02T20:59:59Z"));
            assertThat(placement.archiveExpiredDates()).isZero();
            instant.set(Instant.parse("2026-10-02T21:00:00Z"));
            assertThat(placement.archiveExpiredDates()).isEqualTo(1);
            assertThat(placement.get(active.getBindingId()).getState()).isEqualTo(HomeworkBindingState.HOMEWORK_BINDING_STATE_ARCHIVED);
            assertThatThrownBy(() -> homeworkBindingService.reserve(create)).isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
            assertThat(placement.get(active.getBindingId()).getHomeworkId()).isEqualTo(active.getHomeworkId());
        });
    }

    @Test
    void dateBindingDeletionIncludesInventoryAndRetainsImmutablePublicationIdentity() {
        long semester = FIXTURE_SEQUENCE.incrementAndGet();
        AtomicReference<SemesterStateResponse> authority = new AtomicReference<>(SemesterStateResponse.newBuilder().setId(semester).setActive(true).build());
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semester)).thenAnswer(invocation -> authority.get());
        archiveBarrierSemesterIds.add(semester);
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> {
            String emptyDigest = deletionSnapshots.read(semester).participantDigest();
            var create = dateCreate(semester, "2090-01-01");
            var active = createDate(create);
            var before = deletionSnapshots.read(semester);
            assertThat(before.participantDigest()).isNotEqualTo(emptyDigest);
            UUID operation = UUID.randomUUID();
            authority.set(deletionAuthority(semester, 96, operation, "PREPARING"));
            assertThat(archiveBarrierService.set(deleteBarrierRequest(operation, semester, 96,
                    SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_PREPARE_DELETE, before.participantDigest())).getState())
                    .isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_READY);
            archiveBarrierService.set(deleteBarrierRequest(operation, semester, 96,
                    SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_SEAL_DELETE, before.participantDigest()));
            authority.set(deletionAuthority(semester, 96, operation, "DELETING").toBuilder().setArchived(true).build());
            var commit = deleteBarrierRequest(operation, semester, 96,
                    SemesterArchiveBarrierCommand.SEMESTER_ARCHIVE_BARRIER_COMMIT_DELETE, before.participantDigest());
            assertThat(archiveBarrierService.set(commit).getState()).isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_DELETED);
            assertThat(archiveBarrierService.set(commit).getState()).isEqualTo(SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_DELETED);
            var row = jdbcTemplate.queryForMap("SELECT * FROM lesson_homework_bindings WHERE binding_id = ?", active.getBindingId());
            assertThat(row.get("state")).isEqualTo("ARCHIVED"); assertThat(row.get("homework_id")).isNull();
            assertThat(row.get("request_key").toString()).isEqualTo(create.getRequestKey());
            assertThat(row.get("original_binding_mode")).isEqualTo("DATE");
            assertThat(row.get("semester_id")).isEqualTo(semester);
            assertThat(row.get("occurrence_id")).isNull();
            assertThatThrownBy(() -> homeworkBindingService.reserve(create)).isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
        });
    }

    @Test
    void closedTargetAndElapsedLinkedSourceRejectWithoutPlacementReceiptOrRevision() {
        long semester = FIXTURE_SEQUENCE.incrementAndGet();
        Fixture lesson = insertFixture(AUTHORIZED_GROUP_ID, semester);
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semester)).thenReturn(
                SemesterStateResponse.newBuilder().setId(semester).setActive(true).build());
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> {
            String date = jdbcTemplate.queryForObject("SELECT date::TEXT FROM lessons WHERE id = ?", String.class, lesson.lessonId());
            var active = createDate(dateCreate(semester, date));
            var targetIdentity = editIdentity(active, semester);
            var targetMove = MoveHomeworkBindingRequest.newBuilder().setIdentity(targetIdentity).setBindingMode("LESSON")
                    .setDate(date).setLessonNumber(1).setTargetOccurrenceId(lesson.occurrenceId())
                    .setExpectedBindingRevision(active.getRevision()).setExpectedLessonRevision(1).build();
            jdbcTemplate.update("UPDATE lessons SET status = 'closed'::lesson_status WHERE id = ?", lesson.lessonId());
            assertThatThrownBy(() -> placement.move(targetMove)).isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM homework_placement_operations WHERE binding_id = ?", Long.class, active.getBindingId())).isZero();
            assertThat(placement.get(active.getBindingId()).getRevision()).isEqualTo(active.getRevision());
            assertThat(placement.get(active.getBindingId()).getBindingMode()).isEqualTo("DATE");
            jdbcTemplate.update("UPDATE lessons SET status = 'planned'::lesson_status WHERE id = ?", lesson.lessonId());
            var accepted = placement.move(targetMove); // unchanged future target is allowed
            placement.acknowledge(targetIdentity);
            var linked = accepted.getAcceptedBinding();
            instant.set(LocalDate.parse(date).atTime(10, 6).atZone(ZoneId.of("Europe/Moscow")).toInstant());
            var sourceIdentity = editIdentity(linked, semester);
            assertThatThrownBy(() -> placement.move(dateMove(linked, sourceIdentity, LocalDate.parse(date).plusDays(1).toString())))
                    .isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM homework_placement_operations WHERE binding_id = ?", Long.class, active.getBindingId())).isEqualTo(1);
            assertThat(placement.get(active.getBindingId()).getRevision()).isEqualTo(linked.getRevision());
            assertThat(placement.get(active.getBindingId()).getOccurrenceId()).isEqualTo(lesson.occurrenceId());
            // The same elapsed physical target must also be refused while status still says planned.
            var second = createDate(dateCreate(semester, date));
            assertThatThrownBy(() -> placement.move(targetMove.toBuilder().setIdentity(editIdentity(second, semester))
                    .setExpectedBindingRevision(second.getRevision()).build())).isInstanceOf(ru.rutcampustrack.schedule.exception.ConflictException.class);
        });
    }

    @Test
    void blockedExpiredBatchDoesNotStarveWritableSemester() {
        long blocked = FIXTURE_SEQUENCE.incrementAndGet(), writable = FIXTURE_SEQUENCE.incrementAndGet();
        when(academicGrpcClient.getSemesterArchiveAuthorityState(writable)).thenReturn(
                SemesterStateResponse.newBuilder().setId(writable).setActive(true).build());
        jdbcTemplate.update("""
                INSERT INTO lesson_homework_bindings
                    (binding_mode, group_id, subject_id, semester_id, placement_date, actor_id, request_key, payload_hash, state, revision)
                SELECT 'DATE', ?, ?, ?, '2026-10-02'::date, ?, gen_random_uuid(), decode(repeat('11', 32), 'hex'), 'PENDING', 1
                  FROM generate_series(1, 256)
                """, AUTHORIZED_GROUP_ID, SUBJECT_ID, blocked, ACTOR_ID);
        jdbcTemplate.update("""
                INSERT INTO schedule_semester_archive_barriers(semester_id, operation_id, state_version, participant_state)
                VALUES (?, ?, 97, 'READY')
                """, blocked, UUID.randomUUID());
        archiveBarrierSemesterIds.add(blocked);
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> createDate(dateCreate(writable, "2026-10-02")));
        instant.set(Instant.parse("2026-10-02T21:00:00Z"));
        assertThat(placement.archiveExpiredDates()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_homework_bindings WHERE semester_id = ? AND state = 'PENDING'", Long.class, blocked)).isEqualTo(256);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lesson_homework_bindings WHERE semester_id = ? AND state = 'ARCHIVED'", Long.class, writable)).isEqualTo(1);
    }

    @Test
    void batchReadAndMoveWithReversedOriginIdsCompleteUnderConcurrentLocks() throws Exception {
        long semester = FIXTURE_SEQUENCE.incrementAndGet();
        Fixture first = insertFixture(AUTHORIZED_GROUP_ID, semester), second = insertFixture(AUTHORIZED_GROUP_ID, semester);
        Fixture source = cloneFixture(second), target = cloneFixture(first);
        long highOrigin = jdbcTemplate.queryForObject("SELECT schedule_item_id FROM lesson_occurrences WHERE id = ?", Long.class, source.occurrenceId());
        long lowOrigin = jdbcTemplate.queryForObject("SELECT schedule_item_id FROM lesson_occurrences WHERE id = ?", Long.class, target.occurrenceId());
        assertThat(source.occurrenceId()).isLessThan(target.occurrenceId()); assertThat(highOrigin).isGreaterThan(lowOrigin);
        when(academicGrpcClient.getSemesterArchiveAuthorityState(semester)).thenReturn(
                SemesterStateResponse.newBuilder().setId(semester).setActive(true).build());
        var sourceDate = jdbcTemplate.queryForObject("SELECT date::TEXT FROM lessons WHERE id = ?", String.class, source.lessonId());
        var targetDate = jdbcTemplate.queryForObject("SELECT date::TEXT FROM lessons WHERE id = ?", String.class, target.lessonId());
        AtomicReference<HomeworkBindingResponse> linked = new AtomicReference<>();
        Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).run(() -> {
            var active = createDate(dateCreate(semester, sourceDate)); var identity = editIdentity(active, semester);
            linked.set(placement.move(MoveHomeworkBindingRequest.newBuilder().setIdentity(identity).setBindingMode("LESSON")
                    .setDate(sourceDate).setLessonNumber(1).setTargetOccurrenceId(source.occurrenceId())
                    .setExpectedBindingRevision(active.getRevision()).setExpectedLessonRevision(1).build()).getAcceptedBinding());
            placement.acknowledge(identity);
        });
        var highLocked = new java.util.concurrent.CountDownLatch(1);
        var resumeReader = new java.util.concurrent.CountDownLatch(1);
        var paused = new java.util.concurrent.atomic.AtomicBoolean();
        JdbcTemplate readerJdbc = new JdbcTemplate(jdbcTemplate.getDataSource()) {
            @Override public <T> List<T> query(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
                List<T> result = super.query(sql, mapper, args);
                if (sql.equals("SELECT id FROM schedule_items WHERE id = ? FOR UPDATE") && args[0].equals(highOrigin) && paused.compareAndSet(false, true)) {
                    highLocked.countDown();
                    try { if (!resumeReader.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("reader coordination timeout"); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                }
                return result;
            }
        };
        var reader = new HomeworkBindingService(readerJdbc, writeFence, placement);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var writerName = "homework-move-" + UUID.randomUUID();
        var identity = editIdentity(linked.get(), semester);
        try {
            var read = executor.submit(() -> Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).call(() ->
                    tx.execute(status -> reader.getBindings(HomeworkBindingsRequest.newBuilder().addOccurrenceIds(source.occurrenceId()).addOccurrenceIds(target.occurrenceId()).build()))));
            assertThat(highLocked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var write = executor.submit(() -> Context.current().withValue(HomeworkBindingActorContext.CLAIMS, headman()).call(() -> tx.execute(status -> {
                jdbcTemplate.queryForObject("SELECT set_config('application_name', ?, true)", String.class, writerName);
                return placement.move(MoveHomeworkBindingRequest.newBuilder().setIdentity(identity).setBindingMode("LESSON").setDate(targetDate)
                        .setLessonNumber(1).setTargetOccurrenceId(target.occurrenceId()).setExpectedBindingRevision(linked.get().getRevision())
                        .setExpectedLessonRevision(1).build());
            })));
            boolean waiting = false; long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!waiting && System.nanoTime() < until) {
                waiting = Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE application_name = ? AND wait_event_type = 'Lock')", Boolean.class, writerName));
                if (!waiting) Thread.sleep(10);
            }
            assertThat(waiting).isTrue(); resumeReader.countDown();
            assertThat(read.get(10, java.util.concurrent.TimeUnit.SECONDS)).hasSize(1);
            assertThat(write.get(10, java.util.concurrent.TimeUnit.SECONDS).getState()).isEqualTo("APPLIED_AWAITING_ACK");
            placement.acknowledge(identity);
            assertThat(jdbcTemplate.queryForObject("SELECT occurrence_id FROM lesson_homework_bindings WHERE binding_id = ?", Long.class, linked.get().getBindingId())).isEqualTo(target.occurrenceId());
        } finally { resumeReader.countDown(); executor.shutdownNow(); }
    }

    private Fixture cloneFixture(Fixture original) {
        Long occurrence = jdbcTemplate.queryForObject("""
                INSERT INTO lesson_occurrences(schedule_item_id, occurrence_date, assignment_id, group_id, subject_id, semester_id, assigned_teacher_id, lesson_type)
                SELECT schedule_item_id, occurrence_date + 7, assignment_id, group_id, subject_id, semester_id, assigned_teacher_id, lesson_type
                  FROM lesson_occurrences WHERE id = ? RETURNING id
                """, Long.class, original.occurrenceId());
        occurrenceIds.add(occurrence);
        Long lesson = jdbcTemplate.queryForObject("""
                INSERT INTO lessons(schedule_item_id, occurrence_id, assignment_id, group_id, subject_id, semester_id,
                    assigned_teacher_id, lesson_type, lesson_number, day_of_week, start_time, end_time, room_snapshot, week_type_snapshot,
                    generation, revision, date, status, is_geo_blocked)
                SELECT schedule_item_id, ?, assignment_id, group_id, subject_id, semester_id, assigned_teacher_id, lesson_type,
                    lesson_number, day_of_week, start_time, end_time, room_snapshot, week_type_snapshot, 1, 1, date + 7, 'planned'::lesson_status, false
                  FROM lessons WHERE id = ? RETURNING id
                """, Long.class, occurrence, original.lessonId());
        lessonIds.add(lesson); jdbcTemplate.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?", lesson, occurrence);
        return new Fixture(occurrence, lesson);
    }

    private InternalJwtClaims headman() {
        return new InternalJwtClaims(ACTOR_ID, UUID.randomUUID(), 1L, 1L, "HEADMAN", "ACTIVE", AUTHORIZED_GROUP_ID, true, false);
    }

    private ReserveHomeworkBindingRequest dateCreate(long semester, String date) {
        byte[] hash = new byte[32]; hash[0] = 17;
        return ReserveHomeworkBindingRequest.newBuilder().setBindingMode("DATE").setDate(date)
                .setGroupId(AUTHORIZED_GROUP_ID).setSubjectId(SUBJECT_ID).setSemesterId(semester)
                .setRequestKey(UUID.randomUUID().toString()).setPayloadHash(ByteString.copyFrom(hash)).build();
    }

    private HomeworkBindingResponse createDate(ReserveHomeworkBindingRequest request) {
        var pending = homeworkBindingService.reserve(request);
        assertThat(pending.getOccurrenceId()).isZero(); assertThat(pending.hasCurrentLesson()).isFalse();
        return homeworkBindingService.confirm(ConfirmHomeworkBindingRequest.newBuilder().setBindingId(pending.getBindingId())
                .setHomeworkId(FIXTURE_SEQUENCE.incrementAndGet()).setRequestKey(request.getRequestKey()).build());
    }

    private HomeworkEditIdentity editIdentity(HomeworkBindingResponse active, long semester) {
        byte[] hash = new byte[32]; hash[0] = 29;
        return HomeworkEditIdentity.newBuilder().setOperationId(UUID.randomUUID().toString()).setBindingId(active.getBindingId())
                .setHomeworkId(active.getHomeworkId()).setActorId(ACTOR_ID).setRequestKey(UUID.randomUUID().toString())
                .setCommandHash(ByteString.copyFrom(hash)).setGroupId(AUTHORIZED_GROUP_ID).setSubjectId(SUBJECT_ID).setSemesterId(semester).build();
    }

    private MoveHomeworkBindingRequest dateMove(HomeworkBindingResponse active, HomeworkEditIdentity identity, String date) {
        return MoveHomeworkBindingRequest.newBuilder().setIdentity(identity).setBindingMode("DATE").setDate(date)
                .setExpectedBindingRevision(active.getRevision()).build();
    }

    private static SetSemesterArchiveBarrierRequest deleteBarrierRequest(
            UUID operationId, long semesterId, long stateVersion,
            SemesterArchiveBarrierCommand command, String expectedDigest) {
        return SetSemesterArchiveBarrierRequest.newBuilder()
                .setOperationId(operationId.toString())
                .setSemesterId(semesterId)
                .setStateVersion(stateVersion)
                .setCommand(command)
                .setExpectedParticipantDigest(expectedDigest)
                .build();
    }

    private static SemesterStateResponse deletionAuthority(long semesterId, long stateVersion,
                                                            UUID operationId, String phase) {
        return SemesterStateResponse.newBuilder()
                .setId(semesterId)
                .setStateVersion(stateVersion)
                .setTransition(SemesterTransition.DELETING)
                .setWriteBlocked(true)
                .setDeletionPhase(phase)
                .setTransitionOperationId(operationId.toString())
                .build();
    }

    private Fixture insertFixture(long groupId) {
        return insertFixture(groupId, SEMESTER_ID);
    }

    private Fixture insertFixture(long groupId, long semesterId) {
        long assignmentId = FIXTURE_SEQUENCE.incrementAndGet();
        LocalDate date = LocalDate.of(2090, 1, 1).plusDays(assignmentId % 10000);
        int lessonNumber = groupId == AUTHORIZED_GROUP_ID ? 1 : 2;
        jdbcTemplate.update("""
                INSERT INTO schedule_assignment_fences
                    (assignment_id, group_id, subject_id, semester_id, assigned_teacher_id,
                     lesson_type, valid_from, cap_until_exclusive, creation_cap_until_exclusive)
                VALUES (?, ?, ?, ?, 884001, 'lecture', ?, ?, ?)
                """, assignmentId, groupId, SUBJECT_ID, semesterId,
                date, date.plusDays(45), date.plusDays(45));
        Long scheduleItemId = jdbcTemplate.queryForObject("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room)
                VALUES (?, ?, ?, ?, 4, ?, '08:30'::time, '10:00'::time, 'all', 'B-101')
                RETURNING id
                """, Long.class, assignmentId, groupId, SUBJECT_ID, semesterId, lessonNumber);
        scheduleItemIds.add(scheduleItemId);

        Long occurrenceId = jdbcTemplate.queryForObject("""
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type)
                VALUES (?, ?, ?, ?, ?, ?, 884001, 'lecture')
                RETURNING id
                """, Long.class, scheduleItemId, date, assignmentId, groupId,
                SUBJECT_ID, semesterId);
        occurrenceIds.add(occurrenceId);

        Long lessonId = jdbcTemplate.queryForObject("""
                INSERT INTO lessons
                    (schedule_item_id, occurrence_id, assignment_id, group_id, subject_id,
                     semester_id, assigned_teacher_id, lesson_type, lesson_number,
                     day_of_week, start_time, end_time, room_snapshot, week_type_snapshot,
                     generation, revision, date, status, is_geo_blocked)
                VALUES (?, ?, ?, ?, ?, ?, 884001, 'lecture', ?,
                        4, '08:30'::time, '10:00'::time, 'B-101', 'all',
                        1, 1, ?, 'planned'::lesson_status, false)
                RETURNING id
                """, Long.class, scheduleItemId, occurrenceId, assignmentId, groupId,
                SUBJECT_ID, semesterId, lessonNumber, date);
        lessonIds.add(lessonId);
        jdbcTemplate.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                lessonId, occurrenceId);
        return new Fixture(occurrenceId, lessonId);
    }

    private record Fixture(long occurrenceId, long lessonId) {
    }
}
