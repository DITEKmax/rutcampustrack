package ru.rutcampustrack.schedule.grpc;

import com.google.protobuf.ByteString;
import io.grpc.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
