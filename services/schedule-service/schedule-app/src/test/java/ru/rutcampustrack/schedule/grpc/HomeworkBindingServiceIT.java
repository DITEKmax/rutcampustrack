package ru.rutcampustrack.schedule.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Context;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
                date, date.plusDays(1), date.plusDays(1));
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
