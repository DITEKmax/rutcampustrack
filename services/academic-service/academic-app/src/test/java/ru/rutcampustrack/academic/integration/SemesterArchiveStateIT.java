package ru.rutcampustrack.academic.integration;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.academic.contract.dto.semester.DeleteSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.SemesterStateRequest;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.semester.SemesterService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL/gRPC checks for authoritative semester archive lifecycle state. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "grpc.server.in-process-name=academic-grpc-test",
                "grpc.server.port=-1",
                "grpc.client.inProcess.address=in-process:academic-grpc-test",
                "grpc.client.inProcess.negotiationType=plaintext"
        }
)
class SemesterArchiveStateIT extends AbstractAcademicIntegrationTest {

    @GrpcClient("inProcess")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub grpc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SemesterService semesterService;

    @Autowired
    private SemesterRepository semesterRepository;

    private final List<Long> fixtureIds = new ArrayList<>();
    private List<Long> priorActiveSemesterIds;
    private Long semesterId;

    @BeforeEach
    void createActiveFixture() {
        priorActiveSemesterIds = jdbcTemplate.query(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id",
                (rs, row) -> rs.getLong(1));
        semesterId = insertInactiveFixture("archive-state-" + UUID.randomUUID());
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", semesterId);
    }

    @AfterEach
    void restoreFixtureAndPriorActiveState() {
        if (priorActiveSemesterIds == null) {
            return;
        }
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        for (Long fixtureId : fixtureIds) {
            jdbcTemplate.update("DELETE FROM semesters WHERE id = ?", fixtureId);
        }
        for (Long priorId : priorActiveSemesterIds) {
            jdbcTemplate.update("UPDATE semesters SET is_archived = false, archive_transition = 'NONE', "
                    + "is_active = true, state_version = state_version + 1 WHERE id = ?", priorId);
        }
        fixtureIds.clear();
    }

    @Test
    void getSemesterState_reportsTransitionsReplayAndWriteBlock() {
        SemesterStateResponse initial = state(semesterId);
        assertThat(initial.getActive()).isTrue();
        assertThat(initial.getArchived()).isFalse();
        assertThat(initial.getTransition().name()).isEqualTo("NONE");
        assertThat(initial.getWriteBlocked()).isFalse();

        long outboxBaseline = latestOutboxId();
        Semester archiving = semesterService.beginArchiveTransition(semesterId);
        assertThat(archiving.isActive()).isFalse();
        assertThat(archiving.isArchived()).isFalse();
        assertThat(archiving.getArchiveTransition().name()).isEqualTo("ARCHIVING");
        assertThat(activeSemesterIds()).isEmpty();

        SemesterStateResponse archivingState = state(semesterId);
        assertThat(archivingState.getId()).isEqualTo(semesterId);
        assertThat(archivingState.getActive()).isFalse();
        assertThat(archivingState.getArchived()).isFalse();
        assertThat(archivingState.getTransition().name()).isEqualTo("ARCHIVING");
        assertThat(archivingState.getWriteBlocked()).isTrue();
        assertThat(archivingState.getStateVersion()).isEqualTo(archiving.getStateVersion());
        assertThat(archivedEventIdsAfter(outboxBaseline)).containsExactly(semesterId);
        assertDeleteBlocked(semesterId, archiving.getName());

        Semester repeatedArchiving = semesterService.beginArchiveTransition(semesterId);
        assertThat(repeatedArchiving.getStateVersion()).isEqualTo(archiving.getStateVersion());
        assertThat(archivedEventIdsAfter(outboxBaseline)).containsExactly(semesterId);

        assertThatThrownBy(() -> semesterService.activateSemester(semesterId))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> semesterService.completeArchiveTransition(
                semesterId, archiving.getStateVersion() - 1))
                .isInstanceOf(ConflictException.class);

        Semester archived = semesterService.completeArchiveTransition(
                semesterId, archiving.getStateVersion());
        SemesterStateResponse archivedState = state(semesterId);
        assertThat(archivedState.getActive()).isFalse();
        assertThat(archivedState.getArchived()).isTrue();
        assertThat(archivedState.getTransition().name()).isEqualTo("NONE");
        assertThat(archivedState.getWriteBlocked()).isTrue();
        assertThat(archivedState.getStateVersion()).isEqualTo(archived.getStateVersion());
        assertThat(archived.getStateVersion()).isEqualTo(archiving.getStateVersion());
        assertThat(semesterService.completeArchiveTransition(semesterId, archiving.getStateVersion())
                .getStateVersion())
                .isEqualTo(archived.getStateVersion());
        assertThatThrownBy(() -> semesterService.activateSemester(semesterId))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> semesterService.completeArchiveTransition(semesterId, -1))
                .isInstanceOf(ConflictException.class);

        Semester archivedRow = semesterRepository.findById(semesterId).orElseThrow();
        assertDeleteBlocked(semesterId, archivedRow.getName());
        assertThatThrownBy(() -> semesterService.updateSemester(semesterId,
                new UpdateSemesterRequest(archivedRow.getName(), archivedRow.getDateFrom(), archivedRow.getDateTo())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("архив");

        Semester restoring = semesterService.beginRestoreTransition(semesterId);
        SemesterStateResponse restoringState = state(semesterId);
        assertThat(restoring.isActive()).isFalse();
        assertThat(restoringState.getActive()).isFalse();
        assertThat(restoringState.getArchived()).isTrue();
        assertThat(restoringState.getTransition().name()).isEqualTo("RESTORING");
        assertThat(restoringState.getWriteBlocked()).isTrue();
        assertThat(semesterService.beginRestoreTransition(semesterId).getStateVersion())
                .isEqualTo(restoring.getStateVersion());

        assertThatThrownBy(() -> semesterService.completeRestoreTransition(
                semesterId, restoring.getStateVersion() - 1))
                .isInstanceOf(ConflictException.class);
        Semester restored = semesterService.completeRestoreTransition(
                semesterId, restoring.getStateVersion());
        SemesterStateResponse restoredState = state(semesterId);
        assertThat(restored.isActive()).isFalse();
        assertThat(restored.isArchived()).isFalse();
        assertThat(restoredState.getActive()).isFalse();
        assertThat(restoredState.getArchived()).isFalse();
        assertThat(restoredState.getTransition().name()).isEqualTo("NONE");
        assertThat(restoredState.getWriteBlocked()).isFalse();
        assertThat(restored.getStateVersion()).isEqualTo(restoring.getStateVersion());
        assertThat(activeSemesterIds()).isEmpty();
        assertThat(semesterService.completeRestoreTransition(semesterId, restoring.getStateVersion())
                .getStateVersion())
                .isEqualTo(restored.getStateVersion());
        assertThatThrownBy(() -> semesterService.completeRestoreTransition(semesterId, -1))
                .isInstanceOf(ConflictException.class);

        Semester restoredRow = semesterRepository.findById(semesterId).orElseThrow();
        semesterService.updateSemester(semesterId,
                new UpdateSemesterRequest(restoredRow.getName(), restoredRow.getDateFrom(), restoredRow.getDateTo()));
        assertThat(semesterService.activateSemester(semesterId).isActive()).isTrue();
        assertThat(state(semesterId).getActive()).isTrue();

        long normallyDeletableId = insertInactiveFixture("normal-delete-" + UUID.randomUUID());
        String normallyDeletableName = semesterRepository.findById(normallyDeletableId).orElseThrow().getName();
        semesterService.deleteSemester(normallyDeletableId, new DeleteSemesterRequest(normallyDeletableName));
        assertThat(semesterRepository.existsById(normallyDeletableId)).isFalse();

        StatusRuntimeException missing = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class, () -> state(Long.MAX_VALUE));
        assertThat(missing.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        StatusRuntimeException invalid = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class, () -> state(0L));
        assertThat(invalid.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void archiveAndActivation_areSerializedAndKeepAtMostOneActiveSemester() throws Exception {
        long activationTargetId = insertInactiveFixture("activation-race-" + UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Semester> archive = pool.submit(() -> awaitStartThenRun(
                    ready, start, () -> semesterService.beginArchiveTransition(semesterId)));
            Future<Semester> activate = pool.submit(() -> awaitStartThenRun(
                    ready, start, () -> semesterService.activateSemester(activationTargetId)));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            long outboxBaseline = latestOutboxId();
            start.countDown();
            Semester archivedTarget = archive.get(20, TimeUnit.SECONDS);
            Semester activeTarget = activate.get(20, TimeUnit.SECONDS);

            assertThat(archivedTarget.isActive()).isFalse();
            assertThat(archivedTarget.getArchiveTransition().name()).isEqualTo("ARCHIVING");
            assertThat(activeTarget.isActive()).isTrue();
            assertThat(activeSemesterIds()).containsExactly(activationTargetId);
            assertThat(state(semesterId).getWriteBlocked()).isTrue();
            assertThat(state(activationTargetId).getActive()).isTrue();
            assertThat(archivedEventIdsAfter(outboxBaseline)).containsExactly(semesterId);

            long deleteRaceOutboxBaseline = latestOutboxId();
            String deleteRaceName = activeTarget.getName();
            CountDownLatch deleteRaceReady = new CountDownLatch(2);
            CountDownLatch deleteRaceStart = new CountDownLatch(1);
            Future<Semester> archiveBeforeDelete = pool.submit(() -> awaitStartThenRun(
                    deleteRaceReady, deleteRaceStart,
                    () -> semesterService.beginArchiveTransition(activationTargetId)));
            Future<Void> deleteDuringArchive = pool.submit(() -> awaitStartThenRun(
                    deleteRaceReady, deleteRaceStart, () -> {
                        semesterService.deleteSemester(
                                activationTargetId, new DeleteSemesterRequest(deleteRaceName));
                        return null;
                    }));

            assertThat(deleteRaceReady.await(10, TimeUnit.SECONDS)).isTrue();
            deleteRaceStart.countDown();

            Semester archiveResult = null;
            Throwable archiveFailure = null;
            try {
                archiveResult = archiveBeforeDelete.get(20, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                archiveFailure = exception.getCause();
            }
            Throwable deleteFailure = null;
            try {
                deleteDuringArchive.get(20, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                deleteFailure = exception.getCause();
            }

            if (semesterRepository.existsById(activationTargetId)) {
                assertThat(archiveFailure).isNull();
                assertThat(archiveResult.getArchiveTransition().name()).isEqualTo("ARCHIVING");
                assertThat(deleteFailure).isInstanceOf(ConflictException.class);
                assertThat(state(activationTargetId).getWriteBlocked()).isTrue();
                assertThat(archivedEventIdsAfter(deleteRaceOutboxBaseline)).containsExactly(activationTargetId);
            } else {
                assertThat(archiveFailure).isInstanceOf(ResourceNotFoundException.class);
                assertThat(deleteFailure).isNull();
                assertThat(archivedEventIdsAfter(deleteRaceOutboxBaseline)).isEmpty();
            }
            assertThat(activeSemesterIds()).isEmpty();
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private SemesterStateResponse state(long id) {
        return grpc.getSemesterState(SemesterStateRequest.newBuilder().setSemesterId(id).build());
    }

    private long insertInactiveFixture(String name) {
        int fixtureYear = 9990 + fixtureIds.size();
        long id = jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                Long.class, name, LocalDate.of(fixtureYear, 1, 1), LocalDate.of(fixtureYear, 6, 30));
        fixtureIds.add(id);
        return id;
    }

    private List<Long> activeSemesterIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id", Long.class);
    }

    private long latestOutboxId() {
        return jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM academic_outbox", Long.class);
    }

    private List<Long> archivedEventIdsAfter(long outboxId) {
        return jdbcTemplate.queryForList("""
                SELECT (payload -> 'payload' ->> 'semester_id')::bigint
                FROM academic_outbox
                WHERE id > ? AND event_type = 'semester.archived'
                ORDER BY id
                """, Long.class, outboxId);
    }

    private void assertDeleteBlocked(long id, String name) {
        assertThatThrownBy(() -> semesterService.deleteSemester(id, new DeleteSemesterRequest(name)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("архив");
        assertThat(semesterRepository.existsById(id)).isTrue();
    }

    private static <T> T awaitStartThenRun(CountDownLatch ready,
                                           CountDownLatch start,
                                           java.util.concurrent.Callable<T> operation)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("semester archive race barrier timed out");
        }
        return operation.call();
    }
}
