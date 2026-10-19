package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.schedule.contract.enums.*;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.subjectdeletion.ScheduleSubjectDeletionParticipant;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.UnaryOperator;

/** Real canonical PG rows/guards/outbox, controlled service transport is tested separately. */
class SubjectDeletionParticipantIT extends AbstractScheduleIntegrationTest {
    @Autowired ScheduleSubjectDeletionParticipant participant;
    @Autowired PlatformTransactionManager manager;
    @Autowired ru.rutcampustrack.schedule.oneoff.OneOffLessonWriter oneOff;
    @Autowired ru.rutcampustrack.schedule.lesson.LessonRoomWriter room;
    @Autowired ru.rutcampustrack.schedule.recurring.RecurringScheduleItemWriter recurring;
    @Autowired ru.rutcampustrack.schedule.recurring.RecurringScheduleItemLifecycleWriter lifecycle;
    @Autowired ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence archive;

    @Autowired
    ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveBarrierService semesterArchive;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.schedule.grpc.AcademicGrpcClient academic;

    private long lesson;

    @BeforeEach
    void ownFixture() {
        resetScheduleData();
        lesson = seed(20, 1, 30);
        seed(21, 2, 31);
    }

    @Test
    void releaseBeforePreparePersistsExactTerminalTupleAndLeavesOrdinaryWritesAvailable()
            throws Exception {
        var c = prepare();
        var release = c.toBuilder().setAction(SubjectDeletionAction.RELEASE).build();
        var receipt = participant.apply(release);
        assertThat(receipt.getAffectedRows()).isZero();
        assertThat(state(c)).isEqualTo("RELEASED");
        byte[] saved =
                jdbcTemplate.queryForObject(
                        "SELECT command_manifest FROM schedule_subject_deletion_participants WHERE"
                            + " operation_id=?",
                        byte[].class,
                        UUID.fromString(c.getOperationId()));
        assertThat(SubjectDeletionCommand.parseFrom(saved)).isEqualTo(c);
        assertThat(participant.apply(release)).isEqualTo(receipt);
        assertLateAndMismatchedCommandsRefused(c);
        assertThat(state(c)).isEqualTo("RELEASED");
        assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=20")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM schedule_outbox WHERE event_type='lesson.deleted'"))
                .isZero();
        jdbcTemplate.update("UPDATE lessons SET revision=revision+1 WHERE id=?", lesson);
        var fresh = prepare();
        participant.apply(fresh);
        participant.apply(fresh.toBuilder().setAction(SubjectDeletionAction.RELEASE).build());
        jdbcTemplate.update("UPDATE lessons SET revision=revision+1 WHERE id=?", lesson);
        seed(22, 3, 30); // The cancelled operation also leaves its semester writable.
    }

    @Test
    void emptySubjectCommitsOrReleasesAndTerminalCommandsCannotResurrectItsEpoch() {
        for (var action : List.of(SubjectDeletionAction.RELEASE, SubjectDeletionAction.COMMIT)) {
            var request =
                    SubjectDeletionCommand.newBuilder()
                            .setOperationId(UUID.randomUUID().toString())
                            .setSubjectId(99)
                            .setSemesterId(30)
                            .setGroupId(10)
                            .setActorId(42)
                            .setAction(SubjectDeletionAction.PREVIEW)
                            .build();
            var c =
                    request.toBuilder()
                            .setParticipantDigest(participant.apply(request).getParticipantDigest())
                            .setManifestDigest("a".repeat(64))
                            .setAction(SubjectDeletionAction.PREPARE)
                            .build();
            participant.apply(c);
            participant.apply(c.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
            var terminal = c.toBuilder().setAction(action).build();
            var receipt = participant.apply(terminal);
            assertThat(receipt.getAffectedRows()).isZero();
            assertThat(receipt.getLessonsList()).isEmpty();
            assertThat(participant.apply(terminal)).isEqualTo(receipt);
            for (var late : List.of(SubjectDeletionAction.PREPARE, SubjectDeletionAction.SEAL))
                assertThatThrownBy(() -> participant.apply(c.toBuilder().setAction(late).build()))
                        .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(
                            () ->
                                    participant.apply(
                                            c.toBuilder()
                                                    .setAction(
                                                            action == SubjectDeletionAction.COMMIT
                                                                    ? SubjectDeletionAction.RELEASE
                                                                    : SubjectDeletionAction.COMMIT)
                                                    .build()))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(state(c))
                    .isEqualTo(action == SubjectDeletionAction.COMMIT ? "DELETED" : "RELEASED");
        }
        assertThat(count("SELECT count(*) FROM lessons")).isEqualTo(2);
    }

    @Test
    void releaseRacingPrepareAlwaysFinishesWithDurableReleasedEpochAndNoWriteFence()
            throws Exception {
        var c = prepare();
        var entered = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        List<Future<Throwable>> attempts = new ArrayList<>();
        List<Throwable> outcomes = new ArrayList<>();
        try {
            for (var action : List.of(SubjectDeletionAction.PREPARE, SubjectDeletionAction.RELEASE))
                attempts.add(
                        pool.submit(
                                () -> {
                                    entered.await(5, TimeUnit.SECONDS);
                                    try {
                                        participant.apply(c.toBuilder().setAction(action).build());
                                        return null;
                                    } catch (RuntimeException failure) {
                                        return failure;
                                    }
                                }));
            for (var attempt : attempts) outcomes.add(attempt.get(15, TimeUnit.SECONDS));
        } finally {
            for (var attempt : attempts) if (!attempt.isDone()) attempt.cancel(true);
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(outcomes.get(1))
                .as("RELEASE succeeds whichever transaction acquired locks first")
                .isNull();
        if (outcomes.getFirst() != null)
            assertThat(outcomes.getFirst()).isInstanceOf(IllegalStateException.class);
        assertThat(state(c)).isEqualTo("RELEASED");
        assertThat(count("SELECT count(*) FROM schedule_subject_deletion_participants"))
                .isEqualTo(1);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.RELEASE).build());
        assertLateAndMismatchedCommandsRefused(c);
        jdbcTemplate.update("UPDATE lessons SET revision=revision+1 WHERE id=?", lesson);
        assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=20")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM schedule_outbox WHERE event_type='lesson.deleted'"))
                .isZero();
    }

    private String state(SubjectDeletionCommand c) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM schedule_subject_deletion_participants WHERE operation_id=?",
                String.class,
                UUID.fromString(c.getOperationId()));
    }

    private void assertLateAndMismatchedCommandsRefused(SubjectDeletionCommand c) {
        for (var action :
                List.of(
                        SubjectDeletionAction.PREPARE,
                        SubjectDeletionAction.SEAL,
                        SubjectDeletionAction.COMMIT))
            assertThatThrownBy(() -> participant.apply(c.toBuilder().setAction(action).build()))
                    .isInstanceOf(IllegalStateException.class);
        List<UnaryOperator<SubjectDeletionCommand.Builder>> changes =
                List.of(
                        b -> b.setActorId(99),
                        b -> b.setSubjectId(21),
                        b -> b.setSemesterId(31),
                        b -> b.setGroupId(11),
                        b -> b.setParticipantDigest("b".repeat(64)),
                        b -> b.setManifestDigest("b".repeat(64)),
                        SubjectDeletionCommand.Builder::clearLessons);
        for (var change : changes)
            assertThatThrownBy(
                            () ->
                                    participant.apply(
                                            change.apply(c.toBuilder())
                                                    .setAction(SubjectDeletionAction.RELEASE)
                                                    .build()))
                    .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void genuineOneOffRoomAndRecurringRestoreReceiptsSurviveTheirExactPhysicalCascade() {
        org.mockito.Mockito.when(academic.getSemesterArchiveAuthorityState(30L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(30)
                                .setActive(true)
                                .build());
        LocalDate date = LocalDate.now(java.time.ZoneId.of("Europe/Moscow")).plusDays(7);
        if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) date = date.plusDays(1);
        var authority =
                new ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority(
                        501, 700, 20, 10, 30, "lecture", date.minusDays(40), date.plusDays(40));
        var single =
                oneOff.create(
                        new ru.rutcampustrack.schedule.contract.dto.oneoff
                                .CreateOneOffLessonRequest(
                                10L, 20L, 501L, date, (short) 3, null, null, "Before"),
                        UUID.randomUUID(),
                        42,
                        authority);
        room.change(
                single.physicalLessonId(),
                42,
                UUID.randomUUID(),
                new ru.rutcampustrack.schedule.contract.dto.lesson.ChangeLessonRoomRequest(
                        "After", "1"),
                archive.prepareBusinessWrite(30));
        var request =
                new ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest(
                        501L,
                        10L,
                        20L,
                        30L,
                        (short) date.getDayOfWeek().getValue(),
                        (short) 4,
                        null,
                        null,
                        WeekType.ALL,
                        "Recurring");
        var created =
                recurring.write(request, UUID.randomUUID(), 42, authority, date, date.plusDays(7));
        var authorities = java.util.Map.of(501L, authority);
        var observed =
                lifecycle.preview(
                        created.scheduleItemId(), null, true, authorities, date, date.plusDays(7));
        lifecycle.mutate(
                created.scheduleItemId(),
                null,
                true,
                observed.revision(),
                UUID.randomUUID(),
                42,
                authorities,
                date,
                date.plusDays(7),
                archive.prepareBusinessWrite(30));
        lifecycle.reactivate(
                created.scheduleItemId(),
                request,
                UUID.randomUUID(),
                42,
                authorities,
                date,
                date.plusDays(7),
                archive.prepareBusinessWrite(30));
        // None of these accepted receipts grants an ordinary physical-delete authority.
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "DELETE FROM lessons WHERE id=?",
                                        single.physicalLessonId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "DELETE FROM schedule_items WHERE id=?",
                                        created.scheduleItemId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        String receipts =
                jdbcTemplate.queryForObject(
                        "SELECT coalesce(jsonb_agg(jsonb_build_object('kind',kind,'data',data)"
                                + " ORDER BY kind,data::TEXT),'[]'::jsonb)::TEXT FROM"
                                + " schedule_subject_retained_rows(20)",
                        String.class);
        assertThat(count("SELECT count(*) FROM schedule_one_off_create_replay")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM lesson_room_change_ledger")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM schedule_recurring_lifecycle_replay")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM lesson_template_restore_authorities"))
                .isGreaterThan(0);
        var preview =
                participant.apply(
                        SubjectDeletionCommand.newBuilder()
                                .setOperationId(UUID.randomUUID().toString())
                                .setSubjectId(20)
                                .setGroupId(10)
                                .setSemesterId(30)
                                .setActorId(42)
                                .setAction(SubjectDeletionAction.PREVIEW)
                                .build());
        assertThat(preview.getLessonsCount()).isGreaterThan(2);
        var c =
                SubjectDeletionCommand.newBuilder()
                        .setOperationId(preview.getOperationId())
                        .setSubjectId(20)
                        .setGroupId(10)
                        .setSemesterId(30)
                        .setActorId(42)
                        .setAction(SubjectDeletionAction.PREPARE)
                        .setParticipantDigest(preview.getParticipantDigest())
                        .setManifestDigest("a".repeat(64))
                        .addAllLessons(preview.getLessonsList())
                        .build();
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO"
                                            + " schedule_recurring_lifecycle_replay(operation_id,actor_id,request_key,payload_hash,schedule_item_id,action,response_snapshot,accepted_at)"
                                            + " VALUES(?,42,?,?,?,'DELETE','{}'::jsonb,NOW())",
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        new byte[32],
                                        created.scheduleItemId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.COMMIT).build());
        assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=20")).isZero();
        assertThat(count("SELECT count(*) FROM schedule_one_off_lessons WHERE subject_id=20"))
                .isZero();
        // Rows retain original exact IDs/tuples after detachment; joining live parents is no longer
        // possible.
        String retained =
                jdbcTemplate.queryForObject(
                        "SELECT jsonb_agg(jsonb_build_object('kind',kind,'data',data) ORDER BY"
                            + " kind,data::TEXT)::TEXT FROM (SELECT"
                            + " 'schedule_one_off_create_replay' AS kind,to_jsonb(r) AS data FROM"
                            + " schedule_one_off_create_replay r UNION ALL SELECT"
                            + " 'lesson_room_change_ledger',to_jsonb(r) FROM"
                            + " lesson_room_change_ledger r UNION ALL SELECT"
                            + " 'schedule_recurring_lifecycle_replay',to_jsonb(r) FROM"
                            + " schedule_recurring_lifecycle_replay r UNION ALL SELECT"
                            + " 'lesson_template_restore_authorities',to_jsonb(r) FROM"
                            + " lesson_template_restore_authorities r) rows",
                        String.class);
        assertThat(retained).isEqualTo(receipts);
        assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=21 AND semester_id=31"))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE lesson_room_change_ledger SET actor_id=99"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO"
                                            + " schedule_recurring_lifecycle_replay(operation_id,actor_id,request_key,payload_hash,schedule_item_id,action,response_snapshot,accepted_at)"
                                            + " VALUES(?,42,?,?,?,'DELETE','{}'::jsonb,NOW())",
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        new byte[32],
                                        created.scheduleItemId()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.COMMIT).build());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM schedule_one_off_create_replay", Long.class))
                .isEqualTo(1);
    }

    @Test
    void establishedFinalSemesterAuthorityStillCascadesOneOffReceiptsOnlyInItsOwnSemester() {
        org.mockito.Mockito.when(academic.getSemesterArchiveAuthorityState(30L))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(30)
                                .setActive(true)
                                .build());
        LocalDate date = LocalDate.now(java.time.ZoneId.of("Europe/Moscow")).plusDays(7);
        if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) date = date.plusDays(1);
        var authority =
                new ru.rutcampustrack.schedule.recurring.RecurringAssignmentAuthority(
                        501, 700, 20, 10, 30, "lecture", date.minusDays(40), date.plusDays(40));
        oneOff.create(
                new ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest(
                        10L, 20L, 501L, date, (short) 3, null, null, "Room"),
                UUID.randomUUID(),
                42,
                authority);
        assertThat(count("SELECT count(*) FROM schedule_one_off_create_replay")).isEqualTo(1);
        var reader =
                new ru.rutcampustrack.schedule.grpc.ScheduleSemesterDeletionSnapshotReader(
                        jdbcTemplate);
        String digest = reader.read(30).participantDigest();
        UUID operation = UUID.randomUUID();
        var remote =
                ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                        .setId(30)
                        .setStateVersion(7)
                        .setTransition(ru.rutcampustrack.academic.grpc.SemesterTransition.DELETING)
                        .setWriteBlocked(true)
                        .setDeletionPhase("PREPARING")
                        .setTransitionOperationId(operation.toString());
        org.mockito.Mockito.when(academic.getSemesterArchiveAuthorityState(30L))
                .thenReturn(remote.build());
        var command =
                ru.rutcampustrack.schedule.grpc.SetSemesterArchiveBarrierRequest.newBuilder()
                        .setOperationId(operation.toString())
                        .setSemesterId(30)
                        .setStateVersion(7)
                        .setExpectedParticipantDigest(digest);
        var prepared =
                semesterArchive.set(
                        command.setCommand(
                                        ru.rutcampustrack.schedule.grpc
                                                .SemesterArchiveBarrierCommand
                                                .SEMESTER_ARCHIVE_BARRIER_PREPARE_DELETE)
                                .build());
        assertThat(prepared.getState())
                .isEqualTo(
                        ru.rutcampustrack.schedule.grpc.SemesterArchiveParticipantState
                                .SEMESTER_ARCHIVE_PARTICIPANT_READY);
        var sealed =
                semesterArchive.set(
                        command.setCommand(
                                        ru.rutcampustrack.schedule.grpc
                                                .SemesterArchiveBarrierCommand
                                                .SEMESTER_ARCHIVE_BARRIER_SEAL_DELETE)
                                .build());
        assertThat(sealed.getState())
                .isEqualTo(
                        ru.rutcampustrack.schedule.grpc.SemesterArchiveParticipantState
                                .SEMESTER_ARCHIVE_PARTICIPANT_READY);
        org.mockito.Mockito.when(academic.getSemesterArchiveAuthorityState(30L))
                .thenReturn(remote.setDeletionPhase("DELETING").build());
        var result =
                semesterArchive.set(
                        command.setCommand(
                                        ru.rutcampustrack.schedule.grpc
                                                .SemesterArchiveBarrierCommand
                                                .SEMESTER_ARCHIVE_BARRIER_COMMIT_DELETE)
                                .build());
        assertThat(result.getState())
                .isEqualTo(
                        ru.rutcampustrack.schedule.grpc.SemesterArchiveParticipantState
                                .SEMESTER_ARCHIVE_PARTICIPANT_DELETED);
        assertThat(count("SELECT count(*) FROM schedule_one_off_create_replay")).isZero();
        assertThat(count("SELECT count(*) FROM lessons WHERE semester_id=30")).isZero();
        assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=21 AND semester_id=31"))
                .isEqualTo(1);
    }

    @Test
    void exactCanonicalManifestDeletesOnlySubjectAndReplayHasOneOutboxFact() {
        var c = prepare();
        participant.apply(c);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE lessons SET revision=revision+1 WHERE id=?",
                                        lesson))
                .isInstanceOf(RuntimeException.class);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
        var result =
                participant.apply(c.toBuilder().setAction(SubjectDeletionAction.COMMIT).build());
        assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=20")).isZero();
        assertThat(count("SELECT count(*) FROM lesson_occurrences WHERE subject_id=20")).isZero();
        assertThat(count("SELECT count(*) FROM schedule_items WHERE subject_id=20")).isZero();
        assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=21 AND semester_id=31"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM schedule_items WHERE subject_id=21")).isEqualTo(1);
        long events =
                count("SELECT count(*) FROM schedule_outbox WHERE event_type='lesson.deleted'");
        assertThat(events).isEqualTo(1);
        assertThat(participant.apply(c.toBuilder().setAction(SubjectDeletionAction.COMMIT).build()))
                .isEqualTo(result);
        assertThat(count("SELECT count(*) FROM schedule_outbox WHERE event_type='lesson.deleted'"))
                .isEqualTo(events);
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE schedule_assignment_fences SET"
                                                + " cap_until_exclusive=cap_until_exclusive+1 WHERE"
                                                + " subject_id=20"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void changedSnapshotForeignScopeAndLegacyCrossSemesterRefuseWithoutEffects() {
        var c = prepare();
        jdbcTemplate.update("UPDATE lessons SET revision=revision+1 WHERE id=?", lesson);
        assertThatThrownBy(() -> participant.apply(c)).isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT count(*) FROM schedule_subject_deletion_participants")).isZero();
        var fresh = prepare();
        assertThatThrownBy(() -> participant.apply(fresh.toBuilder().setSemesterId(31).build()))
                .isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT count(*) FROM lessons")).isEqualTo(2);
    }

    @Test
    void releaseRestoresOrdinaryWritesAndCommitOutboxFailureRemainsSealed() {
        var c = prepare();
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(SubjectDeletionAction.RELEASE).build());
        jdbcTemplate.update("UPDATE lessons SET revision=revision+1 WHERE id=?", lesson);
        var retry = prepare();
        participant.apply(retry);
        participant.apply(retry.toBuilder().setAction(SubjectDeletionAction.SEAL).build());
        String name = "subject_outbox_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.execute(
                "ALTER TABLE schedule_outbox ADD CONSTRAINT "
                        + name
                        + " CHECK(event_type<>'lesson.deleted') NOT VALID");
        Throwable primary = null;
        try {
            assertThatThrownBy(
                            () ->
                                    participant.apply(
                                            retry.toBuilder()
                                                    .setAction(SubjectDeletionAction.COMMIT)
                                                    .build()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=20")).isEqualTo(1);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT state FROM schedule_subject_deletion_participants WHERE"
                                            + " operation_id=?",
                                    String.class,
                                    UUID.fromString(retry.getOperationId())))
                    .isEqualTo("SEALED");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                jdbcTemplate.execute("ALTER TABLE schedule_outbox DROP CONSTRAINT " + name);
            } catch (RuntimeException failure) {
                var fixed = new IllegalStateException("Owned subject outbox cleanup failed");
                if (primary != null) primary.addSuppressed(fixed);
                else throw fixed;
            }
        }
        participant.apply(retry.toBuilder().setAction(SubjectDeletionAction.COMMIT).build());
        assertThat(count("SELECT count(*) FROM lessons WHERE subject_id=20")).isZero();
    }

    private SubjectDeletionCommand prepare() {
        var request =
                SubjectDeletionCommand.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setSubjectId(20)
                        .setSemesterId(30)
                        .setGroupId(10)
                        .setActorId(42)
                        .setAction(SubjectDeletionAction.PREVIEW)
                        .build();
        var preview = participant.apply(request);
        assertThat(preview.getLessonsList())
                .extracting(SubjectDeletionLesson::getLessonId)
                .containsExactly(lesson);
        return request.toBuilder()
                .setAction(SubjectDeletionAction.PREPARE)
                .setParticipantDigest(preview.getParticipantDigest())
                .setManifestDigest("a".repeat(64))
                .addAllLessons(preview.getLessonsList())
                .build();
    }

    private long seed(long subject, int slot, long semester) {
        return new TransactionTemplate(manager)
                .execute(
                        status -> {
                            var item = new ScheduleItem();
                            item.setGroupId(10L);
                            item.setSubjectId(subject);
                            item.setSemesterId(semester);
                            item.setDayOfWeek((short) 1);
                            item.setLessonNumber((short) slot);
                            item.setWeekType(WeekType.ALL);
                            item = saveFixtureScheduleItem(item);
                            var lesson = new Lesson();
                            lesson.setScheduleItemId(item.getId());
                            lesson.setDate(LocalDate.of(2026, 2, 2));
                            lesson.setStatus(LessonStatus.CLOSED);
                            return saveFixtureLesson(lesson).getId();
                        });
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
