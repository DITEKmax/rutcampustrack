package ru.rutcampustrack.academic.constructor;

import static org.assertj.core.api.Assertions.*;

import com.google.protobuf.ByteString;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.constructor.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

/** Real migrated PostgreSQL: history retention and both admission race orders. */
class ConstructorAcademicParticipantIT extends AbstractAcademicIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ConstructorAcademicParticipant participant;
    @Autowired PlatformTransactionManager transactions;
    private static final ByteString HASH = ByteString.copyFrom(new byte[32]);

    @DynamicPropertySource
    static void constructorProperties(DynamicPropertyRegistry registry) {
        registry.add("server.address", () -> "127.0.0.1");
    }

    @Test
    void acceptedRecoveryIsImmutableAndDeletedIdsCannotBeRepopulated() throws Exception {
        var command = command();
        var preview = apply(command, true);
        assertThat(preview.getProtectedCount()).isZero();
        assertThat(preview.getProtectedOccurrenceIdsList()).isEmpty();
        command = command.toBuilder().setParticipantDigest(preview.getParticipantDigest()).build();
        var prepare = action(command, ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE);
        assertThat(apply(prepare, false).getState()).isEqualTo("PREPARED");
        assertThat(apply(prepare, false).getState()).isEqualTo("PREPARED");
        assertThatThrownBy(() -> apply(prepare.toBuilder().setRequestHash(ByteString.copyFrom(ConstructorHashes.sha256(new byte[]{1}))).build(), false))
                .isInstanceOf(StatusRuntimeException.class);
        assertThat(apply(action(command, ConstructorDeletionAction.CONSTRUCTOR_DELETION_SEAL), false).getState()).isEqualTo("SEALED");
        var commit = action(command, ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT);
        assertThat(apply(commit, false).getState()).isEqualTo("DELETED");
        assertThat(apply(commit, false).getState()).isEqualTo("DELETED");
        assertThatThrownBy(() -> apply(action(commit, ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE), false))
                .isInstanceOf(StatusRuntimeException.class);
        var frozen = command;
        assertThatThrownBy(() -> insertHomework(frozen, 1001, 2001, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A new explicit constructor UUID/occurrence may reuse the natural slot.
        insertHomework(command, 999001, 999002, "ACTIVE");
        assertThat(homeworkCount(command)).isEqualTo(1);
    }

    @Test
    void releaseBeforePrepareDeniesDelayedPrepareAndDoesNotDeleteHomework() throws Exception {
        var command = command();
        command = command.toBuilder().setParticipantDigest(apply(command, true).getParticipantDigest()).build();
        assertThat(apply(action(command, ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE), false).getState()).isEqualTo("RELEASED");
        var delayed = action(command, ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE);
        assertThatThrownBy(() -> apply(delayed, false)).isInstanceOf(StatusRuntimeException.class);
        insertHomework(command, 1001, 2001, "ARCHIVED");
        assertThat(homeworkCount(command)).isEqualTo(1);
    }

    @Test
    void archivedOriginalSnapshotProtectsOneLogicalOccurrenceAcrossGenerations() throws Exception {
        var command = command();
        long homework = insertHomework(command, 9001, 9002, "ARCHIVED",
                LocalDate.parse(command.getScope().getLessons(0).getLessonDate()).plusDays(1));
        // Current placement is elsewhere; retained edit evidence names the old lesson.
        jdbc.update("""
                INSERT INTO homework_edit_history(homework_id,semester_id,revision,actor_id,occurred_at,action,before_snapshot,after_snapshot)
                VALUES(?,?,1,?,now(),'UPDATE',?::jsonb,?::jsonb)
                """, homework, command.getScope().getSemesterId(), command.getOriginalActorId(),
                "{\"original\":{\"snapshotLessonId\":1001}}",
                "{\"lessonDate\":\""+LocalDate.parse(command.getScope().getLessons(0).getLessonDate()).plusDays(1)+"\",\"lessonNumber\":1}");
        var protectedPreview = apply(command, true);
        assertThat(protectedPreview.getProtectedCount()).isEqualTo(1);
        assertThat(protectedPreview.getProtectedOccurrenceIdsList()).containsExactly(2001L);
        assertThat(protectedPreview.getProtectedReasons(0).getCode()).isEqualTo("HOMEWORK");
        var prepare = action(command.toBuilder().setParticipantDigest(protectedPreview.getParticipantDigest()).build(),
                ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE);
        assertThatThrownBy(() -> apply(prepare, false)).isInstanceOf(StatusRuntimeException.class);
        assertThat(homeworkCount(command)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM homework_edit_history WHERE homework_id=?", Long.class, homework)).isEqualTo(1);
    }

    @Test
    void pendingEditToOldOccurrenceProtectsFamilyWithoutCurrentPlacementThere() throws Exception {
        var command = command();
        long homework = insertHomework(command, 9001, 9002, "ACTIVE",
                LocalDate.parse(command.getScope().getLessons(0).getLessonDate()).plusDays(1));
        var scope = command.getScope();
        long binding = jdbc.queryForObject("SELECT binding_id FROM homeworks WHERE id=?",Long.class,homework);
        jdbc.update("""
                INSERT INTO homework_edit_operations(operation_id,homework_id,binding_id,actor_id,request_key,command_hash,
                    group_id,subject_id,semester_id,expected_revision,expected_binding_revision,request,before_snapshot,desired_snapshot,target_occurrence_id)
                VALUES(?,?,?,?,?,?,?,?,?,1,1,'{}'::jsonb,?::jsonb,?::jsonb,2001)
                """,UUID.randomUUID(),homework,binding,command.getOriginalActorId(),UUID.randomUUID(),new byte[32],
                scope.getGroupId(),scope.getSubjectId(),scope.getSemesterId(),
                "{\"placementLessonId\":9001}","{\"selectedOccurrenceId\":2001}");
        var preview = apply(command,true);
        assertThat(preview.getProtectedCount()).isEqualTo(1);
        assertThat(preview.getProtectedOccurrenceIdsList()).containsExactly(2001L);
        assertThat(preview.getProtectedReasons(0).getCode()).isEqualTo("HOMEWORK");
    }

    @Test
    void unknownRetainedSnapshotProtectsItsSubjectButNotAnUnrelatedFamily() throws Exception {
        var command=command();
        long homework=insertHomework(command,9001,9002,"ARCHIVED",
                LocalDate.parse(command.getScope().getLessons(0).getLessonDate()).plusDays(1));
        jdbc.update("""
                INSERT INTO homework_edit_history(homework_id,semester_id,revision,actor_id,occurred_at,action,before_snapshot,after_snapshot)
                VALUES(?,?,1,?,now(),'UPDATE','{}'::jsonb,'{}'::jsonb)
                """,homework,command.getScope().getSemesterId(),command.getOriginalActorId());
        var preview=apply(command,true);
        assertThat(preview.getProtectedCount()).isEqualTo(1);
        assertThat(preview.getProtectedOccurrenceIdsList()).containsExactly(2001L);
        assertThat(preview.getProtectedReasons(0).getCode()).isEqualTo("UNKNOWN_HISTORY");
        assertThat(apply(command(),true).getProtectedCount()).isZero();
    }

    @Test
    void writerCommittedFirstMakesPrepareStaleAndPrepareCommittedFirstRejectsWriter() throws Exception {
        var command = command();
        var accepted = command.toBuilder().setParticipantDigest(apply(command, true).getParticipantDigest()).build();
        var prepare = action(accepted, ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE);
        var writerReady = new CountDownLatch(1);
        var finishWriter = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> tx().execute(status -> {
                insertHomework(accepted, 1001, 2001, "PENDING");
                writerReady.countDown();
                await(finishWriter);
                return null;
            }));
            assertThat(writerReady.await(5, TimeUnit.SECONDS)).isTrue();
            var prepareStarted = new CountDownLatch(1);
            var receiver = pool.submit(() -> { prepareStarted.countDown(); return apply(prepare, false); });
            assertThat(prepareStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> receiver.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { finishWriter.countDown(); }
            writer.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> receiver.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(StatusRuntimeException.class);
            assertThat(homeworkCount(accepted)).isEqualTo(1);
        } finally { finishWriter.countDown(); }

        var other = command();
        var otherPrepare = action(other.toBuilder().setParticipantDigest(apply(other, true).getParticipantDigest()).build(),
                ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE);
        var prepared = new CountDownLatch(1);
        var commitPrepare = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var receiver = pool.submit(() -> tx().execute(status -> {
                try { apply(otherPrepare, false); } catch (Exception failure) { throw new RuntimeException(failure); }
                prepared.countDown();
                await(commitPrepare);
                return null;
            }));
            assertThat(prepared.await(5, TimeUnit.SECONDS)).isTrue();
            var writerStarted = new CountDownLatch(1);
            var writer = pool.submit(() -> { writerStarted.countDown(); return insertHomework(other, 1001, 2001, "PENDING"); });
            assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally { commitPrepare.countDown(); }
            receiver.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> writer.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(DataIntegrityViolationException.class);
            assertThat(homeworkCount(other)).isZero();
        } finally { commitPrepare.countDown(); }
    }

    @Test
    void previewRequiresSignedOwnGroupAndCommitRequiresPreparedIdentity() throws Exception {
        var command = command();
        assertThatThrownBy(() -> Context.current().withValue(ServicePrincipal.CONTEXT_KEY,ServicePrincipal.SCHEDULE_SERVICE)
                .call(() -> participant.apply(command))).isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
        assertThatThrownBy(() -> apply(command, false)).isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
        var foreign = new InternalJwtClaims(command.getOriginalActorId(), UUID.randomUUID(), 1, 1,
                "STUDENT", "ACTIVE", command.getScope().getGroupId()+1, true, false);
        assertThatThrownBy(() -> serviceContext().withValue(ConstructorServiceIdentityInterceptor.ACTOR, foreign)
                .call(() -> participant.apply(command))).isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
        var commit = action(command.toBuilder().setParticipantDigest(apply(command, true).getParticipantDigest()).build(),
                ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT);
        assertThatThrownBy(() -> apply(commit, false)).isInstanceOf(StatusRuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_constructor_deletion_participants", Long.class)).isZero();
    }

    private ConstructorDeletionCommand command() {
        var c = identityFixture.canonical();
        long subject = jdbc.queryForObject("INSERT INTO subjects(name,type,group_id,semester_id) VALUES('constructor fence','lecture',?,?) RETURNING id",
                Long.class,c.groupId(),c.semesterId());
        jdbc.update("INSERT INTO subject_lesson_types(subject_id,lesson_type) VALUES(?,'lecture')", subject);
        LocalDate day = jdbc.queryForObject("SELECT date_from FROM semesters WHERE id=?",LocalDate.class,c.semesterId());
        var lesson = ConstructorLessonScope.newBuilder().setLessonId(1001).setOccurrenceId(2001).setScheduleItemId(3001)
                .setLessonDate(day.toString()).setLessonNumber(1).setLessonType("lecture").setGeneration(1)
                .setLessonRevision(1).setOccurrenceRevision(1).setOriginProvenanceHash(HASH).build();
        var scope = ConstructorDeletionScope.newBuilder().setGroupId(c.groupId()).setSubjectId(subject).setSemesterId(c.semesterId())
                .addScheduleItemIds(3001).setScheduleManifestDigest(HASH).addLessons(lesson)
                .addLessons(lesson.toBuilder().setLessonId(1002).setGeneration(2)).build();
        return ConstructorDeletionCommand.newBuilder().setOperationId(UUID.randomUUID().toString())
                .setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW).setRequestHash(HASH)
                .setScopeHash(ByteString.copyFrom(ConstructorHashes.sha256(scope.toByteArray())))
                .setOriginalActorId(c.adminId()).setScope(scope).build();
    }

    private long insertHomework(ConstructorDeletionCommand command,long lesson,long occurrence,String state) {
        return insertHomework(command,lesson,occurrence,state,LocalDate.parse(command.getScope().getLessons(0).getLessonDate()));
    }

    private long insertHomework(ConstructorDeletionCommand command,long lesson,long occurrence,String state,LocalDate day) {
        var s=command.getScope();
        return jdbc.queryForObject("""
                INSERT INTO homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,binding_mode,
                    binding_id,actor_id,request_key,payload_hash,publication_state,placement_lesson_id,placement_occurrence_id,placement_binding_revision)
                VALUES(?,?,?,'constructor race',?,?,1,'LESSON',?,?,?, ?,?,?,?,1) RETURNING id
                """,Long.class,s.getGroupId(),s.getSubjectId(),s.getSemesterId(),command.getOriginalActorId(),
                day,Math.abs(UUID.randomUUID().getMostSignificantBits()/2)+1,
                command.getOriginalActorId(),UUID.randomUUID(),new byte[32],state,lesson,occurrence);
    }

    private long homeworkCount(ConstructorDeletionCommand command) {
        return jdbc.queryForObject("SELECT count(*) FROM homeworks WHERE subject_id=?",Long.class,command.getScope().getSubjectId());
    }

    private ConstructorParticipantReceipt apply(ConstructorDeletionCommand command,boolean signed) throws Exception {
        var context=serviceContext();
        if(signed) context=context.withValue(ConstructorServiceIdentityInterceptor.ACTOR,new InternalJwtClaims(
                command.getOriginalActorId(),UUID.randomUUID(),1,1,"ADMIN","ACTIVE",null,false,false));
        return context.call(() -> participant.apply(command));
    }

    private Context serviceContext() { return Context.current().withValue(ServicePrincipal.CONTEXT_KEY,ServicePrincipal.SCHEDULE_SERVICE)
                .withValue(ConstructorServiceIdentityInterceptor.VERIFIED_SCHEDULE,true); }
    private TransactionTemplate tx() { var tx=new TransactionTemplate(transactions); tx.setTimeout(10); return tx; }
    private static ConstructorDeletionCommand action(ConstructorDeletionCommand command,ConstructorDeletionAction action) {
        return command.toBuilder().setAction(action).build();
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("race fixture timed out"); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
    }
}
