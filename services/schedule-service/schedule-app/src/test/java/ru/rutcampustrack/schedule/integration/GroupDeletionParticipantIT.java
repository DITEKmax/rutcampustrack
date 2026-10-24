package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.groupdeletion.grpc.*;
import ru.rutcampustrack.schedule.contract.enums.*;
import ru.rutcampustrack.schedule.groupdeletion.ScheduleGroupDeletionParticipant;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;

import java.time.LocalDate;
import java.util.UUID;

class GroupDeletionParticipantIT extends AbstractScheduleIntegrationTest {
    @Autowired ScheduleGroupDeletionParticipant participant;
    @Autowired PlatformTransactionManager manager;

    @BeforeEach
    void fixture() {
        resetScheduleData();
        seed(10, 20, 1, 30);
        seed(10, 21, 2, 31);
        seed(11, 22, 1, 30);
    }

    @Test
    void allSemestersOfOnlyTargetGroupAreErasedAndRetriedWithDurableTombstone() {
        var c = prepare();
        assertThat(c.getLessonsCount()).isEqualTo(2);
        participant.apply(c);
        participant.apply(c.toBuilder().setAction(GroupDeletionAction.SEAL).build());
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE lessons SET revision=revision+1 WHERE group_id=10"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        var commit = c.toBuilder().setAction(GroupDeletionAction.COMMIT).build();
        var receipt = participant.apply(commit);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM lessons WHERE group_id=10", Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM lesson_occurrences WHERE group_id=10",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM schedule_items WHERE group_id=10",
                                Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM lessons WHERE group_id=11", Long.class))
                .isEqualTo(1L);
        assertThat(participant.apply(commit)).isEqualTo(receipt);
        assertThatThrownBy(
                        () ->
                                participant.apply(
                                        c.toBuilder()
                                                .setActorId(99)
                                                .setAction(GroupDeletionAction.COMMIT)
                                                .build()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(
                        () ->
                                participant.apply(
                                        c.toBuilder()
                                                .setAction(GroupDeletionAction.RELEASE)
                                                .build()))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> seed(10, 23, 3, 30)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void staleManifestHasNoEffectsAndReleaseBeforePrepareSurvivesLateCommands() {
        var stale = prepare();
        jdbcTemplate.update("UPDATE lessons SET revision=revision+1 WHERE group_id=10");
        assertThatThrownBy(() -> participant.apply(stale)).isInstanceOf(RuntimeException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lessons", Long.class))
                .isEqualTo(3L);
        var c = prepare();
        var release = c.toBuilder().setAction(GroupDeletionAction.RELEASE).build();
        var receipt = participant.apply(release);
        assertThat(participant.apply(release)).isEqualTo(receipt);
        assertThatThrownBy(() -> participant.apply(c)).isInstanceOf(RuntimeException.class);
        jdbcTemplate.update("UPDATE lessons SET revision=revision+1 WHERE group_id=10");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM lessons", Long.class))
                .isEqualTo(3L);
    }

    @Test
    void actualCallbackAdmitsParticipantRuntimeDmlWithoutSchemaOrReceiptTruncateAuthority()
            throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath();
        while (root != null
                && !java.nio.file.Files.isRegularFile(
                        root.resolve("infra/postgres/flyway-callbacks/afterMigrate.sql")))
            root = root.getParent();
        assertThat(root).as("repository callback source").isNotNull();
        String callback =
                java.nio.file.Files.readString(
                        root.resolve("infra/postgres/flyway-callbacks/afterMigrate.sql"));
        // Fixture owner delegates only inside this fresh testcontainer, then runs the actual
        // callback
        // as its required migration identity. Runtime receives only the callback's closed
        // allowlist.
        jdbcTemplate.execute("ALTER SCHEMA public OWNER TO rct_migrator");
        jdbcTemplate.execute(
                "GRANT ALL ON ALL TABLES IN SCHEMA public TO rct_migrator WITH GRANT OPTION");
        jdbcTemplate.execute(
                "GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO rct_migrator WITH GRANT OPTION");
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            jdbcTemplate.execute("SET LOCAL ROLE rct_migrator");
                            jdbcTemplate.execute(callback);
                        });
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT"
                                    + " has_table_privilege('rct_runtime','group_deletion_participants','SELECT,INSERT,UPDATE,DELETE')",
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT"
                                    + " has_table_privilege('rct_runtime','group_deletion_participants','TRUNCATE')"
                                    + " OR has_schema_privilege('rct_runtime','public','CREATE')",
                                Boolean.class))
                .isFalse();
        var preview =
                GroupDeletionCommand.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setActorId(42)
                        .setGroupId(10)
                        .setAction(GroupDeletionAction.PREVIEW)
                        .build();
        var receipt = runtimeApply(preview);
        var c =
                preview.toBuilder()
                        .setAction(GroupDeletionAction.PREPARE)
                        .setParticipantDigest(receipt.getParticipantDigest())
                        .setPreviewDigest("a".repeat(64))
                        .addAllLessons(receipt.getLessonsList())
                        .build();
        runtimeApply(c);
        runtimeApply(c.toBuilder().setAction(GroupDeletionAction.SEAL).build());
        runtimeApply(c.toBuilder().setAction(GroupDeletionAction.COMMIT).build());
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM lessons WHERE group_id=10", Long.class))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM lessons WHERE group_id=11", Long.class))
                .isEqualTo(1);
    }

    private GroupDeletionReceipt runtimeApply(GroupDeletionCommand command) {
        return new TransactionTemplate(manager)
                .execute(
                        status -> {
                            jdbcTemplate.execute("SET LOCAL ROLE rct_runtime");
                            return participant.apply(command);
                        });
    }

    private GroupDeletionCommand prepare() {
        var preview =
                GroupDeletionCommand.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setGroupId(10)
                        .setActorId(42)
                        .setAction(GroupDeletionAction.PREVIEW)
                        .build();
        var receipt = participant.apply(preview);
        return preview.toBuilder()
                .setAction(GroupDeletionAction.PREPARE)
                .setParticipantDigest(receipt.getParticipantDigest())
                .setPreviewDigest("a".repeat(64))
                .addAllLessons(receipt.getLessonsList())
                .build();
    }

    private long seed(long group, long subject, int slot, long semester) {
        return new TransactionTemplate(manager)
                .execute(
                        status -> {
                            var item = new ScheduleItem();
                            item.setGroupId(group);
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
}
