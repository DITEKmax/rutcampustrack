package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.LessonStatusTransitionJob;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Real PostgreSQL serialization against constructor admission, cron and semester barriers. */
@TestPropertySource(properties = "server.address=127.0.0.1")
class ConstructorDeletionIsolationIT extends AbstractScheduleIntegrationTest {
    @MockitoBean AcademicGrpcClient academic;
    @MockitoBean Clock clock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired LessonStatusTransitionJob job;

    @BeforeEach
    void prepare() {
        resetScheduleData();
        when(clock.getZone()).thenReturn(ZoneId.of("Europe/Moscow"));
        when(clock.instant()).thenReturn(Instant.parse("2026-04-03T09:00:00Z"));
        when(academic.getSemesterArchiveAuthorityState(1L))
                .thenReturn(SemesterStateResponse.newBuilder().setId(1).build());
    }

    @Test
    void jobRechecksAfterSemesterLockAndContinuesUnrelatedFamily() throws Exception {
        Lesson fenced = fixture(10, LessonStatus.PLANNED);
        Lesson unrelated = fixture(11, LessonStatus.ACTIVE);
        CountDownLatch authorityRead = new CountDownLatch(1);
        when(academic.getSemesterArchiveAuthorityState(1L))
                .thenAnswer(
                        call -> {
                            authorityRead.countDown();
                            return SemesterStateResponse.newBuilder().setId(1).build();
                        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var tick =
                    new TransactionTemplate(transactions)
                            .execute(
                                    transaction -> {
                                        ScheduleSemesterArchiveWriteFence.lockSemester(
                                                jdbcTemplate, 1);
                                        var waitingTick = executor.submit(job::runTransitions);
                                        try {
                                            assertThat(authorityRead.await(5, TimeUnit.SECONDS))
                                                    .isTrue();
                                        } catch (InterruptedException interrupted) {
                                            throw new AssertionError(interrupted);
                                        }
                                        // Job has already read its due candidates. Admission
                                        // commits while
                                        // the job waits for this shared semester lock.
                                        admit(fenced, UUID.randomUUID());
                                        return waitingTick;
                                    });
            tick.get(10, TimeUnit.SECONDS);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT status::text FROM lessons WHERE id=?",
                                    String.class,
                                    fenced.getId()))
                    .isEqualTo("planned");
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT status::text FROM lessons WHERE id=?",
                                    String.class,
                                    unrelated.getId()))
                    .isEqualTo("closed");
            var mapper = new ObjectMapper();
            var effects =
                    outboxStorage.findPending(100).stream()
                            .filter(
                                    event ->
                                            List.of("lesson.started", "lesson.closed")
                                                    .contains(event.eventType()))
                            .toList();
            assertThat(effects).hasSize(1);
            assertThat(
                            mapper.readTree(effects.getFirst().payload())
                                    .path("payload")
                                    .path("lesson_id")
                                    .asLong())
                    .isEqualTo(unrelated.getId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void archiveAndConstructorAdmissionSerializeInBothOrders() throws Exception {
        Lesson closed = fixture(10, LessonStatus.CLOSED);
        var executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch barrierAttempted = new CountDownLatch(1);
            var blockedBarrier =
                    new TransactionTemplate(transactions)
                            .execute(
                                    transaction -> {
                                        admit(closed, UUID.randomUUID());
                                        var contender =
                                                executor.submit(
                                                        () -> {
                                                            barrierAttempted.countDown();
                                                            insertBarrier("READY");
                                                        });
                                        await(barrierAttempted);
                                        return contender;
                                    });
            assertThatThrownBy(() -> blockedBarrier.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(DataAccessException.class);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM schedule_semester_archive_barriers",
                                    Long.class))
                    .isZero();

            resetScheduleData();
            Lesson otherClosed = fixture(10, LessonStatus.CLOSED);
            CountDownLatch constructorAttempted = new CountDownLatch(1);
            var blockedConstructor =
                    new TransactionTemplate(transactions)
                            .execute(
                                    transaction -> {
                                        insertBarrier("DELETE_PREPARING");
                                        var contender =
                                                executor.submit(
                                                        () -> {
                                                            constructorAttempted.countDown();
                                                            admit(otherClosed, UUID.randomUUID());
                                                        });
                                        await(constructorAttempted);
                                        return contender;
                                    });
            assertThatThrownBy(() -> blockedConstructor.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(DataAccessException.class);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM constructor_deletion_operations",
                                    Long.class))
                    .isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void everyNonterminalStateBlocksArchiveAndDeletionUntilResolution() {
        for (String state : List.of("PREPARING", "SEALED", "COMMITTING", "RELEASING")) {
            resetScheduleData();
            Lesson closed = fixture(10, LessonStatus.CLOSED);
            UUID operation = UUID.randomUUID();
            admit(closed, operation);
            if (!state.equals("PREPARING")) transition(operation, "SEALED");
            if (state.equals("COMMITTING") || state.equals("RELEASING"))
                transition(operation, state);
            for (String barrier : List.of("PENDING", "READY", "DELETE_PREPARING", "DELETE_SEALED"))
                assertThatThrownBy(() -> insertBarrier(barrier))
                        .isInstanceOf(DataAccessException.class);
            if (!state.equals("COMMITTING")) {
                if (!state.equals("RELEASING")) transition(operation, "RELEASING");
                transition(operation, "RELEASED");
                insertBarrier("READY");
            }
        }
    }

    private void admit(Lesson lesson, UUID operation) {
        jdbcTemplate.update(
                """
INSERT INTO constructor_deletion_operations
    (operation_id,actor_id,request_key,kind,requested_item_id,group_id,subject_id,semester_id,
     family_ids,request_hash,preview_revision,manifest,attendance_command,academic_command,state)
VALUES(?,42,?,'DELETE_UNUSED_SERIES',?,?,100,1,ARRAY[?]::bigint[],
       decode(repeat('01',32),'hex'),repeat('a',64),constructor_deletion_manifest(ARRAY[?]::bigint[]),?,?,'PREPARING')
""",
                operation,
                UUID.randomUUID(),
                lesson.getScheduleItemId(),
                lesson.getGroupId(),
                lesson.getScheduleItemId(),
                lesson.getScheduleItemId(),
                new byte[] {1},
                new byte[] {1});
    }

    private void insertBarrier(String state) {
        jdbcTemplate.update(
                "INSERT INTO"
                    + " schedule_semester_archive_barriers(semester_id,operation_id,state_version,participant_state)"
                    + " VALUES(1,?,1,?)",
                UUID.randomUUID(),
                state);
    }

    private void transition(UUID operation, String state) {
        jdbcTemplate.update(
                "UPDATE constructor_deletion_operations SET state=? WHERE operation_id=?",
                state,
                operation);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            throw new AssertionError(interrupted);
        }
    }

    private Lesson fixture(long group, LessonStatus status) {
        var item = new ScheduleItem();
        item.setGroupId(group);
        item.setSubjectId(100L);
        item.setSemesterId(1L);
        item.setDayOfWeek((short) 5);
        item.setLessonNumber((short) 1);
        item.setWeekType(WeekType.ALL);
        item.setRoom("101");
        item.setActive(true);
        item = saveFixtureScheduleItem(item);
        var lesson = new Lesson();
        lesson.setScheduleItemId(item.getId());
        lesson.setDate(LocalDate.of(2026, 4, 3));
        lesson.setStatus(status);
        if (status == LessonStatus.CLOSED)
            lesson.setClosedAt(OffsetDateTime.ofInstant(clock.instant(), clock.getZone()));
        lesson.setCreatedAt(OffsetDateTime.now());
        return saveFixtureLesson(lesson);
    }
}
